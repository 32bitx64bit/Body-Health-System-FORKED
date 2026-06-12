package xyz.srgnis.bodyhealthsystem.effects;

import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;

public class BrokenBoneStatusEffect extends StatusEffect {
    public BrokenBoneStatusEffect() {
        super(StatusEffectCategory.HARMFUL, 0xC0C0C0);
    }
}
