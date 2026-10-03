package com.riverfishing.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.riverfishing.RiverFishing;
import com.riverfishing.fish.FishProfile;
import com.riverfishing.fish.FishProfileManager;
import com.riverfishing.fishing.JournalData;
import com.riverfishing.fishing.PlayerData;
import com.riverfishing.registry.ModItems;
import dev.architectury.event.events.common.CommandRegistrationEvent;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/**
 * Server-side debug commands for the angler journal (ops only, §multiloader). Registered via Architectury's
 * {@link CommandRegistrationEvent} in place of Forge's {@code RegisterCommandsEvent}. Player data goes
 * through the cross-loader {@link PlayerData} store (§multiloader).
 */
public final class JournalCommand {
    private JournalCommand() {}

    public static void init() {
        CommandRegistrationEvent.EVENT.register((dispatcher, registry, selection) ->
                dispatcher.register(Commands.literal("rffish")
                        // §guide-nudge: the ONE branch any player may run — it is what the offered line
                        // clicks, and it does nothing but open a page the journal already holds.
                        // §hints-toggle: any player — the lines that explain a quiet water, off for immersion or back on
                        .then(Commands.literal("hints")
                                .then(Commands.literal("on").executes(c -> hints(c, true)))
                                .then(Commands.literal("off").executes(c -> hints(c, false))))
                        .then(Commands.literal("guide")
                                .then(Commands.argument("page", StringArgumentType.word())
                                        .executes(JournalCommand::guide)))
                        // §fish-give: a fish by name, made the way the water makes one — see give()
                        .then(Commands.literal("give").requires(net.minecraft.commands.Commands.hasPermission(net.minecraft.commands.Commands.LEVEL_GAMEMASTERS))
                                .then(Commands.argument("species", StringArgumentType.word()).suggests(SPECIES)
                                        .executes(c -> give(c, "", 1, -1))
                                        .then(Commands.argument("variety", StringArgumentType.word())
                                                .executes(c -> give(c, StringArgumentType.getString(c, "variety"), 1, -1))
                                                .then(Commands.argument("count", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 64))
                                                        .executes(c -> give(c, StringArgumentType.getString(c, "variety"),
                                                                com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "count"), -1))
                                                        .then(Commands.argument("pattern", com.mojang.brigadier.arguments.IntegerArgumentType.integer(-1, 999))
                                                                .executes(c -> give(c, StringArgumentType.getString(c, "variety"),
                                                                        com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "count"),
                                                                        com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "pattern"))))))))
                        // §event-fish (1.1.0): an organiser's hand in the water, for competitions
                        .then(Commands.literal("spawn").requires(net.minecraft.commands.Commands.hasPermission(net.minecraft.commands.Commands.LEVEL_GAMEMASTERS))
                                .then(Commands.argument("species", StringArgumentType.word()).suggests(SPECIES)
                                        .then(Commands.argument("count", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 2000))
                                                .executes(c -> spawn(c, false, -1))
                                                .then(Commands.argument("grams", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1))
                                                        .executes(c -> spawn(c, false, com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "grams")))))
                                        .then(Commands.literal("trophy")
                                                .executes(c -> spawn(c, true, -1))
                                                .then(Commands.argument("grams", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1))
                                                        .executes(c -> spawn(c, true, com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "grams")))))))
                        .then(Commands.literal("clear").requires(net.minecraft.commands.Commands.hasPermission(net.minecraft.commands.Commands.LEVEL_GAMEMASTERS))
                                .executes(c -> clear(c, false))
                                .then(Commands.literal("all").executes(c -> clear(c, true))))
                        .then(Commands.literal("census").requires(net.minecraft.commands.Commands.hasPermission(net.minecraft.commands.Commands.LEVEL_GAMEMASTERS))
                                .executes(JournalCommand::census))
                        .then(Commands.literal("unlockall")
                                .requires(net.minecraft.commands.Commands.hasPermission(net.minecraft.commands.Commands.LEVEL_GAMEMASTERS))
                                .executes(JournalCommand::unlockAll))
                        .then(Commands.literal("reset")
                                .requires(net.minecraft.commands.Commands.hasPermission(net.minecraft.commands.Commands.LEVEL_GAMEMASTERS))
                                .executes(JournalCommand::reset))));
    }

    /** Every species id, for the commands that name one. */
    private static final com.mojang.brigadier.suggestion.SuggestionProvider<CommandSourceStack> SPECIES = (c, b) ->
            net.minecraft.commands.SharedSuggestionProvider.suggest(
                    FishProfileManager.get().all().stream().map(p -> p.id.getPath()).sorted(), b);

    private static final String EV = "command.riverfishing.event.";

    /**
     * §event-fish: the wild water at the command's position (so {@code execute positioned … run rffish spawn}
     * works from a console or a command block), or a failure said why. A claimed pond is its owner's to stock.
     */
    private static com.riverfishing.alife.Lake eventWater(CommandContext<CommandSourceStack> c) {
        net.minecraft.server.level.ServerLevel level = c.getSource().getLevel();
        BlockPos pos = BlockPos.containing(c.getSource().getPosition());
        if (!com.riverfishing.config.RiverFishingConfig.alife()) {
            c.getSource().sendFailure(Component.translatable(EV + "off"));
            return null;
        }
        if (com.riverfishing.fishing.PondLife.claimNear(level, pos) != null) {
            c.getSource().sendFailure(Component.translatable(EV + "pond"));
            return null;
        }
        com.riverfishing.alife.Lake lake = com.riverfishing.fishing.AlifeData.get(level).lakeAt(level, pos);
        if (lake.zones.isEmpty()) {
            c.getSource().sendFailure(Component.translatable(EV + "no_water"));
            return null;
        }
        return lake;
    }

    /** §event-fish: /rffish spawn <species> <count> [grams] | <species> trophy [grams]. */
    private static int spawn(CommandContext<CommandSourceStack> c, boolean trophy, int grams) {
        String id = StringArgumentType.getString(c, "species");
        FishProfile p = FishProfileManager.get().byId(RiverFishing.id(id));
        if (p == null) {
            c.getSource().sendFailure(Component.translatable(EV + "no_species", id));
            return 0;
        }
        com.riverfishing.alife.Lake lake = eventWater(c);
        if (lake == null) return 0;
        int count = trophy ? 1 : com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "count");
        // a trophy is the kind the water grows itself: well over the mean, under the record
        double g = grams > 0 ? grams : trophy ? Math.min(p.weightMax * 0.9, p.weightMean * 3) : p.weightMean;
        g = Math.max(Math.max(1, p.weightMin), Math.min(p.weightMax, g));
        BlockPos pos = BlockPos.containing(c.getSource().getPosition());
        int groups = com.riverfishing.fishing.AlifeData.spawnEvent(lake, p, pos, count, g, trophy);
        com.riverfishing.fishing.AlifeData.get(c.getSource().getLevel()).setDirty();
        if (groups < 0) {
            c.getSource().sendFailure(Component.translatable(EV + "full", lake.agents.size()));
            return 0;
        }
        Component name = Component.translatable("fish.riverfishing." + id);
        long shown = Math.round(g);
        int made = groups;
        c.getSource().sendSuccess(() -> trophy
                ? Component.translatable(EV + "trophy", name, shown)
                : Component.translatable(EV + "spawned", name, count, shown, made), true);
        return count;
    }

    /** §event-fish: /rffish clear — the event's fish out of this water; /rffish clear all — every fish in it. */
    private static int clear(CommandContext<CommandSourceStack> c, boolean all) {
        com.riverfishing.alife.Lake lake = eventWater(c);
        if (lake == null) return 0;
        int[] gone = com.riverfishing.fishing.AlifeData.clearEvent(lake, all);
        com.riverfishing.fishing.AlifeData.get(c.getSource().getLevel()).setDirty();
        c.getSource().sendSuccess(() -> Component.translatable(EV + (all ? "cleared_all" : "cleared"), gone[0], gone[1]), true);
        return gone[0];
    }

    /** §event-fish: /rffish census — what swims in this water now, species by species, and how much of it is the event's. */
    private static int census(CommandContext<CommandSourceStack> c) {
        com.riverfishing.alife.Lake lake = eventWater(c);
        if (lake == null) return 0;
        var rows = com.riverfishing.fishing.AlifeData.census(lake);
        double fish = 0, kg = 0, event = 0;
        for (var r : rows) {
            fish += r.getValue()[1];
            kg += r.getValue()[2];
            event += r.getValue()[3];
        }
        net.minecraft.network.chat.MutableComponent out = Component.translatable(EV + "census",
                (long) fish, String.format(java.util.Locale.ROOT, "%.1f", kg), (long) event);
        for (var r : rows) {
            double[] v = r.getValue();
            out.append("\n").append(Component.translatable(EV + "census_line", Component.translatable("fish.riverfishing." + r.getKey()),
                    (long) v[1], String.format(java.util.Locale.ROOT, "%.1f", v[2]), (long) v[0]));
            if (v[3] > 0) out.append(Component.translatable(EV + "census_event", (long) v[3]));
        }
        c.getSource().sendSuccess(() -> out, false);
        return (int) fish;
    }

    private static int hints(CommandContext<CommandSourceStack> c, boolean on) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        PlayerData.root(sp).putBoolean(com.riverfishing.fishing.FishingManager.NO_HINTS, !on);
        PlayerData.markDirty(sp);
        c.getSource().sendSuccess(() -> Component.translatable(on ? "message.riverfishing.hints_on" : "message.riverfishing.hints_off"), false);
        return 1;
    }

    /**
     * §guide-nudge: open the journal on a guide page. Only the handful of pages the nudge can offer are
     * accepted, so this cannot become a way to poke at the journal from a command block.
     */
    private static int guide(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        String page = StringArgumentType.getString(c, "page");
        if (!com.riverfishing.fishing.GuideNudge.isOfferable(page)) return 0;
        com.riverfishing.fishing.GuideNudge.accepted(sp);
        com.riverfishing.network.ModNetwork.toPlayer(sp,
                com.riverfishing.network.JournalOpenPacket.forPlayer(sp, page));
        return 1;
    }

    /**
     * §fish-give: {@code /rffish give <species> [variety|random|all] [count] [pattern]}. The fish is built
     * through FishItem.create and CatchCard.debug — the body() every catch goes through — so it has a
     * genome, a sex, a nature and a size class the way a caught one does, and on 26.x it is stamped.
     * A koi variety may be named bare ("kohaku") or as the card writes it ("koi_kohaku"); {@code all}
     * on a koi is one of each of the seventeen; {@code random} lets the water draw.
     */
    private static int give(CommandContext<CommandSourceStack> c, String variety, int count, int pattern)
            throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) sp.level();
        String path = StringArgumentType.getString(c, "species");
        var id = RiverFishing.id(path);
        FishProfile p = FishProfileManager.get().byId(id);
        if (p == null || ModItems.fishItem(id) == null) {
            c.getSource().sendFailure(Component.literal("no such fish: " + path));
            return 0;
        }
        boolean koi = com.riverfishing.fish.Genome.isKoiId(path);
        java.util.List<String> varieties = new java.util.ArrayList<>();
        if (koi && "all".equals(variety)) {
            for (String v : com.riverfishing.fish.Genome.koiVarieties()) varieties.add("koi_" + v);
        } else {
            String v = "random".equals(variety) ? "" : variety;
            if (koi && !v.isEmpty() && !v.startsWith("koi_")) v = "koi_" + v;
            for (int i = 0; i < count; i++) varieties.add(v);
        }
        java.util.Random rng = new java.util.Random(level.getGameTime());
        int made = 0;
        for (String v : varieties) {
            int weightG = (int) Math.round(Math.max(p.weightMin, Math.min(p.weightMax,
                    p.weightMean * (0.6 + 0.8 * rng.nextDouble()))));
            double t = p.weightMax > p.weightMin ? (weightG - p.weightMin) / (p.weightMax - p.weightMin) : 0.5;
            int lengthCm = (int) Math.round(p.lengthMin + (p.lengthMax - p.lengthMin) * Math.max(0, Math.min(1, t)));
            net.minecraft.world.item.ItemStack fish = com.riverfishing.item.FishItem.create(
                    ModItems.fishItem(id), id, weightG, lengthCm, true);
            final String vv = v;
            final int ww = weightG;
            com.riverfishing.item.StackNbt.mutate(fish, tag -> tag.put(com.riverfishing.fish.CatchCard.TAG,
                    com.riverfishing.fish.CatchCard.debug(sp, level, p, ww, sp.blockPosition(), vv, pattern)));
            com.riverfishing.item.FishItem.stampIcon(fish);   // 26.x: the icon is the stack
            if (!sp.getInventory().add(fish)) com.riverfishing.compat.Mc.drop(sp, fish, false);
            made++;
        }
        final int n = made;
        c.getSource().sendSuccess(() -> Component.literal("gave " + n + " " + path
                + (variety.isEmpty() ? "" : " (" + variety + ")")), true);
        return n;
    }

    private static int unlockAll(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        for (String species : ModItems.FISH_SPECIES) {
            Identifier id = RiverFishing.id(species);
            FishProfile p = FishProfileManager.get().byId(id);
            int w = p != null ? (int) Math.round(p.weightMax) : 100000;
            JournalData.record(sp, id, w);
            if (p != null) JournalData.recordTraits(sp, p.group, p.diet, w);   // §progression: the counters the quests read
        }
        net.minecraft.nbt.CompoundTag root = JournalData.get(sp);
        root.putInt(JournalData.TOTAL, Math.max(root.getIntOr(JournalData.TOTAL, 0), 120));
        root.putInt(JournalData.TROPHIES, Math.max(root.getIntOr(JournalData.TROPHIES, 0), 10));
        root.putInt(JournalData.ICE, Math.max(root.getIntOr(JournalData.ICE, 0), 40));
        net.minecraft.nbt.CompoundTag provs = new net.minecraft.nbt.CompoundTag();
        for (String pr : com.riverfishing.water.Provinces.ALL) provs.putBoolean(pr, true);
        root.put("provinces", provs);
        net.minecraft.nbt.CompoundTag seasons = new net.minecraft.nbt.CompoundTag();
        for (String s : new String[]{"spring", "summer", "autumn", "winter"}) seasons.putBoolean(s, true);
        root.put("seasons", seasons);
        root.putLong(JournalData.XP, Math.max(root.getLongOr(JournalData.XP, 0L), JournalData.xpForLevel(25)));
        PlayerData.root(sp).put(JournalData.TAG, root);
        PlayerData.markDirty(sp);
        c.getSource().sendSuccess(() ->
                Component.literal("Unlocked the journal: all species, trophies, ice, XP -> all quest goals complete"), true);
        return 1;
    }

    private static int reset(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        PlayerData.root(sp).remove(JournalData.TAG);
        PlayerData.root(sp).remove(com.riverfishing.quest.QuestData.TAG);
        PlayerData.markDirty(sp);
        c.getSource().sendSuccess(() -> Component.literal("Cleared the fishing journal (records, XP, quests)"), true);
        return 1;
    }
}
