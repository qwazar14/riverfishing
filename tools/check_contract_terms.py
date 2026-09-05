# -*- coding: utf-8 -*-
"""§contracts-b1: the two ways a contract can fail silently, as a source lint.

    py tools/check_contract_terms.py

1. The trusted shelf names items by id and `sellOf` returns null for an unknown one — the shelf would
   simply not appear, at rep 30, for the one player who earned it. Every id in TRUSTED must be
   registered in ModItems (the reel loop included).
2. Every literal lang key the contract code prints must exist in en_us.json, and the three languages
   must carry the same keys — a missing key renders as itself, on parchment, in the villager's window.
"""
import io, json, os, re, sys

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
J = os.path.join(REPO, "common", "src", "main", "java", "com", "riverfishing")
R = os.path.join(REPO, "common", "src", "main", "resources", "assets", "riverfishing")


def read(*p):
    return io.open(os.path.join(*p), encoding="utf-8").read()


bad = 0

# ---- 1. the trusted shelf
items = read(J, "registry", "ModItems.java")
ids = set(re.findall(r'reg\("([a-z0-9_]+)"', items))
m = re.search(r'for \(int size : new int\[\]\{([\d, ]+)\}\)', items)
if m:
    ids |= {"reel_%s" % s.strip() for s in m.group(1).split(",")}
vill = read(J, "registry", "ModVillagers.java")
block = vill[vill.index("TRUSTED = {"):vill.index("};", vill.index("TRUSTED = {"))]
trusted = re.findall(r'"([a-z0-9_]+)"', block)
for t in trusted:
    if t not in ids:
        print("  FAIL trusted item not registered: %s" % t)
        bad += 1
print("trusted shelf: %s" % ", ".join(trusted))

# ---- 2. the lang keys
en = json.loads(read(R, "lang", "en_us.json"))
src = "".join(read(J, *p) for p in (("fishing", "Contracts.java"), ("item", "ContractItem.java"),
                                    ("client", "ContractBoardState.java")))
keys = set()
for m in re.finditer(r'"((?:contract|message|journal|screen)\.riverfishing\.[\w.]+)"(\s*\+)?', src):
    if not m.group(2):
        keys.add(m.group(1))
# say(sp, "contract_x", ...) builds message keys from a stem
for stem in re.findall(r'say\(sp, "(\w+)"', src):
    keys.add("message.riverfishing." + stem)
for k in sorted(keys):
    if k not in en:
        print("  FAIL missing lang key: %s" % k)
        bad += 1
for lang in ("ru_ru", "uk_ua"):
    other = json.loads(read(R, "lang", "%s.json" % lang))
    for k in sorted(keys):
        if k not in other:
            print("  FAIL %s lacks %s" % (lang, k))
            bad += 1
print("%d contract lang keys present in three languages" % len(keys))

if bad:
    sys.exit("%d problem(s)" % bad)
print("ok")
