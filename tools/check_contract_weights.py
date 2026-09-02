# -*- coding: utf-8 -*-
"""A weight that is already grams must never be multiplied by a thousand.

    py tools/check_contract_weights.py [--selftest]

Contracts shipped a board reading

    3 x Peacock bass, from 1753.1 kg

because Contracts.minGrams() did `p.weightMean * 1000.0 * share` and weightMean is parsed from
"weight_g" — grams, already. Nothing complained: 1753100 is a perfectly good integer, the cap it was
clamped against had the same x1000 in it, and the number only looks absurd once a player reads it in
their own language. It took a screenshot to find.

The first version of this check mirrored minGrams() in Python and compared the answer against the
profile. That cannot work, and it is worth saying why: a mirror reads the same JSON and applies the
same intent, so it computes the RIGHT number and agrees with nothing. A mirror of a formula can only
catch a typo in the mirror.

What actually distinguishes the bug is visible in the source and nowhere else: a field whose name ends
in a gram unit, multiplied by a thousand. That is what this reads. It is a lint, not a simulation, and
it is the shape the bug had.

Also checks the profiles themselves for a mean above the maximum, which is the data-side version of
the same mistake.
"""
import glob, io, json, os, re, sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = "common/src/main/java/com/riverfishing"
PROF = "common/src/main/resources/data/riverfishing/fish_profiles"

# weightMean/weightMax/weightMin, or anything named ...WeightG / ...weightG, times a thousand.
SCALED = re.compile(r"\b(\w*[Ww]eight(?:Mean|Max|Min|G|MeanG|MaxG)?)\s*\*\s*1000(?:\.0)?\b")

# Dividing BY a thousand is how grams become kilograms and is correct — only multiplication is wrong.


def offenders(text):
    out = []
    for i, line in enumerate(text.split("\n"), 1):
        if line.lstrip().startswith(("//", "*")):
            continue                      # a comment describing the bug is not the bug
        m = SCALED.search(line)
        if m:
            out.append((i, m.group(1), line.strip()))
    return out


def profile_faults():
    out = []
    for path in sorted(glob.glob(os.path.join(ROOT, PROF, "*.json"))):
        name = os.path.basename(path)[:-len(".json")]
        w = (json.load(io.open(path, encoding="utf-8")).get("weight_g") or {})
        mean, mx = w.get("mean"), w.get("max")
        if mean and mx and mean > mx:
            out.append("%s: mean %s g is above max %s g" % (name, mean, mx))
    return out, len(glob.glob(os.path.join(ROOT, PROF, "*.json")))


def selftest():
    shipped = "        int g = (int) Math.round(p.weightMean * 1000.0 * share);"
    assert offenders(shipped), "MISSED the line that shipped"
    assert offenders("int g = (int) Math.round(p.weightMax * 1000 * 0.8);"), "missed the cap"
    # correct code, in both directions
    assert not offenders("        int g = (int) Math.round(p.weightMean * share);"), "flagged the fix"
    assert not offenders("        double kg = weightMeanG / 1000.0;"), "flagged a grams->kg divide"
    assert not offenders("        // weightMean * 1000 was the bug"), "flagged a comment about it"
    assert not offenders("        int ms = seconds * 1000;"), "flagged an unrelated thousand"
    print("self-test ok: catches the line that shipped and its cap, and leaves correct code alone")
    return 0


def main():
    bad = []
    scanned = 0
    for base, _, names in os.walk(os.path.join(ROOT, SRC)):
        for n in names:
            if not n.endswith(".java"):
                continue
            scanned += 1
            path = os.path.join(base, n)
            text = io.open(path, encoding="utf-8").read()
            for line, field, src in offenders(text):
                bad.append((os.path.relpath(path, ROOT).replace("\\", "/"), line, field, src))

    data_bad, profiles = profile_faults()
    print("%d sources and %d fish profiles read" % (scanned, profiles))

    if bad:
        print("\n%d place(s) scale a weight that is already grams:" % len(bad))
        for rel, line, field, src in bad:
            print("  %s:%d  %s" % (rel, line, src))
    if data_bad:
        print("\n%d profile(s) whose ordinary fish is bigger than its biggest:" % len(data_bad))
        for d in data_bad:
            print("  %s" % d)
    if bad or data_bad:
        return 1
    print("every weight stays in the unit it was written in")
    return 0


if __name__ == "__main__":
    sys.exit(selftest() if "--selftest" in sys.argv else main())
