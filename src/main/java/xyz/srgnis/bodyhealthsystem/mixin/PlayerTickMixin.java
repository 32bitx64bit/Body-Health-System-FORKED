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
import xyz.srgnis.bodyhealthsystem.network.ServerNetworking;

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
                    // Inform all clients immediately
                    ServerNetworking.broadcastBody(player);
                }
            }
            body.tickDowned();
            // While downed, periodically sync timer/pose to clients (once per second)
            if (body.isDowned() && (player.age % 20 == 0)) {
                ServerNetworking.broadcastBody(player);
            }
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

            // Ensure pose is set on the server so all clients see it consistently
            if (!player.getWorld().isClient) {
                // Only change pose if not swimming and not in water
                if (!player.isTouchingWater() && (player.getPose() != EntityPose.SWIMMING || !player.isSwimming())) {
                    player.setSwimming(true);
                    player.setPose(EntityPose.SWIMMING);
                }
                // Server: stabilize motion only
                player.setVelocity(0.0, 0.0, 0.0);
                player.velocityDirty = true;
                player.setSneaking(false);
            } else {
                // Client fallback to smooth visuals between server syncs
                if (!player.isTouchingWater() && (player.age % 6 == 0) && (player.getPose() != EntityPose.SWIMMING || !player.isSwimming())) {
                    player.setSwimming(true);
                    player.setPose(EntityPose.SWIMMING);
                }
            }
            return;
        }

        // Replace damage-based effects with bone-based system
        body.applyBrokenBonesEffects();

        // Force crawling only if both legs AND both feet have broken bones (bone state, not HP)
        boolean crawlingRequired = false;
        if (body instanceof xyz.srgnis.bodyhealthsystem.body.player.PlayerBody pb) {
            crawlingRequired = pb.isCrawlingRequired();
        } else {
            BodyPart leftLeg = body.getPart(PlayerBodyParts.LEFT_LEG);
            BodyPart rightLeg = body.getPart(PlayerBodyParts.RIGHT_LEG);
            BodyPart leftFoot = body.getPart(PlayerBodyParts.LEFT_FOOT);
            BodyPart rightFoot = body.getPart(PlayerBodyParts.RIGHT_FOOT);
            boolean bothLegsBroken = leftLeg != null && rightLeg != null && leftLeg.isBroken() && rightLeg.isBroken();
            boolean bothFeetBroken = leftFoot != null && rightFoot != null && leftFoot.isBroken() && rightFoot.isBroken();
            crawlingRequired = bothLegsBroken && bothFeetBroken;
        }

        if (crawlingRequired) {
            // Force crawling pose; set on server so clients remain in sync
            if (!player.getWorld().isClient) {
                // Avoid forcing if in/entering water to prevent pose tug-of-war
                if (!player.isTouchingWater() && (player.getPose() != EntityPose.SWIMMING || !player.isSwimming())) {
                    player.setSwimming(true);
                    player.setPose(EntityPose.SWIMMING);
                }
            } else {
                // Client fallback to smooth visuals between server syncs
                if (!player.isTouchingWater() && (player.age % 8 == 0) && (player.getPose() != EntityPose.SWIMMING || !player.isSwimming())) {
                    player.setSwimming(true);
                    player.setPose(EntityPose.SWIMMING);
                }
            }

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
            // Restore normal pose when not crawling/downed and not in water
            if (!player.getWorld().isClient) {
                if (player.getPose() == EntityPose.SWIMMING && !player.isTouchingWater()) {
                    player.setSwimming(false);
                    player.setPose(EntityPose.STANDING);
                }
            } else {
                if (player.getPose() == EntityPose.SWIMMING && !player.isTouchingWater()) {
                    if (player.age % 10 == 0) {
                        player.setSwimming(false);
                        player.setPose(EntityPose.STANDING);
                    }
                }
            }
        }
    }
}
