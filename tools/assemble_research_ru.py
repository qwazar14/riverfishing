# -*- coding: utf-8 -*-
"""Assemble the Russian research reference from the per-section translations, then check it is whole.

    python tools/assemble_research_ru.py <ru_sections_dir> [out.md]

Eight agents translated one section each into <dir>/section_1.md … section_8.md. This stitches them back
into one document with the original front matter and source list, and refuses to write anything if the
idea count does not match the English original — a translation that silently drops entries is worse than
no translation, because nothing downstream would notice.

The source list is NOT translated: it is 328 URLs and page titles, and a translated URL is a broken one.
"""
import io, os, re, sys

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
EN = os.path.join(REPO, "docs/RESEARCH_0.7.0.md")
SECTIONS = 8


def main():
    if len(sys.argv) < 2:
        sys.exit("usage: assemble_research_ru.py <ru_sections_dir> [out.md]")
    src = sys.argv[1].rstrip("/")
    out = sys.argv[2] if len(sys.argv) > 2 else os.path.join(REPO, "docs/RESEARCH_0.7.0.ru.md")

    en = io.open(EN, encoding="utf-8").read()
    en_ideas = en.count("\n### ")

    # Front matter: everything up to the first "---" that precedes a "## " section. Already Russian.
    head_end = en.find("\n## Оглавление")
    if head_end < 0:
        sys.exit("English doc has no Оглавление — layout changed")
    front = en[:head_end].rstrip()

    # Source list: keep verbatim.
    si = en.find("## Источники, которые агенты действительно прочитали")
    if si < 0:
        sys.exit("English doc has no source list")
    sources = en[si:].rstrip()

    parts, counts = [], []
    for n in range(1, SECTIONS + 1):
        p = os.path.join(src, "section_%d.md" % n)
        if not os.path.exists(p):
            sys.exit("missing %s — that section's agent did not write its file" % p)
        t = io.open(p, encoding="utf-8").read().strip()
        if not t.startswith("## "):
            sys.exit("%s does not start with a '## ' heading" % p)
        c = t.count("\n### ") + (1 if t.startswith("### ") else 0)
        counts.append(c)
        parts.append(t)

    total = sum(counts)
    print("идей: английский %d, перевод %d" % (en_ideas, total))
    for n, c in enumerate(counts, 1):
        print("   раздел %d: %d" % (n, c))
    if total != en_ideas:
        sys.exit("НЕ СОВПАЛО: %d против %d — ничего не записано" % (total, en_ideas))

    # Rebuild the contents list from the translated headings, so it can never drift from them.
    toc = ["## Оглавление", ""]
    for t in parts:
        title = t.split("\n", 1)[0][3:].strip()
        n = t.count("\n### ") + (1 if t.startswith("### ") else 0)
        anchor = re.sub(r"[^a-zа-яёєіїґ0-9]+", "-", title.lower())
        toc.append("- [%s](#%s) — %d" % (title, anchor, n))
    toc.append("")

    doc = "\n".join([front, "", "---", ""] + toc + ["---", ""]
                    + sum([[p, "", "---", ""] for p in parts], [])
                    + [sources, ""])
    io.open(out, "w", encoding="utf-8", newline="\n").write(doc)
    print("-> %s  (%d строк, %d символов)" % (out, doc.count("\n") + 1, len(doc)))

    # A translated document that still reads as English in the body is a failed translation.
    body = "\n".join(parts)
    lat = len(re.findall(r"[A-Za-z]", body))
    cyr = len(re.findall(r"[А-Яа-яЁёЄєІіЇїҐґ]", body))
    print("   кириллица %d, латиница %d (латиница ожидаема: имена игр, ключи, URL)" % (cyr, lat))
    if cyr < lat:
        print("   ВНИМАНИЕ: латиницы больше — проверьте, действительно ли переведено")
    return 0


if __name__ == "__main__":
    sys.exit(main())
