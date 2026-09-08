# -*- coding: utf-8 -*-
"""§fly-4: the fly rod stops talking.

Three complaints, all fair:
  1. Two quick right clicks picked the line up, so fishing a streamer fast kept ending the cast. Gone.
     A cast is abandoned the way every other rod abandons one: switch hotbar slot. Stripping the fly all
     the way to your feet still ends it, because at that point there is no line left on the water.
  2. Too much text. The status line, the strike prompt, the landing messages and the hook messages are all
     gone — this mod does not print "Поклёвка!" when a float goes under either (§silent-bite), and the fly
     rod had no business being the exception. What is left is the water: the fly skating and throwing a
     wake, the bulge of a fish coming up, the boil when it eats, and the sounds of all three.
  3. The left click reset an invisible number, so it looked broken. It is a FLICK now: the fly is lifted
     and dropped two blocks upstream, in a visible arc of spray, and it lands riding cleanly again.

And with the prompt gone, the strike takes either button — the fly's own is a better hook, the other one
still hooks. Nothing on the screen has to say so.
Idempotent; run on each tree.  py tools/patches/p_fly4.py [tree ...]"""
import io, json, os, sys
from collections import OrderedDict

MAIN = r"C:/Users/Qwazar/VS Code Projects/fishing mod"
TREES = [MAIN, r"C:/Users/Qwazar/wt/rf1201", r"C:/Users/Qwazar/wt/rf26"]
J = "common/src/main/java/com/riverfishing/"
LANG = "common/src/main/resources/assets/riverfishing/lang"
FILES = ["fishing/FlyDrift.java", "fishing/FlyStrike.java", "fishing/FlyCast.java", "fishing/FlySession.java"]
sys.path.insert(0, os.path.join(MAIN, "tools", "patches"))
from p_fly2 import client_dialect


def rd(p): return io.open(p, encoding="utf-8").read()
def wr(p, s): io.open(p, "w", encoding="utf-8", newline="\n").write(s)


def sub(path, old, new, what):
    s = rd(path)
    if new in s: return
    assert s.count(old) == 1, "%s @ %s (%d)" % (what, path, s.count(old))
    wr(path, s.replace(old, new, 1))


def cut(path, a, b, what):
    """Remove everything from the line containing `a` through the line containing `b`."""
    s = rd(path)
    if a not in s: return
    i = s.index(a)
    i = s.rindex("\n", 0, i) + 1
    k = s.index(b, i)
    k = s.index("\n", k) + 1
    wr(path, s[:i] + s[k:])





def run(tree):
    name = os.path.basename(tree.rstrip("/\\"))
    j = lambda *a: os.path.join(tree, *a)
    if tree != MAIN:
        for rel in FILES:
            s = rd(os.path.join(MAIN, J, rel))
            if name == "rf26":
                s = (s.replace("sp.serverLevel()", "sp.level()")
                      .replace("net.minecraft.resources.ResourceLocation", "net.minecraft.resources.Identifier")
                      .replace("ResourceLocation", "Identifier"))
            wr(j(J, rel), s)
        wr(j(J, "client/FlyCastClient.java"), client_dialect(name, rd(os.path.join(MAIN, J, "client/FlyCastClient.java"))))
    fm = j(J, "fishing/FishingManager.java")
    # the landing says nothing: the sound and the water already did
    for msg in ("            actionbar(sp, Component.translatable(\"message.riverfishing.fly_tight\").withStyle(ChatFormatting.GREEN));\n",
                "            actionbar(sp, Component.translatable(\"message.riverfishing.fly_open\").withStyle(ChatFormatting.YELLOW));\n",
                "            actionbar(sp, Component.translatable(\"message.riverfishing.fly_pile\").withStyle(ChatFormatting.RED));\n"):
        s = rd(fm)
        if msg in s: wr(fm, s.replace(msg, "", 1))
    _off = "        if (session.fly != null) FlyCast.statusOff(sp);   // §fly-3: the status line goes with the line\n"
    _s = rd(fm)
    if _off in _s: wr(fm, _s.replace(_off, "", 1))
    sub(fm, "        if (s != null && s.fly != null && !s.fighting) FlyDrift.mend(sp, level, s, now);",
        "        if (s != null && s.fly != null && !s.fighting) FlyDrift.flick(sp, level, s, now);", "flick call")
    # ---- lang: everything the fly rod used to say ----
    dead = ["message.riverfishing.fly_tight", "message.riverfishing.fly_open", "message.riverfishing.fly_pile",
            "message.riverfishing.fly_spat", "message.riverfishing.fly_hook", "message.riverfishing.fly_hook_solid",
            "message.riverfishing.fly_hook_weak", "message.riverfishing.fly_on_fish", "message.riverfishing.fly_straight",
            "message.riverfishing.fly_too_fast", "message.riverfishing.fly_too_slow", "message.riverfishing.fly_drag", "message.riverfishing.fly_pickup",
            "hud.riverfishing.fly_dead_drift", "hud.riverfishing.fly_drag", "hud.riverfishing.fly_straight",
            "hud.riverfishing.fly_on_fish", "hud.riverfishing.fly_approach", "hud.riverfishing.fly_strike_lmb",
            "hud.riverfishing.fly_strike_rmb", "hud.riverfishing.fly_controls", "gui.riverfishing.fly_hint",
            "gui.riverfishing.fly_full"]
    for code in ("en_us", "ru_ru", "uk_ua"):
        p = j(LANG, code + ".json"); d = json.loads(rd(p), object_pairs_hook=OrderedDict)
        n = sum(1 for k in dead if d.pop(k, None) is not None)
        if n: wr(p, json.dumps(d, ensure_ascii=False, indent=2) + "\n")
    print("  patched", name)


for t in (sys.argv[1:] or TREES): run(t)
