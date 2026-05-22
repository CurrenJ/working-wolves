#!/usr/bin/env python3
"""
expedition_sim.py — Monte Carlo balancing tool for Working Wolves expeditions.

Reads expedition data directly from the mod's resource files and simulates
N full expeditions to produce expected item yield tables.

Fortune / Looting note:
  ExpeditionLootHelper currently uses LootContextParamSets.CHEST, so Fortune
  and Looting are NOT applied in-game. This script models them as intended
  mechanics (Fortune multiplies ore counts; Looting boosts mob drop maxima)
  so you can preview balance impact before wiring them in.

Usage examples:
  python expedition_sim.py --mining --collar-tier 3 --biome cave --fortune 3
  python expedition_sim.py --hunting --collar-tier 2 --looting 2
  python expedition_sim.py --mining --hunting --woodcutting --collar-tier 3 --biome forest --wood-biome taiga
  python expedition_sim.py --mining --collar-tier 1 --sims 50000
"""

import argparse
import json
import random
import statistics
from collections import defaultdict
from pathlib import Path

# matplotlib is optional — only imported when --plot / --save is used

# ──────────────────────────────────────────────────────────────────────────────
# Paths
# ──────────────────────────────────────────────────────────────────────────────
REPO_ROOT      = Path(__file__).parent
DATA_ROOT      = REPO_ROOT / "common/src/main/resources/data/workingwolves"
LOOT_ROOT      = DATA_ROOT / "loot_table/expedition"
DISC_ROOT      = DATA_ROOT / "workingwolves"

# Expedition config constants (mirrors Config.java)
RARE_EVENT_CHANCE        = 0.025
CROSS_ROLE_RARE_CHANCE   = 0.12
EVENT_MIN_TICKS          = 160
EVENT_MAX_TICKS          = 600   # exclusive in Java → randint(min, max-1)

# Collar-tier default durations in ticks (10 / 18 / 25 min × 1200 t/min)
TIER_DURATIONS = {0: 12000, 1: 12000, 2: 21600, 3: 30000, 4: 30000}


# ──────────────────────────────────────────────────────────────────────────────
# Data loading
# ──────────────────────────────────────────────────────────────────────────────
def _load_json(path):
    with open(path) as f:
        return json.load(f)


def _loot_key_to_path(key):
    """'workingwolves:expedition/miner/coal' → 'expedition/miner/coal'"""
    return key.split(":", 1)[1]


def load_loot_tables():
    tables = {}
    for path in LOOT_ROOT.rglob("*.json"):
        rel = path.relative_to(LOOT_ROOT)
        key = f"expedition/{rel.with_suffix('')}"
        tables[key] = _load_json(path)
    return tables


def load_ore_discoveries():
    return [_load_json(p) for p in sorted((DISC_ROOT / "ore_discovery").glob("*.json"))]


def load_mob_encounters():
    return [_load_json(p) for p in sorted((DISC_ROOT / "mob_encounter").glob("*.json"))]


def load_wood_discoveries():
    return [_load_json(p) for p in sorted((DISC_ROOT / "wood_discovery").glob("*.json"))]


def load_rare_events():
    return [_load_json(p) for p in sorted((DISC_ROOT / "rare_event").glob("*.json"))]


def load_roles():
    """
    Returns dict: frozenset(role_types) → role_entry dict
    Single roles  keyed as frozenset({"mining"}) etc.
    Combined roles keyed as frozenset({"mining","hunting"}) etc.
    """
    roles = {}
    for path in sorted((DISC_ROOT / "role").glob("*.json")):
        d = _load_json(path)
        reqs = d.get("requires_roles")
        if reqs:
            key = frozenset(reqs)
        else:
            rt = d.get("role_type")
            key = frozenset([rt]) if rt else frozenset()
        roles[key] = d
    return roles


# ──────────────────────────────────────────────────────────────────────────────
# Loot table rolling
# ──────────────────────────────────────────────────────────────────────────────
def _roll_count(spec, rng):
    if isinstance(spec, (int, float)):
        return int(spec)
    t = spec.get("type", "")
    if t == "minecraft:uniform":
        return rng.randint(int(spec["min"]), int(spec["max"]))
    if t == "minecraft:constant":
        return int(spec["value"])
    return 1


def _roll_pool(pool, rng, fortune=0, looting=0, ore_roll=False, mob_roll=False):
    drops = defaultdict(int)
    rolls = _roll_count(pool.get("rolls", 1), rng)

    for _ in range(rolls):
        entries = pool.get("entries", [])
        total_w = sum(e.get("weight", 1) for e in entries)
        if total_w <= 0:
            continue

        pick = rng.randint(0, total_w - 1)
        cursor = 0
        chosen = None
        for e in entries:
            cursor += e.get("weight", 1)
            if pick < cursor:
                chosen = e
                break

        if chosen is None or chosen.get("type") == "minecraft:empty":
            continue
        if chosen.get("type") != "minecraft:item":
            continue

        item = chosen["name"].replace("minecraft:", "")
        count = 1
        for fn in chosen.get("functions", []):
            fname = fn.get("function", "")
            if "set_count" in fname:
                count = _roll_count(fn["count"], rng)

        # Fortune: ore drops multiply by (uniform(0, fortune) + 1)
        # This models the MC fortune_ore_drops formula applied post-roll.
        if ore_roll and fortune > 0:
            count *= rng.randint(0, fortune) + 1

        # Looting: add uniform(0, looting) to the rolled count per item.
        if mob_roll and looting > 0:
            count += rng.randint(0, looting)

        drops[item] += count

    return dict(drops)


def _roll_table(table_key, loot_tables, rng, fortune=0, looting=0,
                ore_roll=False, mob_roll=False):
    key = _loot_key_to_path(table_key)
    table = loot_tables.get(key)
    if not table:
        return {}
    drops = defaultdict(int)
    for pool in table.get("pools", []):
        for item, cnt in _roll_pool(pool, rng, fortune=fortune, looting=looting,
                                    ore_roll=ore_roll, mob_roll=mob_roll).items():
            drops[item] += cnt
    return dict(drops)


# ──────────────────────────────────────────────────────────────────────────────
# Zone from expedition progress
# ──────────────────────────────────────────────────────────────────────────────
def _get_zone(elapsed, total, collar_tier):
    progress = elapsed / max(total, 1)
    max_zone = 2 if collar_tier >= 2 else 1
    if progress < 0.25:
        z = 0
    elif progress < 0.75:
        z = 1
    else:
        z = 2
    return min(z, max_zone)


# ──────────────────────────────────────────────────────────────────────────────
# Rare event condition check
# ──────────────────────────────────────────────────────────────────────────────
def _check_rare_cond(cond, zone, biome, collar_tier,
                     has_mining, has_hunting, has_woodcutting):
    zones = cond.get("zones", [])
    if zones and zone not in zones:
        return False
    biomes = cond.get("biome_categories", [])
    if biomes and biome not in biomes:
        return False
    excl = cond.get("excluded_biome_categories", [])
    if excl and biome in excl:
        return False
    if collar_tier < cond.get("min_collar_tier", 0):
        return False
    if collar_tier > cond.get("max_collar_tier", 999):
        return False
    if cond.get("requires_mining")    and not has_mining:      return False
    if cond.get("requires_hunting")   and not has_hunting:     return False
    if cond.get("requires_woodcutting") and not has_woodcutting: return False
    if cond.get("excludes_mining")    and has_mining:          return False
    if cond.get("excludes_hunting")   and has_hunting:         return False
    if cond.get("excludes_woodcutting") and has_woodcutting:   return False
    return True


# ──────────────────────────────────────────────────────────────────────────────
# Discovery rollers
# ──────────────────────────────────────────────────────────────────────────────
def _roll_ore(zone, collar_tier, biome, loot_tables, ore_discoveries,
              rng, fortune, silk_touch):
    pool = [e for e in ore_discoveries
            if zone in e["zones"] and collar_tier >= e.get("min_collar_tier", 0)]
    if not pool:
        return {}

    total_w = sum(e["weight"] for e in pool)
    pick = rng.randint(0, total_w - 1)
    cursor = 0
    chosen = pool[-1]
    for e in pool:
        cursor += e["weight"]
        if pick < cursor:
            chosen = e
            break

    if silk_touch and "silk_touch_loot_table" in chosen:
        drops = _roll_table(chosen["silk_touch_loot_table"], loot_tables, rng)
    else:
        drops = _roll_table(chosen["loot_table"], loot_tables, rng,
                            fortune=fortune, ore_roll=True)

    bonus_cats = chosen.get("biome_bonus_categories", [])
    if bonus_cats and biome in bonus_cats and "biome_bonus_loot_table" in chosen:
        eff_fortune = 0 if silk_touch else fortune
        bonus = _roll_table(chosen["biome_bonus_loot_table"], loot_tables, rng,
                            fortune=eff_fortune, ore_roll=not silk_touch)
        for item, cnt in bonus.items():
            drops[item] = drops.get(item, 0) + cnt

    return drops


def _roll_mob(zone, collar_tier, biome, loot_tables, mob_encounters,
              rng, looting):
    pool = [e for e in mob_encounters
            if zone in e["zones"]
            and collar_tier >= e.get("min_collar_tier", 0)
            and (not e.get("required_biome_categories")
                 or biome in e["required_biome_categories"])]
    if not pool:
        return {}

    total_w = sum(e["weight"] for e in pool)
    pick = rng.randint(0, total_w - 1)
    cursor = 0
    chosen = pool[-1]
    for e in pool:
        cursor += e["weight"]
        if pick < cursor:
            chosen = e
            break

    return _roll_table(chosen["loot_table"], loot_tables, rng,
                       looting=looting, mob_roll=True)


def _roll_wood(wood_biome, loot_tables, wood_discoveries, rng):
    entry = next((e for e in wood_discoveries
                  if e["wood_biome_id"] == wood_biome), None)
    if not entry:
        return {}
    return _roll_table(entry["loot_table"], loot_tables, rng)


def _roll_discovery(role_type, zone, collar_tier, biome, wood_biome,
                    loot_tables, ore_discoveries, mob_encounters, wood_discoveries,
                    rng, fortune, looting, silk_touch,
                    has_mining, has_hunting, has_woodcutting):
    if not role_type:
        options = (["mining"] if has_mining else []) + \
                  (["hunting"] if has_hunting else []) + \
                  (["woodcutting"] if has_woodcutting else [])
        if not options:
            return {}
        role_type = rng.choice(options)

    if role_type == "mining":
        return _roll_ore(zone, collar_tier, biome, loot_tables, ore_discoveries,
                         rng, fortune, silk_touch)
    if role_type == "hunting":
        return _roll_mob(zone, collar_tier, biome, loot_tables, mob_encounters,
                         rng, looting)
    if role_type == "woodcutting":
        return _roll_wood(wood_biome, loot_tables, wood_discoveries, rng)
    return {}


# ──────────────────────────────────────────────────────────────────────────────
# Rare event roller
# ──────────────────────────────────────────────────────────────────────────────
def _roll_rare(zone, biome, collar_tier, has_mining, has_hunting, has_woodcutting,
               loot_tables, rare_events, rng, cross_role):
    pool = []
    for e in rare_events:
        if bool(e.get("cross_role", False)) != cross_role:
            continue
        if not _check_rare_cond(e.get("conditions", {}), zone, biome, collar_tier,
                                has_mining, has_hunting, has_woodcutting):
            continue
        pool.append(e)
    if not pool:
        return {}

    total_w = sum(e["weight"] for e in pool)
    pick = rng.randint(0, total_w - 1)
    cursor = 0
    chosen = pool[-1]
    for e in pool:
        cursor += e["weight"]
        if pick < cursor:
            chosen = e
            break

    if "loot_table" in chosen:
        return _roll_table(chosen["loot_table"], loot_tables, rng)
    return {}


# ──────────────────────────────────────────────────────────────────────────────
# Single expedition simulation
# ──────────────────────────────────────────────────────────────────────────────
def simulate_one(cfg, loot_tables, ore_discoveries, mob_encounters,
                 wood_discoveries, rare_events, roles, rng):

    collar_tier   = cfg["collar_tier"]
    has_mining    = cfg["mining"]
    has_hunting   = cfg["hunting"]
    has_woodcutting = cfg["woodcutting"]
    biome         = cfg["biome_category"]
    wood_biome    = cfg["wood_biome"]
    fortune       = cfg["fortune"]
    looting       = cfg["looting"]
    silk_touch    = cfg["silk_touch"]
    total_ticks   = cfg["total_ticks"]

    active_roles = frozenset(
        (["mining"] if has_mining else []) +
        (["hunting"] if has_hunting else []) +
        (["woodcutting"] if has_woodcutting else [])
    )
    role_count = len(active_roles)
    if role_count == 0:
        return {}

    # Role entry: combined if 2+, else single.
    # Combined roles always used when available (mirrors Java findCrossRoleEntry logic).
    role_entry = roles.get(active_roles)
    if role_entry is None:
        for rt in active_roles:
            role_entry = roles.get(frozenset([rt]))
            if role_entry:
                break
    if role_entry is None:
        return {}

    zone_ew  = role_entry["zone_event_weights"]
    role_type = role_entry.get("role_type") or role_entry.get("discovery_type") or ""

    elapsed = 0
    event_timer = rng.randint(EVENT_MIN_TICKS, EVENT_MAX_TICKS - 1)
    drops_total = defaultdict(int)

    while elapsed < total_ticks:
        elapsed += 1
        event_timer -= 1

        if event_timer > 0:
            continue

        zone = _get_zone(elapsed, total_ticks, collar_tier)
        drops = {}

        # 1. Rare event
        if rng.random() < RARE_EVENT_CHANCE:
            drops = _roll_rare(zone, biome, collar_tier,
                               has_mining, has_hunting, has_woodcutting,
                               loot_tables, rare_events, rng, cross_role=False)
        # 2. Cross-role rare event (requires 2+ roles)
        elif role_count >= 2 and rng.random() < CROSS_ROLE_RARE_CHANCE:
            drops = _roll_rare(zone, biome, collar_tier,
                               has_mining, has_hunting, has_woodcutting,
                               loot_tables, rare_events, rng, cross_role=True)
        # 3. Normal role event
        else:
            ew_idx = min(zone, len(zone_ew) - 1)
            ew = zone_ew[ew_idx]
            total_ew = ew["travel"] + ew["discovery"] + ew["hazard"]
            roll = rng.randint(0, total_ew - 1)

            if roll < ew["travel"]:
                pass  # travel — no loot
            elif roll < ew["travel"] + ew["discovery"]:
                drops = _roll_discovery(
                    role_type, zone, collar_tier, biome, wood_biome,
                    loot_tables, ore_discoveries, mob_encounters, wood_discoveries,
                    rng, fortune, looting, silk_touch,
                    has_mining, has_hunting, has_woodcutting
                )
            # hazard — no loot (satiation/injury not modelled here)

        for item, cnt in drops.items():
            drops_total[item] += cnt

        event_timer = rng.randint(EVENT_MIN_TICKS, EVENT_MAX_TICKS - 1)

    return dict(drops_total)


# ──────────────────────────────────────────────────────────────────────────────
# Output
# ──────────────────────────────────────────────────────────────────────────────
def print_results(results, n_sims, cfg):
    all_items = set()
    for run in results:
        all_items.update(run.keys())

    if not all_items:
        print("\n  No loot generated with these settings.\n")
        return

    rows = []
    for item in sorted(all_items):
        counts = [run.get(item, 0) for run in results]
        mean   = statistics.mean(counts)
        std    = statistics.stdev(counts) if n_sims > 1 else 0.0
        p25    = sorted(counts)[int(n_sims * 0.25)]
        p75    = sorted(counts)[int(n_sims * 0.75)]
        mx     = max(counts)
        freq   = sum(1 for c in counts if c > 0) / n_sims * 100
        rows.append((item, mean, std, p25, p75, mx, freq))

    rows.sort(key=lambda r: -r[1])

    roles_str = " + ".join(
        r for r, flag in [("mining", cfg["mining"]),
                           ("hunting", cfg["hunting"]),
                           ("woodcutting", cfg["woodcutting"])] if flag
    )
    enc_parts = []
    if cfg["fortune"]:    enc_parts.append(f"Fortune {cfg['fortune']}")
    if cfg["looting"]:    enc_parts.append(f"Looting {cfg['looting']}")
    if cfg["silk_touch"]: enc_parts.append("Silk Touch")
    enc_str = ", ".join(enc_parts) if enc_parts else "none"

    dur_min = cfg["total_ticks"] / 1200
    avg_interval = (EVENT_MIN_TICKS + EVENT_MAX_TICKS) / 2
    avg_events = cfg["total_ticks"] / avg_interval

    print()
    print("╔══════════════════════════════════════════════════════════════╗")
    print("║               Expedition Expected Returns                    ║")
    print("╚══════════════════════════════════════════════════════════════╝")
    print(f"  Roles        : {roles_str}")
    print(f"  Collar tier  : {cfg['collar_tier']}")
    print(f"  Biome        : {cfg['biome_category']}", end="")
    if cfg["woodcutting"]:
        print(f"  |  Wood biome : {cfg['wood_biome']}", end="")
    print()
    print(f"  Enchantments : {enc_str}")
    print(f"  Duration     : {dur_min:.1f} min  ({cfg['total_ticks']:,} ticks)")
    print(f"  ~Events/run  : {avg_events:.0f}")
    print(f"  Simulations  : {n_sims:,}")
    print()

    col_w = max(len(r[0]) for r in rows) + 2
    hdr = (f"  {'Item':<{col_w}}  {'Mean':>7}  {'±Std':>7}  "
           f"{'P25':>5}  {'P75':>5}  {'Max':>5}  {'%>0':>6}")
    print(hdr)
    print("  " + "─" * (len(hdr) - 2))
    for item, mean, std, p25, p75, mx, freq in rows:
        print(f"  {item:<{col_w}}  {mean:>7.2f}  {std:>7.2f}  "
              f"{p25:>5}  {p75:>5}  {mx:>5}  {freq:>5.1f}%")
    print()

    if cfg["fortune"] or cfg["looting"]:
        print("  * Fortune/Looting are simulated as design-intent mechanics.")
        print("    ExpeditionLootHelper currently uses CHEST context — enchantments")
        print("    have no in-game effect until LootContextParams.TOOL is wired in.")
        print()


# ──────────────────────────────────────────────────────────────────────────────
# Plot
# ──────────────────────────────────────────────────────────────────────────────
def plot_results(results, n_sims, cfg, save_path=None):
    import matplotlib.pyplot as plt
    import matplotlib.gridspec as gridspec
    import numpy as np

    all_items = set()
    for run in results:
        all_items.update(run.keys())
    if not all_items:
        print("No loot to plot.")
        return

    # Build per-item stats
    rows = []
    for item in sorted(all_items):
        counts = np.array([run.get(item, 0) for run in results], dtype=float)
        rows.append({
            "item":   item,
            "mean":   counts.mean(),
            "std":    counts.std(),
            "p10":    float(np.percentile(counts, 10)),
            "p25":    float(np.percentile(counts, 25)),
            "p50":    float(np.percentile(counts, 50)),
            "p75":    float(np.percentile(counts, 75)),
            "p90":    float(np.percentile(counts, 90)),
            "freq":   float((counts > 0).mean() * 100),
            "counts": counts,
        })
    rows.sort(key=lambda r: -r["mean"])

    n_items = len(rows)
    N_HIST  = min(12, n_items)     # histogram panels to draw
    top     = rows[:N_HIST]

    # ── Style ──
    BG      = "#F5F0E8"            # parchment
    BAR_C   = "#B8742A"            # amber ore
    IQR_C   = "#6B3A1F"            # dark mahogany
    HIST_C  = "#C4873A"
    MEAN_C  = "#3D1F05"
    SHADE_C = "#8B5E2A"
    GRID_C  = "#D5C9B5"

    plt.rcParams.update({
        "figure.facecolor": BG,
        "axes.facecolor":   BG,
        "axes.edgecolor":   "#A09070",
        "axes.labelcolor":  "#3D2208",
        "xtick.color":      "#5C3D1A",
        "ytick.color":      "#5C3D1A",
        "text.color":       "#3D2208",
        "grid.color":       GRID_C,
        "font.family":      "sans-serif",
    })

    # ── Figure & GridSpec ──
    fig_h = max(10, n_items * 0.42 + 5)
    n_hist_cols = 3
    n_hist_rows = (N_HIST + n_hist_cols - 1) // n_hist_cols
    fig = plt.figure(figsize=(18, fig_h), facecolor=BG)
    gs  = gridspec.GridSpec(1, 2, figure=fig,
                            width_ratios=[1.0, 1.5], wspace=0.30,
                            left=0.03, right=0.97, top=0.93, bottom=0.06)

    # ────────────────────────────────────────────
    # LEFT — caterpillar chart (all items)
    # ────────────────────────────────────────────
    ax_cat = fig.add_subplot(gs[0])

    # Draw bottom-to-top so highest-mean item is at top
    items_rev = [r["item"] for r in reversed(rows)]
    y         = np.arange(n_items)

    for i, r in enumerate(reversed(rows)):
        yi = y[i]
        # P10–P90 thin whisker
        ax_cat.plot([r["p10"], r["p90"]], [yi, yi],
                    color=IQR_C, linewidth=1.2, alpha=0.45, solid_capstyle="round", zorder=2)
        # P25–P75 thick IQR bar
        ax_cat.plot([r["p25"], r["p75"]], [yi, yi],
                    color=IQR_C, linewidth=5, alpha=0.55, solid_capstyle="round", zorder=3)
        # Mean dot
        ax_cat.scatter([r["mean"]], [yi],
                       color=MEAN_C, s=32, zorder=4, clip_on=False)
        # Mean label
        ax_cat.text(r["mean"] + max(rows[0]["mean"] * 0.015, 0.5), yi,
                    f"{r['mean']:.1f}", va="center", fontsize=7.5, color=MEAN_C, zorder=5)

    ax_cat.set_yticks(y)
    ax_cat.set_yticklabels(items_rev, fontsize=9)
    ax_cat.set_xlabel("Items per expedition", fontsize=10, labelpad=6)
    ax_cat.set_title("All Items\n● mean  ▬ IQR (P25–P75)  — P10–P90",
                     fontsize=10, pad=8)
    ax_cat.grid(axis="x", linewidth=0.7, zorder=0)
    ax_cat.spines[["top", "right"]].set_visible(False)
    ax_cat.set_ylim(-0.8, n_items - 0.2)
    # light alternating row bands
    for i in range(n_items):
        if i % 2 == 0:
            ax_cat.axhspan(i - 0.45, i + 0.45, color="#EDE6D6", alpha=0.5, zorder=0)

    # ────────────────────────────────────────────
    # RIGHT — histogram grid (top N_HIST items)
    # ────────────────────────────────────────────
    gs_r = gridspec.GridSpecFromSubplotSpec(
        n_hist_rows, n_hist_cols, subplot_spec=gs[1],
        hspace=0.80, wspace=0.45
    )

    for idx, r in enumerate(top):
        ri, ci = divmod(idx, n_hist_cols)
        ax = fig.add_subplot(gs_r[ri, ci])

        counts = r["counts"]
        # bin width: at least 1 for integer data
        rng_val = counts.max() - counts.min()
        n_bins  = min(40, max(10, int(rng_val)))
        ax.hist(counts, bins=n_bins, color=HIST_C, alpha=0.85,
                edgecolor="white", linewidth=0.4, zorder=2)

        ax.axvline(r["mean"], color=MEAN_C,  linewidth=1.6, linestyle="--", zorder=3)
        ax.axvline(r["p50"],  color=SHADE_C, linewidth=1.0, linestyle=":",  zorder=3)
        ax.axvspan(r["p25"], r["p75"], alpha=0.18, color=IQR_C, zorder=1)

        ax.set_title(r["item"], fontsize=8.5, fontweight="bold", pad=3)
        ax.set_xlabel("count / expedition", fontsize=7, labelpad=2)
        ax.tick_params(labelsize=7)
        ax.spines[["top", "right"]].set_visible(False)

        label = f"μ={r['mean']:.1f}  σ={r['std']:.1f}\n{r['freq']:.0f}% runs"
        ax.text(0.97, 0.97, label, transform=ax.transAxes,
                fontsize=7, va="top", ha="right",
                bbox=dict(boxstyle="round,pad=0.25", facecolor="white",
                          alpha=0.75, edgecolor="none"))

    # hide unused panels
    for idx in range(N_HIST, n_hist_rows * n_hist_cols):
        ri, ci = divmod(idx, n_hist_cols)
        ax = fig.add_subplot(gs_r[ri, ci])
        ax.set_visible(False)

    # ── Suptitle ──
    roles_str = " + ".join(
        r for r, f in [("Mining", cfg["mining"]),
                        ("Hunting", cfg["hunting"]),
                        ("Woodcutting", cfg["woodcutting"])] if f
    )
    enc_parts = []
    if cfg["fortune"]:    enc_parts.append(f"Fortune {cfg['fortune']}")
    if cfg["looting"]:    enc_parts.append(f"Looting {cfg['looting']}")
    if cfg["silk_touch"]: enc_parts.append("Silk Touch")
    enc_str = f"  [{', '.join(enc_parts)}]" if enc_parts else ""

    dur_min = cfg["total_ticks"] / 1200
    biome_str = cfg["biome_category"]
    if cfg["woodcutting"]:
        biome_str += f" / {cfg['wood_biome']}"

    fig.suptitle(
        f"Working Wolves — Expedition Balance Sim\n"
        f"{roles_str}{enc_str}   ·   Tier {cfg['collar_tier']}   ·   "
        f"{biome_str}   ·   {dur_min:.0f} min   ·   {n_sims:,} runs",
        fontsize=12, fontweight="bold", y=0.98
    )

    if save_path:
        plt.savefig(save_path, dpi=150, bbox_inches="tight", facecolor=BG)
        plt.close(fig)
        print(f"Plot saved → {save_path}")
    else:
        plt.tight_layout(rect=[0, 0, 1, 0.96])
        plt.show()
        plt.close(fig)

    plt.rcParams.update(plt.rcParamsDefault)


# ──────────────────────────────────────────────────────────────────────────────
# CLI
# ──────────────────────────────────────────────────────────────────────────────
def main():
    parser = argparse.ArgumentParser(
        description="Monte Carlo expedition sim for Working Wolves",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=__doc__
    )

    roles = parser.add_argument_group("roles (at least one required)")
    roles.add_argument("--mining",      action="store_true", help="Wolf has mining role")
    roles.add_argument("--hunting",     action="store_true", help="Wolf has hunting role")
    roles.add_argument("--woodcutting", action="store_true", help="Wolf has woodcutting role")

    wolf = parser.add_argument_group("wolf stats")
    wolf.add_argument("--collar-tier", type=int, default=1, metavar="0-4",
                      choices=range(5),
                      help="Collar tier — sets zone access and default duration (default: 1)")
    wolf.add_argument("--biome", type=str, default="other",
                      metavar="CATEGORY",
                      help="Biome category: cave, mountain, forest, plains, ocean, other (default: other)")
    wolf.add_argument("--wood-biome", type=str, default="forest",
                      metavar="BIOME",
                      help="Wood biome for woodcutters: forest, taiga, jungle, dark_forest, "
                           "savanna, mangrove, cherry (default: forest)")
    wolf.add_argument("--fortune",    type=int, default=0, choices=range(4), metavar="0-3",
                      help="Fortune enchantment level (default: 0)")
    wolf.add_argument("--looting",    type=int, default=0, choices=range(4), metavar="0-3",
                      help="Looting enchantment level (default: 0)")
    wolf.add_argument("--silk-touch", action="store_true",
                      help="Pickaxe has Silk Touch (redirects to silk loot tables, disables Fortune)")

    sim = parser.add_argument_group("simulation")
    sim.add_argument("--duration", type=float, default=None, metavar="MINUTES",
                     help="Override expedition duration in minutes "
                          "(default: collar-tier-based: tier1=10, tier2=18, tier3+=25)")
    sim.add_argument("--sims", type=int, default=10_000, metavar="N",
                     help="Number of Monte Carlo simulations (default: 10000)")
    sim.add_argument("--seed", type=int, default=None,
                     help="Random seed for reproducibility")

    out = parser.add_argument_group("output")
    out.add_argument("--plot", action="store_true",
                     help="Show distribution plots (requires matplotlib)")
    out.add_argument("--save", type=str, default=None, metavar="FILE",
                     help="Save plot to file instead of showing it (e.g. out.png, out.svg)")

    args = parser.parse_args()

    if not any([args.mining, args.hunting, args.woodcutting]):
        parser.error("Specify at least one role: --mining, --hunting, or --woodcutting")

    rng = random.Random(args.seed)

    total_ticks = (int(args.duration * 1200) if args.duration is not None
                   else TIER_DURATIONS[args.collar_tier])

    cfg = {
        "collar_tier":    args.collar_tier,
        "mining":         args.mining,
        "hunting":        args.hunting,
        "woodcutting":    args.woodcutting,
        "biome_category": args.biome,
        "wood_biome":     args.wood_biome,
        "fortune":        args.fortune,
        "looting":        args.looting,
        "silk_touch":     args.silk_touch,
        "total_ticks":    total_ticks,
    }

    print("Loading data...", flush=True)
    loot_tables     = load_loot_tables()
    ore_discoveries = load_ore_discoveries()
    mob_encounters  = load_mob_encounters()
    wood_discoveries = load_wood_discoveries()
    rare_events     = load_rare_events()
    roles           = load_roles()

    print(f"Running {args.sims:,} simulations...", flush=True)
    results = []
    for i in range(args.sims):
        results.append(
            simulate_one(cfg, loot_tables, ore_discoveries, mob_encounters,
                         wood_discoveries, rare_events, roles, rng)
        )
        if (i + 1) % 2000 == 0:
            print(f"  {i + 1:,} / {args.sims:,}...", end="\r", flush=True)

    print_results(results, args.sims, cfg)

    if args.plot or args.save:
        plot_results(results, args.sims, cfg, save_path=args.save)


if __name__ == "__main__":
    main()
