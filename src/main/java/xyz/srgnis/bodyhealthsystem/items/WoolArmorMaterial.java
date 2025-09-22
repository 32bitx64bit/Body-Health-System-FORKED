package xyz.srgnis.bodyhealthsystem.items;

import net.minecraft.item.ArmorItem;
import net.minecraft.item.ArmorMaterial;
import net.minecraft.item.ArmorMaterials;
import net.minecraft.recipe.Ingredient;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.sound.SoundEvent;

public enum WoolArmorMaterial implements ArmorMaterial {
    INSTANCE;

    private static final ArmorMaterial BASE = ArmorMaterials.LEATHER;

    @Override
    public int getDurability(ArmorItem.Type type) {
        return BASE.getDurability(type);
    }

    @Override
    public int getProtection(ArmorItem.Type type) {
        return BASE.getProtection(type);
    }

    @Override
    public int getEnchantability() {
        return BASE.getEnchantability();
    }

    @Override
    public SoundEvent getEquipSound() {
        return BASE.getEquipSound();
    }

    @Override
    public Ingredient getRepairIngredient() {
        // Any wool color via the WOOL item tag
        return Ingredient.fromTag(ItemTags.WOOL);
    }

    @Override
    public String getName() {
        // Namespaced so textures resolve to assets/bodyhealthsystem/textures/models/armor/wool_layer_[1|2].png
        return "bodyhealthsystem:wool";
    }

    @Override
    public float getToughness() {
        return BASE.getToughness();
    }

    @Override
    public float getKnockbackResistance() {
        return BASE.getKnockbackResistance();
    }
}
