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
2. **[done] Move sources → `common`** + convert all 8 registries (items, blocks, block-entities, menus,
   sounds, recipes, POI/profession, creative tab) to Architectury `DeferredRegister`/`RegistrySupplier`,
   bound via `ModRegistries.init()` (BLOCKS before ITEMS). `IForgeMenuType.create` → `MenuRegistry.ofExtended`.
   Common still won't compile — Loom builds the whole module, so green only comes after ALL 36 Forge-coupled
   files are done. Remaining Forge surface (grep `net.minecraftforge`): network(7), item(6), client(4),
   block(3), jei(2), config, command, event, quest, and the villager **trades event** in ModVillagers.
3. **[done — server events] ** `ModEvents` → Architectury events (`ReloadListenerRegistry`, `TickEvent.PLAYER_POST`,
   `PlayerEvent.PLAYER_QUIT`, `BlockEvent.BREAK`) + mob-bait via `LootEvent.MODIFY_LOOT_TABLE`; `JournalCommand`
   → `CommandRegistrationEvent`; villager trades → `@ExpectPlatform VillagerTradeRegistry` (Forge
   `VillagerTradesEvent` ↔ Fabric `TradeOfferHelper`); `ForgeConfigSpec` → plain static config. Forge-import
   surface 36→25. (Client HUD/render events move with Stage 5.)
4. **[done — data] `getPersistentData` → `PlayerData`** (overworld `SavedData` by UUID; JournalData/QuestData/
   AnglerSkills/JournalCommand). Networking (`SimpleChannel` → Architectury `NetworkManager`) is DEFERRED into
   Stage 5: 4 of the 6 packets are S2C and their handlers call the client screens/HUD, so the net layer is
   converted together with those client classes to avoid double work.
5. **[done] Client + networking.**
   - 5a: `SimpleChannel` → Architectury `NetworkManager` (S2C routed to client via `EnvExecutor.runInEnv`).
   - 5b: items/blocks/menus/recipes/season off Forge (`ForgeRegistries`→`BuiltInRegistries`,
     `NetworkHooks.openScreen`→`MenuRegistry.openExtendedMenu`, `DistExecutor`→`EnvExecutor`).
   - 5c: the CLIENT de-Forge — **`common` now compiles green on both loaders.** A single common `ClientInit`
     (called from `RiverFishingForge` on the client dist / `RiverFishingFabricClient`) wires: screens via
     `MenuRegistry.registerScreenFactory`, BER via `BlockEntityRendererRegistry`, HUD via
     `ClientGuiEvent.RENDER_HUD`, disconnect via `ClientPlayerEvent.CLIENT_PLAYER_QUIT`, the `/rfrod` command
     via `ClientCommandRegistrationEvent`. The three hooks Architectury doesn't wrap ride a client
     `@ExpectPlatform ClientPlatform`: **item BEWLR** (Forge `IClientItemExtensions` via `RodItem`/`FishItem`
     client mixins ↔ Fabric `BuiltinItemRendererRegistry`), **extra models** (Forge
     `ModelEvent.RegisterAdditional` ↔ Fabric `ModelLoadingPlugin`), **in-world line** (Forge
     `RenderLevelStageEvent` ↔ Fabric `WorldRenderEvents.AFTER_TRANSLUCENT`), plus `bakedModel(loc)`
     (Forge overload ↔ Fabric `FabricBakedModelManager`). Koi release → shared `ItemEntityMixin`
     (`riverfishing.mixins.json`, referenced by both loaders). JEI moved to the **forge** module
     (blamejared maven, `modCompileOnly` api). Latent common bugs fixed along the way: `javax.annotation`
     JSR-305 dep, `LootEvent.MODIFY_LOOT_TABLE` 4-arg lambda, `CreativeModeTab.builder()`→
     `CreativeTabRegistry.create`, redundant `instanceof`, and Forge-only `onDataPacket` overrides removed
     (vanilla client calls `load(tag)` itself). **`./gradlew build` → both loader jars.**
6. Wire `mods.toml` + `fabric.mod.json` fully; shared data (recipes/tags/lang) in `common`; **forge JEI [done]**.
7. Run both dev clients (`:forge:runClient`, `:fabric:runClient`); fix runtime issues; then REI/EMI on Fabric.

## Build
```powershell
$env:JAVA_HOME = "C:\Users\<you>\AppData\Roaming\PrismLauncher\java\java-runtime-gamma"
.\gradlew.bat build            # both loaders
.\gradlew.bat :fabric:runClient
.\gradlew.bat :forge:runClient
```
> First run downloads MC + Forge + Fabric and decompiles Minecraft twice — expect a long initial setup.
