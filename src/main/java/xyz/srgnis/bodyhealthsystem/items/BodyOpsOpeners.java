package xyz.srgnis.bodyhealthsystem.items;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import xyz.srgnis.bodyhealthsystem.client.screen.BodyOperationsScreenHandler;

public final class BodyOpsOpeners {
    private BodyOpsOpeners() {}

    public static ExtendedScreenHandlerFactory factory(ItemStack stack, LivingEntity entity, String title) {
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
            public Text getDisplayName() {
                return Text.literal(title);
            }
        };
    }
}
