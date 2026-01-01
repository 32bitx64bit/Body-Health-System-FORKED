package xyz.srgnis.bodyhealthsystem.effects;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;

public class HerbalPoulticeStatusEffect extends StatusEffect {
    public HerbalPoulticeStatusEffect() {
        super(StatusEffectCategory.BENEFICIAL, 0x4FA34F);
    }

    @Override
    public boolean canApplyUpdateEffect(int duration, int amplifier) {
        return false; // driven by PlayerTickMixin (bleed-cycle hook)
    }

    @Override
    public void applyUpdateEffect(LivingEntity entity, int amplifier) {
        // no periodic effect
    }
}
