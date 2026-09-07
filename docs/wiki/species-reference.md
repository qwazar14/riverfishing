# Species reference

The rest of the profile data for all 91 species: habitat gates, the environmental multiplier tables, and fight statistics. The player-facing tables (size, water, bait, tackle) are on [Species](species.md).

## Habitat gates

These four columns are **hard gates** — outside them the fish is simply absent, not merely rare, unless it has been [stocked](stocking.md) there: a stocked species stays catchable at a quarter of full activity even in water that fails every gate. `Presents at` is the depth horizon the rig must fish (see [the float depth slider](rigs-and-baits.md#the-float)); `Cast distance` is the band, in blocks from the bank, where the species holds.

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
| Bluefish | 2+ | 14+ | beach 1.2, temperate 1.2, ocean_biome 1.1, warm 1 | mid | 10–50 |
| Bullseye snakehead | 1–6 | 4+ | warm 1.4, swamp 1.2 | surface | 3–20 |
| Jack crevalle | 2+ | 12+ | warm 1.4, beach 1.2, ocean_biome 1.1 | mid | 10–50 |
| Mayan cichlid | 1+ | 3+ | warm 1.4, swamp 1.1 | mid | 2–15 |
| Oscar | 1+ | 4+ | warm 1.4, swamp 1.1 | mid | 2–18 |
| Peacock bass | 1–10 | 6+ | warm 1.4 | mid | 5–30 |
| Snook | 2+ | 8+ | warm 1.4, beach 1.2, swamp 1, ocean_biome 0.9 | mid | 5–35 |
| Striped bass | 2+ | 12+ | beach 1.3, temperate 1.2, cold 1, ocean_biome 1 | mid | 10–45 |
| Tarpon | 3+ | 16+ | warm 1.5, beach 1.2, ocean_biome 1 | mid | 15–60 |
| Arapaima | 3+ | 14+ | warm 1.4, swamp 1.0 | surface | 10–35 |
| Beluga sturgeon | 6+ | 26+ | temperate 1.0, cold 1.0, ocean_biome 0.9, deep 0.8 | bottom | 20–60 |
| Piraiba | 5+ | 18+ | warm 1.4, swamp 0.8 | bottom | 15–45 |
| Goliath grouper | 5+ | 20+ | warm 1.4, ocean_biome 1.0, beach 0.9 | bottom | 10–35 |
| Bull shark | 4+ | 18+ | warm 1.3, ocean_biome 1.0, beach 1.0 | mid | 15–40 |
| Frilled shark | 14+ | 28+ | deep 1.6, cold 1.0, ocean_biome 0.7 | bottom | 25–60 |
| Golden dorado | 2+ | 10+ | warm 1.4, swamp 0.6 | mid | 8–30 |
| Golden crucian | 1+ | 5+ | swamp 1.3, warm 1.1, temperate 1.0 | bottom | 3–15 |
| Bitterling | 1–3 | 4+ | warm 1.1, temperate 1.0, swamp 0.9 | mid | 2–10 |
| Sunbleak | 1–2 | 3+ | temperate 1.0, warm 1.0, swamp 1.0 | surface | 1–8 |
| Sculpin | 1–4 | 3+ | cold 1.3, mountain 1.3, taiga 1.1, temperate 0.9 | bottom | 1–8 |
| Tubenose goby | 1+ | 4+ | warm 1.1, temperate 1.0, beach 1.0 | bottom | 1–10 |
| Kutum | 2–12 | 10+ | temperate 1.1, warm 1.0, beach 0.9 | bottom | 10–40 |
| Naked Carp | 2+ | 12+ | warm 1.2, temperate 1.0 | bottom | 14–45 |
| Mullet | 1–12 | 8+ | warm 1.2, beach 1.2, temperate 1.0, ocean_biome 0.9 | surface | 3–20 |
| Anglerfish | 12+ | 24+ | deep 1.4, cold 1.2, temperate 0.8, ocean_biome 0.5 | bottom | 20–45 |
| Black marlin | 12+ | 24+ | deep 1.4, warm 1.3, ocean_biome 0.4 | surface | 32–45 |
| Blobfish | 12+ | 24+ | deep 1.5, cold 1.1, temperate 0.7, ocean_biome 0.15 | bottom | 25–45 |
| Bluefin tuna | 10+ | 24+ | deep 1.3, temperate 1.2, cold 1.0, warm 0.8, ocean_biome 0.5 | mid | 25–45 |
| Loach | 1–4 | 2+ | swamp 1.2, temperate 1.1, warm 1.0, cold 0.8 | bottom | 1–8 |
| Whale shark | 14+ | 28+ | deep 1.5, warm 1.4, ocean_biome 0.3 | surface | 35–45 |
| Nelma | 3+ | 14+ | cold 1.3, taiga 1.2, mountain 0.8, temperate 0.5 | mid | 10–35 |
| Ocean sunfish | 10+ | 24+ | deep 1.3, warm 1.1, temperate 1.0, ocean_biome 0.5 | surface | 25–45 |
| Pollock | 4+ | 16+ | cold 1.4, deep 1.1, ocean_biome 0.9, temperate 0.6 | mid | 15–45 |
| Red piranha | 1–12 | 5+ | jungle 1.4, warm 1.3, swamp 1.0 | mid | 3–20 |
| Tiger shark | 8+ | 24+ | warm 1.3, ocean_biome 1.0, deep 1.0, beach 0.9 | mid | 20–45 |
| Koi carp | 1+ | 4+ | cherry 1.0 | bottom | 6–30 |
| Linear carp | 2+ | 10+ | warm 1.2, temperate 1.0 | bottom | 12–40 |
| Aba aba | 5+ | 10+ | jungle 1.2, swamp 1.2, warm 1.2, temperate 0.5 | bottom | 0–0 |
| Adonis pleco | 6+ | 14+ | jungle 1.3, warm 1.2, swamp 1, temperate 0.6 | bottom | 0–0 |
| African arowana | 4–12 | 10+ | jungle 1.4, swamp 1.2, warm 1.2, temperate 0.6 | surface | 0–0 |
| African knifefish | 3+ | 6+ | swamp 1.3, jungle 1.2, warm 1.2, temperate 0.5 | mid | 0–0 |
| African pike | 4+ | 10+ | jungle 1.3, warm 1.2, swamp 1, temperate 0.6 | mid | 0–0 |
| African sharptooth catfish | 5+ | 12+ | swamp 1.5, warm 1.2, jungle 1.1, temperate 0.7 | bottom | 0–0 |
| Alligator gar | 5+ | 18+ | swamp 1.3, warm 0.9, temperate 0.8, cherry 0.6 | surface | 0–0 |
| Angolian walking catfish | 3+ | 8+ | swamp 1.5, warm 1.2, jungle 1.1, temperate 0.6 | bottom | 0–0 |
| Ansorge's dwarf characin | 1–6 | 2–24 | jungle 1.2, warm 1.2, swamp 1.1, temperate 0.6 | mid | 0–0 |
| Asian arowana | 3–11 | 8+ | jungle 1.4, swamp 1.2, warm 1.2, temperate 0.6 | surface | 0–0 |
| Barramundi | 8+ | 22+ | jungle 1.3, warm 1.2, swamp 1, temperate 0.6 | mid | 0–0 |
| Bester | 8+ | 18+ | cold 1.1, temperate 1.1, taiga 1, cherry 0.9, mountain 0.7 | bottom | 0–0 |
| Black crappie | 2–12 | 4+ | temperate 1.2, cherry 1.1, taiga 1, cold 0.9, warm 0.9, mountain 0.8, swamp 0.8 | mid | 0–0 |
| Black drum | 5+ | 16+ | beach 1.3, ocean_biome 1.2, warm 1, temperate 0.9, deep 0.8 | bottom | 0–0 |
| Black mahseer | 6+ | 14+ | mountain 1.3, jungle 1.2, warm 1.1, temperate 0.6 | bottom | 0–0 |
| Hybrid crappie | 3–13 | 6+ | temperate 1.2, cherry 1.1, taiga 1, cold 0.9, warm 0.9, mountain 0.8, swamp 0.8 | mid | 0–0 |
| Blacktail snapper | 3+ | 10+ | beach 1.3, warm 1.3, ocean_biome 1.2, deep 0.6, temperate 0.6 | mid | 0–0 |
| Blotched upsidedown catfish | 2–10 | 3+ | warm 1.3, jungle 1.2, swamp 1.1, cherry 0.6, temperate 0.6 | bottom | 0–0 |
| Blue catfish | 7+ | 14+ | temperate 1.2, swamp 1, warm 0.9, cherry 0.8, cold 0.6 | bottom | 0–0 |
| Hybrid tilapia (blue × Mozambique) | 3–13 | 5+ | warm 1.3, jungle 1.2, swamp 1.1, cherry 0.6, temperate 0.6 | mid | 0–0 |
| Blue tilapia | 2–12 | 3+ | warm 1.3, jungle 1.2, swamp 1.1, cherry 0.6, temperate 0.6 | mid | 0–0 |
| Greengill sunfish / Hybrid bluegill | 1–8 | 2–32 | temperate 1.2, cherry 1.1, taiga 1, cold 0.9, warm 0.9, mountain 0.8, swamp 0.8 | mid | 0–0 |
| Pumpkingill (bluegill × pumpkinseed hybrid) | 1–8 | 2–32 | temperate 1.2, cherry 1.1, taiga 1, cold 0.9, warm 0.9, mountain 0.8, swamp 0.8 | mid | 0–0 |
| Bluegill × redbreast sunfish hybrid | 1–8 | 2–32 | temperate 1.2, cherry 1.1, taiga 1, cold 0.9, warm 0.9, mountain 0.8, swamp 0.8 | mid | 0–0 |
| Bluegill × redear sunfish hybrid | 1–8 | 2–32 | temperate 1.2, cherry 1.1, taiga 1, cold 0.9, warm 0.9, mountain 0.8, swamp 0.8 | mid | 0–0 |
| Bowfin | 3–10 | 8+ | swamp 1.5, warm 0.9, temperate 0.7 | bottom | 0–0 |
| Bream × roach hybrid | 3+ | 6+ | temperate 1.2, cherry 1.1, taiga 1, cold 0.9, warm 0.9, mountain 0.8, swamp 0.8 | bottom | 0–0 |
| Brook trout × bull trout hybrid | 3–13 | 7+ | cold 1.2, taiga 1.2, mountain 1.1, temperate 1, cherry 0.9, warm 0.3 | mid | 0–0 |
| Brook trout | 3–13 | 7+ | cold 1.2, taiga 1.2, mountain 1.1, temperate 1, cherry 0.9, warm 0.3 | mid | 0–0 |
| Bull trout | 4–14 | 9+ | cold 1.2, taiga 1.2, mountain 1.1, temperate 1, cherry 0.9, warm 0.3 | mid | 0–0 |
| Cameroon suckermouth catfish | 2–10 | 3+ | warm 1.3, jungle 1.2, swamp 1.1, cherry 0.6, temperate 0.6 | bottom | 0–0 |
| Catla | 6+ | 12+ | warm 1.3, jungle 1.2, swamp 1.1, cherry 0.6, temperate 0.6 | bottom | 0–0 |
| Chain pickerel | 3–11 | 10+ | temperate 1.2, cherry 1.1, taiga 1, cold 0.9, warm 0.9, mountain 0.8, swamp 0.8 | mid | 0–0 |
| Hybrid catfish (channel × blue catfish) | 7+ | 14+ | temperate 1.2, swamp 1, warm 0.9, cherry 0.8, cold 0.6 | bottom | 0–0 |
| Chinese mahseer | 4+ | 10+ | mountain 1.3, jungle 1.2, warm 1.1, temperate 0.6 | bottom | 0–0 |
| Chinook × coho salmon hybrid | 5+ | 14+ | cold 1.2, taiga 1.2, mountain 1.1, temperate 1, cherry 0.9, warm 0.3 | mid | 0–0 |
| Chinook × pink salmon hybrid | 4+ | 12+ | cold 1.2, taiga 1.2, mountain 1.1, temperate 1, cherry 0.9, warm 0.3 | mid | 0–0 |
| Chinook salmon | 5+ | 14+ | cold 1.2, taiga 1.2, mountain 1.1, temperate 1, cherry 0.9, warm 0.3 | mid | 0–0 |
| Chum salmon | 5+ | 14+ | cold 1.2, taiga 1.2, mountain 1.1, temperate 1, cherry 0.9, warm 0.3 | mid | 0–0 |
| Climbing perch | 1–10 | 3+ | swamp 1.5, warm 1.2, jungle 1.1, temperate 0.6 | mid | 0–0 |
| Coho salmon | 5+ | 14+ | cold 1.2, taiga 1.2, mountain 1.1, temperate 1, cherry 0.9, warm 0.3 | mid | 0–0 |
| Common barbel | 5+ | 10+ | temperate 1.2, mountain 1.1, cherry 1, cold 0.9, taiga 0.8 | bottom | 0–0 |
| F1 hybrid carp | 3+ | 6+ | temperate 1.2, cherry 1.1, taiga 1, cold 0.9, warm 0.9, mountain 0.8, swamp 0.8 | bottom | 0–0 |
| Congo knifefish | 3+ | 6+ | swamp 1.3, jungle 1.2, warm 1.2, temperate 0.5 | mid | 0–0 |
| Cornish jack | 5+ | 10+ | jungle 1.2, swamp 1.2, warm 1.2, temperate 0.5 | bottom | 0–0 |
| Crimean barbel | 4+ | 8+ | temperate 1.2, mountain 1.1, cherry 1, cold 0.9, taiga 0.8 | bottom | 0–0 |
| Cutbow | 5+ | 14+ | cold 1.2, taiga 1.2, mountain 1.1, temperate 1, cherry 0.9, warm 0.3 | mid | 0–0 |
| Cutthroat trout | 4+ | 12+ | cold 1.2, taiga 1.2, mountain 1.1, temperate 1, cherry 0.9, warm 0.3 | mid | 0–0 |
| Desert pupfish | 1–4 | 2–20 | warm 0.9, beach 0.8, temperate 0.7 | mid | 0–0 |
| Devils Hole pupfish | 1–4 | 2–20 | warm 0.9, beach 0.8, temperate 0.7 | mid | 0–0 |
| Dolly Varden × bull trout hybrid | 3–13 | 7+ | cold 1.2, taiga 1.2, mountain 1.1, temperate 1, cherry 0.9, warm 0.3 | mid | 0–0 |
| Dolly Varden trout | 4–14 | 9+ | cold 1.2, taiga 1.2, mountain 1.1, temperate 1, cherry 0.9, warm 0.3 | mid | 0–0 |
| Double-trunk elephant nose | 3+ | 6+ | jungle 1.2, swamp 1.2, warm 1.2, temperate 0.5 | bottom | 0–0 |
| Eastern happy | 3+ | 6+ | warm 1.3, jungle 1, deep 0.9, temperate 0.6 | mid | 0–0 |
| Electric catfish | 5+ | 12+ | swamp 1.5, warm 1.2, jungle 1.1, temperate 0.6 | bottom | 0–0 |
| Electric eel | 5–13 | 14+ | swamp 1.4, jungle 1.3, warm 1.1, temperate 0.6 | bottom | 0–0 |
| Elephantnose fish | 3+ | 6+ | jungle 1.2, swamp 1.2, warm 1.2, temperate 0.5 | bottom | 0–0 |
| Elongate lamprologus | 3+ | 6+ | warm 1.3, jungle 1, deep 0.9, temperate 0.6 | mid | 0–0 |
| Fierce bathybates | 3+ | 6+ | warm 1.3, jungle 1, deep 0.9, temperate 0.6 | mid | 0–0 |
| Flathead catfish | 7+ | 14+ | temperate 1.2, swamp 1, warm 0.9, cherry 0.8, cold 0.6 | bottom | 0–0 |
| Flier | 1–8 | 2–32 | temperate 1.2, cherry 1.1, taiga 1, cold 0.9, warm 0.9, mountain 0.8, swamp 0.8 | mid | 0–0 |
| Florida gar | 4+ | 14+ | swamp 1.3, warm 0.9, temperate 0.8, cherry 0.6 | surface | 0–0 |
| Frontosa cichlid | 3+ | 6+ | warm 1.3, jungle 1, deep 0.9, temperate 0.6 | mid | 0–0 |
| Giant cichlid | 4+ | 8+ | warm 1.3, jungle 1, deep 0.9, temperate 0.6 | mid | 0–0 |
| Giant featherback | 5+ | 10+ | swamp 1.3, jungle 1.2, warm 1.2, temperate 0.5 | mid | 0–0 |
| Giant freshwater stingray | 9+ | 24+ | jungle 1.3, warm 1.2, swamp 1, temperate 0.6 | bottom | 0–0 |
| Giant gourami | 2–11 | 5+ | swamp 1.5, warm 1.2, jungle 1.1, temperate 0.6 | mid | 0–0 |
| Giant mottled eel | 6+ | 16+ | swamp 1.2, jungle 1.1, warm 1.1, temperate 0.8 | bottom | 0–0 |
| giant snakehead | 3–12 | 10+ | swamp 1.5, warm 1.2, jungle 1.1, temperate 0.6 | surface | 0–0 |
| Goliath tigerfish | 5+ | 12+ | jungle 1.3, warm 1.2, swamp 1, temperate 0.6 | mid | 0–0 |
| Green sunfish | 1–8 | 2–32 | temperate 1.2, cherry 1.1, taiga 1, cold 0.9, warm 0.9, mountain 0.8, swamp 0.8 | mid | 0–0 |
| Helicopter Catfish | 9+ | 22+ | jungle 1.3, warm 1.2, swamp 1, temperate 0.6 | bottom | 0–0 |
| Himalayan mahseer | 7+ | 18+ | mountain 1.3, jungle 1.2, warm 1.1, temperate 0.6 | bottom | 0–0 |
| Iridescent shark | 8+ | 18+ | jungle 1.3, warm 1.2, swamp 1, temperate 0.6 | bottom | 0–0 |
| Kaluga-sterlet hybrid | 9+ | 22+ | cold 1.1, temperate 1.1, taiga 1, cherry 0.9, mountain 0.7 | bottom | 0–0 |
| Kaluga sturgeon | 9+ | 22+ | cold 1.1, temperate 1.1, taiga 1, cherry 0.9, mountain 0.7 | bottom | 0–0 |
| Kuria labeo | 6+ | 12+ | warm 1.3, jungle 1.2, swamp 1.1, cherry 0.6, temperate 0.6 | bottom | 0–0 |
| Kuria labeo × catla hybrid | 6+ | 12+ | warm 1.3, jungle 1.2, swamp 1.1, cherry 0.6, temperate 0.6 | bottom | 0–0 |
| Laced moray | 7+ | 20+ | ocean_biome 1.3, beach 1.1, warm 1.1, deep 1, temperate 0.6 | bottom | 0–0 |
| Lake sturgeon | 9+ | 22+ | cold 1.1, temperate 1.1, taiga 1, cherry 0.9, mountain 0.7 | bottom | 0–0 |
| Lake trout | 4–14 | 9+ | cold 1.2, taiga 1.2, mountain 1.1, temperate 1, cherry 0.9, warm 0.3 | mid | 0–0 |
| Largemouth × smallmouth bass hybrid | 3–13 | 8+ | temperate 1.2, cherry 1.1, taiga 1, cold 0.9, warm 0.9, mountain 0.8, swamp 0.8 | mid | 0–0 |
| Largemouth yellowfish | 5+ | 12+ | mountain 1.3, jungle 1.2, warm 1.1, temperate 0.6 | bottom | 0–0 |
| Longnose × alligator gar hybrid | 5+ | 18+ | swamp 1.3, warm 0.9, temperate 0.8, cherry 0.6 | surface | 0–0 |
| Longnose gar | 5+ | 18+ | swamp 1.3, warm 0.9, temperate 0.8, cherry 0.6 | surface | 0–0 |
| Longsnout distichodus | 3+ | 8+ | jungle 1.3, warm 1.2, swamp 1, temperate 0.5 | mid | 0–0 |
| Lutefish | 4+ | 10+ | jungle 1.3, warm 1.2, swamp 1, temperate 0.5 | mid | 0–0 |
| Lyre-tail pleco | 6+ | 14+ | jungle 1.3, warm 1.2, swamp 1, temperate 0.6 | bottom | 0–0 |
| Malayan mahseer | 6+ | 14+ | mountain 1.3, jungle 1.2, warm 1.1, temperate 0.6 | bottom | 0–0 |
| Map puffer | 4+ | 12+ | beach 1.3, warm 1.3, ocean_biome 1.2, deep 0.6, temperate 0.6 | mid | 0–0 |
| Marbled lungfish | 4–11 | 12+ | swamp 1.5, warm 1.2, jungle 1.1, temperate 0.6 | bottom | 0–0 |
| Mekong giant catfish | 9+ | 22+ | jungle 1.3, warm 1.2, swamp 1, temperate 0.6 | bottom | 0–0 |
| Mozambique tilapia | 2–12 | 3+ | warm 1.3, jungle 1.2, swamp 1.1, cherry 0.6, temperate 0.6 | mid | 0–0 |
| Muskellunge | 4–12 | 12+ | temperate 1.2, cherry 1.1, taiga 1, cold 0.9, warm 0.9, mountain 0.8, swamp 0.8 | mid | 0–0 |
| Nile bichir | 2–9 | 5+ | swamp 1.5, warm 1.2, jungle 1.1, temperate 0.6 | bottom | 0–0 |
| Hybrid tilapia | 3–13 | 5+ | warm 1.3, jungle 1.2, swamp 1.1, cherry 0.6, temperate 0.6 | mid | 0–0 |
| Red tilapia / Hybrid tilapia (Nile × Mozambique) | 3–13 | 5+ | warm 1.3, jungle 1.2, swamp 1.1, cherry 0.6, temperate 0.6 | mid | 0–0 |
| Nile perch | 8+ | 22+ | jungle 1.3, warm 1.2, swamp 1, temperate 0.6 | mid | 0–0 |
| Nile tilapia | 3–13 | 5+ | warm 1.3, jungle 1.2, swamp 1.1, cherry 0.6, temperate 0.6 | mid | 0–0 |
| Northern pike | 4–12 | 12+ | temperate 1.2, cherry 1.1, taiga 1, cold 0.9, warm 0.9, mountain 0.8, swamp 0.8 | mid | 0–0 |
| Ocellaris clownfish | 3+ | 10+ | beach 1.3, warm 1.3, ocean_biome 1.2, deep 0.6, temperate 0.6 | mid | 0–0 |
| Motoro stingray | 8+ | 20+ | jungle 1.3, warm 1.2, swamp 1, temperate 0.6 | bottom | 0–0 |
| American paddlefish | 9+ | 22+ | temperate 1.2, cherry 0.9, cold 0.9, taiga 0.8 | mid | 0–0 |
| Palette surgeonfish | 3+ | 10+ | beach 1.3, warm 1.3, ocean_biome 1.2, deep 0.6, temperate 0.6 | mid | 0–0 |
| Payara | 5+ | 12+ | jungle 1.3, warm 1.2, swamp 1, temperate 0.6 | mid | 0–0 |
| Polka-dot squeaker | 3–11 | 5+ | warm 1.3, jungle 1.2, swamp 1.1, cherry 0.6, temperate 0.6 | bottom | 0–0 |
| Pumpkinseed | 1–8 | 2–32 | temperate 1.2, cherry 1.1, taiga 1, cold 0.9, warm 0.9, mountain 0.8, swamp 0.8 | mid | 0–0 |
| Red drum | 5+ | 16+ | beach 1.3, ocean_biome 1.2, warm 1, temperate 0.9, deep 0.8 | bottom | 0–0 |
| Red-finned mahseer | 6+ | 14+ | mountain 1.3, jungle 1.2, warm 1.1, temperate 0.6 | bottom | 0–0 |
| Redbreast sunfish | 1–8 | 2–32 | temperate 1.2, cherry 1.1, taiga 1, cold 0.9, warm 0.9, mountain 0.8, swamp 0.8 | mid | 0–0 |
| Hybrid sunfish (redear × green sunfish) | 1–8 | 2–32 | temperate 1.2, cherry 1.1, taiga 1, cold 0.9, warm 0.9, mountain 0.8, swamp 0.8 | mid | 0–0 |
| Redear sunfish | 1–8 | 2–32 | temperate 1.2, cherry 1.1, taiga 1, cold 0.9, warm 0.9, mountain 0.8, swamp 0.8 | mid | 0–0 |
| Redtail catfish | 7+ | 16+ | jungle 1.3, warm 1.2, swamp 1, temperate 0.6 | bottom | 0–0 |
| Reedfish | 1–8 | 3+ | swamp 1.5, warm 1.2, jungle 1.1, temperate 0.6 | bottom | 0–0 |
| Reticulate knifefish | 4+ | 8+ | swamp 1.3, jungle 1.2, warm 1.2, temperate 0.5 | mid | 0–0 |
| Ripon barbel | 4+ | 8+ | mountain 1.1, cherry 1, temperate 0.6 | bottom | 0–0 |
| Roach × rudd hybrid | 2+ | 4+ | temperate 1.2, cherry 1.1, taiga 1, cold 0.9, warm 0.9, mountain 0.8, swamp 0.8 | bottom | 0–0 |
| Rock bass | 1–8 | 2–32 | temperate 1.2, cherry 1.1, taiga 1, cold 0.9, warm 0.9, mountain 0.8, swamp 0.8 | mid | 0–0 |
| Rohu | 7+ | 16+ | warm 1.3, jungle 1.2, swamp 1.1, cherry 0.6, temperate 0.6 | bottom | 0–0 |
| Rohu × catla hybrid | 6+ | 12+ | warm 1.3, jungle 1.2, swamp 1.1, cherry 0.6, temperate 0.6 | bottom | 0–0 |
| Rohu × kuria labeo hybrid | 6+ | 12+ | warm 1.3, jungle 1.2, swamp 1.1, cherry 0.6, temperate 0.6 | bottom | 0–0 |
| Royal featherback | 5+ | 10+ | swamp 1.3, jungle 1.2, warm 1.2, temperate 0.5 | mid | 0–0 |
| Saddled bichir | 2–9 | 5+ | swamp 1.5, warm 1.2, jungle 1.1, temperate 0.6 | bottom | 0–0 |
| Salt Creek pupfish | 1–4 | 2–20 | warm 0.9, beach 0.8, temperate 0.7 | mid | 0–0 |
| Sauger | 5+ | 8+ | temperate 1.2, cherry 1.1, taiga 1, cold 0.9, warm 0.9, mountain 0.8, swamp 0.8 | bottom | 0–0 |
| Saugeye | 5+ | 8+ | temperate 1.2, cherry 1.1, taiga 1, cold 0.9, warm 0.9, mountain 0.8, swamp 0.8 | bottom | 0–0 |
| Semutundu | 5+ | 12+ | swamp 1.5, warm 1.2, jungle 1.1, temperate 0.6 | bottom | 0–0 |
| Senegal bichir | 2–9 | 5+ | swamp 1.5, warm 1.2, jungle 1.1, temperate 0.6 | bottom | 0–0 |
| Short-tailed river stingray | 9+ | 24+ | jungle 1.3, warm 1.2, swamp 1, temperate 0.6 | bottom | 0–0 |
| Shovelnose sturgeon | 8+ | 18+ | cold 1.1, temperate 1.1, taiga 1, cherry 0.9, mountain 0.7 | bottom | 0–0 |
| Silver catfish | 4+ | 10+ | swamp 1.5, warm 1.2, jungle 1.1, temperate 0.6 | bottom | 0–0 |
| Sixbar distichodus | 4+ | 10+ | jungle 1.3, warm 1.2, swamp 1, temperate 0.5 | mid | 0–0 |
| Smallmouth bass | 3–13 | 8+ | temperate 1.2, cherry 1.1, taiga 1, cold 0.9, warm 0.9, mountain 0.8, swamp 0.8 | mid | 0–0 |
| Meanmouth bass | 3–13 | 8+ | temperate 1.2, cherry 1.1, taiga 1, cold 0.9, warm 0.9, mountain 0.8, swamp 0.8 | mid | 0–0 |
| Sockeye salmon | 4+ | 12+ | cold 1.2, taiga 1.2, mountain 1.1, temperate 1, cherry 0.9, warm 0.3 | mid | 0–0 |
| Splake | 4–14 | 9+ | cold 1.2, taiga 1.2, mountain 1.1, temperate 1, cherry 0.9, warm 0.3 | mid | 0–0 |
| Spotted bass | 3–13 | 8+ | temperate 1.2, cherry 1.1, taiga 1, cold 0.9, warm 0.9, mountain 0.8, swamp 0.8 | mid | 0–0 |
| Spotted gar | 4+ | 14+ | swamp 1.3, warm 0.9, temperate 0.8, cherry 0.6 | surface | 0–0 |
| Spotted seatrout | 5+ | 16+ | beach 1.3, ocean_biome 1.2, warm 1, temperate 0.9, deep 0.8 | bottom | 0–0 |
| Starry puffer | 5+ | 14+ | beach 1.3, warm 1.3, ocean_biome 1.2, deep 0.6, temperate 0.6 | mid | 0–0 |
| Tambaqui | 5+ | 12+ | jungle 1.3, warm 1.2, swamp 1, temperate 0.5 | mid | 0–0 |
| Tapah Catfish | 9+ | 22+ | jungle 1.3, warm 1.2, swamp 1, temperate 0.6 | bottom | 0–0 |
| Terek barbel | 4+ | 8+ | temperate 1.2, mountain 1.1, cherry 1, cold 0.9, taiga 0.8 | bottom | 0–0 |
| Tiger muskellunge | 4–12 | 12+ | temperate 1.2, cherry 1.1, taiga 1, cold 0.9, warm 0.9, mountain 0.8, swamp 0.8 | mid | 0–0 |
| African tigerfish | 5+ | 12+ | jungle 1.3, warm 1.2, swamp 1, temperate 0.6 | mid | 0–0 |
| Trahira | 4+ | 10+ | jungle 1.3, warm 1.2, swamp 1, temperate 0.6 | mid | 0–0 |
| Trout cichlid | 3+ | 6+ | warm 1.3, jungle 1, deep 0.9, temperate 0.6 | mid | 0–0 |
| Vistula barbel | 3+ | 6+ | temperate 1.2, mountain 1.1, cherry 1, cold 0.9, taiga 0.8 | bottom | 0–0 |
| Vundu catfish | 5+ | 12+ | swamp 1.5, warm 1.2, jungle 1.1, temperate 0.6 | bottom | 0–0 |
| Walleye | 6+ | 10+ | temperate 1.2, cherry 1.1, taiga 1, cold 0.9, warm 0.9, mountain 0.8, swamp 0.8 | bottom | 0–0 |
| Warmouth | 1–8 | 2–32 | temperate 1.2, cherry 1.1, taiga 1, cold 0.9, warm 0.9, mountain 0.8, swamp 0.8 | mid | 0–0 |
| West African lungfish | 3–10 | 8+ | swamp 1.5, warm 1.2, jungle 1.1, temperate 0.6 | bottom | 0–0 |
| White bass | 3+ | 8+ | temperate 1.2, cherry 1.1, taiga 1, cold 0.9, warm 0.9, mountain 0.8, swamp 0.8 | mid | 0–0 |
| White crappie | 3–13 | 6+ | temperate 1.2, cherry 1.1, taiga 1, cold 0.9, warm 0.9, mountain 0.8, swamp 0.8 | mid | 0–0 |
| White sturgeon | 9+ | 22+ | cold 1.1, temperate 1.1, taiga 1, cherry 0.9, mountain 0.7 | bottom | 0–0 |
| Turkey moray | 6+ | 16+ | ocean_biome 1.3, beach 1.1, warm 1.1, deep 1, temperate 0.6 | bottom | 0–0 |
| White-spotted puffer | 4+ | 12+ | beach 1.3, warm 1.3, ocean_biome 1.2, deep 0.6 | mid | 0–0 |
| Wiper (hybrid striped bass) | 5+ | 12+ | temperate 1.2, cherry 1.1, taiga 1, cold 0.9, warm 0.9, mountain 0.8, swamp 0.8 | mid | 0–0 |
| Yellow bass | 3+ | 8+ | temperate 1.2, cherry 1.1, taiga 1, cold 0.9, warm 0.9, mountain 0.8, swamp 0.8 | mid | 0–0 |
| Yellow perch | 5+ | 8+ | temperate 1.2, cherry 1.1, taiga 1, cold 0.9, warm 0.9, mountain 0.8, swamp 0.8 | bottom | 0–0 |

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
| Bluefish | 1 | 1.2 | 1.3 | 0.4 | 1.3 | 0.9 | 1.3 | 0.8 | 1 | 1.1 | 1.1 |
| Bullseye snakehead | 1.1 | 1.3 | 1 | 0.05 | 1.2 | 1 | 1.2 | 0.6 | 1 | 1.1 | 1 |
| Jack crevalle | 1.1 | 1.3 | 1.2 | 0.3 | 1.3 | 1 | 1.3 | 0.6 | 1 | 1.1 | 1 |
| Mayan cichlid | 1.1 | 1.3 | 1 | 0.05 | 1.1 | 1.1 | 1.1 | 0.5 | 1 | 1 | 0.9 |
| Oscar | 1.1 | 1.3 | 1 | 0.05 | 1.2 | 1 | 1.2 | 0.5 | 1 | 1 | 0.9 |
| Peacock bass | 1.2 | 1.3 | 1 | 0.05 | 1.3 | 1 | 1.3 | 0.4 | 1 | 1 | 0.9 |
| Snook | 1.1 | 1.3 | 1.1 | 0.2 | 1.4 | 0.7 | 1.4 | 1.3 | 1 | 1.1 | 1 |
| Striped bass | 1.2 | 0.9 | 1.3 | 0.7 | 1.3 | 0.7 | 1.4 | 1.1 | 0.9 | 1.2 | 1.2 |
| Tarpon | 1.2 | 1.3 | 1 | 0.2 | 1.4 | 0.7 | 1.4 | 1.2 | 1 | 1.1 | 0.9 |
| Arapaima | 1.1 | 1.2 | 1.0 | 0.3 | 1.2 | 0.9 | 1.2 | 0.8 | 1.0 | 1.2 | 0.9 |
| Beluga sturgeon | 1.2 | 0.9 | 1.2 | 0.5 | 1.1 | 0.6 | 1.2 | 1.4 | 1.0 | 1.1 | 1.0 |
| Piraiba | 1.1 | 1.2 | 1.0 | 0.3 | 1.0 | 0.4 | 1.3 | 1.6 | 0.9 | 1.3 | 1.1 |
| Goliath grouper | 1.0 | 1.2 | 1.1 | 0.7 | 1.2 | 1.0 | 1.2 | 1.1 | 1.0 | 1.0 | 1.0 |
| Bull shark | 1.0 | 1.3 | 1.1 | 0.5 | 1.3 | 0.9 | 1.3 | 1.1 | 1.0 | 1.1 | 1.0 |
| Frilled shark | 1.0 | 0.8 | 1.0 | 1.1 | 1.0 | 0.25 | 1.2 | 1.6 | 1.0 | 1.0 | 1.0 |
| Golden dorado | 1.2 | 1.3 | 1.0 | 0.2 | 1.4 | 0.9 | 1.4 | 0.5 | 1.0 | 1.2 | 0.9 |
| Golden crucian | 1.0 | 1.4 | 0.8 | 0.05 | 1.3 | 0.9 | 1.3 | 0.6 | 1.0 | 1.2 | 0.7 |
| Bitterling | 1.1 | 1.3 | 0.7 | 0.0 | 1.0 | 1.2 | 1.0 | 0.0 | 1.2 | 0.9 | 0.6 |
| Sunbleak | 1.1 | 1.4 | 0.6 | 0.0 | 1.1 | 1.3 | 1.1 | 0.0 | 1.2 | 0.8 | 0.5 |
| Sculpin | 1.1 | 1.0 | 1.1 | 0.5 | 1.0 | 0.6 | 1.2 | 1.4 | 1.0 | 1.1 | 0.9 |
| Tubenose goby | 1.1 | 1.2 | 1.0 | 0.3 | 1.1 | 1.0 | 1.1 | 0.8 | 1.0 | 1.1 | 0.8 |
| Kutum | 1.4 | 0.9 | 1.1 | 0.4 | 1.3 | 0.8 | 1.3 | 0.7 | 1.0 | 1.1 | 0.9 |
| Naked Carp | 0.8 | 1.4 | 0.95 | 0.03 | 1.2 | 0.85 | 1.2 | 1.05 | 0.9 | 1.2 | 0.8 |
| Mullet | 1.0 | 1.4 | 1.1 | 0.3 | 1.2 | 1.1 | 1.2 | 0.4 | 1.1 | 0.9 | 0.7 |
| Anglerfish | 1.0 | 0.8 | 1.1 | 1.2 | 0.8 | 0.5 | 1.1 | 1.5 | 1.0 | 1.0 | 0.9 |
| Black marlin | 1.0 | 1.3 | 1.1 | 0.4 | 1.3 | 1.2 | 1.1 | 0.2 | 1.2 | 0.9 | 0.5 |
| Blobfish | 1.0 | 1.0 | 1.0 | 1.0 | 1.0 | 1.0 | 1.0 | 1.0 | 1.0 | 1.0 | 1.0 |
| Bluefin tuna | 0.9 | 1.2 | 1.3 | 0.6 | 1.3 | 1.0 | 1.2 | 0.5 | 1.1 | 1.0 | 0.7 |
| Loach | 1.1 | 1.2 | 1.0 | 0.5 | 1.1 | 0.5 | 1.3 | 1.4 | 0.8 | 1.3 | 1.4 |
| Whale shark | 1.0 | 1.2 | 1.0 | 0.6 | 1.1 | 1.2 | 1.0 | 0.5 | 1.2 | 0.8 | 0.4 |
| Nelma | 0.9 | 0.8 | 1.5 | 0.7 | 1.3 | 1.0 | 1.3 | 0.6 | 1.0 | 1.1 | 0.8 |
| Ocean sunfish | 1.0 | 1.3 | 1.1 | 0.5 | 1.0 | 1.3 | 1.0 | 0.4 | 1.3 | 0.8 | 0.5 |
| Pollock | 1.1 | 0.8 | 1.2 | 1.3 | 1.2 | 1.0 | 1.2 | 0.8 | 0.9 | 1.1 | 1.0 |
| Red piranha | 1.0 | 1.2 | 1.0 | 0.4 | 1.1 | 1.3 | 1.1 | 0.4 | 1.1 | 1.0 | 0.8 |
| Tiger shark | 1.0 | 1.3 | 1.1 | 0.4 | 1.2 | 0.7 | 1.3 | 1.5 | 1.0 | 1.1 | 0.9 |
| Koi carp | 1.0 | 1.0 | 1.0 | 0.3 | 1.0 | 1.0 | 1.0 | 1.0 | 1.0 | 1.0 | 1.0 |
| Linear carp | 0.8 | 1.4 | 0.9 | 0.05 | 1.2 | 0.9 | 1.2 | 1.0 | 0.9 | 1.2 | 0.8 |
| Aba aba | 1 | 1.1 | 1 | 0.3 | 1 | 0.4 | 1.2 | 1.5 | 1 | 1 | 1 |
| Adonis pleco | 1 | 1.1 | 1 | 0.3 | 1 | 0.4 | 1.2 | 1.5 | 1 | 1 | 1 |
| African arowana | 1 | 1.1 | 1 | 0.3 | 1.1 | 1.2 | 1 | 0.4 | 1.1 | 0.9 | 0.8 |
| African knifefish | 1 | 1.1 | 1 | 0.3 | 1 | 0.4 | 1.2 | 1.5 | 1 | 1 | 1 |
| African pike | 1 | 1.1 | 1 | 0.3 | 1.3 | 0.8 | 1.3 | 0.7 | 1.1 | 0.9 | 0.8 |
| African sharptooth catfish | 1 | 1.1 | 1 | 0.3 | 1 | 0.4 | 1.2 | 1.5 | 1 | 1.2 | 1.1 |
| Alligator gar | 1 | 1.1 | 1 | 0.3 | 1 | 0.4 | 1.2 | 1.5 | 1 | 1 | 1 |
| Angolian walking catfish | 1 | 1.1 | 1 | 0.3 | 1 | 0.4 | 1.2 | 1.5 | 1 | 1.2 | 1.1 |
| Ansorge's dwarf characin | 1 | 1.1 | 1 | 0.3 | 1.1 | 1.2 | 1 | 0.4 | 1 | 1 | 1 |
| Asian arowana | 1 | 1.1 | 1 | 0.3 | 1.1 | 1.2 | 1 | 0.4 | 1.1 | 0.9 | 0.8 |
| Barramundi | 1 | 1.1 | 1 | 0.3 | 1.3 | 0.8 | 1.3 | 0.7 | 1 | 1.2 | 1.1 |
| Bester | 1.1 | 1 | 1.2 | 0.6 | 1 | 0.4 | 1.2 | 1.5 | 1 | 1 | 1 |
| Black crappie | 1.3 | 1.2 | 0.9 | 0.4 | 1.3 | 0.8 | 1.3 | 0.7 | 1.1 | 0.9 | 0.8 |
| Black drum | 1 | 1.2 | 1.1 | 0.5 | 1.3 | 0.8 | 1.3 | 0.7 | 1 | 1.2 | 1.1 |
| Black mahseer | 1 | 1.1 | 1 | 0.3 | 1.3 | 0.8 | 1.3 | 0.7 | 1 | 1.2 | 1.1 |
| Hybrid crappie | 1.3 | 1.2 | 0.9 | 0.4 | 1.3 | 0.8 | 1.3 | 0.7 | 1.1 | 0.9 | 0.8 |
| Blacktail snapper | 1 | 1.2 | 1.1 | 0.3 | 1.1 | 1.2 | 1 | 0.4 | 1.1 | 0.9 | 0.8 |
| Blotched upsidedown catfish | 1 | 1.1 | 1 | 0.3 | 1 | 0.4 | 1.2 | 1.5 | 1 | 1 | 1 |
| Blue catfish | 1.1 | 1 | 1.2 | 0.6 | 1 | 0.4 | 1.2 | 1.5 | 1 | 1.2 | 1.1 |
| Hybrid tilapia (blue × Mozambique) | 1 | 1.1 | 1 | 0.3 | 1.1 | 1.2 | 1 | 0.4 | 1.1 | 0.9 | 0.8 |
| Blue tilapia | 1 | 1.1 | 1 | 0.3 | 1.1 | 1.2 | 1 | 0.4 | 1.1 | 0.9 | 0.8 |
| Greengill sunfish / Hybrid bluegill | 1.3 | 1.2 | 0.9 | 0.4 | 1.1 | 1.2 | 1 | 0.4 | 1.1 | 0.9 | 0.8 |
| Pumpkingill (bluegill × pumpkinseed hybrid) | 1.3 | 1.2 | 0.9 | 0.4 | 1.1 | 1.2 | 1 | 0.4 | 1.1 | 0.9 | 0.8 |
| Bluegill × redbreast sunfish hybrid | 1.3 | 1.2 | 0.9 | 0.4 | 1.1 | 1.2 | 1 | 0.4 | 1.1 | 0.9 | 0.8 |
| Bluegill × redear sunfish hybrid | 1.3 | 1.2 | 0.9 | 0.4 | 1.1 | 1.2 | 1 | 0.4 | 1.1 | 0.9 | 0.8 |
| Bowfin | 1.3 | 1.2 | 0.9 | 0.4 | 1 | 0.4 | 1.2 | 1.5 | 1 | 1.2 | 1.1 |
| Bream × roach hybrid | 1.1 | 1 | 1.2 | 0.6 | 1.1 | 1 | 1.1 | 0.8 | 1 | 1.2 | 1.1 |
| Brook trout × bull trout hybrid | 1.1 | 0.7 | 1.4 | 0.9 | 1.3 | 0.8 | 1.3 | 0.7 | 1 | 1.2 | 1.1 |
| Brook trout | 1.1 | 0.7 | 1.4 | 0.9 | 1.3 | 0.8 | 1.3 | 0.7 | 1 | 1.2 | 1.1 |
| Bull trout | 1.1 | 0.7 | 1.4 | 0.9 | 1.3 | 0.8 | 1.3 | 0.7 | 1 | 1.2 | 1.1 |
| Cameroon suckermouth catfish | 1 | 1.1 | 1 | 0.3 | 1 | 0.4 | 1.2 | 1.5 | 1 | 1 | 1 |
| Catla | 1 | 1.1 | 1 | 0.3 | 1.1 | 1 | 1.1 | 0.8 | 1 | 1.2 | 1.1 |
| Chain pickerel | 1.1 | 0.7 | 1.4 | 0.9 | 1.3 | 0.8 | 1.3 | 0.7 | 1.1 | 0.9 | 0.8 |
| Hybrid catfish (channel × blue catfish) | 1.1 | 1 | 1.2 | 0.6 | 1 | 0.4 | 1.2 | 1.5 | 1 | 1.2 | 1.1 |
| Chinese mahseer | 1 | 1.1 | 1 | 0.3 | 1.3 | 0.8 | 1.3 | 0.7 | 1 | 1.2 | 1.1 |
| Chinook × coho salmon hybrid | 1.1 | 0.7 | 1.4 | 0.9 | 1.3 | 0.8 | 1.3 | 0.7 | 1 | 1.2 | 1.1 |
| Chinook × pink salmon hybrid | 1.1 | 0.7 | 1.4 | 0.9 | 1.3 | 0.8 | 1.3 | 0.7 | 1 | 1.2 | 1.1 |
| Chinook salmon | 1.1 | 0.7 | 1.4 | 0.9 | 1.3 | 0.8 | 1.3 | 0.7 | 1 | 1.2 | 1.1 |
| Chum salmon | 1.1 | 0.7 | 1.4 | 0.9 | 1.3 | 0.8 | 1.3 | 0.7 | 1 | 1.2 | 1.1 |
| Climbing perch | 1 | 1.1 | 1 | 0.3 | 1.1 | 1.2 | 1 | 0.4 | 1 | 1.2 | 1.1 |
| Coho salmon | 1.1 | 0.7 | 1.4 | 0.9 | 1.3 | 0.8 | 1.3 | 0.7 | 1 | 1.2 | 1.1 |
| Common barbel | 1.1 | 1 | 1.2 | 0.6 | 1.3 | 0.8 | 1.3 | 0.7 | 1 | 1.2 | 1.1 |
| F1 hybrid carp | 1.1 | 1 | 1.2 | 0.6 | 1.1 | 1 | 1.1 | 0.8 | 1 | 1.2 | 1.1 |
| Congo knifefish | 1 | 1.1 | 1 | 0.3 | 1 | 0.4 | 1.2 | 1.5 | 1 | 1 | 1 |
| Cornish jack | 1 | 1.1 | 1 | 0.3 | 1 | 0.4 | 1.2 | 1.5 | 1 | 1 | 1 |
| Crimean barbel | 1.1 | 1 | 1.2 | 0.6 | 1.3 | 0.8 | 1.3 | 0.7 | 1 | 1.2 | 1.1 |
| Cutbow | 1.1 | 0.7 | 1.4 | 0.9 | 1.3 | 0.8 | 1.3 | 0.7 | 1 | 1.2 | 1.1 |
| Cutthroat trout | 1.1 | 0.7 | 1.4 | 0.9 | 1.3 | 0.8 | 1.3 | 0.7 | 1 | 1.2 | 1.1 |
| Desert pupfish | 1 | 1.1 | 1 | 0.3 | 1.1 | 1.2 | 1 | 0.4 | 1.1 | 0.9 | 0.8 |
| Devils Hole pupfish | 1 | 1.1 | 1 | 0.3 | 1.1 | 1.2 | 1 | 0.4 | 1.1 | 0.9 | 0.8 |
| Dolly Varden × bull trout hybrid | 1.1 | 0.7 | 1.4 | 0.9 | 1.3 | 0.8 | 1.3 | 0.7 | 1 | 1.2 | 1.1 |
| Dolly Varden trout | 1.1 | 0.7 | 1.4 | 0.9 | 1.3 | 0.8 | 1.3 | 0.7 | 1 | 1.2 | 1.1 |
| Double-trunk elephant nose | 1 | 1.1 | 1 | 0.3 | 1 | 0.4 | 1.2 | 1.5 | 1 | 1 | 1 |
| Eastern happy | 1 | 1.1 | 1 | 0.3 | 1.1 | 1.2 | 1 | 0.4 | 1.1 | 0.9 | 0.8 |
| Electric catfish | 1 | 1.1 | 1 | 0.3 | 1 | 0.4 | 1.2 | 1.5 | 1 | 1.2 | 1.1 |
| Electric eel | 1 | 1.1 | 1 | 0.3 | 1 | 0.4 | 1.2 | 1.5 | 1 | 1 | 1 |
| Elephantnose fish | 1 | 1.1 | 1 | 0.3 | 1 | 0.4 | 1.2 | 1.5 | 1 | 1 | 1 |
| Elongate lamprologus | 1 | 1.1 | 1 | 0.3 | 1.1 | 1.2 | 1 | 0.4 | 1.1 | 0.9 | 0.8 |
| Fierce bathybates | 1 | 1.1 | 1 | 0.3 | 1.1 | 1.2 | 1 | 0.4 | 1.1 | 0.9 | 0.8 |
| Flathead catfish | 1.1 | 1 | 1.2 | 0.6 | 1 | 0.4 | 1.2 | 1.5 | 1 | 1.2 | 1.1 |
| Flier | 1.3 | 1.2 | 0.9 | 0.4 | 1.1 | 1.2 | 1 | 0.4 | 1.1 | 0.9 | 0.8 |
| Florida gar | 1 | 1.1 | 1 | 0.3 | 1 | 0.4 | 1.2 | 1.5 | 1 | 1 | 1 |
| Frontosa cichlid | 1 | 1.1 | 1 | 0.3 | 1.1 | 1.2 | 1 | 0.4 | 1.1 | 0.9 | 0.8 |
| Giant cichlid | 1 | 1.1 | 1 | 0.3 | 1.1 | 1.2 | 1 | 0.4 | 1.1 | 0.9 | 0.8 |
| Giant featherback | 1 | 1.1 | 1 | 0.3 | 1 | 0.4 | 1.2 | 1.5 | 1 | 1 | 1 |
| Giant freshwater stingray | 1 | 1.1 | 1 | 0.3 | 1 | 0.4 | 1.2 | 1.5 | 1 | 1 | 1 |
| Giant gourami | 1 | 1.1 | 1 | 0.3 | 1.1 | 1.2 | 1 | 0.4 | 1 | 1.2 | 1.1 |
| Giant mottled eel | 1 | 1.1 | 1 | 0.3 | 1 | 0.4 | 1.2 | 1.5 | 1 | 1.2 | 1.1 |
| giant snakehead | 1 | 1.1 | 1 | 0.3 | 1.1 | 1.2 | 1 | 0.4 | 1 | 1.2 | 1.1 |
| Goliath tigerfish | 1 | 1.1 | 1 | 0.3 | 1.3 | 0.8 | 1.3 | 0.7 | 1.1 | 0.9 | 0.8 |
| Green sunfish | 1.3 | 1.2 | 0.9 | 0.4 | 1.1 | 1.2 | 1 | 0.4 | 1.1 | 0.9 | 0.8 |
| Helicopter Catfish | 1 | 1.1 | 1 | 0.3 | 1 | 0.4 | 1.2 | 1.5 | 1 | 1.2 | 1.1 |
| Himalayan mahseer | 1 | 1.1 | 1 | 0.3 | 1.3 | 0.8 | 1.3 | 0.7 | 1 | 1.2 | 1.1 |
| Iridescent shark | 1 | 1.1 | 1 | 0.3 | 1 | 0.4 | 1.2 | 1.5 | 1 | 1.2 | 1.1 |
| Kaluga-sterlet hybrid | 1.1 | 1 | 1.2 | 0.6 | 1 | 0.4 | 1.2 | 1.5 | 1 | 1 | 1 |
| Kaluga sturgeon | 1.1 | 1 | 1.2 | 0.6 | 1 | 0.4 | 1.2 | 1.5 | 1 | 1 | 1 |
| Kuria labeo | 1 | 1.1 | 1 | 0.3 | 1.1 | 1 | 1.1 | 0.8 | 1 | 1.2 | 1.1 |
| Kuria labeo × catla hybrid | 1 | 1.1 | 1 | 0.3 | 1.1 | 1 | 1.1 | 0.8 | 1 | 1.2 | 1.1 |
| Laced moray | 1 | 1.2 | 1.1 | 0.3 | 1 | 0.4 | 1.2 | 1.5 | 1 | 1 | 1 |
| Lake sturgeon | 1.1 | 1 | 1.2 | 0.6 | 1 | 0.4 | 1.2 | 1.5 | 1 | 1 | 1 |
| Lake trout | 1.1 | 0.7 | 1.4 | 0.9 | 1.3 | 0.8 | 1.3 | 0.7 | 1 | 1.2 | 1.1 |
| Largemouth × smallmouth bass hybrid | 1.3 | 1.2 | 0.9 | 0.4 | 1.3 | 0.8 | 1.3 | 0.7 | 1 | 1.2 | 1.1 |
| Largemouth yellowfish | 1 | 1.1 | 1 | 0.3 | 1.3 | 0.8 | 1.3 | 0.7 | 1 | 1.2 | 1.1 |
| Longnose × alligator gar hybrid | 1 | 1.1 | 1 | 0.3 | 1 | 0.4 | 1.2 | 1.5 | 1 | 1 | 1 |
| Longnose gar | 1 | 1.1 | 1 | 0.3 | 1 | 0.4 | 1.2 | 1.5 | 1 | 1 | 1 |
| Longsnout distichodus | 1 | 1.1 | 1 | 0.3 | 1.1 | 1.2 | 1 | 0.4 | 1 | 1.2 | 1.1 |
| Lutefish | 1 | 1.1 | 1 | 0.3 | 1.1 | 1.2 | 1 | 0.4 | 1 | 1.2 | 1.1 |
| Lyre-tail pleco | 1 | 1.1 | 1 | 0.3 | 1 | 0.4 | 1.2 | 1.5 | 1 | 1 | 1 |
| Malayan mahseer | 1 | 1.1 | 1 | 0.3 | 1.3 | 0.8 | 1.3 | 0.7 | 1 | 1.2 | 1.1 |
| Map puffer | 1 | 1.2 | 1.1 | 0.3 | 1.1 | 1.2 | 1 | 0.4 | 1 | 1 | 1 |
| Marbled lungfish | 1 | 1.1 | 1 | 0.3 | 1 | 0.4 | 1.2 | 1.5 | 1 | 1.2 | 1.1 |
| Mekong giant catfish | 1 | 1.1 | 1 | 0.3 | 1 | 0.4 | 1.2 | 1.5 | 1 | 1.2 | 1.1 |
| Mozambique tilapia | 1 | 1.1 | 1 | 0.3 | 1.1 | 1.2 | 1 | 0.4 | 1.1 | 0.9 | 0.8 |
| Muskellunge | 1.1 | 0.7 | 1.4 | 0.9 | 1.3 | 0.8 | 1.3 | 0.7 | 1.1 | 0.9 | 0.8 |
| Nile bichir | 1 | 1.1 | 1 | 0.3 | 1 | 0.4 | 1.2 | 1.5 | 1 | 1 | 1 |
| Hybrid tilapia | 1 | 1.1 | 1 | 0.3 | 1.1 | 1.2 | 1 | 0.4 | 1.1 | 0.9 | 0.8 |
| Red tilapia / Hybrid tilapia (Nile × Mozambique) | 1 | 1.1 | 1 | 0.3 | 1.1 | 1.2 | 1 | 0.4 | 1.1 | 0.9 | 0.8 |
| Nile perch | 1 | 1.1 | 1 | 0.3 | 1.3 | 0.8 | 1.3 | 0.7 | 1 | 1.2 | 1.1 |
| Nile tilapia | 1 | 1.1 | 1 | 0.3 | 1.1 | 1.2 | 1 | 0.4 | 1.1 | 0.9 | 0.8 |
| Northern pike | 1.1 | 0.7 | 1.4 | 0.9 | 1.3 | 0.8 | 1.3 | 0.7 | 1.1 | 0.9 | 0.8 |
| Ocellaris clownfish | 1 | 1.2 | 1.1 | 0.3 | 1.1 | 1.2 | 1 | 0.4 | 1.1 | 0.9 | 0.8 |
| Motoro stingray | 1 | 1.1 | 1 | 0.3 | 1 | 0.4 | 1.2 | 1.5 | 1 | 1 | 1 |
| American paddlefish | 1.1 | 1 | 1.2 | 0.6 | 1.1 | 1 | 1.1 | 0.8 | 1 | 1 | 1 |
| Palette surgeonfish | 1 | 1.2 | 1.1 | 0.3 | 1.1 | 1.2 | 1 | 0.4 | 1.1 | 0.9 | 0.8 |
| Payara | 1 | 1.1 | 1 | 0.3 | 1.3 | 0.8 | 1.3 | 0.7 | 1.1 | 0.9 | 0.8 |
| Polka-dot squeaker | 1 | 1.1 | 1 | 0.3 | 1 | 0.4 | 1.2 | 1.5 | 1 | 1 | 1 |
| Pumpkinseed | 1.3 | 1.2 | 0.9 | 0.4 | 1.1 | 1.2 | 1 | 0.4 | 1.1 | 0.9 | 0.8 |
| Red drum | 1 | 1.2 | 1.1 | 0.5 | 1.3 | 0.8 | 1.3 | 0.7 | 1 | 1.2 | 1.1 |
| Red-finned mahseer | 1 | 1.1 | 1 | 0.3 | 1.3 | 0.8 | 1.3 | 0.7 | 1 | 1.2 | 1.1 |
| Redbreast sunfish | 1.3 | 1.2 | 0.9 | 0.4 | 1.1 | 1.2 | 1 | 0.4 | 1.1 | 0.9 | 0.8 |
| Hybrid sunfish (redear × green sunfish) | 1.3 | 1.2 | 0.9 | 0.4 | 1.1 | 1.2 | 1 | 0.4 | 1.1 | 0.9 | 0.8 |
| Redear sunfish | 1.3 | 1.2 | 0.9 | 0.4 | 1.1 | 1.2 | 1 | 0.4 | 1.1 | 0.9 | 0.8 |
| Redtail catfish | 1 | 1.1 | 1 | 0.3 | 1 | 0.4 | 1.2 | 1.5 | 1 | 1.2 | 1.1 |
| Reedfish | 1 | 1.1 | 1 | 0.3 | 1 | 0.4 | 1.2 | 1.5 | 1 | 1 | 1 |
| Reticulate knifefish | 1 | 1.1 | 1 | 0.3 | 1 | 0.4 | 1.2 | 1.5 | 1 | 1 | 1 |
| Ripon barbel | 1.1 | 1 | 1.2 | 0.3 | 1.3 | 0.8 | 1.3 | 0.7 | 1 | 1.2 | 1.1 |
| Roach × rudd hybrid | 1.1 | 1 | 1.2 | 0.6 | 1.1 | 1 | 1.1 | 0.8 | 1 | 1.2 | 1.1 |
| Rock bass | 1.3 | 1.2 | 0.9 | 0.4 | 1.1 | 1.2 | 1 | 0.4 | 1.1 | 0.9 | 0.8 |
| Rohu | 1 | 1.1 | 1 | 0.3 | 1.1 | 1 | 1.1 | 0.8 | 1 | 1.2 | 1.1 |
| Rohu × catla hybrid | 1 | 1.1 | 1 | 0.3 | 1.1 | 1 | 1.1 | 0.8 | 1 | 1.2 | 1.1 |
| Rohu × kuria labeo hybrid | 1 | 1.1 | 1 | 0.3 | 1.1 | 1 | 1.1 | 0.8 | 1 | 1.2 | 1.1 |
| Royal featherback | 1 | 1.1 | 1 | 0.3 | 1 | 0.4 | 1.2 | 1.5 | 1 | 1 | 1 |
| Saddled bichir | 1 | 1.1 | 1 | 0.3 | 1 | 0.4 | 1.2 | 1.5 | 1 | 1 | 1 |
| Salt Creek pupfish | 1 | 1.1 | 1 | 0.3 | 1.1 | 1.2 | 1 | 0.4 | 1.1 | 0.9 | 0.8 |
| Sauger | 1.1 | 0.7 | 1.4 | 0.9 | 1.3 | 0.8 | 1.3 | 0.7 | 1.1 | 0.9 | 0.8 |
| Saugeye | 1.1 | 0.7 | 1.4 | 0.9 | 1.3 | 0.8 | 1.3 | 0.7 | 1.1 | 0.9 | 0.8 |
| Semutundu | 1 | 1.1 | 1 | 0.3 | 1 | 0.4 | 1.2 | 1.5 | 1 | 1.2 | 1.1 |
| Senegal bichir | 1 | 1.1 | 1 | 0.3 | 1 | 0.4 | 1.2 | 1.5 | 1 | 1 | 1 |
| Short-tailed river stingray | 1 | 1.1 | 1 | 0.3 | 1 | 0.4 | 1.2 | 1.5 | 1 | 1 | 1 |
| Shovelnose sturgeon | 1.1 | 1 | 1.2 | 0.6 | 1 | 0.4 | 1.2 | 1.5 | 1 | 1 | 1 |
| Silver catfish | 1 | 1.1 | 1 | 0.3 | 1 | 0.4 | 1.2 | 1.5 | 1 | 1.2 | 1.1 |
| Sixbar distichodus | 1 | 1.1 | 1 | 0.3 | 1.1 | 1.2 | 1 | 0.4 | 1 | 1.2 | 1.1 |
| Smallmouth bass | 1.3 | 1.2 | 0.9 | 0.4 | 1.3 | 0.8 | 1.3 | 0.7 | 1 | 1.2 | 1.1 |
| Meanmouth bass | 1.3 | 1.2 | 0.9 | 0.4 | 1.3 | 0.8 | 1.3 | 0.7 | 1 | 1.2 | 1.1 |
| Sockeye salmon | 1.1 | 0.7 | 1.4 | 0.9 | 1.3 | 0.8 | 1.3 | 0.7 | 1 | 1.2 | 1.1 |
| Splake | 1.1 | 0.7 | 1.4 | 0.9 | 1.3 | 0.8 | 1.3 | 0.7 | 1 | 1.2 | 1.1 |
| Spotted bass | 1.3 | 1.2 | 0.9 | 0.4 | 1.3 | 0.8 | 1.3 | 0.7 | 1 | 1.2 | 1.1 |
| Spotted gar | 1 | 1.1 | 1 | 0.3 | 1 | 0.4 | 1.2 | 1.5 | 1 | 1 | 1 |
| Spotted seatrout | 1 | 1.2 | 1.1 | 0.5 | 1.3 | 0.8 | 1.3 | 0.7 | 1 | 1.2 | 1.1 |
| Starry puffer | 1 | 1.2 | 1.1 | 0.3 | 1.1 | 1.2 | 1 | 0.4 | 1 | 1 | 1 |
| Tambaqui | 1 | 1.1 | 1 | 0.3 | 1.1 | 1.2 | 1 | 0.4 | 1 | 1.2 | 1.1 |
| Tapah Catfish | 1 | 1.1 | 1 | 0.3 | 1 | 0.4 | 1.2 | 1.5 | 1 | 1.2 | 1.1 |
| Terek barbel | 1.1 | 1 | 1.2 | 0.6 | 1.3 | 0.8 | 1.3 | 0.7 | 1 | 1.2 | 1.1 |
| Tiger muskellunge | 1.1 | 0.7 | 1.4 | 0.9 | 1.3 | 0.8 | 1.3 | 0.7 | 1.1 | 0.9 | 0.8 |
| African tigerfish | 1 | 1.1 | 1 | 0.3 | 1.3 | 0.8 | 1.3 | 0.7 | 1.1 | 0.9 | 0.8 |
| Trahira | 1 | 1.1 | 1 | 0.3 | 1.3 | 0.8 | 1.3 | 0.7 | 1.1 | 0.9 | 0.8 |
| Trout cichlid | 1 | 1.1 | 1 | 0.3 | 1.1 | 1.2 | 1 | 0.4 | 1.1 | 0.9 | 0.8 |
| Vistula barbel | 1.1 | 1 | 1.2 | 0.6 | 1.3 | 0.8 | 1.3 | 0.7 | 1 | 1.2 | 1.1 |
| Vundu catfish | 1 | 1.1 | 1 | 0.3 | 1 | 0.4 | 1.2 | 1.5 | 1 | 1.2 | 1.1 |
| Walleye | 1.1 | 0.7 | 1.4 | 0.9 | 1.3 | 0.8 | 1.3 | 0.7 | 1.1 | 0.9 | 0.8 |
| Warmouth | 1.3 | 1.2 | 0.9 | 0.4 | 1.1 | 1.2 | 1 | 0.4 | 1.1 | 0.9 | 0.8 |
| West African lungfish | 1 | 1.1 | 1 | 0.3 | 1 | 0.4 | 1.2 | 1.5 | 1 | 1.2 | 1.1 |
| White bass | 1.1 | 1 | 1.2 | 0.6 | 1.3 | 0.8 | 1.3 | 0.7 | 1 | 1.2 | 1.1 |
| White crappie | 1.3 | 1.2 | 0.9 | 0.4 | 1.3 | 0.8 | 1.3 | 0.7 | 1.1 | 0.9 | 0.8 |
| White sturgeon | 1.1 | 1 | 1.2 | 0.6 | 1 | 0.4 | 1.2 | 1.5 | 1 | 1 | 1 |
| Turkey moray | 1 | 1.2 | 1.1 | 0.3 | 1 | 0.4 | 1.2 | 1.5 | 1 | 1 | 1 |
| White-spotted puffer | 1 | 1.2 | 1.1 | 0.5 | 1.1 | 1.2 | 1 | 0.4 | 1 | 1 | 1 |
| Wiper (hybrid striped bass) | 1.1 | 1 | 1.2 | 0.6 | 1.3 | 0.8 | 1.3 | 0.7 | 1 | 1.2 | 1.1 |
| Yellow bass | 1.1 | 1 | 1.2 | 0.6 | 1.3 | 0.8 | 1.3 | 0.7 | 1 | 1.2 | 1.1 |
| Yellow perch | 1.1 | 0.7 | 1.4 | 0.9 | 1.3 | 0.8 | 1.3 | 0.7 | 1.1 | 0.9 | 0.8 |

## Fight statistics

- **Pattern** drives run frequency, run length and the gaps between them, plus the signature events (dives, jumps). Full tables in [fight patterns](fishing-mechanics.md#fight-patterns).
- **Strength** sets the load the fish puts on your tackle: `requiredKg = max(0.5, strength × (1 + fightMassKg(weightKg)) × 2)`, where `fightMassKg` is the weight itself up to 20 kg and `20 × (kg / 20)^0.55` above it. A 200 kg fish of strength 1.0 is fought as 70.96 kg and asks for 143.9 kg of line, not 402 — the strongest line in the mod carries 108 kg.
- **Runs** is the baseline count before pattern, size and predator bonuses.
- **Aggression** drives head-shake frequency and tightens the strike-timing window.
- **`base`** is relative density. **0.95 or above means the species lives in every water** (see [community](water-and-conditions.md#every-water-is-its-own)); 0.0 means it is never drawn from the normal pool at all (the koi).
- **`stamina`**, present in every profile, decides how long a fish can keep running: the fatigue it accrues per running tick is divided by `stamina / 0.70` (the table median), clamped to 0.5–1.6. A 0.30-stamina rotan sits on that floor and tires twice as fast as the median; a 1.0-stamina tuna tires 1.43× slower.

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
| Sterlet | burst | 0.8 | 4 | — | 0.35 | — |
| Wild Carp | aggressive | 0.9 | 3 | 1.0 | 0.5 | 17.5 kg @ 0.6 % |
| Mirror Carp | burst | 0.8 | 3 | 0.75 | 0.7 | — |
| Grass Carp | relentless | 1.0 | 3 | 0.92 | 0.5 | — |
| Koi Kohaku | burst | 0.7 | 3 | 0.6 | 0.0 | — |
| Koi Tancho Sanke | burst | 0.7 | 3 | 0.6 | 0.0 | — |
| Koi Showa Sanke | burst | 0.7 | 3 | 0.6 | 0.0 | — |
| Koi Asagi | burst | 0.7 | 3 | 0.6 | 0.0 | — |
| Koi Bekko | burst | 0.7 | 3 | 0.6 | 0.0 | — |
| Bluegill | steady | 0.3 | 1 | 0.5 | 1.1 | — |
| Largemouth bass | aggressive | 0.8 | 3 | 0.9 | 0.8 | — |
| Rainbow trout | burst | 0.8 | 4 | 0.85 | 0.65 | — |
| Channel catfish | burst | 0.85 | 3 | 0.7 | 0.55 | — |
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
| Ray | active_then_passive | 0.95 | 2 | 0.2 | 0.5 | — |
| Mahi-mahi | greyhounding | 0.85 | 3 | 0.9 | 0.5 | — |
| Wahoo | burst | 0.9 | 4 | 0.95 | 0.4 | — |
| Yellowfin tuna | sounding | 1.0 | 4 | 0.85 | 0.35 | 140 kg @ 0.6 % |
| Barracuda | burst | 0.85 | 3 | 0.95 | 0.55 | — |
| Blue marlin | greyhounding | 1.0 | 5 | 0.95 | 0.25 | 380 kg @ 0.8 % |
| Sailfish | greyhounding | 0.9 | 5 | 1.0 | 0.35 | — |
| Swordfish | sounding | 1.0 | 4 | 0.8 | 0.25 | — |
| Mako shark | greyhounding | 1.0 | 5 | 1.0 | 0.3 | 390 kg @ 0.4 % |
| Rotan | steady | 0.2 | 1 | 0.5 | 1.2 | — |
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
| Halibut | sounding | 0.9 | 3 | 0.4 | 0.3 | 250 kg @ 0.4 % |
| Common dace | aggressive | 0.3 | 2 | 0.8 | 0.9 | — |
| Volga zander | active_then_passive | 0.55 | 2 | 0.7 | 0.7 | — |
| White-eye bream | active_then_passive | 0.4 | 2 | 0.5 | 0.8 | — |
| Round goby | burst | 0.25 | 2 | 0.7 | 1.0 | — |
| Bluefish | aggressive | 0.8 | 4 | 1 | 0.6 | — |
| Bullseye snakehead | active_then_passive | 0.9 | 2 | 0.85 | 0.6 | — |
| Jack crevalle | relentless | 0.95 | 5 | 0.95 | 0.55 | — |
| Mayan cichlid | burst | 0.45 | 2 | 0.8 | 1 | — |
| Oscar | burst | 0.55 | 2 | 0.85 | 0.9 | — |
| Peacock bass | aggressive | 0.95 | 4 | 0.95 | 0.55 | — |
| Snook | burst | 0.9 | 3 | 0.85 | 0.55 | — |
| Striped bass | relentless | 0.85 | 4 | 0.7 | 0.6 | — |
| Tarpon | greyhounding | 1 | 5 | 0.75 | 0.35 | — |
| Arapaima | greyhounding | 0.95 | 4 | 0.8 | 0.22 | — |
| Beluga sturgeon | sounding | 1.0 | 6 | 0.5 | 0.1 | — |
| Piraiba | relentless | 0.95 | 5 | 0.85 | 0.18 | — |
| Goliath grouper | sounding | 1.0 | 3 | 0.9 | 0.2 | — |
| Bull shark | aggressive | 1.0 | 5 | 1.0 | 0.24 | — |
| Frilled shark | sounding | 0.7 | 3 | 0.55 | 0.06 | — |
| Golden dorado | greyhounding | 0.9 | 5 | 1.0 | 0.38 | — |
| Golden crucian | steady | 0.4 | 2 | 0.3 | 0.85 | — |
| Bitterling | steady | 0.03 | 1 | — | 1.0 | — |
| Sunbleak | steady | 0.02 | 1 | — | 1.0 | — |
| Sculpin | steady | 0.08 | 1 | — | 0.7 | — |
| Tubenose goby | steady | 0.06 | 1 | — | 0.9 | — |
| Kutum | burst | 0.6 | 3 | 0.6 | 0.45 | — |
| Naked Carp | burst | 0.85 | 4 | 0.8 | 0.35 | — |
| Mullet | burst | 0.6 | 3 | 0.5 | 0.85 | — |
| Anglerfish | steady | 0.5 | 1 | 0.2 | 0.22 | — |
| Black marlin | greyhounding | 0.95 | 6 | 1.0 | 0.18 | — |
| Blobfish | steady | 0.15 | 1 | 0.05 | 0.16 | — |
| Bluefin tuna | sounding | 1.0 | 5 | 0.9 | 0.24 | — |
| Loach | steady | 0.1 | 1 | 0.2 | 1.0 | — |
| Whale shark | relentless | 0.15 | 8 | 0.1 | 0.03 | — |
| Nelma | burst | 0.8 | 4 | 0.7 | 0.4 | — |
| Ocean sunfish | steady | 0.35 | 2 | 0.1 | 0.2 | — |
| Pollock | active_then_passive | 0.7 | 2 | 0.5 | 0.75 | — |
| Red piranha | aggressive | 0.65 | 3 | 1.0 | 0.7 | — |
| Tiger shark | relentless | 0.85 | 5 | 0.9 | 0.2 | — |
| Koi carp | burst | 0.7 | 3 | 0.6 | 0.0 | — |
| Linear carp | burst | 0.8 | 3 | 0.75 | 0.45 | — |
| Aba aba | steady | 0.68 | 4 | 0.4 | 0.68 | — |
| Adonis pleco | steady | 0.81 | 3 | 0.2 | 0.74 | — |
| African arowana | burst | 0.75 | 4 | 0.8 | 0.61 | — |
| African knifefish | burst | 0.59 | 2 | 0.7 | 0.84 | — |
| African pike | burst | 0.82 | 3 | 0.95 | 0.7 | — |
| African sharptooth catfish | sounding | 1 | 6 | 0.6 | 0.78 | — |
| Alligator gar | relentless | 1 | 7 | 0.8 | 0.6 | — |
| Angolian walking catfish | sounding | 0.86 | 2 | 0.6 | 0.92 | — |
| Ansorge's dwarf characin | burst | 0.15 | 1 | 0.45 | 1.5 | — |
| Asian arowana | burst | 0.74 | 3 | 0.8 | 0.55 | — |
| Barramundi | greyhounding | 1 | 7 | 0.9 | 0.53 | — |
| Bester | sounding | 1 | 6 | 0.35 | 0.5 | — |
| Black crappie | steady | 0.38 | 2 | 0.6 | 1.14 | — |
| Black drum | relentless | 0.92 | 6 | 0.7 | 0.74 | — |
| Black mahseer | relentless | 0.92 | 5 | 0.45 | 0.64 | — |
| Hybrid crappie | steady | 0.37 | 1 | 0.6 | 1.15 | — |
| Blacktail snapper | burst | 0.44 | 2 | 0.55 | 1.12 | — |
| Blotched upsidedown catfish | steady | 0.35 | 1 | 0.35 | 1.4 | — |
| Blue catfish | sounding | 1 | 6 | 0.55 | 0.73 | — |
| Hybrid tilapia (blue × Mozambique) | steady | 0.51 | 2 | 0.45 | 1.18 | — |
| Blue tilapia | steady | 0.5 | 2 | 0.45 | 1.2 | — |
| Greengill sunfish / Hybrid bluegill | burst | 0.38 | 1 | 0.7 | 1.32 | — |
| Pumpkingill (bluegill × pumpkinseed hybrid) | burst | 0.37 | 1 | 0.7 | 1.34 | — |
| Bluegill × redbreast sunfish hybrid | burst | 0.37 | 1 | 0.7 | 1.34 | — |
| Bluegill × redear sunfish hybrid | burst | 0.38 | 1 | 0.7 | 1.31 | — |
| Bowfin | aggressive | 0.8 | 3 | 0.95 | 0.83 | — |
| Bream × roach hybrid | steady | 0.48 | 2 | 0.25 | 1.18 | — |
| Brook trout × bull trout hybrid | burst | 0.7 | 3 | 0.72 | 0.84 | — |
| Brook trout | burst | 0.72 | 4 | 0.72 | 0.83 | — |
| Bull trout | burst | 0.74 | 4 | 0.72 | 0.82 | — |
| Cameroon suckermouth catfish | steady | 0.35 | 1 | 0.35 | 1.44 | — |
| Catla | relentless | 0.82 | 6 | 0.25 | 0.85 | — |
| Chain pickerel | burst | 0.87 | 3 | 0.95 | 0.76 | — |
| Hybrid catfish (channel × blue catfish) | sounding | 1 | 5 | 0.55 | 0.77 | — |
| Chinese mahseer | relentless | 0.84 | 3 | 0.45 | 0.7 | — |
| Chinook × coho salmon hybrid | greyhounding | 0.95 | 6 | 0.75 | 0.64 | — |
| Chinook × pink salmon hybrid | greyhounding | 0.92 | 5 | 0.75 | 0.66 | — |
| Chinook salmon | greyhounding | 1 | 7 | 0.75 | 0.61 | — |
| Chum salmon | greyhounding | 0.94 | 5 | 0.75 | 0.65 | — |
| Climbing perch | steady | 0.51 | 1 | 0.5 | 1.13 | — |
| Coho salmon | greyhounding | 0.94 | 5 | 0.75 | 0.65 | — |
| Common barbel | relentless | 0.78 | 4 | 0.35 | 0.9 | — |
| F1 hybrid carp | steady | 0.52 | 2 | 0.25 | 1.14 | — |
| Congo knifefish | burst | 0.6 | 2 | 0.7 | 0.82 | — |
| Cornish jack | steady | 0.67 | 3 | 0.4 | 0.69 | — |
| Crimean barbel | relentless | 0.72 | 3 | 0.35 | 0.94 | — |
| Cutbow | greyhounding | 0.93 | 5 | 0.75 | 0.65 | — |
| Cutthroat trout | greyhounding | 0.95 | 5 | 0.75 | 0.64 | — |
| Desert pupfish | burst | 0.1 | 1 | 0.4 | 1.5 | — |
| Devils Hole pupfish | burst | 0.1 | 1 | 0.4 | 0.55 | — |
| Dolly Varden × bull trout hybrid | burst | 0.72 | 4 | 0.72 | 0.83 | — |
| Dolly Varden trout | burst | 0.75 | 4 | 0.72 | 0.81 | — |
| Double-trunk elephant nose | steady | 0.55 | 1 | 0.4 | 0.81 | — |
| Eastern happy | burst | 0.5 | 1 | 0.75 | 1.11 | — |
| Electric catfish | sounding | 0.98 | 5 | 0.6 | 0.81 | — |
| Electric eel | sounding | 0.88 | 4 | 0.6 | 0.65 | — |
| Elephantnose fish | steady | 0.56 | 1 | 0.4 | 0.78 | — |
| Elongate lamprologus | burst | 0.52 | 1 | 0.75 | 1.06 | — |
| Fierce bathybates | burst | 0.53 | 2 | 0.75 | 1.04 | — |
| Flathead catfish | sounding | 1 | 6 | 0.55 | 0.74 | — |
| Flier | burst | 0.37 | 1 | 0.7 | 1.34 | — |
| Florida gar | relentless | 0.95 | 4 | 0.8 | 0.61 | — |
| Frontosa cichlid | burst | 0.53 | 2 | 0.75 | 1.03 | — |
| Giant cichlid | burst | 0.57 | 2 | 0.75 | 0.99 | — |
| Giant featherback | burst | 0.7 | 4 | 0.7 | 0.73 | — |
| Giant freshwater stingray | sounding | 1 | 8 | 0.3 | 0.5 | — |
| Giant gourami | steady | 0.61 | 3 | 0.5 | 1 | — |
| Giant mottled eel | relentless | 0.91 | 5 | 0.6 | 0.64 | — |
| giant snakehead | aggressive | 0.88 | 4 | 0.98 | 0.73 | — |
| Goliath tigerfish | burst | 0.95 | 6 | 0.95 | 0.6 | — |
| Green sunfish | burst | 0.38 | 1 | 0.7 | 1.32 | — |
| Helicopter Catfish | sounding | 1 | 6 | 0.6 | 0.58 | — |
| Himalayan mahseer | relentless | 1 | 8 | 0.45 | 0.6 | — |
| Iridescent shark | sounding | 1 | 6 | 0.6 | 0.58 | — |
| Kaluga-sterlet hybrid | sounding | 1 | 6 | 0.35 | 0.5 | — |
| Kaluga sturgeon | sounding | 1 | 8 | 0.35 | 0.5 | — |
| Kuria labeo | relentless | 0.77 | 5 | 0.25 | 0.88 | — |
| Kuria labeo × catla hybrid | relentless | 0.74 | 4 | 0.25 | 0.9 | — |
| Laced moray | relentless | 0.87 | 4 | 0.85 | 0.7 | — |
| Lake sturgeon | sounding | 1 | 7 | 0.35 | 0.5 | — |
| Lake trout | burst | 0.78 | 5 | 0.72 | 0.79 | — |
| Largemouth × smallmouth bass hybrid | aggressive | 0.76 | 3 | 0.9 | 0.98 | — |
| Largemouth yellowfish | relentless | 0.94 | 6 | 0.45 | 0.64 | — |
| Longnose × alligator gar hybrid | relentless | 1 | 6 | 0.8 | 0.58 | — |
| Longnose gar | relentless | 0.99 | 5 | 0.8 | 0.59 | — |
| Longsnout distichodus | relentless | 0.73 | 2 | 0.3 | 0.89 | — |
| Lutefish | relentless | 0.79 | 4 | 0.3 | 0.84 | — |
| Lyre-tail pleco | steady | 0.8 | 3 | 0.2 | 0.74 | — |
| Malayan mahseer | relentless | 0.92 | 5 | 0.45 | 0.64 | — |
| Map puffer | steady | 0.52 | 2 | 0.6 | 0.9 | — |
| Marbled lungfish | sounding | 0.85 | 4 | 0.65 | 0.69 | — |
| Mekong giant catfish | sounding | 1 | 8 | 0.6 | 0.5 | — |
| Mozambique tilapia | steady | 0.48 | 1 | 0.45 | 1.22 | — |
| Muskellunge | burst | 0.95 | 4 | 0.95 | 0.65 | — |
| Nile bichir | steady | 0.56 | 2 | 0.6 | 0.91 | — |
| Hybrid tilapia | steady | 0.52 | 2 | 0.45 | 1.17 | — |
| Red tilapia / Hybrid tilapia (Nile × Mozambique) | steady | 0.52 | 2 | 0.45 | 1.17 | — |
| Nile perch | greyhounding | 1 | 8 | 0.9 | 0.6 | — |
| Nile tilapia | steady | 0.52 | 2 | 0.45 | 1.17 | — |
| Northern pike | burst | 0.95 | 4 | 0.95 | 0.72 | — |
| Ocellaris clownfish | burst | 0.4 | 1 | 0.55 | 1.26 | — |
| Motoro stingray | sounding | 1 | 7 | 0.3 | 0.5 | — |
| American paddlefish | sounding | 1 | 6 | 0.2 | 0.5 | — |
| Palette surgeonfish | burst | 0.42 | 1 | 0.55 | 1.15 | — |
| Payara | burst | 0.91 | 5 | 0.95 | 0.64 | — |
| Polka-dot squeaker | steady | 0.39 | 1 | 0.35 | 1.2 | — |
| Pumpkinseed | burst | 0.37 | 1 | 0.7 | 1.34 | — |
| Red drum | relentless | 0.92 | 6 | 0.7 | 0.75 | — |
| Red-finned mahseer | relentless | 0.98 | 7 | 0.45 | 0.61 | — |
| Redbreast sunfish | burst | 0.38 | 1 | 0.7 | 1.33 | — |
| Hybrid sunfish (redear × green sunfish) | burst | 0.38 | 1 | 0.7 | 1.31 | — |
| Redear sunfish | burst | 0.41 | 1 | 0.7 | 1.28 | — |
| Redtail catfish | sounding | 1 | 7 | 0.7 | 0.61 | — |
| Reedfish | steady | 0.51 | 1 | 0.6 | 0.98 | — |
| Reticulate knifefish | burst | 0.62 | 2 | 0.7 | 0.79 | — |
| Ripon barbel | relentless | 0.75 | 4 | 0.35 | 0.92 | — |
| Roach × rudd hybrid | steady | 0.46 | 2 | 0.25 | 1.2 | — |
| Rock bass | burst | 0.39 | 1 | 0.7 | 1.31 | — |
| Rohu | relentless | 0.82 | 6 | 0.25 | 0.85 | — |
| Rohu × catla hybrid | relentless | 0.76 | 5 | 0.25 | 0.89 | — |
| Rohu × kuria labeo hybrid | relentless | 0.73 | 4 | 0.25 | 0.91 | — |
| Royal featherback | burst | 0.69 | 4 | 0.7 | 0.74 | — |
| Saddled bichir | steady | 0.57 | 2 | 0.6 | 0.9 | — |
| Salt Creek pupfish | burst | 0.1 | 1 | 0.4 | 1.5 | — |
| Sauger | steady | 0.69 | 3 | 0.72 | 0.9 | — |
| Saugeye | steady | 0.71 | 3 | 0.72 | 0.89 | — |
| Semutundu | sounding | 1 | 5 | 0.6 | 0.79 | — |
| Senegal bichir | steady | 0.53 | 2 | 0.6 | 0.94 | — |
| Short-tailed river stingray | sounding | 1 | 8 | 0.3 | 0.6 | — |
| Shovelnose sturgeon | sounding | 0.98 | 4 | 0.35 | 0.5 | — |
| Silver catfish | sounding | 0.89 | 2 | 0.6 | 0.89 | — |
| Sixbar distichodus | relentless | 0.77 | 3 | 0.3 | 0.86 | — |
| Smallmouth bass | aggressive | 0.76 | 3 | 0.9 | 0.98 | — |
| Meanmouth bass | aggressive | 0.75 | 3 | 0.9 | 0.99 | — |
| Sockeye salmon | greyhounding | 0.91 | 4 | 0.75 | 0.66 | — |
| Splake | burst | 0.72 | 4 | 0.72 | 0.83 | — |
| Spotted bass | aggressive | 0.76 | 3 | 0.9 | 0.99 | — |
| Spotted gar | relentless | 0.92 | 4 | 0.8 | 0.63 | — |
| Spotted seatrout | relentless | 0.84 | 4 | 0.7 | 0.79 | — |
| Starry puffer | steady | 0.59 | 3 | 0.6 | 0.85 | — |
| Tambaqui | relentless | 0.86 | 6 | 0.3 | 0.79 | — |
| Tapah Catfish | sounding | 1 | 7 | 0.6 | 0.56 | — |
| Terek barbel | relentless | 0.69 | 2 | 0.35 | 0.98 | — |
| Tiger muskellunge | burst | 0.94 | 4 | 0.95 | 0.72 | — |
| African tigerfish | burst | 0.93 | 5 | 0.95 | 0.63 | — |
| Trahira | burst | 0.86 | 3 | 0.95 | 0.67 | — |
| Trout cichlid | burst | 0.52 | 1 | 0.75 | 1.06 | — |
| Vistula barbel | relentless | 0.69 | 2 | 0.35 | 0.98 | — |
| Vundu catfish | sounding | 1 | 6 | 0.6 | 0.78 | — |
| Walleye | steady | 0.73 | 3 | 0.72 | 0.87 | — |
| Warmouth | burst | 0.38 | 1 | 0.7 | 1.32 | — |
| West African lungfish | sounding | 0.79 | 3 | 0.65 | 0.72 | — |
| White bass | relentless | 0.72 | 3 | 0.85 | 0.95 | — |
| White crappie | steady | 0.37 | 2 | 0.6 | 1.15 | — |
| White sturgeon | sounding | 1 | 8 | 0.35 | 0.5 | — |
| Turkey moray | relentless | 0.8 | 3 | 0.85 | 0.67 | — |
| White-spotted puffer | steady | 0.5 | 2 | 0.6 | 0.92 | — |
| Wiper (hybrid striped bass) | relentless | 0.77 | 4 | 0.85 | 0.91 | — |
| Yellow bass | relentless | 0.7 | 2 | 0.85 | 0.98 | — |
| Yellow perch | steady | 0.67 | 2 | 0.72 | 0.92 | — |

## See also

- [Species](species.md) · [Water and conditions](water-and-conditions.md) · [Fishing mechanics](fishing-mechanics.md)
