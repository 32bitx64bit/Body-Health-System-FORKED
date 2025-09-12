package xyz.srgnis.bodyhealthsystem.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.srgnis.bodyhealthsystem.util.ProjectileHitTracker;
import net.minecraft.util.hit.HitResult;   

@Mixin(PersistentProjectileEntity.class)
public class ProjectileHitMixin {
    private static final double ARM_X_THRESHOLD = 0.585; 

    @Inject(method = "onEntityHit", at = @At("HEAD"))
    private void bhs$recordHit(EntityHitResult entityHitResult, CallbackInfo ci) {
        Entity self = (Entity)(Object)this;
        if (entityHitResult == null) return;
        Entity hitEntity = entityHitResult.getEntity();
        if (!(hitEntity instanceof PlayerEntity player)) return;
        if (player.getWorld().isClient) return;

        // --- Build candidate hit position ---
        Vec3d hitPos = entityHitResult.getPos();
        Vec3d projPos = self.getPos();
        Box box = player.getBoundingBox();

        double centerX = (box.minX + box.maxX) * 0.5;
        double centerZ = (box.minZ + box.maxZ) * 0.5;

        double distHit = horizontalDistance(hitPos, centerX, centerZ);
        double distProj = horizontalDistance(projPos, centerX, centerZ);

        Vec3d best = (distProj > distHit) ? projPos : hitPos;

        double py = clamp(best.y, box.minY, box.maxY);
        Vec3d adjustedHit = new Vec3d(best.x, py, best.z);

        Vec3d origin = new Vec3d(centerX, py, centerZ);
        Vec3d offset = adjustedHit.subtract(origin);

        double height = Math.max(player.getHeight(), 1.8);
        double halfWidth = Math.max(player.getWidth() * 0.5, 0.3);

        double yawRad = Math.toRadians(player.getBodyYaw());
        Vec3d forward = new Vec3d(-Math.sin(yawRad), 0.0, Math.cos(yawRad)).normalize();
        Vec3d right = new Vec3d(forward.z, 0.0, -forward.x).normalize();

        double localX = offset.dotProduct(right);
        double localZ = offset.dotProduct(forward);

        double xNorm = clamp(localX / halfWidth, -1.0, 1.0);
        double yNorm = clamp((py - box.minY) / height, 0.0, 1.0);
        double zNorm = clamp(localZ / halfWidth, -1.0, 1.0);

        ProjectileHitTracker.record(player, xNorm, yNorm, zNorm);

        String bodyPart = classifyBodyPart(xNorm, yNorm);
        if (player instanceof ServerPlayerEntity serverPlayer) {
            serverPlayer.sendMessage(Text.literal(String.format(
                "BHS-hit x=%.3f y=%.3f z=%.3f -> %s | best=(%.2f,%.2f,%.2f) offset=(%.3f,%.3f,%.3f) yaw=%.1f",
                xNorm, yNorm, zNorm, bodyPart,
                best.x, best.y, best.z,
                offset.x, offset.y, offset.z,
                player.getBodyYaw()
            )), false);
        }
    }

    private static String classifyBodyPart(double xNorm, double yNorm) {
        String side = "";
        if (xNorm < -0.25) side = "Right ";
        else if (xNorm > 0.25) side = "Left ";

        if (yNorm < 0.15) return side + "Foot";
        if (yNorm < 0.45) return side + "Leg";
        if (yNorm < 0.75) {
            // Arms extend laterally in the torso band
            if (Math.abs(xNorm) > ARM_X_THRESHOLD) return side + "Arm";
            return "Torso";
        }
        return side + "Head";
    }

    private static double horizontalDistance(Vec3d v, double cx, double cz) {
        double dx = v.x - cx;
        double dz = v.z - cz;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }
}
