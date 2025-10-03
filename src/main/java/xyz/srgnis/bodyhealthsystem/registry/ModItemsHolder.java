package xyz.srgnis.bodyhealthsystem.registry;

import net.minecraft.item.Item;

// Simple indirection to reference items before ModItems is updated in this phase
public final class ModItemsHolder {
    private static Item STITCHES;
    private static Item TOURNIQUET;

    private ModItemsHolder() {}

    public static void setStitches(Item item) { STITCHES = item; }
    public static void setTourniquet(Item item) { TOURNIQUET = item; }
    public static Item getStitches() { return STITCHES; }
    public static Item getTourniquet() { return TOURNIQUET; }
}
