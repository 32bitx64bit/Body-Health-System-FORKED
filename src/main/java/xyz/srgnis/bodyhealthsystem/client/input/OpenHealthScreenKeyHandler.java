package xyz.srgnis.bodyhealthsystem.client.input;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import xyz.srgnis.bodyhealthsystem.body.player.BodyProvider;
import xyz.srgnis.bodyhealthsystem.client.screen.BodyOperationsScreen;
import xyz.srgnis.bodyhealthsystem.client.screen.BodyOperationsScreenHandler;

public final class OpenHealthScreenKeyHandler {
    private static KeyBinding OPEN_HEALTH_KEY;

    public static void initClient() {
        OPEN_HEALTH_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.bodyhealthsystem.open_health", // translation key
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_R, // default 'R'
                "category.bodyhealthsystem" // category
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (OPEN_HEALTH_KEY.wasPressed()) {
                MinecraftClient mc = MinecraftClient.getInstance();
                if (mc.player == null) continue;
                if (!(mc.player instanceof BodyProvider)) continue;
                // Toggle: if our screen is open, close it; otherwise open overview-only
                if (mc.currentScreen instanceof BodyOperationsScreen) {
                    mc.setScreen(null);
                } else {
                    mc.setScreen(new BodyOperationsScreen(new BodyOperationsScreenHandler(0, mc.player.getInventory(), net.minecraft.item.ItemStack.EMPTY, mc.player), mc.player.getInventory(), net.minecraft.text.Text.literal("Health")));
                }
            }
        });
    }
}
