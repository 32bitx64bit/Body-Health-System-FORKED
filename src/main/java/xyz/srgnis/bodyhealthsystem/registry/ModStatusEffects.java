package xyz.srgnis.bodyhealthsystem.registry;

import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.util.Identifier;
import net.minecraft.registry.Registry;
import net.minecraft.registry.Registries;
import xyz.srgnis.bodyhealthsystem.BHSMain;
import xyz.srgnis.bodyhealthsystem.effects.MorphineStatusEffect;
import xyz.srgnis.bodyhealthsystem.effects.AdrenalineStatusEffect;
import xyz.srgnis.bodyhealthsystem.effects.DressingStatusEffect;

public class ModStatusEffects {
    public static final StatusEffect MORPHINE_EFFECT = new MorphineStatusEffect();
    public static final StatusEffect ADRENALINE_EFFECT = new AdrenalineStatusEffect();
    public static final StatusEffect DRESSING_EFFECT = new DressingStatusEffect();

    public static void registerStatusEffects(){
        Registry.register(Registries.STATUS_EFFECT, new Identifier(BHSMain.MOD_ID, "morphine_effect"), MORPHINE_EFFECT);
        Registry.register(Registries.STATUS_EFFECT, new Identifier(BHSMain.MOD_ID, "adrenaline_effect"), ADRENALINE_EFFECT);
        Registry.register(Registries.STATUS_EFFECT, new Identifier(BHSMain.MOD_ID, "dressing_effect"), DRESSING_EFFECT);
    }
}
