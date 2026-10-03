package com.riverfishing.network;

import dev.architectury.utils.EnvExecutor;
import net.fabricmc.api.EnvType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

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
    public record Entry(ResourceLocation species, int weightG, int lengthCm, byte age,
                        byte depth, byte lane, byte phase, int kind, int group, String variety, int pattern) {
        /**
         * §shoal-life: bit 0 a predator, bit 1 a jumper, bit 2 a species that moves in numbers.
         * §shoal-live: bit 3 feeding on the bottom (bubbles), bit 4 feeding at the surface (rings), bit 5 a
         * predator hunting (strikes), bit 6 a trophy, bit 7 a batch of fry.
         */
        public static final int PREDATOR = 1, JUMPER = 2, SHOALING = 4,
                FEEDING = 8, RISING = 16, HUNTING = 32, TROPHY = 64, FRY = 128,
                /** §fish-world: how it lives — on the bottom, lying in wait, in an open-water school, patrolling. */
                BOTTOM = 256, AMBUSH = 512, SCHOOL = 1024, CHASER = 16384,
                /** §fish-world: resting (out of its hours, or full), spawning, feeding over the angler's groundbait. */
                REST = 2048, SPAWNING = 4096, BAITED = 8192;

        /** The old shape — a fish with no group, variety or pattern (the scenery path). */
        public Entry(ResourceLocation species, int weightG, int lengthCm, byte age, byte depth, byte lane, byte phase, byte kind) {
            this(species, weightG, lengthCm, age, depth, lane, phase, kind, 0, "", 0);
        }

        public boolean predator() { return (kind & PREDATOR) != 0; }
        public boolean jumper() { return (kind & JUMPER) != 0; }
        public boolean shoaling() { return (kind & SHOALING) != 0; }
        public boolean feeding() { return (kind & FEEDING) != 0; }
        public boolean rising() { return (kind & RISING) != 0; }
        public boolean hunting() { return (kind & HUNTING) != 0; }
        public boolean trophy() { return (kind & TROPHY) != 0; }
        public boolean fry() { return (kind & FRY) != 0; }
        public boolean is(int flag) { return (kind & flag) != 0; }
    }

    /**
     * One shoal, anchored to a water surface block. {@code clarity} is how well this water shows what it
     * holds, {@code spread} how far its circuits may reach before they would leave the water.
     */
    public record Spot(BlockPos centre, float clarity, byte spread, byte spook, List<Entry> fish,
                       boolean hasBait, float baitX, float baitZ) {
        public Spot(BlockPos centre, float clarity, byte spread, byte spook, List<Entry> fish) {
            this(centre, clarity, spread, spook, fish, false, 0f, 0f);
        }

        /** §shoal-spook: 0..100, how disturbed this water is right now (see SpookTracker). */
        public float spookFraction() {
            return Math.max(0f, Math.min(1f, spook / 100f));
        }
    }

    public static final CustomPacketPayload.Type<ShoalPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("riverfishing", "shoal"));

    public static final StreamCodec<FriendlyByteBuf, ShoalPacket> STREAM_CODEC =
            StreamCodec.of(ShoalPacket::encode, ShoalPacket::read);

    /** No fish here — the client clears its shoals. Sent once when you walk away from water. */
    public static ShoalPacket empty() {
        return new ShoalPacket(List.of());
    }

    private static void encode(FriendlyByteBuf buf, ShoalPacket p) {
        // A species palette, not a species per fish: a ResourceLocation costs ~25 bytes on the wire and
        // the same roach id appears in a dozen entries. Fourteen shoals of bream fit in a few hundred.
        List<ResourceLocation> palette = new ArrayList<>();
        Map<ResourceLocation, Integer> index = new HashMap<>();
        for (Spot s : p.spots) {
            for (Entry e : s.fish()) {
                index.computeIfAbsent(e.species(), id -> {
                    palette.add(id);
                    return palette.size() - 1;
                });
            }
        }
        buf.writeVarInt(palette.size());
        for (ResourceLocation id : palette) buf.writeResourceLocation(id);
        // §shoal-live: varieties the same way — "koi_kohaku" once, not once per koi
        List<String> varieties = new ArrayList<>();
        Map<String, Integer> vIndex = new HashMap<>();
        for (Spot s : p.spots) for (Entry e : s.fish()) {
            if (!e.variety().isEmpty()) vIndex.computeIfAbsent(e.variety(), v -> { varieties.add(v); return varieties.size() - 1; });
        }
        buf.writeVarInt(varieties.size());
        for (String v : varieties) buf.writeUtf(v);

        buf.writeVarInt(p.spots.size());
        for (Spot s : p.spots) {
            buf.writeBlockPos(s.centre());
            buf.writeFloat(s.clarity());
            buf.writeByte(s.spread());
            buf.writeByte(s.spook());
            buf.writeBoolean(s.hasBait());
            if (s.hasBait()) { buf.writeFloat(s.baitX()); buf.writeFloat(s.baitZ()); }
            buf.writeVarInt(s.fish().size());
            for (Entry e : s.fish()) {
                buf.writeVarInt(index.get(e.species()));
                buf.writeVarInt(e.weightG());
                buf.writeVarInt(e.lengthCm());
                buf.writeByte(e.age());
                buf.writeByte(e.depth());
                buf.writeByte(e.lane());
                buf.writeByte(e.phase());
                buf.writeVarInt(e.kind());
                buf.writeInt(e.group());
                buf.writeVarInt(e.variety().isEmpty() ? 0 : vIndex.get(e.variety()) + 1);
                buf.writeVarInt(e.pattern());
            }
        }
    }

    private static ShoalPacket read(FriendlyByteBuf buf) {
        int np = buf.readVarInt();
        List<ResourceLocation> palette = new ArrayList<>(np);
        for (int i = 0; i < np; i++) palette.add(buf.readResourceLocation());
        int nv = buf.readVarInt();
        List<String> varieties = new ArrayList<>(nv);
        for (int i = 0; i < nv; i++) varieties.add(buf.readUtf());

        int ns = buf.readVarInt();
        List<Spot> spots = new ArrayList<>(ns);
        for (int s = 0; s < ns; s++) {
            BlockPos centre = buf.readBlockPos();
            float clarity = buf.readFloat();
            byte spread = buf.readByte();
            byte spook = buf.readByte();
            boolean hasBait = buf.readBoolean();
            float bx = hasBait ? buf.readFloat() : 0f, bz = hasBait ? buf.readFloat() : 0f;
            int nf = buf.readVarInt();
            List<Entry> fish = new ArrayList<>(nf);
            for (int i = 0; i < nf; i++) {
                int pi = buf.readVarInt();
                ResourceLocation id = pi >= 0 && pi < palette.size() ? palette.get(pi) : null;
                int w = buf.readVarInt(), len = buf.readVarInt();
                byte age = buf.readByte(), depth = buf.readByte(), lane = buf.readByte(), phase = buf.readByte();
                int kind = buf.readVarInt(), group = buf.readInt(), vi = buf.readVarInt(), pattern = buf.readVarInt();
                String variety = vi > 0 && vi <= varieties.size() ? varieties.get(vi - 1) : "";
                Entry e = new Entry(id, w, len, age, depth, lane, phase, kind, group, variety, pattern);
                if (id != null) fish.add(e);
            }
            spots.add(new Spot(centre, clarity, spread, spook, fish, hasBait, bx, bz));
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
