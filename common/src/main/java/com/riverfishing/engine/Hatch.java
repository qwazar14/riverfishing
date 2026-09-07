package com.riverfishing.engine;

import com.riverfishing.tackle.TiedDesign;
import com.riverfishing.water.WaterType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;

/**
 * §fly (0.10.0): what is hatching over the water right now, and how well the fly on the rig matches it.
 *
 * <p>Insects hatch by season and hour, and a feeding trout takes what is hatching and refuses the
 * rest — "match the hatch" is the whole craft of dry-fly fishing. Here it is a small table: a hatch
 * is a KIND (which of the tied {@link TiedDesign.Template}s reads as it) and a SIZE in millimetres,
 * and the fly on the rig is scored against both. The water shows the hatch ({@link #particles}) so
 * the angler reads it off the river, not off a tooltip.
 */
public enum Hatch {
    /** Winter and any cold hour: tiny, and the only thing on the water for months. */
    MIDGE("midge", TiedDesign.Template.DRY_FLY, 4),
    /** Spring days: a big nymph crawling to the bank — fished under, not on top. */
    STONEFLY("stonefly", TiedDesign.Template.NYMPH, 14),
    /** Late spring into early summer, in the evening: the classic rise. */
    MAYFLY("mayfly", TiedDesign.Template.DRY_FLY, 10),
    /** Summer evenings: skittering on the surface. */
    CADDIS("caddis", TiedDesign.Template.DRY_FLY, 8),
    /** Summer afternoons in fair weather: ants and hoppers blown onto the water. */
    TERRESTRIAL("terrestrial", TiedDesign.Template.ANT, 8),
    /** Still water, all year: the shrimp in the weed — the fallback when nothing is in the air. */
    SCUD("scud", TiedDesign.Template.SHRIMP, 8),
    /**
     * Autumn, or a storm colouring the water: the fish are on fry, and a streamer is a fry. The size
     * is 14, not the 22 mm a real minnow measures: the tying canvas is 16 pixels wide, a millimetre
     * each, so 22 could never be within three of any fly — a streamer drawn edge to edge is the match.
     */
    BAITFISH("baitfish", TiedDesign.Template.STREAMER, 14);

    public final String key;
    private final TiedDesign.Template kind;
    private final int sizeMm;

    Hatch(String key, TiedDesign.Template kind, int sizeMm) {
        this.key = key;
        this.kind = kind;
        this.sizeMm = sizeMm;
    }

    /** Lang-key tail: {@code hatch.riverfishing.<key>}. */
    public String key() { return key; }

    public int sizeMm() { return sizeMm; }

    /** Does a fly of this template read as this hatch? One kind per hatch, on purpose: the choice is the game. */
    public boolean matches(TiedDesign.Template t) {
        return t == kind;
    }

    /**
     * The table on flowing water in fair weather, [season][time] in enum order; null = nothing in the
     * air. Thunder, and autumn, put the fish on fry regardless; still water falls back to the scud.
     * The check script reads this literal, so keep it a literal.
     */
    private static final Hatch[][] TABLE = {
            // DAWN        DAY           DUSK       NIGHT
            {MIDGE,        STONEFLY,     MAYFLY,    null},       // SPRING
            {MAYFLY,       TERRESTRIAL,  CADDIS,    null},       // SUMMER
            {BAITFISH,     BAITFISH,     BAITFISH,  BAITFISH},   // AUTUMN
            {MIDGE,        MIDGE,        MIDGE,     MIDGE},      // WINTER
    };

    /** What is hatching now, or null. Season null (never set) reads as nothing hatching. */
    public static Hatch now(Season s, TimeOfDay t, Weather w, WaterType water) {
        if (w == Weather.THUNDER) return BAITFISH;               // high, coloured water: fry are washed out
        Hatch h = s == null || t == null ? null : TABLE[s.ordinal()][t.ordinal()];
        if (h == TERRESTRIAL && w != Weather.CLEAR) h = null;    // ants do not fly in the rain
        if (h == null && still(water)) h = SCUD;
        return h;
    }

    private static boolean still(WaterType water) {
        return water == WaterType.LAKE || water == WaterType.POND || water == WaterType.SWAMP;
    }

    /**
     * The fly's bite factor against the hatch. Right kind within 3 mm of the hatch = 1.5; right kind,
     * wrong size = 1.0; wrong kind = 0.6. With nothing hatching a dry fly is 0.7 and a streamer 0.9 —
     * nymphs catch most fish most of the time, which is true on the river as well.
     */
    public static double factor(Hatch h, TiedDesign.Analysis a) {
        if (a == null) return 1.0;
        if (h == null) {
            return switch (a.template()) {
                case DRY_FLY -> 0.7;
                case STREAMER -> 0.9;
                default -> 1.0;
            };
        }
        if (!h.matches(a.template())) return 0.6;
        return Math.abs(h.sizeMm - a.sizeMm()) <= 3 ? 1.5 : 1.0;
    }

    /**
     * The tell: a few motes over the water within six blocks of the fly and, one call in four, a rise
     * ring at a random water block nearby — a fish that is not yours, feeding on what is up. Called once
     * a second while the line waits; never more than eight particles, so a bank of anglers is not a snow
     * globe.
     */
    public void particles(ServerLevel level, BlockPos target) {
        RandomSource r = level.getRandom();
        for (int i = 0; i < 3; i++) {
            double x = target.getX() + 0.5 + (r.nextDouble() - 0.5) * 12.0;
            double z = target.getZ() + 0.5 + (r.nextDouble() - 0.5) * 12.0;
            BlockPos p = BlockPos.containing(x, target.getY(), z);
            if (!level.getFluidState(p).is(FluidTags.WATER)) continue;
            level.sendParticles(ParticleTypes.WHITE_ASH, x, target.getY() + 1.1 + r.nextDouble() * 0.6, z,
                    1, 0.0, 0.02, 0.0, 0.0);
        }
        if (r.nextInt(4) != 0) return;
        BlockPos p = target.offset(r.nextInt(13) - 6, 0, r.nextInt(13) - 6);
        if (!level.getFluidState(p).is(FluidTags.WATER) || !level.getBlockState(p.above()).isAir()) return;
        double y = p.getY() + 0.92;
        int n = 5;
        for (int i = 0; i < n; i++) {
            double a = i * (Math.PI * 2.0 / n);
            double dx = Math.cos(a), dz = Math.sin(a);
            // count 0 turns the offsets into a velocity: the ring runs outward from the rise
            level.sendParticles(ParticleTypes.FISHING, p.getX() + 0.5 + dx * 0.3, y, p.getZ() + 0.5 + dz * 0.3,
                    0, dx, 0.0, dz, 0.15);
        }
    }
}
