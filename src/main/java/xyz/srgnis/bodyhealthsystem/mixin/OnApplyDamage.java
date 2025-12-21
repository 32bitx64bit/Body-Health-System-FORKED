package xyz.srgnis.bodyhealthsystem.mixin;

import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.tag.DamageTypeTags;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import xyz.srgnis.bodyhealthsystem.body.Body;
import xyz.srgnis.bodyhealthsystem.body.player.BodyProvider;
import xyz.srgnis.bodyhealthsystem.network.ServerNetworking;
import xyz.srgnis.bodyhealthsystem.registry.ModStatusEffects;
import xyz.srgnis.bodyhealthsystem.config.Config;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;

@Mixin(PlayerEntity.class)
public class OnApplyDamage {

    //NOTE: The method signature is needed to be able to access the source parameter.
    @ModifyVariable(method = "applyDamage(Lnet/minecraft/entity/damage/DamageSource;F)V", at = @At("HEAD"), ordinal = 0, argsOnly = true)
    public float handleHealthChange(float amount, DamageSource source) {
        if(source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY)){
            //If is out of world (/kill) just return the damage to kill the player
            return amount;
        }
        if (!((PlayerEntity) (Object)this).isInvulnerableTo(source)) {
            PlayerEntity player = (PlayerEntity)(Object)this;
            Body body = ((BodyProvider)this).getBody();
            body.applyDamageBySource(amount, source);

            // Totems should trigger if the head OR torso would be destroyed.
            // Vanilla totems are normally based on heart HP reaching 0; since we manage HP ourselves,
            // we invoke the vanilla totem path here when a critical part is destroyed.
            if (!player.getWorld().isClient) {
                var head = body.getPart(xyz.srgnis.bodyhealthsystem.body.player.PlayerBodyParts.HEAD);
                var torso = body.getPart(xyz.srgnis.bodyhealthsystem.body.player.PlayerBodyParts.TORSO);
                boolean criticalDestroyed = (head != null && head.getHealth() <= 0.0f)
                        || (torso != null && torso.getHealth() <= 0.0f);
                if (criticalDestroyed) {
                    try {
                        boolean used = ((TryUseTotemInvoker) player).invokeTryUseTotem(source);
                        if (used) {
                            ServerNetworking.syncBody(player);
                            return 0.0f;
                        }
                    } catch (Throwable ignored) {
                        // If the invoker fails for any reason, fall back to normal behavior.
                    }
                }
            }
            body.updateHealth();

            // If player entered or is in downed state, prevent vanilla health subtraction this hit
            if (body.isDowned()) {
                // Ensure health is strictly positive to prevent vanilla death logic
                if (player.getHealth() <= 0.0f) {
                    player.setHealth(1.0f);
                }
                ServerNetworking.syncBody(player);
                return 0.0f;
            }

            // Apply adrenaline based on incoming damage (server-side only)
            if (!player.getWorld().isClient && amount > 0) {
                int durationTicks = (int)(amount * Config.adrenalineSecondsPerDamage * 10.0f);
                int cap = Math.max(0, Config.adrenalineMaxSeconds * 15);
                StatusEffectInstance existing = player.getStatusEffect(ModStatusEffects.ADRENALINE_EFFECT);
                if (existing != null) {
                    durationTicks += existing.getDuration();
                }
                if (cap > 0) durationTicks = Math.min(durationTicks, cap);
                if (durationTicks > 0) {
                    player.addStatusEffect(new StatusEffectInstance(ModStatusEffects.ADRENALINE_EFFECT, durationTicks, 0));
                    player.removeStatusEffect(StatusEffects.SLOWNESS);
                    player.removeStatusEffect(StatusEffects.MINING_FATIGUE);
                    player.removeStatusEffect(StatusEffects.WEAKNESS);
                }
            }

            ServerNetworking.syncBody(player);
        }
        // Return 0 to prevent vanilla from applying damage again (since we already synced health via updateHealth)
        return 0.0f;
    }
}
