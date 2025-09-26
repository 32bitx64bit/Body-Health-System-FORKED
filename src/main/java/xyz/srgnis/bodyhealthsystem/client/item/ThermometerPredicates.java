package xyz.srgnis.bodyhealthsystem.client.item;

import gavinx.temperatureapi.api.TemperatureAPI;
import net.minecraft.client.item.ModelPredicateProviderRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import xyz.srgnis.bodyhealthsystem.BHSMain;
import xyz.srgnis.bodyhealthsystem.registry.ModItems;

/**
 * Minimal client-side predicate that maps ambient temperature to a stage 0..6.
 * Stages:
 *  0: < -10°C
 *  1: [-10, 0)
 *  2: [0, 13)
 *  3: [13, 22)
 *  4: [22, 30]
 *  5: (30, 38)
 *  6: >= 38
 *
 * Returns a normalized float [0.0 .. 1.0] so JSON overrides can match properly.
 */
public final class ThermometerPredicates {
    private ThermometerPredicates() {}

    public static void init() {
        ModelPredicateProviderRegistry.register(
                ModItems.THERMOMETER,
                new Identifier(BHSMain.MOD_ID, "temperature_stage"),
                ThermometerPredicates::calc
        );
    }

    private static float calc(ItemStack stack, net.minecraft.client.world.ClientWorld world, LivingEntity entity, int seed) {
        PlayerEntity player = entity instanceof PlayerEntity p ? p : MinecraftClient.getInstance().player;
        if (player == null) return 0.5f; // default to mid band if no player
        double c = TemperatureAPI.getTemperatureCelsius(player);
        if (Double.isNaN(c)) return 0.5f; // default to mid band on error

        int stage;
        if (c < -10.0) stage = 0;
        else if (c < 0.0) stage = 1;
        else if (c < 13.0) stage = 2;
        else if (c <= 30.0) stage = 3;
        else if (c < 34.0) stage = 4;
        else if (c < 38.0) stage = 5;
        else stage = 6;

        // normalize: stage / 6 → [0.0, 1.0]
        return stage / 6.0f;
    }
}
