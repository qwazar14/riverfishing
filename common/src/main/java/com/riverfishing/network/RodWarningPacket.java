package com.riverfishing.network;

import com.riverfishing.RiverFishing;
import dev.architectury.utils.EnvExecutor;
import net.fabricmc.api.EnvType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * Server → client: a tackle warning (reel/line incompatibility, §tackle-compat) shown INSIDE the rod
 * assembly window rather than as external chat/actionbar text.
 */
public class RodWarningPacket implements ModNetwork.RfPacket {
    public static final ResourceLocation TYPE = RiverFishing.id("rod_warning");

    private final Component message;

    public RodWarningPacket(Component message) {
        this.message = message;
    }

    @Override
    public ResourceLocation type() {
        return TYPE;
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeComponent(message);
    }

    public static RodWarningPacket decode(FriendlyByteBuf buf) {
        return new RodWarningPacket(buf.readComponent());
    }

    public void handleClient() {
        EnvExecutor.runInEnv(EnvType.CLIENT,
                () -> () -> com.riverfishing.client.RodAssemblyScreen.showWarning(message));
    }
}
