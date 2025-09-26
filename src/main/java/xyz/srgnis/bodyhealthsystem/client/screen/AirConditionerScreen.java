package xyz.srgnis.bodyhealthsystem.client.screen;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class AirConditionerScreen extends HandledScreen<AirConditionerScreenHandler> {
    private static final Identifier TEXTURE = new Identifier("bodyhealthsystem", "textures/gui/air_conditioner.png");
    private ButtonWidget modeButton;
    private boolean lastRegulating;

    public AirConditionerScreen(AirConditionerScreenHandler handler, PlayerInventory inventory, Text title) {
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
        // Place a toggle button near the top-right of the GUI
        modeButton = ButtonWidget.builder(getModeLabel(), b -> {
            boolean newMode = !handler.isRegulating();
            handler.setRegulating(newMode);
            modeButton.setMessage(getModeLabel());
            lastRegulating = newMode;
        }).dimensions(x + 176 - 80 - 8, y + 6, 80, 20).build();
        addDrawableChild(modeButton);
    }

    private Text getModeLabel() {
        // Off = constant breeze (-6°C), On = hold 22°C
        return handler.isRegulating()
                ? Text.translatable("screen.bodyhealthsystem.ac.mode_regulate")
                : Text.translatable("screen.bodyhealthsystem.ac.mode_breeze");
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        int x = (this.width - this.backgroundWidth) / 2;
        int y = (this.height - this.backgroundHeight) / 2;
        // Draw only the GUI sub-rectangle (u=0,v=0,w=176,h=166)
        context.drawTexture(TEXTURE, x, y, 0, 0, 176, 166);

        // Snowflake progress: source sprite at (176,1)-(189,14), place at (99,37)-(112,50)
        int burn = this.handler.getBurnTime();
        int total = this.handler.getBurnTimeTotal();
        if (total > 0 && burn > 0) {
            int h = (int)Math.ceil(burn * 13.0 / total); // 13px high
            // draw from top of source, shrink downward
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
        // Refresh button message if the property synced after init or changed externally
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
