package xyz.srgnis.bodyhealthsystem.client.screen;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import xyz.srgnis.bodyhealthsystem.BHSMain;
import xyz.srgnis.bodyhealthsystem.client.screen.DownedOverlayController;

public class DownedGiveUpScreen extends Screen {
    public DownedGiveUpScreen() {
        super(Text.translatable("screen.bodyhealthsystem.downed_title"));
    }

    @Override
    protected void init() {
        int w = 100;
        int h = 20;
        int x = (this.width - w) / 2;
        int y = (this.height / 2) + 20;
        this.addDrawableChild(ButtonWidget.builder(Text.translatable("button.bodyhealthsystem.give_up"), btn -> {
            ClientPlayNetworking.send(BHSMain.id("give_up"), PacketByteBufs.create());
            close();
        }).dimensions(x, y, w, h).build());
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);
        super.render(context, mouseX, mouseY, delta);

        int centerX = this.width / 2;
        int titleY = (this.height / 2) - 20;
        context.drawCenteredTextWithShadow(this.textRenderer, Text.translatable("screen.bodyhealthsystem.downed_message"), centerX, titleY, 0xFFFFFF);

        // Show remaining rescue time
        var mc = MinecraftClient.getInstance();
        if (mc.player != null && mc.player instanceof xyz.srgnis.bodyhealthsystem.body.player.BodyProvider provider) {
            var body = provider.getBody();
            if (body != null && body.isDowned()) {
                int ticks = Math.max(0, body.getBleedOutTicksRemaining());
                int seconds = (ticks + 19) / 20; // ceil to next second
                Text timer = Text.translatable("screen.bodyhealthsystem.downed_timer", seconds);
                context.drawCenteredTextWithShadow(this.textRenderer, timer, centerX, titleY + 12, seconds <= 10 ? 0xFF5555 : 0xFFFFFF);
            }
        }
    }

    @Override
    public void close() {
        // User dismissed the overlay (ESC/back). Suppress auto-reopen until they press E.
        DownedOverlayController.suppressOnce();
        MinecraftClient.getInstance().setScreen(null);
    }
}
