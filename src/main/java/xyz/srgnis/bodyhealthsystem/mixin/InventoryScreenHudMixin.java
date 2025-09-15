package xyz.srgnis.bodyhealthsystem.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.util.Window;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.srgnis.bodyhealthsystem.body.BodyPart;
import xyz.srgnis.bodyhealthsystem.body.player.BodyProvider;
import xyz.srgnis.bodyhealthsystem.body.player.PlayerBodyParts;
import xyz.srgnis.bodyhealthsystem.constants.GUIConstants;
import net.minecraft.util.Identifier;
import xyz.srgnis.bodyhealthsystem.BHSMain;

import static xyz.srgnis.bodyhealthsystem.util.Draw.drawHealthRectangle;
import static xyz.srgnis.bodyhealthsystem.util.Draw.selectHealthColor;

@Mixin(InventoryScreen.class)
public abstract class InventoryScreenHudMixin {

    private static final Identifier TEX_BONEMAIN = new Identifier(BHSMain.MOD_ID, "textures/gui/bonemain.png");
    private static final Identifier TEX_RIBCAGE = new Identifier(BHSMain.MOD_ID, "textures/gui/ribcage.png");
    private static final Identifier TEX_SKULL = new Identifier(BHSMain.MOD_ID, "textures/gui/skull.png");
    private static final Identifier TEX_FOOT = new Identifier(BHSMain.MOD_ID, "textures/gui/foot.png");

    private static final Identifier TEX_BONEMAIN_BROKEN = new Identifier(BHSMain.MOD_ID, "textures/gui/bonemainbroken.png");
    private static final Identifier TEX_RIBCAGE_BROKEN = new Identifier(BHSMain.MOD_ID, "textures/gui/ribcagebroken.png");
    private static final Identifier TEX_FOOT_BROKEN = new Identifier(BHSMain.MOD_ID, "textures/gui/footbroken.png");

    @Inject(method = "render(Lnet/minecraft/client/gui/DrawContext;IIF)V", at = @At("TAIL"))
    private void bhs$renderBodyHudOnInventory(DrawContext drawContext, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!xyz.srgnis.bodyhealthsystem.config.Config.showInventoryBodyHud) return;
        if (!(MinecraftClient.getInstance().player instanceof BodyProvider provider)) return;
        if (provider.getBody() == null) return;

        // Compute preferred placement (to the right of the inventory GUI), flip to left if it would overflow
        Window window = MinecraftClient.getInstance().getWindow();
        int screenW = window.getScaledWidth();
        int screenH = window.getScaledHeight();

        int bodyW = GUIConstants.SCALED_BODY_WIDTH;
        int bodyH = GUIConstants.SCALED_BODY_HEIGHT;

        HandledScreenAccessor acc = (HandledScreenAccessor)(Object)this;
        int baseY = acc.getY_BHS() + (acc.getBackgroundHeight_BHS() - bodyH) / 2;
        if (baseY < 2) baseY = 2;
        if (baseY + bodyH > screenH - 2) baseY = Math.max(2, screenH - 2 - bodyH);

        // Measure numbers to ensure they fit when placed to sides
        BodyPart leftArm = provider.getBody().getPart(PlayerBodyParts.LEFT_ARM);
        BodyPart rightArm = provider.getBody().getPart(PlayerBodyParts.RIGHT_ARM);
        BodyPart leftLeg = provider.getBody().getPart(PlayerBodyParts.LEFT_LEG);
        BodyPart rightLeg = provider.getBody().getPart(PlayerBodyParts.RIGHT_LEG);

        var tr = MinecraftClient.getInstance().textRenderer;
        int leftMarginNeeded = tr.getWidth(formatHealth(leftLeg)) + 3; // labels to the left (left leg only)

        int baseXLeft = acc.getX_BHS() - 8 - bodyW;

        boolean recipeOpen = false;
        if ((Object) this instanceof net.minecraft.client.gui.screen.recipebook.RecipeBookProvider rbProvider) {
            // Use the recipe book widget's open state
            recipeOpen = rbProvider.getRecipeBookWidget() != null && rbProvider.getRecipeBookWidget().isOpen();
        }

        int baseX;
        if (recipeOpen) {
            final int recipeBookWidth = 147; // vanilla recipe book panel width (~147px)
            final int gap = 18;               // small gap between panels
            int desired = acc.getX_BHS() - gap - bodyW - recipeBookWidth - gap;
            baseX = Math.max(2 + leftMarginNeeded, desired);
        } else {
            // Default: place to the left of the inventory, clamped to screen
            baseX = Math.max(2 + leftMarginNeeded, baseXLeft);
        }

        // Draw body part rectangles
        drawPart(drawContext, provider, PlayerBodyParts.HEAD, baseX + GUIConstants.SCALED_HEAD_X_OFFSET, baseY + GUIConstants.SCALED_HEAD_Y_OFFSET, GUIConstants.SCALED_HEAD_WIDTH, GUIConstants.SCALED_HEAD_HEIGHT);
        drawPart(drawContext, provider, PlayerBodyParts.LEFT_ARM, baseX + GUIConstants.SCALED_LEFT_ARM_X_OFFSET, baseY + GUIConstants.SCALED_LEFT_ARM_Y_OFFSET, GUIConstants.SCALED_LEFT_ARM_WIDTH, GUIConstants.SCALED_LEFT_ARM_HEIGHT);
        drawPart(drawContext, provider, PlayerBodyParts.TORSO, baseX + GUIConstants.SCALED_TORSO_X_OFFSET, baseY + GUIConstants.SCALED_TORSO_Y_OFFSET, GUIConstants.SCALED_TORSO_WIDTH, GUIConstants.SCALED_TORSO_HEIGHT);
        drawPart(drawContext, provider, PlayerBodyParts.RIGHT_ARM, baseX + GUIConstants.SCALED_RIGHT_ARM_X_OFFSET, baseY + GUIConstants.SCALED_RIGHT_ARM_Y_OFFSET, GUIConstants.SCALED_RIGHT_ARM_WIDTH, GUIConstants.SCALED_RIGHT_ARM_HEIGHT);
        drawPart(drawContext, provider, PlayerBodyParts.LEFT_LEG, baseX + GUIConstants.SCALED_LEFT_LEG_X_OFFSET, baseY + GUIConstants.SCALED_LEFT_LEG_Y_OFFSET, GUIConstants.SCALED_LEFT_LEG_WIDTH, GUIConstants.SCALED_LEFT_LEG_HEIGHT);
        drawPart(drawContext, provider, PlayerBodyParts.RIGHT_LEG, baseX + GUIConstants.SCALED_RIGHT_LEG_X_OFFSET, baseY + GUIConstants.SCALED_RIGHT_LEG_Y_OFFSET, GUIConstants.SCALED_RIGHT_LEG_WIDTH, GUIConstants.SCALED_RIGHT_LEG_HEIGHT);
        drawPart(drawContext, provider, PlayerBodyParts.LEFT_FOOT, baseX + GUIConstants.SCALED_LEFT_FOOT_X_OFFSET, baseY + GUIConstants.SCALED_LEFT_FOOT_Y_OFFSET, GUIConstants.SCALED_LEFT_FOOT_WIDTH, GUIConstants.SCALED_LEFT_FOOT_HEIGHT);
        drawPart(drawContext, provider, PlayerBodyParts.RIGHT_FOOT, baseX + GUIConstants.SCALED_RIGHT_FOOT_X_OFFSET, baseY + GUIConstants.SCALED_RIGHT_FOOT_Y_OFFSET, GUIConstants.SCALED_RIGHT_FOOT_WIDTH, GUIConstants.SCALED_RIGHT_FOOT_HEIGHT);

        // Draw numbers positioned as requested
        int white = 0xFFFFFF;

        // Head: number inside the head (centered)
        BodyPart head = provider.getBody().getPart(PlayerBodyParts.HEAD);
        String headStr = formatHealth(head);
        int headTextW = tr.getWidth(headStr);
        int headX = baseX + GUIConstants.SCALED_HEAD_X_OFFSET + (GUIConstants.SCALED_HEAD_WIDTH - headTextW) / 2;
        int headY = baseY + GUIConstants.SCALED_HEAD_Y_OFFSET + (GUIConstants.SCALED_HEAD_HEIGHT - 9) / 2;
        drawContext.drawTextWithShadow(tr, headStr, headX, headY, white);

        // Torso: number centered inside the torso
        BodyPart torso = provider.getBody().getPart(PlayerBodyParts.TORSO);
        String torsoStr = formatHealth(torso);
        int torsoTextW = tr.getWidth(torsoStr);
        int torsoX = baseX + GUIConstants.SCALED_TORSO_X_OFFSET + (GUIConstants.SCALED_TORSO_WIDTH - torsoTextW) / 2;
        int torsoY = baseY + GUIConstants.SCALED_TORSO_Y_OFFSET + (GUIConstants.SCALED_TORSO_HEIGHT - 9) / 2;
        drawContext.drawTextWithShadow(tr, torsoStr, torsoX, torsoY, white);

        // Left Arm: number inside (centered)
        String laStr = formatHealth(leftArm);
        int laTextW = tr.getWidth(laStr);
        int laX = baseX + GUIConstants.SCALED_LEFT_ARM_X_OFFSET + (GUIConstants.SCALED_LEFT_ARM_WIDTH - laTextW) / 2;
        int laY = baseY + GUIConstants.SCALED_LEFT_ARM_Y_OFFSET + (GUIConstants.SCALED_LEFT_ARM_HEIGHT - 9) / 2;
        drawContext.drawTextWithShadow(tr, laStr, laX, laY, white);

        // Right Arm: number inside (centered)
        String raStr = formatHealth(rightArm);
        int raTextW = tr.getWidth(raStr);
        int raX = baseX + GUIConstants.SCALED_RIGHT_ARM_X_OFFSET + (GUIConstants.SCALED_RIGHT_ARM_WIDTH - raTextW) / 2;
        int raY = baseY + GUIConstants.SCALED_RIGHT_ARM_Y_OFFSET + (GUIConstants.SCALED_RIGHT_ARM_HEIGHT - 9) / 2;
        drawContext.drawTextWithShadow(tr, raStr, raX, raY, white);

        // Left Leg: number on the left side
        BodyPart leftLegPart = leftLeg;
        String llStr = formatHealth(leftLegPart);
        int llTextW = tr.getWidth(llStr);
        int llY = baseY + GUIConstants.SCALED_LEFT_LEG_Y_OFFSET + GUIConstants.SCALED_LEFT_LEG_HEIGHT / 2 - 4;
        int llX = baseX + GUIConstants.SCALED_LEFT_LEG_X_OFFSET - llTextW - 2; // keep outside on left
        drawContext.drawTextWithShadow(tr, llStr, llX, llY, white);

        // Right Leg: number on the right side
        BodyPart rightLegPart = rightLeg;
        String rlStr = formatHealth(rightLegPart);
        int rlY = baseY + GUIConstants.SCALED_RIGHT_LEG_Y_OFFSET + GUIConstants.SCALED_RIGHT_LEG_HEIGHT / 2 - 4;
        int rlX = baseX + GUIConstants.SCALED_RIGHT_LEG_X_OFFSET + GUIConstants.SCALED_RIGHT_LEG_WIDTH + 2; // keep outside on right
        drawContext.drawTextWithShadow(tr, rlStr, rlX, rlY, white);

        // Feet: numbers at the bottom of each foot
        BodyPart leftFoot = provider.getBody().getPart(PlayerBodyParts.LEFT_FOOT);
        BodyPart rightFoot = provider.getBody().getPart(PlayerBodyParts.RIGHT_FOOT);
        String lfStr = formatHealth(leftFoot);
        String rfStr = formatHealth(rightFoot);
        int lfTextW = tr.getWidth(lfStr);
        int rfTextW = tr.getWidth(rfStr);
        int lfX = baseX + GUIConstants.SCALED_LEFT_FOOT_X_OFFSET + (GUIConstants.SCALED_LEFT_FOOT_WIDTH - lfTextW) / 2;
        int rfX = baseX + GUIConstants.SCALED_RIGHT_FOOT_X_OFFSET + (GUIConstants.SCALED_RIGHT_FOOT_WIDTH - rfTextW) / 2;
        int feetY = baseY + GUIConstants.SCALED_LEFT_FOOT_Y_OFFSET + GUIConstants.SCALED_LEFT_FOOT_HEIGHT + 2;
        drawContext.drawTextWithShadow(tr, lfStr, lfX, feetY, white);
        drawContext.drawTextWithShadow(tr, rfStr, rfX, feetY, white);
    }

    private static void drawPart(DrawContext ctx, BodyProvider provider, net.minecraft.util.Identifier partId, int x, int y, int w, int h) {
        BodyPart p = provider.getBody().getPart(partId);
        int color = selectHealthColor(p);
        // Draw health rectangle as background
        drawHealthRectangle(ctx, x, y, w, h, color);

        boolean broken = p.isBroken();
        // Overlay bone texture for this part
        Identifier tex = selectBoneTexture(partId, broken);
        if (isDrawableResource(tex)) {
            if (partId.equals(PlayerBodyParts.LEFT_ARM) || partId.equals(PlayerBodyParts.RIGHT_ARM)) {
                // Arms: draw two stacked bone images to reduce vertical stretching
                int topH = h / 2;
                int bottomH = h - topH; // handle odd heights
                if (broken) {
                    // Use stored half selection for broken overlay on arms
                    Boolean topBroken = p.getBrokenTopHalf();
                    if (topBroken == null) topBroken = Boolean.TRUE; // default if unset
                    Identifier brokenTex = TEX_BONEMAIN_BROKEN;
                    Identifier normalTex = TEX_BONEMAIN;
                    if (topBroken) {
                        ctx.drawTexture(brokenTex, x, y, w, topH, 0.0F, 0.0F, 16, 16, 16, 16);
                        ctx.drawTexture(normalTex, x, y + topH, w, bottomH, 0.0F, 0.0F, 16, 16, 16, 16);
                    } else {
                        ctx.drawTexture(normalTex, x, y, w, topH, 0.0F, 0.0F, 16, 16, 16, 16);
                        ctx.drawTexture(brokenTex, x, y + topH, w, bottomH, 0.0F, 0.0F, 16, 16, 16, 16);
                    }
                } else {
                    ctx.drawTexture(tex, x, y, w, topH, 0.0F, 0.0F, 16, 16, 16, 16);
                    ctx.drawTexture(tex, x, y + topH, w, bottomH, 0.0F, 0.0F, 16, 16, 16, 16);
                }
            } else {
                // Other parts: stretch single bone texture to destination size without tiling
                ctx.drawTexture(tex, x, y, w, h, 0.0F, 0.0F, 16, 16, 16, 16);
            }
        } else {
            // Visible placeholder if texture not present: draw a white 1px border
            final int white = 0xFFFFFFFF;
            ctx.fill(x, y, x + w, y + 1, white);
            ctx.fill(x, y + h - 1, x + w, y + h, white);
            ctx.fill(x, y, x + 1, y + h, white);
            ctx.fill(x + w - 1, y, x + w, y + h, white);
        }

    }

    private static boolean isDrawableResource(Identifier id) {
        var rm = MinecraftClient.getInstance().getResourceManager();
        try {
            var opt = rm.getResource(id);
            if (opt.isEmpty()) return false;
            try (var is = opt.get().getInputStream()) {
                return is.read() != -1;
            }
        } catch (Exception e) {
            return false;
        }
    }

    private static Identifier selectBoneTexture(net.minecraft.util.Identifier partId, boolean broken) {
        if (partId.equals(PlayerBodyParts.HEAD)) {
            // Skull is exempt from broken overlay
            return TEX_SKULL;
        } else if (partId.equals(PlayerBodyParts.TORSO)) {
            return broken ? TEX_RIBCAGE_BROKEN : TEX_RIBCAGE;
        } else if (partId.equals(PlayerBodyParts.LEFT_FOOT) || partId.equals(PlayerBodyParts.RIGHT_FOOT)) {
            return broken ? TEX_FOOT_BROKEN : TEX_FOOT;
        } else {
            // Arms and legs share the same generic bone graphic
            return broken ? TEX_BONEMAIN_BROKEN : TEX_BONEMAIN;
        }
    }

    private static String formatHealth(BodyPart p) {
        // round to integer for compact display
        return String.valueOf(Math.round(p.getHealth()));
    }
}
