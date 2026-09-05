# -*- coding: utf-8 -*-
"""§26-block-id: on 26.x, every block's Properties carries its OWN registry id.

    py -X utf8 tools/check_block_id26.py <path-to-the-26.x-worktree>

From 1.21.2 on, BlockBehaviour's constructor calls effectiveDrops(), which dereferences the id the
Properties were given, and throws "Block id not set" if there is none. There is no compiler error and no
warning: the mod builds perfectly and dies on the registry event, and because the registry then rolls
back, every later entry reports itself missing too — one bug reads as a dozen.

That happened: 0.9.0 added six blocks to this tree with a bare Properties.of(), and all four 26.x builds
refused to start. So this file holds two things the compiler cannot:

  1. every block registration goes through blockProps(...) rather than a bare Properties.of()
  2. the name it passes is its OWN. `blockProps("aerator")` under `registerSimple("snag_pile", …)` is a
     copy-paste that compiles, launches, and quietly gives one block another's loot table.

Run it against the 26.x worktree; the other two trees have no such requirement and no such helper, and
the file says so rather than pretending to check them.
"""
import io, os, re, sys

if len(sys.argv) < 2:
    print("usage: py -X utf8 tools/check_block_id26.py <path-to-the-26.x-worktree>")
    sys.exit(2)

ROOT = sys.argv[1]
P = os.path.join(ROOT, "common/src/main/java/com/riverfishing/registry/ModBlocks.java")
if not os.path.exists(P):
    print("FAILED: no ModBlocks.java under %s" % ROOT)
    sys.exit(1)

s = io.open(P, encoding="utf-8").read()
if "blockProps" not in s:
    print("this tree has no blockProps() helper, so it is not a 26.x tree — nothing to check")
    sys.exit(0)

fails, checked = [], 0


def body_of(at):
    """The registration's own text, bounded by paren depth — not by the next registration."""
    depth, i = 1, at
    while i < len(s) and depth > 0:
        if s[i] == "(":
            depth += 1
        elif s[i] == ")":
            depth -= 1
        i += 1
    return s[at:i]


# registerSimple("name", …) and registerPod("name", …) and the bare BLOCKS.register("name", …)
for m in re.finditer(r'(?:registerSimple|registerPod|BLOCKS\.register)\(\s*"(\w+)"\s*,', s):
    name, body = m.group(1), body_of(m.end())
    checked += 1
    if "BlockBehaviour.Properties.of()" in body or "new BlockBehaviour.Properties(" in body:
        fails.append("%s is registered with a bare Properties — 26.x throws \"Block id not set\" the "
                     "moment that block is built, and the whole registry rolls back behind it" % name)
        continue
    used = re.findall(r'blockProps\("(\w+)"\)', body)
    if not used:
        # A block may take its Properties from a helper of its own (registerPod does); only flag the
        # ones that clearly build their own and skip the id.
        if "Properties" in body and "Props(" not in body:
            fails.append("%s builds Properties without blockProps() and without any other id" % name)
        continue
    for u in used:
        if u != name:
            fails.append("%s passes blockProps(%r) — a block wearing another block's id gets its loot "
                         "table, silently" % (name, u))

# …and the helper itself, which one careless rewrite turned into `return blockProps("ice_hole")`.
helper = re.search(r"static BlockBehaviour\.Properties blockProps\(String name\) \{(.*?)\n    \}", s, re.S)
if not helper:
    fails.append("blockProps(String) is gone")
elif "blockProps(" in helper.group(1):
    fails.append("blockProps() calls itself — that is an infinite recursion, and the id it was supposed "
                 "to set is not set")
elif "name" not in helper.group(1):
    fails.append("blockProps() ignores the name it is given, so every block would share one id")

if fails:
    print("FAILED:")
    for x in fails:
        print("  " + x)
    sys.exit(1)
print("26.x block ids: %d registrations, each carrying its own" % checked)
