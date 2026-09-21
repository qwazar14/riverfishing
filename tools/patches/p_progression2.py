# -*- coding: utf-8 -*-
"""§progression-2: fourteen more achievements, and /rffish unlockall filling the counters the new quests read.
  night_owl, storm_rider, four_seasons, all_diets, seven_families, all_families, fed_swim, trophy_10, trophy_50,
  thousand, fly_fifty, released, sea_first, deep_sea — every one code-awarded from the catch (or the release).
Idempotent; run on each tree.  py tools/patches/p_progression2.py [tree ...]"""
import io, json, os, re, sys
from collections import OrderedDict

TREES = [r"C:/Users/Qwazar/VS Code Projects/fishing mod", r"C:/Users/Qwazar/wt/rf1201", r"C:/Users/Qwazar/wt/rf26"]
J = "common/src/main/java/com/riverfishing/"
LANG = "common/src/main/resources/assets/riverfishing/lang"

ADV = [  # id, parent, icon, frame, hidden, chat
    ("night_owl", "root", "minecraft:clock", "task", False, False),
    ("storm_rider", "night_owl", "minecraft:lightning_rod", "goal", False, True),
    ("four_seasons", "root", "minecraft:oak_sapling", "goal", False, True),
    ("all_diets", "root", "minecraft:cooked_cod", "goal", False, True),
    ("seven_families", "root", "riverfishing:fishing_journal", "goal", False, True),
    ("all_families", "seven_families", "minecraft:writable_book", "challenge", False, True),
    ("fed_swim", "root", "riverfishing:groundbait_powder", "task", False, False),
    ("trophy_10", "trophy", "minecraft:gold_nugget", "goal", False, True),
    ("trophy_50", "trophy_10", "minecraft:gold_block", "challenge", False, True),
    ("thousand", "root", "minecraft:bucket", "challenge", False, True),
    ("fly_fifty", "fly_first", "riverfishing:fly_rod", "goal", False, True),
    ("released", "root", "minecraft:water_bucket", "task", False, False),
    ("sea_first", "root", "riverfishing:mackerel", "task", False, True),
    ("deep_sea", "sea_first", "riverfishing:anglerfish", "goal", False, True),
]
ADV_LANG = {
    "en_us": {
        "night_owl": ("Night Owl", "Land a fish at night"),
        "storm_rider": ("Storm Rider", "Land a fish in a thunderstorm"),
        "four_seasons": ("Four Seasons", "Land a fish in every season"),
        "all_diets": ("A Table for Everyone", "Catch a peaceful feeder, an omnivore, a predator and an insect feeder"),
        "seven_families": ("Seven Families", "Catch fish of seven different families"),
        "all_families": ("Every Family", "Catch fish of all thirteen families"),
        "fed_swim": ("The Table Was Laid", "Land a fish from a swim you fed"),
        "trophy_10": ("Ten Trophies", "Land ten trophy specimens"),
        "trophy_50": ("A Wall of Trophies", "Land fifty trophy specimens"),
        "thousand": ("A Thousand Fish", "Land a thousand fish"),
        "fly_fifty": ("Dry Fly Hand", "Land fifty fish on the fly"),
        "released": ("Go Home", "Release a fish back into the water"),
        "sea_first": ("Salt", "Land a fish from the sea"),
        "deep_sea": ("The Deep", "Land a fish over the deep ocean"),
    },
    "ru_ru": {
        "night_owl": ("Ночная смена", "Поймайте рыбу ночью"),
        "storm_rider": ("В грозу", "Поймайте рыбу в грозу"),
        "four_seasons": ("Четыре сезона", "Поймайте рыбу в каждый из сезонов"),
        "all_diets": ("Стол для всех", "Поймайте мирную рыбу, всеядную, хищника и насекомоядную"),
        "seven_families": ("Семь семейств", "Поймайте рыб семи разных семейств"),
        "all_families": ("Все семейства", "Поймайте рыб всех тринадцати семейств"),
        "fed_swim": ("Стол накрыт", "Поймайте рыбу с прикормленного места"),
        "trophy_10": ("Десять трофеев", "Вытащите десять трофейных экземпляров"),
        "trophy_50": ("Стена трофеев", "Вытащите пятьдесят трофейных экземпляров"),
        "thousand": ("Тысяча рыб", "Вытащите тысячу рыб"),
        "fly_fifty": ("Рука нахлыстовика", "Поймайте пятьдесят рыб нахлыстом"),
        "released": ("Плыви домой", "Отпустите рыбу обратно в воду"),
        "sea_first": ("Соль", "Поймайте рыбу в море"),
        "deep_sea": ("Глубина", "Поймайте рыбу над глубоким океаном"),
    },
    "uk_ua": {
        "night_owl": ("Нічна зміна", "Зловіть рибу вночі"),
        "storm_rider": ("У грозу", "Зловіть рибу в грозу"),
        "four_seasons": ("Чотири сезони", "Зловіть рибу в кожен із сезонів"),
        "all_diets": ("Стіл для всіх", "Зловіть мирну рибу, всеїдну, хижака та комахоїдну"),
        "seven_families": ("Сім родин", "Зловіть риб семи різних родин"),
        "all_families": ("Усі родини", "Зловіть риб усіх тринадцяти родин"),
        "fed_swim": ("Стіл накрито", "Зловіть рибу з прикормленого місця"),
        "trophy_10": ("Десять трофеїв", "Витягніть десять трофейних екземплярів"),
        "trophy_50": ("Стіна трофеїв", "Витягніть п'ятдесят трофейних екземплярів"),
        "thousand": ("Тисяча риб", "Витягніть тисячу риб"),
        "fly_fifty": ("Рука нахлистовика", "Зловіть п'ятдесят риб нахлистом"),
        "released": ("Пливи додому", "Відпустіть рибу назад у воду"),
        "sea_first": ("Сіль", "Зловіть рибу в морі"),
        "deep_sea": ("Глибина", "Зловіть рибу над глибоким океаном"),
    },
}

JOURNAL_ADD = '''    /** §progression-2: the season a fish was taken in. */
    public static void recordSeason(Player player, String season) {
        CompoundTag root = get(player);
        CompoundTag seen = root.getCompound("seasons");
        seen.putBoolean(season, true);
        root.put("seasons", seen);
        PlayerData.root(player).put(TAG, root);
        PlayerData.markDirty(player);
    }

    public static int seasonsSeen(Player player) {
        return get(player).getCompound("seasons").getAllKeys().size();
    }

    /** §progression-2: journal keys with the prefix and a count above zero — the families or the diets fished. */
    public static int countPrefix(Player player, String prefix) {
        CompoundTag root = get(player);
        int n = 0;
        for (String k : root.getAllKeys()) if (k.startsWith(prefix) && root.getInt(k) > 0) n++;
        return n;
    }

    /** Records a fish landed through the ice (§winter-quests): a counter for winter-fishing goals. */'''

ADV_BLOCK = '''        if (provs >= 5) com.riverfishing.quest.AnglerAdvancements.grant(sp, "five_provinces");
        // §progression-2
        com.riverfishing.engine.BiteContext cx = session.ctx;
        if (cx != null) {
            if (cx.time == TimeOfDay.NIGHT) com.riverfishing.quest.AnglerAdvancements.grant(sp, "night_owl");
            if (cx.weather == Weather.THUNDER) com.riverfishing.quest.AnglerAdvancements.grant(sp, "storm_rider");
            if (cx.season != null) {
                JournalData.recordSeason(sp, cx.season.name().toLowerCase(java.util.Locale.ROOT));
                if (JournalData.seasonsSeen(sp) >= 4) com.riverfishing.quest.AnglerAdvancements.grant(sp, "four_seasons");
            }
            if (cx.inFeedZone && cx.feedFreshness > 0) com.riverfishing.quest.AnglerAdvancements.grant(sp, "fed_swim");
            if (cx.water == WaterType.SEA) com.riverfishing.quest.AnglerAdvancements.grant(sp, "sea_first");
            if (cx.biomeGroups.contains("deep")) com.riverfishing.quest.AnglerAdvancements.grant(sp, "deep_sea");
        }
        net.minecraft.nbt.CompoundTag jr = JournalData.get(sp);
        if (jr.getInt(JournalData.TROPHIES) >= 10) com.riverfishing.quest.AnglerAdvancements.grant(sp, "trophy_10");
        if (jr.getInt(JournalData.TROPHIES) >= 50) com.riverfishing.quest.AnglerAdvancements.grant(sp, "trophy_50");
        if (jr.getInt(JournalData.TOTAL) >= 1000) com.riverfishing.quest.AnglerAdvancements.grant(sp, "thousand");
        if (jr.getInt(JournalData.FLY) >= 50) com.riverfishing.quest.AnglerAdvancements.grant(sp, "fly_fifty");
        int families = JournalData.countPrefix(sp, "grp.");
        if (families >= 7) com.riverfishing.quest.AnglerAdvancements.grant(sp, "seven_families");
        if (families >= com.riverfishing.fish.FishGroup.ORDER.size() - 1) com.riverfishing.quest.AnglerAdvancements.grant(sp, "all_families");
        if (JournalData.countPrefix(sp, "diet.") >= 4) com.riverfishing.quest.AnglerAdvancements.grant(sp, "all_diets");
'''

UNLOCK_ADD = '''            JournalData.record(sp, id, w);
            if (p != null) JournalData.recordTraits(sp, p.group, p.diet, w);   // §progression: the counters the quests read
'''
UNLOCK_TAIL = '''        root.putInt(JournalData.FLY, Math.max(root.getInt(JournalData.FLY), 60));
        net.minecraft.nbt.CompoundTag provs = new net.minecraft.nbt.CompoundTag();
        for (String pr : com.riverfishing.water.Provinces.ALL) provs.putBoolean(pr, true);
        root.put("provinces", provs);
        net.minecraft.nbt.CompoundTag seasons = new net.minecraft.nbt.CompoundTag();
        for (String s : new String[]{"spring", "summer", "autumn", "winter"}) seasons.putBoolean(s, true);
        root.put("seasons", seasons);
'''


def rd(p): return io.open(p, encoding="utf-8").read()
def wr(p, s): io.open(p, "w", encoding="utf-8", newline="\n").write(s)


def sub(path, old, new, what):
    s = rd(path)
    if new in s: return
    assert old in s, what + " @ " + path
    wr(path, s.replace(old, new, 1))


def adv_json(aid, parent, icon, frame, hidden, chat, old_dialect):
    d = OrderedDict()
    d["parent"] = "riverfishing:riverfishing/" + parent
    disp = OrderedDict()
    disp["icon"] = {"item": icon} if old_dialect else {"id": icon}
    disp["title"] = {"translate": "advancement.riverfishing.%s.title" % aid}
    disp["description"] = {"translate": "advancement.riverfishing.%s.description" % aid}
    disp["frame"] = frame; disp["show_toast"] = True; disp["announce_to_chat"] = chat
    if hidden: disp["hidden"] = True
    d["display"] = disp; d["criteria"] = {"code": {"trigger": "minecraft:impossible"}}
    return json.dumps(d, indent=2) + "\n"


def lang_insert(path, after_key, entries):
    data = json.loads(rd(path), object_pairs_hook=OrderedDict)
    if all(k in data for k in entries): return
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
    d = (lambda t: re.sub(r"\.getInt\(([^()]*)\)", r".getIntOr(\1, 0)", re.sub(r"\.getCompound\(([^()]*)\)", r".getCompoundOrEmpty(\1)", t)).replace("getAllKeys()", "keySet()")) if name == "rf26" else (lambda t: t)
    j = lambda *a: os.path.join(tree, *a)
    fm = j(J, "fishing/FishingManager.java")
    sub(fm, '        if (provs >= 5) com.riverfishing.quest.AnglerAdvancements.grant(sp, "five_provinces");\n', d(ADV_BLOCK), "adv block 2")
    sub(fm, "                                @org.jetbrains.annotations.Nullable BlockPos caughtAt) {\n        // A floating item sits in the AIR block above the surface — resolve to the actual water.\n",
        "                                @org.jetbrains.annotations.Nullable BlockPos caughtAt) {\n        if (thrower != null) com.riverfishing.quest.AnglerAdvancements.grant(thrower, \"released\");   // §progression-2\n        // A floating item sits in the AIR block above the surface — resolve to the actual water.\n", "released")
    sub(j(J, "fishing/JournalData.java"), "    /** Records a fish landed through the ice (§winter-quests): a counter for winter-fishing goals. */", d(JOURNAL_ADD), "journal add 2")
    jc = j(J, "command/JournalCommand.java")
    sub(jc, "            JournalData.record(sp, id, w);\n", UNLOCK_ADD, "unlock traits")
    s = rd(jc)
    if "root.put(\"seasons\", seasons);" not in s:
        m = re.search(r"        root\.putInt\(JournalData\.ICE, [^\n]*\n", s); assert m, "unlock ice line"
        s = s[:m.end()] + d(UNLOCK_TAIL) + s[m.end():]; wr(jc, s)
    adir = j("common/src/main/resources/data/riverfishing", "advancements" if old else "advancement", "riverfishing")
    for aid, parent, icon, frame, hidden, chat in ADV:
        wr(os.path.join(adir, aid + ".json"), adv_json(aid, parent, icon, frame, hidden, chat, old))
    for code in ("en_us", "ru_ru", "uk_ua"):
        adv = OrderedDict()
        for aid, *_ in ADV:
            t, ds = ADV_LANG[code][aid]
            adv["advancement.riverfishing.%s.title" % aid] = t; adv["advancement.riverfishing.%s.description" % aid] = ds
        lang_insert(j(LANG, code + ".json"), "advancement.riverfishing.keepnet_sale.description", adv)
    print("  patched", name)


for t in (sys.argv[1:] or TREES): run(t)
