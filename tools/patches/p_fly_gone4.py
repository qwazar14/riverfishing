# -*- coding: utf-8 -*-
"""The fly's two rows in the rod tables, cut as TEXT — re-serialising these files would have reformatted
every rod in them for the sake of deleting one. Both are tab-indented and the fly was the last key, so
the comma on the key before it goes too.

Run once per tree.  py -X utf8 tools/patches/p_fly_gone4.py [tree ...]"""
import io, json, os, sys

MAIN = r"C:/Users/Qwazar/VS Code Projects/fishing mod"
TREES = [MAIN, r"C:/Users/Qwazar/wt/rf1201", r"C:/Users/Qwazar/wt/rf26"]
A = "common/src/main/resources/assets/riverfishing/"


def cut_last_key(path, key):
    if not os.path.exists(path):
        return
    s = io.open(path, encoding="utf-8").read()
    head = '\t"%s":' % key
    if head not in s:
        return
    i = s.index(head)
    # back up over the comma that ended the key before it
    j = s.rindex(",", 0, i)
    # forward to the closing brace of the whole object
    k = s.rindex("}")
    out = s[:j] + "\n" + s[k:]
    json.loads(out)   # it still parses, or nothing is written
    io.open(path, "w", encoding="utf-8", newline="\n").write(out)
    print("   ", os.path.basename(path), "lost", key)


for t in (sys.argv[1:] or TREES):
    print(" ", os.path.basename(t.rstrip("/\\")))
    cut_last_key(os.path.join(t, A, "rod_line_paths.json"), "fly")
    cut_last_key(os.path.join(t, A, "rod_physics.json"), "fly")
