# -*- coding: utf-8 -*-
"""§hybrid-rare: a hybrid is a fish of the breeding tank, not of the river. The thirty hybrids the table
brought came with bases up to 1.34 — a bluegill cross took more often than a bluegill. In wild water a
hybrid now weighs one twenty-fifth of what its profile says (the shoal, the rises and the finder all read
the same score); where it has been stocked and settled it fishes like any other fish. The catch card says
whose cross it is. Idempotent; run on each tree.  py tools/patches/p_hybrid_rare.py [tree ...]"""
import io, json, os, sys
from collections import OrderedDict

TREES = [r"C:/Users/Qwazar/VS Code Projects/fishing mod", r"C:/Users/Qwazar/wt/rf1201", r"C:/Users/Qwazar/wt/rf26"]
J = "common/src/main/java/com/riverfishing/"
LANG = "common/src/main/resources/assets/riverfishing/lang"
CARD = {"en_us": "Hybrid of:", "ru_ru": "Гибрид:", "uk_ua": "Гібрид:"}
WIKI = {
    "docs/wiki/breeding.md": "\n**Hybrids are the tank's fish.** Thirty species are crosses — bester, tiger trout, the sunfish and crappie hybrids, bream × roach, the tilapia hybrids and more — and a pair of the two parents in the tank gives the hybrid's roe. In wild water a hybrid is one fish in twenty-five of what its profile would otherwise be, so you will all but never take one blind; stocked and settled, it fishes like anything else. The catch card names the cross.\n",
    "docs/wiki/ru/breeding.md": "\n**Гибриды — рыба аквариума.** Тридцать видов — помеси: бестер, тигровая форель, гибриды солнечников и краппи, лещ × плотва, гибриды тиляпий и другие, — и пара из двух родителей в аквариуме даёт икру гибрида. В дикой воде гибрид встречается в двадцать пять раз реже, чем говорит его профиль, так что вслепую его почти не поймать; зарыбленный и прижившийся, он ловится как любая другая рыба. Карточка улова называет, чья это помесь.\n",
    "docs/wiki/uk/breeding.md": "\n**Гібриди — риба акваріума.** Тридцять видів — помісі: бестер, тигрова форель, гібриди сонячників і крапі, лящ × плітка, гібриди тиляпій та інші, — і пара з двох батьків в акваріумі дає ікру гібрида. У дикій воді гібрид трапляється у двадцять п'ять разів рідше, ніж каже його профіль, тож наосліп його майже не зловити; зарибнений і прижитий, він ловиться як будь-яка інша риба. Картка улову називає, чия це помісь.\n",
}


def rd(p): return io.open(p, encoding="utf-8").read()
def wr(p, s): io.open(p, "w", encoding="utf-8", newline="\n").write(s)


def sub(path, old, new, what):
    s = rd(path)
    if new in s: return
    assert s.count(old) == 1, "%s @ %s (%d)" % (what, path, s.count(old))
    wr(path, s.replace(old, new, 1))


def run(tree):
    name = os.path.basename(tree.rstrip("/\\"))
    j = lambda *a: os.path.join(tree, *a)
    sub(j(J, "engine/BiteEngine.java"),
        "        double natural = naturalScore(p, c);\n        // §stocked-survival (0.5.1)",
        "        double natural = naturalScore(p, c);\n"
        "        // §hybrid-rare: a hybrid is a fish of the breeding tank, not of the river — wild water holds it one\n"
        "        // time in twenty-five; stocked and settled it fishes like anything else (the presence rule below)\n"
        "        double presence0 = c.stockedPresence != null ? c.stockedPresence.applyAsDouble(p.id) : 0.0;\n"
        "        if (!p.hybridOf.isEmpty() && presence0 <= 0) natural *= HYBRID_WILD;\n"
        "        // §stocked-survival (0.5.1)", "environmentScore")
    sub(j(J, "engine/BiteEngine.java"), "    private static final double HOOK_GATE = 0.34;",
        "    /** §hybrid-rare: a hybrid's share of its own weight in wild water. */\n    private static final double HYBRID_WILD = 0.04;\n    private static final double HOOK_GATE = 0.34;", "constant")
    sub(j(J, "fish/CatchCard.java"), '        c.putString("Life", p == null ? "" : p.depthPref);\n',
        '        c.putString("Life", p == null ? "" : p.depthPref);\n        c.putString("Hybrid", p == null ? "" : String.join(",", p.hybridOf));   // §hybrid-rare: whose cross it is\n', "card")
    tt = j(J, "client/FishCardClientTooltip.java")
    getter = 'c.getStringOr("Hybrid", "")' if name == "rf26" else 'c.getString("Hybrid")'
    life = 'if (!c.getStringOr("Life", "").isEmpty()) row("lifestyle", key("life." + c.getStringOr("Life", "")), BLUE);\n' if name == "rf26" \
        else 'if (!c.getString("Life").isEmpty()) row("lifestyle", key("life." + c.getString("Life")), BLUE);\n'
    sub(tt, "        " + life,
        "        " + life +
        "        if (!%s.isEmpty()) {   // §hybrid-rare: the cross, by its parents' names\n"
        "            StringBuilder parents = new StringBuilder();\n"
        "            for (String id : %s.split(\",\")) {\n"
        "                if (parents.length() > 0) parents.append(\" × \");\n"
        "                parents.append(Component.translatable(\"fish.riverfishing.\" + id).getString());\n"
        "            }\n"
        "            row(\"hybrid\", Component.literal(parents.toString()), GOLD);\n"
        "        }\n" % (getter, getter), "tooltip")
    for code, text in CARD.items():
        p = j(LANG, code + ".json"); d = json.loads(rd(p), object_pairs_hook=OrderedDict)
        if d.get("card.riverfishing.hybrid") == text: continue
        out = OrderedDict()
        for k, v in d.items():
            out[k] = v
            if k == "card.riverfishing.lifestyle": out["card.riverfishing.hybrid"] = text
        wr(p, json.dumps(out, ensure_ascii=False, indent=2) + "\n")
    for rel, para in WIKI.items():
        p = j(rel); s = rd(p)
        if para.strip()[:30] in s: continue
        lines = s.split("\n")
        i = next(k for k, l in enumerate(lines) if l.startswith("## ") and k > 140)   # the section after the crossing one
        lines.insert(i, para.strip("\n") + "\n")
        wr(p, "\n".join(lines))
    p = j("docs/patchnotes/0.10.0.md"); s = rd(p)
    line = "- **Hybrids are the tank's fish.** In wild water a hybrid weighs one twenty-fifth of its profile — the sunfish crosses stop out-biting the sunfish; stocked and settled it fishes like anything else. The catch card names the cross.\n"
    if "Hybrids are the tank's fish" not in s:
        s = s.replace("- **Tooltips.**", line + "- **Tooltips.**", 1); wr(p, s)
    print("  patched", name)


for t in (sys.argv[1:] or TREES): run(t)
