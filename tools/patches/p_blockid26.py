# -*- coding: utf-8 -*-
"""§26-block-id: every block's Properties carries its registry id, or 26.x refuses to build the block.

    py -X utf8 tools/patches/p_blockid26.py <root> 26

Reported as a crash on 26.1.2 Fabric and both 26.2 loaders, identically:

    java.lang.NullPointerException: Block id not set
        at BlockBehaviour$Properties.effectiveDrops(BlockBehaviour.java:1163)
        at BlockBehaviour.<init>
        at com.riverfishing.block.WaterUpgradeBlock.<init>

From 1.21.2 on, a block's Properties must carry the id it will be registered under: the constructor
derives the loot table from it and throws the moment it is missing. This tree has known that since the
port — {@code ModBlocks.blockProps(name)} exists for exactly this and every block written before 0.9.0
uses it — but the six blocks 0.9.0 added (the five water upgrades and the pond sign) were ported with a
bare {@code Properties.of()}, which is what the other two trees want and what 26.x will not take.

Nothing after the first failure means anything: the registry rolls back, so the log's later complaints
("Registry Object not present: riverfishing:corn_crop", "…:trophy_stand") are the rollback talking, not
six more bugs.

This is 26.x only. The other two trees have no such requirement and no such helper.
"""
import io, os, re, sys

ROOT = sys.argv[1]
D = sys.argv[2] if len(sys.argv) > 2 else "26"
if D != "26":
    print("  not a 26.x tree — a block id is not a thing there")
    sys.exit(0)

P = os.path.join(ROOT, "common/src/main/java/com/riverfishing/registry/ModBlocks.java")
s = io.open(P, encoding="utf-8").read()

# Each registerSimple("name", () -> new Something(BlockBehaviour.Properties.of()…)) gets blockProps(name).
#
# The body is bounded by PAREN DEPTH, not by "up to the next registerSimple". The first cut used the
# latter, and the last registration in the file therefore ran to the end of it and rewrote the
# `Properties.of()` inside blockProps' own definition — `return blockProps("ice_hole")`, an infinite
# recursion that also lost the id it was there to set. A registration ends where its parens close.
fixed = []
for m in list(re.finditer(r'registerSimple\("(\w+)",', s)):
    name = m.group(1)
    depth, i = 1, m.end()                      # the paren of registerSimple( is already open
    while i < len(s) and depth > 0:
        if s[i] == "(":
            depth += 1
        elif s[i] == ")":
            depth -= 1
        i += 1
    body = s[m.end():i]
    if "BlockBehaviour.Properties.of()" not in body:
        continue
    s = s[:m.end()] + body.replace("BlockBehaviour.Properties.of()", 'blockProps("%s")' % name, 1) + s[i:]
    fixed.append(name)

if not fixed:
    print("  every block already carries its id")
    sys.exit(0)

io.open(P, "w", encoding="utf-8", newline="\n").write(s)
print("  ModBlocks: %d blocks given their registry id — %s" % (len(fixed), ", ".join(fixed)))
print("done (%s)" % D)
