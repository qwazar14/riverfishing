package com.riverfishing.item;

import com.riverfishing.fishing.JournalData;
import com.riverfishing.network.JournalOpenPacket;
import com.riverfishing.network.ModNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/** Fishing journal (§15): right-click to open the bestiary screen of your records. */
public class JournalItem extends Item {
    public JournalItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide() && player instanceof ServerPlayer sp) {
            ModNetwork.toPlayer(sp, new JournalOpenPacket(exportFor(sp)));
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * The journal payload sent to the client: a COPY of the records plus the claimed-quest set (§quest-
     * claim) so the screen knows which rewards are still waiting. The skill ranks / points ride along in
     * the record's own {@code skills} tag. The extra keys are never written back to the live NBT.
     */
    public static net.minecraft.nbt.CompoundTag exportFor(ServerPlayer sp) {
        var out = JournalData.get(sp).copy();
        var claimed = new net.minecraft.nbt.CompoundTag();
        for (com.riverfishing.quest.Quests.Quest q : com.riverfishing.quest.Quests.ALL) {
            claimed.putBoolean(q.id(), com.riverfishing.quest.QuestData.isRewarded(sp, q.id()));
        }
        out.put("rf_claimed", claimed);
        return out;
    }

    @Override
    public void appendHoverText(ItemStack stack, net.minecraft.world.item.Item.TooltipContext context, net.minecraft.world.item.component.TooltipDisplay display, java.util.function.Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable("tooltip.riverfishing.journal_use").withStyle(ChatFormatting.DARK_GRAY));
    }
}
