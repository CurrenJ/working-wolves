#!/usr/bin/env python3
"""
generate_balance_charts.py — Batch-generate expedition balance charts.

Loads the mod data once, then renders one PNG per configuration to balance_charts/.
Edit CONFIGS below to add or remove scenarios.

Usage:
    python generate_balance_charts.py
    python generate_balance_charts.py --sims 10000   # more accurate, slower
    python generate_balance_charts.py --out my_dir   # custom output folder
"""

import argparse
import importlib.util
import random
from pathlib import Path

# ── Import simulation helpers from expedition_sim.py ──────────────────────────
_spec = importlib.util.spec_from_file_location(
    "expedition_sim", Path(__file__).parent / "expedition_sim.py"
)
_sim = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(_sim)

# ── Configuration matrix ──────────────────────────────────────────────────────
#
# Each entry is a dict of overrides; unspecified keys use the defaults below.
# Defaults: collar_tier=1, biome=other, wood_biome=forest, fortune/looting=0,
#           silk_touch=False, duration uses collar-tier default.
#
CONFIGS = [

    # ── Miner: tier progression ───────────────────────────────────────────────
    dict(label="miner_tier1",              mining=True, collar_tier=1, biome="other"),
    dict(label="miner_tier2",              mining=True, collar_tier=2, biome="other"),
    dict(label="miner_tier3",              mining=True, collar_tier=3, biome="other"),

    # ── Miner: biome variants (tier 3) ───────────────────────────────────────
    dict(label="miner_tier3_cave",         mining=True, collar_tier=3, biome="cave"),
    dict(label="miner_tier3_mountain",     mining=True, collar_tier=3, biome="mountain"),

    # ── Miner: enchantment variants (tier 3, cave) ───────────────────────────
    dict(label="miner_tier3_cave_f1",      mining=True, collar_tier=3, biome="cave", fortune=1),
    dict(label="miner_tier3_cave_f2",      mining=True, collar_tier=3, biome="cave", fortune=2),
    dict(label="miner_tier3_cave_f3",      mining=True, collar_tier=3, biome="cave", fortune=3),
    dict(label="miner_tier3_cave_silk",    mining=True, collar_tier=3, biome="cave", silk_touch=True),
    dict(label="miner_tier3_mtn_f3",       mining=True, collar_tier=3, biome="mountain", fortune=3),

    # ── Hunter: tier progression ──────────────────────────────────────────────
    dict(label="hunter_tier1",             hunting=True, collar_tier=1, biome="other"),
    dict(label="hunter_tier2",             hunting=True, collar_tier=2, biome="other"),
    dict(label="hunter_tier3",             hunting=True, collar_tier=3, biome="other"),

    # ── Hunter: looting variants (tier 3) ────────────────────────────────────
    dict(label="hunter_tier3_l1",          hunting=True, collar_tier=3, biome="other", looting=1),
    dict(label="hunter_tier3_l2",          hunting=True, collar_tier=3, biome="other", looting=2),
    dict(label="hunter_tier3_l3",          hunting=True, collar_tier=3, biome="other", looting=3),

    # ── Woodcutter: tier progression (forest) ────────────────────────────────
    dict(label="woodcutter_tier1_forest",  woodcutting=True, collar_tier=1, biome="forest", wood_biome="forest"),
    dict(label="woodcutter_tier2_forest",  woodcutting=True, collar_tier=2, biome="forest", wood_biome="forest"),
    dict(label="woodcutter_tier3_forest",  woodcutting=True, collar_tier=3, biome="forest", wood_biome="forest"),

    # ── Woodcutter: biome variants (tier 3) ──────────────────────────────────
    dict(label="woodcutter_tier3_taiga",       woodcutting=True, collar_tier=3, biome="forest", wood_biome="taiga"),
    dict(label="woodcutter_tier3_jungle",      woodcutting=True, collar_tier=3, biome="forest", wood_biome="jungle"),
    dict(label="woodcutter_tier3_dark_forest", woodcutting=True, collar_tier=3, biome="forest", wood_biome="dark_forest"),
    dict(label="woodcutter_tier3_savanna",     woodcutting=True, collar_tier=3, biome="other",  wood_biome="savanna"),
    dict(label="woodcutter_tier3_mangrove",    woodcutting=True, collar_tier=3, biome="other",  wood_biome="mangrove"),
    dict(label="woodcutter_tier3_cherry",      woodcutting=True, collar_tier=3, biome="other",  wood_biome="cherry"),

    # ── Mining + Hunting ──────────────────────────────────────────────────────
    dict(label="mining+hunting_tier2",         mining=True, hunting=True, collar_tier=2, biome="other"),
    dict(label="mining+hunting_tier3_cave",    mining=True, hunting=True, collar_tier=3, biome="cave"),
    dict(label="mining+hunting_tier3_cave_enchanted",
         mining=True, hunting=True, collar_tier=3, biome="cave", fortune=2, looting=2),

    # ── Mining + Woodcutting ──────────────────────────────────────────────────
    dict(label="mining+woodcutting_tier3_cave_taiga",
         mining=True, woodcutting=True, collar_tier=3, biome="cave", wood_biome="taiga"),
    dict(label="mining+woodcutting_tier3_forest_taiga",
         mining=True, woodcutting=True, collar_tier=3, biome="forest", wood_biome="taiga"),

    # ── Hunting + Woodcutting ─────────────────────────────────────────────────
    dict(label="hunting+woodcutting_tier3_forest",
         hunting=True, woodcutting=True, collar_tier=3, biome="forest", wood_biome="forest"),

    # ── All three roles ───────────────────────────────────────────────────────
    dict(label="all_roles_tier2_forest",
         mining=True, hunting=True, woodcutting=True,
         collar_tier=2, biome="forest", wood_biome="forest"),
    dict(label="all_roles_tier3_forest_taiga",
         mining=True, hunting=True, woodcutting=True,
         collar_tier=3, biome="forest", wood_biome="taiga"),
    dict(label="all_roles_tier3_cave_taiga",
         mining=True, hunting=True, woodcutting=True,
         collar_tier=3, biome="cave", wood_biome="taiga"),
    dict(label="all_roles_tier3_cave_taiga_enchanted",
         mining=True, hunting=True, woodcutting=True,
         collar_tier=3, biome="cave", wood_biome="taiga", fortune=3, looting=3),
]


# ── Helpers ───────────────────────────────────────────────────────────────────
def _build_cfg(raw):
    tier = raw.get("collar_tier", 1)
    return {
        "collar_tier":    tier,
        "mining":         raw.get("mining",      False),
        "hunting":        raw.get("hunting",     False),
        "woodcutting":    raw.get("woodcutting", False),
        "biome_category": raw.get("biome",       "other"),
        "wood_biome":     raw.get("wood_biome",  "forest"),
        "fortune":        raw.get("fortune",     0),
        "looting":        raw.get("looting",     0),
        "silk_touch":     raw.get("silk_touch",  False),
        "total_ticks":    _sim.TIER_DURATIONS[tier],
    }


def main():
    parser = argparse.ArgumentParser(description="Batch-generate expedition balance charts")
    parser.add_argument("--sims", type=int, default=5000,
                        help="Simulations per config (default: 5000)")
    parser.add_argument("--seed", type=int, default=42,
                        help="Random seed (default: 42)")
    parser.add_argument("--out", type=str, default="balance_charts",
                        help="Output directory (default: balance_charts/)")
    args = parser.parse_args()

    out_dir = Path(__file__).parent / args.out
    out_dir.mkdir(exist_ok=True)

    print("Loading data...", flush=True)
    loot_tables      = _sim.load_loot_tables()
    ore_discoveries  = _sim.load_ore_discoveries()
    mob_encounters   = _sim.load_mob_encounters()
    wood_discoveries = _sim.load_wood_discoveries()
    rare_events      = _sim.load_rare_events()
    roles            = _sim.load_roles()

    total = len(CONFIGS)
    print(f"Generating {total} charts → {out_dir}/\n", flush=True)

    for i, raw in enumerate(CONFIGS, 1):
        label = raw.get("label", f"config_{i:02d}")
        cfg   = _build_cfg(raw)
        out   = out_dir / f"{label}.png"

        roles_str = " + ".join(r for r in ["mining", "hunting", "woodcutting"] if cfg[r])
        enc_parts = []
        if cfg["fortune"]:    enc_parts.append(f"Fortune {cfg['fortune']}")
        if cfg["looting"]:    enc_parts.append(f"Looting {cfg['looting']}")
        if cfg["silk_touch"]: enc_parts.append("Silk Touch")
        enc_str = f"  [{', '.join(enc_parts)}]" if enc_parts else ""

        print(f"  [{i:2d}/{total}]  {label}  ({roles_str}{enc_str})", flush=True)

        rng     = random.Random(args.seed)
        results = [
            _sim.simulate_one(cfg, loot_tables, ore_discoveries, mob_encounters,
                              wood_discoveries, rare_events, roles, rng)
            for _ in range(args.sims)
        ]

        _sim.plot_results(results, args.sims, cfg, save_path=str(out))

    print(f"\n✓  {total} charts saved to {out_dir}/")


if __name__ == "__main__":
    main()
