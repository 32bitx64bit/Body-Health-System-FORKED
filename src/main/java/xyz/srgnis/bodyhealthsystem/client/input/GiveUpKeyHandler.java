package xyz.srgnis.bodyhealthsystem.client.input;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import xyz.srgnis.bodyhealthsystem.BHSMain;
import xyz.srgnis.bodyhealthsystem.body.player.BodyProvider;

public final class GiveUpKeyHandler {
    private static KeyBinding GIVE_UP_KEY;

    public static void initClient() {
        GIVE_UP_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.bodyhealthsystem.give_up", // translation key
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_G, // default 'G'
                "category.bodyhealthsystem" // category
        ));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (GIVE_UP_KEY.wasPressed()) {
                MinecraftClient mc = MinecraftClient.getInstance();
                if (mc.player == null) continue;
                var body = ((BodyProvider) mc.player).getBody();
                if (body != null && body.isDowned()) {
                    // No payload needed, just a signal
                    ClientPlayNetworking.send(BHSMain.id("give_up"), PacketByteBufs.create());
                }
            }
        });
    }
}
