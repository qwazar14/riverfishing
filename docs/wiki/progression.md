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

Two other things award XP: **netting a friend's fish** (+5) and **fishing up treasure** (+15).

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
| **0** | 13 | Bleak, Bluegill, Bream, Common dace, Crucian Carp, Gudgeon, Perch, Roach, Rotan, Round goby, Rudd, Ruffe, White Bream |
| **1** | 1 | Smelt |
| **2** | 6 | Blue bream, Ide, Nase, Sabrefish, Tench, White-eye bream |
| **3** | 13 | Carp, Chub, Grayling, all five Koi, Largemouth bass, Mirror Carp, Pink salmon, Vimba bream, Volga zander |
| **4** | 11 | Burbot, Flounder, Garfish, Grass Carp, Herring, Mackerel, Pike, Rainbow trout, Whitefish, Wild Carp, Zander |
| **5** | 8 | Arctic char, Asp, Channel catfish, Eel, Lenok, Saithe, Sea bass, Trout |
| **6** | 6 | Atlantic salmon, Barracuda, Catfish, Cod, Ray, Silver carp |
| **7** | 8 | Blue marlin, Conger eel, Mahi-mahi, Mako shark, Sailfish, Swordfish, Wahoo, Yellowfin tuna |
| **8** | 2 | Sterlet, Taimen |
| **9** | 2 | Halibut, Sturgeon |

## Skills

Every angler level grants **one skill point**. Six perks, each with **5 ranks**, so a full tree costs 30 points — level 30.

Spend them on the **Skills** tab of the journal. Points cannot be refunded (only `/rffish reset` clears them, along with everything else).

| Perk | Branch | Per rank | At rank 5 |
|---|---|---|---|
| **Frugal** | Bait | +5 % chance to keep the bait after a bite | 25 % |
| **Keen Sense** | Sense | −5 % time to bite | −25 % |
| **Naturalist** | Knowledge | +5 % overall bite chance | +25 % |
| **Steady Hand** | Hand | +5 % line tension before it snaps | +25 % |
| **Angler's Luck** | Fortune | +1 % to the size of the fish you meet — and so to trophies | +5 % |
| **Finesse** | Finesse | +1 % wider strike zone | +5 % |

Naturalist's bonus is applied uniformly to every species' bite weight; Steady Hand multiplies your break tolerance; Finesse widens the green band of every [timing bar](fishing-mechanics.md#the-timing-bar).

## The quest chain

Eight stages: **51 tasks** plus one completion prize per stage, 59 entries in all. Quests are **derived live** from your journal, so you never have to "accept" one.

- Completing a goal announces *"Quest complete: … — the reward is waiting in the journal!"* once.
- You then **claim** the reward by clicking the quest on the journal's **Quests** tab.
- Each stage's final entry (*"Fully complete stage N"*) is a bonus prize for finishing every task in it.
- A stage becomes **visible and claimable** once **70 %** of the previous stage's tasks are done. Locked stages read *"Locked - finish stage N"*.

### Stage 1 — Beginner

| Goal | Reward |
|---|---|
| Catch your first fish | 8 × Worm |
| Catch a roach | 8 × Maggot |
| Discover 3 species | 4 × Hook No.12 |
| Catch a crucian carp | 4 × Grain Groundbait |
| Catch 10 fish | Bait Trap |
| Fully complete stage 1 | 12 emeralds |

### Stage 2 — Float & feeder

| Goal | Reward |
|---|---|
| Catch a bream | 3 × Hook No.8 |
| Catch a rudd | 4 × Powder Groundbait |
| Catch a tench | 4 × Pellet Groundbait |
| Catch a bream 2+ kg | 6 emeralds |
| Discover 8 species | **Spinning Rod** |
| Fully complete stage 2 | Reel 3000 |

The tench quest is currently the **only source of Pellet Groundbait** in the game.

### Stage 3 — Predators

| Goal | Reward |
|---|---|
| Catch a perch | 2 × Spinner |
| Catch a pike | 2 × Steel Leader |
| Catch a pike 5+ kg | 10 emeralds |
| Catch a zander | Wobbler |
| Catch an asp | 6 emeralds |
| Fully complete stage 3 | Titanium Leader |

### Stage 4 — Heavy tackle

| Goal | Reward |
|---|---|
| Catch a carp | 8 × Boilie |
| Catch a carp 8+ kg | 10 emeralds |
| Catch a catfish | Titanium Leader |
| Catch a catfish 20+ kg | 20 emeralds |
| Catch a trout | 6 emeralds |
| Catch 100 fish | Reel 5000 |
| Fully complete stage 4 | 32 emeralds |

### Stage 5 — Master

| Goal | Reward |
|---|---|
| Discover 15 species | Reel 7000 |
| Catch a sterlet | 16 emeralds |
| Catch a grayling | 10 emeralds |
| Catch a koi carp | 12 emeralds |
| Land a trophy specimen | 8 emeralds |
| Land 5 trophies | 24 emeralds |
| Discover 20 species | 20 emeralds |
| Reach Master rank (lvl 20) | 30 emeralds |
| Fully complete stage 5 | **Carp Rod** |

### Stage 6 — Under the ice

See [Ice fishing](ice-fishing.md#progression). Completion reward: 50 emeralds.

### Stage 7 — The North and the taiga

Each reward hands you the exact lure the *next* quest's fish wants — the stage teaches itself.

| Goal | Reward |
|---|---|
| Catch a rotan — everyone started with one | Spinner |
| Catch a nase in the current | 12 × Maggot |
| Catch a vimba on the spring run | 4 × Grain Groundbait |
| Catch a whitefish in a cold lake | 12 × Bloodworm |
| Catch an Arctic char in northern water | Castmaster |
| Catch a lenok on a taiga riffle | Wobbler |
| Catch a running Atlantic salmon | 2 × Spoon Lure |
| Beat a taimen of 15 kg or more | 30 emeralds |
| Fully complete stage 7 | **Surf rod** |

### Stage 8 — The sea and big game

See [Sea fishing](sea-fishing.md#progression-into-the-sea). Completion reward: 64 emeralds.

## Advancements

Twenty-two advancements. Some are driven by simply having the fish in your inventory; others are **code-driven** and depend on *how* you caught it.

| Advancement | How |
|---|---|
| **River Fishing** (root) | Have any fish from the mod |
| **Ten in the Net** | 10 different species |
| **A Quarter Hundred** | 25 different species |
| **Fifty Species** | 50 different species |
| **The Full Bestiary** *(challenge)* | Every species — koi don't count |
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

The species-count advancements are **counted in code** against the live species roster, so they can never drift out of step with a content update. Koi are excluded from that count.

*A Trophy Specimen* is code-driven too, from 0.7.0: it is granted at the moment a trophy is landed, so it says what it always meant to say — you earn it for catching one, not for holding one somebody handed you. (It used to be a datapack item predicate, and on 1.21.1 that predicate decoded to an empty one, which matches every item there is. Players were being handed the goal for picking up pea seeds.)

## The Fishing Journal

Craft it from **Book + Hook No.12 + Leather** (shapeless), then right-click to read. Six tabs:

| Tab | Contents |
|---|---|
| **Fish** | The bestiary — every species, your count and personal best, and a full "how to catch" page (water, depth, width, biomes, best conditions, baits, tackle) read straight from the same profile the bite engine uses |
| **Baits & Lures** | Every bait, lure and groundbait, and which fish it attracts |
| **Gear** | Rods, reels, lines and rigs with their crafting recipes and compatibility bands |
| **Quests** | The chain above, with progress and the claim buttons |
| **Skills** | The perk tree and your point balance |
| **Guides** | Twelve written how-to pages |

The Guides shelf covers: the drag, tackle stress, live bait, *every water is its own*, working the lure, topwater, trolling, sea giants, legendary fish, the market and the daily order, fishing together, and the tackle bench.

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
