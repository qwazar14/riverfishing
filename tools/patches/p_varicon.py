# -*- coding: utf-8 -*-
"""§variety-icon: a carp is DRAWN as the scale variety its genotype gave it.

    py -X utf8 tools/patches/p_varicon.py <root> [1211|1201|26]

§scale-genes made four fish into one: the water hands out `carp` and the K/N pair on the card says
whether it is scaled, mirror, linear or leather. The card said it; the picture did not — every carp
came ashore wearing the scaled drawing, and the mirror, linear and naked sprites (three of them drawn
by hand) were left to the legacy items nothing hands out any more.

The drawing follows the genotype now. Nothing about the ITEM changes — it is still `riverfishing:carp`
in the chest, still one species in the journal and the ledger — only which sprite is drawn for it.
Koi are deliberately NOT here: one white koi sprite is PAINTED into nine varieties by tint layers
(§koi-genes), so a koi already looks like its genotype.
"""
import io, os, sys

ROOT = sys.argv[1]
D = sys.argv[2] if len(sys.argv) > 2 else "1211"
J = os.path.join(ROOT, "common/src/main/java/com/riverfishing/")


def rd(p): return io.open(p, encoding="utf-8").read()
def wr(p, s): io.open(p, "w", encoding="utf-8", newline="\n").write(s)


# ---- Genome: which drawing a variety wears ---------------------------------------------------------
p = J + "fish/Genome.java"; s = rd(p)
if "§variety-icon" not in s:
    anchor = "    private Genome() {}"
    assert anchor in s
    s = s.replace(anchor, '''    /**
     * §variety-icon: the id whose SPRITE this fish is drawn with. A carp wears the drawing of the
     * scale variety its K/N pair gave it — the three hand-drawn sprites are still on disk, still
     * registered as icon models, so this is a name swap and nothing more: the item, the price, the
     * journal page and the ledger all go on saying `carp`.
     *
     * <p>Koi are not here on purpose. One white koi sprite is painted into all nine varieties by tint
     * layers (§koi-genes), so a koi already looks like its genotype without changing drawings.
     */
    public static String drawnAs(String speciesPath, String variety) {
        if (variety == null || variety.isEmpty() || !"carp".equals(speciesPath)) return speciesPath;
        for (java.util.Map.Entry<String, String> e : VARIETY_OF_ID.entrySet()) {
            if (e.getValue().equals(variety)) return e.getKey();
        }
        return speciesPath;
    }

''' + anchor, 1)
    wr(p, s); print("  Genome: drawnAs()")

if D == "26":
    # ---- 26.x: no BEWLR. The variety rides in as a custom_model_data STRING and the item definition
    # selects the drawing on it (tools/gen_dynamic_icons.py writes that select).
    p = J + "item/FishItem.java"; s = rd(p)
    if "§variety-icon" not in s:
        old = """        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_MODEL_DATA,
                new net.minecraft.world.item.component.CustomModelData(
                        java.util.List.of(getIconScale(stack)),
                        java.util.List.of(), java.util.List.of(), colors));"""
        assert old in s, "stampIcon moved"
        s = s.replace(old, """        // §variety-icon: the scale variety, as a STRING the item definition selects the drawing on.
        // Empty for everything that is not a carp, which is the definition's fallback: its own sprite.
        String draw = sp == null ? "" : com.riverfishing.fish.Genome.drawnAs(sp.getPath(),
                com.riverfishing.fish.CatchCard.of(stack).getStringOr("Variety", ""));
        java.util.List<String> strings = sp == null || draw.equals(sp.getPath())
                ? java.util.List.of() : java.util.List.of(draw);
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_MODEL_DATA,
                new net.minecraft.world.item.component.CustomModelData(
                        java.util.List.of(getIconScale(stack)),
                        java.util.List.of(), strings, colors));""", 1)
        wr(p, s); print("  FishItem.stampIcon: the variety as a model string")

    p = os.path.join(ROOT, "tools/gen_dynamic_icons.py"); s = rd(p)
    if "§variety-icon" not in s:
        old = """        write(os.path.join(ITEMS, sp + ".json"), {"model": {
            "type": "minecraft:range_dispatch",
            "property": "minecraft:custom_model_data",
            "index": 0,
            "entries": entries,
            "fallback": fish_node("riverfishing:item/" + sp, sp),
        }})"""
        assert old in s, "the fish item writer moved"
        s = s.replace(old, """        dispatch = {
            "type": "minecraft:range_dispatch",
            "property": "minecraft:custom_model_data",
            "index": 0,
            "entries": entries,
            "fallback": fish_node("riverfishing:item/" + sp, sp),
        }
        SCALE_DISPATCH[sp] = dispatch
        write(os.path.join(ITEMS, sp + ".json"), {"model": dispatch})

    # §variety-icon: a carp is drawn as its scale genotype. On 1.21.1 the item renderer simply looks up
    # another icon model; here the drawing is chosen by the item definition, selecting on the variety
    # string FishItem.stampIcon writes. Each case is that OTHER species' whole scale dispatch, so a
    # mirror carp keeps every size bucket the scaled one has.
    varieties = [v for v in ("mirror_carp", "linear_carp", "naked_carp") if v in SCALE_DISPATCH]
    if "carp" in SCALE_DISPATCH and varieties:
        write(os.path.join(ITEMS, "carp.json"), {"model": {
            "type": "minecraft:select",
            "property": "minecraft:custom_model_data",
            "index": 0,
            "cases": [{"when": v, "model": SCALE_DISPATCH[v]} for v in varieties],
            "fallback": SCALE_DISPATCH["carp"],
        }})""", 1)
        s = s.replace("    # ---- fish ----", "    # ---- fish ----\n    SCALE_DISPATCH = {}", 1)
        wr(p, s); print("  gen_dynamic_icons: carp selects its drawing on the variety")
else:
    # ---- 1.21.1 / 1.20.1: the BEWLR picks the icon model, so this is one lookup -----------------------
    p = J + "client/FishItemRenderer.java"; s = rd(p)
    if "§variety-icon" not in s:
        old = "        BakedModel model = com.riverfishing.client.platform.ClientPlatform.bakedModel(iconModel(sp.getPath()));"
        assert old in s, "the icon lookup moved"
        s = s.replace(old, """        // §variety-icon: a carp is drawn as the scale variety on its card — mirror, linear or leather.
        // Its own model if the card says nothing, which is every fish that is not a carp.
        String draw = com.riverfishing.fish.Genome.drawnAs(sp.getPath(),
                com.riverfishing.fish.CatchCard.of(stack).getString("Variety"));
        BakedModel model = com.riverfishing.client.platform.ClientPlatform.bakedModel(iconModel(draw));""", 1)
        wr(p, s); print("  FishItemRenderer: the drawing follows the genotype")
print("done (%s)" % D)
