package com.riverfishing.client;

import com.riverfishing.RiverFishing;
import com.riverfishing.component.RigType;
import com.riverfishing.fish.FishProfile;
import com.riverfishing.fish.FishProfileManager;
import com.riverfishing.fishing.JournalData;
import com.riverfishing.item.BaitItem;
import com.riverfishing.item.GroundbaitItem;
import com.riverfishing.item.LineItem;
import com.riverfishing.item.ReelItem;
import com.riverfishing.item.RigItem;
import com.riverfishing.item.RodItem;
import com.riverfishing.quest.Quests;
import com.riverfishing.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import dev.architectury.registry.registries.RegistrySupplier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Bestiary journal (§15) + angler's guide (§guidebook). Three tabs: FISH (species grid → a page with a
 * framed illustration + "how to catch"), BAITS (natural baits / lures / groundbaits, sectioned, → what each
 * pulls in), and GEAR (rods / reels / lines / rigs). Bait & gear pages also show "how to get" — the crafting
 * recipe when one exists, else a generic hint. Long lists (gear) scroll. Everything is built live from the
 * same {@link FishProfile}s and recipes the game uses, so the guide can't drift from the balance.
 */
public class JournalScreen extends Screen {
    private static final String[] SPECIES = ModItems.FISH_SPECIES;
    private static final int ROW_H = 16;
    private static final int GRID_TOP = 54;
    // §journal-room (0.8.0): the page carries four more blocks than it did, and the list grew a family
    // column and a search field. Both need width. The shrink-to-fit below is what makes this safe: the
    // panel is a MAXIMUM, and a small screen scales the whole thing down rather than clipping it.
    private static final int H = 420;
    private static final int MAX_W = 470;
    /** §fish-list: one row per species, wide enough to carry the catch count and the personal best. */
    private static final int LIST_ROW = 15;
    private static final int FAM_W = 116;
    private static final int LIST_TOP = 76;
    // Panel width adapts to the screen (GUI scale) so it never clips off-screen; columns + illustration follow.
    private int W = MAX_W;
    private int COL_W = MAX_W - FAM_W - 24;
    /**
     * §journal-columns: the bait/gear catalog lays items out in a grid and the species list does not, so
     * they stopped sharing a width the moment the list grew a family rail. One name, one meaning.
     */
    private static final int CAT_COLS = 2;
    private int catColW = (MAX_W - 20) / CAT_COLS;
    private int ILLUS_W = 240;
    private int ILLUS_H = 160;

    private static final int TAB_FISH = 0;
    private static final int TAB_BAIT = 1;
    private static final int TAB_LURE = 2;
    private static final int TAB_GEAR = 3;
    private static final int TAB_QUEST = 4;
    private static final int TAB_SKILL = 5;
    private static final int TAB_RECORD = 6;
    private static final int TAB_GUIDE = 7;
    /** §discord: same invite as the mod metadata and the wiki — one place for the community. */
    private static final String DISCORD_URL = "https://discord.gg/Kk2nKvsuRh";
    private static final String[] TAB_KEYS = {
            "journal.riverfishing.tab_fish", "journal.riverfishing.tab_bait",
            "journal.riverfishing.tab_lure", "journal.riverfishing.tab_gear",
            "journal.riverfishing.tab_quest", "journal.riverfishing.tab_skill",
            "journal.riverfishing.tab_record", "journal.riverfishing.tab_guide"};

    /**
     * §gb-pantry (0.8.0): GB_PART is the shelf of things that only ever go INTO a mix — the ballast and
     * the vanilla crops. They are not hook baits, so nothing in the journal listed them, and after the
     * groundbait rework they are half of what the tab is about.
     */
    private enum Kind { NATURAL, LURE, GROUNDBAIT, GB_PART, ROD, REEL, LINE, RIG, GUIDE }

    private final List<Cat> guideCat = new ArrayList<>();

    /** §guide (0.5.0): a how-to entry — an icon carrying the guide title, text from guide.riverfishing.<id>. */
    /**
     * §guide-order (0.8.0): which progression group a page sits under. The groups follow the mod's OWN
     * quest stages, so the words a player reads on the quest tab and on the guide shelf are the same
     * words — an order invented here would have been a second opinion about the same journey.
     */
    private final java.util.Map<String, Integer> guideGroup = new java.util.HashMap<>();
    private int guideGroupNow;

    private void addGuide(String id, ItemStack icon) {
        guideGroup.put(id, guideGroupNow);
        icon.setHoverName(Component.translatable("guide.riverfishing." + id + ".title")
                .withStyle(s -> s.withItalic(false)));
        guideCat.add(new Cat(icon, Kind.GUIDE, id));
    }

    private record Cat(ItemStack stack, Kind kind, String id) {}

    /** The catalog the current tab shows. One place decides, so the render and the click cannot differ. */
    private List<Cat> tabList() {
        return switch (tab) {
            case TAB_BAIT -> baitCat;
            case TAB_LURE -> lureCat;
            case TAB_GUIDE -> guideCat;
            default -> gearCatalog;
        };
    }

    /** A pantry id → its item. Bare ids are this mod's; the rest name their namespace, as vanilla's do. */
    // ---- GEAR: four different things, four sets of columns ----

    /** Which category rail row the gear tab is on: 0 rods, 1 reels, 2 lines, 3 rigs. */
    private int gearCat;
    private static final Kind[] GEAR_KINDS = {Kind.ROD, Kind.REEL, Kind.LINE, Kind.RIG};

    /**
     * §gear-sort-head: which column the shelf is sorted by, or -1 for the catalogue's own order.
     *
     * <p>-1 rather than the bait table's 0, because there the name column IS the natural order and here
     * it is not: gear is ordered by tier and size on purpose (§gear-sort), so "sorted by name" has to be
     * a state you can ask for and then leave.
     */
    private int gearSort = -1;
    private boolean gearSortDesc = true;
    private final int[] gearColX = new int[5];
    private final int[] gearColW = new int[5];
    private int gearColCount;

    /**
     * The number a cell sorts by: its leading figure, or NaN when it has none.
     *
     * <p>Cells are the strings the table prints, and some of them are ranges ("10–40", "4–6k") or words
     * ("нет"). Sorting a range by where it starts is what a reader means by sorting it, and a word has no
     * number at all, so it sinks — the same rule the bait table already uses for its blanks.
     */
    private static double cellKey(String cell) {
        int i = 0, n = cell.length();
        while (i < n && (cell.charAt(i) == '-' || cell.charAt(i) == '+')) i++;
        int start = i;
        while (i < n && (Character.isDigit(cell.charAt(i)) || cell.charAt(i) == '.' || cell.charAt(i) == ',')) i++;
        if (i == start) return Double.NaN;
        try {
            return Double.parseDouble(cell.substring(0, i).replace(',', '.'));
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    /**
     * §gear-table (0.8.0): the gear shelf as four tables, not one.
     *
     * <p>A rod has a test window, a reel has a drag, a line has a diameter and a rig has hooks. Putting
     * all four in one table would print a dash in three cells of every row — the same mistake the lure
     * page made with its Colour column, and it is worse here because there are four kinds, not one.
     * So the categories get a rail, exactly like the fish tab's families, and each one gets the columns
     * that mean something for it.
     *
     * <p>Everything is read off the item's own type — RodType, ReelItem, LineItem, RigType — so the table
     * cannot disagree with the tackle it describes.
     */
    private void renderGearTable(GuiGraphics g, int mouseX, int mouseY) {
        int railW = 96;
        int x = left + 10, railX = left + 10, tableX = railX + railW + 8;
        int wAll = W - 28 - railW - 8;

        g.drawString(this.font, Component.translatable("journal.riverfishing.gt_hint"),
                x, top + 22, GuiStyle.TEXT_HINT, false);

        int y0 = top + 40;
        g.fill(railX - 2, y0 - 2, railX + railW + 2, y0 + 4 * LIST_ROW + 2, 0x22000000);
        for (int i = 0; i < GEAR_KINDS.length; i++) {
            int ry = y0 + i * LIST_ROW;
            boolean hov = mouseX >= railX && mouseX < railX + railW && mouseY >= ry && mouseY < ry + LIST_ROW;
            if (i == gearCat) {
                g.fill(railX, ry, railX + railW, ry + LIST_ROW, 0x55B08D3C);
            } else if (hov) {
                g.fill(railX, ry, railX + railW, ry + LIST_ROW, 0x22000000);
            }
            g.drawString(this.font, Component.translatable(sectionKey(GEAR_KINDS[i])), railX + 4, ry + 4,
                    i == gearCat ? GuiStyle.TEXT : GuiStyle.TEXT_HINT, false);
        }

        Kind kind = GEAR_KINDS[gearCat];
        String[] heads = switch (kind) {
            case ROD -> new String[]{"journal.riverfishing.gt_item", "journal.riverfishing.gt_test",
                    "journal.riverfishing.gt_reel", "journal.riverfishing.gt_range"};
            // §gear-width: no column may restate the name. "Катушка 4000" does not need a Size column
            // and "Монолеска" does not need a Type one — they cost width the name was starving for.
            case REEL -> new String[]{"journal.riverfishing.gt_item",
                    "journal.riverfishing.gt_drag", "journal.riverfishing.gt_maxline"};
            // §gear-width, again: every line is named for its diameter ("Монолеска 0.30"), so an Ø
            // column restated the name in the same way the Type column did before it — and now that
            // Seen is computed FROM the diameter, the number is on the row twice over.
            case LINE -> new String[]{"journal.riverfishing.gt_item",
                    "journal.riverfishing.gt_strain", "journal.riverfishing.gt_seen"};
            default -> new String[]{"journal.riverfishing.gt_item", "journal.riverfishing.gt_hooks",
                    "journal.riverfishing.gt_mass", "journal.riverfishing.gt_leader"};
        };
        // §gear-width: the columns are measured from their OWN headings, not given a flat 62 px. At 62
        // the Russian "Заметность" was wider than its column and ran into "Тест, кг", and what was left
        // for the name was 66 px — every line read "Монолеска ...". Each column takes the width of its
        // heading, the name gets what remains, and nothing has to be guessed per language.
        int cols = heads.length;
        int[] colW = new int[cols];
        int used = 0;
        for (int c = 1; c < cols; c++) {
            colW[c] = Math.max(42, this.font.width(Component.translatable(heads[c])) + 10);
            used += colW[c];
        }
        int nameW = Math.max(70, wAll - used);
        int head = top + 40;
        // §gear-sort-head: the headings sort, the same three-state click the bait table uses — a column,
        // then the other direction, then back to the shelf's own order. The rects are remembered because
        // the click handler runs in a different frame from the one that measured them.
        gearColCount = cols;
        gearColX[0] = tableX;
        gearColW[0] = nameW;
        int[] colRight = new int[cols];
        int cx = tableX + nameW;
        for (int c = 1; c < cols; c++) {
            gearColX[c] = cx;
            gearColW[c] = colW[c];
            cx += colW[c];
            colRight[c] = cx;
        }
        for (int c = 0; c < cols; c++) {
            String label = Component.translatable(heads[c]).getString()
                    + (gearSort == c ? (gearSortDesc ? " ▼" : " ▲") : "");
            boolean hov = mouseX >= gearColX[c] && mouseX < gearColX[c] + gearColW[c]
                    && mouseY >= head && mouseY < head + 10;
            int colour = gearSort == c ? 0xFFD8A93C : (hov ? 0xFFD8C88C : 0xFFB0842C);
            g.drawString(this.font, label,
                    c == 0 ? tableX : colRight[c] - this.font.width(label), head, colour, false);
        }
        g.fill(tableX, head + 10, tableX + wAll, head + 11, 0x33000000);

        int contentTop = head + 14, contentBottom = top + H - 14;
        scroll = Mth.clamp(scroll, 0, Math.max(0, lastCatH - (contentBottom - contentTop)));
        scissorJournal(g, left + 6, contentTop, left + W - 6, contentBottom);
        int y = contentTop - scroll;
        List<Component> tooltip = null;
        // §gear-table: a row that is not drawn must not stay CLICKABLE where it used to be. The click
        // handler walks the whole catalog against catRects, so leaving last frame's coordinates on the
        // categories you are not looking at meant clicking a reel opened the rod that happened to sort
        // into that slot. Park every row of every other category where nothing can hit it.
        List<Cat> rows = new ArrayList<>();
        for (int i = 0; i < gearCatalog.size(); i++) {
            if (gearCatalog.get(i).kind() == kind) {
                rows.add(gearCatalog.get(i));
            } else {
                catRects[i][0] = Integer.MIN_VALUE / 2;
                catRects[i][1] = Integer.MIN_VALUE / 2;
            }
        }
        if (gearSort == 0) {
            rows.sort(Comparator.comparing(e -> e.stack().getHoverName().getString()));
            if (gearSortDesc) java.util.Collections.reverse(rows);
        } else if (gearSort > 0) {
            int col = gearSort;
            rows.sort((a, b) -> {
                String[] ca = gearCells(a, kind), cb = gearCells(b, kind);
                double va = col - 1 < ca.length ? cellKey(ca[col - 1]) : Double.NaN;
                double vb = col - 1 < cb.length ? cellKey(cb[col - 1]) : Double.NaN;
                boolean na = Double.isNaN(va), nb = Double.isNaN(vb);
                if (na || nb) return na && nb ? 0 : (na ? 1 : -1);   // wordy cells always sink
                return gearSortDesc ? Double.compare(vb, va) : Double.compare(va, vb);
            });
        }
        for (Cat e : rows) {
            int slot = gearCatalog.indexOf(e);
            boolean hov = mouseX >= tableX && mouseX < tableX + wAll && mouseY >= y && mouseY < y + 17
                    && mouseY >= contentTop && mouseY < contentBottom;
            if (hov) {
                g.fill(tableX, y - 1, tableX + wAll, y + 16, 0x22000000);
                tooltip = catTooltip(e);
            }
            // The drawn order is the SORTED order, so each rect has to go to the row it actually is.
            catRects[slot][0] = tableX;
            catRects[slot][1] = y;
            g.renderItem(e.stack(), tableX, y);
            g.drawString(this.font, fitName(e.stack().getHoverName().getString(), nameW - 24),
                    tableX + 20, y + 4, hov ? 0xFF8A5A00 : GuiStyle.TEXT, false);
            String[] cells = gearCells(e, kind);
            for (int c = 0; c < cells.length && c + 1 < cols; c++) {
                String cell = fitName(cells[c], colW[c + 1] - 4);
                g.drawString(this.font, cell, colRight[c + 1] - this.font.width(cell), y + 4,
                        GuiStyle.TEXT_HINT, false);
            }
            y += 17;
        }
        lastCatH = (y + scroll) - contentTop;
        lastViewH = contentBottom - contentTop;
        g.disableScissor();
        renderScrollbar(g, contentTop, contentBottom);
        if (tooltip != null) g.renderComponentTooltip(this.font, tooltip, mouseX, mouseY);
    }

    /** The numbers for one row, in the order its category's headings promise. */
    private String[] gearCells(Cat e, Kind kind) {
        Item it = e.stack().getItem();
        switch (kind) {
            case ROD -> {
                if (!(it instanceof RodItem rod)) return new String[]{"—", "—", "—"};
                var rt = rod.rodType();
                String reel = rt.takesReel() ? (rt.minReel() / 1000) + "–" + (rt.maxReel() / 1000) + "k"
                        : Component.translatable("journal.riverfishing.gt_noreel").getString();
                String range = rt.takesReel()
                        ? (rt.longRange() ? "32" : (rt == com.riverfishing.component.RodType.SPINNING ? "16" : "18"))
                        : "6";
                return new String[]{(int) rt.castWeightMin() + "–" + (int) rt.castWeightMax(), reel, range};
            }
            case REEL -> {
                if (!(it instanceof ReelItem reel)) return new String[]{"—", "—"};
                return new String[]{
                        String.format(java.util.Locale.ROOT, "%.0f", reel.maxDragKg()),
                        String.format(java.util.Locale.ROOT, "%.2f",
                                com.riverfishing.component.TackleCompat.maxLineDiameter(reel.size()))};
            }
            case LINE -> {
                if (!(it instanceof LineItem ln)) return new String[]{"—", "—"};
                // §line-visibility: the ENGINE's own number — material times diameter, 0.20 mm mono = 1.
                // This column used to print the material factor alone, so 0.10 and 0.80 mono both read
                // 1.00 while the bite engine treated the thick one as eight times as easy to see.
                return new String[]{
                        String.format(java.util.Locale.ROOT, "%.1f", ln.breakingStrainKg()),
                        String.format(java.util.Locale.ROOT, "%.2f",
                                ln.lineType().visibility(ln.diameterMm()))};
            }
            default -> {
                if (!(it instanceof RigItem rig)) return new String[]{"—", "—", "—"};
                var rt = rig.rigType();
                return new String[]{Integer.toString(rt.hookCount()),
                        String.format(java.util.Locale.ROOT, "%.0f", rt.massGrams()),
                        Component.translatable(rt.hasLeader()
                                ? "journal.riverfishing.gt_yes" : "journal.riverfishing.gt_no").getString()};
            }
        }
    }

    // ---- LURES: how to work them, and what the light wants ----

    /** §lure-work: which cadence rule the retrieve applies to this lure — the engine's own three cases. */
    private static String retrieveKey(String lureId) {
        if ("popper".equals(lureId)) return "journal.riverfishing.lw_topwater";
        if ("wobbler".equals(lureId) || "crankbait".equals(lureId)) return "journal.riverfishing.lw_strict";
        if ("mormyshka".equals(lureId)) return "journal.riverfishing.lw_jig";
        return "journal.riverfishing.lw_free";
    }

    /**
     * §lure-light: what the water looks like to a fish RIGHT NOW, 0 dark/murky … 1 bright/clear.
     *
     * <p>The same shape as {@code LureColor.conditionLight}, fed from what the CLIENT can see: the hour,
     * the sky, the biome. It cannot know the depth you are about to cast into, so it uses 3 — the one
     * depth that contributes nothing either way in the real formula, which makes this an honest
     * "before you cast" reading rather than a guess dressed up as an answer.
     */
    private float lightNow() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return 0.5f;
        double v = 0.5;
        long t = mc.level.getDayTime() % 24000L;
        if (t >= 13500) v -= 0.32;                                  // night
        else if (t < 1000 || (t > 12500 && t < 13500)) v -= 0.08;   // dawn / dusk
        else v += 0.28;                                             // day
        if (mc.level.isThundering()) v -= 0.20;
        else if (mc.level.isRaining()) v -= 0.14;
        else v += 0.10;
        if (mc.player != null) {
            var biome = mc.level.getBiome(mc.player.blockPosition());
            if (biome.is(net.minecraft.tags.BiomeTags.IS_RIVER)) v += 0.04;
        }
        return (float) Mth.clamp(v, 0.0, 1.0);
    }

    /** The colour class the current light suits best, by the engine's own closeness rule. */
    private static com.riverfishing.engine.LureColor bestColour(float light) {
        com.riverfishing.engine.LureColor best = com.riverfishing.engine.LureColor.NATURAL;
        double top = -1;
        for (com.riverfishing.engine.LureColor lc : com.riverfishing.engine.LureColor.values()) {
            double closeness = 1.0 - Math.min(1.0, Math.abs(light - lc.idealLight()) * 2.0);
            if (closeness > top) {
                top = closeness;
                best = lc;
            }
        }
        return best;
    }

    /**
     * §lure-tab (0.8.0): the lure page, on the three axes a lure actually has.
     *
     * <p>Not the bait table's axes — a spinner has no grind and no richness, and copying those columns
     * here would have printed a dash in every one of them. A lure is WORKED, it wears a COLOUR the light
     * either suits or does not, and it swims in a LAYER. None of that was anywhere in the game.
     */
    private void renderLureTable(GuiGraphics g, int mouseX, int mouseY) {
        int x = left + 10, wAll = W - 26;
        g.drawString(this.font, Component.translatable("journal.riverfishing.note_lure"),
                x, top + 22, GuiStyle.TEXT_HINT, false);

        // §lure-colour-note: colour was a COLUMN, and it printed "any — dye it" on every single row —
        // ninety-six pixels of the same sentence eleven times, which is exactly the width the retrieve
        // column needed and did not have. It is a fact about all lures, so it is said once, up here.
        float light = lightNow();
        com.riverfishing.engine.LureColor best = bestColour(light);
        g.fill(x, top + 34, x + wAll, top + 68, 0x18000000);
        g.drawString(this.font, Component.translatable("journal.riverfishing.lw_now"),
                x + 5, top + 38, GuiStyle.TEXT_HINT, false);
        bar(g, x + 5, top + 50, 90, 3, light, lerpColour(0xFF2A3550, 0xFFE8D89A, light));
        g.drawString(this.font, Component.translatable("journal.riverfishing.lw_wants",
                        Component.translatable("lurecolor.riverfishing."
                                + best.name().toLowerCase(java.util.Locale.ROOT))),
                x + 105, top + 41, 0xFF8A5A00, false);
        g.drawString(this.font, fitName(
                        Component.translatable("journal.riverfishing.lw_colour_note").getString(), wAll - 10),
                x + 5, top + 58, GuiStyle.GHOST, false);

        int layerW = 74, retrieveW = 190;
        int nameW = wAll - layerW - retrieveW;
        int head = top + 74;
        g.drawString(this.font, Component.translatable("journal.riverfishing.bt_item"), x, head, 0xFFB0842C, false);
        g.drawString(this.font, Component.translatable("journal.riverfishing.lw_layer"),
                x + nameW, head, 0xFFB0842C, false);
        g.drawString(this.font, Component.translatable("journal.riverfishing.lw_retrieve"),
                x + nameW + layerW, head, 0xFFB0842C, false);
        g.fill(x, head + 10, x + wAll, head + 11, 0x33000000);

        int contentTop = head + 14, contentBottom = top + H - 14;
        scroll = Mth.clamp(scroll, 0, Math.max(0, lastCatH - (contentBottom - contentTop)));
        scissorJournal(g, left + 6, contentTop, left + W - 6, contentBottom);
        int y = contentTop - scroll;
        List<Component> tooltip = null;
        for (int i = 0; i < lureCat.size(); i++) {
            Cat e = lureCat.get(i);
            boolean hov = mouseX >= x && mouseX < x + wAll && mouseY >= y && mouseY < y + 17
                    && mouseY >= contentTop && mouseY < contentBottom;
            if (hov) {
                g.fill(x, y - 1, x + wAll, y + 16, 0x22000000);
                tooltip = catTooltip(e);
            }
            catRects[i][0] = x;
            catRects[i][1] = y;
            g.renderItem(e.stack(), x, y);
            g.drawString(this.font, fitName(e.stack().getHoverName().getString(), nameW - 24),
                    x + 20, y + 4, hov ? 0xFF8A5A00 : GuiStyle.TEXT, false);
            boolean surface = "popper".equals(e.id());
            g.drawString(this.font, Component.translatable(surface
                            ? "journal.riverfishing.lw_surface" : "journal.riverfishing.lw_depth"),
                    x + nameW, y + 4, surface ? 0xFF2E7D32 : GuiStyle.TEXT_HINT, false);
            g.drawString(this.font, fitName(
                            Component.translatable(retrieveKey(e.id())).getString(), retrieveW - 4),
                    x + nameW + layerW, y + 4, GuiStyle.TEXT, false);
            y += 17;
        }
        lastCatH = (y + scroll) - contentTop;
        lastViewH = contentBottom - contentTop;
        g.disableScissor();
        renderScrollbar(g, contentTop, contentBottom);
        if (tooltip != null) g.renderComponentTooltip(this.font, tooltip, mouseX, mouseY);
    }

    // ---- BAIT & FEED: one table ----

    /** Which column the bait table is sorted on, and which way. 0 = the shelf's own order. */
    private int baitSort;
    private boolean baitSortDesc = true;
    /** Column x offsets, filled by the header render so the rows and the hit-test cannot drift. */
    private final int[] baitCols = new int[6];

    /** The sortable value behind a column, or NaN when this row has nothing to say there. */
    private float baitCell(Cat e, int col) {
        com.riverfishing.groundbait.GroundbaitMix.Component c =
                com.riverfishing.groundbait.GroundbaitMix.PANTRY.get(e.id());
        return switch (col) {
            case 1 -> e.kind() == Kind.NATURAL ? 1f : 0f;                     // goes on a hook
            case 2 -> c != null ? 1f : 0f;                                    // goes in a mix
            case 3 -> c == null ? Float.NaN : (float) c.nutrition();
            case 4 -> c == null ? Float.NaN : (float) c.fraction();
            case 5 -> c == null ? Float.NaN : predatorPull(c.diet());
            default -> 0f;
        };
    }

    /**
     * §bait-table (0.8.0): baits and feed as ONE table a newcomer can read.
     *
     * <p>The tab used to be three headed shelves of names, and the two facts a beginner most needs —
     * whether a thing goes on the hook, in the mix, or both — were a sentence under a heading rather than
     * something you could see down a column. They are columns now, and the numbers that decide a mix sit
     * beside them instead of being one click away each.
     *
     * <p>Click a heading to sort by it. Rows with nothing to say in that column sink to the bottom rather
     * than pretending to be zero, because "not a mix component" and "worth nought in a mix" are different
     * statements and only one of them is true of a spinner.
     */
    private void renderBaitTable(GuiGraphics g, int mouseX, int mouseY) {
        int x = left + 10, wAll = W - 26;
        g.drawString(this.font, Component.translatable("journal.riverfishing.tab_bait_hint"),
                x, top + 22, GuiStyle.TEXT_HINT, false);

        int nameW = wAll - 42 - 42 - 46 - 46 - 62;
        baitCols[0] = x;
        baitCols[1] = x + nameW;
        baitCols[2] = baitCols[1] + 42;
        baitCols[3] = baitCols[2] + 42;
        baitCols[4] = baitCols[3] + 46;
        baitCols[5] = baitCols[4] + 46;

        int head = top + 38;
        String[] keys = {"journal.riverfishing.bt_item", "journal.riverfishing.bt_hook",
                "journal.riverfishing.bt_mix", "journal.riverfishing.gb_col_rich",
                "journal.riverfishing.gb_col_grind", "journal.riverfishing.gb_col_pull"};
        for (int i = 0; i < 6; i++) {
            Component label = Component.translatable(keys[i]);
            String arrow = baitSort == i ? (baitSortDesc ? " ▼" : " ▲") : "";
            int cw = i == 0 ? nameW : (i == 5 ? 62 : (i < 3 ? 42 : 46));
            boolean hov = mouseX >= baitCols[i] && mouseX < baitCols[i] + cw
                    && mouseY >= head && mouseY < head + 10;
            int colour = baitSort == i ? 0xFFD8A93C : (hov ? 0xFFD8C88C : 0xFFB0842C);
            if (i == 0) {
                g.drawString(this.font, label.getString() + arrow, baitCols[i], head, colour, false);
            } else {
                String s = label.getString() + arrow;
                g.drawString(this.font, s, baitCols[i] + cw - this.font.width(s), head, colour, false);
            }
        }
        g.fill(x, head + 10, x + wAll, head + 11, 0x33000000);

        List<Cat> rows = new ArrayList<>(baitCat);
        if (baitSort > 0) {
            int col = baitSort;
            rows.sort((a, b) -> {
                float va = baitCell(a, col), vb = baitCell(b, col);
                boolean na = Float.isNaN(va), nb = Float.isNaN(vb);
                if (na || nb) return na && nb ? 0 : (na ? 1 : -1);   // blanks always sink
                return baitSortDesc ? Float.compare(vb, va) : Float.compare(va, vb);
            });
        }

        int contentTop = head + 14, contentBottom = top + H - 14;
        scroll = Mth.clamp(scroll, 0, Math.max(0, lastCatH - (contentBottom - contentTop)));
        scissorJournal(g, left + 6, contentTop, left + W - 6, contentBottom);
        int y = contentTop - scroll;
        List<Component> tooltip = null;
        for (int i = 0; i < rows.size(); i++) {
            Cat e = rows.get(i);
            int slot = baitCat.indexOf(e);
            boolean hov = mouseX >= x && mouseX < x + wAll && mouseY >= y && mouseY < y + 17
                    && mouseY >= contentTop && mouseY < contentBottom;
            if (hov) {
                g.fill(x, y - 1, x + wAll, y + 16, 0x22000000);
                tooltip = catTooltip(e);
            }
            // The click list is the SORTED list, so remember what each drawn row actually is.
            catRects[slot][0] = x;
            catRects[slot][1] = y;
            g.renderItem(e.stack(), x, y);
            g.drawString(this.font, fitName(e.stack().getHoverName().getString(), nameW - 24),
                    x + 20, y + 4, hov ? 0xFF8A5A00 : GuiStyle.TEXT, false);
            tick(g, baitCols[1] + 42, y + 4, baitCell(e, 1) > 0);
            tick(g, baitCols[2] + 42, y + 4, baitCell(e, 2) > 0);
            num(g, baitCols[3] + 46, y + 4, baitCell(e, 3), GuiStyle.TEXT_HINT);
            num(g, baitCols[4] + 46, y + 4, baitCell(e, 4), GuiStyle.TEXT_HINT);
            float pull = baitCell(e, 5);
            if (Float.isNaN(pull)) {
                drawRight(g, Component.literal("—"), baitCols[5] + 62, y + 4, GuiStyle.GHOST);
            } else {
                bar(g, baitCols[5], y + 6, 30, 4, pull, pullColour(pull));
                drawRight(g, Component.literal(String.format(java.util.Locale.ROOT, "%.2f", pull)),
                        baitCols[5] + 62, y + 4, GuiStyle.TEXT);
            }
            y += 17;
        }
        lastCatH = (y + scroll) - contentTop;
        lastViewH = contentBottom - contentTop;
        g.disableScissor();
        renderScrollbar(g, contentTop, contentBottom);
        if (tooltip != null) g.renderComponentTooltip(this.font, tooltip, mouseX, mouseY);
    }

    /**
     * §guide-visual (0.8.0): a guide page may carry a TABLE and a set of BARS, and both are written in
     * the lang file rather than in code.
     *
     * <p>{@code guide.riverfishing.<id>.table} is rows separated by newlines and cells by "|", the first
     * row being the heading. {@code .bars} is "label|0.0-1.0" per line. Neither key has to exist.
     *
     * <p>Doing it this way is the whole point: adding a table to a page is then a TRANSLATION job, done
     * once in three languages, instead of a new render method per page that only English would ever get.
     */
    private int guideTable(GuiGraphics g, String id, int y) {
        String key = "guide.riverfishing." + id + ".table";
        if (!I18n.exists(key)) return y;
        String[] rows = I18n.get(key).split("\n");
        int cols = 0;
        for (String r : rows) cols = Math.max(cols, r.split("\\|").length);
        if (cols == 0) return y;
        int wAll = W - 24, colW = wAll / cols;
        for (int r = 0; r < rows.length; r++) {
            String[] cells = rows[r].split("\\|");
            boolean head = r == 0;
            for (int c = 0; c < cells.length; c++) {
                String cell = fitName(cells[c].trim(), colW - 6);
                int cx = left + 10 + c * colW;
                // First column left, the rest right — numbers line up, names stay readable.
                if (c > 0) cx += colW - this.font.width(cell) - 6;
                g.drawString(this.font, cell, cx, y, head ? 0xFFB0842C : GuiStyle.TEXT, false);
            }
            y += 11;
            if (head) {
                g.fill(left + 10, y - 1, left + 10 + wAll, y, 0x33000000);
                y += 2;
            }
        }
        return y;
    }

    private int guideBars(GuiGraphics g, String id, int y) {
        String key = "guide.riverfishing." + id + ".bars";
        if (!I18n.exists(key)) return y;
        List<Param> rows = new ArrayList<>();
        for (String line : I18n.get(key).split("\n")) {
            String[] kv = line.split("\\|");
            if (kv.length < 2) continue;
            float v;
            try {
                v = Float.parseFloat(kv[1].trim());
            } catch (NumberFormatException ignored) {
                continue;   // a mistranslated number must not take the page down with it
            }
            rows.add(new Param(kv[0].trim(), v, lerpColour(0xFF6E8A3C, 0xFF9A4A3C, v)));
        }
        return rows.isEmpty() ? y : paramTableLiteral(g, left + 10, y, W - 24, rows);
    }

    /** {@link #paramTable} for rows whose labels are already text, not lang keys. */
    private int paramTableLiteral(GuiGraphics g, int x, int y, int w, List<Param> rows) {
        int labelW = 0;
        for (Param p : rows) labelW = Math.max(labelW, this.font.width(p.key()));
        labelW = Math.min(labelW + 8, w - 80);
        int barX = x + labelW, barW = w - labelW - 34;
        for (Param p : rows) {
            g.drawString(this.font, p.key(), x, y, GuiStyle.TEXT_HINT, false);
            bar(g, barX, y + 2, barW, 4, p.value(), p.colour());
            String num = String.format(java.util.Locale.ROOT, "%.2f", p.value());
            g.drawString(this.font, num, x + w - this.font.width(num), y, GuiStyle.TEXT, false);
            y += 12;
        }
        return y;
    }

    /** A yes/no column: a tick when it is true, a quiet dash when it is not. */
    private void tick(GuiGraphics g, int rightX, int y, boolean on) {
        Component c = Component.literal(on ? "✔" : "—");
        g.drawString(this.font, c, rightX - this.font.width(c), y, on ? 0xFF2E7D32 : GuiStyle.GHOST, false);
    }

    private void num(GuiGraphics g, int rightX, int y, float v, int colour) {
        String s = Float.isNaN(v) ? "—" : String.format(java.util.Locale.ROOT, "%.2f", v);
        g.drawString(this.font, s, rightX - this.font.width(s), y,
                Float.isNaN(v) ? GuiStyle.GHOST : colour, false);
    }

    /**
     * §gb-pull: of all the fish that answer to this ingredient, how much of that pull is predatory.
     *
     * <p>Weighted by how strongly each species wants it, so an ingredient one pike loves and six roach
     * merely tolerate reads as predatory, which is what it is. Peaceful here means the cyprinids, the koi
     * and the sturgeons; everything else hunts. Returns -1 when no fish answers to it at all — ballast.
     */
    private float predatorPull(String diet) {
        if (diet == null) return -1f;
        double total = 0, predator = 0;
        for (String sp : SPECIES) {
            Float score = card(sp).baits().get(diet);
            if (score == null || score <= 0) continue;
            total += score;
            if (!PEACEFUL.contains(card(sp).group())) predator += score;
        }
        return total <= 0 ? -1f : (float) (predator / total);
    }

    private static final java.util.Set<String> PEACEFUL = java.util.Set.of(
            com.riverfishing.fish.FishGroup.CYPRINID,
            com.riverfishing.fish.FishGroup.KOI,
            com.riverfishing.fish.FishGroup.STURGEON);

    /** The species that want this ingredient, keenest first — the honest answer to "who is this for". */
    private List<String> fishForDiet(String diet, int limit) {
        if (diet == null) return List.of();
        List<String> out = new ArrayList<>();
        for (String sp : SPECIES) {
            Float score = card(sp).baits().get(diet);
            if (score != null && score >= 0.5f) out.add(sp);
        }
        out.sort((a, b) -> Float.compare(card(b).baits().getOrDefault(diet, 0f),
                card(a).baits().getOrDefault(diet, 0f)));
        return out.subList(0, Math.min(limit, out.size())).stream()
                .map(sp -> Component.translatable("fish.riverfishing." + sp).getString())
                .collect(Collectors.toList());
    }

    /**
     * §gb-pull: peaceful at 0, predatory at 1, and the bar carries that in its COLOUR.
     *
     * <p>The first cut drew a split green/red track with a needle on it and a caption at each end. It
     * needed three rows and a legend to say one number, and it did not look like the two rows above it —
     * which is the whole reason it read badly. It is one value between nought and one, so it gets the
     * same row every other value gets.
     */
    private static int pullColour(float pull) {
        return lerpColour(0xFF3F7E2E, 0xFF9A3C2E, Mth.clamp(pull, 0f, 1f));
    }

    private static int lerpColour(int a, int b, float t) {
        int r = (int) (((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * t);
        int g2 = (int) (((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * t);
        int bl = (int) ((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * t);
        return 0xFF000000 | (r << 16) | (g2 << 8) | bl;
    }

    /** One row of a parameter table: {@code label ──[bar]── value}, all three on the same line. */
    private record Param(String key, float value, int colour) {}

    /**
     * A block of parameters as an actual TABLE — labels in one column, bars in the next, numbers
     * right-aligned in the last, every row on one line and every column the same width down the block.
     *
     * <p>Each parameter used to take two lines: label and number on one, a full-width bar under it. Three
     * of those in a row is six lines of drifting left edges, which is what "unstructured" looked like.
     * The label column is measured from the strings themselves, so Russian and English both line up.
     */
    private int paramTable(GuiGraphics g, int x, int y, int w, List<Param> rows) {
        int labelW = 0;
        for (Param p : rows) labelW = Math.max(labelW, this.font.width(Component.translatable(p.key())));
        labelW = Math.min(labelW + 6, w - 70);
        int numW = 26;
        int barX = x + labelW, barW = w - labelW - numW - 4;
        for (Param p : rows) {
            g.drawString(this.font, Component.translatable(p.key()), x, y, GuiStyle.TEXT_HINT, false);
            bar(g, barX, y + 2, barW, 4, p.value(), p.colour());
            String num = String.format(java.util.Locale.ROOT, "%.2f", p.value());
            g.drawString(this.font, num, x + w - this.font.width(num), y, GuiStyle.TEXT, false);
            y += 12;
        }
        return y;
    }

    private static ItemStack pantryStack(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id.contains(":") ? id : RiverFishing.MODID + ":" + id);
        if (rl == null) return ItemStack.EMPTY;
        Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(rl);
        return item == net.minecraft.world.item.Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
    }

    private final CompoundTag data;
    private final List<Cat> baitCat = new ArrayList<>();
    private final List<Cat> gearCatalog = new ArrayList<>();
    /** §lure-tab: lures live on their own page now — they are the only bait that never goes in a mix. */
    private final List<Cat> lureCat = new ArrayList<>();
    private final int[][] catRects;
    /** Quest rows' {x,y} from the last render (§quest-claim) + optimistic locally-claimed ids. */
    private final int[][] questRects = new int[Quests.ALL.size()][2];
    private final java.util.Set<String> claimedNow = new java.util.HashSet<>();
    /** §discord: the "open Discord" button's rect on the Discord guide page, or zeros when not shown. */
    private final int[] linkRect = new int[4];
    /** Skill "+" button rects {x,y,x2,y2} from the last render (§skills) + optimistic local spends. */
    private final int[][] skillRects = new int[com.riverfishing.fishing.AnglerSkills.Perk.values().length][4];
    private final java.util.Map<String, Integer> spentNow = new java.util.HashMap<>();
    private int left;
    private int top;
    private float uiScale = 1f;   // §journal-scale: <1 shrinks the whole panel to fit a small (high-GUI-scale) screen
    private int tab = TAB_FISH;
    private String detail;      // opened fish species, or null
    private int catDetail = -1; // opened bait/gear entry index (in the current tab's list), or -1
    private int scroll;
    private int lastCatH;       // measured content height of the last catalog render (for scroll clamp)
    /** Visible height of whatever the last render scrolled, so the wheel clamps to the RIGHT viewport. */
    private int lastViewH = 1;
    /** §fish-list: which family column row is selected — 0 is "everything", the rest index {@link #families}. */
    private int family;
    /** §fish-search: what has been typed into the filter box, and whether it has the keyboard. */
    private String search = "";
    private boolean searchFocus;
    /** Families that any species is actually filed under, in {@link com.riverfishing.fish.FishGroup} order. */
    private final List<String> families = new ArrayList<>();
    /** The species the list is showing right now — rebuilt every frame, and what a click indexes into. */
    private final List<String> shown = new ArrayList<>();
    /**
     * §fish-order: every species by the angler level it wants, then by name.
     *
     * <p>Registry order is the order they were ADDED to the mod over six releases, which is a fact about
     * the changelog and not about fishing. Level ascending is the ladder the player is actually climbing:
     * what you can catch now sits at the top, what you are working towards sits below it.
     *
     * <p>Sorted once, not per frame — the name comparator translates, and doing seventy-nine lookups
     * times log seventy-nine on every frame is a cost for nothing.
     */
    private final List<String> ordered = new ArrayList<>();

    public JournalScreen(CompoundTag data) {
        super(Component.translatable("journal.riverfishing.header"));
        this.data = data;
        for (RegistrySupplier<Item> ro : ModItems.ALL) {
            Item it = ro.get();
            if (it instanceof BaitItem b) {
                (b.artificial() ? lureCat : baitCat)
                        .add(new Cat(new ItemStack(it), b.artificial() ? Kind.LURE : Kind.NATURAL, b.baitId()));
            } else if (it instanceof GroundbaitItem) {
                baitCat.add(new Cat(new ItemStack(it), Kind.GROUNDBAIT, "groundbait"));
            } else if (it instanceof RodItem) {
                gearCatalog.add(new Cat(new ItemStack(it), Kind.ROD, ""));
            } else if (it instanceof ReelItem) {
                gearCatalog.add(new Cat(new ItemStack(it), Kind.REEL, ""));
            } else if (it instanceof LineItem) {
                gearCatalog.add(new Cat(new ItemStack(it), Kind.LINE, ""));
            } else if (it instanceof RigItem ri && !isInternalRig(ri.rigType())) {
                gearCatalog.add(new Cat(new ItemStack(it), Kind.RIG, ""));
            }
        }
        // §gb-pantry: the mix-only shelf, read straight off GroundbaitMix.PANTRY so the journal and the
        // engine cannot disagree about what goes in a mix or what it weighs. Anything already on the
        // shelf as a hook bait is skipped — it is one thing, and it gets its numbers on its own page.
        java.util.Set<String> already = new java.util.HashSet<>();
        for (Cat e : baitCat) already.add(e.id());
        for (com.riverfishing.groundbait.GroundbaitMix.Component comp
                : com.riverfishing.groundbait.GroundbaitMix.PANTRY.values()) {
            if (already.contains(comp.id())
                    || com.riverfishing.groundbait.GroundbaitMix.BASE_ID.equals(comp.id())) {
                continue;
            }
            ItemStack stack = pantryStack(comp.id());
            if (!stack.isEmpty()) baitCat.add(new Cat(stack, Kind.GB_PART, comp.id()));
        }

        Comparator<Cat> byKindThenName = Comparator.comparingInt((Cat e) -> e.kind().ordinal())
                .thenComparing(e -> e.stack().getHoverName().getString());
        baitCat.sort(byKindThenName);
        lureCat.sort(byKindThenName);
        // §gear-sort: reels by SIZE, lines by type+diameter, rods by tier — alphabetical put 10000
        // between 1000 and 2000.
        gearCatalog.sort(Comparator.comparingInt((Cat e) -> e.kind().ordinal())
                .thenComparingDouble(JournalScreen::gearSortKey)
                .thenComparing(e -> e.stack().getHoverName().getString()));

        // §guide-order (0.8.0): the shelf follows the mod's OWN quest stages. It used to be "newest
        // first", which is an order about the changelog: a player on their first evening met the drag,
        // trolling and big game before they had a rod that could do any of it. Each block below is a
        // moment in the same journey the quests already describe.

        guideGroupNow = 0;   // first casts
        // §wait-guide: FIRST on purpose. Three pages teach cranking, and a float/bottom angler has to
        // meet "do not crank this rod" before any of them.
        addGuide("waiting", modStack("bottom_rod"));
        addGuide("spook", new ItemStack(net.minecraft.world.item.Items.LEATHER_BOOTS));
        addGuide("stress", modStack("line_mono_030"));
        addGuide("drag", modStack("reel_7000"));

        guideGroupNow = 1;   // float and feeder — where groundbait is learned
        addGuide("groundbait", modStack("groundbait_powder"));
        addGuide("gbnumbers", modStack("corn"));
        addGuide("feeding", modStack("groundbait_soil"));
        addGuide("gbrecipes", modStack("boilie"));
        addGuide("keepnet", modStack("keepnet_medium"));

        guideGroupNow = 2;   // predators
        addGuide("lurework", modStack("wobbler"));
        addGuide("topwater", modStack("popper"));
        addGuide("livebait", modStack("livebait"));

        guideGroupNow = 3;   // the bench, and where the tackle lives
        addGuide("tacklebench", modStack("fishing_stall"));
        addGuide("tacklebox", modStack("tackle_box_medium"));

        guideGroupNow = 4;   // reading the water, and what to do with a catch
        addGuide("community", modStack("fish_finder"));
        addGuide("market", new ItemStack(net.minecraft.world.item.Items.EMERALD));
        addGuide("coop", new ItemStack(net.minecraft.world.item.Items.LEAD));

        guideGroupNow = 5;   // under the ice — quest stage 6, and until now the only mode with no page
        addGuide("icefishing", modStack("ice_auger"));

        guideGroupNow = 6;   // the sea, and the fish that need a boat
        addGuide("trolling", modStack("trolling_rod"));
        addGuide("biggame", modStack("yellowfin_tuna"));
        addGuide("legendary", modStack("blue_marlin"));

        guideGroupNow = 7;   // not for anglers: whoever runs the world, and where to shout
        addGuide("cull", modStack("electro_rod"));
        addGuide("discord", new ItemStack(net.minecraft.world.item.Items.PLAYER_HEAD));
        addGuide("thanks", new ItemStack(net.minecraft.world.item.Items.HEART_OF_THE_SEA));

        // Every catalog indexes into this, so it has to fit the LONGEST of them — lureCat was missing,
        // which is a crash waiting for the release that adds a twelfth lure.
        catRects = new int[Math.max(Math.max(guideCat.size(), lureCat.size()),
                Math.max(baitCat.size(), gearCatalog.size()))][2];
    }

    private static ItemStack modStack(String path) {
        return new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM
                .get(com.riverfishing.RiverFishing.id(path)));
    }

    /** §fit-name: truncate to width with a visible ellipsis — a silently cut name looked missing. */
    private String fitName(String full, int width) {
        String cut = this.font.plainSubstrByWidth(full, width);
        return cut.length() >= full.length() ? full : this.font.plainSubstrByWidth(full, width - 6) + "…";
    }

    /** §gear-sort: numeric ordering inside a gear kind (reel size, line type+diameter, rod tier). */
    private static double gearSortKey(Cat e) {
        Item it = e.stack().getItem();
        if (it instanceof ReelItem r) return r.size();
        if (it instanceof LineItem l) return l.lineType().ordinal() * 10 + l.diameterMm();
        if (it instanceof RodItem rod) return rod.rodType().ordinal();
        return 0;
    }

    private static boolean isInternalRig(RigType t) {
        // WINTER included (0.5.0): it lives INSIDE the winter rod (native rig) — never separate gear.
        return t == RigType.PRIMITIVE || t == RigType.FLOAT_LIGHT || t == RigType.FLOAT || t == RigType.PREDATOR
                || t == RigType.WINTER;
    }

    public static void open(CompoundTag data) {
        open(data, "");
    }

    /**
     * §guide-nudge: open straight on a guide page when the player took up the offer of help. An offer
     * that lands you on the front page and leaves you to find the right shelf is not help.
     */
    public static void open(CompoundTag data, String guideId) {
        JournalScreen next = new JournalScreen(data);
        if (guideId != null && !guideId.isEmpty()) {
            for (int i = 0; i < next.guideCat.size(); i++) {
                if (next.guideCat.get(i).id().equals(guideId)) {
                    next.tab = TAB_GUIDE;
                    next.catDetail = i;
                    Minecraft.getInstance().setScreen(next);
                    return;
                }
            }
        }
        // A refresh (server re-sends the journal after a skill unlock / quest claim) reuses this same
        // entry point — carry the reader's place over so they don't get thrown back to the FISH tab.
        if (Minecraft.getInstance().screen instanceof JournalScreen prev) {
            next.tab = prev.tab;
            next.scroll = prev.scroll;
            next.detail = prev.detail;
            next.catDetail = prev.catDetail;
        }
        Minecraft.getInstance().setScreen(next);
    }

    @Override
    protected void init() {
        this.W = MAX_W;
        this.COL_W = this.W - FAM_W - 24;
        this.catColW = (this.W - 20) / CAT_COLS;
        buildFamilies();
        this.ILLUS_W = 240;
        this.ILLUS_H = this.ILLUS_W * 2 / 3;
        // §journal-scale: at a high GUI scale the screen is small in GUI units and the full-size journal
        // (W×H) would clip off the bottom (unusable at scale 4). Shrink the whole panel to fit, centred; the
        // render + mouse + scissor all go through this factor so clicks and clipping stay aligned.
        this.uiScale = Math.min(1f, Math.min((this.width - 8f) / MAX_W, (this.height - 8f) / H));
        this.left = (this.width - W) / 2;
        this.top = (this.height - H) / 2;
    }

    /** Screen → journal-space coordinate (the render is scaled by {@link #uiScale} around the screen centre). */
    private double toJournalX(double sx) { return (sx - this.width / 2.0) / uiScale + this.width / 2.0; }
    private double toJournalY(double sy) { return (sy - this.height / 2.0) / uiScale + this.height / 2.0; }

    /** Scissor rect given in journal space, pushed in the scaled screen space the content actually draws to. */
    private void scissorJournal(GuiGraphics g, int x1, int y1, int x2, int y2) {
        float cx = this.width / 2f, cy = this.height / 2f;
        g.enableScissor(Math.round(cx + (x1 - cx) * uiScale), Math.round(cy + (y1 - cy) * uiScale),
                Math.round(cx + (x2 - cx) * uiScale), Math.round(cy + (y2 - cy) * uiScale));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);
        boolean scaled = uiScale < 0.999f;
        if (scaled) {
            g.pose().pushPose();
            g.pose().translate(this.width / 2f, this.height / 2f, 0);
            g.pose().scale(uiScale, uiScale, 1f);
            g.pose().translate(-this.width / 2f, -this.height / 2f, 0);
            // hover in the same space the panel now draws in
            mouseX = (int) Math.round(toJournalX(mouseX));
            mouseY = (int) Math.round(toJournalY(mouseY));
        }
        GuiStyle.panel(g, left, top, W, H);
        renderTabs(g, mouseX, mouseY);
        if (tab == TAB_FISH) {
            if (detail != null) renderFishDetail(g, detail);
            else renderFishGrid(g, mouseX, mouseY);
        } else if (tab == TAB_QUEST) {
            renderQuests(g, mouseX, mouseY);
        } else if (tab == TAB_SKILL) {
            renderSkills(g, mouseX, mouseY);
        } else if (tab == TAB_RECORD) {
            renderRecords(g, mouseX, mouseY);
        } else {
            List<Cat> list = tabList();
            if (catDetail >= 0 && catDetail < list.size()) {
                renderCatDetail(g, list.get(catDetail), mouseX, mouseY);
            } else if (tab == TAB_BAIT) {
                renderBaitTable(g, mouseX, mouseY);
            } else if (tab == TAB_LURE) {
                renderLureTable(g, mouseX, mouseY);
            } else if (tab == TAB_GEAR) {
                renderGearTable(g, mouseX, mouseY);
            } else {
                renderCatalog(g, list, mouseX, mouseY);
            }
        }
        if (scaled) g.pose().popPose();
    }

    // ---- tabs ----

    private int tabW(int i) {
        return this.font.width(Component.translatable(TAB_KEYS[i])) + 12;
    }

    private int tabX(int i) {
        int x = left + 8;
        for (int j = 0; j < i; j++) x += tabW(j) + 4;
        return x;
    }

    private void renderTabs(GuiGraphics g, int mouseX, int mouseY) {
        int y0 = top + 3, y1 = top + 18;
        for (int i = 0; i < TAB_KEYS.length; i++) {
            int x = tabX(i), w = tabW(i);
            boolean active = tab == i;
            boolean hov = mouseX >= x && mouseX < x + w && mouseY >= y0 && mouseY < y1;
            int fill = active ? GuiStyle.PANEL_FACE : (hov ? 0xFF8A7038 : 0xFF63512F);
            g.fill(x, y0, x + w, y1, fill);
            int hi = active ? GuiStyle.PANEL_HI : 0xFF9A8048;
            g.fill(x, y0, x + w, y0 + 1, hi);
            g.fill(x, y0, x + 1, y1, hi);
            g.fill(x + w - 1, y0, x + w, y1, 0xFF3A2A16);
            if (!active) g.fill(x, y1 - 1, x + w, y1, 0xFF3A2A16);
            int tc = active ? GuiStyle.TEXT : 0xFFEDE2C6;
            g.drawString(this.font, Component.translatable(TAB_KEYS[i]), x + 7, top + 6, tc, !active);
        }
    }

    // ---- FISH: families, search, list ----

    /** §journal-card: the species facts, as sent by the server. Never {@code null}; ask {@code present()}. */
    private com.riverfishing.fish.FishCard card(String sp) {
        return com.riverfishing.fish.FishCard.of(data.getCompound("cards").getCompound(sp));
    }

    private boolean caught(String sp) {
        return data.contains(key(sp));
    }

    /** How many rows of the species list fit under the header. */
    private int listRows() {
        return Math.max(1, (H - LIST_TOP - 12) / LIST_ROW);
    }

    /** The families any species is filed under, plus the "everything" row at index 0. */
    private void buildFamilies() {
        ordered.clear();
        for (String sp : SPECIES) ordered.add(sp);
        ordered.sort(Comparator
                .comparingInt((String sp) -> card(sp).minLevel())
                .thenComparing(sp -> Component.translatable("fish.riverfishing." + sp).getString(),
                        String.CASE_INSENSITIVE_ORDER));
        families.clear();
        for (String gname : com.riverfishing.fish.FishGroup.ORDER) {
            for (String sp : SPECIES) {
                if (gname.equals(card(sp).group())) {
                    families.add(gname);
                    break;
                }
            }
        }
        if (family > families.size()) family = 0;
    }

    /** Does this species belong in the list as it is currently filtered? */
    private boolean inFilter(String sp) {
        if (family > 0 && !families.get(family - 1).equals(card(sp).group())) return false;
        if (search.isEmpty()) return true;
        return Component.translatable("fish.riverfishing." + sp).getString()
                .toLowerCase(java.util.Locale.ROOT).contains(search.toLowerCase(java.util.Locale.ROOT));
    }

    private void renderFishGrid(GuiGraphics g, int mouseX, int mouseY) {
        if (families.isEmpty()) buildFamilies();
        int discovered = 0;
        for (String sp : SPECIES) if (caught(sp)) discovered++;
        long xp = data.getLong(JournalData.XP);
        int level = JournalData.levelForXp(xp);

        String angler = this.font.plainSubstrByWidth(
                Component.translatable("journal.riverfishing.angler", level,
                        Component.translatable("rank.riverfishing." + JournalData.rankKey(level)),
                        xp, JournalData.xpForLevel(level + 1) - xp).getString(), W - 20);
        g.drawString(this.font, angler, left + 10, top + 22, GuiStyle.TEXT, false);

        long lvlBase = JournalData.xpForLevel(level);
        long lvlNext = JournalData.xpForLevel(level + 1);
        float frac = lvlNext > lvlBase ? (float) (xp - lvlBase) / (lvlNext - lvlBase) : 0f;
        bar(g, left + 10, top + 33, W - 20, 3, frac, 0xFFC89C4A);

        g.drawString(this.font, Component.translatable("journal.riverfishing.total",
                data.getInt("total"), discovered + "/" + SPECIES.length), left + 10, top + 40,
                GuiStyle.TEXT_HINT, false);

        renderFamilyColumn(g, mouseX, mouseY);
        renderSearchBox(g, mouseX, mouseY);
        renderSpeciesList(g, mouseX, mouseY);
    }

    /** The left rail: every family with "how many you have caught / how many there are". */
    private void renderFamilyColumn(GuiGraphics g, int mouseX, int mouseY) {
        int x = left + 8, y0 = top + LIST_TOP;
        g.fill(x - 2, y0 - 2, x + FAM_W + 2, y0 + (families.size() + 1) * LIST_ROW + 2, 0x22000000);
        for (int i = 0; i <= families.size(); i++) {
            int y = y0 + i * LIST_ROW;
            boolean hov = mouseX >= x && mouseX < x + FAM_W && mouseY >= y && mouseY < y + LIST_ROW;
            if (i == family) {
                g.fill(x, y, x + FAM_W, y + LIST_ROW, 0x55B08D3C);
            } else if (hov) {
                g.fill(x, y, x + FAM_W, y + LIST_ROW, 0x22000000);
            }
            Component label = i == 0
                    ? Component.translatable("journal.riverfishing.fam_all")
                    : Component.translatable(com.riverfishing.fish.FishGroup.nameKey(families.get(i - 1)));
            int have = 0, all = 0;
            for (String sp : SPECIES) {
                if (i > 0 && !families.get(i - 1).equals(card(sp).group())) continue;
                all++;
                if (caught(sp)) have++;
            }
            String count = have + "/" + all;
            g.drawString(this.font, fitName(label.getString(), FAM_W - this.font.width(count) - 14),
                    x + 4, y + 4, i == family ? GuiStyle.TEXT : GuiStyle.TEXT_HINT, false);
            g.drawString(this.font, count, x + FAM_W - this.font.width(count) - 4, y + 4,
                    have == all && all > 0 ? 0xFF2E7D32 : GuiStyle.GHOST, false);
        }
    }

    /** §fish-search: type to narrow the list. Click it to focus, Escape or a click elsewhere lets it go. */
    private void renderSearchBox(GuiGraphics g, int mouseX, int mouseY) {
        int x = left + FAM_W + 16, y = top + 56, w = COL_W - 8, h = 14;
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, searchFocus ? 0xFF8A7038 : 0xFF6E5A3C);
        g.fill(x, y, x + w, y + h, 0xFFF3EBD6);
        String shownText = search.isEmpty() && !searchFocus
                ? Component.translatable("journal.riverfishing.search_hint").getString()
                : search;
        int colour = search.isEmpty() && !searchFocus ? GuiStyle.GHOST : GuiStyle.TEXT;
        g.drawString(this.font, fitName(shownText, w - 8), x + 4, y + 3, colour, false);
        if (searchFocus && (System.currentTimeMillis() / 500) % 2 == 0) {
            int cx = x + 4 + this.font.width(fitName(search, w - 8));
            g.fill(cx, y + 2, cx + 1, y + h - 2, GuiStyle.TEXT);
        }
    }

    private void renderSpeciesList(GuiGraphics g, int mouseX, int mouseY) {
        shown.clear();
        for (String sp : ordered) if (inFilter(sp)) shown.add(sp);

        int x = left + FAM_W + 16, y0 = top + LIST_TOP, w = COL_W - 8;
        int rows = listRows();
        scroll = Mth.clamp(scroll, 0, Math.max(0, shown.size() - rows));
        if (shown.isEmpty()) {
            g.drawString(this.font, Component.translatable("journal.riverfishing.search_empty"),
                    x + 4, y0 + 4, GuiStyle.GHOST, false);
            return;
        }
        List<Component> tooltip = null;
        for (int i = 0; i < rows && i + scroll < shown.size(); i++) {
            String sp = shown.get(i + scroll);
            int y = y0 + i * LIST_ROW;
            boolean hov = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + LIST_ROW;
            if (hov) g.fill(x, y, x + w, y + LIST_ROW, 0x22000000);
            if (!caught(sp)) {
                // Undiscovered stays "???" — but it is in its family, so the list still tells you there
                // IS a carp you have not met, which is a goal rather than a blank.
                g.fill(x + 2, y + 1, x + 14, y + 13, 0xFF6B6B6B);
                g.drawString(this.font, "???", x + 20, y + 4, GuiStyle.GHOST, false);
                continue;
            }
            drawFishIcon(g, sp, x + 1, y - 1);
            CompoundTag rec = data.getCompound(key(sp));
            int best = rec.getInt("best");
            com.riverfishing.fish.FishCard c = card(sp);
            boolean trophy = c.present() && best >= c.trophyG() && c.trophyG() > 0;

            // The level the species wants, ahead of the name — the list is sorted by it, so saying it
            // out loud is what turns an order into a reason.
            String lvl = c.present() && c.minLevel() > 0 ? c.minLevel() + " " : "";
            if (!lvl.isEmpty()) {
                g.drawString(this.font, lvl, x + 20, y + 4, 0xFFB05A00, false);
            }
            String right = "x" + rec.getInt("count") + "   " + weight(best);
            int rw = this.font.width(right) + (trophy ? 10 : 0);
            int nameX = x + 20 + this.font.width(lvl);
            g.drawString(this.font, fitName(Component.translatable("fish.riverfishing." + sp).getString(),
                    w - rw - 26 - this.font.width(lvl)), nameX, y + 4,
                    hov ? 0xFF8A5A00 : GuiStyle.TEXT, false);
            g.drawString(this.font, right, x + w - rw - 2, y + 4, GuiStyle.TEXT_HINT, false);
            if (trophy) {
                g.drawString(this.font, "★", x + w - 10, y + 4, 0xFFC89C4A, false);
            }
            if (hov && c.present()) {
                tooltip = new ArrayList<>();
                tooltip.add(Component.translatable("fish.riverfishing." + sp));
                tooltip.add(Component.translatable(
                        com.riverfishing.fish.FishGroup.nameKey(c.group())).copy()
                        .withStyle(ChatFormatting.DARK_GRAY));
                tooltip.add(Component.literal(weight(c.weightMin()) + " – " + weight(c.weightMax()))
                        .withStyle(ChatFormatting.GRAY));
            }
        }
        rowScrollbar(g, y0, rows * LIST_ROW, rows, shown.size());
        if (tooltip != null) g.renderComponentTooltip(this.font, tooltip, mouseX, mouseY);
    }

    /**
     * A scrollbar for a list measured in ROWS.
     *
     * <p>Deliberately not {@link #renderScrollbar}, which measures in pixels off {@code lastCatH}. Feeding
     * a row count into a pixel scrollbar means two counters that have to agree about the same scroll, and
     * every duplicate-and-drift bug in this mod has been exactly that.
     */
    private void rowScrollbar(GuiGraphics g, int y0, int trackH, int rows, int total) {
        if (total <= rows) return;
        int tx = left + W - 5;
        int knobH = Math.max(16, trackH * rows / total);
        int knobY = y0 + (trackH - knobH) * scroll / Math.max(1, total - rows);
        g.fill(tx, y0, tx + 2, y0 + trackH, 0x40000000);
        g.fill(tx, knobY, tx + 2, knobY + knobH, 0xFF8A6E3C);
    }

    // ---- RECORDS ----

    /**
     * §journal-records (0.8.0): what the player has actually done, in one place.
     *
     * <p>All of it was already in the journal tag and none of it was ever added up: the biggest fish you
     * have landed, how far each family has got, how many of your catches were trophies. The fish tab
     * could only ever answer "what about THIS species".
     */
    private void renderRecords(GuiGraphics g, int mouseX, int mouseY) {
        if (families.isEmpty()) buildFamilies();
        int caught = 0, species = 0, trophies = 0;
        List<String> byBest = new ArrayList<>();
        for (String sp : SPECIES) {
            if (!caught(sp)) continue;
            species++;
            CompoundTag rec = data.getCompound(key(sp));
            caught += rec.getInt("count");
            com.riverfishing.fish.FishCard c = card(sp);
            if (c.present() && c.trophyG() > 0 && rec.getInt("best") >= c.trophyG()) trophies++;
            byBest.add(sp);
        }
        byBest.sort((a, b) -> Integer.compare(data.getCompound(key(b)).getInt("best"),
                data.getCompound(key(a)).getInt("best")));

        int y = top + 24;
        long xp = data.getLong(JournalData.XP);
        int level = JournalData.levelForXp(xp);
        g.drawString(this.font, Component.translatable("journal.riverfishing.rec_rank", level,
                Component.translatable("rank.riverfishing." + JournalData.rankKey(level))),
                left + 10, y, GuiStyle.TEXT, false);
        y += 14;

        int colW = (W - 28) / 2;
        y = tile(g, left + 10, y, colW, "journal.riverfishing.rec_caught", Integer.toString(caught));
        tile(g, left + 18 + colW, y - 26, colW, "journal.riverfishing.rec_species",
                species + "/" + SPECIES.length);
        y = tile(g, left + 10, y, colW, "journal.riverfishing.rec_trophies", Integer.toString(trophies));
        tile(g, left + 18 + colW, y - 26, colW, "journal.riverfishing.rec_ice",
                Integer.toString(data.getInt(JournalData.ICE)));
        y += 4;

        // Biggest fish landed, best first. Five is what fits beside the family bars without scrolling.
        int listY = railHead(g, "journal.riverfishing.rec_biggest", left + 10, y, colW);
        for (int i = 0; i < 5 && i < byBest.size(); i++) {
            String sp = byBest.get(i);
            drawFishIcon(g, sp, left + 10, listY - 2);
            String wt = weight(data.getCompound(key(sp)).getInt("best"));
            g.drawString(this.font, fitName(Component.translatable("fish.riverfishing." + sp).getString(),
                    colW - this.font.width(wt) - 24), left + 29, listY + 2, GuiStyle.TEXT, false);
            g.drawString(this.font, wt, left + 10 + colW - this.font.width(wt), listY + 2,
                    GuiStyle.TEXT_HINT, false);
            listY += 15;
        }
        if (byBest.isEmpty()) {
            g.drawString(this.font, Component.translatable("journal.riverfishing.rec_nothing"),
                    left + 10, listY + 2, GuiStyle.GHOST, false);
        }

        // Family completion, so "what is left" is a glance rather than a count.
        int fx = left + 18 + colW;
        int fy = railHead(g, "journal.riverfishing.rec_families", fx, y, colW);
        for (String fam : families) {
            int have = 0, all = 0;
            for (String sp : SPECIES) {
                if (!fam.equals(card(sp).group())) continue;
                all++;
                if (caught(sp)) have++;
            }
            if (all == 0) continue;
            Component label = Component.translatable(com.riverfishing.fish.FishGroup.nameKey(fam));
            String count = have + "/" + all;
            g.drawString(this.font, fitName(label.getString(), colW - this.font.width(count) - 6),
                    fx, fy, GuiStyle.TEXT_HINT, false);
            g.drawString(this.font, count, fx + colW - this.font.width(count), fy,
                    have == all ? 0xFF2E7D32 : GuiStyle.GHOST, false);
            bar(g, fx, fy + 10, colW, 3, have / (float) all,
                    have == all ? 0xFF2E7D32 : 0xFFC89C4A);
            fy += 16;
        }
    }

    /** A boxed number with its caption — the record tab's headline figures. */
    private int tile(GuiGraphics g, int x, int y, int w, String key, String value) {
        g.fill(x, y, x + w, y + 22, 0x18000000);
        g.fill(x, y, x + w, y + 1, 0x22FFFFFF);
        g.drawString(this.font, Component.translatable(key), x + 5, y + 3, GuiStyle.TEXT_HINT, false);
        g.drawString(this.font, value, x + w - this.font.width(value) - 5, y + 11, GuiStyle.TEXT, false);
        return y + 26;
    }

    /** A filled progress bar with the mod's sunken frame. Used by the level, the record and the stats. */
    private void bar(GuiGraphics g, int x, int y, int w, int h, float frac, int colour) {
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFF2A1E12);
        g.fill(x, y, x + w, y + h, 0xFF1E1610);
        g.fill(x, y, x + (int) (w * Mth.clamp(frac, 0f, 1f)), y + h, colour);
    }

    // ---- FISH: detail with illustration ----

    private void renderFishDetail(GuiGraphics g, String sp) {
        ResourceLocation id = RiverFishing.id(sp);
        // fixed header
        drawFishIcon(g, sp, left + 10, top + 22);
        g.drawString(this.font, Component.translatable("fish.riverfishing." + sp),
                left + 30, top + 26, GuiStyle.TEXT, false);
        CompoundTag rec = data.getCompound(key(sp));
        String recStr = "x" + rec.getInt("count") + "  •  " + weight(rec.getInt("best"));
        g.drawString(this.font, recStr, left + W - 10 - this.font.width(recStr), top + 26,
                GuiStyle.TEXT_HINT, false);

        // scrollable body: illustration → description → how-to-catch
        int contentTop = top + 38, contentBottom = top + H - 16;
        int visibleH = contentBottom - contentTop;
        scroll = Mth.clamp(scroll, 0, Math.max(0, lastCatH - visibleH));
        scissorJournal(g, left + 6, contentTop, left + W - 6, contentBottom);
        com.riverfishing.fish.FishCard c = card(sp);
        // §journal-two-column (0.8.0): the illustration and the prose keep the left; the numbers get a
        // rail of their own on the right. The single column meant every number was a sentence, and five
        // sentences of numbers is a page nobody reads to the bottom of.
        int railW = 186;
        int leftW = W - railW - 30;
        int y = contentTop - scroll;
        int railY = y;
        drawIllustration(g, sp, left + 10, y, Math.min(ILLUS_W, leftW), Math.min(ILLUS_W, leftW) * 2 / 3);
        y += Math.min(ILLUS_W, leftW) * 2 / 3 + 8;
        String desc = descText(sp);
        if (!desc.isEmpty()) {
            for (net.minecraft.util.FormattedCharSequence seq : this.font.split(Component.literal(desc), leftW)) {
                g.drawString(this.font, seq, left + 10, y, GuiStyle.TEXT, false);
                y += 11;
            }
            y += 4;
        }
        if (c.present()) {
            railY = statRail(g, sp, c, left + W - railW - 10, railY, railW);
        }
        y = Math.max(y, railY + 4);
        if (c.present()) {
            y = line(g, y, "guide.riverfishing.water", waters(c));
            y = line(g, y, "guide.riverfishing.bait", baits(c));
            y = line(g, y, "guide.riverfishing.tackle", tackle(c));
            y = line(g, y, "guide.riverfishing.best",
                    bestOf(c.seasons(), com.riverfishing.fish.FishCard.SEASONS, "season")
                            + "  •  " + bestOf(c.times(), com.riverfishing.fish.FishCard.TIMES, "time"));
            if (c.minLevel() > 0) {
                g.drawString(this.font, Component.translatable("jei.riverfishing.level", c.minLevel()),
                        left + 10, y, 0xFFB05A00, false);
                y += 12;
            }
        }
        // §guide-nudge: honest bookkeeping. Nothing is withheld and nothing is locked — the record just
        // says this one was landed after the mod offered a hand.
        if (com.riverfishing.fishing.JournalData.wasHinted(data, id)) {
            g.drawString(this.font, Component.translatable("journal.riverfishing.hinted"),
                    left + 10, y, GuiStyle.GHOST, false);
            y += 12;
        }
        y = morphRow(g, sp, id, y);
        lastCatH = (y + scroll) - contentTop;
        lastViewH = contentBottom - contentTop;
        g.disableScissor();
        renderScrollbar(g, contentTop, contentBottom);

        g.drawString(this.font, Component.translatable("guide.riverfishing.back"),
                left + 10, top + H - 14, GuiStyle.GHOST, false);
    }

    /**
     * The right-hand rail of a species page: your record, the groundbait it answers to, the water it can
     * live in, and what it does once it is hooked.
     *
     * <p>Every number here was already being computed and none of it was ever shown. The grind/richness
     * pair in particular arrived with 0.8.0's groundbait and existed only on the wiki, which is a poor
     * place to keep the one fact that decides what your feed catches.
     */
    private int statRail(GuiGraphics g, String sp, com.riverfishing.fish.FishCard c, int x, int y, int w) {
        CompoundTag rec = data.getCompound(key(sp));

        y = railHead(g, "journal.riverfishing.stat_record", x, y, w);
        int best = rec.getInt("best");
        int max = Math.max(1, c.weightMax());
        g.drawString(this.font, weight(best), x, y, GuiStyle.TEXT, false);
        String of = weight(max);
        g.drawString(this.font, of, x + w - this.font.width(of), y, GuiStyle.GHOST, false);
        y += 11;
        bar(g, x, y, w, 4, best / (float) max, best >= c.trophyG() ? 0xFFC89C4A : 0xFF7A9A4A);
        // The trophy bar sits ON the same scale, so "how far off am I" is a look rather than a subtraction.
        int tx = x + (int) (w * Mth.clamp(c.trophyG() / (float) max, 0f, 1f));
        g.fill(tx, y - 2, tx + 1, y + 6, 0xFF8A5A00);
        y += 8;
        g.drawString(this.font, Component.translatable("journal.riverfishing.stat_trophy_at",
                weight(c.trophyG())), x, y, GuiStyle.TEXT_HINT, false);
        y += 14;

        y = railHead(g, "journal.riverfishing.stat_groundbait", x, y, w);
        y = paramTable(g, x, y, w, List.of(
                new Param("journal.riverfishing.stat_grind", c.grind(), 0xFF8A6E3C),
                new Param("journal.riverfishing.stat_richness", c.richness(), 0xFF6E8A3C))) + 4;

        y = railHead(g, "journal.riverfishing.stat_habitat", x, y, w);
        y = railLine(g, "journal.riverfishing.stat_depth", range(c.depthMin(), c.depthMax(), 999), x, y, w);
        y = railLine(g, "journal.riverfishing.stat_width", range(c.widthMin(), c.widthMax(), 99999), x, y, w);
        StringBuilder bio = new StringBuilder();
        for (Map.Entry<String, Float> e : c.biomes().entrySet()) {
            if (e.getValue() <= 0) continue;
            if (bio.length() > 0) bio.append(", ");
            bio.append(Component.translatable("biomegroup.riverfishing." + e.getKey()).getString());
        }
        y = railLine(g, "journal.riverfishing.stat_biomes",
                bio.length() == 0 ? Component.translatable("journal.riverfishing.stat_anywhere").getString()
                        : bio.toString(), x, y, w);
        y += 4;

        y = railHead(g, "journal.riverfishing.stat_fight", x, y, w);
        y = paramTable(g, x, y, w, List.of(
                new Param("journal.riverfishing.stat_strength", c.fightStrength(), 0xFF9A4A3C),
                new Param("journal.riverfishing.stat_stamina", c.fightStamina(), 0xFF3C6E9A))) + 2;
        y = railLine(g, "journal.riverfishing.stat_runs", Integer.toString(c.fightRuns()), x, y, w);
        y = railLine(g, "journal.riverfishing.stat_pattern",
                Component.translatable("fightpattern.riverfishing." + c.fightPattern()).getString(), x, y, w);
        return y;
    }

    /** A rail section heading with a rule under it. */
    private int railHead(GuiGraphics g, String key, int x, int y, int w) {
        g.drawString(this.font, Component.translatable(key), x, y, 0xFF8A5A00, false);
        g.fill(x, y + 10, x + w, y + 11, 0x33000000);
        return y + 14;
    }

    /** A label on the left, its value right-aligned, wrapped onto its own line when it will not fit. */
    private int railLine(GuiGraphics g, String key, String value, int x, int y, int w) {
        Component label = Component.translatable(key);
        g.drawString(this.font, label, x, y, GuiStyle.TEXT_HINT, false);
        int room = w - this.font.width(label) - 6;
        if (this.font.width(value) <= room) {
            g.drawString(this.font, value, x + w - this.font.width(value), y, GuiStyle.TEXT, false);
            return y + 11;
        }
        y += 11;
        for (net.minecraft.util.FormattedCharSequence seq : this.font.split(Component.literal(value), w)) {
            g.drawString(this.font, seq, x, y, GuiStyle.TEXT, false);
            y += 11;
        }
        return y;
    }

    /** "3–8", "4+" or "up to 40" — an open end is stated as open rather than as 999. */
    private static String range(int min, int max, int unbounded) {
        if (max >= unbounded) return min <= 0 ? "—" : min + "+";
        if (min <= 0) return Component.translatable("journal.riverfishing.stat_upto", max).getString();
        return min + "–" + max;
    }

    private static String descText(String sp) {
        String k = "fishdesc.riverfishing." + sp;
        return I18n.exists(k) ? I18n.get(k) : "";
    }

    // ---- QUESTS tab ----

    /** Is this quest's reward already claimed? Server truth (rf_claimed) + optimistic local clicks. */
    private boolean isClaimed(Quests.Quest q) {
        return claimedNow.contains(q.id()) || data.getCompound("rf_claimed").getBoolean(q.id());
    }

    /** §stage-reveal: highest stage the player can see; a stage opens at 70% of the previous (in Quests). */
    private int maxUnlockedStage() {
        return Quests.maxUnlockedStage(data);
    }

    private void renderQuests(GuiGraphics g, int mouseX, int mouseY) {
        int contentTop = top + 24, contentBottom = top + H - 6;
        int visibleH = contentBottom - contentTop;
        scroll = Mth.clamp(scroll, 0, Math.max(0, lastCatH - visibleH));
        scissorJournal(g, left + 6, contentTop, left + W - 6, contentBottom);
        int y = contentTop - scroll;
        // §order-board: the day's order, written out as the recipe for catching it. First on the board
        // because it is the one task that changes every day — and the one that teaches a habitat.
        y = orderBoard(g, y);
        int stage = -1;
        int maxStage = maxUnlockedStage();
        List<Component> tooltip = null;
        for (int i = 0; i < Quests.ALL.size(); i++) {
            Quests.Quest q = Quests.ALL.get(i);
            questRects[i][0] = 0; questRects[i][1] = 0; // reset; locked rows are not clickable
            boolean locked = q.stage() > maxStage;
            if (q.stage() != stage) {
                if (stage != -1) y += 5;
                stage = q.stage();
                g.drawString(this.font, Component.translatable("quest.riverfishing.stage." + stage),
                        left + 10, y, locked ? 0xFF6A5A3A : 0xFFB0842C, false);
                y += 13;
                if (locked) { // §stage-reveal: hide this stage's goals until the previous one is done
                    g.drawString(this.font, Component.translatable("quest.riverfishing.stage_locked", maxStage),
                            left + 12, y, GuiStyle.GHOST, false);
                    y += 13;
                }
            }
            if (locked) continue;
            questRects[i][0] = left + 10;
            questRects[i][1] = y;
            boolean done = q.goal().complete(data);
            boolean claimed = done && isClaimed(q);
            boolean ready = done && !claimed;
            if (ready) { // a claimable reward glows behind the whole row (§quest-claim)
                g.fill(left + 8, y - 2, left + W - 8, y + 11, 0x38E8B430);
            }
            int boxOuter = claimed ? 0xFF3FA34A : (ready ? 0xFFE8B430 : 0xFF3A2A18);
            int boxInner = claimed ? 0xFF57C063 : (ready ? 0xFFFFDE70 : 0xFF241A10);
            g.fill(left + 10, y, left + 18, y + 8, boxOuter);
            g.fill(left + 11, y + 1, left + 17, y + 7, boxInner);
            String title = this.font.plainSubstrByWidth(q.title().getString(), W - 110);
            int tc = claimed ? 0xFF6E5A3C : (ready ? 0xFF9A6E10 : GuiStyle.TEXT);
            g.drawString(this.font, title, left + 24, y, tc, false);
            ItemStack rw = q.rewardStack();
            int rx = left + W - 26;
            if (!rw.isEmpty()) g.renderItem(rw, rx, y - 4);
            if (ready) {
                Component claim = Component.translatable("quest.riverfishing.claim");
                g.drawString(this.font, claim, rx - 6 - this.font.width(claim), y, 0xFFB05A00, false);
            } else if (!done) {
                String prog = q.goal().progress(data);
                if (!prog.isEmpty()) {
                    g.drawString(this.font, prog, rx - 6 - this.font.width(prog), y, GuiStyle.TEXT_HINT, false);
                }
            }
            boolean hov = mouseX >= left + 8 && mouseX < left + W - 8 && mouseY >= y - 2 && mouseY < y + 12
                    && mouseY >= contentTop && mouseY < contentBottom;
            if (hov && !rw.isEmpty()) {
                tooltip = new ArrayList<>();
                tooltip.add(q.title());
                tooltip.add(Component.translatable("quest.riverfishing.reward",
                        rw.getHoverName().copy().append(" x" + rw.getCount())).withStyle(ChatFormatting.GREEN));
                if (ready) {
                    tooltip.add(Component.translatable("quest.riverfishing.claim_hint")
                            .withStyle(ChatFormatting.GOLD));
                }
            }
            y += 15;
        }
        lastCatH = (y + scroll) - contentTop;
        lastViewH = contentBottom - contentTop;
        g.disableScissor();
        renderScrollbar(g, contentTop, contentBottom);
        if (tooltip != null) g.renderComponentTooltip(this.font, tooltip, mouseX, mouseY);
    }

    // ---- SKILLS tab (§skills) ----

    private int anglerLevel() {
        return JournalData.levelForXp(data.getLong(JournalData.XP));
    }

    private int skillRank(com.riverfishing.fishing.AnglerSkills.Perk p) {
        return data.getCompound("skills").getInt(p.id) + spentNow.getOrDefault(p.id, 0);
    }

    private int availablePts() {
        int spent = 0;
        for (var p : com.riverfishing.fishing.AnglerSkills.Perk.values()) spent += skillRank(p);
        return Math.max(0, anglerLevel() - spent);
    }

    /** The current numeric bonus of a perk, as a short "+N%"/"+N" string for the UI. */
    private static String skillBonus(com.riverfishing.fishing.AnglerSkills.Perk p, int rank) {
        return switch (p) {
            case FRUGAL, QUICK_BITE, NATURALIST, STRONG_LINE -> "+" + (rank * 5) + "%";
            case ANGLERS_LUCK, FINESSE -> "+" + (rank * 1) + "%";
        };
    }

    private void renderSkills(GuiGraphics g, int mouseX, int mouseY) {
        var perks = com.riverfishing.fishing.AnglerSkills.Perk.values();
        int avail = availablePts();
        g.drawString(this.font, Component.translatable("journal.riverfishing.skill_points", avail),
                left + 10, top + 24, avail > 0 ? 0xFF3FA34A : GuiStyle.TEXT_HINT, false);

        int contentTop = top + 38, contentBottom = top + H - 6;
        scroll = Mth.clamp(scroll, 0, Math.max(0, lastCatH - (contentBottom - contentTop)));
        scissorJournal(g, left + 6, contentTop, left + W - 6, contentBottom);
        int y = contentTop - scroll;
        List<Component> tooltip = null;
        for (int i = 0; i < perks.length; i++) {
            var p = perks[i];
            int rank = skillRank(p);
            boolean maxed = rank >= p.maxRank;
            boolean canBuy = avail > 0 && !maxed;

            // branch label
            g.drawString(this.font, Component.translatable("skill.riverfishing.branch." + p.branch),
                    left + 10, y, 0xFFB0842C, false);
            y += 11;
            // name + current bonus
            g.drawString(this.font, Component.translatable("skill.riverfishing." + p.id)
                    .append(Component.literal("  " + skillBonus(p, rank))
                            .withStyle(rank > 0 ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY)),
                    left + 12, y, GuiStyle.TEXT, false);
            // rank pips on the right
            int pipsX = left + W - 22 - p.maxRank * 8;
            for (int r = 0; r < p.maxRank; r++) {
                int px = pipsX + r * 8;
                int col = r < rank ? 0xFFE8B430 : 0xFF3A2A18;
                g.fill(px, y, px + 6, y + 6, 0xFF241A10);
                g.fill(px + 1, y + 1, px + 5, y + 5, col);
            }
            y += 11;
            // description line
            String descKey = "skill.riverfishing." + p.id + ".desc";
            for (net.minecraft.util.FormattedCharSequence seq
                    : this.font.split(Component.translatable(descKey), W - 60)) {
                g.drawString(this.font, seq, left + 12, y, GuiStyle.TEXT_HINT, false);
                y += 10;
            }
            // "+" buy button
            skillRects[i][0] = 0; skillRects[i][1] = 0; skillRects[i][2] = 0; skillRects[i][3] = 0;
            int by = y - 20;
            if (canBuy) {
                int bx = left + W - 20;
                boolean hov = mouseX >= bx && mouseX < bx + 12 && mouseY >= by && mouseY < by + 12;
                g.fill(bx, by, bx + 12, by + 12, hov ? 0xFF57C063 : 0xFF3FA34A);
                g.fill(bx + 1, by + 1, bx + 11, by + 11, hov ? 0xFF6FD07B : 0xFF4FB459);
                g.drawCenteredString(this.font, "+", bx + 6, by + 2, 0xFFFFFFFF);
                skillRects[i][0] = bx; skillRects[i][1] = by; skillRects[i][2] = bx + 12; skillRects[i][3] = by + 12;
            } else if (maxed) {
                g.drawString(this.font, Component.translatable("skill.riverfishing.maxed")
                        .withStyle(ChatFormatting.DARK_GREEN), left + W - 20 - this.font.width(
                                Component.translatable("skill.riverfishing.maxed")), by + 2, GuiStyle.GHOST, false);
            }
            y += 8;
        }
        lastCatH = (y + scroll) - contentTop;
        lastViewH = contentBottom - contentTop;
        g.disableScissor();
        renderScrollbar(g, contentTop, contentBottom);
        if (tooltip != null) g.renderComponentTooltip(this.font, tooltip, mouseX, mouseY);
    }

    private void renderScrollbar(GuiGraphics g, int contentTop, int contentBottom) {
        int visibleH = contentBottom - contentTop;
        int maxScroll = Math.max(0, lastCatH - visibleH);
        if (maxScroll <= 0) return;
        int tx = left + W - 5;
        int knobH = Math.max(16, (int) ((long) visibleH * visibleH / lastCatH));
        int knobY = contentTop + (int) ((visibleH - knobH) * (scroll / (float) maxScroll));
        g.fill(tx, contentTop, tx + 2, contentBottom, 0x40000000);
        g.fill(tx, knobY, tx + 2, knobY + knobH, 0xFF8A6E3C);
    }

    private void drawIllustration(GuiGraphics g, String sp, int bx, int by, int bw, int bh) {
        g.fill(bx - 3, by - 3, bx + bw + 3, by + bh + 3, GuiStyle.PANEL_EDGE);
        g.fill(bx - 2, by - 2, bx + bw + 2, by + bh + 2, GuiStyle.TITLE_BAR);
        g.fill(bx - 1, by - 1, bx + bw + 1, by + bh + 1, 0xFF2B2016);
        ResourceLocation tex = RiverFishing.id("textures/gui/journal/fish/" + sp + ".png");
        if (Minecraft.getInstance().getResourceManager().getResource(tex).isPresent()) {
            g.blit(tex, bx, by, bw, bh, 0f, 0f, 16, 16, 16, 16);
        } else {
            g.fill(bx, by, bx + bw, by + bh, 0xFF223038);
            int isz = 64;
            g.blit(fishTex(sp), bx + (bw - isz) / 2, by + (bh - isz) / 2 - 6, isz, isz, 0f, 0f, 16, 16, 16, 16);
            Component hint = Component.translatable("journal.riverfishing.no_illustration");
            g.drawString(this.font, hint, bx + (bw - this.font.width(hint)) / 2, by + bh - 14, GuiStyle.GHOST, false);
        }
    }

    // ---- BAIT / GEAR: scrolling sectioned catalog ----

    private void renderCatalog(GuiGraphics g, List<Cat> list, int mouseX, int mouseY) {
        int contentTop = top + 38, contentBottom = top + H - 6;
        int visibleH = contentBottom - contentTop;
        scroll = Mth.clamp(scroll, 0, Math.max(0, lastCatH - visibleH));

        scissorJournal(g, left + 6, contentTop, left + W - 6, contentBottom);
        int y = contentTop - scroll;
        int col = 0;
        Kind section = null;
        // §guide-order: the guide shelf is all one Kind, so it breaks on its PROGRESSION GROUP instead.
        // Everything else still breaks on kind; one variable, two meanings of "a new heading is due".
        int gsection = -1;
        List<Component> tooltip = null;
        for (int i = 0; i < list.size(); i++) {
            Cat e = list.get(i);
            int gnow = guideGroup.getOrDefault(e.id(), -1);
            boolean newSection = tab == TAB_GUIDE ? gnow != gsection : e.kind() != section;
            if (newSection) {
                if (col != 0) { y += ROW_H; col = 0; }
                if (i != 0) y += 3;
                section = e.kind();
                gsection = gnow;
                Component headText = tab == TAB_GUIDE
                        ? Component.translatable("guidegroup.riverfishing." + gnow)
                        : Component.translatable(sectionKey(section));
                g.drawString(this.font, headText, left + 10, y, 0xFFB0842C, false);
                y += 12;
            }
            int x = left + 10 + col * catColW;
            catRects[i][0] = x;
            catRects[i][1] = y;
            g.renderItem(e.stack(), x, y);
            String name = fitName(e.stack().getHoverName().getString(), catColW - 24);
            boolean hov = mouseX >= x && mouseX < x + catColW - 8 && mouseY >= y && mouseY < y + ROW_H - 1
                    && mouseY >= contentTop && mouseY < contentBottom;
            g.drawString(this.font, name, x + 20, y + 4, hov ? 0xFFB8860B : GuiStyle.TEXT, false);
            if (hov) tooltip = catTooltip(e);
            if (++col >= CAT_COLS) { col = 0; y += ROW_H; }
        }
        if (col != 0) y += ROW_H;
        lastCatH = (y + scroll) - contentTop;
        lastViewH = contentBottom - contentTop;
        g.disableScissor();

        int maxScroll = Math.max(0, lastCatH - visibleH);
        if (maxScroll > 0) {
            int tx = left + W - 5;
            int knobH = Math.max(16, (int) ((long) visibleH * visibleH / lastCatH));
            int knobY = contentTop + (int) ((visibleH - knobH) * (scroll / (float) maxScroll));
            g.fill(tx, contentTop, tx + 2, contentBottom, 0x40000000);
            g.fill(tx, knobY, tx + 2, knobY + knobH, 0xFF8A6E3C);
        }
        if (tooltip != null) g.renderComponentTooltip(this.font, tooltip, mouseX, mouseY);
    }

    private void renderCatDetail(GuiGraphics g, Cat e, int mouseX, int mouseY) {
        g.renderItem(e.stack(), left + 10, top + 22);
        g.drawString(this.font, e.stack().getHoverName(), left + 30, top + 26, GuiStyle.TEXT, false);
        g.drawString(this.font, Component.translatable(kindKey(e.kind())), left + 10, top + 44,
                GuiStyle.TEXT_HINT, false);

        // §gb-pantry: anything that can go in a mix says what it does to one, right here. It is the same
        // pantry the engine averages, so a bait that is ALSO a component gets its numbers on its own page
        // rather than in a second copy of itself further down the list.
        com.riverfishing.groundbait.GroundbaitMix.Component comp =
                com.riverfishing.groundbait.GroundbaitMix.PANTRY.get(e.id());
        if (comp != null && e.kind() != Kind.GUIDE) {
            int rx = left + W - 176, ry = top + 22;
            ry = railHead(g, "journal.riverfishing.stat_groundbait", rx, ry, 166);
            float pullOf = predatorPull(comp.diet());
            List<Param> rows = new ArrayList<>();
            rows.add(new Param("journal.riverfishing.stat_grind", (float) comp.fraction(), 0xFF8A6E3C));
            rows.add(new Param("journal.riverfishing.stat_richness", (float) comp.nutrition(), 0xFF6E8A3C));
            if (pullOf >= 0) {
                rows.add(new Param("journal.riverfishing.gb_predation", pullOf, pullColour(pullOf)));
            }
            ry = paramTable(g, rx, ry, 166, rows) + 4;
            // §gb-attracts: this used to print "reads as dough", which is the engine's internal wiring
            // said out loud — a player reads it as "potato IS dough" and is right to be baffled. What
            // they actually want to know is who turns up, so name the fish.
            String who = pullOf < 0
                    ? Component.translatable("journal.riverfishing.gb_ballast").getString()
                    : String.join(", ", fishForDiet(comp.diet(), 8));
            g.drawString(this.font, Component.translatable("journal.riverfishing.gb_attracts"),
                    rx, ry, GuiStyle.TEXT_HINT, false);
            ry += 11;
            for (net.minecraft.util.FormattedCharSequence seq
                    : this.font.split(Component.literal(who.isEmpty() ? "—" : who), 166)) {
                g.drawString(this.font, seq, rx, ry, GuiStyle.TEXT, false);
                ry += 10;
            }
        }

        // §guide-page (0.5.0): a guide is a TEXT page — no giant icon, no "how to craft" of whatever
        // item happens to illustrate it. Just the how-to, scrollable, with breathing room per line.
        if (e.kind() == Kind.GUIDE) {
            int contentTop = top + 58, contentBottom = top + H - 20;
            scroll = Mth.clamp(scroll, 0, Math.max(0, lastCatH - (contentBottom - contentTop)));
            scissorJournal(g, left + 6, contentTop, left + W - 6, contentBottom);
            int dy = contentTop - scroll;
            String bk = "guide.riverfishing." + e.id() + ".text";
            for (net.minecraft.util.FormattedCharSequence seq
                    : this.font.split(Component.translatable(bk), W - 24)) {
                g.drawString(this.font, seq, left + 10, dy, GuiStyle.TEXT, false);
                dy += 12;
            }
            dy = guideBars(g, e.id(), dy + 4);
            dy = guideTable(g, e.id(), dy + 4);
            lastCatH = (dy + scroll) - contentTop;
            lastViewH = contentBottom - contentTop;
            g.disableScissor();
            renderScrollbar(g, contentTop, contentBottom);
            // §discord: a real button, pinned outside the scrolled area — a call to action that scrolls
            // out of reach is not one. Only this guide has a link, so only this guide gets a button.
            linkRect[0] = linkRect[1] = linkRect[2] = linkRect[3] = 0;
            if ("discord".equals(e.id())) {
                Component label = Component.translatable("guide.riverfishing.discord.button");
                int bw = this.font.width(label) + 14, bh = 14;
                int bx = left + 10, by = top + H - 32;
                boolean hov = mouseX >= bx && mouseX < bx + bw && mouseY >= by && mouseY < by + bh;
                g.fill(bx, by, bx + bw, by + bh, hov ? 0xFF57C063 : 0xFF3FA34A);
                g.fill(bx + 1, by + 1, bx + bw - 1, by + bh - 1, hov ? 0xFF6FD07B : 0xFF4FB459);
                g.drawCenteredString(this.font, label, bx + bw / 2, by + 3, 0xFFFFFFFF);
                linkRect[0] = bx; linkRect[1] = by; linkRect[2] = bx + bw; linkRect[3] = by + bh;
            }
            g.drawString(this.font, Component.translatable("guide.riverfishing.back"),
                    left + 10, top + H - 14, GuiStyle.GHOST, false);
            return;
        }

        float s = 5f;
        g.pose().pushPose();
        g.pose().translate(left + W / 2f - 8 * s, top + 60, 0);
        g.pose().scale(s, s, s);
        g.renderItem(e.stack(), 0, 0);
        g.pose().popPose();

        // §bait-desc: the wrapped flavour text under the big icon.
        if (isBait(e.kind())) {
            String bk = "baitdesc.riverfishing." + e.id();
            if (I18n.exists(bk)) {
                int dy = top + 104;
                for (net.minecraft.util.FormattedCharSequence seq : this.font.split(Component.translatable(bk), W - 20)) {
                    g.drawString(this.font, seq, left + 10, dy, GuiStyle.TEXT_HINT, false);
                    dy += 10;
                }
            }
        }

        // §gb-table: the base's own page carries the whole pantry, because "what can I put in this"
        // is the only question anyone opens the base to ask. Scrollable — it is twenty-six rows.
        if (e.kind() == Kind.GROUNDBAIT) {
            renderPantryTable(g);
            g.drawString(this.font, Component.translatable("guide.riverfishing.back"),
                    left + 10, top + H - 14, GuiStyle.GHOST, false);
            return;
        }

        // §lure-size: the one number that decides what takes a lure, and the trap that it is usually 0.
        if (e.kind() == Kind.LURE) {
            int rx = left + W - 176, ry = top + 22;
            ry = railHead(g, "journal.riverfishing.lw_size_head", rx, ry, 166);
            for (int grams : new int[]{10, 20, 50, 100, 200}) {
                // opt(kg) = 0.5 * sqrt(g) — BiteEngine's own §round-6 curve, not a table I typed.
                String kg = String.format(java.util.Locale.ROOT, "%.1f", 0.5 * Math.sqrt(grams));
                g.drawString(this.font, grams + " g", rx, ry, GuiStyle.TEXT_HINT, false);
                drawRight(g, Component.literal("~" + kg + " kg"), rx + 166, ry, GuiStyle.TEXT);
                ry += 11;
            }
            ry += 4;
            for (net.minecraft.util.FormattedCharSequence seq : this.font.split(
                    Component.translatable("journal.riverfishing.lw_size_note"), 166)) {
                g.drawString(this.font, seq, rx, ry, 0xFFB05A00, false);
                ry += 10;
            }
            g.drawString(this.font, Component.translatable(retrieveKey(e.id())),
                    left + 10, top + 58, 0xFF8A5A00, false);
        }

        int y = top + 148;
        y = obtainRender(g, y, e.stack()) + 4;

        if (e.kind() == Kind.ROD || e.kind() == Kind.REEL || e.kind() == Kind.LINE) {
            y = compatLines(g, y, e) + 2;
        }

        if (isBait(e.kind())) {
            g.drawString(this.font, Component.translatable("journal.riverfishing.bait_catches"),
                    left + 10, y, GuiStyle.TEXT_HINT, false);
            y += 12;
            List<String> fish = fishFor(e, 12);
            String list = fish.isEmpty() ? "—" : String.join(", ", fish);
            for (net.minecraft.util.FormattedCharSequence seq : this.font.split(Component.literal(list), W - 20)) {
                g.drawString(this.font, seq, left + 10, y, GuiStyle.TEXT, false);
                y += 11;
            }
        }
        g.drawString(this.font, Component.translatable("guide.riverfishing.back"),
                left + 10, top + H - 14, GuiStyle.GHOST, false);
    }

    /**
     * §gb-table: every component of a mix, with what it does to one.
     *
     * <p>Four columns, because four things decide whether an ingredient belongs in your jar: how rich it
     * is, how coarse it is, and which way it pulls the swim. The numbers are the pantry's own — the same
     * map the engine averages when it scores a fed spot — so this table cannot drift from the game.
     */
    private void renderPantryTable(GuiGraphics g) {
        int x = left + 10, wAll = W - 26;
        int nameW = wAll - 40 - 40 - 96;
        int nutX = x + nameW, fracX = nutX + 40, pullX = fracX + 40;

        int head = top + 62;
        g.drawString(this.font, Component.translatable("journal.riverfishing.gb_col_part"), x, head,
                0xFFB0842C, false);
        drawRight(g, Component.translatable("journal.riverfishing.gb_col_rich"), nutX + 34, head, 0xFFB0842C);
        drawRight(g, Component.translatable("journal.riverfishing.gb_col_grind"), fracX + 34, head, 0xFFB0842C);
        drawRight(g, Component.translatable("journal.riverfishing.gb_col_pull"), pullX + 90, head, 0xFFB0842C);
        g.fill(x, head + 10, x + wAll, head + 11, 0x33000000);

        int contentTop = head + 14, contentBottom = top + H - 18;
        scroll = Mth.clamp(scroll, 0, Math.max(0, lastCatH - (contentBottom - contentTop)));
        scissorJournal(g, left + 6, contentTop, left + W - 6, contentBottom);
        int y = contentTop - scroll;
        for (com.riverfishing.groundbait.GroundbaitMix.Component c
                : com.riverfishing.groundbait.GroundbaitMix.PANTRY.values()) {
            ItemStack stack = pantryStack(c.id());
            if (stack.isEmpty()) continue;
            g.renderItem(stack, x, y - 1);
            g.drawString(this.font, fitName(stack.getHoverName().getString(), nameW - 22), x + 20, y + 3,
                    GuiStyle.TEXT, false);
            drawRight(g, Component.literal(String.format(java.util.Locale.ROOT, "%.2f", c.nutrition())),
                    nutX + 34, y + 3, GuiStyle.TEXT_HINT);
            drawRight(g, Component.literal(String.format(java.util.Locale.ROOT, "%.2f", c.fraction())),
                    fracX + 34, y + 3, GuiStyle.TEXT_HINT);
            float pull = predatorPull(c.diet());
            if (pull < 0) {
                drawRight(g, Component.translatable("journal.riverfishing.gb_ballast_short"),
                        pullX + 90, y + 3, GuiStyle.GHOST);
            } else {
                // Bar then number, the same shape as every other row in the journal — and the bar's
                // colour walks green→red, so the column reads at a glance without a legend.
                bar(g, pullX, y + 5, 54, 4, pull, pullColour(pull));
                drawRight(g, Component.literal(String.format(java.util.Locale.ROOT, "%.2f", pull)),
                        pullX + 90, y + 3, GuiStyle.TEXT);
            }
            y += 17;
        }
        lastCatH = (y + scroll) - contentTop;
        lastViewH = contentBottom - contentTop;
        g.disableScissor();
        renderScrollbar(g, contentTop, contentBottom);
    }

    private void drawRight(GuiGraphics g, Component text, int rightX, int y, int colour) {
        g.drawString(this.font, text, rightX - this.font.width(text), y, colour, false);
    }

    /**
     * §tackle-compat: the rod↔reel↔line compatibility a player needs to assemble a working rod. Rods list
     * the reel band + line window; reels list the rods they fit + the thickest line they spool; lines list
     * the smallest reel that can hold them.
     */
    private int compatLines(GuiGraphics g, int y, Cat e) {
        Item it = e.stack().getItem();
        if (it instanceof RodItem rod) {
            var rt = rod.rodType();
            String reels = rt.takesReel()
                    ? rt.minReel() + "–" + rt.maxReel()
                    : Component.translatable("journal.riverfishing.compat_no_reel").getString();
            y = line(g, y, "journal.riverfishing.compat_reel", reels);
        } else if (it instanceof ReelItem reel) {
            int size = reel.size();
            StringBuilder rods = new StringBuilder();
            for (com.riverfishing.component.RodType rt : com.riverfishing.component.RodType.values()) {
                if (rt.acceptsReelSize(size)) {
                    if (rods.length() > 0) rods.append(", ");
                    rods.append(Component.translatable("item.riverfishing." + rt.jsonKey() + "_rod").getString());
                }
            }
            y = line(g, y, "journal.riverfishing.compat_rods", rods.length() == 0 ? "—" : rods.toString());
            double maxDia = com.riverfishing.component.TackleCompat.maxLineDiameter(size);
            y = line(g, y, "journal.riverfishing.compat_line", String.format("≤ %.2f", maxDia));
        } else if (it instanceof LineItem line) {
            int minReel = com.riverfishing.component.TackleCompat.minReelForLine(line.diameterMm());
            String reels = minReel == 0 ? "—" : (minReel + "+");
            y = line(g, y, "journal.riverfishing.compat_reel_from", reels);
        }
        return y;
    }

    /** "How to get": the crafting recipe's ingredients when one exists, else a generic hint. */
    private int obtainRender(GuiGraphics g, int y, ItemStack stack) {
        List<String> ings = craftIngredients(stack);
        if (!ings.isEmpty()) {
            g.drawString(this.font, Component.translatable("journal.riverfishing.obtain_craft"),
                    left + 10, y, GuiStyle.TEXT_HINT, false);
            y += 12;
            for (net.minecraft.util.FormattedCharSequence seq
                    : this.font.split(Component.literal(String.join(", ", ings)), W - 20)) {
                g.drawString(this.font, seq, left + 10, y, GuiStyle.TEXT, false);
                y += 11;
            }
        } else {
            for (net.minecraft.util.FormattedCharSequence seq
                    : this.font.split(Component.translatable("journal.riverfishing.obtain_other"), W - 20)) {
                g.drawString(this.font, seq, left + 10, y, GuiStyle.TEXT, false);
                y += 11;
            }
        }
        return y;
    }

    /**
     * §groundbait-one-jar: groundbait is NOT in here any more.
     *
     * <p>"This groundbait attracts bream, roach, tench" was a true sentence when there were four jars and
     * every fish named the ones it liked. With one jar it would be a lie in either direction: the jar
     * attracts nothing on its own, and everything once you mix. The entry shows its recipe like a piece
     * of gear does, and the two guide pages are where the actual answer lives.
     */
    private static boolean isBait(Kind k) {
        return k == Kind.NATURAL || k == Kind.LURE;
    }

    private List<Component> catTooltip(Cat e) {
        List<Component> t = new ArrayList<>();
        t.add(e.stack().getHoverName());
        t.add(Component.translatable(kindKey(e.kind())).withStyle(ChatFormatting.GRAY));
        if (isBait(e.kind())) {
            List<String> fish = fishFor(e, 6);
            if (!fish.isEmpty()) {
                t.add(Component.translatable("journal.riverfishing.bait_catches"));
                t.add(Component.literal(String.join(", ", fish)).withStyle(ChatFormatting.DARK_GREEN));
            }
        } else {
            List<String> ings = craftIngredients(e.stack());
            if (!ings.isEmpty()) {
                t.add(Component.translatable("journal.riverfishing.obtain_craft"));
                t.add(Component.literal(String.join(", ", ings)).withStyle(ChatFormatting.DARK_GREEN));
            }
        }
        return t;
    }

    private static List<String> fishFor(Cat e, int limit) {
        return FishProfileManager.get().all().stream()
                .filter(p -> p.baitScore(e.id()) >= 0.5)
                .sorted((a, b) -> Double.compare(b.baitScore(e.id()), a.baitScore(e.id())))
                .limit(limit)
                .map(p -> Component.translatable("fish.riverfishing." + p.id.getPath()).getString())
                .collect(Collectors.toList());
    }

    /** Distinct ingredient names of the first crafting recipe that yields this item, or empty. */
    private static List<String> craftIngredients(ItemStack stack) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return List.of();
        // §groundbait-one-jar: the jar IS craftable again — wheat + wheat seeds — so the generic scan
        // below finds it and prints the two ingredients, the same as for any piece of gear. The oil cake's
        // hand-written sunflower-and-piston recipe used to live here; that item no longer exists.
        for (net.minecraft.world.item.crafting.Recipe<?> holder : mc.level.getRecipeManager().getRecipes()) {
            ItemStack res;
            try {
                res = holder.getResultItem(mc.level.registryAccess());
            } catch (Throwable ignored) {
                continue;
            }
            if (res == null || res.isEmpty() || res.getItem() != stack.getItem()) continue;
            NonNullList<Ingredient> ings = holder.getIngredients();
            if (ings.isEmpty()) continue;
            LinkedHashSet<String> names = new LinkedHashSet<>();
            for (Ingredient ing : ings) {
                if (ing.isEmpty()) continue;
                ItemStack[] arr = ing.getItems();
                if (arr.length > 0) names.add(arr[0].getHoverName().getString());
            }
            if (!names.isEmpty()) return new ArrayList<>(names);
        }
        return List.of();
    }

    private static String sectionKey(Kind k) {
        return switch (k) {
            case NATURAL -> "journal.riverfishing.sec_natural";
            case LURE -> "journal.riverfishing.sec_lure";
            case GROUNDBAIT -> "journal.riverfishing.sec_groundbait";
            // The bait shelf is a table now, so this heading is never drawn — but the switch has to be
            // total, and pointing it at a string I deleted is how a missing key gets shipped.
            case GB_PART -> "journal.riverfishing.kind_gbpart";

            case ROD -> "journal.riverfishing.sec_rod";
            case REEL -> "journal.riverfishing.sec_reel";
            case LINE -> "journal.riverfishing.sec_line";
            case RIG -> "journal.riverfishing.sec_rig";
            case GUIDE -> "journal.riverfishing.kind_guide";
        };
    }

    private static String kindKey(Kind k) {
        return switch (k) {
            case NATURAL -> "journal.riverfishing.bait_natural";
            case LURE -> "journal.riverfishing.bait_artificial";
            case GROUNDBAIT -> "journal.riverfishing.bait_groundbait";
            case GB_PART -> "journal.riverfishing.kind_gbpart";
            case GUIDE -> "journal.riverfishing.kind_guide";
            default -> sectionKey(k); // gear: use the section name as the category label
        };
    }

    // ---- shared helpers ----

    private static ResourceLocation fishTex(String sp) {
        return RiverFishing.id("textures/item/fish/" + sp + ".png");
    }

    /** Fish are builtin/entity items whose BEWLR the GUI shades dark; blit the texture directly instead. */
    private void drawFishIcon(GuiGraphics g, String sp, int x, int y) {
        g.blit(fishTex(sp), x, y, 16, 16, 0f, 0f, 16, 16, 16, 16);
    }

    /**
     * §morph: this species' own variant list — every documented form it can show, and whether you have
     * found it. This is where the mod turns 79 species into a collection several times that size without
     * a single new drawing: each row is the species' own icon under the morph's tint.
     *
     * <p>They live on the species page rather than as extra cells in the grid on purpose. The grid sizes
     * its columns to fit the panel, and two hundred cells would shred it — and a variant belongs next to
     * the fish it is a variant OF, not scattered through the alphabet.
     */
    private int morphRow(GuiGraphics g, String sp, ResourceLocation id, int y) {
        java.util.List<com.riverfishing.fish.FishMorph.Def> morphs =
                com.riverfishing.fish.FishMorph.forSpecies(sp);
        if (morphs.isEmpty()) return y;
        int found = 0;
        for (var d : morphs) {
            if (com.riverfishing.fishing.JournalData.hasMorph(data, id, d.id())) found++;
        }
        y += 6;
        g.drawString(this.font, Component.translatable("journal.riverfishing.morphs", found, morphs.size()),
                left + 10, y, GuiStyle.TEXT_HINT, false);
        y += 13;
        for (var d : morphs) {
            boolean have = com.riverfishing.fishing.JournalData.hasMorph(data, id, d.id());
            if (have) {
                // The icon is the species' own, under the morph's own multiply — the same number the
                // fish in your hand and the fish in the water are painted with.
                int t = d.tint();
                g.setColor(((t >> 16) & 0xFF) / 255f, ((t >> 8) & 0xFF) / 255f, (t & 0xFF) / 255f, 1f);
                drawFishIcon(g, sp, left + 12, y - 4);
                g.setColor(1f, 1f, 1f, 1f);
            }
            g.drawString(this.font,
                    have ? Component.translatable("morph.riverfishing." + d.id()) : Component.literal("???"),
                    left + 32, y, have ? GuiStyle.TEXT : GuiStyle.GHOST, false);
            y += 14;
        }
        return y;
    }

    private int line(GuiGraphics g, int y, String labelKey, String value) {
        Component label = Component.translatable(labelKey);
        g.drawString(this.font, label, left + 10, y, GuiStyle.TEXT_HINT, false);
        int vx = left + 14 + this.font.width(label);
        for (net.minecraft.util.FormattedCharSequence seq
                : this.font.split(Component.literal(value.isEmpty() ? "—" : value), W - (vx - left) - 10)) {
            g.drawString(this.font, seq, vx, y, GuiStyle.TEXT, false);
            y += 11;
        }
        return y + 2;
    }

    private static String weight(int g) {
        return com.riverfishing.item.FishItem.weightLabel(g); // §i18n: localized units (kg/g ↔ кг/г)
    }

    /**
     * §order-board: today's order as a checklist — habitat, depth, season, hour, bait, rig, rod — with a
     * tick against every condition the player already meets where they stand.
     *
     * <p>The rows arrive from the server as LANG KEYS, never as sentences, so this draws them in the
     * reader's own language and works on a multiplayer client, which has no fish profiles at all. The tick
     * state is a snapshot taken when the journal was opened: this is a book you consult, not a HUD.
     */
    private int orderBoard(GuiGraphics g, int y) {
        CompoundTag order = data.getCompound("order");
        if (order.isEmpty() || !order.contains("rows")) return y;

        String sp = order.getString("species");
        g.drawString(this.font, Component.translatable("journal.riverfishing.order_of_the_day"),
                left + 10, y, 0xFFB0842C, false);
        y += 12;
        drawFishIcon(g, sp, left + 10, y - 3);
        g.drawString(this.font, Component.translatable("fish.riverfishing." + sp), left + 30, y,
                GuiStyle.TEXT, false);
        y += 15;

        ListTag rows = order.getList("rows", 10);
        for (int i = 0; i < rows.size(); i++) {
            CompoundTag r = rows.getCompound(i);
            boolean info = r.getBoolean("info");
            boolean ok = r.getBoolean("ok");
            // A tick, an empty box, or a dash for a line that states a fact rather than sets a condition.
            String mark = info ? "-" : ok ? "\u2714" : "\u2610";
            int mc = info ? GuiStyle.GHOST : ok ? 0xFF2E7D32 : GuiStyle.TEXT_HINT;
            g.drawString(this.font, mark, left + 12, y, mc, false);

            Component label = Component.translatable(r.getString("l"));
            g.drawString(this.font, label, left + 24, y, GuiStyle.TEXT_HINT, false);
            int vx = left + 28 + this.font.width(label);

            String value;
            if (r.contains("t")) {
                value = r.getString("t");
            } else {
                StringBuilder sb = new StringBuilder();
                ListTag keys = r.getList("v", 8);
                for (int k = 0; k < keys.size(); k++) {
                    if (sb.length() > 0) sb.append(", ");
                    sb.append(Component.translatable(keys.getString(k)).getString());
                }
                value = sb.length() == 0 ? "\u2014" : sb.toString();
            }
            for (net.minecraft.util.FormattedCharSequence seq
                    : this.font.split(Component.literal(value), W - (vx - left) - 12)) {
                g.drawString(this.font, seq, vx, y, ok || info ? GuiStyle.TEXT : GuiStyle.TEXT_HINT, false);
                y += 11;
            }
        }

        // The ladder: a fixed spine under the daily churn, so the grind visibly goes somewhere.
        int filled = order.getInt("filled");
        int every = Math.max(1, order.getInt("every"));
        ListTag ladder = order.getList("ladder", 8);
        y += 4;
        g.drawString(this.font, Component.translatable("journal.riverfishing.order_progress", filled),
                left + 10, y, GuiStyle.TEXT_HINT, false);
        y += 12;
        int lx = left + 12;
        for (int i = 0; i < ladder.size(); i++) {
            int at = (i + 1) * every;
            boolean got = filled >= at;
            ItemStack stack = new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM
                    .get(RiverFishing.id(ladder.getString(i))));
            if (!stack.isEmpty()) {
                g.renderFakeItem(stack, lx, y);
                if (!got) g.fill(lx, y, lx + 16, y + 16, 0xA0202020);   // a rung still ahead of you
                String n = String.valueOf(at);
                g.drawString(this.font, n, lx + 16 - this.font.width(n), y + 10,
                        got ? 0xFF2E7D32 : GuiStyle.GHOST, false);
            }
            lx += 20;
        }
        return y + 22;
    }

    // §journal-card: these read the SERVER-SENT card, not a fish profile. The client has no profiles on
    // a dedicated server, which is why every one of these lines used to be blank in multiplayer.

    private static String waters(com.riverfishing.fish.FishCard c) {
        StringBuilder sb = new StringBuilder();
        float[] w = c.waters();
        for (int i = 0; i < w.length; i++) {
            if (w[i] <= 0) continue;
            if (sb.length() > 0) sb.append(", ");
            sb.append(Component.translatable(
                    "water.riverfishing." + com.riverfishing.fish.FishCard.WATERS[i]).getString());
        }
        return sb.toString();
    }

    /** The best entry of a fixed-order table, named in the player's language. */
    private static String bestOf(float[] table, String[] names, String prefix) {
        int at = com.riverfishing.fish.FishCard.best(table);
        return at < 0 ? "—" : Component.translatable(prefix + ".riverfishing." + names[at]).getString();
    }

    private static String baits(com.riverfishing.fish.FishCard c) {
        return c.baitsRanked().stream()
                .limit(3)
                .map(e -> Component.translatable("item.riverfishing." + e.getKey()).getString())
                .reduce((a, b) -> a + ", " + b).orElse("—");
    }

    private static String tackle(com.riverfishing.fish.FishCard c) {
        StringBuilder sb = new StringBuilder();
        for (String rod : c.rods()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(Component.translatable("item.riverfishing." + rod + "_rod").getString());
        }
        for (String rig : c.rigs()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(Component.translatable("item.riverfishing.rig_" + rig).getString());
        }
        return sb.toString();
    }

    // ---- input ----

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        // §fish-list: the species list counts in ROWS and everything else counts in PIXELS. They share
        // one `scroll`, so the wheel has to be told which unit it is turning — mixing them is how a list
        // ends up scrolling nineteen species per notch.
        if (tab == TAB_FISH && detail == null) {
            scroll = Mth.clamp(scroll - (int) Math.signum(delta) * 3, 0,
                    Math.max(0, shown.size() - listRows()));
            return true;
        }
        // §journal-scroll: everything except the fish list scrolls in pixels — INCLUDING a detail page.
        //
        // It used to read `catDetail < 0`, which excluded exactly the pages that need it most: every
        // guide, every bait page, every gear page. They each measured their content and drew a
        // scrollbar, and then the wheel refused to move them, so anything past the bottom of the
        // parchment was simply unreachable. A page that renders a scrollbar and will not scroll is the
        // clearest possible statement that the two halves were never asked to agree.
        //
        // The clamp uses the viewport the LAST RENDER actually measured rather than a hardcoded H-44:
        // the guide page, the catalog and the species page each start at a different y, so one constant
        // was wrong for at least two of them — cutting some pages short and letting others overscroll.
        scroll = Mth.clamp(scroll - (int) (delta * 18), 0, Math.max(0, lastCatH - lastViewH));
        return true;
    }

    /** §fish-search: characters go to the filter box while it holds the keyboard, and nowhere else. */
    @Override
    public boolean charTyped(char ch, int modifiers) {
        if (tab == TAB_FISH && detail == null && searchFocus && ch >= ' ' && search.length() < 24) {
            search += ch;
            scroll = 0;
            return true;
        }
        return super.charTyped(ch, modifiers);
    }

    @Override
    public boolean keyPressed(int key, int scancode, int modifiers) {
        if (tab == TAB_FISH && detail == null && searchFocus) {
            if (key == 259 && !search.isEmpty()) {          // backspace
                search = search.substring(0, search.length() - 1);
                scroll = 0;
                return true;
            }
            if (key == 256) {                                // escape closes the box, not the journal
                if (!search.isEmpty()) {
                    search = "";
                    scroll = 0;
                } else {
                    searchFocus = false;
                }
                return true;
            }
        }
        return super.keyPressed(key, scancode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // §journal-scale: hit-test in journal space (the panel is drawn scaled around the screen centre).
        mouseX = toJournalX(mouseX);
        mouseY = toJournalY(mouseY);
        if (button == 0) {
            for (int i = 0; i < TAB_KEYS.length; i++) {
                int x = tabX(i), w = tabW(i);
                if (mouseX >= x && mouseX < x + w && mouseY >= top + 3 && mouseY < top + 18) {
                    if (tab != i) { tab = i; catDetail = -1; scroll = 0; detail = null; }
                    return true;
                }
            }
            if (tab == TAB_FISH) {
                if (detail != null) { detail = null; scroll = 0; return true; }
                if (families.isEmpty()) buildFamilies();
                // The search box takes the keyboard on a click and gives it up on a click anywhere else,
                // so typing "щук" never eats a keystroke the rest of the screen wanted.
                int sx = left + FAM_W + 16, sy = top + 56;
                boolean onSearch = mouseX >= sx - 1 && mouseX < sx + COL_W - 7
                        && mouseY >= sy - 1 && mouseY < sy + 15;
                searchFocus = onSearch;
                if (onSearch) return true;
                for (int i = 0; i <= families.size(); i++) {
                    int y = top + LIST_TOP + i * LIST_ROW;
                    if (mouseX >= left + 8 && mouseX < left + 8 + FAM_W && mouseY >= y && mouseY < y + LIST_ROW) {
                        family = i;
                        scroll = 0;
                        return true;
                    }
                }
                for (int i = 0; i < listRows() && i + scroll < shown.size(); i++) {
                    int y = top + LIST_TOP + i * LIST_ROW;
                    if (mouseX >= sx && mouseX < sx + COL_W - 8 && mouseY >= y && mouseY < y + LIST_ROW
                            && caught(shown.get(i + scroll))) {
                        detail = shown.get(i + scroll);
                        scroll = 0;
                        return true;
                    }
                }
            } else if (tab == TAB_QUEST) {
                int contentTop = top + 24, contentBottom = top + H - 6;
                for (int i = 0; i < Quests.ALL.size(); i++) {
                    int x = questRects[i][0], y = questRects[i][1];
                    if (mouseX >= x - 2 && mouseX < left + W - 8 && mouseY >= y - 2 && mouseY < y + 12
                            && mouseY >= contentTop && mouseY < contentBottom) {
                        Quests.Quest q = Quests.ALL.get(i);
                        if (q.goal().complete(data) && !isClaimed(q)) {
                            claimedNow.add(q.id()); // optimistic; the server validates and grants
                            com.riverfishing.network.ModNetwork.toServer(
                                    new com.riverfishing.network.QuestClaimPacket(q.id()));
                        }
                        return true;
                    }
                }
            } else if (tab == TAB_SKILL) {
                var perks = com.riverfishing.fishing.AnglerSkills.Perk.values();
                for (int i = 0; i < perks.length; i++) {
                    int[] r = skillRects[i];
                    if (r[2] > r[0] && mouseX >= r[0] && mouseX < r[2] && mouseY >= r[1] && mouseY < r[3]) {
                        var p = perks[i];
                        if (availablePts() > 0 && skillRank(p) < p.maxRank) {
                            spentNow.merge(p.id, 1, Integer::sum); // optimistic; server validates + re-sends
                            com.riverfishing.network.ModNetwork.toServer(
                                    new com.riverfishing.network.SkillUnlockPacket(p.id));
                        }
                        return true;
                    }
                }
            } else if (tab == TAB_BAIT || tab == TAB_GEAR || tab == TAB_GUIDE) {
                // §discord: test the link button before the "any click closes the page" rule below.
                if (linkRect[2] > 0 && mouseX >= linkRect[0] && mouseX < linkRect[2]
                        && mouseY >= linkRect[1] && mouseY < linkRect[3]) {
                    // Vanilla's confirm-link flow: it shows the URL, offers "copy link", then opens it.
                    net.minecraft.client.gui.screens.ConfirmLinkScreen.confirmLinkNow(DISCORD_URL, this, false);
                    return true;
                }
                if (catDetail >= 0) { catDetail = -1; scroll = 0; return true; }
                List<Cat> list = tabList();
                // §bait-table: the headings sort. Clicking the one already sorted flips the direction,
                // and the third click on it goes back to the shelf's own order — no state you cannot undo.
                if (tab == TAB_BAIT && mouseY >= top + 38 && mouseY < top + 48) {
                    for (int i = 0; i < baitCols.length; i++) {
                        int cw = i == 0 ? baitCols[1] - baitCols[0]
                                : (i == 5 ? 62 : (i < 3 ? 42 : 46));
                        if (mouseX < baitCols[i] || mouseX >= baitCols[i] + cw) continue;
                        if (baitSort != i) {
                            baitSort = i;
                            baitSortDesc = true;
                        } else if (baitSortDesc) {
                            baitSortDesc = false;
                        } else {
                            baitSort = 0;
                        }
                        scroll = 0;
                        return true;
                    }
                }
                // §gear-sort-head: the headings sort here too. First click takes a column's natural
                // direction — biggest first for a number, A first for the name — the second flips it,
                // and the third gives the shelf's own tier-and-size order back.
                if (tab == TAB_GEAR && mouseY >= top + 40 && mouseY < top + 50) {
                    for (int i = 0; i < gearColCount; i++) {
                        if (mouseX < gearColX[i] || mouseX >= gearColX[i] + gearColW[i]) continue;
                        boolean natural = i > 0;
                        if (gearSort != i) {
                            gearSort = i;
                            gearSortDesc = natural;
                        } else if (gearSortDesc == natural) {
                            gearSortDesc = !natural;
                        } else {
                            gearSort = -1;
                        }
                        scroll = 0;
                        return true;
                    }
                }
                // §gear-table: the category rail on the left, same shape as the fish tab's families.
                if (tab == TAB_GEAR) {
                    for (int i = 0; i < GEAR_KINDS.length; i++) {
                        int ry = top + 40 + i * LIST_ROW;
                        if (mouseX >= left + 10 && mouseX < left + 106
                                && mouseY >= ry && mouseY < ry + LIST_ROW) {
                            gearCat = i;
                            // The categories do not share a column count — rods have four headings and
                            // reels three — so a sort held across the switch would point at a column
                            // that is not there. Drop it; the new shelf opens in its own order.
                            gearSort = -1;
                            scroll = 0;
                            return true;
                        }
                    }
                }
                boolean table = tab == TAB_BAIT || tab == TAB_LURE || tab == TAB_GEAR;
                int rowH = table ? 17 : ROW_H - 1;
                int rowW = table ? (tab == TAB_GEAR ? W - 132 : W - 26) : catColW - 8;
                int contentTop = top + 38, contentBottom = top + H - 6;
                for (int i = 0; i < list.size(); i++) {
                    int x = catRects[i][0], y = catRects[i][1];
                    if (mouseX >= x && mouseX < x + rowW && mouseY >= y && mouseY < y + rowH
                            && mouseY >= contentTop && mouseY < contentBottom) {
                        catDetail = i;
                        scroll = 0;
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private static String key(String species) {
        return RiverFishing.id(species).toString();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
