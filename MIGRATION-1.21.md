# MC 1.20.1 → 1.21.1 migration (NeoForge + Fabric)

Branch `mc-1.21.1`, forked from `multiloader-migration` (the shippable 1.20.1 Forge+Fabric build).
Loaders: **NeoForge + Fabric** (Architectury dropped LexForge after 1.20.4). Java **21**.

## Toolchain (all bumped)
- MC `1.21.1`, Java 21 (PrismLauncher `java-runtime-delta`), `enabled_platforms=fabric,neoforge`
- `dev.architectury.loom` 1.7-SNAPSHOT (→1.7.435), architectury-plugin 3.4-SNAPSHOT (→3.4.164)
- architectury API `13.0.8`, fabric-loader `0.16.9`, fabric-api `0.115.6+1.21.1`, neoforge `21.1.90`
- JEI `19.21.0.247` (jei-1.21.1-neoforge)

## Stages
1. **[done] Toolchain scaffold** — versions, Java 21, `forge`→`neoforge` module (`neoForge()`, `loom.platform=neoforge`,
   `net.neoforged:neoforge`, `architectury-neoforge`, `transformProductionNeoForge`). `./gradlew :neoforge:dependencies`
   resolves + provisions MC 1.21.1. Java does NOT compile yet — that's the rest.
2. **neoforge platform module** — `META-INF/neoforge.mods.toml` (new TOML shape); `RiverFishingNeoForge` (`@Mod` ctor takes
   `IEventBus modBus, ModContainer`); fix `package com.riverfishing.forge`→`neoforge` decls; PlatformHelperImpl /
   VillagerTradeRegistryImpl / ClientPlatformImpl → NeoForge APIs; mixin configs via neoforge.mods.toml `[[mixins]]`.
3. **Data Components (biggest)** — item NBT is gone. `stack.getTag/getOrCreateTag/hasTag` → `DataComponents.CUSTOM_DATA`
   (`CustomData`): read `stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag()`, write
   `CustomData.update(DataComponents.CUSTOM_DATA, stack, tag->…)` / `stack.set(...)`. Migrate RodData, RigData, FishItem
   (species/weight/length/legal/trophy/grade/release), RodItem, RodData depth, ReelItem/LineItem readers, etc.
4. **Items / registries** — `Item.Properties` now needs an id (`.setId`/registry key via DeferredRegister handles it);
   `FoodProperties.Builder` (saturation is absolute, `saturationMod`→`saturationModifier`); CreativeTab/MenuType via arch.
5. **Trades** — `MerchantOffer(ItemCost, Optional<ItemCost>, ItemStack result, …)`; `VillagerTrades.ItemListing` unchanged
   shape but `ItemCost`. Rewrite ModVillagers `sell/buyPrime`.
6. **Recipes** — `CustomRecipe` ctor drops the ResourceLocation; `RecipeSerializer` = `MapCodec` + `StreamCodec`;
   `CraftingInput` replaces `CraftingContainer`. Rewrite OilCakeRecipe + ModRecipes. Recipe JSON result `{"id":…,"count":…}`.
7. **Networking** — Architectury NetworkManager 1.21 uses `CustomPacketPayload` + `StreamCodec`. Rewrite ModNetwork + 6 packets.
8. **Events / commands / loot / config** — Architectury 1.21 event signatures; `LootEvent`; config holder.
9. **Client** — BEWLR renderer signatures, `GuiGraphics`/`RenderType`, screens, ClientInit; **NeoForge has
   `RegisterClientExtensionsEvent`** → replace RodItem/FishItem BEWLR mixins with the event (cleaner). Model/render-type seams.
10. **Data pack JSON** — recipe/advancement/loot format bumps (fish profiles + lang are fine).
11. **Mixins** — recheck PoiTypes invoker on 1.21 (Fabric profession fix); ItemEntity koi; refmaps.
12. **Build + `:neoforge:runClient` / `:fabric:runClient`**, fix runtime.

## Build
```powershell
$env:JAVA_HOME = "C:\Users\<you>\AppData\Roaming\PrismLauncher\java\java-runtime-delta"   # JDK 21
.\gradlew.bat build
```
