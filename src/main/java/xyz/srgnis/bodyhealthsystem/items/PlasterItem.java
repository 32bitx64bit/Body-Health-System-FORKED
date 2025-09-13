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

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        NbtCompound tag = stack.getNbt();
        if (tag != null && tag.contains(TARGET_NBT)) {
            tag.remove(TARGET_NBT);
            if (tag.isEmpty()) stack.setNbt(null);
        }
        user.setCurrentHand(hand);
        return TypedActionResult.consume(stack);
    }

    @Override
    public ActionResult useOnEntity(ItemStack stack, PlayerEntity user, LivingEntity entity, Hand hand) {
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
                BodyPart best = null;

                // First: if any broken parts exist, heal the most missing among them
                BodyPart bestBroken = null;
                float brokenMissing = 0f;
                for (BodyPart part : body.getParts()) {
                    float health = part.getHealth();
                    float max = part.getMaxHealth();
                    if (health <= 0f) {
                        float missing = max - health;
                        if (bestBroken == null || missing > brokenMissing) {
                            bestBroken = part;
                            brokenMissing = missing;
                        }
                    }
                }

                if (bestBroken != null) {
                    best = bestBroken;
                } else {
                    // Second: prioritize torso/head if damaged
                    BodyPart bestTorsoHead = null;
                    int bestTHPriority = -1; // 1 = critical, 0 = damaged
                    float bestTHMissing = 0f;
                    for (BodyPart part : body.getParts()) {
                        if (!part.getIdentifier().equals(PlayerBodyParts.HEAD) && !part.getIdentifier().equals(PlayerBodyParts.TORSO)) continue;
                        float health = part.getHealth();
                        float max = part.getMaxHealth();
                        if (health >= max) continue; // not damaged
                        int priority = (health <= part.getCriticalThreshold()) ? 1 : 0;
                        float missing = max - health;
                        if (priority > bestTHPriority || (priority == bestTHPriority && missing > bestTHMissing)) {
                            bestTorsoHead = part;
                            bestTHPriority = priority;
                            bestTHMissing = missing;
                        }
                    }
                    if (bestTorsoHead != null) {
                        best = bestTorsoHead;
                    } else {
                        // Finally: fallback to general selection by critical first then missing
                        BodyPart bestGeneral = null;
                        int bestPriority = -1; // 1 = critical, 0 = damaged
                        float bestMissing = 0f;
                        for (BodyPart part : body.getParts()) {
                            float health = part.getHealth();
                            float max = part.getMaxHealth();
                            if (health >= max) continue; 
                            int priority = (health <= part.getCriticalThreshold() ? 1 : 0);
                            float missing = max - health;
                            if (priority > bestPriority || (priority == bestPriority && missing > bestMissing)) {
                                bestGeneral = part;
                                bestPriority = priority;
                                bestMissing = missing;
                            }
                        }
                        best = bestGeneral;
                    }
                }

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
                        stack.decrement(1);
                        world.playSound(null, target.getBlockPos(), SoundEvents.BLOCK_WOOL_PLACE, SoundCategory.PLAYERS, 1.0f, 1.0f);
                        consumed = true;
                    }
                }
            }

            // Fallback to vanilla heal if not a BodyProvider or nothing selected
            if (!consumed && target.getHealth() < target.getMaxHealth()) {
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
