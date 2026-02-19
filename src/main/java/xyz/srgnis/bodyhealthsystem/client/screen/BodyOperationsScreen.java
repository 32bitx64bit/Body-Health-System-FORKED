package xyz.srgnis.bodyhealthsystem.client.screen;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.LivingEntity;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import xyz.srgnis.bodyhealthsystem.BHSMain;
import xyz.srgnis.bodyhealthsystem.body.Body;
import xyz.srgnis.bodyhealthsystem.body.BodyPart;
import xyz.srgnis.bodyhealthsystem.body.player.PlayerBodyParts;
import xyz.srgnis.bodyhealthsystem.constants.GUIConstants;
import xyz.srgnis.bodyhealthsystem.network.ClientNetworking;
import gavinx.temperatureapi.api.BodyTemperatureAPI;

import static xyz.srgnis.bodyhealthsystem.util.Draw.drawHealthRectangle;
import static xyz.srgnis.bodyhealthsystem.util.Draw.selectHealthColor;
import static xyz.srgnis.bodyhealthsystem.util.Draw.selectTemperatureColor;

/**
 * Unified GUI for all health operations:
 * - Shows full body status and bone overlays
 * - When opened with a medkit/medical item, allows selecting a part to treat
 * - When opened without an item, works as a read-only overview
 */
public class BodyOperationsScreen extends HandledScreen<BodyOperationsScreenHandler> {

    // Cache for texture availability to avoid per-frame resource lookups
    private static final java.util.Map<Identifier, Boolean> TEX_AVAILABLE = new java.util.HashMap<>();
    private static boolean texCacheWarmed = false;

    private static void warmTextureCache() {
        if (texCacheWarmed) return;
        cacheTexture(TEX_BONEMAIN);
        cacheTexture(TEX_RIBCAGE);
        cacheTexture(TEX_SKULL);
        cacheTexture(TEX_FOOT);
        cacheTexture(TEX_BONEMAIN_BROKEN);
        cacheTexture(TEX_RIBCAGE_BROKEN);
        cacheTexture(TEX_FOOT_BROKEN);
        cacheTexture(TEX_WOUND_SMALL);
        cacheTexture(TEX_WOUND_LARGE);
        texCacheWarmed = true;
    }

    private static void cacheTexture(Identifier id) {
        var rm = MinecraftClient.getInstance().getResourceManager();
        boolean ok = false;
        try {
            var opt = rm.getResource(id);
            ok = opt.isPresent();
        } catch (Exception ignored) {}
        TEX_AVAILABLE.put(id, ok);
    }

    private static boolean isTextureAvailable(Identifier id) {
        Boolean b = TEX_AVAILABLE.get(id);
        if (b != null) return b;
        cacheTexture(id);
        return TEX_AVAILABLE.getOrDefault(id, false);
    }

    // Cached part rectangles (recomputed on init/resize)
    private static final class PartRect { int x,y,w,h; PartRect(int x,int y,int w,int h){this.x=x;this.y=y;this.w=w;this.h=h;} }
    private final java.util.Map<Identifier, PartRect> partRects = new java.util.HashMap<>();


    private static final Identifier TEX_BONEMAIN = new Identifier(BHSMain.MOD_ID, "textures/gui/bonemain.png");
    private static final Identifier TEX_RIBCAGE = new Identifier(BHSMain.MOD_ID, "textures/gui/ribcage.png");
    private static final Identifier TEX_SKULL = new Identifier(BHSMain.MOD_ID, "textures/gui/skull.png");
    private static final Identifier TEX_FOOT = new Identifier(BHSMain.MOD_ID, "textures/gui/foot.png");
    // Wound overlays
    private static final Identifier TEX_WOUND_SMALL = new Identifier(BHSMain.MOD_ID, "textures/gui/wound_small.png");
    private static final Identifier TEX_WOUND_LARGE = new Identifier(BHSMain.MOD_ID, "textures/gui/wound_large.png");

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

    // Instance toggle for bone overlay (was incorrectly static - each screen should have its own state)
    private boolean boneLayerEnabled = true;

    // Session flags for medkit behavior
    private boolean disabledByMedkit = false;
    private boolean boneLayerWasEnabledOnOpen = false;

    // Small vertical shift to keep feet labels within the GUI
    private static final float BODY_Y_SHIFT_LOGICAL = 5.0f; // logical pixels, scaled by DRAW_SCALE

    // Temperature view state
    private boolean showTemperature = false;
    private double bodyTempC = BodyTemperatureAPI.NORMAL_BODY_TEMP_C;
    private long lastUpdateNanos = 0L;
    private long lastTempRequestNanos = 0L;
    private ButtonWidget tempToggleBtn;
    private ButtonWidget boneToggleBtn;

    public BodyOperationsScreen(BodyOperationsScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        // Window size is a scaled version of the GUI art region
        this.backgroundWidth = Math.round(GUI_TEX_W * DRAW_SCALE);
        this.backgroundHeight = Math.round(GUI_TEX_H * DRAW_SCALE);
    }

    @Override
    protected void init() {
        super.init();
        // Enforce bone system disabled: force bone layer off and block medkit override
        if (!xyz.srgnis.bodyhealthsystem.config.Config.enableBoneSystem) {
            boneLayerEnabled = false;
            boneLayerWasEnabledOnOpen = false;
            disabledByMedkit = false;
        }
        // If opened with an item (medkit), disable bone layer if it was enabled, remember to restore on successful use
        if (!this.handler.getItemStack().isEmpty() && boneLayerEnabled) {
            boneLayerWasEnabledOnOpen = true;
            boneLayerEnabled = false;
            disabledByMedkit = true;
        }
        // Request fresh body data from server so per-part buckets are up-to-date
        if (this.handler.getEntity() != null) {
            var buf = PacketByteBufs.create();
            buf.writeInt(this.handler.getEntity().getId());
            ClientPlayNetworking.send(BHSMain.id("data_request"), buf);
        }
        computeBodyScale();
        warmTextureCache();
        computeGeometry();
        addWidgets();
        lastUpdateNanos = System.nanoTime();
        bodyTempC = BodyTemperatureAPI.NORMAL_BODY_TEMP_C;
    }

    private void computeGeometry() {
        partRects.clear();
        int startX = (this.width - this.backgroundWidth) / 2;
        int startY = (this.height - this.backgroundHeight) / 2;
        int left = startX + Math.round(LEFT_INSET * DRAW_SCALE);
        int topRegion = Math.round(TOP_INSET * DRAW_SCALE);
        int bottomRegion = Math.round(BOTTOM_INSET * DRAW_SCALE);
        int top = startY + topRegion;
        int availH = this.backgroundHeight - topRegion - bottomRegion;
        int bodyHpx = Math.round(GUIConstants.SCALED_BODY_HEIGHT * bodyScale);
        int shiftY = Math.round(BODY_Y_SHIFT_LOGICAL * DRAW_SCALE);
        int baseY = top + Math.max(0, (availH - bodyHpx) / 2) - shiftY;
        int baseX = left;
        // Build rects
        partRects.put(PlayerBodyParts.HEAD, new PartRect(baseX + sx(GUIConstants.SCALED_HEAD_X_OFFSET), baseY + sy(GUIConstants.SCALED_HEAD_Y_OFFSET), sx(GUIConstants.SCALED_HEAD_WIDTH), sy(GUIConstants.SCALED_HEAD_HEIGHT)));
        partRects.put(PlayerBodyParts.LEFT_ARM, new PartRect(baseX + sx(GUIConstants.SCALED_LEFT_ARM_X_OFFSET), baseY + sy(GUIConstants.SCALED_LEFT_ARM_Y_OFFSET), sx(GUIConstants.SCALED_LEFT_ARM_WIDTH), sy(GUIConstants.SCALED_LEFT_ARM_HEIGHT)));
        partRects.put(PlayerBodyParts.TORSO, new PartRect(baseX + sx(GUIConstants.SCALED_TORSO_X_OFFSET), baseY + sy(GUIConstants.SCALED_TORSO_Y_OFFSET), sx(GUIConstants.SCALED_TORSO_WIDTH), sy(GUIConstants.SCALED_TORSO_HEIGHT)));
        partRects.put(PlayerBodyParts.RIGHT_ARM, new PartRect(baseX + sx(GUIConstants.SCALED_RIGHT_ARM_X_OFFSET), baseY + sy(GUIConstants.SCALED_RIGHT_ARM_Y_OFFSET), sx(GUIConstants.SCALED_RIGHT_ARM_WIDTH), sy(GUIConstants.SCALED_RIGHT_ARM_HEIGHT)));
        partRects.put(PlayerBodyParts.LEFT_LEG, new PartRect(baseX + sx(GUIConstants.SCALED_LEFT_LEG_X_OFFSET), baseY + sy(GUIConstants.SCALED_LEFT_LEG_Y_OFFSET), sx(GUIConstants.SCALED_LEFT_LEG_WIDTH), sy(GUIConstants.SCALED_LEFT_LEG_HEIGHT)));
        partRects.put(PlayerBodyParts.RIGHT_LEG, new PartRect(baseX + sx(GUIConstants.SCALED_RIGHT_LEG_X_OFFSET), baseY + sy(GUIConstants.SCALED_RIGHT_LEG_Y_OFFSET), sx(GUIConstants.SCALED_RIGHT_LEG_WIDTH), sy(GUIConstants.SCALED_RIGHT_LEG_HEIGHT)));
        partRects.put(PlayerBodyParts.LEFT_FOOT, new PartRect(baseX + sx(GUIConstants.SCALED_LEFT_FOOT_X_OFFSET), baseY + sy(GUIConstants.SCALED_LEFT_FOOT_Y_OFFSET), sx(GUIConstants.SCALED_LEFT_FOOT_WIDTH), sy(GUIConstants.SCALED_LEFT_FOOT_HEIGHT)));
        partRects.put(PlayerBodyParts.RIGHT_FOOT, new PartRect(baseX + sx(GUIConstants.SCALED_RIGHT_FOOT_X_OFFSET), baseY + sy(GUIConstants.SCALED_RIGHT_FOOT_Y_OFFSET), sx(GUIConstants.SCALED_RIGHT_FOOT_WIDTH), sy(GUIConstants.SCALED_RIGHT_FOOT_HEIGHT)));
    }

    private void addWidgets() {
        this.clearChildren();
        LivingEntity target = this.handler.getEntity();
        if (target == null) return;
        boolean allowClick = true; // Always allow clicking parts; server-side will validate item use vs. bare removal

        // Part hitboxes from cached geometry
        PartRect r;
        r = partRects.get(PlayerBodyParts.HEAD); addDrawableChild(new PartButton(PlayerBodyParts.HEAD, r.x, r.y, r.w, r.h, allowClick));
        r = partRects.get(PlayerBodyParts.LEFT_ARM); addDrawableChild(new PartButton(PlayerBodyParts.LEFT_ARM, r.x, r.y, r.w, r.h, allowClick));
        r = partRects.get(PlayerBodyParts.TORSO); addDrawableChild(new PartButton(PlayerBodyParts.TORSO, r.x, r.y, r.w, r.h, allowClick));
        r = partRects.get(PlayerBodyParts.RIGHT_ARM); addDrawableChild(new PartButton(PlayerBodyParts.RIGHT_ARM, r.x, r.y, r.w, r.h, allowClick));
        r = partRects.get(PlayerBodyParts.LEFT_LEG); addDrawableChild(new PartButton(PlayerBodyParts.LEFT_LEG, r.x, r.y, r.w, r.h, allowClick));
        r = partRects.get(PlayerBodyParts.RIGHT_LEG); addDrawableChild(new PartButton(PlayerBodyParts.RIGHT_LEG, r.x, r.y, r.w, r.h, allowClick));
        r = partRects.get(PlayerBodyParts.LEFT_FOOT); addDrawableChild(new PartButton(PlayerBodyParts.LEFT_FOOT, r.x, r.y, r.w, r.h, allowClick));
        r = partRects.get(PlayerBodyParts.RIGHT_FOOT); addDrawableChild(new PartButton(PlayerBodyParts.RIGHT_FOOT, r.x, r.y, r.w, r.h, allowClick));

        // Button in right panel
        int startX = (this.width - this.backgroundWidth) / 2;
        int startY = (this.height - this.backgroundHeight) / 2;
        int rpLeft = startX + Math.round((GUI_TEX_W - RIGHT_INSET - RIGHT_PANEL_W) * DRAW_SCALE);
        int rpTop = startY + Math.round(TOP_INSET * DRAW_SCALE);
        int btnW = Math.round(80 * DRAW_SCALE);
        int btnH = Math.round(20 * DRAW_SCALE);
        int btnX = rpLeft + Math.max(0, (Math.round(RIGHT_PANEL_W * DRAW_SCALE) - btnW) / 2);
        int btnY = rpTop;
        boneToggleBtn = ButtonWidget.builder(Text.literal("Bone Layer"), b -> {
            if (!xyz.srgnis.bodyhealthsystem.config.Config.enableBoneSystem) {
                // Enforce off when system disabled
                boneLayerEnabled = false;
                return;
            }
            boneLayerEnabled = !boneLayerEnabled;
        }).dimensions(btnX, btnY, btnW, btnH).build();
        boneToggleBtn.active = xyz.srgnis.bodyhealthsystem.config.Config.enableBoneSystem;
        addDrawableChild(boneToggleBtn);

        int btnY2 = btnY + btnH + Math.round(6 * DRAW_SCALE);
        tempToggleBtn = ButtonWidget.builder(Text.literal(showTemperature ? "Show Health" : "Show Temp"), b -> {
            showTemperature = !showTemperature;
            tempToggleBtn.setMessage(Text.literal(showTemperature ? "Show Health" : "Show Temp"));
            if (showTemperature && this.handler.getEntity() != null) {
                // Request immediate server temperature when switching on
                ClientNetworking.requestBodyData(this.handler.getEntity());
                lastTempRequestNanos = System.nanoTime();
            }
        }).dimensions(btnX, btnY2, btnW, btnH).build();
        addDrawableChild(tempToggleBtn);
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

        // Draw parts with cached rectangles
        PartRect r;
        r = partRects.get(PlayerBodyParts.HEAD); drawPart(drawContext, PlayerBodyParts.HEAD, r.x, r.y, r.w, r.h);
        r = partRects.get(PlayerBodyParts.LEFT_ARM); drawPart(drawContext, PlayerBodyParts.LEFT_ARM, r.x, r.y, r.w, r.h);
        r = partRects.get(PlayerBodyParts.TORSO); drawPart(drawContext, PlayerBodyParts.TORSO, r.x, r.y, r.w, r.h);
        r = partRects.get(PlayerBodyParts.RIGHT_ARM); drawPart(drawContext, PlayerBodyParts.RIGHT_ARM, r.x, r.y, r.w, r.h);
        r = partRects.get(PlayerBodyParts.LEFT_LEG); drawPart(drawContext, PlayerBodyParts.LEFT_LEG, r.x, r.y, r.w, r.h);
        r = partRects.get(PlayerBodyParts.RIGHT_LEG); drawPart(drawContext, PlayerBodyParts.RIGHT_LEG, r.x, r.y, r.w, r.h);
        r = partRects.get(PlayerBodyParts.LEFT_FOOT); drawPart(drawContext, PlayerBodyParts.LEFT_FOOT, r.x, r.y, r.w, r.h);
        r = partRects.get(PlayerBodyParts.RIGHT_FOOT); drawPart(drawContext, PlayerBodyParts.RIGHT_FOOT, r.x, r.y, r.w, r.h);
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
        // Temperature view: pull server-authoritative value periodically
        if (xyz.srgnis.bodyhealthsystem.config.Config.enableTemperatureSystem && showTemperature) {
            long now = System.nanoTime();
            // consume last known value from networking map
            LivingEntity target = handler.getEntity();
            if (target != null) {
                Double sv = ClientNetworking.getLastBodyTempC(target);
                if (sv != null) bodyTempC = sv;
            }
            // request an update at most 4 times per second
            if (lastTempRequestNanos == 0L || (now - lastTempRequestNanos) > 250_000_000L) {
                if (target != null) ClientNetworking.requestBodyData(target);
                lastTempRequestNanos = now;
            }
        }

        this.renderBackground(drawContext);
        super.render(drawContext, mouseX, mouseY, delta);
        // Enforce bone overlay toggle state based on config
        if (!xyz.srgnis.bodyhealthsystem.config.Config.enableBoneSystem) {
            boneLayerEnabled = false;
            if (boneToggleBtn != null) boneToggleBtn.active = false;
        } else {
            if (boneToggleBtn != null) boneToggleBtn.active = true;
        }
        // Numbers overlay
        drawNumbers(drawContext);
        // Tooltips for parts (only when showing health)
        if (!showTemperature) {
            for (var child : this.children()) {
                if (child instanceof PartButton b && b.isMouseOver(mouseX, mouseY)) {
                    BodyPart part = handler.getBody().getPart(b.partId);
                    if (part != null) {
                        java.util.List<Text> list = new java.util.ArrayList<>();
                        list.add(Text.literal(part.getIdentifier().getPath()));
                        float boost = handler.getBody().getBoostForPart(part.getIdentifier());
                        float effMax = part.getMaxHealth() + Math.max(0.0f, boost);
                        list.add(Text.literal("" + Math.round(part.getHealth()) + "/" + Math.round(effMax)));
                        float allocAbs = handler.getBody().getAbsorptionForPart(part.getIdentifier());
                        if (allocAbs > 0.0f) list.add(Text.literal("Absorption: " + Math.round(allocAbs)));
                        float hbAlloc = handler.getBody().getBoostForPart(part.getIdentifier());
                        if (hbAlloc > 0.0f) list.add(Text.literal("Health Boost: " + Math.round(hbAlloc)));
                        if (part.isBroken()) list.add(Text.literal("Broken"));
                        // Wound/tourniquet info - use direct method calls instead of reflection
                        int s = part.getSmallWounds();
                        int l = part.getLargeWounds();
                        boolean tq = part.hasTourniquet();
                        int tqTicks = part.getTourniquetTicks();
                        int necState = part.getNecrosisState();
                        float nScale = part.getNecrosisScale();
                        if (l > 0) list.add(Text.literal("Large wound"));
                        else if (s > 0) list.add(Text.literal("Small wound"));
                        if (tq) {
                            // Show countdowns by part type:
                            // - Head: 15s to necrosis, then 15s to death
                            // - Others: 7min safe, then 4min necrosis
                            int total = tqTicks / 20;
                            boolean isHead = b.partId.equals(PlayerBodyParts.HEAD);
                            if (isHead) {
                                if (necState == 0) {
                                    int remaining = Math.max(0, 15 - total);
                                    list.add(Text.literal("Tourniquet: "+formatMMSS(remaining)+" (to necrosis)"));
                                } else if (necState == 1) {
                                    int necElapsed = Math.max(0, total - 15);
                                    int necRemaining = Math.max(0, 15 - necElapsed);
                                    list.add(Text.literal("Tourniquet: "+formatMMSS(necRemaining)+" (to death)"));
                                } else {
                                    list.add(Text.literal("Tourniquet: 00:00 (permanent)"));
                                }
                            } else {
                                if (necState == 0) {
                                    // Clamp to [0, 7*60]
                                    int totalClamped = Math.max(0, Math.min(7*60, total));
                                    int remaining = 7*60 - totalClamped;
                                    list.add(Text.literal("Tourniquet: "+formatMMSS(remaining)+" (safe)"));
                                } else if (necState == 1) {
                                    // Clamp necrosis elapsed to [0, 4*60]
                                    int necElapsed = Math.max(0, total - 7*60);
                                    necElapsed = Math.min(necElapsed, 4*60);
                                    int necRemaining = 4*60 - necElapsed;
                                    list.add(Text.literal("Tourniquet: "+formatMMSS(necRemaining)+" (necrosis)"));
                                } else {
                                    list.add(Text.literal("Tourniquet: 00:00 (permanent)"));
                                }
                            }
                        }
                        if (necState == 1) {
                            String phase = tq ? "Active" : "Healing";
                            list.add(Text.literal("Necrosis: "+phase));
                        }
                        if (necState == 2) list.add(Text.literal("Necrosis: permanent"));
                        if (nScale < 1.0f) list.add(Text.literal("MaxHP: "+Math.round(nScale*100)+"%"));
                        drawContext.drawTooltip(this.textRenderer, list, mouseX, mouseY);
                    }
                }
            }
        }
        this.drawMouseoverTooltip(drawContext, mouseX, mouseY);
    }

    @Override
    public void close() {
        // If this screen was opened with a medkit and we auto-disabled the bone layer,
        // restore it on close to the previous state (ensures bones reappear next open).
        if (disabledByMedkit && boneLayerWasEnabledOnOpen && xyz.srgnis.bodyhealthsystem.config.Config.enableBoneSystem) {
            boneLayerEnabled = true;
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
        int shiftY = Math.round(BODY_Y_SHIFT_LOGICAL * DRAW_SCALE);
        int baseY = top + Math.max(0, (availH - bodyHpx) / 2) - shiftY;
        var tr = MinecraftClient.getInstance().textRenderer;
        int white = 0xFFFFFF;

        if (xyz.srgnis.bodyhealthsystem.config.Config.enableTemperatureSystem && showTemperature) {
            String tempStr;
            if (xyz.srgnis.bodyhealthsystem.config.Config.temperatureUnit == xyz.srgnis.bodyhealthsystem.config.Config.TemperatureUnit.FAHRENHEIT) {
                double f = bodyTempC * 9.0 / 5.0 + 32.0;
                tempStr = String.format("%.1f°F", f);
            } else {
                tempStr = String.format("%.1f°C", bodyTempC);
            }
            int textW = tr.getWidth(tempStr);
            // Center roughly over torso
            int torsoCenterX = baseX + sx(GUIConstants.SCALED_TORSO_X_OFFSET) + sx(GUIConstants.SCALED_TORSO_WIDTH) / 2;
            int torsoCenterY = baseY + sy(GUIConstants.SCALED_TORSO_Y_OFFSET) + sy(GUIConstants.SCALED_TORSO_HEIGHT) / 2 - 4;
            ctx.drawTextWithShadow(tr, tempStr, torsoCenterX - textW / 2, torsoCenterY, 0xFFFFFF);
            return;
        }

        PartRect rh = partRects.get(PlayerBodyParts.HEAD);
        BodyPart head = handler.getBody().getPart(PlayerBodyParts.HEAD);
        String headStr = formatHealth(head);
        int headTextW = tr.getWidth(headStr);
        int headX = rh.x + (rh.w - headTextW) / 2;
        int headY = rh.y + (rh.h - 9) / 2;
        ctx.drawTextWithShadow(tr, headStr, headX, headY, white);

        PartRect rt = partRects.get(PlayerBodyParts.TORSO);
        BodyPart torso = handler.getBody().getPart(PlayerBodyParts.TORSO);
        String torsoStr = formatHealth(torso);
        int torsoTextW = tr.getWidth(torsoStr);
        int torsoX = rt.x + (rt.w - torsoTextW) / 2;
        int torsoY = rt.y + (rt.h - 9) / 2;
        ctx.drawTextWithShadow(tr, torsoStr, torsoX, torsoY, white);

        PartRect rla = partRects.get(PlayerBodyParts.LEFT_ARM);
        BodyPart la = handler.getBody().getPart(PlayerBodyParts.LEFT_ARM);
        String laStr = formatHealth(la);
        int laTextW = tr.getWidth(laStr);
        int laX = rla.x + (rla.w - laTextW) / 2;
        int laY = rla.y + (rla.h - 9) / 2;
        ctx.drawTextWithShadow(tr, laStr, laX, laY, white);

        PartRect rra = partRects.get(PlayerBodyParts.RIGHT_ARM);
        BodyPart ra = handler.getBody().getPart(PlayerBodyParts.RIGHT_ARM);
        String raStr = formatHealth(ra);
        int raTextW = tr.getWidth(raStr);
        int raX = rra.x + (rra.w - raTextW) / 2;
        int raY = rra.y + (rra.h - 9) / 2;
        ctx.drawTextWithShadow(tr, raStr, raX, raY, white);

        PartRect rll = partRects.get(PlayerBodyParts.LEFT_LEG);
        BodyPart ll = handler.getBody().getPart(PlayerBodyParts.LEFT_LEG);
        String llStr = formatHealth(ll);
        int llTextW = tr.getWidth(llStr);
        int llY = rll.y + rll.h / 2 - 4;
        int llX = rll.x - llTextW - 2;
        ctx.drawTextWithShadow(tr, llStr, llX, llY, white);

        PartRect rrl = partRects.get(PlayerBodyParts.RIGHT_LEG);
        BodyPart rl = handler.getBody().getPart(PlayerBodyParts.RIGHT_LEG);
        String rlStr = formatHealth(rl);
        int rlY = rrl.y + rrl.h / 2 - 4;
        int rlX = rrl.x + rrl.w + 2;
        ctx.drawTextWithShadow(tr, rlStr, rlX, rlY, white);

        PartRect rlf = partRects.get(PlayerBodyParts.LEFT_FOOT);
        PartRect rrf = partRects.get(PlayerBodyParts.RIGHT_FOOT);
        BodyPart lf = handler.getBody().getPart(PlayerBodyParts.LEFT_FOOT);
        BodyPart rf = handler.getBody().getPart(PlayerBodyParts.RIGHT_FOOT);
        String lfStr = formatHealth(lf);
        String rfStr = formatHealth(rf);
        int lfTextW = tr.getWidth(lfStr);
        int rfTextW = tr.getWidth(rfStr);
        int sep = Math.max(2, Math.round(3 * DRAW_SCALE));
        int lfX = rlf.x + (rlf.w - lfTextW) / 2 - sep;
        int rfX = rrf.x + (rrf.w - rfTextW) / 2 + sep;
        int feetY = rlf.y + rlf.h + 2;
        ctx.drawTextWithShadow(tr, lfStr, lfX, feetY, white);
        ctx.drawTextWithShadow(tr, rfStr, rfX, feetY, white);
    }

    private void drawPart(DrawContext ctx, Identifier partId, int x, int y, int w, int h) {
        BodyPart p = handler.getBody().getPart(partId);
        int color = showTemperature ? selectTemperatureColor(bodyTempC) : selectHealthColor(p);
        drawHealthRectangle(ctx, x, y, w, h, color);

        // Skip overlays in temperature mode.
        if (showTemperature) return;

        // Read wound counts directly - no reflection needed
        int s = p.getSmallWounds();
        int l = p.getLargeWounds();

        boolean boneSystemEnabled = xyz.srgnis.bodyhealthsystem.config.Config.enableBoneSystem;
        boolean broken = boneSystemEnabled && p.isBroken();

        // If bone overlay is enabled, render bones first.
        if (boneSystemEnabled && boneLayerEnabled) {
            Identifier tex = selectBoneTexture(partId, broken);
            if (isTextureAvailable(tex)) {
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

        // Always render wound overlays (even when bone layer is disabled by medkit).
        drawWounds(ctx, partId, x, y, w, h, s, l);
        drawWoundCounts(ctx, x, y, w, h, s, l);

        // If bone system is enabled but the bone layer is off, still indicate broken bones.
        if (boneSystemEnabled && !boneLayerEnabled && broken && !partId.equals(PlayerBodyParts.HEAD)) {
            int icon = Math.max(8, Math.min(12, Math.round(10 * DRAW_SCALE)));
            ctx.drawTexture(TEX_BONEMAIN_BROKEN, x + 1, y + 1, icon, icon, 0.0F, 0.0F, 16, 16, 16, 16);
        }
    }

    private void drawWounds(DrawContext ctx, Identifier partId, int x, int y, int w, int h, int s, int l) {
        if (s <= 0 && l <= 0) return;
        // Scale texture rects to the target rect
        // Small wound source rect: (6,6)-(9,11) on 16x16
        // Large wound source rect: (5,3)-(11,12)
        // We’ll draw at logical center of the part rect, repeated per wound count with slight offsets
        int cx = x + w / 2;
        int cy = y + h / 2;
        int offset = Math.max(2, Math.round(2 * DRAW_SCALE));
        int drawCount = Math.min(2, s); // cap visual draws to 2 for small
        for (int i = 0; i < drawCount; i++) {
            int dx = cx - w / 8 + (i * offset);
            int dy = cy - h / 8 + (i * offset);
            // dest size proportional: roughly 1/4 of part rect each axis (clamped)
            int dw = Math.max(6, Math.min(w, Math.round(Math.max(6, w * 0.35f))));
            int dh = Math.max(6, Math.min(h, Math.round(Math.max(6, h * 0.35f))));
            // source: 4x6 region (x:6..9, y:6..11) inclusive => width=4, height=6
            ctx.drawTexture(TEX_WOUND_SMALL, dx, dy, dw, dh, 6.0F, 6.0F, 4, 6, 16, 16);
        }
        drawCount = Math.min(2, l);
        for (int i = 0; i < drawCount; i++) {
            int dx = cx - w / 6 - (i * offset);
            int dy = cy - h / 6 - (i * offset);
            int dw = Math.max(8, Math.min(w, Math.round(Math.max(8, w * 0.5f))));
            int dh = Math.max(8, Math.min(h, Math.round(Math.max(8, h * 0.5f))));
            // source: 7x10 region (x:5..11, y:3..12) inclusive => width=7, height=10
            ctx.drawTexture(TEX_WOUND_LARGE, dx, dy, dw, dh, 5.0F, 3.0F, 7, 10, 16, 16);
        }
    }

    private void drawWoundCounts(DrawContext ctx, int x, int y, int w, int h, int s, int l) {
        if (s <= 0 && l <= 0) return;
        String text;
        if (s > 0 && l > 0) text = "S" + s + " L" + l;
        else if (l > 0) text = "L" + l;
        else text = "S" + s;
        int tx = x + 2;
        int ty = y + h - 10;
        ctx.drawTextWithShadow(MinecraftClient.getInstance().textRenderer, text, tx, ty, 0xFFFFFF);
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

    private String formatHealth(BodyPart p) {
        if (p == null) return "0/0";
        Body body = this.handler.getBody();
        float boost = body.getBoostForPart(p.getIdentifier());
        float current = p.getHealth();
        float maxEff = p.getMaxHealth() + Math.max(0.0f, boost);
        return Math.round(current) + "/" + Math.round(maxEff);
    }

    // No client-side allocation logic anymore; we use server-synced buckets for display

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Allow the same keybind (F) to close the GUI when it is open
        if (keyCode == GLFW.GLFW_KEY_F) {
            this.close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private static String formatMMSS(int seconds) {
        int mm = seconds / 60;
        int ss = seconds % 60;
        return String.format("%d:%02d", mm, ss);
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
            if (part == null) return;
            // If not using the Tourniquet item and a tourniquet is present, allow removal
            var item = BodyOperationsScreen.this.handler.getItemStack();
            boolean usingTourniquetItem = !item.isEmpty() && item.getItem() == xyz.srgnis.bodyhealthsystem.registry.ModItems.TOURNIQUET;
            boolean usingStitchesItem = !item.isEmpty() && item.getItem() == xyz.srgnis.bodyhealthsystem.registry.ModItems.STITCHES;
            if (!usingTourniquetItem && !usingStitchesItem && part.hasTourniquet()) {
                ClientNetworking.removeTourniquet(BodyOperationsScreen.this.handler.getEntity(), part.getIdentifier());
                BodyOperationsScreen.this.close();
                return;
            }
            // Otherwise, route to generic item use (Tourniquet item toggles apply/remove; other items heal, etc.)
            if (!item.isEmpty()) {
                ClientNetworking.useHealingItem(BodyOperationsScreen.this.handler.getEntity(), part.getIdentifier(), item);
                usedMedkit = true;
            }
            // Restore bone layer if we auto-disabled it when opening for medkit
            if (disabledByMedkit && boneLayerWasEnabledOnOpen && xyz.srgnis.bodyhealthsystem.config.Config.enableBoneSystem) {
                boneLayerEnabled = true;
            }
            BodyOperationsScreen.this.close();
        }

        @Override
        public void renderButton(DrawContext drawContext, int mouseX, int mouseY, float delta) {
            // No extra rendering; the background draws body and bones
        }
    }
}