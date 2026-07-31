package com.riverfishing.mixin;

/**
 * §fish-seat: the transparent margin under a fish sprite, carried on the item-entity render state.
 *
 * <p>The render state is the only thing {@code submit} gets, and it does not carry the ItemStack —
 * so the one place that CAN tell a fish from a stick ({@code extractRenderState}, which still has the
 * entity) measures it and leaves the answer here.
 *
 * <p>0 for everything that is not a fish, which is the whole of vanilla.
 */
public interface SeatedFish {
    float riverfishing$bottomMargin();

    void riverfishing$setBottomMargin(float margin);
}
