package grill24.workingwolves.blockentity.expedition.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;

import java.util.List;
import java.util.Optional;

public record ExpeditionLifecycleEntry(
        List<String> departureLines,
        List<String> recallLines,
        List<String> abandonedLines,
        List<String> deathLines,
        List<String> failureLines,
        List<String> successLines,
        List<String> arrivalLines,
        List<String> lowResourcesLines,
        List<String> lootLossLines,
        List<String> lowFoodLines
) {
    public static final Codec<ExpeditionLifecycleEntry> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            Codec.STRING.listOf().fieldOf("departure_lines").forGetter(ExpeditionLifecycleEntry::departureLines),
            Codec.STRING.listOf().fieldOf("recall_lines").forGetter(ExpeditionLifecycleEntry::recallLines),
            Codec.STRING.listOf().optionalFieldOf("abandoned_lines", List.of("Bed destroyed. Expedition abandoned.")).forGetter(ExpeditionLifecycleEntry::abandonedLines),
            Codec.STRING.listOf().fieldOf("death_lines").forGetter(ExpeditionLifecycleEntry::deathLines),
            Codec.STRING.listOf().fieldOf("failure_lines").forGetter(ExpeditionLifecycleEntry::failureLines),
            Codec.STRING.listOf().fieldOf("success_lines").forGetter(ExpeditionLifecycleEntry::successLines),
            Codec.STRING.listOf().fieldOf("arrival_lines").forGetter(ExpeditionLifecycleEntry::arrivalLines),
            Codec.STRING.listOf().fieldOf("low_resources_lines").forGetter(ExpeditionLifecycleEntry::lowResourcesLines),
            Codec.STRING.listOf().fieldOf("loot_loss_lines").forGetter(ExpeditionLifecycleEntry::lootLossLines),
            Codec.STRING.listOf().optionalFieldOf("low_food_lines", List.of("Last of the food. Making it count.")).forGetter(ExpeditionLifecycleEntry::lowFoodLines)
        ).apply(instance, ExpeditionLifecycleEntry::new)
    );

    public static Optional<ExpeditionLifecycleEntry> getDefault(ServerLevel level) {
        var reg = level.registryAccess().lookupOrThrow(ExpeditionRegistries.EXPEDITION_LIFECYCLE);
        return reg.getOptional(Identifier.fromNamespaceAndPath("workingwolves", "default"));
    }
}
