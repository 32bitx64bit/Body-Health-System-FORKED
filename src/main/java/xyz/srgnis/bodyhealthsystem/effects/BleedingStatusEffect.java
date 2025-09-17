package xyz.srgnis.bodyhealthsystem.effects;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;

public class BleedingStatusEffect extends StatusEffect {
    public BleedingStatusEffect() {
        super(StatusEffectCategory.HARMFUL, 0xB30000); // deep red
    }

    @Override
    public boolean canApplyUpdateEffect(int duration, int amplifier) {
        return false; // purely cosmetic marker
    }

    @Override
    public void applyUpdateEffect(LivingEntity entity, int amplifier) {
        // no periodic effect
    }
}
