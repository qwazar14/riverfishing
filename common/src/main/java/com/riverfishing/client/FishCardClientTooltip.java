package com.riverfishing.client;

import com.riverfishing.fish.CatchCard;
import com.riverfishing.item.ContractItem;
import com.riverfishing.item.FishCardTooltip;
import com.riverfishing.item.FishItem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * §fish-card: the card as it is drawn under the item's name — a landed fish, or a contract paper.
 *
 * <p>Blocks separated by rules, labels in grey, values in the colour of what they are, badges as filled
 * plaques. The frame around the whole tooltip is coloured to the card's standing through {@link #FRAME}:
 * {@code getWidth} runs during layout, BEFORE vanilla paints the background, so it can leave the colour
 * for the frame mixin to pick up and clear.
 *
 * <p>The contract card counts the fish in the bag and the days left on the CLIENT: the inventory is
 * here, and the world day is synced every second — so both tick over while you hold the paper, with
 * no packet to wait for.
 */
public final class FishCardClientTooltip implements ClientTooltipComponent {
    /** ARGB the next tooltip background should wear, or 0 for vanilla. Set here, consumed by the mixin. */
    public static int FRAME = 0;

    private static final int LABEL = 0xFFA0A0A0, WHITE = 0xFFF0F0F0, DIM = 0xFF707070, RULE = 0x50FFFFFF;
    private static final int GREEN = 0xFF55FF55, AQUA = 0xFF55FFFF, YELLOW = 0xFFFFFF55, GOLD = 0xFFFFAA00,
            BLUE = 0xFF5599FF, PINK = 0xFFFF55FF, RED = 0xFFFF5555, ORANGE = 0xFFFFA040;
    private static final int ROW = 10, PAD = 4;

    private final List<Object[]> rows = new ArrayList<>();   // {label Component or null, value Component, colour}, or null = rule
    private final List<Object[]> badges = new ArrayList<>(); // {text, bg}
    private final int frame;

    public FishCardClientTooltip(FishCardTooltip data) {
        ItemStack stack = data.fish();
        frame = stack.getItem() instanceof ContractItem ? contract(stack) : fish(stack);
    }

    // ---- the fish --------------------------------------------------------------------------------

    private int fish(ItemStack fish) {
        CompoundTag c = CatchCard.of(fish);
        CompoundTag t = com.riverfishing.item.StackNbt.get(fish);
        String morph = t.getStringOr(FishItem.TAG_MORPH, "");
        boolean legend = t.getBooleanOr(FishItem.TAG_LEGEND, false), trophy = FishItem.isTrophy(fish);
        if (!morph.isEmpty()) badges.add(new Object[]{key("badge.special"), 0xFFB0209A});
        if (trophy) badges.add(new Object[]{key("badge.trophy"), 0xFF8A6A10});
        if (legend) badges.add(new Object[]{key("badge.legendary"), 0xFF5A2AA0});
        if (FishItem.isPrime(fish)) badges.add(new Object[]{key("badge.prime"), 0xFF3A6A20});
        if (!FishItem.isLegal(fish)) badges.add(new Object[]{key("badge.foul"), 0xFF8A2020});

        String latin = c.getStringOr("Latin", "");
        if (!latin.isEmpty()) plain(Component.literal(latin).withStyle(ChatFormatting.ITALIC), LABEL);
        boolean seen = c.getBooleanOr("Seen", false);
        // §nature: the counter buys PRIME fish; anything else has no price there, and says so.
        if (FishItem.isPrime(fish) && c.getIntOr("Value", 0) > 0) row("value", key("emeralds", c.getIntOr("Value", 0)), GREEN);
        else row("value", Component.literal("—"), DIM);
        row("nature", key("nature." + CatchCard.NATURE[c.getByteOr("Nature", (byte) 0)]), AQUA);   // in the open: the fish fought with it, you felt it
        String biome = c.getStringOr("Biome", "");
        if (!biome.isEmpty()) row("location", Component.translatable("biome." + biome.replace(':', '.')), YELLOW);
        Component size = key("size." + CatchCard.SIZE[Math.min(4, c.getByteOr("Size", (byte) 0))])
                .append(Component.literal(c.getByteOr("Sex", (byte) 0) == 0 ? " ♀" : " ♂")
                        .withStyle(net.minecraft.network.chat.Style.EMPTY.withColor(c.getByteOr("Sex", (byte) 0) == 0 ? 0xFF60A0 : 0x60A0FF)));
        row("size", size, AQUA);
        row("weight", FishItem.weightText(FishItem.getWeightG(fish)), WHITE);
        row("length", Component.literal(FishItem.getLengthCm(fish) + " cm"), WHITE);
        rule();
        if (!c.getStringOr("Group", "").isEmpty()) row("group", key("group." + c.getStringOr("Group", "")), GREEN);
        if (!c.getStringOr("Life", "").isEmpty()) row("lifestyle", key("life." + c.getStringOr("Life", "")), BLUE);
        String eco = c.getStringOr("Eco", "");
        if (!eco.isEmpty()) row("ecosystem", key("eco." + eco), eco.equals("native") ? GREEN : eco.equals("settled") ? YELLOW : ORANGE);
        rule();
        row("angler", Component.literal(c.getStringOr("Angler", "")), AQUA);
        row("date", Component.literal(c.getStringOr("Date", "") + "  ·  " + key("day", c.getLongOr("Day", 0L)).getString()), GOLD);
        String rodItem = c.getStringOr("RodItem", "");
        row("rod", rodItem.isEmpty() ? key("rod." + c.getStringOr("Rod", ""))
                : Component.translatable("item.riverfishing." + rodItem), GREEN);

        if (Minecraft.getInstance().hasShiftDown()) {
            rule();
            if (!c.getStringOr("Bait", "").isEmpty()) row("bait", Component.translatable("item.riverfishing." + c.getStringOr("Bait", "")), YELLOW);
            if (!c.getStringOr("Water", "").isEmpty()) row("water", Component.translatable("water.riverfishing." + c.getStringOr("Water", "")), BLUE);
            if (!c.getStringOr("Time", "").isEmpty()) row("time", Component.translatable("time.riverfishing." + c.getStringOr("Time", "")), WHITE);
            if (!c.getStringOr("Season", "").isEmpty()) row("season", Component.translatable("season.riverfishing." + c.getStringOr("Season", "")), WHITE);
            if (!c.getStringOr("Weather", "").isEmpty()) row("weather", Component.translatable("weather.riverfishing." + c.getStringOr("Weather", "")), WHITE);
            if (!c.getStringOr("Bed", "").isEmpty()) row("bed", Component.translatable("bed.riverfishing." + c.getStringOr("Bed", "")), WHITE);
            if (!c.getStringOr("Spot", "").isEmpty()) row("spot", Component.translatable("card.riverfishing.spot." + c.getStringOr("Spot", "")), AQUA);
            if (c.getBooleanOr("Ice", false)) row("ice", key("yes"), AQUA);
            row("genes", seen ? Component.literal(c.getStringOr("Genes", "")) : key("hidden"), seen ? PINK : DIM);
        } else {
            plain(key("shift"), DIM);
        }
        return legend ? 0xFF9A5AFF : !morph.isEmpty() ? 0xFFE040D0 : trophy ? 0xFFF0C040 : 0xFF40A060;
    }

    // ---- the contract ----------------------------------------------------------------------------

    private int contract(ItemStack paper) {
        CompoundTag t = ContractItem.tag(paper);
        Minecraft mc = Minecraft.getInstance();
        long day = mc.level == null ? 0 : mc.level.getOverworldClockTime() / 24000L;
        long daysLeft = t.getLongOr("Exp", 0L) - day;
        boolean expired = daysLeft < 0;
        badges.add(expired ? new Object[]{key("badge.expired"), 0xFF8A2020} : new Object[]{key("badge.contract"), 0xFF8A6A10});

        String sp = t.getStringOr("Sp", "");
        int n = t.getIntOr("N", 0);
        row("contract.fish", Component.translatable("fish.riverfishing." + sp), AQUA);
        row("contract.count", Component.literal(String.valueOf(n)), WHITE);
        row("contract.from", Component.literal(ContractItem.grams(t.getIntOr("W", 0))), WHITE);
        // Every term on its own line, labelled — the whole point of the card: nothing folded away.
        if (!t.getStringOr("Water", "").isEmpty()) row("water", Component.translatable("water.riverfishing." + t.getStringOr("Water", "")), BLUE);
        if (!t.getStringOr("Rod", "").isEmpty()) row("rod", key("rod." + t.getStringOr("Rod", "")), GREEN);
        if (!t.getStringOr("Bait", "").isEmpty()) row("bait", Component.translatable("item.riverfishing." + t.getStringOr("Bait", "")), YELLOW);
        if (!t.getStringOr("Time", "").isEmpty()) row("time", Component.translatable("time.riverfishing." + t.getStringOr("Time", "")), WHITE);
        rule();
        int have = mc.player == null ? 0
                : com.riverfishing.fishing.Contracts.held(mc.player.getInventory(), sp, t.getIntOr("W", 0), t).size();
        row("contract.bag", Component.literal(have + " / " + n), have >= n ? GREEN : YELLOW);
        row("contract.pays", key("contract.pays_value", t.getIntOr("Em", 0), t.getIntOr("Xp", 0), t.getIntOr("Rep", 0)), GREEN);
        row("contract.days", expired ? key("contract.expired") : Component.literal(String.valueOf(daysLeft)),
                expired ? RED : daysLeft <= 1 ? ORANGE : GOLD);
        return expired ? 0xFFC03030 : 0xFFC8A040;
    }

    // ---- rows ------------------------------------------------------------------------------------

    private static net.minecraft.network.chat.MutableComponent key(String k, Object... args) {
        return Component.translatable("card.riverfishing." + k, args);
    }

    private void row(String label, Component value, int colour) {
        rows.add(new Object[]{key(label), value, colour});
    }

    private void plain(Component value, int colour) {
        rows.add(new Object[]{null, value, colour});
    }

    private void rule() {
        rows.add(null);
    }

    @Override
    public int getHeight(Font font) {
        int h = badges.isEmpty() ? 0 : 13;
        for (Object[] r : rows) h += r == null ? 5 : ROW;
        return h + 2;
    }

    @Override
    public int getWidth(Font font) {
        FRAME = frame;                                   // layout runs before the background is painted
        int lw = labelWidth(font), w = 0;
        for (Object[] r : rows) {
            if (r == null) continue;
            int vw = font.width((Component) r[1]);
            w = Math.max(w, (r[0] == null ? 0 : lw + PAD) + vw);
        }
        int bw = 0;
        for (Object[] b : badges) bw += font.width((Component) b[0]) + 8;
        return Math.max(w, bw) + 2;
    }

    private int labelWidth(Font font) {
        int lw = 0;
        for (Object[] r : rows) if (r != null && r[0] != null) lw = Math.max(lw, font.width((Component) r[0]));
        return lw;
    }

    @Override
    public void extractImage(Font font, int x, int y, int width0, int height0, GuiGraphicsExtractor g) {
        int lw = labelWidth(font), width = getWidth(font);
        FRAME = 0;
        int cy = y;
        if (!badges.isEmpty()) {
            int bx = x;
            for (Object[] b : badges) {
                Component txt = (Component) b[0];
                int w = font.width(txt) + 6;
                g.fill(bx, cy, bx + w, cy + 11, (int) b[1]);
                g.text(font, txt, bx + 3, cy + 2, WHITE, false);
                bx += w + 2;
            }
            cy += 13;
        }
        for (Object[] r : rows) {
            if (r == null) {
                g.fill(x, cy + 2, x + width, cy + 3, RULE);
                cy += 5;
                continue;
            }
            if (r[0] != null) {
                g.text(font, (Component) r[0], x, cy, LABEL, false);
                g.text(font, (Component) r[1], x + lw + PAD, cy, (int) r[2], false);
            } else {
                g.text(font, (Component) r[1], x, cy, (int) r[2], false);
            }
            cy += ROW;
        }
    }
}
