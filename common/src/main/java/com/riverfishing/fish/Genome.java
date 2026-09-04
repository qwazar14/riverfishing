package com.riverfishing.fish;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.Random;

/**
 * §breeding (0.9.0): the loci on every catch card, read and crossed — four on every fish, and two more
 * on a carp, whose scale cover is a real Mendelian pair of them (§scale-genes).
 *
 * <p>The card already writes {@code "Ss Cc VV ff"} on every landed fish (S size, C colour, V vigour,
 * F fertility; a capital is the strong allele and is written first). That string is the ONLY genome
 * format — nothing here invents a second one, so a fish caught before breeding existed is a valid
 * parent. Mendel and nothing more: one allele from each parent per locus, no linkage, no mutation
 * (ponytail: mutation is a one-line coin in {@link #cross} when the colour morphs want it).
 */
public final class Genome {
    /**
     * §scale-genes (0.9.0): six loci now — K and N are the carp's scale cover, the two-locus Mendelian
     * system the mod used to spell as three separate species. K scaled (dominant) / k mirror;
     * N nude (dominant, and DEAD when homozygous) / n normal. Only the carp family carries them:
     * every other fish keeps the four pairs it always had.
     */
    // §koi-genes (0.9.0): three more, and koi alone carry them — W white ground, R red (hi),
    // B black (sumi). Nine pairs on a koi, six on a carp, four on everything else; `cross`
    // writes as many as the longer parent has, so nothing that never had them grows any.
    public static final String LOCI = "SCVFKNWRB";

    /** The four loci EVERY fish carries. K and N belong to the carp alone (§scale-genes). */
    public static final String COMMON_LOCI = "SCVF";

    /**
     * §scale-genes: the four ids that are one fish wearing different scales, and the variety each
     * names. The sazan ({@code wild_carp}) is deliberately NOT here: it is scaled like the rest, but it
     * is the wild form — its own price, its own fight — so it keeps its own id and only borrows the
     * variety word.
     */
    private static final java.util.Map<String, String> VARIETY_OF_ID = java.util.Map.of(
            "carp", "scaled", "mirror_carp", "mirror", "linear_carp", "linear", "naked_carp", "naked");

    /**
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

    private Genome() {}

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

    /**
     * §pattern: the variety's multiplier and the pattern's together — one call, because every price in
     * the mod wants both and a price that forgot one is a fish worth six times what the counter pays.
     */
    public static double varietyValue(String variety, int pattern) {
        return varietyValue(variety) * Pattern.value(pattern);
    }

    /** The variety a carp id names, or "" for a fish that is not a carp (nothing else has K/N). */
    public static String varietyOfSpecies(String path) {
        String v = VARIETY_OF_ID.get(path);
        if (v != null) return v;
        // §koi-genes: a koi's variety word is the card's own key tail, prefixed so `kohaku` cannot
        // collide with a scale variety in the one `variety.riverfishing.*` namespace both use.
        String koi = koiOfId(path);
        return !koi.isEmpty() ? "koi_" + koi : "wild_carp".equals(path) ? "scaled" : "";
    }

    /**
     * §scale-genes: the id a draw is LANDED as. The water no longer hands out three species of the
     * same fish — a mirror, a linear or a leather roll comes ashore as {@code carp} with the genotype
     * that says which. The old ids stay registered, so a chest, a keepnet or a ledger full of them
     * keeps working exactly as before.
     */
    public static Identifier landed(Identifier drawn) {
        String p = drawn.getPath();
        // §koi-genes: the same trade, one species along — five koi ids and nine draw ids all come
        // ashore as `koi_carp`, and the genotype on the card says which of them it is.
        if (!koiOfId(p).isEmpty()) return com.riverfishing.RiverFishing.id("koi_carp");
        return isVarietyId(p) ? com.riverfishing.RiverFishing.id("carp") : drawn;
    }

    /**
     * §scale-genes: an id the water no longer hands out — a scale variety of {@code carp} other than
     * the carp itself. The items stay registered (a chest full of mirror carp keeps working) but
     * nothing may ASK for one: not a contract, not the order of the day, not the all-species bar.
     */
    public static boolean isVarietyId(String path) {
        // §koi-genes: the five old koi ids are varieties of `koi_carp` in exactly this way.
        return (VARIETY_OF_ID.containsKey(path) && !"carp".equals(path)) || !koiOfId(path).isEmpty();
    }

    /**
     * §scale-genes: the phenotype a K/N pair makes.
     *
     * <p>{@code K_ nn} scaled · {@code kk nn} mirror · {@code K_ Nn} linear · {@code kk Nn} leather.
     * A genome with no K/N pair at all (every fish caught before 0.9.0) reads as scaled, which is what
     * {@link #pair} gives it.
     */
    public static String carpVariety(String genome) {
        boolean nude = dominant(genome, 'N');
        return dominant(genome, 'K') ? (nude ? "linear" : "scaled") : (nude ? "naked" : "mirror");
    }

    /** §scale-genes: NN — the egg never develops. Why leather × leather loses a quarter of its clutch. */
    public static boolean lethal(String genome) {
        return dominant(genome, 'N') && pure(genome, 'N');
    }

    /** The genome on a fish's catch card, or "" for a fish that has none (netted, traded, spawned in). */
    public static String of(ItemStack fish) {
        return CatchCard.of(fish).getStringOr("Genes", "");
    }

    /**
     * One random allele from each parent per locus, dominant written first. A parent whose string is
     * empty or malformed contributes the recessive pair at that locus — a fish with no known genes is
     * an ordinary fish, never a hidden champion.
     */
    public static String cross(String mother, String father, Random rng) {
        // §scale-genes compatibility: the string grew from four pairs to six, and a cross writes as many
        // as the longer parent carries (never fewer than four). So a perch stays a perch — four pairs,
        // exactly as before — while a carp caught last year, whose card stops at "ff", breeds as the
        // scaled fish it looks like: pair() hands out KK and nn for the loci it never had written down.
        int n = Math.max(4, Math.max(pairs(mother), pairs(father)));
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < n; i++) {
            char locus = LOCI.charAt(i);
            char a = pair(mother, locus).charAt(rng.nextInt(2));
            char b = pair(father, locus).charAt(rng.nextInt(2));
            if (Character.isLowerCase(a) && Character.isUpperCase(b)) { char t = a; a = b; b = t; }
            if (i > 0) out.append(' ');
            out.append(a).append(b);
        }
        return out.toString();
    }

    /** At least one strong allele at the locus. */
    public static boolean dominant(String genome, char locus) {
        String p = pair(genome, locus);
        return Character.isUpperCase(p.charAt(0)) || Character.isUpperCase(p.charAt(1));
    }

    /** Both alleles the same (FF or ff, never Ff). */
    public static boolean pure(String genome, char locus) {
        String p = pair(genome, locus);
        return p.charAt(0) == p.charAt(1);
    }

    /**
     * Eggs in one clutch. The base is a reading of the mother's weight against an ordinary fish of the
     * species — 10 for a minnow of one, 40 for a fish twice the ordinary weight — then fertility:
     * FF lays half again as much, ff barely more than half. {@code rng} is in the signature by the
     * breeding contract and unused: the clutch is what the mother IS, not what she rolled that day.
     */
    public static int clutch(String motherGenome, int motherWeightG, FishProfile p, Random rng) {
        double mean = p == null || p.weightMean <= 0 ? motherWeightG : p.weightMean;
        double r = mean <= 0 ? 1.0 : Math.max(0.0, Math.min(2.0, motherWeightG / mean));
        double base = 10 + 30 * r / 2;
        double fertility = !dominant(motherGenome, 'F') ? 0.6 : pure(motherGenome, 'F') ? 1.5 : 1.0;
        return Math.max(4, (int) Math.round(base * fertility));
    }

    /**
     * The two alleles at {@code locus}, or the default pair when the string does not carry them.
     *
     * <p>The default is the recessive pair — a fish with no known genes is an ordinary fish — EXCEPT at
     * the scale loci: a fish whose card predates §scale-genes is a fully scaled wild carp, so it reads
     * as {@code KK nn}. Anything else would turn every carp already in a chest into a hidden mirror.
     */
    private static String pair(String genome, char locus) {
        locus = Character.toUpperCase(locus);
        String t = token(genome, LOCI.indexOf(locus));
        if (t != null) return t;
        char l = Character.toLowerCase(locus);
        // §koi-genes: and a koi card written before the colour loci reads as a kohaku — the
        // archetype, and the only reading that leaves an old red-on-white fish looking like itself.
        return locus == 'K' ? "KK" : locus == 'N' ? "nn"
                : locus == 'W' ? "WW" : locus == 'R' ? "Rr" : locus == 'B' ? "bb" : "" + l + l;
    }

    /** The i-th locus as the string actually writes it, or null when it does not carry that pair. */
    private static String token(String genome, int i) {
        String[] t = genome == null ? new String[0] : genome.trim().split("\\s+");
        if (i < 0 || i >= t.length || t[i].length() != 2) return null;
        char locus = LOCI.charAt(i);
        return Character.toUpperCase(t[i].charAt(0)) == locus
                && Character.toUpperCase(t[i].charAt(1)) == locus ? t[i] : null;
    }

    /** How many loci this string actually carries: 4 for anything written before §scale-genes. */
    private static int pairs(String genome) {
        int n = 0;
        for (int i = 0; i < LOCI.length(); i++) if (token(genome, i) != null) n = i + 1;
        return n;
    }
}

// §ported26
