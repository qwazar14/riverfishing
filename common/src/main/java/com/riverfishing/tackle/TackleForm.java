package com.riverfishing.tackle;

import com.riverfishing.RiverFishing;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;

/**
 * §tackle-station (0.6.0): the catalog of forms the Tackle Station ties. Two tabs — six peaceful
 * rigs and fourteen predator lures. Each form limits its sensible weight range; the WEIGHT is the player's
 * main decision (iron cost, cast distance, sink speed — §cast-weight reads the same grams later).
 * v1 is a hardcoded list — the numbers are being playtested; JSON-ify once they settle.
 */
public enum TackleForm {
    // ---- peaceful tab: the SWAPPABLE bottom-rod rigs only (float/predator/winter rigs live inside
    // their rods — see JournalScreen.isInternalRig — and are never tied separately) ----
    GRUSHA("rig_grusha", false, true, false, new int[]{30, 50, 80}, 15),
    FEEDER("rig_feeder", false, true, false, new int[]{40, 60, 80}, 60),
    FLAT_FEEDER("rig_flat_feeder", false, true, false, new int[]{40, 60, 80}, 10),
    GROUND("rig_ground", false, true, false, new int[]{30, 60, 100}, 40),
    CARP("rig_carp", false, true, false, new int[]{60, 90, 120, 160}, 20),
    CATFISH("rig_catfish", false, true, false, new int[]{80, 150, 250}, 50),
    // ---- predator tab: the artificial lures (all take the optional dye). The heavy steps are the
    // SEA sizes — sea-spin (20–120 g), boat (100–400 g) and trolling (150–600 g) rods finally have
    // tackle that lands inside their test windows. ----
    SPINNER("spinner", true, false, true, new int[]{3, 7, 14}, 0),
    SPOON("spoon", true, false, true, new int[]{10, 20, 35, 60, 180}, 0),
    WOBBLER("wobbler", true, false, true, new int[]{6, 12, 20, 40, 160}, 0),
    SILICONE("silicone", true, false, true, new int[]{5, 10, 20, 40}, 0),
    POPPER("popper", true, false, true, new int[]{7, 12, 30}, 0),
    CRANKBAIT("crankbait", true, false, true, new int[]{8, 14, 22, 40}, 0),
    JIG("jig", true, false, true, new int[]{10, 20, 40, 80, 200}, 0),
    CASTMASTER("castmaster", true, false, true, new int[]{14, 28, 45, 80, 160}, 0),
    // §trolling-lures (0.7.0): the two heavy forms a trolled spread actually uses. Their
    // ladders START above the heaviest older lure (the 180 g spoon), because that is the
    // gap — the boat blank tests to 400 g and the trolling blank to 600 g.
    OCTOPUS_JIG("octopus_jig", true, false, true, new int[]{60, 120, 250, 400}, 0),
    GIANT_SPOON("giant_spoon", true, false, true, new int[]{80, 160, 300, 500}, 0),
    // §more-lures-2 (0.9.0): four more forms, and unlike the trolling pair these fill in
    // BETWEEN the old ladders rather than above them. The wacky worm at 4 g is the lightest
    // thing the bench ties — ultralight water — and the swimbait is the only form that runs
    // from a spinning rod all the way up to the boat blank in one ladder.
    SPINNERBAIT("spinnerbait", true, false, true, new int[]{10, 18, 28, 45}, 0),
    BLADEBAIT("bladebait", true, false, true, new int[]{8, 14, 22, 35}, 0),
    SWIMBAIT("swimbait", true, false, true, new int[]{12, 25, 45, 90, 180}, 0),
    WACKY_WORM("wacky_worm", true, false, true, new int[]{4, 7, 12}, 0);

    /** NBT keys on the tied tackle. */
    public static final String TAG_WEIGHT = "TackleWeightG";
    public static final String TAG_TIED_BY = "TiedBy";

    public final String id;
    public final boolean predatorTab;
    public final boolean rig;
    public final boolean dyeable;
    public final int[] weights;
    /** §tackle-adv: each rig style has its own sensible hook-link default (flat = short, feeder = long). */
    public final int defaultLinkCm;

    TackleForm(String id, boolean predatorTab, boolean rig, boolean dyeable, int[] weights, int defaultLinkCm) {
        this.id = id;
        this.predatorTab = predatorTab;
        this.rig = rig;
        this.dyeable = dyeable;
        this.weights = weights;
        this.defaultLinkCm = defaultLinkCm;
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

    /** The angling sizes the bench can tie with, smallest first (§hook-pick). Bigger number = smaller hook. */
    public static final int[] HOOK_SIZES = {16, 14, 12, 10, 8, 6, 4, 2, 1};

    /**
     * §hook-pick: what the chosen hook adds to the bill, in whole iron ingots.
     *
     * <p>Priced off the hook ladder the player would otherwise climb by hand — a nugget makes two of the
     * smallest hook and every size up is one nugget more — and then billed at three nuggets to the ingot,
     * because the bench has no nugget slot and is tying the thing for you.
     *
     * <p>That rate is the whole point. At the honest nine-to-one every single-hook rig, which is most of
     * them, costs exactly one extra ingot no matter which hook you pick, and a choice nobody can feel is
     * worse than no choice. At three it reads: #16 / #10 / #4 cost 1 / 2 / 3 on one hook, and 1 / 4 / 7 on
     * the three-hook rig.
     *
     * @param sizeIdx index into {@link #HOOK_SIZES}
     */
    public static int hookIngots(int sizeIdx, int hooks) {
        int nuggets = (1 + Math.max(0, Math.min(HOOK_SIZES.length - 1, sizeIdx))) * Math.max(0, hooks);
        return (nuggets + 2) / 3;
    }

    /** §tackle-adv NBT keys. */
    public static final String TAG_LEADER_CM = "LeaderLenCm";
    public static final String TAG_BALANCE = "BalancePos";  // 0 nose / 1 center / 2 tail
    public static final String TAG_BLADE = "BladeSize";     // auto from mass (spinner/spoon)

    /** Rough cast-distance feel for the UI hint (blocks). */
    public static int castHintBlocks(int grams) {
        return (int) Math.round(4.0 * Math.sqrt(grams));
    }

    /**
     * Write the bench-tied identity onto a fresh tackle stack. The ONE place this happens: the Tackle
     * Station and the village fisherman's stock both go through here, so shop tackle is never invisible
     * to §cast-weight (an unstamped lure contributes 0 g — see {@code RigData.lureTackleWeightG}).
     *
     * @param tiedBy maker's mark shown in the tooltip, or null to leave the tackle anonymous
     */
    public static void stamp(net.minecraft.world.item.ItemStack out, TackleForm form, int grams,
                             String tiedBy, int leaderCm, int balance) {
        com.riverfishing.item.StackNbt.mutate(out, tag -> {
            tag.putInt(TAG_WEIGHT, grams);
            if (tiedBy != null) tag.putString(TAG_TIED_BY, tiedBy);
            // §tackle-adv: the knobs ride along; effects arrive with the bite-engine wiring.
            // Hook link (formerly "leader") is a RIG concept — the distance hook-to-anchor point.
            if (form.rig) tag.putInt(TAG_LEADER_CM, leaderCm);
            else tag.putInt(TAG_BALANCE, balance);
            if (form == SPINNER || form == SPOON) {
                tag.putInt(TAG_BLADE, Math.min(5, 1 + grams / 15)); // blade follows the mass
            }
        });
    }

    /** The middle weight step — the "stock" size a shop would sensibly carry. */
    public int stockWeight() {
        return weights[weights.length / 2];
    }

    /** The form that ties this item id, or null if the bench can't make it (built-in rod rigs). */
    public static TackleForm byItemId(String id) {
        for (TackleForm f : values()) {
            if (f.id.equals(id)) return f;
        }
        return null;
    }
}
