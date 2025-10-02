package xyz.srgnis.bodyhealthsystem.effects;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;

public class BrokenBoneStatusEffect extends StatusEffect {
    public BrokenBoneStatusEffect() {
        super(StatusEffectCategory.HARMFUL, 0xC0C0C0);
    }

    @Override
    public boolean canApplyUpdateEffect(int duration, int amplifier) {
        // Apply every tick to refresh hidden debuffs
        return true;
    }

    @Override
    public void applyUpdateEffect(LivingEntity entity, int amplifier) {
        // Amplifier is stacks-1; cap at 3 visually (III)
        int stacks = Math.min(4, amplifier + 1); // allow up to 4 stacks for safety; will clamp in producer
        // Apply Slowness and Mining Fatigue at level = min(3, stacks)
        int level = Math.min(3, stacks) - 1; // potion level index (0-based)
        if (level >= 0) {
            entity.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 40, level, false, false, false));
            entity.addStatusEffect(new StatusEffectInstance(StatusEffects.MINING_FATIGUE, 40, level, false, false, false));
        }
    }
}
