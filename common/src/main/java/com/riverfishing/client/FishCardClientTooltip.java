package com.riverfishing.client;

import com.mojang.math.Axis;
import com.riverfishing.RiverFishing;
import com.riverfishing.fish.CatchCard;
import com.riverfishing.item.ContractItem;
import com.riverfishing.item.FishCardTooltip;
import com.riverfishing.item.FishItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * §fish-card-2 (1.1.0): the card drawn under the item's name — a landed fish's catch record, or a contract's
 * order form. It used to be badges and a column of "Label: value" rows, which is the fishing server's card the
 * mod started from; the record is the mod's own: the weight big with where it stands in its kind on a scale,
 * the length on a measuring board, tilted stamps for what makes the fish special, the rod and bait as their
 * own pictures. The order is a paper form in ink.
 *
 * <p>The ground under both is painted by the frame mixin: {@code getWidth} runs during layout, BEFORE vanilla
 * paints the background, so it leaves the accent and the ground ({@link #FRAME}, {@link #STYLE}) for the mixin
 * to pick up and clear.
 *
 * <p>The contract counts the fish in the bag and the days left on the CLIENT: the inventory is here, and the
 * world day is synced every second — so both tick over while you hold the paper, with no packet to wait for.
 */
public final class FishCardClientTooltip implements ClientTooltipComponent {
    /** ARGB of the card's accent, or 0 for a vanilla tooltip. Set here, consumed by the mixin. */
    public static int FRAME = 0;
    /** The ground the mixin paints: 0 deep water (a fish), 1 paper (a contract). */
    public static int STYLE = 0;

    // water: cream and blue-grey inks
    private static final int CREAM = 0xFFECE6D6, DIM = 0xFF8FA8A8, FAINT = 0xFF607878, SOFT = 0xFFB8D8D0;
    private static final int PINK = 0xFFF080B8, BLUE = 0xFF60AAF0, GOLD = 0xFFE8C050, GREEN = 0xFF82DC82,
            MAGENTA = 0xFFE060D8, VIOLET = 0xFFB080FF, RED = 0xFFE05A4A, GREY = 0xFFA0A8B0;
    // paper: brown and black inks
    private static final int P_INK = 0xFF2A2218, P_LABEL = 0xFF7A6448, P_LINE = 0x66806040, P_RED = 0xFFB03020,
            P_GREEN = 0xFF2E6A2A, P_BROWN = 0xFF8A5A20;
    private static final int MIN_W = 160;

    private final boolean paper;
    private final int accent;
    private final List<Object[]> stamps = new ArrayList<>();   // {Component word, int ink}
    private int width = -1;

    // ---- the fish ----
    private Component latin = Component.empty(), weight = Component.empty(), imperial = Component.empty();
    private Component heavier, trophyFrom, lengthText = Component.empty(), sex = Component.empty(), traits = Component.empty();
    private int wPct = -1, tPct = -1, lengthCm, lengthMax;
    private final List<Object[]> lines = new ArrayList<>();       // {ItemStack icon, Component text, int ink}
    private final List<Component[]> facts = new ArrayList<>();    // {label, value}
    private boolean shift;

    // ---- the contract ----
    private final List<Component[]> form = new ArrayList<>();     // {label, value}
    private int have, need, days, em, xp, rep;
    private boolean fry, expired;

    public FishCardClientTooltip(FishCardTooltip data) {
        ItemStack stack = data.fish();
        paper = stack.getItem() instanceof ContractItem;
        accent = paper ? contract(stack) : fish(stack);
    }

    private static net.minecraft.network.chat.MutableComponent key(String k, Object... args) {
        return Component.translatable("card.riverfishing." + k, args);
    }

    private static ItemStack item(String id) {
        if (id == null || id.isEmpty()) return ItemStack.EMPTY;
        var rl = id.contains(":") ? net.minecraft.resources.ResourceLocation.tryParse(id) : RiverFishing.id(id);
        return rl == null ? ItemStack.EMPTY : new ItemStack(BuiltInRegistries.ITEM.get(rl));
    }

    // ================================================================ the fish

    private int fish(ItemStack fish) {
        CompoundTag c = CatchCard.of(fish);
        CompoundTag t = com.riverfishing.item.StackNbt.get(fish);
        String morph = t.getString(FishItem.TAG_MORPH);
        boolean legend = t.getBoolean(FishItem.TAG_LEGEND), trophy = FishItem.isTrophy(fish);
        // the stamps, most telling first — two at most, so they sit beside the weight and not on it
        if (c.getBoolean("Poached")) stamps.add(new Object[]{key("badge.poached"), RED});
        if (!FishItem.isLegal(fish)) stamps.add(new Object[]{key("badge.foul"), RED});
        if (legend) stamps.add(new Object[]{key("badge.legendary"), VIOLET});
        if (!morph.isEmpty()) stamps.add(new Object[]{key("badge.special"), MAGENTA});
        if (trophy) stamps.add(new Object[]{key("badge.trophy"), GOLD});
        if (FishItem.isPrime(fish)) stamps.add(new Object[]{key("badge.prime"), GREEN});
        if (c.getBoolean("Net")) stamps.add(new Object[]{key("badge.netted"), GREY});
        while (stamps.size() > 2) stamps.remove(stamps.size() - 1);

        latin = Component.literal(c.getString("Latin")).withStyle(net.minecraft.ChatFormatting.ITALIC);
        int g = FishItem.getWeightG(fish);
        weight = FishItem.weightText(g);
        imperial = Component.literal(FishItem.imperialText(g));

        // §weight-scale: where it stands among its kind — stamped at the catch; a fish from before it, where this
        // game knows the species (single player), is placed by the same curve now; otherwise there is no scale
        if (c.contains("WPct")) {
            wPct = c.getShort("WPct");
            tPct = c.getShort("TPct");
            trophyFrom = key("trophy_from", FishItem.weightText(c.getInt("TrophyG")));
            lengthMax = c.getShort("LMax");
        } else {
            com.riverfishing.fish.FishProfile p = com.riverfishing.fish.FishProfileManager.get().byId(FishItem.getSpecies(fish));
            if (p != null) {
                int trophyG = FishItem.trophyThresholdG(p.weightMin, p.weightMax);
                wPct = (int) Math.round(1000 * p.weightPercentile(g));
                tPct = (int) Math.round(1000 * p.weightPercentile(trophyG));
                trophyFrom = key("trophy_from", FishItem.weightText(trophyG));
                lengthMax = (int) Math.round(p.lengthMax);
            }
        }
        if (wPct >= 0) heavier = wPct >= 995 ? key("biggest") : key("heavier", Math.max(1, wPct / 10));
        lengthCm = FishItem.getLengthCm(fish);
        if (lengthMax < lengthCm) lengthMax = Math.max(lengthCm + lengthCm / 4, 10);
        lengthText = Component.literal(lengthCm + " " + key("cm").getString() + " · " + FishItem.inchesText(lengthCm));   // §card-imperial

        boolean female = c.getByte("Sex") == 0;
        sex = Component.literal(female ? "♀" : "♂").withStyle(s -> s.withColor(female ? PINK : BLUE));
        StringBuilder tr = new StringBuilder(key("size." + CatchCard.SIZE[Mth.clamp(c.getByte("Size"), 0, 4)]).getString());
        tr.append(" · ").append(key("nature." + CatchCard.NATURE[Mth.clamp(c.getByte("Nature"), 0, 3)]).getString().toLowerCase(Locale.ROOT));
        // §scale-genes, §morph-row: the variety and the morph are the fish's own look — named beside its nature
        String variety = c.getString("Variety");
        if (!variety.isEmpty()) tr.append(" · ").append(Component.translatable("variety.riverfishing." + variety).getString());
        if (!morph.isEmpty()) tr.append(" · ").append(Component.translatable("morph.riverfishing." + morph).getString());
        traits = Component.literal(tr.toString());

        // how it came out: the rod and the bait as themselves, where, and who
        String rodItem = c.getString("RodItem");
        lines.add(new Object[]{c.getBoolean("Net") ? item("cast_net") : item(rodItem),
                rodItem.isEmpty() ? key("rod." + c.getString("Rod")) : Component.translatable("item.riverfishing." + rodItem), CREAM});
        String bait = c.getString("Bait");
        if (!bait.isEmpty()) {
            lines.add(new Object[]{bait.startsWith("tied:") ? ItemStack.EMPTY : item(bait), baitName(bait), CREAM});
        }
        // §card-province: where it came out is its province and the water — the biome said "River · river" in a river;
        // a card from before the province was written keeps its biome
        StringBuilder where = new StringBuilder();
        String province = c.getString("Province"), biome = c.getString("Biome");
        if (!province.isEmpty()) where.append(Component.translatable("province.riverfishing." + province).getString());
        else if (!biome.isEmpty()) where.append(Component.translatable("biome." + biome.replace(':', '.')).getString());
        if (!c.getString("Water").isEmpty()) {
            if (where.length() > 0) where.append(" · ");
            where.append(Component.translatable("water.riverfishing." + c.getString("Water")).getString());
        }
        if (where.length() > 0) lines.add(new Object[]{new ItemStack(Items.MAP), Component.literal(where.toString()), CREAM});
        // §catch-time: the minute it came out, in the reader's own clock; cards from before 1.1.0 have the date only
        long caughtAt = c.getLong("CaughtAt");
        String when = caughtAt > 0 ? java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").format(
                java.time.LocalDateTime.ofInstant(java.time.Instant.ofEpochSecond(caughtAt), java.time.ZoneId.systemDefault()))
                : c.getString("Date");
        lines.add(new Object[]{new ItemStack(Items.FEATHER), Component.literal(c.getString("Angler") + " · " + when
                + " · " + key("day", c.getLong("Day")).getString()), CREAM});
        // §nature: the counter buys PRIME fish only — said in emeralds, and not at all for the rest
        if (FishItem.isPrime(fish) && c.getInt("Value") > 0) {
            lines.add(new Object[]{new ItemStack(Items.EMERALD), key("at_fisherman", c.getInt("Value")), GREEN});
        }

        shift = Screen.hasShiftDown();
        if (shift) {
            fact("time", c, "time.riverfishing.");
            fact("season", c, "season.riverfishing.");
            fact("weather", c, "weather.riverfishing.");
            if (!c.getString("Bed").isEmpty()) {
                String bed = Component.translatable("bed.riverfishing." + c.getString("Bed")).getString();
                if (!c.getString("Spot").isEmpty()) bed += ", " + key("spot." + c.getString("Spot")).getString();
                facts.add(new Component[]{key("bed"), Component.literal(bed)});
            }
            if (c.getBoolean("Ice")) facts.add(new Component[]{key("ice"), key("yes")});
            if (!c.getString("Group").isEmpty()) {
                String grp = Component.translatable(com.riverfishing.fish.FishGroup.nameKey(c.getString("Group"))).getString();
                if (!c.getString("Life").isEmpty()) grp += " · " + key("life." + c.getString("Life")).getString().toLowerCase(Locale.ROOT);
                facts.add(new Component[]{key("group"), Component.literal(grp)});
            }
            if (!c.getString("Eco").isEmpty()) facts.add(new Component[]{key("ecosystem"), key("eco." + c.getString("Eco"))});
            if (!c.getString("Hybrid").isEmpty()) {   // §hybrid-rare: the cross, by its parents' names
                StringBuilder parents = new StringBuilder();
                for (String id : c.getString("Hybrid").split(",")) {
                    if (parents.length() > 0) parents.append(" × ");
                    parents.append(Component.translatable("fish.riverfishing." + id).getString());
                }
                facts.add(new Component[]{key("hybrid"), Component.literal(parents.toString())});
            }
            // §pattern: the index this fish came out at, the family it belongs to — or, one fish in 83, the gem
            int pattern = CatchCard.pattern(fish);
            if (com.riverfishing.fish.Pattern.has(pattern)) {
                boolean gem = com.riverfishing.fish.Pattern.isGem(pattern);
                facts.add(new Component[]{key("pattern"), Component.literal("#" + pattern + " ").append(Component.translatable(gem
                        ? "gem.riverfishing." + com.riverfishing.fish.Pattern.gemName(pattern)
                        : "pattern.riverfishing." + com.riverfishing.fish.Pattern.family(pattern)))});
            }
            facts.add(new Component[]{key("genes"), Component.literal(c.getString("Genes"))});
        }
        return legend ? 0xFF9A5AFF : !morph.isEmpty() ? 0xFFE040D0 : trophy ? 0xFFE8C050 : 0xFF5AA080;
    }

    private void fact(String label, CompoundTag c, String prefix) {
        String v = c.getString(label.substring(0, 1).toUpperCase(Locale.ROOT) + label.substring(1));
        if (!v.isEmpty()) facts.add(new Component[]{key(label), Component.translatable(prefix + v)});
    }

    /** §fly-card: "tied:<pattern>" is a fly tied at the bench; anything else is an item id. */
    private static Component baitName(String bait) {
        return bait.startsWith("tied:") ? Component.translatable("tied.riverfishing." + bait.substring(5))
                : Component.translatable("item.riverfishing." + bait);
    }

    // ================================================================ the contract

    private int contract(ItemStack paper) {
        CompoundTag t = ContractItem.tag(paper);
        Minecraft mc = Minecraft.getInstance();
        long day = mc.level == null ? 0 : mc.level.getDayTime() / 24000L;
        days = (int) (t.getLong("Exp") - day);
        expired = days < 0;
        stamps.add(expired ? new Object[]{key("badge.expired"), P_RED} : new Object[]{key("badge.contract"), P_BROWN});

        String sp = t.getString("Sp");
        need = t.getInt("N");
        fry = ContractItem.isFry(t);   // §e: no size bar and no terms on a fry order
        form.add(new Component[]{key("contract.fish"), Component.translatable("fish.riverfishing." + sp)});
        if (!fry) form.add(new Component[]{key("contract.from"), Component.literal(ContractItem.grams(t.getInt("W")))});
        // every term on its own line — the whole point of the order: nothing folded away
        if (!t.getString("Water").isEmpty()) form.add(new Component[]{key("water"), Component.translatable("water.riverfishing." + t.getString("Water"))});
        if (!t.getString("Rod").isEmpty()) form.add(new Component[]{key("rod"), key("rod." + t.getString("Rod"))});
        if (!t.getString("Bait").isEmpty()) form.add(new Component[]{key("bait"), baitName(t.getString("Bait"))});
        if (!t.getString("Time").isEmpty()) form.add(new Component[]{key("time"), Component.translatable("time.riverfishing." + t.getString("Time"))});
        have = mc.player == null ? 0
                : fry ? com.riverfishing.fishing.Contracts.fryHeld(mc.player.getInventory(), sp)   // §e
                : com.riverfishing.fishing.Contracts.held(mc.player.getInventory(), sp, t.getInt("W"), t).size();
        em = t.getInt("Em");
        xp = t.getInt("Xp");
        rep = t.getInt("Rep");
        return expired ? 0xFFC03030 : 0xFFC8A040;
    }

    // ================================================================ layout

    private static final int LATIN_H = 11, WEIGHT_H = 20, SCALE_H = 19, RULER_H = 12, TRAITS_H = 12, SEP_H = 5,
            LINE_H = 13, FACT_H = 10, FOOT_H = 11;
    private static final int FORM_H = 12, BOXES_MAX = 10;

    @Override
    public int getHeight() {
        if (paper) return 4 + form.size() * FORM_H + 4 + 12 + 14 + 12 + 2;
        int h = LATIN_H + WEIGHT_H + (wPct >= 0 ? SCALE_H : 0) + RULER_H + TRAITS_H + SEP_H;
        h += shift ? facts.size() * FACT_H : lines.size() * LINE_H + FOOT_H;
        return h + 2;
    }

    @Override
    public int getWidth(Font font) {
        FRAME = accent;                                   // layout runs before the background is painted
        STYLE = paper ? 1 : 0;
        if (width >= 0) return width;
        int w = MIN_W, stampW = stampsWidth(font);
        if (paper) {
            int lw = formLabelWidth(font);
            for (Component[] r : form) w = Math.max(w, lw + 8 + font.width(r[1]) + 4);
            w = Math.max(w, font.width(key("contract.bag")) + 8 + Math.min(need, BOXES_MAX) * 9 + 30);
            w = Math.max(w, stampW + 60);
        } else {
            w = Math.max(w, font.width(latin) + stampW + 8);
            w = Math.max(w, font.width(weight) * 2 + 4 + font.width(imperial) + stampW + 4);
            if (heavier != null) w = Math.max(w, font.width(heavier) + 8 + font.width(trophyFrom));
            w = Math.max(w, font.width(sex) + 3 + font.width(traits));
            for (Object[] l : lines) w = Math.max(w, 15 + font.width((Component) l[1]));
            int lw = 0;
            for (Component[] f : facts) lw = Math.max(lw, font.width(f[0]));
            for (Component[] f : facts) w = Math.max(w, lw + 6 + font.width(f[1]));
            w = Math.max(w, font.width(key("shift_more")));
        }
        return width = w + 2;
    }

    private int stampsWidth(Font font) {
        int w = 0;
        for (Object[] s : stamps) w = Math.max(w, font.width((Component) s[0]) + 12);
        return w;
    }

    private int formLabelWidth(Font font) {
        int lw = 0;
        for (Component[] r : form) lw = Math.max(lw, font.width(r[0]));
        return Math.max(lw, Math.max(font.width(key("contract.bag")), font.width(key("contract.days"))));
    }

    // ================================================================ drawing

    @Override
    public void renderImage(Font font, int x, int y, GuiGraphics g) {
        int w = getWidth(font);
        FRAME = 0;
        if (paper) drawPaper(font, x, y, w, g);
        else drawFish(font, x, y, w, g);
        // the stamps last, over the top right — as if the angler's book had been stamped. §stamp-straight: level, on
        // whole pixels: a tilt resampled the pixel font and the letters ran into each other (Besoulq, 26.3 beta)
        for (int i = 0; i < stamps.size(); i++) {
            Component word = (Component) stamps.get(i)[0];
            int ink = (int) stamps.get(i)[1], sw = font.width(word) + 10;
            g.pose().pushPose();
            g.pose().translate(x + w - sw / 2 - 2, y + 6 + i * 15, 400);
            box(g, -sw / 2, -6, sw / 2, 7, ink);
            box(g, -sw / 2 + 2, -4, sw / 2 - 2, 5, (ink & 0x00FFFFFF) | 0x99000000);
            g.drawString(font, word, -sw / 2 + 5, -3, ink, false);
            g.pose().popPose();
        }
    }

    private static void box(GuiGraphics g, int x0, int y0, int x1, int y1, int ink) {
        g.fill(x0, y0, x1, y0 + 1, ink);
        g.fill(x0, y1 - 1, x1, y1, ink);
        g.fill(x0, y0, x0 + 1, y1, ink);
        g.fill(x1 - 1, y0, x1, y1, ink);
    }

    private static void icon(GuiGraphics g, ItemStack stack, int x, int y) {
        if (stack.isEmpty()) return;
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(0.75f, 0.75f, 1f);
        g.renderItem(stack, 0, 0);
        g.pose().popPose();
    }

    private void drawFish(Font font, int x, int y, int w, GuiGraphics g) {
        int cy = y;
        g.drawString(font, latin, x, cy, DIM, false);
        cy += LATIN_H;
        // the weight, big — the number the angler came for
        g.pose().pushPose();
        g.pose().translate(x, cy, 0);
        g.pose().scale(2f, 2f, 1f);
        g.drawString(font, weight, 0, 0, CREAM, true);
        g.pose().popPose();
        g.drawString(font, imperial, x + font.width(weight) * 2 + 4, cy + 7, DIM, false);
        cy += WEIGHT_H;
        if (wPct >= 0) {
            // §weight-scale: the bar is the species' own catches, lightest to heaviest by how often they come —
            // so the marker's place IS "heavier than N %", and the gold tick is where a trophy starts
            int bw = w - 2;
            g.fill(x, cy, x + bw, cy + 5, 0xFF06101A);
            int fill = (bw - 2) * Math.min(1000, wPct) / 1000;
            for (int i = 0; i < fill; i++) {
                float t = i / (float) Math.max(1, bw - 2);
                int r = (int) (0x3A + 0x90 * t), gr = (int) (0x9A - 0x10 * t), b = (int) (0x8A - 0x50 * t);
                g.fill(x + 1 + i, cy + 1, x + 2 + i, cy + 4, 0xFF000000 | r << 16 | gr << 8 | b);
            }
            int tx = x + 1 + (bw - 2) * Math.min(1000, tPct) / 1000;
            g.fill(tx, cy - 2, tx + 1, cy + 7, GOLD);
            int mx = x + 1 + fill;
            g.fill(mx - 1, cy - 1, mx + 2, cy + 6, CREAM);
            cy += 7;
            g.drawString(font, heavier, x, cy, SOFT, false);
            g.drawString(font, trophyFrom, x + bw - font.width(trophyFrom), cy, 0xFFC8A860, false);
            cy += SCALE_H - 7;
        }
        // the measuring board: the fish laid on it nose to the stop, a notch at its tail
        int rw = w - 2 - font.width(lengthText) - 6;
        g.fill(x, cy, x + rw, cy + 7, 0xFFC8A870);
        g.fill(x, cy + 6, x + rw, cy + 7, 0xFF8A6A40);
        int step = lengthMax <= 60 ? 5 : lengthMax <= 200 ? 10 : 50;
        for (int cm = 0; cm <= lengthMax; cm += step) {
            int px = x + (rw - 1) * cm / Math.max(1, lengthMax);
            g.fill(px, cy, px + 1, cy + (cm % (step * 2) == 0 ? 4 : 2), 0xFF5A4020);
        }
        int lx = x + (rw - 1) * Math.min(lengthCm, lengthMax) / Math.max(1, lengthMax);
        g.fill(x, cy + 1, lx, cy + 2, 0xFF2E6A80);
        g.fill(lx, cy - 2, lx + 1, cy + 8, 0xFFC03020);
        g.drawString(font, lengthText, x + w - 2 - font.width(lengthText), cy, CREAM, true);
        cy += RULER_H;
        g.drawString(font, sex, x, cy, CREAM, false);
        g.drawString(font, traits, x + font.width(sex) + 3, cy, SOFT, false);
        cy += TRAITS_H;
        for (int dx = 0; dx < w - 2; dx += 3) g.fill(x + dx, cy + 1, x + dx + 1, cy + 2, (accent & 0x00FFFFFF) | 0x78000000);
        cy += SEP_H;
        if (shift) {
            int lw = 0;
            for (Component[] f : facts) lw = Math.max(lw, font.width(f[0]));
            for (Component[] f : facts) {
                g.drawString(font, f[0], x, cy, DIM, false);
                g.drawString(font, f[1], x + lw + 6, cy, CREAM, false);
                cy += FACT_H;
            }
            return;
        }
        for (Object[] l : lines) {
            icon(g, (ItemStack) l[0], x, cy);
            g.drawString(font, (Component) l[1], x + 15, cy + 2, (int) l[2], false);
            cy += LINE_H;
        }
        g.drawString(font, key("shift_more"), x, cy + 1, FAINT, false);
    }

    private void drawPaper(Font font, int x, int y, int w, GuiGraphics g) {
        int lw = formLabelWidth(font), vx = x + lw + 8, cy = y + 4;
        // the form: printed labels, the order written in ink on dotted lines
        for (Component[] r : form) {
            g.drawString(font, r[0], x, cy, P_LABEL, false);
            g.drawString(font, r[1], vx, cy, P_INK, false);
            for (int dx = vx; dx < x + w - 2; dx += 2) g.fill(dx, cy + 9, dx + 1, cy + 10, P_LINE);
            cy += FORM_H;
        }
        cy += 4;
        // the bag: a box a fish, ticked for each one already in it
        g.drawString(font, key(fry ? "contract.bag_fry" : "contract.bag"), x, cy, P_LABEL, false);
        if (need <= BOXES_MAX) {
            for (int i = 0; i < need; i++) {
                int bx = vx + i * 9;
                box(g, bx, cy, bx + 7, cy + 7, P_INK);
                if (i < have) {
                    g.fill(bx + 2, cy + 3, bx + 3, cy + 5, P_GREEN);
                    g.fill(bx + 3, cy + 4, bx + 4, cy + 6, P_GREEN);
                    g.fill(bx + 4, cy + 1, bx + 5, cy + 5, P_GREEN);
                }
            }
            g.drawString(font, have + "/" + need, vx + need * 9 + 3, cy, have >= need ? P_GREEN : P_INK, false);
        } else {
            g.drawString(font, have + " / " + need, vx, cy, have >= need ? P_GREEN : P_INK, false);
        }
        cy += 12;
        // the pay, as what it is
        g.drawString(font, key("contract.pays"), x, cy + 3, P_LABEL, false);
        int px = vx;
        icon(g, new ItemStack(Items.EMERALD), px, cy);
        g.drawString(font, String.valueOf(em), px + 14, cy + 3, P_INK, false);
        px += 14 + font.width(String.valueOf(em)) + 8;
        icon(g, new ItemStack(Items.EXPERIENCE_BOTTLE), px, cy);
        g.drawString(font, String.valueOf(xp), px + 14, cy + 3, P_INK, false);
        px += 14 + font.width(String.valueOf(xp)) + 8;
        g.drawString(font, key("contract.rep", rep), px, cy + 3, P_INK, false);
        cy += 14;
        g.drawString(font, key("contract.days"), x, cy, P_LABEL, false);
        g.drawString(font, expired ? key("contract.expired") : Component.literal(String.valueOf(days)), vx, cy,
                expired || days <= 1 ? P_RED : P_INK, false);
    }
}
