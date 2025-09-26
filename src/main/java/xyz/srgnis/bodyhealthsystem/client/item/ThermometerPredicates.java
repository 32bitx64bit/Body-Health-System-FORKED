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
        if (player == null) return 0f;
        double c = TemperatureAPI.getTemperatureCelsius(player);
        if (Double.isNaN(c)) return 0f;
        if (c < -10.0) return 0f;
        if (c < 0.0) return 1f;
        if (c < 13.0) return 2f;
        if (c < 22.0) return 3f;
        if (c <= 30.0) return 4f; // safe band upper bound
        if (c < 38.0) return 5f;
        return 6f;
    }
}
