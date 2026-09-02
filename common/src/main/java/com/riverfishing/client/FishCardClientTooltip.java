package com.riverfishing.client;

import com.riverfishing.fish.CatchCard;
import com.riverfishing.item.FishCardTooltip;
import com.riverfishing.item.FishItem;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * §fish-card: the card as it is drawn under the fish's name.
 *
 * <p>Three blocks separated by rules — the fish, its kind, its catch — then, behind Shift, how it was
 * caught. Labels in grey, values in the colour of what they are, badges as filled plaques. The frame
 * around the whole tooltip is coloured to the fish's standing through {@link #FRAME}: this component's
 * {@code getWidth} runs during layout, BEFORE vanilla paints the background, so it can leave the colour
 * for the frame mixin to pick up and clear.
 */
public final class FishCardClientTooltip implements ClientTooltipComponent {
    /** ARGB the next tooltip background should wear, or 0 for vanilla. Set here, consumed by the mixin. */
    public static int FRAME = 0;

    private static final int LABEL = 0xFFA0A0A0, WHITE = 0xFFF0F0F0, DIM = 0xFF707070, RULE = 0x50FFFFFF;
    private static final int GREEN = 0xFF55FF55, AQUA = 0xFF55FFFF, YELLOW = 0xFFFFFF55, GOLD = 0xFFFFAA00,
            BLUE = 0xFF5599FF, PINK = 0xFFFF55FF, RED = 0xFFFF5555, ORANGE = 0xFFFFA040;
    private static final int ROW = 10, PAD = 4;

    private final ItemStack fish;
    private final CompoundTag c;
    private final List<Object[]> rows = new ArrayList<>();   // {label Component, value Component, colour} or null = rule
    private final List<Object[]> badges = new ArrayList<>(); // {text, bg}
    private final int frame;

    public FishCardClientTooltip(FishCardTooltip data) {
        this.fish = data.fish();
        this.c = CatchCard.of(fish);
        CompoundTag t = com.riverfishing.item.StackNbt.get(fish);
        String morph = t.getString(FishItem.TAG_MORPH);
        boolean legend = t.getBoolean(FishItem.TAG_LEGEND), trophy = FishItem.isTrophy(fish);
        if (!morph.isEmpty()) badges.add(new Object[]{key("badge.special"), 0xFFB0209A});
        if (trophy) badges.add(new Object[]{key("badge.trophy"), 0xFF8A6A10});
        if (legend) badges.add(new Object[]{key("badge.legendary"), 0xFF5A2AA0});
        if (FishItem.isPrime(fish)) badges.add(new Object[]{key("badge.prime"), 0xFF3A6A20});
        if (!FishItem.isLegal(fish)) badges.add(new Object[]{key("badge.foul"), 0xFF8A2020});
        frame = legend ? 0xFF9A5AFF : !morph.isEmpty() ? 0xFFE040D0 : trophy ? 0xFFF0C040 : 0xFF40A060;

        boolean seen = c.getBoolean("Seen");
        // The fish.
        if (c.getInt("Value") > 0) row("value", key("emeralds", c.getInt("Value")), GREEN);
        row("nature", seen ? key("nature." + CatchCard.NATURE[c.getByte("Nature")]) : key("hidden"), seen ? AQUA : DIM);
        String biome = c.getString("Biome");
        if (!biome.isEmpty()) row("location", Component.translatable("biome." + biome.replace(':', '.')), YELLOW);
        Component size = key("size." + CatchCard.SIZE[Math.min(4, c.getByte("Size"))])
                .append(Component.literal(c.getByte("Sex") == 0 ? " ♀" : " ♂")
                        .withStyle(net.minecraft.network.chat.Style.EMPTY.withColor(c.getByte("Sex") == 0 ? 0xFF60A0 : 0x60A0FF)));
        row("size", size, AQUA);
        row("weight", FishItem.weightText(FishItem.getWeightG(fish)), WHITE);
        row("length", Component.literal(FishItem.getLengthCm(fish) + " cm"), WHITE);
        rule();
        // Its kind.
        if (!c.getString("Group").isEmpty()) row("group", key("group." + c.getString("Group")), GREEN);
        if (!c.getString("Life").isEmpty()) row("lifestyle", key("life." + c.getString("Life")), BLUE);
        String eco = c.getString("Eco");
        if (!eco.isEmpty()) row("ecosystem", key("eco." + eco), eco.equals("native") ? GREEN : eco.equals("settled") ? YELLOW : ORANGE);
        rule();
        // Its catch.
        row("angler", Component.literal(c.getString("Angler")), AQUA);
        row("date", Component.literal(c.getString("Date") + "  ·  " + key("day", c.getLong("Day")).getString()), GOLD);
        String rodItem = c.getString("RodItem");
        row("rod", rodItem.isEmpty() ? key("rod." + c.getString("Rod"))
                : Component.translatable("item.riverfishing." + rodItem), GREEN);

        if (Screen.hasShiftDown()) {
            rule();
            if (!c.getString("Bait").isEmpty()) row("bait", Component.translatable("item.riverfishing." + c.getString("Bait")), YELLOW);
            if (!c.getString("Water").isEmpty()) row("water", Component.translatable("water.riverfishing." + c.getString("Water")), BLUE);
            if (!c.getString("Time").isEmpty()) row("time", Component.translatable("time.riverfishing." + c.getString("Time")), WHITE);
            if (!c.getString("Season").isEmpty()) row("season", Component.translatable("season.riverfishing." + c.getString("Season")), WHITE);
            if (!c.getString("Weather").isEmpty()) row("weather", Component.translatable("weather.riverfishing." + c.getString("Weather")), WHITE);
            if (!c.getString("Bed").isEmpty()) row("bed", Component.translatable("bed.riverfishing." + c.getString("Bed")), WHITE);
            if (!c.getString("Spot").isEmpty()) row("spot", Component.translatable("card.riverfishing.spot." + c.getString("Spot")), AQUA);
            if (c.getBoolean("Ice")) row("ice", key("yes"), AQUA);
            row("genes", seen ? Component.literal(c.getString("Genes")) : key("hidden"), seen ? PINK : DIM);
        } else {
            rows.add(new Object[]{null, key("shift"), DIM});
        }
    }

    private static net.minecraft.network.chat.MutableComponent key(String k, Object... args) {
        return Component.translatable("card.riverfishing." + k, args);
    }

    private void row(String label, Component value, int colour) {
        rows.add(new Object[]{key(label), value, colour});
    }

    private void rule() {
        rows.add(null);
    }

    @Override
    public int getHeight() {
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
    public void renderImage(Font font, int x, int y, GuiGraphics g) {
        FRAME = 0;
        int lw = labelWidth(font), width = getWidth(font);
        FRAME = 0;
        int cy = y;
        if (!badges.isEmpty()) {
            int bx = x;
            for (Object[] b : badges) {
                Component txt = (Component) b[0];
                int w = font.width(txt) + 6;
                g.fill(bx, cy, bx + w, cy + 11, (int) b[1]);
                g.drawString(font, txt, bx + 3, cy + 2, WHITE, false);
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
                g.drawString(font, (Component) r[0], x, cy, LABEL, false);
                g.drawString(font, (Component) r[1], x + lw + PAD, cy, (int) r[2], false);
            } else {
                g.drawString(font, (Component) r[1], x, cy, (int) r[2], false);
            }
            cy += ROW;
        }
    }
}
