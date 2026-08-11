package com.riverfishing.network;

import com.riverfishing.RiverFishing;
import com.riverfishing.fishing.StockedData;
import com.riverfishing.item.ElectroRodItem;
import dev.architectury.networking.NetworkManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * Client → server: take one species out of one water, or put one in (§cull, §stock-tool).
 *
 * <p>Re-validated from scratch: creative mode, an electrofisher actually in hand, a real species id, and
 * the water within reach. The confirmation the player clicked through lives on the client and is worth
 * exactly nothing here — including the greying-out of species the water cannot hold, which is re-asked
 * against {@code habitatContext} below.
 */
public class CullPacket implements ModNetwork.RfPacket {
    public static final CustomPacketPayload.Type<CullPacket> TYPE =
            new CustomPacketPayload.Type<>(RiverFishing.id("cull"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CullPacket> STREAM_CODEC =
            StreamCodec.of((buf, pkt) -> pkt.write(buf), CullPacket::decode);

    private final BlockPos water;
    private final ResourceLocation species;
    private final boolean remove;

    public CullPacket(BlockPos water, ResourceLocation species, boolean remove) {
        this.water = water;
        this.species = species;
        this.remove = remove;
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
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
        com.riverfishing.fish.FishProfile profile =
                com.riverfishing.fish.FishProfileManager.get().byId(species);
        if (profile == null) return;
        if (!(sp.level() instanceof ServerLevel level)) return;

        Component name = Component.translatable("item.riverfishing." + species.getPath());
        StockedData data = StockedData.get(level);
        long region = StockedData.region(water);
        if (remove) {
            data.setCulled(region, species.getPath(), true);
        } else {
            // §stock-tool: putting a fish in has to answer to the same habitat gates a release does, or
            // the tool would hand out a marlin in a pond that then silently never bites.
            var body = com.riverfishing.water.WaterBodyCache.forLevel(level).get(level, water);
            if (com.riverfishing.engine.BiteEngine.environmentScore(
                    profile, com.riverfishing.fishing.FishingManager.habitatContext(level, water, body)) <= 0) {
                sp.displayClientMessage(Component.translatable("message.riverfishing.cull_unfit", name)
                        .withStyle(ChatFormatting.RED), false);
                return;
            }
            // One call does both halves: it settles the species here AND lifts any cull on it, so the
            // restore and the introduction are literally the same act — which is what they are.
            data.markStocked(region, species.getPath());
        }

        // The whole ~128-block region is one water community, so say so — an operator who thinks they
        // cleared one pond and actually cleared a river system should find that out now, not later.
        sp.displayClientMessage(Component.translatable(
                remove ? "message.riverfishing.cull_done" : "message.riverfishing.cull_added", name)
                .withStyle(remove ? ChatFormatting.RED : ChatFormatting.GREEN), false);
        level.playSound(null, water, SoundEvents.TRIDENT_THUNDER.value(), SoundSource.PLAYERS, 0.4f,
                remove ? 1.5f : 0.9f);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK,
                water.getX() + 0.5, water.getY() + 1.0, water.getZ() + 0.5, 40, 1.5, 0.3, 1.5, 0.1);
    }
}
