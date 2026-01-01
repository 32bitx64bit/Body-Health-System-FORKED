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
import xyz.srgnis.bodyhealthsystem.items.WoolClothingItem;
import xyz.srgnis.bodyhealthsystem.items.StrawHatItem;
import xyz.srgnis.bodyhealthsystem.items.CoolingGelItem;
import xyz.srgnis.bodyhealthsystem.items.HeatingGelItem;
import xyz.srgnis.bodyhealthsystem.items.ThermometerItem;
import xyz.srgnis.bodyhealthsystem.items.PortableFanItem;
import xyz.srgnis.bodyhealthsystem.items.StitchesItem;
import xyz.srgnis.bodyhealthsystem.items.TourniquetItem;
import xyz.srgnis.bodyhealthsystem.items.HerbalPoulticesItem;
import xyz.srgnis.bodyhealthsystem.items.PrimitiveMedkitItem;

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
    public static final Item PRIMITIVE_MEDKIT_ITEM = new PrimitiveMedkitItem(new FabricItemSettings());
    public static final Item WOOL_HELMET = new WoolClothingItem(net.minecraft.item.ArmorItem.Type.HELMET, new FabricItemSettings());
    public static final Item WOOL_CHESTPLATE = new WoolClothingItem(net.minecraft.item.ArmorItem.Type.CHESTPLATE, new FabricItemSettings());
    public static final Item WOOL_LEGGINGS = new WoolClothingItem(net.minecraft.item.ArmorItem.Type.LEGGINGS, new FabricItemSettings());
    public static final Item WOOL_BOOTS = new WoolClothingItem(net.minecraft.item.ArmorItem.Type.BOOTS, new FabricItemSettings());

    public static final Item STRAW_HAT = new StrawHatItem(new FabricItemSettings());
    public static final Item STITCHES = new StitchesItem(new FabricItemSettings());
    public static final Item TOURNIQUET = new TourniquetItem(new FabricItemSettings());

    public static final Item HERBAL_POULTICES = new HerbalPoulticesItem(new FabricItemSettings());

    public static final Item COOLING_GEL = new CoolingGelItem(new FabricItemSettings().maxCount(16));
    public static final Item HEATING_GEL = new HeatingGelItem(new FabricItemSettings().maxCount(16));
    public static final Item THERMOMETER = new ThermometerItem(new FabricItemSettings().maxCount(1));
    public static final Item PORTABLE_FAN = new PortableFanItem(new FabricItemSettings().maxCount(1));

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
        Registry.register(Registries.ITEM, new Identifier(BHSMain.MOD_ID,"primitive_medkit"), PRIMITIVE_MEDKIT_ITEM);
        Registry.register(Registries.ITEM, new Identifier(BHSMain.MOD_ID,"wool_helmet"), WOOL_HELMET);
        Registry.register(Registries.ITEM, new Identifier(BHSMain.MOD_ID,"wool_chestplate"), WOOL_CHESTPLATE);
        Registry.register(Registries.ITEM, new Identifier(BHSMain.MOD_ID,"wool_leggings"), WOOL_LEGGINGS);
        Registry.register(Registries.ITEM, new Identifier(BHSMain.MOD_ID,"wool_boots"), WOOL_BOOTS);
        Registry.register(Registries.ITEM, new Identifier(BHSMain.MOD_ID,"straw_hat"), STRAW_HAT);
        Registry.register(Registries.ITEM, new Identifier(BHSMain.MOD_ID,"cooling_gel"), COOLING_GEL);
        Registry.register(Registries.ITEM, new Identifier(BHSMain.MOD_ID,"heating_gel"), HEATING_GEL);
        Registry.register(Registries.ITEM, new Identifier(BHSMain.MOD_ID,"thermometer"), THERMOMETER);
        Registry.register(Registries.ITEM, new Identifier(BHSMain.MOD_ID,"portable_fan"), PORTABLE_FAN);
        Registry.register(Registries.ITEM, new Identifier(BHSMain.MOD_ID, "stitches"), STITCHES);
        Registry.register(Registries.ITEM, new Identifier(BHSMain.MOD_ID, "tourniquet"), TOURNIQUET);
        Registry.register(Registries.ITEM, new Identifier(BHSMain.MOD_ID, "herbal_poultices"), HERBAL_POULTICES);
        ModItemsHolder.setStitches(STITCHES);
        ModItemsHolder.setTourniquet(TOURNIQUET);

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
            content.add(PRIMITIVE_MEDKIT_ITEM);
        });
        ItemGroupEvents.modifyEntriesEvent(BHSMain.BHS_GROUP).register(content -> {
            content.add(TRAUMA_KIT_ITEM);
        });
        ItemGroupEvents.modifyEntriesEvent(BHSMain.BHS_GROUP).register(content -> {
            content.add(WOOL_HELMET);
            content.add(WOOL_CHESTPLATE);
            content.add(WOOL_LEGGINGS);
            content.add(WOOL_BOOTS);
            content.add(STRAW_HAT);
            content.add(COOLING_GEL);
            content.add(HEATING_GEL);
            content.add(THERMOMETER);
            content.add(PORTABLE_FAN);
            content.add(STITCHES);
            content.add(TOURNIQUET);
            content.add(HERBAL_POULTICES);
        });
    }
}
