package xyz.srgnis.bodyhealthsystem.registry;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.registry.Registry;
import net.minecraft.registry.Registries;
import xyz.srgnis.bodyhealthsystem.client.screen.HealScreenHandler;
import xyz.srgnis.bodyhealthsystem.client.screen.BodyOperationsScreenHandler;

import static xyz.srgnis.bodyhealthsystem.BHSMain.id;

public class ScreenHandlers {

    // Legacy heal screen (kept for compatibility)
    public static final ScreenHandlerType<HealScreenHandler> HEAL_SCREEN_HANDLER = new ExtendedScreenHandlerType<>(HealScreenHandler::new);

    // Unified Body Operations screen
    public static final ScreenHandlerType<BodyOperationsScreenHandler> BODY_OPS_SCREEN_HANDLER = new ExtendedScreenHandlerType<>(BodyOperationsScreenHandler::new);

    public static void registerScreenHandlers() {
        // Legacy id
        Registry.register(Registries.SCREEN_HANDLER, id("medkit"), HEAL_SCREEN_HANDLER);
        // New unified GUI id
        Registry.register(Registries.SCREEN_HANDLER, id("body_ops"), BODY_OPS_SCREEN_HANDLER);
    }
}
