package com.riverfishing.fishing;

import com.riverfishing.RiverFishing;
import com.riverfishing.component.ComponentSlot;
import com.riverfishing.component.RigType;
import com.riverfishing.component.RodType;
import com.riverfishing.engine.Season;
import com.riverfishing.engine.TimeOfDay;
import com.riverfishing.fish.FishProfile;
import com.riverfishing.fish.FishProfileManager;
import com.riverfishing.rig.RigData;
import com.riverfishing.item.RigItem;
import com.riverfishing.item.RodData;
import com.riverfishing.item.RodItem;
import com.riverfishing.integration.SeasonProvider;
import com.riverfishing.water.WaterBody;
import com.riverfishing.water.WaterBodyCache;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * §order-board (0.7.0): the order of the day, written out as the recipe for catching it.
 *
 * <p>Terraria's Angler is a tutorial wearing the costume of a chore: because the quest names the biome in
 * plain words, thirty quests walk a player through thirty habitats and they never once open a wiki. This
 * is the same trick with the mod's own data — the order prints the target's ADMISSION CONDITIONS from the
 * bite engine as a checklist, and every condition the player already meets is ticked against where they
 * are standing and what they are holding.
 *
 * <p>One panel, read once, teaches water type, depth band, seasonality, the daily window, bait choice and
 * which rig the fish expects. Nothing here is authored: every line is the fish profile the engine reads,
 * so the board cannot teach something the engine does not do.
 *
 * <p>The server builds it and sends keys, never sentences: the client turns
 * {@code water.riverfishing.river} into its own language. That also keeps the checklist honest in
 * multiplayer, where the client has no fish profiles at all.
 */
public final class OrderBoard {
    /** Every fifth filled order pays out. A fixed ladder, so the grind has a visible spine. */
    private static final int MILESTONE_EVERY = 5;

    /** The ladder. Named gear, in the order a growing angler would want it. */
    private static final String[] MILESTONES = {
            "fish_finder",      // 5  — read the water instead of guessing at it
            "reel_6000",        // 10 — a reel that can hold a proper fish
            "rig_feeder",       // 15 — the rig half the species list asks for
            "digital_alarm",    // 20 — sit two rods at once and hear both
            "carp_rod",         // 25 — a real blank
            "reel_10000"        // 30 — and the drag to go under it
    };

    private static final String TAG = "riverfishing_orders";

    private OrderBoard() {}

    // ---- progress ------------------------------------------------------------------------------------

    /** How many orders this player has filled. */
    public static int filled(net.minecraft.world.entity.player.Player player) {
        return PlayerData.root(player).getCompoundOrEmpty(TAG).getIntOr("filled", 0);
    }

    /**
     * A prime specimen of today's order was landed. Credits it once per day, and pays the ladder when the
     * count lands on a rung.
     */
    public static void credit(ServerPlayer sp, Identifier species) {
        ServerLevel level = sp.level();
        if (!MarketData.orderOfTheDay(level).equals(species.getPath())) return;
        long day = level.getServer().overworld().getOverworldClockTime() / 24000L;
        CompoundTag root = PlayerData.root(sp).getCompoundOrEmpty(TAG);
        if (root.getLongOr("day", 0L) == day) return;        // one order a day, not one fish a minute
        root.putLong("day", day);
        int n = root.getIntOr("filled", 0) + 1;
        root.putInt("filled", n);
        PlayerData.root(sp).put(TAG, root);
        PlayerData.markDirty(sp);

        sp.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                "message.riverfishing.order_filled", n).withStyle(net.minecraft.ChatFormatting.GREEN));

        int rung = n / MILESTONE_EVERY - 1;
        if (n % MILESTONE_EVERY == 0 && rung >= 0 && rung < MILESTONES.length) {
            ItemStack prize = reward(MILESTONES[rung]);
            if (!prize.isEmpty()) {
                if (!sp.getInventory().add(prize)) sp.drop(prize, false);
                sp.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                                "message.riverfishing.order_milestone", n, prize.getHoverName())
                        .withStyle(net.minecraft.ChatFormatting.GOLD));
            }
        }
    }

    private static ItemStack reward(String id) {
        // §26.x: Registry.get returns Optional<Holder.Reference>; getValue is the direct lookup.
        var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(RiverFishing.id(id));
        if (item == null || item == net.minecraft.world.item.Items.AIR) {
            // A ladder rung that points at nothing would silently pay nothing — say so once, loudly.
            RiverFishing.LOGGER.warn("§order-board: milestone item '{}' does not exist", id);
            return ItemStack.EMPTY;
        }
        return new ItemStack(item);
    }

    // ---- the checklist -------------------------------------------------------------------------------

    /**
     * The order written out, with every condition ticked against this player right now. Returns an empty
     * tag when there is nothing to show (an unknown species, a datapack that removed it).
     */
    public static CompoundTag build(ServerPlayer sp) {
        ServerLevel level = sp.level();
        String path = MarketData.orderOfTheDay(level);
        FishProfile p = FishProfileManager.get().byId(RiverFishing.id(path));
        CompoundTag out = new CompoundTag();
        if (p == null) return out;

        out.putString("species", path);
        out.putInt("filled", filled(sp));
        out.putInt("every", MILESTONE_EVERY);
        ListTag ladder = new ListTag();
        for (String m : MILESTONES) ladder.add(StringTag.valueOf(m));
        out.put("ladder", ladder);

        // Where the player is and what they hold — the two things the ticks are measured against.
        BlockPos surface = SpookTracker.nearestSurface(level, sp.blockPosition(), 8.0);
        WaterBody body = surface == null ? null : WaterBodyCache.forLevel(level).get(level, surface);
        int depth = surface == null ? -1 : FishingManager.measureDepth(level, surface);
        Season season = SeasonProvider.getSeason(level);
        TimeOfDay time = TimeOfDay.fromDayTime(level.getOverworldClockTime());
        ItemStack rod = sp.getMainHandItem();
        if (!(rod.getItem() instanceof RodItem)) rod = sp.getOffhandItem();

        ListTag rows = new ListTag();

        // 1. Water. The single most-taught line: the order names the habitat in plain words.
        rows.add(row("order.riverfishing.water", keys("water.riverfishing.", positives(p.waterBodies)),
                body != null && p.waterFactor(body.type()) > 0));

        // 2. Depth band, in blocks, straight from the habitat gate.
        String depthText = p.depthMax > 0 && p.depthMax < 100
                ? p.depthMin + "–" + p.depthMax
                : "≥ " + p.depthMin;
        rows.add(text("order.riverfishing.depth", depthText,
                depth >= 0 && depth >= p.depthMin && (p.depthMax <= 0 || depth <= p.depthMax)));

        // 3. Season and 4. hour: the best entries in each table, which is what "when to come" means.
        List<String> bestSeasons = best(p.season);
        rows.add(row("order.riverfishing.season", keys("season.riverfishing.", bestSeasons),
                season == null || bestSeasons.contains(season.jsonKey())));
        List<String> bestTimes = best(p.time);
        rows.add(row("order.riverfishing.time", keys("time.riverfishing.", bestTimes),
                bestTimes.contains(time.jsonKey())));

        // 5. Bait — the top three it rates, ticked if anything on the rig is on its menu at all.
        List<String> baits = topBaits(p);
        boolean baitOk = false;
        ItemStack rigStack = rod.getItem() instanceof RodItem ? RodData.get(rod, ComponentSlot.RIG) : ItemStack.EMPTY;
        if (rigStack.getItem() instanceof RigItem) {
            for (String b : RigData.baitIds(rigStack)) {
                if (p.baitScore(b) > 0) baitOk = true;
            }
        }
        rows.add(row("order.riverfishing.bait", keys("item.riverfishing.", baits), baitOk));

        // 6. Rig, and 7. rod: the tackle the species expects.
        boolean rigOk = rigStack.getItem() instanceof RigItem ri && p.idealRigs.contains(ri.rigType().jsonKey());
        rows.add(row("order.riverfishing.rig", keys("item.riverfishing.rig_", p.idealRigs), rigOk));
        boolean rodOk = rod.getItem() instanceof RodItem ri && p.idealRods.contains(ri.rodType().jsonKey());
        rows.add(rowSuffix("order.riverfishing.rod", "item.riverfishing.", p.idealRods, "_rod", rodOk));

        // 8. The angler level the species asks for, when it asks for one.
        if (p.minAnglerLevel > 0) {
            rows.add(text("order.riverfishing.level", String.valueOf(p.minAnglerLevel),
                    JournalData.getLevel(sp) >= p.minAnglerLevel));
        }
        // 9. And the one thing that is about the BUYER rather than the fish: which fisherman takes it.
        // This is the line that exists because the board had to be written down — see §order-tier.
        int tier = com.riverfishing.registry.ModVillagers.buyTier(path);
        if (tier > 0) {
            CompoundTag t = text("order.riverfishing.buyer", String.valueOf(tier), false);
            t.putBoolean("info", true);      // information, not a condition: nothing to tick
            rows.add(t);
        }

        out.put("rows", rows);
        return out;
    }

    private static CompoundTag row(String label, List<String> valueKeys, boolean ok) {
        CompoundTag t = new CompoundTag();
        t.putString("l", label);
        ListTag v = new ListTag();
        for (String k : valueKeys) v.add(StringTag.valueOf(k));
        t.put("v", v);
        t.putBoolean("ok", ok);
        return t;
    }

    private static CompoundTag rowSuffix(String label, String prefix, Iterable<String> ids,
                                         String suffix, boolean ok) {
        List<String> keys = new ArrayList<>();
        for (String id : ids) keys.add(prefix + id + suffix);
        return row(label, keys, ok);
    }

    private static CompoundTag text(String label, String value, boolean ok) {
        CompoundTag t = new CompoundTag();
        t.putString("l", label);
        t.putString("t", value);
        t.putBoolean("ok", ok);
        return t;
    }

    private static List<String> keys(String prefix, Iterable<String> ids) {
        List<String> out = new ArrayList<>();
        for (String id : ids) out.add(prefix + id);
        return out;
    }

    private static List<String> positives(Map<String, Double> table) {
        List<String> out = new ArrayList<>();
        for (var e : table.entrySet()) {
            if (e.getValue() > 0) out.add(e.getKey());
        }
        return out;
    }

    /** The entries at the top of a season/time table — "when this fish is actually on the feed". */
    private static List<String> best(Map<String, Double> table) {
        double top = 0;
        for (double v : table.values()) top = Math.max(top, v);
        List<String> out = new ArrayList<>();
        for (var e : table.entrySet()) {
            if (top > 0 && e.getValue() >= top * 0.999) out.add(e.getKey());
        }
        return out;
    }

    private static List<String> topBaits(FishProfile p) {
        return p.baitScores.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .limit(3)
                .map(Map.Entry::getKey)
                .toList();
    }

    /** Kept so the rig/rod imports read as deliberate rather than incidental. */
    static {
        assert RigType.values().length > 0 && RodType.values().length > 0;
    }
}
