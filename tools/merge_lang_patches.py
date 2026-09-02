# -*- coding: utf-8 -*-
"""§breeding: fold tools/patches/lang_*.json into the three lang files, keys inserted (or replaced)
before the closing brace, one line each, so the files keep their own layout.

    py tools/merge_lang_patches.py            # merges every lang_*.json found
    py tools/merge_lang_patches.py lang_b     # just one

Every patch must carry the same key set in en_us / ru_ru / uk_ua — refused otherwise, because a key
that exists in one language and not the others is exactly the bug check_lang.py exists to catch.
"""
import glob, io, json, os, sys

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
LANG = os.path.join(REPO, "common", "src", "main", "resources", "assets", "riverfishing", "lang")
PATCHES = os.path.join(REPO, "tools", "patches")
LANGS = ("en_us", "ru_ru", "uk_ua")

only = sys.argv[1] if len(sys.argv) > 1 else None
files = sorted(glob.glob(os.path.join(PATCHES, "lang_*.json")))
if only:
    files = [f for f in files if os.path.basename(f) == only + ".json" or os.path.basename(f) == only]
if not files:
    sys.exit("no lang patches found")

added = {l: {} for l in LANGS}
for f in files:
    patch = json.load(io.open(f, encoding="utf-8"))
    keys = None
    for l in LANGS:
        if l not in patch:
            sys.exit("%s: missing %s" % (os.path.basename(f), l))
        k = set(patch[l])
        if keys is not None and k != keys:
            sys.exit("%s: key sets differ between languages: %s" % (os.path.basename(f), sorted(k ^ keys)))
        keys = k
        added[l].update(patch[l])
    print("%s: %d keys" % (os.path.basename(f), len(keys)))

for l in LANGS:
    p = os.path.join(LANG, "%s.json" % l)
    raw = io.open(p, encoding="utf-8").read()
    lines = raw.rstrip("\n").split("\n")
    # drop existing copies of the keys we are adding, then append before the closing brace
    out = []
    for ln in lines:
        s = ln.strip()
        key = s.split('"')[1] if s.startswith('"') else None
        if key in added[l]:
            continue
        out.append(ln)
    assert out[-1].strip() == "}", "%s does not end with }" % p
    body = out[:-1]
    # the previous last entry must end with a comma before we append
    if body and body[-1].rstrip().endswith("}") is False and not body[-1].rstrip().endswith(","):
        body[-1] = body[-1].rstrip() + ","
    for k in added[l]:
        body.append('  "%s": %s,' % (k, json.dumps(added[l][k], ensure_ascii=False)))
    body[-1] = body[-1].rstrip(",")
    io.open(p, "w", encoding="utf-8", newline="\n").write("\n".join(body) + "\n}\n")
    json.load(io.open(p, encoding="utf-8"))          # it must still parse
    print("%s: +%d" % (l, len(added[l])))
