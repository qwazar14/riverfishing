# -*- coding: utf-8 -*-
"""§pattern-gate: the pattern index belongs to the fish that WEAR it — the carps and the koi.

    py -X utf8 tools/patches/p_patgate.py <root> [1211|1201|26]

0.9.0 shipped the index on every species. On a koi it is the whole point: the band turns the four
tint layers and two kohaku stop being the same fish. On a bleak it is a line of text that changes
nothing — the sprite is untinted for any ordinary index — so the card grew a row nobody could use.

The gate is a data tag, `riverfishing:patterned`, not a list in Java: a pack (or a later version of
this mod) puts a fish item in it and that species starts rolling indices, inheriting them through
roe, showing the row and paying the gem's six times. Nothing else has to be touched.

Two choke points do all the work:
  * CatchCard.rollPattern — a species outside the tag is never GIVEN an index;
  * CatchCard.pattern(ItemStack) — and one that has one from before reads as NONE, so the perch in
    your chest loses the row, the gem tint and the price term the same tick this loads.
Everything downstream (the tooltip row, FishTint's gem paint, KeepnetSale, the journal board) already
asks one of those two, so this is the whole change.
"""
import io, os, sys, json

ROOT = sys.argv[1]
D = sys.argv[2] if len(sys.argv) > 2 else "1211"
J = os.path.join(ROOT, "common/src/main/java/com/riverfishing/")
TAGDIR = "items" if D == "1201" else "item"      # 1.21 renamed the folder to the singular
ID = "Identifier" if D == "26" else "ResourceLocation"          # 26.x renamed the class
ID_IMPORT = "net.minecraft.resources." + ID
GG = "GuiGraphicsExtractor" if D == "26" else "GuiGraphics"    # and the graphics handle


def rd(p): return io.open(p, encoding="utf-8").read()
def wr(p, s): io.open(p, "w", encoding="utf-8", newline="\n").write(s)


# ---- the tag itself: the carps of Cyprinus carpio, and the koi -------------------------------------
# The five legacy koi ids and the three legacy scale ids are in it too: a mirror carp caught last week
# is still a mirror carp in a chest, and it keeps its row.
SPECIES = ["carp", "wild_carp", "mirror_carp", "linear_carp", "naked_carp", "koi_carp",
           "carp_koi_kohaku", "carp_koi_asagi", "carp_koi_bekko", "carp_koi_showa_sanke",
           "carp_koi_tancho_sanke"]
p = os.path.join(ROOT, "common/src/main/resources/data/riverfishing/tags", TAGDIR, "patterned.json")
os.makedirs(os.path.dirname(p), exist_ok=True)
wr(p, json.dumps({"replace": False, "values": ["riverfishing:" + s for s in SPECIES]},
                 indent=2, ensure_ascii=False) + "\n")
print("  tag: %s (%d species)" % (os.path.relpath(p, ROOT), len(SPECIES)))

# ---- ModItemTags: the key, and the two ways to ask ------------------------------------------------
p = J + "registry/ModItemTags.java"; s = rd(p)
if "§pattern-gate" not in s:
    s = s.replace("""    public static final TagKey<Item> MAGGOT_FOOD =
            TagKey.create(Registries.ITEM, RiverFishing.id("maggot_food"));
}""", """    public static final TagKey<Item> MAGGOT_FOOD =
            TagKey.create(Registries.ITEM, RiverFishing.id("maggot_food"));

    /**
     * §pattern-gate: the species that carry a pattern index — the carps and the koi, whose colours the
     * index actually turns. It is a tag rather than a list in Java so the answer stays in data: put a
     * fish item in here and that species starts rolling indices, breeding them through its roe, showing
     * the row on its card and paying the gem's six times. Nothing in code has to know.
     */
    public static final TagKey<Item> PATTERNED =
            TagKey.create(Registries.ITEM, RiverFishing.id("patterned"));

    /** Does this fish wear a pattern? Asked of the STACK, so it works on a client with no profiles. */
    public static boolean patterned(ItemStack fish) {
        return fish.is(PATTERNED);
    }

    /** The same, for a species that has no stack in hand — the journal's board, a roe tooltip. */
    public static boolean patterned(%s species) {
        RegistrySupplier<Item> item = species == null ? null : ModItems.FISH_ITEMS.get(species);
        return item != null && new ItemStack(item.get()).is(PATTERNED);
    }
}""" % ID, 1)
    s = s.replace("import net.minecraft.world.item.Item;",
                  "import dev.architectury.registry.registries.RegistrySupplier;\n"
                  "import " + ID_IMPORT + ";\n"
                  "import net.minecraft.world.item.Item;\n"
                  "import net.minecraft.world.item.ItemStack;", 1)
    wr(p, s); print("  ModItemTags: PATTERNED + patterned()")

# ---- CatchCard: the two choke points ---------------------------------------------------------------
p = J + "fish/CatchCard.java"; s = rd(p)
if "§pattern-gate" not in s:
    old = """    public static int pattern(ItemStack fish) {
        return pattern(has(fish) ? of(fish) : null);
    }"""
    assert old in s, "CatchCard.pattern(ItemStack) moved"
    s = s.replace(old, """    public static int pattern(ItemStack fish) {
        // §pattern-gate: only the tagged species wear one. A fish landed while every species rolled an
        // index keeps the number in its NBT and simply stops reading it — no row, no gem paint, no
        // price term — so an old world cleans itself up without touching a single card.
        if (!com.riverfishing.registry.ModItemTags.patterned(fish)) return Pattern.NONE;
        return pattern(has(fish) ? of(fish) : null);
    }""", 1)
    old = """    private static int rollPattern(ServerLevel level, BlockPos where, FishProfile p, Random rng) {
        int bred ="""
    assert old in s, "rollPattern moved"
    s = s.replace(old, """    private static int rollPattern(ServerLevel level, BlockPos where, FishProfile p, Random rng) {
        // §pattern-gate: a species outside `riverfishing:patterned` is never given one in the first place.
        if (p == null || !com.riverfishing.registry.ModItemTags.patterned(p.id)) return Pattern.NONE;
        int bred =""", 1)
    wr(p, s); print("  CatchCard: rollPattern + pattern(stack) gated")

# ---- the journal board: only on a page where the board means something ------------------------------
p = J + "client/JournalScreen.java"; s = rd(p)
if "§pattern-gate" not in s:
    old = """    private int patternRow(%s g, %s id, int y) {
        String[] fam""" % (GG, ID)
    assert old in s, "patternRow moved"
    s = s.replace(old, """    private int patternRow(%s g, %s id, int y) {
        // §pattern-gate: a species that does not wear a pattern has no board to fill, and an old world
        // whose journal recorded families for a perch simply stops drawing them.
        if (!com.riverfishing.registry.ModItemTags.patterned(id)) return y;
        String[] fam""" % (GG, ID), 1)
    wr(p, s); print("  JournalScreen: board gated")

# ---- roe and fry: the clutch's line, on the fish that have one --------------------------------------
p = J + "item/RoeItem.java"; s = rd(p)
if "§pattern-gate" not in s:
    old = """        // §pattern: the clutch's index and the family it will hatch into — what the line is FOR.
        int pattern = pattern(stack);"""
    assert old in s, "RoeItem.brood moved"
    s = s.replace(old, """        // §pattern: the clutch's index and the family it will hatch into — what the line is FOR.
        // §pattern-gate: on the species that wear one; roe laid before the gate keeps its int unread.
        int pattern = com.riverfishing.registry.ModItemTags.patterned(species(stack))
                ? pattern(stack) : com.riverfishing.fish.Pattern.NONE;""", 1)
    wr(p, s); print("  RoeItem: brood line gated")
print("done (%s)" % D)
