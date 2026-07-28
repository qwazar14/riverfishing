package com.riverfishing.network;

import com.riverfishing.RiverFishing;
import com.riverfishing.fishing.StockedData;
import com.riverfishing.item.ElectroRodItem;
import dev.architectury.networking.NetworkManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * Client → server: remove (or put back) one species in one water (§cull).
 *
 * <p>Re-validated from scratch: creative mode, an electrofisher actually in hand, a real species id, and
 * the water within reach. The confirmation the player clicked through lives on the client and is worth
 * exactly nothing here.
 */
public class CullPacket implements ModNetwork.RfPacket {
    public static final ResourceLocation TYPE = RiverFishing.id("cull");

    private final BlockPos water;
    private final ResourceLocation species;
    private final boolean remove;

    public CullPacket(BlockPos water, ResourceLocation species, boolean remove) {
        this.water = water;
        this.species = species;
        this.remove = remove;
    }

    @Override
    public ResourceLocation type() {
        return TYPE;
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeBlockPos(water);
        buf.writeResourceLocation(species);
        buf.writeBoolean(remove);
    }

    public static CullPacket decode(FriendlyByteBuf buf) {
        return new CullPacket(buf.readBlockPos(), buf.readResourceLocation(), buf.readBoolean());
    }

    public void handleServer(NetworkManager.PacketContext ctx) {
        if (!(ctx.getPlayer() instanceof ServerPlayer sp)) return;
        if (!sp.isCreative()) return;
        if (!(sp.getMainHandItem().getItem() instanceof ElectroRodItem)
                && !(sp.getOffhandItem().getItem() instanceof ElectroRodItem)) {
            return;
        }
        if (sp.distanceToSqr(water.getCenter()) > 64.0 * 64.0) return;
        if (com.riverfishing.fish.FishProfileManager.get().byId(species) == null) return;
        if (!(sp.level() instanceof ServerLevel level)) return;

        StockedData data = StockedData.get(level);
        data.setCulled(StockedData.region(water), species.getPath(), remove);

        // The whole ~128-block region is one water community, so say so — an operator who thinks they
        // cleared one pond and actually cleared a river system should find that out now, not later.
        Component name = Component.translatable("item.riverfishing." + species.getPath());
        sp.displayClientMessage(Component.translatable(
                remove ? "message.riverfishing.cull_done" : "message.riverfishing.cull_undone", name)
                .withStyle(remove ? ChatFormatting.RED : ChatFormatting.GREEN), false);
        level.playSound(null, water, SoundEvents.TRIDENT_THUNDER, SoundSource.PLAYERS, 0.4f,
                remove ? 1.5f : 0.9f);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK,
                water.getX() + 0.5, water.getY() + 1.0, water.getZ() + 0.5, 40, 1.5, 0.3, 1.5, 0.1);
    }
}
