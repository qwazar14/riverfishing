package com.riverfishing.command;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.riverfishing.RiverFishing;
import com.riverfishing.fish.FishProfile;
import com.riverfishing.fish.FishProfileManager;
import com.riverfishing.fishing.JournalData;
import com.riverfishing.registry.ModItems;
import dev.architectury.event.events.common.CommandRegistrationEvent;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * Server-side debug commands for the angler journal (ops only, §multiloader). Registered via Architectury's
 * {@link CommandRegistrationEvent} in place of Forge's {@code RegisterCommandsEvent}. The {@code getPersistentData}
 * calls in the bodies are the last Forge touch here — they go away in Stage 4 (JournalData → SavedData).
 */
public final class JournalCommand {
    private JournalCommand() {}

    public static void init() {
        CommandRegistrationEvent.EVENT.register((dispatcher, registry, selection) ->
                dispatcher.register(Commands.literal("rffish")
                        .requires(s -> s.hasPermission(2))
                        .then(Commands.literal("unlockall").executes(JournalCommand::unlockAll))
                        .then(Commands.literal("reset").executes(JournalCommand::reset))));
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
        sp.getPersistentData().put(JournalData.TAG, root);
        c.getSource().sendSuccess(() ->
                Component.literal("Unlocked the journal: all species, trophies, ice, XP -> all quest goals complete"), true);
        return 1;
    }

    private static int reset(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        sp.getPersistentData().remove(JournalData.TAG);
        sp.getPersistentData().remove(com.riverfishing.quest.QuestData.TAG);
        c.getSource().sendSuccess(() -> Component.literal("Cleared the fishing journal (records, XP, quests)"), true);
        return 1;
    }
}
