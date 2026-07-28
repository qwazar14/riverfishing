package com.riverfishing.item;

/**
 * §keepnet (0.7.0): the four containers a catch can go into, and the room each gives you.
 *
 * <p>The ladder is the upgrade path: a wicker creel holds a morning's small fish, a livewell holds a day
 * on the sea. The last column or two of every tier above the creel is GEAR — single cells that take
 * anything except a fish, so the bait tube and the groundbait you actually carry compete with the catch
 * for the same box. That is the decision the whole mechanic exists to create.
 */
public enum KeepnetTier {
    /** Плетёнка: a morning's roach, and no room for anything you might want to bring. */
    WICKER("wicker_creel", 5, 3, 0),
    /** Садок: the working net. One column of gear. */
    KEEPNET("keepnet", 7, 4, 1),
    /** Холодильник: a cool box — more room, and it keeps a fish worth selling. */
    COOLER("cool_box", 8, 5, 1),
    /** Живорыбный ящик: the boat's livewell. Two gear columns, and room for something enormous. */
    LIVEWELL("livewell", 9, 6, 2);

    private final String id;
    private final int width;
    private final int height;
    private final int gearColumns;

    KeepnetTier(String id, int width, int height, int gearColumns) {
        this.id = id;
        this.width = width;
        this.height = height;
        this.gearColumns = gearColumns;
    }

    public String id() { return id; }

    public int width() { return width; }

    public int height() { return height; }

    /** Columns at the right-hand edge that hold gear rather than fish. */
    public int gearColumns() { return gearColumns; }

    /** The first column index that is gear. Everything left of it is water. */
    public int gearFrom() { return width - gearColumns; }

    public boolean isGearCell(int x) { return x >= gearFrom(); }
}
