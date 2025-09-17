package xyz.srgnis.bodyhealthsystem.mixin;

import net.minecraft.entity.EntityPose;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.srgnis.bodyhealthsystem.body.Body;
import xyz.srgnis.bodyhealthsystem.body.player.BodyProvider;
import xyz.srgnis.bodyhealthsystem.body.player.PlayerBody;

/**
 * Ensure the crawling/downed pose is applied consistently on both server and client
 * by overriding the result of PlayerEntity.updatePose(). This avoids vanilla resetting
 * the pose later in the same tick and fixes client/server desync.
 */
@Mixin(PlayerEntity.class)
public abstract class ForcePoseMixin {
    @Inject(method = "updatePose", at = @At("TAIL"))
    private void bhs$forceCrawlOrDownedPose(CallbackInfo ci) {
        PlayerEntity player = (PlayerEntity) (Object) this;
        if (!(player instanceof BodyProvider)) return;
        Body body = ((BodyProvider) player).getBody();
        if (body == null) return;

        boolean requireCrawl = false;
        if (body instanceof PlayerBody pb) {
            requireCrawl = pb.isCrawlingRequired();
        }

        if (body.isDowned() || requireCrawl) {
            // Force swimming (crawl) pose when downed or crawling is required, but not in water
            if (!player.isTouchingWater()) {
                player.setSwimming(true);
                player.setPose(EntityPose.SWIMMING);
            }
        } else {
            // Restore standing if we previously forced crawling and the player is not actually swimming in water
            if (player.getPose() == EntityPose.SWIMMING && !player.isTouchingWater()) {
                player.setSwimming(false);
                player.setPose(EntityPose.STANDING);
            }
        }
    }
}
