# -*- coding: utf-8 -*-
"""§strike-tune: the hookset gauge is the last HUD element still wearing the 2023 grey box.

    py -X utf8 tools/patches/p_striketune.py <root> [1211|1201|26]

Reported, testing 26.1.2: "индикатор подсечки старый". It is — and on every version, not just that one:
FloatTimingClient has not been touched since the sources moved into :common, while everything drawn
near it was restyled. It is four flat `fill` rectangles and a centred string, sitting two pixels above
where the cast gauge draws its brass frame, so the two instruments that appear at the same moment of the
same cast look like they came out of different mods.

The fix reuses the cast gauge's own sheet — textures/gui/cast_bar.png, frame at (0,0), the 112x8 recess
it leaves at (4,4) — so the strike window is read on the same instrument the cast was aimed with, and no
new art is needed. The zones stay green and orange because those are the two things the bar is TELLING
you, and the needle keeps its own bright colour so it reads against both.

The mini-game itself is untouched: the same triangle wave, the same window, the same zones off the same
packet. This is the drawing, and only the drawing.
"""
import io, os, sys

ROOT = sys.argv[1]
D = sys.argv[2] if len(sys.argv) > 2 else "1211"
P = os.path.join(ROOT, "common/src/main/java/com/riverfishing/client/FloatTimingClient.java")

RL = "net.minecraft.resources.Identifier" if D == "26" else "net.minecraft.resources.ResourceLocation"


def blit(x, y, w, h, u, v):
    """One dialect of GuiGraphics.blit against a 128x48 sheet."""
    if D == "26":
        return ("g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, BAR, "
                "%s, %s, %sf, %sf, %s, %s, 128, 48);" % (x, y, u, v, w, h))
    return "g.blit(BAR, %s, %s, %s, %s, %sf, %sf, %s, %s, 128, 48);" % (x, y, w, h, u, v, w, h)


CENTRED = ('g.text(mc.font, label, screenW / 2 - mc.font.width(label) / 2, ly, 0xFFF0E6CD, true);'
           if D == "26" else
           'g.drawCenteredString(mc.font, label, screenW / 2, ly, 0xFFF0E6CD);')

s = io.open(P, encoding="utf-8").read()
if "strike-tune" in s:
    print("  already patched")
    sys.exit(0)

# ---- the sheet, beside the state it is drawn from ------------------------------------------------
old = "    private FloatTimingClient() {}"
assert old in s, "constructor moved"
s = s.replace(old, """    /**
     * §strike-tune: the cast gauge's sheet. The same brass frame, because this is the same instrument
     * one moment later — the rod was aimed on it, and now the strike window is read on it.
     */
    private static final %s BAR =
            com.riverfishing.RiverFishing.id("textures/gui/cast_bar.png");

    private FloatTimingClient() {}""" % RL, 1)

# ---- the drawing ---------------------------------------------------------------------------------
old = s[s.index("        int barW = 160;"):s.rindex("    }\n}")]
assert old.strip().endswith(");"), "render body does not end where expected"
s = s.replace(old, """        // §strike-tune: the cast gauge's own geometry — frame 120x16, with a 112x8 recess at (4,4).
        final int FW = 120, FH = 16, TW = 112, TH = 8;
        int x = (screenW - FW) / 2, y = screenH - 70;
        int tx = x + 4, ty = y + 4;

        %s

        // The orange band is the 25%% hook, the green is the whole fish; green goes on top because
        // where they overlap the better answer is the one the player should be aiming at.
        int os = tx + (int) (orangeStart * TW), oe = tx + (int) (orangeEnd * TW);
        g.fill(os, ty, oe, ty + TH, 0xE0C8862E);
        int zs = tx + (int) (greenStart * TW), ze = tx + (int) (greenEnd * TW);
        g.fill(zs, ty, ze, ty + TH, 0xE05FA84E);

        // The needle, through the whole frame and a little past it, so it reads over either zone.
        int mx = tx + (int) (marker(t) * TW);
        g.fill(mx - 2, y - 2, mx + 3, y + FH + 2, 0xC0231A10);
        g.fill(mx - 1, y - 1, mx + 2, y + FH + 1, 0xFFFFE8A8);

        Component label = Component.translatable("hud.riverfishing.strike_timing");
        int ly = y - 12;
        %s
""" % (blit("x", "y", "FW", "FH", 0, 0), CENTRED), 1)

io.open(P, "w", encoding="utf-8", newline="\n").write(s)
print("  FloatTimingClient: the strike window is read on the cast gauge's frame")
print("done (%s)" % D)
