package xyz.srgnis.bodyhealthsystem.registry;

import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.util.Identifier;
import net.minecraft.registry.Registry;
import net.minecraft.registry.Registries;
import xyz.srgnis.bodyhealthsystem.BHSMain;
import xyz.srgnis.bodyhealthsystem.effects.MorphineStatusEffect;
import xyz.srgnis.bodyhealthsystem.effects.AdrenalineStatusEffect;
import xyz.srgnis.bodyhealthsystem.effects.DressingStatusEffect;
import xyz.srgnis.bodyhealthsystem.effects.BleedingStatusEffect;
import xyz.srgnis.bodyhealthsystem.effects.ConditionStatusEffect;
import xyz.srgnis.bodyhealthsystem.effects.BrokenBoneStatusEffect;

public class ModStatusEffects {
    public static final StatusEffect MORPHINE_EFFECT = new MorphineStatusEffect();
    public static final StatusEffect ADRENALINE_EFFECT = new AdrenalineStatusEffect();
    public static final StatusEffect DRESSING_EFFECT = new DressingStatusEffect();
    public static final StatusEffect BLEEDING_EFFECT = new BleedingStatusEffect();
    public static final StatusEffect BROKEN_BONE = new BrokenBoneStatusEffect();

    // Temperature condition icons (IDs are chosen to match texture file names under textures/mob_effect/)
    public static final StatusEffect HEAT_STROKE_INIT = new ConditionStatusEffect();      // heatstrokeinit.png
    public static final StatusEffect HEAT_STROKE_MOD = new ConditionStatusEffect();       // heatstrokemod.png
    public static final StatusEffect HEAT_STROKE_SERV = new ConditionStatusEffect();      // heatstrokeserv.png
    public static final StatusEffect HYPO_MILD = new ConditionStatusEffect();             // mildhypo.png
    public static final StatusEffect HYPO_MOD = new ConditionStatusEffect();              // modhypo.png
    public static final StatusEffect HYPO_SERV = new ConditionStatusEffect();             // servhypo.png

    public static void registerStatusEffects(){
        Registry.register(Registries.STATUS_EFFECT, new Identifier(BHSMain.MOD_ID, "morphine_effect"), MORPHINE_EFFECT);
        Registry.register(Registries.STATUS_EFFECT, new Identifier(BHSMain.MOD_ID, "adrenaline_effect"), ADRENALINE_EFFECT);
        Registry.register(Registries.STATUS_EFFECT, new Identifier(BHSMain.MOD_ID, "dressing_effect"), DRESSING_EFFECT);
        Registry.register(Registries.STATUS_EFFECT, new Identifier(BHSMain.MOD_ID, "bleeding_effect"), BLEEDING_EFFECT);
        Registry.register(Registries.STATUS_EFFECT, new Identifier(BHSMain.MOD_ID, "broken_bone"), BROKEN_BONE);

        Registry.register(Registries.STATUS_EFFECT, new Identifier(BHSMain.MOD_ID, "heatstrokeinit"), HEAT_STROKE_INIT);
        Registry.register(Registries.STATUS_EFFECT, new Identifier(BHSMain.MOD_ID, "heatstrokemod"), HEAT_STROKE_MOD);
        Registry.register(Registries.STATUS_EFFECT, new Identifier(BHSMain.MOD_ID, "heatstrokeserv"), HEAT_STROKE_SERV);
        Registry.register(Registries.STATUS_EFFECT, new Identifier(BHSMain.MOD_ID, "mildhypo"), HYPO_MILD);
        Registry.register(Registries.STATUS_EFFECT, new Identifier(BHSMain.MOD_ID, "modhypo"), HYPO_MOD);
        Registry.register(Registries.STATUS_EFFECT, new Identifier(BHSMain.MOD_ID, "servhypo"), HYPO_SERV);
    }
}
