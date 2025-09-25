package xyz.srgnis.bodyhealthsystem.registry;

import net.fabricmc.fabric.api.screenhandler.v1.ScreenHandlerRegistry;
import net.minecraft.inventory.CraftingResultInventory;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.AnvilScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerContext;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.slot.Slot;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ItemScatterer;
import xyz.srgnis.bodyhealthsystem.items.CoolingGelItem;
import xyz.srgnis.bodyhealthsystem.items.HeatingGelItem;
import xyz.srgnis.bodyhealthsystem.items.ResistanceUtil;

// Registers a hook to apply gels via anvil combine: [Armor] + [Cooling/Heating Gel] -> armor with +1 cold/heat tier
public final class AnvilHandlers {
    private AnvilHandlers() {}

    // Fabric doesn't provide a direct high-level hook for anvil result crafting, so we rely on ScreenHandler slots logic.
    // We'll create a helper that can be invoked from a mixin if needed. For now, we provide a static method used by a mixin.
    public static ItemStack computeResult(ItemStack left, ItemStack right) {
        if (left == null || right == null) return ItemStack.EMPTY;
        if (!(left.getItem() instanceof ArmorItem)) return ItemStack.EMPTY;
        ItemStack armorCopy = left.copy();

        if (right.getItem() instanceof CoolingGelItem) {
            boolean ok = ResistanceUtil.applyColdTier(armorCopy, 1);
            return ok ? armorCopy : ItemStack.EMPTY;
        }
        if (right.getItem() instanceof HeatingGelItem) {
            boolean ok = ResistanceUtil.applyHeatTier(armorCopy, 1);
            return ok ? armorCopy : ItemStack.EMPTY;
        }
        return ItemStack.EMPTY;
    }
}
