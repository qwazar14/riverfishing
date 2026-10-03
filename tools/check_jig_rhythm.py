# -*- coding: utf-8 -*-
"""§jig-3: the winter jig's beat — what the server judges and what the screen shows are the same beat.

    py tools/check_jig_rhythm.py

The rhythm lives in two places: JigRhythm judges a click against a triangle wave in TICKS, and JigClient
draws that same wave in PIXELS. Nothing in either build knows about the other, and the way they come
apart is not an error — it is a green stop that the needle is standing on and the server calls a miss,
which is precisely what got reported. So the two are re-derived here and compared tick by tick.

Also checks the two things the rework is: no ceiling on the combo, and an arcade word for every rung.
"""
import io, json, os, re, sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRV = "common/src/main/java/com/riverfishing/fishing/JigRhythm.java"
CLI = "common/src/main/java/com/riverfishing/client/JigClient.java"
LANG = "common/src/main/resources/assets/riverfishing/lang/%s.json"
LANGS = ("en_us", "ru_ru", "uk_ua")


def read(rel):
    return io.open(os.path.join(ROOT, rel), encoding="utf-8").read()


def const(text, name, cast):
    m = re.search(r"%s\s*=\s*([0-9.]+)f?\s*;" % name, text)
    assert m, "no %s in the source any more" % name
    return cast(m.group(1))


def triangle(el, period):
    """The server's marker(), and the client's — one wave, written once here."""
    phase = (el % period) / float(period)
    return phase * 2 if phase < 0.5 else 2 - phase * 2


def main():
    srv, cli = read(SRV), read(CLI)
    period = const(srv, "JIG_PERIOD", int)
    zone = const(srv, "JIG_ZONE_HALF", float)
    tw = int(re.search(r"TW\s*=\s*(\d+)", cli).group(1))

    # the server's verdict, and the pixel the player is looking at, for every tick of a period
    bad = []
    for el in range(period * 3):
        m = triangle(el, period)
        accepts = min(m, 1 - m) <= zone
        zw = max(2, int(zone * tw))
        mx = int(m * tw)
        drawn_on_stop = mx <= zw or mx >= tw - zw
        if accepts != drawn_on_stop:
            bad.append((el, round(m, 3), accepts, drawn_on_stop))
    assert not bad, "the needle and the hitbox disagree on these ticks: %s" % bad

    # §jig-3: the combo has no ceiling — JIG_MAX is the advancement's number, not a cap
    accent = srv[srv.index("public static int jigAccent"):]
    accent = accent[:accent.index("\n    }")]
    assert "JIG_MAX" not in accent, "jigAccent is capping the combo again"
    assert "s.beats = 0" in accent, "a miss no longer resets the combo"

    # every rung of the arcade ladder has a word, in every language
    tiers = re.search(r"TIER_AT\s*=\s*\{([^}]*)\}", cli).group(1)
    tiers = [int(x) for x in tiers.split(",")]
    assert tiers == sorted(tiers, reverse=True), "the ladder is out of order: %s" % tiers
    keys = re.findall(r'"([a-z]+)"', re.search(r"TIER_KEY\s*=\s*\{([^}]*)\}", cli).group(1))
    assert len(keys) == len(tiers), "%d rungs but %d words" % (len(tiers), len(keys))
    want = ["hud.riverfishing.jig." + k for k in keys] + ["hud.riverfishing.jig.miss"]
    for lang in LANGS:
        have = json.load(io.open(os.path.join(ROOT, LANG % lang), encoding="utf-8"))
        missing = [k for k in want if k not in have]
        assert not missing, "%s is missing %s" % (lang, missing)
        assert "%s" in have["hud.riverfishing.jig.miss"], "%s: the MISS line dropped its %%s" % lang

    print("jig rhythm ok: period %d, zone +-%.2f (+-%.1f ticks), %d rungs, 3 languages"
          % (period, zone, zone * period / 2, len(tiers)))


if __name__ == "__main__":
    sys.exit(main())
