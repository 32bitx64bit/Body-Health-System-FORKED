package xyz.srgnis.bodyhealthsystem.client.model;

import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.model.Dilation;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.EntityModelLayer;

public class StrawHatModel extends EntityModel<LivingEntity> {
    // Layer id for baking in client init
    public static final EntityModelLayer LAYER = new EntityModelLayer(new Identifier("bodyhealthsystem", "strawhat"), "main");

    private final ModelPart strawHat;

    public StrawHatModel(ModelPart root) {
        this.strawHat = root.getChild("StrawHat");
    }

    public static TexturedModelData getTexturedModelData() {
        ModelData modelData = new ModelData();
        ModelPartData root = modelData.getRoot();
        root.addChild("StrawHat",
                ModelPartBuilder.create()
                        .uv(0, 16).cuboid(-5.0F, -34.0F, -5.0F, 10.0F, 2.0F, 10.0F, new Dilation(0.0F))
                        .uv(0, 28).cuboid(-4.0F, -36.0F, -4.0F, 8.0F, 2.0F, 8.0F, new Dilation(0.0F))
                        .uv(0, 0).cuboid(-8.0F, -31.99F, -8.0F, 16.0F, 0.0F, 16.0F, new Dilation(0.0F)),
                ModelTransform.pivot(0.0F, 26.0F, 0.0F)
        );
        return TexturedModelData.of(modelData, 64, 64);
    }

    @Override
    public void setAngles(LivingEntity entity, float limbAngle, float limbDistance, float animationProgress, float headYaw, float headPitch) {
        // No animation
    }

    @Override
    public void render(MatrixStack matrices, VertexConsumer vertices, int light, int overlay, float red, float green, float blue, float alpha) {
        strawHat.render(matrices, vertices, light, overlay, red, green, blue, alpha);
    }
}
