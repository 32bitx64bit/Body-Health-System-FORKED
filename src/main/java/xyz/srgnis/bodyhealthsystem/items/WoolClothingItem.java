package xyz.srgnis.bodyhealthsystem.items;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ArmorMaterials;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class WoolClothingItem extends ArmorItem {
    public WoolClothingItem(Type type, Settings settings) {
        super(WoolArmorMaterial.INSTANCE, type, settings);
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        super.appendTooltip(stack, world, tooltip, context);
        // Show Cold/Heat Resistance dynamically using a label + tier (e.g., "Cold Resistance 3")
        tooltip.add(
                Text.translatable(
                        "tooltip.bodyhealthsystem.temp_resistance_label",
                        Text.translatable("label.bodyhealthsystem.cold"),
                        3
                ).styled(style -> style.withColor(0x006EFC))
        );
    }
}
