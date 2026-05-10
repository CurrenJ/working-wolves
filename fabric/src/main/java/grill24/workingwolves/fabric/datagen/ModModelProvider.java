package grill24.workingwolves.fabric.datagen;

import grill24.workingwolves.ModBlocks;
import grill24.workingwolves.ModItems;
import grill24.workingwolves.WorkingWolves;
import net.fabricmc.fabric.api.client.datagen.v1.provider.FabricModelProvider;
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.blockstates.MultiVariantGenerator;
import net.minecraft.client.data.models.blockstates.PropertyDispatch;
import net.minecraft.client.data.models.model.ModelTemplates;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public class ModModelProvider extends FabricModelProvider {
    private static final Identifier DOG_BED_MODEL = WorkingWolves.id("block/dog_bed");

    public ModModelProvider(FabricPackOutput output) {
        super(output);
    }

    @Override
    public void generateBlockStateModels(BlockModelGenerators blockModels) {
        var block = ModBlocks.DOG_BED.value();
        blockModels.blockStateOutput.accept(
                MultiVariantGenerator.dispatch(block, BlockModelGenerators.plainVariant(DOG_BED_MODEL))
                        .with(PropertyDispatch.modify(BlockStateProperties.HORIZONTAL_FACING)
                                .select(Direction.EAST, BlockModelGenerators.Y_ROT_90)
                                .select(Direction.SOUTH, BlockModelGenerators.Y_ROT_180)
                                .select(Direction.WEST, BlockModelGenerators.Y_ROT_270)
                                .select(Direction.NORTH, BlockModelGenerators.NOP)));
        blockModels.registerSimpleItemModel(block, DOG_BED_MODEL);
    }

    @Override
    public void generateItemModels(ItemModelGenerators itemModels) {
        itemModels.generateFlatItem(ModItems.LEATHER_COLLAR.value(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.IRON_STUDDED_COLLAR.value(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.GOLD_TRIMMED_COLLAR.value(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.DISPATCH_WHISTLE.value(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.RECALL_WHISTLE.value(), ModelTemplates.FLAT_ITEM);
    }
}
