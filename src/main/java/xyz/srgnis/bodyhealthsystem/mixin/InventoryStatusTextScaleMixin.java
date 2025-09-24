package xyz.srgnis.bodyhealthsystem.mixin;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.AbstractInventoryScreen;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import xyz.srgnis.bodyhealthsystem.util.TextDraw;

/**
 * Scales down status effect text (name and duration) if it would overflow the effect box.
 * Applies only within AbstractInventoryScreen#drawStatusEffects.
 */
@Mixin(AbstractInventoryScreen.class)
public abstract class InventoryStatusTextScaleMixin {

    @Redirect(
        method = "drawStatusEffectDescriptions(Lnet/minecraft/client/gui/DrawContext;IILjava/lang/Iterable;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/DrawContext;drawTextWithShadow(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/Text;III)I"
        )
    )
    private int bhs$scaleStatusText(DrawContext context, TextRenderer tr, Text text, int x, int y, int color) {
        // Use our autoscaling drawer with a conservative max width that fits vanilla status box
        return TextDraw.drawStatusEffectTextWithAutoscale(context, tr, text, x, y, color);
    }
}
