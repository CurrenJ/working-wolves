package grill24.workingwolves.fabric.datagen;

import grill24.workingwolves.ModBlocks;
import grill24.workingwolves.ModItems;
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricRecipeProvider;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.recipes.*;
import net.minecraft.world.item.Items;

import java.util.concurrent.CompletableFuture;

public class ModRecipeProvider extends FabricRecipeProvider {
    public ModRecipeProvider(FabricPackOutput output, CompletableFuture<HolderLookup.Provider> registriesFuture) {
        super(output, registriesFuture);
    }

    @Override
    protected RecipeProvider createRecipeProvider(HolderLookup.Provider registries, RecipeOutput output) {
        return new RecipeProvider(registries, output) {
            @Override
            public void buildRecipes() {
                ShapedRecipeBuilder.shaped(this.registries.lookupOrThrow(Registries.ITEM), RecipeCategory.MISC, ModItems.LEATHER_COLLAR.value())
                        .pattern("LLL")
                        .pattern("L L")
                        .pattern("LLL")
                        .define('L', Items.LEATHER)
                        .unlockedBy("has_leather", has(Items.LEATHER))
                        .save(this.output);

                ShapedRecipeBuilder.shaped(this.registries.lookupOrThrow(Registries.ITEM), RecipeCategory.MISC, ModItems.IRON_STUDDED_COLLAR.value())
                        .pattern("IBI")
                        .pattern("BLB")
                        .pattern("IBI")
                        .define('I', Items.IRON_INGOT)
                        .define('B', Items.IRON_BLOCK)
                        .define('L', ModItems.LEATHER_COLLAR.value())
                        .unlockedBy("has_leather_collar", has(ModItems.LEATHER_COLLAR.value()))
                        .save(this.output);

                ShapedRecipeBuilder.shaped(this.registries.lookupOrThrow(Registries.ITEM), RecipeCategory.MISC, ModItems.GOLD_TRIMMED_COLLAR.value())
                        .pattern("GBG")
                        .pattern("BLB")
                        .pattern("GBG")
                        .define('G', Items.GOLD_INGOT)
                        .define('B', Items.GOLD_BLOCK)
                        .define('L', ModItems.IRON_STUDDED_COLLAR.value())
                        .unlockedBy("has_leather_collar", has(ModItems.LEATHER_COLLAR.value()))
                        .save(this.output);

                ShapedRecipeBuilder.shaped(this.registries.lookupOrThrow(Registries.ITEM), RecipeCategory.MISC, ModBlocks.DOG_BED.value())
                        .pattern("SBS")
                        .pattern("THT")
                        .pattern("TTT")
                        .define('T', Items.TERRACOTTA)
                        .define('B', Items.BRICKS)
                        .define('S', Items.BRICK_SLAB)
                        .define('H', Items.HAY_BLOCK)
                        .unlockedBy("has_leather", has(Items.LEATHER))
                        .save(this.output);
            }
        };
    }

    @Override
    public String getName() {
        return "Working Wolves Recipes";
    }
}
