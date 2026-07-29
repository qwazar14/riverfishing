package com.riverfishing.network;

import com.riverfishing.RiverFishing;
import com.riverfishing.fishing.StockedData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.List;

/**
 * Server → client: what lives in the water the operator just pointed the electrofisher at (§cull).
 *
 * <p>The list is built server-side from the same function the fish finder uses, so the tool can never
 * offer to remove something that was not there or hide something that was.
 */
public class CullListPacket implements ModNetwork.RfPacket {
    public static final CustomPacketPayload.Type<CullListPacket> TYPE =
            new CustomPacketPayload.Type<>(RiverFishing.id("cull_list"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CullListPacket> STREAM_CODEC =
            StreamCodec.of((buf, pkt) -> pkt.write(buf), CullListPacket::decode);

    public final BlockPos water;
    public final List<Identifier> species;
    /** Parallel to {@link #species}: already removed from this water. */
    public final List<Boolean> culled;

    public CullListPacket(BlockPos water, List<Identifier> species, List<Boolean> culled) {
        this.water = water;
        this.species = species;
        this.culled = culled;
    }

    /** Builds the packet with each species' current cull state read off the region's book. */
    public static CullListPacket of(ServerLevel level, BlockPos water, List<Identifier> species) {
        StockedData data = StockedData.get(level);
        long region = StockedData.region(water);
        List<Boolean> flags = new ArrayList<>(species.size());
        for (Identifier id : species) flags.add(data.isCulled(region, id.getPath()));
        return new CullListPacket(water, species, flags);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeBlockPos(water);
        buf.writeVarInt(species.size());
        for (int i = 0; i < species.size(); i++) {
            buf.writeIdentifier(species.get(i));
            buf.writeBoolean(culled.get(i));
        }
    }

    public static CullListPacket decode(FriendlyByteBuf buf) {
        BlockPos water = buf.readBlockPos();
        int n = buf.readVarInt();
        List<Identifier> species = new ArrayList<>(n);
        List<Boolean> culled = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            species.add(buf.readIdentifier());
            culled.add(buf.readBoolean());
        }
        return new CullListPacket(water, species, culled);
    }

    public void handleClient() {
        com.riverfishing.client.CullScreen.open(this);
    }
}
