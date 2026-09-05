package com.riverfishing.item;

import net.minecraft.world.item.Item;

/**
 * §breeding: the cast net — thrown from the bank, a circle of weighted mesh. 0..1 fish a throw,
 * every 15 seconds; the light mesh tears after 32 throws.
 */
public class CastNetItem extends NetItem {
    public CastNetItem(Item.Properties props) {
        super(props, 32, 0, 1, 300);
    }
}

// §ported26
