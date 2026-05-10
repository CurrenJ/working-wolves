package grill24.workingwolves;

import grill24.workingwolves.architectury.RegistrationApiSided;
import grill24.workingwolves.blockentity.DogBedBlockEntity;
import net.minecraft.core.Holder;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;

public class ModBlockEntityTypes {
    public static Holder<BlockEntityType<?>> DOG_BED;

    public static void registerBlockEntityTypes() {
        var api = RegistrationApiSided.getInstance();

        DOG_BED = api.registerBlockEntityType("dog_bed",
                DogBedBlockEntity::new,
                () -> new Block[]{ModBlocks.DOG_BED.value()});
    }
}
