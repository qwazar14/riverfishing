# Species

Seventy-nine species. Every number on this page comes from that species' profile in `data/riverfishing/fish_profiles/`, which is fully datapack-overridable — see [`docs/FISH_PROFILES.md`](../FISH_PROFILES.md) for the schema.

Companion page: **[Species reference](species-reference.md)** holds the habitat gates, the season / time / weather tables and the fight statistics.

## How to read this

- **Weight (min – max)** is the species' whole possible range. **Median catch** is the profile's `mean`, and it really is the median — half your fish of that species come in under it. See [the weight roll](fishing-mechanics.md#weight).
- **Water bodies** lists every type the species lives in with its presence factor. A type not listed has a factor of 0 and the fish is **never** there.
- **Level** is `min_angler_level`. It is a soft gate: each level you are short multiplies the fish's bite weight by 0.6, floored at 3 %. A novice can fluke a trophy with the right kit in the right place — just rarely.
- **Best baits** are scored 0 to ~1.2. The engine takes the single best-scoring bait on your rig. A bait not listed scores 0, and **no listed bait on the rig means the fish will not take at all**.
- Bait ids map to items as listed in [Rigs and baits](rigs-and-baits.md#natural-baits): `pearl_barley` = Pearl Barley, `bread` = Bread Crumb, `silicone` = Soft Plastic, `jig` = Soft Jig, `mormyshka` = Ice Jig, `fish_strip` = Fish strip, `livebait` = Live Bait.

## All species

| # | Species | Item id | Weight (min – max) | Median catch | Length | Water bodies (presence factor) | Level |
|---|---|---|---|---|---|---|---|
| 1 | <img src="../../common/src/main/resources/assets/riverfishing/textures/item/fish/bream.png" width="28" alt=""> Bream | `bream` | 300 g – 4 kg | 900 g | 25–55 cm | lake 1.1, river 1.0, pond 0.9, swamp 0.4 | — |
| 2 | <img src="../../common/src/main/resources/assets/riverfishing/textures/item/fish/crucian_carp.png" width="28" alt=""> Crucian Carp | `crucian_carp` | 50 g – 1.5 kg | 250 g | 10–38 cm | pond 1.2, swamp 1.1, lake 1.0, river 0.5, puddle 0.3 | — |
| 3 | <img src="../../common/src/main/resources/assets/riverfishing/textures/item/fish/roach.png" width="28" alt=""> Roach | `roach` | 50 g – 1 kg | 120 g | 10–40 cm | river 1.0, lake 1.0, pond 0.7, swamp 0.4 | — |
| 4 | <img src="../../common/src/main/resources/assets/riverfishing/textures/item/fish/rudd.png" width="28" alt=""> Rudd | `rudd` | 50 g – 1 kg | 110 g | 10–40 cm | lake 1.1, swamp 1.0, pond 0.9, river 0.6 | — |
| 5 | <img src="../../common/src/main/resources/assets/riverfishing/textures/item/fish/white_bream.png" width="28" alt=""> White Bream | `white_bream` | 100 g – 1.2 kg | 300 g | 12–35 cm | river 1.0, lake 1.0, pond 0.6, swamp 0.3 | — |
| 6 | <img src="../../common/src/main/resources/assets/riverfishing/textures/item/fish/carp.png" width="28" alt=""> Carp | `carp` | 1 kg – 15 kg | 3.5 kg | 35–100 cm | lake 1.2, pond 1.1, river 0.6, swamp 0.4 | 3 |
| 7 | <img src="../../common/src/main/resources/assets/riverfishing/textures/item/fish/catfish.png" width="28" alt=""> Catfish | `catfish` | 2 kg – 120 kg | 7 kg | 60–260 cm | river 1.1, lake 1.0, swamp 0.3, pond 0.2 | 6 |
| 8 | <img src="../../common/src/main/resources/assets/riverfishing/textures/item/fish/perch.png" width="28" alt=""> Perch | `perch` | 50 g – 2 kg | 250 g | 10–45 cm | lake 1.1, river 1.0, pond 0.8, swamp 0.5 | — |
| 9 | <img src="../../common/src/main/resources/assets/riverfishing/textures/item/fish/pike.png" width="28" alt=""> Pike | `pike` | 500 g – 10 kg | 2 kg | 35–120 cm | lake 1.1, river 1.0, swamp 0.7, pond 0.6 | 4 |
| 10 | <img src="../../common/src/main/resources/assets/riverfishing/textures/item/fish/zander.png" width="28" alt=""> Zander | `zander` | 500 g – 6 kg | 1.5 kg | 35–90 cm | river 1.1, lake 1.0, pond 0.3, swamp 0.2 | 4 |
| 11 | <img src="../../common/src/main/resources/assets/riverfishing/textures/item/fish/gudgeon.png" width="28" alt=""> Gudgeon | `gudgeon` | 20 g – 150 g | 60 g | 8–20 cm | river 1.2, lake 0.3, pond 0.2 | — |
| 12 | <img src="../../common/src/main/resources/assets/riverfishing/textures/item/fish/ruffe.png" width="28" alt=""> Ruffe | `ruffe` | 20 g – 150 g | 60 g | 8–20 cm | lake 1.1, river 1.0, pond 0.4, swamp 0.2 | — |
| 13 | <img src="../../common/src/main/resources/assets/riverfishing/textures/item/fish/bleak.png" width="28" alt=""> Bleak | `bleak` | 10 g – 100 g | 30 g | 6–18 cm | river 1.1, lake 1.0, pond 0.5 | — |
| 14 | <img src="../../common/src/main/resources/assets/riverfishing/textures/item/fish/ide.png" width="28" alt=""> Ide | `ide` | 300 g – 3 kg | 800 g | 25–60 cm | river 1.2, lake 0.7, pond 0.2, swamp 0.1 | 2 |
| 15 | <img src="../../common/src/main/resources/assets/riverfishing/textures/item/fish/chub.png" width="28" alt=""> Chub | `chub` | 200 g – 4 kg | 700 g | 20–60 cm | river 1.2 | 3 |
| 16 | <img src="../../common/src/main/resources/assets/riverfishing/textures/item/fish/asp.png" width="28" alt=""> Asp | `asp` | 500 g – 8 kg | 2 kg | 30–90 cm | river 1.2 | 5 |
| 17 | <img src="../../common/src/main/resources/assets/riverfishing/textures/item/fish/tench.png" width="28" alt=""> Tench | `tench` | 300 g – 3.5 kg | 800 g | 20–60 cm | pond 1.2, swamp 1.2, lake 1.0, river 0.2 | 2 |
| 18 | <img src="../../common/src/main/resources/assets/riverfishing/textures/item/fish/burbot.png" width="28" alt=""> Burbot | `burbot` | 500 g – 8 kg | 1.5 kg | 30–100 cm | river 1.1, lake 0.9, pond 0.1 | 4 |
| 19 | <img src="../../common/src/main/resources/assets/riverfishing/textures/item/fish/eel.png" width="28" alt=""> Eel | `eel` | 300 g – 4 kg | 900 g | 40–130 cm | lake 1.1, river 0.9, pond 0.6, swamp 0.4 | 5 |
| 20 | <img src="../../common/src/main/resources/assets/riverfishing/textures/item/fish/grayling.png" width="28" alt=""> Grayling | `grayling` | 150 g – 2.5 kg | 500 g | 18–55 cm | river 1.3, lake 0.4 | 3 |
| 21 | <img src="../../common/src/main/resources/assets/riverfishing/textures/item/fish/trout.png" width="28" alt=""> Trout | `trout` | 300 g – 5 kg | 1 kg | 25–80 cm | river 1.2, lake 0.8, pond 0.2 | 5 |
| 22 | <img src="../../common/src/main/resources/assets/riverfishing/textures/item/fish/sterlet.png" width="28" alt=""> Sterlet | `sterlet` | 1 kg – 16 kg | 3 kg | 40–125 cm | river 1.2 | 8 |
| 23 | <img src="../../common/src/main/resources/assets/riverfishing/textures/item/fish/wild_carp.png" width="28" alt=""> Wild Carp | `wild_carp` | 1.5 kg – 18 kg | 4.2 kg | 40–110 cm | river 1.3, lake 0.9, pond 0.5, swamp 0.3 | 4 |
| 24 | <img src="../../common/src/main/resources/assets/riverfishing/textures/item/fish/mirror_carp.png" width="28" alt=""> Mirror Carp | `mirror_carp` | 1 kg – 14 kg | 3.2 kg | 33–95 cm | lake 1.2, pond 1.2, river 0.5, swamp 0.4 | 3 |
| 25 | <img src="../../common/src/main/resources/assets/riverfishing/textures/item/fish/grass_carp.png" width="28" alt=""> Grass Carp | `grass_carp` | 1.5 kg – 25 kg | 5 kg | 40–120 cm | lake 1.3, pond 1.2, river 0.7, swamp 0.6 | 4 |
| 26 | <img src="../../common/src/main/resources/assets/riverfishing/textures/item/fish/carp_koi_kohaku.png" width="28" alt=""> Koi Kohaku | `carp_koi_kohaku` | 800 g – 8 kg | 2.5 kg | 25–90 cm | pond 1.0, lake 1.0, river 0.4 | 3 |
| 27 | <img src="../../common/src/main/resources/assets/riverfishing/textures/item/fish/carp_koi_tancho_sanke.png" width="28" alt=""> Koi Tancho Sanke | `carp_koi_tancho_sanke` | 800 g – 8 kg | 2.5 kg | 25–90 cm | pond 1.0, lake 1.0, river 0.4 | 3 |
| 28 | <img src="../../common/src/main/resources/assets/riverfishing/textures/item/fish/carp_koi_showa_sanke.png" width="28" alt=""> Koi Showa Sanke | `carp_koi_showa_sanke` | 800 g – 8 kg | 2.5 kg | 25–90 cm | pond 1.0, lake 1.0, river 0.4 | 3 |
| 29 | <img src="../../common/src/main/resources/assets/riverfishing/textures/item/fish/carp_koi_asagi.png" width="28" alt=""> Koi Asagi | `carp_koi_asagi` | 800 g – 8 kg | 2.5 kg | 25–90 cm | pond 1.0, lake 1.0, river 0.4 | 3 |
| 30 | <img src="../../common/src/main/resources/assets/riverfishing/textures/item/fish/carp_koi_bekko.png" width="28" alt=""> Koi Bekko | `carp_koi_bekko` | 800 g – 8 kg | 2.5 kg | 25–90 cm | pond 1.0, lake 1.0, river 0.4 | 3 |
| 31 | <img src="../../common/src/main/resources/assets/riverfishing/textures/item/fish/bluegill.png" width="28" alt=""> Bluegill | `bluegill` | 40 g – 800 g | 150 g | 8–35 cm | pond 1.3, lake 1.2, river 0.6, swamp 0.4 | — |
| 32 | <img src="../../common/src/main/resources/assets/riverfishing/textures/item/fish/largemouth_bass.png" width="28" alt=""> Largemouth bass | `largemouth_bass` | 400 g – 8 kg | 1.5 kg | 25–75 cm | lake 1.3, pond 1.1, swamp 0.8, river 0.7 | 3 |
| 33 | <img src="../../common/src/main/resources/assets/riverfishing/textures/item/fish/rainbow_trout.png" width="28" alt=""> Rainbow trout | `rainbow_trout` | 300 g – 6 kg | 1.1 kg | 25–85 cm | river 1.3, lake 0.9, pond 0.2 | 4 |
| 34 | <img src="../../common/src/main/resources/assets/riverfishing/textures/item/fish/channel_catfish.png" width="28" alt=""> Channel catfish | `channel_catfish` | 800 g – 18 kg | 3.5 kg | 35–110 cm | river 1.2, lake 1.0, pond 0.6, swamp 0.5 | 5 |
| 35 | <img src="../../common/src/main/resources/assets/riverfishing/textures/item/fish/silver_carp.png" width="28" alt=""> Silver carp | `silver_carp` | 2 kg – 25 kg | 6 kg | 50–120 cm | lake 1.3, pond 0.9, river 0.8, swamp 0.2 | 6 |
| 36 | <img src="../../common/src/main/resources/assets/riverfishing/textures/item/fish/sabrefish.png" width="28" alt=""> Sabrefish | `sabrefish` | 150 g – 1.5 kg | 400 g | 20–60 cm | river 1.3, lake 0.8, pond 0.1 | 2 |
| 37 | <img src="../../common/src/main/resources/assets/riverfishing/textures/item/fish/blue_bream.png" width="28" alt=""> Blue bream | `blue_bream` | 150 g – 800 g | 350 g | 15–45 cm | river 1.1, lake 1.0, pond 0.3, swamp 0.2 | 2 |
| 38 | <img src="../../common/src/main/resources/assets/riverfishing/textures/item/fish/mackerel.png" width="28" alt=""> Mackerel | `mackerel` | 300 g – 2 kg | 600 g | 25–60 cm | sea 1.2 | 4 |
| 39 | <img src="../../common/src/main/resources/assets/riverfishing/textures/item/fish/herring.png" width="28" alt=""> Herring | `herring` | 100 g – 600 g | 250 g | 15–40 cm | sea 1.3 | 4 |
| 40 | <img src="../../common/src/main/resources/assets/riverfishing/textures/item/fish/garfish.png" width="28" alt=""> Garfish | `garfish` | 300 g – 1.5 kg | 600 g | 40–95 cm | sea 1.1 | 4 |
| 41 | <img src="../../common/src/main/resources/assets/riverfishing/textures/item/fish/seabass.png" width="28" alt=""> Sea bass | `seabass` | 500 g – 8 kg | 1.5 kg | 30–90 cm | sea 1.2 | 5 |
| 42 | <img src="../../common/src/main/resources/assets/riverfishing/textures/item/fish/flounder.png" width="28" alt=""> Flounder | `flounder` | 300 g – 4 kg | 900 g | 20–60 cm | sea 1.2 | 4 |
| 43 | <img src="../../common/src/main/resources/assets/riverfishing/textures/item/fish/cod.png" width="28" alt=""> Cod | `cod` | 2 kg – 40 kg | 6 kg | 50–150 cm | sea 1.2 | 6 |
| 44 | <img src="../../common/src/main/resources/assets/riverfishing/textures/item/fish/saithe.png" width="28" alt=""> Saithe | `saithe` | 1 kg – 15 kg | 3 kg | 40–110 cm | sea 1.1 | 5 |
| 45 | <img src="../../common/src/main/resources/assets/riverfishing/textures/item/fish/conger.png" width="28" alt=""> Conger eel | `conger` | 3 kg – 60 kg | 9 kg | 80–250 cm | sea 1.1 | 7 |
| 46 | <img src="../../common/src/main/resources/assets/riverfishing/textures/item/fish/ray.png" width="28" alt=""> Ray | `ray` | 2 kg – 50 kg | 8 kg | 40–180 cm | sea 1.1 | 6 |
| 47 | <img src="../../common/src/main/resources/assets/riverfishing/textures/item/fish/mahi.png" width="28" alt=""> Mahi-mahi | `mahi` | 2 kg – 20 kg | 5 kg | 50–160 cm | sea 1.1 | 7 |
| 48 | <img src="../../common/src/main/resources/assets/riverfishing/textures/item/fish/wahoo.png" width="28" alt=""> Wahoo | `wahoo` | 5 kg – 40 kg | 12 kg | 80–210 cm | sea 1.0 | 7 |
| 49 | <img src="../../common/src/main/resources/assets/riverfishing/textures/item/fish/yellowfin_tuna.png" width="28" alt=""> Yellowfin tuna | `yellowfin_tuna` | 10 kg – 150 kg | 30 kg | 90–220 cm | sea 1.0 | 7 |
| 50 | <img src="../../common/src/main/resources/assets/riverfishing/textures/item/fish/barracuda.png" width="28" alt=""> Barracuda | `barracuda` | 2 kg – 20 kg | 6 kg | 60–180 cm | sea 1.1 | 6 |
| 51 | <img src="../../common/src/main/resources/assets/riverfishing/textures/item/fish/blue_marlin.png" width="28" alt=""> Blue marlin | `blue_marlin` | 50 kg – 400 kg | 110 kg | 200–450 cm | sea 1.0 | 7 |
| 52 | <img src="../../common/src/main/resources/assets/riverfishing/textures/item/fish/sailfish.png" width="28" alt=""> Sailfish | `sailfish` | 20 kg – 80 kg | 35 kg | 150–320 cm | sea 1.0 | 7 |
| 53 | <img src="../../common/src/main/resources/assets/riverfishing/textures/item/fish/swordfish.png" width="28" alt=""> Swordfish | `swordfish` | 30 kg – 300 kg | 80 kg | 150–400 cm | sea 1.0 | 7 |
| 54 | <img src="../../common/src/main/resources/assets/riverfishing/textures/item/fish/mako.png" width="28" alt=""> Mako shark | `mako` | 20 kg – 200 kg | 60 kg | 150–380 cm | sea 1.0 | 7 |
| 55 | <img src="../../common/src/main/resources/assets/riverfishing/textures/item/fish/rotan.png" width="28" alt=""> Rotan | `rotan` | 20 g – 600 g | 90 g | 8–35 cm | pond 1.3, swamp 1.3, puddle 1.0, lake 0.4, river 0.2 | — |
| 56 | <img src="../../common/src/main/resources/assets/riverfishing/textures/item/fish/nase.png" width="28" alt=""> Nase | `nase` | 100 g – 1 kg | 400 g | 15–45 cm | river 1.3, lake 0.1 | 2 |
| 57 | <img src="../../common/src/main/resources/assets/riverfishing/textures/item/fish/vimba.png" width="28" alt=""> Vimba bream | `vimba` | 200 g – 1.5 kg | 700 g | 20–50 cm | river 1.2, lake 0.3, sea 0.2 | 3 |
| 58 | <img src="../../common/src/main/resources/assets/riverfishing/textures/item/fish/smelt.png" width="28" alt=""> Smelt | `smelt` | 20 g – 250 g | 60 g | 10–30 cm | sea 1.2, river 0.3, lake 0.2 | 1 |
| 59 | <img src="../../common/src/main/resources/assets/riverfishing/textures/item/fish/whitefish.png" width="28" alt=""> Whitefish | `whitefish` | 300 g – 4 kg | 1 kg | 25–70 cm | lake 1.3, river 0.5, pond 0.1 | 4 |
| 60 | <img src="../../common/src/main/resources/assets/riverfishing/textures/item/fish/char.png" width="28" alt=""> Arctic char | `char` | 300 g – 6 kg | 1.2 kg | 25–85 cm | lake 1.1, river 1.0, sea 0.2, pond 0.1 | 5 |
| 61 | <img src="../../common/src/main/resources/assets/riverfishing/textures/item/fish/lenok.png" width="28" alt=""> Lenok | `lenok` | 500 g – 6 kg | 1.5 kg | 30–90 cm | river 1.2, lake 0.4 | 5 |
| 62 | <img src="../../common/src/main/resources/assets/riverfishing/textures/item/fish/taimen.png" width="28" alt=""> Taimen | `taimen` | 3 kg – 60 kg | 11 kg | 60–180 cm | river 1.3, lake 0.4 | 8 |
| 63 | <img src="../../common/src/main/resources/assets/riverfishing/textures/item/fish/salmon.png" width="28" alt=""> Atlantic salmon | `salmon` | 1.5 kg – 25 kg | 5 kg | 50–130 cm | river 1.1, sea 1.0, lake 0.2 | 6 |
| 64 | <img src="../../common/src/main/resources/assets/riverfishing/textures/item/fish/pink_salmon.png" width="28" alt=""> Pink salmon | `pink_salmon` | 800 g – 3.5 kg | 1.4 kg | 35–70 cm | sea 1.1, river 1.0, lake 0.1 | 3 |
| 65 | <img src="../../common/src/main/resources/assets/riverfishing/textures/item/fish/sturgeon.png" width="28" alt=""> Sturgeon | `sturgeon` | 5 kg – 150 kg | 22 kg | 80–250 cm | river 1.2, lake 0.6, sea 0.3 | 9 |
| 66 | <img src="../../common/src/main/resources/assets/riverfishing/textures/item/fish/halibut.png" width="28" alt=""> Halibut | `halibut` | 2 kg – 200 kg | 18 kg | 50–250 cm | sea 1.2 | 9 |
| 67 | <img src="../../common/src/main/resources/assets/riverfishing/textures/item/fish/common_dace.png" width="28" alt=""> Common dace | `common_dace` | 20 g – 1 kg | 150 g | 15–40 cm | river 1.3, lake 0.2 | — |
| 68 | <img src="../../common/src/main/resources/assets/riverfishing/textures/item/fish/volga_zander.png" width="28" alt=""> Volga zander | `volga_zander` | 100 g – 2 kg | 450 g | 20–40 cm | river 1.3, lake 0.6 | 3 |
| 69 | <img src="../../common/src/main/resources/assets/riverfishing/textures/item/fish/white_eye_bream.png" width="28" alt=""> White-eye bream | `white_eye_bream` | 50 g – 1.3 kg | 300 g | 15–35 cm | river 1.3, lake 0.3 | 2 |
| 70 | <img src="../../common/src/main/resources/assets/riverfishing/textures/item/fish/round_goby.png" width="28" alt=""> Round goby | `round_goby` | 10 g – 380 g | 100 g | 10–35 cm | sea 1.1, river 1.0, lake 0.6, pond 0.2 | — |
| 71 | <img src="../../common/src/main/resources/assets/riverfishing/textures/item/fish/bluefish.png" width="28" alt=""> Bluefish | `bluefish` | 400 g – 14 kg | 2 kg | 30–110 cm | sea 1.2 | 6 |
| 72 | <img src="../../common/src/main/resources/assets/riverfishing/textures/item/fish/bullseye_snakehead.png" width="28" alt=""> Bullseye snakehead | `bullseye_snakehead` | 400 g – 8 kg | 1.5 kg | 30–90 cm | lake 1.2, pond 1.1, river 1, swamp 0.9 | 5 |
| 73 | <img src="../../common/src/main/resources/assets/riverfishing/textures/item/fish/jack_crevalle.png" width="28" alt=""> Jack crevalle | `jack_crevalle` | 800 g – 30 kg | 4.5 kg | 35–120 cm | sea 1.2, river 0.5, swamp 0.3 | 7 |
| 74 | <img src="../../common/src/main/resources/assets/riverfishing/textures/item/fish/mayan_cichlid.png" width="28" alt=""> Mayan cichlid | `mayan_cichlid` | 80 g – 1.2 kg | 300 g | 12–35 cm | lake 1.2, pond 1.1, river 1, swamp 0.9 | 3 |
| 75 | <img src="../../common/src/main/resources/assets/riverfishing/textures/item/fish/oscar.png" width="28" alt=""> Oscar | `oscar` | 150 g – 1.6 kg | 450 g | 15–40 cm | lake 1.2, pond 1.1, river 1, swamp 0.9 | 3 |
| 76 | <img src="../../common/src/main/resources/assets/riverfishing/textures/item/fish/peacock_bass.png" width="28" alt=""> Peacock bass | `peacock_bass` | 300 g – 12 kg | 1.8 kg | 25–75 cm | lake 1.2, pond 1.1, river 1, swamp 0.9 | 5 |
| 77 | <img src="../../common/src/main/resources/assets/riverfishing/textures/item/fish/snook.png" width="28" alt=""> Snook | `snook` | 700 g – 25 kg | 3.5 kg | 35–140 cm | sea 1.2, river 0.5, swamp 0.3 | 7 |
| 78 | <img src="../../common/src/main/resources/assets/riverfishing/textures/item/fish/striped_bass.png" width="28" alt=""> Striped bass | `striped_bass` | 500 g – 35 kg | 4 kg | 30–130 cm | sea 1.2, river 0.5, swamp 0.3 | 6 |
| 79 | <img src="../../common/src/main/resources/assets/riverfishing/textures/item/fish/tarpon.png" width="28" alt=""> Tarpon | `tarpon` | 5 kg – 130 kg | 30 kg | 90–250 cm | sea 1.2, river 0.5, swamp 0.3 | 9 |

## Ideal tackle

Match these and the fish's bite weight climbs sharply — and the bigger the fish, the more sharply. See [the match coefficient](fishing-mechanics.md#match-coefficient-m--your-tackle).

| Species | Best baits (score) | Ideal rods | Ideal rigs | Hook | Reel | Line | Groundbait | Leader |
|---|---|---|---|---|---|---|---|---|
| Bream | maggot 1.0, worm 0.9, pearl_barley 0.8, mormyshka 0.7, corn 0.6, bread 0.4, boilie 0.3 | bottom, feeder | feeder, flat_feeder, float | No.10 ±2 | 4000 ±1000 | braid 0.1 ±0.04 | cake, grain, powder | — |
| Crucian Carp | worm 1.0, dough 0.9, maggot 0.8, corn 0.6, bread 0.5 | feeder, pole | feeder, float | No.12 ±2 | 2000 ±1000 | mono 0.18 ±0.06 | cake, grain | — |
| Roach | maggot 1.0, mormyshka 0.9, bloodworm 0.9, dough 0.7, bread 0.5 | pole, ultralight | float | No.14 ±2 | 2000 ±1000 | mono 0.14 ±0.04 | cake, powder | — |
| Rudd | bread 1.0, dough 0.9, maggot 0.8 | pole | float | No.14 ±2 | 1000 ±1000 | mono 0.14 ±0.04 | cake, powder | — |
| White Bream | maggot 1.0, worm 0.9, bloodworm 0.7 | feeder | feeder, float | No.12 ±2 | 3000 ±1000 | braid 0.1 ±0.04 | cake, powder | — |
| Carp | boilie 1.0, corn 0.8, pea 0.6, pearl_barley 0.5 | carp | carp, flat_feeder | No.6 ±2 | 6000 ±1000 | mono 0.3 ±0.08 | cake, pellet | — |
| Catfish | livebait 1.0, chicken_liver 1.0, jig 0.85, worm 0.7, boilie 0.6 | bottom | catfish, grusha | No.4 ±2 | 7000 ±1000 | braid 0.18 ±0.04 | cake | — |
| Perch | crankbait 1.0, silicone 0.95, mormyshka 0.9, livebait 0.9, spinner 0.9, jig 0.8, popper 0.7, worm 0.6 | spinning, ultralight | predator | No.8 ±3 | 3000 ±1000 | braid 0.1 ±0.04 | — | — |
| Pike | wobbler 1.0, spoon 0.95, livebait 0.9, crankbait 0.9, spinner 0.9, jig 0.85, popper 0.7 | spinning | predator | No.4 ±2 | 3000 ±1000 | braid 0.14 ±0.04 | — | **yes** |
| Zander | silicone 1.0, livebait 0.95, jig 0.95, crankbait 0.85, wobbler 0.8 | spinning | predator | No.4 ±2 | 3000 ±1000 | braid 0.12 ±0.04 | — | **yes** |
| Gudgeon | bloodworm 1.0, mormyshka 0.9, worm 0.9, maggot 0.8 | pole, stick, ultralight | float, primitive | No.16 ±2 | 1000 ±1000 | mono 0.14 ±0.04 | powder | — |
| Ruffe | mormyshka 1.0, worm 1.0, bloodworm 1.0, maggot 0.7 | feeder, pole | feeder, float | No.14 ±2 | 2000 ±1000 | mono 0.14 ±0.04 | powder | — |
| Bleak | maggot 1.0, bread 0.9, mormyshka 0.8, dough 0.8, bloodworm 0.7 | pole, stick, ultralight | float, primitive | No.16 ±2 | 1000 ±1000 | mono 0.14 ±0.04 | powder | — |
| Ide | worm 1.0, popper 0.9, maggot 0.8, corn 0.8, bread 0.7, crankbait 0.7, pea 0.6 | feeder, pole, ultralight | feeder, float | No.10 ±2 | 3000 ±1000 | mono 0.18 ±0.05 | cake, grain | — |
| Chub | popper 1.0, wobbler 0.9, bread 0.8, spinner 0.8, crankbait 0.75, worm 0.7, castmaster 0.7, corn 0.5 | pole, spinning, ultralight | float, predator | No.8 ±3 | 2000 ±1000 | mono 0.16 ±0.05 | cake | — |
| Asp | spoon 1.0, castmaster 0.9, wobbler 0.9, popper 0.85, spinner 0.8, crankbait 0.7 | spinning | predator | No.6 ±2 | 4000 ±1000 | braid 0.12 ±0.04 | — | — |
| Tench | worm 1.0, dough 0.8, corn 0.7, bread 0.6, maggot 0.6 | feeder, pole | feeder, float | No.10 ±2 | 3000 ±1000 | mono 0.2 ±0.05 | cake, grain | — |
| Burbot | livebait 1.0, worm 0.9, chicken_liver 0.9, jig 0.75 | bottom, feeder | feeder, ground | No.6 ±2 | 4000 ±1000 | mono 0.3 ±0.08 | — | — |
| Eel | worm 1.0, livebait 0.8, chicken_liver 0.7, jig 0.7 | bottom, feeder | feeder, ground | No.8 ±2 | 4000 ±1000 | mono 0.25 ±0.06 | — | — |
| Grayling | spinner 0.95, worm 0.9, maggot 0.8, castmaster 0.8, bloodworm 0.7, crankbait 0.6 | ultralight | float, predator | No.12 ±2 | 2000 ±1000 | mono 0.16 ±0.04 | — | — |
| Trout | castmaster 1.0, spinner 0.95, wobbler 0.9, crankbait 0.85, silicone 0.7, worm 0.6 | spinning, ultralight | float, predator | No.8 ±2 | 2000 ±1000 | fluoro 0.2 ±0.05 | — | — |
| Sterlet | worm 1.0, bloodworm 0.7, maggot 0.5 | bottom, carp | catfish, ground | No.6 ±2 | 6000 ±1000 | braid 0.14 ±0.04 | cake | — |
| Wild Carp | boilie 1.0, corn 0.85, pea 0.7, pearl_barley 0.55 | bottom, carp | carp, flat_feeder | No.4 ±2 | 6000 ±1000 | mono 0.3 ±0.07 | cake, pellet | — |
| Mirror Carp | boilie 1.0, corn 0.8, pea 0.6, pearl_barley 0.5 | carp | carp, flat_feeder | No.6 ±2 | 6000 ±1000 | mono 0.3 ±0.08 | cake, pellet | — |
| Grass Carp | corn 1.0, bread 0.9, dough 0.8, pea 0.7, boilie 0.5 | carp, feeder | carp, flat_feeder | No.6 ±2 | 6000 ±1000 | mono 0.3 ±0.08 | cake, pellet | — |
| Koi Kohaku | boilie 1.0, corn 0.8, pea 0.6, bread 0.6 | carp | carp | No.6 ±2 | 6000 ±1000 | mono 0.3 ±0.08 | cake, pellet | — |
| Koi Tancho Sanke | boilie 1.0, corn 0.8, pea 0.6, bread 0.6 | carp | carp | No.6 ±2 | 6000 ±1000 | mono 0.3 ±0.08 | cake, pellet | — |
| Koi Showa Sanke | boilie 1.0, corn 0.8, pea 0.6, bread 0.6 | carp | carp | No.6 ±2 | 6000 ±1000 | mono 0.3 ±0.08 | cake, pellet | — |
| Koi Asagi | boilie 1.0, corn 0.8, pea 0.6, bread 0.6 | carp | carp | No.6 ±2 | 6000 ±1000 | mono 0.3 ±0.08 | cake, pellet | — |
| Koi Bekko | boilie 1.0, corn 0.8, pea 0.6, bread 0.6 | carp | carp | No.6 ±2 | 6000 ±1000 | mono 0.3 ±0.08 | cake, pellet | — |
| Bluegill | worm 1.0, maggot 0.9, bloodworm 0.8, corn 0.5 | bamboo, pole, stick, ultralight | float | No.12 ±3 | 1000 ±1000 | mono 0.12 ±0.05 | grain | — |
| Largemouth bass | popper 1.2, wobbler 1.0, silicone 0.95, jig 0.9, crankbait 0.9, livebait 0.8, spinner 0.7 | spinning, ultralight | predator | No.4 ±3 | 3000 ±1000 | braid 0.16 ±0.05 | — | — |
| Rainbow trout | spinner 1.0, castmaster 0.95, wobbler 0.85, crankbait 0.8, silicone 0.7, worm 0.6 | spinning, ultralight | float, predator | No.8 ±2 | 2000 ±1000 | fluoro 0.18 ±0.05 | — | — |
| Channel catfish | livebait 1.1, chicken_liver 1.0, worm 0.8, maggot 0.6, boilie 0.5 | bottom, carp, feeder | catfish, grusha | No.2 ±2 | 5000 ±1000 | mono 0.35 ±0.08 | pellet | — |
| Silver carp | pearl_barley 0.5, corn 0.4, boilie 0.3 | bottom, carp | carp, flat_feeder | No.6 ±2 | 6000 ±1000 | mono 0.4 ±0.08 | powder | — |
| Sabrefish | castmaster 1.0, maggot 0.9, worm 0.8, spinner 0.8, bloodworm 0.7, silicone 0.6 | feeder, spinning, ultralight | float, predator | No.10 ±3 | 3000 ±1000 | mono 0.16 ±0.05 | — | — |
| Blue bream | bloodworm 1.0, maggot 0.85, worm 0.7, pearl_barley 0.5 | bamboo, feeder, pole | flat_feeder, float | No.12 ±3 | 3000 ±1000 | mono 0.14 ±0.05 | grain, powder | — |
| Mackerel | castmaster 1.0, spinner 0.9, silicone 0.8, fish_strip 0.6 | sea_spin, spinning | predator | No.6 ±3 | 5000 ±2000 | braid 0.2 ±0.06 | — | — |
| Herring | fish_strip 0.8, bloodworm 0.7, maggot 0.6, castmaster 0.5 | sea_spin, spinning, surf | float, predator | No.10 ±3 | 5000 ±2000 | mono 0.18 ±0.06 | — | — |
| Garfish | fish_strip 1.0, spinner 0.7, castmaster 0.7, silicone 0.5 | sea_spin | predator | No.8 ±3 | 5000 ±2000 | mono 0.2 ±0.06 | — | — |
| Sea bass | wobbler 1.0, silicone 0.95, livebait 0.9, popper 0.8, fish_strip 0.7 | sea_spin, surf | predator | No.4 ±2 | 6000 ±2000 | braid 0.25 ±0.06 | — | — |
| Flounder | fish_strip 1.0, worm 0.9, maggot 0.5 | boat, bottom, surf | catfish, grusha | No.6 ±2 | 8000 ±2000 | mono 0.3 ±0.08 | — | — |
| Cod | fish_strip 1.0, jig 0.95, livebait 0.9, silicone 0.7 | boat, surf | catfish, grusha | No.2 ±2 | 10000 ±2000 | braid 0.3 ±0.08 | — | — |
| Saithe | jig 1.0, silicone 0.8, fish_strip 0.7, castmaster 0.7 | boat, sea_spin | predator | No.4 ±2 | 10000 ±2000 | braid 0.25 ±0.06 | — | — |
| Conger eel | fish_strip 1.0, livebait 1.0, worm 0.4 | boat, surf | catfish | No.1 ±2 | 12000 ±2000 | mono 0.5 ±0.1 | — | **yes** |
| Ray | fish_strip 1.0, worm 0.7, livebait 0.6 | boat, surf | catfish, grusha | No.2 ±2 | 12000 ±2000 | mono 0.5 ±0.1 | — | — |
| Mahi-mahi | wobbler 1.0, popper 0.9, silicone 0.8, fish_strip 0.6 | sea_spin, trolling | predator | No.2 ±2 | 10000 ±2000 | braid 0.3 ±0.08 | — | — |
| Wahoo | wobbler 1.0, castmaster 0.8, silicone 0.7 | trolling | predator | No.1 ±2 | 12000 ±2000 | braid 0.4 ±0.08 | — | **yes** |
| Yellowfin tuna | wobbler 0.9, livebait 0.9, fish_strip 0.8, silicone 0.7 | boat, trolling | predator | No.1 ±1 | 14000 ±2000 | braid 0.4 ±0.08 | — | — |
| Barracuda | wobbler 1.0, silicone 0.9, spinner 0.7, fish_strip 0.6 | sea_spin, trolling | predator | No.2 ±2 | 9000 ±2000 | braid 0.3 ±0.08 | — | **yes** |
| Blue marlin | wobbler 1.0, silicone 0.6 | trolling | predator | No.1 ±1 | 14000 ±1000 | braid 0.4 ±0.06 | — | — |
| Sailfish | wobbler 1.0, popper 0.8, silicone 0.7 | sea_spin, trolling | predator | No.1 ±2 | 12000 ±2000 | braid 0.3 ±0.08 | — | — |
| Swordfish | livebait 1.0, fish_strip 0.9, wobbler 0.6 | boat, trolling | catfish, predator | No.1 ±1 | 14000 ±1000 | mono 0.5 ±0.08 | — | — |
| Mako shark | livebait 1.0, fish_strip 0.9, wobbler 0.7 | boat, trolling | catfish, predator | No.1 ±1 | 14000 ±1000 | braid 0.4 ±0.06 | — | **yes** |
| Rotan | worm 1.0, bloodworm 0.9, maggot 0.8, livebait 0.7, silicone 0.6, chicken_liver 0.6 | pole, stick, ultralight | float, primitive | No.12 ±4 | none | mono 0.18 ±0.08 | — | — |
| Nase | maggot 1.0, worm 0.8, bloodworm 0.8, pearl_barley 0.7 | feeder, pole | feeder, float | No.12 ±3 | 2500 ±1500 | mono 0.16 ±0.05 | powder | — |
| Vimba bream | worm 1.0, maggot 0.9, bloodworm 0.8, pea 0.5 | bottom, feeder | feeder, float | No.10 ±3 | 3500 ±1500 | mono 0.2 ±0.05 | grain | — |
| Smelt | bloodworm 1.0, mormyshka 0.9, fish_strip 0.8, worm 0.7 | pole, ultralight, winter | float, winter | No.16 ±4 | none | mono 0.12 ±0.05 | — | — |
| Whitefish | bloodworm 1.0, mormyshka 0.9, maggot 0.8, worm 0.6 | feeder, ultralight, winter | feeder, float, winter | No.10 ±3 | 2500 ±1500 | fluoro 0.18 ±0.05 | — | — |
| Arctic char | spinner 1.0, spoon 0.9, castmaster 0.9, wobbler 0.7, worm 0.6 | spinning, ultralight | predator | No.8 ±2 | 2500 ±1000 | fluoro 0.2 ±0.05 | — | — |
| Lenok | wobbler 1.0, spinner 0.9, spoon 0.9, crankbait 0.8, worm 0.5 | spinning, ultralight | predator | No.6 ±2 | 3000 ±1000 | braid 0.14 ±0.05 | — | — |
| Taimen | wobbler 1.0, spoon 0.9, popper 0.85, livebait 0.8, crankbait 0.8 | spinning, trolling | predator | No.2 ±2 | 6000 ±2000 | braid 0.35 ±0.08 | — | **yes** |
| Atlantic salmon | spoon 1.0, wobbler 0.9, spinner 0.8, fish_strip 0.5 | sea_spin, spinning | predator | No.4 ±2 | 5000 ±2000 | braid 0.25 ±0.06 | — | — |
| Pink salmon | spoon 1.0, spinner 0.9, castmaster 0.8, fish_strip 0.5 | sea_spin, spinning, ultralight | predator | No.6 ±2 | 3500 ±1500 | braid 0.18 ±0.05 | — | — |
| Sturgeon | chicken_liver 1.0, worm 0.9, livebait 0.7, boilie 0.5 | bottom, carp | catfish, grusha | No.1 ±2 | 9000 ±3000 | braid 0.45 ±0.1 | pellet | — |
| Halibut | fish_strip 1.0, livebait 0.9, silicone 0.8, jig 0.7 | boat, surf | catfish, predator | No.1 ±3 | 11000 ±3000 | braid 0.5 ±0.1 | — | — |
| Common dace | maggot 1.0, worm 0.9, bread 0.7, bloodworm 0.65, dough 0.6, spinner 0.4 | pole, stick, ultralight | float, primitive | No.14 ±2 | 1000 ±1000 | mono 0.14 ±0.04 | powder | — |
| Volga zander | silicone 1.0, jig 0.95, livebait 0.9, worm 0.7, crankbait 0.6, wobbler 0.55 | spinning, ultralight | predator | No.6 ±2 | 2000 ±1000 | braid 0.1 ±0.04 | — | — |
| White-eye bream | worm 1.0, maggot 0.95, bloodworm 0.85, pearl_barley 0.5, corn 0.4 | bottom, feeder | feeder, float | No.12 ±3 | 3500 ±1500 | mono 0.18 ±0.05 | grain, powder | — |
| Round goby | worm 1.0, fish_strip 0.9, bloodworm 0.7, maggot 0.6, silicone 0.5 | bottom, feeder, ultralight | feeder, primitive | No.8 ±3 | 3000 ±2000 | mono 0.2 ±0.06 | — | — |
| Peacock bass | wobbler 1.2, popper 1.15, crankbait 1, silicone 0.95, spinner 0.9, livebait 0.85, jig 0.8 | spinning, ultralight | predator | No.4 ±3 | 3000 ±1000 | braid 0.2 ±0.05 | — | — |
| Bullseye snakehead | livebait 1.2, silicone 1.05, popper 1, wobbler 0.95, jig 0.85, worm 0.6 | spinning | predator | No.2 ±3 | 3000 ±1000 | braid 0.22 ±0.06 | — | — |
| Mayan cichlid | worm 1.2, bloodworm 1, maggot 1, silicone 0.8, bread 0.7 | pole, ultralight | float, predator | No.10 ±3 | 1500 ±1000 | mono 0.14 ±0.04 | — | — |
| Oscar | worm 1.2, livebait 1.1, maggot 0.9, silicone 0.9, jig 0.8 | spinning, ultralight | float, predator | No.8 ±3 | 2000 ±1000 | mono 0.16 ±0.04 | — | — |
| Striped bass | livebait 1.2, fish_strip 1.1, wobbler 1, silicone 0.95, spoon 0.9, jig 0.85 | boat, sea_spin, surf | ground, predator | No.2 ±2 | 7000 ±2000 | braid 0.3 ±0.08 | — | — |
| Bluefish | spoon 1.2, castmaster 1.15, fish_strip 1.1, wobbler 1, livebait 0.9, silicone 0.9 | boat, sea_spin, surf | predator | No.2 ±2 | 6000 ±2000 | braid 0.28 ±0.07 | — | **yes** |
| Jack crevalle | popper 1.25, castmaster 1.1, spoon 1.1, livebait 1, silicone 1, wobbler 0.95 | boat, sea_spin, surf | predator | No.1 ±2 | 8000 ±2000 | braid 0.35 ±0.08 | — | — |
| Tarpon | livebait 1.3, fish_strip 1.1, popper 1, silicone 1, jig 0.9 | boat, sea_spin, surf | catfish, predator | No.1 ±2 | 10000 ±3000 | braid 0.45 ±0.1 | — | — |
| Snook | livebait 1.25, silicone 1.1, wobbler 1.05, popper 1, jig 0.9, fish_strip 0.85 | sea_spin, spinning, surf | predator | No.2 ±2 | 6000 ±2000 | braid 0.3 ±0.08 | — | — |

## Per-species notes

### The five koi

Koi Kohaku, Koi Tancho Sanke, Koi Showa Sanke, Koi Asagi and Koi Bekko are a **hidden collectible**, not a normal fish. Their profile `base` is **0.0**, so they can never be drawn from the ordinary bite pool.

Instead, whenever you land a **carp, mirror carp or wild carp on a Carp Rig**, the catch has a chance to turn out to be a koi:

- **0.5 %** anywhere
- **35 %** in a cherry-blossom biome

Their only listed biome group is `cherry`, so a cherry-grove pond is the only place they belong at all. All five share identical statistics (800 g – 8 kg, median 2.5 kg, 25–90 cm, burst fighter, level 3).

Koi are **excluded from the species count** used by the tiered "N species" advancements and by *The Full Bestiary* — they have their own *A Living Jewel* and *Koi Collector* challenges. Filleting one is possible, announces your name in server chat with *"you seriously filleted it?"*, and grants the *Heartless Cook* advancement.

### Legendary specimens

Seven species hide one named, one-per-server specimen. Full mechanics in [Fishing mechanics](fishing-mechanics.md#legendary-fish).

| Species | Name | Weight | Chance |
|---|---|---|---|
| Pike | Queen of the Snags | 14 kg | 0.6 % |
| Wild Carp | Grandfather Sazan | 17.5 kg | 0.6 % |
| Catfish | Master of the Pit | 150 kg | 0.5 % |
| Yellowfin tuna | Old Ridgeback | 140 kg | 0.6 % |
| Blue marlin | The Leviathan | 380 kg | 0.8 % |
| Sturgeon | The Tsar-Fish | 145 kg | 0.4 % |
| Mako shark | The Megalodon | 390 kg | 0.4 % |
| Halibut | The Abyssal Demon | 250 kg | 0.4 % |

Four of these are **heavier than their species' normal maximum**: the pike (14 kg vs a 10 kg ceiling), the catfish (150 kg vs 120 kg), the halibut (250 kg vs 200 kg) and especially the mako (390 kg vs 200 kg). A legendary is genuinely outside the size range you can otherwise reach.

### Unusual profiles

**Silver carp** — a plankton filter-feeder, and the only species whose *best* bait scores just **0.5** (pearl barley). Because bite speed scales directly with the bait score, silver carp are permanently slow to take no matter what you do; powder groundbait and a thin line are what decide it. Level 6, and a relentless 25 kg fighter.

**Rotan** and **Smelt** — the only two species whose ideal `reel_size` is **0**: they actively prefer a reel-less rod. Fishing them with a reel scores 0.6 on the reel component instead of 1.0. Rotan is also the only species with a real **puddle** presence (1.0) — it genuinely lives in any ditch, which is why it is every angler's first fish. Smelt carries the mod's only **level 1** gate.

**Burbot** — the most tightly gated fish in the mod: `summer: 0.0` **and** `day: 0.0`. It exists only on cold nights, peaking in winter (1.6) and at night (1.5). Its own advancement, *King of the Winter Night*, exists because of this.

**Bleak** and **Gudgeon** — `night: 0.0`. They stop biting completely after dark.

**Ray** — a single run, the `steady` pattern, and aggression 0.2, but strength 0.95 across a 2–50 kg range. It doesn't fight; it is simply heavy. The profile describes it as lifting a slab of the seabed.

**Largemouth bass** (popper 1.2) and **Channel catfish** (livebait 1.1) are the only species with a bait score above 1.0 — a favourite bait earns a small bonus beyond a perfect match.

**Chub, Asp, Sterlet** live in **rivers only** (`river` 1.2 and every other water at 0). Nothing you do in a lake will produce one.

**Round goby** is the only species equally at home in salt and fresh water — `sea` 1.1 and `river` 1.0, plus lake 0.6 and pond 0.2.

**Brackish and migratory** — six species carry a non-zero `sea` factor alongside fresh water: Vimba bream (0.2), Smelt (1.2 sea / 0.3 river), Arctic char (0.2), Atlantic salmon (1.1 river / 1.0 sea), Pink salmon (1.1 sea / 1.0 river) and Sturgeon (0.3). Salmon and pink salmon are the true run fish — salmon peaks in autumn (1.4), pink salmon in summer (1.5).

**Grass Carp** — a vegetarian giant: corn 1.0, bread 0.9, dough 0.8, and the only "carp" that is `mid`-water rather than bottom. `relentless` pattern, so it fights just as hard at the net as at the strike.

**Winter-rod species** — only **Smelt** and **Whitefish** list the winter rod as ideal tackle, and only they list the winter rig. Everything else caught through the ice is caught on a rod it doesn't strictly want.

**Stick-rod species** — Gudgeon, Bleak, Rotan, Common dace and Bluegill are the five fish that list the humblest blank as ideal. **Bamboo** appears for only two, Bluegill and Blue bream.

**Zero-winter species** — Crucian Carp, Rudd, Bleak, Chub, Tench, Catfish, Eel and Grass Carp all have `winter: 0.0`; Carp, Mirror Carp, Wild Carp and Silver carp are effectively shut down too (0.02–0.05). Winter is a genuinely different game.

## See also

- [Species reference](species-reference.md) — habitat gates, condition tables, fight statistics
- [Water and conditions](water-and-conditions.md) · [Fishing mechanics](fishing-mechanics.md)
- [Sea fishing](sea-fishing.md) · [Ice fishing](ice-fishing.md)
- [Villager](villager.md) — which species the fisherman buys, and for how much
