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
        // Revive progress bar under crosshair (no texture needed)
        float reviveProgress = getReviveProgressClient();
        if (reviveProgress > 0f) {
            drawReviveProgress(drawContext, reviveProgress);
        }

        // Hide the always-on HUD when the player inventory screen is open to avoid confusion with the inventory-side HUD
        // Only hide if the inventory HUD is enabled in config
        if (xyz.srgnis.bodyhealthsystem.config.Config.showInventoryBodyHud &&
                MinecraftClient.getInstance().currentScreen instanceof net.minecraft.client.gui.screen.ingame.InventoryScreen) {
            return;
        }

        setHudCords();
        BodyProvider player = (BodyProvider)MinecraftClient.getInstance().player;

        if (player != null) {
            if (MinecraftClient.getInstance().interactionManager.hasStatusBars()) {
                int color;
                drawContext.getMatrices().push();
                drawContext.getMatrices().translate(0,0,-1);
                drawContext.getMatrices().scale(Config.hudScale, Config.hudScale, 1);
                //head
                color = selectHealthColor(player.getBody().getPart(PlayerBodyParts.HEAD));
                drawHealthRectangle(drawContext, startX+ GUIConstants.HEAD_X_OFFSET, startY+ GUIConstants.HEAD_Y_OFFSET, GUIConstants.HEAD_WIDTH, GUIConstants.HEAD_HEIGHT, color);
                //arm
                color = selectHealthColor(player.getBody().getPart(PlayerBodyParts.LEFT_ARM));
                drawHealthRectangle(drawContext, startX+ GUIConstants.LEFT_ARM_X_OFFSET, startY+ GUIConstants.LEFT_ARM_Y_OFFSET, GUIConstants.LEFT_ARM_WIDTH, GUIConstants.LEFT_ARM_HEIGHT, color);
                //torso
                color = selectHealthColor(player.getBody().getPart(PlayerBodyParts.TORSO));
                drawHealthRectangle(drawContext, startX+ GUIConstants.TORSO_X_OFFSET, startY+ GUIConstants.TORSO_Y_OFFSET, GUIConstants.TORSO_WIDTH, GUIConstants.TORSO_HEIGHT, color);
                //arm
                color = selectHealthColor(player.getBody().getPart(PlayerBodyParts.RIGHT_ARM));
                drawHealthRectangle(drawContext, startX+ GUIConstants.RIGHT_ARM_X_OFFSET, startY+ GUIConstants.RIGHT_ARM_Y_OFFSET, GUIConstants.RIGHT_ARM_WIDTH, GUIConstants.RIGHT_ARM_HEIGHT, color);
                //legs
                color = selectHealthColor(player.getBody().getPart(PlayerBodyParts.LEFT_LEG));
                drawHealthRectangle(drawContext, startX+ GUIConstants.LEFT_LEG_X_OFFSET, startY+ GUIConstants.LEFT_LEG_Y_OFFSET, GUIConstants.LEFT_LEG_WIDTH, GUIConstants.LEFT_LEG_HEIGHT, color);
                color = selectHealthColor(player.getBody().getPart(PlayerBodyParts.RIGHT_LEG));
                drawHealthRectangle(drawContext, startX+ GUIConstants.RIGHT_LEG_X_OFFSET, startY+ GUIConstants.RIGHT_LEG_Y_OFFSET, GUIConstants.RIGHT_LEG_WIDTH, GUIConstants.RIGHT_LEG_HEIGHT, color);
                //foot
                color = selectHealthColor(player.getBody().getPart(PlayerBodyParts.LEFT_FOOT));
                drawHealthRectangle(drawContext, startX+ GUIConstants.LEFT_FOOT_X_OFFSET, startY+ GUIConstants.LEFT_FOOT_Y_OFFSET, GUIConstants.LEFT_FOOT_WIDTH, GUIConstants.LEFT_FOOT_HEIGHT, color);
                color = selectHealthColor(player.getBody().getPart(PlayerBodyParts.RIGHT_FOOT));
                drawHealthRectangle(drawContext, startX+ GUIConstants.RIGHT_FOOT_X_OFFSET, startY+ GUIConstants.RIGHT_FOOT_Y_OFFSET, GUIConstants.RIGHT_FOOT_WIDTH, GUIConstants.RIGHT_FOOT_HEIGHT, color);
                drawContext.getMatrices().pop();
            }
        }
    }

    private static void setHudCords(){
        switch (Config.hudPosition){
            case TOP_LEFT:
                startX = Config.hudXOffset;
                startY = Config.hudYOffset;
                break;
            case TOP_RIGHT:
                startX = MinecraftClient.getInstance().getWindow().getScaledWidth()- GUIConstants.BODY_WIDTH-Config.hudXOffset;
                startY = Config.hudYOffset;
                break;
            case BOTTOM_LEFT:
                startX = Config.hudXOffset;
                startY = MinecraftClient.getInstance().getWindow().getScaledHeight()- GUIConstants.BODY_HEIGHT-Config.hudYOffset;
                break;
            case BOTTOM_RIGHT:
                startX = MinecraftClient.getInstance().getWindow().getScaledWidth()- GUIConstants.BODY_WIDTH-Config.hudXOffset;
                startY = MinecraftClient.getInstance().getWindow().getScaledHeight()- GUIConstants.BODY_HEIGHT-Config.hudYOffset;
                break;
        }
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
