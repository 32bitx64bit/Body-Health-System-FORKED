package xyz.srgnis.bodyhealthsystem.registry;

import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.util.Identifier;
import net.minecraft.registry.Registry;
import net.minecraft.registry.Registries;
import xyz.srgnis.bodyhealthsystem.BHSMain;
import xyz.srgnis.bodyhealthsystem.items.MedkitItem;
import xyz.srgnis.bodyhealthsystem.items.MorphineItem;
import xyz.srgnis.bodyhealthsystem.items.PlasterItem;
import xyz.srgnis.bodyhealthsystem.items.SplintItem;
import xyz.srgnis.bodyhealthsystem.items.DressingItem;
import xyz.srgnis.bodyhealthsystem.items.AnkleBraceItem;
import xyz.srgnis.bodyhealthsystem.items.ChestBraceItem;
import xyz.srgnis.bodyhealthsystem.items.UpgradedMedkitItem;
import xyz.srgnis.bodyhealthsystem.items.TraumaKitItem;

public class ModItems {
    public static final Item PLASTER_ITEM = new PlasterItem(new FabricItemSettings());
    public static final Item MORPHINE_ITEM = new MorphineItem(new FabricItemSettings());
    public static final Item MEDKIT_ITEM = new MedkitItem(new FabricItemSettings());
    public static final Item SPLINT_ITEM = new SplintItem(new FabricItemSettings());
    public static final Item DRESSING_ITEM = new DressingItem(new FabricItemSettings());
    public static final Item ANKLE_BRACE_ITEM = new AnkleBraceItem(new FabricItemSettings());
    public static final Item CHEST_BRACE_ITEM = new ChestBraceItem(new FabricItemSettings());
    public static final Item UPGRADED_MEDKIT_ITEM = new UpgradedMedkitItem(new FabricItemSettings());
    public static final Item TRAUMA_KIT_ITEM = new TraumaKitItem(new FabricItemSettings());

    public static void registerItems(){
        Registry.register(Registries.ITEM, new Identifier(BHSMain.MOD_ID, "plaster"), PLASTER_ITEM);
        Registry.register(Registries.ITEM, new Identifier(BHSMain.MOD_ID, "morphine"), MORPHINE_ITEM);
        Registry.register(Registries.ITEM, new Identifier(BHSMain.MOD_ID,"medkit"), MEDKIT_ITEM);
        Registry.register(Registries.ITEM, new Identifier(BHSMain.MOD_ID,"splint"), SPLINT_ITEM);
        Registry.register(Registries.ITEM, new Identifier(BHSMain.MOD_ID,"dressing"), DRESSING_ITEM);
        Registry.register(Registries.ITEM, new Identifier(BHSMain.MOD_ID,"ankle_brace"), ANKLE_BRACE_ITEM);
        Registry.register(Registries.ITEM, new Identifier(BHSMain.MOD_ID,"chest_brace"), CHEST_BRACE_ITEM);
        Registry.register(Registries.ITEM, new Identifier(BHSMain.MOD_ID,"medkit_upgraded"), UPGRADED_MEDKIT_ITEM);
        Registry.register(Registries.ITEM, new Identifier(BHSMain.MOD_ID,"trauma_kit"), TRAUMA_KIT_ITEM);

        ItemGroupEvents.modifyEntriesEvent(BHSMain.BHS_GROUP).register(content -> {
            content.add(PLASTER_ITEM);
        });
        ItemGroupEvents.modifyEntriesEvent(BHSMain.BHS_GROUP).register(content -> {
            content.add(MORPHINE_ITEM);
        });
        ItemGroupEvents.modifyEntriesEvent(BHSMain.BHS_GROUP).register(content -> {
            content.add(MEDKIT_ITEM);
        });
        ItemGroupEvents.modifyEntriesEvent(BHSMain.BHS_GROUP).register(content -> {
            content.add(SPLINT_ITEM);
        });
        ItemGroupEvents.modifyEntriesEvent(BHSMain.BHS_GROUP).register(content -> {
            content.add(DRESSING_ITEM);
        });
        ItemGroupEvents.modifyEntriesEvent(BHSMain.BHS_GROUP).register(content -> {
            content.add(ANKLE_BRACE_ITEM);
        });
        ItemGroupEvents.modifyEntriesEvent(BHSMain.BHS_GROUP).register(content -> {
            content.add(CHEST_BRACE_ITEM);
        });
        ItemGroupEvents.modifyEntriesEvent(BHSMain.BHS_GROUP).register(content -> {
            content.add(UPGRADED_MEDKIT_ITEM);
        });
        ItemGroupEvents.modifyEntriesEvent(BHSMain.BHS_GROUP).register(content -> {
            content.add(TRAUMA_KIT_ITEM);
        });
    }
}
