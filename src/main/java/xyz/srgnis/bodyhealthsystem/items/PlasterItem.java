package xyz.srgnis.bodyhealthsystem.items;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.world.World;
import xyz.srgnis.bodyhealthsystem.body.Body;
import xyz.srgnis.bodyhealthsystem.body.BodyPart;
import xyz.srgnis.bodyhealthsystem.body.player.BodyProvider;
import xyz.srgnis.bodyhealthsystem.body.player.PlayerBodyParts;
import xyz.srgnis.bodyhealthsystem.network.ServerNetworking;
import xyz.srgnis.bodyhealthsystem.registry.ModStatusEffects;

public class PlasterItem extends Item {
    private static final String TARGET_NBT = "PlasterTargetId";
    private static final int USE_TICKS = 40; // 2 seconds
    private static final float HEAL_AMOUNT = 2.0f;

    public PlasterItem(Settings settings) {
        super(settings);
    }

    // Determine the best body part to heal with plaster, skipping permanently locked fractures
    private BodyPart findBestPlasterTarget(Body body) {
        BodyPart best = null;

        // First: if any BROKEN parts exist, heal the most missing among them (use boosted cap), skip locked
        BodyPart bestBroken = null;
        float brokenMissing = 0f;
        for (BodyPart part : body.getParts()) {
            float health = part.getHealth();
            float baseMax = part.getMaxHealth();
            float boost = Math.max(0.0f, body.getBoostForPart(part.getIdentifier()));
            float effMax = baseMax + boost;
            if (health <= 0f && !part.isFractureLocked()) {
                float missing = effMax - health;
                if (bestBroken == null || missing > brokenMissing) {
                    bestBroken = part;
                    brokenMissing = missing;
                }
            }
        }
        if (bestBroken != null) return bestBroken;

        // Second: prioritize torso/head if damaged (use boosted cap), skip locked
        BodyPart bestTorsoHead = null;
        int bestTHPriority = -1; // 1 = critical, 0 = damaged
        float bestTHMissing = 0f;
        for (BodyPart part : body.getParts()) {
            if (!part.getIdentifier().equals(PlayerBodyParts.HEAD) && !part.getIdentifier().equals(PlayerBodyParts.TORSO)) continue;
            if (part.isFractureLocked()) continue;
            float health = part.getHealth();
            float baseMax = part.getMaxHealth();
            float boost = Math.max(0.0f, body.getBoostForPart(part.getIdentifier()));
            float effMax = baseMax + boost;
            if (health >= effMax) continue; // not damaged
            int priority = (health <= part.getCriticalThreshold()) ? 1 : 0;
            float missing = effMax - health;
            if (priority > bestTHPriority || (priority == bestTHPriority && missing > bestTHMissing)) {
                bestTorsoHead = part;
                bestTHPriority = priority;
                bestTHMissing = missing;
            }
        }
        if (bestTorsoHead != null) return bestTorsoHead;

        // Finally: fallback to general selection by critical first then missing (use boosted cap), skip locked
        BodyPart bestGeneral = null;
        int bestPriority = -1; // 1 = critical, 0 = damaged
        float bestMissing = 0f;
        for (BodyPart part : body.getParts()) {
            if (part.isFractureLocked()) continue;
            float health = part.getHealth();
            float baseMax = part.getMaxHealth();
            float boost = Math.max(0.0f, body.getBoostForPart(part.getIdentifier()));
            float effMax = baseMax + boost;
            if (health >= effMax) continue;
            int priority = (health <= part.getCriticalThreshold() ? 1 : 0);
            float missing = effMax - health;
            if (priority > bestPriority || (priority == bestPriority && missing > bestMissing)) {
                bestGeneral = part;
                bestPriority = priority;
                bestMissing = missing;
            }
        }
        return bestGeneral;
    }

    private boolean canUseOnTarget(LivingEntity target) {
        if (target instanceof BodyProvider) {
            Body body = ((BodyProvider) target).getBody();
            BodyPart candidate = findBestPlasterTarget(body);
            return candidate != null;
        } else {
            return target.getHealth() < target.getMaxHealth();
        }
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        NbtCompound tag = stack.getNbt();
        if (tag != null && tag.contains(TARGET_NBT)) {
            tag.remove(TARGET_NBT);
            if (tag.isEmpty()) stack.setNbt(null);
        }
        // Only begin using (start animation) if there is a valid target to heal
        if (!canUseOnTarget(user)) {
            return TypedActionResult.fail(stack);
        }
        user.setCurrentHand(hand);
        return TypedActionResult.consume(stack);
    }

    @Override
    public ActionResult useOnEntity(ItemStack stack, PlayerEntity user, LivingEntity entity, Hand hand) {
        // Only start using on the entity if it can actually be treated
        if (!canUseOnTarget(entity)) {
            return ActionResult.PASS;
        }
        NbtCompound tag = stack.getOrCreateNbt();
        tag.putInt(TARGET_NBT, entity.getId());
        user.setCurrentHand(hand);
        return ActionResult.CONSUME;
    }

    @Override
    public int getMaxUseTime(ItemStack stack) {
        return USE_TICKS;
    }

    @Override
    public UseAction getUseAction(ItemStack stack) {
        return UseAction.BRUSH;
    }

    @Override
    public ItemStack finishUsing(ItemStack stack, World world, LivingEntity user) {
        if (!world.isClient) {
            LivingEntity target = user;
            NbtCompound tag = stack.getNbt();
            if (tag != null && tag.contains(TARGET_NBT)) {
                Entity e = world.getEntityById(tag.getInt(TARGET_NBT));
                if (e instanceof LivingEntity) {
                    target = (LivingEntity) e;
                }
                tag.remove(TARGET_NBT);
                if (tag.isEmpty()) stack.setNbt(null);
            }

            boolean consumed = false;

            if (target instanceof BodyProvider) {
                Body body = ((BodyProvider) target).getBody();

                // First, remove one small wound if present anywhere (priority before HP heal)
                BodyPart smallWoundPart = null;
                for (BodyPart p : body.getParts()) {
                    try { if (p.getSmallWounds() > 0) { smallWoundPart = p; break; } } catch (Throwable ignored) {}
                }
                if (smallWoundPart != null) {
                    if (smallWoundPart.removeSmallWound()) {
                        consumed = true;
                    }
                }

                // Then proceed with original heal logic
                BodyPart best = findBestPlasterTarget(body);
                if (best != null) {
                    float healValue = HEAL_AMOUNT;
                    if (target.hasStatusEffect(ModStatusEffects.DRESSING_EFFECT)) {
                        healValue *= 2.0f;
                        target.removeStatusEffect(ModStatusEffects.DRESSING_EFFECT);
                    }
                    float before = best.getHealth();
                    body.healPart(healValue, best);
                    body.updateHealth();
                    if (target instanceof PlayerEntity) {
                        ServerNetworking.syncBody((PlayerEntity) target);
                    }
                    float healed = best.getHealth() - before;
                    if (healed > 0f) {
                        consumed = true;
                    }
                }

                if (consumed) {
                    stack.decrement(1);
                    world.playSound(null, target.getBlockPos(), SoundEvents.BLOCK_WOOL_PLACE, SoundCategory.PLAYERS, 1.0f, 1.0f);
                }
            }

            // Fallback to vanilla heal ONLY for non-body entities
            if (!consumed && !(target instanceof BodyProvider) && target.getHealth() < target.getMaxHealth()) {
                float healValue = HEAL_AMOUNT;
                if (target.hasStatusEffect(ModStatusEffects.DRESSING_EFFECT)) {
                    healValue *= 2.0f;
                    target.removeStatusEffect(ModStatusEffects.DRESSING_EFFECT);
                }
                target.playSound(SoundEvents.BLOCK_WOOL_BREAK, 1.0F, 1.0F);
                target.heal(healValue);
                stack.decrement(1);
            }
        }
        return stack;
    }

    @Override
    public void onStoppedUsing(ItemStack stack, World world, LivingEntity user, int remainingUseTicks) {
        NbtCompound tag = stack.getNbt();
        if (tag != null && tag.contains(TARGET_NBT)) {
            tag.remove(TARGET_NBT);
            if (tag.isEmpty()) stack.setNbt(null);
        }
    }
}
