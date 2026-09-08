# -*- coding: utf-8 -*-
"""Three fixes after the morning's test.
  §bossbar-end  the boss bar outlived the fight: the revert had put every block back but endSession's — it is back.
  §line-glide   the line's water end walked block to block (the fly's drift, the strip); the client now eases the
                drawn end between the server's block centres, the way the spinning retrieve already glides.
  §fly-3        no set moment on the fly rod: the take hooks itself and goes straight into the show (the spray,
                the breach) and the fight. The rise clock, its SET label and the too-fast / too-slow messages go.
Idempotent; run on each tree.  py tools/patches/p_fly_fixes.py [tree ...]"""
import io, json, os, re, sys
from collections import OrderedDict

TREES = [r"C:/Users/Qwazar/VS Code Projects/fishing mod", r"C:/Users/Qwazar/wt/rf1201", r"C:/Users/Qwazar/wt/rf26"]
J = "common/src/main/java/com/riverfishing/"
LANG = "common/src/main/resources/assets/riverfishing/lang"


def rd(p): return io.open(p, encoding="utf-8").read()
def wr(p, s): io.open(p, "w", encoding="utf-8", newline="\n").write(s)


def sub(path, old, new, what):
    s = rd(path)
    if new in s: return
    assert s.count(old) == 1, "%s @ %s (%d)" % (what, path, s.count(old))
    wr(path, s.replace(old, new, 1))


def cut(path, pattern, what):
    s = rd(path)
    s2, n = re.subn(pattern, "", s, count=1, flags=re.S)
    if n == 0 and what + "-gone" in s: return
    assert n == 1, what + " @ " + path
    wr(path, s2)


WIKI = {
    "docs/wiki/fly-fishing.md": ("## The take and the set\n", "## The hatch\n", """## The take

A fish takes a fly by coming up under it and turning down — and a fly hook sets itself: the fish turns on it. There is no strike bar and nothing to click. The take is the show: the fish comes out of the water in a burst of spray with a slap the whole bank hears, and you are straight into the fight. Keep the tip high and let the drag give line when it runs.

""", "and set the hook when it comes up.", "and hold on when it comes up."),
    "docs/wiki/ru/fly-fishing.md": ("## Поклёвка и подсечка\n", "## Вылет насекомых\n", """## Поклёвка

Рыба берёт мушку, поднимаясь под неё и разворачиваясь вниз — и нахлыстовый крючок подсекает сам: рыба разворачивается на нём. Полоски подсечки нет, кликать нечего. Поклёвка — это зрелище: рыба вылетает из воды в фонтане брызг со шлепком, который слышит весь берег, и вы сразу в бою. Держите вершинку высоко и давайте фрикциону отдавать шнур на рывках.

""", "и подсечь, когда она возьмёт.", "и удержать её, когда она возьмёт."),
    "docs/wiki/uk/fly-fishing.md": ("## Поклювання і підсічка\n", "## Виліт комах\n", """## Поклювання

Риба бере мушку, піднімаючись під неї й розвертаючись донизу — і нахлистовий гачок підсікає сам: риба розвертається на ньому. Смужки підсічки немає, клікати нічого. Поклювання — це видовище: риба вилітає з води у фонтані бризок із ляском, який чує весь берег, і ви одразу в бою. Тримайте вершинку високо й давайте фрикціону віддавати шнур на ривках.

""", "і підсікти, коли вона візьме.", "і втримати її, коли вона візьме."),
}


def run(tree):
    j = lambda *a: os.path.join(tree, *a)
    fm = j(J, "fishing/FishingManager.java")
    # §bossbar-end
    sub(fm, "        if (session.ctx != null && session.ctx.rod == RodType.FLY) FlyCast.driftOff(sp);   // §fly-2: the status line goes with it\n",
        "        if (session.ctx != null && session.ctx.rod == RodType.FLY) FlyCast.driftOff(sp);   // §fly-2: the status line goes with it\n"
        "        if (session.bossBar != null) {   // §bossbar-end: the bar goes with the fight\n            session.bossBar.removeAllPlayers();\n            session.bossBar = null;\n        }\n", "endSession bar")
    # §fly-3
    sub(fm, "                if (session.ctx != null && session.ctx.rod == RodType.FLY) {   // §fly: the rise — a timed set, not a random zone\n                    startFlyRise(sp, session, now);\n                } else if",
        "                if (session.ctx != null && session.ctx.rod == RodType.FLY) {   // §fly-3: the take hooks itself — straight into the show\n                    FlyCast.driftOff(sp);\n                    hookUp(sp, level, session, now);\n                    return;\n                } else if", "fly take")
    s = rd(fm)
    if "private static void startFlyRise" in s:
        sig = s.index("    private static void startFlyRise(ServerPlayer sp, FishingSession session, long now) {")
        start = s.rindex("    /**\n", 0, sig)          # the method's own javadoc
        end = s.index("\n    }\n", sig) + len("\n    }\n")
        while s[end:end + 1] == "\n": end += 1
        s2 = s[:start] + s[end:]
        assert "startFlyRise" not in s2, "startFlyRise"
        wr(fm, s2)
    cut(fm, r"            if \(session\.ctx != null && session\.ctx\.rod == RodType\.FLY\) \{\n                // §fly: before the green the fly came out of its mouth.*?\n            \}\n", "fly_too_fast")
    assert "fly_too_fast" not in rd(fm), "fly strike branch left"
    # §line-glide
    cls = j(J, "client/ClientLineState.java")
    sub(cls, "        public float smoothProgress;   // eased for rendering\n",
        "        public float smoothProgress;   // eased for rendering\n        public net.minecraft.world.phys.Vec3 shownEnd;   // §line-glide: the drawn water end, eased between block centres\n", "shownEnd field")
    sub(cls, "        public void tickSmoothing(float frameSeconds) {\n            smoothProgress = Mth.lerp(Math.min(1f, frameSeconds * 6f), smoothProgress, progress);\n",
        "        public void tickSmoothing(float frameSeconds) {\n            smoothProgress = Mth.lerp(Math.min(1f, frameSeconds * 6f), smoothProgress, progress);\n"
        "            // §line-glide: the water end walks between the server's block centres (the fly's drift, a strip)\n"
        "            // instead of jumping; a fresh cast, or anything six blocks off, snaps\n"
        "            net.minecraft.world.phys.Vec3 tc = new net.minecraft.world.phys.Vec3(target.getX() + 0.5, target.getY(), target.getZ() + 0.5);\n"
        "            shownEnd = shownEnd == null || shownEnd.distanceToSqr(tc) > 36.0 ? tc : shownEnd.lerp(tc, Math.min(1f, frameSeconds * 4f));\n", "glide")
    lr = j(J, "client/LineRenderer.java")
    sub(lr, "            double fdx = state.target.getX() + 0.5 - player.getX(), fdz = state.target.getZ() + 0.5 - player.getZ();",
        "            double fdx = state.shownEnd.x - player.getX(), fdz = state.shownEnd.z - player.getZ();   // §line-glide", "renderer heading")
    sub(lr, "        BlockPos t = state.target;\n        Vec3 water = new Vec3(t.getX() + 0.5, t.getY() + 0.95 + bob, t.getZ() + 0.5);\n",
        "        BlockPos t = state.target;\n        Vec3 e = state.shownEnd != null ? state.shownEnd : new Vec3(t.getX() + 0.5, t.getY(), t.getZ() + 0.5);   // §line-glide\n        Vec3 water = new Vec3(e.x, e.y + 0.95 + bob, e.z);\n", "renderer end")
    # the strike bar's fly label goes with the set
    ft = j(J, "client/FloatTimingClient.java")
    sub(ft, 'Component label = Component.translatable(FlyCastClient.flyHeld() ? "hud.riverfishing.fly_set" : "hud.riverfishing.strike_timing");   // §fly-2',
        'Component label = Component.translatable("hud.riverfishing.strike_timing");', "label")
    for code in ("en_us", "ru_ru", "uk_ua"):
        p = j(LANG, code + ".json"); d = json.loads(rd(p), object_pairs_hook=OrderedDict); ch = False
        for k in ("message.riverfishing.fly_too_fast", "message.riverfishing.fly_too_slow", "hud.riverfishing.fly_set"):
            if d.pop(k, None) is not None: ch = True
        if ch: wr(p, json.dumps(d, ensure_ascii=False, indent=2) + "\n")
    for rel, (a, b, new, io_old, io_new) in WIKI.items():
        p = j(rel); s = rd(p)
        if a in s:
            i = s.index(a); k = s.index(b, i); s = s[:i] + new + s[k:]
        s = s.replace(io_old, io_new)
        wr(p, s)
    p = j("docs/patchnotes/0.10.0.md"); s = rd(p)
    s = s.replace("- **The take says SET.** The strike bar is labelled, and the green is wider.\n",
                  "- **The take hooks itself.** No strike bar on the fly rod: the fish turns on the hook and you are straight into the fight.\n")
    wr(p, s)
    print("  patched", os.path.basename(tree.rstrip("/\\")))


for t in (sys.argv[1:] or TREES): run(t)
