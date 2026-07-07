package com.riverfishing.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server → client: a tackle warning (reel/line incompatibility, §tackle-compat) to show INSIDE the rod
 * assembly window rather than as external chat/actionbar text. The client stashes it on the open
 * {@link com.riverfishing.client.RodAssemblyScreen} for a few seconds.
 */
public class RodWarningPacket {
    private final Component message;

    public RodWarningPacket(Component message) {
        this.message = message;
    }

    public static void encode(RodWarningPacket p, FriendlyByteBuf buf) {
        buf.writeComponent(p.message);
    }

    public static RodWarningPacket decode(FriendlyByteBuf buf) {
        return new RodWarningPacket(buf.readComponent());
    }

    public static void handle(RodWarningPacket p, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context c = ctx.get();
        c.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.riverfishing.client.RodAssemblyScreen.showWarning(p.message)));
        c.setPacketHandled(true);
    }
}
