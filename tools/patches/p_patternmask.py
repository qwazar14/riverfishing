# -*- coding: utf-8 -*-
"""§pattern-mask: the pattern index finally draws something — a marking, in the family's shape.

    py -X utf8 tools/patches/p_patternmask.py <root> [1211|1201|26]

Reported: "узоры не работают — на карточке пишутся, на рыбе не видны". True. §pattern gave every
carp and koi a number, twelve named families and twelve gems, and then painted an ordinary index as a
hue turn of 3–18° — nothing at all on an ordinary carp ("a perch is a perch"), and on a white kohaku
nothing anyone could see. Pattern.offset() was computed and read by nobody.

Now the family is a MASK (tools/gen_pattern_masks.py: hard-edged, cut to each sprite) drawn over the
fish in a marking colour — Pattern.marking(): a darker cut of the ground on a light fish, a paler one
on a dark fish, ghost pale, ember warm, aurora turned round the wheel — moved a notch by offset() and
turned a few degrees by the in-band index, so #237 and #238 are cousins. Plain draws nothing; a gem
draws nothing because it has already painted the whole fish.

  1.21.1 / 1.20.1   FishItemRenderer draws the mask model after the body at the same pose; its quad
                    carries tintindex 5, which FishTint answers with the marking. ClientModels lists
                    the 66 models so they are baked at all.
  26.x              stampIcon writes the family name as strings[1] and the marking as one more
                    custom_model_data colour; tools/wire_pattern_models.py made the definitions read
                    both. FishMorph.patternTint is the same function on every tree.
"""
import io, os, re, sys

ROOT = sys.argv[1]
D = sys.argv[2] if len(sys.argv) > 2 else "1211"
J = os.path.join(ROOT, "common/src/main/java/com/riverfishing")
RL = "Identifier" if D == "26" else "ResourceLocation"
GET_STR = (lambda t, k: '%s.getStringOr("%s", "")' % (t, k)) if D == "26" else (lambda t, k: '%s.getString("%s")' % (t, k))


def patch(rel, marker, edits):
    p = os.path.join(J, rel)
    s = io.open(p, encoding="utf-8").read()
    if marker in s:
        print("  %s: already patched" % rel); return
    for old, new in edits:
        assert old in s, "%s: anchor moved — %r" % (rel, old[:70])
        assert s.count(old) == 1, "%s: anchor not unique — %r" % (rel, old[:70])
        s = s.replace(old, new, 1)
    io.open(p, "w", encoding="utf-8", newline="\n").write(s)
    print("  %s: patched" % rel)


# ---- Pattern.marking(): what colour the mask is painted -------------------------------------------
patch("fish/Pattern.java", "§pattern-mask", [(
    "    private static int shift(int rgb, double degrees, double lift) {",
    """    /**
     * §pattern-mask: the colour the family's MASK is painted, given the fish's ground colour. The mask
     * says where; this says what. A light fish takes a darker cut of its own ground and a dark one a
     * paler cut, so the marking always reads against the body; ghost is a pale wash whatever the
     * fish, ember is warm, aurora is the ground turned round the wheel by where the index sits in its
     * band. Then the family's own hue and lift, and the in-band turn — so two neighbours differ.
     * A gem returns the gem: the whole fish is already that colour and the mask must vanish into it.
     */
    public static int marking(int ground, int pattern) {
        int gem = gemColor(pattern);
        if (gem >= 0) return gem;
        int fam = familyIndex(pattern);
        if (fam == 0) return ground;
        double lum = (0.299 * ((ground >> 16) & 0xFF) + 0.587 * ((ground >> 8) & 0xFF) + 0.114 * (ground & 0xFF)) / 255.0;
        String name = FAMILY[fam];
        int base;
        if ("ghost".equals(name)) base = mix(ground, 0xF6F2EA, 0.62);
        else if ("ember".equals(name)) base = mix(ground, 0xE8702A, 0.72);
        else if ("aurora".equals(name)) base = shift(mix(ground, 0x60B8FF, 0.55), (pattern % 70) * 360.0 / 70.0, 0.10);
        else base = lum > 0.42 ? mix(ground, 0x241A12, 0.55) : mix(ground, 0xF0E6D2, 0.45);
        return shift(base, hueShift(pattern) - HUE[fam], LIFT[fam]) & 0xFFFFFF;
    }

    private static int mix(int a, int b, double t) {
        int r = (int) Math.round(((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
        int g = (int) Math.round(((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
        int bl = (int) Math.round((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
        return (r << 16) | (g << 8) | bl;
    }

    private static int shift(int rgb, double degrees, double lift) {""")])

# ---- FishMorph.patternTint(): the ground per draw, then the marking -------------------------------
patch("fish/FishMorph.java", "§pattern-mask", [(
    "    /** ground, hi, sumi, crown; -1 means \"the ground colour\", i.e. the fish does not wear that layer. */",
    """    /**
     * §pattern-mask: the colour the pattern mask over this fish is painted. The ground is the koi's own
     * ground for a koi, and for the carp draws the mean colour of the sprite as drawn (measured by
     * tools/gen_pattern_masks.py) — the marking is then Pattern.marking()'s cut of it.
     */
    public static int patternTint(String speciesPath, String variety, int pattern) {
        int ground;
        if ("koi_carp".equals(speciesPath)) {
            int[] p = KOI_PAINT.get(variety.startsWith("koi_") ? variety.substring(4) : variety);
            ground = (p == null ? KOI_PAINT.get("kohaku") : p)[0];
        } else {
            ground = CARP_GROUND.getOrDefault(speciesPath, 0x7D5835);
        }
        return 0xFF000000 | Pattern.marking(ground, pattern);
    }

    /** The mean colour of each carp draw's sprite — what a marking is cut from. */
    private static final java.util.Map<String, Integer> CARP_GROUND = java.util.Map.of(
            "carp", 0x7D5835, "wild_carp", 0x664B31, "mirror_carp", 0x74573E,
            "linear_carp", 0x7D5535, "naked_carp", 0x815940);

    /** ground, hi, sumi, crown; -1 means "the ground colour", i.e. the fish does not wear that layer. */""")])

if D == "26":
    # ---- 26.x: the stack carries the family and the marking; the definition reads both -------------
    patch("item/FishItem.java", "§pattern-mask", [(
        """        java.util.List<String> strings = sp == null || draw.equals(sp.getPath())
                ? java.util.List.of() : java.util.List.of(draw);""",
        """        // §pattern-mask: strings[1] is the pattern FAMILY the item definition selects the mask on, and
        // the marking rides as one more colour — colors[4] for a koi (0..3 are its layers), [1] otherwise.
        // A plain fish or a gem names no family, and the definition's select falls back to empty.
        String family = com.riverfishing.fish.Pattern.familyIndex(pattern) > 0
                && !com.riverfishing.fish.Pattern.isGem(pattern) ? com.riverfishing.fish.Pattern.family(pattern) : "";
        java.util.List<String> strings = java.util.List.of(
                sp == null || draw.equals(sp.getPath()) ? "" : draw, family);
        if (sp != null && com.riverfishing.registry.ModItemTags.patterned(stack)) {
            java.util.List<Integer> withMark = new java.util.ArrayList<>(colors);
            withMark.add(com.riverfishing.fish.FishMorph.patternTint(sp.getPath(),
                    com.riverfishing.fish.CatchCard.of(stack).getStringOr("Variety", ""), pattern));
            colors = withMark;
        }""")])
    print("done (26)")
    sys.exit(0)

# ---- 1.21.1 / 1.20.1: tint, models, and the second draw -------------------------------------------
patch("client/FishTint.java", "§pattern-mask", [(
    """        if (sp == null) return -1;                       // a creative-tab entry with no specimen data""",
    """        if (sp == null) return -1;                       // a creative-tab entry with no specimen data
        // §pattern-mask: tintindex 5 is the family's mask, drawn by FishItemRenderer over the body.
        if (tintIndex == 5) {
            return FishMorph.patternTint(sp.getPath(), %s,
                    com.riverfishing.fish.CatchCard.pattern(stack));
        }""" % GET_STR("com.riverfishing.fish.CatchCard.of(stack)", "Variety"))])

patch("client/ClientModels.java", "§pattern-mask", [(
    """        list.add(FryItemRenderer.FALLBACK); // §breeding: the static fry icon the procedural one falls back to""",
    """        list.add(FryItemRenderer.FALLBACK); // §breeding: the static fry icon the procedural one falls back to
        // §pattern-mask: one flat mask model per patterned draw per family. Unlisted here they would
        // never be baked, and the renderer would draw the missing model — which is nothing, silently.
        for (String draw : FishItemRenderer.PATTERN_DRAWS) {
            for (String fam : com.riverfishing.fish.Pattern.families()) {
                if (!"plain".equals(fam)) list.add(FishItemRenderer.patternModel(draw, fam));
            }
        }""")])

patch("client/FishItemRenderer.java", "§pattern-mask", [(
    """    public static %s iconModel(String speciesPath) {""" % RL,
    """    /** §pattern-mask: the sprites that carry a pattern mask — the koi and the five carp draws. */
    public static final String[] PATTERN_DRAWS = {"koi_carp", "carp", "wild_carp", "mirror_carp", "linear_carp", "naked_carp"};

    /** §pattern-mask: the flat mask model for one draw and one family — models/item/pattern/. */
    public static %s patternModel(String draw, String family) {
        return RiverFishing.id("item/pattern/" + draw + "_" + family);
    }

    public static %s iconModel(String speciesPath) {""" % (RL, RL)), (
    """        ir.render(stack, ItemDisplayContext.NONE, false, pose, buffers, light,
                ov == net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY ? overlay : ov, model);
        pose.popPose();""",
    """        ir.render(stack, ItemDisplayContext.NONE, false, pose, buffers, light,
                ov == net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY ? overlay : ov, model);
        // §pattern-mask: the family's marking, a second flat model at the same pose. Its quad sits just
        // outside the sprite's slab (z 7.4..8.6 against 7.5..8.5) so nothing z-fights, and its tintindex
        // 5 is what FishTint paints with the marking. Plain and gem draw nothing: plain has no mask, and
        // a gem has already painted the whole fish.
        int pat = com.riverfishing.fish.CatchCard.pattern(stack);
        if (com.riverfishing.fish.Pattern.familyIndex(pat) > 0 && !com.riverfishing.fish.Pattern.isGem(pat)) {
            BakedModel mask = com.riverfishing.client.platform.ClientPlatform.bakedModel(
                    patternModel(draw, com.riverfishing.fish.Pattern.family(pat)));
            if (mask != null && mask != mm.getMissingModel()) {
                pose.pushPose();
                // offset(): a notch along the body — ±2 texels of 256 — so neighbours are not twins
                pose.translate(com.riverfishing.fish.Pattern.offset(pat) / 256f, 0f, 0f);
                ir.render(stack, ItemDisplayContext.NONE, false, pose, buffers, light, overlay, mask);
                pose.popPose();
            }
        }
        pose.popPose();""")])
print("done (%s)" % D)
