package com.riverfishing.item;

/**
 * §j (0.9.0): what an oily fish renders down to in a furnace. It goes in the groundbait bowl and
 * nowhere else — a pantry entry with fraction 0: all scent, no grain — so it is an {@link IngredientItem}
 * and its one tooltip line says exactly that.
 */
public class FishOilItem extends IngredientItem {
    public FishOilItem(Properties properties) {
        super("tooltip.riverfishing.fish_oil", properties);
    }
}

// §ported26
