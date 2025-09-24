package xyz.srgnis.bodyhealthsystem.registry;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.screen.ScreenHandlerContext;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.registry.Registry;
import net.minecraft.registry.Registries;
import xyz.srgnis.bodyhealthsystem.client.screen.HealScreenHandler;
import xyz.srgnis.bodyhealthsystem.client.screen.AirConditionerScreenHandler;
import xyz.srgnis.bodyhealthsystem.client.screen.BodyOperationsScreenHandler;

import static xyz.srgnis.bodyhealthsystem.BHSMain.id;

public class ScreenHandlers {

    // Legacy heal screen (kept for compatibility)
    public static final ScreenHandlerType<HealScreenHandler> HEAL_SCREEN_HANDLER = new ExtendedScreenHandlerType<>(HealScreenHandler::new);

    // Unified Body Operations screen
    public static final ScreenHandlerType<BodyOperationsScreenHandler> BODY_OPS_SCREEN_HANDLER = new ExtendedScreenHandlerType<>(BodyOperationsScreenHandler::new);

    // Air Conditioner GUI
    public static final ScreenHandlerType<AirConditionerScreenHandler> AIR_CONDITIONER_SCREEN_HANDLER = new ExtendedScreenHandlerType<>((syncId, inv, buf) -> new AirConditionerScreenHandler(syncId, inv, new net.minecraft.inventory.SimpleInventory(1), new net.minecraft.screen.ArrayPropertyDelegate(2)));

    public static void registerScreenHandlers() {
        // Legacy id
        Registry.register(Registries.SCREEN_HANDLER, id("medkit"), HEAL_SCREEN_HANDLER);
        // New unified GUI id
        Registry.register(Registries.SCREEN_HANDLER, id("body_ops"), BODY_OPS_SCREEN_HANDLER);
        Registry.register(Registries.SCREEN_HANDLER, id("air_conditioner"), AIR_CONDITIONER_SCREEN_HANDLER);
    }
}
