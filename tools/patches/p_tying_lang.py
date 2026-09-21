# -*- coding: utf-8 -*-
"""§tying: the vise's strings in three languages — item, block, templates, materials, screen.

    py -X utf8 tools/patches/p_tying_lang.py <root>
"""
import io, json, os, sys

ROOT = sys.argv[1]
L = os.path.join(ROOT, "common/src/main/resources/assets/riverfishing/lang")
T = {
    "en_us": {"item.riverfishing.tied_lure": "Tied Lure", "block.riverfishing.tying_vise": "Tying Vise",
              "tooltip.riverfishing.tied_lure": "Tied at the vise — fishes as a winter jig",
              "tooltip.riverfishing.tied_size": "%s mm, %s g", "tooltip.riverfishing.tied_hook": "Hook №%s",
              "tooltip.riverfishing.tied_eyes": "Eyes — predators look twice", "tooltip.riverfishing.tied_flash": "Flash — bead and tinsel",
              "tooltip.riverfishing.tied_action": "Hackle — it works on the pause", "tooltip.riverfishing.tied_by": "Tied by %s",
              "gui.riverfishing.tie": "Tie", "gui.riverfishing.tie_clear": "Clear", "gui.riverfishing.tie_mirror": "Mirror",
              "tied.riverfishing.pellet": "Pellet jig", "tied.riverfishing.drop": "Drop jig", "tied.riverfishing.devil": "Devil jig",
              "tied.riverfishing.ant": "Ant", "tied.riverfishing.nymph": "Nymph", "tied.riverfishing.streamer": "Streamer",
              "tied.riverfishing.shrimp": "Shrimp", "tied.riverfishing.dry_fly": "Dry fly", "tied.riverfishing.none": "Curiosity",
              "material.riverfishing.hackle": "Hackle (feather)", "material.riverfishing.fur": "Dubbing (wool)",
              "material.riverfishing.bead_iron": "Iron bead (nugget)", "material.riverfishing.bead_gold": "Gold bead (nugget)",
              "material.riverfishing.tinsel": "Tinsel (copper)", "material.riverfishing.eye": "Eye (ink sac)"},
    "ru_ru": {"item.riverfishing.tied_lure": "Самодельная приманка", "block.riverfishing.tying_vise": "Вязальные тиски",
              "tooltip.riverfishing.tied_lure": "Связана в тисках — ловит как мормышка",
              "tooltip.riverfishing.tied_size": "%s мм, %s г", "tooltip.riverfishing.tied_hook": "Крючок №%s",
              "tooltip.riverfishing.tied_eyes": "Глазки — хищник присматривается", "tooltip.riverfishing.tied_flash": "Блеск — бусина и люрекс",
              "tooltip.riverfishing.tied_action": "Оперение — играет на паузе", "tooltip.riverfishing.tied_by": "Связал %s",
              "gui.riverfishing.tie": "Связать", "gui.riverfishing.tie_clear": "Очистить", "gui.riverfishing.tie_mirror": "Отразить",
              "tied.riverfishing.pellet": "Дробинка", "tied.riverfishing.drop": "Капля", "tied.riverfishing.devil": "Чёртик",
              "tied.riverfishing.ant": "Муравей", "tied.riverfishing.nymph": "Нимфа", "tied.riverfishing.streamer": "Стример",
              "tied.riverfishing.shrimp": "Креветка", "tied.riverfishing.dry_fly": "Сухая мушка", "tied.riverfishing.none": "Нечто",
              "material.riverfishing.hackle": "Оперение (перо)", "material.riverfishing.fur": "Даббинг (шерсть)",
              "material.riverfishing.bead_iron": "Железная бусина (самородок)", "material.riverfishing.bead_gold": "Золотая бусина (самородок)",
              "material.riverfishing.tinsel": "Люрекс (медь)", "material.riverfishing.eye": "Глазок (чернильный мешок)"},
    "uk_ua": {"item.riverfishing.tied_lure": "Саморобна принада", "block.riverfishing.tying_vise": "В'язальні лещата",
              "tooltip.riverfishing.tied_lure": "Зв'язана в лещатах — ловить як мормишка",
              "tooltip.riverfishing.tied_size": "%s мм, %s г", "tooltip.riverfishing.tied_hook": "Гачок №%s",
              "tooltip.riverfishing.tied_eyes": "Очка — хижак придивляється", "tooltip.riverfishing.tied_flash": "Блиск — намистина й люрекс",
              "tooltip.riverfishing.tied_action": "Оперення — грає на паузі", "tooltip.riverfishing.tied_by": "Зв'язав %s",
              "gui.riverfishing.tie": "Зв'язати", "gui.riverfishing.tie_clear": "Очистити", "gui.riverfishing.tie_mirror": "Віддзеркалити",
              "tied.riverfishing.pellet": "Дробинка", "tied.riverfishing.drop": "Крапля", "tied.riverfishing.devil": "Чортик",
              "tied.riverfishing.ant": "Мураха", "tied.riverfishing.nymph": "Німфа", "tied.riverfishing.streamer": "Стример",
              "tied.riverfishing.shrimp": "Креветка", "tied.riverfishing.dry_fly": "Суха мушка", "tied.riverfishing.none": "Щось",
              "material.riverfishing.hackle": "Оперення (перо)", "material.riverfishing.fur": "Дабінг (вовна)",
              "material.riverfishing.bead_iron": "Залізна намистина (самородок)", "material.riverfishing.bead_gold": "Золота намистина (самородок)",
              "material.riverfishing.tinsel": "Люрекс (мідь)", "material.riverfishing.eye": "Очко (чорнильний мішок)"},
}
DYE = {"en_us": ["White", "Orange", "Magenta", "Light blue", "Yellow", "Lime", "Pink", "Grey", "Light grey", "Cyan", "Purple", "Blue", "Brown", "Green", "Red", "Black"],
       "ru_ru": ["белая", "оранжевая", "пурпурная", "голубая", "жёлтая", "лаймовая", "розовая", "серая", "светло-серая", "бирюзовая", "фиолетовая", "синяя", "коричневая", "зелёная", "красная", "чёрная"],
       "uk_ua": ["біла", "помаранчева", "пурпурова", "блакитна", "жовта", "лаймова", "рожева", "сіра", "світло-сіра", "бірюзова", "фіолетова", "синя", "коричнева", "зелена", "червона", "чорна"]}
IDS = ["white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"]
WORD = {"en_us": "%s thread", "ru_ru": "Нить, %s", "uk_ua": "Нитка, %s"}
for loc, extra in T.items():
    p = os.path.join(L, loc + ".json")
    d = json.load(io.open(p, encoding="utf-8"))
    for i, k in enumerate(IDS):
        extra["material.riverfishing.thread_" + k] = WORD[loc] % DYE[loc][i]
    d.update(extra)
    io.open(p, "w", encoding="utf-8", newline="\n").write(json.dumps(d, ensure_ascii=False, indent=2) + "\n")
    print("  %s: %d tying keys" % (loc, len(extra)))
