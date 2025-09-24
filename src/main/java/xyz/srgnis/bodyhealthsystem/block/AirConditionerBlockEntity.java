package xyz.srgnis.bodyhealthsystem.block;

import gavinx.temperatureapi.api.BlockThermalAPI;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventories;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import xyz.srgnis.bodyhealthsystem.registry.ModBlocks;

public class AirConditionerBlockEntity extends BlockEntity implements net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory, net.minecraft.inventory.Inventory {
    // Single-slot inventory for coolant (ice variants)
    private final DefaultedList<ItemStack> items = DefaultedList.ofSize(1, ItemStack.EMPTY);

    private int burnTime = 0; // ticks remaining for current coolant item
    private int burnTimeTotal = 0; // total ticks for current item (for GUI progress)

    public static final int SLOT_COOLANT = 0;

    public AirConditionerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.AIR_CONDITIONER_BE, pos, state);
    }

    public int getBurnTime() { return burnTime; }
    public int getBurnTimeTotal() { return burnTimeTotal; }

    public int getComparatorValue() {
        return Math.min(15, (int)Math.round((burnTimeTotal == 0 ? 0.0 : (double)burnTime / burnTimeTotal) * 15.0));
    }

    public static void serverTick(World world, BlockPos pos, BlockState state, AirConditionerBlockEntity be) {
        if (world.isClient) return;

        // Handle fuel consumption when active and has coolant
        boolean wasBurning = be.burnTime > 0;

        if (be.burnTime <= 0) {
            // try consume new coolant
            ItemStack stack = be.items.get(SLOT_COOLANT);
            if (!stack.isEmpty()) {
                int burn = getCoolantBurnTime(stack.getItem());
                if (burn > 0) {
                    stack.decrement(1);
                    be.burnTime = burn;
                    be.burnTimeTotal = burn;
                    be.markDirty();
                }
            }
        }
        if (be.burnTime > 0) {
            be.burnTime--; // consume
        }

        boolean isBurning = be.burnTime > 0;
        if (wasBurning != isBurning) {
            // Update block LIT property like furnace
            if (state.contains(net.minecraft.state.property.Properties.LIT)) {
                world.setBlockState(pos, state.with(net.minecraft.state.property.Properties.LIT, isBurning), 3);
            }
        }
    }

    private static int getCoolantBurnTime(Item item) {
        if (item == Items.ICE) return 200; // 10s
        if (item == Items.PACKED_ICE) return 400; // 20s
        if (item == Items.BLUE_ICE) return 1000; // 50s
        if (item == Items.SNOWBALL) return 40; // 2s
        if (item == Items.SNOW_BLOCK) return 300; // 15s
        return 0;
    }

    public DefaultedList<ItemStack> getItems() { return items; }

    // Persistence
    @Override
    protected void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        nbt.putInt("Burn", burnTime);
        nbt.putInt("BurnTotal", burnTimeTotal);
        Inventories.writeNbt(nbt, items);
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        burnTime = nbt.getInt("Burn");
        burnTimeTotal = nbt.getInt("BurnTotal");
        Inventories.readNbt(nbt, items);
    }

    public void onBroken(World world, BlockPos pos) {
        // drop inventory
        ItemStack stack = items.get(SLOT_COOLANT);
        if (!stack.isEmpty()) {
            net.minecraft.util.ItemScatterer.spawn(world, pos, DefaultedList.ofSize(1, stack));
        }
    }

    // GUI + inventory
    private static final Text TITLE = Text.translatable("block.bodyhealthsystem.air_conditioner");

    private final PropertyDelegate properties = new PropertyDelegate() {
        @Override public int get(int index) { return switch (index) { case 0 -> burnTime; case 1 -> burnTimeTotal; default -> 0; }; }
        @Override public void set(int index, int value) { if (index == 0) burnTime = value; else if (index == 1) burnTimeTotal = value; }
        @Override public int size() { return 2; }
    };

    @Override
    public Text getDisplayName() { return TITLE; }

    @Override
    public void writeScreenOpeningData(ServerPlayerEntity player, PacketByteBuf buf) {
        buf.writeBlockPos(this.pos);
    }

    @Nullable
    @Override
    public ScreenHandler createMenu(int syncId, net.minecraft.entity.player.PlayerInventory inv, PlayerEntity player) {
        return new xyz.srgnis.bodyhealthsystem.client.screen.AirConditionerScreenHandler(syncId, inv, this, properties);
    }

    // Inventory impl (1 slot)
    @Override public int size() { return items.size(); }
    @Override public boolean isEmpty() { return items.get(0).isEmpty(); }
    @Override public ItemStack getStack(int slot) { return items.get(slot); }
    @Override public ItemStack removeStack(int slot, int amount) { ItemStack res = Inventories.splitStack(items, slot, amount); if (!res.isEmpty()) markDirty(); return res; }
    @Override public ItemStack removeStack(int slot) { ItemStack res = Inventories.removeStack(items, slot); if (!res.isEmpty()) markDirty(); return res; }
    @Override public void setStack(int slot, ItemStack stack) { items.set(slot, stack); if (stack.getCount() > stack.getMaxCount()) stack.setCount(stack.getMaxCount()); markDirty(); }
    @Override public boolean canPlayerUse(PlayerEntity player) { return player.squaredDistanceTo(this.pos.getX()+0.5, this.pos.getY()+0.5, this.pos.getZ()+0.5) <= 64.0; }
    @Override public void clear() { items.set(0, ItemStack.EMPTY); }

    public PropertyDelegate getProperties() { return properties; }

    public static boolean isValidCoolant(ItemStack stack) { return getCoolantBurnTime(stack.getItem()) > 0; }
}

