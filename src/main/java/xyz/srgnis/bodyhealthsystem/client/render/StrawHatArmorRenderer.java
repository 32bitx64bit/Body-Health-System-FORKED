package xyz.srgnis.bodyhealthsystem.client.render;

import net.fabricmc.fabric.api.client.rendering.v1.ArmorRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import xyz.srgnis.bodyhealthsystem.client.model.StrawHatModel;

public class StrawHatArmorRenderer implements ArmorRenderer {
    private static final Identifier TEXTURE = new Identifier("bodyhealthsystem", "textures/models/armor/straw_hat.png");
    private StrawHatModel model;

    private StrawHatModel getModel() {
        if (model == null) {
            model = new StrawHatModel(MinecraftClient.getInstance().getEntityModelLoader().getModelPart(StrawHatModel.LAYER));
        }
        return model;
    }

    @Override
    public void render(MatrixStack matrices, VertexConsumerProvider vertexConsumers, ItemStack stack, LivingEntity entity, EquipmentSlot slot, int light, BipedEntityModel<LivingEntity> contextModel) {
        if (slot != EquipmentSlot.HEAD) return;
        matrices.push();
        contextModel.head.rotate(matrices);
        VertexConsumer vc = vertexConsumers.getBuffer(RenderLayer.getEntityTranslucent(TEXTURE));
        getModel().render(matrices, vc, light, OverlayTexture.DEFAULT_UV, 1f, 1f, 1f, 1f);
        matrices.pop();
    }
}
