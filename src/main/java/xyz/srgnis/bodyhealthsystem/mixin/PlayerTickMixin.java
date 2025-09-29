package xyz.srgnis.bodyhealthsystem.mixin;

import net.minecraft.entity.EntityPose;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.srgnis.bodyhealthsystem.body.Body;
import xyz.srgnis.bodyhealthsystem.body.BodyPart;
import xyz.srgnis.bodyhealthsystem.body.player.BodyProvider;
import xyz.srgnis.bodyhealthsystem.body.player.PlayerBodyParts;
import xyz.srgnis.bodyhealthsystem.config.Config;
import xyz.srgnis.bodyhealthsystem.network.ServerNetworking;
import xyz.srgnis.bodyhealthsystem.registry.ModStatusEffects;

@Mixin(PlayerEntity.class)
public class PlayerTickMixin {

    @Unique
    private static void clearHeatConditions(PlayerEntity p) {
        p.removeStatusEffect(ModStatusEffects.HEAT_STROKE_INIT);
        p.removeStatusEffect(ModStatusEffects.HEAT_STROKE_MOD);
        p.removeStatusEffect(ModStatusEffects.HEAT_STROKE_SERV);
    }

    @Unique
    private static void clearColdConditions(PlayerEntity p) {
        p.removeStatusEffect(ModStatusEffects.HYPO_MILD);
        p.removeStatusEffect(ModStatusEffects.HYPO_MOD);
        p.removeStatusEffect(ModStatusEffects.HYPO_SERV);
    }

    @Unique
    private static void setHeatConditionStage(PlayerEntity p, int stage) {
        clearHeatConditions(p);
        switch (stage) {
            case 1 -> p.addStatusEffect(new StatusEffectInstance(ModStatusEffects.HEAT_STROKE_INIT, 40, 0, false, true, true));
            case 2 -> p.addStatusEffect(new StatusEffectInstance(ModStatusEffects.HEAT_STROKE_MOD, 40, 0, false, true, true));
            case 3 -> p.addStatusEffect(new StatusEffectInstance(ModStatusEffects.HEAT_STROKE_SERV, 40, 0, false, true, true));
        }
    }

    @Unique
    private static void setColdConditionStage(PlayerEntity p, int stage) {
        clearColdConditions(p);
        switch (stage) {
            case 1 -> p.addStatusEffect(new StatusEffectInstance(ModStatusEffects.HYPO_MILD, 40, 0, false, true, true));
            case 2 -> p.addStatusEffect(new StatusEffectInstance(ModStatusEffects.HYPO_MOD, 40, 0, false, true, true));
            case 3 -> p.addStatusEffect(new StatusEffectInstance(ModStatusEffects.HYPO_SERV, 40, 0, false, true, true));
        }
    }

    @Unique
    private static java.util.List<BodyPart> pickColdTargets(Body body) {
        java.util.List<BodyPart> out = new java.util.ArrayList<>();
        BodyPart la = body.getPart(PlayerBodyParts.LEFT_ARM);
        BodyPart ra = body.getPart(PlayerBodyParts.RIGHT_ARM);
        BodyPart ll = body.getPart(PlayerBodyParts.LEFT_LEG);
        BodyPart rl = body.getPart(PlayerBodyParts.RIGHT_LEG);
        BodyPart lf = body.getPart(PlayerBodyParts.LEFT_FOOT);
        BodyPart rf = body.getPart(PlayerBodyParts.RIGHT_FOOT);
        if (la != null && la.getHealth() > 0.0f) out.add(la);
        if (ra != null && ra.getHealth() > 0.0f) out.add(ra);
        if (ll != null && ll.getHealth() > 0.0f) out.add(ll);
        if (rl != null && rl.getHealth() > 0.0f) out.add(rl);
        if (lf != null && lf.getHealth() > 0.0f) out.add(lf);
        if (rf != null && rf.getHealth() > 0.0f) out.add(rf);
        return out;
    }

    @Unique private int bhs$heatTickCounter = 0;
    @Unique private int bhs$coldTickCounter = 0;

    @Inject(method = "tick", at = @At("HEAD"))
    public void onTick(CallbackInfo ci){
        PlayerEntity player = (PlayerEntity) (Object) this;
        Body body = ((BodyProvider)player).getBody();

        // Server-side safety: if player is at/below 0 HP but head is intact, force downed instead of death
        if (!player.getWorld().isClient) {
            if (body.isPendingDeath()) {
                if (player.isAlive()) {
                    player.damage(player.getDamageSources().outOfWorld(), 1000.0f);
                }
                return;
            }

            BodyPart head = body.getPart(PlayerBodyParts.HEAD);
            if (player.isAlive()) {
                if (player.getHealth() <= 0.0f && head != null && head.getHealth() > 0.0f && !body.isDowned()) {
                    body.startDowned();
                    player.setHealth(1.0f);
                    ServerNetworking.broadcastBody(player);
                }
            }
            body.tickDowned();
            if (body.isDowned() && (player.age % 20 == 0)) {
                ServerNetworking.broadcastBody(player);
            }

            // Instant-death from temperature only if system enabled
            if (Config.enableTemperatureSystem && player instanceof ServerPlayerEntity spe) {
                double tempC = 0.0;
                try { tempC = gavinx.temperatureapi.BodyTemperatureState.getC(spe); } catch (Throwable ignored) {}
                if (tempC >= 44.0) {
                    player.damage(player.getDamageSources().outOfWorld(), 1000.0f);
                    return;
                }
            }
        }

        if (body.isDowned()) {
            player.setSprinting(false);
            player.removeStatusEffect(StatusEffects.SLOWNESS);
            player.removeStatusEffect(StatusEffects.MINING_FATIGUE);
            player.removeStatusEffect(StatusEffects.WEAKNESS);
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 40, 255, false, false));
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.MINING_FATIGUE, 40, 255, false, false));
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 40, 2, false, false));

            if (!player.getWorld().isClient) {
                if (!player.isTouchingWater() && (player.getPose() != EntityPose.SWIMMING || !player.isSwimming())) {
                    player.setSwimming(true);
                    player.setPose(EntityPose.SWIMMING);
                }
                player.setVelocity(0.0, 0.0, 0.0);
                player.velocityDirty = true;
                player.setSneaking(false);
            } else {
                if (!player.isTouchingWater() && (player.age % 6 == 0) && (player.getPose() != EntityPose.SWIMMING || !player.isSwimming())) {
                    player.setSwimming(true);
                    player.setPose(EntityPose.SWIMMING);
                }
            }
            return;
        }

        // Replace damage-based effects with bone-based system
        body.applyBrokenBonesEffects();

        // Force crawling only if both legs AND both feet have broken bones
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
            if (!player.getWorld().isClient) {
                if (!player.isTouchingWater() && (player.getPose() != EntityPose.SWIMMING || !player.isSwimming())) {
                    player.setSwimming(true);
                    player.setPose(EntityPose.SWIMMING);
                }
            } else {
                if (!player.isTouchingWater() && (player.age % 8 == 0) && (player.getPose() != EntityPose.SWIMMING || !player.isSwimming())) {
                    player.setSwimming(true);
                    player.setPose(EntityPose.SWIMMING);
                }
            }

            boolean hasSuppression = player.getStatusEffect(ModStatusEffects.MORPHINE_EFFECT) != null
                    || player.getStatusEffect(ModStatusEffects.ADRENALINE_EFFECT) != null;

            if (!hasSuppression) {
                player.removeStatusEffect(StatusEffects.SLOWNESS);
                StatusEffectInstance s = player.getStatusEffect(StatusEffects.SLOWNESS);
                if (s == null || s.getAmplifier() > 1 || s.getDuration() <= 5) {
                    player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 40, 1, false, false));
                }
            } else {
                player.removeStatusEffect(StatusEffects.SLOWNESS);
            }
        } else {
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

        // Temperature gameplay: only when enabled
        if (!player.getWorld().isClient && Config.enableTemperatureSystem && player instanceof ServerPlayerEntity spe) {
            double tempC = 0.0;
            try { tempC = gavinx.temperatureapi.BodyTemperatureState.getC(spe); } catch (Throwable ignored) {}

            // Heat stroke
            if (tempC >= 40.0) {
                // hidden debuffs
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 40, 0, false, false, false));
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 40, 0, false, false, false));
                if (tempC < 41.0) setHeatConditionStage(player, 1);
            }
            if (tempC >= 41.0) {
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 40, 1, false, false, false));
                if (tempC < 42.0) setHeatConditionStage(player, 2);
            }
            if (tempC >= 42.0) {
                setHeatConditionStage(player, 3);
                // periodic heat damage (~2s default)
                bhs$heatTickCounter++;
                if (bhs$heatTickCounter >= 40) {
                    bhs$heatTickCounter = 0;
                    java.util.List<BodyPart> candidates = new java.util.ArrayList<>();
                    for (BodyPart p : body.getNoCriticalParts()) {
                        if (p.getHealth() > 0.0f && !p.getIdentifier().equals(PlayerBodyParts.HEAD)) {
                            candidates.add(p);
                        }
                    }
                    if (candidates.isEmpty()) {
                        for (BodyPart p : body.getParts()) {
                            if (p.getHealth() > 0.0f && !p.getIdentifier().equals(PlayerBodyParts.HEAD)) {
                                candidates.add(p);
                            }
                        }
                    }
                    if (!candidates.isEmpty()) {
                        BodyPart target = candidates.get(player.getRandom().nextInt(candidates.size()));
                        body.applyNonBreakingDamage(1.0f, player.getDamageSources().generic(), target);
                        body.updateHealth();
                        ServerNetworking.broadcastBody(player);
                    }
                }
            } else {
                if (bhs$heatTickCounter > 40) bhs$heatTickCounter = 40;
            }

            // Hypothermia
            if (tempC < 35.0) {
                if (tempC >= 32.0) {
                    // Mild hypothermia: icon only, no gameplay debuffs
                    setColdConditionStage(player, 1);
                } else if (tempC >= 28.0) {
                    player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 40, 1, false, false, false));
                    player.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 40, 0, false, false, false));
                    player.addStatusEffect(new StatusEffectInstance(StatusEffects.MINING_FATIGUE, 40, 0, false, false, false));
                    setColdConditionStage(player, 2);
                    // periodic cold damage (60s)
                    bhs$coldTickCounter++;
                    if (bhs$coldTickCounter >= 1200) {
                        bhs$coldTickCounter = 0;
                        java.util.List<BodyPart> targets = pickColdTargets(body);
                        if (!targets.isEmpty()) {
                            BodyPart t = targets.get(player.getRandom().nextInt(targets.size()));
                            body.applyNonBreakingDamage(1.0f, player.getDamageSources().generic(), t);
                            body.updateHealth();
                            ServerNetworking.broadcastBody(player);
                        }
                    }
                } else {
                    player.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 40, 1, false, false, false));
                    player.addStatusEffect(new StatusEffectInstance(StatusEffects.MINING_FATIGUE, 40, 1, false, false, false));
                    player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 40, 2, false, false, false));
                    setColdConditionStage(player, 3);
                    // periodic cold damage (15s)
                    bhs$coldTickCounter++;
                    if (bhs$coldTickCounter >= 300) {
                        bhs$coldTickCounter = 0;
                        java.util.List<BodyPart> targets = pickColdTargets(body);
                        if (!targets.isEmpty()) {
                            BodyPart t = targets.get(player.getRandom().nextInt(targets.size()));
                            body.applyNonBreakingDamage(1.0f, player.getDamageSources().generic(), t);
                            body.updateHealth();
                            ServerNetworking.broadcastBody(player);
                        }
                    }
                }
            } else {
                if (bhs$coldTickCounter > 0) bhs$coldTickCounter--;
                clearColdConditions(player);
            }
        } else if (!player.getWorld().isClient && !Config.enableTemperatureSystem) {
            // System disabled: clear icons and reset counters to avoid lingering visuals
            clearHeatConditions(player);
            clearColdConditions(player);
            bhs$heatTickCounter = 0;
            bhs$coldTickCounter = 0;
        }
    }
}