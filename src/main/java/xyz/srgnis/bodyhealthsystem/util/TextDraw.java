package xyz.srgnis.bodyhealthsystem.util;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

/**
 * Small helpers for drawing text with automatic downscaling to fit a maximum width.
 */
public final class TextDraw {
    private TextDraw() {}

    // Conservative max text width inside the vanilla status effect box when drawn in wide mode (120px background)
    // Left icon + paddings take ~30px, keep ~6px right padding => 84-90px usable. Use 90px to be generous.
    private static final int DEFAULT_STATUS_TEXT_MAX_WIDTH = 84;

    /**
     * Draw status-effect text at (x,y), scaling down if it exceeds DEFAULT_STATUS_TEXT_MAX_WIDTH.
     * - Only scales down, never up
     * - Returns an x-advance similar to DrawContext#drawTextWithShadow
     */
    public static int drawStatusEffectTextWithAutoscale(DrawContext context, TextRenderer tr, Text text, int x, int y, int color) {
        return drawAutoScaled(context, tr, text, x, y, color, DEFAULT_STATUS_TEXT_MAX_WIDTH, true);
    }

    /**
     * Generic auto-scale draw for Text. If text width exceeds maxWidth, scale down so it fits exactly.
     * If not, draw normally. Optionally draw shadow.
     * Returns an x-advance close to the original drawTextWithShadow behavior.
     */
    public static int drawAutoScaled(DrawContext context, TextRenderer tr, Text text, int x, int y, int color, int maxWidth, boolean shadow) {
        int textWidth = tr.getWidth(text);
        if (textWidth <= 0) return x;

        if (textWidth <= maxWidth) {
            return context.drawTextWithShadow(tr, text, x, y, color);
        }

        float scale = maxWidth / (float) textWidth;
        var matrices = context.getMatrices();
        matrices.push();
        matrices.translate(x, y, 0);
        matrices.scale(scale, scale, 1.0f);
        int drawn = shadow
            ? context.drawTextWithShadow(tr, text, 0, 0, color)
            : context.drawText(tr, text, 0, 0, color, false);
        matrices.pop();

        // Advance by the scaled width to approximate vanilla return semantics
        return x + Math.round(textWidth * scale);
    }
}
