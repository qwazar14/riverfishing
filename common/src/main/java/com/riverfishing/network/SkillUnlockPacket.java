package com.riverfishing.network;

import com.riverfishing.fishing.AnglerSkills;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client → server: spend one skill point on a perk (§skills). Re-validated server-side by
 * {@link AnglerSkills#tryUnlock} (a free point + below max rank), so the click can never cheat.
 */
public class SkillUnlockPacket {
    private final String perkId;

    public SkillUnlockPacket(String perkId) {
        this.perkId = perkId;
    }

    public static void encode(SkillUnlockPacket p, FriendlyByteBuf buf) {
        buf.writeUtf(p.perkId, 64);
    }

    public static SkillUnlockPacket decode(FriendlyByteBuf buf) {
        return new SkillUnlockPacket(buf.readUtf(64));
    }

    public static void handle(SkillUnlockPacket p, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context c = ctx.get();
        c.enqueueWork(() -> {
            ServerPlayer sp = c.getSender();
            if (sp == null) return;
            AnglerSkills.Perk perk = AnglerSkills.Perk.byId(p.perkId);
            if (AnglerSkills.tryUnlock(sp, perk)) {
                sp.displayClientMessage(Component.translatable("skill.riverfishing." + perk.id)
                        .append(" ").append(Component.translatable("skill.riverfishing.learned"))
                        .withStyle(ChatFormatting.GREEN), true);
                sp.serverLevel().playSound(null, sp.blockPosition(), SoundEvents.PLAYER_LEVELUP,
                        SoundSource.PLAYERS, 0.5f, 1.6f);
                // Push the refreshed record so the open journal shows the new rank / spent point.
                ModNetwork.toPlayer(sp, new JournalOpenPacket(
                        com.riverfishing.item.JournalItem.exportFor(sp)));
            }
        });
        c.setPacketHandled(true);
    }
}
