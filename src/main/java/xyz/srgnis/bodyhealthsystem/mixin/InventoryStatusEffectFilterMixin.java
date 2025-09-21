package xyz.srgnis.bodyhealthsystem.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.AbstractInventoryScreen;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.client.gui.DrawContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import xyz.srgnis.bodyhealthsystem.registry.ModStatusEffects;

import java.util.ArrayList;
import java.util.Collection;

@Mixin(AbstractInventoryScreen.class)
public abstract class InventoryStatusEffectFilterMixin {

    @ModifyVariable(
        method = "drawStatusEffects(Lnet/minecraft/client/gui/DrawContext;II)V",
        at = @At(value = "STORE", target = "Lnet/minecraft/entity/LivingEntity;getStatusEffects()Ljava/util/Collection;"),
        ordinal = 0
    )
    private Collection<StatusEffectInstance> bhs$filterInventoryEffects(Collection<StatusEffectInstance> original, DrawContext context, int mouseX, int mouseY) {
        // Get the player entity from MinecraftClient
        var player = MinecraftClient.getInstance().player;
        if (player == null) {
            return original; // Return unchanged if player is null (shouldn't happen in inventory screen)
        }

        boolean heat = player.getStatusEffect(ModStatusEffects.HEAT_STROKE_INIT) != null
                || player.getStatusEffect(ModStatusEffects.HEAT_STROKE_MOD) != null
                || player.getStatusEffect(ModStatusEffects.HEAT_STROKE_SERV) != null;
        boolean cold = player.getStatusEffect(ModStatusEffects.HYPO_MILD) != null
                || player.getStatusEffect(ModStatusEffects.HYPO_MOD) != null
                || player.getStatusEffect(ModStatusEffects.HYPO_SERV) != null;

        ArrayList<StatusEffectInstance> filtered = new ArrayList<>(original.size());
        for (StatusEffectInstance inst : original) {
            // Respect shouldShowIcon() for hidden debuffs
            if (!inst.shouldShowIcon()) continue;

            StatusEffect type = inst.getEffectType();

            // Hide vanilla negative effects when heat or cold conditions are active
            if ((heat || cold) &&
                (type == StatusEffects.SLOWNESS || type == StatusEffects.WEAKNESS || type == StatusEffects.MINING_FATIGUE)) {
                continue;
            }
            filtered.add(inst);
        }
        return filtered;
    }
}