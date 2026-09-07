# -*- coding: utf-8 -*-
"""§fight-bar: the fight's own boss bar, drawn by the mod, one per angler in sight.

The vanilla ServerBossEvent is gone from the server: the client already knows every nearby fight through
LineSyncPacket (progress, tension, running, fatigue), so FightBarHud draws a carved frame per fighting
player — the angler's own first, the neighbours' stacked under it — off textures/gui/fight_bar.png
(tools/gen_fight_bar.py, a placeholder sheet the artist repaints in place). The pump/reel cue moved onto
the bar's stone sign. Idempotent; run on each tree.  py tools/patches/p_fight_bar.py [tree ...]"""
import io, json, os, re, sys
from collections import OrderedDict

TREES = [r"C:/Users/Qwazar/VS Code Projects/fishing mod", r"C:/Users/Qwazar/wt/rf1201", r"C:/Users/Qwazar/wt/rf26"]
FM = "common/src/main/java/com/riverfishing/fishing/FishingManager.java"
FS = "common/src/main/java/com/riverfishing/fishing/FishingSession.java"
CH = "common/src/main/java/com/riverfishing/client/ClientHud.java"
HUD = "common/src/main/java/com/riverfishing/client/FightBarHud.java"
LANG = "common/src/main/resources/assets/riverfishing/lang"
TIRED = {"en_us": "tiring", "ru_ru": "выдыхается", "uk_ua": "видихається"}

TEMPLATE = r'''package com.riverfishing.client;

import com.riverfishing.RiverFishing;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * §fight-bar: the fight's own boss bar — a carved oak frame with the water in its window, the fish riding
 * the edge of what you have won back, the angler's name on the plate above and the cue on the stone sign
 * below. One per angler fighting in sight (the line sync already tells every nearby client about every
 * fight), your own on top, the neighbours' stacked under it. Drawn off textures/gui/fight_bar.png; the
 * sheet's regions are listed in tools/gen_fight_bar.py, and any of them can be repainted in place.
 */
public final class FightBarHud {
    private static final ResourceLocation TEX = RiverFishing.id("textures/gui/fight_bar.png");
    private static final int SW = 256, SH = 96;                    // the sheet
    private static final int FW = 256, FH = 56;                    // the frame
    private static final int WX = 28, WY = 20, WW = 200, WH = 20;  // the window
    private static final int SIGN_W = 88, SIGN_H = 20;
    private static final int GAP = 6;

    private FightBarHud() {}

    public static void render(GuiGraphics g, Minecraft mc) {
        if (mc.player == null || mc.level == null || HIDE_GUI) return;
        List<Map.Entry<Integer, ClientLineState.Line>> fights = new ArrayList<>();
        for (Map.Entry<Integer, ClientLineState.Line> e : ClientLineState.lines().entrySet()) {
            if (e.getValue().fighting) fights.add(e);
        }
        if (fights.isEmpty()) return;
        final int me = mc.player.getId();
        fights.sort((a, b) -> a.getKey() == me ? -1 : b.getKey() == me ? 1 : Integer.compare(a.getKey(), b.getKey()));
        int cx = mc.getWindow().getGuiScaledWidth() / 2;
        int limit = mc.getWindow().getGuiScaledHeight() / 2;
        long t = mc.level.getGameTime();
        int y = 4;
        for (Map.Entry<Integer, ClientLineState.Line> e : fights) {
            drawOne(g, mc, e.getKey(), e.getValue(), cx - FW / 2, y, t);
            y += FH + SIGN_H + GAP;
            if (y > limit) break;   // the top half of the screen is as much as the fights may take
        }
    }

    private static void drawOne(GuiGraphics g, Minecraft mc, int playerId, ClientLineState.Line l, int x, int y, long t) {
        int wx = x + WX, wy = y + WY;
        int fill = Mth.clamp(Math.round(l.smoothProgress * WW), 0, WW);
        // the water: the wave scrolls a pixel a tick under everything, the deep stands from the fill's edge on
        g.enableScissor(wx, wy, wx + WW, wy + WH);
        int scroll = (int) (t % 40);
        for (int px = wx - 40 - scroll; px < wx + WW; px += 40) blit(g, px, wy, 0, 56, 40, 20);
        for (int px = wx + fill; px < wx + WW; px += 40) blit(g, px, wy, 40, 56, Math.min(40, wx + WW - px), 20);
        float ten = l.smoothTension;
        if (ten > 0.5f) {   // the line's break-risk, as a red that rises through the water you hold
            int a = (int) (Math.min(1f, (ten - 0.5f) * 2f) * 150);
            g.fill(wx, wy, wx + Math.max(1, fill), wy + WH, (a << 24) | 0xE03A2A);
        }
        g.disableScissor();
        blit(g, x, y, 0, 0, FW, FH);
        // the fish rides the fill's edge, and beats its tail while it runs
        int frame = l.running && (t / 4) % 2 == 0 ? 104 : 80;
        int fx = wx + Mth.clamp(fill - 12, 0, WW - 24);
        int fy = wy + 2 + (int) Math.round(Math.sin(t * 0.35) * (l.running ? 1.5 : 0.5));
        blit(g, fx, fy, frame, 56, 24, 16);
        // the name plate
        Entity ent = mc.level.getEntity(playerId);
        String name = ent != null ? ent.getName().getString() : "?";
        if (l.fatigue > 0.7f) name = name + " · " + I18n.get("hud.riverfishing.tired");
        centred(g, mc, name, x + FW / 2, y + 3, 0xFFEAD9B0, true);
        // the sign: the cue
        int sx = x + (FW - SIGN_W) / 2, sy = y + FH;
        blit(g, sx, sy, 0, 76, SIGN_W, SIGN_H);
        String key; int col;
        if (ten > 0.85f) { key = "hud.riverfishing.drag_now"; col = 0xFF8E1A10; }
        else if (l.running) { key = "hud.riverfishing.ease"; col = 0xFF7A4E0A; }
        else { key = "hud.riverfishing.reel"; col = 0xFF1E4A1A; }
        centred(g, mc, I18n.get(key), x + FW / 2, sy + 6, col, false);
    }

    private static void centred(GuiGraphics g, Minecraft mc, String s, int cx, int y, int color, boolean shadow) {
        TEXT_CALL;
    }

    private static void blit(GuiGraphics g, int x, int y, int u, int v, int w, int h) {
        BLIT_CALL;
    }
}
'''


def dialect(name):
    s = TEMPLATE
    if name == "rf26":
        s = s.replace("import net.minecraft.client.gui.GuiGraphics;", "import net.minecraft.client.gui.GuiGraphicsExtractor;")
        s = s.replace("GuiGraphics g", "GuiGraphicsExtractor g")
        s = s.replace("net.minecraft.resources.ResourceLocation", "net.minecraft.resources.Identifier").replace("ResourceLocation TEX", "Identifier TEX")
        s = s.replace("        if (mc.player == null || mc.level == null || HIDE_GUI) return;\n",
                      "        // §26.2: Options.hideGui moved onto the Hud itself (mc.gui.hud.isHidden()).\n"
                      "        //? if <26.2 {\n"
                      "        if (mc.player == null || mc.level == null || mc.options.hideGui) return;\n"
                      "        //?} else {\n"
                      "        /*if (mc.player == null || mc.level == null || mc.gui.hud.isHidden()) return;\n"
                      "        *///?}\n")
        s = s.replace("TEXT_CALL;", "g.text(mc.font, s, cx - mc.font.width(s) / 2, y, color, shadow);")
        s = s.replace("BLIT_CALL;", "g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, TEX, x, y, (float) u, (float) v, w, h, SW, SH);")
    else:
        s = s.replace("HIDE_GUI", "mc.options.hideGui")
        s = s.replace("TEXT_CALL;", "g.drawString(mc.font, s, cx - mc.font.width(s) / 2, y, color, shadow);")
        s = s.replace("BLIT_CALL;", "g.blit(TEX, x, y, w, h, (float) u, (float) v, w, h, SW, SH);")
    return s


def rd(p): return io.open(p, encoding="utf-8").read()
def wr(p, s): io.open(p, "w", encoding="utf-8", newline="\n").write(s)


def cut(s, pattern, what, flags=re.S):
    s2, n = re.subn(pattern, "", s, count=1, flags=flags)
    assert n == 1, what
    return s2


def run(tree):
    name = os.path.basename(tree.rstrip("/\\"))
    j = lambda *a: os.path.join(tree, *a)
    # ---- the server lets go of the vanilla bar ----
    fm = j(FM); s = rd(fm)
    if "session.bossBar" in s:
        s = s.replace("import net.minecraft.server.level.ServerBossEvent;\n", "").replace("import net.minecraft.world.BossEvent;\n", "")
        s = cut(s, r"\n[ \t]*if \((?:session != null && )?session\.bossBar != null\) \{\n[ \t]*session\.bossBar\.removeAllPlayers\(\);\n[ \t]*\}", "clear()")
        s = cut(s, r"\n[ \t]*if \(s\.bossBar != null\) \{\n[ \t]*s\.bossBar\.removeAllPlayers\(\);\n[ \t]*s\.bossBar = null;\n[ \t]*\}", "detach")
        for i in range(2):
            s = cut(s, r"\n(?:[ \t]*// §26\.x: ServerBossEvent[^\n]*)?\n?[ \t]*session\.bossBar = new ServerBossEvent\(.*?\);\n[ \t]*session\.bossBar\.setProgress\(0\.0f\);\n[ \t]*session\.bossBar\.addPlayer\(sp\);", "constructor %d" % i)
        s = cut(s, r"\n[ \t]*session\.bossBar\.setProgress\(\(float\) Mth\.clamp\(session\.landProgress, 0\.0, 1\.0\)\);.*?session\.bossBar\.addPlayer\(other\);\n[ \t]*\}\n[ \t]*\}\n[ \t]*\}", "fight update + spectators")
        s = cut(s, r"\n[ \t]*if \(session\.bossBar != null\) \{\n[ \t]*session\.bossBar\.removeAllPlayers\(\);\n[ \t]*session\.bossBar = null;\n[ \t]*\}", "endSession")
        assert "bossBar" not in s, "bossBar left in FishingManager"
        s = s.replace("        // §fight-mystery: NO species name during the fight — you learn what it was when you land it.\n",
                      "        // §fight-mystery: NO species name during the fight — you learn what it was when you land it.\n"
                      "        // §fight-bar: no vanilla boss bar either — the client draws its own off the line sync (FightBarHud).\n", 1)
        wr(fm, s)
    fs = j(FS); s = rd(fs)
    if "bossBar" in s:
        s = s.replace("import net.minecraft.server.level.ServerBossEvent;\n", "")
        s = re.sub(r"\n[ \t]*public ServerBossEvent bossBar;", "", s, count=1)
        assert "bossBar" not in s and "ServerBossEvent" not in s, "session"
        wr(fs, s)
    # ---- the client draws the bar; the pump/reel cue lives on its sign now ----
    ch = j(CH); s = rd(ch)
    if "FightBarHud.render" not in s:
        s = s.replace("        renderPumpReel(graphics, mc);\n", "        FightBarHud.render(graphics, mc);   // §fight-bar: the frame, the water, the fish and the cue\n", 1)
        s = cut(s, r"\n    /\*\*\n     \* §pump-reel \(0\.6\.0\).*?\n    \}\n(?=\n    /\*\* Cast power bar)", "renderPumpReel")
        assert "renderPumpReel" not in s, "cue left"
        wr(ch, s)
    wr(j(HUD), dialect(name))
    # ---- lang ----
    for code, text in TIRED.items():
        p = j(LANG, code + ".json"); d = json.loads(rd(p), object_pairs_hook=OrderedDict)
        if "hud.riverfishing.tired" in d: continue
        out = OrderedDict()
        for k, v in d.items():
            out[k] = v
            if k == "hud.riverfishing.drag_now": out["hud.riverfishing.tired"] = text
        wr(p, json.dumps(out, ensure_ascii=False, indent=2) + "\n")
    print("  patched", name)


for t in (sys.argv[1:] or TREES): run(t)
