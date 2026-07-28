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
public record ShoalPacket(BlockPos centre, float clarity, byte spread, List<Entry> fish)
        implements ModNetwork.RfPacket {

    /**
     * One visible fish. {@code lengthCm} is what drives the rendered SIZE — FishItem.getIconScale reads
     * length, not weight, and returns a flat 1.0 when there is none, which is why the first cut of this
     * feature drew every fish the same size. {@code depth} is blocks under the surface, {@code lane}
     * groups a shoal onto one circuit, {@code phase} places each fish on it.
     */
    public record Entry(ResourceLocation species, int weightG, int lengthCm,
                        byte depth, byte lane, byte phase) {}

    public static final CustomPacketPayload.Type<ShoalPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("riverfishing", "shoal"));

    public static final StreamCodec<FriendlyByteBuf, ShoalPacket> STREAM_CODEC =
            StreamCodec.of(ShoalPacket::encode, ShoalPacket::read);

    /** No fish here — the client clears its shoal. Sent once when you walk away from water. */
    public static ShoalPacket empty() {
        return new ShoalPacket(BlockPos.ZERO, 0f, (byte) 0, List.of());
    }

    private static void encode(FriendlyByteBuf buf, ShoalPacket p) {
        buf.writeBlockPos(p.centre);
        buf.writeFloat(p.clarity);
        buf.writeByte(p.spread);
        buf.writeVarInt(p.fish.size());
        for (Entry e : p.fish) {
            buf.writeResourceLocation(e.species());
            buf.writeVarInt(e.weightG());
            buf.writeVarInt(e.lengthCm());
            buf.writeByte(e.depth());
            buf.writeByte(e.lane());
            buf.writeByte(e.phase());
        }
    }

    private static ShoalPacket read(FriendlyByteBuf buf) {
        BlockPos centre = buf.readBlockPos();
        float clarity = buf.readFloat();
        byte spread = buf.readByte();
        int n = buf.readVarInt();
        List<Entry> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            out.add(new Entry(buf.readResourceLocation(), buf.readVarInt(), buf.readVarInt(),
                    buf.readByte(), buf.readByte(), buf.readByte()));
        }
        return new ShoalPacket(centre, clarity, spread, out);
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
