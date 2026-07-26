# -*- coding: utf-8 -*-
"""Add the species sprite to the markdown wiki's main species table, for the GitHub/VS Code view.

    python tools/gen_wiki_md_sprites.py            (English, docs/wiki/species.md)
    python tools/gen_wiki_md_sprites.py ru uk      (translated pages under docs/wiki/<lang>/)

Idempotent: it does nothing if the page already contains an <img>, so it is safe to re-run after adding
species. Only the ONE table that leads with a row number and a name — the reference tables repeat the
same 70 species, and 70 more images each would make them worse, not clearer. Sized with an <img width>
(GitHub honours it) because the source art is 256px.

Translated pages are written without the img tags, partly so the translator has 70 fewer things to get
wrong and partly because the relative path to the textures is one hop longer from a language folder.
Matching a row still requires the name in the table to equal the name in that language's lang file, so
this doubles as a check that the wiki calls each fish what the game calls it — mismatches are reported
and left alone rather than guessed at.

This is for the markdown view only. The bundled single-page wiki gets its own, richer illustration from
tools/gen_wiki_bundle.py — it does not read anything this writes.
"""
import io, json, os, re, sys

LANGFILE = {"": "en_us", "ru": "ru_ru", "uk": "uk_ua"}
LANG = "common/src/main/resources/assets/riverfishing/lang/%s.json"
TEX = "common/src/main/resources/assets/riverfishing/textures/item/fish"

# Rows of the main table: | 1 | Bream | `bream` | ...  — the name column is whatever the language calls it.
ROW = re.compile(r"^(\|\s*\d+\s*\|\s*)([^|]+?)(\s*\|\s*`([a-z_0-9]+)`)", re.M)


def illustrate(lang):
    page = os.path.join("docs/wiki", lang, "species.md")
    if not os.path.exists(page):
        print("%s: no species.md yet — skipped" % (lang or "en"))
        return
    src = io.open(page, encoding="utf-8").read()
    if "<img" in src:
        print("%s: already illustrated" % (lang or "en"))
        return

    strings = json.load(io.open(LANG % LANGFILE[lang], encoding="utf-8"))
    names = {}
    for k, v in strings.items():
        if k.startswith("fish.riverfishing."):
            names.setdefault(v, k.rsplit(".", 1)[1])

    rel = "/".join([".."] * (2 + (1 if lang else 0)) + [TEX])
    hit, miss = [0], []

    def sub(m):
        name, sid = m.group(2).strip(), m.group(4)
        if names.get(name) != sid:
            miss.append("%s (row says %r, the game says %r)"
                        % (sid, name, next((n for n, i in names.items() if i == sid), "?")))
            return m.group(0)
        hit[0] += 1
        return "%s<img src=\"%s/%s.png\" width=\"28\" alt=\"\"> %s%s" % (m.group(1), rel, sid, name, m.group(3))

    out = ROW.sub(sub, src)
    io.open(page, "w", encoding="utf-8", newline="\n").write(out)
    print("%s: illustrated %d rows in %s" % (lang or "en", hit[0], page))
    for m in miss:
        print("  name does not match the game: " + m)


if __name__ == "__main__":
    for lang in (sys.argv[1:] or [""]):
        illustrate("" if lang in ("en", "") else lang)
