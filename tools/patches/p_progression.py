# -*- coding: utf-8 -*-
"""§progression (0.10.0): the achievements and the journal quests, for a planet of 267 species.
  Quests: the early chain asked for roach, crucian, bream, rudd, tench, perch, pike, zander, asp, carp, trout,
  sterlet, grayling — a Palearctic pond. An angler who spawns in the Afrotropical could never finish stage 1.
  The chain now asks for what the water anywhere holds: a peaceful feeder, an omnivore, a predator, a family,
  a kilo, five kilos — counted in the journal itself (grp./diet./best) so the client shows progress without
  a profile lookup. The soil reward is gone. Stage 7 is cold water and the fly rod (a FLY counter joins ICE).
  Achievements: twelve new — the fly rod's first fish and a tight-loop catch, the first tied lure, a second
  province and all five, 100 and 200 species, a 50 kg fish, a hybrid, level 50, a full jig combo through the
  ice, a keepnet sold.
Idempotent; run on each tree.  py tools/patches/p_progression.py [tree ...]"""
import io, json, os, re, sys
from collections import OrderedDict

TREES = [r"C:/Users/Qwazar/VS Code Projects/fishing mod", r"C:/Users/Qwazar/wt/rf1201", r"C:/Users/Qwazar/wt/rf26"]
J = "common/src/main/java/com/riverfishing/"
LANG = "common/src/main/resources/assets/riverfishing/lang"


def rd(p): return io.open(p, encoding="utf-8").read()
def wr(p, s): io.open(p, "w", encoding="utf-8", newline="\n").write(s)


def sub(path, old, new, what):
    s = rd(path)
    if new in s: return
    assert old in s, what + " @ " + path
    wr(path, s.replace(old, new, 1))


# ---------------------------------------------------------------- advancements
ADV = [  # id, parent, icon, frame, hidden, chat
    ("fly_first", "root", "riverfishing:fly_rod", "task", False, True),
    ("fly_tight", "fly_first", "riverfishing:fly_rod", "challenge", True, True),
    ("tied", "root", "minecraft:string", "task", False, False),
    ("far_shore", "root", "riverfishing:fish_finder", "goal", False, True),
    ("five_provinces", "far_shore", "minecraft:compass", "challenge", False, True),
    ("species_100", "species_50", "riverfishing:nile_tilapia", "goal", False, True),
    ("species_200", "species_100", "riverfishing:asian_arowana", "challenge", False, True),
    ("heavyweight", "root", "riverfishing:white_sturgeon", "goal", False, True),
    ("hybrid", "root", "riverfishing:bream_roach_hybrid", "task", False, True),
    ("grandmaster", "master", "minecraft:netherite_ingot", "challenge", False, True),
    ("ice_rhythm", "ice_burbot", "riverfishing:mormyshka", "challenge", False, True),
    ("keepnet_sale", "root", "riverfishing:keepnet_small", "task", False, False),
]
ADV_LANG = {
    "en_us": {
        "fly_first": ("On the Fly", "Land a fish on the fly rod"),
        "fly_tight": ("Tight Loop", "Land a fish on a cast that unrolled without a splash"),
        "tied": ("Tied by Hand", "Tie your first lure at the bench"),
        "far_shore": ("A Far Shore", "Catch a fish in a second faunal province"),
        "five_provinces": ("The Whole Planet", "Catch a fish in every one of the five provinces"),
        "species_100": ("A Hundred Names", "Catch 100 different fish species"),
        "species_200": ("Two Hundred Names", "Catch 200 different fish species"),
        "heavyweight": ("Heavyweight", "Land a fish of 50 kg or more"),
        "hybrid": ("Neither One Nor the Other", "Catch a hybrid"),
        "grandmaster": ("Grandmaster", "Reach angler level 50"),
        "ice_rhythm": ("In Time", "Hook a fish through the ice on a full jig combo"),
        "keepnet_sale": ("Market Day", "Sell a keepnet of fish to the fisherman"),
    },
    "ru_ru": {
        "fly_first": ("Нахлыстом", "Поймайте рыбу на нахлыстовое удилище"),
        "fly_tight": ("Тугая петля", "Поймайте рыбу с заброса, который лёг без всплеска"),
        "tied": ("Связано вручную", "Свяжите первую приманку на верстаке"),
        "far_shore": ("Дальний берег", "Поймайте рыбу во второй фаунистической провинции"),
        "five_provinces": ("Вся планета", "Поймайте рыбу в каждой из пяти провинций"),
        "species_100": ("Сотня имён", "Поймайте 100 разных видов рыб"),
        "species_200": ("Две сотни имён", "Поймайте 200 разных видов рыб"),
        "heavyweight": ("Тяжеловес", "Вытащите рыбу от 50 кг"),
        "hybrid": ("Ни то ни другое", "Поймайте гибрида"),
        "grandmaster": ("Гроссмейстер", "Достигните 50-го уровня рыболова"),
        "ice_rhythm": ("В такт", "Подсеките рыбу подо льдом на полном комбо игры мормышкой"),
        "keepnet_sale": ("Базарный день", "Продайте садок рыбы рыбаку"),
    },
    "uk_ua": {
        "fly_first": ("Нахлистом", "Зловіть рибу на нахлистове вудилище"),
        "fly_tight": ("Туга петля", "Зловіть рибу із закиду, що ліг без сплеску"),
        "tied": ("Зв'язано вручну", "Зв'яжіть першу принаду на верстаку"),
        "far_shore": ("Далекий берег", "Зловіть рибу в другій фауністичній провінції"),
        "five_provinces": ("Уся планета", "Зловіть рибу в кожній з п'яти провінцій"),
        "species_100": ("Сотня імен", "Зловіть 100 різних видів риб"),
        "species_200": ("Дві сотні імен", "Зловіть 200 різних видів риб"),
        "heavyweight": ("Важковаговик", "Витягніть рибу від 50 кг"),
        "hybrid": ("Ні те ні інше", "Зловіть гібрида"),
        "grandmaster": ("Гросмейстер", "Досягніть 50-го рівня рибалки"),
        "ice_rhythm": ("У такт", "Підсічіть рибу під льодом на повному комбо гри мормишкою"),
        "keepnet_sale": ("Базарний день", "Продайте садок риби рибалці"),
    },
}

# ---------------------------------------------------------------- quests
QUESTS = '''    public static final List<Quest> ALL = List.of(
            // §progression (0.10.0): the chain asks for what the water ANYWHERE holds — a diet, a family, a
            // weight — never for a roach, because half the planet has never seen one.
            // Stage 1 — first casts at the pond
            new Quest("q_first_fish", 1, total(1), item("worm", 8), 15),
            new Quest("q_peaceful", 1, diet("peaceful", 1), item("maggot", 8), 15),
            new Quest("q_species3", 1, distinct(3), item("hook_12", 4), 30),
            new Quest("q_peaceful3", 1, diet("peaceful", 3), item("groundbait_powder", 4), 20),
            new Quest("q_ten_fish", 1, total(10), item("bait_trap", 1), 25),
            new Quest("q_stage1_done", 1, stageComplete(1), emeralds(12), 40),
            // Stage 2 — float & feeder
            new Quest("q_omnivore", 2, diet("omnivore", 1), item("hook_8", 3), 25),
            new Quest("q_peaceful10", 2, diet("peaceful", 10), item("boilie", 6), 35),
            new Quest("q_kilo", 2, bestAny(1000), emeralds(6), 40),
            new Quest("q_families3", 2, families(3), emeralds(6), 30),
            new Quest("q_species8", 2, distinct(8), item("spinning_rod", 1), 60),
            new Quest("q_stage2_done", 2, stageComplete(2), item("reel_3000", 1), 60),
            // Stage 3 — predators
            new Quest("q_predator", 3, diet("predator", 1), item("spinner", 2), 25),
            new Quest("q_predators5", 3, diet("predator", 5), item("leader", 2), 40),
            new Quest("q_predator_big", 3, bestDiet("predator", 3000), emeralds(10), 70),
            new Quest("q_predators15", 3, diet("predator", 15), item("wobbler", 1), 45),
            new Quest("q_families5", 3, families(5), emeralds(6), 45),
            new Quest("q_stage3_done", 3, stageComplete(3), item("leader_titanium", 1), 80),
            // Stage 4 — heavy tackle
            new Quest("q_five_kilo", 4, bestAny(5000), item("boilie", 8), 50),
            new Quest("q_ten_kilo", 4, bestAny(10000), item("leader_titanium", 1), 80),
            new Quest("q_twenty_kilo", 4, bestAny(20000), emeralds(20), 120),
            new Quest("q_families6", 4, families(6), emeralds(6), 50),
            new Quest("q_hundred", 4, total(100), item("reel_5000", 1), 90),
            new Quest("q_stage4_done", 4, stageComplete(4), emeralds(32), 120),
            // Stage 5 — master
            new Quest("q_species15", 5, distinct(15), item("reel_7000", 1), 100),
            new Quest("q_species30", 5, distinct(30), emeralds(16), 100),
            new Quest("q_forty_kilo", 5, bestAny(40000), emeralds(10), 70),
            new Quest("q_koi", 5, koi(), emeralds(12), 80),
            new Quest("q_trophy", 5, trophies(1), emeralds(8), 60),
            new Quest("q_trophy5", 5, trophies(5), emeralds(24), 140),
            new Quest("q_species20", 5, distinct(20), emeralds(20), 150),
            new Quest("q_master", 5, level(20), emeralds(30), 0),
            new Quest("q_stage5_done", 5, stageComplete(5), item("carp_rod", 1), 150),
            // Stage 6 — under the ice (§winter-quests)
            new Quest("q_ice_first", 6, ice(1), item("mormyshka", 2), 40),
            new Quest("q_ice_burbot", 6, species("burbot", 1), item("chicken_liver", 4), 60),
            new Quest("q_ice_five", 6, ice(5), item("maggot", 12), 30),
            new Quest("q_ice_ten", 6, ice(10), item("winter_rod", 1), 80),
            new Quest("q_ice_thirty", 6, ice(30), emeralds(24), 160),
            new Quest("q_stage6_done", 6, stageComplete(6), emeralds(50), 200),
            // Stage 7 — cold water and the fly (§fly): the salmonids, and the rod that was made for them
            new Quest("q_fly_first", 7, fly(1), emeralds(10), 40),
            new Quest("q_salmonid", 7, group("salmonid", 1), item("spoon", 2), 50),
            new Quest("q_fly_ten", 7, fly(10), emeralds(24), 90),
            new Quest("q_salmonids3", 7, group("salmonid", 3), item("castmaster", 1), 60),
            new Quest("q_insectivores5", 7, diet("insectivore", 5), item("bloodworm", 12), 50),
            new Quest("q_salmonid_big", 7, bestGroup("salmonid", 5000), emeralds(30), 160),
            new Quest("q_fly_thirty", 7, fly(30), emeralds(30), 120),
            new Quest("q_stage7_done", 7, stageComplete(7), item("surf_rod", 1), 180),
            // Stage 8 — the sea and big game (§ocean): coast → shelf → the pelagic monsters.
            new Quest("q_seabass", 8, species("seabass", 1), item("castmaster", 1), 50),
            new Quest("q_herring", 8, species("herring", 5), item("fish_strip", 8), 50),
            new Quest("q_cod", 8, species("cod", 1), emeralds(8), 60),
            new Quest("q_species40", 8, distinct(40), item("trolling_rod", 1), 120),
            new Quest("q_mahi", 8, species("mahi", 1), emeralds(10), 90),
            new Quest("q_tuna_big", 8, weight("yellowfin_tuna", 60000), emeralds(24), 150),
            new Quest("q_halibut", 8, species("halibut", 1), item("line_braid_060", 1), 150),
            new Quest("q_billfish", 8, anyOf("blue_marlin", "sailfish", "swordfish"), emeralds(40), 200),
            new Quest("q_species60", 8, distinct(60), item("reel_14000", 1), 250),
            new Quest("q_stage8_done", 8, stageComplete(8), emeralds(64), 300)
    );
'''
GOALS = '''    // §progression: the traits the journal counts at the catch (JournalData.recordTraits) — a quest can
    // ask for "three peaceful feeders" or "a five-kilo fish" wherever on the planet the angler stands
    private static Goal counter(String key, int n) {
        return new Goal() {
            public boolean complete(CompoundTag j) { return j.getInt(key) >= n; }
            public String progress(CompoundTag j) { return Math.min(n, j.getInt(key)) + "/" + n; }
        };
    }

    private static Goal diet(String diet, int n) { return counter("diet." + diet, n); }
    private static Goal group(String group, int n) { return counter("grp." + group, n); }
    private static Goal fly(int n) { return counter(JournalData.FLY, n); }
    private static Goal bestAny(int grams) { return j -> j.getInt("best_any") >= grams; }
    private static Goal bestDiet(String diet, int grams) { return j -> j.getInt("dbest." + diet) >= grams; }
    private static Goal bestGroup(String group, int grams) { return j -> j.getInt("gbest." + group) >= grams; }

    /** Families (journal groups) with at least one fish in them. */
    private static Goal families(int n) {
        return new Goal() {
            public boolean complete(CompoundTag j) { return familyCount(j) >= n; }
            public String progress(CompoundTag j) { return Math.min(n, familyCount(j)) + "/" + n; }
        };
    }

    private static int familyCount(CompoundTag j) {
        int c = 0;
        for (String k : j.getAllKeys()) if (k.startsWith("grp.") && j.getInt(k) > 0) c++;
        return c;
    }

    private static Goal distinct(int n) {'''

QUEST_LANG = {
    "en_us": {
        "q_peaceful": "Catch a peaceful feeder", "q_peaceful3": "Catch 3 peaceful feeders", "q_omnivore": "Catch an omnivore",
        "q_peaceful10": "Catch 10 peaceful feeders", "q_kilo": "Land a fish of 1 kg or more", "q_families3": "Catch fish of 3 different families",
        "q_predator": "Catch a predator", "q_predators5": "Catch 5 predators", "q_predator_big": "Land a predator of 3 kg or more",
        "q_predators15": "Catch 15 predators", "q_families5": "Catch fish of 5 different families",
        "q_five_kilo": "Land a fish of 5 kg or more", "q_ten_kilo": "Land a fish of 10 kg or more", "q_twenty_kilo": "Land a fish of 20 kg or more",
        "q_families6": "Catch fish of 6 different families", "q_species30": "Discover 30 species", "q_forty_kilo": "Land a fish of 40 kg or more",
        "q_ice_five": "Catch 5 fish through the ice",
        "q_fly_first": "Land a fish on the fly rod", "q_fly_ten": "Land 10 fish on the fly", "q_salmonid": "Catch a salmonid",
        "q_salmonids3": "Catch 3 salmonids", "q_insectivores5": "Catch 5 insect feeders", "q_salmonid_big": "Land a salmonid of 5 kg or more",
        "q_fly_thirty": "Land 30 fish on the fly",
        "quest.riverfishing.stage.7": "Stage 7 - Cold water and the fly",
    },
    "ru_ru": {
        "q_peaceful": "Поймайте мирную рыбу", "q_peaceful3": "Поймайте 3 мирные рыбы", "q_omnivore": "Поймайте всеядную рыбу",
        "q_peaceful10": "Поймайте 10 мирных рыб", "q_kilo": "Вытащите рыбу от 1 кг", "q_families3": "Поймайте рыб 3 разных семейств",
        "q_predator": "Поймайте хищника", "q_predators5": "Поймайте 5 хищников", "q_predator_big": "Вытащите хищника от 3 кг",
        "q_predators15": "Поймайте 15 хищников", "q_families5": "Поймайте рыб 5 разных семейств",
        "q_five_kilo": "Вытащите рыбу от 5 кг", "q_ten_kilo": "Вытащите рыбу от 10 кг", "q_twenty_kilo": "Вытащите рыбу от 20 кг",
        "q_families6": "Поймайте рыб 6 разных семейств", "q_species30": "Откройте 30 видов", "q_forty_kilo": "Вытащите рыбу от 40 кг",
        "q_ice_five": "Поймайте 5 рыб со льда",
        "q_fly_first": "Поймайте рыбу нахлыстом", "q_fly_ten": "Поймайте 10 рыб нахлыстом", "q_salmonid": "Поймайте лососёвую",
        "q_salmonids3": "Поймайте 3 лососёвых", "q_insectivores5": "Поймайте 5 насекомоядных рыб", "q_salmonid_big": "Вытащите лососёвую от 5 кг",
        "q_fly_thirty": "Поймайте 30 рыб нахлыстом",
        "quest.riverfishing.stage.7": "Стадия 7 — Холодная вода и нахлыст",
    },
    "uk_ua": {
        "q_peaceful": "Зловіть мирну рибу", "q_peaceful3": "Зловіть 3 мирні риби", "q_omnivore": "Зловіть всеїдну рибу",
        "q_peaceful10": "Зловіть 10 мирних риб", "q_kilo": "Витягніть рибу від 1 кг", "q_families3": "Зловіть риб 3 різних родин",
        "q_predator": "Зловіть хижака", "q_predators5": "Зловіть 5 хижаків", "q_predator_big": "Витягніть хижака від 3 кг",
        "q_predators15": "Зловіть 15 хижаків", "q_families5": "Зловіть риб 5 різних родин",
        "q_five_kilo": "Витягніть рибу від 5 кг", "q_ten_kilo": "Витягніть рибу від 10 кг", "q_twenty_kilo": "Витягніть рибу від 20 кг",
        "q_families6": "Зловіть риб 6 різних родин", "q_species30": "Відкрийте 30 видів", "q_forty_kilo": "Витягніть рибу від 40 кг",
        "q_ice_five": "Зловіть 5 риб з льоду",
        "q_fly_first": "Зловіть рибу нахлистом", "q_fly_ten": "Зловіть 10 риб нахлистом", "q_salmonid": "Зловіть лососеву",
        "q_salmonids3": "Зловіть 3 лососевих", "q_insectivores5": "Зловіть 5 комахоїдних риб", "q_salmonid_big": "Витягніть лососеву від 5 кг",
        "q_fly_thirty": "Зловіть 30 риб нахлистом",
        "quest.riverfishing.stage.7": "Етап 7 — Холодна вода і нахлист",
    },
}

JOURNAL_ADD = '''    public static final String FLY = "fly";   // §progression: fish landed on the fly rod

    /** §progression: a fish landed on the fly rod — the counter the stage-7 quests read. */
    public static void addFlyCatch(Player player) {
        CompoundTag root = get(player);
        root.putInt(FLY, root.getInt(FLY) + 1);
        PlayerData.root(player).put(TAG, root);
        PlayerData.markDirty(player);
    }

    /**
     * §progression: the family, the diet and the weight of a catch, counted in the journal itself —
     * {@code grp.<group>}, {@code diet.<diet>}, {@code gbest.<group>}, {@code dbest.<diet>}, {@code best_any}
     * — so a quest can ask for "three peaceful feeders" without a profile lookup on the client.
     */
    public static void recordTraits(Player player, String group, String diet, int weightG) {
        CompoundTag root = get(player);
        if (group != null && !group.isEmpty()) {
            root.putInt("grp." + group, root.getInt("grp." + group) + 1);
            root.putInt("gbest." + group, Math.max(root.getInt("gbest." + group), weightG));
        }
        if (diet != null && !diet.isEmpty()) {
            root.putInt("diet." + diet, root.getInt("diet." + diet) + 1);
            root.putInt("dbest." + diet, Math.max(root.getInt("dbest." + diet), weightG));
        }
        root.putInt("best_any", Math.max(root.getInt("best_any"), weightG));
        PlayerData.root(player).put(TAG, root);
        PlayerData.markDirty(player);
    }

    /** §progression: the faunal province a fish was taken in. */
    public static void recordProvince(Player player, String province) {
        CompoundTag root = get(player);
        CompoundTag provs = root.getCompound("provinces");
        provs.putBoolean(province, true);
        root.put("provinces", provs);
        PlayerData.root(player).put(TAG, root);
        PlayerData.markDirty(player);
    }

    public static int provincesSeen(Player player) {
        return get(player).getCompound("provinces").getAllKeys().size();
    }

    /** Records a fish landed through the ice (§winter-quests): a counter for winter-fishing goals. */'''

CATCH_BLOCK = '''            if (session.iceFishing) JournalData.addIceCatch(sp); // §winter-quests
            {   // §progression: the traits, the province and the fly rod, counted before the quests look
                FishProfile pr = FishProfileManager.get().byId(session.species);
                if (pr != null) JournalData.recordTraits(sp, pr.group, pr.diet, session.weightG);
                JournalData.recordProvince(sp, com.riverfishing.water.Provinces.at(level.getSeed(),
                        session.target.getX(), session.target.getZ()));
                ItemStack rodNow = sessionRod(sp, session);
                if (rodNow.getItem() instanceof RodItem ri && ri.rodType() == RodType.FLY) JournalData.addFlyCatch(sp);
            }
'''
ADV_BLOCK = '''        // Thematic: a burbot pulled through the ice.
        if (sp2.equals("burbot") && session.iceFishing) {
            com.riverfishing.quest.AnglerAdvancements.grant(sp, "ice_burbot");
        }
        // §progression (0.10.0)
        if (rodType == RodType.FLY) {
            com.riverfishing.quest.AnglerAdvancements.grant(sp, "fly_first");
            if (session.flyTight) com.riverfishing.quest.AnglerAdvancements.grant(sp, "fly_tight");
        }
        if (session.weightG >= 50000) com.riverfishing.quest.AnglerAdvancements.grant(sp, "heavyweight");
        FishProfile prof = FishProfileManager.get().byId(session.species);
        if (prof != null && !prof.hybridOf.isEmpty()) com.riverfishing.quest.AnglerAdvancements.grant(sp, "hybrid");
        if (session.iceFishing && session.jigBest >= FlyCast.JIG_MAX) com.riverfishing.quest.AnglerAdvancements.grant(sp, "ice_rhythm");
        int provs = JournalData.provincesSeen(sp);
        if (provs >= 2) com.riverfishing.quest.AnglerAdvancements.grant(sp, "far_shore");
        if (provs >= 5) com.riverfishing.quest.AnglerAdvancements.grant(sp, "five_provinces");
'''


def adv_json(aid, parent, icon, frame, hidden, chat, old_dialect):
    d = OrderedDict()
    d["parent"] = "riverfishing:riverfishing/" + parent
    disp = OrderedDict()
    disp["icon"] = {"item": icon} if old_dialect else {"id": icon}
    disp["title"] = {"translate": "advancement.riverfishing.%s.title" % aid}
    disp["description"] = {"translate": "advancement.riverfishing.%s.description" % aid}
    disp["frame"] = frame
    disp["show_toast"] = True
    disp["announce_to_chat"] = chat
    if hidden: disp["hidden"] = True
    d["display"] = disp
    d["criteria"] = {"code": {"trigger": "minecraft:impossible"}}
    return json.dumps(d, indent=2) + "\n"


def lang_insert(path, after_key, entries):
    data = json.loads(rd(path), object_pairs_hook=OrderedDict)
    if all(k in data for k in entries):
        changed = False
        for k, v in entries.items():
            if data[k] != v: data[k] = v; changed = True
        if changed: wr(path, json.dumps(data, ensure_ascii=False, indent=2) + "\n")
        return
    out = OrderedDict()
    for k, v in data.items():
        if k in entries: continue
        out[k] = v
        if k == after_key:
            for ek, ev in entries.items(): out[ek] = ev
    assert all(k in out for k in entries), "anchor " + after_key + " @ " + path
    wr(path, json.dumps(out, ensure_ascii=False, indent=2) + "\n")


def run(tree):
    name = os.path.basename(tree.rstrip("/\\")); old = name == "rf1201"
    # §26x: the NBT getters have a default argument there
    d = (lambda t: re.sub(r"\.getInt\(([^()]*)\)", r".getIntOr(\1, 0)", re.sub(r"\.getCompound\(([^()]*)\)", r".getCompoundOrEmpty(\1)", t)).replace("getAllKeys()", "keySet()")) if name == "rf26" else (lambda t: t)
    global JOURNAL_ADD_T, GOALS_T, CATCH_BLOCK_T, ADV_BLOCK_T
    JOURNAL_ADD_T, GOALS_T, CATCH_BLOCK_T, ADV_BLOCK_T = d(JOURNAL_ADD), d(GOALS), d(CATCH_BLOCK), d(ADV_BLOCK)
    j = lambda *a: os.path.join(tree, *a)
    fm = j(J, "fishing/FishingManager.java")
    # session fields
    sub(j(J, "fishing/FishingSession.java"), "    public boolean iceFishing;\n",
        "    public boolean iceFishing;\n    public boolean flyTight;   // §progression: this cast unrolled without a splash\n    public int jigBest;        // §progression: the best jig combo of this session\n", "session fields")
    # fly tight + jig best
    sub(fm, "        if (quality == 0) {\n            actionbar(sp, Component.translatable(\"message.riverfishing.fly_tight\")",
        "        if (quality == 0) {\n            session.flyTight = true;   // §progression\n            actionbar(sp, Component.translatable(\"message.riverfishing.fly_tight\")", "fly tight")
    sub(fm, "        int combo = FlyCast.jigBeat(sp, now);\n", "        int combo = FlyCast.jigBeat(sp, now);\n        session.jigBest = Math.max(session.jigBest, combo);   // §progression\n", "jig best")
    # the catch path
    sub(fm, "            if (session.iceFishing) JournalData.addIceCatch(sp); // §winter-quests\n", CATCH_BLOCK, "catch traits")
    sub(fm, '                if (n >= 50) com.riverfishing.quest.AnglerAdvancements.grant(sp, "species_50");\n',
        '                if (n >= 50) com.riverfishing.quest.AnglerAdvancements.grant(sp, "species_50");\n'
        '                if (n >= 100) com.riverfishing.quest.AnglerAdvancements.grant(sp, "species_100");   // §progression\n'
        '                if (n >= 200) com.riverfishing.quest.AnglerAdvancements.grant(sp, "species_200");\n', "species tiers")
    sub(fm, '        // Thematic: a burbot pulled through the ice.\n        if (sp2.equals("burbot") && session.iceFishing) {\n            com.riverfishing.quest.AnglerAdvancements.grant(sp, "ice_burbot");\n        }\n', ADV_BLOCK_T, "adv block")
    sub(fm, "            String rankBefore = JournalData.rankKey(before);\n",
        '            if (after >= 50) com.riverfishing.quest.AnglerAdvancements.grant(sp, "grandmaster");   // §progression\n            String rankBefore = JournalData.rankKey(before);\n', "grandmaster")
    # journal
    sub(j(J, "fishing/JournalData.java"), "    /** Records a fish landed through the ice (§winter-quests): a counter for winter-fishing goals. */", JOURNAL_ADD_T, "journal add")
    # tie + keepnet
    sub(j(J, "network/TieLurePacket.java"), "        if (count(menu, HOOK) < 1 + cost[TiedDesign.BEAD_IRON] || !affordable(menu, design)) return;\n",
        "        if (count(menu, HOOK) < 1 + cost[TiedDesign.BEAD_IRON] || !affordable(menu, design)) return;\n        com.riverfishing.quest.AnglerAdvancements.grant(sp, \"tied\");   // §progression\n", "tied")
    sub(j(J, "fishing/KeepnetSale.java"), "        data.write(net);\n", "        data.write(net);\n        com.riverfishing.quest.AnglerAdvancements.grant(sp, \"keepnet_sale\");   // §progression\n", "keepnet sale")
    # quests
    q = j(J, "quest/Quests.java"); s = rd(q)
    if "q_peaceful3" not in s:
        s2 = re.sub(r"    public static final List<Quest> ALL = List\.of\(\n.*?\n    \);\n", QUESTS.replace("\\", "\\\\"), s, count=1, flags=re.S)
        assert s2 != s, "quest table"
        s = s2
    if "private static Goal counter(" not in s:
        assert "    private static Goal distinct(int n) {" in s
        s = s.replace("    private static Goal distinct(int n) {", GOALS_T, 1)
    wr(q, s)
    # advancements
    adir = j("common/src/main/resources/data/riverfishing", "advancements" if old else "advancement", "riverfishing")
    assert os.path.isdir(adir), adir
    for aid, parent, icon, frame, hidden, chat in ADV:
        wr(os.path.join(adir, aid + ".json"), adv_json(aid, parent, icon, frame, hidden, chat, old))
    sub(os.path.join(adir, "all_species.json"), '"parent": "riverfishing:riverfishing/species_50"', '"parent": "riverfishing:riverfishing/species_200"', "all_species parent")
    # lang
    for code in ("en_us", "ru_ru", "uk_ua"):
        p = j(LANG, code + ".json")
        adv = OrderedDict()
        for aid, *_ in ADV:
            t, d = ADV_LANG[code][aid]
            adv["advancement.riverfishing.%s.title" % aid] = t; adv["advancement.riverfishing.%s.description" % aid] = d
        lang_insert(p, "advancement.riverfishing.koi_fillet.description", adv)
        ql = OrderedDict((("quest.riverfishing." + k) if k.startswith("q_") else k, v) for k, v in QUEST_LANG[code].items())
        lang_insert(p, "quest.riverfishing.q_stage8_done", ql)
    print("  patched", name)


for t in (sys.argv[1:] or TREES): run(t)
