package xyz.srgnis.bodyhealthsystem.items;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;

import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ItemStack;

import net.minecraft.world.World;



import java.util.UUID;

public class StrawHatItem extends ArmorItem {
    // Stable UUID for attribute modifier so stacking rules work consistently
    private static final UUID ARMOR_MODIFIER_ID = UUID.fromString("a7c7f04a-4bfe-4bb3-9c71-6d8c5a7d2c9e");

    // Desired stats
    private static final double ARMOR_POINTS = 1.5; // armor value
    private static final int DEFAULT_HEAT_TIER = 4; // +8°C heat comfort

    private final Multimap<EntityAttribute, EntityAttributeModifier> attributeModifiers;

    public StrawHatItem(Settings settings) {
        super(StrawHatMaterial.INSTANCE, Type.HELMET, settings);

        // Build attribute modifiers with fractional armor
        ImmutableMultimap.Builder<EntityAttribute, EntityAttributeModifier> builder = ImmutableMultimap.builder();
        builder.put(EntityAttributes.GENERIC_ARMOR,
                new EntityAttributeModifier(ARMOR_MODIFIER_ID, "Straw Hat armor", ARMOR_POINTS, EntityAttributeModifier.Operation.ADDITION));
        this.attributeModifiers = builder.build();
    }

    @Override
    public Multimap<EntityAttribute, EntityAttributeModifier> getAttributeModifiers(EquipmentSlot slot) {
        if (slot == EquipmentSlot.HEAD) {
            return attributeModifiers;
        }
        return super.getAttributeModifiers(slot);
    }

    @Override
    public ItemStack getDefaultStack() {
        ItemStack stack = super.getDefaultStack();
        int[] tiers = ResistanceUtil.readTiers(stack);
        if (tiers[0] <= 0) {
            ResistanceUtil.writeTiers(stack, DEFAULT_HEAT_TIER, tiers[1]);
        }
        return stack;
    }

    @Override
    public void onCraft(ItemStack stack, World world, PlayerEntity player) {
        super.onCraft(stack, world, player);
        int[] tiers = ResistanceUtil.readTiers(stack);
        if (tiers[0] <= 0) {
            ResistanceUtil.writeTiers(stack, DEFAULT_HEAT_TIER, tiers[1]);
        }
    }

}
