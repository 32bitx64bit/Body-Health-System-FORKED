package xyz.srgnis.bodyhealthsystem.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.srgnis.bodyhealthsystem.body.Body;
import xyz.srgnis.bodyhealthsystem.body.player.BodyProvider;

@Mixin(LivingEntity.class)
public abstract class OnDeathMixin {

    @Inject(method = "onDeath", at = @At("HEAD"))
    private void bhs$clearDownedOnDeath(DamageSource source, CallbackInfo ci) {
        LivingEntity self = (LivingEntity)(Object)this;
        if (self instanceof PlayerEntity) {
            Body body = ((BodyProvider) self).getBody();
            if (body != null) body.onVanillaDeath();
        }
    }
}
