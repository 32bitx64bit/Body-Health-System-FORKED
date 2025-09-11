package xyz.srgnis.bodyhealthsystem.mixin;

import net.minecraft.entity.EntityPose;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.srgnis.bodyhealthsystem.body.Body;
import xyz.srgnis.bodyhealthsystem.body.BodyPart;
import xyz.srgnis.bodyhealthsystem.body.player.BodyProvider;
import xyz.srgnis.bodyhealthsystem.body.player.PlayerBodyParts;
import xyz.srgnis.bodyhealthsystem.registry.ModStatusEffects;

@Mixin(PlayerEntity.class)
public class PlayerTickMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    public void onTick(CallbackInfo ci){
        PlayerEntity player = (PlayerEntity) (Object) this;
        Body body = ((BodyProvider)player).getBody();
        body.applyCriticalPartsEffect();

        // Force crawling if both legs and both feet are broken (health <= 0)
        BodyPart leftLeg = body.getPart(PlayerBodyParts.LEFT_LEG);
        BodyPart rightLeg = body.getPart(PlayerBodyParts.RIGHT_LEG);
        BodyPart leftFoot = body.getPart(PlayerBodyParts.LEFT_FOOT);
        BodyPart rightFoot = body.getPart(PlayerBodyParts.RIGHT_FOOT);

        boolean legsAndFeetBroken = leftLeg.getHealth() <= 0.0f && rightLeg.getHealth() <= 0.0f
                && leftFoot.getHealth() <= 0.0f && rightFoot.getHealth() <= 0.0f;

        if (legsAndFeetBroken) {
            // Force crawling pose and ensure camera lowers
            player.setPose(EntityPose.SWIMMING);
            player.setSwimming(true);

            boolean hasSuppression = player.getStatusEffect(ModStatusEffects.MORPHINE_EFFECT) != null
                    || player.getStatusEffect(ModStatusEffects.ADRENALINE_EFFECT) != null;

            if (!hasSuppression) {
                // Cap Slowness at level 2 (amplifier 1) while crawling
                player.removeStatusEffect(StatusEffects.SLOWNESS);

                StatusEffectInstance s = player.getStatusEffect(StatusEffects.SLOWNESS);
                if (s == null || s.getAmplifier() > 1 || s.getDuration() <= 5) {
                    player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 40, 1, false, false));
                }
            } else {
                // If suppression is active, remove negatives
                player.removeStatusEffect(StatusEffects.SLOWNESS);
            }
        } else {
            // Restore normal pose if not required to crawl and currently in crawling pose
            if (player.getPose() == EntityPose.SWIMMING && !player.isTouchingWater()) {
                player.setSwimming(false);
                player.setPose(EntityPose.STANDING);
            }
        }
    }
}
