# -*- coding: utf-8 -*-
"""§pattern (0.9.0): the pattern index — the seams in the files that already existed.

    py -X utf8 tools/patches/p_pattern.py <repo root> [1211|1201|26]

Idempotent: every edit carries a §pattern marker and is skipped when it is already there. Anchors are
exact text; a missing one exits 1 with the anchor printed, because a silent no-op is how a port loses a
feature on one branch.

fish/Pattern.java is a NEW file and is copied, not patched. What this script does is thread one int
through the places a fish's look and price are decided: the card writes it, the tint reads it, the
tank inherits it, the ledger remembers the line, the counter pays for a gem and the journal counts the
families you have seen.
"""
import io, os, sys

MARK = "§pattern"


class Patch:
    def __init__(self, root, rel):
        self.path = os.path.join(root, "common/src/main/java/com/riverfishing", rel)
        self.src = io.open(self.path, encoding="utf-8").read()
        self.orig = self.src

    def sub1(self, old, new):
        if self.src.count(old) != 1:
            sys.exit("%s: anchor found %d times, expected 1:\n---\n%s\n---"
                     % (os.path.basename(self.path), self.src.count(old), old))
        self.src = self.src.replace(old, new)
        return self

    def save(self):
        if self.src != self.orig:
            io.open(self.path, "w", encoding="utf-8", newline="\n").write(self.src)
        return self.src != self.orig


def nbt_int(var, tag, default, dialect):
    """The dialects' one difference that matters here: 26.x reads NBT with a default in hand."""
    return ("%s.getIntOr(%s, %s)" % (var, tag, default)) if dialect == "26" \
        else ("%s.contains(%s) ? %s.getInt(%s) : %s" % (var, tag, var, tag, default))


# ---- the pieces that go in ------------------------------------------------------------------------

CARD_READERS = '''
    /**
     * §pattern: the index on this fish's card, or {@link Pattern#NONE} for one landed before the index
     * existed. Read through here rather than off the tag: an absent int reads as 0 in NBT, and 0 is a
     * perfectly good pattern — every old fish in every chest would have become a plain-band specimen.
     */
    public static int pattern(ItemStack fish) {
        return pattern(has(fish) ? of(fish) : null);
    }

    /** §pattern: the same, off a card already in hand (a released fish, a pond ledger entry). */
    public static int pattern(CompoundTag card) {
        return card == null ? Pattern.NONE : %(read)s;
    }

    /**
     * §pattern: the index a fish being landed HERE comes out at.
     *
     * <p>A wild fish is rolled off the world seed, the block it came from and the tick it came out on —
     * the tick is in there so the same swim cannot be re-cast for the same number. A fish out of water
     * somebody has STOCKED inherits that line's index instead, which is the whole collector's hook: fry
     * bred toward a family and released go on breeding toward it.
     */
    private static int rollPattern(ServerLevel level, BlockPos where, FishProfile p, Random rng) {
        int bred = p == null ? Pattern.NONE : com.riverfishing.fishing.StockedData.get(level)
                .pattern(com.riverfishing.fishing.StockedData.region(where), p.id.getPath());
        return Pattern.has(bred) ? Pattern.inherit(bred, bred, rng)
                : Pattern.roll(level.getSeed(), where, level.getGameTime());
    }
'''

MORPH_TINT = '''    public static int tint(String speciesPath, double age, String morphId) {
        return tint(speciesPath, age, morphId, Pattern.NONE);
    }

    /**
     * §pattern: the same, for a specimen carrying a pattern index. An ordinary index changes nothing —
     * a perch is a perch, and the bands are the koi's business — but a GEM paints the whole fish one
     * saturated colour. Multiplied over the sprite that keeps the drawing's light and shade and throws
     * its colour away, which is exactly what a solid-colour pike should look like.
     */
    public static int tint(String speciesPath, double age, String morphId, int pattern) {
        int gem = Pattern.gemColor(pattern);
        if (gem >= 0) return 0xFF000000 | gem;
        Age a = AGE.getOrDefault(speciesPath, AGE_DEFAULT);
'''

JOURNAL_DATA = '''
    /**
     * §pattern: the pattern FAMILIES the player has seen of this species, kept as a twelve-bit mask in
     * that species' own compound — the same shape {@link #recordMorph} uses, so the collection board
     * needs no second structure, no migration and no packet of its own.
     *
     * @return true the first time a family is seen for this species
     */
    public static boolean recordPattern(Player player, %(rl)s species, int pattern) {
        if (!com.riverfishing.fish.Pattern.has(pattern)) return false;
        int bit = 1 << com.riverfishing.fish.Pattern.familyIndex(pattern);
        CompoundTag root = get(player);
        CompoundTag fish = root.%(comp)s(species.toString());
        int seen = fish.%(getint)s;
        if ((seen & bit) != 0) return false;
        fish.putInt("patterns", seen | bit);
        root.put(species.toString(), fish);
        PlayerData.root(player).put(TAG, root);
        PlayerData.markDirty(player);
        return true;
    }

    /** §pattern: the mask of families seen for this species. Reads the journal tag straight. */
    public static int patternsSeen(CompoundTag journal, %(rl)s species) {
        return journal.%(comp)s(species.toString()).%(getint)s;
    }
'''

STOCKED = '''    /**
     * §pattern: the pattern index this water's line runs at, or {@link com.riverfishing.fish.Pattern#NONE}
     * when nobody has stocked one. A fish landed out of the water inherits it, so a family bred in a tank
     * and released keeps coming back out of the pond.
     */
    public int pattern(long region, String species) {
        CompoundTag t = brood.get(key(region, species));
        if (t == null) return com.riverfishing.fish.Pattern.NONE;
        return %(read)s;
    }

    /**
     * §pattern: what was just released moves the water's line HALFWAY toward its own index, rather than
     * overwriting it — a pond is the fish in it, so one gem carp dropped into a stocked lake shifts the
     * line without becoming it. The first fish sets it outright, because there is nothing to average.
     */
    public void setPattern(long region, String species, int pattern) {
        if (!com.riverfishing.fish.Pattern.has(pattern)) return;
        CompoundTag t = entry(region, species);
        int have = %(read2)s;
        t.putInt("Pattern", com.riverfishing.fish.Pattern.has(have) ? (have + pattern) / 2 : pattern);
        setDirty();
    }
'''

ROE_READERS = '''

    /**
     * §pattern: the clutch's index — the parents' mean with a small mutation, written when the pair
     * spawns and carried through the egg into the fry, because the pattern is the LINE and the line is
     * what a breeder is working on. {@link com.riverfishing.fish.Pattern#NONE} on roe laid before this.
     */
    public static int pattern(ItemStack s) {
        CompoundTag t = StackNbt.get(s);
        return %(read)s;
    }

    public static void setPattern(ItemStack s, int pattern) {
        if (com.riverfishing.fish.Pattern.has(pattern)) {
            StackNbt.mutate(s, t -> t.putInt(TAG_PATTERN, pattern));
        }
    }'''

JOURNAL_SCREEN = '''
    /**
     * §pattern: the twelve pattern families, and which of them you have landed of this species. A row of
     * cells rather than a list of names — the index IS a collection, and a board you can see the holes
     * in is the only thing a collection board is for. The swatch is the family's own hue turn, so the
     * grid reads left to right as the sequence the bands actually paint.
     */
    private int patternRow(%(gg)s g, %(rl)s id, int y) {
        String[] fam = com.riverfishing.fish.Pattern.families();
        int seen = com.riverfishing.fishing.JournalData.patternsSeen(data, id);
        y += 6;
        g.%(text)s(this.font, Component.translatable("journal.riverfishing.patterns",
                Integer.bitCount(seen), fam.length), left + 10, y, GuiStyle.TEXT_HINT, false);
        y += 12;
        for (int i = 0; i < fam.length; i++) {
            int x = left + 10 + i * 13;
            g.fill(x, y, x + 11, y + 11, GuiStyle.TEXT_HINT);        // the frame, so a pale band shows
            g.fill(x + 1, y + 1, x + 10, y + 10, (seen & (1 << i)) != 0
                    ? 0xFF000000 | com.riverfishing.fish.Pattern.swatch(i) : 0xFFE8DCC0);
        }
        return y + 15;
    }
'''

TOOLTIP_ROW = '''        // §pattern: the index this fish came out at, the family it belongs to — or, one fish in 83,
        // the gem it turned out to be, named in its own colour because that is the whole reward.
        int pattern = CatchCard.pattern(fish);
        if (com.riverfishing.fish.Pattern.has(pattern)) {
            boolean gem = com.riverfishing.fish.Pattern.isGem(pattern);
            row("pattern", Component.literal("#" + pattern + "  ").append(Component.translatable(gem
                            ? "gem.riverfishing." + com.riverfishing.fish.Pattern.gemName(pattern)
                            : "pattern.riverfishing." + com.riverfishing.fish.Pattern.family(pattern))),
                    gem ? com.riverfishing.fish.Pattern.gemInk(pattern) : PINK);
        }
'''


# ---- the files ------------------------------------------------------------------------------------

def genome(root, d):
    p = Patch(root, "fish/Genome.java")
    if MARK in p.src:
        return False
    p.sub1('    /** The variety a carp id names, or "" for a fish that is not a carp (nothing else has K/N). */',
           '''    /**
     * §pattern: the variety's multiplier and the pattern's together — one call, because every price in
     * the mod wants both and a price that forgot one is a fish worth six times what the counter pays.
     */
    public static double varietyValue(String variety, int pattern) {
        return varietyValue(variety) * Pattern.value(pattern);
    }

    /** The variety a carp id names, or "" for a fish that is not a carp (nothing else has K/N). */''')
    return p.save()


def catchcard(root, dialect):
    p = Patch(root, "fish/CatchCard.java")
    if MARK in p.src:
        return False
    read = nbt_int("card", "Pattern.TAG", "Pattern.NONE", dialect)
    p.sub1('''    public static boolean has(ItemStack fish) {
        return StackNbt.get(fish).contains(TAG);
    }
''',
           '''    public static boolean has(ItemStack fish) {
        return StackNbt.get(fish).contains(TAG);
    }
''' + CARD_READERS % {"read": read})
    p.sub1('''        c.putString("Eco", eco);
        c.putInt("Value", value);

        Random rng = new Random(level.getGameTime() * 31L + sp.getUUID().hashCode() + weightG);
        body(c, p, weightG, morph, rng, s.nature, s.variety);''',
           '''        c.putString("Eco", eco);

        Random rng = new Random(level.getGameTime() * 31L + sp.getUUID().hashCode() + weightG);
        // §pattern: the index is rolled before the price, because the price depends on it — a gem is
        // six times the fish, and the top band half again.
        int pattern = rollPattern(level, s.target, p, rng);
        c.putInt("Value", (int) Math.round(value * Pattern.value(pattern)));
        body(c, p, weightG, morph, rng, s.nature, s.variety, pattern);''')
    p.sub1('''        c.putInt("Value", value);
        c.putBoolean("Net", true);''',
           '''        c.putBoolean("Net", true);''')
    p.sub1('''        body(c, p, weightG, "", rng, (byte) -1, "");''',
           '''        // §pattern: a hauled fish has an index too — it came out of the same water.
        int pattern = rollPattern(level, pos, p, rng);
        c.putInt("Value", (int) Math.round(value * Pattern.value(pattern)));
        body(c, p, weightG, "", rng, (byte) -1, "", pattern);''')
    p.sub1('''    private static void body(CompoundTag c, FishProfile p, int weightG, String morph, Random rng,
                             byte natureIn, String variety) {''',
           '''    private static void body(CompoundTag c, FishProfile p, int weightG, String morph, Random rng,
                             byte natureIn, String variety, int pattern) {''')
    p.sub1('''        c.putString("Genes", g.toString());''',
           '''        c.putString("Genes", g.toString());
        c.putInt(Pattern.TAG, pattern);   // §pattern''')
    return p.save()


def fishmorph(root, d):
    p = Patch(root, "fish/FishMorph.java")
    if MARK in p.src:
        return False
    p.sub1('''    public static int tint(String speciesPath, double age, String morphId) {
        Age a = AGE.getOrDefault(speciesPath, AGE_DEFAULT);
''', MORPH_TINT)
    p.sub1('''    public static int koiTint(String variety, int layer) {
        int[] p = KOI_PAINT.get(variety.startsWith("koi_") ? variety.substring(4) : variety);
        if (p == null) p = KOI_PAINT.get("kohaku");     // a koi with no card yet is the archetype
        int c = layer >= 0 && layer < p.length ? p[layer] : -1;
        return 0xFF000000 | (c < 0 ? p[0] : c);
    }''',
           '''    public static int koiTint(String variety, int layer, int pattern) {
        int[] p = KOI_PAINT.get(variety.startsWith("koi_") ? variety.substring(4) : variety);
        if (p == null) p = KOI_PAINT.get("kohaku");     // a koi with no card yet is the archetype
        int c = layer >= 0 && layer < p.length ? p[layer] : -1;
        // §pattern: the band turns the hue, and a gem overrides every layer at once. A layer this fish
        // does not wear still takes the GROUND colour, painted AS ground, so it goes on vanishing into
        // the body whatever the pattern does — otherwise a bekko would grow a red field.
        return 0xFF000000 | Pattern.paint(c < 0 ? p[0] : c, pattern, layer > 0 && c >= 0);
    }''')
    return p.save()


def fishtint(root, dialect):
    p = Patch(root, "client/FishTint.java")
    if MARK in p.src:
        return False
    get = 'getStringOr("Variety", "")' if dialect == "26" else 'getString("Variety")'
    p.sub1('''            return tintIndex >= 0 && tintIndex < 4
                    ? FishMorph.koiTint(com.riverfishing.fish.CatchCard.of(stack).%s, tintIndex) : -1;
        }
        if (tintIndex != 0) return -1;
        return FishMorph.tint(sp.getPath(), FishItem.getAge(stack), FishItem.getMorph(stack));''' % get,
           '''            return tintIndex >= 0 && tintIndex < 4
                    ? FishMorph.koiTint(com.riverfishing.fish.CatchCard.of(stack).%s, tintIndex,
                            com.riverfishing.fish.CatchCard.pattern(stack)) : -1;   // §pattern
        }
        if (tintIndex != 0) return -1;
        // §pattern: an ordinary index leaves a perch a perch; a gem paints it one saturated colour.
        return FishMorph.tint(sp.getPath(), FishItem.getAge(stack), FishItem.getMorph(stack),
                com.riverfishing.fish.CatchCard.pattern(stack));''' % get)
    return p.save()


def tooltip(root, dialect):
    p = Patch(root, "client/FishCardClientTooltip.java")
    if MARK in p.src:
        return False
    get = 'getStringOr("Variety", "")' if dialect == "26" else 'getString("Variety")'
    p.sub1('''        String variety = c.%s;
        if (!variety.isEmpty()) row("variety", Component.translatable("variety.riverfishing." + variety), ORANGE);
''' % get,
           '''        String variety = c.%s;
        if (!variety.isEmpty()) row("variety", Component.translatable("variety.riverfishing." + variety), ORANGE);
''' % get + TOOLTIP_ROW)
    return p.save()


def keepnet(root, d):
    p = Patch(root, "fishing/KeepnetSale.java")
    if MARK in p.src:
        return False
    get = 'getStringOr("Variety", "")' if d == "26" else 'getString("Variety")'
    p.sub1('''        market = (int) Math.max(1, Math.round(market * com.riverfishing.fish.Genome.varietyValue(
                CatchCard.of(fish).%s)));''' % get,
           '''        // §pattern: and by its pattern index — a gem is six times the fish, wherever it is sold.
        market = (int) Math.max(1, Math.round(market * com.riverfishing.fish.Genome.varietyValue(
                CatchCard.of(fish).%s, CatchCard.pattern(fish))));''' % get)
    return p.save()


def journaldata(root, dialect):
    p = Patch(root, "fishing/JournalData.java")
    if MARK in p.src:
        return False
    fields = {"rl": "Identifier" if dialect == "26" else "ResourceLocation",
              "comp": "getCompoundOrEmpty" if dialect == "26" else "getCompound",
              "getint": 'getIntOr("patterns", 0)' if dialect == "26" else 'getInt("patterns")'}
    p.sub1('    /** True if the player has never landed this species before (call BEFORE {@link #record}). */',
           JOURNAL_DATA % fields
           + '\n    /** True if the player has never landed this species before (call BEFORE {@link #record}). */')
    return p.save()


def stockeddata(root, dialect):
    p = Patch(root, "fishing/StockedData.java")
    if MARK in p.src:
        return False
    read = nbt_int("t", '"Pattern"', "com.riverfishing.fish.Pattern.NONE", dialect)
    fields = {"read": read, "read2": read}
    p.sub1('    /** The clock starts the first day the condition holds; a brood that merely grew keeps its date. */',
           STOCKED % fields
           + '\n    /** The clock starts the first day the condition holds; a brood that merely grew keeps its date. */')
    return p.save()


def roe(root, dialect):
    p = Patch(root, "item/RoeItem.java")
    if MARK in p.src:
        return False
    fields = {"read": nbt_int("t", "TAG_PATTERN", "com.riverfishing.fish.Pattern.NONE", dialect)}
    p.sub1('''    public static final String TAG_LAID = "Laid";''',
           '''    public static final String TAG_LAID = "Laid";
    /** §pattern: the index the clutch runs at — inherited from the pair, not rolled. */
    public static final String TAG_PATTERN = com.riverfishing.fish.Pattern.TAG;''')
    p.sub1('''    public static int count(ItemStack s) {
        return StackNbt.get(s).%s;
    }''' % ('getIntOr(TAG_COUNT, 0)' if dialect == "26" else 'getInt(TAG_COUNT)'),
           '''    public static int count(ItemStack s) {
        return StackNbt.get(s).%s;
    }''' % ('getIntOr(TAG_COUNT, 0)' if dialect == "26" else 'getInt(TAG_COUNT)')
           + ROE_READERS % fields)
    add = "tooltip.accept" if dialect == "26" else "tooltip.add"
    getg = 'getStringOr(TAG_GENOME, "")' if dialect == "26" else 'getString(TAG_GENOME)'
    p.sub1('''        %s(Component.translatable("tooltip.riverfishing.genome", t.%s)
                .withStyle(ChatFormatting.DARK_GRAY));''' % (add, getg),
           '''        %s(Component.translatable("tooltip.riverfishing.genome", t.%s)
                .withStyle(ChatFormatting.DARK_GRAY));
        // §pattern: the clutch's index and the family it will hatch into — what the line is FOR.
        int pattern = pattern(stack);
        if (com.riverfishing.fish.Pattern.has(pattern)) {
            %s(Component.translatable("tooltip.riverfishing.pattern", pattern,
                    Component.translatable("pattern.riverfishing."
                            + com.riverfishing.fish.Pattern.family(pattern)))
                    .withStyle(ChatFormatting.DARK_GRAY));
        }''' % (add, getg, add))
    return p.save()


def aquarium(root, d):
    p = Patch(root, "block/AquariumBreeding.java")
    if MARK in p.src:
        return False
    p.sub1('        be.roe = RoeItem.of(FishItem.getSpecies(mother), genome, clutch(be, pair, p), now / DAY);',
           '''        be.roe = RoeItem.of(FishItem.getSpecies(mother), genome, clutch(be, pair, p), now / DAY);
        // §pattern: the clutch's index is the parents' mean, plus a mutation of about twelve. That is
        // the collector's line — a pair bred toward a family throws inside it nearly every time, and
        // the last few points toward a gem are always work.
        RoeItem.setPattern(be.roe, com.riverfishing.fish.Pattern.inherit(
                CatchCard.pattern(mother), CatchCard.pattern(pair[1]), RNG));''')
    p.sub1('        be.roe = FryItem.of(RoeItem.species(be.roe), g, n);',
           '''        int pattern = RoeItem.pattern(be.roe);   // §pattern: the index survives the egg
        be.roe = FryItem.of(RoeItem.species(be.roe), g, n);
        RoeItem.setPattern(be.roe, pattern);''')
    return p.save()


def fishingmanager(root, dialect):
    p = Patch(root, "fishing/FishingManager.java")
    if MARK in p.src:
        return False
    genes = 'card.getStringOr("Genes", "")' if dialect == "26" else 'card.getString("Genes")'
    p.sub1('        String genes = card == null ? "" : %s;' % genes,
           '''        String genes = card == null ? "" : %s;
        // §pattern: a released fish puts its line on the ledger, so the water hands the family back.
        int pattern = com.riverfishing.fish.CatchCard.pattern(card);''' % genes)
    p.sub1('''            long day = StockedData.worldDay(level);''',
           '''            long day = StockedData.worldDay(level);
            stocked.setPattern(region, species.getPath(), pattern);   // §pattern''')
    rl = "Identifier" if dialect == "26" else "ResourceLocation"
    p.sub1('''    public static void releaseFry(ServerLevel level, BlockPos pos, %s species, String genome, int count,
                                  @org.jetbrains.annotations.Nullable ServerPlayer thrower) {''' % rl,
           '''    public static void releaseFry(ServerLevel level, BlockPos pos, %s species, String genome, int count,
                                  @org.jetbrains.annotations.Nullable ServerPlayer thrower, int pattern) {''' % rl)
    p.sub1('''        release(level, pos, p, alive * 0.02, thrower, (stocked, region) ->
                stocked.addFry(region, species.getPath(), alive, StockedData.worldDay(level), genome,
                        thrower == null ? null : thrower.getUUID()));''',
           '''        release(level, pos, p, alive * 0.02, thrower, (stocked, region) -> {
            stocked.addFry(region, species.getPath(), alive, StockedData.worldDay(level), genome,
                    thrower == null ? null : thrower.getUUID());
            stocked.setPattern(region, species.getPath(), pattern);   // §pattern: the bred line
        });''')
    p.sub1('            com.riverfishing.item.StackNbt.mutate(fish, t -> t.put(com.riverfishing.fish.CatchCard.TAG, card));',
           '''            com.riverfishing.item.StackNbt.mutate(fish, t -> t.put(com.riverfishing.fish.CatchCard.TAG, card));
            // §pattern: the family goes in the journal — the collection board the index exists for.
            JournalData.recordPattern(sp, species, com.riverfishing.fish.CatchCard.pattern(card));''')
    return p.save()


def fishitem(root, d):
    p = Patch(root, "item/FishItem.java")
    if MARK in p.src:
        return False
    p.sub1('                                com.riverfishing.item.FryItem.count(stack), thrower);',
           '''                                com.riverfishing.item.FryItem.count(stack), thrower,
                                com.riverfishing.item.RoeItem.pattern(stack));   // §pattern''')
    if d == "26":
        # 26.x has no BEWLR: the four koi tints are numbers stamped onto the stack, so the pattern has
        # to reach them here rather than through FishTint.
        p.sub1('''        Identifier sp = getSpecies(stack);
        int tint = sp == null ? -1
                : com.riverfishing.fish.FishMorph.tint(sp.getPath(), getAge(stack), getMorph(stack));''',
               '''        Identifier sp = getSpecies(stack);
        // §pattern: the index rides in with the species — a gem paints the fish whatever it is.
        int pattern = com.riverfishing.fish.CatchCard.pattern(stack);
        int tint = sp == null ? -1
                : com.riverfishing.fish.FishMorph.tint(sp.getPath(), getAge(stack), getMorph(stack), pattern);''')
        p.sub1('''            colors = java.util.List.of(
                    com.riverfishing.fish.FishMorph.koiTint(variety, 0),
                    com.riverfishing.fish.FishMorph.koiTint(variety, 1),
                    com.riverfishing.fish.FishMorph.koiTint(variety, 2),
                    com.riverfishing.fish.FishMorph.koiTint(variety, 3));''',
               '''            colors = java.util.List.of(
                    com.riverfishing.fish.FishMorph.koiTint(variety, 0, pattern),
                    com.riverfishing.fish.FishMorph.koiTint(variety, 1, pattern),
                    com.riverfishing.fish.FishMorph.koiTint(variety, 2, pattern),
                    com.riverfishing.fish.FishMorph.koiTint(variety, 3, pattern));''')
    return p.save()


def keepnetscreen(root, d):
    """26.x only: the keepnet draws the fish itself instead of through the item renderer, so the one
    place a gem would otherwise stay grey is the net it is carried home in."""
    if d != "26":
        return False
    p = Patch(root, "client/KeepnetScreen.java")
    if MARK in p.src:
        return False
    p.sub1('''                com.riverfishing.fish.FishMorph.tint(sp.getPath(),
                        com.riverfishing.item.FishItem.getAge(stack),
                        com.riverfishing.item.FishItem.getMorph(stack)));''',
           '''                com.riverfishing.fish.FishMorph.tint(sp.getPath(),
                        com.riverfishing.item.FishItem.getAge(stack),
                        com.riverfishing.item.FishItem.getMorph(stack),
                        com.riverfishing.fish.CatchCard.pattern(stack)));   // §pattern''')
    return p.save()


def journalscreen(root, dialect):
    p = Patch(root, "client/JournalScreen.java")
    if MARK in p.src:
        return False
    # 26.x draws through GuiGraphicsExtractor and its own text(); everything else is GuiGraphics.
    fields = {"rl": "Identifier" if dialect == "26" else "ResourceLocation",
              "gg": "GuiGraphicsExtractor" if dialect == "26" else "GuiGraphics",
              "text": "text" if dialect == "26" else "drawString"}
    anchor = '    private int line(%s g, int y, String labelKey, String value) {' % fields["gg"]
    p.sub1('        y = morphRow(g, sp, id, y);',
           '''        y = morphRow(g, sp, id, y);
        y = patternRow(g, id, y);   // §pattern''')
    p.sub1(anchor, (JOURNAL_SCREEN % fields).lstrip("\n") + '\n' + anchor)
    return p.save()


STEPS = (genome, catchcard, fishmorph, fishtint, tooltip, keepnet, journaldata, stockeddata,
         roe, aquarium, fishingmanager, fishitem, keepnetscreen, journalscreen)


def main():
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    root, d = sys.argv[1], (sys.argv[2] if len(sys.argv) > 2 else "1211")
    done = [f.__name__ for f in STEPS if f(root, d)]
    print("%-10s %s" % (d, ", ".join(done) if done else "already patched"))
    return 0


if __name__ == "__main__":
    sys.exit(main())
