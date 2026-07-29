package com.riverfishing.fishing;

import com.riverfishing.RiverFishing;
import com.riverfishing.component.RodClass;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * §guide-nudge (0.7.0): the Super Guide rule — help arrives only for the player who is actually stuck,
 * arrives once, and is never imposed.
 *
 * <p>A counter per (rod class, kind of failure) sits in the player's own data. Eight of the same failure
 * with nothing landed in between, and the mod offers — ONE line, clickable, ignorable — to open the page
 * that teaches that flow. Landing anything at all clears every counter for that rod class: the moment the
 * player works it out, the offer stops being on its way.
 *
 * <p>Deliberate limits:
 * <ul>
 *   <li><b>Once per rod class per world.</b> A player who has seen the page has seen it. The journal shelf
 *       holds every page anyway, so reading it again is a decision they make, not one the mod makes.</li>
 *   <li><b>An offer, never a screen.</b> Nothing opens by itself and nothing blocks the game. If it is
 *       ignored it costs one line of chat, which is the price of being wrong about someone being stuck.</li>
 *   <li><b>Honest bookkeeping.</b> Accept the help and the next fish is marked in the journal as caught
 *       with a hint. Nintendo's dignity rule: nothing is taken away, the record simply tells the truth.</li>
 * </ul>
 *
 * <p>It is also free telemetry with nobody's data in it: every offer logs which class and which failure
 * tripped it, so the flows that are genuinely unteachable show up in a server log rather than in a bug
 * report that nobody bothers to write.
 */
public final class GuideNudge {
    /** Eight of the same failure with nothing landed in between. */
    private static final int THRESHOLD = 8;

    private static final String TAG = "riverfishing_guide";

    // Kinds of failure. Each keeps its own count, so a run of eight snapped lines is not diluted by the
    // odd missed strike in between — they are different problems and they have different answers.
    public static final String EMPTY = "empty";          // the retrieve or the wait came back with nothing
    public static final String NO_BITES = "no_bites";    // the cast was refused outright
    public static final String MISSED = "missed";        // the take came and went unanswered
    public static final String SHAKE_OFF = "shake_off";  // hooked, then lost
    public static final String BREAK = "break";          // the line or the leader parted
    public static final String SNAG = "snag";            // hung up and lost the rig

    private GuideNudge() {}

    /**
     * The page that answers this failure. The kind wins where it is more specific than the rod class — a
     * player who keeps snapping the line needs the tackle-stress page whatever they are holding.
     */
    private static String page(RodClass rodClass, String kind) {
        return switch (kind) {
            case BREAK -> "stress";
            case SHAKE_OFF -> "drag";
            default -> rodClass == RodClass.ACTIVE ? "lurework" : "waiting";
        };
    }

    /** Record a failure and, on the eighth of its kind, offer the page once. */
    public static void failure(ServerPlayer sp, RodClass rodClass, String kind) {
        if (rodClass == null) return;
        CompoundTag root = get(sp);
        if (root.getBooleanOr("shown." + rodClass.name(), false)) return;   // this class has had its one offer

        String key = "n." + rodClass.name() + "." + kind;
        int n = root.getIntOr(key, 0) + 1;
        root.putInt(key, n);
        if (n < THRESHOLD) {
            put(sp, root);
            return;
        }
        root.putBoolean("shown." + rodClass.name(), true);
        root.putString("offered." + rodClass.name(), page(rodClass, kind));
        put(sp, root);
        offer(sp, page(rodClass, kind));
        // §guide-telemetry: which flow could not be worked out, with no data collection of any kind —
        // it is a line in the server's own log, for the one person who can fix the flow.
        RiverFishing.LOGGER.info("§guide-nudge: offered '{}' after {} × {} on a {} rod",
                page(rodClass, kind), THRESHOLD, kind, rodClass.name().toLowerCase());
    }

    /** Landed a fish: this rod class is working, so nothing about it is on its way to being offered. */
    public static void success(ServerPlayer sp, RodClass rodClass) {
        if (rodClass == null) return;
        CompoundTag root = get(sp);
        boolean any = false;
        for (String k : root.keySet().toArray(new String[0])) {
            if (k.startsWith("n." + rodClass.name() + ".")) {
                root.remove(k);
                any = true;
            }
        }
        if (any) put(sp, root);
    }

    /**
     * One line, clickable, and that is the whole of it. Chat rather than the action bar on purpose: the
     * action bar is gone in three seconds, and an offer that vanishes before it is read is not an offer.
     */
    private static void offer(ServerPlayer sp, String guideId) {
        Component title = Component.translatable("guide.riverfishing." + guideId + ".title");
        sp.sendSystemMessage(Component.translatable("message.riverfishing.guide_offer", title)
                .withStyle(s -> s.withColor(ChatFormatting.AQUA)
                        .withClickEvent(new ClickEvent.RunCommand("/rffish guide " + guideId))
                        .withHoverEvent(new HoverEvent.ShowText(
                                Component.translatable("message.riverfishing.guide_offer_hover")))));
    }

    /**
     * The player took the offer. From here the next fish is marked in the journal as caught with a hint —
     * nothing is taken away and nothing is locked, the record just says what happened.
     */
    public static void accepted(Player player) {
        CompoundTag root = get(player);
        root.putBoolean("pending_hint", true);
        put(player, root);
    }

    /**
     * Consume the "caught with a hint" mark, if one is owed. Called once per landed fish.
     *
     * @return true if THIS catch is the one that follows accepted help
     */
    public static boolean consumeHint(Player player) {
        CompoundTag root = get(player);
        if (!root.getBooleanOr("pending_hint", false)) return false;
        root.remove("pending_hint");
        put(player, root);
        return true;
    }

    private static CompoundTag get(Player player) {
        return PlayerData.root(player).getCompoundOrEmpty(TAG);
    }

    private static void put(Player player, CompoundTag root) {
        PlayerData.root(player).put(TAG, root);
        PlayerData.markDirty(player);
    }

    /** Guide ids this mechanic can offer — the command accepts these and nothing else. */
    public static boolean isOfferable(String guideId) {
        return guideId.equals("stress") || guideId.equals("drag")
                || guideId.equals("lurework") || guideId.equals("waiting");
    }

}
