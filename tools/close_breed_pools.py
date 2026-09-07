# -*- coding: utf-8 -*-
"""§species-table: the hybrids in the author's table link parents that were in different breeding pools,
and a pool has to be a closed clique (tools/check_breeds.py rule 3). This closes every pool: any two
species joined through the others get a direct, weak rate. Idempotent; run on each tree.
    py tools/close_breed_pools.py [tree ...]"""
import io, json, os, sys

TREES = [r"C:/Users/Qwazar/VS Code Projects/fishing mod", r"C:/Users/Qwazar/wt/rf1201", r"C:/Users/Qwazar/wt/rf26"]
PROF = "common/src/main/resources/data/riverfishing/fish_profiles"
WEAK = 0.2


def run(tree):
    d = os.path.join(tree, PROF)
    profiles = {f[:-5]: json.load(io.open(os.path.join(d, f), encoding="utf-8")) for f in os.listdir(d) if f.endswith(".json")}
    edges = {}
    for sid, p in profiles.items():
        bw = p.get("breeds_with", {})
        if isinstance(bw, list): bw = {o: 1.0 for o in bw}
        for o, r in bw.items():
            if o in profiles:
                edges.setdefault(sid, {})[o] = r
                edges.setdefault(o, {}).setdefault(sid, r)
    seen, added = set(), 0
    for sid in sorted(edges):
        if sid in seen: continue
        pool, stack = {sid}, [sid]
        while stack:
            x = stack.pop()
            for o in edges.get(x, {}):
                if o not in pool: pool.add(o); stack.append(o)
        seen |= pool
        for a in pool:
            for b in pool:
                if a != b and b not in edges.get(a, {}):
                    r = edges.get(b, {}).get(a, WEAK)
                    edges.setdefault(a, {})[b] = r; edges.setdefault(b, {})[a] = r; added += 1
    for sid, row in edges.items():
        p = profiles[sid]
        p["breeds_with"] = {o: row[o] for o in sorted(row)}
        io.open(os.path.join(d, sid + ".json"), "w", encoding="utf-8", newline="\n").write(json.dumps(p, ensure_ascii=False, indent=2) + "\n")
    print("  %-12s %d links added" % (os.path.basename(tree.rstrip("/\\")), added // 2))


for t in (sys.argv[1:] or TREES): run(t)
