package xyz.srgnis.bodyhealthsystem;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.ArmorRenderer;

import net.minecraft.item.ArmorItem;
import net.minecraft.item.ArmorMaterials;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import xyz.srgnis.bodyhealthsystem.client.hud.BHSHud;
import xyz.srgnis.bodyhealthsystem.network.ClientNetworking;
import xyz.srgnis.bodyhealthsystem.registry.Screens;
import xyz.srgnis.bodyhealthsystem.client.input.GiveUpKeyHandler;
import xyz.srgnis.bodyhealthsystem.client.screen.DownedOverlayController;
import xyz.srgnis.bodyhealthsystem.client.input.OpenHealthScreenKeyHandler;
import xyz.srgnis.bodyhealthsystem.client.model.StrawHatModel;
import xyz.srgnis.bodyhealthsystem.client.render.StrawHatArmorRenderer;
import xyz.srgnis.bodyhealthsystem.registry.ModItems;

public class BHSClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        HudRenderCallback.EVENT.register(new BHSHud());
        ClientNetworking.initialize();
        Screens.registerScreens();
        GiveUpKeyHandler.initClient();
        OpenHealthScreenKeyHandler.initClient();
        DownedOverlayController.initClient();

        // Register model layer and Fabric ArmorRenderer for Straw Hat (overrides vanilla armor layer rendering)
        net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry.registerModelLayer(
                StrawHatModel.LAYER, StrawHatModel::getTexturedModelData
        );
        ArmorRenderer.register(new StrawHatArmorRenderer(), ModItems.STRAW_HAT);

        // Tooltip: show cold resistance tier on leather armor pieces
        ItemTooltipCallback.EVENT.register((stack, context, lines) -> {
            if (stack.getItem() instanceof ArmorItem armor && armor.getMaterial() == ArmorMaterials.LEATHER) {
                Text line = Text.literal("Cold resistance 2")
                        .setStyle(Style.EMPTY.withColor(TextColor.fromRgb(0x4da1ff)));
                lines.add(line);
            }
        });
    }
}
