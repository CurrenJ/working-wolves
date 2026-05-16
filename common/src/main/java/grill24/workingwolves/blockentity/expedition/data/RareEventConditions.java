package grill24.workingwolves.blockentity.expedition.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;

public record RareEventConditions(
        List<Integer> zones,
        List<String> biomeCategories,
        List<String> excludedBiomeCategories,
        int minCollarTier,
        int maxCollarTier,
        boolean requiresMining,
        boolean requiresHunting,
        boolean requiresWoodcutting,
        boolean excludesMining,
        boolean excludesHunting,
        boolean excludesWoodcutting
) {
    public static final RareEventConditions NONE = new RareEventConditions(
            List.of(), List.of(), List.of(), 0, Integer.MAX_VALUE,
            false, false, false, false, false, false
    );

    public static final Codec<RareEventConditions> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            Codec.INT.listOf().optionalFieldOf("zones", List.of()).forGetter(RareEventConditions::zones),
            Codec.STRING.listOf().optionalFieldOf("biome_categories", List.of()).forGetter(RareEventConditions::biomeCategories),
            Codec.STRING.listOf().optionalFieldOf("excluded_biome_categories", List.of()).forGetter(RareEventConditions::excludedBiomeCategories),
            Codec.INT.optionalFieldOf("min_collar_tier", 0).forGetter(RareEventConditions::minCollarTier),
            Codec.INT.optionalFieldOf("max_collar_tier", Integer.MAX_VALUE).forGetter(RareEventConditions::maxCollarTier),
            Codec.BOOL.optionalFieldOf("requires_mining", false).forGetter(RareEventConditions::requiresMining),
            Codec.BOOL.optionalFieldOf("requires_hunting", false).forGetter(RareEventConditions::requiresHunting),
            Codec.BOOL.optionalFieldOf("requires_woodcutting", false).forGetter(RareEventConditions::requiresWoodcutting),
            Codec.BOOL.optionalFieldOf("excludes_mining", false).forGetter(RareEventConditions::excludesMining),
            Codec.BOOL.optionalFieldOf("excludes_hunting", false).forGetter(RareEventConditions::excludesHunting),
            Codec.BOOL.optionalFieldOf("excludes_woodcutting", false).forGetter(RareEventConditions::excludesWoodcutting)
        ).apply(instance, RareEventConditions::new)
    );

    public boolean test(int zone, String biomeCategory, int collarTier,
                        boolean hasMining, boolean hasHunting, boolean hasWoodcutting) {
        if (!zones.isEmpty() && !zones.contains(zone)) return false;
        if (!biomeCategories.isEmpty() && !biomeCategories.contains(biomeCategory)) return false;
        if (!excludedBiomeCategories.isEmpty() && excludedBiomeCategories.contains(biomeCategory)) return false;
        if (collarTier < minCollarTier) return false;
        if (maxCollarTier != Integer.MAX_VALUE && collarTier > maxCollarTier) return false;
        if (requiresMining && !hasMining) return false;
        if (requiresHunting && !hasHunting) return false;
        if (requiresWoodcutting && !hasWoodcutting) return false;
        if (excludesMining && hasMining) return false;
        if (excludesHunting && hasHunting) return false;
        if (excludesWoodcutting && hasWoodcutting) return false;
        return true;
    }
}
