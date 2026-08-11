package com.riverfishing.network;

import com.riverfishing.RiverFishing;
import com.riverfishing.engine.BiteContext;
import com.riverfishing.engine.BiteEngine;
import com.riverfishing.fish.FishProfile;
import com.riverfishing.fish.FishProfileManager;
import com.riverfishing.fishing.FishingManager;
import com.riverfishing.fishing.StockedData;
import com.riverfishing.water.WaterBody;
import com.riverfishing.water.WaterBodyCache;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.List;

/**
 * Server → client: where every species stands in the water the operator just pointed the electrofisher
 * at (§cull, §stock-tool).
 *
 * <p>The list used to be only what lives here, because the tool could only take things out. It now
 * carries EVERY species with its standing, for two reasons: the tool adds as well as removes, and a
 * culled fish scores zero on the environment and therefore fell straight out of the old "what lives
 * here" list — which meant the undo the screen advertised could never be reached.
 *
 * <p>The states are computed from the same functions the rest of the mod asks: {@code speciesHere} for
 * presence (the one the fish finder uses) and {@code habitatContext} for whether the water can hold the
 * species at all (the one a release rolls against). Nothing here re-derives either.
 */
public class CullListPacket implements ModNetwork.RfPacket {
    /** Not in this water, but the water would suit it — the electrofisher can put it there. */
    public static final byte ABSENT = 0;
    /** Lives here: native, settled, or a transplant still holding on. */
    public static final byte HERE = 1;
    /** An operator took it out of this water. */
    public static final byte CULLED = 2;
    /** This water cannot hold the species at all — wrong body, depth, width or climate. */
    public static final byte UNFIT = 3;

    public static final ResourceLocation TYPE = RiverFishing.id("cull_list");

    public final BlockPos water;
    public final List<ResourceLocation> species;
    /** Parallel to {@link #species}: {@link #ABSENT} / {@link #HERE} / {@link #CULLED} / {@link #UNFIT}. */
    public final byte[] state;
    /**
     * Parallel to {@link #species}: the family to file it under.
     *
     * <p>Sent rather than looked up. Fish profiles are datapack files, so a client connected to a
     * dedicated server does not have them — the journal already has to guard on that. A picker that
     * silently lost its categories on multiplayer would be a bug found by someone else.
     */
    public final List<String> group;

    public CullListPacket(BlockPos water, List<ResourceLocation> species, byte[] state, List<String> group) {
        this.water = water;
        this.species = species;
        this.state = state;
        this.group = group;
    }

    /**
     * Builds the whole standings table for one water. Species that live here come first, in the order
     * the fish finder ranks them; the rest follow in registry order and are laid out by family on the
     * client, which is where the display names live.
     */
    public static CullListPacket of(ServerLevel level, BlockPos water) {
        StockedData data = StockedData.get(level);
        long region = StockedData.region(water);
        List<ResourceLocation> here = FishingManager.speciesHere(level, water);

        WaterBody body = WaterBodyCache.forLevel(level).get(level, water);
        // One context for all seventy-nine — the fit question is about the WATER, so the answer does not
        // change from species to species and building it per fish would be seventy-eight wasted scans.
        BiteContext habitat = FishingManager.habitatContext(level, water, body);

        List<ResourceLocation> ids = new ArrayList<>(here);
        for (FishProfile p : FishProfileManager.get().all()) {
            if (!ids.contains(p.id)) ids.add(p.id);
        }
        byte[] st = new byte[ids.size()];
        List<String> groups = new ArrayList<>(ids.size());
        for (int i = 0; i < ids.size(); i++) {
            ResourceLocation id = ids.get(i);
            FishProfile p = FishProfileManager.get().byId(id);
            if (data.isCulled(region, id.getPath())) st[i] = CULLED;
            else if (here.contains(id)) st[i] = HERE;
            else if (p == null || BiteEngine.environmentScore(p, habitat) <= 0) st[i] = UNFIT;
            else st[i] = ABSENT;
            groups.add(com.riverfishing.fish.FishGroup.of(p));
        }
        return new CullListPacket(water, ids, st, groups);
    }

    @Override
    public ResourceLocation type() {
        return TYPE;
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeBlockPos(water);
        buf.writeVarInt(species.size());
        for (int i = 0; i < species.size(); i++) {
            buf.writeResourceLocation(species.get(i));
            buf.writeByte(state[i]);
            buf.writeUtf(group.get(i));
        }
    }

    public static CullListPacket decode(FriendlyByteBuf buf) {
        BlockPos water = buf.readBlockPos();
        int n = buf.readVarInt();
        List<ResourceLocation> species = new ArrayList<>(n);
        byte[] state = new byte[n];
        List<String> group = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            species.add(buf.readResourceLocation());
            state[i] = buf.readByte();
            group.add(buf.readUtf());
        }
        return new CullListPacket(water, species, state, group);
    }

    public void handleClient() {
        com.riverfishing.client.CullScreen.open(this);
    }
}
