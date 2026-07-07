# Emits crafting recipes for River Fishing tackle (#6). ASCII-only, re-runnable.
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$recDir = Join-Path $root "src\main\resources\data\riverfishing\recipes"
New-Item -ItemType Directory -Force -Path $recDir | Out-Null

function Shapeless($name, $items, $result, $count) {
    $ings = ($items | ForEach-Object { "    { ""item"": ""$_"" }" }) -join ",`n"
    $json = "{`n  ""type"": ""minecraft:crafting_shapeless"",`n  ""ingredients"": [`n$ings`n  ],`n  ""result"": { ""item"": ""$result"", ""count"": $count }`n}`n"
    [System.IO.File]::WriteAllText((Join-Path $recDir "$name.json"), $json, (New-Object System.Text.UTF8Encoding $false))
}

$I = "minecraft:iron_ingot"; $N = "minecraft:iron_nugget"; $S = "minecraft:string"
$R = "minecraft:redstone"; $C = "minecraft:copper_ingot"; $B = "minecraft:bamboo"
$SL = "minecraft:slime_ball"

# ----- Rods -----
Shapeless "bamboo_rod"     @($B,$B,$B,$S)                  "riverfishing:bamboo_rod" 1
Shapeless "pole_rod"       @($B,$B,$S,$S,$N)               "riverfishing:pole_rod" 1
Shapeless "ultralight_rod" @($I,$I,$S,$S,$N)               "riverfishing:ultralight_rod" 1
Shapeless "spinning_rod"   @($I,$I,$I,$S,$S,$N)            "riverfishing:spinning_rod" 1
Shapeless "feeder_rod"     @($I,$I,$I,$I,$S,$S)            "riverfishing:feeder_rod" 1
Shapeless "bottom_rod"     @($I,$I,$I,$I,$I,$S,$S)         "riverfishing:bottom_rod" 1
Shapeless "carp_rod"       @($I,$I,$I,$I,$I,$I,$S,$S,"minecraft:amethyst_shard") "riverfishing:carp_rod" 1

# ----- Reels -----
Shapeless "reel_1000" @($I,$I,$R)            "riverfishing:reel_1000" 1
Shapeless "reel_2000" @($I,$I,$R,$N)         "riverfishing:reel_2000" 1
Shapeless "reel_3000" @($I,$I,$I,$R,$R)      "riverfishing:reel_3000" 1
Shapeless "reel_4000" @($I,$I,$I,$R,$R,$N)   "riverfishing:reel_4000" 1
Shapeless "reel_5000" @($I,$I,$I,$I,$R,$R,$C)    "riverfishing:reel_5000" 1
Shapeless "reel_6000" @($I,$I,$I,$I,$R,$R,$C,$C) "riverfishing:reel_6000" 1
Shapeless "reel_7000" @($I,$I,$I,$I,$I,$R,$R,$C,$C) "riverfishing:reel_7000" 1

# ----- Lines (thick mono only; thin/braid/fluoro are villager-only per GDD) -----
Shapeless "line_mono_025" @($S,$S,$S,$SL)    "riverfishing:line_mono_025" 1
Shapeless "line_mono_040" @($S,$S,$SL,$SL)   "riverfishing:line_mono_040" 1

# ----- Rigs (hardware only; hooks/bait are loaded into the rig via its GUI) -----
Shapeless "rig_float"       @("riverfishing:float",$S,$N) "riverfishing:rig_float" 1
Shapeless "rig_feeder"      @($N,$N,$N,$S)                "riverfishing:rig_feeder" 1
Shapeless "rig_flat_feeder" @($N,$N,$N,$N,$S)             "riverfishing:rig_flat_feeder" 1
Shapeless "rig_ground"      @($S,$S,$N)                   "riverfishing:rig_ground" 1
Shapeless "rig_grusha"      @($N,$N,$N,$S,$S)             "riverfishing:rig_grusha" 1
Shapeless "rig_carp"        @($N,$N,$S,$SL)               "riverfishing:rig_carp" 1
Shapeless "rig_predator"    @("riverfishing:leader",$N)   "riverfishing:rig_predator" 1
Shapeless "rig_catfish"     @("riverfishing:leader",$N,$N,$S) "riverfishing:rig_catfish" 1

# ----- Leaders -----
Shapeless "leader"          @($S,$N)                      "riverfishing:leader" 1
Shapeless "leader_fluoro"   @($S,"minecraft:prismarine_shard") "riverfishing:leader_fluoro" 1
Shapeless "leader_titanium" @($S,$I)                      "riverfishing:leader_titanium" 1

# ----- Keepnets (садок, by total weight) -----
Shapeless "keepnet_5"  @($S,$S,$S,$S,$N)                  "riverfishing:keepnet_5" 1
Shapeless "keepnet_10" @($S,$S,$S,$S,$S,$S,$N,$N)         "riverfishing:keepnet_10" 1
Shapeless "keepnet_20" @($S,$S,$S,$S,$S,$S,$S,$S,$I)      "riverfishing:keepnet_20" 1

# ----- Float + baits -----
Shapeless "float"  @($B,"minecraft:feather")              "riverfishing:float" 1
Shapeless "boilie" @("minecraft:wheat","minecraft:wheat","minecraft:egg","minecraft:sugar") "riverfishing:boilie" 4
Shapeless "dough"  @("minecraft:wheat","minecraft:water_bucket") "riverfishing:dough" 1

# ----- Records -----
Shapeless "fishing_journal" @("minecraft:book","riverfishing:hook_12","minecraft:leather") "riverfishing:fishing_journal" 1

# ----- Fisherman workstation (§8) -----
Shapeless "fishing_stall" @("minecraft:barrel",$S,$S,$I) "riverfishing:fishing_stall" 1

# ----- Trophy stand (§15.5) -----
Shapeless "trophy_stand" @("minecraft:oak_planks","minecraft:oak_planks","minecraft:glass",$N) "riverfishing:trophy_stand" 1

Write-Output "Wrote tackle recipes to $recDir"
