# -*- coding: utf-8 -*-
"""§koi-metal §pattern-shift §koi-legacy: a fourth koi locus, the index moved under Shift, and the
five old koi ids out of the journal.

    py -X utf8 tools/patches/p_koi2.py <root> [1211|1201|26]

Three things the author asked for, in one pass because they touch the same two files.

1. §koi-metal — the koi get a METALLIC locus, G. It is dominant, so every koi already in a world (no G
   pair written, which reads recessive) stays exactly the variety it was; a fish that carries one turns
   the eight colour bases into eight more varieties, gold among them. Yamabuki, the gold koi, is a
   karasu — no white, no red, no black — with the lustre on: a dark ground made metallic comes up gold.
   Wild koi can be kujaku or gin bekko, so the allele exists in the world; gold, platinum and tancho
   still have to be bred.

2. §pattern-shift — the pattern index moves off the face of the card into the Shift block, beside the
   genes it belongs with.

3. §koi-legacy — the five ids koi used to be (carp_koi_kohaku and friends) leave the journal list. They
   stay registered, priced and whatever is in your chest; they are just not species any more, and a
   bestiary that lists them lists the same fish six times.
"""
import io, os, sys

ROOT = sys.argv[1]
D = sys.argv[2] if len(sys.argv) > 2 else "1211"
J = os.path.join(ROOT, "common/src/main/java/com/riverfishing/")


def rd(p): return io.open(p, encoding="utf-8").read()
def wr(p, s): io.open(p, "w", encoding="utf-8", newline="\n").write(s)


# ---- 1. Genome: the fourth locus -------------------------------------------------------------------
p = J + "fish/Genome.java"
s = rd(p)
if "koi-metal" not in s:
    assert 'String LOCI = "SCVFKNWRB"' in s, "LOCI moved"
    s = s.replace('String LOCI = "SCVFKNWRB"', 'String LOCI = "SCVFKNWRBG"', 1)

    a = s.index("    private static final String[] KOI_TABLE = {")
    b = s.index('"wwrrbb=karasu",') + len('"wwrrbb=karasu",\n    };')
    new_table = '''    private static final String[] KOI_TABLE = {
            // §koi-metal: G is the LUSTRE, and it reads before colour — a metallic fish is named for
            // being metallic first. A G* column means either way: the rows that carry it were the whole
            // table before the locus existed, and a koi with no G pair written reads recessive, so every
            // koi in every old world still names the variety it always did.
            "WWRRbbG*=tancho",
            "wwrrbbG_=yamabuki",
            "W_rrbbG_=ogon",
            "W_R_bbG_=sakura_ogon",
            "W_R_B_G_=yamatonishiki",
            "wwR_B_G_=kin_showa",
            "W_rrB_G_=gin_bekko",
            "wwrrB_G_=kujaku",
            "wwR_bbG_=kin_hi_utsuri",
            "W_R_bbG*=kohaku",
            "W_R_B_G*=taisho_sanke",
            "wwR_B_G*=showa",
            "W_rrB_G*=bekko",
            "wwrrB_G*=asagi",
            "W_rrbbG*=platinum",
            "wwR_bbG*=hi_utsuri",
            "wwrrbbG*=karasu",
    };

    /** §koi-metal: the loci a koi variety is read off, in the order the rows above write them. */
    private static final String KOI_LOCI = "WRBG";'''
    s = s[:a] + new_table + s[b:]

    old_wild = '            "kohaku=8", "taisho_sanke=5", "bekko=4", "showa=2", "asagi=2", "hi_utsuri=2", "karasu=1",'
    assert old_wild in s, "the wild table moved"
    s = s.replace(old_wild, old_wild + '''
            // §koi-metal: the lustre has to come from somewhere, or nobody could ever breed a gold
            // one. These two are its whole wild source — and the prizes (yamabuki, platinum, tancho)
            // are still exactly what a pond will not hand you.
            "kujaku=1", "gin_bekko=1",''', 1)

    old_match = '''    private static boolean koiMatch(String genome, String row) {
        for (int i = 0; i < 3; i++) {
            char locus = "WRB".charAt(i), want = row.charAt(i * 2 + 1);'''
    assert old_match in s, "koiMatch moved"
    s = s.replace(old_match, '''    private static boolean koiMatch(String genome, String row) {
        for (int i = 0; i < KOI_LOCI.length(); i++) {
            char locus = KOI_LOCI.charAt(i), want = row.charAt(i * 2 + 1);
            if (want == '*') continue;      // §koi-metal: this row does not care about that locus''', 1)

    old_gen = '''            StringBuilder b = new StringBuilder(head);
            for (int i = 0; i < 3; i++) {
                char L = "WRB".charAt(i), l = Character.toLowerCase(L), want = row.charAt(i * 2 + 1);
                boolean homo = want == L || (want == '_' && tries < 7 && rng.nextBoolean());
                b.append(' ').append(want == l ? "" + l + l : "" + L + (homo ? L : l));'''
    assert old_gen in s, "koiGenome moved"
    s = s.replace(old_gen, '''            StringBuilder b = new StringBuilder(head);
            for (int i = 0; i < KOI_LOCI.length(); i++) {
                char L = KOI_LOCI.charAt(i), l = Character.toLowerCase(L), want = row.charAt(i * 2 + 1);
                boolean homo = want == L || (want == '_' && tries < 7 && rng.nextBoolean());
                // §koi-metal: a locus the row does not care about gets the RECESSIVE pair — a wild
                // kohaku must not come out metallic by accident and read as a sakura ogon on its card.
                b.append(' ').append(want == l || want == '*' ? "" + l + l : "" + L + (homo ? L : l));''', 1)

    old_val = '''            case "koi_tancho": return 4.0;
            case "koi_platinum": return 3.0;'''
    assert old_val in s, "varietyValue moved"
    s = s.replace(old_val, '''            case "koi_tancho": return 4.0;
            // §koi-metal: metallic is worth more than the same colours matt, and yamabuki — the gold
            // one — is the fish the hobby is named for. None of the three can be netted out of a pond.
            case "koi_yamabuki": return 3.5;
            case "koi_ogon": case "koi_yamatonishiki": return 3.0;
            case "koi_sakura_ogon": case "koi_kin_showa": case "koi_kujaku": return 2.5;
            case "koi_gin_bekko": case "koi_kin_hi_utsuri": return 2.0;
            case "koi_platinum": return 3.0;''', 1)
    wr(p, s)
    print("  Genome: the metallic locus, eight new varieties, two of them wild")

# ---- 2. FishMorph: what the eight new ones are painted with ----------------------------------------
p = J + "fish/FishMorph.java"
s = rd(p)
if "koi-metal" not in s:
    a = s.index("    private static final java.util.Map<String, int[]> KOI_PAINT = java.util.Map.of(")
    tail = '"tancho",       new int[]{0xF4F2EC, -1, -1, 0xD8342A});'
    b = s.index(tail) + len(tail)
    new = '''    // §koi-metal: the eight metallic varieties are the same eight colour bases with the lustre on —
    // a brighter, cleaner ground and a hotter red, which is what metallic scales do to a colour.
    // Map.of tops out at ten pairs, so this is ofEntries now.
    private static final java.util.Map<String, int[]> KOI_PAINT = java.util.Map.ofEntries(
            java.util.Map.entry("kohaku",        new int[]{0xF4F2EC, 0xD8342A, -1, -1}),
            java.util.Map.entry("taisho_sanke",  new int[]{0xF4F2EC, 0xD8342A, 0x2A2622, -1}),
            java.util.Map.entry("showa",         new int[]{0x4A423C, 0xC8302A, 0x1E1A18, -1}),
            java.util.Map.entry("bekko",         new int[]{0xF4F2EC, -1, 0x2A2622, -1}),
            java.util.Map.entry("asagi",         new int[]{0x7C93AE, -1, 0x46586E, -1}),
            java.util.Map.entry("platinum",      new int[]{0xFFFDF6, -1, -1, -1}),
            java.util.Map.entry("hi_utsuri",     new int[]{0x3A322C, 0xD2382A, -1, -1}),
            java.util.Map.entry("karasu",        new int[]{0x2A2622, -1, -1, -1}),
            java.util.Map.entry("tancho",        new int[]{0xF4F2EC, -1, -1, 0xD8342A}),
            // yamabuki: a karasu's dark ground with the lustre on comes up GOLD. That is the gold koi.
            java.util.Map.entry("yamabuki",      new int[]{0xF0BE22, -1, -1, -1}),
            java.util.Map.entry("ogon",          new int[]{0xEDF0F2, -1, -1, -1}),
            java.util.Map.entry("sakura_ogon",   new int[]{0xFBF8F0, 0xE8483A, -1, -1}),
            java.util.Map.entry("yamatonishiki", new int[]{0xFBF8F0, 0xE8483A, 0x35302B, -1}),
            java.util.Map.entry("kin_showa",     new int[]{0x5A4C3A, 0xDC4630, 0x2A2420, -1}),
            java.util.Map.entry("gin_bekko",     new int[]{0xF6F3EA, -1, 0x35302B, -1}),
            java.util.Map.entry("kujaku",        new int[]{0xBFD0DE, 0xE07030, 0x51637A, -1}),
            java.util.Map.entry("kin_hi_utsuri", new int[]{0x453B30, 0xE2622A, -1, -1}));'''
    s = s[:a] + new + s[b:]
    wr(p, s)
    print("  FishMorph: the metallic paints")

# ---- 3. the tank window: twenty-one varieties no longer fit in a nibble -----------------------------
p = J + "block/AquariumBreeding.java"
s = rd(p)
if "koi-metal" not in s:
    old = """        // §scale-genes: the pair's scale varieties, ♀ then ♂, one per nibble (0 = not a carp). The
        // window names them, so a clutch that came out a quarter short says why on its own.
        v[10] = pair == null ? 0 : variety(pair[0]) | variety(pair[1]) << 4;"""
    assert old in s, "the variety packing moved"
    s = s.replace(old, """        // §scale-genes: the pair's varieties, ♀ then ♂, one per BYTE (0 = not a carp). The window
        // names them, so a clutch that came out a quarter short says why on its own.
        // §koi-metal: a byte each, not a nibble — the metallic locus took the list past sixteen.
        v[10] = pair == null ? 0 : variety(pair[0]) | variety(pair[1]) << 8;""", 1)
    old = """     * §koi-genes: the four scale varieties, then the nine koi colour varieties in Genome's own table
     * order — one list, so the window cannot drift from the genetics. Thirteen entries still index
     * inside the nibble each parent is packed into in {@code v[10]}."""
    assert old in s, "the VARIETIES doc moved"
    s = s.replace(old, """     * §koi-genes: the four scale varieties, then every koi variety in Genome's own table order — one
     * list, so the window cannot drift from the genetics. §koi-metal took it past sixteen, so each
     * parent rides in a BYTE of {@code v[10]} rather than a nibble.""", 1)
    wr(p, s)
    print("  AquariumBreeding: a byte per parent")

p = J + "client/AquariumScreen.java"
s = rd(p)
if "koi-metal" not in s:
    old = '    private static final String[] VARIETIES = { "scaled", "mirror", "linear", "naked" };'
    assert old in s, "the screen's variety list moved"
    s = s.replace(old, '''    // §koi-metal: the window used to keep its own four-name list, so a koi pair in the tank showed
    // nothing at all. One list now, the breeding rules' own, and it cannot drift again.
    private static final String[] VARIETIES = com.riverfishing.block.AquariumBreeding.VARIETIES;''', 1)
    old = '        String pair = variety(menu.data(10) & 15), sire = variety(menu.data(10) >> 4);'
    assert old in s, "the screen's unpacking moved"
    s = s.replace(old, '        String pair = variety(menu.data(10) & 255), sire = variety(menu.data(10) >> 8);', 1)
    s = s.replace("    /** §scale-genes: one nibble of data(10) as the variety's own name, or \"\" when the fish is no carp. */",
                  "    /** §scale-genes: one byte of data(10) as the variety's own name, or \"\" when the fish is no carp. */", 1)
    wr(p, s)
    print("  AquariumScreen: one list with the breeding rules")

# ---- 4. the index goes under Shift, beside the genes ------------------------------------------------
p = J + "client/FishCardClientTooltip.java"
s = rd(p)
if "pattern-shift" not in s:
    a = s.index("        // §pattern: the index this fish came out at")
    b = s.index("        // §nature: the counter buys PRIME fish")
    block = s[a:b]
    s = s[:a] + s[b:]
    block = block.replace("        // §pattern: the index this fish came out at",
                          "        // §pattern-shift: under Shift, beside the genes. On most fish the index changes nothing\n"
                          "        // you can see — only a carp or a koi is painted by it — so it is not face material.\n"
                          "        // §pattern: the index this fish came out at")
    anchor = '        row("genes",'
    i = s.index(anchor)
    s = s[:i] + block + s[i:]
    wr(p, s)
    print("  card: the pattern index under Shift")

# ---- 5. the five old koi ids out of the journal list ------------------------------------------------
p = J + "client/JournalScreen.java"
s = rd(p)
if "koi-legacy" not in s:
    old = "    private static final String[] SPECIES = ModItems.FISH_SPECIES;"
    assert old in s, "the journal's species list moved"
    s = s.replace(old, '''    /**
     * §koi-legacy: every registered species EXCEPT the five ids koi used to be. They are still items,
     * still priced, still whatever is in your chest — but they stopped being species when §koi-genes
     * made them varieties of one, and a bestiary that lists them lists the same fish six times.
     */
    private static final String[] SPECIES = java.util.Arrays.stream(ModItems.FISH_SPECIES)
            .filter(sp -> !com.riverfishing.fish.Genome.isKoiId(sp) || "koi_carp".equals(sp))
            .toArray(String[]::new);''', 1)
    wr(p, s)
    print("  journal: the old koi ids hidden")

# ---- 6. lang ---------------------------------------------------------------------------------------
NAMES = {
    "en_us": [("yamabuki", "Yamabuki Ogon"), ("ogon", "Ogon"), ("sakura_ogon", "Sakura Ogon"),
              ("yamatonishiki", "Yamatonishiki"), ("kin_showa", "Kin Showa"), ("gin_bekko", "Gin Bekko"),
              ("kujaku", "Kujaku"), ("kin_hi_utsuri", "Kin Hi Utsuri")],
    "ru_ru": [("yamabuki", "Ямабуки Огон"), ("ogon", "Огон"),
              ("sakura_ogon", "Сакура Огон"),
              ("yamatonishiki", "Яматонисики"),
              ("kin_showa", "Кин Сёва"), ("gin_bekko", "Гин Бекко"),
              ("kujaku", "Кудзяку"),
              ("kin_hi_utsuri", "Кин Хи Уцури")],
    "uk_ua": [("yamabuki", "Ямабукі Оґон"), ("ogon", "Оґон"),
              ("sakura_ogon", "Сакура Оґон"),
              ("yamatonishiki", "Яматонісікі"),
              ("kin_showa", "Кін Сьова"), ("gin_bekko", "Ґін Бекко"),
              ("kujaku", "Кудзяку"),
              ("kin_hi_utsuri", "Кін Хі Уцурі")],
}
for loc, rows in NAMES.items():
    p = os.path.join(ROOT, "common/src/main/resources/assets/riverfishing/lang", loc + ".json")
    s = rd(p)
    if '"variety.riverfishing.koi_yamabuki"' in s:
        continue
    i = s.index('"variety.riverfishing.koi_tancho":')
    end = s.index("\n", i) + 1
    add = "".join('  "variety.riverfishing.koi_%s": "%s",\n' % (k, v) for k, v in rows)
    wr(p, s[:end] + add + s[end:])
    print("  lang %s: +%d" % (loc, len(rows)))
print("done (%s)" % D)
