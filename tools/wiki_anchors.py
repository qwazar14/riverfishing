# -*- coding: utf-8 -*-
"""Retarget the anchor links in a translated wiki, and check the translation's structure.

    python tools/wiki_anchors.py ru
    python tools/wiki_anchors.py ru --check      (report only, write nothing)

Translators leave `](rods.md#loading-the-blank-the-test-window)` exactly as it is in English, because a
translator working on one page cannot know what the translator of another page called its headings. The
anchors are therefore all wrong the moment the headings are translated. This fixes them.

The trick is that a heading's anchor is derived from its text, and the translated pages are required to
keep the same headings in the same order — so pairing them positionally gives an EN-slug → translated-
slug map without anyone having to coordinate. That requirement is also what makes this checkable: if a
page's heading count differs from its English source the pairing is meaningless, and this refuses to
touch that language until it is fixed.

Slugs follow GitHub's rule (lowercase, drop punctuation, spaces to hyphens, `-1` on collisions), which
is also what tools/gen_wiki_bundle.py uses, so one fix serves both the GitHub view and the bundle.
"""
import io, os, re, sys

WIKI = "docs/wiki"
HEADING = re.compile(r"^(#{1,6})\s+(.*?)\s*#*\s*$", re.M)
FENCE = re.compile(r"^\s*(```|~~~)")
# ](target#anchor) or ](#anchor) — the target, when present, is a sibling page.
LINK = re.compile(r"\]\((?P<page>[A-Za-z0-9._/-]*\.md)?#(?P<anchor>[^)\s]+)\)")


def slugify(text):
    """GitHub's heading → anchor rule, applied to the heading's *rendered* text."""
    t = text
    t = re.sub(r"`([^`]*)`", r"\1", t)                     # code spans
    t = re.sub(r"!?\[([^\]]*)\]\([^)]*\)", r"\1", t)       # links and images keep their label
    t = re.sub(r"<[^>]+>", "", t)                          # stray inline html
    t = re.sub(r"[*_~]", "", t)                            # bold / italic / strike markers
    t = t.lower()
    t = "".join(c for c in t if c.isalnum() or c in " -_")  # unicode-aware: keeps Cyrillic
    return t.strip().replace(" ", "-")


def headings(path):
    """(level, text, slug) per heading, skipping fenced blocks so a `# comment` isn't mistaken for one."""
    out, seen, fence = [], {}, None
    for line in io.open(path, encoding="utf-8").read().splitlines():
        f = FENCE.match(line)
        if f:
            marker = f.group(1)
            if fence is None:
                fence = marker
            elif line.strip().startswith(fence):
                fence = None
            continue
        if fence is not None:
            continue
        m = HEADING.match(line)
        if not m:
            continue
        base = slugify(m.group(2))
        n = seen.get(base, 0)
        seen[base] = n + 1
        out.append((len(m.group(1)), m.group(2), base if n == 0 else "%s-%d" % (base, n)))
    return out


def pages(lang):
    src = sorted(f for f in os.listdir(WIKI) if f.endswith(".md"))
    return src, os.path.join(WIKI, lang)


def main(lang, check_only):
    src, outdir = pages(lang)
    missing = [f for f in src if not os.path.exists(os.path.join(outdir, f))]
    if missing:
        print("%s is incomplete — %d of %d pages not translated: %s"
              % (lang, len(missing), len(src), ", ".join(missing)))
        return 1

    # Pair headings per page. A count mismatch makes every anchor on that page a guess, so stop.
    amap, bad = {}, []
    for f in src:
        en, tr = headings(os.path.join(WIKI, f)), headings(os.path.join(outdir, f))
        if len(en) != len(tr):
            bad.append("%s: %d headings in English, %d in %s" % (f, len(en), len(tr), lang))
            continue
        for (le, _, se), (lt, _, st) in zip(en, tr):
            if le != lt:
                bad.append("%s: heading level %d became %d (%s)" % (f, le, lt, se))
            amap[(f, se)] = st
    if bad:
        print("structure does not match the English — not touching %s:" % lang)
        for b in bad:
            print("  " + b)
        return 1

    # What the translated pages actually offer, so a re-run recognises its own output as done.
    done = {}
    for (page, _), tr in amap.items():
        done.setdefault(page, set()).add(tr)

    rewrites = unresolved = 0
    for f in src:
        p = os.path.join(outdir, f)
        text = io.open(p, encoding="utf-8").read()
        stats = [0, 0]

        def fix(m):
            page, anchor = os.path.basename(m.group("page") or f), m.group("anchor")
            if (page, anchor) in amap:
                tgt = amap[(page, anchor)]
                if tgt == anchor:
                    return m.group(0)
                stats[0] += 1
                return "](%s#%s)" % (m.group("page") or "", tgt)
            if anchor in done.get(page, ()):
                return m.group(0)                     # already pointing at a translated heading
            stats[1] += 1
            print("  %s: unresolved anchor #%s -> %s" % (f, anchor, page))
            return m.group(0)

        out = LINK.sub(fix, text)
        # One folder deeper than the English pages, so links that already left docs/wiki need one more hop.
        out = out.replace("](../FISH_PROFILES.md)", "](../../FISH_PROFILES.md)")
        rewrites += stats[0]
        unresolved += stats[1]
        if out != text and not check_only:
            io.open(p, "w", encoding="utf-8", newline="\n").write(out)

    # Sibling page links must resolve inside the language folder.
    for f in src:
        text = io.open(os.path.join(outdir, f), encoding="utf-8").read()
        for tgt in set(re.findall(r"\]\(([A-Za-z0-9._-]+\.md)[#)]", text)):
            if not os.path.exists(os.path.join(outdir, tgt)):
                print("  %s: links to %s, which is not in %s/" % (f, tgt, lang))
                unresolved += 1

    print("%s: %d anchor links %s, %d unresolved"
          % (lang, rewrites, "would be rewritten" if check_only else "rewritten", unresolved))
    return 1 if unresolved else 0


def self_test():
    """Every anchor link in the English wiki must resolve — if not, slugify() is wrong, not the wiki."""
    src, _ = pages("")
    hs = {f: {s for _, _, s in headings(os.path.join(WIKI, f))} for f in src}
    bad = 0
    for f in src:
        text = io.open(os.path.join(WIKI, f), encoding="utf-8").read()
        for m in LINK.finditer(text):
            page = os.path.basename(m.group("page") or f)
            if m.group("anchor") not in hs.get(page, ()):
                print("  %s: #%s not found in %s" % (f, m.group("anchor"), page))
                bad += 1
    print("self-test: %d headings, %d broken anchors in the English wiki"
          % (sum(len(v) for v in hs.values()), bad))
    return 1 if bad else 0


if __name__ == "__main__":
    args = sys.argv[1:]
    if not args or args[0] == "--self-test":
        sys.exit(self_test())
    sys.exit(main(args[0], "--check" in args))
