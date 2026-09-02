package com.riverfishing.network;

import com.riverfishing.RiverFishing;
import dev.architectury.utils.EnvExecutor;
import net.fabricmc.api.EnvType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

/**
 * §finder-screen: one sounding, told to the player who took it.
 *
 * <p>Built by {@code FishingManager.finderPayload} — the client cannot work any of it out, because fish
 * profiles are server data and the stock, community and pressure numbers are world state. Keys, never
 * sentences, so the screen renders in the reader's own language.
 *
 * <p>{@code hud} separates the two ways a sounding arrives: a right-click OPENS the screen, while the
 * strip that runs while the finder is simply held only wants the numbers and must never steal the
 * player's controls (§finder-hud).
 */
public class FinderPacket implements ModNetwork.RfPacket {
    public static final net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<FinderPacket> TYPE =
            new net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<>(RiverFishing.id("finder"));
    public static final net.minecraft.network.codec.StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, FinderPacket> STREAM_CODEC =
            net.minecraft.network.codec.StreamCodec.of((buf, pkt) -> pkt.write(buf), FinderPacket::decode);

    private final CompoundTag data;
    private final boolean hud;

    public FinderPacket(CompoundTag data, boolean hud) {
        this.data = data;
        this.hud = hud;
    }

    @Override
    public net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<? extends net.minecraft.network.protocol.common.custom.CustomPacketPayload> type() {
        return TYPE;
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeNbt(data);
        buf.writeBoolean(hud);
    }

    public static FinderPacket decode(FriendlyByteBuf buf) {
        return new FinderPacket(buf.readNbt(), buf.readBoolean());
    }

    public void handleClient() {
        EnvExecutor.runInEnv(EnvType.CLIENT,
                () -> () -> com.riverfishing.client.FinderState.accept(data, hud));
    }
}
