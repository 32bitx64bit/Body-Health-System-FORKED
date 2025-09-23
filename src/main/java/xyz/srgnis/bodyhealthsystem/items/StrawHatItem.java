package xyz.srgnis.bodyhealthsystem.items;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

public class StrawHatItem extends ArmorItem {
    // Stable UUID for attribute modifier so stacking rules work consistently
    private static final UUID ARMOR_MODIFIER_ID = UUID.fromString("a7c7f04a-4bfe-4bb3-9c71-6d8c5a7d2c9e");

    // Desired stats
    private static final double ARMOR_POINTS = 1.5; // armor value
    private static final int HEAT_RESISTANCE_TIER = 3; // for tooltip only in this project

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
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        super.appendTooltip(stack, world, tooltip, context);
        // Reuse existing translatable strings pattern used by wool clothing
        tooltip.add(Text.translatable(
                "tooltip.bodyhealthsystem.temp_resistance_label",
                Text.translatable("label.bodyhealthsystem.heat"),
                HEAT_RESISTANCE_TIER
        ));
    }
}
