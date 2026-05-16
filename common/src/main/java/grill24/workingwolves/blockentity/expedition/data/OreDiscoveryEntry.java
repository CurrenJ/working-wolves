package grill24.workingwolves.blockentity.expedition.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.storage.loot.LootTable;

import java.util.List;
import java.util.Optional;

public record OreDiscoveryEntry(
        List<Integer> zones,
        int weight,
        Optional<Integer> minCollarTier,
        ResourceKey<LootTable> lootTable,
        Optional<ResourceKey<LootTable>> silkTouchLootTable,
        Optional<List<String>> biomeBonusCategories,
        Optional<ResourceKey<LootTable>> biomeBonusLootTable,
        List<String> journalLines
) {
    public static final Codec<OreDiscoveryEntry> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            Codec.INT.listOf().fieldOf("zones").forGetter(OreDiscoveryEntry::zones),
            Codec.INT.optionalFieldOf("weight", 1).forGetter(OreDiscoveryEntry::weight),
            Codec.INT.optionalFieldOf("min_collar_tier").forGetter(OreDiscoveryEntry::minCollarTier),
            ResourceKey.codec(Registries.LOOT_TABLE).fieldOf("loot_table").forGetter(OreDiscoveryEntry::lootTable),
            ResourceKey.codec(Registries.LOOT_TABLE).optionalFieldOf("silk_touch_loot_table").forGetter(OreDiscoveryEntry::silkTouchLootTable),
            Codec.STRING.listOf().optionalFieldOf("biome_bonus_categories").forGetter(OreDiscoveryEntry::biomeBonusCategories),
            ResourceKey.codec(Registries.LOOT_TABLE).optionalFieldOf("biome_bonus_loot_table").forGetter(OreDiscoveryEntry::biomeBonusLootTable),
            Codec.STRING.listOf().fieldOf("journal_lines").forGetter(OreDiscoveryEntry::journalLines)
        ).apply(instance, OreDiscoveryEntry::new)
    );
}
