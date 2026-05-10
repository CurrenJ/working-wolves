package grill24.workingwolves.neoforge;

import grill24.workingwolves.WorkingWolves;
import grill24.workingwolves.architectury.IRegistrationApi;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

public class NeoForgeRegistrationApi implements IRegistrationApi {
    public static final NeoForgeRegistrationApi INSTANCE = new NeoForgeRegistrationApi();

    // ----- Registration Methods ----- //

    @Override
    public <I extends Item> Holder<Item> registerItem(final String name, final Function<Identifier, ? extends I> func) {
        return WorkingWolvesRegistriesNeoForge.ITEMS.register(name, func);
    }

    @Override
    public <I extends Block> Holder<Block> registerBlock(final String name, final Function<Identifier, ? extends I> func) {
        Holder<Block> blockHolder = WorkingWolvesRegistriesNeoForge.BLOCKS.register(name, func);
        WorkingWolvesRegistriesNeoForge.ITEMS.register(name, loc -> new BlockItem(blockHolder.value(), new Item.Properties().setId(ResourceKey.create(Registries.ITEM, loc))));
        return blockHolder;
    }

    @Override
    public Holder<BlockEntityType<?>> registerBlockEntityType(String name, BiFunction<BlockPos, BlockState, ? extends BlockEntity> factory, Supplier<Block[]> validBlocksSupplier) {
        return WorkingWolvesRegistriesNeoForge.BLOCK_ENTITY_TYPES.register(name, () ->
                new BlockEntityType<>(factory::apply, validBlocksSupplier.get())
        );
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Holder<DataComponentType<T>> registerDataComponent(String name, UnaryOperator<DataComponentType.Builder<T>> builderOperator) {
        return (Holder<DataComponentType<T>>) (Holder<?>) WorkingWolvesRegistriesNeoForge.DATA_COMPONENT_TYPES.register(name, () -> builderOperator.apply(DataComponentType.<T>builder()).build());
    }

    @Override
    public Holder<CreativeModeTab> registerCreativeModeTab(String name, Function<Identifier, ? extends CreativeModeTab> func) {
        return WorkingWolvesRegistriesNeoForge.CREATIVE_MODE_TABS.register(name, func);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Holder<SoundEvent> registerSoundEvent(String name) {
        return (Holder<SoundEvent>) (Holder<?>) WorkingWolvesRegistriesNeoForge.SOUND_EVENTS.register(name,
            () -> SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath(WorkingWolves.MODID, name)));
    }

    // ----- Registry Accessors ----- //

    @Override
    public Registry<Block> blocks() {
        return WorkingWolvesRegistriesNeoForge.BLOCKS.getRegistry().get();
    }

    @Override
    public Registry<Item> items() {
        return WorkingWolvesRegistriesNeoForge.ITEMS.getRegistry().get();
    }

    @Override
    public Registry<BlockEntityType<?>> blockEntityTypes() {
        return WorkingWolvesRegistriesNeoForge.BLOCK_ENTITY_TYPES.getRegistry().get();
    }

    @Override
    public Registry<DataComponentType<?>> dataComponentTypes() {
        return WorkingWolvesRegistriesNeoForge.DATA_COMPONENT_TYPES.getRegistry().get();
    }

    @Override
    public Registry<CreativeModeTab> creativeModeTabs() {
        return WorkingWolvesRegistriesNeoForge.CREATIVE_MODE_TABS.getRegistry().get();
    }

    @Override
    public Registry<SoundEvent> soundEvents() {
        return WorkingWolvesRegistriesNeoForge.SOUND_EVENTS.getRegistry().get();
    }

    // ----- Singleton Access ----- //

    public static NeoForgeRegistrationApi getInstance() {
        return INSTANCE;
    }
}
