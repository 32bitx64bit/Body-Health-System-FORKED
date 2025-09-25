package xyz.srgnis.bodyhealthsystem.mixin;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.AnvilScreenHandler;
import net.minecraft.screen.ForgingScreenHandler;
import net.minecraft.screen.ScreenHandlerContext;
import net.minecraft.screen.ScreenHandlerType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xyz.srgnis.bodyhealthsystem.registry.AnvilHandlers;

@Mixin(AnvilScreenHandler.class)
public abstract class AnvilApplyGelMixin extends ForgingScreenHandler {

    protected AnvilApplyGelMixin(ScreenHandlerType<?> type, int syncId, PlayerInventory playerInventory, ScreenHandlerContext context) {
        super(type, syncId, playerInventory, context);
    }

    // Compute and set our custom result, then cancel vanilla update
    @Inject(method = "updateResult()V", at = @At("HEAD"), cancellable = true)
    private void bhs$applyGelToAnvilResult(CallbackInfo ci) {
        ItemStack left = this.getSlot(0).getStack();
        ItemStack right = this.getSlot(1).getStack();
        ItemStack out = AnvilHandlers.computeResult(left, right);
        if (!out.isEmpty()) {
            this.getSlot(2).setStack(out);
            ci.cancel();
        }
    }

    // Allow taking output when our recipe is active
    @Inject(method = "canTakeOutput(Lnet/minecraft/entity/player/PlayerEntity;Z)Z", at = @At("HEAD"), cancellable = true)
    private void bhs$canTakeOutput(PlayerEntity player, boolean present, CallbackInfoReturnable<Boolean> cir) {
        ItemStack left = this.getSlot(0).getStack();
        ItemStack right = this.getSlot(1).getStack();
        if (!AnvilHandlers.computeResult(left, right).isEmpty()) {
            cir.setReturnValue(true);
        }
    }

    // Consume inputs and (optionally) charge cost on take; let vanilla move the output stack
    @Inject(method = "onTakeOutput(Lnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/item/ItemStack;)V", at = @At("HEAD"))
    private void bhs$consumeGelAndCharge(PlayerEntity player, ItemStack taken, CallbackInfo ci) {
        ItemStack left = this.getSlot(0).getStack();
        ItemStack right = this.getSlot(1).getStack();
        if (!AnvilHandlers.computeResult(left, right).isEmpty()) {
            // Clear left and consume one gel on right
            this.getSlot(0).setStack(ItemStack.EMPTY);
            if (!right.isEmpty()) {
                right.decrement(1);
                this.getSlot(1).setStack(right);
            }
            // No XP charge for now (keeps parity with many anvil-augment mods)
        }
    }
}
