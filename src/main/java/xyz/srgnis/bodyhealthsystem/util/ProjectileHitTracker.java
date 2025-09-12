package xyz.srgnis.bodyhealthsystem.util;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ProjectileHitTracker {
    private static final Map<UUID, Vec3d> LAST_HIT = new ConcurrentHashMap<>();

    private ProjectileHitTracker() {}

    public static void record(PlayerEntity player, double xNorm, double yNorm, double zNorm) {
        if (player == null) return;
        LAST_HIT.put(player.getUuid(), new Vec3d(xNorm, yNorm, zNorm));
    }

    public static Vec3d getLastHit(PlayerEntity player) {
        if (player == null) return null;
        return LAST_HIT.get(player.getUuid());
    }

    public static void clear(PlayerEntity player) {
        if (player == null) return;
        LAST_HIT.remove(player.getUuid());
    }
}