package com.riverfishing.network;

import com.riverfishing.RiverFishing;
import dev.architectury.networking.NetworkManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * Client → server: a left click while the winter rod jigs (§ice-rhythm) — an accent, if it landed on a
 * stop. Empty on purpose: the server judges it against its own needle when it arrives, and a click
 * outside a live jig is simply ignored, so there is nothing here to trust.
 */
public class JigBeatPacket implements ModNetwork.RfPacket {
    public static final ResourceLocation TYPE = RiverFishing.id("jig_beat");

    public JigBeatPacket() {}

    @Override
    public ResourceLocation type() {
        return TYPE;
    }

    @Override
    public void write(FriendlyByteBuf buf) {}

    public static JigBeatPacket decode(FriendlyByteBuf buf) {
        return new JigBeatPacket();
    }

    public void handleServer(NetworkManager.PacketContext ctx) {
        if (ctx.getPlayer() instanceof ServerPlayer sp) {
            com.riverfishing.fishing.FishingManager.jigBeat(sp);   // §jig-2: the accent
        }
    }
}
