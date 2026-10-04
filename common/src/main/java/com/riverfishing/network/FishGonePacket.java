package com.riverfishing.network;

import com.riverfishing.RiverFishing;
import dev.architectury.utils.EnvExecutor;
import net.fabricmc.api.EnvType;
import net.minecraft.network.FriendlyByteBuf;

/**
 * §fight-moves (1.1.0) server → clients: a fish that was on the line is gone — {@code landed} lifted out to the
 * angler, or else away and down. Sent just before the line is cleared, so the client still knows where it drew
 * the fish; the position here is the server's, for a client that never drew it (a pole's pull-out).
 */
public class FishGonePacket implements ModNetwork.RfPacket {
    public static final net.minecraft.resources.ResourceLocation TYPE = RiverFishing.id("fish_gone");

    public final int playerId;
    public final boolean landed;
    public final String species;
    public final int lengthCm;
    public final double x, y, z;
    /** §rod-anim: the line itself snapped (a piece is left on the rod), rather than the hook pulling free. */
    public final boolean broke;

    public FishGonePacket(int playerId, boolean landed, String species, int lengthCm, double x, double y, double z) {
        this(playerId, landed, species, lengthCm, x, y, z, false);
    }

    public FishGonePacket(int playerId, boolean landed, String species, int lengthCm, double x, double y, double z, boolean broke) {
        this.broke = broke;
        this.playerId = playerId;
        this.landed = landed;
        this.species = species;
        this.lengthCm = lengthCm;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @Override
    public net.minecraft.resources.ResourceLocation type() {
        return TYPE;
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(playerId);
        buf.writeBoolean(landed);
        buf.writeUtf(species);
        buf.writeVarInt(lengthCm);
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);
        buf.writeBoolean(broke);
    }

    public static FishGonePacket decode(FriendlyByteBuf buf) {
        return new FishGonePacket(buf.readVarInt(), buf.readBoolean(), buf.readUtf(), buf.readVarInt(),
                buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readBoolean());
    }

    public void handleClient() {
        EnvExecutor.runInEnv(EnvType.CLIENT, () -> () -> com.riverfishing.client.FishExitRenderer.accept(this));
    }
}
