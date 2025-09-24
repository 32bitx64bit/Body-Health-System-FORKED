package xyz.srgnis.bodyhealthsystem.registry;

import net.minecraft.client.gui.screen.ingame.HandledScreens;
import xyz.srgnis.bodyhealthsystem.client.screen.BodyOperationsScreen;
import xyz.srgnis.bodyhealthsystem.client.screen.AirConditionerScreen;
import xyz.srgnis.bodyhealthsystem.client.screen.AirConditionerScreenHandler;
import xyz.srgnis.bodyhealthsystem.client.screen.BodyOperationsScreenHandler;
import xyz.srgnis.bodyhealthsystem.client.screen.HealScreen;

public class Screens {
    public static void registerScreens() {
        // Register unified Body Operations screen
        HandledScreens.register(ScreenHandlers.BODY_OPS_SCREEN_HANDLER, BodyOperationsScreen::new);
        HandledScreens.register(ScreenHandlers.AIR_CONDITIONER_SCREEN_HANDLER, AirConditionerScreen::new);
        // Keep legacy heal screen (will be phased out)
        HandledScreens.register(ScreenHandlers.HEAL_SCREEN_HANDLER, HealScreen::new);
    }
}
