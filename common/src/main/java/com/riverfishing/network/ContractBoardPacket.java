package com.riverfishing.network;

import com.riverfishing.RiverFishing;
import dev.architectury.utils.EnvExecutor;
import net.fabricmc.api.EnvType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/**
 * §contracts: this fisherman's board, told to the player who just opened his counter — the same moment
 * and the same reason as {@link OrderPacket}. {@code vid} is the villager's entity id so a click can
 * name him back; {@code rep} is this player's reputation, for the caption; {@code posts} are the
 * three posts in paper format.
 */
public record ContractBoardPacket(CompoundTag tag) implements ModNetwork.RfPacket {

    public static final ResourceLocation TYPE = RiverFishing.id("contract_board");

    public static ContractBoardPacket decode(FriendlyByteBuf buf) {
        return new ContractBoardPacket(buf.readNbt());
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeNbt(tag);
    }

    @Override
    public ResourceLocation type() {
        return TYPE;
    }

    public void handleClient() {
        EnvExecutor.runInEnv(EnvType.CLIENT, () -> () -> com.riverfishing.client.ContractBoardState.accept(tag));
    }
}
