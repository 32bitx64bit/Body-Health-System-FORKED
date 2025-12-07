package xyz.srgnis.bodyhealthsystem.client.hud;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import xyz.srgnis.bodyhealthsystem.body.player.BodyProvider;
import xyz.srgnis.bodyhealthsystem.body.player.PlayerBodyParts;
import xyz.srgnis.bodyhealthsystem.config.Config;
import xyz.srgnis.bodyhealthsystem.constants.GUIConstants;
import xyz.srgnis.bodyhealthsystem.items.MedkitItem;
import xyz.srgnis.bodyhealthsystem.items.UpgradedMedkitItem;
import xyz.srgnis.bodyhealthsystem.items.TraumaKitItem;

import static xyz.srgnis.bodyhealthsystem.util.Draw.drawHealthRectangle;
import static xyz.srgnis.bodyhealthsystem.util.Draw.selectHealthColor;

public class BHSHud implements HudRenderCallback {
    private static int startX;
    private static int startY;

    //TODO: select color in the parts
    @Override
    public void onHudRender(DrawContext drawContext, float v) {
        MinecraftClient mc = MinecraftClient.getInstance();
        var im = mc.interactionManager;
        var screen = mc.currentScreen;

        // Revive progress bar under crosshair (no texture needed)
        float reviveProgress = getReviveProgressClient();
        if (reviveProgress > 0f) {
            drawReviveProgress(drawContext, reviveProgress);
        }

        // Hide the always-on HUD when the inventory screen is open and inventory HUD is enabled
        if (xyz.srgnis.bodyhealthsystem.config.Config.showInventoryBodyHud &&
                screen instanceof net.minecraft.client.gui.screen.ingame.InventoryScreen) {
            return;
        }

        PlayerEntity pe = mc.player;
        if (pe == null || im == null || !im.hasStatusBars()) return;
        if (!(pe instanceof BodyProvider provider)) return;
        var body = provider.getBody();
        if (body == null) return;

        // Optional: draw only when damaged or broken
        if (Config.hudOnlyWhenDamaged) {
            boolean anyDamaged = false;
            // Optimized: use view to avoid ArrayList creation
            for (var p : body.getPartsView()) {
                if (p.isDamaged() || p.isBroken()) { anyDamaged = true; break; }
            }
            if (!anyDamaged) return;
        }

        // Compute scaled anchor-corrected coordinates
        setHudCords();

        drawContext.getMatrices().push();
        drawContext.getMatrices().translate(0, 0, -1);
        drawContext.getMatrices().scale(Config.hudScale, Config.hudScale, 1);

        // Optimized: cache body part references to avoid 8 HashMap lookups
        var head = body.getPart(PlayerBodyParts.HEAD);
        var leftArm = body.getPart(PlayerBodyParts.LEFT_ARM);
        var rightArm = body.getPart(PlayerBodyParts.RIGHT_ARM);
        var torso = body.getPart(PlayerBodyParts.TORSO);
        var leftLeg = body.getPart(PlayerBodyParts.LEFT_LEG);
        var rightLeg = body.getPart(PlayerBodyParts.RIGHT_LEG);
        var leftFoot = body.getPart(PlayerBodyParts.LEFT_FOOT);
        var rightFoot = body.getPart(PlayerBodyParts.RIGHT_FOOT);

        int color;
        // head
        color = selectHealthColor(head);
        drawHealthRectangle(drawContext, startX + GUIConstants.HEAD_X_OFFSET, startY + GUIConstants.HEAD_Y_OFFSET, GUIConstants.HEAD_WIDTH, GUIConstants.HEAD_HEIGHT, color);
        // left arm
        color = selectHealthColor(leftArm);
        drawHealthRectangle(drawContext, startX + GUIConstants.LEFT_ARM_X_OFFSET, startY + GUIConstants.LEFT_ARM_Y_OFFSET, GUIConstants.LEFT_ARM_WIDTH, GUIConstants.LEFT_ARM_HEIGHT, color);
        // torso
        color = selectHealthColor(torso);
        drawHealthRectangle(drawContext, startX + GUIConstants.TORSO_X_OFFSET, startY + GUIConstants.TORSO_Y_OFFSET, GUIConstants.TORSO_WIDTH, GUIConstants.TORSO_HEIGHT, color);
        // right arm
        color = selectHealthColor(rightArm);
        drawHealthRectangle(drawContext, startX + GUIConstants.RIGHT_ARM_X_OFFSET, startY + GUIConstants.RIGHT_ARM_Y_OFFSET, GUIConstants.RIGHT_ARM_WIDTH, GUIConstants.RIGHT_ARM_HEIGHT, color);
        // legs
        color = selectHealthColor(leftLeg);
        drawHealthRectangle(drawContext, startX + GUIConstants.LEFT_LEG_X_OFFSET, startY + GUIConstants.LEFT_LEG_Y_OFFSET, GUIConstants.LEFT_LEG_WIDTH, GUIConstants.LEFT_LEG_HEIGHT, color);
        color = selectHealthColor(rightLeg);
        drawHealthRectangle(drawContext, startX + GUIConstants.RIGHT_LEG_X_OFFSET, startY + GUIConstants.RIGHT_LEG_Y_OFFSET, GUIConstants.RIGHT_LEG_WIDTH, GUIConstants.RIGHT_LEG_HEIGHT, color);
        // feet
        color = selectHealthColor(leftFoot);
        drawHealthRectangle(drawContext, startX + GUIConstants.LEFT_FOOT_X_OFFSET, startY + GUIConstants.LEFT_FOOT_Y_OFFSET, GUIConstants.LEFT_FOOT_WIDTH, GUIConstants.LEFT_FOOT_HEIGHT, color);
        color = selectHealthColor(rightFoot);
        drawHealthRectangle(drawContext, startX + GUIConstants.RIGHT_FOOT_X_OFFSET, startY + GUIConstants.RIGHT_FOOT_Y_OFFSET, GUIConstants.RIGHT_FOOT_WIDTH, GUIConstants.RIGHT_FOOT_HEIGHT, color);

        drawContext.getMatrices().pop();
    }

    private static void setHudCords(){
        MinecraftClient mc = MinecraftClient.getInstance();
        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();

        float scale = Math.max(0.1f, Config.hudScale);
        float inv = 1.0f / scale;

        int bodyWpx = Math.round(GUIConstants.BODY_WIDTH * scale);
        int bodyHpx = Math.round(GUIConstants.BODY_HEIGHT * scale);

        int pxX = 0, pxY = 0;
        switch (Config.hudPosition){
            case TOP_LEFT -> {
                pxX = Config.hudXOffset;
                pxY = Config.hudYOffset;
            }
            case TOP_RIGHT -> {
                pxX = sw - bodyWpx - Config.hudXOffset;
                pxY = Config.hudYOffset;
            }
            case BOTTOM_LEFT -> {
                pxX = Config.hudXOffset;
                pxY = sh - bodyHpx - Config.hudYOffset;
            }
            case BOTTOM_RIGHT -> {
                pxX = sw - bodyWpx - Config.hudXOffset;
                pxY = sh - bodyHpx - Config.hudYOffset;
            }
        }
        startX = Math.round(pxX * inv);
        startY = Math.round(pxY * inv);
    }

    private float getReviveProgressClient() {
        MinecraftClient mc = MinecraftClient.getInstance();
        PlayerEntity player = mc.player;
        if (player == null) return 0f;
        if (!player.isUsingItem()) return 0f;
        ItemStack active = player.getActiveItem();
        if (active == null) return 0f;
        Item item = active.getItem();
        // Only show for our reviving items
        if (!(item instanceof MedkitItem || item instanceof UpgradedMedkitItem || item instanceof TraumaKitItem)) return 0f;
        int total = item.getMaxUseTime(active);
        int left = player.getItemUseTimeLeft();
        if (total <= 0) return 0f;
        int elapsed = Math.max(0, total - left);
        float p = (float) elapsed / (float) total;
        if (p <= 0f || p > 1.0001f) return 0f;
        return p;
    }

    private void drawReviveProgress(DrawContext ctx, float progress) {
        MinecraftClient mc = MinecraftClient.getInstance();
        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();
        int width = 80;
        int height = 6;
        int x = (sw - width) / 2;
        int y = (sh / 2) + 10;

        int bg = 0xAA000000;      // semi-opaque black
        int fg = 0xFF4CAF50;      // green
        int border = 0xCCFFFFFF;  // soft white border

        // Border
        ctx.fill(x - 1, y - 1, x + width + 1, y, border);
        ctx.fill(x - 1, y + height, x + width + 1, y + height + 1, border);
        ctx.fill(x - 1, y, x, y + height, border);
        ctx.fill(x + width, y, x + width + 1, y + height, border);

        // Background
        ctx.fill(x, y, x + width, y + height, bg);

        // Foreground (progress = fuller means closer to finishing)
        int filled = (int)(width * Math.min(Math.max(progress, 0f), 1f));
        ctx.fill(x, y, x + filled, y + height, fg);
    }
}
