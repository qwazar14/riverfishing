package com.riverfishing.network;

import dev.architectury.networking.NetworkManager;
import dev.architectury.utils.Env;
import dev.architectury.utils.EnvExecutor;
import net.fabricmc.api.EnvType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * §shoal (0.7.0): what is actually swimming in the water in front of you.
 *
 * <p>The client cannot work this out for itself. Fish profiles load under {@code PackType.SERVER_DATA},
 * so a multiplayer client has none of them, and the two things that make the answer honest — per-chunk
 * fishing pressure and pond stocking — live in server {@code SavedData}. So the server decides and sends
 * a short list, and what you SEE is what the water actually holds.
 *
 * <p>Deliberately small: a handful of entries, no per-tick updates. The client animates them itself, the
 * same way the aquarium does, so this is a few hundred bytes every couple of seconds, not a stream.
 */
public record ShoalPacket(BlockPos centre, float clarity, List<Entry> fish) implements ModNetwork.RfPacket {

    /**
     * One visible fish. {@code weightG} drives the rendered size, {@code depth} how far under the surface
     * it swims, {@code lane} spreads the shoal out so they do not overlap, and {@code phase} keeps each
     * one on its own point of the swim path across packets.
     */
    public record Entry(ResourceLocation species, int weightG, byte depth, byte lane, byte phase) {}

    public static final CustomPacketPayload.Type<ShoalPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("riverfishing", "shoal"));

    public static final StreamCodec<FriendlyByteBuf, ShoalPacket> STREAM_CODEC =
            StreamCodec.of(ShoalPacket::encode, ShoalPacket::read);

    /** No fish here — the client clears its shoal. Sent once when you walk away from water. */
    public static ShoalPacket empty() {
        return new ShoalPacket(BlockPos.ZERO, 0f, List.of());
    }

    private static void encode(FriendlyByteBuf buf, ShoalPacket p) {
        buf.writeBlockPos(p.centre);
        buf.writeFloat(p.clarity);
        buf.writeVarInt(p.fish.size());
        for (Entry e : p.fish) {
            buf.writeResourceLocation(e.species());
            buf.writeVarInt(e.weightG());
            buf.writeByte(e.depth());
            buf.writeByte(e.lane());
            buf.writeByte(e.phase());
        }
    }

    private static ShoalPacket read(FriendlyByteBuf buf) {
        BlockPos centre = buf.readBlockPos();
        float clarity = buf.readFloat();
        int n = buf.readVarInt();
        List<Entry> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            out.add(new Entry(buf.readResourceLocation(), buf.readVarInt(),
                    buf.readByte(), buf.readByte(), buf.readByte()));
        }
        return new ShoalPacket(centre, clarity, out);
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        encode(buf, this);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handleClient() {
        EnvExecutor.runInEnv(EnvType.CLIENT,
                () -> () -> com.riverfishing.client.ShoalState.accept(this));
    }
}
