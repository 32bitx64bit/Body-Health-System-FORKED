package xyz.srgnis.bodyhealthsystem.block;

import gavinx.temperatureapi.api.TemperatureAPI;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventories;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import xyz.srgnis.bodyhealthsystem.registry.ModBlocks;

public class SpaceHeaterBlockEntity extends BlockEntity implements net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory, net.minecraft.inventory.Inventory {
    private final DefaultedList<ItemStack> items = DefaultedList.ofSize(1, ItemStack.EMPTY);

    private int burnTime = 0;
    private int burnTimeTotal = 0;
    private boolean regulate = false;
    private double consumeDebt = 0.0;

    public static final int SLOT_FUEL = 0;

    public SpaceHeaterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.SPACE_HEATER_BE, pos, state);
    }

    public int getBurnTime() { return burnTime; }
    public int getBurnTimeTotal() { return burnTimeTotal; }
    public boolean isRegulating() { return regulate; }
    public void setRegulating(boolean v) { this.regulate = v; markDirty(); }

    public int getComparatorValue() {
        return Math.min(15, (int)Math.round((burnTimeTotal == 0 ? 0.0 : (double)burnTime / burnTimeTotal) * 15.0));
    }

    public static void serverTick(World world, BlockPos pos, BlockState state, SpaceHeaterBlockEntity be) {
        if (world.isClient) return;

        // Keep blockstate REGULATE in sync with BE value so client providers can read it
        if (state.getBlock() instanceof xyz.srgnis.bodyhealthsystem.block.SpaceHeaterBlock) {
            Boolean has = state.contains(xyz.srgnis.bodyhealthsystem.block.SpaceHeaterBlock.REGULATE);
            if (has && state.get(xyz.srgnis.bodyhealthsystem.block.SpaceHeaterBlock.REGULATE) != be.regulate) {
                world.setBlockState(pos, state.with(xyz.srgnis.bodyhealthsystem.block.SpaceHeaterBlock.REGULATE, be.regulate), 3);
                state = world.getBlockState(pos);
            }
        }

        boolean wantsHeating;
        double envC = Double.NaN;
        if (!be.regulate) {
            wantsHeating = true; // constant heater
        } else {
            envC = TemperatureAPI.getEnvironmentCelsius(world, pos);
            wantsHeating = !Double.isNaN(envC) && envC < 22.0;
        }

        boolean wasLit = state.contains(net.minecraft.state.property.Properties.LIT) && state.get(net.minecraft.state.property.Properties.LIT);

        if (be.burnTime <= 0 && wantsHeating) {
            ItemStack stack = be.items.get(SLOT_FUEL);
            if (!stack.isEmpty()) {
                int burn = getFuelBurnTime(stack.getItem());
                if (burn > 0) {
                    stack.decrement(1);
                    be.burnTime = burn;
                    be.burnTimeTotal = burn;
                    be.markDirty();
                }
            }
        }
        if (be.burnTime > 0 && wantsHeating) {
            int consume = 1;
            if (be.regulate && !Double.isNaN(envC)) {
                double deficit = Math.max(0.0, 22.0 - envC);
                if (deficit > 0.0) {
                    double rate = 1.0 + 0.05 * deficit; // further from 22 -> slightly faster fuel
                    double total = rate + be.consumeDebt;
                    int units = (int)Math.floor(total);
                    be.consumeDebt = total - units;
                    consume = Math.max(1, units);
                }
            }
            be.burnTime -= consume;
            if (be.burnTime < 0) be.burnTime = 0;
            // Instant refill to prevent 1-tick flicker when fuel expires
            if (be.burnTime <= 0 && wantsHeating) {
                ItemStack stack2 = be.items.get(SLOT_FUEL);
                if (!stack2.isEmpty()) {
                    int burn2 = getFuelBurnTime(stack2.getItem());
                    if (burn2 > 0) {
                        stack2.decrement(1);
                        be.burnTime = burn2;
                        be.burnTimeTotal = burn2;
                        be.markDirty();
                    }
                }
            }
        }

        boolean isBurning = be.burnTime > 0 && wantsHeating;
        if (wasLit != isBurning) {
            if (state.contains(net.minecraft.state.property.Properties.LIT)) {
                world.setBlockState(pos, state.with(net.minecraft.state.property.Properties.LIT, isBurning), 3);
            }
        }
    }

    private static int getFuelBurnTime(Item item) {
        if (item == Items.CHARCOAL) return 1600; // same as furnace
        if (item == Items.COAL) return 1600;
        if (item == Items.COAL_BLOCK) return 16000;
        return 0;
    }

    public DefaultedList<ItemStack> getItems() { return items; }

    @Override
    protected void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        nbt.putInt("Burn", burnTime);
        nbt.putInt("BurnTotal", burnTimeTotal);
        nbt.putBoolean("Regulate", regulate);
        Inventories.writeNbt(nbt, items);
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        burnTime = nbt.getInt("Burn");
        burnTimeTotal = nbt.getInt("BurnTotal");
        regulate = nbt.getBoolean("Regulate");
        Inventories.readNbt(nbt, items);
    }

    public void onBroken(World world, BlockPos pos) {
        ItemStack stack = items.get(SLOT_FUEL);
        if (!stack.isEmpty()) {
            net.minecraft.util.ItemScatterer.spawn(world, pos, DefaultedList.ofSize(1, stack));
        }
    }

    private static final Text TITLE = Text.translatable("block.bodyhealthsystem.space_heater");

    private final PropertyDelegate properties = new PropertyDelegate() {
        @Override public int get(int index) { return switch (index) { case 0 -> burnTime; case 1 -> burnTimeTotal; case 2 -> regulate ? 1 : 0; default -> 0; }; }
        @Override public void set(int index, int value) { if (index == 0) burnTime = value; else if (index == 1) burnTimeTotal = value; else if (index == 2) regulate = (value != 0); }
        @Override public int size() { return 3; }
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
        return new xyz.srgnis.bodyhealthsystem.client.screen.SpaceHeaterScreenHandler(syncId, inv, this, properties, this.pos);
    }

    @Override public int size() { return items.size(); }
    @Override public boolean isEmpty() { return items.get(0).isEmpty(); }
    @Override public ItemStack getStack(int slot) { return items.get(slot); }
    @Override public ItemStack removeStack(int slot, int amount) { ItemStack res = Inventories.splitStack(items, slot, amount); if (!res.isEmpty()) markDirty(); return res; }
    @Override public ItemStack removeStack(int slot) { ItemStack res = Inventories.removeStack(items, slot); if (!res.isEmpty()) markDirty(); return res; }
    @Override public void setStack(int slot, ItemStack stack) { items.set(slot, stack); if (stack.getCount() > stack.getMaxCount()) stack.setCount(stack.getMaxCount()); markDirty(); }
    @Override public boolean canPlayerUse(PlayerEntity player) { return player.squaredDistanceTo(this.pos.getX()+0.5, this.pos.getY()+0.5, this.pos.getZ()+0.5) <= 64.0; }
    @Override public void clear() { items.set(0, ItemStack.EMPTY); }

    public PropertyDelegate getProperties() { return properties; }

    public static boolean isValidFuel(ItemStack stack) { return getFuelBurnTime(stack.getItem()) > 0; }
}
