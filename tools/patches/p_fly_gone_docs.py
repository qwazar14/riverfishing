# -*- coding: utf-8 -*-
"""The fly's paper trail: the wiki page in three languages, the design notes, the rows in the quest and
achievement tables, the recipe row, the cross-references, and the 0.10.0 patch notes (unreleased, so the
fly sections simply go — nothing shipped that anyone has to be told about).

Idempotent; run on each tree.  py -X utf8 tools/patches/p_fly_gone_docs.py [tree ...]"""
import io, os, re, sys

MAIN = r"C:/Users/Qwazar/VS Code Projects/fishing mod"
TREES = [MAIN, r"C:/Users/Qwazar/wt/rf1201", r"C:/Users/Qwazar/wt/rf26"]


def rd(p): return io.open(p, encoding="utf-8").read()
def wr(p, s): io.open(p, "w", encoding="utf-8", newline="\n").write(s)


def swap(path, old, new, what=""):
    if not os.path.exists(path):
        return
    s = rd(path)
    if old not in s:
        return
    wr(path, s.replace(old, new))


def drop_lines(path, needles):
    """Delete every line that contains any of these — for table rows."""
    if not os.path.exists(path):
        return
    keep = [ln for ln in rd(path).split("\n") if not any(n in ln for n in needles)]
    wr(path, "\n".join(keep))


def cut_section(path, head, stop="\n## "):
    """Delete a '### ...' section up to the next section or heading."""
    if not os.path.exists(path):
        return
    s = rd(path)
    if head not in s:
        return
    i = s.index(head)
    k = s.find("\n### ", i + 1)
    j = s.find(stop, i + 1)
    ends = [x for x in (k, j) if x >= 0]
    wr(path, s[:i] + (s[min(ends) + 1:] if ends else ""))


# the fly's own pages, and the design notes that only ever described it
GONE = ["docs/wiki/fly-fishing.md", "docs/wiki/ru/fly-fishing.md", "docs/wiki/uk/fly-fishing.md",
        "docs/design/fly-fishing.md", "docs/design/fly-api.md"]

# README index rows, quest rows, achievement rows, the recipe row: matched by their own words
ROWS = {
    "docs/wiki/README.md": ["[Fly fishing](fly-fishing.md)"],
    "docs/wiki/ru/README.md": ["[\u041d\u0430\u0445\u043b\u044b\u0441\u0442](fly-fishing.md)"],
    "docs/wiki/uk/README.md": ["[\u041d\u0430\u0445\u043b\u0438\u0441\u0442](fly-fishing.md)"],
    "docs/wiki/crafting.md": ["| Fly Rod |"],
    "docs/wiki/ru/crafting.md": ["| \u041d\u0430\u0445\u043b\u044b\u0441\u0442\u043e\u0432\u043e\u0435 \u0443\u0434\u0438\u043b\u0438\u0449\u0435 |"],
    "docs/wiki/uk/crafting.md": ["| \u041d\u0430\u0445\u043b\u0438\u0441\u0442\u043e\u0432\u0435 \u0432\u0443\u0434\u0438\u043b\u0438\u0449\u0435 |"],
    "docs/wiki/progression.md": ["| Land a fish on the fly rod |", "| Land 10 fish on the fly |",
                                 "| Land 30 fish on the fly |", "| **On the Fly** |", "| **Tight Loop** |",
                                 "| **Tight Loop** *(hidden challenge)* |", "| **Dry Fly Hand** |"],
    "docs/wiki/ru/progression.md": ["| \u041f\u043e\u0439\u043c\u0430\u0439 \u0440\u044b\u0431\u0443 \u043d\u0430\u0445\u043b\u044b\u0441\u0442\u043e\u043c |",
                                    "| \u041f\u043e\u0439\u043c\u0430\u0439 10 \u0440\u044b\u0431 \u043d\u0430\u0445\u043b\u044b\u0441\u0442\u043e\u043c |",
                                    "| \u041f\u043e\u0439\u043c\u0430\u0439 30 \u0440\u044b\u0431 \u043d\u0430\u0445\u043b\u044b\u0441\u0442\u043e\u043c |",
                                    "| **\u041d\u0430\u0445\u043b\u044b\u0441\u0442\u043e\u043c** |",
                                    "| **\u0422\u0443\u0433\u0430\u044f \u043f\u0435\u0442\u043b\u044f**",
                                    "| **\u0420\u0443\u043a\u0430 \u043d\u0430\u0445\u043b\u044b\u0441\u0442\u043e\u0432\u0438\u043a\u0430** |"],
    "docs/wiki/uk/progression.md": ["| \u0417\u043b\u043e\u0432\u0438 \u0440\u0438\u0431\u0443 \u043d\u0430\u0445\u043b\u0438\u0441\u0442\u043e\u043c |",
                                    "| \u0417\u043b\u043e\u0432\u0438 10 \u0440\u0438\u0431 \u043d\u0430\u0445\u043b\u0438\u0441\u0442\u043e\u043c |",
                                    "| \u0417\u043b\u043e\u0432\u0438 30 \u0440\u0438\u0431 \u043d\u0430\u0445\u043b\u0438\u0441\u0442\u043e\u043c |",
                                    "| **\u041d\u0430\u0445\u043b\u0438\u0441\u0442\u043e\u043c** |",
                                    "| **\u0422\u0443\u0433\u0430 \u043f\u0435\u0442\u043b\u044f**",
                                    "| **\u0420\u0443\u043a\u0430 \u043d\u0430\u0445\u043b\u0438\u0441\u0442\u043e\u0432\u0438\u043a\u0430** |"],
}

# the prose that named the fly rod: the stage-7 heading and its lead, and the ice page's cross-reference
PROSE = [
    ("docs/wiki/progression.md",
     "### Stage 7 \u2014 Cold water and the fly\n\nThe [fly rod](fly-fishing.md) and the salmonids, wherever the water is cold enough for them.",
     "### Stage 7 \u2014 Cold water\n\nThe salmonids, wherever the water is cold enough for them."),
    ("docs/wiki/ru/progression.md",
     "### \u0421\u0442\u0430\u0434\u0438\u044f 7 \u2014 \u0425\u043e\u043b\u043e\u0434\u043d\u0430\u044f \u0432\u043e\u0434\u0430 \u0438 \u043d\u0430\u0445\u043b\u044b\u0441\u0442\n\n[\u041d\u0430\u0445\u043b\u044b\u0441\u0442\u043e\u0432\u043e\u0435 \u0443\u0434\u0438\u043b\u0438\u0449\u0435](fly-fishing.md) \u0438 \u043b\u043e\u0441\u043e\u0441\u0451\u0432\u044b\u0435 \u2014 \u0432\u0435\u0437\u0434\u0435,",
     "### \u0421\u0442\u0430\u0434\u0438\u044f 7 \u2014 \u0425\u043e\u043b\u043e\u0434\u043d\u0430\u044f \u0432\u043e\u0434\u0430\n\n\u041b\u043e\u0441\u043e\u0441\u0451\u0432\u044b\u0435 \u2014 \u0432\u0435\u0437\u0434\u0435,"),
    ("docs/wiki/uk/progression.md",
     "### \u0415\u0442\u0430\u043f 7 \u2014 \u0425\u043e\u043b\u043e\u0434\u043d\u0430 \u0432\u043e\u0434\u0430 \u0456 \u043d\u0430\u0445\u043b\u0438\u0441\u0442\n\n[\u041d\u0430\u0445\u043b\u0438\u0441\u0442\u043e\u0432\u0435 \u0432\u0443\u0434\u0438\u043b\u0438\u0449\u0435](fly-fishing.md) \u0442\u0430 \u043b\u043e\u0441\u043e\u0441\u0435\u0432\u0456 \u2014 \u0441\u043a\u0440\u0456\u0437\u044c,",
     "### \u0415\u0442\u0430\u043f 7 \u2014 \u0425\u043e\u043b\u043e\u0434\u043d\u0430 \u0432\u043e\u0434\u0430\n\n\u041b\u043e\u0441\u043e\u0441\u0435\u0432\u0456 \u2014 \u0441\u043a\u0440\u0456\u0437\u044c,"),
    ("docs/wiki/ice-fishing.md",
     "**hold right-click and the rod jigs on its own** \u2014 the same rule as the [fly cast](fly-fishing.md#the-cast). A needle",
     "**hold right-click and the rod jigs on its own**. A needle"),
    ("docs/wiki/ru/ice-fishing.md",
     "**\u0434\u0435\u0440\u0436\u0438\u0442\u0435 \u041f\u041a\u041c \u2014 \u0438 \u0443\u0434\u043e\u0447\u043a\u0430 \u0438\u0433\u0440\u0430\u0435\u0442 \u043c\u043e\u0440\u043c\u044b\u0448\u043a\u043e\u0439 \u0441\u0430\u043c\u0430**, \u043f\u043e \u0442\u043e\u043c\u0443 \u0436\u0435 \u043f\u0440\u0430\u0432\u0438\u043b\u0443, \u0447\u0442\u043e \u0438 [\u043d\u0430\u0445\u043b\u044b\u0441\u0442\u043e\u0432\u044b\u0439 \u0437\u0430\u0431\u0440\u043e\u0441](fly-fishing.md#\u0437\u0430\u0431\u0440\u043e\u0441). \u0412\u043d\u0438\u0437\u0443",
     "**\u0434\u0435\u0440\u0436\u0438\u0442\u0435 \u041f\u041a\u041c \u2014 \u0438 \u0443\u0434\u043e\u0447\u043a\u0430 \u0438\u0433\u0440\u0430\u0435\u0442 \u043c\u043e\u0440\u043c\u044b\u0448\u043a\u043e\u0439 \u0441\u0430\u043c\u0430**. \u0412\u043d\u0438\u0437\u0443"),
    ("docs/wiki/uk/ice-fishing.md",
     "**\u0442\u0440\u0438\u043c\u0430\u0439\u0442\u0435 \u041f\u041a\u041c \u2014 \u0456 \u0432\u0443\u0434\u043a\u0430 \u0433\u0440\u0430\u0454 \u043c\u043e\u0440\u043c\u0438\u0448\u043a\u043e\u044e \u0441\u0430\u043c\u0430**, \u0437\u0430 \u0442\u0438\u043c \u0441\u0430\u043c\u0438\u043c \u043f\u0440\u0430\u0432\u0438\u043b\u043e\u043c, \u0449\u043e \u0439 [\u043d\u0430\u0445\u043b\u0438\u0441\u0442\u043e\u0432\u0438\u0439 \u0437\u0430\u043a\u0438\u0434](fly-fishing.md#\u0437\u0430\u043a\u0438\u0434). \u0423\u043d\u0438\u0437\u0443",
     "**\u0442\u0440\u0438\u043c\u0430\u0439\u0442\u0435 \u041f\u041a\u041c \u2014 \u0456 \u0432\u0443\u0434\u043a\u0430 \u0433\u0440\u0430\u0454 \u043c\u043e\u0440\u043c\u0438\u0448\u043a\u043e\u044e \u0441\u0430\u043c\u0430**. \u0423\u043d\u0438\u0437\u0443"),
    ("docs/wiki/ice-fishing.md",
     "opens [stage 7, cold water and the fly](progression.md#the-quest-chain)",
     "opens [stage 7, cold water](progression.md#the-quest-chain)"),
    ("docs/wiki/ru/ice-fishing.md",
     "\u043e\u0442\u043a\u0440\u044b\u0432\u0430\u0435\u0442 [\u0441\u0442\u0430\u0434\u0438\u044e 7 \u2014 \u0425\u043e\u043b\u043e\u0434\u043d\u0443\u044e \u0432\u043e\u0434\u0443 \u0438 \u043d\u0430\u0445\u043b\u044b\u0441\u0442](progression.md#\u0446\u0435\u043f\u043e\u0447\u043a\u0430-\u0437\u0430\u0434\u0430\u043d\u0438\u0439)",
     "\u043e\u0442\u043a\u0440\u044b\u0432\u0430\u0435\u0442 [\u0441\u0442\u0430\u0434\u0438\u044e 7 \u2014 \u0425\u043e\u043b\u043e\u0434\u043d\u0443\u044e \u0432\u043e\u0434\u0443](progression.md#\u0446\u0435\u043f\u043e\u0447\u043a\u0430-\u0437\u0430\u0434\u0430\u043d\u0438\u0439)"),
    ("docs/wiki/uk/ice-fishing.md",
     "\u0432\u0456\u0434\u043a\u0440\u0438\u0432\u0430\u044e\u0442\u044c [\u0435\u0442\u0430\u043f 7 \u2014 \u0425\u043e\u043b\u043e\u0434\u043d\u0430 \u0432\u043e\u0434\u0430 \u0456 \u043d\u0430\u0445\u043b\u0438\u0441\u0442](progression.md#\u043b\u0430\u043d\u0446\u044e\u0436\u043e\u043a-\u0437\u0430\u0432\u0434\u0430\u043d\u044c)",
     "\u0432\u0456\u0434\u043a\u0440\u0438\u0432\u0430\u044e\u0442\u044c [\u0435\u0442\u0430\u043f 7 \u2014 \u0425\u043e\u043b\u043e\u0434\u043d\u0430 \u0432\u043e\u0434\u0430](progression.md#\u043b\u0430\u043d\u0446\u044e\u0436\u043e\u043a-\u0437\u0430\u0432\u0434\u0430\u043d\u044c)"),
]

# the 0.10.0 notes: two fly sections, and the three sentences that counted the fly in
NOTES = [
    # the fly was the only thing under this heading, and the diet line named it
    ("---\n\n## Tackle & Fishing\n\n## Species\n", "---\n\n## Species\n"),
    ("The journal shows it; the fly a species takes is chosen by it.",
     "The journal shows it; the lure and the bait a species takes are chosen by it."),
    ("Stages 1\u20137 now ask for what the water anywhere holds: a peaceful feeder, an omnivore, a predator, a family, a kilo, five kilos, a salmonid, a fish on the fly. The soil reward is gone. Stage 7 is cold water and the fly rod.",
     "Stages 1\u20137 now ask for what the water anywhere holds: a peaceful feeder, an omnivore, a predator, a family, a kilo, five kilos, a salmonid. The soil reward is gone. Stage 7 is cold water."),
    ("Twelve new achievements: On the Fly, Tight Loop, Tied by Hand,", "Ten new achievements: Tied by Hand,"),
    ("Fourteen more achievements: Night Owl, Storm Rider, Four Seasons, A Table for Everyone, Seven Families, Every Family, The Table Was Laid, Ten Trophies, A Wall of Trophies, A Thousand Fish, Dry Fly Hand, Go Home, Salt, The Deep.",
     "Thirteen more achievements: Night Owl, Storm Rider, Four Seasons, A Table for Everyone, Seven Families, Every Family, The Table Was Laid, Ten Trophies, A Wall of Trophies, A Thousand Fish, Go Home, Salt, The Deep."),
]


def run(tree):
    j = lambda rel: os.path.join(tree, rel)
    for rel in GONE:
        p = j(rel)
        if os.path.exists(p):
            os.remove(p)
    for rel, needles in ROWS.items():
        drop_lines(j(rel), needles)
    for rel, old, new in PROSE:
        swap(j(rel), old, new)
    notes = j("docs/patchnotes/0.10.0.md")
    if os.path.exists(notes):
        cut_section(notes, "### Fly fishing\n")
        cut_section(notes, "### Fly fishing, rebuilt to the spec\n")
        for old, new in NOTES:
            swap(notes, old, new)
        s = rd(notes).rstrip() + "\n"
        wr(notes, re.sub(r"\n{3,}", "\n\n", s))
    # the page must not be listed for publication either
    bundle = j("tools/gen_wiki_bundle.py")
    swap(bundle, '"ice-fishing", "fly-fishing",   # \u00a7fly', '"ice-fishing",')
    swap(bundle, '"ice-fishing", "fly-fishing",', '"ice-fishing",')
    print("  docs:", os.path.basename(tree.rstrip("/\\")))


for t in (sys.argv[1:] or TREES):
    run(t)
