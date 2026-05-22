package grill24.workingwolves.blockentity.expedition.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public record RoleEntry(
        String name,
        String icon,
        Optional<String> roleType,
        Optional<String> discoveryType,
        Optional<String> requiredToolTag,
        List<EventWeights> zoneEventWeights,
        List<HazardLevelWeights> hazardLevelWeights,
        Optional<DurabilityDamage> durabilityDamage,
        Map<String, List<String>> travelJournalLines,
        Map<String, List<String>> hazardJournalLines,
        Optional<ToolMessages> toolMessages,
        Optional<List<String>> requiresRoles
) {

    public record EventWeights(int travel, int discovery, int hazard) {
        public static final Codec<EventWeights> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.optionalFieldOf("travel", 50).forGetter(EventWeights::travel),
                Codec.INT.optionalFieldOf("discovery", 40).forGetter(EventWeights::discovery),
                Codec.INT.optionalFieldOf("hazard", 10).forGetter(EventWeights::hazard)
        ).apply(i, EventWeights::new));
    }

    public record HazardLevelWeights(int light, int moderate, int severe) {
        public static final Codec<HazardLevelWeights> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.optionalFieldOf("light", 75).forGetter(HazardLevelWeights::light),
                Codec.INT.optionalFieldOf("moderate", 22).forGetter(HazardLevelWeights::moderate),
                Codec.INT.optionalFieldOf("severe", 3).forGetter(HazardLevelWeights::severe)
        ).apply(i, HazardLevelWeights::new));
    }

    public record DurabilityDamage(int travel, int discovery, int lightHazard, int moderateHazard, int severeHazard) {
        public static final Codec<DurabilityDamage> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.optionalFieldOf("travel", 1).forGetter(DurabilityDamage::travel),
                Codec.INT.optionalFieldOf("discovery", 3).forGetter(DurabilityDamage::discovery),
                Codec.INT.optionalFieldOf("light_hazard", 1).forGetter(DurabilityDamage::lightHazard),
                Codec.INT.optionalFieldOf("moderate_hazard", 2).forGetter(DurabilityDamage::moderateHazard),
                Codec.INT.optionalFieldOf("severe_hazard", 3).forGetter(DurabilityDamage::severeHazard)
        ).apply(i, DurabilityDamage::new));
    }

    public record ToolMessages(
            boolean updateMiningStats,
            List<String> noTool,
            List<String> preciousBreak,
            List<String> broken,
            List<String> lastGone,
            List<String> switchedSpare,
            List<String> nearlyDone
    ) {
        public static final Codec<ToolMessages> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.BOOL.optionalFieldOf("update_mining_stats", false).forGetter(ToolMessages::updateMiningStats),
                Codec.STRING.listOf().fieldOf("no_tool").forGetter(ToolMessages::noTool),
                Codec.STRING.listOf().fieldOf("precious_break").forGetter(ToolMessages::preciousBreak),
                Codec.STRING.listOf().fieldOf("broken").forGetter(ToolMessages::broken),
                Codec.STRING.listOf().fieldOf("last_gone").forGetter(ToolMessages::lastGone),
                Codec.STRING.listOf().fieldOf("switched_spare").forGetter(ToolMessages::switchedSpare),
                Codec.STRING.listOf().fieldOf("nearly_done").forGetter(ToolMessages::nearlyDone)
        ).apply(i, ToolMessages::new));
    }

    public static final Codec<RoleEntry> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            Codec.STRING.fieldOf("name").forGetter(RoleEntry::name),
            Codec.STRING.fieldOf("icon").forGetter(RoleEntry::icon),
            Codec.STRING.optionalFieldOf("role_type").forGetter(RoleEntry::roleType),
            Codec.STRING.optionalFieldOf("discovery_type").forGetter(RoleEntry::discoveryType),
            Codec.STRING.optionalFieldOf("required_tool_tag").forGetter(RoleEntry::requiredToolTag),
            EventWeights.CODEC.listOf().fieldOf("zone_event_weights").forGetter(RoleEntry::zoneEventWeights),
            HazardLevelWeights.CODEC.listOf().fieldOf("hazard_level_weights").forGetter(RoleEntry::hazardLevelWeights),
            DurabilityDamage.CODEC.optionalFieldOf("durability_damage").forGetter(RoleEntry::durabilityDamage),
            Codec.unboundedMap(Codec.STRING, Codec.STRING.listOf()).fieldOf("travel_journal_lines").forGetter(RoleEntry::travelJournalLines),
            Codec.unboundedMap(Codec.STRING, Codec.STRING.listOf()).fieldOf("hazard_journal_lines").forGetter(RoleEntry::hazardJournalLines),
            ToolMessages.CODEC.optionalFieldOf("tool_messages").forGetter(RoleEntry::toolMessages),
            Codec.STRING.listOf().optionalFieldOf("requires_roles").forGetter(RoleEntry::requiresRoles)
        ).apply(instance, RoleEntry::new)
    );
}
