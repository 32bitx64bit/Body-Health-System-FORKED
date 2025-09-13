package xyz.srgnis.bodyhealthsystem.items;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsage;
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

public class SplintItem extends Item {
    private static final String TARGET_NBT = "SplintTargetId";
    private static final int USE_TICKS = 60; // 3 seconds
    private static final float HEAL_AMOUNT = 4.0f; // heals up to 4 hp on one leg

    public SplintItem(Settings settings) {
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
                // Clear after use
                tag.remove(TARGET_NBT);
                if (tag.isEmpty()) stack.setNbt(null);
            }

            if (target instanceof BodyProvider) {
                Body body = ((BodyProvider) target).getBody();
                BodyPart left = body.getPart(PlayerBodyParts.LEFT_LEG);
                BodyPart right = body.getPart(PlayerBodyParts.RIGHT_LEG);
                if (left != null && right != null) {
                    float leftMissing = Math.max(0f, left.getMaxHealth() - left.getHealth());
                    float rightMissing = Math.max(0f, right.getMaxHealth() - right.getHealth());

                    if (leftMissing <= 0f && rightMissing <= 0f) {
                        return stack; // nothing to heal, do not consume
                    }

                    BodyPart toHeal = leftMissing >= rightMissing ? left : right;
                    float before = toHeal.getHealth();
                    body.healPart(HEAL_AMOUNT, toHeal);
                    body.updateHealth();

                    if (target instanceof PlayerEntity) {
                        ServerNetworking.syncBody((PlayerEntity) target);
                    }

                    float healed = toHeal.getHealth() - before;
                    if (healed > 0f) {
                        stack.decrement(1);
                        world.playSound(null, target.getBlockPos(), SoundEvents.BLOCK_WOOL_PLACE, SoundCategory.PLAYERS, 1.0f, 1.0f);
                    }
                }
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
