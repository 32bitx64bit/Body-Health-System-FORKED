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

public class AnkleBraceItem extends Item {
    private static final String TARGET_NBT = "AnkleBraceTargetId";
    private static final int USE_TICKS = 40; // 2 seconds

    public AnkleBraceItem(Settings settings) {
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
                tag.remove(TARGET_NBT);
                if (tag.isEmpty()) stack.setNbt(null);
            }

            if (target instanceof BodyProvider) {
                Body body = ((BodyProvider) target).getBody();
                BodyPart left = body.getPart(PlayerBodyParts.LEFT_FOOT);
                BodyPart right = body.getPart(PlayerBodyParts.RIGHT_FOOT);
                boolean fixed = false;
                if (left != null && left.isBroken()) { left.setBroken(false); left.setBrokenTopHalf(null); left.setHealth(Math.max(1.0f, left.getHealth())); fixed = true; }
                if (right != null && right.isBroken()) { right.setBroken(false); right.setBrokenTopHalf(null); right.setHealth(Math.max(1.0f, right.getHealth())); fixed = true; }
                if (fixed && target instanceof PlayerEntity) {
                    body.onBoneTreatmentApplied();
                    ServerNetworking.syncBody((PlayerEntity) target);
                    stack.decrement(1);
                    world.playSound(null, target.getBlockPos(), SoundEvents.ITEM_ARMOR_EQUIP_LEATHER, SoundCategory.PLAYERS, 1.0f, 1.0f);
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
