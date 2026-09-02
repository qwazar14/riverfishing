package com.riverfishing.network;

import com.riverfishing.RiverFishing;
import dev.architectury.utils.EnvExecutor;
import net.fabricmc.api.EnvType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * §contracts: this fisherman's board, told to the player who just opened his counter — the same moment
 * and the same reason as {@link OrderPacket}. {@code vid} is the villager's entity id so a click can
 * name him back; {@code rep} is this player's reputation, for the caption; {@code posts} are the
 * three posts in paper format.
 */
public record ContractBoardPacket(CompoundTag tag) implements ModNetwork.RfPacket {

    public static final CustomPacketPayload.Type<ContractBoardPacket> TYPE =
            new CustomPacketPayload.Type<>(RiverFishing.id("contract_board"));

    public static final StreamCodec<FriendlyByteBuf, ContractBoardPacket> STREAM_CODEC =
            StreamCodec.of((buf, p) -> buf.writeNbt(p.tag()), buf -> new ContractBoardPacket(buf.readNbt()));

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeNbt(tag);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handleClient() {
        EnvExecutor.runInEnv(EnvType.CLIENT, () -> () -> com.riverfishing.client.ContractBoardState.accept(tag));
    }
}
