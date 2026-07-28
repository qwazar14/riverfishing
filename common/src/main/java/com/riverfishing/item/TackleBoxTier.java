package com.riverfishing.item;

/**
 * §tackle-box (0.7.0): the four sizes of tackle box, and the only place their capacity is written down.
 *
 * <p>Rows of nine, because the box shares the player inventory's grid — a tackle box that lined up with
 * nothing would be a box you have to think about. Each size is its own item rather than an NBT field, for
 * the same reason the keepnets are: an upgrade should be a craft, and a stack of "boxes" whose capacity
 * depends on hidden data is a stack that merges wrong.
 */
public enum TackleBoxTier {
    SMALL("tackle_box_small", 1),
    MEDIUM("tackle_box_medium", 2),
    LARGE("tackle_box_large", 3),
    HUGE("tackle_box_huge", 4);

    private final String id;
    private final int rows;

    TackleBoxTier(String id, int rows) {
        this.id = id;
        this.rows = rows;
    }

    public String id() {
        return id;
    }

    public int rows() {
        return rows;
    }

    public int slots() {
        return rows * 9;
    }
}
