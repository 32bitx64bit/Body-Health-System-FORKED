package xyz.srgnis.bodyhealthsystem.items;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.world.World;

public class PlasterItem extends Item {
    private static final String TARGET_NBT = "PlasterTargetId";
    private static final int USE_TICKS = 60; // 3 seconds
    private static final float HEAL_AMOUNT = 2.0f;

    public PlasterItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        NbtCompound tag = stack.getOrCreateNbt();
        if (tag.contains(TARGET_NBT)) tag.remove(TARGET_NBT);
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
            }

            if (target.getHealth() < target.getMaxHealth()) {
                target.playSound(SoundEvents.BLOCK_WOOL_BREAK, 1.0F, 1.0F);
                target.heal(HEAL_AMOUNT);
                stack.decrement(1);
            }
        }
        return stack;
    }

    @Override
    public void onStoppedUsing(ItemStack stack, World world, LivingEntity user, int remainingUseTicks) {
        // Released early: do nothing
    }
}
