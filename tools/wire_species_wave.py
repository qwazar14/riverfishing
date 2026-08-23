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

WAVE = ("§giants-and-minnows (0.8.0): the top of the ladder and the bottom of it — six fish that outgrow\n"
        "            // every rod in the shop, and five you can catch with a stick and a maggot.")

# id: (donor, profile patch, (en, ru, uk) name, (en, ru, uk) journal description, (trade tier, emeralds, xp))
SPECIES = {
 "arapaima": ("tarpon", {
    "display": "Арапайма", "group": "big_game",
    "water_bodies": {"river": 1.2, "lake": 1.0, "swamp": 0.9, "pond": 0.3, "sea": 0.0, "puddle": 0.0},
    "weight_g": {"min": 20000, "max": 180000, "mean": 45000}, "length_cm": {"min": 120, "max": 300},
    "fight": {"strength": 0.95, "stamina": 0.85, "runs": 4, "pattern": "greyhounding", "aggression": 0.8},
    "ideal": {"rod": ["boat", "sea_spin"], "reel_size": 10000, "reel_tolerance": 2000,
              "line": {"type": "braid", "diameter_mm": 0.45, "tolerance_mm": 0.08},
              "rig": ["predator", "catfish"], "groundbait": {"fraction": 0.95, "nutrition": 0.80},
              "bait": {"livebait": 1.0, "fish_strip": 0.9, "giant_spoon": 0.85, "wobbler": 0.75, "silicone": 0.6},
              "hook": {"ideal": 1, "tolerance": 2}, "requires_leader": True},
    "season": {"spring": 1.1, "summer": 1.2, "autumn": 1.0, "winter": 0.3},
    "time": {"dawn": 1.2, "day": 0.9, "dusk": 1.2, "night": 0.8},
    "weather": {"clear": 1.0, "rain": 1.2, "thunder": 0.9},
    "depth_pref": "surface", "distance_pref": {"min": 10, "max": 35},
    "habitat": {"depth_min": 3, "width_min": 14}, "biomes": {"warm": 1.4, "swamp": 1.0},
    "base": 0.22, "min_angler_level": 10, "legendary": {"weight_g": 175000, "chance": 0.004}},
   ("Arapaima", "Арапайма", "Арапайма"),
   ("Three metres of armour-plated Amazon that has to surface for air — watch for the roll, then put the "
    "bait where it went down. Livebait and the heaviest gear you own; it jumps like a tarpon and weighs four times more.",
    "Три метра бронированной амазонской чешуи, которой нужно всплывать за воздухом — заметьте всплеск и "
    "кладите приманку туда. Живец и самая тяжёлая снасть; свечит как тарпон, а весит вчетверо больше.",
    "Три метри броньованої амазонської луски, якій треба спливати по повітря — помітьте сплеск і кладіть "
    "приманку туди. Живець і найважча снасть; свічкує як тарпон, а важить учетверо більше."),
   (5, 26, 38)),

 "beluga": ("sturgeon", {
    "display": "Белуга", "group": "sturgeon",
    "water_bodies": {"river": 1.0, "lake": 0.3, "pond": 0.0, "swamp": 0.0, "sea": 1.0, "puddle": 0.0},
    "weight_g": {"min": 40000, "max": 600000, "mean": 90000}, "length_cm": {"min": 150, "max": 500},
    "fight": {"strength": 1.0, "stamina": 1.0, "runs": 6, "pattern": "sounding", "aggression": 0.5},
    "ideal": {"rod": ["boat", "bottom"], "reel_size": 14000, "reel_tolerance": 2000,
              "line": {"type": "braid", "diameter_mm": 0.55, "tolerance_mm": 0.10},
              "rig": ["catfish", "grusha"], "groundbait": {"fraction": 0.98, "nutrition": 0.82},
              "bait": {"livebait": 1.0, "fish_strip": 0.9, "chicken_liver": 0.85, "worm": 0.5},
              "hook": {"ideal": 1, "tolerance": 1}, "requires_leader": True},
    "season": {"spring": 1.2, "summer": 0.9, "autumn": 1.2, "winter": 0.5},
    "time": {"dawn": 1.1, "day": 0.6, "dusk": 1.2, "night": 1.4},
    "weather": {"clear": 1.0, "rain": 1.1, "thunder": 1.0},
    "depth_pref": "bottom", "distance_pref": {"min": 20, "max": 60},
    "habitat": {"depth_min": 6, "width_min": 26},
    "biomes": {"temperate": 1.0, "cold": 1.0, "ocean_biome": 0.9, "deep": 0.8},
    "base": 0.10, "min_angler_level": 12, "legendary": {"weight_g": 580000, "chance": 0.003}},
   ("Beluga sturgeon", "Белуга", "Білуга"),
   ("The Tsar-Fish herself: half a tonne of sturgeon that runs the sea and climbs the big rivers to spawn. "
    "Bottom tackle rated for a boat anchor, livebait, and a night with nothing else planned.",
    "Та самая царь-рыба: полтонны осетровой породы, что ходит морем и поднимается на нерест в большие "
    "реки. Донка под якорную нагрузку, живец и ночь, на которую больше ничего не планируйте.",
    "Та сама цар-риба: пів тонни осетрової породи, що ходить морем і підіймається на нерест у великі "
    "річки. Донка під якірне навантаження, живець і ніч, на яку більше нічого не плануйте."),
   (5, 30, 44)),

 "piraiba": ("catfish", {
    "display": "Пирайба", "group": "predator",
    "water_bodies": {"river": 1.3, "lake": 0.5, "swamp": 0.4, "pond": 0.1, "sea": 0.0, "puddle": 0.0},
    "weight_g": {"min": 15000, "max": 160000, "mean": 32000}, "length_cm": {"min": 100, "max": 280},
    "fight": {"strength": 0.95, "stamina": 1.0, "runs": 5, "pattern": "relentless", "aggression": 0.85},
    "ideal": {"rod": ["bottom", "carp", "boat"], "reel_size": 10000, "reel_tolerance": 2000,
              "line": {"type": "braid", "diameter_mm": 0.50, "tolerance_mm": 0.08},
              "rig": ["catfish", "grusha"], "groundbait": {"fraction": 0.96, "nutrition": 0.80},
              "bait": {"livebait": 1.0, "fish_strip": 0.95, "chicken_liver": 0.85, "worm": 0.5},
              "hook": {"ideal": 1, "tolerance": 2}, "requires_leader": True},
    "season": {"spring": 1.1, "summer": 1.2, "autumn": 1.0, "winter": 0.3},
    "time": {"dawn": 1.0, "day": 0.4, "dusk": 1.3, "night": 1.6},
    "weather": {"clear": 0.9, "rain": 1.3, "thunder": 1.1},
    "depth_pref": "bottom", "distance_pref": {"min": 15, "max": 45},
    "habitat": {"depth_min": 5, "width_min": 18}, "biomes": {"warm": 1.4, "swamp": 0.8},
    "base": 0.18, "min_angler_level": 10, "legendary": {"weight_g": 155000, "chance": 0.004}},
   ("Piraiba", "Пирайба", "Пірайба"),
   ("The Amazon's own catfish, and the one the river stories are about — it hunts the deep channel after "
    "dark. Heavy ledger, livebait or a cut strip, and a leader, because it swallows.",
    "Амазонский сом — тот самый, про которого рассказывают на реке. Охотится в глубоком русле после "
    "заката. Тяжёлая донка, живец или резка, и обязательно поводок: заглатывает.",
    "Амазонський сом — той самий, про якого розповідають на річці. Полює в глибокому руслі після заходу "
    "сонця. Важка донка, живець або різка, і обов'язково повідець: заковтує."),
   (5, 22, 34)),

 "goliath_grouper": ("halibut", {
    "display": "Голиафовый групер", "group": "big_game",
    "water_bodies": {"river": 0.0, "lake": 0.0, "pond": 0.0, "swamp": 0.0, "sea": 1.2, "puddle": 0.0},
    "weight_g": {"min": 20000, "max": 320000, "mean": 55000}, "length_cm": {"min": 100, "max": 250},
    "fight": {"strength": 1.0, "stamina": 0.75, "runs": 3, "pattern": "sounding", "aggression": 0.9},
    "ideal": {"rod": ["boat", "surf"], "reel_size": 12000, "reel_tolerance": 2000,
              "line": {"type": "braid", "diameter_mm": 0.55, "tolerance_mm": 0.10},
              "rig": ["catfish", "predator"], "groundbait": {"fraction": 0.97, "nutrition": 0.78},
              "bait": {"livebait": 1.0, "fish_strip": 0.95, "octopus_jig": 0.8, "giant_spoon": 0.5},
              "hook": {"ideal": 1, "tolerance": 1}, "requires_leader": True},
    "season": {"spring": 1.0, "summer": 1.2, "autumn": 1.1, "winter": 0.7},
    "time": {"dawn": 1.2, "day": 1.0, "dusk": 1.2, "night": 1.1},
    "weather": {"clear": 1.0, "rain": 1.0, "thunder": 1.0},
    "depth_pref": "bottom", "distance_pref": {"min": 10, "max": 35},
    "habitat": {"depth_min": 5, "width_min": 20},
    "biomes": {"warm": 1.4, "ocean_biome": 1.0, "beach": 0.9},
    "base": 0.20, "min_angler_level": 10, "legendary": {"weight_g": 310000, "chance": 0.004}},
   ("Goliath grouper", "Голиафовый групер", "Голіафовий групер"),
   ("A third of a tonne of reef that decided to eat your bait. The whole fight is the first ten seconds: "
    "stop it reaching the hole it lives in, or you are pulling on a rock with your line inside it.",
    "Треть тонны рифа, решившая съесть вашу наживку. Весь бой — первые десять секунд: не пустите его в "
    "нору, иначе будете тянуть камень, внутри которого ваша леска.",
    "Третина тонни рифу, що вирішила з'їсти вашу наживку. Увесь бій — перші десять секунд: не пустіть "
    "його в нору, інакше тягтимете камінь, усередині якого ваша волосінь."),
   (5, 24, 36)),

 "bull_shark": ("mako", {
    "display": "Тупорылая акула", "group": "big_game",
    "water_bodies": {"river": 0.6, "lake": 0.25, "pond": 0.0, "swamp": 0.0, "sea": 1.1, "puddle": 0.0},
    "weight_g": {"min": 30000, "max": 230000, "mean": 65000}, "length_cm": {"min": 150, "max": 350},
    "fight": {"strength": 1.0, "stamina": 0.95, "runs": 5, "pattern": "aggressive", "aggression": 1.0},
    "ideal": {"rod": ["boat", "surf", "trolling"], "reel_size": 12000, "reel_tolerance": 2000,
              "line": {"type": "braid", "diameter_mm": 0.50, "tolerance_mm": 0.08},
              "rig": ["predator", "catfish"], "groundbait": {"fraction": 1.00, "nutrition": 0.76},
              "bait": {"livebait": 1.0, "fish_strip": 1.0, "octopus_jig": 0.75, "giant_spoon": 0.7},
              "hook": {"ideal": 1, "tolerance": 1}, "requires_leader": True},
    "season": {"spring": 1.0, "summer": 1.3, "autumn": 1.1, "winter": 0.5},
    "time": {"dawn": 1.3, "day": 0.9, "dusk": 1.3, "night": 1.1},
    "weather": {"clear": 1.0, "rain": 1.1, "thunder": 1.0},
    "depth_pref": "mid", "distance_pref": {"min": 15, "max": 40},
    "habitat": {"depth_min": 4, "width_min": 18},
    "biomes": {"warm": 1.3, "ocean_biome": 1.0, "beach": 1.0},
    "base": 0.24, "min_angler_level": 9, "legendary": {"weight_g": 225000, "chance": 0.004}},
   ("Bull shark", "Тупорылая акула", "Тупорила акула"),
   ("The one shark that swims UP the river — brackish, fresh, it does not care, which is why it turns up "
    "where no shark should be. Wire trace, the heaviest drag you own, and no hands in the water.",
    "Единственная акула, которая поднимается ВВЕРХ по реке — солоноватая вода, пресная, ей всё равно; "
    "потому и появляется там, где акул быть не должно. Поводок, максимальный фрикцион, руки из воды.",
    "Єдина акула, яка підіймається ВГОРУ по річці — солонувата вода, прісна, їй байдуже; тому й "
    "з'являється там, де акул бути не повинно. Повідець, максимальний фрикціон, руки з води."),
   (5, 24, 36)),

 "frilled_shark": ("conger", {
    "display": "Плащеносная акула", "group": "big_game",
    "water_bodies": {"river": 0.0, "lake": 0.0, "pond": 0.0, "swamp": 0.0, "sea": 1.0, "puddle": 0.0},
    "weight_g": {"min": 8000, "max": 50000, "mean": 16000}, "length_cm": {"min": 90, "max": 200},
    "fight": {"strength": 0.7, "stamina": 0.95, "runs": 3, "pattern": "sounding", "aggression": 0.55},
    "ideal": {"rod": ["boat"], "reel_size": 10000, "reel_tolerance": 2000,
              "line": {"type": "braid", "diameter_mm": 0.40, "tolerance_mm": 0.08},
              "rig": ["catfish", "predator"], "groundbait": {"fraction": 0.90, "nutrition": 0.70},
              "bait": {"fish_strip": 1.0, "octopus_jig": 0.95, "livebait": 0.85},
              "hook": {"ideal": 1, "tolerance": 2}, "requires_leader": True},
    "season": {"spring": 1.0, "summer": 0.8, "autumn": 1.0, "winter": 1.1},
    "time": {"dawn": 1.0, "day": 0.25, "dusk": 1.2, "night": 1.6},
    "weather": {"clear": 1.0, "rain": 1.0, "thunder": 1.0},
    "depth_pref": "bottom", "distance_pref": {"min": 25, "max": 60},
    "habitat": {"depth_min": 14, "width_min": 28},
    "biomes": {"deep": 1.6, "cold": 1.0, "ocean_biome": 0.7},
    "base": 0.06, "min_angler_level": 11, "legendary": {"weight_g": 48000, "chance": 0.003}},
   ("Frilled shark", "Плащеносная акула", "Плащоносна акула"),
   ("A living fossil with three hundred backward-curving teeth, hauled up out of water that never sees the "
    "sun. Deep drops at night off a boat; most anglers never see one.",
    "Живое ископаемое с тремя сотнями загнутых внутрь зубов, поднятое из воды, куда не доходит солнце. "
    "Глубокий отвес ночью с лодки; большинство рыбаков не видят её никогда.",
    "Жива копалина з трьома сотнями загнутих усередину зубів, піднята з води, куди не сягає сонце. "
    "Глибокий відвіс уночі з човна; більшість рибалок не бачать її ніколи."),
   (5, 26, 38)),

 "golden_dorado": ("taimen", {
    "display": "Золотой дорадо", "group": "predator",
    "water_bodies": {"river": 1.3, "lake": 0.6, "swamp": 0.3, "pond": 0.2, "sea": 0.0, "puddle": 0.0},
    "weight_g": {"min": 1500, "max": 30000, "mean": 5500}, "length_cm": {"min": 40, "max": 120},
    "fight": {"strength": 0.9, "stamina": 0.8, "runs": 5, "pattern": "greyhounding", "aggression": 1.0},
    "ideal": {"rod": ["spinning", "sea_spin"], "reel_size": 4000, "reel_tolerance": 1000,
              "line": {"type": "braid", "diameter_mm": 0.28, "tolerance_mm": 0.06},
              "rig": ["predator"], "groundbait": {"fraction": 0.60, "nutrition": 0.70},
              "bait": {"wobbler": 1.0, "spoon": 0.9, "spinner": 0.9, "popper": 0.85, "crankbait": 0.8,
                       "silicone": 0.8, "livebait": 0.7},
              "hook": {"ideal": 2, "tolerance": 2}, "requires_leader": True},
    "season": {"spring": 1.2, "summer": 1.3, "autumn": 1.0, "winter": 0.2},
    "time": {"dawn": 1.4, "day": 0.9, "dusk": 1.4, "night": 0.5},
    "weather": {"clear": 1.0, "rain": 1.2, "thunder": 0.9},
    "depth_pref": "mid", "distance_pref": {"min": 8, "max": 30},
    "habitat": {"depth_min": 2, "width_min": 10}, "biomes": {"warm": 1.4, "swamp": 0.6},
    "base": 0.38, "min_angler_level": 6},
   ("Golden dorado", "Золотой дорадо", "Золотий дорадо"),
   ("The river tiger of South America: gold scales, a bulldog jaw and a habit of clearing the water three "
    "times per fight. Wobblers and spoons on a wire trace — it bites through everything else.",
    "Речной тигр Южной Америки: золотая чешуя, бульдожья челюсть и привычка трижды за бой выходить "
    "свечкой. Воблеры и колебалки на поводке — всё остальное перекусывает.",
    "Річковий тигр Південної Америки: золота луска, бульдожа щелепа і звичка тричі за бій виходити "
    "свічкою. Воблери й коливалки на повідці — усе інше перекушує."),
   (5, 12, 22)),

 "golden_crucian": ("crucian_carp", {
    "display": "Золотой карась", "group": "cyprinid",
    "water_bodies": {"river": 0.3, "lake": 0.9, "pond": 1.4, "swamp": 1.3, "sea": 0.0, "puddle": 0.5},
    "weight_g": {"min": 60, "max": 3000, "mean": 350}, "length_cm": {"min": 12, "max": 45},
    "fight": {"strength": 0.4, "stamina": 0.55, "runs": 2, "pattern": "steady", "aggression": 0.3},
    "ideal": {"rod": ["pole", "stick", "bamboo", "feeder"], "reel_size": 2000, "reel_tolerance": 1000,
              "line": {"type": "mono", "diameter_mm": 0.18, "tolerance_mm": 0.05},
              "rig": ["float", "primitive", "feeder"], "groundbait": {"fraction": 0.35, "nutrition": 0.60},
              "bait": {"worm": 1.0, "bread": 0.9, "dough": 0.9, "maggot": 0.85, "corn": 0.7, "pearl_barley": 0.6},
              "hook": {"ideal": 12, "tolerance": 2}},
    "season": {"spring": 1.0, "summer": 1.4, "autumn": 0.8, "winter": 0.05},
    "time": {"dawn": 1.3, "day": 0.9, "dusk": 1.3, "night": 0.6},
    "weather": {"clear": 1.0, "rain": 1.2, "thunder": 0.7},
    "depth_pref": "bottom", "distance_pref": {"min": 3, "max": 15},
    "habitat": {"depth_min": 1, "width_min": 5},
    "biomes": {"temperate": 1.0, "warm": 1.1, "swamp": 1.3},
    "base": 0.85, "min_angler_level": 2},
   ("Golden crucian", "Золотой карась", "Золотий карась"),
   ("The round bronze crucian of farm ponds — it survives water that kills everything else, freezing "
    "included. Float rod, worm or bread, and patience for the fussiest bite in the pond.",
    "Круглый бронзовый карась деревенских прудов — выживает там, где гибнет всё остальное, даже "
    "промерзая насквозь. Поплавок, червь или хлеб и терпение к самой капризной поклёвке пруда.",
    "Круглий бронзовий карась сільських ставків — виживає там, де гине все інше, навіть промерзаючи "
    "наскрізь. Поплавець, черв'як чи хліб і терпіння до найвередливішого клювання ставка."),
   (2, 2, 3)),

 "gorchak": ("bleak", {
    "display": "Горчак", "group": "cyprinid",
    "water_bodies": {"river": 0.9, "lake": 1.0, "pond": 1.2, "swamp": 0.8, "sea": 0.0, "puddle": 0.4},
    "weight_g": {"min": 3, "max": 30, "mean": 9}, "length_cm": {"min": 3, "max": 9},
    "fight": {"strength": 0.03, "stamina": 0.1, "runs": 1},
    "ideal": {"rod": ["pole", "stick", "ultralight"], "reel_size": 1000, "reel_tolerance": 1000,
              "line": {"type": "mono", "diameter_mm": 0.10, "tolerance_mm": 0.03},
              "rig": ["float", "primitive"], "groundbait": {"fraction": 0.10, "nutrition": 0.40},
              "bait": {"bloodworm": 1.0, "maggot": 1.0, "bread": 0.8, "dough": 0.7},
              "hook": {"ideal": 16, "tolerance": 1}},
    "season": {"spring": 1.1, "summer": 1.3, "autumn": 0.7, "winter": 0.0},
    "time": {"dawn": 1.0, "day": 1.2, "dusk": 1.0, "night": 0.0},
    "weather": {"clear": 1.2, "rain": 0.9, "thunder": 0.6},
    "depth_pref": "mid", "distance_pref": {"min": 2, "max": 10},
    "habitat": {"depth_min": 1, "depth_max": 3, "width_min": 4},
    "biomes": {"temperate": 1.0, "warm": 1.1, "swamp": 0.9},
    "base": 1.0},
   ("Bitterling", "Горчак", "Гірчак"),
   ("A thumb-length carp cousin that lays its eggs inside living mussels. Bitter enough that nobody eats "
    "it — smallest hook you own, one bloodworm, shallow water.",
    "Карповая мелочь с палец длиной, которая мечет икру внутрь живых ракушек. Горчит так, что никто его "
    "не ест — самый мелкий крючок, один мотыль и мелководье.",
    "Коропова дрібнота з палець завдовжки, що відкладає ікру всередину живих скойок. Гірчить так, що "
    "ніхто його не їсть — найдрібніший гачок, один мотиль і мілина."),
   (1, 1, 1)),

 "verkhovka": ("bleak", {
    "display": "Верховка", "group": "cyprinid",
    "water_bodies": {"river": 0.4, "lake": 1.0, "pond": 1.4, "swamp": 0.9, "sea": 0.0, "puddle": 0.9},
    "weight_g": {"min": 2, "max": 18, "mean": 6}, "length_cm": {"min": 3, "max": 8},
    "fight": {"strength": 0.02, "stamina": 0.1, "runs": 1},
    "ideal": {"rod": ["pole", "stick", "ultralight"], "reel_size": 1000, "reel_tolerance": 1000,
              "line": {"type": "mono", "diameter_mm": 0.10, "tolerance_mm": 0.03},
              "rig": ["float", "primitive"], "groundbait": {"fraction": 0.08, "nutrition": 0.38},
              "bait": {"maggot": 1.0, "bread": 0.95, "bloodworm": 0.85, "dough": 0.8},
              "hook": {"ideal": 16, "tolerance": 1}},
    "season": {"spring": 1.1, "summer": 1.4, "autumn": 0.6, "winter": 0.0},
    "time": {"dawn": 1.1, "day": 1.3, "dusk": 1.1, "night": 0.0},
    "weather": {"clear": 1.2, "rain": 0.8, "thunder": 0.5},
    "depth_pref": "surface", "distance_pref": {"min": 1, "max": 8},
    "habitat": {"depth_min": 1, "depth_max": 2, "width_min": 3},
    "biomes": {"temperate": 1.0, "warm": 1.0, "swamp": 1.0},
    "base": 1.0},
   ("Sunbleak", "Верховка", "Верхівка"),
   ("A silver sliver living in the top foot of any pond, puddle or ditch — the first fish most anglers "
    "ever caught. A crumb of bread on the smallest hook, right under the surface.",
    "Серебряная щепка, живущая в верхнем слое любого пруда, лужи или канавы — первая рыба в жизни "
    "большинства рыбаков. Крошка хлеба на мелком крючке, у самой поверхности.",
    "Срібна тріска, що живе у верхньому шарі будь-якого ставка, калюжі чи канави — перша риба в житті "
    "більшості рибалок. Крихта хліба на дрібному гачку, біля самої поверхні."),
   (1, 1, 1)),

 "sculpin": ("gudgeon", {
    "display": "Подкаменщик", "group": "predator",
    "water_bodies": {"river": 1.3, "lake": 0.4, "pond": 0.1, "swamp": 0.0, "sea": 0.0, "puddle": 0.0},
    "weight_g": {"min": 5, "max": 90, "mean": 25}, "length_cm": {"min": 5, "max": 16},
    "fight": {"strength": 0.08, "stamina": 0.2, "runs": 1},
    "ideal": {"rod": ["ultralight", "pole", "stick"], "reel_size": 1000, "reel_tolerance": 1000,
              "line": {"type": "mono", "diameter_mm": 0.14, "tolerance_mm": 0.04},
              "rig": ["primitive", "float"], "groundbait": {"fraction": 0.12, "nutrition": 0.50},
              "bait": {"worm": 1.0, "bloodworm": 0.9, "maggot": 0.8, "livebait": 0.3},
              "hook": {"ideal": 14, "tolerance": 2}},
    "season": {"spring": 1.1, "summer": 1.0, "autumn": 1.1, "winter": 0.5},
    "time": {"dawn": 1.0, "day": 0.6, "dusk": 1.2, "night": 1.4},
    "weather": {"clear": 1.0, "rain": 1.1, "thunder": 0.9},
    "depth_pref": "bottom", "distance_pref": {"min": 1, "max": 8},
    "habitat": {"depth_min": 1, "depth_max": 4, "width_min": 3},
    "biomes": {"cold": 1.3, "mountain": 1.3, "taiga": 1.1, "temperate": 0.9},
    "base": 0.7},
   ("Sculpin", "Подкаменщик", "Бабець"),
   ("A flat-headed bottom-sitter that hides under stones in cold clean streams — find one and the water is "
    "healthy. Worm on the gravel at dusk; it does not chase, it ambushes.",
    "Плоскоголовый донный сиделец, прячущийся под камнями в холодных чистых ручьях — если он есть, вода "
    "живая. Червь по гальке в сумерках: он не гонится, он караулит.",
    "Плоскоголовий донний сидень, що ховається під камінням у холодних чистих струмках — якщо він є, вода "
    "жива. Черв'як по гальці в сутінках: він не жене, він чатує."),
   (1, 1, 2)),

 "tubenose_goby": ("round_goby", {
    "display": "Бычок-цуцик", "group": "sea",
    "water_bodies": {"river": 1.1, "lake": 0.7, "pond": 0.5, "swamp": 0.4, "sea": 0.5, "puddle": 0.0},
    "weight_g": {"min": 3, "max": 30, "mean": 10}, "length_cm": {"min": 4, "max": 11},
    "fight": {"strength": 0.06, "stamina": 0.2, "runs": 1},
    "ideal": {"rod": ["ultralight", "pole", "stick"], "reel_size": 1000, "reel_tolerance": 1000,
              "line": {"type": "mono", "diameter_mm": 0.12, "tolerance_mm": 0.04},
              "rig": ["primitive", "float"], "groundbait": {"fraction": 0.12, "nutrition": 0.50},
              "bait": {"worm": 1.0, "bloodworm": 0.95, "maggot": 0.9, "fish_strip": 0.4},
              "hook": {"ideal": 16, "tolerance": 2}},
    "season": {"spring": 1.1, "summer": 1.2, "autumn": 1.0, "winter": 0.3},
    "time": {"dawn": 1.1, "day": 1.0, "dusk": 1.1, "night": 0.8},
    "weather": {"clear": 1.0, "rain": 1.1, "thunder": 0.8},
    "depth_pref": "bottom", "distance_pref": {"min": 1, "max": 10},
    "habitat": {"depth_min": 1, "width_min": 4},
    "biomes": {"temperate": 1.0, "warm": 1.1, "beach": 1.0},
    "base": 0.9},
   ("Tubenose goby", "Бычок-цуцик", "Бичок-цуцик"),
   ("The little goby with nostrils like two short tubes, at home anywhere from a river mouth to brackish "
    "shallows. Worm on the bottom, tiny hook, and it hooks itself.",
    "Мелкий бычок с ноздрями-трубочками, которому одинаково годятся устье реки и солоноватая отмель. "
    "Червь по дну, мелкий крючок — засечётся сам.",
    "Дрібний бичок із ніздрями-трубочками, якому однаково годяться гирло річки й солонувата мілина. "
    "Черв'як по дну, дрібний гачок — засічеться сам."),
   (1, 1, 1)),
}

# Swapped whole, not merged: these are statements about the fish, not deltas on the donor.
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
        write(os.path.join(prof_dir, sp + ".json"),
              json.dumps(merge(base, patch), ensure_ascii=False, indent=2) + "\n")
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
            block = ["", "        // §giants-and-minnows (0.8.0): the giants pay like the taimen tier they sit beside,",
                     "        // the minnows like the bleak they swim with."]
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
                    entry = ("\n        # §giants-and-minnows (0.8.0)\n        "
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
    for script in ("tools/gen_fish_bounds.py", "tools/gen_dynamic_icons.py"):
        if os.path.isfile(os.path.join(tree, script)):
            r = subprocess.run(["py", script], cwd=tree, capture_output=True, text=True)
            made.append(os.path.basename(script).replace("gen_", "").replace(".py", "")
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
