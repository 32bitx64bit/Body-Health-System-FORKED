package xyz.srgnis.bodyhealthsystem.mixin;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.srgnis.bodyhealthsystem.body.Body;
import xyz.srgnis.bodyhealthsystem.body.BodyPart;
import xyz.srgnis.bodyhealthsystem.body.player.BodyProvider;
import xyz.srgnis.bodyhealthsystem.config.Config;
import xyz.srgnis.bodyhealthsystem.network.ServerNetworking;

import java.util.ArrayList;
import net.minecraft.util.math.random.Random;

@Mixin(PlayerEntity.class)
public abstract class SleepHealMixin {

    @Inject(method = "wakeUp", at = @At("TAIL"))
    private void bhs$onWakeUp(boolean skipSleepTimer, boolean updateSleepingPlayers, CallbackInfo ci) {
        PlayerEntity self = (PlayerEntity)(Object)this;
        if (self.getWorld().isClient()) return;
        if (!(self instanceof ServerPlayerEntity spe)) return;

        Body body = ((BodyProvider) self).getBody();
        if (body == null) return;

        float healPercent = Math.max(0.0f, Config.sleepHealPercent);
        if (healPercent > 0.0f) {
            // Compute total effective max health
            float totalMax = 0.0f;
            for (BodyPart p : body.getParts()) {
                float boost = Math.max(0.0f, body.getBoostForPart(p.getIdentifier()));
                totalMax += p.getMaxHealth() + boost;
            }
            float toHeal = Math.max(0.0f, (totalMax * healPercent));
            if (toHeal > 0.0f) {
                body.heal(toHeal);
                body.updateHealth();
            }
        }

        // Bone healing chance on wakeup: try to heal at most one broken bone (if bone system enabled)
        if (Config.enableBoneSystem) {
            float base = Math.max(0.0f, Config.sleepBoneHealBaseChance);
            float daily = Math.max(0.0f, Config.sleepBoneHealDailyIncrease);
            Random rng = self.getRandom();

            ArrayList<BodyPart> broken = new ArrayList<>();
            for (BodyPart p : body.getParts()) {
                // Skip head bone entirely
                if (p.getIdentifier().equals(xyz.srgnis.bodyhealthsystem.body.player.PlayerBodyParts.HEAD)) continue;
                if (p.isBroken()) broken.add(p);
            }
            if (!broken.isEmpty()) {
                // First, increment all broken bones' accumulated chance by daily
                for (BodyPart p : broken) {
                    p.setSleepHealBonus(Math.min(1.0f, p.getSleepHealBonus() + daily));
                }
                // Attempt to heal one random broken bone using base + its accumulated bonus
                BodyPart pick = broken.get(rng.nextInt(broken.size()));
                float chance = Math.min(1.0f, base + pick.getSleepHealBonus());
                boolean healed = rng.nextFloat() < chance;
                if (healed) {
                    pick.setBroken(false);
                    pick.setBrokenTopHalf(null);
                    if (pick.getHealth() <= 0.0f) pick.setHealth(1.0f);
                    pick.setSleepHealBonus(0.0f);
                }
            }
        }

        // Also clear any lingering procedure debuff and necrosis recovery on sleep
        for (BodyPart p : body.getParts()) {
            try {
                // Clear stitches temporary debuff
                p.getClass().getMethod("clearProcedureDebuff").invoke(p);
                // If tourniquet necrosis is healing, complete instantly on sleep
                int state = (int) p.getClass().getMethod("getNecrosisState").invoke(p);
                if (state == 1) { // active
                    p.getClass().getMethod("clearNecrosis").invoke(p);
                    // ensure scale set to full
                    p.getClass().getMethod("forceStartRecovery").invoke(p); // optional: if you prefer instant, set necrosisScale via a setter
                    // But we actually want instant: set recovery to 0 and necrosisScale to 1
                    p.getClass().getMethod("clientSetNecrosis", int.class, float.class).invoke(p, 0, 1.0f);
                }
            } catch (Throwable ignored) {}
        }
        ServerNetworking.broadcastBody(spe);
    }
}
