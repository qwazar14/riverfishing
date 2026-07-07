package com.riverfishing.item;

import com.riverfishing.component.ComponentSlot;
import com.riverfishing.component.RigType;
import com.riverfishing.menu.RigMenu;
import com.riverfishing.rig.RigData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.NonNullList;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Terminal rig / оснастка (§3.4). The rig carries its own inventory (hooks, bait, groundbait, …) in
 * NBT (Module 4); Shift + right-click opens that inventory.
 */
public class RigItem extends Item implements RodComponentItem {
    private final RigType type;

    public RigItem(RigType type, Properties properties) {
        super(properties.stacksTo(1));
        this.type = type;
    }

    public RigType rigType() { return type; }

    @Override
    public ComponentSlot componentSlot() {
        return ComponentSlot.RIG;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isSecondaryUseActive()) {
            if (!level.isClientSide && player instanceof ServerPlayer sp) {
                openRig(sp, hand);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        return InteractionResultHolder.pass(stack);
    }

    private void openRig(ServerPlayer player, InteractionHand hand) {
        MenuProvider provider = new MenuProvider() {
            @Override
            public Component getDisplayName() {
                return player.getItemInHand(hand).getHoverName();
            }

            @Nullable
            @Override
            public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) {
                return new RigMenu(id, inv, hand);
            }
        };
        NetworkHooks.openScreen(player, provider, (FriendlyByteBuf buf) -> buf.writeEnum(hand));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.riverfishing.rig_mass",
                String.format("%.0f", type.massGrams())).withStyle(s -> s.withColor(0xA0A0A0)));

        // What's loaded inside the rig (#2).
        NonNullList<ItemStack> contents = RigData.load(stack);
        boolean any = false;
        for (ItemStack content : contents) {
            if (content.isEmpty()) continue;
            if (!any) {
                tooltip.add(Component.translatable("tooltip.riverfishing.rig_contents").withStyle(ChatFormatting.GRAY));
                any = true;
            }
            MutableComponent line = Component.literal(" • ").append(content.getHoverName());
            if (content.getCount() > 1) {
                line.append(Component.literal(" ×" + content.getCount()));
            }
            tooltip.add(line.withStyle(ChatFormatting.DARK_GRAY));
        }
        if (!any) {
            tooltip.add(Component.translatable("tooltip.riverfishing.rig_empty").withStyle(ChatFormatting.DARK_GRAY));
        }
        tooltip.add(Component.translatable("tooltip.riverfishing.rig_open").withStyle(ChatFormatting.DARK_GRAY));
    }
}
