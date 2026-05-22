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
                ShapelessRecipeBuilder.shapeless(this.registries.lookupOrThrow(Registries.ITEM), RecipeCategory.MISC, ModItems.LEATHER_COLLAR.value())
                        .requires(Items.LEATHER)
                        .requires(Items.STRING)
                        .unlockedBy("has_leather", has(Items.LEATHER))
                        .save(this.output);

                ShapelessRecipeBuilder.shapeless(this.registries.lookupOrThrow(Registries.ITEM), RecipeCategory.MISC, ModItems.IRON_STUDDED_COLLAR.value())
                        .requires(ModItems.LEATHER_COLLAR.value())
                        .requires(Items.IRON_INGOT, 2)
                        .unlockedBy("has_leather_collar", has(ModItems.LEATHER_COLLAR.value()))
                        .save(this.output);

                ShapelessRecipeBuilder.shapeless(this.registries.lookupOrThrow(Registries.ITEM), RecipeCategory.MISC, ModItems.GOLD_TRIMMED_COLLAR.value())
                        .requires(ModItems.IRON_STUDDED_COLLAR.value())
                        .requires(Items.GOLD_INGOT, 2)
                        .unlockedBy("has_iron_collar", has(ModItems.IRON_STUDDED_COLLAR.value()))
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
