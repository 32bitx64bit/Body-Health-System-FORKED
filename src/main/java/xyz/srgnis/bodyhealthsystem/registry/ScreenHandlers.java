package xyz.srgnis.bodyhealthsystem.registry;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.registry.Registry;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import xyz.srgnis.bodyhealthsystem.client.screen.HealScreenHandler;
import xyz.srgnis.bodyhealthsystem.client.screen.AirConditionerScreenHandler;
import xyz.srgnis.bodyhealthsystem.client.screen.BodyOperationsScreenHandler;

import static xyz.srgnis.bodyhealthsystem.BHSMain.id;

public class ScreenHandlers {

    // Legacy heal screen (kept for compatibility)
    public static final net.minecraft.screen.ScreenHandlerType<HealScreenHandler> HEAL_SCREEN_HANDLER = new ExtendedScreenHandlerType<>(HealScreenHandler::new);

    // Unified Body Operations screen
    public static final net.minecraft.screen.ScreenHandlerType<BodyOperationsScreenHandler> BODY_OPS_SCREEN_HANDLER = new ExtendedScreenHandlerType<>(BodyOperationsScreenHandler::new);

    // Air Conditioner GUI
    public static final net.minecraft.screen.ScreenHandlerType<AirConditionerScreenHandler> AIR_CONDITIONER_SCREEN_HANDLER = new ExtendedScreenHandlerType<>((syncId, inv, buf) -> {
        BlockPos pos = buf.readBlockPos();
        return new AirConditionerScreenHandler(syncId, inv, new net.minecraft.inventory.SimpleInventory(1), new net.minecraft.screen.ArrayPropertyDelegate(3), pos);
    });

    // Space Heater GUI
    public static final net.minecraft.screen.ScreenHandlerType<xyz.srgnis.bodyhealthsystem.client.screen.SpaceHeaterScreenHandler> SPACE_HEATER_SCREEN_HANDLER = new ExtendedScreenHandlerType<>((syncId, inv, buf) -> {
        BlockPos pos = buf.readBlockPos();
        return new xyz.srgnis.bodyhealthsystem.client.screen.SpaceHeaterScreenHandler(syncId, inv, new net.minecraft.inventory.SimpleInventory(1), new net.minecraft.screen.ArrayPropertyDelegate(3), pos);
    });

    public static void registerScreenHandlers() {
        // Legacy id
        Registry.register(Registries.SCREEN_HANDLER, id("medkit"), HEAL_SCREEN_HANDLER);
        // New unified GUI id
        Registry.register(Registries.SCREEN_HANDLER, id("body_ops"), BODY_OPS_SCREEN_HANDLER);
        Registry.register(Registries.SCREEN_HANDLER, id("air_conditioner"), AIR_CONDITIONER_SCREEN_HANDLER);
        Registry.register(Registries.SCREEN_HANDLER, id("space_heater"), SPACE_HEATER_SCREEN_HANDLER);
    }
}
