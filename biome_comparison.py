#!/usr/bin/env python3
"""
biome_comparison.py — Show how biome affects expected loot across all biome categories.

Generates three heatmap comparisons (miner, hunter, all-roles) and saves them
to balance_charts/biome_comparison_*.png.

Each cell shows the mean count for that item in that biome. Rows are colored
relative to the item's best biome (darkest = highest), so you can immediately
see which biome unlocks or boosts each item.
"""

import importlib.util, random
from pathlib import Path
import numpy as np
import matplotlib.pyplot as plt
import matplotlib.gridspec as gridspec
from matplotlib.colors import Normalize
from matplotlib.cm import ScalarMappable

# ── Import sim helpers ────────────────────────────────────────────────────────
_spec = importlib.util.spec_from_file_location(
    "expedition_sim", Path(__file__).parent / "expedition_sim.py"
)
_sim = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(_sim)

# ── Settings ──────────────────────────────────────────────────────────────────
N_SIMS   = 5000
SEED     = 42
OUT_DIR  = Path(__file__).parent / "balance_charts"
OUT_DIR.mkdir(exist_ok=True)

BIOMES = ["cave", "mountain", "forest", "plains", "ocean", "other"]

# Base palette
BG      = "#F5F0E8"
COLD    = "#F0EAD8"   # near-zero cell
HOT     = "#8B4513"   # max cell
TEXT_LO = "#9B7B55"
TEXT_HI = "#FFF8EE"
DIVIDER = "#D5C9B5"

# ── Comparison configs ────────────────────────────────────────────────────────
COMPARISONS = [
    {
        "name":  "miner",
        "title": "Miner — Tier 3  (biome comparison)",
        "base":  dict(mining=True, collar_tier=3),
    },
    {
        "name":  "hunter",
        "title": "Hunter — Tier 3  (biome comparison)",
        "base":  dict(hunting=True, collar_tier=3),
    },
    {
        "name":  "all_roles",
        "title": "All Roles (mining + hunting + woodcutting) — Tier 3\nwood biome: taiga  (biome comparison)",
        "base":  dict(mining=True, hunting=True, woodcutting=True,
                      collar_tier=3, wood_biome="taiga"),
    },
]


# ── Simulation helpers ────────────────────────────────────────────────────────
def run_biome(base_cfg_raw, biome, loot_tables, ore_disc, mob_enc,
              wood_disc, rare_ev, roles):
    tier = base_cfg_raw.get("collar_tier", 1)
    cfg = {
        "collar_tier":    tier,
        "mining":         base_cfg_raw.get("mining",      False),
        "hunting":        base_cfg_raw.get("hunting",     False),
        "woodcutting":    base_cfg_raw.get("woodcutting", False),
        "biome_category": biome,
        "wood_biome":     base_cfg_raw.get("wood_biome",  "forest"),
        "fortune":        base_cfg_raw.get("fortune",     0),
        "looting":        base_cfg_raw.get("looting",     0),
        "silk_touch":     base_cfg_raw.get("silk_touch",  False),
        "total_ticks":    _sim.TIER_DURATIONS[tier],
    }
    rng = random.Random(SEED)
    results = [
        _sim.simulate_one(cfg, loot_tables, ore_disc, mob_enc,
                          wood_disc, rare_ev, roles, rng)
        for _ in range(N_SIMS)
    ]
    means = {}
    all_items = set(k for r in results for k in r)
    for item in all_items:
        means[item] = np.mean([r.get(item, 0) for r in results])
    return means


# ── Plot ──────────────────────────────────────────────────────────────────────
def plot_comparison(comp_name, title, biome_means):
    """
    biome_means: dict[biome] -> dict[item] -> mean
    """
    all_items = sorted(set(k for m in biome_means.values() for k in m))
    if not all_items:
        print(f"  No items for {comp_name}, skipping.")
        return

    # Build matrix: rows=items, cols=biomes
    mat = np.array([
        [biome_means[b].get(item, 0.0) for b in BIOMES]
        for item in all_items
    ])  # shape: (n_items, n_biomes)

    # Sort rows by global mean descending
    row_means = mat.mean(axis=1)
    order     = np.argsort(-row_means)
    mat        = mat[order]
    items_sorted = [all_items[i] for i in order]
    n_items = len(items_sorted)

    # Row-normalise: 0 = item never appears, 1 = biome with highest mean
    row_max = mat.max(axis=1, keepdims=True)
    with np.errstate(invalid="ignore", divide="ignore"):
        mat_norm = np.where(row_max > 0, mat / row_max, 0.0)

    # ── Figure ────────────────────────────────────────────────────────────────
    cell_h   = 0.48
    cell_w   = 1.4
    fig_w    = len(BIOMES) * cell_w + 4.5
    fig_h    = n_items * cell_h + 3.5

    fig, ax = plt.subplots(figsize=(fig_w, fig_h), facecolor=BG)
    ax.set_facecolor(BG)

    # ── Draw cells ────────────────────────────────────────────────────────────
    from matplotlib.patches import FancyBboxPatch
    import matplotlib.colors as mcolors

    cmap    = mcolors.LinearSegmentedColormap.from_list("expedition", [COLD, "#C8864A", HOT])

    for ri, item in enumerate(items_sorted):
        for ci, biome in enumerate(BIOMES):
            v_norm = mat_norm[ri, ci]
            v_abs  = mat[ri, ci]

            # Background rectangle
            color = cmap(v_norm)
            rect  = plt.Rectangle([ci - 0.5, ri - 0.5], 1, 1,
                                   color=color, zorder=1)
            ax.add_patch(rect)

            # Value text
            text_color = TEXT_HI if v_norm > 0.55 else TEXT_LO
            if v_abs >= 0.5:
                label = f"{v_abs:.1f}"
            elif v_abs > 0:
                label = f"{v_abs:.2f}"
            else:
                label = "—"
            ax.text(ci, ri, label, ha="center", va="center",
                    fontsize=8, color=text_color, zorder=2,
                    fontweight="bold" if v_norm > 0.75 else "normal")

    # ── Axes cosmetics ────────────────────────────────────────────────────────
    ax.set_xlim(-0.5, len(BIOMES) - 0.5)
    ax.set_ylim(-0.5, n_items - 0.5)
    ax.invert_yaxis()

    ax.set_xticks(range(len(BIOMES)))
    ax.set_xticklabels(BIOMES, fontsize=10, fontweight="bold", color="#3D2208")
    ax.xaxis.set_ticks_position("top")
    ax.xaxis.set_label_position("top")

    ax.set_yticks(range(n_items))
    ax.set_yticklabels(items_sorted, fontsize=9, color="#3D2208")

    # Grid lines between cells
    for x in np.arange(0.5, len(BIOMES) - 0.5):
        ax.axvline(x, color=DIVIDER, linewidth=0.8, zorder=3)
    for y in np.arange(0.5, n_items - 0.5):
        ax.axhline(y, color=DIVIDER, linewidth=0.5, zorder=3)

    for spine in ax.spines.values():
        spine.set_edgecolor(DIVIDER)

    # Colorbar legend
    sm  = ScalarMappable(cmap=cmap, norm=Normalize(0, 1))
    sm.set_array([])
    cbar = fig.colorbar(sm, ax=ax, orientation="vertical",
                        fraction=0.025, pad=0.02, aspect=30)
    cbar.set_label("Relative yield\n(1.0 = best biome for this item)",
                   fontsize=8, color="#3D2208")
    cbar.ax.yaxis.set_tick_params(color="#3D2208", labelsize=7)
    cbar.outline.set_edgecolor(DIVIDER)

    # Note about biome bonus ores
    note = (
        "★ Cave / Mountain give bonus ore rolls for coal, iron, gold, diamond, redstone.\n"
        "  Ocean unlocks elder_guardian_shadow.  Forest/Plains unlock wolf_pack_standoff & buried_chest.\n"
        "  Cave excludes wandering_trader_deal.  Blaze only spawns in cave / mountain / other."
    )
    fig.text(0.01, 0.005, note, fontsize=7.5, color="#6B4C2A",
             va="bottom", style="italic",
             bbox=dict(boxstyle="round,pad=0.3", facecolor="#EDE6D6",
                       edgecolor=DIVIDER, alpha=0.9))

    fig.suptitle(
        f"Working Wolves — Biome Comparison\n{title}  ·  {N_SIMS:,} runs each",
        fontsize=12, fontweight="bold", color="#3D2208", y=0.99
    )

    plt.tight_layout(rect=[0, 0.06, 1, 0.96])

    out = OUT_DIR / f"biome_comparison_{comp_name}.png"
    plt.savefig(out, dpi=150, bbox_inches="tight", facecolor=BG)
    plt.close(fig)
    print(f"  Saved → {out}")


# ── Main ──────────────────────────────────────────────────────────────────────
def main():
    print("Loading data...", flush=True)
    loot_tables      = _sim.load_loot_tables()
    ore_disc         = _sim.load_ore_discoveries()
    mob_enc          = _sim.load_mob_encounters()
    wood_disc        = _sim.load_wood_discoveries()
    rare_ev          = _sim.load_rare_events()
    roles            = _sim.load_roles()

    for comp in COMPARISONS:
        print(f"\n── {comp['title']} ──")
        biome_means = {}
        for biome in BIOMES:
            print(f"  Simulating biome={biome}...", end=" ", flush=True)
            biome_means[biome] = run_biome(
                comp["base"], biome,
                loot_tables, ore_disc, mob_enc, wood_disc, rare_ev, roles
            )
            print("done")
        plot_comparison(comp["name"], comp["title"], biome_means)

    print("\n✓  Done.")


if __name__ == "__main__":
    main()
