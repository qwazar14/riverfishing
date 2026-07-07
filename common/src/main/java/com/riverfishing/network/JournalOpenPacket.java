package com.riverfishing.network;

import com.riverfishing.client.JournalScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server → client: the player's journal records, so the client can open the bestiary screen (§15). */
public class JournalOpenPacket {
    private final CompoundTag data;

    public JournalOpenPacket(CompoundTag data) {
        this.data = data;
    }

    public static void encode(JournalOpenPacket p, FriendlyByteBuf buf) {
        buf.writeNbt(p.data);
    }

    public static JournalOpenPacket decode(FriendlyByteBuf buf) {
        return new JournalOpenPacket(buf.readNbt());
    }

    public static void handle(JournalOpenPacket p, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context c = ctx.get();
        c.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> JournalScreen.open(p.data == null ? new CompoundTag() : p.data)));
        c.setPacketHandled(true);
    }
}
