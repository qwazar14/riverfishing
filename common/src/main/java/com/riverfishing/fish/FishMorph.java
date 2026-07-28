package com.riverfishing.fish;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * §morph (0.7.0): what colour a fish is, and why — one table, no new drawings.
 *
 * <p>Two things live here, and they are deliberately the same mechanism.
 *
 * <p><b>Age shading</b> applies to every fish that exists. A specimen's colour is read off its own size:
 * young fish are pale and silvery, old ones darken and take on their species' adult colour. A young bream
 * is bright silver; a big old one is deep bronze — that is not an invention, it is the first thing anyone
 * who has held both notices. Two knobs do it: how much white to wash a young fish with, and what colour to
 * multiply an old one by.
 *
 * <p><b>Morphs</b> are discrete, documented, real phenomena that sit on top: xanthism, albinism, leucism,
 * the scale patterns of carp strains, natural hybrids, lamprey scars, the hooked jaw of an old male, the
 * stunted fish of an over-fished pond and the deep-bodied one of an over-stocked one. Each is a collection
 * entry of its own. NONE of them is a new sprite — every one is the species' own 256px icon under a
 * different tint, the same pipeline the dyed lures already use.
 *
 * <p>What makes them worth having is the <b>trigger</b> column. Every morph hangs off world state the mod
 * already tracks and has so far kept invisible: a hammered swim hands out stunted fish, a long-settled
 * stocked water hands out colour morphs, a big fish from unfished water carries the marks of having got
 * old. The pressure and stocking simulations finally have a face.
 *
 * <p>The whole table is code rather than a datapack on purpose: the tint has to be identical on the client
 * (which paints the fish) and the server (which rolls it), and a multiplayer client never runs a
 * server-data reload listener. One table, no sync, nothing to drift.
 */
public final class FishMorph {

    /** What has to be true of the water, or of the fish, for a morph to be possible at all. */
    public enum Trigger {
        /** Rare everywhere, conditioned on nothing. Genuine one-in-many-hundreds oddities. */
        ANYWHERE,
        /** A water this species was stocked into and has settled in — an old, established population. */
        SETTLED,
        /** A swim that has been fished down: the surplus for this species is negative. */
        OVERFISHED,
        /** A swim carrying far more of this species than it should — an over-stocked pond. */
        CROWDED,
        /** A big specimen for its species: it has been alive a long time. */
        OLD
    }

    /**
     * One morph.
     *
     * @param id      the lang key suffix and the journal's own record of it
     * @param tint    multiply colour over the species' sprite — can darken and shift hue, never lighten
     * @param pale    0..1 wash of white, which is how a fish gets LIGHTER than its own sprite (albinos)
     * @param trigger the world state that allows it
     * @param chance  probability per landed fish once the trigger is satisfied
     * @param species the species it occurs in; empty means every species
     */
    public record Def(String id, int tint, float pale, Trigger trigger, double chance, Set<String> species) {
        boolean appliesTo(String speciesPath) {
            return species.isEmpty() || species.contains(speciesPath);
        }
    }

    private static Set<String> of(String... ids) {
        return Set.of(ids);
    }

    /**
     * The table. Order is priority order: the first morph whose trigger holds and whose roll comes up wins,
     * so the specific and the condition-driven are listed above the merely rare.
     */
    private static final List<Def> TABLE = List.of(
            // --- what the simulations look like -------------------------------------------------------
            // A pond fished to the bone grows fish that never fill out. This is the single most common
            // morph on purpose: it is the mod telling you what you did to the water.
            new Def("stunted", 0x9C9C8C, 0.05f, Trigger.OVERFISHED, 0.40, of()),
            // Too many fish, too much feed, not enough room: short, deep-bodied, humped in front of the
            // dorsal. Every carp farmer has seen it.
            new Def("humpbacked", 0xB8A98C, 0.00f, Trigger.CROWDED, 0.28, of()),
            // An old male that has spent seasons on the spawning grounds: the lower jaw hooks (kype).
            new Def("hook_jawed", 0xB79A5E, 0.00f, Trigger.OLD, 0.30,
                    of("salmon", "pink_salmon", "trout", "rainbow_trout", "char", "taimen", "lenok", "pike")),
            // Lampreys ride big fish and leave round scars. Common on old pike and on anything that shares
            // water with them.
            new Def("lamprey_scarred", 0xA8AE86, 0.00f, Trigger.OLD, 0.14,
                    of("pike", "taimen", "salmon", "sturgeon", "sterlet", "cod", "saithe", "burbot")),

            // --- carp strains: the same fish, three coats ---------------------------------------------
            // The wild, fully-scaled coat resurfacing in a stocked pond of mirror carp.
            new Def("fully_scaled", 0xC9A96A, 0.00f, Trigger.SETTLED, 0.12,
                    of("carp", "mirror_carp", "wild_carp")),
            // The "Hungarian" linear: one row of scales along the lateral line and nothing else.
            new Def("linear", 0xD8CBA6, 0.12f, Trigger.SETTLED, 0.10,
                    of("carp", "mirror_carp", "wild_carp")),
            // Xanthism — the gold pigment mutation. Golden tench and golden rudd are sold as ornamentals
            // for exactly this reason, and they turn up in established stocked water.
            new Def("xanthic", 0xE8B23C, 0.10f, Trigger.SETTLED, 0.07,
                    of("tench", "roach", "rudd", "crucian_carp", "carp")),

            // --- natural hybrids ---------------------------------------------------------------------
            // Roach × bream is the classic: bream's body, roach's fins, and it fools everyone.
            new Def("hybrid_roach_bream", 0xD5D2BE, 0.15f, Trigger.ANYWHERE, 0.020,
                    of("roach", "bream", "white_bream")),
            // Ide × chub, the other one that starts arguments on the bank.
            new Def("hybrid_ide_chub", 0xD9C9A8, 0.10f, Trigger.ANYWHERE, 0.018, of("ide", "chub")),

            // --- the genuine rarities ----------------------------------------------------------------
            // Leucism: pigment gone, eyes normal. Bream and their relatives throw these.
            new Def("leucistic", 0xF2ECDC, 0.45f, Trigger.ANYWHERE, 0.006,
                    of("bream", "white_bream", "blue_bream", "white_eye_bream", "roach", "perch")),
            // Koi colouring surfacing in an ordinary carp — the ancestry showing through.
            new Def("koi_coloured", 0xF0785A, 0.25f, Trigger.ANYWHERE, 0.004,
                    of("carp", "wild_carp", "mirror_carp", "crucian_carp")),
            // Albinism. The rarest thing in the table, and it belongs on the catfish: an albino wels is
            // the fish every angler has seen a photograph of and nobody has caught.
            new Def("albino", 0xFFEFE6, 0.80f, Trigger.ANYWHERE, 0.002,
                    of("catfish", "channel_catfish", "sturgeon", "sterlet", "burbot", "eel"))
    );

    /** Every morph a species can ever show, in table order — the journal's row for that fish. */
    public static List<Def> forSpecies(String speciesPath) {
        List<Def> out = new ArrayList<>(4);
        for (Def d : TABLE) {
            if (d.appliesTo(speciesPath)) out.add(d);
        }
        return out;
    }

    public static Def byId(String id) {
        for (Def d : TABLE) {
            if (d.id.equals(id)) return d;
        }
        return null;
    }

    /** Total collection entries: one per species, plus one per morph that species can show. */
    public static int totalEntries(String[] allSpecies) {
        int n = 0;
        for (String sp : allSpecies) n += 1 + forSpecies(sp).size();
        return n;
    }

    /**
     * Roll this specimen's morph, or null for an ordinary fish.
     *
     * @param age       0..1, how big this fish is for its species (see {@link #ageFraction})
     * @param settled   this species was stocked here and has taken hold
     * @param surplus   this species' local stock balance: negative is fished down, above 1 is crowded
     */
    public static Def roll(String speciesPath, double age, boolean settled, double surplus,
                           RandomSource rng) {
        for (Def d : TABLE) {
            if (!d.appliesTo(speciesPath)) continue;
            boolean allowed = switch (d.trigger) {
                case ANYWHERE -> true;
                case SETTLED -> settled;
                case OVERFISHED -> surplus < -0.25 && age < 0.45;
                case CROWDED -> surplus > 0.5 && age > 0.5;
                case OLD -> age > 0.7;
            };
            if (allowed && rng.nextDouble() < d.chance) return d;
        }
        return null;
    }

    // ---- age shading ---------------------------------------------------------------------------------

    /**
     * How grown this fish is, 0..1, with the species' typical weight sitting at 0.5. A straight
     * (w−min)/(max−min) would put almost every fish ever caught in the bottom fifth of the scale — the
     * ranges are long-tailed — and the shading would never be visible.
     */
    public static double ageFraction(FishProfile p, int weightG) {
        if (p == null) return 0.5;
        double mean = p.weightMeanSet ? p.weightMean : (p.weightMin + p.weightMax) / 2.0;
        if (weightG <= mean) {
            double span = Math.max(1.0, mean - p.weightMin);
            return Mth.clamp(0.5 * (weightG - p.weightMin) / span, 0.0, 0.5);
        }
        double span = Math.max(1.0, p.weightMax - mean);
        return Mth.clamp(0.5 + 0.5 * (weightG - mean) / span, 0.5, 1.0);
    }

    /** A species' two ends: how pale it is when young, what colour it goes when old. */
    private record Age(float youngPale, int oldTint) {}

    /** Everything not listed simply darkens a little with age — safe, and true of most sea fish. */
    private static final Age AGE_DEFAULT = new Age(0.15f, 0xCFC9BE);

    private static final Map<String, Age> AGE = new LinkedHashMap<>();

    static {
        // The textbook case, and the reason this exists: a young bream is a bright silver coin, an old one
        // is deep bronze-gold and noticeably darker.
        AGE.put("bream", new Age(0.38f, 0xC79A3F));
        AGE.put("white_bream", new Age(0.32f, 0xD6C48A));
        AGE.put("blue_bream", new Age(0.30f, 0xCFCBA6));
        AGE.put("white_eye_bream", new Age(0.30f, 0xD3CDB0));
        AGE.put("roach", new Age(0.26f, 0xD9C77E));
        AGE.put("rudd", new Age(0.22f, 0xE0B24E));
        AGE.put("crucian_carp", new Age(0.26f, 0xC08C3A));
        AGE.put("tench", new Age(0.16f, 0x8E8A3C));      // olive to near-black, and heavy with it
        AGE.put("perch", new Age(0.22f, 0xA79A4A));      // bars darken, belly goes brassy
        AGE.put("pike", new Age(0.16f, 0xA8AE7A));
        AGE.put("zander", new Age(0.18f, 0xAFA478));
        AGE.put("volga_zander", new Age(0.18f, 0xB2A87E));
        AGE.put("carp", new Age(0.20f, 0xB5893C));
        AGE.put("wild_carp", new Age(0.18f, 0xA87F35));
        AGE.put("mirror_carp", new Age(0.20f, 0xBC9046));
        AGE.put("grass_carp", new Age(0.22f, 0xBFB27A));
        AGE.put("silver_carp", new Age(0.28f, 0xD2CDBA));
        AGE.put("catfish", new Age(0.10f, 0x8E7A5E));    // an old wels is nearly black
        AGE.put("channel_catfish", new Age(0.14f, 0x9A8768));
        AGE.put("burbot", new Age(0.14f, 0x9F8E63));
        AGE.put("eel", new Age(0.12f, 0x8E8464));
        AGE.put("chub", new Age(0.24f, 0xCEA85C));
        AGE.put("ide", new Age(0.24f, 0xD0A85A));
        AGE.put("asp", new Age(0.26f, 0xD5CEA8));
        AGE.put("trout", new Age(0.20f, 0xB59A62));
        AGE.put("rainbow_trout", new Age(0.22f, 0xBCA070));
        AGE.put("char", new Age(0.20f, 0xB08A5E));
        AGE.put("taimen", new Age(0.18f, 0xAE8A52));
        AGE.put("lenok", new Age(0.20f, 0xB2915C));
        AGE.put("salmon", new Age(0.24f, 0xB69B6A));
        AGE.put("grayling", new Age(0.22f, 0xB6ADA0));
        AGE.put("sturgeon", new Age(0.14f, 0x9E9480));
        AGE.put("sterlet", new Age(0.18f, 0xA9A08A));
        AGE.put("bleak", new Age(0.34f, 0xDCD8C4));
        AGE.put("gudgeon", new Age(0.26f, 0xBCAE86));
        AGE.put("rotan", new Age(0.14f, 0x8E8A70));
    }

    /**
     * The multiply colour for a specimen: its age shading, then its morph over the top. Alpha is always
     * opaque — this is fed to a vanilla item tint, which multiplies.
     */
    public static int tint(String speciesPath, double age, String morphId) {
        Age a = AGE.getOrDefault(speciesPath, AGE_DEFAULT);
        int base = lerpRgb(0xFFFFFF, a.oldTint, (float) age);
        Def d = morphId == null || morphId.isEmpty() ? null : byId(morphId);
        // Multiplying the two would double-darken an old morph into mud; the morph's own colour is the
        // statement, so it wins, nudged by age rather than stacked with it.
        return d == null ? 0xFF000000 | base
                : 0xFF000000 | lerpRgb(d.tint, mul(d.tint, a.oldTint), (float) (age * 0.5));
    }

    /** How much white to wash over the sprite, 0..1: young fish are pale, and so are pale morphs. */
    public static float pale(String speciesPath, double age, String morphId) {
        Age a = AGE.getOrDefault(speciesPath, AGE_DEFAULT);
        float young = (float) (a.youngPale * (1.0 - age));
        Def d = morphId == null || morphId.isEmpty() ? null : byId(morphId);
        return Math.min(1f, d == null ? young : Math.max(d.pale, young * 0.5f));
    }

    private static int lerpRgb(int from, int to, float t) {
        t = Mth.clamp(t, 0f, 1f);
        int r = Math.round(Mth.lerp(t, (from >> 16) & 0xFF, (to >> 16) & 0xFF));
        int g = Math.round(Mth.lerp(t, (from >> 8) & 0xFF, (to >> 8) & 0xFF));
        int b = Math.round(Mth.lerp(t, from & 0xFF, to & 0xFF));
        return r << 16 | g << 8 | b;
    }

    private static int mul(int a, int b) {
        int r = ((a >> 16) & 0xFF) * ((b >> 16) & 0xFF) / 255;
        int g = ((a >> 8) & 0xFF) * ((b >> 8) & 0xFF) / 255;
        int bl = (a & 0xFF) * (b & 0xFF) / 255;
        return r << 16 | g << 8 | bl;
    }

    private FishMorph() {}
}
