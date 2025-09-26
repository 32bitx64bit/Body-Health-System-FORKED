package xyz.srgnis.bodyhealthsystem.registry;

import net.minecraft.client.gui.screen.ingame.HandledScreens;
import xyz.srgnis.bodyhealthsystem.client.screen.*;

public class Screens {
    public static void registerScreens() {
        // Register unified Body Operations screen
        HandledScreens.register(ScreenHandlers.BODY_OPS_SCREEN_HANDLER, BodyOperationsScreen::new);
        HandledScreens.register(ScreenHandlers.AIR_CONDITIONER_SCREEN_HANDLER, AirConditionerScreen::new);
        HandledScreens.register(ScreenHandlers.SPACE_HEATER_SCREEN_HANDLER, SpaceHeaterScreen::new);
        // Keep legacy heal screen (will be phased out)
        HandledScreens.register(ScreenHandlers.HEAL_SCREEN_HANDLER, HealScreen::new);
    }
}
