# -*- coding: utf-8 -*-
"""Rebuild the families table on every species.md from the profiles themselves.

    py tools/gen_wiki_families.py

The table says "Carp family (22)" and then lists them, in three languages that order the rows
differently and translate the labels — so a row is identified by the fish ALREADY IN IT: whichever
group's names overlap that cell most is the group the row belongs to. The label is kept as written and
only the count and the member list are rewritten, which is what goes stale when a wave lands.
"""
import io, json, os, re, sys

TREE = r"C:/Users/Qwazar/VS Code Projects/fishing mod"
PROF = os.path.join(TREE, "common/src/main/resources/data/riverfishing/fish_profiles")
LANG = os.path.join(TREE, "common/src/main/resources/assets/riverfishing/lang")
LOCALES = [("", "en_us"), ("ru/", "ru_ru"), ("uk/", "uk_ua")]

profiles = {f[:-5]: json.load(io.open(os.path.join(PROF, f), encoding="utf-8")) for f in os.listdir(PROF)}
roster_src = io.open(os.path.join(TREE, "common/src/main/java/com/riverfishing/registry/ModItems.java"),
                     encoding="utf-8").read()
roster = re.findall(r'"([a-z_]+)"', re.search(r"FISH_SPECIES = \{(.*?)\};", roster_src, re.S).group(1))

changed = 0
for sub, lang_file in LOCALES:
    names = json.load(io.open(os.path.join(LANG, lang_file + ".json"), encoding="utf-8"))
    groups = {}
    for sid in roster:
        if sid in profiles:
            groups.setdefault(profiles[sid].get("group", "other"), []).append(
                names.get("fish.riverfishing." + sid, sid))
    path = os.path.join(TREE, "docs/wiki", sub, "species.md")
    lines = io.open(path, encoding="utf-8").read().split("\n")
    out = []
    for line in lines:
        m = re.match(r"\| (\*\*.+?\*\*) \(\d+\) \| (.+) \|$", line)
        if not m:
            out.append(line)
            continue
        cell = {x.strip() for x in m.group(2).split(",")}
        best, score = None, 0
        for g, members in groups.items():
            n = len(cell & set(members))
            if n > score:
                best, score = g, n
        if best is None:
            out.append(line)
            continue
        # Sort the way the language reads, not the way the codepoints fall: Ё belongs with Е, and
        # Ukrainian І/Ї/Є/Ґ belong with И/Е/Г rather than at the end of the alphabet.
        fold = str.maketrans("ЁёІіЇїЄєҐґ", "ЕеИиИиЕеГг")
        members = sorted(groups[best], key=lambda x: x.translate(fold).lower())
        out.append("| %s (%d) | %s |" % (m.group(1), len(members), ", ".join(members)))
        changed += 1
    io.open(path, "w", encoding="utf-8", newline="\n").write("\n".join(out))

print("%d family rows rebuilt across %d languages" % (changed, len(LOCALES)))
