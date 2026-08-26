package com.riverfishing.item;

/**
 * §keepnet (0.7.0): the four sizes of keepnet, and the room each gives you.
 *
 * <p>One object at four sizes rather than four different containers — a keepnet is a keepnet, and each is
 * made from the one below it.
 *
 * <p>A keepnet holds FISH, and nothing else. An earlier cut reserved an edge column for the bait and
 * groundbait an angler carries, so gear and catch competed for one box; it was dropped because a keepnet
 * you can put a pickaxe in is not a keepnet. Every cell is water now.
 */
public enum KeepnetTier {
    /** A morning's small fish and nothing more. */
    SMALL("keepnet_small", 5, 3),
    /** The working net, the one every angler owns. */
    MEDIUM("keepnet_medium", 7, 4),
    /** A day on the bank. */
    LARGE("keepnet_large", 8, 5),
    /** Room for something enormous — and the only thing that will hold a shark. */
    HUGE("keepnet_huge", 9, 6);

    private final String id;
    private final int width;
    private final int height;

    KeepnetTier(String id, int width, int height) {
        this.id = id;
        this.width = width;
        this.height = height;
    }

    public String id() { return id; }

    public int width() { return width; }

    public int height() { return height; }

    /** Every cell of every tier is water: a keepnet holds fish. */
    public int cells() { return width * height; }
}
