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
import net.minecraft.resources.ResourceLocation;
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
                        .then(Commands.literal("unlockall")
                                .requires(s -> s.hasPermission(2)).executes(JournalCommand::unlockAll))
                        .then(Commands.literal("reset")
                                .requires(s -> s.hasPermission(2)).executes(JournalCommand::reset))));
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
                new com.riverfishing.network.JournalOpenPacket(JournalData.get(sp), page));
        return 1;
    }

    private static int unlockAll(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        for (String species : ModItems.FISH_SPECIES) {
            ResourceLocation id = RiverFishing.id(species);
            FishProfile p = FishProfileManager.get().byId(id);
            int w = p != null ? (int) Math.round(p.weightMax) : 100000;
            JournalData.record(sp, id, w);
        }
        net.minecraft.nbt.CompoundTag root = JournalData.get(sp);
        root.putInt(JournalData.TOTAL, Math.max(root.getInt(JournalData.TOTAL), 120));
        root.putInt(JournalData.TROPHIES, Math.max(root.getInt(JournalData.TROPHIES), 10));
        root.putInt(JournalData.ICE, Math.max(root.getInt(JournalData.ICE), 40));
        root.putLong(JournalData.XP, Math.max(root.getLong(JournalData.XP), JournalData.xpForLevel(25)));
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
