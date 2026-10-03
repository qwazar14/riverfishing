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
    /**
     * §koi-legacy: every registered species EXCEPT the five ids koi used to be. They are still items,
     * still priced, still whatever is in your chest — but they stopped being species when §koi-genes
     * made them varieties of one, and a bestiary that lists them lists the same fish six times.
     */
    private static final String[] SPECIES = java.util.Arrays.stream(ModItems.FISH_SPECIES)
            .filter(sp -> !com.riverfishing.fish.Genome.isKoiId(sp) || "koi_carp".equals(sp))
            .toArray(String[]::new);
    // §journal-room (0.8.0): the page carries four more blocks than it did, and the list grew a family
    // column and a search field. Both need width. The shrink-to-fit below is what makes this safe: the
    // panel is a MAXIMUM, and a small screen scales the whole thing down rather than clipping it.
    private static final int H = 420;
    private static final int MAX_W = 470;
    /** §fish-list: one row per species, wide enough to carry the catch count and the personal best. */
    private static final int LIST_ROW = 15;
    private static final int FAM_W = 116;
    private static final int LIST_TOP = 58;
    // Panel width adapts to the screen (GUI scale) so it never clips off-screen; columns + illustration follow.
    private int W = MAX_W;
    private int COL_W = MAX_W - FAM_W - 24;
    private int ILLUS_W = 240;

    private static final int TAB_FISH = 0;
    private static final int TAB_BAIT = 1;
    private static final int TAB_LURE = 2;
    private static final int TAB_GEAR = 3;
    private static final int TAB_QUEST = 4;
    private static final int TAB_SKILL = 5;
    private static final int TAB_RECORD = 6;
    private static final int TAB_GUIDE = 7;
    /** §boilie-page: the flavours against the day — the gear bookmark's last page. */
    private static final int TAB_BOILIE = 8;
    /** §discord: same invite as the mod metadata and the wiki — one place for the community. */
    private static final String DISCORD_URL = "https://discord.gg/Kk2nKvsuRh";

    /**
     * §journal-book (1.1.0): eight tabs in one row became four bookmarks down the left edge, each with its
     * own few pages across the top. The eight were three reference tables of one kind, three pages about
     * the angler, the fish and the guide — so they are grouped the way a reader looks for them. Every
     * renderer below still keys on {@link #tab}; {@link #go} is the one place that maps a bookmark and a
     * page onto it.
     */
    private static final int SEC_FISH = 0, SEC_GEAR = 1, SEC_ANGLER = 2, SEC_GUIDE = 3;
    private static final String[] BOOK_KEYS = {"journal.riverfishing.tab_fish", "journal.riverfishing.tab_gear",
            "journal.riverfishing.book_angler", "journal.riverfishing.book_guide"};
    /** Each bookmark's leather, multiplied over the grey sprite. */
    private static final int[] BOOK_TINT = {0x54A0B0, 0xBE824A, 0x86A852, 0xC85646};
    private static final String[] NO_PAGES = {};
    /** §journal-sheets: a species page is three sheets — the fish, how to catch it, how it lives. */
    private static final String[] SHEET_KEYS = {"journal.riverfishing.sheet_fish",
            "journal.riverfishing.sheet_catch", "journal.riverfishing.sheet_habits"};
    /** The gear bookmark's pages: the four tackle shelves, then baits and lures. Order = {@link #go}'s map. */
    private static final String[] GEAR_KEYS = {"journal.riverfishing.sec_rod", "journal.riverfishing.sec_reel",
            "journal.riverfishing.sec_line", "journal.riverfishing.sec_rig",
            "journal.riverfishing.tab_bait", "journal.riverfishing.tab_lure", "journal.riverfishing.tab_boilie"};
    private static final String[] ANGLER_KEYS = {"journal.riverfishing.tab_quest",
            "journal.riverfishing.tab_skill", "journal.riverfishing.tab_record"};
    private static final ResourceLocation BOOK_TEX = RiverFishing.id("textures/gui/journal/book.png");
    private static final ResourceLocation BOOKMARK_TEX = RiverFishing.id("textures/gui/journal/bookmark.png");
    /** How far a resting bookmark shows past the cover, and how much further the open one is pulled. */
    private static final int BOOKMARK_OUT = 96, BOOKMARK_PULL = 8;
    private static final int BOOKMARK_H = 22, BOOKMARK_STEP = 28, BOOKMARK_TOP = 30;
    /** The page row's band, and the parchment it is cut from (the tab that is open joins the page). */
    private static final int TAB_Y0 = 5, TAB_Y1 = 19;
    private static final int PAGE = 0xFFE6D9BB;
    /** The angler bookmark's pages all start under its level header. */
    private static final int ANGLER_TOP = 46;
    /** §journal-list: the species list sits right under the search field now the level moved out. */
    private static final int SEARCH_Y = 38;

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
        icon.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                Component.translatable("guide.riverfishing." + id + ".title")
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
        // §journal-book: the shelf is picked on the page row above now, so the table has the whole width
        int x = left + 12, tableX = x;
        int wAll = W - 32;

        g.drawString(this.font, Component.translatable("journal.riverfishing.gt_hint"),
                x, top + 25, GuiStyle.TEXT_HINT, false);

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
                String reel = rt.isFly() ? "#" + rt.flyWeight()
                        : rt.takesReel() ? (rt.minReel() / 1000) + "–" + (rt.maxReel() / 1000) + "k"
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
                        reel.fly() ? "#" + reel.flyWeight() : String.format(java.util.Locale.ROOT, "%.2f",
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
        int x = left + 12, wAll = W - 32;
        g.drawString(this.font, Component.translatable("journal.riverfishing.note_lure"),
                x, top + 25, GuiStyle.TEXT_HINT, false);

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
        int x = left + 12, wAll = W - 32;
        g.drawString(this.font, Component.translatable("journal.riverfishing.tab_bait_hint"),
                x, top + 25, GuiStyle.TEXT_HINT, false);

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

    /**
     * §guide-headings: a guide page is prose with SECTIONS. A paragraph written as {@code ## Something}
     * in the lang file becomes a brass heading with air above it and a rule under it; everything else
     * wraps as before.
     *
     * <p>In the lang file rather than in code for the same reason {@code .table} and {@code .bars} are:
     * a page gains sections as a TRANSLATION job, done once per language, instead of a render method
     * per page that only English would ever get. A translator who drops the marker loses the heading
     * and keeps the sentence — the page still reads, it just reads flat.
     *
     * <p>Wrapping happens per paragraph, so the marker survives: splitting the whole page first would
     * hand back wrapped lines with no idea which of them a paragraph began on.
     */
    private int guideProse(GuiGraphics g, String id, int y) {
        String raw = I18n.get("guide.riverfishing." + id + ".text");
        for (String para : raw.split("\n")) {
            if (para.startsWith(HEADING)) {
                y += 7;
                g.drawString(this.font, para.substring(HEADING.length()).trim(), left + 10, y, 0xFFB0842C, false);
                y += 11;
                g.fill(left + 10, y, left + W - 14, y + 1, 0x33000000);
                y += 5;
                continue;
            }
            if (para.isEmpty()) {
                y += 12;    // a blank line is the paragraph gap the pages were written with
                continue;
            }
            for (net.minecraft.util.FormattedCharSequence seq
                    : this.font.split(Component.literal(para), W - 24)) {
                g.drawString(this.font, seq, left + 10, y, GuiStyle.TEXT, false);
                y += 12;
            }
        }
        return y;
    }

    /** What marks a guide paragraph as a section heading. Markdown's, so it reads as one in the file. */
    private static final String HEADING = "## ";

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
    /** §journal-book: which bookmark is open, and which of its pages (a gear shelf, an angler page, a fish sheet). */
    private int section = SEC_FISH;
    private int sub;
    /** §journal-guide: the guide chapter whose pages are listed. */
    private int guideChapter;

    // §journal-anim: everything that moves eases toward where it should be, by frame time.
    private long openedAt = net.minecraft.Util.getMillis();
    private long lastFrame = openedAt;
    private float animScale = 1f, animOff;
    private final float[] markOut = new float[BOOK_KEYS.length];
    private float plateX = -1, plateW;
    /** The scroll the page is drawn at, easing toward {@link #scroll} (which stays the target every handler sets). */
    private float shownScroll;
    private String pageKey;
    private long turnAt;
    private String detail;      // opened fish species, or null
    private int catDetail = -1; // opened bait/gear entry index (in the current tab's list), or -1
    /** §craft-grid: what the cursor is over this frame — its tooltip is drawn after the whole page. */
    private ItemStack hoverStack = ItemStack.EMPTY;
    /** The cursor in journal space this frame, for the pages that are drawn without it. */
    private int hoverX, hoverY;
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
        addGuide("gbrecipes", modStack("groundbait_powder"));
        addGuide("boilies", modStack("boilie"));   // §boilies
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
        addGuide("geography", new ItemStack(net.minecraft.world.item.Items.FILLED_MAP));   // §provinces
        addGuide("nets", modStack("seine_net")); // §D
        addGuide("market", new ItemStack(net.minecraft.world.item.Items.EMERALD));
        addGuide("coop", new ItemStack(net.minecraft.world.item.Items.LEAD));

        guideGroupNow = 5;   // §fish-farming: water of your own — in the order a player meets it
        addGuide("stocking", modStack("pond_sign"));
        addGuide("breeding", modStack("roe"));
        addGuide("genes", modStack("koi_carp"));
        addGuide("upgrades", modStack("aerator"));

        guideGroupNow = 6;   // under the ice — quest stage 6, and until now the only mode with no page
        addGuide("icefishing", modStack("ice_auger"));

        guideGroupNow = 7;   // the sea, and the fish that need a boat
        addGuide("trolling", modStack("trolling_rod"));
        addGuide("biggame", modStack("yellowfin_tuna"));
        addGuide("legendary", modStack("blue_marlin"));

        guideGroupNow = 8;   // not for anglers: whoever runs the world, and where to shout
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
            next.openGuide(guideId);
            if (next.catDetail >= 0) {
                Minecraft.getInstance().setScreen(next);
                return;
            }
        }
        // A refresh (server re-sends the journal after a skill unlock / quest claim) reuses this same
        // entry point — carry the reader's place over so they don't get thrown back to the FISH tab.
        if (Minecraft.getInstance().screen instanceof JournalScreen prev) {
            next.go(prev.section, prev.sub);
            next.scroll = prev.scroll;
            next.detail = prev.detail;
            next.catDetail = prev.catDetail;
            next.guideChapter = prev.guideChapter;
            next.family = prev.family;
            // the same book, re-read: no opening flourish, no page turn, bookmarks where they were
            next.openedAt = prev.openedAt;
            next.pageKey = prev.pageKey;
            next.shownScroll = prev.shownScroll;
            System.arraycopy(prev.markOut, 0, next.markOut, 0, next.markOut.length);
            next.plateX = prev.plateX;
            next.plateW = prev.plateW;
        }
        Minecraft.getInstance().setScreen(next);
    }

    @Override
    protected void init() {
        this.W = MAX_W;
        this.COL_W = this.W - FAM_W - 24;
        buildFamilies();
        this.ILLUS_W = 240;
        // §journal-scale: at a high GUI scale the screen is small in GUI units and the full-size journal
        // (W×H) would clip off the bottom (unusable at scale 4). Shrink the whole panel to fit, centred; the
        // render + mouse + scissor all go through this factor so clicks and clipping stay aligned.
        // §journal-book: the bookmarks stand out past the cover, so the book and its bookmarks are centred together
        int span = MAX_W + BOOKMARK_OUT + BOOKMARK_PULL;
        this.uiScale = Math.min(1f, Math.min((this.width - 8f) / span, (this.height - 8f) / H));
        this.left = (this.width - W + BOOKMARK_OUT + BOOKMARK_PULL) / 2;
        this.top = (this.height - H) / 2;
    }

    /** The scale the book is drawn at: fit-to-screen, times the opening flourish. */
    private float drawScale() {
        return uiScale * animScale;
    }

    /** Screen → journal-space coordinate (the render is scaled by {@link #drawScale} around the screen centre). */
    private double toJournalX(double sx) { return (sx - this.width / 2.0) / drawScale() + this.width / 2.0; }
    private double toJournalY(double sy) { return (sy - this.height / 2.0 - animOff) / drawScale() + this.height / 2.0; }

    /** Scissor rect given in journal space, pushed in the scaled screen space the content actually draws to. */
    private void scissorJournal(GuiGraphics g, int x1, int y1, int x2, int y2) {
        float cx = this.width / 2f, cy = this.height / 2f + animOff, s = drawScale();
        float jy = this.height / 2f;
        g.enableScissor(Math.round(cx + (x1 - cx) * s), Math.round(cy + (y1 - jy) * s),
                Math.round(cx + (x2 - cx) * s), Math.round(cy + (y2 - jy) * s));
    }

    /**
     * §journal-book: open a bookmark on one of its pages. The legacy {@link #tab} every renderer reads is
     * derived here and nowhere else, so a bookmark, its page row and the page drawn cannot disagree.
     */
    private void go(int sec, int page) {
        if (sec != section) plateX = -1;   // a new row of pages: the open tab appears, it does not slide in
        section = sec;
        sub = page;
        catDetail = -1;
        scroll = 0;
        searchFocus = false;
        if (sec != SEC_FISH) detail = null;
        tab = switch (sec) {
            case SEC_FISH -> TAB_FISH;
            case SEC_GEAR -> page < GEAR_KINDS.length ? TAB_GEAR
                    : page == GEAR_KINDS.length ? TAB_BAIT : page == GEAR_KINDS.length + 1 ? TAB_LURE : TAB_BOILIE;
            case SEC_ANGLER -> TAB_QUEST + page;   // quests, skills and records are consecutive
            default -> TAB_GUIDE;
        };
        if (tab == TAB_GEAR && gearCat != page) {
            // the shelves do not share a column count, so a sort held across would point at nothing
            gearCat = page;
            gearSort = -1;
        }
    }

    /** Back out of an opened fish, bait, gear or guide page to its list. */
    private boolean back() {
        if (detail == null && catDetail < 0) return false;
        detail = null;
        catDetail = -1;
        scroll = 0;
        return true;
    }

    /** The row of pages across the top for where the reader is — empty where a bookmark has only one. */
    private String[] subKeys() {
        return switch (section) {
            case SEC_FISH -> detail != null ? SHEET_KEYS : NO_PAGES;
            case SEC_GEAR -> GEAR_KEYS;
            case SEC_ANGLER -> ANGLER_KEYS;
            default -> NO_PAGES;
        };
    }

    private static float ease(float t) {
        float u = 1f - Mth.clamp(t, 0f, 1f);
        return 1f - u * u * u;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g, mouseX, mouseY, partialTick);
        hoverStack = ItemStack.EMPTY;   // §craft-grid: refilled by whatever the cursor is over

        // §journal-anim: frame time drives every ease, so it moves the same at 30 fps and at 240
        long now = net.minecraft.Util.getMillis();
        float dt = Math.min(0.1f, (now - lastFrame) / 1000f);
        lastFrame = now;
        float k = 1f - (float) Math.exp(-dt * 16f);
        float opening = ease((now - openedAt) / 260f);
        animScale = 0.93f + 0.07f * opening;
        animOff = (1f - opening) * 14f;

        // a new page turns in, and lands at its own scroll rather than sliding there from the last one's
        String key = section + "/" + sub + "/" + detail + "/" + catDetail + "/" + guideChapter
                + (tab == TAB_BOILIE ? "/" + boPage : "");
        if (pageKey != null && !pageKey.equals(key)) {
            turnAt = now;
            shownScroll = scroll;
        }
        pageKey = key;
        // §journal-scroll-ease: handlers move the target; the page glides to it. The species list counts in
        // rows, where a glide would only stutter between them, so it follows at once.
        boolean rowList = tab == TAB_FISH && detail == null && section == SEC_FISH;
        int target = scroll;
        shownScroll = rowList ? scroll : shownScroll + (scroll - shownScroll) * k;
        if (Math.abs(scroll - shownScroll) < 0.5f) shownScroll = scroll;
        scroll = Math.round(shownScroll);

        g.pose().pushPose();
        g.pose().translate(this.width / 2f, this.height / 2f + animOff, 0);
        g.pose().scale(drawScale(), drawScale(), 1f);
        g.pose().translate(-this.width / 2f, -this.height / 2f, 0);
        // hover in the same space the book now draws in
        mouseX = (int) Math.round(toJournalX(mouseX));
        mouseY = (int) Math.round(toJournalY(mouseY));

        hoverX = mouseX;
        hoverY = mouseY;
        fishBoLink[2] = 0;
        sheetLinks.clear();
        catFishLinks.clear();
        catFishIds.clear();
        renderBookmarks(g, mouseX, mouseY, k);
        g.blit(BOOK_TEX, left, top, W, H, 0f, 0f, W, H, 512, 512);
        g.fill(left + 12, top + TAB_Y1, left + W - 15, top + TAB_Y1 + 1, 0xFFB08D3C);
        g.fill(left + 12, top + TAB_Y1 + 1, left + W - 15, top + TAB_Y1 + 2, 0x33000000);
        renderTabs(g, mouseX, mouseY, k);
        if (section == SEC_ANGLER) renderAnglerHead(g);
        if (tab == TAB_FISH) {
            if (detail != null) renderFishDetail(g, detail);
            else renderFishGrid(g, mouseX, mouseY);
        } else if (tab == TAB_QUEST) {
            renderQuests(g, mouseX, mouseY);
        } else if (tab == TAB_SKILL) {
            renderSkills(g, mouseX, mouseY);
        } else if (tab == TAB_RECORD) {
            renderRecords(g, mouseX, mouseY);
        } else if (tab == TAB_BOILIE) {
            renderBoilie(g, mouseX, mouseY);
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
                renderGuideShelf(g, mouseX, mouseY);
            }
        }
        pageTurn(g, now);
        // §craft-grid: last, so a tooltip is never drawn under the page it belongs to.
        if (!hoverStack.isEmpty()) g.renderTooltip(this.font, hoverStack, mouseX, mouseY);
        g.pose().popPose();

        // the page may have clamped what it drew; the target stays where the handlers put it, inside the page
        if (scroll != Math.round(shownScroll)) shownScroll = scroll;
        scroll = rowList ? scroll : Mth.clamp(target, 0, Math.max(0, lastCatH - lastViewH));
    }

    /**
     * §journal-anim: the page turn. A blank leaf, cut from the same parchment, is drawn over the new page and
     * pulled away to the right with a curl of shadow at its edge — the new page is uncovered, not swapped in.
     */
    private void pageTurn(GuiGraphics g, long now) {
        float t = (now - turnAt) / 280f;
        if (t >= 1f) return;
        int x0 = left + 8, x1 = left + W - 10, y0 = top + TAB_Y1 + 2, y1 = top + H - 10;
        int edge = x0 + Math.round((x1 - x0) * ease(t));
        if (edge >= x1) return;
        g.flush();
        g.pose().pushPose();
        g.pose().translate(0, 0, 300);   // over the page's items as well as its text
        g.blit(BOOK_TEX, edge, y0, x1 - edge, y1 - y0, edge - left, y0 - top, x1 - edge, y1 - y0, 512, 512);
        for (int i = 0; i < 10; i++) {   // the curl's shadow falls on the page it is uncovering
            int a = (int) (0x38 * (i + 1) / 10f * (1f - t));
            g.fill(edge - 10 + i, y0, edge - 9 + i, y1, a << 24);
        }
        g.fill(edge, y0, edge + 1, y1, 0x55FFFFFF);   // the leaf's lit edge
        g.pose().popPose();
    }

    /**
     * §journal-book: the four bookmarks, leather tabs standing out of the cover's left edge. The open one is
     * pulled further out and in full colour; the one under the cursor leans out to meet it.
     */
    private void renderBookmarks(GuiGraphics g, int mouseX, int mouseY, float k) {
        for (int i = 0; i < BOOK_KEYS.length; i++) {
            int y = top + BOOKMARK_TOP + i * BOOKMARK_STEP;
            boolean hov = mouseX >= left - BOOKMARK_OUT - BOOKMARK_PULL && mouseX < left
                    && mouseY >= y && mouseY < y + BOOKMARK_H;
            float want = i == section ? BOOKMARK_PULL : (hov ? BOOKMARK_PULL / 2f : 0f);
            markOut[i] += (want - markOut[i]) * k;
            int x = left - BOOKMARK_OUT - Math.round(markOut[i]);
            int t = BOOK_TINT[i];
            float dim = i == section ? 1f : (hov ? 0.9f : 0.76f);
            g.setColor(((t >> 16) & 0xFF) / 255f * dim, ((t >> 8) & 0xFF) / 255f * dim, (t & 0xFF) / 255f * dim, 1f);
            g.blit(BOOKMARK_TEX, x, y, 100, BOOKMARK_H, 0f, 0f, 100, 22, 100, 22);
            g.setColor(1f, 1f, 1f, 1f);
            if (i == SEC_FISH) drawFishIcon(g, "carp", x + 6, y + 3);
            else g.renderFakeItem(bookmarkIcon(i), x + 6, y + 3);
            String label = fitName(Component.translatable(BOOK_KEYS[i]).getString(), left - x - 28);
            g.drawString(this.font, label, x + 25, y + 7, i == section ? 0xFFFFF6E0 : 0xFFE8DCC0, true);
        }
    }

    private ItemStack bookmarkIcon(int i) {
        return switch (i) {
            case SEC_GEAR -> new ItemStack(ModItems.RODS.get(0).get());
            case SEC_ANGLER -> new ItemStack(net.minecraft.world.item.Items.EXPERIENCE_BOTTLE);
            default -> new ItemStack(net.minecraft.world.item.Items.BOOK);
        };
    }

    /** §journal-book: the angler bookmark's header — level, rank, and how far to the next — over all three pages. */
    private void renderAnglerHead(GuiGraphics g) {
        long xp = data.getLong(JournalData.XP);
        int level = JournalData.levelForXp(xp);
        String angler = this.font.plainSubstrByWidth(
                Component.translatable("journal.riverfishing.angler", level,
                        Component.translatable("rank.riverfishing." + JournalData.rankKey(level)),
                        xp, JournalData.xpForLevel(level + 1) - xp).getString(), W - 24);
        g.drawString(this.font, angler, left + 12, top + 25, GuiStyle.TEXT, false);
        long lvlBase = JournalData.xpForLevel(level);
        long lvlNext = JournalData.xpForLevel(level + 1);
        float frac = lvlNext > lvlBase ? (float) (xp - lvlBase) / (lvlNext - lvlBase) : 0f;
        bar(g, left + 12, top + 36, W - 30, 3, frac, 0xFFC89C4A);
    }

    /**
     * §journal-blur (1.21): skip the new menu-background blur. 1.21's {@code renderBackground} runs a
     * gaussian blur post-effect over the world behind the screen; on the bestiary's parchment panel that
     * reads as a washed-out, "размытый" page. The panel is opaque, so a plain (unblurred) backdrop is
     * cleaner and crisper. No-op keeps the world sharp behind the journal.
     */
    @Override
    protected void renderBlurredBackground(float partialTick) {
    }

    // ---- tabs ----

    private int tabW(String[] keys, int i) {
        return this.font.width(Component.translatable(keys[i])) + 12;
    }

    private int tabX(String[] keys, int i) {
        int x = left + 12;
        for (int j = 0; j < i; j++) x += tabW(keys, j) + 3;
        return x;
    }

    /**
     * §journal-book: the open bookmark's pages, as leather tabs along the top of the page. The open one is a
     * plate of the page itself that slides from tab to tab; a bookmark with one page shows its title instead.
     */
    private void renderTabs(GuiGraphics g, int mouseX, int mouseY, float k) {
        String[] keys = subKeys();
        if (keys.length == 0) {
            plateX = -1;
            Component title = Component.translatable(section == SEC_FISH ? "journal.riverfishing.header" : BOOK_KEYS[section]);
            g.drawString(this.font, title, left + (W - this.font.width(title)) / 2, top + 8, 0xFF8A5A00, false);
            return;
        }
        int y0 = top + TAB_Y0, y1 = top + TAB_Y1;
        int open = Mth.clamp(sub, 0, keys.length - 1);
        for (int i = 0; i < keys.length; i++) {
            int x = tabX(keys, i), w = tabW(keys, i);
            boolean hov = i != open && mouseX >= x && mouseX < x + w && mouseY >= y0 && mouseY < y1;
            g.fill(x, y0, x + w, y1, hov ? 0xFF7A5232 : 0xFF5C3B23);
            g.fill(x, y0, x + w, y0 + 1, hov ? 0xFFA07A50 : 0xFF86603E);
            g.fill(x + w - 1, y0, x + w, y1, 0xFF2A180C);
        }
        int ax = tabX(keys, open), aw = tabW(keys, open);
        if (plateX < 0) {
            plateX = ax;
            plateW = aw;
        } else {
            plateX += (ax - plateX) * k;
            plateW += (aw - plateW) * k;
        }
        int px = Math.round(plateX), pw = Math.round(plateW);
        g.fill(px, y0 - 1, px + pw, y1 + 2, PAGE);            // down over the rule: the tab IS the page
        g.fill(px, y0 - 1, px + pw, y0, 0xFFF4ECD6);
        g.fill(px, y0 - 1, px + 1, y1 + 2, 0xFFB8A27A);
        g.fill(px + pw - 1, y0 - 1, px + pw, y1 + 2, 0xFFB8A27A);
        for (int i = 0; i < keys.length; i++) {
            int x = tabX(keys, i);
            boolean on = i == open;
            g.drawString(this.font, Component.translatable(keys[i]), x + 6, top + 8,
                    on ? GuiStyle.TEXT : 0xFFEDE2C6, !on);
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
        // §journal-book: the level and its bar live on the angler bookmark now; the list keeps the tally
        g.drawString(this.font, Component.translatable("journal.riverfishing.total",
                data.getInt("total"), discovered + "/" + SPECIES.length), left + 12, top + 26,
                GuiStyle.TEXT_HINT, false);

        renderFamilyColumn(g, mouseX, mouseY);
        renderSearchBox(g, mouseX, mouseY);
        renderSpeciesList(g, mouseX, mouseY);
    }

    /** The left rail: every family with "how many you have caught / how many there are". */
    private void renderFamilyColumn(GuiGraphics g, int mouseX, int mouseY) {
        int x = left + 12, y0 = top + LIST_TOP;
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
        int x = left + FAM_W + 20, y = top + SEARCH_Y, w = COL_W - 16, h = 14;
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

        int x = left + FAM_W + 20, y0 = top + LIST_TOP, w = COL_W - 16;
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
        int tx = left + W - 17;
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

        int y = top + ANGLER_TOP;   // §journal-book: the level is the bookmark's header now

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

    // ---- FISH: a species, on three sheets ----

    /**
     * §journal-sheets (1.1.0): the species page was one scroll of twenty blocks — the picture, the prose,
     * the record, the feed, the water, the fight, the baits, the season, the varieties — all at once. It is
     * three sheets now, each answering one question: what is this fish (and how have I done with it), how
     * do I catch it, how does it live. The header — icon, name, latin, your tally — stays over all three.
     */
    private void renderFishDetail(GuiGraphics g, String sp) {
        ResourceLocation id = RiverFishing.id(sp);
        // fixed header
        drawFishIcon(g, sp, left + 12, top + 23);
        g.drawString(this.font, Component.translatable("fish.riverfishing." + sp),
                left + 32, top + 27, GuiStyle.TEXT, false);
        CompoundTag rec = data.getCompound(key(sp));
        String recStr = "x" + rec.getInt("count") + "  •  " + weight(rec.getInt("best"));
        // §cards-2: the scientific name after the common one, when there is room before the record.
        String latin = card(sp).latin();
        int nameEnd = left + 32 + this.font.width(Component.translatable("fish.riverfishing." + sp)) + 6;
        if (!latin.isEmpty() && nameEnd + this.font.width(latin) < left + W - 20 - this.font.width(recStr)) {
            g.drawString(this.font, Component.literal(latin).withStyle(net.minecraft.ChatFormatting.ITALIC),
                    nameEnd, top + 27, GuiStyle.TEXT_HINT, false);
        }
        g.drawString(this.font, recStr, left + W - 16 - this.font.width(recStr), top + 27,
                GuiStyle.TEXT_HINT, false);

        int contentTop = top + 42, contentBottom = top + H - 24;
        int visibleH = contentBottom - contentTop;
        scroll = Mth.clamp(scroll, 0, Math.max(0, lastCatH - visibleH));
        scissorJournal(g, left + 8, contentTop, left + W - 10, contentBottom);
        com.riverfishing.fish.FishCard c = card(sp);
        int y = contentTop - scroll;
        if (sub == 1 && c.present()) {
            y = sheetCatch(g, sp, c, y);
        } else if (sub == 2 && c.present()) {
            y = sheetHabits(g, c, y);
        } else {
            y = sheetFish(g, sp, id, c, rec, y);
        }
        lastCatH = (y + scroll) - contentTop;
        lastViewH = contentBottom - contentTop;
        g.disableScissor();
        renderScrollbar(g, contentTop, contentBottom);

        g.drawString(this.font, Component.translatable("guide.riverfishing.back"),
                left + 12, top + H - 20, GuiStyle.GHOST, false);
    }

    /** Sheet one: the picture and the prose, your record beside them, and the varieties you have found. */
    private int sheetFish(GuiGraphics g, String sp, ResourceLocation id, com.riverfishing.fish.FishCard c,
                          CompoundTag rec, int y) {
        int railW = 176;
        int leftW = W - railW - 40;
        int iw = Math.min(ILLUS_W, leftW);
        drawIllustration(g, sp, left + 15, y + 3, iw, iw * 2 / 3);
        int ly = y + iw * 2 / 3 + 12;
        String desc = descText(sp);
        if (!desc.isEmpty()) {
            for (net.minecraft.util.FormattedCharSequence seq : this.font.split(Component.literal(desc), leftW)) {
                g.drawString(this.font, seq, left + 12, ly, GuiStyle.TEXT, false);
                ly += 11;
            }
        }
        int rx = left + W - railW - 18;
        int ry = y;
        if (c.present()) {
            ry = recordBlock(g, c, rec, rx, ry, railW);
            String fav = favouriteFlavour(c, rec);
            if (fav != null) ry = railLine(g, "journal.riverfishing.fav_flavour", fav, rx, ry, railW);
        }
        // §guide-nudge: honest bookkeeping. Nothing is withheld and nothing is locked — the record just
        // says this one was landed after the mod offered a hand.
        if (JournalData.wasHinted(data, id)) {
            for (net.minecraft.util.FormattedCharSequence seq
                    : this.font.split(Component.translatable("journal.riverfishing.hinted"), railW)) {
                g.drawString(this.font, seq, rx, ry, GuiStyle.GHOST, false);
                ry += 10;
            }
        }
        y = Math.max(ly, ry) + 2;
        y = morphRow(g, sp, id, y);
        return patternRow(g, id, y);   // §pattern
    }

    /** Sheet two: where, when and on what — and what the feed has to be to hold it. */
    private int sheetCatch(GuiGraphics g, String sp, com.riverfishing.fish.FishCard c, int y) {
        int colW = (W - 44) / 2;
        int lx = left + 12, rx = lx + colW + 16;
        int ly = railHead(g, "journal.riverfishing.sheet_catch", lx, y + 2, colW);
        String pop = data.getCompound("pop").getString(sp);   // §h: the population where the player stands
        if (!pop.isEmpty()) ly = railLine(g, "journal.riverfishing.pop_here", pop, lx, ly, colW);
        ly = railLine(g, "guide.riverfishing.water", waters(c), lx, ly, colW);
        ly = railLine(g, "guide.riverfishing.best",
                bestOf(c.seasons(), com.riverfishing.fish.FishCard.SEASONS, "season")
                        + " • " + bestOf(c.times(), com.riverfishing.fish.FishCard.TIMES, "time"), lx, ly, colW);
        ly = baitLinks(g, c, lx, ly, colW);
        ly = railLine(g, "guide.riverfishing.tackle", tackle(c), lx, ly, colW);
        // §boilie-builder: the builder, asking about this fish — only where the page shows the line
        Component bl = Component.translatable("journal.riverfishing.bo_for_fish");
        if (onPage(ly + 2, ly + 13)) {
            boolean hov = over(hoverX, hoverY, lx, ly + 2, lx + this.font.width(bl), ly + 13);
            g.drawString(this.font, bl, lx, ly + 3, hov ? 0xFFD8A93C : 0xFF8A5A00, false);
            fishBoLink[0] = lx; fishBoLink[1] = ly + 2; fishBoLink[2] = lx + this.font.width(bl); fishBoLink[3] = ly + 13;
        }
        ly += 14;
        if (c.minLevel() > 0) {
            for (net.minecraft.util.FormattedCharSequence seq : this.font.split(
                    Component.translatable("jei.riverfishing.level", c.minLevel()), colW)) {
                g.drawString(this.font, seq, lx, ly + 3, 0xFFB05A00, false);
                ly += 11;
            }
            ly += 3;
        }
        int ry = railHead(g, "journal.riverfishing.stat_groundbait", rx, y + 2, colW);
        ry = paramTable(g, rx, ry, colW, List.of(
                new Param("journal.riverfishing.stat_grind", c.grind(), 0xFF8A6E3C),
                new Param("journal.riverfishing.stat_richness", c.richness(), 0xFF6E8A3C))) + 4;
        return Math.max(ly, ry);
    }

    /** Sheet three: the water it lives in, what it eats, and what it does on the line. */
    private int sheetHabits(GuiGraphics g, com.riverfishing.fish.FishCard c, int y) {
        int colW = (W - 44) / 2;
        int lx = left + 12, rx = lx + colW + 16;
        int ly = railHead(g, "journal.riverfishing.stat_habitat", lx, y + 2, colW);
        ly = railLine(g, "journal.riverfishing.stat_depth", range(c.depthMin(), c.depthMax(), 999), lx, ly, colW);
        ly = railLine(g, "journal.riverfishing.stat_width", range(c.widthMin(), c.widthMax(), 99999), lx, ly, colW);
        StringBuilder bio = new StringBuilder();
        for (Map.Entry<String, Float> e : c.biomes().entrySet()) {
            if (e.getValue() <= 0) continue;
            if (bio.length() > 0) bio.append(", ");
            bio.append(Component.translatable("biomegroup.riverfishing." + e.getKey()).getString());
        }
        ly = railLine(g, "journal.riverfishing.stat_biomes",
                bio.length() == 0 ? Component.translatable("journal.riverfishing.stat_anywhere").getString()
                        : bio.toString(), lx, ly, colW);
        if (!c.diet().isEmpty()) {   // §species-table
            ly = railLine(g, "journal.riverfishing.diet",
                    Component.translatable("diet.riverfishing." + c.diet()).getString(), lx, ly, colW);
        }

        int ry = railHead(g, "journal.riverfishing.stat_fight", rx, y + 2, colW);
        ry = paramTable(g, rx, ry, colW, List.of(
                new Param("journal.riverfishing.stat_strength", c.fightStrength(), 0xFF9A4A3C),
                new Param("journal.riverfishing.stat_stamina", c.fightStamina(), 0xFF3C6E9A))) + 2;
        ry = railLine(g, "journal.riverfishing.stat_runs", Integer.toString(c.fightRuns()), rx, ry, colW);
        ry = railLine(g, "journal.riverfishing.stat_pattern",
                Component.translatable("fightpattern.riverfishing." + c.fightPattern()).getString(), rx, ry, colW);
        return Math.max(ly, ry);
    }

    /** §boilies: a favourite shows once three fish have come out on it — until then, how close you are. */
    private static final int FAVOURITE_AFTER = 3;

    /**
     * §boilies: the flavour this angler has caught this fish on most — "???" and the count toward it until one
     * flavour has three fish. Null (no line) unless the species takes boilies or was caught on a flavour.
     */
    private static String favouriteFlavour(com.riverfishing.fish.FishCard c, CompoundTag rec) {
        CompoundTag fl = rec.getCompound("fl");
        if (fl.isEmpty() && !c.baits().containsKey("boilie")) return null;
        String best = null;
        int most = 0;
        for (String k : fl.getAllKeys()) {
            if (fl.getInt(k) > most) { most = fl.getInt(k); best = k; }
        }
        return best != null && most >= FAVOURITE_AFTER
                ? Component.translatable("flavour.riverfishing." + best).getString() + " (" + most + ")"
                : "??? (" + most + "/" + FAVOURITE_AFTER + ")";
    }

    /** Your best against the species' own scale, with the trophy line marked on the same bar. */
    private int recordBlock(GuiGraphics g, com.riverfishing.fish.FishCard c, CompoundTag rec, int x, int y, int w) {
        y = railHead(g, "journal.riverfishing.stat_record", x, y + 2, w);
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
        return y + 14;
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
        int contentTop = top + ANGLER_TOP, contentBottom = top + H - 14;
        int visibleH = contentBottom - contentTop;
        scroll = Mth.clamp(scroll, 0, Math.max(0, lastCatH - visibleH));
        scissorJournal(g, left + 6, contentTop, left + W - 6, contentBottom);
        int y = contentTop - scroll;
        // §order-board: the day's order, written out as the recipe for catching it. First on the board
        // because it is the one task that changes every day — and the one that teaches a habitat.
        y = contractBoard(g, y, mouseX, mouseY);
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
                g.fill(left + 8, y - 2, left + W - 18, y + 11, 0x38E8B430);
            }
            int boxOuter = claimed ? 0xFF3FA34A : (ready ? 0xFFE8B430 : 0xFF3A2A18);
            int boxInner = claimed ? 0xFF57C063 : (ready ? 0xFFFFDE70 : 0xFF241A10);
            g.fill(left + 10, y, left + 18, y + 8, boxOuter);
            g.fill(left + 11, y + 1, left + 17, y + 7, boxInner);
            String title = this.font.plainSubstrByWidth(q.title().getString(), W - 120);
            int tc = claimed ? 0xFF6E5A3C : (ready ? 0xFF9A6E10 : GuiStyle.TEXT);
            g.drawString(this.font, title, left + 24, y, tc, false);
            ItemStack rw = q.rewardStack();
            int rx = left + W - 34;
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
            boolean hov = mouseX >= left + 8 && mouseX < left + W - 18 && mouseY >= y - 2 && mouseY < y + 12
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
            case SNAG_SENSE -> "-" + (rank * 8) + "%";
            case ANGLERS_LUCK, FINESSE -> "+" + (rank * 1) + "%";
        };
    }

    private void renderSkills(GuiGraphics g, int mouseX, int mouseY) {
        var perks = com.riverfishing.fishing.AnglerSkills.Perk.values();
        int avail = availablePts();
        g.drawString(this.font, Component.translatable("journal.riverfishing.skill_points", avail),
                left + 12, top + ANGLER_TOP, avail > 0 ? 0xFF3FA34A : GuiStyle.TEXT_HINT, false);

        int contentTop = top + ANGLER_TOP + 14, contentBottom = top + H - 14;
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
            int pipsX = left + W - 36 - p.maxRank * 8;
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
                int bx = left + W - 32;
                boolean hov = mouseX >= bx && mouseX < bx + 12 && mouseY >= by && mouseY < by + 12;
                g.fill(bx, by, bx + 12, by + 12, hov ? 0xFF57C063 : 0xFF3FA34A);
                g.fill(bx + 1, by + 1, bx + 11, by + 11, hov ? 0xFF6FD07B : 0xFF4FB459);
                g.drawCenteredString(this.font, "+", bx + 6, by + 2, 0xFFFFFFFF);
                skillRects[i][0] = bx; skillRects[i][1] = by; skillRects[i][2] = bx + 12; skillRects[i][3] = by + 12;
            } else if (maxed) {
                g.drawString(this.font, Component.translatable("skill.riverfishing.maxed")
                        .withStyle(ChatFormatting.DARK_GREEN), left + W - 32 - this.font.width(
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
        int tx = left + W - 17;
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

    // ---- BOILIES: every flavour against the day, read by the rules the fish go by ----

    /** The boilie page's columns: five fixed moments, then now. */
    private static final String[] BOILIE_HEADS = {"journal.riverfishing.bo_cold", "journal.riverfishing.bo_heat",
            "journal.riverfishing.bo_night", "journal.riverfishing.bo_murky", "journal.riverfishing.bo_pred",
            "journal.riverfishing.bo_now"};

    private static com.riverfishing.fish.Boilie.Scene boilieScene(com.riverfishing.engine.Season s,
            com.riverfishing.engine.TimeOfDay t, com.riverfishing.engine.Weather w, double clarity, int bed, String diet) {
        String group = "predator".equals(diet) ? "catfish" : com.riverfishing.fish.FishGroup.CYPRINID;
        return new com.riverfishing.fish.Boilie.Scene(s, t, w, false, clarity, bed, 0.3, false, diet, group, 3000);
    }

    /**
     * §boilie-page: the five moments the table is read at — a three-kilo carp on a gravel bed unless the
     * column says otherwise. Cold water, a hot clear noon, the night, murky water over mud after rain,
     * and a predator at dusk: the corners of the author's rules, where the flavours part the most.
     */
    private static final com.riverfishing.fish.Boilie.Scene[] BOILIE_SCENES = {
            boilieScene(com.riverfishing.engine.Season.SPRING, com.riverfishing.engine.TimeOfDay.DAY,
                    com.riverfishing.engine.Weather.CLEAR, 0.9, 2, "peaceful"),
            boilieScene(com.riverfishing.engine.Season.SUMMER, com.riverfishing.engine.TimeOfDay.DAY,
                    com.riverfishing.engine.Weather.CLEAR, 0.9, 2, "peaceful"),
            boilieScene(com.riverfishing.engine.Season.SUMMER, com.riverfishing.engine.TimeOfDay.NIGHT,
                    com.riverfishing.engine.Weather.CLEAR, 0.9, 2, "peaceful"),
            boilieScene(com.riverfishing.engine.Season.AUTUMN, com.riverfishing.engine.TimeOfDay.DAY,
                    com.riverfishing.engine.Weather.RAIN, 0.6, 4, "peaceful"),
            boilieScene(com.riverfishing.engine.Season.SUMMER, com.riverfishing.engine.TimeOfDay.DUSK,
                    com.riverfishing.engine.Weather.CLEAR, 0.9, 2, "predator")};

    /** The moment as this client sees it: the season, the hour and the sky. The water it cannot know. */
    private static com.riverfishing.fish.Boilie.Scene boilieNow() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return BOILIE_SCENES[1];
        var weather = mc.level.isThundering() ? com.riverfishing.engine.Weather.THUNDER
                : mc.level.isRaining() ? com.riverfishing.engine.Weather.RAIN : com.riverfishing.engine.Weather.CLEAR;
        return boilieScene(com.riverfishing.integration.SeasonProvider.getSeason(mc.level),
                com.riverfishing.engine.TimeOfDay.fromDayTime(mc.level.getDayTime()), weather, 0.9, 0, "peaceful");
    }

    /** One flavour, on a plain 20 mm bottom boilie — so the column is the flavour and nothing else. */
    private static double boilieFactor(com.riverfishing.fish.Flavour f, com.riverfishing.fish.Boilie.Scene s) {
        return new com.riverfishing.fish.Boilie(List.of(f), com.riverfishing.fish.Boilie.Buoyancy.SINKER, 20, false, null)
                .factor(s);
    }

    private static int factorColour(double f) {
        return lerpColour(0xFFA0402E, 0xFF2E7D32, (float) Mth.clamp((f - 0.5) / 1.1, 0.0, 1.0));
    }

    private static String times(double f) {
        return String.format(java.util.Locale.ROOT, "×%.1f", f);
    }

    // ---- §boilie-builder (1.1.0): the boilie page is three pages — the flavour table, a builder that runs the
    // real rulebook on the boilie and the water you set and says why, and how that boilie of yours is made ----

    private static final String[] BO_PAGES = {"journal.riverfishing.bo_page_table",
            "journal.riverfishing.bo_page_build", "journal.riverfishing.bo_page_make"};
    private static final int BC_PAGE = 0, BC_SLOT = 1, BC_PAL = 2, BC_FORM = 3, BC_SIZE = 4, BC_MEAL = 5,
            BC_SEASON = 6, BC_TIME = 7, BC_WEATHER = 8, BC_WATER = 9, BC_BED = 10, BC_CURRENT = 11, BC_COVER = 12,
            BC_SHIFT = 13, BC_FISH = 14, BC_NOW = 15, BC_ROW = 16, BC_HEAD = 17, BC_CELL = 18, BC_GUIDE = 19,
            BC_CLEAR = 20;
    /** The water's look as the rulebook reads it — untouched, ordinary, murky — and the bottom: mud, hard, not known. */
    private static final double[] BO_CLARITY = {1.0, 0.9, 0.6};
    private static final int[] BO_BED = {4, 2, 0};

    // The builder's boilie and water live for the session: a book shut and opened again has the last try on it.
    private static int boPage;
    /** Two flavours and the dip; {@code null} is an empty slot. */
    private static final com.riverfishing.fish.Flavour[] boSlot = new com.riverfishing.fish.Flavour[3];
    private static int boTarget;
    private static int boForm, boSize = 2, boSeason = 1, boTime = 1, boWeather, boWater = 1, boBed = 1;
    /** The chosen fish by name — a preset's key or a species id — so a list rebuilt after a catch keeps it. */
    private static String boFishId = "journal.riverfishing.bf_carp";
    private static boolean boMeal, boCurrent, boCover, boShift;
    /** What was drawn where this frame, {x0, y0, x1, y1, control, value} — the clicks read it back. */
    private final List<int[]> boHits = new ArrayList<>();
    /** The species sheet's link into the builder, {x0, y0, x1, y1}. */
    private final int[] fishBoLink = new int[4];

    private record BoFish(String key, String sp, String diet, String group, double meanG) {}

    /** The fish the builder can ask: four kinds to begin with, then every species in the journal. */
    private List<BoFish> boFishes() {
        List<BoFish> out = new ArrayList<>(List.of(
                new BoFish("journal.riverfishing.bf_carp", null, "peaceful", com.riverfishing.fish.FishGroup.CYPRINID, 3500),
                new BoFish("journal.riverfishing.bf_big", null, "peaceful", com.riverfishing.fish.FishGroup.CYPRINID, 12000),
                new BoFish("journal.riverfishing.bf_small", null, "peaceful", com.riverfishing.fish.FishGroup.CYPRINID, 150),
                new BoFish("journal.riverfishing.bf_pred", null, "predator", com.riverfishing.fish.FishGroup.PREDATOR, 3000)));
        for (String sp : ordered) {
            if (!caught(sp)) continue;
            com.riverfishing.fish.FishCard c = card(sp);
            if (c.present()) out.add(new BoFish(null, sp, c.diet(), c.group(), c.weightMean()));
        }
        return out;
    }

    /** Where the chosen fish is in this journal's list; the first (carp) when it is not there. */
    private static int boFishIndex(List<BoFish> fishes) {
        for (int i = 0; i < fishes.size(); i++) {
            BoFish f = fishes.get(i);
            if (boFishId.equals(f.sp() != null ? f.sp() : f.key())) return i;
        }
        return 0;
    }

    /** Step the chosen fish along the list, and keep it by name. */
    private void boFishStep(int by) {
        List<BoFish> fishes = boFishes();
        BoFish f = fishes.get(Math.floorMod(boFishIndex(fishes) + by, fishes.size()));
        boFishId = f.sp() != null ? f.sp() : f.key();
    }

    private static com.riverfishing.fish.Boilie boBuilt() {
        List<com.riverfishing.fish.Flavour> fl = new ArrayList<>();
        for (int i = 0; i < 2; i++) if (boSlot[i] != null && !fl.contains(boSlot[i])) fl.add(boSlot[i]);
        return new com.riverfishing.fish.Boilie(fl, com.riverfishing.fish.Boilie.Buoyancy.values()[boForm],
                com.riverfishing.fish.Boilie.SIZES[boSize], boMeal, boSlot[2]);
    }

    private static com.riverfishing.fish.Boilie.Scene boScene(BoFish f) {
        return new com.riverfishing.fish.Boilie.Scene(com.riverfishing.engine.Season.values()[boSeason],
                com.riverfishing.engine.TimeOfDay.values()[boTime], com.riverfishing.engine.Weather.values()[boWeather],
                boShift, BO_CLARITY[boWater], BO_BED[boBed], boCover ? 0.6 : 0.2, boCurrent, f.diet(), f.group(), f.meanG());
    }

    /** The builder's water set to one of the table's moments (its fish too, where the column names one). */
    private static void boFrom(com.riverfishing.fish.Boilie.Scene s) {
        boSeason = s.season().ordinal();
        boTime = s.time().ordinal();
        boWeather = s.weather().ordinal();
        boWater = s.clarity() >= 1.0 ? 0 : s.clarity() >= 0.85 ? 1 : 2;
        boBed = s.bed() == 4 ? 0 : s.bed() >= 1 ? 1 : 2;
        boShift = s.shift();
        boCurrent = s.current();
        boCover = s.cover() >= 0.5;
        boFishId = "predator".equals(s.diet()) ? "journal.riverfishing.bf_pred" : "journal.riverfishing.bf_carp";
    }

    /** What this client can see of now — the season, the hour and the sky; the water under the float stays as set. */
    private static void boNow() {
        com.riverfishing.fish.Boilie.Scene s = boilieNow();
        boSeason = s.season().ordinal();
        boTime = s.time().ordinal();
        boWeather = s.weather().ordinal();
    }

    /** The species sheet's way in: the builder, asking about that fish. */
    private void openBuilder(String sp) {
        go(SEC_GEAR, GEAR_KINDS.length + 2);
        boPage = 1;
        boFishId = sp;
    }

    private static boolean over(double mx, double my, int x0, int y0, int x1, int y1) {
        return mx >= x0 && mx < x1 && my >= y0 && my < y1;
    }

    private static String cap(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static String[] tr(String prefix, String... ids) {
        String[] out = new String[ids.length];
        for (int i = 0; i < ids.length; i++) out[i] = cap(Component.translatable(prefix + ids[i]).getString());
        return out;
    }

    /** One pressable face on the page: leather when it is set, a shade under the cursor, a ruled box otherwise. */
    private static void boFace(GuiGraphics g, int x0, int y0, int x1, int y1, boolean on, boolean hov) {
        if (on) {
            g.fill(x0, y0, x1, y1, 0xFF5C3B23);
            g.fill(x0, y0, x1, y0 + 1, 0xFF86603E);
        } else {
            g.fill(x0, y0, x1, y1, hov ? 0x30000000 : 0x14000000);
            g.fill(x0, y1 - 1, x1, y1, 0x30000000);
        }
    }

    /** A button sized to its label; returns the x after it. */
    private int boButton(GuiGraphics g, int x, int y, String label, boolean on, int control, int value, int mx, int my) {
        int x1 = x + this.font.width(label) + 10;
        boFace(g, x, y, x1, y + 13, on, over(mx, my, x, y, x1, y + 13));
        g.drawString(this.font, label, x + 5, y + 3, on ? 0xFFF4ECD6 : GuiStyle.TEXT, false);
        boHits.add(new int[]{x, y, x1, y + 13, control, value});
        return x1 + 3;
    }

    /**
     * A row of buttons across {@code w}, the {@code sel}-th pressed; returns the y under it. Each is as wide as
     * its word plus a share of what is left — "Світанок" beside "Ніч" — and equal only when the words do not fit.
     */
    private int boSeg(GuiGraphics g, int x, int y, int w, String[] labels, int sel, int control, int mx, int my) {
        int n = labels.length, room = w - (n - 1) * 2, words = 0;
        int[] bws = new int[n];
        for (int i = 0; i < n; i++) words += bws[i] = this.font.width(labels[i]) + 6;
        for (int i = 0; i < n; i++) bws[i] = words <= room ? bws[i] + (room - words) / n : room / n;
        int bx = x;
        for (int i = 0; i < n; i++, bx += bws[i - 1] + 2) {
            int bw = i == n - 1 ? x + w - bx : bws[i];
            boFace(g, bx, y, bx + bw, y + 13, i == sel, over(mx, my, bx, y, bx + bw, y + 13));
            String label = fitName(labels[i], bw - 4);
            g.drawString(this.font, label, bx + (bw - this.font.width(label)) / 2, y + 3,
                    i == sel ? 0xFFF4ECD6 : GuiStyle.TEXT, false);
            boHits.add(new int[]{bx, y, bx + bw, y + 13, control, i});
        }
        return y + 16;
    }

    /** {@link #boSeg} with its name in the margin. */
    private int boRow(GuiGraphics g, int x, int y, int w, String key, String[] labels, int sel, int control, int mx, int my) {
        g.drawString(this.font, fitName(Component.translatable(key).getString(), 42), x, y + 3, GuiStyle.TEXT_HINT, false);
        return boSeg(g, x + 44, y, w - 44, labels, sel, control, mx, my);
    }

    /** A tick box and its label; returns the x after it. */
    private int boTick(GuiGraphics g, int x, int y, String key, boolean on, int control, int mx, int my) {
        Component label = Component.translatable(key);
        int x1 = x + 12 + this.font.width(label);
        boolean hov = over(mx, my, x, y, x1, y + 11);
        g.fill(x, y + 1, x + 9, y + 10, 0xFF5C3B23);
        g.fill(x + 1, y + 2, x + 8, y + 9, hov ? 0xFFF4ECD6 : PAGE);
        if (on) g.fill(x + 2, y + 3, x + 7, y + 8, 0xFF2E7D32);
        g.drawString(this.font, label, x + 12, y + 2, GuiStyle.TEXT, false);
        boHits.add(new int[]{x, y, x1, y + 11, control, 0});
        return x1 + 8;
    }

    private static String times2(double f) {
        return String.format(java.util.Locale.ROOT, "×%.2f", f);
    }

    private static ItemStack boilieStack(com.riverfishing.fish.Boilie b, int count) {
        ItemStack st = new ItemStack(ModItems.BOILIE.get(), count);
        com.riverfishing.item.BoilieItem.write(st, b);
        if (b.dip() != null) com.riverfishing.item.BoilieItem.dip(st, b.dip());
        return st;
    }

    private static ItemStack ingredientOf(com.riverfishing.fish.Flavour f) {
        ResourceLocation id = ResourceLocation.tryParse(f.ingredient.contains(":") ? f.ingredient : "minecraft:" + f.ingredient);
        return id == null ? ItemStack.EMPTY : new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(id));
    }

    private List<Component> flavourTooltip(com.riverfishing.fish.Flavour f) {
        List<Component> tip = new ArrayList<>();
        tip.add(Component.translatable("flavour.riverfishing." + f.id()).withStyle(s -> s.withColor(f.rgb)));
        tip.add(Component.translatable("journal.riverfishing.bo_kind",
                Component.translatable("journal.riverfishing.bo_strength_" + f.strength.name().toLowerCase(java.util.Locale.ROOT)),
                Component.translatable(f.bright ? "journal.riverfishing.bo_bright" : "journal.riverfishing.bo_dark"))
                .withStyle(ChatFormatting.GRAY));
        ItemStack ing = ingredientOf(f);
        if (!ing.isEmpty()) {
            tip.add(Component.translatable("journal.riverfishing.bo_bottle", ing.getHoverName()).withStyle(ChatFormatting.DARK_GRAY));
        }
        return tip;
    }

    /** The boilie page: its three pages' switch along the top, then the page. */
    private void renderBoilie(GuiGraphics g, int mouseX, int mouseY) {
        boHits.clear();
        int x = left + 12, bx = x;
        for (int i = 0; i < BO_PAGES.length; i++) {
            bx = boButton(g, bx, top + 24, Component.translatable(BO_PAGES[i]).getString(), i == boPage, BC_PAGE, i,
                    mouseX, mouseY);
        }
        if (boPage == 1) renderBoilieBuilder(g, mouseX, mouseY);
        else if (boPage == 2) renderBoilieMake(g, mouseX, mouseY);
        else renderBoilieTable(g, mouseX, mouseY);
    }

    /**
     * §boilie-page (1.1.0): the flavours as a table the angler can read the day off. Every cell is the
     * real rulebook ({@link com.riverfishing.fish.Boilie#factor}) run on one flavour at one moment, so the
     * page cannot say strawberry and the water mean chili. The last column is now, from what this client
     * can see — the season, the hour and the sky; the water under the float it leaves to the angler.
     * §boilie-builder: a flavour, a column or a cell opens the builder on it.
     */
    private void renderBoilieTable(GuiGraphics g, int mouseX, int mouseY) {
        int x = left + 12, wAll = W - 32, top = this.top + 17;
        g.drawString(this.font, fitName(Component.translatable("journal.riverfishing.bo_hint").getString(), wAll),
                x, top + 25, GuiStyle.TEXT_HINT, false);

        com.riverfishing.fish.Flavour[] fl = com.riverfishing.fish.Flavour.values();
        com.riverfishing.fish.Boilie.Scene now = boilieNow();
        double[][] cell = new double[fl.length][BOILIE_HEADS.length];
        int[] best = new int[BOILIE_HEADS.length];
        int worstNow = 0;
        for (int i = 0; i < fl.length; i++) {
            for (int c = 0; c < BOILIE_HEADS.length; c++) {
                cell[i][c] = boilieFactor(fl[i], c < BOILIE_SCENES.length ? BOILIE_SCENES[c] : now);
                if (cell[i][c] > cell[best[c]][c]) best[c] = i;
            }
            if (cell[i][BOILIE_HEADS.length - 1] < cell[worstNow][BOILIE_HEADS.length - 1]) worstNow = i;
        }

        // the moment, and what it wants
        int nowC = BOILIE_HEADS.length - 1;
        g.fill(x, top + 36, x + wAll, top + 62, 0x18000000);
        g.drawString(this.font, Component.translatable("journal.riverfishing.bo_now_is",
                Component.translatable("season.riverfishing." + now.season().jsonKey()),
                Component.translatable("time.riverfishing." + now.time().jsonKey()),
                Component.translatable("weather.riverfishing." + now.weather().jsonKey())),
                x + 5, top + 40, GuiStyle.TEXT_HINT, false);
        g.drawString(this.font, fitName(Component.translatable("journal.riverfishing.bo_best",
                Component.translatable("flavour.riverfishing." + fl[best[nowC]].id()), times(cell[best[nowC]][nowC]),
                Component.translatable("flavour.riverfishing." + fl[worstNow].id()), times(cell[worstNow][nowC])).getString(),
                wAll - 10), x + 5, top + 51, 0xFF8A5A00, false);

        // the table: the flavour, its family, then a column a moment
        int cols = BOILIE_HEADS.length;
        int[] colW = new int[cols];
        int used = 0;
        for (int c = 0; c < cols; c++) {
            colW[c] = Math.max(36, this.font.width(Component.translatable(BOILIE_HEADS[c])) + 8);
            used += colW[c];
        }
        int famW = 70, nameW = wAll - used - famW;
        int head = top + 70;
        g.drawString(this.font, Component.translatable("journal.riverfishing.bo_flavour"), x, head, 0xFFB0842C, false);
        int cx = x + nameW + famW;
        int[] right = new int[cols];
        for (int c = 0; c < cols; c++) {
            cx += colW[c];
            right[c] = cx;
            Component h = Component.translatable(BOILIE_HEADS[c]);
            boolean hovH = over(mouseX, mouseY, cx - colW[c] + 4, head - 2, cx + 2, head + 10);
            if (hovH) g.fill(cx - colW[c] + 4, head - 2, cx + 2, head + 10, 0x22000000);
            g.drawString(this.font, h, cx - this.font.width(h), head, c == nowC || hovH ? 0xFFD8A93C : 0xFFB0842C, false);
            boHits.add(new int[]{cx - colW[c] + 4, head - 2, cx + 2, head + 10, BC_HEAD, c});
        }
        g.fill(x, head + 10, x + wAll, head + 11, 0x33000000);

        int y = head + 14;
        List<Component> tooltip = null;
        for (int i = 0; i < fl.length; i++) {
            com.riverfishing.fish.Flavour f = fl[i];
            boolean hov = over(mouseX, mouseY, x, y - 1, x + wAll, y + 16);
            if (hov) g.fill(x, y - 1, x + wAll, y + 16, 0x22000000);
            g.renderItem(com.riverfishing.item.FlavourItem.make(f), x, y);
            g.drawString(this.font, fitName(Component.translatable("flavour.riverfishing." + f.id()).getString(), nameW - 24),
                    x + 20, y + 4, hov ? 0xFF8A5A00 : GuiStyle.TEXT, false);
            g.drawString(this.font, fitName(Component.translatable("flavourfamily.riverfishing."
                            + f.family.name().toLowerCase(java.util.Locale.ROOT)).getString(), famW - 4),
                    x + nameW, y + 4, GuiStyle.TEXT_HINT, false);
            boolean onCell = false;
            for (int c = 0; c < cols; c++) {
                int c0 = right[c] - colW[c] + 4;
                if (best[c] == i) g.fill(c0, y - 1, right[c] + 2, y + 15, 0x2240A040);
                if (over(mouseX, mouseY, c0, y - 1, right[c] + 2, y + 16)) {
                    g.fill(c0, y - 1, right[c] + 2, y + 16, 0x33000000);
                    onCell = true;
                }
                String s = times(cell[i][c]);
                g.drawString(this.font, s, right[c] - this.font.width(s), y + 4, factorColour(cell[i][c]), false);
                boHits.add(new int[]{c0, y - 1, right[c] + 2, y + 16, BC_CELL, i * 8 + c});
            }
            boHits.add(new int[]{x, y - 1, x + wAll, y + 16, BC_ROW, i});
            if (hov) {
                tooltip = flavourTooltip(f);
                tooltip.add(Component.translatable(onCell ? "journal.riverfishing.bo_click_cell" : "journal.riverfishing.bo_click_row")
                        .withStyle(ChatFormatting.DARK_GREEN));
            }
            y += 17;
        }

        y += 4;
        g.drawString(this.font, fitName(Component.translatable("journal.riverfishing.bo_table_hint").getString(), wAll),
                x, y, GuiStyle.TEXT_HINT, false);
        y += 13;
        Component link = Component.translatable("journal.riverfishing.bo_recipes");
        boolean hovLink = over(mouseX, mouseY, x, y - 1, x + this.font.width(link), y + 10);
        g.drawString(this.font, link, x, y, hovLink ? 0xFFD8A93C : 0xFF8A5A00, false);
        boHits.add(new int[]{x, y - 1, x + this.font.width(link), y + 10, BC_GUIDE, 0});
        if (tooltip != null) g.renderComponentTooltip(this.font, tooltip, mouseX, mouseY);
    }

    /**
     * §boilie-builder (1.1.0): a boilie and a swim, set by hand, and the rulebook's answer — the same
     * {@link com.riverfishing.fish.Boilie#factor} the bite engine multiplies a bite by — with every rule that
     * moved it listed, so the page teaches the notes instead of hiding them behind a number.
     */
    private void renderBoilieBuilder(GuiGraphics g, int mouseX, int mouseY) {
        int x = left + 12, wAll = W - 32, lw = 206, rx = x + 220, rw = wAll - 220;
        List<Component> tooltip = null;
        List<BoFish> fishes = boFishes();
        BoFish fish = fishes.get(boFishIndex(fishes));

        // ---- the boilie ----
        int y = top + 42;
        g.drawString(this.font, Component.translatable("journal.riverfishing.bo_the_boilie"), x, y, 0xFFB0842C, false);
        String clear = Component.translatable("journal.riverfishing.bo_clear").getString();
        boButton(g, x + lw - this.font.width(clear) - 10, y - 3, clear, false, BC_CLEAR, 0, mouseX, mouseY);
        y = top + 56;
        for (int i = 0; i < 3; i++) {
            int sx = x + (i == 2 ? 52 : i * 22);
            boolean hov = over(mouseX, mouseY, sx, y, sx + 20, y + 20);
            g.fill(sx, y, sx + 20, y + 20, i == boTarget ? 0xFFD8A93C : 0xFF5C3B23);
            g.fill(sx + 1, y + 1, sx + 19, y + 19, hov ? 0xFF7A6446 : GuiStyle.SLOT_BG);
            if (boSlot[i] != null) g.renderItem(com.riverfishing.item.FlavourItem.make(boSlot[i]), sx + 2, y + 2);
            boHits.add(new int[]{sx, y, sx + 20, y + 20, BC_SLOT, i});
            if (hov) {
                tooltip = new ArrayList<>();
                tooltip.add(Component.translatable(i == 2 ? "journal.riverfishing.bo_slot_dip" : "journal.riverfishing.bo_slot_flavour"));
                com.riverfishing.fish.Flavour sf = boSlot[i];
                if (sf != null) tooltip.add(Component.translatable("flavour.riverfishing." + sf.id()).withStyle(st -> st.withColor(sf.rgb)));
                tooltip.add(Component.translatable("journal.riverfishing.bo_slot_hint").withStyle(ChatFormatting.DARK_GREEN));
            }
        }
        g.drawString(this.font, "+", x + 45, y + 6, GuiStyle.TEXT_HINT, false);
        Component fLabel = Component.translatable("journal.riverfishing.bo_slot_flavours"), dLabel = Component.translatable("journal.riverfishing.bo_slot_dip_short");
        g.drawString(this.font, fLabel, x + 21 - this.font.width(fLabel) / 2, y + 22, GuiStyle.TEXT_HINT, false);
        g.drawString(this.font, dLabel, x + 62 - this.font.width(dLabel) / 2, y + 22, GuiStyle.TEXT_HINT, false);

        // what came out: the ball itself, big, and what it is
        com.riverfishing.fish.Boilie b = boBuilt();
        g.pose().pushPose();
        g.pose().translate(x + 84, y - 4, 0);
        g.pose().scale(2f, 2f, 1f);
        g.renderItem(boilieStack(b, 1), 0, 0);
        g.pose().popPose();
        int tx = x + 120, tw = lw - 120, ty = y - 2;
        g.drawString(this.font, fitName(Component.translatable("journal.riverfishing.bo_form_size",
                Component.translatable("journal.riverfishing.bo_f_" + b.buoyancy().name().toLowerCase(java.util.Locale.ROOT)).getString(),
                b.sizeMm()).getString(), tw), tx, ty, GuiStyle.TEXT, false);
        ty += 10;
        String taste = b.flavours().isEmpty() ? Component.translatable("journal.riverfishing.bo_no_flavour").getString()
                : b.flavours().stream().map(fv -> Component.translatable("flavour.riverfishing." + fv.id()).getString())
                .collect(Collectors.joining(" + "));
        for (net.minecraft.util.FormattedCharSequence seq : this.font.split(Component.literal(taste), tw)) {
            if (ty > y + 22) break;
            g.drawString(this.font, seq, tx, ty, GuiStyle.TEXT_HINT, false);
            ty += 10;
        }

        // the palette
        com.riverfishing.fish.Flavour[] fl = com.riverfishing.fish.Flavour.values();
        int py = top + 92;
        for (int i = 0; i < fl.length; i++) {
            int px = x + (i % 7) * 20, pyy = py + (i / 7) * 20;
            boolean hov = over(mouseX, mouseY, px, pyy, px + 18, pyy + 18);
            boolean used = fl[i] == boSlot[0] || fl[i] == boSlot[1] || fl[i] == boSlot[2];
            boFace(g, px, pyy, px + 18, pyy + 18, false, hov);
            if (used) {
                g.fill(px, pyy, px + 18, pyy + 1, 0xFF2E7D32);
                g.fill(px, pyy + 17, px + 18, pyy + 18, 0xFF2E7D32);
            }
            g.renderItem(com.riverfishing.item.FlavourItem.make(fl[i]), px + 1, pyy + 1);
            boHits.add(new int[]{px, pyy, px + 18, pyy + 18, BC_PAL, i});
            if (hov) {
                tooltip = flavourTooltip(fl[i]);
                tooltip.add(Component.translatable(boTarget == 2 ? "journal.riverfishing.bo_pal_dip" : "journal.riverfishing.bo_pal_flavour")
                        .withStyle(ChatFormatting.DARK_GREEN));
            }
        }
        y = top + 136;
        y = boSeg(g, x, y, lw, tr("journal.riverfishing.bo_f_", "sinker", "wafter", "popup", "snowman"), boForm, BC_FORM, mouseX, mouseY);
        String[] sizes = new String[com.riverfishing.fish.Boilie.SIZES.length];
        for (int i = 0; i < sizes.length; i++) {
            sizes[i] = Component.translatable("journal.riverfishing.bo_mm", com.riverfishing.fish.Boilie.SIZES[i]).getString();
        }
        y = boSeg(g, x, y, lw, sizes, boSize, BC_SIZE, mouseX, mouseY);
        boTick(g, x, y + 1, "journal.riverfishing.bo_meal", boMeal, BC_MEAL, mouseX, mouseY);

        // ---- the swim and the fish ----
        y = top + 42;
        g.drawString(this.font, Component.translatable("journal.riverfishing.bo_the_water"), rx, y, 0xFFB0842C, false);
        String nowL = Component.translatable("journal.riverfishing.bo_now_btn").getString();
        boButton(g, rx + rw - this.font.width(nowL) - 10, y - 3, nowL, false, BC_NOW, 0, mouseX, mouseY);
        y = top + 56;
        y = boRow(g, rx, y, rw, "journal.riverfishing.bo_l_season", tr("season.riverfishing.", "spring", "summer", "autumn", "winter"),
                boSeason, BC_SEASON, mouseX, mouseY);
        y = boRow(g, rx, y, rw, "journal.riverfishing.bo_l_time", tr("time.riverfishing.", "dawn", "day", "dusk", "night"),
                boTime, BC_TIME, mouseX, mouseY);
        y = boRow(g, rx, y, rw, "journal.riverfishing.bo_l_weather", tr("weather.riverfishing.", "clear", "rain", "thunder"),
                boWeather, BC_WEATHER, mouseX, mouseY);
        y = boRow(g, rx, y, rw, "journal.riverfishing.bo_l_water", tr("journal.riverfishing.bo_w_", "clear", "normal", "murky"),
                boWater, BC_WATER, mouseX, mouseY);
        y = boRow(g, rx, y, rw, "journal.riverfishing.bo_l_bed", tr("journal.riverfishing.bo_b_", "mud", "hard", "any"),
                boBed, BC_BED, mouseX, mouseY);
        int tickX = boTick(g, rx, y, "journal.riverfishing.bo_current", boCurrent, BC_CURRENT, mouseX, mouseY);
        tickX = boTick(g, tickX, y, "journal.riverfishing.bo_cover", boCover, BC_COVER, mouseX, mouseY);
        boTick(g, tickX, y, "journal.riverfishing.bo_shift", boShift, BC_SHIFT, mouseX, mouseY);
        y += 15;
        // the fish: ◀ name ▶, the wheel turns it too
        g.drawString(this.font, fitName(Component.translatable("journal.riverfishing.bo_l_fish").getString(), 42),
                rx, y + 5, GuiStyle.TEXT_HINT, false);
        int fx = rx + 44, fw = rw - 44;
        boFace(g, fx, y, fx + 12, y + 18, false, over(mouseX, mouseY, fx, y, fx + 12, y + 18));
        g.drawString(this.font, "◀", fx + 3, y + 5, GuiStyle.TEXT, false);
        boFace(g, fx + fw - 12, y, fx + fw, y + 18, false, over(mouseX, mouseY, fx + fw - 12, y, fx + fw, y + 18));
        g.drawString(this.font, "▶", fx + fw - 9, y + 5, GuiStyle.TEXT, false);
        boHits.add(new int[]{fx, y, fx + 12, y + 18, BC_FISH, -1});
        boHits.add(new int[]{fx + fw - 12, y, fx + fw, y + 18, BC_FISH, 1});
        boHits.add(new int[]{fx + 12, y, fx + fw - 12, y + 18, BC_FISH, 1});
        int nx = fx + 15;
        if (fish.sp() != null) {
            drawFishIcon(g, fish.sp(), nx, y + 1);
            nx += 18;
        }
        String fname = fish.sp() != null ? Component.translatable("fish.riverfishing." + fish.sp()).getString()
                : Component.translatable(fish.key()).getString();
        String weight = fish.meanG() >= 1000
                ? Component.translatable("journal.riverfishing.bo_kg", String.format(java.util.Locale.ROOT, "%.1f", fish.meanG() / 1000.0)).getString()
                : Component.translatable("journal.riverfishing.bo_g", Math.round(fish.meanG())).getString();
        g.drawString(this.font, fitName(fname + " · " + weight, fx + fw - 14 - nx), nx, y + 5, GuiStyle.TEXT, false);
        if (over(mouseX, mouseY, fx, y, fx + fw, y + 18)) {
            tooltip = List.of(Component.translatable("journal.riverfishing.bo_fish_hint").withStyle(ChatFormatting.DARK_GREEN));
        }

        // ---- the answer ----
        com.riverfishing.fish.Boilie.Scene s = boScene(fish);
        java.util.Map<String, Double> why = new java.util.LinkedHashMap<>();
        double f = b.factor(s, (k, v) -> why.merge(k, v, Double::sum));
        y = top + 186;
        g.fill(x, y, x + wAll, y + 1, 0x33000000);
        y += 6;
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(2f, 2f, 1f);
        g.drawString(this.font, times2(f), 0, 0, factorColour(f), false);
        g.pose().popPose();
        // the corridor, 0.3 to 2.0, with the plain boilie marked
        int barX = x + 90, barW = wAll - 90, barY = y + 3;
        for (int i = 0; i < barW; i++) {
            g.fill(barX + i, barY, barX + i + 1, barY + 6, factorColour(0.3 + 1.7 * i / (double) barW));
        }
        int one = barX + (int) Math.round((1.0 - 0.3) / 1.7 * barW);
        g.fill(one, barY - 2, one + 1, barY + 8, 0xFF3A2A18);
        int at = barX + (int) Math.round(Mth.clamp((f - 0.3) / 1.7, 0.0, 1.0) * (barW - 1));
        g.fill(at - 1, barY - 3, at + 2, barY + 9, 0xFF1A120A);
        g.fill(at, barY - 2, at + 1, barY + 8, 0xFFF4ECD6);
        g.drawString(this.font, "×0.3", barX, barY + 9, GuiStyle.TEXT_HINT, false);
        g.drawString(this.font, "×1", one - this.font.width("×1") / 2, barY + 9, GuiStyle.TEXT_HINT, false);
        g.drawString(this.font, "×2", barX + barW - this.font.width("×2"), barY + 9, GuiStyle.TEXT_HINT, false);

        y += 24;
        String verdict = f >= 1.15 ? "journal.riverfishing.bo_v_good" : f <= 0.87 ? "journal.riverfishing.bo_v_bad"
                : "journal.riverfishing.bo_v_same";
        g.drawString(this.font, fitName(Component.translatable(verdict).getString()
                        + (f >= 2.0 || f <= 0.3 ? " " + Component.translatable("journal.riverfishing.bo_v_cap").getString() : ""), wAll),
                x, y, factorColour(f), false);
        y += 11;
        List<Component> notes = new ArrayList<>();
        if (b.flavours().isEmpty() && b.dip() == null) notes.add(Component.translatable("journal.riverfishing.bo_n_empty"));
        if (b.tooBigFor(fish.meanG())) notes.add(Component.translatable("journal.riverfishing.bo_n_big"));
        if (b.nuisanceBait() && fish.meanG() >= 300) notes.add(Component.translatable("journal.riverfishing.bo_n_nuisance"));
        double reach = b.reachBonus(s);
        if (reach > 0) notes.add(Component.translatable("journal.riverfishing.bo_n_reach", (int) reach));
        for (Component n : notes) {
            g.drawString(this.font, fitName(n.getString(), wAll), x, y, 0xFFB05A00, false);
            y += 10;
        }

        // why: every rule that moved it, the strongest first
        y += 3;
        g.drawString(this.font, Component.translatable("journal.riverfishing.bo_why"), x, y, 0xFFB0842C, false);
        y += 11;
        List<Map.Entry<String, Double>> lines = new ArrayList<>(why.entrySet());
        lines.removeIf(e -> Math.abs(e.getValue()) < 0.005);
        lines.sort((a, c) -> Double.compare(Math.abs(c.getValue()), Math.abs(a.getValue())));
        int colW = (wAll - 12) / 2, rows = Math.max(1, (top + H - 14 - y) / 10);
        if (lines.isEmpty()) g.drawString(this.font, Component.translatable("journal.riverfishing.bo_why_none"), x, y, GuiStyle.TEXT_HINT, false);
        for (int i = 0; i < lines.size() && i < rows * 2; i++) {
            int lx = x + (i / rows) * (colW + 12), ly = y + (i % rows) * 10;
            String k = lines.get(i).getKey();
            int slash = k.indexOf('/');
            String rule = cap(Component.translatable("journal.riverfishing.bw_" + k.substring(slash + 1)).getString());
            if (slash > 0) {
                rule = Component.translatable("flavour.riverfishing." + k.substring(0, slash)).getString() + " · "
                        + rule.toLowerCase(java.util.Locale.ROOT);
            }
            double mult = Math.exp(0.85 * lines.get(i).getValue());
            String v = times2(mult);
            g.drawString(this.font, fitName(rule, colW - this.font.width(v) - 6), lx, ly, GuiStyle.TEXT, false);
            g.drawString(this.font, v, lx + colW - this.font.width(v), ly, mult >= 1 ? 0xFF2E7D32 : 0xFFA0402E, false);
        }
        if (tooltip != null) g.renderComponentTooltip(this.font, tooltip, mouseX, mouseY);
    }

    /** A recipe as items: the stacks with a "+" between, an arrow, what comes out. Returns the x after it. */
    private int boChain(GuiGraphics g, int x, int y, List<ItemStack> in, ItemStack out, int mx, int my) {
        for (int i = 0; i < in.size(); i++) {
            if (i > 0) {
                g.drawString(this.font, "+", x, y + 5, GuiStyle.TEXT_HINT, false);
                x += 8;
            }
            x = boStack(g, in.get(i), x, y, mx, my);
        }
        g.drawString(this.font, "→", x + 1, y + 5, GuiStyle.TEXT, false);
        return boStack(g, out, x + 12, y, mx, my);
    }

    private int boStack(GuiGraphics g, ItemStack st, int x, int y, int mx, int my) {
        g.renderItem(st, x, y);
        g.renderItemDecorations(this.font, st, x, y);
        if (over(mx, my, x, y, x + 16, y + 16)) hoverStack = st;
        return x + 18;
    }

    /** A step's heading and its line of explanation around a chain; returns the y under it. */
    private int boStep(GuiGraphics g, int x, int y, int w, String head, String text) {
        g.drawString(this.font, Component.translatable(head), x, y, 0xFF8A5A00, false);
        int ty = y + 30;
        for (net.minecraft.util.FormattedCharSequence seq : this.font.split(Component.translatable(text), w)) {
            g.drawString(this.font, seq, x, ty, GuiStyle.TEXT_HINT, false);
            ty += 10;
        }
        return ty + 5;
    }

    /**
     * §boilie-builder: how the boilie on the builder is made, drawn as items — the flavour bottles it needs,
     * the paste with the wheat for its size and the kelp for its float, the boil, and the snowman or the dip
     * where it has one. The guide says the same in words; this is the one you just built.
     */
    private void renderBoilieMake(GuiGraphics g, int mouseX, int mouseY) {
        int x = left + 12, wAll = W - 32, y = top + 42;
        com.riverfishing.fish.Boilie b = boBuilt();
        java.util.LinkedHashSet<com.riverfishing.fish.Flavour> bottles = new java.util.LinkedHashSet<>(b.flavours());
        if (b.dip() != null) bottles.add(b.dip());

        // 1. the bottles
        int ny = boStep(g, x, y, wAll, "journal.riverfishing.bm_bottle", "journal.riverfishing.bm_bottle_t");
        int cx = x;
        if (bottles.isEmpty()) {
            g.drawString(this.font, Component.translatable("journal.riverfishing.bm_no_flavour"), x, y + 16, GuiStyle.TEXT_HINT, false);
        }
        for (com.riverfishing.fish.Flavour f : bottles) {
            cx = boChain(g, cx, y + 11, List.of(new ItemStack(net.minecraft.world.item.Items.GLASS_BOTTLE),
                    new ItemStack(net.minecraft.world.item.Items.SUGAR), ingredientOf(f)),
                    com.riverfishing.item.FlavourItem.make(f), mouseX, mouseY) + 14;
        }
        y = ny;

        // 2. the paste: wheat is the size, kelp the float; a snowman is a bottom ball and a pop-up
        boolean snow = b.buoyancy() == com.riverfishing.fish.Boilie.Buoyancy.SNOWMAN;
        List<com.riverfishing.fish.Flavour> fl = b.flavours();
        com.riverfishing.fish.Boilie low = snow
                ? new com.riverfishing.fish.Boilie(fl.isEmpty() ? List.of() : List.of(fl.get(0)), com.riverfishing.fish.Boilie.Buoyancy.SINKER, b.sizeMm(), b.meal(), null)
                : new com.riverfishing.fish.Boilie(fl, b.buoyancy(), b.sizeMm(), b.meal(), null);
        com.riverfishing.fish.Boilie high = snow
                ? new com.riverfishing.fish.Boilie(fl.isEmpty() ? List.of() : List.of(fl.get(fl.size() - 1)), com.riverfishing.fish.Boilie.Buoyancy.POPUP, b.sizeMm(), b.meal(), null)
                : null;
        ny = boStep(g, x, y, wAll, "journal.riverfishing.bm_paste", snow ? "journal.riverfishing.bm_paste_snow" : "journal.riverfishing.bm_paste_t");
        cx = x;
        for (com.riverfishing.fish.Boilie ball : high == null ? List.of(low) : List.of(low, high)) {
            int wheat = boSize + 1;
            List<ItemStack> in = new ArrayList<>();
            // one to a slot: the paste counts the SLOTS that hold wheat and kelp, not how many are stacked in one
            for (int k = 0; k < wheat; k++) in.add(new ItemStack(net.minecraft.world.item.Items.WHEAT));
            in.add(new ItemStack(net.minecraft.world.item.Items.EGG));
            for (com.riverfishing.fish.Flavour f : ball.flavours()) in.add(com.riverfishing.item.FlavourItem.make(f));
            int kelp = switch (ball.buoyancy()) {
                case WAFTER -> 1;
                case POPUP -> 2;
                default -> 0;
            };
            for (int k = 0; k < kelp; k++) in.add(new ItemStack(net.minecraft.world.item.Items.DRIED_KELP));
            if (ball.meal()) in.add(new ItemStack(ModItems.FISH_MEAL.get()));
            ItemStack paste = new ItemStack(ModItems.BOILIE_PASTE.get(), wheat * 2);
            com.riverfishing.item.BoiliePasteItem.write(paste, ball);
            cx = boChain(g, cx, y + 11, in, paste, mouseX, mouseY) + 14;
        }
        y = ny;

        // 3. the boil
        ny = boStep(g, x, y, wAll, "journal.riverfishing.bm_boil", "journal.riverfishing.bm_boil_t");
        ItemStack onePaste = new ItemStack(ModItems.BOILIE_PASTE.get());
        com.riverfishing.item.BoiliePasteItem.write(onePaste, low);
        cx = boChain(g, x, y + 11, List.of(onePaste, new ItemStack(net.minecraft.world.item.Items.WATER_BUCKET),
                new ItemStack(net.minecraft.world.item.Items.CAULDRON), new ItemStack(net.minecraft.world.item.Items.CAMPFIRE)),
                boilieStack(low, com.riverfishing.item.BoiliePasteItem.PER_PASTE), mouseX, mouseY);
        y = ny;

        // 4. the snowman, 5. the dip — where this boilie has them
        if (snow) {
            ny = boStep(g, x, y, wAll, "journal.riverfishing.bm_snow", "journal.riverfishing.bm_snow_t");
            boChain(g, x, y + 11, List.of(boilieStack(low, 1), boilieStack(high, 1)),
                    boilieStack(new com.riverfishing.fish.Boilie(fl, b.buoyancy(), b.sizeMm(), b.meal(), null), 1), mouseX, mouseY);
            y = ny;
        }
        if (b.dip() != null) {
            ny = boStep(g, x, y, wAll, "journal.riverfishing.bm_dip", "journal.riverfishing.bm_dip_t");
            com.riverfishing.fish.Boilie dry = new com.riverfishing.fish.Boilie(fl, b.buoyancy(), b.sizeMm(), b.meal(), null);
            boChain(g, x, y + 11, List.of(com.riverfishing.item.FlavourItem.make(b.dip()), boilieStack(dry, 16)),
                    boilieStack(b, 16), mouseX, mouseY);
            y = ny;
        }

        Component link = Component.translatable("journal.riverfishing.bo_recipes");
        boolean hovLink = over(mouseX, mouseY, x, y - 1, x + this.font.width(link), y + 10);
        g.drawString(this.font, link, x, y, hovLink ? 0xFFD8A93C : 0xFF8A5A00, false);
        boHits.add(new int[]{x, y - 1, x + this.font.width(link), y + 10, BC_GUIDE, 0});
    }

    /** A click on the boilie page: whatever {@link #boHits} says was drawn there. */
    private boolean boilieClick(double mx, double my, int button) {
        for (int[] h : boHits) {
            if (!over(mx, my, h[0], h[1], h[2], h[3])) continue;
            int v = h[5];
            if (button == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT) {   // a right click only ever empties a slot
                if (h[4] != BC_SLOT) return false;
                boSlot[v] = null;
                boTarget = v;
                return true;
            }
            switch (h[4]) {
                case BC_PAGE -> boPage = v;
                case BC_SLOT -> boTarget = v;
                case BC_PAL -> {
                    com.riverfishing.fish.Flavour f = com.riverfishing.fish.Flavour.values()[v];
                    if (boSlot[boTarget] == f) {
                        boSlot[boTarget] = null;   // the same flavour again takes it out
                    } else {
                        if (boTarget < 2 && boSlot[1 - boTarget] == f) boSlot[1 - boTarget] = null;   // one flavour, one slot
                        boSlot[boTarget] = f;
                        if (boTarget == 0 && boSlot[1] == null) boTarget = 1;
                    }
                }
                case BC_FORM -> boForm = v;
                case BC_SIZE -> boSize = v;
                case BC_MEAL -> boMeal = !boMeal;
                case BC_SEASON -> boSeason = v;
                case BC_TIME -> boTime = v;
                case BC_WEATHER -> boWeather = v;
                case BC_WATER -> boWater = v;
                case BC_BED -> boBed = v;
                case BC_CURRENT -> boCurrent = !boCurrent;
                case BC_COVER -> boCover = !boCover;
                case BC_SHIFT -> boShift = !boShift;
                case BC_FISH -> boFishStep(v);
                case BC_NOW -> boNow();
                case BC_CLEAR -> {
                    java.util.Arrays.fill(boSlot, null);
                    boTarget = 0;
                }
                case BC_ROW, BC_CELL -> {
                    int f = h[4] == BC_ROW ? v : v / 8, c = h[4] == BC_ROW ? BOILIE_HEADS.length - 1 : v % 8;
                    boFrom(c < BOILIE_SCENES.length ? BOILIE_SCENES[c] : boilieNow());
                    // the table's boilie is a plain 20 mm bottom one: the builder shows the cell's number, not a
                    // pop-up left over from last time
                    boForm = 0;
                    boSize = 2;
                    boMeal = false;
                    boSlot[0] = com.riverfishing.fish.Flavour.values()[f];
                    boSlot[1] = null;
                    boSlot[2] = null;
                    boTarget = 1;
                    boPage = 1;
                }
                case BC_HEAD -> {
                    boFrom(v < BOILIE_SCENES.length ? BOILIE_SCENES[v] : boilieNow());
                    boPage = 1;
                }
                case BC_GUIDE -> openGuide("boilies");
                default -> { }
            }
            return true;
        }
        return false;
    }

    // ---- §journal-links (1.1.0): the book cross-referenced — a species' baits open the bait, a bait's fish
    // open the species, so "what does it take" and "what takes it" are one click apart ----

    /** A bait named on a species sheet, {x0, y0, x1, y1, tab, index}: a click opens its page. */
    private final List<int[]> sheetLinks = new ArrayList<>();
    /** A fish on a bait's page, {x0, y0, x1, y1}, parallel to {@link #catFishIds}: a click opens the species. */
    private final List<int[]> catFishLinks = new ArrayList<>();
    private final List<String> catFishIds = new ArrayList<>();

    /** Is a line from y0 to y1 inside the page, where the scroll has not taken it under the edge? */
    private boolean onPage(int y0, int y1) {
        return y0 >= top + 42 && y1 <= top + H - 24;   // the sheet's clip: under its header, over the back line
    }

    private static boolean baitIs(Cat c, String id) {
        return id.equals(c.id()) || id.equals(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(c.stack().getItem()).getPath());
    }

    /** Where a bait lives in the gear bookmark, {tab, index}, or null for one the shelves do not carry. */
    private int[] baitPage(String id) {
        for (int i = 0; i < baitCat.size(); i++) if (baitIs(baitCat.get(i), id)) return new int[]{TAB_BAIT, i};
        for (int i = 0; i < lureCat.size(); i++) if (baitIs(lureCat.get(i), id)) return new int[]{TAB_LURE, i};
        return null;
    }

    /**
     * The species' three best baits, each with its picture and a link to its own page: the sheet says what the
     * fish takes, the click says how that bait is had and what else takes it. Returns the y under them.
     */
    private int baitLinks(GuiGraphics g, com.riverfishing.fish.FishCard c, int x, int y, int w) {
        List<String> ids = c.baitsRanked().stream().limit(3).map(Map.Entry::getKey).toList();
        if (ids.isEmpty()) return railLine(g, "guide.riverfishing.bait", "—", x, y, w);
        g.drawString(this.font, Component.translatable("guide.riverfishing.bait"), x, y, GuiStyle.TEXT_HINT, false);
        y += 11;
        int cx = x;
        for (String id : ids) {
            int[] page = baitPage(id);
            ItemStack st = page == null ? ItemStack.EMPTY : (page[0] == TAB_BAIT ? baitCat : lureCat).get(page[1]).stack();
            String name = st.isEmpty() ? Component.translatable("item.riverfishing." + id).getString() : st.getHoverName().getString();
            int wd = 18 + this.font.width(name);
            if (cx > x && cx + wd > x + w) {
                cx = x;
                y += 18;
            }
            boolean hov = page != null && over(hoverX, hoverY, cx, y, cx + wd, y + 16);
            if (hov) g.fill(cx - 1, y - 1, cx + wd + 1, y + 17, 0x22000000);
            if (!st.isEmpty()) g.renderItem(st, cx, y);
            g.drawString(this.font, name, cx + 18, y + 4, page == null ? GuiStyle.TEXT : hov ? 0xFFD8A93C : 0xFF8A5A00, false);
            if (page != null && onPage(y, y + 16)) sheetLinks.add(new int[]{cx, y, cx + wd, y + 16, page[0], page[1]});
            cx += wd + 10;
        }
        return y + 19;
    }

    /** A bait's page, from a species sheet. */
    private void openCat(int tabOf, int index) {
        go(SEC_GEAR, tabOf == TAB_BAIT ? GEAR_KINDS.length : GEAR_KINDS.length + 1);
        catDetail = index;
    }

    /** A species' page, on its "how to catch" sheet — where its baits are — from a bait's page. */
    private void openSpecies(String sp) {
        go(SEC_FISH, 1);
        detail = sp;
        scroll = 0;
    }

    /** Open a guide page by id, on its chapter — the boilie page's link and the §guide-nudge both come here. */
    private void openGuide(String id) {
        for (int i = 0; i < guideCat.size(); i++) {
            if (guideCat.get(i).id().equals(id)) {
                go(SEC_GUIDE, 0);
                guideChapter = guideGroup.getOrDefault(id, 0);
                catDetail = i;
                return;
            }
        }
    }

    // ---- GUIDE: chapters on the left, the chapter's pages on the right ----

    private static final int CHAPTER_ROW = 18, GUIDE_ROW = 26, GUIDE_TOP = 28;

    private int guideChapters() {
        int n = 0;
        for (int gi : guideGroup.values()) n = Math.max(n, gi + 1);
        return n;
    }

    /**
     * §journal-guide (1.1.0): the shelf was thirty-one pages in two columns under nine headings, all on one
     * scroll. It is a table of contents now: pick a chapter, and only its pages are listed — each with its
     * first line, so a title that does not say enough is not a page you have to open to find out.
     */
    private void renderGuideShelf(GuiGraphics g, int mouseX, int mouseY) {
        int x = left + 12, y0 = top + GUIDE_TOP, chapters = guideChapters();
        g.fill(x - 2, y0 - 2, x + FAM_W + 2, y0 + chapters * CHAPTER_ROW + 2, 0x22000000);
        for (int i = 0; i < chapters; i++) {
            int y = y0 + i * CHAPTER_ROW;
            boolean hov = mouseX >= x && mouseX < x + FAM_W && mouseY >= y && mouseY < y + CHAPTER_ROW;
            if (i == guideChapter) {
                g.fill(x, y, x + FAM_W, y + CHAPTER_ROW, 0x55B08D3C);
            } else if (hov) {
                g.fill(x, y, x + FAM_W, y + CHAPTER_ROW, 0x22000000);
            }
            g.drawString(this.font, fitName(Component.translatable("guidegroup.riverfishing." + i).getString(), FAM_W - 8),
                    x + 4, y + 5, i == guideChapter ? GuiStyle.TEXT : GuiStyle.TEXT_HINT, false);
        }

        int px = left + FAM_W + 24, pw = W - FAM_W - 44;
        int y = y0;
        for (int i = 0; i < guideCat.size(); i++) {
            Cat e = guideCat.get(i);
            if (guideGroup.getOrDefault(e.id(), -1) != guideChapter) {
                catRects[i][0] = Integer.MIN_VALUE / 2;   // not on this chapter: nowhere a click can land
                catRects[i][1] = Integer.MIN_VALUE / 2;
                continue;
            }
            catRects[i][0] = px;
            catRects[i][1] = y;
            boolean hov = mouseX >= px && mouseX < px + pw && mouseY >= y && mouseY < y + GUIDE_ROW - 2;
            if (hov) g.fill(px - 2, y - 1, px + pw, y + GUIDE_ROW - 3, 0x22000000);
            g.renderItem(e.stack(), px, y + 3);
            g.drawString(this.font, fitName(e.stack().getHoverName().getString(), pw - 24), px + 22, y + 2,
                    hov ? 0xFF8A5A00 : GuiStyle.TEXT, false);
            g.drawString(this.font, fitName(guideLead(e.id()), pw - 24), px + 22, y + 13, GuiStyle.GHOST, false);
            y += GUIDE_ROW;
        }
        lastCatH = 0;
        lastViewH = 1;
    }

    /** A guide page's first line of prose — what the page is about, in its own words. */
    private static String guideLead(String id) {
        for (String para : I18n.get("guide.riverfishing." + id + ".text").split("\n")) {
            if (!para.isBlank() && !para.startsWith(HEADING)) return para.trim();
        }
        return "";
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
            int contentTop = top + 58, contentBottom = top + H - 24;
            scroll = Mth.clamp(scroll, 0, Math.max(0, lastCatH - (contentBottom - contentTop)));
            scissorJournal(g, left + 6, contentTop, left + W - 6, contentBottom);
            int dy = contentTop - scroll;
            dy = guideProse(g, e.id(), dy);
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
                int bx = left + 12, by = top + H - 38;
                boolean hov = mouseX >= bx && mouseX < bx + bw && mouseY >= by && mouseY < by + bh;
                g.fill(bx, by, bx + bw, by + bh, hov ? 0xFF57C063 : 0xFF3FA34A);
                g.fill(bx + 1, by + 1, bx + bw - 1, by + bh - 1, hov ? 0xFF6FD07B : 0xFF4FB459);
                g.drawCenteredString(this.font, label, bx + bw / 2, by + 3, 0xFFFFFFFF);
                linkRect[0] = bx; linkRect[1] = by; linkRect[2] = bx + bw; linkRect[3] = by + bh;
            }
            g.drawString(this.font, Component.translatable("guide.riverfishing.back"),
                    left + 12, top + H - 20, GuiStyle.GHOST, false);
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
                    left + 12, top + H - 20, GuiStyle.GHOST, false);
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
        y = obtainRender(g, y, e.stack(), mouseX, mouseY) + 4;

        if (e.kind() == Kind.ROD || e.kind() == Kind.REEL || e.kind() == Kind.LINE) {
            y = compatLines(g, y, e) + 2;
        }

        if (isBait(e.kind())) {
            g.drawString(this.font, Component.translatable("journal.riverfishing.bait_catches"),
                    left + 10, y, GuiStyle.TEXT_HINT, false);
            y += 12;
            // §catch-icons: the fish this bait takes, drawn as fish. Twelve names in a row is a
            // paragraph to read, and "what do I catch on this" is the only question the page answers.
            List<String> fish = fishIdsFor(e, 12);
            if (fish.isEmpty()) {
                g.drawString(this.font, "—", left + 10, y, GuiStyle.TEXT, false);
                y += 11;
            } else {
                int perRow = Math.max(1, (W - 24) / 18);
                for (int i = 0; i < fish.size(); i++) {
                    int fx = left + 10 + (i % perRow) * 18, fy = y + (i / perRow) * 18;
                    GuiStyle.slot(g, fx, fy);
                    boolean hov = mouseX >= fx && mouseX < fx + 16 && mouseY >= fy && mouseY < fy + 16;
                    if (hov && caught(fish.get(i))) g.fill(fx - 1, fy - 1, fx + 17, fy + 17, 0x55D8A93C);
                    drawFishIcon(g, fish.get(i), fx, fy);
                    if (hov) hoverStack = modStack(fish.get(i));
                    if (caught(fish.get(i)) && onPage(fy, fy + 16)) {   // §journal-links: a fish you know opens its page
                        catFishLinks.add(new int[]{fx, fy, fx + 16, fy + 16});
                        catFishIds.add(fish.get(i));
                    }
                }
                y += ((fish.size() + perRow - 1) / perRow) * 18;
            }
        }
        g.drawString(this.font, Component.translatable("guide.riverfishing.back"),
                left + 12, top + H - 20, GuiStyle.GHOST, false);
    }

    /**
     * §gb-table: every component of a mix, with what it does to one.
     *
     * <p>Four columns, because four things decide whether an ingredient belongs in your jar: how rich it
     * is, how coarse it is, and which way it pulls the swim. The numbers are the pantry's own — the same
     * map the engine averages when it scores a fed spot — so this table cannot drift from the game.
     */
    private void renderPantryTable(GuiGraphics g) {
        int x = left + 12, wAll = W - 32;
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

    /**
     * §craft-grid: "how to get" is the recipe DRAWN — the slots where the bench wants them, the
     * result past an arrow. It used to be the ingredient names in a row, which said what goes in
     * and never where, so the answer to "how do I make this" was still a trip out to JEI.
     */
    private int obtainRender(GuiGraphics g, int y, ItemStack stack, int mouseX, int mouseY) {
        Craft craft = craftOf(stack);
        if (craft != null) {
            g.drawString(this.font, Component.translatable("journal.riverfishing.obtain_craft"),
                    left + 10, y, GuiStyle.TEXT_HINT, false);
            return recipeGrid(g, left + 10, y + 12, craft, mouseX, mouseY);
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

    /**
     * The species this bait takes, keenest first — ids, so a caller can draw them. §bait-mp: read off the cards
     * the server sends with the journal (their "bait" map is the profiles' own scores): the profiles themselves
     * exist only in single player, so on a server this list used to come up empty.
     */
    private List<String> fishIdsFor(Cat e, int limit) {
        List<Map.Entry<String, Float>> scored = new ArrayList<>();
        for (String sp : data.getCompound("cards").getAllKeys()) {
            Float s = card(sp).baits().get(e.id());
            if (s != null && s >= 0.5f) scored.add(Map.entry(sp, s));
        }
        scored.sort((a, b) -> Float.compare(b.getValue(), a.getValue()));
        return scored.stream().limit(limit).map(Map.Entry::getKey).collect(Collectors.toList());
    }

    private List<String> fishFor(Cat e, int limit) {
        return fishIdsFor(e, limit).stream()
                .map(sp -> Component.translatable("fish.riverfishing." + sp).getString())
                .collect(Collectors.toList());
    }

    /** Distinct ingredient names of the first crafting recipe that yields this item, or empty. */
    private static List<String> craftIngredients(ItemStack stack) {
        Craft c = craftOf(stack);
        if (c == null) return List.of();
        LinkedHashSet<String> names = new LinkedHashSet<>();
        for (ItemStack[] opt : c.cells()) {
            if (opt.length > 0) names.add(opt[0].getHoverName().getString());
        }
        return new ArrayList<>(names);
    }

    /** A recipe's cells in bench order, every cell holding each item its ingredient accepts. */
    private record Craft(ItemStack[][] cells, int w, int h, ItemStack result) {}

    private static final ItemStack[] EMPTY_CELL = new ItemStack[0];

    /**
     * The bench: {@code w × h} slots, an arrow, the result. A cell whose ingredient is a TAG holds
     * every item that tag accepts and steps through them once a second, the way the recipe book
     * does — one frozen example would read as the only thing that works.
     *
     * <p>Returns the y below the grid.
     */
    private int recipeGrid(GuiGraphics g, int x, int y, Craft c, int mouseX, int mouseY) {
        long sec = System.currentTimeMillis() / 1000L;
        for (int r = 0; r < c.h(); r++) {
            for (int col = 0; col < c.w(); col++) {
                int sx = x + col * 18, sy = y + r * 18;
                GuiStyle.slot(g, sx, sy);
                ItemStack[] opt = c.cells()[r * c.w() + col];
                if (opt.length == 0) continue;
                ItemStack it = opt[(int) Math.floorMod(sec, opt.length)];
                g.renderItem(it, sx, sy);
                if (mouseX >= sx && mouseX < sx + 16 && mouseY >= sy && mouseY < sy + 16) hoverStack = it;
            }
        }
        int ax = x + c.w() * 18 + 4, ay = y + (c.h() * 18 - 16) / 2;
        g.drawString(this.font, "\u2192", ax, ay + 4, GuiStyle.TEXT_HINT, false);
        int rx = ax + 14;
        GuiStyle.slot(g, rx, ay);
        g.renderItem(c.result(), rx, ay);
        if (mouseX >= rx && mouseX < rx + 16 && mouseY >= ay && mouseY < ay + 16) hoverStack = c.result();
        return y + c.h() * 18;
    }

    /**
     * §craft-grid: the first crafting recipe that yields this item, laid out the way the bench
     * wants it. Shaped recipes keep their real shape; shapeless ones have none, so they pack three
     * to a row. Nothing here is hand-written per item — the scan finds whatever the data pack
     * defines, so a recipe changed in JSON changes the page with it.
     *
     * <p>The one scan behind both the grid and {@link #craftIngredients}.
     */
    private static Craft craftOf(ItemStack stack) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;
        for (net.minecraft.world.item.crafting.RecipeHolder<?> holder : mc.level.getRecipeManager().getRecipes()) {
            ItemStack res;
            try {
                res = holder.value().getResultItem(mc.level.registryAccess());
            } catch (Throwable ignored) {
                continue;
            }
            if (res == null || res.isEmpty() || res.getItem() != stack.getItem()) continue;
            NonNullList<Ingredient> ings = holder.value().getIngredients();
            if (ings.isEmpty()) continue;
            int w, h;
            if (holder.value() instanceof net.minecraft.world.item.crafting.ShapedRecipe sr) {
                w = sr.getWidth();
                h = sr.getHeight();
            } else {
                w = Math.min(3, ings.size());
                h = (ings.size() + w - 1) / w;
            }
            if (w <= 0 || h <= 0 || w > 3 || h > 3) continue;
            ItemStack[][] cells = new ItemStack[w * h][];
            for (int i = 0; i < cells.length; i++) {
                Ingredient ing = i < ings.size() ? ings.get(i) : Ingredient.EMPTY;
                cells[i] = ing.isEmpty() ? EMPTY_CELL : ing.getItems();
            }
            return new Craft(cells, w, h, res);
        }
        return null;
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

    /**
     * §pattern: the twelve pattern families, and which of them you have landed of this species. A row of
     * cells rather than a list of names — the index IS a collection, and a board you can see the holes
     * in is the only thing a collection board is for. The swatch is the family's own hue turn, so the
     * grid reads left to right as the sequence the bands actually paint.
     */
    private int patternRow(GuiGraphics g, ResourceLocation id, int y) {
        // §pattern-gate: a species that does not wear a pattern has no board to fill, and an old world
        // whose journal recorded families for a perch simply stops drawing them.
        if (!com.riverfishing.registry.ModItemTags.patterned(id)) return y;
        String[] fam = com.riverfishing.fish.Pattern.families();
        int seen = com.riverfishing.fishing.JournalData.patternsSeen(data, id);
        y += 6;
        g.drawString(this.font, Component.translatable("journal.riverfishing.patterns",
                Integer.bitCount(seen), fam.length), left + 10, y, GuiStyle.TEXT_HINT, false);
        y += 12;
        for (int i = 0; i < fam.length; i++) {
            int x = left + 10 + i * 13;
            g.fill(x, y, x + 11, y + 11, GuiStyle.TEXT_HINT);        // the frame, so a pale band shows
            g.fill(x + 1, y + 1, x + 10, y + 10, (seen & (1 << i)) != 0
                    ? 0xFF000000 | com.riverfishing.fish.Pattern.swatch(i) : 0xFFE8DCC0);
        }
        return y + 15;
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
    /**
     * §contracts-b1: the papers in the bag, above the order of the day. Each is its terms, how many
     * are caught under them, how many days are left, and whether the set is in the bag. Nothing here is
     * clickable: a contract is handed to the fisherman, not to the book.
     */
    private int contractBoard(GuiGraphics g, int y, int mouseX, int mouseY) {
        ListTag list = data.getList("contracts", 10);
        if (list.isEmpty()) return y;
        long day = data.getLong("day");
        g.drawString(this.font, Component.translatable("journal.riverfishing.contracts"),
                left + 10, y, 0xFFB0842C, false);
        y += 13;
        for (int i = 0; i < list.size(); i++) {
            CompoundTag c = list.getCompound(i);
            String sp = c.getString("Sp");
            int need = c.getInt("N");
            int have = heldFish(c);                                    // §catch-card
            boolean ready = have >= need;
            if (ready) g.fill(left + 8, y - 3, left + W - 18, y + 24, 0x38E8B430);

            drawFishIcon(g, sp, left + 10, y - 2);
            g.drawString(this.font, com.riverfishing.item.ContractItem.headline(c), left + 30, y,
                    ready ? 0xFF9A6E10 : GuiStyle.TEXT, false);
            ItemStack em = new ItemStack(net.minecraft.world.item.Items.EMERALD);
            int ex = left + W - 34;
            g.renderItem(em, ex, y - 4);
            String n = String.valueOf(c.getInt("Em"));
            g.drawString(this.font, n, ex - 4 - this.font.width(n), y, 0xFF2E7D32, false);

            StringBuilder sb = new StringBuilder();
            for (Component t : com.riverfishing.item.ContractItem.terms(c)) {
                if (sb.length() > 0) sb.append(" · ");
                sb.append(t.getString());
            }
            if (sb.length() > 0) g.drawString(this.font, sb.toString(), left + 30, y + 10, GuiStyle.TEXT_HINT, false);
            long daysLeft = c.getLong("Exp") - day;
            Component note = ready
                    ? Component.translatable("journal.riverfishing.contract_ready")
                    : Component.translatable("journal.riverfishing.contract_state", have, need, Math.max(0, daysLeft));
            g.drawString(this.font, note, left + 30, y + 20,
                    ready ? 0xFFB05A00 : daysLeft <= 1 ? 0xFFB03020 : GuiStyle.TEXT_HINT, false);
            y += 34;
        }
        return y + 4;
    }

    /**
     * How many of this species, at or over the bar, this player is carrying — loose or in a keepnet.
     *
     * <p>The counting lives in {@link com.riverfishing.fishing.Contracts#held}, which is also what the
     * server takes with. Two implementations of "do I have three bream" would be two answers, and the
     * one the row shows is not the one that decides.
     */
    private int heldFish(CompoundTag terms) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return 0;
        if (com.riverfishing.item.ContractItem.isFry(terms)) {   // §e
            return com.riverfishing.fishing.Contracts.fryHeld(mc.player.getInventory(), terms.getString("Sp"));
        }
        return com.riverfishing.fishing.Contracts.held(
                mc.player.getInventory(), terms.getString("Sp"), terms.getInt("W"), terms).size();
    }

    /** A weight bar the way an angler says it: grams under a kilo, kilos above. */
    private static String grams(int g) {
        return g >= 1000 ? String.format(java.util.Locale.ROOT, "%.1f kg", g / 1000f) : g + " g";
    }

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

    /** §species-table: the line and the hook — the two asks a species still makes of your tackle. */
    private static String tackle(com.riverfishing.fish.FishCard c) {
        String line = Component.translatable("linetype.riverfishing." + c.lineType()).getString();   // §line-name
        String s = c.lineDiameter() > 0 ? String.format(java.util.Locale.ROOT, "%s %.2f", line, c.lineDiameter()) : line;
        return c.hookIdeal() > 0 ? s + " · №" + c.hookIdeal() : s;
    }

    // ---- input ----

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        // §fish-list: the species list counts in ROWS and everything else counts in PIXELS. They share
        // one `scroll`, so the wheel has to be told which unit it is turning — mixing them is how a list
        // ends up scrolling nineteen species per notch.
        if (tab == TAB_BOILIE) {   // §boilie-builder
            for (int[] h : boHits) {
                if (h[4] == BC_FISH && over(toJournalX(mouseX), toJournalY(mouseY), h[0], h[1], h[2], h[3])) {
                    boFishStep(-(int) Math.signum(scrollY));
                    return true;
                }
            }
            return true;
        }
        if (tab == TAB_FISH && detail == null) {
            scroll = Mth.clamp(scroll - (int) Math.signum(scrollY) * 3, 0,
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
        scroll = Mth.clamp(scroll - (int) (scrollY * 24), 0, Math.max(0, lastCatH - lastViewH));   // §journal-scroll-ease glides it
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
            if (key == com.mojang.blaze3d.platform.InputConstants.KEY_BACKSPACE && !search.isEmpty()) {          // backspace
                search = search.substring(0, search.length() - 1);
                scroll = 0;
                return true;
            }
            // §key-codes: the constants — 26.3 moved to SDL scancodes (Esc 41, Backspace 42), 256 and 259 are gone
            if (key == com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE) {                                // escape closes the box, not the journal
                if (!search.isEmpty()) {
                    search = "";
                    scroll = 0;
                } else {
                    searchFocus = false;
                }
                return true;
            }
        }
        // §journal-book: Escape on an opened page goes back to its list; on a list it closes the book
        if (key == com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE && back()) return true;
        return super.keyPressed(key, scancode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // §journal-scale: hit-test in journal space (the panel is drawn scaled around the screen centre).
        mouseX = toJournalX(mouseX);
        mouseY = toJournalY(mouseY);
        // §journal-book: a right click anywhere on an opened page goes back to its list
        // §mouse-buttons: the constants, not 0 and 1 — 26.3 numbers the buttons as SDL does (left 1, right 3)
        if (button == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT && back()) return true;
        if (button == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT && tab == TAB_BOILIE && boilieClick(mouseX, mouseY, button)) return true;
        if (button == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT) {
            for (int i = 0; i < BOOK_KEYS.length; i++) {
                int by = top + BOOKMARK_TOP + i * BOOKMARK_STEP;
                if (mouseX >= left - BOOKMARK_OUT - BOOKMARK_PULL && mouseX < left
                        && mouseY >= by && mouseY < by + BOOKMARK_H) {
                    if (i != section) go(i, 0);
                    else back();   // the open bookmark again: back to its own first page
                    return true;
                }
            }
            String[] pages = subKeys();
            for (int i = 0; i < pages.length; i++) {
                int x = tabX(pages, i), w = tabW(pages, i);
                if (mouseX >= x && mouseX < x + w && mouseY >= top + TAB_Y0 && mouseY < top + TAB_Y1) {
                    if (section == SEC_FISH) {   // a species' sheets: the species stays open
                        sub = i;
                        scroll = 0;
                    } else if (i != sub || catDetail >= 0) {
                        go(section, i);
                    }
                    return true;
                }
            }
            // an opened page closes from its "back" line; a click elsewhere on it is just a click
            if ((detail != null || catDetail >= 0) && mouseY >= top + H - 24 && mouseY < top + H - 10) {
                back();
                return true;
            }
            if (tab == TAB_FISH) {
                if (detail != null) {
                    for (int[] l : sheetLinks) {
                        if (over(mouseX, mouseY, l[0], l[1], l[2], l[3])) {
                            openCat(l[4], l[5]);
                            return true;
                        }
                    }
                }
                if (detail != null && fishBoLink[2] > 0 && over(mouseX, mouseY, fishBoLink[0], fishBoLink[1], fishBoLink[2], fishBoLink[3])) {
                    openBuilder(detail);   // §boilie-builder: the species sheet's way into the builder
                    return true;
                }
                if (detail != null) return true;
                if (families.isEmpty()) buildFamilies();
                // The search box takes the keyboard on a click and gives it up on a click anywhere else,
                // so typing "щук" never eats a keystroke the rest of the screen wanted.
                int sx = left + FAM_W + 20, sy = top + SEARCH_Y;
                boolean onSearch = mouseX >= sx - 1 && mouseX < sx + COL_W - 15
                        && mouseY >= sy - 1 && mouseY < sy + 15;
                searchFocus = onSearch;
                if (onSearch) return true;
                for (int i = 0; i <= families.size(); i++) {
                    int y = top + LIST_TOP + i * LIST_ROW;
                    if (mouseX >= left + 12 && mouseX < left + 12 + FAM_W && mouseY >= y && mouseY < y + LIST_ROW) {
                        family = i;
                        scroll = 0;
                        return true;
                    }
                }
                for (int i = 0; i < listRows() && i + scroll < shown.size(); i++) {
                    int y = top + LIST_TOP + i * LIST_ROW;
                    if (mouseX >= sx && mouseX < sx + COL_W - 16 && mouseY >= y && mouseY < y + LIST_ROW
                            && caught(shown.get(i + scroll))) {
                        detail = shown.get(i + scroll);
                        scroll = 0;
                        return true;
                    }
                }
            } else if (tab == TAB_QUEST) {
                int contentTop = top + ANGLER_TOP, contentBottom = top + H - 14;
                for (int i = 0; i < Quests.ALL.size(); i++) {
                    int x = questRects[i][0], y = questRects[i][1];
                    if (mouseX >= x - 2 && mouseX < left + W - 18 && mouseY >= y - 2 && mouseY < y + 12
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
            } else if (tab == TAB_BOILIE) {
                if (boilieClick(mouseX, mouseY, button)) return true;
            } else {   // the gear bookmark's shelves and the guide
                // §discord: test the link button before the "any click closes the page" rule below.
                if (linkRect[2] > 0 && mouseX >= linkRect[0] && mouseX < linkRect[2]
                        && mouseY >= linkRect[1] && mouseY < linkRect[3]) {
                    // Vanilla's confirm-link flow: it shows the URL, offers "copy link", then opens it.
                    net.minecraft.client.gui.screens.ConfirmLinkScreen.confirmLinkNow(this, DISCORD_URL);
                    return true;
                }
                if (catDetail >= 0) {
                    for (int i = 0; i < catFishLinks.size(); i++) {
                        int[] l = catFishLinks.get(i);
                        if (over(mouseX, mouseY, l[0], l[1], l[2], l[3])) {
                            openSpecies(catFishIds.get(i));
                            return true;
                        }
                    }
                    return true;
                }
                List<Cat> list = tabList();
                // §journal-guide: a chapter on the left, one of its pages on the right
                if (tab == TAB_GUIDE) {
                    for (int i = 0; i < guideChapters(); i++) {
                        int y = top + GUIDE_TOP + i * CHAPTER_ROW;
                        if (mouseX >= left + 12 && mouseX < left + 12 + FAM_W && mouseY >= y && mouseY < y + CHAPTER_ROW) {
                            guideChapter = i;
                            return true;
                        }
                    }
                    for (int i = 0; i < list.size(); i++) {
                        int x = catRects[i][0], y = catRects[i][1];
                        if (mouseX >= x && mouseX < left + W - 20 && mouseY >= y && mouseY < y + GUIDE_ROW - 2) {
                            catDetail = i;
                            scroll = 0;
                            return true;
                        }
                    }
                    return super.mouseClicked(mouseX, mouseY, button);
                }
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
                // every shelf here is a table of 17 px rows across the page (lures included — their rows
                // used to fall through this handler and could not be opened at all)
                int rowH = 17, rowW = W - 32;
                int contentTop = top + 38, contentBottom = top + H - 14;
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
