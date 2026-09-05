# -*- coding: utf-8 -*-
"""§fry-look: a fry is drawn as the fish it will be — variety, genes and pattern, not a bare species.

    py -X utf8 tools/patches/p_frylook.py <root> [1211|1201|26]

Reported on 26.2: hatched fry "do not match their species" in the aquarium, and in the hand they are
white. Every fry renderer built its picture from a BARE species stack — `new ItemStack(fish)`, or
FishItem.create with no card. On 26.x a bare koi has no colours and draws white; on 1.21.1 FishTint
reads the card off the stack it is handed, the fry stack has none, and every koi fry came out a
kohaku. A showa's fry swimming as kohaku is "not their species".

FryItem.look(fry) is the one answer: the species' own item, carrying a card with the VARIETY read off
the fry's genome (koi: Genome.koiVariety; carp: carpVariety; anything else: none), the genes, and the
pattern the fry inherited — stamped on 26.x. The four renderers ask it instead of guessing:

  1.21.1/1.20.1   FryItemRenderer (the bucket in the hand), AquariumRenderer.renderFry
  26.x            FrySpecialRenderer (its argument is now the look stack, not a species id),
                  AquariumRenderer.extractFry
"""
import io, os, sys

ROOT = sys.argv[1]
D = sys.argv[2] if len(sys.argv) > 2 else "1211"
J = os.path.join(ROOT, "common/src/main/java/com/riverfishing")
PATTERN = ('t.getIntOr(com.riverfishing.fish.Pattern.TAG, com.riverfishing.fish.Pattern.NONE)' if D == "26"
           else 't.contains(com.riverfishing.fish.Pattern.TAG) ? t.getInt(com.riverfishing.fish.Pattern.TAG) : com.riverfishing.fish.Pattern.NONE')
STAMP = "        com.riverfishing.item.FishItem.stampIcon(s);   // 26.x: the icon is the stack\n" if D == "26" else ""


def patch(rel, marker, edits):
    p = os.path.join(J, rel)
    s = io.open(p, encoding="utf-8").read()
    if marker in s:
        print("  %s: already patched" % rel); return
    for old, new in edits:
        assert s.count(old) == 1, "%s: anchor %r (count %d)" % (rel, old[:60], s.count(old))
        s = s.replace(old, new, 1)
    io.open(p, "w", encoding="utf-8", newline="\n").write(s)
    print("  %s: patched" % rel)


# ---- 1. the one answer ------------------------------------------------------------------------------
patch("item/FryItem.java", "§fry-look", [(
    "    public static String genome(ItemStack s) {",
    """    /**
     * §fry-look: the fish this fry will be, as a stack the renderers can draw — the species' own item
     * with a card carrying the variety read off the genome, the genes and the inherited pattern. A bare
     * species stack drew a white koi on 26.x and a kohaku for every koi on 1.21.1; this is what a
     * showa's fry looks like. EMPTY when the fry names no species (a creative-tab bucket).
     */
    public static ItemStack look(ItemStack fry) {
        var sp = species(fry);
        if (sp == null) return ItemStack.EMPTY;
        var item = com.riverfishing.registry.ModItems.fishItem(sp);
        if (item == null) return ItemStack.EMPTY;
        String path = sp.getPath(), genome = genome(fry);
        String variety = com.riverfishing.fish.Genome.isKoiId(path)
                ? "koi_" + com.riverfishing.fish.Genome.koiVariety(genome)
                : com.riverfishing.fish.Genome.varietyOfSpecies(path).isEmpty() ? ""
                : com.riverfishing.fish.Genome.carpVariety(genome);
        net.minecraft.nbt.CompoundTag t = StackNbt.get(fry);
        int pattern = %s;
        ItemStack s = com.riverfishing.item.FishItem.create(item, sp, 1, 5, true);
        net.minecraft.nbt.CompoundTag card = new net.minecraft.nbt.CompoundTag();
        if (!variety.isEmpty()) card.putString("Variety", variety);
        card.putString("Genes", genome);
        card.putInt(com.riverfishing.fish.Pattern.TAG, pattern);
        StackNbt.mutate(s, tag -> tag.put(com.riverfishing.fish.CatchCard.TAG, card));
%s        return s;
    }

    public static String genome(ItemStack s) {""" % (PATTERN, STAMP))])

# ---- 2. the aquarium, every tree ---------------------------------------------------------------------
patch("client/AquariumRenderer.java", "§fry-look", [(
    "        ItemStack fish = FishItem.create(com.riverfishing.registry.ModItems.fishItem(sp), sp, 1, 5, true);",
    """        // §fry-look: the fish the fry will be — variety, genes, pattern — not a bare species stack.
        ItemStack fish = FryItem.look(fryStack);
        if (fish.isEmpty()) return;""")])

if D == "26":
    # ---- 3. the bucket in the hand, 26.x: the special renderer's argument is the look stack ---------
    patch("client/FrySpecialRenderer.java", "§fry-look", [
        ("public final class FrySpecialRenderer implements SpecialModelRenderer<Identifier> {",
         "public final class FrySpecialRenderer implements SpecialModelRenderer<ItemStack> {   // §fry-look"),
        ("    public record Unbaked() implements SpecialModelRenderer.Unbaked<Identifier> {",
         "    public record Unbaked() implements SpecialModelRenderer.Unbaked<ItemStack> {"),
        ("        public SpecialModelRenderer<Identifier> bake(BakingContext context) {",
         "        public SpecialModelRenderer<ItemStack> bake(BakingContext context) {"),
        ("""    public Identifier extractArgument(ItemStack stack) {
        return FryItem.species(stack);
    }""",
         """    public ItemStack extractArgument(ItemStack stack) {
        // §fry-look: the fish the fry will be, card and all — a species id alone drew a white koi
        return FryItem.look(stack);
    }"""),
        ("    public void submit(Identifier species, PoseStack pose, SubmitNodeCollector collector,",
         "    public void submit(ItemStack look, PoseStack pose, SubmitNodeCollector collector,"),
        ("""        RegistrySupplier<Item> fish = species == null ? null : ModItems.FISH_ITEMS.get(species);
        if (fish == null) {""",
         """        if (look == null || look.isEmpty()) {"""),
        ("        ItemStack stack = new ItemStack(fish.get());",
         "        ItemStack stack = look;"),
    ])
else:
    # ---- 3. the bucket in the hand, 1.21.1 / 1.20.1 -------------------------------------------------
    patch("client/FryItemRenderer.java", "§fry-look", [(
        """        for (int i = 0; i < FISH.length; i++) {
            float[] f = FISH[i];
            pose.pushPose();
            // a hair of depth per fish so the flat sprites never z-fight where they overlap
            pose.translate(0.5 + f[0], 0.5 + f[1], 0.5 + i * 0.02);
            if (f[3] > 0f) pose.mulPose(Axis.YP.rotationDegrees(180f)); // mirror: the sprite is two-faced
            pose.scale(f[2], f[2], f[2]);
            ir.render(stack, ItemDisplayContext.NONE, false, pose, buffers, light, overlay, fish);""",
        """        // §fry-look: the tint provider reads the card off the stack it is handed, and the fry stack has
        // none — every koi fry drew as a kohaku. Hand it the fish the fry will be.
        ItemStack look = FryItem.look(stack);
        if (look.isEmpty()) look = stack;
        for (int i = 0; i < FISH.length; i++) {
            float[] f = FISH[i];
            pose.pushPose();
            // a hair of depth per fish so the flat sprites never z-fight where they overlap
            pose.translate(0.5 + f[0], 0.5 + f[1], 0.5 + i * 0.02);
            if (f[3] > 0f) pose.mulPose(Axis.YP.rotationDegrees(180f)); // mirror: the sprite is two-faced
            pose.scale(f[2], f[2], f[2]);
            ir.render(look, ItemDisplayContext.NONE, false, pose, buffers, light, overlay, fish);""")])
print("done (%s)" % D)
