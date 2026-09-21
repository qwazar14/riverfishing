# -*- coding: utf-8 -*-
"""§fight-bar revert: the vanilla ServerBossEvent comes back exactly as it was before p_fight_bar.py, the
drawn bar (FightBarHud, its sheet, its generator, its design page) goes, the pump/reel cue returns under the
crosshair. The blocks are lifted from the pre-fight-bar commit of each tree, so nothing is retyped.
    py tools/patches/p_fight_bar_revert.py"""
import io, json, os, re, subprocess
from collections import OrderedDict

TREES = {r"C:/Users/Qwazar/VS Code Projects/fishing mod": "6b2da163", r"C:/Users/Qwazar/wt/rf1201": "68ed5523", r"C:/Users/Qwazar/wt/rf26": "1cabe7e1"}
FM = "common/src/main/java/com/riverfishing/fishing/FishingManager.java"
FS = "common/src/main/java/com/riverfishing/fishing/FishingSession.java"
CH = "common/src/main/java/com/riverfishing/client/ClientHud.java"
LANG = "common/src/main/resources/assets/riverfishing/lang"


def rd(p): return io.open(p, encoding="utf-8").read()
def wr(p, s): io.open(p, "w", encoding="utf-8", newline="\n").write(s)
def old(tree, rev, path): return subprocess.run(["git", "-C", tree, "show", rev + ":" + path], capture_output=True, text=True, encoding="utf-8").stdout.replace("\r\n", "\n")


def between(s, a, b):
    i = s.index(a) + len(a); k = s.index(b, i)
    return s[i:k]


def replace_between(s, a, b, inner):
    i = s.index(a) + len(a); k = s.index(b, i)
    return s[:i] + inner + s[k:]


def run(tree, rev):
    name = os.path.basename(tree.rstrip("/\\"))
    j = lambda *a: os.path.join(tree, *a)
    o = old(tree, rev, FM); s = rd(j(FM))
    if "session.bossBar" not in s:
        # imports
        s = s.replace("import net.minecraft.server.level.ServerPlayer;\n", "import net.minecraft.server.level.ServerBossEvent;\nimport net.minecraft.server.level.ServerPlayer;\n", 1)
        s = s.replace("import net.minecraft.world.InteractionHand;\n", "import net.minecraft.world.BossEvent;\nimport net.minecraft.world.InteractionHand;\n", 1)
        assert "import net.minecraft.world.BossEvent;" in s and "ServerBossEvent;" in s, "imports"
        # clear(): from SESSIONS.remove(uuid) to the detach javadoc
        a, b = "        FishingSession session = SESSIONS.remove(uuid);\n", "    /** Detach a player's waiting bottom-rod session"
        s = replace_between(s, a, b, between(o, a, b))
        # detach
        a, b = "            return null;\n        }\n", "        SESSIONS.remove(sp.getUUID());\n        ModNetwork.toTracking(sp, new LineSyncPacket(sp.getId(), false, null, 0f, 0, (byte) 0)); // line now lives on the pod"
        s = replace_between(s, a, b, between(o, a, b))
        # hookUp constructor: between the fight-mystery comment and the hook-wear comment
        a, b = "        // §fight-mystery: NO species name during the fight — you learn what it was when you land it.\n", "        // Hooking a fish wears the line a little and dulls the hook (§3.8).\n"
        s = s.replace(a + "        // §fight-bar: no vanilla boss bar either — the client draws its own off the line sync (FightBarHud).\n", a, 1)
        s = replace_between(s, a, b, between(o, a, b))
        # the snag/boot fight constructor: after fightPattern = "steady" up to the splash sound
        a, b = '        session.fightPattern = "steady";\n', "        level.playSound(null, session.target, SoundEvents.FISHING_BOBBER_SPLASH, SoundSource.PLAYERS, 0.8f, 0.7f);\n    }\n"
        s = replace_between(s, a, b, between(o, a, b))
        # the fight update + spectators: before the landing-net comment, after the creak block
        b = "        // §co-op (0.5.0): the landing net"
        a = "                    SoundSource.PLAYERS, 0.8f, 1.0f);\n        }\n"
        blk = between(o, a, b)
        assert "session.bossBar.setProgress" in blk, "update block"
        i = s.index(b); k = s.rindex(a, 0, i) + len(a)
        s = s[:k] + blk + s[i:]
        # endSession
        a = "        if (session.iceFishing) FlyCast.cancel(sp);   // §ice-rhythm: the needle goes with the line\n"
        b = "        SESSIONS.remove(sp.getUUID());\n        // Clear the line for everyone" if "        SESSIONS.remove(sp.getUUID());\n        // Clear the line for everyone" in s else "        SESSIONS.remove(sp.getUUID());\n        // §rod-layers: the line is back in"
        keep = between(s, a, b)   # the §fly-2 driftOff line lives here now
        s = replace_between(s, a, b, keep + between(o, a, b))
        assert s.count("session.bossBar") >= 12, "bossBar count %d" % s.count("session.bossBar")
        wr(j(FM), s)
    fs = rd(j(FS))
    if "bossBar" not in fs:
        fs = fs.replace("import net.minecraft.server.level.ServerPlayer;\n", "import net.minecraft.server.level.ServerBossEvent;\nimport net.minecraft.server.level.ServerPlayer;\n", 1) if "import net.minecraft.server.level.ServerPlayer;" in fs else fs.replace("package com.riverfishing.fishing;\n\n", "package com.riverfishing.fishing;\n\nimport net.minecraft.server.level.ServerBossEvent;\n", 1)
        fs = fs.replace("    public int barState = -1;", "    public int barState = -1;\n    public ServerBossEvent bossBar;", 1)
        assert "public ServerBossEvent bossBar;" in fs and "import net.minecraft.server.level.ServerBossEvent;" in fs, "session"
        wr(j(FS), fs)
    oc = old(tree, rev, CH); c = rd(j(CH))
    if "renderPumpReel" not in c:
        c = c.replace("        FightBarHud.render(graphics, mc);   // §fight-bar: the frame, the water, the fish and the cue\n", "        renderPumpReel(graphics, mc);\n", 1)
        m = re.search(r"\n    /\*\*\n     \* §pump-reel \(0\.6\.0\).*?\n    \}\n(?=\n    /\*\* Cast power bar)", oc, re.S)
        assert m, "old pump reel"
        c = c.replace("\n    /** Cast power bar", m.group(0) + "\n    /** Cast power bar", 1)
        assert "renderPumpReel(graphics, mc);" in c and "private static void renderPumpReel" in c, "hud"
        wr(j(CH), c)
    for p in ("common/src/main/java/com/riverfishing/client/FightBarHud.java", "common/src/main/resources/assets/riverfishing/textures/gui/fight_bar.png",
              "tools/gen_fight_bar.py", "docs/design/fight-bar.md", "tools/patches/p_fight_bar.py"):
        if os.path.exists(j(p)): os.remove(j(p))
    for code in ("en_us", "ru_ru", "uk_ua"):
        p = j(LANG, code + ".json"); d = json.loads(rd(p), object_pairs_hook=OrderedDict)
        if d.pop("hud.riverfishing.tired", None) is not None: wr(p, json.dumps(d, ensure_ascii=False, indent=2) + "\n")
    print("  reverted", name)


for t, rev in TREES.items(): run(t, rev)
