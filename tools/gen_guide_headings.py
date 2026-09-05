# -*- coding: utf-8 -*-
"""§guide-headings: promote the guide pages' own CAPS leads into real section headings.

    py tools/gen_guide_headings.py [--dry]

The guide texts were already written with sections — they just had no way to say so, so a section
opened as an ALL-CAPS phrase inside the paragraph and the page rendered as one unbroken wall:

    THE AUGER. Right-click ice with it: ordinary, packed, blue or frosted, as long as ...

JournalScreen.guideProse() now reads "## " at the head of a paragraph as a heading, so this promotes
what the author already wrote instead of inventing anything:

    ## THE AUGER
    Right-click ice with it: ordinary, packed, blue or frosted, as long as ...

NOT ONE WORD CHANGES. The heading is the text that was there, moved onto its own line — which is why
this runs over every language at once and needs no translator: Russian and Ukrainian use the same
caps convention because they were translated from the same sentences.

A paragraph is promoted only when ALL of these hold, and the conjunction is what makes the pass
idempotent — run it twice and the second run finds nothing:

    it opens a section       -> it is the first paragraph, or the one before it is blank
    it is not a list item    -> it does not start with a bullet or an indent
    it opens in CAPS         -> its first two letters are both uppercase
    the head is a head       -> the text up to the first "." or ":" is at most 75 characters
    a page has SECTIONS      -> a language that yields only one heading on a page gets none

The last two are what keep prose out. "There is ONE groundbait: BASE GROUNDBAIT." opens with "Th"
and is left alone; "A DIFFERENT ONE TAKES THE SWIM OVER - the old bed does not blend into the new
one" is all caps at the front but runs 80 characters before its full stop, so it stays a sentence.

Run with --dry to see what would move without writing anything.
"""
import io, json, os, re, sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
LANG = "common/src/main/resources/assets/riverfishing/lang"
MARK = "## "
MAX_HEAD = 75

BULLETS = ("•", "-", " ", "\t", MARK)


def head_of(para):
    """The heading this paragraph would give up, or None."""
    if para.startswith(BULLETS) or not para.strip():
        return None
    m = re.search(r"[.:]", para)
    head = (para[:m.start()] if m else para).strip()
    if not head or len(head) > MAX_HEAD:
        return None
    # The CAPS test is on the head itself, not on the paragraph: "A. B" has two capitals but its
    # head is one letter long, and a one-letter heading is a sentence that lost its sentence.
    letters = [c for c in head if c.isalpha()]
    if len(letters) < 3 or not (letters[0].isupper() and letters[1].isupper()):
        return None
    rest = para[m.end():].strip() if m else ""
    return head, rest


def candidates(text):
    """Indices of the paragraphs in this text that could become headings."""
    paras = text.split("\n")
    out = set()
    for i, para in enumerate(paras):
        # Never the first paragraph: a page opens under its own title, and the first line of
        # prose is that page, not a section of it.
        if i > 0 and paras[i - 1].strip() == "" and head_of(para) is not None:
            out.add(i)
    return out


def promote(text, only=None):
    """The text with its section openers lifted onto their own lines, and how many moved.

    {@code only} restricts the move to those paragraph indices — that is how the languages stay in
    step (see {@link #main}). Left out, every candidate moves, which is what the self-test uses.
    """
    paras = text.split("\n")
    pick = candidates(text) if only is None else (candidates(text) & only)
    # One heading is not structure, it is a page that looks like it lost the rest.
    if len(pick) < 2:
        return text, 0
    out = []
    for i, para in enumerate(paras):
        if i not in pick:
            out.append(para)
            continue
        head, rest = head_of(para)
        out.append(MARK + head)
        if rest:
            out.append(rest)
    return "\n".join(out), len(pick)


def main():
    """Promote, in every language at once and only where every language agrees.

    The languages have to move TOGETHER. check_lang.py holds every translation to the English line
    count, so a heading that appears in one language and not another is a hard error there — and it
    happens for real: "ПКМ" is an acronym, so it is always caps and always looks like a section
    opener, where the English "Right-click" does not. Intersecting the candidates is what keeps a
    page the same shape in all three, and it costs only the headings one language could not match.
    """
    dry = "--dry" in sys.argv
    langs, missing = {}, []
    for lang in ("en_us", "ru_ru", "uk_ua"):
        path = os.path.join(ROOT, LANG, lang + ".json")
        if not os.path.isfile(path):
            missing.append(lang)
            continue
        langs[lang] = (path, json.loads(io.open(path, encoding="utf-8").read()))
    for lang in missing:
        print("  %-6s absent" % lang)

    keys = sorted(k for k in langs["en_us"][1]
                  if k.startswith("guide.riverfishing.") and k.endswith(".text"))
    total, touched = 0, {lang: 0 for lang in langs}
    for key in keys:
        shared = None
        for _, d in langs.values():
            if key not in d:
                shared = set()
                break
            c = candidates(d[key])
            shared = c if shared is None else (shared & c)
        if not shared or len(shared) < 2:
            continue
        guide = key[len("guide.riverfishing."):-len(".text")]
        print("  %-14s %d heading(s)" % (guide, len(shared)))
        for lang, (_, d) in langs.items():
            new, moved = promote(d[key], shared)
            d[key] = new
            touched[lang] += moved
            if lang == "en_us":
                for line in new.split("\n"):
                    if line.startswith(MARK):
                        print("      %s" % line)
        total += len(shared)

    if total and not dry:
        for path, d in langs.values():
            # The lang files are hand-edited: keep them readable and diffable, not minified.
            io.open(path, "w", encoding="utf-8", newline="\n").write(
                json.dumps(d, ensure_ascii=False, indent=2) + "\n")

    counts = ", ".join("%s %d" % (l, n) for l, n in sorted(touched.items()))
    print("\n%d heading(s) per language (%s) %s"
          % (total, counts, "— dry run, nothing written" if dry else "promoted"))
    assert len(set(touched.values())) == 1, "the languages went out of step: " + counts
    return 0


def selftest():
    """Every rule with the case it exists to reject, on a page shaped like a real one."""
    LEAD = "The page opens here."
    TWO = ("THE NOD. It twitches.", "THE AUGER. It cuts.")

    def page(*paras):
        return "\n\n".join((LEAD,) + paras)

    def moved(*paras):
        return promote(page(*paras))[1]

    out, n = promote(page(*TWO))
    lines = out.split("\n")
    assert n == 2 and lines[2] == "## THE NOD" and lines[3] == "It twitches.", out

    # each of these adds a paragraph that must NOT become a heading, so the count stays at two
    assert moved("There is ONE groundbait: BASE GROUNDBAIT. Wheat makes two.", *TWO) == 2, "prose"
    assert moved("• ИТОГ, кто придёт.", *TWO) == 2, "bullet"
    assert moved("A DIFFERENT ONE TAKES THE SWIM OVER, and the old bed does not blend into "
                 "the new one at all.", *TWO) == 2, "long sentence"
    assert moved("AN. Ok", *TWO) == 2, "one-letter head"
    assert moved("THE LEAD. Buried inside a paragraph, THE NOD is not a section.") == 0, "lone heading"

    assert moved("MAKING A MIX", *TWO) == 3, "bare heading line missed"
    assert promote(promote(page(*TWO))[0])[1] == 0, "not idempotent"
    print("self-test ok: promotes caps leads and bare heading lines; refuses prose, bullets, long "
          "sentences, one-letter heads and a page with only one; does nothing on a second run")
    return 0


if __name__ == "__main__":
    sys.exit(selftest() if "--selftest" in sys.argv else main())
