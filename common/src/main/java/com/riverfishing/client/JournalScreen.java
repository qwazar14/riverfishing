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
    private static final int H = 363;      // §journal-size: +15% headroom so text never clips
    private static final int MAX_W = 391;  // §journal-size: +15% wider for the same reason
    // §fish-grid-fit (0.5.0): rows are capped by the panel height and the COLUMN COUNT grows with the
    // species list (54 species → 3 columns) — the grid can never run past the journal's border again.
    private static final int ROWS = (H - GRID_TOP - 12) / ROW_H;
    private static final int COLS = (SPECIES.length + ROWS - 1) / ROWS;
    // Panel width adapts to the screen (GUI scale) so it never clips off-screen; columns + illustration follow.
    private int W = MAX_W;
    private int COL_W = (MAX_W - 20) / COLS;
    private int ILLUS_W = 240;
    private int ILLUS_H = 160;

    private static final int TAB_FISH = 0;
    private static final int TAB_BAIT = 1;
    private static final int TAB_GEAR = 2;
    private static final int TAB_QUEST = 3;
    private static final int TAB_SKILL = 4;
    private static final int TAB_GUIDE = 5;
    private static final String[] TAB_KEYS = {
            "journal.riverfishing.tab_fish", "journal.riverfishing.tab_bait",
            "journal.riverfishing.tab_gear", "journal.riverfishing.tab_quest",
            "journal.riverfishing.tab_skill", "journal.riverfishing.tab_guide"};

    private enum Kind { NATURAL, LURE, GROUNDBAIT, ROD, REEL, LINE, RIG, GUIDE }

    private final List<Cat> guideCat = new ArrayList<>();

    /** §guide (0.5.0): a how-to entry — an icon carrying the guide title, text from guide.riverfishing.<id>. */
    private void addGuide(String id, ItemStack icon) {
        icon.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                Component.translatable("guide.riverfishing." + id + ".title")
                        .withStyle(s -> s.withItalic(false)));
        guideCat.add(new Cat(icon, Kind.GUIDE, id));
    }

    private record Cat(ItemStack stack, Kind kind, String id) {}

    private final CompoundTag data;
    private final List<Cat> baitCat = new ArrayList<>();
    private final List<Cat> gearCat = new ArrayList<>();
    private final int[][] catRects;
    /** Quest rows' {x,y} from the last render (§quest-claim) + optimistic locally-claimed ids. */
    private final int[][] questRects = new int[Quests.ALL.size()][2];
    private final java.util.Set<String> claimedNow = new java.util.HashSet<>();
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

    public JournalScreen(CompoundTag data) {
        super(Component.translatable("journal.riverfishing.header"));
        this.data = data;
        for (RegistrySupplier<Item> ro : ModItems.ALL) {
            Item it = ro.get();
            if (it instanceof BaitItem b) {
                baitCat.add(new Cat(new ItemStack(it), b.artificial() ? Kind.LURE : Kind.NATURAL, b.baitId()));
            } else if (it instanceof GroundbaitItem gb) {
                baitCat.add(new Cat(new ItemStack(it), Kind.GROUNDBAIT, gb.category()));
            } else if (it instanceof RodItem) {
                gearCat.add(new Cat(new ItemStack(it), Kind.ROD, ""));
            } else if (it instanceof ReelItem) {
                gearCat.add(new Cat(new ItemStack(it), Kind.REEL, ""));
            } else if (it instanceof LineItem) {
                gearCat.add(new Cat(new ItemStack(it), Kind.LINE, ""));
            } else if (it instanceof RigItem ri && !isInternalRig(ri.rigType())) {
                gearCat.add(new Cat(new ItemStack(it), Kind.RIG, ""));
            }
        }
        Comparator<Cat> byKindThenName = Comparator.comparingInt((Cat e) -> e.kind().ordinal())
                .thenComparing(e -> e.stack().getHoverName().getString());
        baitCat.sort(byKindThenName);
        gearCat.sort(byKindThenName);

        // §guide (0.5.0): the how-to shelf — mechanics that deserve a page, newest first.
        addGuide("drag", modStack("reel_7000"));
        addGuide("lurework", modStack("wobbler"));
        addGuide("stress", modStack("line_mono_030"));
        addGuide("livebait", modStack("livebait"));
        addGuide("topwater", modStack("popper"));
        addGuide("trolling", modStack("trolling_rod"));
        addGuide("biggame", modStack("yellowfin_tuna"));
        addGuide("legendary", modStack("blue_marlin"));
        addGuide("market", new ItemStack(net.minecraft.world.item.Items.EMERALD));
        addGuide("coop", new ItemStack(net.minecraft.world.item.Items.LEAD));

        catRects = new int[Math.max(guideCat.size(), Math.max(baitCat.size(), gearCat.size()))][2];
    }

    private static ItemStack modStack(String path) {
        return new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM
                .get(com.riverfishing.RiverFishing.id(path)));
    }

    private static boolean isInternalRig(RigType t) {
        // WINTER included (0.5.0): it lives INSIDE the winter rod (native rig) — never separate gear.
        return t == RigType.PRIMITIVE || t == RigType.FLOAT_LIGHT || t == RigType.FLOAT || t == RigType.PREDATOR
                || t == RigType.WINTER;
    }

    public static void open(CompoundTag data) {
        JournalScreen next = new JournalScreen(data);
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
        this.COL_W = (this.W - 20) / COLS;
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
        this.renderBackground(g, mouseX, mouseY, partialTick);
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
        } else {
            List<Cat> list = tab == TAB_BAIT ? baitCat : tab == TAB_GUIDE ? guideCat : gearCat;
            if (catDetail >= 0 && catDetail < list.size()) {
                renderCatDetail(g, list.get(catDetail));
            } else {
                if (tab == TAB_BAIT) {
                    g.drawString(this.font, Component.translatable("journal.riverfishing.tab_bait_hint"),
                            left + 10, top + 24, GuiStyle.TEXT_HINT, false);
                }
                renderCatalog(g, list, mouseX, mouseY);
            }
        }
        if (scaled) g.pose().popPose();
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

    // ---- FISH: grid ----

    private void renderFishGrid(GuiGraphics g, int mouseX, int mouseY) {
        int discovered = 0;
        for (String sp : SPECIES) if (data.contains(key(sp))) discovered++;
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
        int bx = left + 10, by = top + 33, bw = W - 20;
        g.fill(bx - 1, by - 1, bx + bw + 1, by + 4, 0xFF2A1E12);
        g.fill(bx, by, bx + bw, by + 3, 0xFF1E1610);
        g.fill(bx, by, bx + (int) (bw * Math.min(1f, frac)), by + 3, 0xFFC89C4A);

        g.drawString(this.font, Component.translatable("journal.riverfishing.total",
                data.getInt("total"), discovered + "/" + SPECIES.length), left + 10, top + 40,
                GuiStyle.TEXT_HINT, false);

        List<Component> tooltip = null;
        for (int i = 0; i < SPECIES.length; i++) {
            String sp = SPECIES[i];
            int x = left + 10 + (i / ROWS) * COL_W;
            int y = top + GRID_TOP + (i % ROWS) * ROW_H;
            boolean disc = data.contains(key(sp));
            boolean hovered = mouseX >= x && mouseX < x + COL_W - 8 && mouseY >= y && mouseY < y + ROW_H - 1;
            if (disc) {
                drawFishIcon(g, sp, x, y);
                String name = this.font.plainSubstrByWidth(
                        Component.translatable("fish.riverfishing." + sp).getString(), COL_W - 24);
                g.drawString(this.font, name, x + 20, y + 4, hovered ? 0xFFB8860B : GuiStyle.TEXT, false);
                if (hovered) {
                    CompoundTag fish = data.getCompound(key(sp));
                    tooltip = new ArrayList<>();
                    tooltip.add(Component.translatable("fish.riverfishing." + sp));
                    tooltip.add(Component.literal("x" + fish.getInt("count") + "  •  " + weight(fish.getInt("best"))));
                }
            } else {
                g.fill(x, y, x + 16, y + 16, 0xFF555555);
                g.drawString(this.font, "???", x + 20, y + 4, GuiStyle.GHOST, false);
            }
        }
        if (tooltip != null) g.renderComponentTooltip(this.font, tooltip, mouseX, mouseY);
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
        int y = contentTop - scroll;
        drawIllustration(g, sp, left + (W - ILLUS_W) / 2, y, ILLUS_W, ILLUS_H);
        y += ILLUS_H + 8;
        String desc = descText(sp);
        if (!desc.isEmpty()) {
            for (net.minecraft.util.FormattedCharSequence seq : this.font.split(Component.literal(desc), W - 20)) {
                g.drawString(this.font, seq, left + 10, y, GuiStyle.TEXT, false);
                y += 11;
            }
            y += 4;
        }
        FishProfile p = FishProfileManager.get().byId(id);
        if (p != null) {
            y = line(g, y, "guide.riverfishing.water", waters(p));
            y = line(g, y, "guide.riverfishing.bait", baits(p));
            y = line(g, y, "guide.riverfishing.tackle", tackle(p));
            y = line(g, y, "guide.riverfishing.best", best(p.season, "season") + "  •  " + best(p.time, "time"));
            if (p.minAnglerLevel > 0) {
                g.drawString(this.font, Component.translatable("jei.riverfishing.level", p.minAnglerLevel),
                        left + 10, y, 0xFFB05A00, false);
                y += 12;
            }
        }
        lastCatH = (y + scroll) - contentTop;
        g.disableScissor();
        renderScrollbar(g, contentTop, contentBottom);

        g.drawString(this.font, Component.translatable("guide.riverfishing.back"),
                left + 10, top + H - 14, GuiStyle.GHOST, false);
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
        List<Component> tooltip = null;
        for (int i = 0; i < list.size(); i++) {
            Cat e = list.get(i);
            if (e.kind() != section) {
                if (col != 0) { y += ROW_H; col = 0; }
                if (i != 0) y += 3;
                section = e.kind();
                g.drawString(this.font, Component.translatable(sectionKey(section)), left + 10, y, 0xFFB0842C, false);
                y += 12;
            }
            int x = left + 10 + col * COL_W;
            catRects[i][0] = x;
            catRects[i][1] = y;
            g.renderItem(e.stack(), x, y);
            String name = this.font.plainSubstrByWidth(e.stack().getHoverName().getString(), COL_W - 24);
            boolean hov = mouseX >= x && mouseX < x + COL_W - 8 && mouseY >= y && mouseY < y + ROW_H - 1
                    && mouseY >= contentTop && mouseY < contentBottom;
            g.drawString(this.font, name, x + 20, y + 4, hov ? 0xFFB8860B : GuiStyle.TEXT, false);
            if (hov) tooltip = catTooltip(e);
            if (++col >= COLS) { col = 0; y += ROW_H; }
        }
        if (col != 0) y += ROW_H;
        lastCatH = (y + scroll) - contentTop;
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

    private void renderCatDetail(GuiGraphics g, Cat e) {
        g.renderItem(e.stack(), left + 10, top + 22);
        g.drawString(this.font, e.stack().getHoverName(), left + 30, top + 26, GuiStyle.TEXT, false);
        g.drawString(this.font, Component.translatable(kindKey(e.kind())), left + 10, top + 44,
                GuiStyle.TEXT_HINT, false);

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
            lastCatH = (dy + scroll) - contentTop;
            g.disableScissor();
            renderScrollbar(g, contentTop, contentBottom);
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

        int y = top + 148;
        y = obtainRender(g, y, e.stack()) + 4;

        if (e.kind() == Kind.ROD || e.kind() == Kind.REEL || e.kind() == Kind.LINE) {
            y = compatLines(g, y, e) + 2;
        }

        if (isBait(e.kind())) {
            boolean gb = e.kind() == Kind.GROUNDBAIT;
            g.drawString(this.font, Component.translatable(gb
                    ? "journal.riverfishing.bait_attracts" : "journal.riverfishing.bait_catches"),
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

    private static boolean isBait(Kind k) {
        return k == Kind.NATURAL || k == Kind.LURE || k == Kind.GROUNDBAIT;
    }

    private List<Component> catTooltip(Cat e) {
        List<Component> t = new ArrayList<>();
        t.add(e.stack().getHoverName());
        t.add(Component.translatable(kindKey(e.kind())).withStyle(ChatFormatting.GRAY));
        if (isBait(e.kind())) {
            List<String> fish = fishFor(e, 6);
            if (!fish.isEmpty()) {
                t.add(Component.translatable(e.kind() == Kind.GROUNDBAIT
                        ? "journal.riverfishing.bait_attracts" : "journal.riverfishing.bait_catches"));
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
        if (e.kind() == Kind.GROUNDBAIT) {
            return FishProfileManager.get().all().stream()
                    .filter(p -> p.idealGroundbaits.contains(e.id()))
                    .limit(limit)
                    .map(p -> Component.translatable("fish.riverfishing." + p.id.getPath()).getString())
                    .collect(Collectors.toList());
        }
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
        // §oilcake-info: the oil cake is a CUSTOM recipe (no listed ingredients) — spell it out by hand.
        ResourceLocation itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (itemId != null && itemId.getPath().equals("groundbait_cake")) {
            return List.of(
                    new ItemStack(net.minecraft.world.item.Items.SUNFLOWER).getHoverName().getString(),
                    new ItemStack(net.minecraft.world.item.Items.PISTON).getHoverName().getString());
        }
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

    private static String waters(FishProfile p) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Double> e : p.waterBodies.entrySet()) {
            if (e.getValue() > 0) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(Component.translatable("water.riverfishing." + e.getKey()).getString());
            }
        }
        return sb.toString();
    }

    private static String best(Map<String, Double> table, String prefix) {
        String bestKey = "";
        double bestV = -1;
        for (Map.Entry<String, Double> e : table.entrySet()) {
            if (e.getValue() > bestV) { bestV = e.getValue(); bestKey = e.getKey(); }
        }
        return bestKey.isEmpty() ? "—" : Component.translatable(prefix + ".riverfishing." + bestKey).getString();
    }

    private static String baits(FishProfile p) {
        return p.baitScores.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .limit(3)
                .map(e -> Component.translatable("item.riverfishing." + e.getKey()).getString())
                .reduce((a, b) -> a + ", " + b).orElse("—");
    }

    private static String tackle(FishProfile p) {
        StringBuilder sb = new StringBuilder();
        for (String rod : p.idealRods) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(Component.translatable("item.riverfishing." + rod + "_rod").getString());
        }
        for (String rig : p.idealRigs) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(Component.translatable("item.riverfishing.rig_" + rig).getString());
        }
        return sb.toString();
    }

    // ---- input ----

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        boolean scrollView = (tab != TAB_FISH && catDetail < 0) || (tab == TAB_FISH && detail != null);
        if (scrollView) {
            scroll = Mth.clamp(scroll - (int) (scrollY * 18), 0, Math.max(0, lastCatH - (H - 44)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
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
                for (int i = 0; i < SPECIES.length; i++) {
                    int x = left + 10 + (i / ROWS) * COL_W;
                    int y = top + GRID_TOP + (i % ROWS) * ROW_H;
                    if (mouseX >= x && mouseX < x + COL_W - 8 && mouseY >= y && mouseY < y + ROW_H - 1
                            && data.contains(key(SPECIES[i]))) {
                        detail = SPECIES[i];
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
                if (catDetail >= 0) { catDetail = -1; scroll = 0; return true; }
                List<Cat> list = tab == TAB_BAIT ? baitCat : tab == TAB_GUIDE ? guideCat : gearCat;
                int contentTop = top + 38, contentBottom = top + H - 6;
                for (int i = 0; i < list.size(); i++) {
                    int x = catRects[i][0], y = catRects[i][1];
                    if (mouseX >= x && mouseX < x + COL_W - 8 && mouseY >= y && mouseY < y + ROW_H - 1
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
