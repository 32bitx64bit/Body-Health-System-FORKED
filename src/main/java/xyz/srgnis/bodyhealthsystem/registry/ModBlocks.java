package xyz.srgnis.bodyhealthsystem.registry;

import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import org.jetbrains.annotations.Nullable;
import xyz.srgnis.bodyhealthsystem.BHSMain;
import xyz.srgnis.bodyhealthsystem.block.AirConditionerBlock;
import xyz.srgnis.bodyhealthsystem.block.AirConditionerBlockEntity;
import xyz.srgnis.bodyhealthsystem.block.SpaceHeaterBlock;
import xyz.srgnis.bodyhealthsystem.block.SpaceHeaterBlockEntity;

public class ModBlocks {
    public static final Identifier AIR_CONDITIONER_ID = BHSMain.id("air_conditioner");
    public static final Identifier SPACE_HEATER_ID = BHSMain.id("space_heater");

    public static final Block AIR_CONDITIONER = new AirConditionerBlock(AbstractBlock.Settings.create()
            .strength(2.5f)
            .sounds(BlockSoundGroup.METAL)
            .nonOpaque());

    public static final Block SPACE_HEATER = new SpaceHeaterBlock(AbstractBlock.Settings.create()
            .strength(2.5f)
            .sounds(BlockSoundGroup.METAL)
            .nonOpaque());

    public static BlockEntityType<AirConditionerBlockEntity> AIR_CONDITIONER_BE;
    public static BlockEntityType<SpaceHeaterBlockEntity> SPACE_HEATER_BE;

    public static void registerBlocks() {
        // Block + BlockItem
        Registry.register(Registries.BLOCK, AIR_CONDITIONER_ID, AIR_CONDITIONER);
        Registry.register(Registries.ITEM, AIR_CONDITIONER_ID, new BlockItem(AIR_CONDITIONER, new Item.Settings()));
        Registry.register(Registries.BLOCK, SPACE_HEATER_ID, SPACE_HEATER);
        Registry.register(Registries.ITEM, SPACE_HEATER_ID, new BlockItem(SPACE_HEATER, new Item.Settings()));

        // Block entity type
        AIR_CONDITIONER_BE = Registry.register(
                Registries.BLOCK_ENTITY_TYPE,
                AIR_CONDITIONER_ID,
                BlockEntityType.Builder.create(AirConditionerBlockEntity::new, AIR_CONDITIONER).build(null)
        );
        SPACE_HEATER_BE = Registry.register(
                Registries.BLOCK_ENTITY_TYPE,
                SPACE_HEATER_ID,
                BlockEntityType.Builder.create(SpaceHeaterBlockEntity::new, SPACE_HEATER).build(null)
        );

        // Creative tab
        ItemGroupEvents.modifyEntriesEvent(BHSMain.BHS_GROUP).register(entries -> {
            entries.add(AIR_CONDITIONER.asItem());
            entries.add(SPACE_HEATER.asItem());
        });
    }
}
