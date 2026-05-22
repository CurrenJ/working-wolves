package grill24.workingwolves.blockentity.expedition;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;

class ExpeditionBiomeHelper {

    static String getBiomeCategory(Level level, BlockPos pos) {
        Holder<Biome> holder = level.getBiome(pos);
        Identifier loc = holder.unwrapKey().map(k -> k.identifier()).orElse(null);
        if (loc == null) return "other";
        String path = loc.getPath();
        if (path.contains("cave") || path.contains("dripstone") || path.contains("lush")) return "cave";
        if (path.contains("mountain") || path.contains("peak") || path.contains("badlands") || path.contains("stony")) return "mountain";
        if (path.contains("forest") || path.contains("taiga") || path.contains("jungle") || path.contains("dark")) return "forest";
        if (path.contains("ocean") || path.contains("beach") || path.contains("swamp") || path.contains("mangrove")) return "ocean";
        if (path.contains("plains") || path.contains("meadow") || path.contains("savanna")) return "plains";
        return "other";
    }

    static String getWoodBiome(Level level, BlockPos pos) {
        Holder<Biome> holder = level.getBiome(pos);
        Identifier loc = holder.unwrapKey().map(k -> k.identifier()).orElse(null);
        if (loc == null) return "sparse";
        String path = loc.getPath();
        if (path.contains("bamboo") || path.contains("jungle")) return "jungle";
        if (path.contains("cherry")) return "cherry";
        if (path.contains("dark_forest")) return "dark_forest";
        if (path.contains("mangrove")) return "mangrove";
        if (path.contains("savanna")) return "savanna";
        if (path.contains("taiga") || path.contains("snowy_forest") || path.contains("spruce")
                || path.contains("grove") || path.contains("mountain") || path.contains("peak")) return "taiga";
        if (path.contains("forest") || path.contains("birch")) return "forest";
        return "sparse";
    }
}
