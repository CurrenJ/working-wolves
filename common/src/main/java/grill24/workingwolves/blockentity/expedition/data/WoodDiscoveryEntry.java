package grill24.workingwolves.blockentity.expedition.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.storage.loot.LootTable;

import java.util.List;

public record WoodDiscoveryEntry(
        String woodBiomeId,
        ResourceKey<LootTable> lootTable,
        List<String> journalLines
) {
    public static final Codec<WoodDiscoveryEntry> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            Codec.STRING.fieldOf("wood_biome_id").forGetter(WoodDiscoveryEntry::woodBiomeId),
            ResourceKey.codec(Registries.LOOT_TABLE).fieldOf("loot_table").forGetter(WoodDiscoveryEntry::lootTable),
            Codec.STRING.listOf().fieldOf("journal_lines").forGetter(WoodDiscoveryEntry::journalLines)
        ).apply(instance, WoodDiscoveryEntry::new)
    );
}
