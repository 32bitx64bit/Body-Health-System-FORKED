package xyz.srgnis.bodyhealthsystem.mixin;

import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.srgnis.bodyhealthsystem.body.player.BodyProvider;

@Mixin(MinecraftClient.class)
public class BlockInventoryWhileDownedMixin {
    @Inject(method = "handleInputEvents", at = @At("HEAD"))
    private void bhs$blockInventoryWhenDowned(CallbackInfo ci) {
        MinecraftClient mc = (MinecraftClient) (Object) this;
        if (mc.player == null) return;
        if (!(mc.player instanceof BodyProvider provider)) return;
        var body = provider.getBody();
        if (body == null) return;
        if (!body.isDowned()) return;
        // Consume any pending inventory key presses so vanilla doesn't open the inventory
        if (mc.options != null) {
            while (mc.options.inventoryKey.wasPressed()) {
                // loop until drained
            }
        }
    }
}
