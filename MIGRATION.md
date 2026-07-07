# Multi-loader migration (Forge 1.20.1 → Architectury common/forge/fabric)

This branch (`multiloader-migration`) splits the single-project Forge mod into an **Architectury**
multi-project. The working single-loader Forge build is preserved on **`forge-1.20.1`**.

## Layout
```
common/   platform-neutral game logic, items, blocks, engine, GUI, data
  └ platform/   @ExpectPlatform seams (per-loader impls in forge/ + fabric/)
forge/    @Mod bootstrap, Forge @ExpectPlatform impls, JEI plugin (forge-only)
fabric/   ModInitializer + fabric.mod.json, Fabric @ExpectPlatform impls
src/      ← PARKED legacy Forge sources; ported into common/ stage by stage, then deleted
```

## Decisions (agreed)
- **Player data:** the Forge-only `player.getPersistentData()` (journal / quests / skills) is rewritten
  to a level `SavedData` keyed by player UUID — works identically on both loaders, no extra dependency.
- **JEI:** the "how to catch" category stays a **forge-only** extra; Fabric gets REI/EMI in a later stage.
- **Mappings:** official Mojang mappings across all three modules (no name translation).

## Stages (each ends on a green build before the next)
1. **[done] Scaffold** — Architectury Gradle multi-project, entry points, `@ExpectPlatform` seam,
   `fabric.mod.json` + `mods.toml`. Empty skeleton builds on both loaders.
2. Move sources → `common`; convert `DeferredRegister` (items/blocks/BE/menus/villager) to Architectury
   `DeferredRegister`.
3. Abstract entry point + events (worm/mob drops, villager trades, commands) + client HUD/render events.
4. Networking → Architectury `NetworkManager`; `getPersistentData` → `SavedData`.
5. Client: BEWLR renderers (fish/rod), aquarium BER, screens, cast-bar / float-timing HUD, in-world line.
6. Wire `mods.toml` + `fabric.mod.json` fully; shared data (recipes/tags/lang) in `common`; forge JEI.
7. Run both dev clients; fix; then REI/EMI on Fabric.

## Build
```powershell
$env:JAVA_HOME = "C:\Users\<you>\AppData\Roaming\PrismLauncher\java\java-runtime-gamma"
.\gradlew.bat build            # both loaders
.\gradlew.bat :fabric:runClient
.\gradlew.bat :forge:runClient
```
> First run downloads MC + Forge + Fabric and decompiles Minecraft twice — expect a long initial setup.
