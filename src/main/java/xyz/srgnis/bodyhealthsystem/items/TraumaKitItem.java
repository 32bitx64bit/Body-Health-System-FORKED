package xyz.srgnis.bodyhealthsystem.items;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.world.World;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import xyz.srgnis.bodyhealthsystem.body.Body;
import xyz.srgnis.bodyhealthsystem.body.BodyPart;
import xyz.srgnis.bodyhealthsystem.body.player.BodyProvider;
import xyz.srgnis.bodyhealthsystem.body.player.PlayerBodyParts;
import xyz.srgnis.bodyhealthsystem.network.ServerNetworking;

public class TraumaKitItem extends Item {
    private static final String TARGET_NBT = "TraumaKitTargetId";
    private static final int REVIVE_TICKS = 120; // 6 seconds

    public TraumaKitItem(Settings settings) {
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
        if (entity instanceof BodyProvider) {
            NbtCompound tag = stack.getOrCreateNbt();
            tag.putInt(TARGET_NBT, entity.getId());
            user.setCurrentHand(hand);
            return ActionResult.CONSUME;
        }
        return ActionResult.PASS;
    }

    @Override
    public int getMaxUseTime(ItemStack stack) {
        return REVIVE_TICKS;
    }

    @Override
    public UseAction getUseAction(ItemStack stack) {
        return UseAction.BRUSH;
    }

    @Override
    public void onStoppedUsing(ItemStack stack, World world, LivingEntity user, int remainingUseTicks) {
        if (!world.isClient && user instanceof PlayerEntity) {
            PlayerEntity player = (PlayerEntity) user;
            NbtCompound tag = stack.getNbt();
            LivingEntity target = user;
            if (tag != null && tag.contains(TARGET_NBT)) {
                Entity e = world.getEntityById(tag.getInt(TARGET_NBT));
                if (e instanceof LivingEntity) target = (LivingEntity) e;
            }
            if (target instanceof BodyProvider) {
                Body body = ((BodyProvider) target).getBody();
                body.endRevive(player);
            }
            if (tag != null && tag.contains(TARGET_NBT)) {
                tag.remove(TARGET_NBT);
                if (tag.isEmpty()) stack.setNbt(null);
            }
        }
    }

    @Override
    public ItemStack finishUsing(ItemStack stack, World world, LivingEntity user) {
        if (!world.isClient) {
            LivingEntity target = user;
            NbtCompound tag = stack.getNbt();
            if (tag != null && tag.contains(TARGET_NBT)) {
                Entity e = world.getEntityById(tag.getInt(TARGET_NBT));
                if (e instanceof LivingEntity) target = (LivingEntity) e;
                tag.remove(TARGET_NBT);
                if (tag.isEmpty()) stack.setNbt(null);
            }
            if (target instanceof BodyProvider) {
                Body body = ((BodyProvider) target).getBody();
                // Attempt to perform revive if downed
                if (body.isDowned()) {
                    if (user instanceof PlayerEntity player) body.endRevive(player);
                    // Head destroyed? Can't revive
                    BodyPart head = body.getPart(PlayerBodyParts.HEAD);
                    if (head != null && head.getHealth() <= 0.0f) return stack;
                    // Trauma Kit can revive even if torso destroyed
                    body.applyRevival(3, 4);
                    if (target instanceof PlayerEntity) {
                        ServerNetworking.syncBody((PlayerEntity) target);
                    }
                    stack.decrement(1);
                    world.playSound(null, target.getBlockPos(), SoundEvents.ITEM_TOTEM_USE, SoundCategory.PLAYERS, 1.0f, 1.2f);
                } else {
                    // Not downed: apply its strong effect anyway
                    for (BodyPart p : ((BodyProvider) target).getBody().getParts()) {
                        if (p.getHealth() < p.getMaxHealth()) {
                            p.setHealth(Math.min(p.getMaxHealth(), p.getHealth() + 3));
                        }
                    }
                    ((BodyProvider) target).getBody().applyStatusEffectWithAmplifier(net.minecraft.entity.effect.StatusEffects.REGENERATION, 0);
                    ((BodyProvider) target).getBody().updateHealth();
                    if (target instanceof PlayerEntity) {
                        ServerNetworking.syncBody((PlayerEntity) target);
                    }
                    stack.decrement(1);
                    world.playSound(null, target.getBlockPos(), SoundEvents.ITEM_TOTEM_USE, SoundCategory.PLAYERS, 1.0f, 1.0f);
                }
            }
        }
        return stack;
    }

}