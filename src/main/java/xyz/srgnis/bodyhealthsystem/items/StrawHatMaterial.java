package xyz.srgnis.bodyhealthsystem.items;

import net.minecraft.item.ArmorItem;
import net.minecraft.item.ArmorMaterial;
import net.minecraft.recipe.Ingredient;
import net.minecraft.item.Items;
import net.minecraft.sound.SoundEvent;
import net.minecraft.item.ArmorMaterials;

/**
 * Custom armor material for the Straw Hat.
 * We return 0 base protection so BHS damage pipeline does not double-count armor,
 * and we will provide the actual 1.5 armor value via attribute modifiers on the item itself.
 */
public enum StrawHatMaterial implements ArmorMaterial {
    INSTANCE;

    private static final ArmorMaterial BASE = ArmorMaterials.LEATHER;

    @Override
    public int getDurability(ArmorItem.Type type) {
        return BASE.getDurability(type);
    }

    @Override
    public int getProtection(ArmorItem.Type type) {

        return 0;
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
        return Ingredient.ofItems(Items.WHEAT);
    }

    @Override
    public String getName() {
        return "bodyhealthsystem:straw_hat";
    }

    @Override
    public float getToughness() {
        return 0.0f;
    }

    @Override
    public float getKnockbackResistance() {
        return 0.0f;
    }
}
