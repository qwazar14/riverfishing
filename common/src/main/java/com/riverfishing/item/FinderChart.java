package com.riverfishing.item;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * §chart-item: which sounder a depth chart belongs to.
 *
 * <p>The chart used to be filed per player per world, which made a surveyed lake a property of the
 * ACCOUNT — it could not be lost, lent, inherited or sold, and on a server that is the difference
 * between a map being work and a map being a wiki page. A finder mints an id the first time it is
 * used and carries it in its own stack data; the chart is filed under that id. One sounder, one
 * chart. A second finder starts blank, and a finder that goes in the lava takes its survey with it.
 *
 * <p>The id is twelve hex characters — short enough to read off a tooltip and tell two sounders
 * apart, long enough that no server will ever collide two.
 */
public final class FinderChart {

    /** The stack-data key. Capitalised like every other key the mod writes. */
    private static final String KEY = "Chart";

    private FinderChart() {}

    /** The id already on this stack, or empty. Never mints — safe on the client. */
    public static String of(ItemStack stack) {
        return StackNbt.get(stack).getStringOr("Chart", "");
    }

    /**
     * …and the server's way in: the id, minting one if this sounder has never been used. Called where
     * a sounding is actually taken, so a finder that has never seen water carries nothing.
     */
    public static String mint(ItemStack stack) {
        String id = of(stack);
        if (!id.isEmpty()) return id;
        String fresh = java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        StackNbt.mutate(stack, t -> t.putString(KEY, fresh));
        return fresh;
    }

    /** The player-facing finder in this player's hands, or EMPTY. The admin probe is not one. */
    public static ItemStack held(Player p) {
        ItemStack main = p.getMainHandItem();
        if (main.getItem() instanceof WaterProbeItem w && !w.admin()) return main;
        ItemStack off = p.getOffhandItem();
        if (off.getItem() instanceof WaterProbeItem w && !w.admin()) return off;
        return ItemStack.EMPTY;
    }
}
