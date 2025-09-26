package xyz.srgnis.bodyhealthsystem.client.screen;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class SpaceHeaterScreen extends HandledScreen<SpaceHeaterScreenHandler> {
    private static final Identifier TEXTURE = new Identifier("bodyhealthsystem", "textures/gui/space_heater.png");
    private ButtonWidget modeButton;
    private boolean lastRegulating;

    public SpaceHeaterScreen(SpaceHeaterScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.backgroundWidth = 176;
        this.backgroundHeight = 166;
    }

    @Override
    protected void init() {
        super.init();
        int x = (this.width - this.backgroundWidth) / 2;
        int y = (this.height - this.backgroundHeight) / 2;
        lastRegulating = handler.isRegulating();
        modeButton = ButtonWidget.builder(getModeLabel(), b -> {
            boolean newMode = !handler.isRegulating();
            handler.setRegulating(newMode);
            modeButton.setMessage(getModeLabel());
            lastRegulating = newMode;
        }).dimensions(x + 176 - 80 - 8, y + 6, 80, 20).build();
        addDrawableChild(modeButton);
    }

    private Text getModeLabel() {
        return handler.isRegulating()
                ? Text.translatable("screen.bodyhealthsystem.heater.mode_regulate")
                : Text.translatable("screen.bodyhealthsystem.heater.mode_heater");
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        int x = (this.width - this.backgroundWidth) / 2;
        int y = (this.height - this.backgroundHeight) / 2;
        context.drawTexture(TEXTURE, x, y, 0, 0, 176, 166);

        int burn = this.handler.getBurnTime();
        int total = this.handler.getBurnTimeTotal();
        if (total > 0 && burn > 0) {
            int h = (int)Math.ceil(burn * 13.0 / total);
            int srcU = 176;
            int srcV = 1;
            int w = 13;
            context.drawTexture(TEXTURE, x + 99, y + 37 + (13 - h), srcU, srcV + (13 - h), w, h);
        }
    }

    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        context.drawText(this.textRenderer, this.title, 8, 6, 0x404040, false);
        context.drawText(this.textRenderer, this.playerInventoryTitle, 8, this.backgroundHeight - 96 + 2, 0x404040, false);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        boolean now = handler.isRegulating();
        if (now != lastRegulating && modeButton != null) {
            modeButton.setMessage(getModeLabel());
            lastRegulating = now;
        }
        this.renderBackground(context);
        super.render(context, mouseX, mouseY, delta);
        this.drawMouseoverTooltip(context, mouseX, mouseY);
    }
}
