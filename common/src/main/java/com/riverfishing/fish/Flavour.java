package com.riverfishing.fish;

/**
 * §boilies: what a boilie tastes of — fourteen flavours, each described by the few properties the rules read
 * (family, how strong, how nourishing, bright or dark), so a rule is written once for "every sour flavour"
 * and a new flavour needs no new rule. The ingredient is what goes into the bottle with the sugar; all of them
 * are things a player already has — allium is the onion-and-garlic genus, blaze powder is as hot as it gets.
 */
public enum Flavour {
    STRAWBERRY("sweet_berries", Family.FRUIT, Strength.MEDIUM, 0xE0304A, true),
    TUTTI_FRUTTI("melon_slice", Family.FRUIT, Strength.STRONG, 0xFF5FA8, true),
    PLUM("chorus_fruit", Family.FRUIT, Strength.MEDIUM, 0x8E3FA0, true),
    PINEAPPLE("glow_berries", Family.SOUR, Strength.STRONG, 0xF2D530, true),
    CITRUS("apple", Family.SOUR, Strength.STRONG, 0xC8E040, true),
    CARAMEL("honey_bottle", Family.SWEET, Strength.MILD, 0xD08A2A, true),
    CHOCOLATE("cocoa_beans", Family.SWEET, Strength.MEDIUM, 0x5A3420, false),
    CORN("riverfishing:corn", Family.NUT, Strength.MILD, 0xE8C44A, true),
    FISH("riverfishing:fish_oil", Family.FISH, Strength.MEDIUM, 0x7A6440, false),
    KRILL("prismarine_crystals", Family.SEAFOOD, Strength.MEDIUM, 0xC0503A, false),
    SQUID("ink_sac", Family.SEAFOOD, Strength.STRONG, 0x2A2A3A, false),
    LIVER("riverfishing:chicken_liver", Family.MEAT, Strength.STRONG, 0x6A1E1E, false),
    GARLIC("allium", Family.SPICE, Strength.STRONG, 0xE8E0D0, false),
    CHILI("blaze_powder", Family.SPICE, Strength.STRONG, 0xE0501E, true);

    public enum Family { FRUIT, SOUR, SWEET, NUT, FISH, SEAFOOD, MEAT, SPICE }

    public enum Strength { MILD, MEDIUM, STRONG }

    /** The item that goes into the bottle (vanilla id bare, the mod's with its namespace). */
    public final String ingredient;
    public final Family family;
    public final Strength strength;
    /** The colour of the liquid, and of a boilie flavoured with it. */
    public final int rgb;
    /** A bright (fluo) flavour — the fruit and the sour; the rest are dark, natural colours. */
    public final boolean bright;

    Flavour(String ingredient, Family family, Strength strength, int rgb, boolean bright) {
        this.ingredient = ingredient;
        this.family = family;
        this.strength = strength;
        this.rgb = rgb;
        this.bright = bright;
    }

    public String id() { return name().toLowerCase(java.util.Locale.ROOT); }

    public static Flavour of(String id) {
        for (Flavour f : values()) if (f.id().equals(id)) return f;
        return null;
    }

    /** The flavour whose bottle this ingredient makes, or null. */
    public static Flavour byIngredient(String itemId) {
        for (Flavour f : values()) {
            String want = f.ingredient.contains(":") ? f.ingredient : "minecraft:" + f.ingredient;
            if (want.equals(itemId)) return f;
        }
        return null;
    }

    public boolean protein() { return family == Family.FISH || family == Family.SEAFOOD || family == Family.MEAT; }
    public boolean fruit() { return family == Family.FRUIT || family == Family.SOUR; }
    public boolean sweet() { return family == Family.SWEET || family == Family.FRUIT; }
    public boolean sour() { return family == Family.SOUR; }
    public boolean spice() { return family == Family.SPICE; }
    public boolean strong() { return strength == Strength.STRONG; }
    /** A natural, quiet taste — what a wary fish in clear water trusts. */
    public boolean natural() { return strength != Strength.STRONG && (protein() || family == Family.NUT); }
    /** What cuts through the smell of a muddy bottom. */
    public boolean punchy() { return sour() || spice() || strong(); }
}
