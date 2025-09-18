package xyz.srgnis.bodyhealthsystem.registry;

import net.minecraft.client.gui.screen.ingame.HandledScreens;
import xyz.srgnis.bodyhealthsystem.client.screen.BodyOperationsScreen;
import xyz.srgnis.bodyhealthsystem.client.screen.BodyOperationsScreenHandler;
import xyz.srgnis.bodyhealthsystem.client.screen.HealScreen;

public class Screens2 {
    public static void registerScreens() {
        HandledScreens.register(ScreenHandlers.BODY_OPS_SCREEN_HANDLER, BodyOperationsScreen::new);
        // Keep existing heal screen registration for now (will migrate callers)
        HandledScreens.register(ScreenHandlers.HEAL_SCREEN_HANDLER, HealScreen::new);
    }
}
