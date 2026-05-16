package grill24.workingwolves.blockentity.expedition.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.storage.loot.LootTable;

import java.util.List;
import java.util.Optional;

public record RareEventEntry(
        RareEventConditions conditions,
        int weight,
        boolean crossRole,
        Optional<ResourceKey<LootTable>> lootTable,
        Optional<Integer> satiationDelta,
        Optional<String> applyHazard,
        boolean removeLastLoot,
        List<String> journalLines
) {
    public static final Codec<RareEventEntry> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            RareEventConditions.CODEC.optionalFieldOf("conditions", RareEventConditions.NONE).forGetter(RareEventEntry::conditions),
            Codec.INT.optionalFieldOf("weight", 10).forGetter(RareEventEntry::weight),
            Codec.BOOL.optionalFieldOf("cross_role", false).forGetter(RareEventEntry::crossRole),
            ResourceKey.codec(Registries.LOOT_TABLE).optionalFieldOf("loot_table").forGetter(RareEventEntry::lootTable),
            Codec.INT.optionalFieldOf("satiation_delta").forGetter(RareEventEntry::satiationDelta),
            Codec.STRING.optionalFieldOf("apply_hazard").forGetter(RareEventEntry::applyHazard),
            Codec.BOOL.optionalFieldOf("remove_last_loot", false).forGetter(RareEventEntry::removeLastLoot),
            Codec.STRING.listOf().fieldOf("journal_lines").forGetter(RareEventEntry::journalLines)
        ).apply(instance, RareEventEntry::new)
    );
}
