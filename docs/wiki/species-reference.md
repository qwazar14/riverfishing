# Species reference

The rest of the profile data for all 70 species: habitat gates, the environmental multiplier tables, and fight statistics. The player-facing tables (size, water, bait, tackle) are on [Species](species.md).

## Habitat gates

These four columns are **hard gates** — outside them the fish is simply absent, not merely rare. `Presents at` is the depth horizon the rig must fish (see [the float depth slider](rigs-and-baits.md#the-float)); `Cast distance` is the band, in blocks from the bank, where the species holds.

- Depth is measured as the water column straight down from your cast point, counted up to 16. `4+` means 4 or deeper with no upper limit.
- Width is the longest open-water span through your cast point. `12+` means 12 or wider; `0–40` means the species only lives in water **up to** 40 wide.
- Biome groups: the best matching group's factor is used, and **no match means absent**. See [biome groups](water-and-conditions.md#biome-groups).
- Cast distance: inside the band scores ×1.1, short of it scales down to ×0.6, past it ×0.85.

| Species | Water depth | Water width | Biome groups (factor) | Presents at | Cast distance |
|---|---|---|---|---|---|
| Bream | 3+ | 10+ | temperate 1.0, cold 0.8, warm 0.7 | bottom | 8–30 |
| Crucian Carp | 1–4 | 0–40 | swamp 1.2, warm 1.1, temperate 1.0 | bottom | 2–15 |
| Roach | 1–8 | 4+ | temperate 1.0, cold 0.9 | mid | 2–12 |
| Rudd | 1–3 | 0–48 | warm 1.1, swamp 1.1, temperate 1.0 | surface | 2–12 |
| White Bream | 2–8 | 8+ | temperate 1.0, cold 0.7 | bottom | 6–25 |
| Carp | 2+ | 12+ | warm 1.2, temperate 1.0 | bottom | 15–40 |
| Catfish | 5+ | 16+ | warm 1.2, temperate 1.0 | bottom | 10–40 |
| Perch | 1–10 | 4+ | cold 1.0, temperate 1.0, warm 0.6 | mid | 5–25 |
| Pike | 2–10 | 8+ | cold 1.1, swamp 1.1, temperate 1.0 | mid | 6–30 |
| Zander | 4+ | 14+ | temperate 1.0, cold 0.8 | bottom | 10–35 |
| Gudgeon | 1–3 | 3–24 | temperate 1.0, cold 0.9 | bottom | 2–10 |
| Ruffe | 3+ | 6+ | cold 1.1, temperate 1.0 | bottom | 4–20 |
| Bleak | 1–2 | 6+ | temperate 1.0, warm 0.9 | surface | 2–12 |
| Ide | 2–6 | 8+ | temperate 1.0, cold 0.8 | mid | 5–25 |
| Chub | 1–4 | 6–40 | temperate 1.0, warm 0.8 | surface | 4–25 |
| Asp | 2–6 | 14+ | temperate 1.0 | surface | 15–40 |
| Tench | 1–4 | 0–32 | swamp 1.2, warm 1.1, temperate 1.0 | bottom | 3–18 |
| Burbot | 4+ | 10+ | cold 1.3, temperate 0.8 | bottom | 6–30 |
| Eel | 3+ | 10+ | warm 1.1, temperate 1.0 | bottom | 5–30 |
| Grayling | 1–3 | 3–20 | mountain 1.3, cold 1.2, taiga 1.2 | mid | 3–18 |
| Trout | 2–6 | 4–24 | mountain 1.3, cold 1.2, taiga 1.1 | mid | 4–25 |
| Sterlet | 5+ | 18+ | temperate 1.0 | bottom | 12–40 |
| Wild Carp | 2+ | 14+ | warm 1.15, temperate 1.0 | bottom | 15–45 |
| Mirror Carp | 2+ | 10+ | warm 1.2, temperate 1.0 | bottom | 12–40 |
| Grass Carp | 2+ | 12+ | warm 1.3, temperate 1.0 | mid | 12–40 |
| Koi Kohaku | 1+ | 4+ | cherry 1.0 | bottom | 6–30 |
| Koi Tancho Sanke | 1+ | 4+ | cherry 1.0 | bottom | 6–30 |
| Koi Showa Sanke | 1+ | 4+ | cherry 1.0 | bottom | 6–30 |
| Koi Asagi | 1+ | 4+ | cherry 1.0 | bottom | 6–30 |
| Koi Bekko | 1+ | 4+ | cherry 1.0 | bottom | 6–30 |
| Bluegill | 1–6 | 3+ | warm 1.2, temperate 1.0 | mid | 2–15 |
| Largemouth bass | 1–8 | 6+ | warm 1.3, temperate 0.9 | mid | 5–30 |
| Rainbow trout | 2–8 | 4+ | mountain 1.2, cold 1.1, taiga 1.1, temperate 0.8 | mid | 4–25 |
| Channel catfish | 2+ | 8+ | warm 1.3, temperate 0.9 | bottom | 10–40 |
| Silver carp | 3+ | 14+ | warm 1.3, temperate 1.0 | mid | 20–45 |
| Sabrefish | 2+ | 10+ | temperate 1.1, warm 1.0 | surface | 15–40 |
| Blue bream | 3+ | 8+ | temperate 1.1, cold 1.0 | mid | 10–30 |
| Mackerel | 2+ | 12+ | temperate 1.1, ocean_biome 1.0, warm 0.9, cold 0.9 | mid | 10–40 |
| Herring | 2+ | 10+ | cold 1.2, ocean_biome 1.0, temperate 1.0 | mid | 8–35 |
| Garfish | 2+ | 12+ | warm 1.2, ocean_biome 1.0, temperate 1.0 | surface | 12–40 |
| Sea bass | 2+ | 12+ | beach 1.2, temperate 1.1, ocean_biome 1.0, warm 1.0 | mid | 8–35 |
| Flounder | 2+ | 12+ | beach 1.2, cold 1.1, ocean_biome 1.0, temperate 1.0 | bottom | 15–45 |
| Cod | 4+ | 16+ | cold 1.3, deep 1.2, temperate 0.8, ocean_biome 0.7 | bottom | 15–45 |
| Saithe | 4+ | 14+ | cold 1.2, deep 1.1, ocean_biome 0.9, temperate 0.9 | mid | 12–40 |
| Conger eel | 4+ | 14+ | deep 1.1, temperate 1.1, ocean_biome 1.0, warm 0.9 | bottom | 15–45 |
| Ray | 3+ | 14+ | warm 1.2, beach 1.1, ocean_biome 1.0, temperate 1.0 | bottom | 18–45 |
| Mahi-mahi | 5+ | 16+ | warm 1.3, deep 1.2, temperate 0.7 | surface | 25–45 |
| Wahoo | 6+ | 18+ | deep 1.2, warm 1.2, temperate 0.6 | mid | 28–45 |
| Yellowfin tuna | 8+ | 20+ | deep 1.3, warm 1.2, temperate 0.7 | mid | 30–45 |
| Barracuda | 3+ | 14+ | warm 1.4, beach 1.1, deep 1.0, ocean_biome 0.9 | mid | 15–40 |
| Blue marlin | 10+ | 24+ | deep 1.3, warm 1.2, temperate 0.5 | surface | 32–45 |
| Sailfish | 8+ | 20+ | warm 1.3, deep 1.2, temperate 0.5 | surface | 28–45 |
| Swordfish | 10+ | 24+ | deep 1.3, warm 1.0, temperate 0.9 | bottom | 30–45 |
| Mako shark | 8+ | 22+ | deep 1.2, warm 1.1, temperate 1.0 | mid | 30–45 |
| Rotan | 1–4 | 2–20 | swamp 1.3, temperate 1.0, cold 1.0 | bottom | 2–12 |
| Nase | 1–5 | 6–48 | temperate 1.1, cold 0.9 | bottom | 5–25 |
| Vimba bream | 2–8 | 8+ | temperate 1.1, cold 1.0 | bottom | 8–35 |
| Smelt | 1–8 | 6+ | cold 1.3, ocean_biome 1.0 | mid | 3–20 |
| Whitefish | 3–14 | 10+ | cold 1.3, taiga 1.1, mountain 1.1 | mid | 6–30 |
| Arctic char | 2–10 | 5+ | cold 1.4, mountain 1.2, taiga 1.0 | mid | 5–28 |
| Lenok | 2–8 | 6–40 | taiga 1.3, mountain 1.2, cold 1.1 | mid | 6–30 |
| Taimen | 3–12 | 12+ | taiga 1.3, mountain 1.3, cold 1.1 | mid | 15–45 |
| Atlantic salmon | 2–10 | 8+ | cold 1.2, temperate 0.9, ocean_biome 0.9 | mid | 10–40 |
| Pink salmon | 2–8 | 6+ | cold 1.3, ocean_biome 1.0 | mid | 8–35 |
| Sturgeon | 4–16 | 16+ | temperate 1.0, cold 0.9 | bottom | 15–50 |
| Halibut | 4+ | 16+ | deep 1.3, cold 1.2, ocean_biome 0.9 | bottom | 15–60 |
| Common dace | 1–4 | 6+ | temperate 1.1, mountain 1.1, cold 1.0 | mid | 3–18 |
| Volga zander | 5+ | 14+ | temperate 1.0, cold 0.9 | bottom | 10–35 |
| White-eye bream | 4+ | 12+ | temperate 1.1, cold 0.9 | bottom | 10–35 |
| Round goby | 1+ | 6+ | warm 1.2, beach 1.2, temperate 1.0, ocean_biome 0.9 | bottom | 2–20 |

## Season, time and weather

Multipliers, 1.0 being neutral. A bold **0** is a hard shutdown — the fish does not bite in that condition at all.

Remember the exponents: the season factor is raised to the power **1.5** and the time factor to **1.4**, so these swings hit harder than they look. Weather is applied flat. Seasons require **Serene Seasons**; without it every season factor is treated as 1.0.

| Species | Spr | Sum | Aut | Win | Dawn | Day | Dusk | Night | Clear | Rain | Thndr |
|---|---|---|---|---|---|---|---|---|---|---|---|
| Bream | 1.2 | 1.2 | 0.8 | 0.3 | 1.2 | 0.8 | 1.2 | 0.6 | 0.9 | 1.2 | 1.0 |
| Crucian Carp | 0.9 | 1.3 | 0.7 | **0** | 1.2 | 1.0 | 1.1 | 0.5 | 1.0 | 1.1 | 0.9 |
| Roach | 1.0 | 1.0 | 1.0 | 0.7 | 1.1 | 1.0 | 1.1 | 0.6 | 1.0 | 1.1 | 0.9 |
| Rudd | 0.8 | 1.3 | 0.6 | **0** | 1.0 | 1.2 | 1.0 | 0.4 | 1.1 | 0.9 | 0.8 |
| White Bream | 1.0 | 1.1 | 0.8 | 0.4 | 1.1 | 0.9 | 1.1 | 0.6 | 0.9 | 1.2 | 1.0 |
| Carp | 0.8 | 1.4 | 0.9 | 0.05 | 1.2 | 0.9 | 1.2 | 1.0 | 0.9 | 1.2 | 0.8 |
| Catfish | 0.7 | 1.5 | 0.6 | **0** | 0.9 | 0.3 | 1.2 | 1.4 | 0.8 | 1.1 | 1.2 |
| Perch | 1.1 | 0.9 | 1.3 | 0.8 | 1.3 | 1.0 | 1.2 | 0.5 | 1.0 | 1.0 | 0.9 |
| Pike | 1.1 | 0.7 | 1.5 | 0.9 | 1.3 | 0.9 | 1.3 | 0.5 | 1.0 | 1.1 | 1.0 |
| Zander | 1.0 | 0.9 | 1.2 | 0.7 | 1.1 | 0.6 | 1.3 | 1.3 | 1.0 | 1.0 | 0.9 |
| Gudgeon | 1.0 | 1.1 | 0.9 | 0.6 | 1.1 | 1.2 | 0.9 | **0** | 1.1 | 0.9 | 0.7 |
| Ruffe | 1.0 | 0.9 | 1.1 | 1.0 | 1.0 | 0.9 | 1.1 | 1.2 | 0.9 | 1.1 | 1.0 |
| Bleak | 1.1 | 1.3 | 0.8 | **0** | 1.0 | 1.2 | 1.0 | **0** | 1.2 | 0.8 | 0.6 |
| Ide | 1.3 | 1.0 | 1.0 | 0.2 | 1.4 | 0.7 | 1.4 | 0.3 | 0.9 | 1.2 | 1.0 |
| Chub | 1.0 | 1.4 | 0.8 | **0** | 1.1 | 1.2 | 1.0 | 0.1 | 1.2 | 0.8 | 0.7 |
| Asp | 0.9 | 1.2 | 1.1 | 0.1 | 1.2 | 1.3 | 0.9 | **0** | 1.2 | 0.8 | 0.6 |
| Tench | 1.0 | 1.3 | 0.5 | **0** | 1.6 | 0.6 | 1.2 | 0.2 | 0.9 | 1.2 | 1.1 |
| Burbot | 0.4 | **0** | 1.2 | 1.6 | 0.4 | **0** | 0.8 | 1.5 | 0.8 | 1.2 | 1.1 |
| Eel | 0.9 | 1.3 | 0.8 | **0** | 0.5 | 0.1 | 1.0 | 1.5 | 0.8 | 1.2 | 1.3 |
| Grayling | 1.0 | 1.0 | 1.1 | 0.8 | 1.3 | 1.2 | 1.0 | 0.1 | 1.1 | 1.0 | 0.8 |
| Trout | 1.2 | 0.8 | 1.1 | 0.6 | 1.4 | 1.0 | 1.1 | 0.2 | 1.0 | 1.1 | 0.9 |
| Sterlet | 1.1 | 1.0 | 0.9 | 0.3 | 0.8 | 0.4 | 1.1 | 1.4 | 0.9 | 1.1 | 1.0 |
| Wild Carp | 0.7 | 1.5 | 0.9 | 0.03 | 1.3 | 0.8 | 1.3 | 1.1 | 0.9 | 1.3 | 0.9 |
| Mirror Carp | 0.8 | 1.4 | 0.9 | 0.05 | 1.2 | 0.9 | 1.2 | 1.0 | 0.9 | 1.2 | 0.8 |
| Grass Carp | 0.7 | 1.5 | 0.8 | 0.02 | 1.2 | 1.0 | 1.2 | 0.8 | 1.1 | 1.0 | 0.7 |
| Koi Kohaku | 1.0 | 1.0 | 1.0 | 0.3 | 1.0 | 1.0 | 1.0 | 1.0 | 1.0 | 1.0 | 1.0 |
| Koi Tancho Sanke | 1.0 | 1.0 | 1.0 | 0.3 | 1.0 | 1.0 | 1.0 | 1.0 | 1.0 | 1.0 | 1.0 |
| Koi Showa Sanke | 1.0 | 1.0 | 1.0 | 0.3 | 1.0 | 1.0 | 1.0 | 1.0 | 1.0 | 1.0 | 1.0 |
| Koi Asagi | 1.0 | 1.0 | 1.0 | 0.3 | 1.0 | 1.0 | 1.0 | 1.0 | 1.0 | 1.0 | 1.0 |
| Koi Bekko | 1.0 | 1.0 | 1.0 | 0.3 | 1.0 | 1.0 | 1.0 | 1.0 | 1.0 | 1.0 | 1.0 |
| Bluegill | 1.1 | 1.2 | 1.0 | 0.4 | 1.2 | 1.1 | 1.2 | 0.3 | 1.1 | 0.9 | 0.8 |
| Largemouth bass | 1.1 | 1.3 | 1.0 | 0.1 | 1.4 | 0.8 | 1.4 | 0.7 | 0.9 | 1.1 | 1.1 |
| Rainbow trout | 1.2 | 0.9 | 1.2 | 0.5 | 1.4 | 1.0 | 1.1 | 0.2 | 1.0 | 1.2 | 0.9 |
| Channel catfish | 0.9 | 1.3 | 1.0 | 0.2 | 1.0 | 0.5 | 1.2 | 1.5 | 0.9 | 1.1 | 1.2 |
| Silver carp | 0.8 | 1.4 | 0.9 | 0.05 | 1.2 | 1.1 | 1.0 | 0.4 | 1.2 | 0.8 | 0.6 |
| Sabrefish | 1.2 | 1.2 | 1.0 | 0.3 | 1.3 | 1.1 | 1.2 | 0.4 | 1.1 | 1.0 | 0.8 |
| Blue bream | 1.2 | 0.9 | 1.2 | 0.6 | 1.3 | 0.9 | 1.1 | 0.7 | 1.0 | 1.0 | 0.9 |
| Mackerel | 1.0 | 1.3 | 1.1 | 0.4 | 1.3 | 1.0 | 1.3 | 0.5 | 1.0 | 1.0 | 0.9 |
| Herring | 1.2 | 0.9 | 1.2 | 0.7 | 1.2 | 1.0 | 1.2 | 0.6 | 1.0 | 1.1 | 0.9 |
| Garfish | 1.1 | 1.3 | 0.9 | 0.2 | 1.2 | 1.2 | 1.0 | 0.3 | 1.2 | 0.8 | 0.7 |
| Sea bass | 1.0 | 1.2 | 1.2 | 0.4 | 1.2 | 0.8 | 1.3 | 1.2 | 0.9 | 1.2 | 1.1 |
| Flounder | 1.0 | 0.9 | 1.2 | 0.8 | 1.0 | 0.8 | 1.2 | 1.3 | 1.0 | 1.0 | 0.9 |
| Cod | 1.0 | 0.7 | 1.2 | 1.3 | 1.1 | 1.0 | 1.1 | 0.9 | 0.9 | 1.1 | 1.0 |
| Saithe | 1.1 | 1.0 | 1.1 | 0.9 | 1.2 | 1.0 | 1.1 | 0.6 | 1.0 | 1.0 | 0.9 |
| Conger eel | 0.9 | 1.2 | 1.1 | 0.6 | 0.8 | 0.4 | 1.2 | 1.5 | 0.9 | 1.1 | 1.1 |
| Ray | 0.9 | 1.2 | 1.1 | 0.5 | 1.0 | 0.9 | 1.1 | 1.2 | 1.0 | 1.0 | 0.9 |
| Mahi-mahi | 1.0 | 1.3 | 1.0 | 0.3 | 1.2 | 1.2 | 1.0 | 0.3 | 1.2 | 0.9 | 0.7 |
| Wahoo | 1.0 | 1.2 | 1.1 | 0.4 | 1.3 | 1.1 | 1.1 | 0.3 | 1.1 | 1.0 | 0.8 |
| Yellowfin tuna | 0.9 | 1.2 | 1.2 | 0.5 | 1.3 | 1.0 | 1.2 | 0.5 | 1.0 | 1.1 | 0.9 |
| Barracuda | 1.0 | 1.3 | 1.0 | 0.3 | 1.1 | 1.0 | 1.3 | 0.6 | 1.1 | 0.9 | 0.8 |
| Blue marlin | 0.9 | 1.3 | 1.0 | 0.3 | 1.2 | 1.2 | 1.0 | 0.2 | 1.2 | 0.9 | 0.6 |
| Sailfish | 1.0 | 1.3 | 1.0 | 0.3 | 1.3 | 1.1 | 1.1 | 0.2 | 1.2 | 0.9 | 0.6 |
| Swordfish | 0.9 | 1.2 | 1.1 | 0.5 | 0.8 | 0.4 | 1.2 | 1.5 | 1.0 | 1.0 | 0.9 |
| Mako shark | 1.0 | 1.2 | 1.1 | 0.4 | 1.2 | 1.0 | 1.2 | 0.8 | 1.0 | 1.1 | 1.0 |
| Rotan | 1.0 | 1.2 | 1.0 | 0.7 | 1.1 | 1.1 | 1.0 | 0.5 | 1.0 | 1.0 | 1.0 |
| Nase | 1.1 | 1.2 | 1.0 | 0.3 | 1.2 | 1.2 | 1.0 | 0.3 | 1.1 | 0.9 | 0.7 |
| Vimba bream | 1.4 | 0.9 | 1.1 | 0.3 | 1.3 | 0.9 | 1.3 | 0.6 | 1.0 | 1.1 | 0.9 |
| Smelt | 1.1 | 0.4 | 0.9 | 1.5 | 1.2 | 1.1 | 1.0 | 0.6 | 1.1 | 0.9 | 0.8 |
| Whitefish | 1.0 | 0.6 | 1.2 | 1.2 | 1.3 | 1.1 | 1.0 | 0.4 | 1.1 | 0.9 | 0.8 |
| Arctic char | 1.1 | 0.8 | 1.2 | 1.1 | 1.3 | 1.0 | 1.2 | 0.3 | 1.0 | 1.1 | 0.9 |
| Lenok | 0.9 | 1.1 | 1.1 | 0.4 | 1.4 | 0.9 | 1.3 | 0.4 | 1.0 | 1.1 | 0.9 |
| Taimen | 1.0 | 0.8 | 1.3 | 0.3 | 1.5 | 0.6 | 1.4 | 0.8 | 0.9 | 1.2 | 1.0 |
| Atlantic salmon | 0.9 | 1.0 | 1.4 | 0.3 | 1.4 | 0.9 | 1.3 | 0.5 | 0.9 | 1.2 | 1.0 |
| Pink salmon | 0.5 | 1.5 | 0.9 | 0.2 | 1.3 | 1.0 | 1.2 | 0.4 | 1.0 | 1.1 | 0.9 |
| Sturgeon | 1.0 | 1.1 | 1.1 | 0.4 | 1.0 | 0.5 | 1.2 | 1.4 | 1.0 | 1.1 | 1.0 |
| Halibut | 1.0 | 0.8 | 1.2 | 1.1 | 1.1 | 1.0 | 1.1 | 0.7 | 1.0 | 1.0 | 0.9 |
| Common dace | 1.1 | 1.2 | 1.1 | 0.2 | 1.2 | 1.1 | 1.2 | 0.2 | 1.1 | 1.0 | 0.7 |
| Volga zander | 0.9 | 0.9 | 1.3 | 0.8 | 1.2 | 0.6 | 1.3 | 1.2 | 1.0 | 1.0 | 0.8 |
| White-eye bream | 1.2 | 1.0 | 1.1 | 0.4 | 1.2 | 0.9 | 1.2 | 0.7 | 1.0 | 1.1 | 0.9 |
| Round goby | 1.0 | 1.3 | 1.1 | 0.3 | 1.1 | 1.1 | 1.1 | 0.6 | 1.1 | 1.0 | 0.8 |

## Fight statistics

- **Pattern** drives run frequency, run length and the gaps between them, plus the signature events (dives, jumps). Full tables in [fight patterns](fishing-mechanics.md#fight-patterns).
- **Strength** sets the load the fish puts on your tackle: `requiredKg = max(0.5, strength × (1 + weightKg) × 2)`.
- **Runs** is the baseline count before pattern, size and predator bonuses.
- **Aggression** drives head-shake frequency and tightens the strike-timing window.
- **`base`** is relative density. **0.95 or above means the species lives in every water** (see [community](water-and-conditions.md#every-water-is-its-own)); 0.0 means it is never drawn from the normal pool at all (the koi).
- A `stamina` value is also present in every profile but **is not currently read by any game logic**.

| Species | Pattern | Strength | Runs | Aggression | `base` density | Legendary |
|---|---|---|---|---|---|---|
| Bream | active_then_passive | 0.4 | 2 | — | 1.0 | — |
| Crucian Carp | steady | 0.3 | 1 | — | 1.1 | — |
| Roach | steady | 0.2 | 1 | — | 1.0 | — |
| Rudd | steady | 0.2 | 1 | — | 1.0 | — |
| White Bream | steady | 0.35 | 1 | — | 1.0 | — |
| Carp | burst | 0.9 | 4 | 0.85 | 0.8 | — |
| Catfish | burst | 1.0 | 5 | 1.0 | 0.5 | 150 kg @ 0.5 % |
| Perch | aggressive | 0.5 | 2 | 0.8 | 1.0 | — |
| Pike | aggressive | 0.85 | 3 | 0.9 | 0.9 | 14 kg @ 0.6 % |
| Zander | aggressive | 0.7 | 2 | 0.75 | 0.9 | — |
| Gudgeon | steady | 0.1 | 1 | — | 1.0 | — |
| Ruffe | steady | 0.15 | 1 | — | 1.0 | — |
| Bleak | steady | 0.05 | 1 | — | 0.95 | — |
| Ide | active_then_passive | 0.5 | 2 | — | 0.9 | — |
| Chub | aggressive | 0.6 | 2 | 0.8 | 0.8 | — |
| Asp | aggressive | 0.8 | 3 | 0.9 | 0.6 | — |
| Tench | steady | 0.55 | 2 | — | 0.8 | — |
| Burbot | steady | 0.6 | 2 | — | 0.7 | — |
| Eel | burst | 0.6 | 3 | 0.7 | 0.6 | — |
| Grayling | aggressive | 0.55 | 3 | 0.85 | 0.8 | — |
| Trout | aggressive | 0.75 | 3 | 0.85 | 0.7 | — |
| Sterlet | steady | 0.8 | 3 | — | 0.35 | — |
| Wild Carp | aggressive | 1.0 | 6 | 1.0 | 0.5 | 17.5 kg @ 0.6 % |
| Mirror Carp | burst | 0.8 | 3 | 0.75 | 0.7 | — |
| Grass Carp | relentless | 1.0 | 5 | 0.92 | 0.5 | — |
| Koi Kohaku | burst | 0.7 | 3 | 0.6 | 0.0 | — |
| Koi Tancho Sanke | burst | 0.7 | 3 | 0.6 | 0.0 | — |
| Koi Showa Sanke | burst | 0.7 | 3 | 0.6 | 0.0 | — |
| Koi Asagi | burst | 0.7 | 3 | 0.6 | 0.0 | — |
| Koi Bekko | burst | 0.7 | 3 | 0.6 | 0.0 | — |
| Bluegill | steady | 0.3 | 1 | 0.5 | 1.1 | — |
| Largemouth bass | aggressive | 0.8 | 3 | 0.9 | 0.8 | — |
| Rainbow trout | burst | 0.8 | 4 | 0.85 | 0.65 | — |
| Channel catfish | steady | 0.85 | 3 | 0.7 | 0.55 | — |
| Silver carp | relentless | 0.9 | 4 | 0.8 | 0.45 | — |
| Sabrefish | aggressive | 0.45 | 2 | 0.75 | 0.8 | — |
| Blue bream | steady | 0.3 | 1 | 0.4 | 0.85 | — |
| Mackerel | burst | 0.5 | 2 | 0.8 | 1.0 | — |
| Herring | steady | 0.25 | 1 | 0.4 | 1.1 | — |
| Garfish | aggressive | 0.45 | 2 | 0.85 | 0.8 | — |
| Sea bass | aggressive | 0.75 | 3 | 0.85 | 0.7 | — |
| Flounder | steady | 0.5 | 1 | 0.3 | 0.9 | — |
| Cod | active_then_passive | 0.8 | 2 | 0.6 | 0.6 | — |
| Saithe | aggressive | 0.75 | 3 | 0.8 | 0.7 | — |
| Conger eel | relentless | 0.9 | 3 | 0.75 | 0.4 | — |
| Ray | steady | 0.95 | 1 | 0.2 | 0.5 | — |
| Mahi-mahi | greyhounding | 0.85 | 3 | 0.9 | 0.5 | — |
| Wahoo | burst | 0.9 | 4 | 0.95 | 0.4 | — |
| Yellowfin tuna | sounding | 1.0 | 4 | 0.85 | 0.35 | 140 kg @ 0.6 % |
| Barracuda | burst | 0.85 | 3 | 0.95 | 0.55 | — |
| Blue marlin | greyhounding | 1.0 | 5 | 0.95 | 0.25 | 380 kg @ 0.8 % |
| Sailfish | greyhounding | 0.9 | 5 | 1.0 | 0.35 | — |
| Swordfish | sounding | 1.0 | 4 | 0.8 | 0.25 | — |
| Mako shark | greyhounding | 1.0 | 5 | 1.0 | 0.3 | 390 kg @ 0.4 % |
| Rotan | aggressive | 0.25 | 1 | 0.7 | 1.2 | — |
| Nase | steady | 0.45 | 2 | 0.4 | 0.95 | — |
| Vimba bream | active_then_passive | 0.5 | 2 | 0.5 | 0.8 | — |
| Smelt | burst | 0.15 | 1 | 0.6 | 1.3 | — |
| Whitefish | steady | 0.55 | 2 | 0.5 | 0.75 | — |
| Arctic char | aggressive | 0.7 | 3 | 0.8 | 0.65 | — |
| Lenok | aggressive | 0.7 | 3 | 0.8 | 0.6 | — |
| Taimen | relentless | 0.95 | 5 | 0.9 | 0.3 | — |
| Atlantic salmon | greyhounding | 0.9 | 4 | 0.8 | 0.5 | — |
| Pink salmon | burst | 0.6 | 3 | 0.7 | 0.9 | — |
| Sturgeon | sounding | 0.9 | 4 | 0.5 | 0.25 | 145 kg @ 0.4 % |
| Halibut | sounding | 0.9 | 3 | 0.4 | 0.3 | — |
| Common dace | aggressive | 0.3 | 2 | 0.8 | 0.9 | — |
| Volga zander | active_then_passive | 0.55 | 2 | 0.7 | 0.7 | — |
| White-eye bream | active_then_passive | 0.4 | 2 | 0.5 | 0.8 | — |
| Round goby | burst | 0.25 | 2 | 0.7 | 1.0 | — |

## See also

- [Species](species.md) · [Water and conditions](water-and-conditions.md) · [Fishing mechanics](fishing-mechanics.md)
