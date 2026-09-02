package com.riverfishing.fish;

import net.minecraft.world.item.ItemStack;

import java.util.Random;

/**
 * §breeding (0.9.0): the four loci on every catch card, read and crossed.
 *
 * <p>The card already writes {@code "Ss Cc VV ff"} on every landed fish (S size, C colour, V vigour,
 * F fertility; a capital is the strong allele and is written first). That string is the ONLY genome
 * format — nothing here invents a second one, so a fish caught before breeding existed is a valid
 * parent. Mendel and nothing more: one allele from each parent per locus, no linkage, no mutation
 * (ponytail: mutation is a one-line coin in {@link #cross} when the colour morphs want it).
 */
public final class Genome {
    public static final String LOCI = "SCVF";

    private Genome() {}

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
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < LOCI.length(); i++) {
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

    /** The two alleles at {@code locus}, or the recessive pair when the string does not carry them. */
    private static String pair(String genome, char locus) {
        locus = Character.toUpperCase(locus);
        int i = LOCI.indexOf(locus);
        String[] t = genome == null ? new String[0] : genome.trim().split("\\s+");
        if (i < 0 || i >= t.length || t[i].length() != 2
                || Character.toUpperCase(t[i].charAt(0)) != locus
                || Character.toUpperCase(t[i].charAt(1)) != locus) {
            char l = Character.toLowerCase(locus);
            return "" + l + l;
        }
        return t[i];
    }
}
