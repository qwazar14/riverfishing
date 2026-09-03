package com.riverfishing.network;

import com.riverfishing.RiverFishing;
import dev.architectury.utils.EnvExecutor;
import net.fabricmc.api.EnvType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;

/** Server → client: the player's journal records, so the client can open the bestiary screen (§15). */
public class JournalOpenPacket implements ModNetwork.RfPacket {
    public static final net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<JournalOpenPacket> TYPE = new net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<>(RiverFishing.id("journal_open"));
    public static final net.minecraft.network.codec.StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, JournalOpenPacket> STREAM_CODEC = net.minecraft.network.codec.StreamCodec.of((buf, pkt) -> pkt.write(buf), JournalOpenPacket::decode);

    private final CompoundTag data;
    /** §guide-nudge: a guide page to open on, or "" for the journal's normal front page. */
    private final String guide;

    public JournalOpenPacket(CompoundTag data) {
        this(data, "");
    }

    public JournalOpenPacket(CompoundTag data, String guide) {
        this.data = data;
        this.guide = guide == null ? "" : guide;
    }

    /** The journal for this player. Every sender uses this — see {@link #payload}. */
    public static JournalOpenPacket forPlayer(net.minecraft.server.level.ServerPlayer sp) {
        return forPlayer(sp, "");
    }

    public static JournalOpenPacket forPlayer(net.minecraft.server.level.ServerPlayer sp, String guide) {
        return new JournalOpenPacket(payload(sp), guide);
    }

    /**
     * Everything the screen needs, assembled in ONE place.
     *
     * <p>There were three senders and three different payloads: the journal item sent the records, the
     * claimed quests and the order; {@code /rffish} sent the raw records with no claimed set, so every
     * reward looked unclaimed; and unlocking a perk re-sent the journal with no order at all, which blanked
     * the order board of an open screen. Nothing made them agree — so now nothing has to.
     *
     * <p>§order-board: the SERVER builds the order checklist, because it is the only side that has the
     * fish profiles, the water body and the season, and it sends lang keys rather than sentences so a
     * client in any language draws it correctly.
     */
    private static CompoundTag payload(net.minecraft.server.level.ServerPlayer sp) {
        CompoundTag copy = com.riverfishing.item.JournalItem.exportFor(sp);
        copy.put("order", com.riverfishing.fishing.OrderBoard.build(sp));
        // §contracts-b1: the papers in the bag, and the world day so the tab can say how long is left.
        copy.put("contracts", com.riverfishing.fishing.Contracts.build(sp));
        copy.putLong("day", com.riverfishing.fishing.Contracts.today(sp.level()));
        // §journal-card (0.8.0): the species facts ride along for exactly the reason above. The screen
        // used to read FishProfileManager directly, which only has anything in singleplayer — on a
        // dedicated server every species page lost its water, bait, tackle, season and trophy weight
        // without saying so. One table, built once per open, and the client stops needing the profiles.
        copy.put("cards", com.riverfishing.fish.FishCard.buildAll());
        // §h §breeding: the genomes stocked in the region the player STANDS in — the client has no ledger.
        copy.put("pop", com.riverfishing.fishing.StockedData.get(sp.level())
                .genomes(com.riverfishing.fishing.StockedData.region(sp.blockPosition())));
        return copy;
    }

    @Override
    public net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<? extends net.minecraft.network.protocol.common.custom.CustomPacketPayload> type() {
        return TYPE;
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeNbt(data);
        buf.writeUtf(guide, 32);
    }

    public static JournalOpenPacket decode(FriendlyByteBuf buf) {
        return new JournalOpenPacket(buf.readNbt(), buf.readUtf(32));
    }

    public void handleClient() {
        EnvExecutor.runInEnv(EnvType.CLIENT,
                () -> () -> com.riverfishing.client.JournalScreen.open(
                        data == null ? new CompoundTag() : data, guide));
    }
}
