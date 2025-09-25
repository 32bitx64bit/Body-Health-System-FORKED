package xyz.srgnis.bodyhealthsystem.items;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;

/**
 * Helper for adding +1 tier per application to TemperatureAPI's tempapi_resistance NBT key
 */
public final class ResistanceUtil {
    private ResistanceUtil() {}

    public static final String KEY = "tempapi_resistance";

    public static boolean applyHeatTier(ItemStack armor, int addTier) {
        int[] hc = readTiers(armor);
        int heatTier = Math.min(6, hc[0] + addTier);
        int coldTier = hc[1];
        boolean changed = heatTier != hc[0];
        if (changed) writeTiers(armor, heatTier, coldTier);
        return changed;
    }

    public static boolean applyColdTier(ItemStack armor, int addTier) {
        int[] hc = readTiers(armor);
        int heatTier = hc[0];
        int coldTier = Math.min(6, hc[1] + addTier);
        boolean changed = coldTier != hc[1];
        if (changed) writeTiers(armor, heatTier, coldTier);
        return changed;
    }

    public static int[] readTiers(ItemStack stack) {
        int heat = 0;
        int cold = 0;
        NbtCompound tag = stack.getNbt();
        if (tag != null && tag.contains(KEY)) {
            String v = tag.getString(KEY);
            // Expected like: "heat:3,cold:2" (case-insensitive)
            String lower = v.toLowerCase();
            for (String part : lower.split("[;,\\s]+")) {
                if (part.startsWith("heat:")) {
                    try { heat = Integer.parseInt(part.substring(5)); } catch (Exception ignored) {}
                } else if (part.startsWith("cold:")) {
                    try { cold = Integer.parseInt(part.substring(5)); } catch (Exception ignored) {}
                }
            }
        }
        return new int[]{heat, cold};
    }

    public static void writeTiers(ItemStack stack, int heatTier, int coldTier) {
        StringBuilder sb = new StringBuilder();
        if (heatTier > 0) sb.append("heat:").append(heatTier);
        if (coldTier > 0) {
            if (sb.length() > 0) sb.append(",");
            sb.append("cold:").append(coldTier);
        }
        NbtCompound tag = stack.getOrCreateNbt();
        if (sb.length() == 0) {
            // If both are 0 remove the key to keep item clean
            tag.remove(KEY);
        } else {
            tag.putString(KEY, sb.toString());
        }
    }
}
