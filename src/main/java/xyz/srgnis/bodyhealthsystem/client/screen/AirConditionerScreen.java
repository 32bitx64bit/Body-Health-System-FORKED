package xyz.srgnis.bodyhealthsystem.client.screen;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class AirConditionerScreen extends HandledScreen<AirConditionerScreenHandler> {
    private static final Identifier TEXTURE = new Identifier("bodyhealthsystem", "textures/gui/air_conditioner.png");

    public AirConditionerScreen(AirConditionerScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.backgroundWidth = 176;
        this.backgroundHeight = 166;
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
            // Shift an additional 1px left (x-2 overall) from original 99,37, still down 1px
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
        this.renderBackground(context);
        super.render(context, mouseX, mouseY, delta);
        this.drawMouseoverTooltip(context, mouseX, mouseY);
    }
}
