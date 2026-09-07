# The fight bar — the sheet for the artist

`assets/riverfishing/textures/gui/fight_bar.png` is a 256×96 sheet. `tools/gen_fight_bar.py` writes a placeholder; the HUD (`client/FightBarHud.java`) only reads the regions below, so any of them can be repainted in place — nothing is measured from the pixels.

| Region | Position | Size | What it is |
|---|---|---|---|
| frame | (0, 0) | 256×56 | the carved frame; the **window** (28, 20)–(228, 40) must stay transparent, the water is drawn under it |
| plate | (78, 0) | 100×14 | the name plate, part of the frame; the angler's name is printed centred at y = 3 |
| wave | (0, 56) | 40×20 | the filled water, tiled left to right, scrolled one pixel a tick |
| deep | (40, 56) | 40×20 | the unfilled water, tiled from the fill's edge to the right |
| fish | (80, 56) | 24×16 | the fish that rides the fill's edge, head to the left |
| fish2 | (104, 56) | 24×16 | the same fish, tail the other way; the two alternate every 4 ticks while it runs |
| sign | (0, 76) | 88×20 | the stone plate under the frame; the cue is printed centred at y = 6 |

On screen the frame is 256 GUI pixels wide (twice a vanilla boss bar), centred at the top; the sign hangs 56 px under its top edge; a second angler's bar starts 82 px lower.

What the HUD adds over the sheet: a red tint over the filled water as the line's break-risk rises past half; *tiring* after the name when the fish's fatigue passes 0.7; the cue text in three colours (reel green, ease amber, drag red).

Bigger art: change `FW/FH/WX/WY/WW/WH/SIGN_W/SIGN_H/SW/SH` at the top of `FightBarHud.java` to the new layout and keep the same region order.
