package com.riverfishing.client;

/**
 * §fish-seat: the transparent margin under a fish sprite, carried on the item-entity render state.
 *
 * <p>The render state is the only thing {@code submit} gets, and it does not carry the ItemStack — so
 * the one place that CAN tell a fish from a stick ({@code extractRenderState}, which still has the
 * entity) measures it and leaves the answer here. 0 for everything that is not a fish.
 *
 * <p>Lives OUTSIDE {@code com.riverfishing.mixin} on purpose: everything in a declared mixin package
 * is treated as a mixin and cannot be referenced as an ordinary class ("is in a defined mixin package
 * ... and cannot be referenced directly"), which is a black screen at resource-reload time.
 */
public interface SeatedFish {
    float riverfishing$bottomMargin();

    void riverfishing$setBottomMargin(float margin);
}
