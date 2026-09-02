package com.riverfishing.network;

import com.riverfishing.RiverFishing;
import dev.architectury.networking.NetworkManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * §contracts, client → server: the player clicked post {@code slot} on villager {@code vid}'s board. The
 * server rebuilds that board and decides; nothing in the click is believed.
 */
public record ContractTakePacket(int vid, int slot) implements ModNetwork.RfPacket {

    public static final ResourceLocation TYPE = RiverFishing.id("contract_take");

    public static ContractTakePacket decode(FriendlyByteBuf buf) {
        return new ContractTakePacket(buf.readVarInt(), buf.readVarInt());
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(vid);
        buf.writeVarInt(slot);
    }

    @Override
    public ResourceLocation type() {
        return TYPE;
    }

    public void handleServer(NetworkManager.PacketContext ctx) {
        if (ctx.getPlayer() instanceof ServerPlayer sp) com.riverfishing.fishing.Contracts.take(sp, vid, slot);
    }
}
