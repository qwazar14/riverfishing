# -*- coding: utf-8 -*-
"""§species-table: rewrite, wholesale, the two wiki tables the spreadsheet import made stale in all
three languages — the tackle table in species.md (rod / rig / reel / hook tolerance columns are gone,
every species regenerated) and the two province tables in provinces.md (the fifth province, the counts,
the cosmopolitan list). Everything else in those pages is left as written.
    py tools/regen_species_wiki_tables.py"""
import io, json, os, re, sys

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(REPO, "tools"))
import gen_species_tables as G   # the row builder and the label vocabulary live there

PROF = G.PROF; WIKI = G.WIKI
HEAD = {
    "en": ("| Species | Best baits (score) | Hook | Line | Groundbait (fraction / nutrition) | Leader |", "|---|---|---|---|---|---|"),
    "ru": ("| Вид | Лучшие наживки (оценка) | Крючок | Леска | Прикормка (фракция / питательность) | Поводок |", "|---|---|---|---|---|---|"),
    "uk": ("| Вид | Найкращі наживки (оцінка) | Гачок | Волосінь | Прикормка (фракція / поживність) | Повідець |", "|---|---|---|---|---|---|"),
}
OLD_HEAD = re.compile(r"^\| (Species|Вид) \| (Best baits|Лучшие наживки|Найкращі наживки)")
PROV = {
    "en": {"palearctic": "Palearctic", "nearctic": "Nearctic", "neotropic": "Neotropic", "indomalaya": "Indomalaya", "afrotropical": "Afrotropical"},
    "ru": {"palearctic": "Палеарктика", "nearctic": "Неарктика", "neotropic": "Неотропика", "indomalaya": "Индомалайя", "afrotropical": "Афротропика"},
    "uk": {"palearctic": "Палеарктика", "nearctic": "Неарктика", "neotropic": "Неотропіка", "indomalaya": "Індомалайя", "afrotropical": "Афротропіка"},
}
ROUGHLY = {"en": "Sub-Saharan Africa", "ru": "Африка южнее Сахары", "uk": "Африка на південь від Сахари"}
ORDER = ["palearctic", "nearctic", "neotropic", "indomalaya", "afrotropical"]


def few(n): return n % 10 in (2, 3, 4) and n % 100 not in (12, 13, 14)   # 2 вида / 5 видов
def rd(p): return io.open(p, encoding="utf-8").read()
def wr(p, s): io.open(p, "w", encoding="utf-8", newline="\n").write(s)


def profiles():
    out = {}
    for f in sorted(os.listdir(PROF)):
        if f.endswith(".json"): out[f[:-5]] = json.load(io.open(os.path.join(PROF, f), encoding="utf-8"))
    return out


def replace_table(text, head_re, new_lines):
    lines = text.split("\n")
    hi = next(i for i, l in enumerate(lines) if head_re.match(l))
    ei = G.table_end(lines, hi)
    return "\n".join(lines[:hi] + new_lines + lines[ei:])


def main():
    prof = profiles()
    langs = {code: json.load(io.open(os.path.join(G.LANGDIR, code + ".json"), encoding="utf-8")) for _, _, _, code in G.LOC}
    # ---- the tackle table ----
    for loc, sub, img, code in G.LOC:
        path = os.path.join(WIKI, sub, "species.md") if sub else os.path.join(WIKI, "species.md")
        text = rd(path)
        rows = [HEAD[loc][0], HEAD[loc][1]]
        for sp in sorted(prof, key=lambda s: langs[code].get("fish.riverfishing." + s, s).lower()):
            rows.append(G.row_tackle(sp, prof[sp], loc, langs[code].get("fish.riverfishing." + sp) or sp))
        wr(path, replace_table(text, OLD_HEAD, rows)); print("  species.md", loc, len(rows) - 2, "rows")
    # ---- the province tables ----
    counts = {k: 0 for k in ORDER}; allfive = []
    for sp, p in prof.items():
        pr = p.get("provinces", [])
        for k in pr:
            if k in counts: counts[k] += 1
        if not pr or len(set(pr)) >= 5: allfive.append(sp)   # §sea-roamers: no provinces = everywhere
    for loc, sub, img, code in G.LOC:
        path = os.path.join(WIKI, sub, "provinces.md") if sub else os.path.join(WIKI, "provinces.md")
        text = rd(path)
        names = PROV[loc]
        # the roughly-where table: add the fifth row after Indomalaya's
        row_indo = [l for l in text.split("\n") if l.startswith("| **" + names["indomalaya"] + "** |")][0]
        if names["afrotropical"] not in text:
            text = text.replace(row_indo, row_indo + "\n| **%s** | %s |" % (names["afrotropical"], ROUGHLY[loc]))
        # the counts table: rewrite the five rows
        for k in ORDER:
            text = re.sub(r"^\| %s \| \*\*\d+\*\* \|$" % re.escape(names[k]), "| %s | **%d** |" % (names[k], counts[k]), text, flags=re.M)
        if "| %s | **" % names["afrotropical"] not in text:
            indo_count = [l for l in text.split("\n") if l.startswith("| %s | **" % names["indomalaya"])][0]
            text = text.replace(indo_count, indo_count + "\n| %s | **%d** |" % (names["afrotropical"], counts["afrotropical"]))
        # the cosmopolitan paragraph
        fish = ", ".join("**%s**" % (langs[code].get("fish.riverfishing." + s) or s) for s in sorted(allfive))
        para = {"en": "%d species are on **all five** provinces — the ones the oceans carry everywhere: %s. Everything else is missing from at least one part of the world." % (len(allfive), fish),
                "ru": "%d %s во **всех пяти** провинциях — те, кого разносят океаны: %s. Всё остальное отсутствует хотя бы в одной части света." % (len(allfive), "вида стоят" if few(len(allfive)) else "видов стоят", fish),
                "uk": "%d %s на **всіх п'яти** провінціях — ті, кого розносять океани: %s. Усе інше відсутнє принаймні в одній частині світу." % (len(allfive), "види стоять" if few(len(allfive)) else "видів стоять", fish)}[loc]
        text = re.sub(r"^(Eight species are on \*\*all four\*\*|Восемь видов стоят во \*\*всех четырёх\*\*|Вісім видів стоять на \*\*всіх чотирьох\*\*"
                      r"|\d+ species are on \*\*all five\*\*|\d+ вид(а|ов) стоят во \*\*всех пяти\*\*|\d+ вид(и|ів) стоять на \*\*всіх п'яти\*\*)[^\n]*$", para, text, flags=re.M)
        text = text.replace("one of the four provinces", "one of the five provinces").replace("одна из четырёх провинций", "одна из пяти провинций").replace("одну з чотирьох провінцій", "одну з п'яти провінцій")
        text = text.replace("порізано на чотири фауністичні", "порізано на п'ять фауністичних").replace("cut into four faunal", "cut into five faunal").replace("разрезан на четыре фаунистические", "разрезан на пять фаунистических")
        wr(path, text); print("  provinces.md", loc, counts)
    print("cosmopolitan:", allfive)


if __name__ == "__main__":
    main()
