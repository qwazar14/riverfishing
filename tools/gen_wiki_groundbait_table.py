# -*- coding: utf-8 -*-
"""Emit the "what each species wants" table into docs/wiki/groundbait.md, in all three languages.

    python tools/gen_wiki_groundbait_table.py

Fraction and nutrition are per-species numbers now (§groundbait-one-jar), and "a big fraction calls big
fish" is only advice a player can act on if they can look the number up. Seventy-nine species times three
languages is 237 rows of hand-typed decimals — the exact shape of thing that goes stale silently, so it
is generated from the profiles the engine actually reads and rewritten in place between the markers.

The word beside each number is not decoration: nobody plans a session in two decimal places. The bands
are the same ones the jar's own tooltip uses, so the page and the item agree.
"""
import io, json, os, re, sys

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PROF = os.path.join(REPO, "common/src/main/resources/data/riverfishing/fish_profiles")
LANGDIR = os.path.join(REPO, "common/src/main/resources/assets/riverfishing/lang")
WIKI = os.path.join(REPO, "docs/wiki")

MARK = "<!-- SPECIES-GB -->"

LOC = [("", "en_us"), ("ru", "ru_ru"), ("uk", "uk_ua")]

HEAD = {
    "en_us": ("| Species | Fraction | Nutrition |", "|---|---|---|"),
    "ru_ru": ("| Вид | Фракция | Питательность |", "|---|---|---|"),
    "uk_ua": ("| Вид | Фракція | Поживність |", "|---|---|---|"),
}
LEAD = {
    "en_us": "Sorted finest first, which is also smallest first — that is what the star means.",
    "ru_ru": "Отсортировано от мелкой фракции к крупной, то есть заодно от мелкой рыбы к крупной — в этом и смысл звёздочки.",
    "uk_ua": "Відсортовано від дрібної фракції до грубої, тобто заразом від дрібної риби до великої — у цьому й сенс зірочки.",
}
# The same three bands the jar tooltip prints, so the wiki and the item never disagree.
COARSE = {
    "en_us": ("fine, clouds", "mixed", "coarse, holds big fish"),
    "ru_ru": ("мелкая, мутит", "смешанная", "крупная, держит крупную рыбу"),
    "uk_ua": ("дрібна, каламутить", "змішана", "груба, тримає велику рибу"),
}
RICH = {
    "en_us": ("lean", "moderate", "rich"),
    "ru_ru": ("постная", "средняя", "сытная"),
    "uk_ua": ("пісна", "середня", "ситна"),
}


def band(value, words, low, high):
    return words[2] if value >= high else (words[1] if value >= low else words[0])


def main():
    species = []
    for name in sorted(os.listdir(PROF)):
        if not name.endswith(".json"):
            continue
        data = json.loads(io.open(os.path.join(PROF, name), encoding="utf-8").read())
        gb = data.get("ideal", {}).get("groundbait")
        if not isinstance(gb, dict):
            sys.exit("%s still carries the old groundbait list — run tools first" % name)
        species.append((name[:-5], gb["fraction"], gb["nutrition"]))
    species.sort(key=lambda s: (s[1], s[0]))
    print("  %d species" % len(species))

    for sub, lang in LOC:
        names = json.loads(io.open(os.path.join(LANGDIR, lang + ".json"), encoding="utf-8").read())
        rows = [LEAD[lang], "", HEAD[lang][0], HEAD[lang][1]]
        missing = []
        for sid, frac, nutr in species:
            label = names.get("fish.riverfishing." + sid)
            if label is None:
                missing.append(sid)
                continue
            rows.append("| %s | %.2f — %s | %.2f — %s |"
                        % (label, frac, band(frac, COARSE[lang], 0.30, 0.65),
                           nutr, band(nutr, RICH[lang], 0.35, 0.70)))
        if missing:
            # A species with no name in this language would print its raw id into the table, which reads
            # as a bug to every player and as data to nobody. Refuse rather than ship it.
            sys.exit("%s has no name for: %s" % (lang, ", ".join(missing)))

        path = os.path.join(WIKI, sub, "groundbait.md") if sub else os.path.join(WIKI, "groundbait.md")
        text = io.open(path, encoding="utf-8").read()
        block = MARK + "\n\n" + "\n".join(rows) + "\n"
        # Replace between the marker and the next horizontal rule, so re-running is idempotent.
        pattern = re.compile(re.escape(MARK) + r".*?(?=\n---\n)", re.S)
        if not pattern.search(text):
            sys.exit("%s: no %s marker" % (path, MARK))
        io.open(path, "w", encoding="utf-8", newline="\n").write(pattern.sub(block, text, count=1))
        print("  %-3s %d rows" % (sub or "en", len(species)))


main()
