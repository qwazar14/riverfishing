# -*- coding: utf-8 -*-
"""Check that a translated wiki calls vanilla Minecraft items what Minecraft calls them.

    python tools/check_wiki_vanilla.py ru uk

A player reads the recipe tables with the vanilla recipe book open. If the wiki says "Железный
самородок" where the game says "Кусочек железа", the recipe is unfindable — and nothing else in the
toolchain notices, because the page is perfectly good prose.

The check needs no hand-maintained list of wrong spellings. For every vanilla item the mod's recipes
actually use, it reads Mojang's own name in English and in the target language, then: **if the English
page names the item, the translated page must contain the official translated name.** A miss is either
a different word or a dropped mention, and both are worth a look.

Mojang's translations come from the PrismLauncher asset store (`assets/indexes/5.json` -> the object
store); English lives in the client jar instead, since it is not shipped as an asset. Both are read at
check time and nothing is copied into this repo — Mojang's strings are not ours to redistribute.

Exits non-zero if anything is missing, so it can gate a release.
"""
import glob, io, json, os, re, sys, zipfile

WIKI = "docs/wiki"
RECIPES = "common/src/main/resources/data/riverfishing"
INDEX = os.path.expandvars(r"%APPDATA%\PrismLauncher\assets\indexes\5.json")
OBJECTS = os.path.expandvars(r"%APPDATA%\PrismLauncher\assets\objects")
CLIENT = glob.glob(os.path.expanduser(r"~/.gradle/caches/fabric-loom/1.21.1/minecraft-client.jar"))


def mojang(locale):
    objs = json.load(io.open(INDEX, encoding="utf-8"))["objects"]
    key = "minecraft/lang/%s.json" % locale
    if key not in objs:                       # en_us is not in the asset store — it ships in the jar
        if not CLIENT:
            return None
        with zipfile.ZipFile(CLIENT[0]) as z:
            return json.loads(z.read("assets/minecraft/lang/en_us.json").decode("utf-8"))
    h = objs[key]["hash"]
    return json.load(io.open(os.path.join(OBJECTS, h[:2], h), encoding="utf-8"))


# English words that are also the name of a vanilla item. The wiki uses both senses, and no amount of
# string matching can tell "a falling glass" (the barometer) from a Glass block.
HOMONYMS = {"String", "Glass"}


def stems(name):
    """Russian and Ukrainian decline, and the adjective declines with the noun ("Костная мука" ->
    "Костной муки"), so a nominative search calls every correct translation missing. Trim two letters
    off each word and require them all to appear somewhere on the page, not adjacently."""
    return [w[:max(3, len(w) - 2)] for w in name.lower().split()]


def used_ids():
    ids = set()
    for f in glob.glob(RECIPES + "/recipe*/**/*.json", recursive=True):
        ids.update(re.findall(r'"minecraft:([a-z_]+)"', io.open(f, encoding="utf-8").read()))
    return ids


def main(langs):
    en = mojang("en_us")
    if not en:
        print("no Minecraft client jar found — skipping"); return 0
    bad = 0
    for lang in langs:
        tr = mojang({"ru": "ru_ru", "uk": "uk_ua"}.get(lang, lang))
        if not tr:
            print("%s: no Minecraft translation in the asset store — skipped" % lang); continue
        pairs = []
        for i in sorted(used_ids()):
            for pre in ("item.minecraft.", "block.minecraft."):
                if pre + i in en and pre + i in tr:
                    pairs.append((en[pre + i], tr[pre + i]))
                    break
        # "Glass Pane" contains "Glass", so search longest-first and blank out each hit as it is taken.
        pairs.sort(key=lambda p: -len(p[0]))
        # The mod's own English names swallow vanilla words too — "Stick Rod" is not a Stick, and
        # "Braided Line" is not a Line. Mask them out of the English before looking for vanilla names.
        modnames = sorted((v for k, v in json.load(io.open(
            "common/src/main/resources/assets/riverfishing/lang/en_us.json", encoding="utf-8")).items()
            if k.startswith(("item.riverfishing.", "block.riverfishing."))), key=len, reverse=True)

        misses = 0
        for f in sorted(x for x in os.listdir(WIKI) if x.endswith(".md")):
            p = os.path.join(WIKI, lang, f)
            if not os.path.exists(p):
                continue
            src = io.open(os.path.join(WIKI, f), encoding="utf-8").read()
            out = io.open(p, encoding="utf-8").read()
            # Code spans and fences hold ids, not names — `stick` there is a rod class, not a Stick.
            src = re.sub(r"```.*?```", " ", src, flags=re.S)
            src = re.sub(r"`[^`\n]*`", " ", src)
            for m in modnames:
                src = re.sub(r"(?<![A-Za-z])%s(?![A-Za-z])" % re.escape(m), " ", src, flags=re.I)
            for name_en, name_tr in pairs:
                # Word-bounded so "Stick" does not match "Stickleback"; case-insensitive because the
                # wiki is inconsistent about capitalising mid-sentence.
                pat = r"(?<![A-Za-z])%s(?![A-Za-z])" % re.escape(name_en)
                if not re.search(pat, src, re.I):
                    continue
                src = re.sub(pat, " ", src, flags=re.I)      # claimed — do not re-match as a shorter name
                if name_en in HOMONYMS:
                    continue
                low = out.lower()
                if not all(s in low for s in stems(name_tr)):
                    print("  %-24s %r is not called %r" % (f, name_en, name_tr))
                    misses += 1
        print("%s: %d vanilla ingredients checked, %d missing" % (lang, len(pairs), misses))
        bad += misses
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:] or ["ru", "uk"]))
