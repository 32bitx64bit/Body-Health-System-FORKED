package xyz.srgnis.bodyhealthsystem.block;

import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import xyz.srgnis.bodyhealthsystem.registry.ModBlocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ItemScatterer;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.random.Random;

public class AirConditionerBlock extends BlockWithEntity {
    public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
    public static final BooleanProperty LIT = Properties.LIT; // "active" like furnace

    public AirConditionerBlock(Settings settings) {
        super(settings);
        this.setDefaultState(this.getDefaultState().with(FACING, Direction.NORTH).with(LIT, false));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING, LIT);
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        return this.getDefaultState().with(FACING, ctx.getHorizontalPlayerFacing().getOpposite());
    }

    @Override
    public boolean hasComparatorOutput(BlockState state) {
        return true;
    }

    @Override
    public int getComparatorOutput(BlockState state, World world, BlockPos pos) {
        BlockEntity be = world.getBlockEntity(pos);
        if (be instanceof AirConditionerBlockEntity ac) {
            return ac.getComparatorValue();
        }
        return 0;
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new AirConditionerBlockEntity(pos, state);
    }

    @Override
    public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (state.getBlock() != newState.getBlock()) {
            BlockEntity blockEntity = world.getBlockEntity(pos);
            if (blockEntity instanceof AirConditionerBlockEntity ac) {
                // drop inventory
                ItemScatterer.spawn(world, pos, ac);
                world.updateComparators(pos, this);
            }
            super.onStateReplaced(state, world, pos, newState, moved);
        }
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (world.isClient) return ActionResult.SUCCESS;
        BlockEntity be = world.getBlockEntity(pos);
        if (be instanceof AirConditionerBlockEntity ac) {
            player.openHandledScreen(ac);
        }
        return ActionResult.CONSUME;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        if (world.isClient) return null;
        return checkType(type, ModBlocks.AIR_CONDITIONER_BE, AirConditionerBlockEntity::serverTick);
    }

    @Override
    public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
        if (!state.get(LIT)) return;
        Direction facing = state.get(FACING);

        // spawn 0-2 particles per tick on average
        int count = random.nextBetween(0, 2);
        for (int i = 0; i < count; i++) {
            double baseX = pos.getX() + 0.5;
            double baseY = pos.getY() + 0.5 + (random.nextDouble() * 0.4 - 0.2); // around center
            double baseZ = pos.getZ() + 0.5;

            // Offset slightly outside the front face (similar to furnace smoke offset)
            double faceOut = 0.52;
            double lateral = random.nextDouble() * 0.6 - 0.3; // across the front face

            double x = baseX + facing.getOffsetX() * faceOut;
            double y = baseY;
            double z = baseZ + facing.getOffsetZ() * faceOut;
            if (facing.getAxis() == Direction.Axis.X) {
                z += lateral;
            } else {
                x += lateral;
            }

            // Slightly stronger outward velocity so flakes push outwards a bit more (~1–1.5 blocks)
            double speed = 0.10 + random.nextDouble() * 0.06;
            double vx = facing.getOffsetX() * speed;
            double vy = random.nextDouble() * 0.02 - 0.01; // small vertical drift
            double vz = facing.getOffsetZ() * speed;

            world.addParticle(ParticleTypes.SNOWFLAKE, x, y, z, vx, vy, vz);
        }
    }
}
