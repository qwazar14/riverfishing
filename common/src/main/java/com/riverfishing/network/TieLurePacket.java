package com.riverfishing.network;

import com.riverfishing.RiverFishing;
import com.riverfishing.item.HookItem;
import com.riverfishing.item.StackNbt;
import com.riverfishing.menu.TyingViseMenu;
import com.riverfishing.registry.ModItems;
import com.riverfishing.tackle.TiedDesign;
import dev.architectury.networking.NetworkManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.function.Predicate;

/**
 * Client → server: tie the drawing on the open vise (§tying).
 *
 * <p>The client sends the canvas and nothing else. The server re-reads everything that matters —
 * the menu is a vise, the hook slot holds a hook, the result slot is empty, the drawing is legal, and
 * the player's inventory holds every material it costs — and only then consumes and ties. A client
 * that lies about its materials ties nothing.
 */
public class TieLurePacket implements ModNetwork.RfPacket {
    public static final CustomPacketPayload.Type<TieLurePacket> TYPE = new CustomPacketPayload.Type<>(RiverFishing.id("tie_lure"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TieLurePacket> STREAM_CODEC =
            StreamCodec.of((buf, pkt) -> pkt.write(buf), TieLurePacket::decode);

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
        return s -> s.getItem() instanceof DyeItem d && d.getDyeColor() == c;
    }

    public static int count(Inventory inv, Predicate<ItemStack> test) {
        int n = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (test.test(s)) n += s.getCount();
        }
        return n;
    }

    private static void take(Inventory inv, Predicate<ItemStack> test, int n) {
        for (int i = 0; i < inv.getContainerSize() && n > 0; i++) {
            ItemStack s = inv.getItem(i);
            if (!test.test(s)) continue;
            int t = Math.min(n, s.getCount());
            s.shrink(t); n -= t;
        }
    }

    /** True when the inventory can pay for the drawing. Shared with the screen's affordability preview. */
    public static boolean affordable(Inventory inv, byte[] design) {
        int[] cost = TiedDesign.cost(design);
        for (int px = 1; px <= TiedDesign.LAST; px++) {
            if (cost[px] == 0) continue;
            if (count(inv, ingredient(px)) < cost[px]) return false;
            Predicate<ItemStack> dye = dyeFor(px);
            if (dye != null && count(inv, dye) < cost[px]) return false;
        }
        return true;
    }

    public void handleServer(NetworkManager.PacketContext ctx) {
        if (!(ctx.getPlayer() instanceof ServerPlayer sp)) return;
        if (!(sp.containerMenu instanceof TyingViseMenu menu)) return;
        if (!TiedDesign.valid(design) || !menu.result().isEmpty()) return;
        if (!(menu.hook().getItem() instanceof HookItem hook)) return;
        Inventory inv = sp.getInventory();
        if (!affordable(inv, design)) return;
        int[] cost = TiedDesign.cost(design);
        for (int px = 1; px <= TiedDesign.LAST; px++) {
            if (cost[px] == 0) continue;
            take(inv, ingredient(px), cost[px]);
            Predicate<ItemStack> dye = dyeFor(px);
            if (dye != null) take(inv, dye, cost[px]);
        }
        ItemStack lure = new ItemStack(ModItems.TIED_LURE.get());
        byte[] d = design.clone();
        int size = hook.hookSize();
        String maker = sp.getGameProfile().getName();
        StackNbt.mutate(lure, tag -> {
            tag.putByteArray(TiedDesign.TAG_DESIGN, d);
            tag.putInt(TiedDesign.TAG_HOOK, size);
            tag.putString(TiedDesign.TAG_MAKER, maker);
        });
        menu.takeHook();
        menu.setResult(lure);
        sp.level().playSound(null, sp.blockPosition(), net.minecraft.sounds.SoundEvents.BUNDLE_INSERT,
                net.minecraft.sounds.SoundSource.PLAYERS, 0.8f, 1.1f);
    }
}
