package grill24.workingwolves.blockentity.expedition;

import grill24.workingwolves.blockentity.expedition.data.RoleEntry;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

class RoleEventHandler {

    static void rollEvent(Level level, int zone, RoleEntry entry, ExpeditionSimulator sim) {
        Random rng = sim.newRng(level);

        int wIdx = Math.min(zone, entry.zoneEventWeights().size() - 1);
        RoleEntry.EventWeights weights = entry.zoneEventWeights().get(wIdx);
        int total = weights.travel() + weights.discovery() + weights.hazard();
        if (total <= 0) return;
        int roll = rng.nextInt(total);

        String roleType = entry.roleType().orElse(entry.discoveryType().orElse(""));

        if (roll < weights.travel()) {
            List<String> lines = travelLines(entry, zone);
            if (!lines.isEmpty()) sim.addLogLine(lines.get(rng.nextInt(lines.size())));
            applyDurability(level, entry, entry.durabilityDamage().map(RoleEntry.DurabilityDamage::travel).orElse(0), sim);

        } else if (roll < weights.travel() + weights.discovery()) {
            rollDiscovery(level, zone, rng, roleType, sim);
            applyDurability(level, entry, entry.durabilityDamage().map(RoleEntry.DurabilityDamage::discovery).orElse(0), sim);

        } else {
            int hIdx = Math.min(zone, entry.hazardLevelWeights().size() - 1);
            ExpeditionSimulator.HazardLevel hl = pickHazardLevel(entry.hazardLevelWeights().get(hIdx), rng);
            List<String> lines = entry.hazardJournalLines().getOrDefault(hl.name().toLowerCase(), List.of());
            if (!lines.isEmpty()) sim.addLogLine(lines.get(rng.nextInt(lines.size())));
            sim.applyHazardCost(level, rng, hl);
            int dmg = switch (hl) {
                case LIGHT    -> entry.durabilityDamage().map(RoleEntry.DurabilityDamage::lightHazard).orElse(0);
                case MODERATE -> entry.durabilityDamage().map(RoleEntry.DurabilityDamage::moderateHazard).orElse(0);
                case SEVERE   -> entry.durabilityDamage().map(RoleEntry.DurabilityDamage::severeHazard).orElse(0);
            };
            applyDurability(level, entry, dmg, sim);
        }
    }

    private static List<String> travelLines(RoleEntry entry, int zone) {
        List<String> lines = entry.travelJournalLines().get(String.valueOf(zone));
        if (lines == null || lines.isEmpty()) lines = entry.travelJournalLines().get("0");
        return lines != null ? lines : List.of();
    }

    private static ExpeditionSimulator.HazardLevel pickHazardLevel(RoleEntry.HazardLevelWeights w, Random rng) {
        int total = w.light() + w.moderate() + w.severe();
        if (total <= 0) return ExpeditionSimulator.HazardLevel.LIGHT;
        int roll = rng.nextInt(total);
        if (roll < w.light()) return ExpeditionSimulator.HazardLevel.LIGHT;
        if (roll < w.light() + w.moderate()) return ExpeditionSimulator.HazardLevel.MODERATE;
        return ExpeditionSimulator.HazardLevel.SEVERE;
    }

    private static void rollDiscovery(Level level, int zone, Random rng, String roleType, ExpeditionSimulator sim) {
        if (roleType.isEmpty()) {
            // Cross-role with no explicit discovery_type: pick randomly from active roles
            List<String> options = new ArrayList<>();
            if (sim.hasMining()) options.add("mining");
            if (sim.hasHunting()) options.add("hunting");
            if (sim.hasWoodcutting()) options.add("woodcutting");
            if (!options.isEmpty()) roleType = options.get(rng.nextInt(options.size()));
        }
        switch (roleType) {
            case "mining"     -> MinerEventHandler.rollDiscovery(level, zone, rng, sim);
            case "hunting"    -> HunterEventHandler.rollDiscovery(level, zone, rng, sim);
            case "woodcutting" -> WoodcutterEventHandler.rollDiscovery(level, zone, rng, sim);
        }
    }

    private static void applyDurability(Level level, RoleEntry entry, int damage, ExpeditionSimulator sim) {
        if (damage <= 0 || entry.requiredToolTag().isEmpty() || entry.toolMessages().isEmpty()) return;
        if (!ToolDurabilityHelper.apply(level, damage, entry.requiredToolTag().get(), entry.toolMessages().get(), sim)) {
            sim.complete(level, false, false);
        }
    }
}
