package xyz.srgnis.bodyhealthsystem.util;

import net.minecraft.client.gui.DrawContext;
import xyz.srgnis.bodyhealthsystem.body.BodyPart;
import xyz.srgnis.bodyhealthsystem.constants.GUIConstants;

public class Draw {
    static final int dark_green = 0xff38761d;
    static final int green = 0xff8fce00;
    static final int red = 0xffb70000;
    static final int black = 0xff191919;
    static final int yellow = 0xffffd966;
    static final int orange = 0xfff87c00;
    static final int gray = 0xff5b5b5b;

    // Temperature colors (HEX provided): hot #ee1b1b, normal #33ee1b, cold #1b50ee
    private static final int TEMP_HOT = 0xffee1b1b;
    private static final int TEMP_NORMAL = 0xff33ee1b;
    private static final int TEMP_COLD = 0xff1b50ee;

    public static int selectHealthColor(BodyPart part){
        float percent = part.getHealth()/part.getMaxHealth();
        if(percent>=1){
            return dark_green;
        }
        if(percent>0.75){
            return green;
        }
        if(percent>0.5){
            return yellow;
        }
        if(percent>0.25){
            return orange;
        }
        if(percent>0){
            return red;
        }
        return gray;
    }

    // Smooth gradient between cold -> normal band (36..38C) -> hot based on body core temperature in Celsius
    // Below 36C: interpolate blue->green from 28C..36C. Above 38C: interpolate green->red from 38C..42C.
    // Within 36..38C: show solid green.
    public static int selectTemperatureColor(double bodyTempC) {
        final double coldMin = 28.0;
        final double greenMin = 36.0;
        final double greenMax = 38.0;
        final double hotMax = 42.0;

        if (bodyTempC < greenMin) {
            double t = clamp01((bodyTempC - coldMin) / (greenMin - coldMin));
            return lerpColor(TEMP_COLD, TEMP_NORMAL, t);
        } else if (bodyTempC > greenMax) {
            double t = clamp01((bodyTempC - greenMax) / (hotMax - greenMax));
            return lerpColor(TEMP_NORMAL, TEMP_HOT, t);
        } else {
            return TEMP_NORMAL;
        }
    }

    private static double clamp01(double v) { return v < 0 ? 0 : (v > 1 ? 1 : v); }

    private static int lerpColor(int a, int b, double t) {
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int rr = (int)Math.round(ar + (br - ar) * t);
        int rg = (int)Math.round(ag + (bg - ag) * t);
        int rb = (int)Math.round(ab + (bb - ab) * t);
        return 0xFF000000 | (rr << 16) | (rg << 8) | rb;
    }

    public static void drawHealthRectangle(DrawContext drawContext, int startX, int startY, int width, int height, int color){
        int endX = startX+width;
        int endY = startY+height;

        drawContext.fill(startX, startY, endX, endY, black);
        drawContext.fill(startX+ GUIConstants.BORDER_SIZE, startY+ GUIConstants.BORDER_SIZE, endX- GUIConstants.BORDER_SIZE, endY- GUIConstants.BORDER_SIZE, color);
    }
}
