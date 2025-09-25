package xyz.srgnis.bodyhealthsystem.items;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

public class WoolClothingItem extends ArmorItem {
    private static final int DEFAULT_COLD_TIER = 3; // +6°C cold comfort

    public WoolClothingItem(Type type, Settings settings) {
        super(WoolArmorMaterial.INSTANCE, type, settings);
    }

    @Override
    public ItemStack getDefaultStack() {
        ItemStack stack = super.getDefaultStack();
        // Ensure creative/default stacks have default cold resistance
        int[] tiers = ResistanceUtil.readTiers(stack);
        if (tiers[1] <= 0) {
            ResistanceUtil.writeTiers(stack, tiers[0], DEFAULT_COLD_TIER);
        }
        return stack;
    }

    @Override
    public void onCraft(ItemStack stack, World world, PlayerEntity player) {
        super.onCraft(stack, world, player);
        // Ensure crafted stacks have default cold resistance if absent
        int[] tiers = ResistanceUtil.readTiers(stack);
        if (tiers[1] <= 0) {
            ResistanceUtil.writeTiers(stack, tiers[0], DEFAULT_COLD_TIER);
        }
    }
}
