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
        // §multiloader: NetworkHooks.openScreen -> Architectury MenuRegistry.openExtendedMenu.
        dev.architectury.registry.menu.ExtendedMenuProvider provider =
                new dev.architectury.registry.menu.ExtendedMenuProvider() {
                    @Override
                    public Component getDisplayName() {
                        return player.getItemInHand(hand).getHoverName();
                    }

                    @Nullable
                    @Override
                    public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) {
                        return new RigMenu(id, inv, hand);
                    }

                    @Override
                    public void saveExtraData(FriendlyByteBuf buf) {
                        buf.writeEnum(hand);
                    }
                };
        dev.architectury.registry.menu.MenuRegistry.openExtendedMenu(player, provider);
    }

    /** §tackle-station: shared weight + tied-by tooltip lines (also used by BaitItem). */
    public static void appendTackleStationLines(ItemStack stack, List<Component> tooltip) {
        var tag = StackNbt.get(stack);
        if (tag.contains(com.riverfishing.tackle.TackleForm.TAG_WEIGHT)) {
            tooltip.add(Component.translatable("tooltip.riverfishing.tackle_weight",
                    tag.getInt(com.riverfishing.tackle.TackleForm.TAG_WEIGHT))
                    .withStyle(s -> s.withColor(0xFFD97A)));
        }
        // §tackle-adv: the fine-tuning rides the tooltip so the tackle explains itself.
        if (tag.contains(com.riverfishing.tackle.TackleForm.TAG_LEADER_CM)) {
            tooltip.add(Component.translatable("tooltip.riverfishing.hook_link",
                    tag.getInt(com.riverfishing.tackle.TackleForm.TAG_LEADER_CM))
                    .withStyle(s -> s.withColor(0xA0A0C8)));
        }
        if (tag.contains(com.riverfishing.tackle.TackleForm.TAG_BALANCE)) {
            String pos = switch (tag.getInt(com.riverfishing.tackle.TackleForm.TAG_BALANCE)) {
                case 0 -> "nose"; case 2 -> "tail"; default -> "center";
            };
            tooltip.add(Component.translatable("tooltip.riverfishing.balance",
                    Component.translatable("screen.riverfishing.tackle_station.balance_" + pos))
                    .withStyle(s -> s.withColor(0xA0A0C8)));
        }
        if (tag.contains(com.riverfishing.tackle.TackleForm.TAG_BLADE)) {
            tooltip.add(Component.translatable("tooltip.riverfishing.blade",
                    tag.getInt(com.riverfishing.tackle.TackleForm.TAG_BLADE))
                    .withStyle(s -> s.withColor(0xA0A0C8)));
        }
        if (tag.contains(com.riverfishing.tackle.TackleForm.TAG_TIED_BY)) {
            tooltip.add(Component.translatable("tooltip.riverfishing.tied_by",
                    tag.getString(com.riverfishing.tackle.TackleForm.TAG_TIED_BY))
                    .withStyle(s -> s.withColor(0x8FB08A).withItalic(true)));
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, net.minecraft.world.item.Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        // §tackle-station (0.6.0): a bench-tied rig shows its CHOSEN weight instead of the old fixed
        // type mass — one weight line, not two.
        if (!StackNbt.get(stack).contains(com.riverfishing.tackle.TackleForm.TAG_WEIGHT)) {
            tooltip.add(Component.translatable("tooltip.riverfishing.rig_mass",
                    String.format("%.0f", type.massGrams())).withStyle(s -> s.withColor(0xA0A0A0)));
        }
        appendTackleStationLines(stack, tooltip);

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
