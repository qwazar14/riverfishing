package com.riverfishing.fish;

import java.util.List;

/**
 * §fish-groups (0.8.0): the families the seventy-nine species are filed under, and the order they are
 * shown in.
 *
 * <p>One place owns the list. The electrofisher's picker, the lang keys and anything else that wants to
 * lay fish out by family all read it here, so a group cannot exist in one screen and not another — the
 * same rule the rest of this mod keeps: one function owns one answer.
 *
 * <p>The order is deliberate and not alphabetical: it runs from the fish you catch in the first hour to
 * the ones you need a boat and a trolling rod for. A player scanning the list is usually looking for
 * something near where they already fish.
 */
public final class FishGroup {
    public static final String CYPRINID = "cyprinid";
    public static final String PREDATOR = "predator";
    public static final String SALMONID = "salmonid";
    public static final String STURGEON = "sturgeon";
    public static final String KOI = "koi";
    public static final String SEA = "sea";
    public static final String BIG_GAME = "big_game";
    /** Anything a datapack added without saying what it is — listed, never silently mis-filed. */
    public static final String OTHER = "other";

    public static final List<String> ORDER =
            List.of(CYPRINID, PREDATOR, SALMONID, STURGEON, KOI, SEA, BIG_GAME, OTHER);

    /** The profile's group, or {@link #OTHER} if it named one this build does not know. */
    public static String of(FishProfile p) {
        return p != null && ORDER.contains(p.group) ? p.group : OTHER;
    }

    public static String nameKey(String group) {
        return "fishgroup.riverfishing." + group;
    }

    private FishGroup() {}
}
