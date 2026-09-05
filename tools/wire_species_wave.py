# -*- coding: utf-8 -*-
"""Wire one WAVE of new species into the mod: profile, models, icon, cutting recipe, lang, registry, trades.

    py tools/wire_species_wave.py

The data block below IS the wave; the code under it is generic, so the next wave replaces the block and
runs the same script. Every species names a DONOR that already works — its profile supplies the full set
of keys (they drift over releases, and a hand-written profile silently misses one), its item model carries
whatever that tree needs (builtin/entity here, item/generated on 26.x), and its cutting recipe carries the
loader gate. The patch then overrides what makes this fish itself.

Keys listed in REPLACE are swapped WHOLE rather than merged: a bull shark that inherited the mako's
"deep" biome, or a sculpin that kept the gudgeon's baits, is a fish nobody could explain.
"""
import io, json, os, re, shutil, subprocess, sys

MAIN = r"C:/Users/Qwazar/VS Code Projects/fishing mod"
# The ports get the same wave. They are NOT copies of this tree: 1.20.1 keeps the old `recipes/` and
# `tags/items/` layout and registers trades in Java, 26.x uses `recipe/`, `tags/item/`, data-driven
# trades from tools/gen_villager_trades.py, and needs its own items/ + fish_scaled models generated
# afterwards. So every step asks the tree what it is rather than assuming, and the DONOR files are read
# from the tree being written — that is what keeps a species in the right dialect on each branch.
TREES = [MAIN, r"C:/Users/Qwazar/wt/rf1201", r"C:/Users/Qwazar/wt/rf26"]
A = "common/src/main/resources/assets/riverfishing"
D = "common/src/main/resources/data/riverfishing"

TAG = "§deep-twelve (0.9.0)"

WAVE = ("§deep-twelve (0.9.0): the abyss, the open ocean and three fresh-water oddities — twelve\n"
        "            // species the author drew, from a 20 g loach to a twenty-tonne shark.")

# id: (donor, profile patch, (en, ru, uk) name, (en, ru, uk) journal description, (trade tier, emeralds, xp))
SPECIES = {
 "mullet": ("kutum", {
    "display": "Кефаль", "group": "sea",
    "water_bodies": {"sea": 1.2, "river": 0.6, "lake": 0.2, "pond": 0.0, "swamp": 0.0, "puddle": 0.0},
    "weight_g": {"min": 300, "max": 8000, "mean": 900}, "length_cm": {"min": 25, "max": 80},
    "fight": {"strength": 0.6, "stamina": 0.75, "runs": 3, "pattern": "burst", "aggression": 0.5},
    "ideal": {"rod": ["pole", "feeder", "ultralight"],
              "reel_size": 3000, "reel_tolerance": 1000,
              "line": {"type": "mono", "diameter_mm": 0.18, "tolerance_mm": 0.05},
              "rig": ["float", "feeder"], "groundbait": {"fraction": 0.45, "nutrition": 0.55},
              "bait": {"bread": 1.0, "dough": 0.95, "maggot": 0.7, "worm": 0.6, "corn": 0.4, "pea": 0.3},
              "hook": {"ideal": 12, "tolerance": 3}, "requires_leader": False},
    "season": {"spring": 1.0, "summer": 1.4, "autumn": 1.1, "winter": 0.3},
    "time": {"dawn": 1.2, "day": 1.1, "dusk": 1.2, "night": 0.4},
    "weather": {"clear": 1.1, "rain": 0.9, "thunder": 0.7},
    "depth_pref": "surface", "distance_pref": {"min": 3, "max": 20},
    "habitat": {"depth_min": 1, "depth_max": 12, "width_min": 8},
    "biomes": {"warm": 1.2, "temperate": 1.0, "beach": 1.2, "ocean_biome": 0.9},
    "base": 0.85, "min_angler_level": 2},
   ("Mullet", "Кефаль", "Кефаль"),
   ("A grey shoal fish of harbours, estuaries and the surf line: it grazes algae and scraps off the "
    "bottom and takes bread the way a carp does. Light float tackle, a small hook and a slow drift down "
    "the current — it reaches eight kilos and fights every one of them like a fish twice the size.",
    "Серая стайная рыба портов, лиманов и прибойной полосы: пасётся на водорослях и объедках и берёт "
    "хлеб не хуже карпа. Лёгкая поплавочная снасть, мелкий крючок и медленный проплыв по течению — "
    "вырастает до восьми килограммов и дерётся так, будто весит вдвое больше.",
    "Сіра зграйна риба портів, лиманів і смуги прибою: пасеться на водоростях та недоїдках і бере "
    "хліб не гірше за коропа. Легка поплавкова снасть, дрібний гачок і повільний проплив за течією — "
    "виростає до восьми кілограмів і б’ється так, ніби важить удвічі більше."),
   (3, 4, 7)),

 "anglerfish": ("conger", {
    "display": "Морской чёрт", "group": "sea",
    "water_bodies": {"sea": 1.0, "river": 0.0, "lake": 0.0, "pond": 0.0, "swamp": 0.0, "puddle": 0.0},
    "weight_g": {"min": 2000, "max": 40000, "mean": 7000}, "length_cm": {"min": 40, "max": 150},
    "fight": {"strength": 0.5, "stamina": 0.4, "runs": 1, "pattern": "steady", "aggression": 0.2},
    "ideal": {"rod": ["boat", "bottom"],
              "reel_size": 10000, "reel_tolerance": 2000,
              "line": {"type": "braid", "diameter_mm": 0.30, "tolerance_mm": 0.08},
              "rig": ["catfish", "grusha"], "groundbait": {"fraction": 0.72, "nutrition": 0.80},
              # No lures at all: it does not chase, it waits. Livebait or a strip of fish, or nothing.
              "bait": {"livebait": 1.1, "fish_strip": 1.0},
              "hook": {"ideal": 1, "tolerance": 2}, "requires_leader": True},
    "season": {"spring": 1.0, "summer": 0.8, "autumn": 1.1, "winter": 1.2},
    "time": {"dawn": 0.8, "day": 0.5, "dusk": 1.1, "night": 1.5},
    "weather": {"clear": 1.0, "rain": 1.0, "thunder": 0.9},
    "depth_pref": "bottom", "distance_pref": {"min": 20, "max": 45},
    "habitat": {"depth_min": 12, "width_min": 24},
    "biomes": {"deep": 1.4, "cold": 1.2, "temperate": 0.8, "ocean_biome": 0.5},
    "base": 0.22, "min_angler_level": 8},
   ("Anglerfish", "Морской чёрт", "Морський чорт"),
   ("All mouth and no manners: it lies on the deep bottom with a lure of its own dangling over its head "
    "and swallows whatever comes to look. Nothing artificial interests it — livebait or a strip of fish "
    "on the seabed, at night, in water deep enough to be dark. It does not fight so much as refuse to "
    "come up.",
    "Сплошная пасть и никаких манер: лежит на глубоком дне, свесив над головой собственную "
    "приманку, и глотает всё, что подойдёт посмотреть. Искусственное ему неинтересно — только "
    "живец или полоска рыбы на дне, ночью, на глубине, где уже темно. Он не столько "
    "сопротивляется, сколько отказывается подниматься.",
    "Суцільна паща й жодних манер: лежить на глибокому дні, звісивши над головою власну "
    "принаду, і ковтає все, що підпливе подивитися. Штучне його не цікавить — тільки "
    "живець або смужка риби на дні, вночі, на глибині, де вже темно. Він не так "
    "опирається, як відмовляється підніматися."),
   (5, 15, 27)),

 "black_marlin": ("sailfish", {
    "display": "Чёрный марлин", "group": "big_game",
    "water_bodies": {"sea": 1.0, "river": 0.0, "lake": 0.0, "pond": 0.0, "swamp": 0.0, "puddle": 0.0},
    "weight_g": {"min": 30000, "max": 700000, "mean": 95000}, "length_cm": {"min": 150, "max": 460},
    "fight": {"strength": 0.95, "stamina": 1.0, "runs": 6, "pattern": "greyhounding", "aggression": 1.0},
    "ideal": {"rod": ["trolling"],
              "reel_size": 14000, "reel_tolerance": 1000,
              "line": {"type": "braid", "diameter_mm": 0.45, "tolerance_mm": 0.06},
              "rig": ["predator"], "groundbait": {"fraction": 1.00, "nutrition": 0.75},
              "bait": {"giant_spoon": 1.05, "octopus_jig": 1.0, "fish_strip": 0.9, "wobbler": 0.9,
                       "silicone": 0.5},
              "hook": {"ideal": 1, "tolerance": 1}, "requires_leader": True},
    "season": {"spring": 1.0, "summer": 1.3, "autumn": 1.1, "winter": 0.4},
    "time": {"dawn": 1.3, "day": 1.2, "dusk": 1.1, "night": 0.2},
    "weather": {"clear": 1.2, "rain": 0.9, "thunder": 0.5},
    "depth_pref": "surface", "distance_pref": {"min": 32, "max": 45},
    "habitat": {"depth_min": 12, "width_min": 24},
    "biomes": {"deep": 1.4, "warm": 1.3, "ocean_biome": 0.4},
    "base": 0.18, "min_angler_level": 9},
   ("Black marlin", "Чёрный марлин", "Чорний марлін"),
   ("The heaviest of the billfish and the fastest thing in the open sea. Troll a giant spoon, an octopus "
    "jig or a big jointed body over deep blue water and hold on: seven hundred kilos of it exists, and "
    "the strongest line the shop sells is barely enough.",
    "Самый тяжёлый из марлиновых и самое быстрое, что есть в открытом море. Троллинг с "
    "огромной блесной, октопус-джигом или крупным составником над синей глубиной — и "
    "держитесь: он бывает и в семьсот килограммов, а самой прочной лески в магазине едва "
    "хватает.",
    "Найважчий із марлінових і найшвидше, що є у відкритому морі. Тролінг із "
    "велетенською блешнею, октопус-джиґом або великим складником над синьою глибиною — і "
    "тримайтеся: він буває і на сімсот кілограмів, а найміцнішої волосіні в крамниці ледве "
    "вистачає."),
   (5, 32, 46)),

 "blobfish": ("flounder", {
    "display": "Рыба-капля", "group": "sea",
    "water_bodies": {"sea": 1.0, "river": 0.0, "lake": 0.0, "pond": 0.0, "swamp": 0.0, "puddle": 0.0},
    "weight_g": {"min": 1000, "max": 10000, "mean": 2500}, "length_cm": {"min": 25, "max": 70},
    "fight": {"strength": 0.15, "stamina": 0.2, "runs": 1, "pattern": "steady", "aggression": 0.05},
    "ideal": {"rod": ["boat", "bottom"],
              "reel_size": 8000, "reel_tolerance": 2000,
              "line": {"type": "braid", "diameter_mm": 0.25, "tolerance_mm": 0.08},
              "rig": ["grusha", "catfish"], "groundbait": {"fraction": 0.55, "nutrition": 0.45},
              "bait": {"fish_strip": 0.9, "worm": 0.8, "bloodworm": 0.7, "chicken_liver": 0.5},
              "hook": {"ideal": 6, "tolerance": 3}, "requires_leader": False},
    # Nothing down there knows what season or hour it is, so nothing about the surface moves the bite.
    "season": {"spring": 1.0, "summer": 1.0, "autumn": 1.0, "winter": 1.0},
    "time": {"dawn": 1.0, "day": 1.0, "dusk": 1.0, "night": 1.0},
    "weather": {"clear": 1.0, "rain": 1.0, "thunder": 1.0},
    "depth_pref": "bottom", "distance_pref": {"min": 25, "max": 45},
    "habitat": {"depth_min": 12, "width_min": 24},
    "biomes": {"deep": 1.5, "cold": 1.1, "temperate": 0.7, "ocean_biome": 0.15},
    "base": 0.16, "min_angler_level": 8},
   ("Blobfish", "Рыба-капля", "Риба-крапля"),
   ("A sack of jelly from the cold deep, famous for what the surface does to its face. It waits on the "
    "bottom for something edible to drift past, and behaves the same way on a hook: a soft take, no run, "
    "no head-shake, and a curiosity in the keepnet nobody will believe.",
    "Студенистый мешок с холодной глубины, знаменитый тем, во что превращается его "
    "морда на поверхности. На дне он ждёт, пока мимо проплывёт что-нибудь съедобное, и на "
    "крючке ведёт себя так же: мягкая поклёвка, ни рывка, ни потяжки — и диковина в садке, "
    "в которую никто не поверит.",
    "Драглистий мішок із холодної глибини, знаменитий тим, на що перетворюється його "
    "морда на поверхні. На дні він чекає, доки повз пропливе щось їстівне, і на "
    "гачку поводиться так само: м’яка поклівка, ні ривка, ні потяжки — і дивовижа в садку, "
    "у яку ніхто не повірить."),
   (4, 5, 10)),

 "bluefin_tuna": ("wahoo", {
    "display": "Синеперый тунец", "group": "big_game",
    "water_bodies": {"sea": 1.0, "river": 0.0, "lake": 0.0, "pond": 0.0, "swamp": 0.0, "puddle": 0.0},
    "weight_g": {"min": 20000, "max": 400000, "mean": 60000}, "length_cm": {"min": 100, "max": 300},
    # runs 5, not 6: §dive-cost gives a sounding fish three extra dives, and a tenth dive puts even a
    # 20 kg bluefin past what the angler can pump back inside the timeout (tools/check_dive_budget.py).
    "fight": {"strength": 1.0, "stamina": 1.0, "runs": 5, "pattern": "sounding", "aggression": 0.9},
    "ideal": {"rod": ["trolling", "boat"],
              "reel_size": 14000, "reel_tolerance": 1000,
              "line": {"type": "braid", "diameter_mm": 0.40, "tolerance_mm": 0.06},
              "rig": ["predator"], "groundbait": {"fraction": 1.00, "nutrition": 0.85},
              "bait": {"livebait": 1.1, "giant_spoon": 1.0, "octopus_jig": 0.9, "fish_strip": 0.9,
                       "castmaster": 0.8, "silicone": 0.6},
              "hook": {"ideal": 1, "tolerance": 1}, "requires_leader": False},
    "season": {"spring": 0.9, "summer": 1.2, "autumn": 1.3, "winter": 0.6},
    "time": {"dawn": 1.3, "day": 1.0, "dusk": 1.2, "night": 0.5},
    "weather": {"clear": 1.1, "rain": 1.0, "thunder": 0.7},
    "depth_pref": "mid", "distance_pref": {"min": 25, "max": 45},
    "habitat": {"depth_min": 10, "width_min": 24},
    "biomes": {"deep": 1.3, "temperate": 1.2, "cold": 1.0, "warm": 0.8, "ocean_biome": 0.5},
    "base": 0.24, "min_angler_level": 8},
   ("Bluefin tuna", "Синеперый тунец", "Синьоперий тунець"),
   ("Warm-blooded, four hundred kilos, and built like a torpedo. It takes livebait, a big swimbait or a "
    "trolled spoon, then goes straight down with the drag screaming — the fish every reel in the shop "
    "was designed against.",
    "Теплокровный, до четырёхсот килограммов и сложенный, как торпеда. Берёт живца, "
    "крупный свимбейт или троллинговую блесну — и тут же уходит вниз под визг "
    "фрикциона: рыба, под которую в магазине придумана каждая катушка.",
    "Теплокровний, до чотирьохсот кілограмів і складений, як торпеда. Бере живця, "
    "великий свімбейт або тролінгову блешню — і одразу йде вниз під виск "
    "фрикціона: риба, під яку в крамниці вигадана кожна котушка."),
   (5, 28, 40)),

 "loach": ("gudgeon", {
    "display": "Вьюн", "group": "cyprinid",
    "water_bodies": {"swamp": 1.2, "pond": 1.1, "river": 0.8, "lake": 0.7, "puddle": 0.5, "sea": 0.0},
    "weight_g": {"min": 20, "max": 150, "mean": 55}, "length_cm": {"min": 10, "max": 30},
    "fight": {"strength": 0.1, "stamina": 0.3, "runs": 1, "pattern": "steady", "aggression": 0.2},
    "ideal": {"rod": ["pole", "stick", "ultralight"],
              "reel_size": 1000, "reel_tolerance": 1000,
              "line": {"type": "mono", "diameter_mm": 0.12, "tolerance_mm": 0.04},
              "rig": ["float", "primitive"], "groundbait": {"fraction": 0.20, "nutrition": 0.50},
              "bait": {"bloodworm": 1.1, "worm": 1.0, "maggot": 0.8, "mormyshka": 0.6, "dough": 0.4},
              "hook": {"ideal": 16, "tolerance": 2}, "requires_leader": False},
    "season": {"spring": 1.1, "summer": 1.2, "autumn": 1.0, "winter": 0.5},
    "time": {"dawn": 1.1, "day": 0.5, "dusk": 1.3, "night": 1.4},
    # The weather fish: it goes restless before a storm, which is the whole reason it was kept in jars.
    "weather": {"clear": 0.8, "rain": 1.3, "thunder": 1.4},
    "depth_pref": "bottom", "distance_pref": {"min": 1, "max": 8},
    "habitat": {"depth_min": 1, "depth_max": 4, "width_min": 2},
    "biomes": {"temperate": 1.1, "warm": 1.0, "cold": 0.8, "swamp": 1.2},
    "base": 1.0, "min_angler_level": 0},
   ("Loach", "Вьюн", "В’юн"),
   ("The weather fish: a finger of a fish that lies in the mud of ponds and swamps and starts fidgeting "
    "hours before a storm. Bloodworm or a piece of worm on the bottom after dark, on the lightest tackle "
    "you own — and the best livebait a pike ever saw.",
    "Рыба-барометр: похожий на червяка обитатель ила прудов и болот, который "
    "начинает метаться за несколько часов до грозы. Мотыль или кусочек червя со дна "
    "после темноты, самая лёгкая снасть, что у вас есть, — и лучший живец, какой "
    "видела щука.",
    "Риба-барометр: схожий на черв’яка мешканець мулу ставків і боліт, який "
    "починає метатися за кілька годин до грози. Мотиль або шматочок черв’яка з дна "
    "після темряви, найлегша снасть, що у вас є, — і найкращий живець, якого "
    "бачила щука."),
   (1, 1, 2)),

 "whale_shark": ("frilled_shark", {
    "display": "Китовая акула", "group": "big_game",
    "water_bodies": {"sea": 1.0, "river": 0.0, "lake": 0.0, "pond": 0.0, "swamp": 0.0, "puddle": 0.0},
    "weight_g": {"min": 500000, "max": 20000000, "mean": 2500000},
    "length_cm": {"min": 400, "max": 1200},
    # It does not fight, it leaves. Strength is per-kilo pull and a filter feeder has almost none of it
    # — but twenty tonnes through the giant taper still asks more of the tackle than anything else here.
    "fight": {"strength": 0.15, "stamina": 1.0, "runs": 8, "pattern": "relentless", "aggression": 0.1},
    "ideal": {"rod": ["trolling", "boat"],
              "reel_size": 14000, "reel_tolerance": 1000,
              "line": {"type": "braid", "diameter_mm": 0.60, "tolerance_mm": 0.05},
              "rig": ["catfish", "grusha"], "groundbait": {"fraction": 1.00, "nutrition": 1.00},
              # A plankton feeder wants nothing on a hook. Heavy chum is what brings it past, and even
              # then the best bait in the world scores a third of what an ordinary fish would take.
              "bait": {"fish_strip": 0.35, "livebait": 0.30},
              "hook": {"ideal": 1, "tolerance": 1}, "requires_leader": False},
    "season": {"spring": 1.0, "summer": 1.2, "autumn": 1.0, "winter": 0.6},
    "time": {"dawn": 1.1, "day": 1.2, "dusk": 1.0, "night": 0.5},
    "weather": {"clear": 1.2, "rain": 0.8, "thunder": 0.4},
    "depth_pref": "surface", "distance_pref": {"min": 35, "max": 45},
    "habitat": {"depth_min": 14, "width_min": 28},
    "biomes": {"deep": 1.5, "warm": 1.4, "ocean_biome": 0.3},
    "base": 0.03, "min_angler_level": 12},
   ("Whale shark", "Китовая акула", "Китова акула"),
   ("Twenty tonnes of shark that eats plankton. It has no interest in anything on a hook, so the only "
    "way to meet one is to chum the warm deep heavily and wait — for a very long time. It will not "
    "fight you; it will simply keep swimming, and no tackle on earth stops that.",
    "Двадцать тонн акулы, которая питается планктоном. Ей нет дела ни до чего "
    "на крючке, поэтому единственный способ встретиться — обильно закормить тёплую "
    "глубину и ждать. Очень долго. Она не станет сопротивляться — она просто поплывёт "
    "дальше, и снасти, которая это остановит, не существует.",
    "Двадцять тонн акули, яка живиться планктоном. Їй немає діла ні до чого "
    "на гачку, тож єдиний спосіб зустрітися — рясно загодувати прикормкою теплу "
    "глибину й чекати. Дуже довго. Вона не опиратиметься — вона просто попливе "
    "далі, і снасті, яка це зупинить, не існує."),
   (5, 40, 60)),

 "nelma": ("taimen", {
    "display": "Нельма", "group": "salmonid",
    "water_bodies": {"river": 1.1, "lake": 0.9, "sea": 0.2, "pond": 0.0, "swamp": 0.0, "puddle": 0.0},
    "weight_g": {"min": 2000, "max": 30000, "mean": 5000}, "length_cm": {"min": 40, "max": 130},
    "fight": {"strength": 0.8, "stamina": 0.85, "runs": 4, "pattern": "burst", "aggression": 0.7},
    "ideal": {"rod": ["spinning"],
              "reel_size": 4000, "reel_tolerance": 1500,
              "line": {"type": "braid", "diameter_mm": 0.20, "tolerance_mm": 0.05},
              "rig": ["predator"], "groundbait": {"fraction": 0.70, "nutrition": 0.60},
              "bait": {"spoon": 1.1, "spinner": 1.0, "livebait": 0.95, "castmaster": 0.95,
                       "wobbler": 0.85, "silicone": 0.7, "jig": 0.7},
              "hook": {"ideal": 4, "tolerance": 2}, "requires_leader": False},
    "season": {"spring": 0.9, "summer": 0.8, "autumn": 1.5, "winter": 0.7},
    "time": {"dawn": 1.3, "day": 1.0, "dusk": 1.3, "night": 0.6},
    "weather": {"clear": 1.0, "rain": 1.1, "thunder": 0.8},
    "depth_pref": "mid", "distance_pref": {"min": 10, "max": 35},
    "habitat": {"depth_min": 3, "width_min": 14},
    "biomes": {"cold": 1.3, "taiga": 1.2, "mountain": 0.8, "temperate": 0.5},
    "base": 0.40, "min_angler_level": 6},
   ("Nelma", "Нельма", "Нельма"),
   ("A whitefish that turned predator: silver, deep-bodied, up to thirty kilos, hunting smelt in the "
    "cold rivers of the north. A spoon or a heavy spinner worked fast, or livebait — best in autumn, "
    "when it runs upriver to spawn.",
    "Сиг, который стал хищником: серебряная, высокотелая, до тридцати килограммов, "
    "охотится за корюшкой в холодных северных реках. Блесна или тяжёлая вертушка на "
    "быстрой проводке либо живец — лучше всего осенью, когда она идёт вверх по "
    "реке на нерест.",
    "Сиг, що став хижаком: срібляста, високотіла, до тридцяти кілограмів, "
    "полює на корюшку в холодних північних річках. Блешня або важка вертушка на "
    "швидкій проводці чи живець — найкраще восени, коли вона йде вгору "
    "річкою на нерест."),
   (5, 13, 24)),

 "ocean_sunfish": ("ray", {
    "display": "Луна-рыба", "group": "sea",
    "water_bodies": {"sea": 1.0, "river": 0.0, "lake": 0.0, "pond": 0.0, "swamp": 0.0, "puddle": 0.0},
    "weight_g": {"min": 100000, "max": 1000000, "mean": 220000},
    "length_cm": {"min": 100, "max": 330},
    "fight": {"strength": 0.35, "stamina": 0.9, "runs": 2, "pattern": "steady", "aggression": 0.1},
    "ideal": {"rod": ["boat", "surf"],
              "reel_size": 12000, "reel_tolerance": 2000,
              "line": {"type": "braid", "diameter_mm": 0.40, "tolerance_mm": 0.10},
              "rig": ["predator", "grusha"], "groundbait": {"fraction": 0.90, "nutrition": 0.50},
              # It eats jellyfish, so what fools it is anything soft and translucent.
              "bait": {"octopus_jig": 1.0, "silicone": 0.85, "fish_strip": 0.6, "livebait": 0.35},
              "hook": {"ideal": 2, "tolerance": 2}, "requires_leader": False},
    "season": {"spring": 1.0, "summer": 1.3, "autumn": 1.1, "winter": 0.5},
    "time": {"dawn": 1.0, "day": 1.3, "dusk": 1.0, "night": 0.4},
    "weather": {"clear": 1.3, "rain": 0.8, "thunder": 0.5},
    "depth_pref": "surface", "distance_pref": {"min": 25, "max": 45},
    "habitat": {"depth_min": 10, "width_min": 24},
    "biomes": {"deep": 1.3, "warm": 1.1, "temperate": 1.0, "ocean_biome": 0.5},
    "base": 0.20, "min_angler_level": 8},
   ("Ocean sunfish", "Луна-рыба", "Місяць-риба"),
   ("A tonne of fish that is mostly head, drifting on its side at the surface to warm up between dives "
    "after jellyfish. Offer it something soft and translucent — an octopus jig, a soft plastic — and "
    "expect no fight at all: it is simply very, very heavy.",
    "Тонна рыбы, состоящей в основном из головы, дрейфует на боку у поверхности, "
    "отогреваясь между погружениями за медузами. Предложите ей что-нибудь мягкое "
    "и прозрачное — октопус-джиг, силикон — и не ждите борьбы: она просто "
    "очень, очень тяжёлая.",
    "Тонна риби, що складається переважно з голови, дрейфує на боці біля поверхні, "
    "відігріваючись між зануреннями по медуз. Запропонуйте їй щось м’яке "
    "й прозоре — октопус-джиґ, силікон — і не чекайте боротьби: вона просто "
    "дуже, дуже важка."),
   (5, 16, 28)),

 "pollock": ("saithe", {
    "display": "Минтай", "group": "sea",
    "water_bodies": {"sea": 1.2, "river": 0.0, "lake": 0.0, "pond": 0.0, "swamp": 0.0, "puddle": 0.0},
    "weight_g": {"min": 500, "max": 15000, "mean": 1800}, "length_cm": {"min": 25, "max": 90},
    "fight": {"strength": 0.7, "stamina": 0.7, "runs": 2, "pattern": "active_then_passive",
              "aggression": 0.5},
    "ideal": {"rod": ["boat", "sea_spin"],
              "reel_size": 8000, "reel_tolerance": 2000,
              "line": {"type": "braid", "diameter_mm": 0.22, "tolerance_mm": 0.06},
              "rig": ["predator", "grusha"], "groundbait": {"fraction": 0.70, "nutrition": 0.70},
              "bait": {"jig": 1.05, "livebait": 1.0, "fish_strip": 0.95, "silicone": 0.85,
                       "octopus_jig": 0.85, "castmaster": 0.8, "giant_spoon": 0.7},
              "hook": {"ideal": 4, "tolerance": 2}, "requires_leader": False},
    "season": {"spring": 1.1, "summer": 0.8, "autumn": 1.2, "winter": 1.3},
    "time": {"dawn": 1.2, "day": 1.0, "dusk": 1.2, "night": 0.8},
    "weather": {"clear": 0.9, "rain": 1.1, "thunder": 1.0},
    "depth_pref": "mid", "distance_pref": {"min": 15, "max": 45},
    "habitat": {"depth_min": 4, "width_min": 16},
    "biomes": {"cold": 1.4, "deep": 1.1, "ocean_biome": 0.9, "temperate": 0.6},
    "base": 0.75, "min_angler_level": 4},
   ("Pollock", "Минтай", "Мінтай"),
   ("The cold northern shoals, in numbers no other sea fish here matches. Drop a jig or a blade down to "
    "the shoal and lift: fifteen kilos is the very top end, a kilo and a half is most days, and dinner "
    "is every single time.",
    "Холодные северные стаи, каких нет ни у одной здешней морской рыбы. Опустите "
    "к стае джиг или цикаду и подбрасывайте: пятнадцать килограммов — это самый "
    "предел, обычно полтора, а ужин — каждый раз.",
    "Холодні північні зграї, яких немає в жодної іншої тутешньої морської риби. Опустіть "
    "до зграї джиг або цикаду й підкидайте: п’ятнадцять кілограмів — це вже "
    "межа, зазвичай півтора, а вечеря — щоразу."),
   (4, 4, 8)),

 "red_piranha": ("oscar", {
    "display": "Краснобрюхая пиранья", "group": "predator",
    "water_bodies": {"river": 1.1, "swamp": 0.9, "lake": 0.8, "pond": 0.5, "sea": 0.0, "puddle": 0.0},
    "weight_g": {"min": 300, "max": 4000, "mean": 900}, "length_cm": {"min": 15, "max": 45},
    "fight": {"strength": 0.65, "stamina": 0.5, "runs": 3, "pattern": "aggressive", "aggression": 1.0},
    "ideal": {"rod": ["ultralight", "spinning", "pole"],
              "reel_size": 2000, "reel_tolerance": 1000,
              "line": {"type": "mono", "diameter_mm": 0.20, "tolerance_mm": 0.06},
              "rig": ["float", "predator"], "groundbait": {"fraction": 0.55, "nutrition": 0.85},
              "bait": {"fish_strip": 1.1, "chicken_liver": 1.0, "livebait": 0.95, "worm": 0.7,
                       "silicone": 0.6, "spinner": 0.5},
              "hook": {"ideal": 8, "tolerance": 2}, "requires_leader": True},
    "season": {"spring": 1.0, "summer": 1.2, "autumn": 1.0, "winter": 0.4},
    "time": {"dawn": 1.1, "day": 1.3, "dusk": 1.1, "night": 0.4},
    "weather": {"clear": 1.1, "rain": 1.0, "thunder": 0.8},
    "depth_pref": "mid", "distance_pref": {"min": 3, "max": 20},
    "habitat": {"depth_min": 1, "depth_max": 12, "width_min": 5},
    "biomes": {"warm": 1.3, "jungle": 1.4, "swamp": 1.0},
    "base": 0.70, "min_angler_level": 4},
   ("Red piranha", "Краснобрюхая пиранья", "Червоночерева піранья"),
   ("A shoal fish the size of your hand with a bite that goes through monofilament like scissors — fish "
    "it on a leader or lose the hook on the first take. A strip of fish, liver or livebait in warm "
    "jungle water, in daylight, wherever the shoal is holding.",
    "Стайная рыба размером с ладонь и с укусом, который перекусывает леску как "
    "ножницами, — ловите с поводком или потеряете крючок на первой же поклёвке. "
    "Полоска рыбы, печень или живец в тёплой воде джунглей, днём, там, где "
    "держится стая.",
    "Зграйна риба завбільшки з долоню й з укусом, який перекушує волосінь, наче "
    "ножиці, — ловіть із повідцем або втратите гачок на першій же поклівці. "
    "Смужка риби, печінка або живець у теплій воді джунглів, удень, там, де "
    "тримається зграя."),
   (3, 3, 5)),

 "tiger_shark": ("bull_shark", {
    "display": "Тигровая акула", "group": "big_game",
    "water_bodies": {"sea": 1.1, "river": 0.0, "lake": 0.0, "pond": 0.0, "swamp": 0.0, "puddle": 0.0},
    "weight_g": {"min": 50000, "max": 800000, "mean": 140000},
    "length_cm": {"min": 200, "max": 500},
    "fight": {"strength": 0.85, "stamina": 1.0, "runs": 5, "pattern": "relentless", "aggression": 0.9},
    "ideal": {"rod": ["boat", "surf", "trolling"],
              "reel_size": 14000, "reel_tolerance": 1000,
              "line": {"type": "braid", "diameter_mm": 0.45, "tolerance_mm": 0.08},
              "rig": ["catfish", "grusha"], "groundbait": {"fraction": 1.00, "nutrition": 0.95},
              "bait": {"fish_strip": 1.15, "livebait": 1.1, "chicken_liver": 0.85, "octopus_jig": 0.7,
                       "giant_spoon": 0.6},
              "hook": {"ideal": 1, "tolerance": 1}, "requires_leader": True},
    "season": {"spring": 1.0, "summer": 1.3, "autumn": 1.1, "winter": 0.4},
    "time": {"dawn": 1.2, "day": 0.7, "dusk": 1.3, "night": 1.5},
    "weather": {"clear": 1.0, "rain": 1.1, "thunder": 0.9},
    "depth_pref": "mid", "distance_pref": {"min": 20, "max": 45},
    "habitat": {"depth_min": 8, "width_min": 24},
    "biomes": {"warm": 1.3, "ocean_biome": 1.0, "deep": 1.0, "beach": 0.9},
    "base": 0.20, "min_angler_level": 9},
   ("Tiger shark", "Тигровая акула", "Тигрова акула"),
   ("The rubbish bin of the sea, and eight hundred kilos of it. It hunts the shallows at night and eats "
    "anything that smells: a strip of fish or livebait on the bottom, a wire leader, and a drag you have "
    "already checked twice.",
    "Мусорный бак океана — и в нём восемьсот килограммов. Охотится на мелководье "
    "ночью и ест всё, что пахнет: полоска рыбы или живец у дна, стальной поводок и "
    "фрикцион, который вы уже дважды проверили.",
    "Смітник океану — і в ньому вісімсот кілограмів. Полює на мілководді "
    "вночі та їсть усе, що пахне: смужка риби або живець біля дна, сталевий повідець і "
    "фрикціон, який ви вже двічі перевірили."),
   (5, 26, 38)),
}
REPLACE = {"water_bodies", "weight_g", "length_cm", "fight", "season", "time", "weather",
           "distance_pref", "habitat", "biomes", "rod", "rig", "bait", "hook", "line", "groundbait",
           "legendary"}


def merge(base, patch):
    out = dict(base)
    for k, v in patch.items():
        if isinstance(v, dict) and isinstance(out.get(k), dict) and k not in REPLACE:
            out[k] = merge(out[k], v)
        else:
            out[k] = v
    return out


def sub_id(text, old, new):
    return re.sub(r"(?<![A-Za-z0-9_])" + re.escape(old) + r"(?![A-Za-z0-9_])", new, text)


def write(path, text):
    io.open(path, "w", encoding="utf-8", newline="\n").write(text)


def first_dir(base, *cands):
    for c in cands:
        p = os.path.join(base, c)
        if os.path.isdir(p):
            return p
    return None


def first_file(base, *cands):
    for c in cands:
        p = os.path.join(base, c)
        if os.path.isfile(p):
            return p
    return None


def wire_tree(tree):
    name = os.path.basename(tree)
    assets, data = os.path.join(tree, A), os.path.join(tree, D)
    prof_dir = os.path.join(data, "fish_profiles")
    # Cutting recipes were a Farmer's Delight extra; no tree carries them any more. Skip, do not die.
    rec_dir = first_dir(data, "recipe/cutting", "recipes/cutting")
    made = []

    for sp, (donor, patch, names, desc, trade) in SPECIES.items():
        base = json.load(io.open(os.path.join(prof_dir, donor + ".json"), encoding="utf-8"))
        prof = merge(base, patch)
        # add_latin.py / add_spawn.py own those two LINES and insert them textually as one-liners;
        # a json.dumps of them here comes out multi-line, which add_spawn.py's line regex then shreds.
        prof.pop("latin", None)
        prof.pop("spawn", None)
        write(os.path.join(prof_dir, sp + ".json"),
              json.dumps(prof, ensure_ascii=False, indent=2) + "\n")
        for sub in ("models/item", "models/item/fish_icon"):
            src = io.open(os.path.join(assets, sub, donor + ".json"), encoding="utf-8").read()
            write(os.path.join(assets, sub, sp + ".json"), sub_id(src, donor, sp))
        if rec_dir and os.path.isfile(os.path.join(rec_dir, donor + ".json")):
            rec = io.open(os.path.join(rec_dir, donor + ".json"), encoding="utf-8").read()
            write(os.path.join(rec_dir, sp + ".json"), sub_id(rec, donor, sp))
    made.append("profiles+models")

    # ---- art: the sprites and journal pictures live in MAIN and are copied out, already compressed ----
    if tree != MAIN:
        copied = 0
        for sub in ("textures/item/fish", "textures/gui/journal/fish"):
            src_dir, dst_dir = os.path.join(MAIN, A, sub), os.path.join(assets, sub)
            for f in os.listdir(src_dir):
                if not f.endswith(".png"):
                    continue
                s_p, d_p = os.path.join(src_dir, f), os.path.join(dst_dir, f)
                if not os.path.isfile(d_p) or os.path.getsize(d_p) != os.path.getsize(s_p) \
                        or io.open(d_p, "rb").read() != io.open(s_p, "rb").read():
                    shutil.copy2(s_p, d_p)
                    copied += 1
        made.append("art x%d" % copied)

    # ---- lang ----
    for idx, loc in enumerate(("en_us", "ru_ru", "uk_ua")):
        p = os.path.join(assets, "lang", loc + ".json")
        txt = io.open(p, encoding="utf-8").read()
        add = []
        for sp, (_, _, names, desc, _) in SPECIES.items():
            for prefix, val in (("item", names[idx]), ("fish", names[idx]), ("fishdesc", desc[idx])):
                key = '"%s.riverfishing.%s"' % (prefix, sp)
                if key not in txt:
                    add.append('  %s: %s,' % (key, json.dumps(val, ensure_ascii=False)))
        if add:
            anchor = '  "item.riverfishing.bleak":'
            assert txt.count(anchor) == 1, (p, anchor)
            txt = txt.replace(anchor, "\n".join(add) + "\n" + anchor)
            write(p, txt)
            json.load(io.open(p, encoding="utf-8"))
    made.append("lang")

    # ---- registry roster ----
    mi = os.path.join(tree, "common/src/main/java/com/riverfishing/registry/ModItems.java")
    s = io.open(mi, encoding="utf-8").read()
    first = list(SPECIES)[0]
    if '"%s"' % first not in s:
        ids = list(SPECIES)
        lines = ["            // " + WAVE]
        for i in range(0, len(ids), 4):
            lines.append("            " + ", ".join('"%s"' % x for x in ids[i:i + 4]) + ",")
        m = re.search(r"(public static final String\[\] FISH_SPECIES = \{\n)", s)
        assert m, mi
        s = s[:m.end()] + "\n".join(lines) + "\n" + s[m.end():]
        write(mi, s)
        made.append("roster")

    # ---- trades: Java table on 1.20.1/1.21.1, a data generator on 26.x ----
    mv = os.path.join(tree, "common/src/main/java/com/riverfishing/registry/ModVillagers.java")
    v = io.open(mv, encoding="utf-8").read()
    anchor = '        buyPrime(fish, 5, "halibut", 22, 34);\n'
    if anchor in v:
        if '"%s"' % first not in v:
            block = ["", "        // " + TAG + ": priced against the fish they swim beside."]
            for sp, (_, _, _, _, (tier, em, xp)) in SPECIES.items():
                block.append('        buyPrime(fish, %d, "%s", %d, %d);' % (tier, sp, em, xp))
            write(mv, v.replace(anchor, anchor + "\n".join(block) + "\n"))
            made.append("trades(java)")
    else:
        gen = first_file(tree, "tools/gen_villager_trades.py")
        if gen:
            g = io.open(gen, encoding="utf-8").read()
            if '"%s"' % first not in g:
                # Scoped to the FISH table on purpose: the tier keys "1:".."5:" also head the POOL
                # table of gear, and an unscoped search put seven species inside level 1's worm
                # listing, which stopped being valid Python the moment it landed.
                fm = re.search(r"\nFISH = \{", g)
                assert fm, "no FISH table in " + gen
                fstart = fm.end()
                fend = g.index("\n}", fstart)
                seg = g[fstart:fend]
                for tier in sorted({t[0] for (_, _, _, _, t) in SPECIES.values()}, reverse=True):
                    rows = [(sp, t[1], t[2]) for sp, (_, _, _, _, t) in SPECIES.items() if t[0] == tier]
                    tm = re.search(r"\n    %d: \[" % tier, seg)
                    assert tm, "tier %d not in the FISH table" % tier
                    close = seg.index("],", tm.end())
                    # The last row of a tier carries no trailing comma, so the block has to bring one:
                    # without it the two tuples juxtapose into a CALL, which parses fine and dies at
                    # runtime with "'tuple' object is not callable".
                    head = seg[:close].rstrip()
                    if not head.endswith(","):
                        head += ","
                    entry = ("\n        # " + TAG + "\n        "
                             + ", ".join('("%s", %d, %d)' % r for r in rows))
                    seg = head + entry + seg[close:]
                write(gen, g[:fstart] + seg + g[fend:])
                made.append("trades(data)")

    # ---- fishes item tag: brought up to the FULL roster, not just this wave ----
    roster_src = io.open(mi, encoding="utf-8").read()
    body = re.search(r"FISH_SPECIES = \{(.*?)\};", roster_src, re.S).group(1)
    roster = re.findall(r'"([a-z_]+)"', body)
    tag_p = first_file(data, "tags/item/fishes.json", "tags/items/fishes.json")
    if tag_p:
        tag = json.load(io.open(tag_p, encoding="utf-8"))
        missing = ["riverfishing:" + x for x in roster if "riverfishing:" + x not in set(tag["values"])]
        if missing:
            tag["values"] = tag["values"] + missing
            write(tag_p, json.dumps(tag, ensure_ascii=False, indent=2) + "\n")
            made.append("tag+%d" % len(missing))

    # ---- generators this tree owns: sprite bounds, and 26.x's per-species item definitions ----
    for script in ("tools/add_latin.py", "tools/add_spawn.py", "tools/add_lure_scores.py",
                   "tools/gen_fish_bounds.py", "tools/gen_dynamic_icons.py",
                   "tools/gen_villager_trades.py"):
        if os.path.isfile(os.path.join(tree, script)):
            r = subprocess.run(["py", script], cwd=tree, capture_output=True, text=True)
            made.append(os.path.basename(script).replace("gen_", "").replace("add_", "+").replace(".py", "")
                        + ("" if r.returncode == 0 else " FAILED"))
            if r.returncode != 0:
                print("    " + (r.stderr or r.stdout).strip().splitlines()[-1])

    print("  %-28s %s" % (name, ", ".join(made)))


def main():
    for tree in TREES:
        wire_tree(tree)
    print("\n%d species across %d trees" % (len(SPECIES), len(TREES)))
    return 0


if __name__ == "__main__":
    sys.exit(main())
