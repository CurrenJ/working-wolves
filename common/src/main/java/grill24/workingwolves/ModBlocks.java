package grill24.workingwolves;

import grill24.workingwolves.architectury.RegistrationApiSided;
import grill24.workingwolves.block.DogBedBlock;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;

public class ModBlocks {
    public static Holder<Block> DOG_BED;

    public static void registerBlocks() {
        var api = RegistrationApiSided.getInstance();

        DOG_BED = api.registerBlock("dog_bed", loc ->
                new DogBedBlock(BlockBehaviour.Properties.of()
                        .strength(2.0f, 3.0f)
                        .noOcclusion()
                        .sound(SoundType.WOOD)
                        .setId(ResourceKey.create(Registries.BLOCK, loc))));
    }
}
