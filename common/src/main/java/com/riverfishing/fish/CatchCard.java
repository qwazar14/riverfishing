package com.riverfishing.fish;

import com.riverfishing.fishing.FishingSession;
import com.riverfishing.item.StackNbt;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Random;

/**
 * §catch-card (0.9.0): every landed fish carries the record of its own catch.
 *
 * <p>A contract that counted on a paper broke the moment the rod went into a chest; a fish that
 * remembers how it was caught cannot. So the landing writes one {@code Card} tag onto the fish — who,
 * when, where, on what, under what sky — and everything that wants to know later (the tooltip, the
 * journal, a contract at the counter) reads the fish. The card is SELF-CONTAINED on purpose: the size
 * class, the group and the lifestyle are copied in at the landing, because the client on a server has
 * no fish profiles and a tooltip that needed one would be blank exactly where it matters.
 *
 * <p>Three levels. The face of the card is what any angler can see by looking at the fish. Behind
 * Shift is HOW it was caught, and with it the fish's NATURE (timid, bold, greedy, wary — a
 * temperament, for the fights and the bites to read one day) and its GENES, written down openly
 * (four loci, a pair of alleles each, for the breeding that is coming; no mechanics yet, just the
 * truth on the card so a fish caught today is a valid parent tomorrow).
 */
public final class CatchCard {
    public static final String TAG = "Card";
    public static final String[] SIZE = {"baby", "juvenile", "adult", "big", "giant"};
    public static final String[] NATURE = {"timid", "wary", "greedy", "bold"};
    /**
     * §nature: what each temperament does, indexed like {@link #NATURE}. A timid fish takes the bait
     * briefly and fights soft; a wary one is nearly as quick to drop it; a greedy one holds on — the
     * longest bite window — and fights ordinarily; a bold one hits, thrashes and runs hard.
     */
    public static final double[] BITE_WINDOW = {0.75, 0.85, 1.35, 1.1};
    public static final double[] AGGRESSION = {0.75, 0.9, 1.0, 1.35};
    public static final double[] HEAD_SHAKE = {0.6, 0.8, 1.0, 1.6};

    /** A dial for the fish that bit, or 1.0 before a nature has been rolled. */
    public static double dial(byte nature, double[] table) {
        return nature < 0 || nature >= table.length ? 1.0 : table[nature];
    }

    /** timid, wary, greedy, bold — hunters lean bold, the rest lean timid. */
    public static byte rollNature(FishProfile p, Random rng) {
        boolean hunter = p != null && (p.group.equals("predator") || p.group.equals("big_game") || p.group.equals("sea"));
        int[] w = hunter ? new int[]{15, 20, 30, 35} : new int[]{35, 30, 20, 15};
        int roll = rng.nextInt(100);
        for (int i = 0, acc = 0; i < w.length; i++) { acc += w[i]; if (roll < acc) return (byte) i; }
        return 0;
    }
    private static final String[] BED = {"", "sand", "gravel", "clay", "mud", "rock", "other"};

    private CatchCard() {}

    public static CompoundTag of(ItemStack fish) {
        return StackNbt.get(fish).getCompound(TAG);
    }

    public static boolean has(ItemStack fish) {
        return StackNbt.get(fish).contains(TAG);
    }

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
        return card == null ? Pattern.NONE : card.contains(Pattern.TAG) ? card.getInt(Pattern.TAG) : Pattern.NONE;
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

    /**
     * The card for a fish being landed now. {@code eco} is native / settled / stocked as the stocking
     * model sees this water; {@code value} is what a fisherman pays for one today.
     */
    public static CompoundTag build(ServerPlayer sp, ServerLevel level, FishingSession s, FishProfile p,
                                    int weightG, ItemStack rod, List<String> baits, String eco, int value,
                                    String morph, String spot) {
        CompoundTag c = new CompoundTag();
        c.putString("Angler", sp.getGameProfile().getName());
        c.putLong("Day", level.getServer().overworld().getDayTime() / 24000L);
        c.putString("Date", java.time.LocalDate.now().toString());
        c.putString("Rod", s.rodClass.name().toLowerCase(java.util.Locale.ROOT));
        c.putString("RodItem", rod.getItem() instanceof com.riverfishing.item.RodItem
                ? net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(rod.getItem()).getPath() : "");
        c.putString("Bait", baits.isEmpty() ? "" : baits.get(0));
        var ctx = s.ctx;
        c.putString("Water", ctx == null ? "" : ctx.water.key());
        c.putString("Biome", level.getBiome(s.target).unwrapKey().map(k -> k.location().toString()).orElse(""));
        c.putString("Time", ctx == null ? "" : ctx.time.jsonKey());
        c.putString("Season", ctx == null || ctx.season == null ? "" : ctx.season.jsonKey());
        c.putString("Weather", ctx == null ? "" : ctx.weather.jsonKey());
        int bed = ctx == null ? 0 : ctx.bed;
        c.putString("Bed", bed > 0 && bed < BED.length ? BED[bed] : "");
        c.putString("Spot", spot == null ? "" : spot);
        c.putBoolean("Ice", s.iceFishing);
        c.putString("Eco", eco);

        Random rng = new Random(level.getGameTime() * 31L + sp.getUUID().hashCode() + weightG);
        // §pattern: the index is rolled before the price, because the price depends on it — a gem is
        // six times the fish, and the top band half again.
        int pattern = rollPattern(level, s.target, p, rng);
        c.putInt("Value", (int) Math.round(value * Pattern.value(pattern)));
        body(c, p, weightG, morph, rng, s.nature, s.variety, pattern);
        return c;
    }

    /**
     * §netted-card: a fish out of a net gets a card as well — it can be put in a pond and bred like
     * any other — and the card says so: {@code Net} true, {@code Poached} when the water was not the
     * netter's own. No rod, no bait, no bite: it was hauled.
     */
    public static CompoundTag netted(ServerPlayer sp, ServerLevel level, FishProfile p, int weightG,
                                     BlockPos pos, String eco, int value, boolean poached) {
        CompoundTag c = new CompoundTag();
        c.putString("Angler", sp.getGameProfile().getName());
        c.putLong("Day", level.getServer().overworld().getDayTime() / 24000L);
        c.putString("Date", java.time.LocalDate.now().toString());
        c.putString("Rod", "net");
        c.putString("RodItem", "");
        c.putString("Bait", "");
        c.putString("Water", com.riverfishing.water.WaterBodyCache.forLevel(level).get(level, pos).type().key());
        c.putString("Biome", level.getBiome(pos).unwrapKey().map(k -> k.location().toString()).orElse(""));
        c.putString("Time", com.riverfishing.engine.TimeOfDay.fromDayTime(level.getDayTime()).jsonKey());
        c.putString("Season", com.riverfishing.engine.Calendar.season(level).jsonKey());
        c.putString("Weather", level.isThundering() ? "thunder" : level.isRaining() ? "rain" : "clear");
        c.putString("Bed", "");
        c.putString("Spot", "");
        c.putBoolean("Ice", false);
        c.putString("Eco", eco);
        c.putBoolean("Net", true);
        c.putBoolean("Poached", poached);
        Random rng = new Random(level.getGameTime() * 31L + sp.getUUID().hashCode() + weightG);
        // §pattern: a hauled fish has an index too — it came out of the same water.
        int pattern = rollPattern(level, pos, p, rng);
        c.putInt("Value", (int) Math.round(value * Pattern.value(pattern)));
        body(c, p, weightG, "", rng, (byte) -1, "", pattern);
        return c;
    }

    /**
     * The half of the card that is the FISH — size class, kind, sex, nature, genes — shared by both.
     * {@code variety} is the carp scale variety the water drew ("" for everything else, and for a carp
     * that arrived without one: the species' own name then says which it is).
     */
    private static void body(CompoundTag c, FishProfile p, int weightG, String morph, Random rng,
                             byte natureIn, String variety, int pattern) {
        // What the profile knows, copied in: the client on a server never sees a profile.
        // §board-3: the size class is FishMorph.ageFraction — 0.5 at an ordinary specimen — so a
        // 1.15 kg pike is a juvenile, not a baby: the old bar measured against the record weight,
        // and against a 25 kg record everything you will ever catch was a baby.
        // Weight against an ORDINARY specimen of the species: under a fifth of it is a baby, under
        // half a juvenile, up to 1.2x adult, up to 2.5x big, past that a giant. ageFraction put a
        // 834 g pike (ordinary: 2 kg) at 0.11 — a baby — because it measures from the smallest
        // catchable fish, and nobody calls an 800 g pike a baby.
        double mean = p == null || p.weightMean <= 0 ? weightG : p.weightMean;
        double r = weightG / mean;
        double pct = r < 0.2 ? 0.1 : r < 0.5 ? 0.3 : r < 1.2 ? 0.5 : r < 2.5 ? 0.7 : 0.9;
        // A giant is a fish over the species' trophy bar — the same bar the trophy badge uses — so a
        // 869 g perch (ordinary: 250 g) is big, and the 1.7 kg one is the giant.
        int giantAt = com.riverfishing.item.FishItem.trophyThresholdG(p == null ? 0 : p.weightMin, p == null ? weightG : p.weightMax);
        c.putByte("Size", (byte) (weightG >= giantAt ? 4 : r < 0.2 ? 0 : r < 0.5 ? 1 : r < 1.2 ? 2 : 3));
        c.putString("Group", p == null ? "" : p.group);
        c.putString("Latin", p == null ? "" : p.latin);   // §cards-2
        c.putString("Life", p == null ? "" : p.depthPref);

        // The hidden two. Seeded off the fish itself so a duplicated stack is the same fish.
        c.putByte("Sex", (byte) rng.nextInt(2));
        // §nature: the temperament the fish fought with, rolled at the bite; a fish that arrived
        // without one (an old session) gets one now.
        byte nature = natureIn >= 0 ? natureIn : rollNature(p, rng);
        c.putByte("Nature", (byte) nature);
        // S size, C colour, V vigour, F fertility: a capital is the strong allele. Size follows the fish,
        // colour follows the morph, the other two are a coin.
        double[] pCap = {0.25 + 0.5 * pct, morph.isEmpty() ? 0.25 : 0.7, 0.5, 0.5};
        String loci = "SCVF";
        StringBuilder g = new StringBuilder();
        for (int i = 0; i < 4; i++) {
            char L = loci.charAt(i), l = Character.toLowerCase(L);
            char a = rng.nextDouble() < pCap[i] ? L : l, b = rng.nextDouble() < pCap[i] ? L : l;
            if (a == l && b == L) { a = L; b = l; }        // dominant first, as it is written
            if (i > 0) g.append(' ');
            g.append(a).append(b);
        }
        // §scale-genes: a carp carries two loci more — K scaled / k mirror, N nude (dead doubled) / n
        // normal — and they are not a coin: they are the variety this fish IS, written back as the
        // genotype that makes it. Every other species keeps the four pairs it always had, so nothing
        // else on a card, in a finder or in a contract changes shape.
        String v = variety.isEmpty() && p != null ? Genome.varietyOfSpecies(p.id.getPath()) : variety;
        // §koi-genes: a koi is a carp wearing three more loci — white ground, red hi, black sumi — and
        // every named variety the hobby trades in falls straight out of them. The water draws a
        // VARIETY; what is written down is the genotype that makes it, so the tank can cross it and
        // the card can be read back instead of believed.
        if (v.startsWith("koi_")) {
            g = new StringBuilder(Genome.koiGenome(g.toString(), v.substring(4), rng));
            c.putString("Variety", "koi_" + Genome.koiVariety(g.toString()));
        } else if (!v.isEmpty()) {
            boolean scaled = v.equals("scaled") || v.equals("linear");
            boolean nude = v.equals("linear") || v.equals("naked");
            g.append(' ').append(scaled ? (rng.nextBoolean() ? "KK" : "Kk") : "kk");
            g.append(' ').append(nude ? "Nn" : "nn");
            // Read back off the genotype rather than stored beside it: the card cannot then say
            // "mirror" over a pair of alleles that spell a leather carp.
            c.putString("Variety", Genome.carpVariety(g.toString()));
        }
        c.putString("Genes", g.toString());
        c.putInt(Pattern.TAG, pattern);   // §pattern
    }

    /** Does this fish's card meet a contract's terms? An empty term is no term. */
    public static boolean meets(ItemStack fish, CompoundTag terms) {
        if (!has(fish)) return false;
        CompoundTag c = of(fish);
        return term(terms.getString("Water"), c.getString("Water"))
                && term(terms.getString("Rod"), c.getString("Rod"))
                && term(terms.getString("Bait"), c.getString("Bait"))
                && term(terms.getString("Time"), c.getString("Time"));
    }

    private static boolean term(String want, String have) {
        return want.isEmpty() || want.equals(have);
    }
}
