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

        // Server-side safety: if player is at/below 0 HP but head is intact, force downed instead of death
        if (!player.getWorld().isClient) {
            // If a forced death is pending, ensure it completes and skip other logic
            if (body.isPendingDeath()) {
                if (player.isAlive()) {
                    player.damage(player.getDamageSources().outOfWorld(), 1000.0f);
                }
                return;
            }

            BodyPart head = body.getPart(PlayerBodyParts.HEAD);
            // Only do safety if the player is alive and we are not forcing a death
            if (player.isAlive()) {
                if (player.getHealth() <= 0.0f && head != null && head.getHealth() > 0.0f && !body.isDowned()) {
                    body.startDowned();
                    player.setHealth(1.0f);
                }
            }
            body.tickDowned();
        }
        if (body.isDowned()) {
            // Hard immobilize: extreme slowness and mining fatigue, prevent sprinting and jumping
            player.setSprinting(false);
            player.removeStatusEffect(StatusEffects.SLOWNESS);
            player.removeStatusEffect(StatusEffects.MINING_FATIGUE);
            player.removeStatusEffect(StatusEffects.WEAKNESS);
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 40, 255, false, false));
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.MINING_FATIGUE, 40, 255, false, false));
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 40, 2, false, false));

            // Downed pose: use SWIMMING for a reliable prone/lying visual without a bed
            player.setPose(EntityPose.SWIMMING);
            player.setSwimming(false);

            // Nudge velocity towards zero on server and kill vertical motion to prevent jump "hops"
            if (!player.getWorld().isClient) {
                player.setVelocity(0.0, 0.0, 0.0);
                player.velocityDirty = true;
                player.setSneaking(false);
            }
            return;
        }

        // Replace damage-based effects with bone-based system
        body.applyBrokenBonesEffects();

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
            // If we were previously using the prone pose for downed, restore standing when no longer downed
            if (player.getPose() == EntityPose.SWIMMING && !player.isTouchingWater()) {
                player.setSwimming(false);
                player.setPose(EntityPose.STANDING);
            }
        }
    }
}
