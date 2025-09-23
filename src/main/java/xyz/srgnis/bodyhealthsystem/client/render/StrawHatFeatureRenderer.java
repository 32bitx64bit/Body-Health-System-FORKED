package xyz.srgnis.bodyhealthsystem.client.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import xyz.srgnis.bodyhealthsystem.items.StrawHatItem;
import xyz.srgnis.bodyhealthsystem.client.model.StrawHatModel;

public class StrawHatFeatureRenderer<T extends LivingEntity, M extends BipedEntityModel<T>> extends FeatureRenderer<T, M> {

    private static final Identifier TEXTURE = new Identifier("bodyhealthsystem", "textures/models/armor/straw_hat.png");
    private final StrawHatModel model;

    public StrawHatFeatureRenderer(FeatureRendererContext<T, M> context) {
        super(context);
        this.model = new StrawHatModel(MinecraftClient.getInstance().getEntityModelLoader().getModelPart(StrawHatModel.LAYER));
    }

    @Override
    public void render(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, T entity, float limbAngle, float limbDistance, float tickDelta, float animationProgress, float headYaw, float headPitch) {
        ItemStack head = entity.getEquippedStack(EquipmentSlot.HEAD);
        if (!(head.getItem() instanceof StrawHatItem)) return;

        matrices.push();
        // Follow head rotation/position
        getContextModel().head.rotate(matrices);

        var vertexConsumer = vertexConsumers.getBuffer(RenderLayer.getEntityTranslucent(TEXTURE));
        model.render(matrices, vertexConsumer, light, OverlayTexture.DEFAULT_UV, 1f, 1f, 1f, 1f);
        matrices.pop();
    }
}
