package xyz.srgnis.bodyhealthsystem.mixin;

import net.minecraft.entity.player.HungerManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import xyz.srgnis.bodyhealthsystem.config.Config;

@Mixin(HungerManager.class)
public class NaturalRegenMixin {
    @ModifyVariable(method = "update", at = @At(value = "INVOKE_ASSIGN", target = "Lnet/minecraft/world/GameRules;getBoolean(Lnet/minecraft/world/GameRules$Key;)Z") )
    public boolean overrideNaturalRegeneration(boolean original) {
        if (Config.forceDisableVanillaRegen) {
            // Enforce disabling vanilla natural regeneration regardless of the game rule
            return false;
        }
        return original;
    }
}
