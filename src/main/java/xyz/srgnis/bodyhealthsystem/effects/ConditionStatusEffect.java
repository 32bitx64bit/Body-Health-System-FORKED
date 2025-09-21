package xyz.srgnis.bodyhealthsystem.effects;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;

public class ConditionStatusEffect extends StatusEffect {
    public ConditionStatusEffect() {
        super(StatusEffectCategory.HARMFUL, 0xFFFFFF);
    }

    @Override
    public boolean canApplyUpdateEffect(int duration, int amplifier) {
        return false;
    }

    @Override
    public void applyUpdateEffect(LivingEntity entity, int amplifier) {
        // no-op: informational/icon-only, logic handled elsewhere
    }
}
