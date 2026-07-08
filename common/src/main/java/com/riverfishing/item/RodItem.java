package com.riverfishing.item;

import com.riverfishing.component.ComponentSlot;
import com.riverfishing.component.RodType;
import com.riverfishing.fishing.FishingManager;
import com.riverfishing.menu.RodAssemblyMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * An assembled rod (§3.1). The rod item is the blank; reel/line/rig/hook live in NBT.
 * Sneak-use opens the assembly GUI; a normal use on water casts / hooks (§4, §7).
 */
public class RodItem extends Item {
    private final RodType rodType;

    public RodItem(RodType rodType, Properties properties) {
        // No stacksTo(1) here: durability already forces max stack 1, and combining them crashes.
        super(properties);
        this.rodType = rodType;
    }

    public RodType rodType() { return rodType; }

    // §multiloader: the composited rod icon (§rod-layers) is a custom item renderer registered per platform
    // in the client bootstrap (Forge IClientItemExtensions / Fabric BuiltinItemRendererRegistry) — no longer
    // via Forge's Item#initializeClient, which doesn't exist on the vanilla/common Item.

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack rod = player.getItemInHand(hand);
        if (player.isSecondaryUseActive()) {
            // Open the rod-assembly GUI — reeling in any line first (§session-guard).
            if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
                FishingManager.reelInIfAny(serverPlayer);
                openAssembly(serverPlayer, hand);
            }
            return InteractionResultHolder.sidedSuccess(rod, level.isClientSide);
        }
        // With an ACTIVE session the click is a strike / reel pulse (server-side); the client guesses
        // session state from its own line renderer so both sides agree on hold behaviour.
        boolean sessionAction;
        if (!level.isClientSide) {
            sessionAction = FishingManager.handleRodUse(player, hand);
        } else {
            sessionAction = dev.architectury.utils.EnvExecutor.getEnvSpecific(
                    () -> () -> com.riverfishing.client.ClientLineState.active(), () -> () -> false);
        }
        // Spinning/ultralight (§spin-charge, 2.3): with a LIVE session the hold drives the retrieve/fight
        // (onUseTick); with NO session the hold CHARGES the cast (power bar) and RELEASING throws to that
        // distance — the distance mini-game is back, while hold-to-retrieve still works (it just starts on
        // the next hold, after the lure lands). The cast itself fires in releaseUsing().
        if (rodType.activeRetrieve()) {
            player.startUsingItem(hand);
            return InteractionResultHolder.consume(rod);
        }

        if (sessionAction) {
            return InteractionResultHolder.sidedSuccess(rod, level.isClientSide);
        }

        // No session: begin the power-bar charge (§cast-minigame) — the cast fires on release.
        if (!RodData.isAssembled(rod)) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.translatable("message.riverfishing.not_assembled")
                        .withStyle(ChatFormatting.RED), true);
            }
            return InteractionResultHolder.fail(rod);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(rod);
    }

    /**
     * Anvil repair with the priciest ingredient of the rod's recipe (§rod-durability):
     * bamboo rods take bamboo, iron-built rods take iron, gold-built take gold, the carp rod a diamond.
     */
    @Override
    public boolean isValidRepairItem(ItemStack toRepair, ItemStack repair) {
        String key = rodType.jsonKey();
        if ("stick".equals(key)) return repair.is(net.minecraft.world.item.Items.STICK);
        if ("bamboo".equals(key)) return repair.is(net.minecraft.world.item.Items.BAMBOO);
        if ("feeder".equals(key) || "bottom".equals(key)) return repair.is(net.minecraft.world.item.Items.GOLD_INGOT);
        if ("carp".equals(key)) return repair.is(net.minecraft.world.item.Items.DIAMOND);
        return repair.is(net.minecraft.world.item.Items.IRON_INGOT); // pole / ultralight / spinning
    }

    /**
     * Cast power from charge ticks (§cast-minigame): a triangle wave — power climbs for a second,
     * then falls back (overcharging weakens the throw), so distance is a timing skill.
     */
    public static float castPower(int ticksUsed) {
        int period = 40;
        float phase = (ticksUsed % period) / (float) period;
        float tri = phase < 0.5f ? phase * 2f : 2f - phase * 2f;
        return 0.15f + 0.85f * tri;
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return 72000; // effectively "hold as long as you like"
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.NONE;
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remainingUseDuration) {
        if (!level.isClientSide && entity instanceof ServerPlayer sp) {
            FishingManager.retrieveTick(sp);
        }
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        if (level.isClientSide || !(entity instanceof ServerPlayer sp)) return;
        if (FishingManager.hasSession(sp)) {
            // Was holding a retrieve — or, on a lure rod, letting go during the take sets the hook (2.4).
            FishingManager.onRetrieveStop(sp);
            return;
        }
        // Releasing a charge: the power at THIS moment decides the cast distance (§cast-minigame). Lure
        // rods charge-and-cast the same way now (§spin-charge, 2.3) — no more instant click-cast.
        int used = getUseDuration(stack) - timeLeft;
        InteractionHand hand = sp.getOffhandItem() == stack ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        FishingManager.chargedCast(sp, hand, castPower(used));
    }

    private void openAssembly(ServerPlayer player, InteractionHand hand) {
        // §closed-slots: float and lure rods carry a built-in rig — install it so the assembly GUI can
        // show that rig's own slots inline (float/hook/bait, leader/lure) with no swappable RIG column.
        // (Bottom rods have no native rig, so this is a no-op and they keep the swappable column.)
        if (rodType.directTackle()) {
            RodData.ensureNativeRig(player.getItemInHand(hand), rodType);
        }
        // §multiloader: NetworkHooks.openScreen -> Architectury MenuRegistry.openExtendedMenu; the extra
        // FriendlyByteBuf (the hand) is written by the ExtendedMenuProvider and read by fromNetwork.
        dev.architectury.registry.menu.ExtendedMenuProvider provider =
                new dev.architectury.registry.menu.ExtendedMenuProvider() {
                    @Override
                    public Component getDisplayName() {
                        return player.getItemInHand(hand).getHoverName();
                    }

                    @Nullable
                    @Override
                    public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) {
                        return new RodAssemblyMenu(id, inv, hand);
                    }

                    @Override
                    public void saveExtraData(FriendlyByteBuf buf) {
                        buf.writeEnum(hand);
                    }
                };
        dev.architectury.registry.menu.MenuRegistry.openExtendedMenu(player, provider);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        boolean assembled = RodData.isAssembled(stack);
        tooltip.add(Component.translatable(assembled
                        ? "tooltip.riverfishing.rod_assembled"
                        : "tooltip.riverfishing.rod_unassembled")
                .withStyle(assembled ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
        // Rod test (§rod-test): the rigged-weight window this blank is built for.
        if (rodType.castWeightMax() > 0) {
            tooltip.add(Component.translatable("tooltip.riverfishing.rod_test",
                    (int) rodType.castWeightMin(), (int) rodType.castWeightMax())
                    .withStyle(ChatFormatting.GRAY));
        }
        appendComponentLine(stack, ComponentSlot.REEL, tooltip);
        appendComponentLine(stack, ComponentSlot.LINE, tooltip);
        appendComponentLine(stack, ComponentSlot.RIG, tooltip);
        tooltip.add(Component.translatable("tooltip.riverfishing.rod_hint").withStyle(ChatFormatting.DARK_GRAY));
    }

    private void appendComponentLine(ItemStack rod, ComponentSlot slot, List<Component> tooltip) {
        ItemStack comp = RodData.get(rod, slot);
        if (!comp.isEmpty()) {
            tooltip.add(Component.literal(" • ").append(comp.getHoverName()).withStyle(ChatFormatting.GRAY));
        }
    }
}
