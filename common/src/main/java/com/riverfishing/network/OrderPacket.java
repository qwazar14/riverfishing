package com.riverfishing.network;

import com.riverfishing.RiverFishing;
import dev.architectury.utils.EnvExecutor;
import net.fabricmc.api.EnvType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/**
 * §order-panel: today's order, told to the one player who just opened a fisherman's counter.
 *
 * <p>The client cannot work any of this out. Fish profiles load as SERVER_DATA so a multiplayer client
 * has none; the order rotation is read out of the trade registry; and the merchant on the client is a
 * {@code ClientSideMerchant}, so the villager standing there is not even reachable to ask what it is.
 *
 * <p>It carries the PAY as well as the species, taken off the live offer after the re-price rather than
 * recomputed — the sign and the trade row under it must not be able to disagree. {@code base} is what
 * the species is worth before the market moves it, so the panel can print the real multiplier instead
 * of a hardcoded ×2.5 that would quietly go stale the day the constant changes.
 *
 * <p>It is sent for EVERY fisherman, including one too junior to fill the order — {@code pay} is then
 * zero and {@code tier} names the rank that does buy the species. A stall that cannot take today's fish
 * is not a reason to say nothing; it is the reason the player most needs a sign.
 */
public record OrderPacket(ResourceLocation species, int pay, int base, int tier) implements ModNetwork.RfPacket {

    public static final ResourceLocation TYPE = RiverFishing.id("order");

    public static OrderPacket decode(FriendlyByteBuf buf) {
        return new OrderPacket(buf.readResourceLocation(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeResourceLocation(species);
        buf.writeVarInt(pay);
        buf.writeVarInt(base);
        buf.writeVarInt(tier);
    }

    @Override
    public ResourceLocation type() {
        return TYPE;
    }

    public void handleClient() {
        EnvExecutor.runInEnv(EnvType.CLIENT, () -> () -> com.riverfishing.client.OrderState.accept(this));
    }
}
