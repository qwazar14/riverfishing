# -*- coding: utf-8 -*-
"""§guide-percent: a guide page is run through String.format, so a bare % erases the whole page.

    py -X utf8 tools/check_guide_format.py [root]

The guide shelf is the one place in this mod where a stray percent sign is fatal. Every other string
goes out as {@code Component.translatable}, whose formatter leaves an unknown specifier alone — which is
what tools/check_lang.py documents, correctly, and why it deliberately does not hunt for stray percents.
But JournalScreen renders a page with {@code I18n.get(key)}:

    JournalScreen.java  guideProse : I18n.get("guide.riverfishing." + id + ".text")
                        guideTable : I18n.get("guide.riverfishing." + id + ".table")

and {@code I18n.get} is {@code String.format} wrapped in a catch that returns {@code "Format error: " +
pattern}. So one bare % does not corrupt a number — it replaces the ENTIRE PAGE with the words "Format
error:" and the raw text, in that language only, silently, with nothing in any log.

That shipped: `community`, `market`, `feeding` and `stress.table` were unreadable in all three languages
at once, and nobody noticed because the pages still had text in them, just the wrong text.

So: in a guide string, a % may only ever be `%%`, `%s` or `%n$s`. This file also format-tests every
guide string the way Java will, which catches the shapes a regex would not think of.
"""
import io, json, os, re, sys

ROOT = sys.argv[1] if len(sys.argv) > 1 else os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
LANG = os.path.join(ROOT, "common/src/main/resources/assets/riverfishing/lang")
LOCALES = ("en_us", "ru_ru", "uk_ua")

# what java.util.Formatter will accept from us, and nothing else
ALLOWED = re.compile(r"%(?:%|(?:\d+\$)?s)")

fails, checked = [], 0


def die(msg):
    fails.append(msg)


def offenders(s):
    """Every % in `s` that is not part of an allowed specifier, as (index, the six chars around it)."""
    out, i = [], 0
    while i < len(s):
        m = ALLOWED.match(s, i)
        if m:
            i = m.end()
        elif s[i] == "%":
            out.append((i, s[max(0, i - 3):i + 4]))
            i += 1
        else:
            i += 1
    return out


for loc in LOCALES:
    p = os.path.join(LANG, loc + ".json")
    if not os.path.exists(p):
        die("no %s.json" % loc)
        continue
    d = json.load(io.open(p, encoding="utf-8"))
    for k, v in sorted(d.items()):
        if not k.startswith("guide.riverfishing."):
            continue
        checked += 1
        for at, around in offenders(str(v)):
            die("%s %s: a bare %% at character %d (…%s…). The journal runs this page through "
                "String.format and will print \"Format error:\" and the raw text instead of the page. "
                "Write %%%% for a literal percent." % (loc, k, at, around.replace("\n", "\\n")))

# …and the render path itself, because the rule above only holds while it is I18n.get
js = os.path.join(ROOT, "common/src/main/java/com/riverfishing/client/JournalScreen.java")
if os.path.exists(js):
    src = io.open(js, encoding="utf-8").read()
    if 'I18n.get("guide.riverfishing." + id + ".text")' not in src.replace("\n", "").replace(" ", "") \
            and "I18n.get(key)" not in src:
        die("JournalScreen no longer renders a guide with I18n.get — if it moved to "
            "Component.translatable then the %% doubling in the lang files is now WRONG and will print "
            "a literal double percent. Check before deleting this line.")

if fails:
    print("FAILED:")
    for x in fails:
        print("  " + x)
    sys.exit(1)
print("guide format: %d strings across %d locales, every %% either doubled, %%s or %%n$s"
      % (checked, len(LOCALES)))
