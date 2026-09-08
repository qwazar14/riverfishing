# -*- coding: utf-8 -*-
"""§fly-3 (0.10.0): fly fishing rebuilt to the written spec — easy to learn, hard to master.

  CAST → DRIFT → STRIKE → FIGHT, one class each (FlySession / FlyCast / FlyDrift / FlyStrike / FlyRises),
  and FishingManager keeps only the glue.

  CAST is the right button and nothing else: hold and the rod false-casts (a full swing every 30 ticks,
  +3 m a cycle from 6), let go within three ticks of the forward stop for a clean turnover at the full
  distance, near it for four fifths, with the rod behind you for half and a slap. No clicking for metres.
  DRIFT: drag rises 2 per ten ticks of current, over 70 the fly skates and leaves a wake, at 100 the line
  is straight. LMB mends (the first two are quiet, the rest are heard); RMB strips two blocks (a streamer
  wants 10–25 ticks between them); two quick RMB picks the line up. No numbers on screen.
  STRIKE is two acts: the fish is SEEN coming 10–20 ticks out, then it takes and a 35-tick window opens
  with the button on the HUD — lift a dry or a nymph, strip-set a streamer. Wrong button still hooks, but
  weakly; early pulls it away most of the time; late and it spits. Weak hooks can throw on a jump.
  FIGHT is the mod's own, plus a fly fish that jumps every 80–150 ticks (winding into one rips the hook).

Idempotent; run on each tree.  py tools/patches/p_fly3.py [tree ...]"""
import io, json, os, re, shutil, sys
from collections import OrderedDict

MAIN = r"C:/Users/Qwazar/VS Code Projects/fishing mod"
TREES = [MAIN, r"C:/Users/Qwazar/wt/rf1201", r"C:/Users/Qwazar/wt/rf26"]
J = "common/src/main/java/com/riverfishing/"
LANG = "common/src/main/resources/assets/riverfishing/lang"
NEW_FILES = ["fishing/FlySession.java", "fishing/FlyCast.java", "fishing/FlyDrift.java",
             "fishing/FlyStrike.java", "fishing/FlyRises.java"]
sys.path.insert(0, os.path.join(MAIN, "tools", "patches"))
from p_fly2 import client_dialect   # the 26.x GUI dialect lives in one place


def rd(p): return io.open(p, encoding="utf-8").read()
def wr(p, s): io.open(p, "w", encoding="utf-8", newline="\n").write(s)


def sub(path, old, new, what):
    s = rd(path)
    if new in s: return
    assert s.count(old) == 1, "%s @ %s (%d)" % (what, path, s.count(old))
    wr(path, s.replace(old, new, 1))


def drop(path, old, what):
    s = rd(path)
    if old not in s: return
    assert s.count(old) == 1, "%s @ %s (%d)" % (what, path, s.count(old))
    wr(path, s.replace(old, "", 1))


# ---------------------------------------------------------------- the glue that replaces the old fly block
GLUE = '''    /**
     * §fly-3: what the delivery did to the water. A clean turnover puts the fly down without a sound; an
     * open loop lands it short; a pile slaps the surface and every fish within a few blocks heard it.
     * Then the drift begins, and from here the fly's own classes have it.
     */
    private static void flyLanded(ServerPlayer sp, FishingSession session, int quality) {
        if (session == null) return;
        ServerLevel level = sp.serverLevel();
        long now = level.getGameTime();
        session.fly = new FlySession(session.target,
                FlySession.Kind.of(session.ctx == null ? null : session.ctx.tied));
        BlockPos t = session.target;
        double cx = t.getX() + 0.5, cy = t.getY() + 1.0, cz = t.getZ() + 0.5;
        if (quality == FlyCast.PERFECT) {
            session.flyTight = true;   // §progression
            level.playSound(null, t, SoundEvents.FISHING_BOBBER_THROW, SoundSource.PLAYERS, 0.4f, 1.9f);
            level.sendParticles(ParticleTypes.FISHING, cx, cy, cz, 4, 0.15, 0.0, 0.15, 0.01);
            actionbar(sp, Component.translatable("message.riverfishing.fly_tight").withStyle(ChatFormatting.GREEN));
        } else if (quality == FlyCast.NORMAL) {
            level.playSound(null, t, SoundEvents.FISHING_BOBBER_SPLASH, SoundSource.PLAYERS, 0.4f, 1.4f);
            actionbar(sp, Component.translatable("message.riverfishing.fly_open").withStyle(ChatFormatting.YELLOW));
        } else {
            SpookTracker.onCastLanded(level, t, 0.18);
            level.playSound(null, t, SoundEvents.GENERIC_SPLASH, SoundSource.PLAYERS, 0.9f, 1.1f);
            level.sendParticles(ParticleTypes.SPLASH, cx, cy, cz, 18, 0.4, 0.1, 0.4, 0.2);
            actionbar(sp, Component.translatable("message.riverfishing.fly_pile").withStyle(ChatFormatting.RED));
        }
        FlyDrift.start(sp, level, session, now);
    }

    /**
     * §fly-3: the left click. On a fly rod it is the mend while the line drifts and the LIFT when a fish
     * has taken; over an ice hole it is the jig's accent. During the cast it is nothing at all — the
     * distance is the release's business and no amount of clicking changes it.
     */
    public static void flyBeat(ServerPlayer sp) {
        ServerLevel level = sp.serverLevel();
        long now = level.getGameTime();
        FishingSession s = SESSIONS.get(sp.getUUID());
        if (FlyCast.isJigging(sp)) {   // §jig-2: the accent
            if (s != null && s.iceFishing && !s.bitten && !s.fighting && FlyCast.jigAccent(sp, now)) {
                iceStroke(sp, level, s, now, true);
            }
            return;
        }
        if (FlyStrike.tryStrike(sp, FlyStrike.Input.LIFT)) return;
        if (s != null && s.fly != null && !s.fighting) FlyDrift.mend(sp, level, s, now);
    }

    /** §jig-2: a winter line down the hole with nothing biting — the state in which a click is a hold. */
    public static boolean winterCalm(ServerPlayer sp) {
        FishingSession s = SESSIONS.get(sp.getUUID());
        return s != null && s.iceFishing && !s.bitten && !s.fighting;
    }

    /** §jig-2: the hold over the hole let go — the pause. True when the press was ours. */
    public static boolean winterTap(ServerPlayer sp) {
        if (!winterCalm(sp)) return false;
        iceJigStop(sp, SESSIONS.get(sp.getUUID()));
        return true;
    }

    /** §fly-3: the player's live session — the fly classes drive their own state through it. */
    static FishingSession session(ServerPlayer sp) {
        return SESSIONS.get(sp.getUUID());
    }

    /** §fly-3: has this spot been frightened? Asked by the fly's own tick before a fish shows itself. */
    static boolean spookedNow(ServerLevel level, FishingSession session, long now) {
        return spooked(level, session, now);
    }

    /** §fly-3: a take that came to nothing still costs the fly, the way every missed strike does. */
    static void eatBaitPublic(ServerPlayer sp, FishingSession session) {
        eatBait(sp, session);
    }

    /** §fly-3: into the fight, once the strike has decided how well the hook is set. */
    static void flyHookUp(ServerPlayer sp, ServerLevel level, FishingSession session, long now) {
        hookUp(sp, level, session, now);
    }

'''

TICK_FLY = '''        // §fly-3: a fly line is its own loop — the drift carries it, the rise is watched for, the fish is
        // seen coming and the take opens its own window. None of the float flow below applies.
        if (session.fly != null) {
            if (session.ctx != null && session.biteAtTick > now && now % 300 == 0) reEvaluate(level, session, now);
            if (now % 20 == 0 && session.ctx != null && session.ctx.hatch != null) {
                session.ctx.hatch.particles(level, session.fly.spot);
            }
            FlyDrift.tick(level, sp, session, now);
            return;
        }
'''

FLY_JUMP = '''        // §fly-3: a fish on a fly jumps, and often — it is the picture the whole method is for. Winding
        // into one rips the hook out (reelPulse knows the window), and a hook the strike set badly can
        // simply be thrown here.
        if (session.flyFight && session.runTicksLeft == 0 && session.landProgress > 0.05
                && now >= session.jumpWindowEnd && now >= session.flyJumpAt) {
            session.flyJumpAt = now + 80 + random.nextInt(71);
            session.jumpWindowEnd = now + 15;
            session.tension += session.runTensionPulse * 0.8;
            level.playSound(null, session.target, SoundEvents.DOLPHIN_JUMP, SoundSource.PLAYERS, 0.9f, 1.05f);
            level.sendParticles(ParticleTypes.SPLASH, session.target.getX() + 0.5, session.target.getY() + 1.2,
                    session.target.getZ() + 0.5, 30, 0.45, 0.4, 0.45, 0.35);
            actionbar(sp, Component.translatable("message.riverfishing.fish_jumps").withStyle(ChatFormatting.RED));
            if (session.hookStrength == 0 && random.nextFloat() < 0.18f) {
                level.playSound(null, session.target, SoundEvents.FISHING_BOBBER_SPLASH, SoundSource.PLAYERS, 0.7f, 0.6f);
                endSession(sp, session);
                actionbar(sp, Component.translatable("message.riverfishing.fly_threw_hook").withStyle(ChatFormatting.RED));
                GuideNudge.failure(sp, session.rodClass, GuideNudge.MISSED);
                return;
            }
        }
'''

LANG_NEW = {
    "en_us": OrderedDict([
        ("message.riverfishing.fly_tight", "Clean turnover — the fly landed without a sound"),
        ("message.riverfishing.fly_open", "The loop opened — a little short"),
        ("message.riverfishing.fly_pile", "The line piled up and slapped the water"),
        ("message.riverfishing.fly_spat", "Too slow — it spat the fly"),
        ("message.riverfishing.fly_hook", "Hooked!"),
        ("message.riverfishing.fly_hook_solid", "Set clean — solid hook!"),
        ("message.riverfishing.fly_hook_weak", "Awkward set — the hook barely holds"),
        ("message.riverfishing.fly_threw_hook", "It threw the hook on the jump"),
        ("hud.riverfishing.fly_dead_drift", "Natural drift"),
        ("hud.riverfishing.fly_drag", "The line is pulling — left-click to mend"),
        ("hud.riverfishing.fly_straight", "Line's straight below — right-click twice to pick up"),
        ("hud.riverfishing.fly_on_fish", "On the fish"),
        ("hud.riverfishing.fly_approach", "Something's coming up…"),
        ("hud.riverfishing.fly_strike_lmb", "STRIKE! Left-click"),
        ("hud.riverfishing.fly_strike_rmb", "STRIKE! Right-click"),
        ("hud.riverfishing.fly_controls", "LMB mend · RMB strip · RMB twice to pick up"),
        ("gui.riverfishing.fly_hint", "Hold — the rod casts on its own · let go as it comes forward"),
        ("gui.riverfishing.fly_full", "All the line's in the air — let go on the green"),
    ]),
    "ru_ru": OrderedDict([
        ("message.riverfishing.fly_tight", "Петля развернулась чисто — мушка легла беззвучно"),
        ("message.riverfishing.fly_open", "Петля раскрылась — легло чуть ближе"),
        ("message.riverfishing.fly_pile", "Шнур лёг кучей и шлёпнул по воде"),
        ("message.riverfishing.fly_spat", "Слишком поздно — рыба выплюнула мушку"),
        ("message.riverfishing.fly_hook", "Есть!"),
        ("message.riverfishing.fly_hook_solid", "Подсечка точная — сидит намертво!"),
        ("message.riverfishing.fly_hook_weak", "Подсечка неловкая — крючок держится еле-еле"),
        ("message.riverfishing.fly_threw_hook", "На свече рыба сбросила крючок"),
        ("hud.riverfishing.fly_dead_drift", "Свободный проплыв"),
        ("hud.riverfishing.fly_drag", "Шнур тянет — ЛКМ: перекладка"),
        ("hud.riverfishing.fly_straight", "Шнур вытянулся — ПКМ дважды: поднять"),
        ("hud.riverfishing.fly_on_fish", "Над рыбой"),
        ("hud.riverfishing.fly_approach", "Кто-то поднимается…"),
        ("hud.riverfishing.fly_strike_lmb", "ПОДСЕКАЙ! ЛКМ"),
        ("hud.riverfishing.fly_strike_rmb", "ПОДСЕКАЙ! ПКМ"),
        ("hud.riverfishing.fly_controls", "ЛКМ перекладка · ПКМ подтяжка · ПКМ дважды — поднять"),
        ("gui.riverfishing.fly_hint", "Держите — удилище машет само · отпустите на движении вперёд"),
        ("gui.riverfishing.fly_full", "Весь шнур в воздухе — отпустите на зелёном"),
    ]),
    "uk_ua": OrderedDict([
        ("message.riverfishing.fly_tight", "Петля розгорнулася чисто — мушка лягла беззвучно"),
        ("message.riverfishing.fly_open", "Петля розкрилася — лягло трохи ближче"),
        ("message.riverfishing.fly_pile", "Шнур ліг купою й ляснув по воді"),
        ("message.riverfishing.fly_spat", "Запізно — риба виплюнула мушку"),
        ("message.riverfishing.fly_hook", "Є!"),
        ("message.riverfishing.fly_hook_solid", "Підсічка точна — сидить намертво!"),
        ("message.riverfishing.fly_hook_weak", "Підсічка незграбна — гачок ледве тримається"),
        ("message.riverfishing.fly_threw_hook", "На свічці риба скинула гачок"),
        ("hud.riverfishing.fly_dead_drift", "Вільний сплав"),
        ("hud.riverfishing.fly_drag", "Шнур тягне — ЛКМ: перекладання"),
        ("hud.riverfishing.fly_straight", "Шнур витягнувся — ПКМ двічі: підняти"),
        ("hud.riverfishing.fly_on_fish", "Над рибою"),
        ("hud.riverfishing.fly_approach", "Хтось піднімається…"),
        ("hud.riverfishing.fly_strike_lmb", "ПІДСІКАЙ! ЛКМ"),
        ("hud.riverfishing.fly_strike_rmb", "ПІДСІКАЙ! ПКМ"),
        ("hud.riverfishing.fly_controls", "ЛКМ перекладання · ПКМ підтяжка · ПКМ двічі — підняти"),
        ("gui.riverfishing.fly_hint", "Тримайте — вудилище махає само · відпустіть на русі вперед"),
        ("gui.riverfishing.fly_full", "Увесь шнур у повітрі — відпустіть на зеленому"),
    ]),
}
LANG_DROP = ["gui.riverfishing.fly_haul", "gui.riverfishing.fly_hauls", "hud.riverfishing.fly_set",
             "message.riverfishing.fly_drag", "message.riverfishing.fly_on_fish"]


def dialect(name, s):
    """26.x speaks a slightly different Minecraft."""
    if name != "rf26": return s
    return (s.replace("sp.serverLevel()", "sp.level()")
             .replace("net.minecraft.resources.ResourceLocation", "net.minecraft.resources.Identifier")
             .replace("ResourceLocation", "Identifier"))


def run(tree):
    name = os.path.basename(tree.rstrip("/\\"))
    j = lambda *a: os.path.join(tree, *a)
    # ---- the five fly classes, written whole ----
    if tree != MAIN:
        for rel in NEW_FILES:
            wr(j(J, rel), dialect(name, rd(os.path.join(MAIN, J, rel))))
        wr(j(J, "client/FlyCastClient.java"), client_dialect(name, rd(os.path.join(MAIN, J, "client/FlyCastClient.java"))))
    # ---- FishingSession ----
    fs = j(J, "fishing/FishingSession.java")
    sub(fs, "    public double flyReel;         // §fly-reel: how far the strips have brought the fly in, 0..1 of the cast\n",
        "    /** §fly-3: the fly cast's own state — null on every other rod. */\n    public FlySession fly;\n"
        "    /** §fly-3: 0 weak, 1 normal, 2 solid — what the strike earned; a weak hook can be thrown. */\n    public int hookStrength = 1;\n"
        "    /** §fly-3: the tick the fish on a fly may next come out of the water. */\n    public long flyJumpAt;\n"
        "    /** §fly-3: this fish was hooked on a fly — it jumps far more than a fish on any other rod. */\n    public boolean flyFight;\n", "session fly")
    s = rd(fs)
    for dead in ("    public int flyDrag;\n", "    public long flyDriftEnd;\n", "    public boolean flyStraight;\n",
                 "    public int flyMends;\n", "    public boolean flyDragWarned;\n",
                 "    public boolean flyOnRise;      // §fly-2: the fly is over a fish that has decided\n",
                 "    public boolean flyPickedUp;    // §fly-2: the hold took the line up — the release is a cast, not a strip\n",
                 "    public int flyDriftState = -1; // §fly-2: the last status sent to the HUD   // §fly: the one 'mend!' per drift has been said\n"):
        s = s.replace(dead, "")
    s = s.replace("    // §fly: the drift — the line bows across the current (drag 0..100), the drift's last tick,\n"
                  "    // whether it has come tight straight below the angler, and how many mends this drift has had.\n", "")
    wr(fs, s)
    # ---- FishingManager: the old fly block out, the glue in ----
    fm = j(J, "fishing/FishingManager.java"); s = rd(fm)
    if "§fly-3: what the delivery did to the water" not in s:
        sig = s.index("    private static void flyLanded(ServerPlayer sp, FishingSession session, int quality) {")
        start = s.rindex("    /**\n", 0, sig)
        end = s.index("    private static boolean isFlyRod(ItemStack stack) {")
        s = s[:start] + dialect(name, GLUE) + s[end:]
        wr(fm, s)
    # the tick: the fly owns its own loop, and the hold-to-pick-up is gone
    drop(fm, '''        // §fly-2: holding use on a calm fly line picks it up and goes straight into the false casts; the
        // release then delivers the next cast — one motion, the way it is done
''', "tick comment")
    drop(fm, '''        if (session.ctx != null && session.ctx.rod == RodType.FLY && !session.bitten && !session.fighting && !session.flyPickedUp
                && sp.isUsingItem() && isFlyRod(sp.getUseItem()) && sp.getTicksUsingItem() >= 6) {
            session.flyPickedUp = true;
            endSession(sp, session);
            FlyCast.begin(sp, now);
            return;
        }

''', "hold pickup")
    sub(fm, "        if (session.fighting) {\n            tickFight(sp, level, session, now);\n            return;\n        }\n",
        "        if (session.fighting) {\n            tickFight(sp, level, session, now);\n            return;\n        }\n\n" + TICK_FLY, "tick fly")
    drop(fm, "            if (session.ctx != null && session.ctx.rod == RodType.FLY) flyDrift(level, sp, session, now);   // §fly\n", "old drift call")
    _auto = ("                if (session.ctx != null && session.ctx.rod == RodType.FLY) {   // §fly-3: the take hooks itself — straight into the show\n"
             "                    FlyCast.driftOff(sp);\n"
             "                    hookUp(sp, level, session, now);\n"
             "                    return;\n"
             "                } else if")
    _s = rd(fm)
    if _auto in _s:
        wr(fm, _s.replace(_auto, "                if", 1))
    # handleRodUse: the fly's own routing, ahead of the generic strike branch
    sub(fm, "            } else if (session.bitten && now <= session.biteWindowEnd) {",
        "            } else if (session.fly != null) {\n"
        "                // §fly-3: on a fly rod the right button is the strip, and the strip-set when a fish has taken\n"
        "                if (!FlyStrike.tryStrike(sp, FlyStrike.Input.STRIP)) FlyDrift.strip(sp, level, session, now);\n"
        "            } else if (session.bitten && now <= session.biteWindowEnd) {", "rod use fly")
    drop(fm, "            } else if (session.ctx != null && session.ctx.rod == RodType.FLY) {\n                flyStrip(sp, level, session, now);             // §fly-2: a click that reached here is a strip\n", "old strip branch")
    # the line sync, the fight's start, the set's spray
    _vis_a = ("            } else if (session.ctx != null && session.ctx.rod == RodType.FLY) {\n"
              "                visProgress = (float) session.flyReel;   // §fly-reel")
    _vis_b = ("            } else if (session.ctx != null && session.ctx.rod == RodType.FLY) {\n"
              "                visProgress = session.fly != null ? (float) session.fly.reel : 0f;   // §fly-3")
    _vis_new = ("            } else if (session.fly != null) {\n"
                "                visProgress = (float) session.fly.reel;   // §fly-3")
    for _old in (_vis_a, _vis_b):
        if _old in rd(fm):
            sub(fm, _old, _vis_new, "vis progress")
            break
    sub(fm, "                : session.ctx != null && session.ctx.rod == RodType.FLY ? Mth.clamp(session.flyReel, 0.0, 0.85)   // §fly-reel: the fight starts where the fly was",
        "                : session.fly != null ? Mth.clamp(session.fly.reel, 0.0, 0.85)   // §fly-3: the fight starts where the fly was", "land progress")
    _spray_old = ("        if (session.ctx != null && session.ctx.rod == RodType.FLY) {\n"
                  "            session.showFishUntil = now + 16;\n"
                  "            net.minecraft.world.phys.Vec3 fa = flyAt(sp, session);   // §fly-reel: the spray where the fly is, not where it landed")
    _spray_new = ("        if (session.fly != null) {\n"
                  "            session.flyFight = true;   // §fly-3: a fish on a fly jumps\n"
                  "            session.flyJumpAt = now + 60 + random.nextInt(60);\n"
                  "            session.showFishUntil = now + 16;\n"
                  "            net.minecraft.world.phys.Vec3 fa = session.fly.flyAt(sp);   // §fly-3: the spray where the fly is")
    sub(fm, _spray_old, _spray_new, "set spray")
    # the fight's jumps
    sub(fm, '        if ("greyhounding".equals(session.fightPattern) && session.runTicksLeft == 0',
        FLY_JUMP + '        if ("greyhounding".equals(session.fightPattern) && session.runTicksLeft == 0', "fly jump")
    # endSession
    sub(fm, "        if (session.ctx != null && session.ctx.rod == RodType.FLY) FlyCast.driftOff(sp);   // §fly-2: the status line goes with it",
        "        if (session.fly != null) FlyCast.statusOff(sp);   // §fly-3: the status line goes with the line", "end session")
    sub(fm, "    private static void actionbar(ServerPlayer sp, Component message) {",
        "    static void actionbar(ServerPlayer sp, Component message) {   // §fly-3: the fly classes talk too", "actionbar")
    sub(fm, "    private static void endSession(ServerPlayer sp, FishingSession session) {",
        "    static void endSession(ServerPlayer sp, FishingSession session) {   // §fly-3", "endSession")
    # ---- RodItem: only the winter rod holds now; a fly line's click is a click ----
    ri = j(J, "item/RodItem.java")
    sub(ri, "        if ((rodType == RodType.FLY || rodType == RodType.WINTER) && lineOut) {   // §jig-2",
        "        if (rodType == RodType.WINTER && lineOut) {   // §jig-2: the jig is a hold; a fly line's click is a click (§fly-3)", "rod hold")
    sub(ri, "FishingManager.flyCalm(fsp)", "FishingManager.winterCalm(fsp)", "rod calm")
    sub(ri, "if (FishingManager.flyTap(sp))", "if (FishingManager.winterTap(sp))", "rod tap")
    # ---- lang ----
    for code, entries in LANG_NEW.items():
        p = j(LANG, code + ".json"); d = json.loads(rd(p), object_pairs_hook=OrderedDict)
        for k in LANG_DROP: d.pop(k, None)
        for k, v in entries.items(): d[k] = v
        wr(p, json.dumps(d, ensure_ascii=False, indent=2) + "\n")
    print("  patched", name)


for t in (sys.argv[1:] or TREES): run(t)
