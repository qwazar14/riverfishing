package com.riverfishing.network;

import com.riverfishing.RiverFishing;
import com.riverfishing.item.StackNbt;
import com.riverfishing.menu.TackleStationMenu;
import com.riverfishing.registry.ModItems;
import com.riverfishing.tackle.TiedDesign;
import dev.architectury.networking.NetworkManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.function.Predicate;

/**
 * Client → server: tie the drawing on the open Tackle Station (§tying).
 *
 * <p>The client sends the canvas and nothing else. The server re-reads everything that matters —
 * the menu is the station, the drawing is legal, the inventory holds every material it costs and an
 * iron nugget for the hook — and only then consumes and ties, on the hook size the station's picker
 * shows. The lure goes straight to the inventory. A client that lies about its materials ties nothing.
 */
public class TieLurePacket implements ModNetwork.RfPacket {
    public static final CustomPacketPayload.Type<TieLurePacket> TYPE = new CustomPacketPayload.Type<>(RiverFishing.id("tie_lure"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TieLurePacket> STREAM_CODEC =
            StreamCodec.of((buf, pkt) -> pkt.write(buf), TieLurePacket::decode);

    /** The hook: a nugget of iron, the way the station makes one. */
    public static final Predicate<ItemStack> HOOK = s -> s.is(Items.IRON_NUGGET);
    /** Everything the store accepts: every ingredient, every dye, the nugget. */
    public static final Predicate<ItemStack> STORABLE = s -> {
        if (s.isEmpty()) return false;
        if (HOOK.test(s)) return true;
        for (int px = 1; px <= TiedDesign.LAST; px++) if (ingredient(px).test(s)) return true;
        for (int px = TiedDesign.THREAD0 + 1; px < TiedDesign.THREAD0 + 16; px++) if (dyeFor(px).test(s)) return true;
        return false;
    };

    private final byte[] design;

    public TieLurePacket(byte[] design) {
        this.design = design;
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }

    @Override
    public void write(FriendlyByteBuf buf) { buf.writeByteArray(design); }

    public static TieLurePacket decode(FriendlyByteBuf buf) { return new TieLurePacket(buf.readByteArray(TiedDesign.SIZE * TiedDesign.SIZE)); }

    /** What a material is paid with: the item test for pixel value {@code px}. */
    public static Predicate<ItemStack> ingredient(int px) {
        if (px >= TiedDesign.THREAD0 && px < TiedDesign.THREAD0 + 16) return s -> s.is(Items.STRING);
        return switch (px) {
            case TiedDesign.HACKLE -> s -> s.is(Items.FEATHER);
            case TiedDesign.FUR -> s -> s.is(ItemTags.WOOL);
            case TiedDesign.BEAD_IRON -> s -> s.is(Items.IRON_NUGGET);
            case TiedDesign.BEAD_GOLD -> s -> s.is(Items.GOLD_NUGGET);
            case TiedDesign.TINSEL -> s -> s.is(Items.COPPER_INGOT);
            case TiedDesign.EYE -> s -> s.is(Items.INK_SAC);
            default -> s -> false;
        };
    }

    /** Coloured thread also costs its dye — white is the string as it comes. */
    public static Predicate<ItemStack> dyeFor(int px) {
        int dye = px - TiedDesign.THREAD0;
        if (dye <= 0 || dye >= 16) return null;
        DyeColor c = DyeColor.values()[dye];
        return s -> s.get(net.minecraft.core.component.DataComponents.DYE) == c;
    }

    /** Every slot the station shows — its three material wells and the player's inventory; never the result well. */
    private static boolean payable(AbstractContainerMenu menu, Slot slot) {
        return slot.index != TackleStationMenu.RESULT_SLOT;
    }

    public static int count(AbstractContainerMenu menu, Predicate<ItemStack> test) {
        int n = 0;
        for (Slot slot : menu.slots) {
            if (!payable(menu, slot)) continue;
            ItemStack s = slot.getItem();
            if (test.test(s)) n += s.getCount();
        }
        return n;
    }

    private static void take(AbstractContainerMenu menu, Predicate<ItemStack> test, int n) {
        for (Slot slot : menu.slots) {
            if (n <= 0) break;
            if (!payable(menu, slot)) continue;
            ItemStack s = slot.getItem();
            if (!test.test(s)) continue;
            int t = Math.min(n, s.getCount());
            s.shrink(t); n -= t;
            slot.setChanged();
        }
    }

    /** True when the wells and the inventory can pay for the drawing's materials (the hook is asked for separately). */
    public static boolean affordable(AbstractContainerMenu menu, byte[] design) {
        int[] cost = TiedDesign.cost(design);
        for (int px = 1; px <= TiedDesign.LAST; px++) {
            if (cost[px] == 0) continue;
            if (count(menu, ingredient(px)) < cost[px]) return false;
            Predicate<ItemStack> dye = dyeFor(px);
            if (dye != null && count(menu, dye) < cost[px]) return false;
        }
        return true;
    }

    public void handleServer(NetworkManager.PacketContext ctx) {
        if (!(ctx.getPlayer() instanceof ServerPlayer sp)) return;
        if (!(sp.containerMenu instanceof TackleStationMenu menu)) return;
        if (!TiedDesign.valid(design)) return;
        // the bead nuggets and the hook nugget come out of the same pile — ask for both at once
        int[] cost = TiedDesign.cost(design);
        if (count(menu, HOOK) < 1 + cost[TiedDesign.BEAD_IRON] || !affordable(menu, design)) return;
        for (int px = 1; px <= TiedDesign.LAST; px++) {
            if (cost[px] == 0) continue;
            take(menu, ingredient(px), cost[px]);
            Predicate<ItemStack> dye = dyeFor(px);
            if (dye != null) take(menu, dye, cost[px]);
        }
        take(menu, HOOK, 1);
        ItemStack lure = new ItemStack(ModItems.TIED_LURE.get());
        byte[] d = design.clone();
        int size = menu.hookSize();
        String maker = sp.getGameProfile().name();
        StackNbt.mutate(lure, tag -> {
            tag.putByteArray(TiedDesign.TAG_DESIGN, d);
            tag.putInt(TiedDesign.TAG_HOOK, size);
            tag.putString(TiedDesign.TAG_MAKER, maker);
        });
        if (!sp.getInventory().add(lure)) sp.drop(lure, false);
        sp.level().playSound(null, sp.blockPosition(), net.minecraft.sounds.SoundEvents.BUNDLE_INSERT,
                net.minecraft.sounds.SoundSource.PLAYERS, 0.8f, 1.1f);
    }
}
