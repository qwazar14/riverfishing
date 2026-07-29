package com.riverfishing.network;

import dev.architectury.utils.EnvExecutor;
import net.fabricmc.api.EnvType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * §shoal (0.7.0): what is actually swimming in the water in front of you.
 *
 * <p>The client cannot work this out for itself. Fish profiles load under {@code PackType.SERVER_DATA},
 * so a multiplayer client has none of them, and the two things that make the answer honest — per-chunk
 * fishing pressure and pond stocking — live in server {@code SavedData}. So the server decides and sends
 * the shoals, and what you SEE is what the water actually holds.
 *
 * <p>One packet covers every shoal across the 3×3 chunks around the player, so a whole stretch of water
 * is populated rather than one ring at your feet. The client animates them itself, the same way the
 * aquarium does, so this is sent every couple of seconds and only when it has actually changed.
 */
public record ShoalPacket(List<Spot> spots) implements ModNetwork.RfPacket {

    /**
     * One visible fish. {@code lengthCm} is what drives the rendered SIZE — FishItem.getIconScale reads
     * length, not weight, and returns a flat 1.0 when there is none, which is why the first cut of this
     * feature drew every fish the same size. {@code age} is 0..100, how grown the fish is, and it paints
     * it (§morph): the small ones in the water are pale, the big ones dark. {@code depth} is blocks under
     * the surface, {@code lane} groups a shoal onto one circuit, {@code phase} places each fish on it.
     */
    public record Entry(Identifier species, int weightG, int lengthCm, byte age,
                        byte depth, byte lane, byte phase) {}

    /**
     * One shoal, anchored to a water surface block. {@code clarity} is how well this water shows what it
     * holds, {@code spread} how far its circuits may reach before they would leave the water.
     */
    public record Spot(BlockPos centre, float clarity, byte spread, List<Entry> fish) {}

    public static final CustomPacketPayload.Type<ShoalPacket> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("riverfishing", "shoal"));

    public static final StreamCodec<FriendlyByteBuf, ShoalPacket> STREAM_CODEC =
            StreamCodec.of(ShoalPacket::encode, ShoalPacket::read);

    /** No fish here — the client clears its shoals. Sent once when you walk away from water. */
    public static ShoalPacket empty() {
        return new ShoalPacket(List.of());
    }

    private static void encode(FriendlyByteBuf buf, ShoalPacket p) {
        // A species palette, not a species per fish: a Identifier costs ~25 bytes on the wire and
        // the same roach id appears in a dozen entries. Fourteen shoals of bream fit in a few hundred.
        List<Identifier> palette = new ArrayList<>();
        Map<Identifier, Integer> index = new HashMap<>();
        for (Spot s : p.spots) {
            for (Entry e : s.fish()) {
                index.computeIfAbsent(e.species(), id -> {
                    palette.add(id);
                    return palette.size() - 1;
                });
            }
        }
        buf.writeVarInt(palette.size());
        for (Identifier id : palette) buf.writeIdentifier(id);

        buf.writeVarInt(p.spots.size());
        for (Spot s : p.spots) {
            buf.writeBlockPos(s.centre());
            buf.writeFloat(s.clarity());
            buf.writeByte(s.spread());
            buf.writeVarInt(s.fish().size());
            for (Entry e : s.fish()) {
                buf.writeVarInt(index.get(e.species()));
                buf.writeVarInt(e.weightG());
                buf.writeVarInt(e.lengthCm());
                buf.writeByte(e.age());
                buf.writeByte(e.depth());
                buf.writeByte(e.lane());
                buf.writeByte(e.phase());
            }
        }
    }

    private static ShoalPacket read(FriendlyByteBuf buf) {
        int np = buf.readVarInt();
        List<Identifier> palette = new ArrayList<>(np);
        for (int i = 0; i < np; i++) palette.add(buf.readIdentifier());

        int ns = buf.readVarInt();
        List<Spot> spots = new ArrayList<>(ns);
        for (int s = 0; s < ns; s++) {
            BlockPos centre = buf.readBlockPos();
            float clarity = buf.readFloat();
            byte spread = buf.readByte();
            int nf = buf.readVarInt();
            List<Entry> fish = new ArrayList<>(nf);
            for (int i = 0; i < nf; i++) {
                int pi = buf.readVarInt();
                Identifier id = pi >= 0 && pi < palette.size() ? palette.get(pi) : null;
                Entry e = new Entry(id, buf.readVarInt(), buf.readVarInt(), buf.readByte(),
                        buf.readByte(), buf.readByte(), buf.readByte());
                if (id != null) fish.add(e);
            }
            spots.add(new Spot(centre, clarity, spread, fish));
        }
        return new ShoalPacket(spots);
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
