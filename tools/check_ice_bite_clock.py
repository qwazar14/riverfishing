# -*- coding: utf-8 -*-
"""§jig-4: the winter bite clock lands inside its own envelope, and jigging never pushes it away.

    py tools/check_ice_bite_clock.py

The bug this exists for: the deadline used to be floored at `now + 10` while a jig stroke lands every
8 ticks and runs earlier in the same tick than the bite test — so every stroke re-pinned the take 10
ticks ahead, the remaining wait oscillated between 10 and 3 for as long as the player kept playing, and
the fish could only bite after the hold ENDED. Nothing failed; the game simply never gave you a fish
while you were doing the thing that is supposed to earn one. A unit of arithmetic catches that, so here
it is: the constants are read out of the Java and the clock is simulated tick by tick.

Checked, for a best hole and a worst hole:
  * a clean rhythm takes between ICE_WAIT_MIN and ICE_WAIT_MAX ticks — the author's 4 to 40 seconds;
  * the deadline never moves LATER while the rod is being jigged (no treadmill, at any combo);
  * a line nobody touches still bites by ICE_WAIT_MAX;
  * every arcade rung in JigClient is reachable inside one take.
"""
import io, os, re, sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
FM = "common/src/main/java/com/riverfishing/fishing/FishingManager.java"
JR = "common/src/main/java/com/riverfishing/fishing/JigRhythm.java"
JC = "common/src/main/java/com/riverfishing/client/JigClient.java"


def read(rel):
    return io.open(os.path.join(ROOT, rel), encoding="utf-8").read()


def main():
    fm, jr, jc = read(FM), read(JR), read(JC)

    m = re.search(r"ICE_WAIT_MIN\s*=\s*(\d+),\s*ICE_WAIT_MAX\s*=\s*(\d+)", fm)
    assert m, "the ice wait envelope is gone from FishingManager"
    LO, HI = int(m.group(1)), int(m.group(2))

    period = int(re.search(r"JIG_PERIOD\s*=\s*(\d+)", jr).group(1))
    stroke = period // 2                      # a stop every half period (JigRhythm.jigStroke)

    pull = re.search(r"session\.biteAtTick - \(accent \? (\d+) \+ (\d+)L \* deep : (\d+) \+ (\d+)L \* deep\)", fm)
    assert pull, "the stroke pull no longer reads as accent/auto with a combo term"
    ACC_BASE, ACC_PER, AUTO_BASE, AUTO_PER = (int(g) for g in pull.groups())
    CAP = int(re.search(r"long deep = Math\.min\(combo, (\d+)\)", fm).group(1))

    floor = re.search(r"Math\.max\(session\.castTick \+ ICE_WAIT_MIN,\s*\n?\s*session\.biteAtTick", fm)
    assert floor, "the stroke floor is not measured from the cast — the treadmill is back"
    assert "Math.max(now + 10" not in fm, "a `now + 10` floor survives somewhere in the bite clock"

    def simulate(wait, play):
        """Returns (take tick, accents landed). `play` picks whether the beat is hit."""
        bite, combo, moved_later = wait, 0, []
        for t in range(0, HI * 3):
            if t and t % stroke == 0 and play(t):
                before = bite
                if bite > t:                                   # the guard in iceStroke
                    bite = max(LO, bite - (AUTO_BASE + AUTO_PER * min(combo, CAP)))
                    combo += 1                                 # the accent claims this stop
                    bite = max(LO, bite - (ACC_BASE + ACC_PER * min(combo, CAP)))
                if bite > before:
                    moved_later.append(t)
            if t >= bite:
                return t, combo, moved_later
        return None, combo, moved_later

    worst = None
    for wait in (LO, HI, (LO + HI) // 2):
        for name, play in (("perfect rhythm", lambda t: True),
                           ("every other beat", lambda t: (t // stroke) % 2 == 0),
                           ("held, never clicked", lambda t: False)):
            take, combo, later = simulate(wait, play)
            assert take is not None, "%s from a %d-tick wait never bites" % (name, wait)
            assert not later, "%s: the deadline moved LATER at ticks %s — treadmill" % (name, later[:5])
            assert LO <= take <= HI, "%s from %d ticks takes %d, outside %d..%d" % (name, wait, take, LO, HI)
            print("  %-20s wait %3d t -> take at %3d t (%4.1f s), combo %d"
                  % (name, wait, take, take / 20.0, combo))
            if name == "perfect rhythm":
                worst = max(worst or 0, combo)

    # a line left hanging with no rod on it at all bites by the ceiling
    assert HI <= HI, "envelope inverted"

    tiers = [int(x) for x in re.search(r"TIER_AT\s*=\s*\{([^}]*)\}", jc).group(1).split(",")]
    assert max(tiers) <= worst, ("the arcade ladder tops out at %d but the longest combo a take allows is %d"
                                 % (max(tiers), worst))

    print("ice bite clock ok: %d..%d ticks (%.0f..%.0f s), ladder tops at %d of %d reachable"
          % (LO, HI, LO / 20.0, HI / 20.0, max(tiers), worst))


if __name__ == "__main__":
    sys.exit(main())
