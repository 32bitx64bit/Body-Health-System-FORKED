package xyz.srgnis.bodyhealthsystem.items;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.world.World;
import xyz.srgnis.bodyhealthsystem.body.BodyPart;
import xyz.srgnis.bodyhealthsystem.body.player.BodyProvider;
import xyz.srgnis.bodyhealthsystem.client.screen.BodyOperationsScreenHandler;

public class StitchesItem extends Item {
    private static final String TARGET_NBT = "StitchesTargetId";

    public StitchesItem(Settings settings) { super(settings); }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        user.openHandledScreen(factory(stack, user));
        return TypedActionResult.success(stack);
    }

    @Override
    public ActionResult useOnEntity(ItemStack stack, PlayerEntity user, LivingEntity entity, Hand hand) {
        if (entity instanceof BodyProvider) {
            user.openHandledScreen(factory(stack, entity));
            return ActionResult.CONSUME;
        }
        return ActionResult.PASS;
    }

    private ExtendedScreenHandlerFactory factory(ItemStack stack, LivingEntity entity) {
        return new ExtendedScreenHandlerFactory() {
            @Override
            public ScreenHandler createMenu(int syncId, PlayerInventory inventory, PlayerEntity player) {
                return new BodyOperationsScreenHandler(syncId, inventory, stack, entity);
            }

            @Override
            public void writeScreenOpeningData(ServerPlayerEntity player, PacketByteBuf buf) {
                buf.writeItemStack(stack);
                buf.writeInt(entity.getId());
            }

            @Override
            public Text getDisplayName() { return Text.literal("Stitches"); }
        };
    }

    @Override
    public UseAction getUseAction(ItemStack stack) { return UseAction.BRUSH; }
}
