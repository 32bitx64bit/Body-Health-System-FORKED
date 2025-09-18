package xyz.srgnis.bodyhealthsystem.client.screen;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import xyz.srgnis.bodyhealthsystem.BHSMain;
import xyz.srgnis.bodyhealthsystem.body.BodyPart;
import xyz.srgnis.bodyhealthsystem.body.player.PlayerBodyParts;
import xyz.srgnis.bodyhealthsystem.constants.GUIConstants;
import xyz.srgnis.bodyhealthsystem.network.ClientNetworking;

import static xyz.srgnis.bodyhealthsystem.util.Draw.drawHealthRectangle;
import static xyz.srgnis.bodyhealthsystem.util.Draw.selectHealthColor;

/**
 * Unified GUI for all health operations:
 * - Shows full body status and bone overlays
 * - When opened with a medkit/medical item, allows selecting a part to treat
 * - When opened without an item, works as a read-only overview
 */
public class BodyOperationsScreen extends HandledScreen<BodyOperationsScreenHandler> {

    private static final Identifier TEX_BONEMAIN = new Identifier(BHSMain.MOD_ID, "textures/gui/bonemain.png");
    private static final Identifier TEX_RIBCAGE = new Identifier(BHSMain.MOD_ID, "textures/gui/ribcage.png");
    private static final Identifier TEX_SKULL = new Identifier(BHSMain.MOD_ID, "textures/gui/skull.png");
    private static final Identifier TEX_FOOT = new Identifier(BHSMain.MOD_ID, "textures/gui/foot.png");

    private static final Identifier TEX_BONEMAIN_BROKEN = new Identifier(BHSMain.MOD_ID, "textures/gui/bonemainbroken.png");
    private static final Identifier TEX_RIBCAGE_BROKEN = new Identifier(BHSMain.MOD_ID, "textures/gui/ribcagebroken.png");
    private static final Identifier TEX_FOOT_BROKEN = new Identifier(BHSMain.MOD_ID, "textures/gui/footbroken.png");

    private static final Identifier TEX_HEALTHSCREEN = new Identifier(BHSMain.MOD_ID, "textures/gui/healthscreen.png");
    private static final Identifier VANILLA_ICONS = new Identifier("textures/gui/icons.png");
    private static final Identifier TEX_HARDCORE_HEART = new Identifier(BHSMain.MOD_ID, "textures/gui/hardcoreheart.png");

    // Texture sheet size (creative/survival GUI sheets are 256x256)
    private static final int SHEET_W = 256;
    private static final int SHEET_H = 256;
    // Region of the GUI inside the sheet (creative-style base ~195x136)
    private static final int GUI_TEX_W = 195;
    private static final int GUI_TEX_H = 136;
    // Screen scale for the whole GUI (makes the window larger/smaller)
    private static final float DRAW_SCALE = 1.6f;

    // Inner layout in logical (texture) pixels
    private static final int LEFT_INSET = 8;
    private static final int RIGHT_INSET = 8;
    private static final int TOP_INSET = 18;
    private static final int BOTTOM_INSET = 16;
    private static final int RIGHT_PANEL_W = 90; // logical space reserved for buttons
    private static final int GAP = 10; // gap between body and right panel

    // Computed body scale (maps GUIConstants.SCALED_* to destination pixels)
    private float bodyScale = 1.0f;

    // Global toggle for bone overlay
    private static boolean BONE_LAYER_ENABLED = true;

    // Session flags for medkit behavior
    private boolean disabledByMedkit = false;
    private boolean boneLayerWasEnabledOnOpen = false;
    private boolean usedMedkit = false;

    public BodyOperationsScreen(BodyOperationsScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        // Window size is a scaled version of the GUI art region
        this.backgroundWidth = Math.round(GUI_TEX_W * DRAW_SCALE);
        this.backgroundHeight = Math.round(GUI_TEX_H * DRAW_SCALE);
    }

    @Override
    protected void init() {
        super.init();
        // If opened with an item (medkit), disable bone layer if it was enabled, remember to restore on successful use
        if (!this.handler.getItemStack().isEmpty() && BONE_LAYER_ENABLED) {
            boneLayerWasEnabledOnOpen = true;
            BONE_LAYER_ENABLED = false;
            disabledByMedkit = true;
        }
        computeBodyScale();
        addWidgets();
    }

    private void addWidgets() {
        this.clearChildren();
        LivingEntity target = this.handler.getEntity();
        if (target == null) return;
        boolean allowClick = !this.handler.getItemStack().isEmpty();

        int startX = (this.width - this.backgroundWidth) / 2;
        int startY = (this.height - this.backgroundHeight) / 2;

        // Compute body base inside the texture region (scaled to screen)
        int left = startX + Math.round(LEFT_INSET * DRAW_SCALE);
        int topRegion = Math.round(TOP_INSET * DRAW_SCALE);
        int bottomRegion = Math.round(BOTTOM_INSET * DRAW_SCALE);
        int top = startY + topRegion;
        int availH = this.backgroundHeight - topRegion - bottomRegion;
        int bodyHpx = Math.round(GUIConstants.SCALED_BODY_HEIGHT * bodyScale);
        int baseY = top + Math.max(0, (availH - bodyHpx) / 2);
        int baseX = left;

        // Part hitboxes scaled to screen pixels
        addDrawableChild(new PartButton(PlayerBodyParts.HEAD, baseX + sx(GUIConstants.SCALED_HEAD_X_OFFSET), baseY + sy(GUIConstants.SCALED_HEAD_Y_OFFSET), sx(GUIConstants.SCALED_HEAD_WIDTH), sy(GUIConstants.SCALED_HEAD_HEIGHT), allowClick));
        addDrawableChild(new PartButton(PlayerBodyParts.LEFT_ARM, baseX + sx(GUIConstants.SCALED_LEFT_ARM_X_OFFSET), baseY + sy(GUIConstants.SCALED_LEFT_ARM_Y_OFFSET), sx(GUIConstants.SCALED_LEFT_ARM_WIDTH), sy(GUIConstants.SCALED_LEFT_ARM_HEIGHT), allowClick));
        addDrawableChild(new PartButton(PlayerBodyParts.TORSO, baseX + sx(GUIConstants.SCALED_TORSO_X_OFFSET), baseY + sy(GUIConstants.SCALED_TORSO_Y_OFFSET), sx(GUIConstants.SCALED_TORSO_WIDTH), sy(GUIConstants.SCALED_TORSO_HEIGHT), allowClick));
        addDrawableChild(new PartButton(PlayerBodyParts.RIGHT_ARM, baseX + sx(GUIConstants.SCALED_RIGHT_ARM_X_OFFSET), baseY + sy(GUIConstants.SCALED_RIGHT_ARM_Y_OFFSET), sx(GUIConstants.SCALED_RIGHT_ARM_WIDTH), sy(GUIConstants.SCALED_RIGHT_ARM_HEIGHT), allowClick));
        addDrawableChild(new PartButton(PlayerBodyParts.LEFT_LEG, baseX + sx(GUIConstants.SCALED_LEFT_LEG_X_OFFSET), baseY + sy(GUIConstants.SCALED_LEFT_LEG_Y_OFFSET), sx(GUIConstants.SCALED_LEFT_LEG_WIDTH), sy(GUIConstants.SCALED_LEFT_LEG_HEIGHT), allowClick));
        addDrawableChild(new PartButton(PlayerBodyParts.RIGHT_LEG, baseX + sx(GUIConstants.SCALED_RIGHT_LEG_X_OFFSET), baseY + sy(GUIConstants.SCALED_RIGHT_LEG_Y_OFFSET), sx(GUIConstants.SCALED_RIGHT_LEG_WIDTH), sy(GUIConstants.SCALED_RIGHT_LEG_HEIGHT), allowClick));
        addDrawableChild(new PartButton(PlayerBodyParts.LEFT_FOOT, baseX + sx(GUIConstants.SCALED_LEFT_FOOT_X_OFFSET), baseY + sy(GUIConstants.SCALED_LEFT_FOOT_Y_OFFSET), sx(GUIConstants.SCALED_LEFT_FOOT_WIDTH), sy(GUIConstants.SCALED_LEFT_FOOT_HEIGHT), allowClick));
        addDrawableChild(new PartButton(PlayerBodyParts.RIGHT_FOOT, baseX + sx(GUIConstants.SCALED_RIGHT_FOOT_X_OFFSET), baseY + sy(GUIConstants.SCALED_RIGHT_FOOT_Y_OFFSET), sx(GUIConstants.SCALED_RIGHT_FOOT_WIDTH), sy(GUIConstants.SCALED_RIGHT_FOOT_HEIGHT), allowClick));

        // Button in right panel
        int rpLeft = startX + Math.round((GUI_TEX_W - RIGHT_INSET - RIGHT_PANEL_W) * DRAW_SCALE);
        int rpTop = startY + Math.round(TOP_INSET * DRAW_SCALE);
        int btnW = Math.round(80 * DRAW_SCALE);
        int btnH = Math.round(20 * DRAW_SCALE);
        int btnX = rpLeft + Math.max(0, (Math.round(RIGHT_PANEL_W * DRAW_SCALE) - btnW) / 2);
        int btnY = rpTop;
        addDrawableChild(ButtonWidget.builder(Text.literal("Bone Layer"), b -> {
            BONE_LAYER_ENABLED = !BONE_LAYER_ENABLED;
        }).dimensions(btnX, btnY, btnW, btnH).build());
    }

    // Compute the body scale so it fits in the left column
    private void computeBodyScale() {
        int logicalAvailW = GUI_TEX_W - LEFT_INSET - RIGHT_PANEL_W - GAP - RIGHT_INSET;
        int logicalAvailH = GUI_TEX_H - TOP_INSET - BOTTOM_INSET;
        float sx = (float) logicalAvailW / (float) GUIConstants.SCALED_BODY_WIDTH;
        float sy = (float) logicalAvailH / (float) GUIConstants.SCALED_BODY_HEIGHT;
        bodyScale = Math.min(sx, sy);
        // Keep within a comfortable range
        if (bodyScale > 1.2f) bodyScale = 1.2f;
        if (bodyScale < 0.7f) bodyScale = 0.7f;
        // Multiply by screen scale for pixels
        bodyScale *= DRAW_SCALE;
    }

    private int sx(int v) { return Math.round(v * bodyScale); }
    private int sy(int v) { return Math.round(v * bodyScale); }

    @Override
    protected void drawBackground(DrawContext drawContext, float delta, int mouseX, int mouseY) {
        int startX = (this.width - this.backgroundWidth) / 2;
        int startY = (this.height - this.backgroundHeight) / 2;

        // Draw the GUI region (195x136) scaled to background size, without sampling beyond it
        drawContext.drawTexture(TEX_HEALTHSCREEN, startX, startY, this.backgroundWidth, this.backgroundHeight, 0.0F, 0.0F, GUI_TEX_W, GUI_TEX_H, SHEET_W, SHEET_H);

        // Body origin inside the texture region
        int baseX = startX + Math.round(LEFT_INSET * DRAW_SCALE);
        int topRegion = Math.round(TOP_INSET * DRAW_SCALE);
        int bottomRegion = Math.round(BOTTOM_INSET * DRAW_SCALE);
        int top = startY + topRegion;
        int availH = this.backgroundHeight - topRegion - bottomRegion;
        int bodyHpx = Math.round(GUIConstants.SCALED_BODY_HEIGHT * bodyScale);
        int baseY = top + Math.max(0, (availH - bodyHpx) / 2);

        // Draw parts with health color backgrounds and optional bone overlays
        drawPart(drawContext, PlayerBodyParts.HEAD, baseX + sx(GUIConstants.SCALED_HEAD_X_OFFSET), baseY + sy(GUIConstants.SCALED_HEAD_Y_OFFSET), sx(GUIConstants.SCALED_HEAD_WIDTH), sy(GUIConstants.SCALED_HEAD_HEIGHT));
        drawPart(drawContext, PlayerBodyParts.LEFT_ARM, baseX + sx(GUIConstants.SCALED_LEFT_ARM_X_OFFSET), baseY + sy(GUIConstants.SCALED_LEFT_ARM_Y_OFFSET), sx(GUIConstants.SCALED_LEFT_ARM_WIDTH), sy(GUIConstants.SCALED_LEFT_ARM_HEIGHT));
        drawPart(drawContext, PlayerBodyParts.TORSO, baseX + sx(GUIConstants.SCALED_TORSO_X_OFFSET), baseY + sy(GUIConstants.SCALED_TORSO_Y_OFFSET), sx(GUIConstants.SCALED_TORSO_WIDTH), sy(GUIConstants.SCALED_TORSO_HEIGHT));
        drawPart(drawContext, PlayerBodyParts.RIGHT_ARM, baseX + sx(GUIConstants.SCALED_RIGHT_ARM_X_OFFSET), baseY + sy(GUIConstants.SCALED_RIGHT_ARM_Y_OFFSET), sx(GUIConstants.SCALED_RIGHT_ARM_WIDTH), sy(GUIConstants.SCALED_RIGHT_ARM_HEIGHT));
        drawPart(drawContext, PlayerBodyParts.LEFT_LEG, baseX + sx(GUIConstants.SCALED_LEFT_LEG_X_OFFSET), baseY + sy(GUIConstants.SCALED_LEFT_LEG_Y_OFFSET), sx(GUIConstants.SCALED_LEFT_LEG_WIDTH), sy(GUIConstants.SCALED_LEFT_LEG_HEIGHT));
        drawPart(drawContext, PlayerBodyParts.RIGHT_LEG, baseX + sx(GUIConstants.SCALED_RIGHT_LEG_X_OFFSET), baseY + sy(GUIConstants.SCALED_RIGHT_LEG_Y_OFFSET), sx(GUIConstants.SCALED_RIGHT_LEG_WIDTH), sy(GUIConstants.SCALED_RIGHT_LEG_HEIGHT));
        drawPart(drawContext, PlayerBodyParts.LEFT_FOOT, baseX + sx(GUIConstants.SCALED_LEFT_FOOT_X_OFFSET), baseY + sy(GUIConstants.SCALED_LEFT_FOOT_Y_OFFSET), sx(GUIConstants.SCALED_LEFT_FOOT_WIDTH), sy(GUIConstants.SCALED_LEFT_FOOT_HEIGHT));
        drawPart(drawContext, PlayerBodyParts.RIGHT_FOOT, baseX + sx(GUIConstants.SCALED_RIGHT_FOOT_X_OFFSET), baseY + sy(GUIConstants.SCALED_RIGHT_FOOT_Y_OFFSET), sx(GUIConstants.SCALED_RIGHT_FOOT_WIDTH), sy(GUIConstants.SCALED_RIGHT_FOOT_HEIGHT));
    }

    @Override
    protected void drawForeground(DrawContext drawContext, int mouseX, int mouseY) {
        // Title: target name and item (if any)
        int x = this.titleX;
        int y = this.titleY;
        Text name = this.handler.getEntity().getName();
        drawContext.drawText(this.textRenderer, name, x, y, 0xFFFFFF, false);
        // Hardcore indicator: draw a small heart next to the player name when in hardcore
        try {
            boolean hardcore = this.handler.getEntity().getWorld().getLevelProperties().isHardcore();
            if (hardcore) {
                int nameW = this.textRenderer.getWidth(name);
                int heartSize = Math.max(7, Math.round(7 * DRAW_SCALE));
                int hx = x + nameW + 4;
                int hy = y + Math.max(0, (this.textRenderer.fontHeight - heartSize) / 2);
                // Use our 7x7 hardcoreheart.png
                drawContext.drawTexture(TEX_HARDCORE_HEART, hx, hy, heartSize, heartSize, 0.0F, 0.0F, 7, 7, 7, 7);
            }
        } catch (Exception ignored) {}
        ItemStack item = this.handler.getItemStack();
        if (!item.isEmpty()) {
            String using = item.getName().getString();
            int w = textRenderer.getWidth(using);
            drawContext.drawText(this.textRenderer, Text.literal(using), this.backgroundWidth - 8 - w, y, 0xC0C0C0, false);
        }
    }

    @Override
    public void render(DrawContext drawContext, int mouseX, int mouseY, float delta) {
        this.renderBackground(drawContext);
        super.render(drawContext, mouseX, mouseY, delta);
        // Numbers overlay
        drawNumbers(drawContext);
        // Tooltips for parts
        for (var child : this.children()) {
            if (child instanceof PartButton b && b.isMouseOver(mouseX, mouseY)) {
                BodyPart part = handler.getBody().getPart(b.partId);
                if (part != null) {
                    java.util.List<Text> list = new java.util.ArrayList<>();
                    list.add(Text.literal(part.getIdentifier().getPath()));
                    list.add(Text.literal("" + Math.round(part.getHealth()) + "/" + Math.round(part.getMaxHealth())));
                    if (part.isBroken()) list.add(Text.literal("Broken"));
                    drawContext.drawTooltip(this.textRenderer, list, mouseX, mouseY);
                }
            }
        }
        this.drawMouseoverTooltip(drawContext, mouseX, mouseY);
    }

    @Override
    public void close() {
        // If this screen was opened with a medkit and we auto-disabled the bone layer,
        // restore it on close to the previous state (ensures bones reappear next open).
        if (disabledByMedkit && boneLayerWasEnabledOnOpen) {
            BONE_LAYER_ENABLED = true;
        }
        super.close();
    }

    private void drawNumbers(DrawContext ctx) {
        int startX = (this.width - this.backgroundWidth) / 2;
        int startY = (this.height - this.backgroundHeight) / 2;
        int baseX = startX + Math.round(LEFT_INSET * DRAW_SCALE);
        int topRegion = Math.round(TOP_INSET * DRAW_SCALE);
        int bottomRegion = Math.round(BOTTOM_INSET * DRAW_SCALE);
        int top = startY + topRegion;
        int availH = this.backgroundHeight - topRegion - bottomRegion;
        int bodyHpx = Math.round(GUIConstants.SCALED_BODY_HEIGHT * bodyScale);
        int baseY = top + Math.max(0, (availH - bodyHpx) / 2);
        var tr = MinecraftClient.getInstance().textRenderer;
        int white = 0xFFFFFF;

        BodyPart head = handler.getBody().getPart(PlayerBodyParts.HEAD);
        String headStr = formatHealth(head);
        int headTextW = tr.getWidth(headStr);
        int headX = baseX + sx(GUIConstants.SCALED_HEAD_X_OFFSET) + (sx(GUIConstants.SCALED_HEAD_WIDTH) - headTextW) / 2;
        int headY = baseY + sy(GUIConstants.SCALED_HEAD_Y_OFFSET) + (sy(GUIConstants.SCALED_HEAD_HEIGHT) - 9) / 2;
        ctx.drawTextWithShadow(tr, headStr, headX, headY, white);

        BodyPart torso = handler.getBody().getPart(PlayerBodyParts.TORSO);
        String torsoStr = formatHealth(torso);
        int torsoTextW = tr.getWidth(torsoStr);
        int torsoX = baseX + sx(GUIConstants.SCALED_TORSO_X_OFFSET) + (sx(GUIConstants.SCALED_TORSO_WIDTH) - torsoTextW) / 2;
        int torsoY = baseY + sy(GUIConstants.SCALED_TORSO_Y_OFFSET) + (sy(GUIConstants.SCALED_TORSO_HEIGHT) - 9) / 2;
        ctx.drawTextWithShadow(tr, torsoStr, torsoX, torsoY, white);

        BodyPart la = handler.getBody().getPart(PlayerBodyParts.LEFT_ARM);
        String laStr = formatHealth(la);
        int laTextW = tr.getWidth(laStr);
        int laX = baseX + sx(GUIConstants.SCALED_LEFT_ARM_X_OFFSET) + (sx(GUIConstants.SCALED_LEFT_ARM_WIDTH) - laTextW) / 2;
        int laY = baseY + sy(GUIConstants.SCALED_LEFT_ARM_Y_OFFSET) + (sy(GUIConstants.SCALED_LEFT_ARM_HEIGHT) - 9) / 2;
        ctx.drawTextWithShadow(tr, laStr, laX, laY, white);

        BodyPart ra = handler.getBody().getPart(PlayerBodyParts.RIGHT_ARM);
        String raStr = formatHealth(ra);
        int raTextW = tr.getWidth(raStr);
        int raX = baseX + sx(GUIConstants.SCALED_RIGHT_ARM_X_OFFSET) + (sx(GUIConstants.SCALED_RIGHT_ARM_WIDTH) - raTextW) / 2;
        int raY = baseY + sy(GUIConstants.SCALED_RIGHT_ARM_Y_OFFSET) + (sy(GUIConstants.SCALED_RIGHT_ARM_HEIGHT) - 9) / 2;
        ctx.drawTextWithShadow(tr, raStr, raX, raY, white);

        BodyPart ll = handler.getBody().getPart(PlayerBodyParts.LEFT_LEG);
        String llStr = formatHealth(ll);
        int llTextW = tr.getWidth(llStr);
        int llY = baseY + sy(GUIConstants.SCALED_LEFT_LEG_Y_OFFSET) + sy(GUIConstants.SCALED_LEFT_LEG_HEIGHT) / 2 - 4;
        int llX = baseX + sx(GUIConstants.SCALED_LEFT_LEG_X_OFFSET) - llTextW - 2;
        ctx.drawTextWithShadow(tr, llStr, llX, llY, white);

        BodyPart rl = handler.getBody().getPart(PlayerBodyParts.RIGHT_LEG);
        String rlStr = formatHealth(rl);
        int rlY = baseY + sy(GUIConstants.SCALED_RIGHT_LEG_Y_OFFSET) + sy(GUIConstants.SCALED_RIGHT_LEG_HEIGHT) / 2 - 4;
        int rlX = baseX + sx(GUIConstants.SCALED_RIGHT_LEG_X_OFFSET) + sx(GUIConstants.SCALED_RIGHT_LEG_WIDTH) + 2;
        ctx.drawTextWithShadow(tr, rlStr, rlX, rlY, white);

        BodyPart lf = handler.getBody().getPart(PlayerBodyParts.LEFT_FOOT);
        BodyPart rf = handler.getBody().getPart(PlayerBodyParts.RIGHT_FOOT);
        String lfStr = formatHealth(lf);
        String rfStr = formatHealth(rf);
        int lfTextW = tr.getWidth(lfStr);
        int rfTextW = tr.getWidth(rfStr);
        int lfX = baseX + sx(GUIConstants.SCALED_LEFT_FOOT_X_OFFSET) + (sx(GUIConstants.SCALED_LEFT_FOOT_WIDTH) - lfTextW) / 2;
        int rfX = baseX + sx(GUIConstants.SCALED_RIGHT_FOOT_X_OFFSET) + (sx(GUIConstants.SCALED_RIGHT_FOOT_WIDTH) - rfTextW) / 2;
        int feetY = baseY + sy(GUIConstants.SCALED_LEFT_FOOT_Y_OFFSET) + sy(GUIConstants.SCALED_LEFT_FOOT_HEIGHT) + 2;
        ctx.drawTextWithShadow(tr, lfStr, lfX, feetY, white);
        ctx.drawTextWithShadow(tr, rfStr, rfX, feetY, white);
    }

    private void drawPart(DrawContext ctx, Identifier partId, int x, int y, int w, int h) {
        BodyPart p = handler.getBody().getPart(partId);
        int color = selectHealthColor(p);
        drawHealthRectangle(ctx, x, y, w, h, color);

        if (!BONE_LAYER_ENABLED) return;

        boolean broken = p.isBroken();
        Identifier tex = selectBoneTexture(partId, broken);
        if (isDrawableResource(tex)) {
            if (partId.equals(PlayerBodyParts.LEFT_ARM) || partId.equals(PlayerBodyParts.RIGHT_ARM)) {
                int topH = h / 2;
                int bottomH = h - topH;
                if (broken) {
                    Boolean topBroken = p.getBrokenTopHalf();
                    if (topBroken == null) topBroken = Boolean.TRUE;
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
                ctx.drawTexture(tex, x, y, w, h, 0.0F, 0.0F, 16, 16, 16, 16);
            }
        }
    }

    private boolean isDrawableResource(Identifier id) {
        var rm = MinecraftClient.getInstance().getResourceManager();
        try {
            var opt = rm.getResource(id);
            if (opt.isEmpty()) return false;
            try (var is = opt.get().getInputStream()) { return is.read() != -1; }
        } catch (Exception e) { return false; }
    }

    private Identifier selectBoneTexture(Identifier partId, boolean broken) {
        if (partId.equals(PlayerBodyParts.HEAD)) return TEX_SKULL;
        if (partId.equals(PlayerBodyParts.TORSO)) return broken ? TEX_RIBCAGE_BROKEN : TEX_RIBCAGE;
        if (partId.equals(PlayerBodyParts.LEFT_FOOT) || partId.equals(PlayerBodyParts.RIGHT_FOOT)) return broken ? TEX_FOOT_BROKEN : TEX_FOOT;
        return broken ? TEX_BONEMAIN_BROKEN : TEX_BONEMAIN;
    }

    private static String formatHealth(BodyPart p) { return String.valueOf(Math.round(p.getHealth())); }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Allow the same keybind (R) to close the GUI when it is open
        if (keyCode == GLFW.GLFW_KEY_R) {
            this.close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private class PartButton extends ButtonWidget {
        private final Identifier partId;
        private final boolean allowClick;

        public PartButton(Identifier partId, int x, int y, int width, int height, boolean allowClick) {
            super(x, y, width, height, Text.empty(), btn -> {}, ButtonWidget.DEFAULT_NARRATION_SUPPLIER);
            this.partId = partId;
            this.allowClick = allowClick;
        }

        @Override
        public void onPress() {
            if (!allowClick) return;
            BodyPart part = BodyOperationsScreen.this.handler.getBody().getPart(partId);
            if (part == null || !part.isDamaged()) return;
            ClientNetworking.useHealingItem(BodyOperationsScreen.this.handler.getEntity(), part.getIdentifier(), BodyOperationsScreen.this.handler.getItemStack());
            usedMedkit = true;
            // Restore bone layer if we auto-disabled it when opening for medkit
            if (disabledByMedkit && boneLayerWasEnabledOnOpen) {
                BONE_LAYER_ENABLED = true;
            }
            BodyOperationsScreen.this.close();
        }

        @Override
        public void renderButton(DrawContext drawContext, int mouseX, int mouseY, float delta) {
            // No extra rendering; the background draws body and bones
        }
    }
}
