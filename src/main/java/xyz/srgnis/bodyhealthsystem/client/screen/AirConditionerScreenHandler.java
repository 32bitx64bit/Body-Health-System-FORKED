package xyz.srgnis.bodyhealthsystem.client.screen;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ArrayPropertyDelegate;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import xyz.srgnis.bodyhealthsystem.block.AirConditionerBlockEntity;
import xyz.srgnis.bodyhealthsystem.registry.ScreenHandlers;

public class AirConditionerScreenHandler extends ScreenHandler {
    private final Inventory inventory;
    private final PropertyDelegate properties;

    public AirConditionerScreenHandler(int syncId, PlayerInventory playerInventory, Inventory inventory, PropertyDelegate properties) {
        super(ScreenHandlers.AIR_CONDITIONER_SCREEN_HANDLER, syncId);
        this.inventory = inventory;
        this.properties = properties;
        this.addProperties(properties);

        // Coolant slot (input): shift an additional 1px left (now 59,36)
        this.addSlot(new Slot(inventory, AirConditionerBlockEntity.SLOT_COOLANT, 62, 36) {
            @Override
            public boolean canInsert(ItemStack stack) {
                return AirConditionerBlockEntity.isValidCoolant(stack);
            }
        });

        // Player inventory (vanilla layout)
        int m;
        int l;
        for (m = 0; m < 3; ++m) {
            for (l = 0; l < 9; ++l) {
                this.addSlot(new Slot(playerInventory, l + m * 9 + 9, 8 + l * 18, 84 + m * 18));
            }
        }
        for (m = 0; m < 9; ++m) {
            this.addSlot(new Slot(playerInventory, m, 8 + m * 18, 142));
        }
    }

    public int getBurnTime() { return properties.get(0); }
    public int getBurnTimeTotal() { return properties.get(1); }

    @Override
    public ItemStack quickMove(PlayerEntity player, int index) {
        ItemStack itemStack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasStack()) {
            ItemStack stackInSlot = slot.getStack();
            itemStack = stackInSlot.copy();
            if (index == 0) {
                // shift from AC slot -> player inventory
                if (!this.insertItem(stackInSlot, 1, 37, true)) return ItemStack.EMPTY;
            } else {
                // player -> AC coolant slot
                if (AirConditionerBlockEntity.isValidCoolant(stackInSlot)) {
                    if (!this.insertItem(stackInSlot, 0, 1, false)) return ItemStack.EMPTY;
                } else {
                    return ItemStack.EMPTY;
                }
            }
            if (stackInSlot.isEmpty()) slot.setStack(ItemStack.EMPTY);
            else slot.markDirty();
        }
        return itemStack;
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return this.inventory.canPlayerUse(player);
    }
}
