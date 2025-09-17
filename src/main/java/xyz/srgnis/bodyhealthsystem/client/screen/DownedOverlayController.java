package xyz.srgnis.bodyhealthsystem.client.screen;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.DeathScreen;
import xyz.srgnis.bodyhealthsystem.body.player.BodyProvider;

public final class DownedOverlayController {
    private DownedOverlayController() {}

    private static boolean suppressOverlay = false;
    private static boolean wasDowned = false;

    public static void initClient() {
        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            MinecraftClient mc = MinecraftClient.getInstance();

            // If player is dead, ensure overlay closes and reset state.
            if (mc.player == null || !mc.player.isAlive() || mc.currentScreen instanceof DeathScreen) {
                if (mc.currentScreen instanceof DownedGiveUpScreen) {
                    mc.setScreen(null);
                }
                wasDowned = false;
                suppressOverlay = false;
                return;
            }

            if (!(mc.player instanceof BodyProvider provider)) return;
            var body = provider.getBody();
            if (body == null) return;

            boolean isDowned = body.isDowned();

            // Reset suppression when entering/exiting downed state
            if (isDowned && !wasDowned) suppressOverlay = false;
            if (!isDowned && wasDowned) suppressOverlay = false;

            if (isDowned) {
                // Client-side countdown estimate so UI shows a live timer
                int ticks = body.getBleedOutTicksRemaining();
                // Do not count down locally if being revived; server freezes during revive
                if (!body.isBeingRevived() && ticks > 0) body.setBleedOutTicksRemaining(ticks - 1);

                // Reopen overlay when pressing inventory key (E by default). Consume the press to prevent inventory.
                if (mc.options != null && mc.options.inventoryKey.wasPressed()) {
                    suppressOverlay = false;
                    if (!(mc.currentScreen instanceof DownedGiveUpScreen)) {
                        mc.setScreen(new DownedGiveUpScreen());
                    }
                    // Consume input so vanilla doesn't open inventory this tick
                    return;
                }

                // Show overlay unless explicitly suppressed
                if (!suppressOverlay) {
                    if (!(mc.currentScreen instanceof DownedGiveUpScreen)) {
                        mc.setScreen(new DownedGiveUpScreen());
                    }
                }
            } else {
                // Not downed: close overlay if open
                if (mc.currentScreen instanceof DownedGiveUpScreen) {
                    mc.setScreen(null);
                }
            }

            wasDowned = isDowned;
        });
    }

    // Called when the overlay is dismissed by the user (e.g., ESC)
    public static void suppressOnce() {
        suppressOverlay = true;
    }
}
