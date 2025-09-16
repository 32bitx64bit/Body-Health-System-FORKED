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
            body.updateHealth();

            // If this hit would kill the player but head is still intact, enter downed and cancel vanilla subtraction
            var head = body.getPart(xyz.srgnis.bodyhealthsystem.body.player.PlayerBodyParts.HEAD);
            float predicted = player.getHealth() - amount;
            if (head != null && head.getHealth() > 0.0f && predicted <= 0.0f) {
                body.startDowned();
                player.setHealth(1.0f);
                ServerNetworking.syncBody(player);
                return 0.0f;
            }

            // If player entered or is in downed state, prevent vanilla health subtraction this hit
            if (body.isDowned()) {
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
        // Return the original amount to allow proper health reduction
        return amount;
    }
}
