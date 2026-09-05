package com.riverfishing.item;

import net.minecraft.world.item.Item;

/**
 * §breeding: the seine — a wall of net dragged through the water. 1..3 fish a haul, one haul a
 * minute; the heavy mesh lasts 64 hauls. Instant use with the cooldown carrying the "one long haul"
 * feel: a timed use would need three dialect-specific overrides to say the same thing.
 */
public class SeineNetItem extends NetItem {
    public SeineNetItem(Item.Properties props) {
        super(props, 64, 1, 3, 1200);
    }
}
