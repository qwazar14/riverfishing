package com.riverfishing.fish;

import net.minecraft.resources.ResourceLocation;
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
    public static final String LOCI = "SCVFKN";

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

    private Genome() {}

    /** The variety a carp id names, or "" for a fish that is not a carp (nothing else has K/N). */
    public static String varietyOfSpecies(String path) {
        String v = VARIETY_OF_ID.get(path);
        return v != null ? v : "wild_carp".equals(path) ? "scaled" : "";
    }

    /**
     * §scale-genes: the id a draw is LANDED as. The water no longer hands out three species of the
     * same fish — a mirror, a linear or a leather roll comes ashore as {@code carp} with the genotype
     * that says which. The old ids stay registered, so a chest, a keepnet or a ledger full of them
     * keeps working exactly as before.
     */
    public static ResourceLocation landed(ResourceLocation drawn) {
        return isVarietyId(drawn.getPath()) ? com.riverfishing.RiverFishing.id("carp") : drawn;
    }

    /**
     * §scale-genes: an id the water no longer hands out — a scale variety of {@code carp} other than
     * the carp itself. The items stay registered (a chest full of mirror carp keeps working) but
     * nothing may ASK for one: not a contract, not the order of the day, not the all-species bar.
     */
    public static boolean isVarietyId(String path) {
        return VARIETY_OF_ID.containsKey(path) && !"carp".equals(path);
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
        return CatchCard.of(fish).getString("Genes");
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
        return locus == 'K' ? "KK" : locus == 'N' ? "nn" : "" + l + l;
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
