package grill24.workingwolves.blockentity.expedition.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.storage.loot.LootTable;

import java.util.List;
import java.util.Optional;

public record MobEncounterEntry(
        List<Integer> zones,
        int weight,
        Optional<List<String>> requiredBiomeCategories,
        Optional<Integer> minCollarTier,
        ResourceKey<LootTable> lootTable,
        List<String> journalLines
) {
    public static final Codec<MobEncounterEntry> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            Codec.INT.listOf().fieldOf("zones").forGetter(MobEncounterEntry::zones),
            Codec.INT.optionalFieldOf("weight", 1).forGetter(MobEncounterEntry::weight),
            Codec.STRING.listOf().optionalFieldOf("required_biome_categories").forGetter(MobEncounterEntry::requiredBiomeCategories),
            Codec.INT.optionalFieldOf("min_collar_tier").forGetter(MobEncounterEntry::minCollarTier),
            ResourceKey.codec(Registries.LOOT_TABLE).fieldOf("loot_table").forGetter(MobEncounterEntry::lootTable),
            Codec.STRING.listOf().fieldOf("journal_lines").forGetter(MobEncounterEntry::journalLines)
        ).apply(instance, MobEncounterEntry::new)
    );
}
