package xyz.srgnis.bodyhealthsystem.mixin;

import net.minecraft.item.ItemStack;
import net.minecraft.screen.AnvilScreenHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.srgnis.bodyhealthsystem.registry.AnvilHandlers;

@Mixin(AnvilScreenHandler.class)
public class DisableDefaultAnvilResultMixin {
    @Inject(method = "updateResult()V", at = @At("HEAD"))
    private void bhs$clearResultFirst(CallbackInfo ci) {
        AnvilScreenHandler self = (AnvilScreenHandler) (Object) this;
        // Clear result to avoid stale outputs; vanilla will repopulate for vanilla recipes
        ItemStack left = self.getSlot(0).getStack();
        ItemStack right = self.getSlot(1).getStack();
        if (left.isEmpty() || right.isEmpty()) {
            self.getSlot(2).setStack(ItemStack.EMPTY);
            self.setProperty(0, 0);
        }
    }
}
