package com.riverfishing.tackle;

import com.riverfishing.RiverFishing;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;

/**
 * §tackle-station (0.6.0): the catalog of forms the Tackle Station ties. Two tabs of nine — peaceful
 * rigs and predator tackle. Each form limits its sensible weight range; the WEIGHT is the player's
 * main decision (iron cost, cast distance, sink speed — §cast-weight reads the same grams later).
 * v1 is a hardcoded list — the numbers are being playtested; JSON-ify once they settle.
 */
public enum TackleForm {
    // ---- peaceful tab: the SWAPPABLE bottom-rod rigs only (float/predator/winter rigs live inside
    // their rods — see JournalScreen.isInternalRig — and are never tied separately) ----
    GRUSHA("rig_grusha", false, true, false, new int[]{30, 50, 80}),
    FEEDER("rig_feeder", false, true, false, new int[]{40, 60, 80}),
    FLAT_FEEDER("rig_flat_feeder", false, true, false, new int[]{40, 60, 80}),
    GROUND("rig_ground", false, true, false, new int[]{30, 60, 100}),
    CARP("rig_carp", false, true, false, new int[]{60, 90, 120, 160}),
    CATFISH("rig_catfish", false, true, false, new int[]{80, 150, 250}),
    // ---- predator tab: the artificial lures (all take the optional dye). The heavy steps are the
    // SEA sizes — sea-spin (20–120 g), boat (100–400 g) and trolling (150–600 g) rods finally have
    // tackle that lands inside their test windows. ----
    SPINNER("spinner", true, false, true, new int[]{3, 7, 14}),
    SPOON("spoon", true, false, true, new int[]{10, 20, 35, 60, 180}),
    WOBBLER("wobbler", true, false, true, new int[]{6, 12, 20, 40, 160}),
    SILICONE("silicone", true, false, true, new int[]{5, 10, 20, 40}),
    POPPER("popper", true, false, true, new int[]{7, 12, 30}),
    CRANKBAIT("crankbait", true, false, true, new int[]{8, 14, 22, 40}),
    JIG("jig", true, false, true, new int[]{10, 20, 40, 80, 200}),
    CASTMASTER("castmaster", true, false, true, new int[]{14, 28, 45, 80, 160});

    /** NBT keys on the tied tackle. */
    public static final String TAG_WEIGHT = "TackleWeightG";
    public static final String TAG_TIED_BY = "TiedBy";

    public final String id;
    public final boolean predatorTab;
    public final boolean rig;
    public final boolean dyeable;
    public final int[] weights;

    TackleForm(String id, boolean predatorTab, boolean rig, boolean dyeable, int[] weights) {
        this.id = id;
        this.predatorTab = predatorTab;
        this.rig = rig;
        this.dyeable = dyeable;
        this.weights = weights;
    }

    public Item item() {
        return BuiltInRegistries.ITEM.get(RiverFishing.id(id));
    }

    /** The weight IS the price: 1 ingot per started 30 g, feeders pay +1 for the cage frame. */
    public int ironFor(int grams) {
        int base = Math.max(1, (int) Math.round(grams / 30.0));
        return base + (this == FEEDER || this == FLAT_FEEDER ? 1 : 0);
    }

    /** Lures 1 string; rigs 2 (rig + leader wrap); grusha 3 — one per hook link. */
    public int stringNeeded() {
        return this == GRUSHA ? 3 : rig ? 2 : 1;
    }

    /** Hooks consumed = the rig's own HOOK slots (grusha carries three), lures take one. */
    public int hooksNeeded() {
        return this == GRUSHA ? 3 : 1;
    }

    /** §tackle-adv NBT keys. */
    public static final String TAG_LEADER_CM = "LeaderLenCm";
    public static final String TAG_BALANCE = "BalancePos";  // 0 nose / 1 center / 2 tail
    public static final String TAG_BLADE = "BladeSize";     // auto from mass (spinner/spoon)

    /** Rough cast-distance feel for the UI hint (blocks). */
    public static int castHintBlocks(int grams) {
        return (int) Math.round(4.0 * Math.sqrt(grams));
    }
}
