package com.riverfishing.client;

import com.riverfishing.RiverFishing;
import com.riverfishing.engine.Calendar;
import com.riverfishing.engine.Season;
import com.riverfishing.fish.CatchCard;
import com.riverfishing.menu.AquariumMenu;
import com.riverfishing.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * §aquarium-window (0.9.0), §aquarium-cabinet (1.1.0): the tank's window, drawn as a cabinet — tank glass
 * for the water, the fish and the modules, the breeding run as five nodes in a groove with the result cup at
 * its end, the feeding under it, and the season in words. Everything it says comes from the synced ints of
 * the contract (docs/design/breeding-api.md, Layer 4) and the slots' own stacks — the screen never looks at
 * the block entity, so it reads the same on a server as in singleplayer.
 *
 * <p>The fish wear a ring for their sex, pink or blue, thin while they are young; the pair the tank will
 * breed is joined by a line that runs in the gutters between the slots, never across one. When the roe is
 * laid the fish are asked out, and once they are, the fish slots step aside for the incubator.
 */
public class AquariumScreen extends AbstractContainerScreen<AquariumMenu> {
    private static final Identifier BG = RiverFishing.id("textures/gui/aquarium.png");
    private static final int TEX_W = 256, TEX_H = 256;
    private static final String K = "screen.riverfishing.aquarium.";
    /**
     * Ordinals of {@code AquariumBreeding.Status} (stream B), spelled out because that class is
     * package-private to the block package; the contract fixes this order.
     */
    private static final String[] STATUS = { "empty", "no_pair", "not_mature", "out_of_season", "hungry",
            "bad_water", "spawning", "roe_ready", "incubating", "fry_ready", "busy" };
    private static final int EMPTY = 0, NO_PAIR = 1, NOT_MATURE = 2, OUT_OF_SEASON = 3, HUNGRY = 4, BAD_WATER = 5,
            SPAWNING = 6, ROE_READY = 7, INCUBATING = 8, FRY_READY = 9, BUSY = 10;
    private static final String[] SLOT_LABEL = { "fish", "fish", "fish", "fish", "fish", "fish",
            "food", "groundbait", "water", "result", "modules", "modules" };
    private static final String[] STAGES = { "pair", "spawning", "roe", "incubation", "fry" };

    // Geometry shared with tools/gen_aquarium_gui.py — change both or the drawings drift off the board.
    private static final int TUBE_X0 = 38, TUBE_Y0 = 20, TUBE_X1 = 45, TUBE_Y1 = 66;
    private static final int NODE_X0 = 26, NODE_STEP = 38, NODE_Y = 92;
    private static final int BAR_X0 = 58, BAR_Y0 = 108, BAR_X1 = 190, BAR_Y1 = 118;
    private static final int CAPTION_Y = 73, STATUS_Y = 124;
    /** Where the caption row ends on the right: short of the result cup. */
    private static final int CAPTION_RIGHT = 193;
    /** The incubator's panel, over where the fish slots are. */
    private static final int INC_X0 = 58, INC_Y0 = 19, INC_X1 = 166, INC_Y1 = 68;
    /** Off-panel sprites, {u, v, w, h}. */
    private static final int[] NODE_OFF = {232, 0, 15, 15}, NODE_DONE = {232, 15, 15, 15}, NODE_CUR = {232, 30, 15, 15},
            GLOW = {232, 45, 21, 21}, HEART_ON = {232, 66, 7, 6}, HEART_OFF = {240, 66, 7, 6},
            GLASS_ON = {248, 66, 5, 5}, GLASS_OFF = {248, 72, 5, 5}, BADGE = {232, 74, 7, 8};

    private static final int CREAM = 0xFFECE2CC, DIM = 0xFFAAC4C0, WARN = 0xFFF0AA50, OK = 0xFF82DC82,
            PINK = 0xFFF080B8, BLUE = 0xFF60AAF0, GOLD = 0xFFECC860, GREY = 0xC8969696, INK = 0xFF3A2812;

    public AquariumScreen(AquariumMenu menu, Inventory inv, Component title) {
        // §26.1: imageWidth/imageHeight are final now — the size goes through the super ctor.
        super(menu, inv, title, 232, 240);   // §aquarium-fit: the least height the auto GUI scale promises
        this.titleLabelY = -1000;      // the title is engraved on the nameplate
        this.inventoryLabelY = -1000;  // the drawer needs no word for itself
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float pt) {
        super.extractRenderState(g, mouseX, mouseY, pt);
        youngDots(g);
        tooltips(g, mouseX, mouseY);
    }

    private void sprite(GuiGraphicsExtractor g, int[] s, int x, int y) {
        g.blit(RenderPipelines.GUI_TEXTURED, BG, x, y, s[0], s[1], s[2], s[3], TEX_W, TEX_H);
    }

    private int status() {
        return Math.floorMod(menu.data(0), STATUS.length);
    }

    /** The run's node for this status: 0 pair, 1 spawning, 2 roe, 3 incubation, 4 fry; -1 = nothing on it. */
    private int stage(int status) {
        return switch (status) {
            case NO_PAIR, NOT_MATURE, OUT_OF_SEASON, HUNGRY -> 0;
            case BAD_WATER -> menu.cupHolds() ? 3 : 0;   // spawning waits below 50, incubation pauses below 25
            case SPAWNING -> 1;
            case ROE_READY -> 2;
            case INCUBATING, BUSY -> 3;
            case FRY_READY -> 4;
            default -> -1;
        };
    }

    private static boolean blocked(int status) {
        return status == NO_PAIR || status == NOT_MATURE || status == OUT_OF_SEASON || status == HUNGRY
                || status == BAD_WATER || status == BUSY;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float pt) {
        super.extractBackground(g, mouseX, mouseY, pt);
        int x = leftPos, y = topPos;
        g.blit(RenderPipelines.GUI_TEXTURED, BG, x, y, 0f, 0f, imageWidth, imageHeight, TEX_W, TEX_H);
        int status = status();
        String name = this.title.getString();
        g.text(font, name, x + 116 - font.width(name) / 2, y + 5, INK, false);

        drawWater(g, x, y);
        ghost(g, AquariumMenu.WATER, new ItemStack(Items.WATER_BUCKET), 0xB00A1E24);
        ghost(g, AquariumMenu.FOOD, new ItemStack(ModItems.WORM.get()), 0xB02A1C12);
        ghost(g, AquariumMenu.GROUNDBAIT, new ItemStack(ModItems.GROUNDBAIT.get()), 0xB02A1C12);
        ghost(g, AquariumMenu.RESULT, new ItemStack(ModItems.ROE.get()), 0xB02A1C12);
        ghost(g, AquariumMenu.MODULE_FIRST, new ItemStack(com.riverfishing.registry.ModBlocks.AERATOR.get()), 0xB00A1E24);
        ghost(g, AquariumMenu.MODULE_LAST, new ItemStack(com.riverfishing.registry.ModBlocks.SNAG_PILE.get()), 0xB00A1E24);
        g.text(font, I18n.get(K + "label.modules"), x + 172, y + 18, DIM, false);

        if (menu.incubatorMode()) drawIncubator(g, x, y, status);
        else drawFish(g, x, y, status);
        drawRun(g, x, y, status);
        drawFeed(g, x, y);
        drawWords(g, x, y, status);
    }

    /** An empty slot shows what goes in it, faint — the window's labels, without the words. */
    private void ghost(GuiGraphicsExtractor g, int slot, ItemStack what, int veil) {
        if (!menu.tankItem(slot).isEmpty()) return;
        int sx = leftPos + AquariumMenu.SLOT_XY[slot][0], sy = topPos + AquariumMenu.SLOT_XY[slot][1];
        g.fakeItem(what, sx, sy);
        // §26.1: RenderType.guiGhostRecipeOverlay() is gone; vanilla's GhostSlots dims with a plain fill too
        g.fill(sx, sy, sx + 16, sy + 16, veil);
    }

    /** The water: the tube fills and clouds with it, the reading under the bucket. */
    private void drawWater(GuiGraphicsExtractor g, int x, int y) {
        int q = Math.max(0, Math.min(100, menu.data(5)));
        int h = (TUBE_Y1 - TUBE_Y0 - 2) * q / 100;
        int col = q > 60 ? 0xFF46AADC : q > 30 ? 0xFF78AA5A : 0xFF826034;
        g.fill(x + TUBE_X0 + 1, y + TUBE_Y1 - 1 - h, x + TUBE_X1 - 1, y + TUBE_Y1 - 1, col);
        g.fill(x + TUBE_X0 + 1, y + TUBE_Y1 - 1 - h, x + TUBE_X0 + 2, y + TUBE_Y1 - 1, 0x46FFFFFF);
        String pct = q + "%";
        int wx = AquariumMenu.SLOT_XY[AquariumMenu.WATER][0] - 1;
        g.text(font, pct, x + wx + (18 - font.width(pct)) / 2, y + 45, q < 50 ? WARN : CREAM, false);
    }

    /** The fish in their sex rings, the hand-off when the roe is laid, and the pair's line. */
    private void drawFish(GuiGraphicsExtractor g, int x, int y, int status) {
        boolean handOff = status == ROE_READY;
        for (int i = AquariumMenu.FISH_FIRST; i <= AquariumMenu.FISH_LAST; i++) {
            ItemStack f = menu.tankItem(i);
            if (f.isEmpty()) continue;
            int wx = x + AquariumMenu.SLOT_XY[i][0] - 1, wy = y + AquariumMenu.SLOT_XY[i][1] - 1;
            if (CatchCard.has(f)) {
                boolean female = CatchCard.of(f).getByteOr("Sex", (byte) 0) == 0;
                boolean adult = CatchCard.of(f).getByteOr("Size", (byte) 0) >= 2;
                ring(g, wx, wy, adult ? 2 : 1, adult ? (female ? PINK : BLUE) : ((female ? PINK : BLUE) & 0x00FFFFFF | 0x96000000));
            }
            if (handOff) ring(g, wx - 2, wy - 2, 1, WARN, 22);
        }
        if (handOff) {   // every fish has to come out now — an arrow says where to
            g.text(font, "↓", x + AquariumMenu.SLOT_XY[2][0] + 20, y + AquariumMenu.SLOT_XY[2][1] + 9, WARN, true);
            g.text(font, "↓", x + AquariumMenu.SLOT_XY[5][0] + 20, y + AquariumMenu.SLOT_XY[5][1] + 3, WARN, true);
            return;
        }
        int pair = menu.data(11), a = (pair & 15) - 1, b = (pair >> 4 & 15) - 1;
        if (a < 0 || b < 0 || sex(a) != 0 || sex(b) != 1) return;
        pairLine(g, x, y, a, b, status == NOT_MATURE ? GREY : GOLD);
    }

    /** The sex on the card of the fish in tank slot {@code i}: 0 female, 1 male, -1 none there. */
    private int sex(int i) {
        ItemStack f = menu.tankItem(i);
        if (f.isEmpty() || !CatchCard.has(f)) return -1;
        return CatchCard.of(f).getByteOr("Sex", (byte) 0);
    }

    private static void ring(GuiGraphicsExtractor g, int x, int y, int thick, int colour) {
        ring(g, x, y, thick, colour, 18);
    }

    private static void ring(GuiGraphicsExtractor g, int x, int y, int thick, int colour, int size) {
        for (int t = 0; t < thick; t++) {
            g.fill(x + t, y + t, x + size - t, y + t + 1, colour);
            g.fill(x + t, y + size - 1 - t, x + size - t, y + size - t, colour);
            g.fill(x + t, y + t, x + t + 1, y + size - t, colour);
            g.fill(x + size - 1 - t, y + t, x + size - t, y + size - t, colour);
        }
    }

    /**
     * §pair-line: from one fish to the other through the gutters, never across a slot. Two in a row: out of
     * the top of both and along the gutter above that row. Two rows apart (a diagonal too): out of the
     * bottom of the upper one, along the gutter between the rows, into the top of the lower one. A heart
     * sits in the middle of the run.
     */
    private void pairLine(GuiGraphicsExtractor g, int x, int y, int a, int b, int colour) {
        int[] pa = AquariumMenu.SLOT_XY[a], pb = AquariumMenu.SLOT_XY[b];
        int[] top = pa[1] <= pb[1] ? pa : pb, low = pa[1] <= pb[1] ? pb : pa;
        int tx = x + top[0] + 8, lx = x + low[0] + 8;
        int y0, y1, gy;
        if (top[1] == low[1]) {
            gy = y + top[1] - 5;
            y0 = y + top[1] - 1;
            y1 = y0;
        } else {
            gy = y + top[1] + 20;
            y0 = y + top[1] + 17;
            y1 = y + low[1] - 1;
        }
        g.fill(tx - 1, Math.min(y0, gy), tx + 1, Math.max(y0, gy) + 1, colour);
        g.fill(Math.min(tx, lx) - 1, gy - 1, Math.max(tx, lx) + 1, gy + 1, colour);
        g.fill(lx - 1, Math.min(y1, gy), lx + 1, Math.max(y1, gy) + 1, colour);
        sprite(g, colour == GOLD ? HEART_ON : HEART_OFF, (tx + lx) / 2 - 3, gy - 3);
    }

    /** The incubator: the roe (or the fry) big, the day, what will hatch — and that no fish goes in. */
    private void drawIncubator(GuiGraphicsExtractor g, int x, int y, int status) {
        g.fill(x + INC_X0, y + INC_Y0, x + INC_X1, y + INC_Y1, 0xF50C2228);
        g.fill(x + INC_X0, y + INC_Y0, x + INC_X1, y + INC_Y0 + 1, 0x8C78C8BE);
        g.fill(x + INC_X0, y + INC_Y1 - 1, x + INC_X1, y + INC_Y1, 0xFF040E12);
        ItemStack cup = menu.tankItem(AquariumMenu.RESULT);
        g.pose().pushMatrix();
        g.pose().translate(x + INC_X0 + 4, y + INC_Y0 + 6);
        g.pose().scale(1.5f, 1.5f);
        g.item(cup, 0, 0);
        g.pose().popMatrix();
        int tx = x + INC_X0 + 32, ty = y + INC_Y0 + 5;
        if (status == FRY_READY) {
            g.text(font, I18n.get(K + "fry_title"), tx, ty, CREAM, true);
            g.text(font, plural("fry", com.riverfishing.item.FryItem.count(cup)), tx, ty + 12, 0xFFC8E6DC, true);
            g.text(font, I18n.get(K + "take"), tx, ty + 24, WARN, true);
        } else {
            int total = Math.max(1, menu.data(3)), day = Math.min(total, menu.data(2) + 1);
            g.text(font, I18n.get(K + "incubator"), tx, ty, CREAM, true);
            g.text(font, I18n.get(K + "inc_day", day, total), tx, ty + 12, 0xFFC8E6DC, true);
            if (menu.data(12) > 0) g.text(font, "≈ " + plural("fry", menu.data(12)), tx, ty + 24, OK, true);
        }
        g.text(font, I18n.get(K + "no_fish"), x + INC_X0 + 4, y + INC_Y1 - 12, DIM, true);
    }

    /** The run: nodes done in brass, the current one lit with its progress, a red badge where it is stuck. */
    private void drawRun(GuiGraphicsExtractor g, int x, int y, int status) {
        int stage = stage(status);
        float progress = status == SPAWNING ? menu.data(1) / 3f
                : status == INCUBATING ? menu.data(2) / (float) Math.max(1, menu.data(3)) : 0f;
        ItemStack[] icons = {ItemStack.EMPTY, firstFish(), new ItemStack(ModItems.ROE.get()), ItemStack.EMPTY,
                new ItemStack(ModItems.FRY.get())};
        for (int i = 0; i < 5; i++) {
            int nx = x + NODE_X0 + i * NODE_STEP, ny = y + NODE_Y;
            boolean done = i < stage, cur = i == stage;
            if (i < 4 && i != 2) {   // the groove between roe and incubation stays dotted: the hand-off
                int gx0 = nx + 8, gx1 = x + NODE_X0 + (i + 1) * NODE_STEP - 7;
                if (done) g.fill(gx0, ny - 1, gx1, ny + 1, 0xFFD6AC54);
                else if (cur && progress > 0) g.fill(gx0, ny - 1, gx0 + Math.round((gx1 - gx0) * Math.min(1f, progress)), ny + 1, 0xFFFAD678);
            }
            sprite(g, done ? NODE_DONE : cur ? NODE_CUR : NODE_OFF, nx - 7, ny - 7);
            if (cur) sprite(g, GLOW, nx - 10, ny - 10);
            if (i == 0) sprite(g, done || cur ? HEART_ON : HEART_OFF, nx - 3, ny - 3);
            else if (i == 3) sprite(g, done || cur ? GLASS_ON : GLASS_OFF, nx - 2, ny - 2);
            else {
                g.pose().pushMatrix();
                g.pose().translate(nx - 5, ny - 5);
                g.pose().scale(0.625f, 0.625f);
                g.item(icons[i], 0, 0);
                g.pose().popMatrix();
                if (!done && !cur) g.fill(nx - 5, ny - 5, nx + 5, ny + 5, 0x99422E1E);
            }
        }
        if (stage >= 0 && blocked(status)) sprite(g, BADGE, x + NODE_X0 + stage * NODE_STEP + 4, y + NODE_Y - 11);

        // the caption on the left, what it will come to on the right
        String caption = I18n.get(K + "caption." + switch (stage) {
            case 0 -> status == NO_PAIR ? "no_pair" : status == NOT_MATURE ? "young" : "pair";
            case 1 -> "spawning";
            case 2 -> "roe";
            case 3 -> "incubating";
            case 4 -> "fry";
            default -> "empty";
        });
        g.text(font, caption, x + 14, y + CAPTION_Y, CREAM, true);
        String right = "";
        ItemStack cup = menu.tankItem(AquariumMenu.RESULT);
        if (stage <= 1 && menu.data(9) > 0) right = "≈ " + plural("eggs", menu.data(9));
        else if (stage == 2) right = plural("eggs", com.riverfishing.item.RoeItem.count(cup));
        else if (stage == 3 && menu.data(12) > 0 && com.riverfishing.item.RoeItem.count(cup) > 0) {
            right = I18n.get(K + "survive", Math.round(100f * menu.data(12) / com.riverfishing.item.RoeItem.count(cup)));
        }
        if (!right.isEmpty()) g.text(font, right, x + CAPTION_RIGHT - font.width(right), y + CAPTION_Y, 0xFFDCC8A0, true);
    }

    /** The fish the spawning node shows: the tank's own, or a crucian for an empty tank. */
    private ItemStack firstFish() {
        for (int i = AquariumMenu.FISH_FIRST; i <= AquariumMenu.FISH_LAST; i++) {
            if (!menu.tankItem(i).isEmpty()) return menu.tankItem(i);
        }
        return new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(RiverFishing.id("crucian_carp")));
    }

    /** The feed bar: a day's worth full, and just the time left in it. */
    private void drawFeed(GuiGraphicsExtractor g, int x, int y) {
        boolean fish = menu.data(8) > 0;
        int secs = Math.max(0, menu.data(4));
        if (fish && secs > 0) {
            int w = BAR_X1 - BAR_X0 - 2;
            g.fill(x + BAR_X0 + 1, y + BAR_Y0 + 1, x + BAR_X0 + 1 + Math.round(w * Math.min(1f, secs / 1200f)), y + BAR_Y1 - 1, 0xFF5AAA6E);
        }
        String t = !fish ? "—" : secs >= 3600 ? String.format("%d:%02d:%02d", secs / 3600, secs / 60 % 60, secs % 60)
                : String.format("%d:%02d", secs / 60, secs % 60);
        g.text(font, t, x + (BAR_X0 + BAR_X1) / 2 - font.width(t) / 2, y + BAR_Y0 + 1,
                !fish ? DIM : secs == 0 ? WARN : CREAM, false);
    }

    /** What is wrong, in one line; then the season now and the one the fish spawn in. */
    private void drawWords(GuiGraphicsExtractor g, int x, int y, int status) {
        int colour = status == EMPTY ? DIM : blocked(status) || status == ROE_READY || status == FRY_READY ? WARN : OK;
        String line = font.plainSubstrByWidth(I18n.get(K + "status." + STATUS[status]), 204);
        g.text(font, line, x + 14, y + STATUS_Y, colour, true);
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        int ny = y + STATUS_Y + 12;
        String now = I18n.get(K + "now");
        g.text(font, now, x + 14, ny, DIM, true);
        g.text(font, Calendar.name(Calendar.season(level), Calendar.sub(level)), x + 14 + font.width(now), ny, CREAM, true);
        if (menu.data(6) < 0) return;   // no fish, no window to wait for
        Season season = Season.values()[Math.floorMod(menu.data(6), Season.values().length)];
        Calendar.Sub sub = menu.data(7) < 0 ? null : Calendar.Sub.values()[Math.floorMod(menu.data(7), Calendar.Sub.values().length)];
        int days = Calendar.daysUntil(level, season, sub);
        ny += 11;
        String spawn = I18n.get(K + "spawn");
        Component window = Calendar.name(season, sub);
        int nx = x + 14 + font.width(spawn);
        g.text(font, spawn, x + 14, ny, DIM, true);
        g.text(font, window, nx, ny, days == 0 ? OK : WARN, true);
        if (days > 0) g.text(font, " — " + plural("days", days), nx + font.width(window), ny, DIM, true);
    }

    /**
     * "142 икринки", "5 икринок", "1 икринка": Minecraft's lang files have no plural, so the three forms are
     * three keys and the rule picks one — Russian and Ukrainian by their endings, everything else one/many.
     */
    private static String plural(String key, int n) {
        String lang = Minecraft.getInstance().getLanguageManager().getSelected();
        String form;
        if (lang.startsWith("ru") || lang.startsWith("uk")) {
            int m10 = n % 10, m100 = n % 100;
            form = m10 == 1 && m100 != 11 ? "one" : m10 >= 2 && m10 <= 4 && (m100 < 12 || m100 > 14) ? "few" : "many";
        } else {
            form = n == 1 ? "one" : "many";
        }
        return I18n.get(K + key + "." + form, n);
    }

    /** A young fish's dot, over its item — a thin ring should not be the only thing that says so. */
    private void youngDots(GuiGraphicsExtractor g) {
        if (menu.incubatorMode()) return;
        g.nextStratum();   // over the items, which the slots drew in the stratum below
        for (int i = AquariumMenu.FISH_FIRST; i <= AquariumMenu.FISH_LAST; i++) {
            ItemStack f = menu.tankItem(i);
            if (f.isEmpty() || !CatchCard.has(f) || CatchCard.of(f).getByteOr("Size", (byte) 0) >= 2) continue;
            int sx = leftPos + AquariumMenu.SLOT_XY[i][0], sy = topPos + AquariumMenu.SLOT_XY[i][1];
            g.fill(sx + 13, sy + 13, sx + 15, sy + 15, 0xFFFAFAFA);
        }
    }

    /** An empty slot names itself; the tube, the nodes and the hand-off say what they are. */
    private void tooltips(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        // the MENU's index: Slot.index is the container's, and the inventory's first twelve have those too
        int hovered = hoveredSlot == null ? -1 : menu.slots.indexOf(hoveredSlot);
        if (hovered >= 0 && hovered < AquariumMenu.INV_START && !hoveredSlot.hasItem()) {
            g.setTooltipForNextFrame(font, Component.translatable(K + "label." + SLOT_LABEL[hovered]), mouseX, mouseY);
            return;
        }
        int mx = mouseX - leftPos, my = mouseY - topPos;
        if (mx >= TUBE_X0 && mx < TUBE_X1 && my >= TUBE_Y0 && my < TUBE_Y1) {
            g.setTooltipForNextFrame(font, Component.translatable(K + "water_tip", menu.data(5)), mouseX, mouseY);
            return;
        }
        for (int i = 0; i < 5; i++) {
            int nx = NODE_X0 + i * NODE_STEP;
            if (Math.abs(mx - nx) <= 7 && Math.abs(my - NODE_Y) <= 7) {
                g.setTooltipForNextFrame(font, Component.translatable(K + "stage." + STAGES[i]), mouseX, mouseY);
                return;
            }
        }
        int hx = NODE_X0 + 2 * NODE_STEP + NODE_STEP / 2;
        if (Math.abs(mx - hx) <= 6 && Math.abs(my - NODE_Y) <= 6) {
            g.setTooltipForNextFrame(font, Component.translatable(K + "stage.handoff"), mouseX, mouseY);
        }
    }
}
