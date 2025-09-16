package xyz.srgnis.bodyhealthsystem.mixin;

import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xyz.srgnis.bodyhealthsystem.body.player.BodyProvider;
import xyz.srgnis.bodyhealthsystem.body.player.PlayerBody;

@Mixin(PlayerEntity.class)
public class CrawlEyeHeightMixin {
    @Inject(method = "getActiveEyeHeight", at = @At("RETURN"), cancellable = true)
    private void bhs$lowerEyeHeightWhenCrawling(EntityPose pose, EntityDimensions dimensions, CallbackInfoReturnable<Float> cir) {
        PlayerEntity player = (PlayerEntity)(Object)this;
        if (player == null) return;
        if (!(player instanceof BodyProvider)) return;
        PlayerBody body = (PlayerBody)((BodyProvider) player).getBody();
        if (body == null) return;
        // Downed: force low eye height so camera matches a prone body
        if (body.isDowned()) {
            cir.setReturnValue(0.4f);
            return;
        }
        if (body.isCrawlingRequired()) {
            // Force a low eye height similar to swimming/crawling
            cir.setReturnValue(0.4f);
        }
    }
}
