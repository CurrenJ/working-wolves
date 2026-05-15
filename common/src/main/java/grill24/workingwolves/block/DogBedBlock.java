package grill24.workingwolves.block;

import com.mojang.serialization.MapCodec;
import grill24.workingwolves.ModBlockEntityTypes;
import grill24.workingwolves.blockentity.DogBedBlockEntity;
import grill24.workingwolves.api.IWorkingWolf;
import grill24.workingwolves.pairing.WolfBedPairing;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public class DogBedBlock extends BaseEntityBlock {
    public static final MapCodec<DogBedBlock> CODEC = simpleCodec(DogBedBlock::new);
    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;

    public DogBedBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DogBedBlockEntity(pos, state);
    }

    @Nullable
    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) return null;
        return createTickerHelper(type, (BlockEntityType<DogBedBlockEntity>) ModBlockEntityTypes.DOG_BED.value(), DogBedBlockEntity::serverTick);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (level.getBlockEntity(pos) instanceof DogBedBlockEntity be) {
            // Shift-right-click: start bed pairing
            if (player.isShiftKeyDown()) {
                if (player instanceof ServerPlayer sp) {
                    WolfBedPairing.startBedPairing(sp, pos.immutable(), level.dimension());
                    sp.sendSystemMessage(Component.translatable("message.workingwolves.bed_pairing_started"));
                }
                return InteractionResult.SUCCESS;
            }

            // Normal right-click: open GUI
            String bedTitle = be.getAssignedWolfName() != null
                ? be.getAssignedWolfName() + "'s Dog Bed"
                : "Dog Bed";
            player.openMenu(new SimpleMenuProvider(
                    (containerId, inventory, p) -> ChestMenu.threeRows(containerId, inventory, be),
                    Component.literal(bedTitle)
            ));
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public void destroy(LevelAccessor levelAccessor, BlockPos pos, BlockState state) {
        if (levelAccessor.getBlockEntity(pos) instanceof DogBedBlockEntity be) {
            be.dropContents((Level) levelAccessor, pos);
            ((Level) levelAccessor).updateNeighbourForOutputSignal(pos, this);
        }
        super.destroy(levelAccessor, pos, state);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return this.defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }
}
