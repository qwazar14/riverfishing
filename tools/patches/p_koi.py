# -*- coding: utf-8 -*-
"""§koi-genes (0.9.0): koi are bred, not found — the seams in the files that already existed.

    py -X utf8 tools/patches/p_koi.py <repo root> [1211|1201|26]

Idempotent: every edit carries a §koi-genes marker and is skipped when it is already there. Anchors are
exact text; a missing one exits 1 with the anchor printed, because a silent no-op is how a port loses a
feature on one branch.

The five koi ids stay registered, priced, profiled and journal-paged — an old world must not lose a
fish. What changes is the DRAW: the water hands out `koi_carp` with three colour loci on its card, and
the five old ids are read as the varieties they always were.
"""
import io, os, sys

MARK = "§koi-genes"


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


# ---- the pieces that go in ------------------------------------------------------------------------

GENOME_BLOCK = '''
    // ---- §koi-genes: the koi's three colour loci ---------------------------------------------------

    /**
     * §koi-genes: the variety table, read top to bottom — the FIRST row a genotype matches names the
     * fish. Two letters per locus, in W R B order: {@code W_} at least one dominant allele, {@code WW}
     * homozygous dominant, {@code ww} no dominant at all.
     *
     * <p>Tancho is not a fourth locus. It is the one genotype that is homozygous at BOTH the white
     * ground and the red with no black to break the crown up, which is why it needs two homozygotes at
     * once, why it is prized, and why it sits above kohaku here: a kohaku that happens to be pure at
     * both IS a tancho.
     */
    private static final String[] KOI_TABLE = {
            "WWRRbb=tancho",
            "W_R_bb=kohaku",
            "W_R_B_=taisho_sanke",
            "wwR_B_=showa",
            "W_rrB_=bekko",
            "wwrrB_=asagi",
            "W_rrbb=platinum",
            "wwR_bb=hi_utsuri",
            "wwrrbb=karasu",
    };

    /**
     * §koi-genes: what the WATER gives, {@code variety=weight}. Platinum and tancho are missing on
     * purpose — both need a homozygote a wild pond never fixes, so they are BRED, not found. That is
     * the whole reason to keep a tank, and the reason a bred koi is worth more than a caught one.
     */
    private static final String[] WILD_KOI = {
            "kohaku=8", "taisho_sanke=5", "bekko=4", "showa=2", "asagi=2", "hi_utsuri=2", "karasu=1",
    };

    /** §koi-genes: the five ids the water used to hand out, and the variety each of them WAS. */
    private static final java.util.Map<String, String> KOI_OF_ID = java.util.Map.of(
            "carp_koi_kohaku", "kohaku", "carp_koi_tancho_sanke", "tancho",
            "carp_koi_showa_sanke", "showa", "carp_koi_asagi", "asagi", "carp_koi_bekko", "bekko");

    /** The nine variety names in table order — the aquarium window indexes them, so it is one list. */
    private static final java.util.List<String> KOI_NAMES = java.util.Arrays.stream(KOI_TABLE)
            .map(r -> r.substring(r.indexOf('=') + 1)).toList();

    public static java.util.List<String> koiVarieties() {
        return KOI_NAMES;
    }

    /**
     * §koi-genes: the koi variety an id names — the five old species ids, and the {@code koi_<variety>}
     * DRAW ids the water uses now. A draw id is never a registered item: it exists between the roll and
     * {@link #landed}, exactly long enough to say which koi came ashore. "" for anything else.
     */
    public static String koiOfId(String path) {
        String v = KOI_OF_ID.get(path);
        if (v != null) return v;
        return path.startsWith("koi_") && KOI_NAMES.contains(path.substring(4)) ? path.substring(4) : "";
    }

    /** True for every id that IS a koi: the species itself, the five old ones, and the draw ids. */
    public static boolean isKoiId(String path) {
        return "koi_carp".equals(path) || !koiOfId(path).isEmpty();
    }

    /**
     * §koi-genes: the variety three colour loci make. Read back off the genotype rather than stored
     * beside it, so a card can never say "kohaku" over alleles that spell a bekko.
     */
    public static String koiVariety(String genome) {
        for (String row : KOI_TABLE) {
            if (koiMatch(genome, row)) return row.substring(row.indexOf('=') + 1);
        }
        return "karasu";   // unreachable: the last row is ww rr bb, which everything else has excluded
    }

    private static boolean koiMatch(String genome, String row) {
        for (int i = 0; i < 3; i++) {
            char locus = "WRB".charAt(i), want = row.charAt(i * 2 + 1);
            boolean dom = dominant(genome, locus);
            if (want == '_' ? !dom : want == locus ? !(dom && pure(genome, locus)) : dom) return false;
        }
        return true;
    }

    /**
     * §koi-genes: the genome a fish DRAWN as a named variety carries — the caller's four (or six) pairs,
     * the carp's scale pair if it had none, then the three colour pairs that make the variety.
     *
     * <p>Written as a genotype rather than stored as a word: that is what lets the tank cross a koi like
     * any other fish and get a variety nobody wrote down. Which pairs are homozygous is a coin, because
     * that is the hidden half a breeder is actually working on — except on the last try, where every
     * "at least one dominant" locus is forced heterozygous so a kohaku can never fall out of the loop
     * still reading as the tancho above it.
     */
    public static String koiGenome(String base, String variety, Random rng) {
        String head = base.trim();
        if (pairs(head) < 6) head = head + " KK nn";     // a koi is a carp, and a bred koi is scaled
        String row = koiRow(variety);
        String out = head;
        for (int tries = 0; tries < 8; tries++) {
            StringBuilder b = new StringBuilder(head);
            for (int i = 0; i < 3; i++) {
                char L = "WRB".charAt(i), l = Character.toLowerCase(L), want = row.charAt(i * 2 + 1);
                boolean homo = want == L || (want == '_' && tries < 7 && rng.nextBoolean());
                b.append(' ').append(want == l ? "" + l + l : "" + L + (homo ? L : l));
            }
            out = b.toString();
            if (koiVariety(out).equals(variety)) return out;
        }
        return out;
    }

    private static String koiRow(String variety) {
        for (String row : KOI_TABLE) if (row.endsWith("=" + variety)) return row;
        return KOI_TABLE[1];      // kohaku: the archetype, and what an unknown name should look like
    }

    /** §koi-genes: the variety a WILD koi is, drawn from {@link #WILD_KOI}; {@code roll} is in [0,1). */
    public static String wildKoi(double roll) {
        int total = 0;
        for (String row : WILD_KOI) total += koiWeight(row);
        int at = (int) Math.floor(Math.max(0.0, Math.min(0.999999, roll)) * total), sum = 0;
        for (String row : WILD_KOI) {
            sum += koiWeight(row);
            if (at < sum) return row.substring(0, row.indexOf('='));
        }
        return "kohaku";
    }

    private static int koiWeight(String row) {
        return Integer.parseInt(row.substring(row.indexOf('=') + 1));
    }

    /**
     * §koi-genes: what a variety is worth against an ordinary fish of its species — the counter cannot
     * read a genotype off a trade slot, so the multiplier is applied wherever a price is worked out
     * from the STACK (the catch card's value, the keepnet sold over the counter). A tancho and a plain
     * platinum are the two the hobby pays for; a karasu is a black fish nobody ordered.
     */
    public static double varietyValue(String variety) {
        switch (variety) {
            case "koi_tancho": return 4.0;
            case "koi_platinum": return 3.0;
            case "koi_showa": case "koi_asagi": return 2.0;
            case "koi_taisho_sanke": case "koi_hi_utsuri": return 1.5;
            case "koi_karasu": return 0.8;
            default: return 1.0;
        }
    }
'''

FISHMORPH_BLOCK = '''
    /**
     * §koi-genes: the four colours ONE white koi sprite is painted with — ground, hi (red), sumi (black)
     * and the tancho crown — for a named variety. The three patch layers are cut out of the sprite
     * itself (tools/gen_koi_layers.py), so a layer this fish does not wear is handed the GROUND colour
     * and disappears into the body: that is what lets four layers paint nine varieties with one drawing.
     *
     * <p>Read on 1.20.1/1.21.1 through the registered item colour ({@code FishTint.itemColor}), and on
     * 26.x through the four {@code custom_model_data} colours {@code FishItem.stampIcon} writes.
     */
    public static int koiTint(String variety, int layer) {
        int[] p = KOI_PAINT.get(variety.startsWith("koi_") ? variety.substring(4) : variety);
        if (p == null) p = KOI_PAINT.get("kohaku");     // a koi with no card yet is the archetype
        int c = layer >= 0 && layer < p.length ? p[layer] : -1;
        return 0xFF000000 | (c < 0 ? p[0] : c);
    }

    /** ground, hi, sumi, crown; -1 means "the ground colour", i.e. the fish does not wear that layer. */
    private static final java.util.Map<String, int[]> KOI_PAINT = java.util.Map.of(
            "kohaku",       new int[]{0xF4F2EC, 0xD8342A, -1, -1},
            "taisho_sanke", new int[]{0xF4F2EC, 0xD8342A, 0x2A2622, -1},
            "showa",        new int[]{0x4A423C, 0xC8302A, 0x1E1A18, -1},
            "bekko",        new int[]{0xF4F2EC, -1, 0x2A2622, -1},
            "asagi",        new int[]{0x7C93AE, -1, 0x46586E, -1},
            "platinum",     new int[]{0xFFFDF6, -1, -1, -1},
            "hi_utsuri",    new int[]{0x3A322C, 0xD2382A, -1, -1},
            "karasu",       new int[]{0x2A2622, -1, -1, -1},
            "tancho",       new int[]{0xF4F2EC, -1, -1, 0xD8342A});
'''


def genome(root, d):
    p = Patch(root, "fish/Genome.java")
    if MARK in p.src:
        return False
    p.sub1('    public static final String LOCI = "SCVFKN";',
           '    // §koi-genes (0.9.0): three more, and koi alone carry them — W white ground, R red (hi),\n'
           '    // B black (sumi). Nine pairs on a koi, six on a carp, four on everything else; `cross`\n'
           '    // writes as many as the longer parent has, so nothing that never had them grows any.\n'
           '    public static final String LOCI = "SCVFKNWRB";')
    p.sub1("    private Genome() {}\n", "    private Genome() {}\n" + GENOME_BLOCK)
    p.sub1('''        String v = VARIETY_OF_ID.get(path);
        return v != null ? v : "wild_carp".equals(path) ? "scaled" : "";''',
           '''        String v = VARIETY_OF_ID.get(path);
        if (v != null) return v;
        // §koi-genes: a koi's variety word is the card's own key tail, prefixed so `kohaku` cannot
        // collide with a scale variety in the one `variety.riverfishing.*` namespace both use.
        String koi = koiOfId(path);
        return !koi.isEmpty() ? "koi_" + koi : "wild_carp".equals(path) ? "scaled" : "";''')
    p.sub1('        return isVarietyId(drawn.getPath()) ? com.riverfishing.RiverFishing.id("carp") : drawn;',
           '''        String p = drawn.getPath();
        // §koi-genes: the same trade, one species along — five koi ids and nine draw ids all come
        // ashore as `koi_carp`, and the genotype on the card says which of them it is.
        if (!koiOfId(p).isEmpty()) return com.riverfishing.RiverFishing.id("koi_carp");
        return isVarietyId(p) ? com.riverfishing.RiverFishing.id("carp") : drawn;''')
    p.sub1('        return VARIETY_OF_ID.containsKey(path) && !"carp".equals(path);',
           '        // §koi-genes: the five old koi ids are varieties of `koi_carp` in exactly this way.\n'
           '        return (VARIETY_OF_ID.containsKey(path) && !"carp".equals(path)) || !koiOfId(path).isEmpty();')
    p.sub1('''        return locus == 'K' ? "KK" : locus == 'N' ? "nn" : "" + l + l;''',
           '''        // §koi-genes: and a koi card written before the colour loci reads as a kohaku — the
        // archetype, and the only reading that leaves an old red-on-white fish looking like itself.
        return locus == 'K' ? "KK" : locus == 'N' ? "nn"
                : locus == 'W' ? "WW" : locus == 'R' ? "Rr" : locus == 'B' ? "bb" : "" + l + l;''')
    return p.save()


def catchcard(root, d):
    p = Patch(root, "fish/CatchCard.java")
    if MARK in p.src:
        return False
    p.sub1('''        String v = variety.isEmpty() && p != null ? Genome.varietyOfSpecies(p.id.getPath()) : variety;
        if (!v.isEmpty()) {''',
           '''        String v = variety.isEmpty() && p != null ? Genome.varietyOfSpecies(p.id.getPath()) : variety;
        // §koi-genes: a koi is a carp wearing three more loci — white ground, red hi, black sumi — and
        // every named variety the hobby trades in falls straight out of them. The water draws a
        // VARIETY; what is written down is the genotype that makes it, so the tank can cross it and
        // the card can be read back instead of believed.
        if (v.startsWith("koi_")) {
            g = new StringBuilder(Genome.koiGenome(g.toString(), v.substring(4), rng));
            c.putString("Variety", "koi_" + Genome.koiVariety(g.toString()));
        } else if (!v.isEmpty()) {''')
    return p.save()


def fishingmanager(root, d):
    p = Patch(root, "fishing/FishingManager.java")
    if MARK in p.src:
        return False
    rl = "Identifier" if d == "26" else "ResourceLocation"
    p.sub1('''    // §koi: the five ornamental koi are a hidden collectible — never in the normal bite pool
    // (their profile base is 0). Instead, a CARP-rig catch of a carp-family fish has a small chance
    // to turn out to be a koi. A cherry-grove pond is proper koi water, so there it's far likelier.
    private static final %s[] KOI = {
            com.riverfishing.RiverFishing.id("carp_koi_kohaku"),
            com.riverfishing.RiverFishing.id("carp_koi_tancho_sanke"),
            com.riverfishing.RiverFishing.id("carp_koi_showa_sanke"),
            com.riverfishing.RiverFishing.id("carp_koi_asagi"),
            com.riverfishing.RiverFishing.id("carp_koi_bekko"),
    };
''' % rl,
           '''    // §koi: the ornamental koi is a hidden collectible — never in the normal bite pool (its
    // profile base is 0). Instead, a CARP-rig catch of a carp-family fish has a small chance to turn
    // out to be a koi. A cherry-grove pond is proper koi water, so there it's far likelier.
    //
    // §koi-genes: the five ids that list used to hold were never five fish. They are one fish with
    // three colour loci, so the draw picks a VARIETY out of Genome's wild table instead.
''')
    p.sub1('        return random.nextDouble() < chance ? KOI[random.nextInt(KOI.length)] : picked;',
           '''        // §koi-genes: the id returned here is the variety's DRAW id and is never a registered item —
        // Genome.landed turns it into `koi_carp`, Genome.varietyOfSpecies into the word the card
        // writes the genotype from. Weighted to the common varieties: platinum and tancho are bred.
        return random.nextDouble() < chance
                ? com.riverfishing.RiverFishing.id(
                        "koi_" + com.riverfishing.fish.Genome.wildKoi(random.nextDouble()))
                : picked;''')
    p.sub1('        if (!session.species.getPath().startsWith("carp_koi")) {',
           '        if (!com.riverfishing.fish.Genome.isKoiId(session.species.getPath())) {   // §koi-genes')
    p.sub1('''            int value = base > 0 ? MarketData.get(lvl).price(lvl, path, base) : 0;''',
           '''            int value = base > 0 ? MarketData.get(lvl).price(lvl, path, base) : 0;
            // §koi-genes: a koi is priced by its VARIETY — that is what the whole hobby is. A trade
            // slot cannot read a genotype, so the multiplier lands here, on the card the buyer reads.
            value = (int) Math.round(value * com.riverfishing.fish.Genome.varietyValue(session.variety));''')
    if d == "26":
        # 26.x has no BEWLR: the tint is four numbers stamped onto the stack, and the koi's four are
        # read off the card — which is written one line above, so the stamp has to be re-run after it.
        p.sub1('            com.riverfishing.item.StackNbt.mutate(fish, t -> t.put(com.riverfishing.fish.CatchCard.TAG, card));',
               '            com.riverfishing.item.StackNbt.mutate(fish, t -> t.put(com.riverfishing.fish.CatchCard.TAG, card));\n'
               '            com.riverfishing.item.FishItem.stampIcon(fish);   // §koi-genes: the card carries the tint')
    return p.save()


def aquarium(root, d):
    p = Patch(root, "block/AquariumBreeding.java")
    if MARK in p.src:
        return False
    p.sub1('    public static final String[] VARIETIES = {"scaled", "mirror", "linear", "naked"};',
           '''    public static final String[] VARIETIES = varieties();

    /**
     * §koi-genes: the four scale varieties, then the nine koi colour varieties in Genome's own table
     * order — one list, so the window cannot drift from the genetics. Thirteen entries still index
     * inside the nibble each parent is packed into in {@code v[10]}.
     */
    private static String[] varieties() {
        java.util.List<String> v = new java.util.ArrayList<>(
                java.util.List.of("scaled", "mirror", "linear", "naked"));
        for (String k : Genome.koiVarieties()) v.add("koi_" + k);
        return v.toArray(new String[0]);
    }''')
    return p.save()


def journal(root, d):
    p = Patch(root, "fishing/JournalData.java")
    if MARK in p.src:
        return False
    p.sub1('''            if (!id.startsWith("carp_koi") && !com.riverfishing.fish.Genome.isVarietyId(id)
                    && root.getCompound''' if d != "26" else
           '''            if (!id.startsWith("carp_koi") && !com.riverfishing.fish.Genome.isVarietyId(id)
                    && root.getCompoundOrEmpty''',
           '''            // §koi-genes: `koi_carp` joins the five old ids as a hidden collectible — the bar is
            // the fish the water offers everyone, and a koi is not one of them.
            if (!com.riverfishing.fish.Genome.isKoiId(id) && !com.riverfishing.fish.Genome.isVarietyId(id)
                    && root.%s''' % ("getCompound" if d != "26" else "getCompoundOrEmpty"))
    p.sub1('            if (!id.startsWith("carp_koi") && !com.riverfishing.fish.Genome.isVarietyId(id)) n++;',
           '            if (!com.riverfishing.fish.Genome.isKoiId(id)\n'
           '                    && !com.riverfishing.fish.Genome.isVarietyId(id)) n++;   // §koi-genes')
    return p.save()


def contracts(root, d):
    p = Patch(root, "fishing/Contracts.java")
    if MARK in p.src:
        return False
    p.sub1('            if (com.riverfishing.fish.Genome.isVarietyId(sp)) continue;',
           '''            if (com.riverfishing.fish.Genome.isVarietyId(sp)) continue;
            // §koi-genes: and no order for a koi. The water gives one on carp tackle by cherry
            // blossom and effectively nowhere else — three of them is not a job, it is a wall.
            if (com.riverfishing.fish.Genome.isKoiId(sp)) continue;''')
    return p.save()


def villagers(root, d):
    p = Patch(root, "registry/ModVillagers.java")
    # NOT the plain marker: tools/wire_koi_carp.py already stamped it on the koi's trade line.
    if "isKoiId(sp)" in p.src:
        return False
    # §koi-genes: the counter BUYS a koi (see the trade table) but never ORDERS one — the order of the
    # day has to name a fish the day can actually produce. 26.x reads its buyable list back out of the
    # data-driven trade registry, so the same guard sits in a different shape there.
    if d == "26":
        p.sub1('''                    && !com.riverfishing.fish.Genome.isVarietyId(path.substring(cut + 5))) {''',
               '''                    && !com.riverfishing.fish.Genome.isVarietyId(path.substring(cut + 5))
                    && !com.riverfishing.fish.Genome.isKoiId(path.substring(cut + 5))) {   // §koi-genes''')
    else:
        p.sub1('                .filter(sp -> !com.riverfishing.fish.Genome.isVarietyId(sp)).sorted().toList();',
               '''                // §koi-genes: bought at the counter, never ordered — the order of the day has to
                // name a fish the day can actually produce.
                .filter(sp -> !com.riverfishing.fish.Genome.isVarietyId(sp)
                        && !com.riverfishing.fish.Genome.isKoiId(sp)).sorted().toList();''')
    return p.save()


def fishmorph(root, d):
    p = Patch(root, "fish/FishMorph.java")
    if MARK in p.src:
        return False
    p.sub1('    /** How much white to wash over the sprite, 0..1: young fish are pale, and so are pale morphs. */',
           FISHMORPH_BLOCK +
           '\n    /** How much white to wash over the sprite, 0..1: young fish are pale, and so are pale morphs. */')
    return p.save()


def fishtint(root, d):
    p = Patch(root, "client/FishTint.java")
    if MARK in p.src:
        return False
    rl = "Identifier" if d == "26" else "ResourceLocation"
    get = 'getStringOr("Variety", "")' if d == "26" else 'getString("Variety")'
    p.sub1('''        if (tintIndex != 0) return -1;
        %s sp = FishItem.getSpecies(stack);
        if (sp == null) return -1;                       // a creative-tab entry with no specimen data
        return FishMorph.tint(sp.getPath(), FishItem.getAge(stack), FishItem.getMorph(stack));''' % rl,
           '''        %s sp = FishItem.getSpecies(stack);
        if (sp == null) return -1;                       // a creative-tab entry with no specimen data
        // §koi-genes: one white koi sprite, four tinted layers — ground, red hi, black sumi and the
        // tancho crown — so every named variety is painted rather than drawn. The patch masks are cut
        // out of the sprite itself, so a layer this fish does not wear is given the ground colour and
        // vanishes into the body (tools/gen_koi_layers.py).
        if ("koi_carp".equals(sp.getPath())) {
            return tintIndex >= 0 && tintIndex < 4
                    ? FishMorph.koiTint(com.riverfishing.fish.CatchCard.of(stack).%s, tintIndex) : -1;
        }
        if (tintIndex != 0) return -1;
        return FishMorph.tint(sp.getPath(), FishItem.getAge(stack), FishItem.getMorph(stack));''' % (rl, get))
    return p.save()


def keepnet(root, d):
    p = Patch(root, "fishing/KeepnetSale.java")
    if MARK in p.src:
        return False
    get = 'getStringOr("Variety", "")' if d == "26" else 'getString("Variety")'
    p.sub1('        int market = MarketData.get(level).price(level, species.getPath(), base);',
           '''        int market = MarketData.get(level).price(level, species.getPath(), base);
        // §koi-genes: a koi's variety is most of what it is worth — a tancho is not just a big carp.
        market = (int) Math.max(1, Math.round(market * com.riverfishing.fish.Genome.varietyValue(
                CatchCard.of(fish).%s)));''' % get)
    return p.save()


def fishitem26(root, d):
    """26.x only: the icon tint is DATA there, so a koi stamps four colours instead of one."""
    if d != "26":
        return False
    p = Patch(root, "item/FishItem.java")
    if MARK in p.src:
        return False
    p.sub1('''        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_MODEL_DATA,
                new net.minecraft.world.item.component.CustomModelData(
                        java.util.List.of(getIconScale(stack)),
                        java.util.List.of(), java.util.List.of(), java.util.List.of(tint)));''',
           '''        java.util.List<Integer> colors = java.util.List.of(tint);
        // §koi-genes: a koi carries FOUR tints — ground, red hi, black sumi and the tancho crown — one
        // per layer of its icon, because one white sprite paints all nine named varieties. On 1.21.1
        // the same four numbers come from FishTint.itemColor; here they are data on the stack.
        if (sp != null && "koi_carp".equals(sp.getPath())) {
            String variety = com.riverfishing.fish.CatchCard.of(stack).getStringOr("Variety", "");
            colors = java.util.List.of(
                    com.riverfishing.fish.FishMorph.koiTint(variety, 0),
                    com.riverfishing.fish.FishMorph.koiTint(variety, 1),
                    com.riverfishing.fish.FishMorph.koiTint(variety, 2),
                    com.riverfishing.fish.FishMorph.koiTint(variety, 3));
        }
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_MODEL_DATA,
                new net.minecraft.world.item.component.CustomModelData(
                        java.util.List.of(getIconScale(stack)),
                        java.util.List.of(), java.util.List.of(), colors));''')
    return p.save()


STEPS = (genome, catchcard, fishingmanager, aquarium, journal, contracts, villagers,
         fishmorph, fishtint, keepnet, fishitem26)


def main():
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    root, d = sys.argv[1], (sys.argv[2] if len(sys.argv) > 2 else "1211")
    done = [f.__name__ for f in STEPS if f(root, d)]
    print("%-10s %s" % (d, ", ".join(done) if done else "already patched"))
    return 0


if __name__ == "__main__":
    sys.exit(main())
