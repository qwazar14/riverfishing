# -*- coding: utf-8 -*-
"""Wire the nine 0.7.0 species into all three trees: models, icons, sprites, cutting recipes, lang, registry.

    python tools/wire_florida_species.py

Run AFTER tools/gen_florida_species.py (profiles) and tools/GenFishSwap.java (sprites).

Every per-tree quirk is COPIED from a species that already works in that tree rather than hardcoded,
because the three trees genuinely differ and hardcoding is how you ship a magenta square:
  * the base item model is `builtin/entity` on 1.20.1 / 1.21.1 (a BEWLR draws it) but
    `item/generated` with a layer0 on 26.x — getting this wrong is exactly the 0.6.0 bug where four
    new species rendered as missing-texture checkerboards on 26.x only;
  * the recipe folder is `recipes/` on 1.20.1 and `recipe/` from 1.21 on.
So: read the donor's own files in each tree, swap the id, write. Whatever quirk exists is inherited.

26.x additionally needs an `items/<id>.json` range_dispatch and eight `fish_scaled/<id>_N` models per
species; those come from that tree's own tools/gen_dynamic_icons.py, which derives its species list from
(profiles ∩ models). This script creates the models, so that generator picks the nine up on its next run.
"""
import io, json, os, re, shutil, sys

MAIN = r"C:/Users/Qwazar/VS Code Projects/fishing mod"
TREES = [MAIN, r"C:/Users/Qwazar/wt/rf1201", r"C:/Users/Qwazar/wt/rf26"]
DONOR = "largemouth_bass"          # a freshwater predator that works in all three trees

A = "common/src/main/resources/assets/riverfishing"
D = "common/src/main/resources/data/riverfishing"

SPECIES = ["peacock_bass", "bullseye_snakehead", "mayan_cichlid", "oscar",
           "striped_bass", "bluefish", "jack_crevalle", "tarpon", "snook"]

NAMES = {
    "peacock_bass":       ("Peacock bass",       "Павлиний окунь",       "Павлиній окунь"),
    "bullseye_snakehead": ("Bullseye snakehead", "Глазчатый змееголов",  "Плямистий змієголов"),
    "mayan_cichlid":      ("Mayan cichlid",      "Цихлазома майя",       "Цихлазома майя"),
    "oscar":              ("Oscar",              "Астронотус",           "Астронотус"),
    "striped_bass":       ("Striped bass",       "Полосатый лаврак",     "Смугастий лаврак"),
    "bluefish":           ("Bluefish",           "Луфарь",               "Луфар"),
    "jack_crevalle":      ("Jack crevalle",      "Каранкс",              "Каранкс"),
    "tarpon":             ("Tarpon",             "Тарпон",               "Тарпон"),
    "snook":              ("Snook",              "Снук",                 "Снук"),
}

# Habitat, then tackle, then the fight — the shape every one of the 70 existing entries uses.
DESC = {
    "peacock_bass": (
        "A tropical bully of warm canals, and the reason local anglers carry spare tackle. Wobblers, "
        "poppers and cranks; it hits like a torpedo and keeps going.",
        "Тропический хулиган тёплых каналов — из-за него местные носят запасную снасть. Воблеры, "
        "топвотер, кренки; бьёт как торпеда и не останавливается.",
        "Тропічний хуліган теплих каналів — через нього місцеві носять запасну снасть. Воблери, "
        "топвотер, кренки; б'є як торпеда і не спиняється."),
    "bullseye_snakehead": (
        "An air-breathing ambusher of weedy canals, marked with the eyespot it is named for. Livebait "
        "and soft plastics: one furious run, then it gives up surprisingly fast.",
        "Дышащий воздухом засадчик заросших каналов, с тем самым глазком на хвосте. Живец и силикон: "
        "один яростный рывок — и сдаётся на удивление быстро.",
        "Засадник зарослих каналів, що дихає повітрям, із тією самою плямою на хвості. Живець і силікон: "
        "один шалений рвиок — і здається на подив швидко."),
    "mayan_cichlid": (
        "A small barred cichlid of warm shallows, bold out of all proportion to its size. Worm, maggot "
        "or bloodworm on a light float rig.",
        "Небольшая полосатая цихлида тёплых отмелей, наглая не по размеру. Червь, опарыш или мотыль на "
        "лёгкой поплавочной снасти.",
        "Невелика смугаста цихліда теплих мілин, зухвала не за розміром. Червяк, опариш або мотиль на "
        "легкій поплавковій снасті."),
    "oscar": (
        "A heavy-bodied dark cichlid with orange marbling and a false eye on the tail. Worm and livebait; "
        "a stubborn, short-range scrap.",
        "Плотная тёмная цихлида с оранжевым мрамором и ложным глазом у хвоста. Червь и живец; упрямая "
        "возня на короткой дистанции.",
        "Щільна темна цихліда з оранжевим мармуром і фальшивим оком біля хвоста. Червяк і живець; "
        "впертий двобій на короткій дистанції."),
    "striped_bass": (
        "Seven dark stripes down a silver flank; runs the surf and the estuaries, and tolerates cold. "
        "Livebait, cut bait and spoons — a bulldog that simply does not stop.",
        "Семь тёмных полос по серебряному боку; ходит прибоем и устьями, холода не боится. Живец, "
        "резка и колебалки — бульдог, который просто не останавливается.",
        "Сім темних смуг по срібному боці; ходить прибоєм і устями, холоду не боїться. Живець, "
        "нарізка і колебалки — бульдог, який просто не спиняється."),
    "bluefish": (
        "A savage pack hunter of the coast — a mouthful of teeth that goes through mono. Spoons and "
        "castmasters, and use a leader.",
        "Свирепый стайный охотник побережья — пасть, которая перекусывает монофил. Колебалки и "
        "кастмастеры, и ставьте поводок.",
        "Лютий стайний хижак узбережжя — паща, що перекушує моноліску. Колебалки й кастмастери, "
        "і ставте повідець."),
    "jack_crevalle": (
        "Called canal tuna where it swims, and the byword for a fish that will not quit. Poppers and "
        "spoons; expect to lose the first three fights.",
        "Там, где он водится, его зовут канальным тунцом — и он образец рыбы, которая не сдаётся. "
        "Топвотер и колебалки; первые три боя вы проиграете.",
        "Там, де він водиться, його звуть канальним тунцем — і він взірець риби, яка не здається. "
        "Топвотер і колебалки; перші три двобої ви програєте."),
    "tarpon": (
        "The silver king: a hundred kilos of chrome scales that answers the hook by leaving the water. "
        "Livebait on the heaviest gear you own.",
        "Серебряный король: сотня килограммов хромовой чешуи, которая на подсечку отвечает выходом из "
        "воды. Живец и самая тяжёлая снасть, что у вас есть.",
        "Срібний король: сотня кілограмів хромової луски, що на підсічку відповідає виходом із води. "
        "Живець і найважча снасть, яку маєте."),
    "snook": (
        "The hard black lateral line gives it away. Lives against structure in warm inshore water and "
        "runs straight back into it — win the first seconds or lose the fish.",
        "Выдаёт себя резкой чёрной боковой линией. Держится у структуры в тёплой прибрежной воде и в неё "
        "же уходит — выиграйте первые секунды или потеряете рыбу.",
        "Виказує себе різкою чорною бічною лінією. Тримається біля структури в теплій прибережній воді і "
        "в неї ж іде — виграйте перші секунди або втратите рибу."),
}


def sub_id(text, old, new):
    """Swap the donor id for the new one on whole-word boundaries only."""
    return re.sub(r"(?<![A-Za-z0-9_])" + re.escape(old) + r"(?![A-Za-z0-9_])", new, text)


def main():
    made = 0
    for tree in TREES:
        name = os.path.basename(tree)
        assets, data = os.path.join(tree, A), os.path.join(tree, D)

        # Recipe folder moved from `recipes` to `recipe` in the 1.21 data-pack layout — detect it.
        rec_dir = None
        for cand in ("recipe/cutting", "recipes/cutting"):
            if os.path.isdir(os.path.join(data, cand)):
                rec_dir = os.path.join(data, cand)
                break
        if rec_dir is None:
            sys.exit("%s: no cutting-recipe folder found" % name)

        model_src = io.open(os.path.join(assets, "models/item", DONOR + ".json"), encoding="utf-8").read()
        icon_src = io.open(os.path.join(assets, "models/item/fish_icon", DONOR + ".json"), encoding="utf-8").read()
        rec_src = io.open(os.path.join(rec_dir, DONOR + ".json"), encoding="utf-8").read()
        builtin = "builtin/entity" in model_src

        for sp in SPECIES:
            io.open(os.path.join(assets, "models/item", sp + ".json"), "w",
                    encoding="utf-8", newline="\n").write(sub_id(model_src, DONOR, sp))
            io.open(os.path.join(assets, "models/item/fish_icon", sp + ".json"), "w",
                    encoding="utf-8", newline="\n").write(sub_id(icon_src, DONOR, sp))
            io.open(os.path.join(rec_dir, sp + ".json"), "w",
                    encoding="utf-8", newline="\n").write(sub_id(rec_src, DONOR, sp))
            if tree != MAIN:   # the sprites are generated in MAIN; copying a file onto itself throws
                shutil.copy2(os.path.join(MAIN, A, "textures/item/fish", sp + ".png"),
                             os.path.join(assets, "textures/item/fish", sp + ".png"))
            made += 4

        # ---- lang, three files per tree ----
        for idx, loc in enumerate(("en_us", "ru_ru", "uk_ua")):
            p = os.path.join(assets, "lang", loc + ".json")
            txt = io.open(p, encoding="utf-8").read()
            add = []
            for sp in SPECIES:
                for prefix, val in (("item", NAMES[sp][idx]), ("fish", NAMES[sp][idx]),
                                    ("fishdesc", DESC[sp][idx])):
                    key = '"%s.riverfishing.%s"' % (prefix, sp)
                    if key in txt:
                        continue
                    add.append('    %s:  %s,' % (key, json.dumps(val, ensure_ascii=False)))
            if add:
                anchor = '    "item.riverfishing.%s":' % DONOR
                assert txt.count(anchor) == 1, (p, anchor)
                txt = txt.replace(anchor, "\n".join(add) + "\n" + anchor)
                io.open(p, "w", encoding="utf-8", newline="\n").write(txt)
                json.load(io.open(p, encoding="utf-8"))   # must still parse

        # ---- registry: one line in FISH_SPECIES ----
        mi = os.path.join(tree, "common/src/main/java/com/riverfishing/registry/ModItems.java")
        s = io.open(mi, encoding="utf-8").read()
        if "peacock_bass" not in s:
            entry = ('            // §florida-nine (0.7.0): the US/Florida wave, from a player who also\n'
                     '            // found the §session-guard bug that 0.6.1 fixes.\n'
                     '            "peacock_bass", "bullseye_snakehead", "mayan_cichlid", "oscar",\n'
                     '            "striped_bass", "bluefish", "jack_crevalle", "tarpon", "snook",\n')
            m = re.search(r"(public static final String\[\] FISH_SPECIES = \{\n)", s)
            assert m, mi
            s = s[:m.end()] + entry + s[m.end():]
            io.open(mi, "w", encoding="utf-8", newline="\n").write(s)

        n = len(json.load(io.open(os.path.join(assets, "lang/en_us.json"), encoding="utf-8")))
        print("  %-12s models+icons+recipes+sprites written, %s base model, en_us now %d keys"
              % (name, "builtin/entity" if builtin else "item/generated", n))

    print("\n%d asset files across %d trees; 9 species registered" % (made, len(TREES)))
    return 0


if __name__ == "__main__":
    sys.exit(main())
