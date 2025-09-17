package xyz.srgnis.bodyhealthsystem;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import xyz.srgnis.bodyhealthsystem.client.hud.BHSHud;
import xyz.srgnis.bodyhealthsystem.network.ClientNetworking;
import xyz.srgnis.bodyhealthsystem.registry.Screens;
import xyz.srgnis.bodyhealthsystem.client.input.GiveUpKeyHandler;
import xyz.srgnis.bodyhealthsystem.client.screen.DownedOverlayController;

public class BHSClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        HudRenderCallback.EVENT.register(new BHSHud());
        ClientNetworking.initialize();
        Screens.registerScreens();
        GiveUpKeyHandler.initClient();
        DownedOverlayController.initClient();
    }
}
