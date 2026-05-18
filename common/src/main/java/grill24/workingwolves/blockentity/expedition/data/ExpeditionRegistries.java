package grill24.workingwolves.blockentity.expedition.data;

import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

public class ExpeditionRegistries {

    public static final ResourceKey<Registry<MobEncounterEntry>> MOB_ENCOUNTER =
        ResourceKey.createRegistryKey(Identifier.fromNamespaceAndPath("workingwolves", "mob_encounter"));

    public static final ResourceKey<Registry<OreDiscoveryEntry>> ORE_DISCOVERY =
        ResourceKey.createRegistryKey(Identifier.fromNamespaceAndPath("workingwolves", "ore_discovery"));

    public static final ResourceKey<Registry<WoodDiscoveryEntry>> WOOD_DISCOVERY =
        ResourceKey.createRegistryKey(Identifier.fromNamespaceAndPath("workingwolves", "wood_discovery"));

    public static final ResourceKey<Registry<RareEventEntry>> RARE_EVENT =
        ResourceKey.createRegistryKey(Identifier.fromNamespaceAndPath("workingwolves", "rare_event"));

    public static final ResourceKey<Registry<RoleEntry>> ROLE =
        ResourceKey.createRegistryKey(Identifier.fromNamespaceAndPath("workingwolves", "role"));

    public static final ResourceKey<Registry<ExpeditionLifecycleEntry>> EXPEDITION_LIFECYCLE =
        ResourceKey.createRegistryKey(Identifier.fromNamespaceAndPath("workingwolves", "expedition_lifecycle"));
}
