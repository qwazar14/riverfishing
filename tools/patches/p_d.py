# -*- coding: utf-8 -*-
"""§breeding stream D: the one edit to an existing file — the "nets" guide page on the journal shelf.

    py tools/patches/p_d.py <repo root> [1211|1201|26]

Idempotent: the §D marker on the inserted line makes a rerun a no-op. The anchor is the same line in
all three trees, so the dialect argument is accepted and unused.
"""
import io, os, sys

root = sys.argv[1] if len(sys.argv) > 1 else "."
MARK = "// §D"
path = os.path.join(root, "common", "src", "main", "java", "com", "riverfishing", "client", "JournalScreen.java")


def sub1(text, old, new):
    if old not in text:
        print("ANCHOR MISSING in %s:\n%s" % (path, old))
        sys.exit(1)
    return text.replace(old, new, 1)


with io.open(path, encoding="utf-8") as f:
    src = f.read()
if MARK in src:
    print("p_d: already applied")
    sys.exit(0)

# The page sits beside "community" — the water's population is where a net's cost is explained.
src = sub1(src,
           '        addGuide("community", modStack("fish_finder"));\n',
           '        addGuide("community", modStack("fish_finder"));\n'
           '        addGuide("nets", modStack("seine_net")); ' + MARK + '\n')
with io.open(path, "w", encoding="utf-8", newline="\n") as f:
    f.write(src)
print("p_d: applied")
