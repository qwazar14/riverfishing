package com.riverfishing.client;

import com.riverfishing.RiverFishing;
import com.riverfishing.component.LineType;
import com.riverfishing.component.RigType;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The sprite layers a composited rod icon is built from (§rod-layers). An assembled rod's icon is
 * NOT one texture — it is stacked at render time from up to four hand-drawn sprites, one per group:
 * <ul>
 *   <li><b>blank</b> — the bare rod, one per rod type (always drawn);</li>
 *   <li><b>reel</b> — the mounted reel (only if a reel is socketed);</li>
 *   <li><b>line</b> — the line on the spool (only in the inventory, so it never doubles the 3D line
 *       drawn in-hand / on the pod);</li>
 *   <li><b>rig</b> — the terminal tackle at the tip (only if a rig is socketed).</li>
 * </ul>
 * Each group resolves most-specific-first (e.g. {@code reel_5000} then {@code reel}); a layer whose
 * texture the artist hasn't drawn yet is simply skipped. Drop PNGs into
 * {@code assets/riverfishing/textures/item/rod/} and the model generator wires them up.
 */
public final class RodModelLayers {
    private RodModelLayers() {}

    public static final String[] ROD_KEYS =
            {"stick", "bamboo", "pole", "winter", "ultralight", "spinning", "feeder", "bottom", "carp",
             "surf", "sea_spin", "boat", "trolling"};
    public static final int[] REEL_SIZES = {1000, 2000, 3000, 4000, 5000, 6000, 7000,
            8000, 10000, 12000, 14000};

    public static Identifier loc(String path) {
        return RiverFishing.id("item/rod/" + path);
    }

    /** The mirrored (hand) counterpart of a rod layer model (§rod-mirror). */
    public static Identifier mirror(Identifier normal) {
        return Identifier.fromNamespaceAndPath(normal.getNamespace(), normal.getPath().replace("item/rod/", "item/rod_m/"));
    }

    public static Identifier blank(String rodKey) {
        return loc("blank_" + rodKey);
    }

    /** §rod-bend: the blank bent into bucket {@code bend} (1..3); 0 = straight. */
    public static Identifier blank(String rodKey, int bend) {
        return bend <= 0 ? blank(rodKey) : loc("blank_" + rodKey + "_bend" + bend);
    }

    /**
     * §rod-bend-3d: rigid pieces of a segmented 3D blank, butt to tip — {@code s0} is the handle
     * (never bends), {@code s1..} are the blank sections, each hinged at the joint in front of it.
     * A rod that has these is bent by {@link RodItemRenderer}'s bone chain instead of by swapping in
     * a pre-drawn bend sprite: the vanilla model format has no bone hierarchy and bakes element
     * rotation at load, so a live chain can only live in the renderer. Rods without segment models
     * keep the sprite buckets.
     */
    public static final int BLANK_SEGMENTS = 8; // sea_spin carries the most pieces: handle + 7 joints

    public static Identifier segment(String rodKey, int index) {
        return loc("blank_" + rodKey + "_s" + index);
    }

    public static Identifier reel(int size) {
        return loc("reel_" + size);
    }

    /**
     * §reel-3d: the solid reel drawn on a 3D blank — authored in the FEEDER's coordinate space
     * (foot docked into its seat), shifted per rod by {@link RodItemRenderer}. All eleven sizes are
     * scaled from one master and share one texture sheet; see tools/gen_reels.js.
     */
    public static Identifier reel3d(int size) {
        return loc("reel_" + size + "_3d");
    }

    /** §reel-crank: the crank lever alone — split out so the renderer can sweep it while reeling. */
    public static Identifier reel3dHandle(int size) {
        return loc("reel_" + size + "_handle_3d");
    }

    /** §reel-crank: the knob alone — it orbits WITH the lever but counter-rotates to stay level. */
    public static Identifier reel3dKnob(int size) {
        return loc("reel_" + size + "_knob_3d");
    }

    public static Identifier reelGeneric() {
        return loc("reel");
    }

    public static Identifier line(LineType type) {
        return type == null ? null : loc("line_" + type.jsonKey());
    }

    public static Identifier lineGeneric() {
        return loc("line");
    }

    public static Identifier rig(RigType type) {
        return type == null ? null : loc("rig_" + type.name().toLowerCase(Locale.ROOT));
    }

    public static Identifier rigGeneric() {
        return loc("rig");
    }

    /**
     * Every layer model that MIGHT exist, normal AND mirrored (§rod-mirror) — the client registers
     * whichever have a JSON present.
     */
    public static List<Identifier> candidates() {
        List<Identifier> normal = new ArrayList<>();
        for (String k : ROD_KEYS) {
            normal.add(blank(k));
            for (int b = 1; b <= 6; b++) normal.add(blank(k, b)); // §rod-bend buckets
            for (int s = 0; s < BLANK_SEGMENTS; s++) normal.add(segment(k, s)); // §rod-bend-3d chain
        }
        normal.add(reelGeneric());
        for (int s : REEL_SIZES) { // §reel-3d + §reel-crank
            normal.add(reel(s)); normal.add(reel3d(s)); normal.add(reel3dHandle(s)); normal.add(reel3dKnob(s));
        }
        normal.add(lineGeneric());
        for (LineType t : LineType.values()) normal.add(line(t));
        normal.add(rigGeneric());
        for (RigType t : RigType.values()) normal.add(rig(t));

        List<Identifier> all = new ArrayList<>(normal);
        for (Identifier loc : normal) all.add(mirror(loc));
        return all;
    }
}
