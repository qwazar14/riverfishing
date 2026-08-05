package com.riverfishing.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.architectury.registry.client.keymappings.KeyMappingRegistry;
import net.minecraft.client.KeyMapping;

/**
 * §fight-keys (0.7.2): the rod gets its own four keys, and the feet keep WASD.
 *
 * <p>Until now the fight course read {@code options.keyLeft/keyRight/keyUp/keyDown} — the movement keys.
 * It only ever read their STATE; the walking was a side effect the mechanic neither needed nor used. But
 * the side effect was real, and players fell off piers answering the boss bar. Foxsy1798 asked for
 * arrows; Makaronn asked for bindings. This is both.
 *
 * <p>It also removes a scar. §fight-footwork measures the angler-to-hook distance and never reads a key,
 * so the two systems were independent in everything but the keyboard — and sharing it made answering the
 * bar pay twice on a DOWN course (S counters the run AND backs you away, which is a leg-pump) and cost
 * you on an UP course (W walks you at the fish, which is slack). FishingManager carries a guard whose own
 * comment says the game "killed you for obeying its own boss bar". That guard exists because of the
 * overload, not because of the design.
 *
 * <p><b>The vertical sense is inverted from 0.7.x on purpose.</b> W used to mean "rod down" and S "rod
 * up", which has no intuition at all — hence the {@code [W]}/{@code [S]} cribs that used to be baked into
 * the boss-bar strings. The arrow now points where the rod tip goes, so all four courses collapse to one
 * sentence: <em>press the arrow opposite the glyph on the bar</em>.
 *
 * <p>Registered through Architectury's {@link KeyMappingRegistry}, which is present in every version this
 * mod pins (9.2.14, 13.0.8, 20.0.8, 21.0.5) on both loader families, so the bindings appear in the
 * vanilla Controls screen with vanilla conflict detection and need no settings screen of our own.
 *
 * <p>Registration has to happen during mod construction rather than at client setup: the NeoForge
 * implementation queues mappings and flushes them on {@code RegisterKeyMappingsEvent}, and anything
 * arriving after that gets a warning and a hand-patch of {@code Options.keyMappings}. Building a
 * KeyMapping touches no registry object, so it is safe there.
 */
public final class FightKeys {

    private static final String CATEGORY = "key.categories.gameplay";

    /** Counter a run heading LEFT: hold the rod to the right. */
    public static final KeyMapping PULL_RIGHT =
            new KeyMapping("key.riverfishing.pull_right", InputConstants.KEY_RIGHT, CATEGORY);
    /** …and the mirror of it. */
    public static final KeyMapping PULL_LEFT =
            new KeyMapping("key.riverfishing.pull_left", InputConstants.KEY_LEFT, CATEGORY);
    /** It has gone deep: LIFT, and get its head up. */
    public static final KeyMapping LIFT =
            new KeyMapping("key.riverfishing.lift", InputConstants.KEY_UP, CATEGORY);
    /** It is coming up to jump: rod DOWN, which is what stops a fish leaping off. */
    public static final KeyMapping PUSH =
            new KeyMapping("key.riverfishing.push", InputConstants.KEY_DOWN, CATEGORY);

    private FightKeys() {}

    public static void register() {
        KeyMappingRegistry.register(PULL_LEFT);
        KeyMappingRegistry.register(PULL_RIGHT);
        KeyMappingRegistry.register(LIFT);
        KeyMappingRegistry.register(PUSH);
    }

    /**
     * What the player would actually have to press, as bound right now.
     *
     * <p>The boss-bar strings used to end in a hardcoded {@code [W]}. That was already a lie for anyone
     * who had rebound movement in the vanilla Controls screen, and it would have become a lie for
     * everyone the moment these keys existed. One owner: the binding itself is asked.
     */
    public static String label(KeyMapping k) {
        return k.isUnbound() ? "—" : k.getTranslatedKeyMessage().getString();
    }
}
