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
                        .then(Commands.literal("guide")
                                .then(Commands.argument("page", StringArgumentType.word())
                                        .executes(JournalCommand::guide)))
                        // §fish-give: a fish by name, made the way the water makes one — see give()
                        .then(Commands.literal("give").requires(net.minecraft.commands.Commands.hasPermission(net.minecraft.commands.Commands.LEVEL_GAMEMASTERS))
                                .then(Commands.argument("species", StringArgumentType.word())
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
                        .then(Commands.literal("unlockall")
                                .requires(net.minecraft.commands.Commands.hasPermission(net.minecraft.commands.Commands.LEVEL_GAMEMASTERS))
                                .executes(JournalCommand::unlockAll))
                        .then(Commands.literal("reset")
                                .requires(net.minecraft.commands.Commands.hasPermission(net.minecraft.commands.Commands.LEVEL_GAMEMASTERS))
                                .executes(JournalCommand::reset))));
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
            if (!sp.getInventory().add(fish)) sp.drop(fish, false);
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
        }
        net.minecraft.nbt.CompoundTag root = JournalData.get(sp);
        root.putInt(JournalData.TOTAL, Math.max(root.getIntOr(JournalData.TOTAL, 0), 120));
        root.putInt(JournalData.TROPHIES, Math.max(root.getIntOr(JournalData.TROPHIES, 0), 10));
        root.putInt(JournalData.ICE, Math.max(root.getIntOr(JournalData.ICE, 0), 40));
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
