# -*- coding: utf-8 -*-
"""How heavy is the journal packet?

    python tools/check_journal_payload.py

§journal-card ships one card per species inside the CompoundTag the journal already opens with, so the
client no longer needs the fish profiles — they are server-only data and a client on a dedicated server
has none of them. The cost of that is a bigger packet, and the packet grows every time a species is
added: 54 species in 0.5.0, 79 now, and the backlog has three more waves in it.

So this measures rather than hopes. It re-implements NBT's own sizing rules against the same profile
JSONs the server reads, and fails if the table gets anywhere near what a custom payload may carry.
"""
import io, json, glob, os, sys

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PROF = os.path.join(REPO, "common/src/main/resources/data/riverfishing/fish_profiles")

# A clientbound custom payload is capped at 1 MiB. Anything approaching a tenth of that is a design
# problem worth hearing about long before it is a disconnect.
LIMIT = 1024 * 1024
WARN = LIMIT // 10

WATERS, SEASONS, TIMES = 6, 4, 4


def named(name, payload):
    """One entry in a compound: type byte + a short-prefixed name + the payload."""
    return 1 + 2 + len(name.encode("utf-8")) + payload


def string(s):
    return 2 + len(s.encode("utf-8"))


def card(p):
    n = 0
    n += named("g", string(p.get("group", "other")))
    for k in ("wmin", "wmax", "wmean", "lmin", "lmax", "trophy", "dmin", "dmax", "wdmin", "wdmax",
              "lvl", "fr", "reel"):
        n += named(k, 4)                      # TAG_Int
    for k in ("gbF", "gbN", "fs", "fst", "dia"):
        n += named(k, 4)                      # TAG_Float
    n += named("fp", string(p.get("fight", {}).get("pattern", "steady")))
    n += named("line", string(p.get("ideal", {}).get("line", {}).get("type", "mono")))
    for k, count in (("water", WATERS), ("season", SEASONS), ("time", TIMES)):
        n += named(k, 1 + 4 + 4 * count)      # TAG_List of floats
    for k, src in (("bio", p.get("biomes", {})),
                   ("bait", p.get("ideal", {}).get("bait", {}))):
        inner = sum(named(name, 4) for name in src) + 1
        n += named(k, inner)
    for k, src in (("rod", p.get("ideal", {}).get("rod", [])),
                   ("rig", p.get("ideal", {}).get("rig", []))):
        inner = 1 + 4 + sum(string(s) for s in src)
        n += named(k, inner)
    return n + 1                              # TAG_End of this card


total, biggest, rows = 1, ("", 0), []
for f in sorted(glob.glob(os.path.join(PROF, "*.json"))):
    p = json.loads(io.open(f, encoding="utf-8").read())
    name = os.path.basename(f)[:-5]
    size = named(name, card(p))
    total += size
    rows.append((size, name))
    if size > biggest[1]:
        biggest = (name, size)

rows.sort(reverse=True)
print("  %d species, cards table ~%.1f KB (%d bytes)" % (len(rows), total / 1024.0, total))
print("  heaviest: %s at %d B; lightest: %s at %d B; mean %d B"
      % (rows[0][1], rows[0][0], rows[-1][1], rows[-1][0], total // max(1, len(rows))))
print("  %.1f%% of the 1 MiB custom-payload cap" % (100.0 * total / LIMIT))
print("  room for ~%d species at this average before the warn line"
      % (WARN // max(1, total // max(1, len(rows)))))

if total > LIMIT:
    print("\nFAILED: the journal packet cannot be sent at all")
    sys.exit(1)
if total > WARN:
    print("\nFAILED: over a tenth of the payload cap — send the cards on join and cache them instead")
    sys.exit(1)
print("\nthe journal packet is comfortable")
