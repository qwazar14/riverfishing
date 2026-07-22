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
    CARP("rig_carp", false, true, false, new int[]{60, 90, 120}),
    CATFISH("rig_catfish", false, true, false, new int[]{80, 150, 250}),
    // ---- predator tab: the artificial lures (all take the optional dye) ----
    SPINNER("spinner", true, false, true, new int[]{3, 7, 14}),
    SPOON("spoon", true, false, true, new int[]{10, 20, 35}),
    WOBBLER("wobbler", true, false, true, new int[]{6, 12, 20}),
    SILICONE("silicone", true, false, true, new int[]{5, 10, 20}),
    POPPER("popper", true, false, true, new int[]{7, 12}),
    CRANKBAIT("crankbait", true, false, true, new int[]{8, 14, 22}),
    JIG("jig", true, false, true, new int[]{10, 20, 40}),
    CASTMASTER("castmaster", true, false, true, new int[]{14, 28, 45});

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

    /** 1 iron ingot per started 40 g — the weight IS the price. */
    public int ironFor(int grams) {
        return Math.max(1, (int) Math.ceil(grams / 40.0));
    }

    /** Rigs take 2 string (rig + leader wrap), lures 1. */
    public int stringNeeded() {
        return rig ? 2 : 1;
    }

    /** Rough cast-distance feel for the UI hint (blocks). */
    public static int castHintBlocks(int grams) {
        return (int) Math.round(4.0 * Math.sqrt(grams));
    }
}
