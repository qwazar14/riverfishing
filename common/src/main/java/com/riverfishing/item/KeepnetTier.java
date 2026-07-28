package com.riverfishing.item;

/**
 * §keepnet (0.7.0): the four containers a catch can go into, and the room each gives you.
 *
 * <p>The ladder is the upgrade path: a wicker creel holds a morning's small fish, a livewell holds a day
 * on the sea.
 *
 * <p>A keepnet holds FISH, and nothing else. An earlier cut reserved an edge column for the bait and
 * groundbait an angler carries, so gear and catch competed for one box; it was dropped because a keepnet
 * you can put a pickaxe in is not a keepnet. Every cell is water now.
 */
public enum KeepnetTier {
    /** Плетёнка: a morning's small fish and nothing more. */
    WICKER("wicker_creel", 5, 3),
    /** Садок: the working net. */
    KEEPNET("keepnet", 7, 4),
    /** Холодильник: a cool box — more room, and it keeps a fish worth selling. */
    COOLER("cool_box", 8, 5),
    /** Живорыбный ящик: the boat's livewell, and room for something enormous. */
    LIVEWELL("livewell", 9, 6);

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
