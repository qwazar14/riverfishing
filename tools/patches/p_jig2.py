# -*- coding: utf-8 -*-
"""§jig-2: one rhythm, not two. The winter rod's jig used the phase-one fly rhythm (tap on the ends, a miss
collapses the combo) while the fly cast moved to hold-and-release. The jig now follows the same rule as
the cast: HOLD use and the rod jigs on its own (a stop every 8 ticks, each one pulls the bite a little
closer), a LEFT-CLICK on a stop is an accent (the combo, a bigger pull), RELEASE is the pause (a small pull
of its own — the take often comes on the pause). Nothing can be failed by clicking.
Idempotent; run on each tree.  py tools/patches/p_jig2.py [tree ...]"""
import io, json, os, re, shutil, sys
from collections import OrderedDict

MAIN = r"C:/Users/Qwazar/VS Code Projects/fishing mod"
TREES = [MAIN, r"C:/Users/Qwazar/wt/rf1201", r"C:/Users/Qwazar/wt/rf26"]
J = "common/src/main/java/com/riverfishing/"
LANG = "common/src/main/resources/assets/riverfishing/lang"
sys.path.insert(0, os.path.join(MAIN, "tools", "patches"))
from p_fly2 import client_dialect   # the 26.x GUI dialect, one place


def rd(p): return io.open(p, encoding="utf-8").read()
def wr(p, s): io.open(p, "w", encoding="utf-8", newline="\n").write(s)


def sub(path, old, new, what):
    s = rd(path)
    if new in s: return
    assert old in s, what + " @ " + path
    wr(path, s.replace(old, new, 1))


def resub(path, pattern, new, what):
    s = rd(path)
    if new.strip()[:50] in s: return
    s2, n = re.subn(pattern, lambda m: new, s, count=1, flags=re.S)
    assert n == 1, what + " @ " + path
    wr(path, s2)


BEAT = '''    /**
     * A left-click while the rod works: on the cast a haul if the needle is on a stop that has not been
     * hauled yet — two metres more, at once; on the jig an accent on a stop — the combo grows and the bite
     * comes closer. Anywhere else it is nothing, and costs nothing. Returns whether it landed.
     */
    public static boolean beat(ServerPlayer sp, long now) {
        State s = STATES.get(sp.getUUID());
        if (s == null) return false;
        int period = s.mode == 1 ? JIG_PERIOD : PERIOD;
        float zone = s.mode == 1 ? JIG_ZONE_HALF : ZONE_HALF;
        long el = now - s.start;
        float m = marker(el, period);
        float off = Math.min(m, 1f - m);
        int st = (int) Math.floorDiv(el, period / 2L);
        boolean room = s.mode == 1 ? s.beats < s.maxBeats : lineOut(el, s.beats, s.maxBeats) < s.maxBeats;
        if (off <= zone && st != s.lastEnd && room) {
            s.beats++;
            s.lastEnd = st;
            send(sp, s, true);
            return true;
        }
        return false;
    }
'''

JIG = '''    // ---- §jig-2: the winter rod's jig on the same needle, by the same rule — hold, and accent on a stop ----
    public static final int JIG_PERIOD = 16, JIG_MAX = 8;
    public static final float JIG_ZONE_HALF = 0.22f;

    /** The hold began over the hole: the rod starts jigging and the client is told to draw it. */
    public static void beginJig(ServerPlayer sp, long now) {
        State s = new State();
        s.start = now;
        s.maxBeats = JIG_MAX;
        s.mode = 1;
        STATES.put(sp.getUUID(), s);
        send(sp, s, true);
    }

    public static boolean isJigging(ServerPlayer sp) {
        State s = STATES.get(sp.getUUID());
        return s != null && s.mode == 1;
    }

    /** The stroke the jig is on — a new number every stop; -1 when it is not jigging. */
    public static int jigStroke(ServerPlayer sp, long now) {
        State s = STATES.get(sp.getUUID());
        return s == null || s.mode != 1 ? -1 : (int) Math.floorDiv(now - s.start, JIG_PERIOD / 2L);
    }

    /** The accents landed so far — the combo. */
    public static int jigCombo(ServerPlayer sp) {
        State s = STATES.get(sp.getUUID());
        return s == null || s.mode != 1 ? 0 : s.beats;
    }

    private static void send(ServerPlayer sp, State s, boolean active) {
        ModNetwork.toPlayer(sp, new FlyCastPacket(active, s.start, s.mode == 1 ? JIG_PERIOD : PERIOD,
                s.mode == 1 ? JIG_ZONE_HALF : ZONE_HALF, s.beats, s.maxBeats, s.openLoop, (byte) s.lastEnd, (byte) s.mode));
    }
}
'''

ICE = '''    /**
     * §jig-2: the jig is a hold. Every stop the rod makes on its own pulls the bite a little closer; an
     * accent (a left-click on a stop) pulls harder and grows the combo; the pause (letting go) pulls once
     * more — the take often comes on the pause. Nothing here pushes the bite away.
     */
    private static void iceStroke(ServerPlayer sp, ServerLevel level, FishingSession session, long now, boolean accent) {
        int combo = FlyCast.jigCombo(sp);
        if (accent) session.jigBest = Math.max(session.jigBest, combo);   // §progression
        session.lastJigTick = now;
        if (session.biteAtTick > now) {
            session.biteAtTick = Math.max(now + 10, session.biteAtTick - (accent ? 20 + 6L * combo : 8 + 2L * combo));
        }
        level.playSound(null, session.target, SoundEvents.FISHING_BOBBER_RETRIEVE, SoundSource.PLAYERS,
                accent ? 0.4f : 0.22f, accent ? 1.3f + 0.06f * combo : 1.0f);
        level.sendParticles(ParticleTypes.SPLASH, session.target.getX() + 0.5, session.target.getY() + 1.0,
                session.target.getZ() + 0.5, accent ? 3 + combo / 2 : 1, 0.1, 0.02, 0.1, 0.02);
    }

    /** §jig-2: the server tick while the winter rod is held over the hole — the strokes the rod makes on its own. */
    private static void iceJigTick(ServerPlayer sp, ServerLevel level, FishingSession session, long now) {
        if (!FlyCast.isJigging(sp)) {
            FlyCast.beginJig(sp, now);
            session.jigStroke = 0;
            return;
        }
        int st = FlyCast.jigStroke(sp, now);
        if (st != session.jigStroke) {
            session.jigStroke = st;
            iceStroke(sp, level, session, now, false);
        }
    }

    /** §jig-2: the hold let go — the pause. The gauge comes down and the bite comes a step closer. */
    private static void iceJigStop(ServerPlayer sp, FishingSession session) {
        FlyCast.cancel(sp);
        session.jigStroke = -1;
        long now = sp.serverLevel().getGameTime();
        if (session.biteAtTick > now) session.biteAtTick = Math.max(now + 10, session.biteAtTick - 10);
    }

'''

WIKI = {
    "docs/wiki/ice-fishing.md": ("### Jigging\n", "### The take\n", """### Jigging

While you are waiting, **hold right-click and the rod jigs on its own** — the same rule as the [fly cast](fly-fishing.md#the-cast). A needle sweeps a bar at the bottom of the screen between two stops, **▲ the lift** on the left and **▼ the drop** on the right, a stop every 0.4 s, and every stop the rod makes pulls the bite a little closer.

| You | The gauge | The fish |
|---|---|---|
| Hold | The needle sweeps, a soft click on every stop | Each stroke pulls the bite **8 ticks + 2 per accent** closer |
| **Left-click as the needle touches a stop** — an accent | The gauge punches, a pip lights, the note climbs, the combo counts (up to 8) | The bite is pulled **20 ticks + 6 per accent** closer |
| Let go — the pause | The gauge comes down | The bite comes **10 ticks** closer once; the take often comes on the pause |

A left-click anywhere else does nothing — there is no way to jerk the mormyshka by clicking. The bite can never be dragged closer than 10 ticks away. Splash particles at the hole grow with the combo. When the nod finally twitches, the needle gives way to the strike bar.

"""),
    "docs/wiki/ru/ice-fishing.md": ("### Игра мормышкой\n", "### Поклёвка\n", """### Игра мормышкой

Пока вы ждёте, **держите ПКМ — и удочка играет мормышкой сама**, по тому же правилу, что и [нахлыстовый заброс](fly-fishing.md#заброс). Внизу экрана стрелка ходит по шкале между двумя остановками, **▲ подъём** слева и **▼ сброс** справа, остановка каждые 0,4 с, и каждая остановка немного приближает поклёвку.

| Вы | Шкала | Рыба |
|---|---|---|
| Держите | Стрелка ходит, тихий щелчок на каждой остановке | Каждый взмах приближает поклёвку на **8 тиков + 2 за каждый акцент** |
| **ЛКМ в момент, когда стрелка касается остановки** — акцент | Шкала вздрагивает, загорается точка, нота растёт, счёт комбо идёт (до 8) | Поклёвка приближается на **20 тиков + 6 за каждый акцент** |
| Отпустили — пауза | Шкала опускается | Поклёвка приближается на **10 тиков** один раз; часто берут именно на паузе |

ЛКМ в любой другой момент ничего не делает — дёрнуть мормышку кликом нельзя. Ближе чем на 10 тиков поклёвку подтянуть нельзя. Брызги у лунки растут с комбо. Когда кивок наконец дрогнет, стрелку сменяет шкала подсечки.

"""),
    "docs/wiki/uk/ice-fishing.md": ("### Гра мормишкою\n", "### Поклівка\n", """### Гра мормишкою

Поки ви чекаєте, **тримайте ПКМ — і вудка грає мормишкою сама**, за тим самим правилом, що й [нахлистовий закид](fly-fishing.md#закид). Унизу екрана стрілка ходить шкалою між двома зупинками, **▲ підйом** ліворуч і **▼ скид** праворуч, зупинка кожні 0,4 с, і кожна зупинка трохи наближає поклівку.

| Ви | Шкала | Риба |
|---|---|---|
| Тримаєте | Стрілка ходить, тихий клац на кожній зупинці | Кожен змах наближає поклівку на **8 тіків + 2 за кожен акцент** |
| **ЛКМ у мить, коли стрілка торкається зупинки** — акцент | Шкала здригається, загоряється точка, нота росте, лік комбо йде (до 8) | Поклівка наближається на **20 тіків + 6 за кожен акцент** |
| Відпустили — пауза | Шкала опускається | Поклівка наближається на **10 тіків** один раз; часто беруть саме на паузі |

ЛКМ будь-якої іншої миті нічого не робить — сіпнути мормишку кліком не вийде. Ближче ніж на 10 тіків поклівку підтягнути не вийде. Бризки біля ополонки ростуть із комбо. Коли кивок нарешті сіпнеться, стрілку змінює шкала підсічки.

"""),
}
LANG_NEW = {
    "en_us": {"gui.riverfishing.jig_hint": "Hold — the rod jigs on its own · left-click on a stop: accent", "gui.riverfishing.jig_beats": "%s accents"},
    "ru_ru": {"gui.riverfishing.jig_hint": "Держите — удочка играет сама · ЛКМ на остановке: акцент", "gui.riverfishing.jig_beats": "акцентов: %s"},
    "uk_ua": {"gui.riverfishing.jig_hint": "Тримайте — вудка грає сама · ЛКМ на зупинці: акцент", "gui.riverfishing.jig_beats": "акцентів: %s"},
}


def run(tree):
    name = os.path.basename(tree.rstrip("/\\"))
    j = lambda *a: os.path.join(tree, *a)
    d26 = (lambda t: t.replace("sp.serverLevel()", "sp.level()")) if name == "rf26" else (lambda t: t)
    # ---- FlyCast (main edited by anchors, then copied) ----
    if tree == MAIN:
        fc = j(J, "fishing/FlyCast.java")
        resub(fc, r"    /\*\*\n     \* A left-click while the rod false-casts:.*?\n    public static void beat\(ServerPlayer sp, long now\) \{.*?\n    \}\n", BEAT, "beat")
        resub(fc, r"    // ---- §ice-rhythm: the winter rod's jig on the same needle.*", JIG, "jig section")
        s = rd(fc)
        s = s.replace("        int lastEnd = -1;   // jig: the end the last good stroke landed on; cast: the stroke index of the last haul",
                      "        int lastEnd = -1;   // the stroke index of the last haul / accent")
        s = s.replace("        boolean openLoop;   // jig only: the rhythm collapsed", "        boolean openLoop;   // unused since §jig-2 — kept on the wire for the packet's shape")
        wr(fc, s)
    else:
        shutil.copyfile(os.path.join(MAIN, J, "fishing/FlyCast.java"), j(J, "fishing/FlyCast.java"))
    # ---- FlyCastClient (main by anchors, dialect copies) ----
    if tree == MAIN:
        cc = j(J, "client/FlyCastClient.java")
        sub(cc, "    private static ItemStack heldFlyRod(Minecraft mc) {\n        if (mc.player == null) return ItemStack.EMPTY;\n        ItemStack main = mc.player.getMainHandItem();\n        if (main.getItem() instanceof RodItem ri && ri.rodType() == RodType.FLY) return main;\n        ItemStack off = mc.player.getOffhandItem();\n        return off.getItem() instanceof RodItem ri2 && ri2.rodType() == RodType.FLY ? off : ItemStack.EMPTY;\n    }\n",
            "    private static ItemStack heldFlyRod(Minecraft mc) {\n        if (mc.player == null) return ItemStack.EMPTY;\n        ItemStack main = mc.player.getMainHandItem();\n        if (main.getItem() instanceof RodItem ri && ri.rodType() == RodType.FLY) return main;\n        ItemStack off = mc.player.getOffhandItem();\n        return off.getItem() instanceof RodItem ri2 && ri2.rodType() == RodType.FLY ? off : ItemStack.EMPTY;\n    }\n\n"
            "    /** §jig-2: the rod whose rhythm this is — the fly rod on the cast, the winter rod on the jig. */\n"
            "    private static ItemStack heldRhythmRod(Minecraft mc) {\n        if (mode == 1) {\n            if (mc.player == null) return ItemStack.EMPTY;\n            ItemStack main = mc.player.getMainHandItem();\n            if (main.getItem() instanceof RodItem ri && ri.rodType() == RodType.WINTER) return main;\n            ItemStack off = mc.player.getOffhandItem();\n            return off.getItem() instanceof RodItem ri2 && ri2.rodType() == RodType.WINTER ? off : ItemStack.EMPTY;\n        }\n        return heldFlyRod(mc);\n    }\n", "rhythm rod")
        sub(cc, "        if (mode == 1) return;   // §ice-rhythm: the jig's beats are the clicks the server already sees\n        if (heldFlyRod(mc).isEmpty() || (mode == 0 && !mc.player.isUsingItem())) {",
            "        if (heldRhythmRod(mc).isEmpty() || (mode != 2 && !mc.player.isUsingItem())) {", "tick guard")
        sub(cc, "        // the whoosh: the rod reaching a stop, on the client's own clock\n        if (mode == 0 && mc.level != null) {\n            int st = (int) Math.floorDiv(mc.level.getGameTime() - startTick, (long) Math.max(1, period / 2));\n            if (st != lastStroke) {\n                if (lastStroke >= 0) mc.player.playSound(SoundEvents.FISHING_BOBBER_THROW, 0.45f, st % 2 == 0 ? 1.1f : 1.35f);\n                lastStroke = st;\n            }\n        }\n",
            "        // the whoosh: the rod reaching a stop, on the client's own clock (the jig ticks instead of whooshing)\n        if (mode != 2 && mc.level != null) {\n            int st = (int) Math.floorDiv(mc.level.getGameTime() - startTick, (long) Math.max(1, period / 2));\n            if (st != lastStroke) {\n                if (lastStroke >= 0) {\n                    if (mode == 0) mc.player.playSound(SoundEvents.FISHING_BOBBER_THROW, 0.45f, st % 2 == 0 ? 1.1f : 1.35f);\n                }\n                lastStroke = st;\n            }\n        }\n", "whoosh")
        sub(cc, "        ItemStack rod = mode == 1 ? ItemStack.EMPTY : heldFlyRod(mc);\n        if (mode != 1 && rod.isEmpty()) return;\n",
            "        ItemStack rod = heldRhythmRod(mc);\n        if (rod.isEmpty()) return;\n", "render rod")
        sub(cc, "        if (mode == 1) {\n            int nextEnd = lastEnd < 0 ? -1 : 1 - lastEnd;\n            int aL = nextEnd == 1 ? 60 : stopA, aR = nextEnd == 0 ? 60 : stopA;\n            g.fill(tx, ty, tx + zw, ty + TH, (aL << 24) | stopRgb);\n            g.fill(tx + TW - zw, ty, tx + TW, ty + TH, (aR << 24) | stopRgb);\n",
            "        if (mode == 1) {   // §jig-2: both stops take an accent — the lift and the drop\n            g.fill(tx, ty, tx + zw, ty + TH, (stopA << 24) | stopRgb);\n            g.fill(tx + TW - zw, ty, tx + TW, ty + TH, (stopA << 24) | stopRgb);\n", "jig stops")
        sub(cc, "            if (beats == 0 && hitNanos < 0) {\n                g.drawCenteredString(mc.font, Component.translatable(\"gui.riverfishing.jig_hint\"), screenW / 2, y + FH + 8, 0xFFB8AE9A);\n            }\n",
            "            g.drawCenteredString(mc.font, Component.translatable(\"gui.riverfishing.jig_hint\"), screenW / 2, y + FH + 8, 0xFFB8AE9A);\n", "jig hint")
    else:
        wr(j(J, "client/FlyCastClient.java"), client_dialect(name, rd(os.path.join(MAIN, J, "client/FlyCastClient.java"))))
    # ---- FishingManager ----
    fm = j(J, "fishing/FishingManager.java")
    sub(fm, "        FlyCast.beginJig(sp, now);   // §ice-rhythm: the needle starts with the line down the hole\n", "", "beginJig at start")
    resub(fm, r"    /\*\*\n     \* §ice-rhythm: a jig click is a stop on the needle.*?\n    private static void iceJig\(ServerPlayer sp, ServerLevel level, FishingSession session, long now\) \{.*?\n    \}\n\n", d26(ICE), "iceJig")
    sub(fm, "                iceJig(sp, level, session, now);               // §ice-jig: work the mormyshka (attract), don't reel in\n",
        "                // §jig-2: the jig is a hold now — a bare click over the hole does nothing\n", "handleRodUse ice")
    sub(fm, "    public static boolean flyCalm(ServerPlayer sp) {\n        FishingSession s = SESSIONS.get(sp.getUUID());\n        return s != null && s.ctx != null && s.ctx.rod == RodType.FLY && !s.bitten && !s.fighting;\n    }\n",
        "    public static boolean flyCalm(ServerPlayer sp) {\n        FishingSession s = SESSIONS.get(sp.getUUID());\n        return s != null && !s.bitten && !s.fighting && ((s.ctx != null && s.ctx.rod == RodType.FLY) || s.iceFishing);   // §jig-2: the winter rod holds too\n    }\n", "flyCalm")
    sub(fm, "    public static boolean flyTap(ServerPlayer sp) {\n        if (!flyCalm(sp)) return false;\n        FishingSession s = SESSIONS.get(sp.getUUID());\n",
        "    public static boolean flyTap(ServerPlayer sp) {\n        if (!flyCalm(sp)) return false;\n        FishingSession s = SESSIONS.get(sp.getUUID());\n        if (s.iceFishing) { iceJigStop(sp, s); return true; }   // §jig-2: letting go is the pause\n", "flyTap ice")
    sub(fm, "        if (FlyCast.isCasting(sp)) { FlyCast.beat(sp, now); return; }\n        if (!flyCalm(sp)) return;\n        flyMend(sp, level, SESSIONS.get(sp.getUUID()), now);\n",
        "        if (FlyCast.isCasting(sp)) { FlyCast.beat(sp, now); return; }\n        if (!flyCalm(sp)) return;\n        FishingSession s = SESSIONS.get(sp.getUUID());\n        if (s.iceFishing) {   // §jig-2: the accent\n            if (FlyCast.isJigging(sp) && FlyCast.beat(sp, now)) iceStroke(sp, level, s, now, true);\n            return;\n        }\n        flyMend(sp, level, s, now);\n", "flyBeat ice")
    sub(fm, "        if (session.ctx != null && session.ctx.rod == RodType.FLY && !session.bitten && !session.fighting && !session.flyPickedUp\n",
        "        // §jig-2: the winter rod held over the hole jigs on its own\n        if (session.iceFishing && !session.bitten && !session.fighting && sp.isUsingItem()\n                && sp.getUseItem().getItem() instanceof RodItem wr && wr.rodType() == RodType.WINTER) {\n            iceJigTick(sp, level, session, now);\n        } else if (FlyCast.isJigging(sp)) {\n            iceJigStop(sp, session);   // the hold ended without a release we saw (a slot switch)\n        }\n        if (session.ctx != null && session.ctx.rod == RodType.FLY && !session.bitten && !session.fighting && !session.flyPickedUp\n", "tick jig")
    sub(j(J, "fishing/FishingSession.java"), "    public int jigBest;        // §progression: the best jig combo of this session\n",
        "    public int jigBest;        // §progression: the best jig combo of this session\n    public int jigStroke = -1; // §jig-2: the last stroke the held rod made on its own\n", "session field")
    # ---- RodItem: the winter rod holds like the fly rod ----
    sub(j(J, "item/RodItem.java"), "        if (rodType == RodType.FLY && lineOut) {", "        if ((rodType == RodType.FLY || rodType == RodType.WINTER) && lineOut) {   // §jig-2", "rod hold")
    # ---- lang ----
    for code, entries in LANG_NEW.items():
        p = j(LANG, code + ".json"); d = json.loads(rd(p), object_pairs_hook=OrderedDict)
        ch = False
        for k, v in entries.items():
            if d.get(k) != v: d[k] = v; ch = True
        if ch: wr(p, json.dumps(d, ensure_ascii=False, indent=2) + "\n")
    # ---- wiki ----
    for rel, (a, b, new) in WIKI.items():
        p = j(rel); s = rd(p)
        if new[:40] in s: continue
        i = s.index(a); k = s.index(b, i)
        wr(p, s[:i] + new + s[k:])
    print("  patched", name)


for t in (sys.argv[1:] or TREES): run(t)
