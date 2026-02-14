package xyz.srgnis.bodyhealthsystem.mixin;

import net.minecraft.entity.EntityPose;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.srgnis.bodyhealthsystem.BHSMain;
import xyz.srgnis.bodyhealthsystem.body.Body;
import xyz.srgnis.bodyhealthsystem.body.BodyPart;
import xyz.srgnis.bodyhealthsystem.body.player.BodyProvider;
import xyz.srgnis.bodyhealthsystem.body.player.PlayerBodyParts;
import xyz.srgnis.bodyhealthsystem.config.Config;
import xyz.srgnis.bodyhealthsystem.network.ServerNetworking;
import xyz.srgnis.bodyhealthsystem.registry.ModStatusEffects;

@Mixin(PlayerEntity.class)
public class PlayerTickMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger(BHSMain.MOD_ID + "/PlayerTickMixin");
    
    // Broadcast interval for downed state (in ticks) - increased to reduce network load
    private static final int DOWNED_BROADCAST_INTERVAL = 40; // 2 seconds instead of 1

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

            // Update per-part Health Boost distribution if vanilla max health changed
            if (body.syncBoostIfNeeded()) {
                ServerNetworking.broadcastBody(player);
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
            // Reduced broadcast frequency for downed state to lower network load
            if (body.isDowned() && (player.age % DOWNED_BROADCAST_INTERVAL == 0)) {
                ServerNetworking.broadcastBody(player);
            }

            // Instant-death from temperature only if system enabled
            if (Config.enableTemperatureSystem && player instanceof ServerPlayerEntity spe) {
                double tempC = 0.0;
                try {
                    tempC = gavinx.temperatureapi.BodyTemperatureState.getC(spe);
                } catch (Exception e) {
                    LOGGER.debug("Failed to get body temperature: {}", e.getMessage());
                }
                if (tempC >= 44.0) {
                    player.damage(player.getDamageSources().outOfWorld(), 1000.0f);
                    return;
                }
            }
        }

        // Bleeding + tourniquet/necrosis ticks (server only)
        if (!player.getWorld().isClient) {
            boolean anyBleed = false;
            boolean hasTqOrNec = false;
            boolean woundsEnabled = Config.enableWoundingSystem;
            boolean poulticeActive = player.hasStatusEffect(ModStatusEffects.HERBAL_POULTICE_EFFECT);
            int smallBleedThreshold = 15 * 20 * (poulticeActive ? 2 : 1);
            int largeBleedThreshold = 150 * (poulticeActive ? 2 : 1);
            var rng = player.getRandom();
            int totalWounds = 0;
            // Iterate parts
            for (BodyPart p : body.getParts()) {
                // Tick tourniquet timer
                if (woundsEnabled) p.tickTourniquet();

                // Track if we should sync once per second for UI timers
                if (woundsEnabled && (p.hasTourniquet() || p.getNecrosisState() > 0)) hasTqOrNec = true;

                // Head special: rapid necrosis if tourniquet applied
                if (p.getIdentifier().equals(PlayerBodyParts.HEAD)) {
                    boolean tq = woundsEnabled && p.hasTourniquet();
                    if (tq) {
                        int tqTicks = p.getTourniquetTicks();
                        if (tqTicks >= 15*20 && p.getNecrosisState() == 0) {
                            p.beginNecrosis();
                        }
                        if (p.getNecrosisState() == 1) {
                            p.tickNecrosisLinear(15.0f/60.0f); // 15s to full
                            // If fully necrotic for head -> kill player
                            if (p.getMaxHealth() <= 0.0f) {
                                player.damage(player.getDamageSources().outOfWorld(), 1000.0f);
                                return;
                            }
                        }
                    } else {
                        // If tourniquet removed while necrosis active (healing), tick recovery via existing method
                        p.tickRecovery();
                        // When fully healed, clear necrosis state for clean UI
                        if (p.getNecrosisState() == 1 && p.getNecrosisScale() >= 1.0f) {
                            p.clearNecrosis();
                        }
                    }
                } else {
                    // Limbs: normal necrosis timeline
                    boolean tq = woundsEnabled && p.hasTourniquet();
                    int state = p.getNecrosisState();
                    if (tq) {
                        int tqTicks = p.getTourniquetTicks();
                        if (state == 0 && tqTicks >= 7*60*20) {
                            p.beginNecrosis();
                        }
                        if (state == 1) {
                            // 4 minutes to reduce to zero
                            p.tickNecrosisLinear(4.0f);
                            if (p.getMaxHealth() <= 0.0f) {
                                p.setPermaDead();
                            }
                                }
                    } else {
                        // If necrosis active and removed, tick recovery and clear when fully healed
                        if (state == 1) {
                            p.tickRecovery();
                            if (p.getNecrosisScale() >= 1.0f) {
                                p.clearNecrosis();
                            }
                        }
                    }
                    // Always tick stitches recovery (procedure debuff)
                    p.tickProcedureRecovery();
                }

                // Per-limb bleeding cadence: 15s per tick, paused by tourniquet
                if (woundsEnabled) {
                    int s = p.getSmallWounds();
                    int l = p.getLargeWounds();
                    totalWounds += Math.min(1, s) + Math.min(1, l);
                    boolean tq2 = p.hasTourniquet();
                    if (!tq2 && (s + l) > 0) {
                        p.tickWoundBleed();
                        p.tickWoundBleedLarge();
                        int tS = p.getWoundBleedTicks();
                        int tL = p.getWoundBleedTicksLarge();
                        float dmg = 0.0f;
                        boolean bledThisCycle = false;
                        if (tS >= smallBleedThreshold && s > 0) {
                            dmg += s * 1.0f;
                            p.resetWoundBleedTicks();
                            bledThisCycle = true;
                        }
                        if (tL >= largeBleedThreshold && l > 0) { // base 7.5 seconds = 150 ticks
                            dmg += l * 1.0f;
                            p.resetWoundBleedTicksLarge();
                            bledThisCycle = true;
                        }

                        // Herbal Poultices: on each bleed cycle, chance to heal/downgrade wounds.
                        if (poulticeActive && bledThisCycle) {
                            boolean improved = false;
                            if (p.getSmallWounds() > 0) {
                                if (rng.nextFloat() < 0.40f) {
                                    improved = p.removeSmallWound();
                                }
                            } else if (p.getLargeWounds() > 0) {
                                if (rng.nextFloat() < 0.15f) {
                                    if (p.removeLargeWound()) {
                                        p.addSmallWound();
                                        improved = true;
                                    }
                                }
                            }
                            if (improved) {
                                player.removeStatusEffect(ModStatusEffects.HERBAL_POULTICE_EFFECT);
                                poulticeActive = false;
                                smallBleedThreshold = 15 * 20;
                                largeBleedThreshold = 150;
                            }
                        }
                        if (dmg > 0.0f) {
                            ((Body) body).applyBleedingWithSpill(dmg, p);
                            anyBleed = true;
                        }
                    }
                }
            }

            if (woundsEnabled) {
                // Visual indicator only: Bleeding effect with amplifier 0..4 (represents 1..5)
                // Reduce severity for parts with tourniquets (they pause bleed); approximate by subtracting count of tourniqueted wounded parts.
                int tqSuppressed = 0;
                for (BodyPart p : body.getParts()) {
                    if ((p.getSmallWounds() + p.getLargeWounds()) > 0 && p.hasTourniquet()) tqSuppressed++;
                }
                int effectiveWounds = Math.max(0, totalWounds - tqSuppressed);
                int amp = Math.max(0, Math.min(4, effectiveWounds - 1));
                if (effectiveWounds > 0) {
                    player.addStatusEffect(new StatusEffectInstance(ModStatusEffects.BLEEDING_EFFECT, 40, amp, false, true, true));
                } else {
                    player.removeStatusEffect(ModStatusEffects.BLEEDING_EFFECT);
                }
            } else {
                player.removeStatusEffect(ModStatusEffects.BLEEDING_EFFECT);
            }

            if (anyBleed) {
                body.updateHealth();
                if (player instanceof ServerPlayerEntity) {
                    ServerNetworking.broadcastBody(player);
                }
            }
            // Sync once per second when timers are present so the tooltip updates live
            if ((player.age % 20) == 0 && hasTqOrNec && player instanceof ServerPlayerEntity spe2) {
                xyz.srgnis.bodyhealthsystem.network.TimerSync.sendSelf(spe2);
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

        // Replace damage-based effects with bone-based system (if enabled)
        if (Config.enableBoneSystem) {
            body.applyBrokenBonesEffects();
        } else {
            // Ensure bone debuffs are cleared when disabled
            player.removeStatusEffect(StatusEffects.SLOWNESS);
            player.removeStatusEffect(StatusEffects.MINING_FATIGUE);
            player.removeStatusEffect(StatusEffects.WEAKNESS);
        }

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

        if (Config.enableBoneSystem && crawlingRequired) {
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
            try {
                tempC = gavinx.temperatureapi.BodyTemperatureState.getC(spe);
            } catch (Exception e) {
                LOGGER.debug("Failed to get body temperature for gameplay: {}", e.getMessage());
            }

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