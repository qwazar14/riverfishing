# Progression

Your record lives in the **Fishing Journal**: every species you have caught, your best specimen of each, your angler XP and level, your skill points and your quest chain. It survives death.

## Angler XP

Every **legal** catch pays out:

```
xp = 2 + weightGrams / 25 + lengthCm / 4        (integer division)
   + 50 if it is a species you have never landed
   + 20 if it is a personal best for that species (and not a new species)
   × 3 if it is a trophy
```

A [foul-hooked](fishing-mechanics.md#foul-hooking) fish pays nothing at all, and is not recorded.

The weight term dominates, deliberately: a swarm of tiny fish is poor XP per hour and targeting bigger fish pays.

| A median specimen of… | Weight / length | XP |
|---|---|---|
| Bleak | 30 g / ~10 cm | 5 |
| Roach | 120 g / ~16 cm | ~10 |
| Bream | 900 g / ~35 cm | ~46 |
| Carp | 3.5 kg / ~58 cm | ~156 |
| Catfish | 7 kg / ~96 cm | ~306 |

(Lengths carry ±2 % natural variation, so the XP wobbles by a point or two.)

Three other things award XP: **netting a friend's fish** (+5), **fishing up treasure** (+15) and **claiming a quest reward** — 15 to 300 XP on top of the item the tables below name.

## Levels and ranks

```
total XP needed to reach level L = 50 × L × (L + 1)
```

| Level | Total XP | Rank |
|---|---|---|
| 0 | 0 | Bronze |
| 1 | 100 | Bronze |
| 2 | 300 | Bronze |
| 3 | 600 | Bronze |
| 4 | 1 000 | Bronze |
| 5 | 1 500 | **Silver** |
| 6 | 2 100 | Silver |
| 7 | 2 800 | Silver |
| 8 | 3 600 | Silver |
| 9 | 4 500 | Silver |
| 10 | 5 500 | **Gold** |
| 12 | 7 800 | Gold |
| 15 | 12 000 | Gold |
| 19 | 19 000 | Gold |
| 20 | 21 000 | **Master** |
| 25 | 32 500 | Master |

Levelling up and each new rank announce themselves in chat. Reaching **Master** grants the *Master Angler* advancement.

## What your level unlocks

Level is the mod's soft difficulty gate. Every species carries a `min_angler_level`, and every level you are short of it costs you:

```
bite weight ×= max(0.03, 0.6 ^ levelsShort)
```

So being two levels short is ×0.36, five levels short is ×0.08, and the floor is 3 %. You *can* fluke a big fish early with the right gear in the right place — it is just rare, and it becomes steady as you level.

| Level needed | Count | Species |
|---|---|---|
| **0** | 17 | Bitterling, Bleak, Bluegill, Bream, Common dace, Crucian Carp, Gudgeon, Perch, Roach, Rotan, Round goby, Rudd, Ruffe, Sculpin, Sunbleak, Tubenose goby, White Bream |
| **1** | 1 | Smelt |
| **2** | 7 | Blue bream, Golden crucian, Ide, Nase, Sabrefish, Tench, White-eye bream |
| **3** | 15 | Carp, Chub, Grayling, all five Koi, Largemouth bass, Mayan cichlid, Mirror Carp, Oscar, Pink salmon, Vimba bream, Volga zander |
| **4** | 11 | Burbot, Flounder, Garfish, Grass Carp, Herring, Mackerel, Pike, Rainbow trout, Whitefish, Wild Carp, Zander |
| **5** | 10 | Arctic char, Asp, Bullseye snakehead, Channel catfish, Eel, Lenok, Peacock bass, Saithe, Sea bass, Trout |
| **6** | 9 | Atlantic salmon, Barracuda, Bluefish, Catfish, Cod, Golden dorado, Ray, Silver carp, Striped bass |
| **7** | 10 | Blue marlin, Conger eel, Jack crevalle, Mahi-mahi, Mako shark, Sailfish, Snook, Swordfish, Wahoo, Yellowfin tuna |
| **8** | 2 | Sterlet, Taimen |
| **9** | 4 | Bull shark, Halibut, Sturgeon, Tarpon |
| **10** | 3 | Arapaima, Goliath grouper, Piraiba |
| **11** | 1 | Frilled shark |
| **12** | 1 | Beluga sturgeon |

## Skills

Every angler level grants **one skill point**. Seven perks, each with **5 ranks**, so a full tree costs 35 points — level 35.

Spend them on the **Skills** tab of the journal. Points cannot be refunded (only `/rffish reset` clears them, along with everything else).

| Perk | Branch | Per rank | At rank 5 |
|---|---|---|---|
| **Frugal** | Bait | +5 % chance to keep the bait after a bite | 25 % |
| **Keen Sense** | Sense | −5 % time to bite | −25 % |
| **Naturalist** | Knowledge | +5 % overall bite chance | +25 % |
| **Steady Hand** | Hand | +5 % line tension before it snaps | +25 % |
| **Bottom Sense** | Hand | −8 % snag chance — a dead snag (rig lost) takes the multiplier twice — and −5 % on the over-strain line-break roll | −40 % snags, dead snags ×0.36, −25 % breaks |
| **Angler's Luck** | Fortune | +1 % to the size of the fish you meet — and so to trophies | +5 % |
| **Finesse** | Finesse | +1 % wider strike zone | +5 % |

Naturalist's bonus is applied uniformly to every species' bite weight; Steady Hand multiplies your break tolerance; Bottom Sense scales the [snag](fishing-mechanics.md#snags) roll and the per-tick snap roll while you are over the limit; Finesse widens the green band of every [timing bar](fishing-mechanics.md#the-timing-bar).

## The quest chain

Eight stages: **49 tasks** plus one completion prize per stage, 57 entries in all. Quests are **derived live** from your journal, so you never have to "accept" one.

- Completing a goal announces *"Quest complete: … — the reward is waiting in the journal!"* once.
- You then **claim** the reward by clicking the quest on the journal's **Quests** tab.
- Each stage's final entry (*"Fully complete stage N"*) is a bonus prize for finishing every task in it.
- A stage becomes **visible and claimable** once **70 %** of the previous stage's tasks are done. Locked stages read *"Locked - finish stage N"*.

From 0.10.0 the first five stages never ask for a fish by name — a chain written around roach and pike meant nothing on an African or Amazonian water. They ask for **diets, families and weights**, which every province has. A *peaceful feeder*, *omnivore*, *predator* or *insect feeder* is the species' diet, printed on its journal page; a *family* is the journal group (carp family, catfish, cichlids, …). Both are counted from the update on — earlier catches are not back-counted.

### Stage 1 — Beginner

| Goal | Reward | XP |
|---|---|---|
| Catch your first fish | 8 × Worm | 15 |
| Catch a peaceful feeder | 8 × Maggot | 15 |
| Discover 3 species | 4 × Hook No.12 | 30 |
| Catch 3 peaceful feeders | 4 × Base Groundbait | 20 |
| Catch 10 fish | Bait Trap | 25 |
| Fully complete stage 1 | 12 emeralds | 40 |

### Stage 2 — Float & feeder

| Goal | Reward | XP |
|---|---|---|
| Catch an omnivore | 3 × Hook No.8 | 25 |
| Catch 10 peaceful feeders | 6 × Boilie | 35 |
| Land a fish of 1 kg or more | 6 emeralds | 40 |
| Catch fish of 3 different families | 6 emeralds | 30 |
| Discover 8 species | **Spinning Rod** | 60 |
| Fully complete stage 2 | Reel 3000 | 60 |

### Stage 3 — Predators

| Goal | Reward | XP |
|---|---|---|
| Catch a predator | 2 × Spinner | 25 |
| Catch 5 predators | 2 × Steel Leader | 40 |
| Land a predator of 3 kg or more | 10 emeralds | 70 |
| Catch 15 predators | Wobbler | 45 |
| Catch fish of 5 different families | 6 emeralds | 45 |
| Fully complete stage 3 | Titanium Leader | 80 |

### Stage 4 — Heavy tackle

| Goal | Reward | XP |
|---|---|---|
| Land a fish of 5 kg or more | 8 × Boilie | 50 |
| Land a fish of 10 kg or more | Titanium Leader | 80 |
| Land a fish of 20 kg or more | 20 emeralds | 120 |
| Catch fish of 6 different families | 6 emeralds | 50 |
| Catch 100 fish | Reel 5000 | 90 |
| Fully complete stage 4 | 32 emeralds | 120 |

### Stage 5 — Master

| Goal | Reward | XP |
|---|---|---|
| Discover 15 species | Reel 7000 | 100 |
| Discover 30 species | 16 emeralds | 100 |
| Land a fish of 40 kg or more | 10 emeralds | 70 |
| Catch a koi carp | 12 emeralds | 80 |
| Land a trophy specimen | 8 emeralds | 60 |
| Land 5 trophies | 24 emeralds | 140 |
| Discover 20 species | 20 emeralds | 150 |
| Reach Master rank (lvl 20) | 30 emeralds | — |
| Fully complete stage 5 | **Carp Rod** | 150 |

### Stage 6 — Under the ice

| Goal | Reward | XP |
|---|---|---|
| Catch your first fish through the ice | 2 × Ice Jig | 40 |
| Catch a burbot | 4 × Chicken Liver | 60 |
| Catch 5 fish through the ice | 12 × Maggot | 30 |
| Catch 10 fish through the ice | **Winter Rod** | 80 |
| Catch 30 fish through the ice | 24 emeralds | 160 |
| Fully complete stage 6 | 50 emeralds | 200 |

See [Ice fishing](ice-fishing.md#progression).

### Stage 7 — Cold water and the fly

The [fly rod](fly-fishing.md) and the salmonids, wherever the water is cold enough for them. A *salmonid* is any fish of the salmon and trout family; an *insect feeder* is a species whose diet says so.

| Goal | Reward | XP |
|---|---|---|
| Land a fish on the fly rod | 10 emeralds | 40 |
| Catch a salmonid | 2 × Spoon Lure | 50 |
| Land 10 fish on the fly | 24 emeralds | 90 |
| Catch 3 salmonids | Castmaster | 60 |
| Catch 5 insect feeders | 12 × Bloodworm | 50 |
| Land a salmonid of 5 kg or more | 30 emeralds | 160 |
| Land 30 fish on the fly | 30 emeralds | 120 |
| Fully complete stage 7 | **Surf rod** | 180 |

### Stage 8 — The sea and big game

See [Sea fishing](sea-fishing.md#progression-into-the-sea). Completion reward: 64 emeralds.

## Advancements

Thirty-four advancements. Some are driven by simply having the fish in your inventory; others are **code-driven** and depend on *how* you caught it.

| Advancement | How |
|---|---|
| **River Fishing** (root) | Have any fish from the mod |
| **Ten in the Net** | 10 different species |
| **A Quarter Hundred** | 25 different species |
| **Fifty Species** | 50 different species |
| **A Hundred Names** | 100 different species |
| **Two Hundred Names** | 200 different species |
| **The Full Bestiary** *(challenge)* | Every species — koi don't count. Hangs under *Two Hundred Names* |
| **A Trophy Specimen** *(goal)* | **Land** a trophy specimen yourself |
| **Toothy** | Have a pike |
| **Master of the Hole** *(goal)* | Have a catfish |
| **The Tsar Fish** *(challenge)* | Have a sterlet |
| **King of the Winter Night** *(goal)* | Have a burbot |
| **River Brawler** *(challenge)* | Have a wild carp |
| **A Living Jewel** *(goal)* | Have any koi |
| **Koi Collector** *(challenge)* | All five koi: Kohaku, Tancho, Showa, Asagi, Bekko |
| **Master Angler** *(challenge)* | Reach the Master rank |
| **A Name in History** | Catch a [legendary fish](fishing-mechanics.md#legendary-fish) |
| **The Old Way** | Land a **4+ kg pike on live bait** with a Stick or Bamboo rod |
| **From Under the Ice** | Pull a **burbot** through a hole with the winter rod |
| **Bare-Handed** | Land a **trophy** on a reel-less Stick / Bamboo / Pole rod |
| **Feeding Time** | Land a fish during a [feeding frenzy](water-and-conditions.md#feeding-frenzy) |
| **Catch of the Decade** | Fish up an old boot |
| **Heartless Cook** | Fillet a koi carp |
| **It Was DEFINITELY Huge** *(hidden)* | Suffer the 0.3 % [catastrophic tackle failure](fishing-mechanics.md#catastrophic-failure) |
| **On the Fly** | Land a fish on the [fly rod](fly-fishing.md) |
| **Tight Loop** *(hidden challenge)* | Land a fish on a cast that unrolled without a splash |
| **Tied by Hand** | Tie a lure at the [tackle bench](tackle-station.md) |
| **A Far Shore** | Catch a fish in a second [faunal province](provinces.md) |
| **The Whole Planet** | A fish in every one of the five provinces |
| **Heavyweight** | Land a fish of 50 kg or more |
| **Neither One Nor the Other** | Catch a hybrid |
| **Grandmaster** | Angler level 50 |
| **In Time** | Hook a fish through the ice on a full [jig combo](ice-fishing.md) — all 8 beats |
| **Market Day** | Sell a [keepnet](keepnet.md) to the fisherman |

The species-count advancements are **counted in code** against the live species roster, so they can never drift out of step with a content update. Koi are excluded from that count.

*A Trophy Specimen* is code-driven too, from 0.7.0: it is granted at the moment a trophy is landed, so it says what it always meant to say — you earn it for catching one, not for holding one somebody handed you. (It used to be a datapack item predicate, and on 1.21.1 that predicate decoded to an empty one, which matches every item there is. Players were being handed the goal for picking up pea seeds.)

## The Fishing Journal

Craft it from **Book + Hook No.12 + Leather** (shapeless), then right-click to read. Eight tabs:

| Tab | Contents |
|---|---|
| **Fish** | The bestiary — every species, your count and personal best, and a full "how to catch" page (water, depth, width, biomes, best conditions, baits, tackle) read straight from the same profile the bite engine uses |
| **Bait & feed** | Every hook bait and groundbait, plus the ballast and crops that only ever go into a mix, and which fish each one attracts |
| **Lures** | Every lure, and which fish it attracts |
| **Gear** | Rods, reels, lines and rigs with their crafting recipes and compatibility bands |
| **Quests** | The chain above, with progress and the claim buttons |
| **Skills** | The perk tree and your point balance |
| **Records** | Your career on one page: level and rank, fish landed, species and families, trophies, ice catches and your five biggest fish |
| **Guides** | Twenty-four written how-to pages |

The Guides shelf covers, in the order the shelf itself runs: the wait on float and bottom, how the fish notice you before they notice the bait, tackle stress, the drag; groundbait in four pages — the base, the numbers, feeding, the recipes — and the keepnet; working the lure, topwater, live bait; the tackle bench and where the tackle lives; *every water is its own*, the market and the daily order, fishing together; ice fishing; trolling, sea giants, legendary fish; and last the electrofisher, Discord and the thanks.

The header line reads *"Angler: lvl N (rank) — X XP, next in Y"*.

## Operator commands

| Command | Effect |
|---|---|
| `/rffish unlockall` | Fills the journal — every species at its maximum weight, 120 total catches, 10 trophies, 40 ice catches, and enough XP for level 25. Every quest goal becomes complete. |
| `/rffish reset` | Wipes your records, XP, skills and quest progress. |

Both need permission level 2.

There is also a **client-side** `/rfrod` command used for tuning the in-hand rod pose and the rod-bend animation (`show`, `reset`, `set`, `add`, `bend`, `tip`). It is a development tool and changes nothing about gameplay.

## See also

- [Fishing mechanics](fishing-mechanics.md) · [Species](species.md)
- [Villager](villager.md) — the other half of the economy
- [Ice fishing](ice-fishing.md) · [Sea fishing](sea-fishing.md)
