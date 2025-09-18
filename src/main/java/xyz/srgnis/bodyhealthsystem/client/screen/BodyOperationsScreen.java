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

    public BodyOperationsScreen(BodyOperationsScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        // Make background a bit larger than the body grid
        this.backgroundWidth = Math.max(176, GUIConstants.SCALED_BODY_WIDTH + 24);
        this.backgroundHeight = Math.max(166, GUIConstants.SCALED_BODY_HEIGHT + 36);
    }

    @Override
    protected void init() {
        super.init();
        addPartButtons();
    }

    private void addPartButtons() {
        this.clearChildren();
        LivingEntity target = this.handler.getEntity();
        if (target == null) return;
        boolean allowClick = !this.handler.getItemStack().isEmpty();

        int startX = (this.width - this.backgroundWidth) / 2;
        int startY = (this.height - this.backgroundHeight) / 2;
        int baseX = startX + (this.backgroundWidth - GUIConstants.SCALED_BODY_WIDTH) / 2;
        int baseY = startY + (this.backgroundHeight - GUIConstants.SCALED_BODY_HEIGHT) / 2;

        addDrawableChild(new PartButton(PlayerBodyParts.HEAD, baseX + GUIConstants.SCALED_HEAD_X_OFFSET, baseY + GUIConstants.SCALED_HEAD_Y_OFFSET, GUIConstants.SCALED_HEAD_WIDTH, GUIConstants.SCALED_HEAD_HEIGHT, allowClick));
        addDrawableChild(new PartButton(PlayerBodyParts.LEFT_ARM, baseX + GUIConstants.SCALED_LEFT_ARM_X_OFFSET, baseY + GUIConstants.SCALED_LEFT_ARM_Y_OFFSET, GUIConstants.SCALED_LEFT_ARM_WIDTH, GUIConstants.SCALED_LEFT_ARM_HEIGHT, allowClick));
        addDrawableChild(new PartButton(PlayerBodyParts.TORSO, baseX + GUIConstants.SCALED_TORSO_X_OFFSET, baseY + GUIConstants.SCALED_TORSO_Y_OFFSET, GUIConstants.SCALED_TORSO_WIDTH, GUIConstants.SCALED_TORSO_HEIGHT, allowClick));
        addDrawableChild(new PartButton(PlayerBodyParts.RIGHT_ARM, baseX + GUIConstants.SCALED_RIGHT_ARM_X_OFFSET, baseY + GUIConstants.SCALED_RIGHT_ARM_Y_OFFSET, GUIConstants.SCALED_RIGHT_ARM_WIDTH, GUIConstants.SCALED_RIGHT_ARM_HEIGHT, allowClick));
        addDrawableChild(new PartButton(PlayerBodyParts.LEFT_LEG, baseX + GUIConstants.SCALED_LEFT_LEG_X_OFFSET, baseY + GUIConstants.SCALED_LEFT_LEG_Y_OFFSET, GUIConstants.SCALED_LEFT_LEG_WIDTH, GUIConstants.SCALED_LEFT_LEG_HEIGHT, allowClick));
        addDrawableChild(new PartButton(PlayerBodyParts.RIGHT_LEG, baseX + GUIConstants.SCALED_RIGHT_LEG_X_OFFSET, baseY + GUIConstants.SCALED_RIGHT_LEG_Y_OFFSET, GUIConstants.SCALED_RIGHT_LEG_WIDTH, GUIConstants.SCALED_RIGHT_LEG_HEIGHT, allowClick));
        addDrawableChild(new PartButton(PlayerBodyParts.LEFT_FOOT, baseX + GUIConstants.SCALED_LEFT_FOOT_X_OFFSET, baseY + GUIConstants.SCALED_LEFT_FOOT_Y_OFFSET, GUIConstants.SCALED_LEFT_FOOT_WIDTH, GUIConstants.SCALED_LEFT_FOOT_HEIGHT, allowClick));
        addDrawableChild(new PartButton(PlayerBodyParts.RIGHT_FOOT, baseX + GUIConstants.SCALED_RIGHT_FOOT_X_OFFSET, baseY + GUIConstants.SCALED_RIGHT_FOOT_Y_OFFSET, GUIConstants.SCALED_RIGHT_FOOT_WIDTH, GUIConstants.SCALED_RIGHT_FOOT_HEIGHT, allowClick));
    }

    @Override
    protected void drawBackground(DrawContext drawContext, float delta, int mouseX, int mouseY) {
        int startX = (this.width - this.backgroundWidth) / 2;
        int startY = (this.height - this.backgroundHeight) / 2;
        // Simple dark panel background
        int bg = 0xAA000000;
        drawContext.fill(startX, startY, startX + this.backgroundWidth, startY + this.backgroundHeight, bg);

        int baseX = startX + (this.backgroundWidth - GUIConstants.SCALED_BODY_WIDTH) / 2;
        int baseY = startY + (this.backgroundHeight - GUIConstants.SCALED_BODY_HEIGHT) / 2;

        // Draw parts with health color backgrounds and bone overlays
        drawPart(drawContext, PlayerBodyParts.HEAD, baseX + GUIConstants.SCALED_HEAD_X_OFFSET, baseY + GUIConstants.SCALED_HEAD_Y_OFFSET, GUIConstants.SCALED_HEAD_WIDTH, GUIConstants.SCALED_HEAD_HEIGHT);
        drawPart(drawContext, PlayerBodyParts.LEFT_ARM, baseX + GUIConstants.SCALED_LEFT_ARM_X_OFFSET, baseY + GUIConstants.SCALED_LEFT_ARM_Y_OFFSET, GUIConstants.SCALED_LEFT_ARM_WIDTH, GUIConstants.SCALED_LEFT_ARM_HEIGHT);
        drawPart(drawContext, PlayerBodyParts.TORSO, baseX + GUIConstants.SCALED_TORSO_X_OFFSET, baseY + GUIConstants.SCALED_TORSO_Y_OFFSET, GUIConstants.SCALED_TORSO_WIDTH, GUIConstants.SCALED_TORSO_HEIGHT);
        drawPart(drawContext, PlayerBodyParts.RIGHT_ARM, baseX + GUIConstants.SCALED_RIGHT_ARM_X_OFFSET, baseY + GUIConstants.SCALED_RIGHT_ARM_Y_OFFSET, GUIConstants.SCALED_RIGHT_ARM_WIDTH, GUIConstants.SCALED_RIGHT_ARM_HEIGHT);
        drawPart(drawContext, PlayerBodyParts.LEFT_LEG, baseX + GUIConstants.SCALED_LEFT_LEG_X_OFFSET, baseY + GUIConstants.SCALED_LEFT_LEG_Y_OFFSET, GUIConstants.SCALED_LEFT_LEG_WIDTH, GUIConstants.SCALED_LEFT_LEG_HEIGHT);
        drawPart(drawContext, PlayerBodyParts.RIGHT_LEG, baseX + GUIConstants.SCALED_RIGHT_LEG_X_OFFSET, baseY + GUIConstants.SCALED_RIGHT_LEG_Y_OFFSET, GUIConstants.SCALED_RIGHT_LEG_WIDTH, GUIConstants.SCALED_RIGHT_LEG_HEIGHT);
        drawPart(drawContext, PlayerBodyParts.LEFT_FOOT, baseX + GUIConstants.SCALED_LEFT_FOOT_X_OFFSET, baseY + GUIConstants.SCALED_LEFT_FOOT_Y_OFFSET, GUIConstants.SCALED_LEFT_FOOT_WIDTH, GUIConstants.SCALED_LEFT_FOOT_HEIGHT);
        drawPart(drawContext, PlayerBodyParts.RIGHT_FOOT, baseX + GUIConstants.SCALED_RIGHT_FOOT_X_OFFSET, baseY + GUIConstants.SCALED_RIGHT_FOOT_Y_OFFSET, GUIConstants.SCALED_RIGHT_FOOT_WIDTH, GUIConstants.SCALED_RIGHT_FOOT_HEIGHT);
    }

    @Override
    protected void drawForeground(DrawContext drawContext, int mouseX, int mouseY) {
        // Title: target name and item (if any)
        int x = this.titleX;
        int y = this.titleY;
        drawContext.drawText(this.textRenderer, this.handler.getEntity().getName(), x, y, 0xFFFFFF, false);
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

    private void drawNumbers(DrawContext ctx) {
        int startX = (this.width - this.backgroundWidth) / 2;
        int startY = (this.height - this.backgroundHeight) / 2;
        int baseX = startX + (this.backgroundWidth - GUIConstants.SCALED_BODY_WIDTH) / 2;
        int baseY = startY + (this.backgroundHeight - GUIConstants.SCALED_BODY_HEIGHT) / 2;
        var tr = MinecraftClient.getInstance().textRenderer;
        int white = 0xFFFFFF;

        BodyPart head = handler.getBody().getPart(PlayerBodyParts.HEAD);
        String headStr = formatHealth(head);
        int headTextW = tr.getWidth(headStr);
        int headX = baseX + GUIConstants.SCALED_HEAD_X_OFFSET + (GUIConstants.SCALED_HEAD_WIDTH - headTextW) / 2;
        int headY = baseY + GUIConstants.SCALED_HEAD_Y_OFFSET + (GUIConstants.SCALED_HEAD_HEIGHT - 9) / 2;
        ctx.drawTextWithShadow(tr, headStr, headX, headY, white);

        BodyPart torso = handler.getBody().getPart(PlayerBodyParts.TORSO);
        String torsoStr = formatHealth(torso);
        int torsoTextW = tr.getWidth(torsoStr);
        int torsoX = baseX + GUIConstants.SCALED_TORSO_X_OFFSET + (GUIConstants.SCALED_TORSO_WIDTH - torsoTextW) / 2;
        int torsoY = baseY + GUIConstants.SCALED_TORSO_Y_OFFSET + (GUIConstants.SCALED_TORSO_HEIGHT - 9) / 2;
        ctx.drawTextWithShadow(tr, torsoStr, torsoX, torsoY, white);

        BodyPart la = handler.getBody().getPart(PlayerBodyParts.LEFT_ARM);
        String laStr = formatHealth(la);
        int laTextW = tr.getWidth(laStr);
        int laX = baseX + GUIConstants.SCALED_LEFT_ARM_X_OFFSET + (GUIConstants.SCALED_LEFT_ARM_WIDTH - laTextW) / 2;
        int laY = baseY + GUIConstants.SCALED_LEFT_ARM_Y_OFFSET + (GUIConstants.SCALED_LEFT_ARM_HEIGHT - 9) / 2;
        ctx.drawTextWithShadow(tr, laStr, laX, laY, white);

        BodyPart ra = handler.getBody().getPart(PlayerBodyParts.RIGHT_ARM);
        String raStr = formatHealth(ra);
        int raTextW = tr.getWidth(raStr);
        int raX = baseX + GUIConstants.SCALED_RIGHT_ARM_X_OFFSET + (GUIConstants.SCALED_RIGHT_ARM_WIDTH - raTextW) / 2;
        int raY = baseY + GUIConstants.SCALED_RIGHT_ARM_Y_OFFSET + (GUIConstants.SCALED_RIGHT_ARM_HEIGHT - 9) / 2;
        ctx.drawTextWithShadow(tr, raStr, raX, raY, white);

        BodyPart ll = handler.getBody().getPart(PlayerBodyParts.LEFT_LEG);
        String llStr = formatHealth(ll);
        int llTextW = tr.getWidth(llStr);
        int llY = baseY + GUIConstants.SCALED_LEFT_LEG_Y_OFFSET + GUIConstants.SCALED_LEFT_LEG_HEIGHT / 2 - 4;
        int llX = baseX + GUIConstants.SCALED_LEFT_LEG_X_OFFSET - llTextW - 2;
        ctx.drawTextWithShadow(tr, llStr, llX, llY, white);

        BodyPart rl = handler.getBody().getPart(PlayerBodyParts.RIGHT_LEG);
        String rlStr = formatHealth(rl);
        int rlY = baseY + GUIConstants.SCALED_RIGHT_LEG_Y_OFFSET + GUIConstants.SCALED_RIGHT_LEG_HEIGHT / 2 - 4;
        int rlX = baseX + GUIConstants.SCALED_RIGHT_LEG_X_OFFSET + GUIConstants.SCALED_RIGHT_LEG_WIDTH + 2;
        ctx.drawTextWithShadow(tr, rlStr, rlX, rlY, white);

        BodyPart lf = handler.getBody().getPart(PlayerBodyParts.LEFT_FOOT);
        BodyPart rf = handler.getBody().getPart(PlayerBodyParts.RIGHT_FOOT);
        String lfStr = formatHealth(lf);
        String rfStr = formatHealth(rf);
        int lfTextW = tr.getWidth(lfStr);
        int rfTextW = tr.getWidth(rfStr);
        int lfX = baseX + GUIConstants.SCALED_LEFT_FOOT_X_OFFSET + (GUIConstants.SCALED_LEFT_FOOT_WIDTH - lfTextW) / 2;
        int rfX = baseX + GUIConstants.SCALED_RIGHT_FOOT_X_OFFSET + (GUIConstants.SCALED_RIGHT_FOOT_WIDTH - rfTextW) / 2;
        int feetY = baseY + GUIConstants.SCALED_LEFT_FOOT_Y_OFFSET + GUIConstants.SCALED_LEFT_FOOT_HEIGHT + 2;
        ctx.drawTextWithShadow(tr, lfStr, lfX, feetY, white);
        ctx.drawTextWithShadow(tr, rfStr, rfX, feetY, white);
    }

    private void drawPart(DrawContext ctx, Identifier partId, int x, int y, int w, int h) {
        BodyPart p = handler.getBody().getPart(partId);
        int color = selectHealthColor(p);
        drawHealthRectangle(ctx, x, y, w, h, color);

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
            BodyOperationsScreen.this.close();
        }

        @Override
        public void renderButton(DrawContext drawContext, int mouseX, int mouseY, float delta) {
            // No extra rendering; the background draws body and bones
        }
    }
}
