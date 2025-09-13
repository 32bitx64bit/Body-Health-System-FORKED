package xyz.srgnis.bodyhealthsystem.effects;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;

public class DressingStatusEffect extends StatusEffect {
    public DressingStatusEffect() {
        super(StatusEffectCategory.BENEFICIAL, 0x4CAF50); // green tint
    }

    @Override
    public boolean canApplyUpdateEffect(int duration, int amplifier) {
        return false; // purely a marker effect
    }

    @Override
    public void applyUpdateEffect(LivingEntity entity, int amplifier) {
        // no periodic effect
    }
}