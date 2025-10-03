package xyz.srgnis.bodyhealthsystem.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.AbstractInventoryScreen;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import xyz.srgnis.bodyhealthsystem.registry.ModStatusEffects;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

@Mixin(AbstractInventoryScreen.class)
public abstract class InventoryStatusEffectFilterMixin {

    @ModifyVariable(
        method = "drawStatusEffects(Lnet/minecraft/client/gui/DrawContext;II)V",
        at = @At(value = "STORE", target = "Lnet/minecraft/entity/LivingEntity;getStatusEffects()Ljava/util/Collection;"),
        ordinal = 0
    )
    private Collection<StatusEffectInstance> bhs$filterInventoryEffects(Collection<StatusEffectInstance> original, DrawContext context, int mouseX, int mouseY) {
        var player = MinecraftClient.getInstance().player;
        if (player == null) return original;
        boolean heat = player.getStatusEffect(ModStatusEffects.HEAT_STROKE_INIT) != null
                || player.getStatusEffect(ModStatusEffects.HEAT_STROKE_MOD) != null
                || player.getStatusEffect(ModStatusEffects.HEAT_STROKE_SERV) != null;
        boolean cold = player.getStatusEffect(ModStatusEffects.HYPO_MILD) != null
                || player.getStatusEffect(ModStatusEffects.HYPO_MOD) != null
                || player.getStatusEffect(ModStatusEffects.HYPO_SERV) != null;

        // Build a stable, consistently sorted list to prevent rapid position switching
        List<StatusEffectInstance> list = new ArrayList<>(original);
        list.sort(Comparator.comparingInt((StatusEffectInstance e) -> e.getEffectType().getCategory().ordinal())
                .thenComparing(e -> {
                    Identifier id = Registries.STATUS_EFFECT.getId(e.getEffectType());
                    return id == null ? "zzzz" : id.toString();
                })
                .thenComparing((StatusEffectInstance e) -> -e.getAmplifier()));

        if (!(heat || cold)) {
            // Respect shouldShowIcon even when not filtering
            List<StatusEffectInstance> visible = new ArrayList<>(list.size());
            for (StatusEffectInstance inst : list) {
                if (inst != null && inst.shouldShowIcon()) visible.add(inst);
            }
            return visible;
        }

        ArrayList<StatusEffectInstance> filtered = new ArrayList<>(list.size());
        for (StatusEffectInstance inst : list) {
            if (inst == null) continue;
            if (!inst.shouldShowIcon()) continue;
            StatusEffect type = inst.getEffectType();
            if (type == StatusEffects.SLOWNESS || type == StatusEffects.WEAKNESS || type == StatusEffects.MINING_FATIGUE) {
                continue;
            }
            filtered.add(inst);
        }
        return filtered;
    }
}