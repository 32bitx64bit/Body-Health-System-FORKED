package xyz.srgnis.bodyhealthsystem.mixin;

import net.minecraft.entity.player.HungerManager;
import net.minecraft.world.GameRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import xyz.srgnis.bodyhealthsystem.config.Config;

@Mixin(HungerManager.class)
public class NaturalRegenMixin {
    @Redirect(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/GameRules;getBoolean(Lnet/minecraft/world/GameRules$Key;)Z"))
    private boolean bhs$overrideNaturalRegeneration(GameRules gameRules, GameRules.Key<GameRules.BooleanRule> key) {
        if (Config.forceDisableVanillaRegen && key == GameRules.NATURAL_REGENERATION) {
            // Enforce disabling vanilla natural regeneration regardless of the game rule
            return false;
        }
        return gameRules.get(key).get();
    }
}
