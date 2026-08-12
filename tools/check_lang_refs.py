# -*- coding: utf-8 -*-
"""Does every lang key the code asks for actually exist? On every tree.

    python tools/check_lang_refs.py

check_lang.py compares the three languages against each other, which catches a key present in one and
missing from another. It cannot catch a key that is missing from ALL of them — and that is the failure
this script exists for, because it renders in-game as the raw key text on a page a player is reading.

It found one the first time it ran: guide.riverfishing.trophy_from was deleted from all three languages
when the journal moved to FishCard, but the two PORT trees never got that Java change, so their species
page still asked for it. A key deleted on canon and still called on a branch is invisible until someone
opens the page on the version you do not play.

Keys built by concatenation ("prefix." + id) are skipped — a stem ending in "." or "_" is a prefix, not
a key, and this script cannot know what gets appended.
"""
import io, json, os, re, sys

HOME = os.path.expanduser("~")
TREES = [os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
         os.path.join(HOME, "wt", "rf1201"), os.path.join(HOME, "wt", "rf26")]
LANG = "common/src/main/resources/assets/riverfishing/lang/en_us.json"
KEY = re.compile(r'"([a-z_]+\.riverfishing\.[a-z0-9_.]+)"')

fails = []
for tree in TREES:
    tag = os.path.basename(tree)
    lang_path = os.path.join(tree, LANG)
    if not os.path.exists(lang_path):
        print("  %-12s no lang file — tree not checked out?" % tag)
        continue
    known = set(json.loads(io.open(lang_path, encoding="utf-8").read()))
    missing = {}
    for dirpath, _, names in os.walk(os.path.join(tree, "common/src/main/java")):
        for name in names:
            if not name.endswith(".java"):
                continue
            path = os.path.join(dirpath, name)
            text = io.open(path, encoding="utf-8", errors="ignore").read()
            # a key named only in a comment is documentation, not a lookup
            text = re.sub(r"/\*.*?\*/", "", text, flags=re.S)
            text = re.sub(r"//[^\n]*", "", text)
            for k in KEY.findall(text):
                if k.endswith(".") or k.endswith("_"):
                    continue          # a stem the code appends to
                if k not in known:
                    missing.setdefault(k, name)
    print("  %-12s %d keys asked for and not defined" % (tag, len(missing)))
    for k, where in sorted(missing.items()):
        print("      %s   (%s)" % (k, where))
        fails.append("%s: %s" % (tag, k))

if fails:
    print("\nFAILED: %d reference(s) would render as raw key text in game" % len(fails))
    sys.exit(1)
print("\nevery key the code asks for exists on every tree")
