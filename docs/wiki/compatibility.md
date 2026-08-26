# Compatibility

The exact versions River Fishing **0.8.0** is built and played against — not "the latest", the jars that were actually in the instance.

## Required

| Minecraft | Loader | Loader version | Architectury API | Fabric API |
| --- | --- | --- | --- | --- |
| 1.20.1 | Fabric | 0.16+ | 9.2.14 | 0.92.2+1.20.1 |
| 1.20.1 | Forge | 47.3.0 | 9.2.14 | — |
| 1.21.1 | Fabric | 0.16+ | 13.0.8 | 0.115.6+1.21.1 |
| 1.21.1 | NeoForge | 21.1.235 | 13.0.8 | — |
| 26.1.2 | Fabric | 0.19.3 | 20.0.8 | 0.154.2+26.1.2 |
| 26.1.2 | NeoForge | 26.1.2.78 | 20.0.8 | — |
| 26.2 | Fabric | 0.19.3 | 21.0.5 | 0.155.2+26.2 |
| 26.2 | NeoForge | 26.2.0.28-beta | 21.0.5 | — |

Architectury API is the one hard requirement on every build. On Fabric, Fabric API as well.

## Optional: Serene Seasons and Biomes O' Plenty

Both are recommended, neither is required. These are the versions the mod is tested with:

| Minecraft / loader | Biomes O' Plenty | Serene Seasons | TerraBlender | GlitchCore |
| --- | --- | --- | --- | --- |
| 1.20.1 Forge | 19.0.0.96 | 9.1.0.3 | 3.0.1.10 | 0.0.1.1 |
| 1.21.1 Fabric | 21.1.0.14 | 10.1.0.1 | 4.1.0.8 | 2.1.0.2 |
| 1.21.1 NeoForge | 21.1.0.14 | 10.1.0.3 | 4.1.0.8 | 2.1.0.2 |
| 26.1.2 Fabric / NeoForge | 26.1.2.0.22 | 26.1.2.0.4 | 26.1.2.0.3 | 26.1.2.0.2 |
| 26.2 NeoForge | 26.2.0.0.26 | 26.1.2.0.4 † | 26.2.0.0.2 | 26.2.0.0.0 |
| 26.2 Fabric | 26.2.0.0.26 | not run † | 26.2.0.0.2 | 26.2.0.0.0 |

† Serene Seasons has **no 26.2 build yet**. The 26.1.2 jar is what runs on 26.2 NeoForge here; on 26.2 Fabric it stayed switched off during testing, so treat seasons on 26.2 Fabric as unsupported until Serene Seasons ships for 26.2.

## Why the conflicts on 26.2 are not River Fishing's

River Fishing has **no version bound on either mod** and no compiled link to them:

- **Serene Seasons** is reached purely by reflection, through `SeasonHelper.getSeasonState`. Present and answering → the seasonal bite is live. Absent, or an API that moved → the season factor is **1.0 for every fish** and the game carries on. It cannot crash on a version mismatch, because there is nothing to mismatch.
- **Biomes O' Plenty** is not referenced in the mod's code at all. Habitats are read from **biome tags**, so any BOP version works and a BOP biome joins a habitat the moment its tag does.

What genuinely conflicts on 26.2 is **BOP and Serene Seasons against their own dependencies**: both need TerraBlender and GlitchCore from the *same* family. A 26.1.2 TerraBlender under a 26.2 Biomes O' Plenty is the usual cause — the table above keeps every row inside one family for exactly that reason.

## Also integrates with

Farmer's Delight, Jade, JEI — all optional, all soft, no version bound.

---

[← Back to the wiki index](README.md)
