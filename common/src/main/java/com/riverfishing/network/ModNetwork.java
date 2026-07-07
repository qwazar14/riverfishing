package com.riverfishing.network;

import com.riverfishing.RiverFishing;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/** Minimal network channel for the float timing mini-game (#5). */
public final class ModNetwork {
    private static final String VERSION = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            RiverFishing.id("main"),
            () -> VERSION, VERSION::equals, VERSION::equals);

    private ModNetwork() {}

    public static void register() {
        int id = 0;
        CHANNEL.registerMessage(id++, FloatTimingPacket.class,
                FloatTimingPacket::encode, FloatTimingPacket::decode, FloatTimingPacket::handle);
        CHANNEL.registerMessage(id++, JournalOpenPacket.class,
                JournalOpenPacket::encode, JournalOpenPacket::decode, JournalOpenPacket::handle);
        CHANNEL.registerMessage(id++, LineSyncPacket.class,
                LineSyncPacket::encode, LineSyncPacket::decode, LineSyncPacket::handle);
        CHANNEL.registerMessage(id++, QuestClaimPacket.class,
                QuestClaimPacket::encode, QuestClaimPacket::decode, QuestClaimPacket::handle);
        CHANNEL.registerMessage(id++, SkillUnlockPacket.class,
                SkillUnlockPacket::encode, SkillUnlockPacket::decode, SkillUnlockPacket::handle);
        CHANNEL.registerMessage(id++, RodWarningPacket.class,
                RodWarningPacket::encode, RodWarningPacket::decode, RodWarningPacket::handle);
    }

    public static void toPlayer(ServerPlayer player, Object message) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), message);
    }

    /** Client → server (e.g. claiming a quest reward from the journal, §quests). */
    public static void toServer(Object message) {
        CHANNEL.sendToServer(message);
    }

    /** Sends to the player AND everyone who can see them (§line-multiplayer). */
    public static void toTracking(ServerPlayer player, Object message) {
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player), message);
    }
}
