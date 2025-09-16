package xyz.srgnis.bodyhealthsystem.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.srgnis.bodyhealthsystem.body.Body;
import xyz.srgnis.bodyhealthsystem.body.player.BodyProvider;

@Mixin(LivingEntity.class)
public abstract class PreventJumpWhenDownedMixin {
    @Inject(method = "jump", at = @At("HEAD"), cancellable = true)
    private void bhs$preventJumpWhileDowned(CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!(self instanceof PlayerEntity)) return;
        Body body = ((BodyProvider) self).getBody();
        if (body != null && body.isDowned()) {
            ci.cancel();
        }
    }
}
