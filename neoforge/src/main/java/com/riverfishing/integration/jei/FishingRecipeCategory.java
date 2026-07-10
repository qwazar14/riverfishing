package com.riverfishing.integration.jei;

import com.riverfishing.RiverFishing;
import com.riverfishing.fish.FishProfile;
import com.riverfishing.fish.FishProfileManager;
import com.riverfishing.registry.ModItems;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Map;

/**
 * JEI "Fishing" category (§ pack integration): for every species, shows the whole recipe to catch it —
 * ideal bait, tackle (rod + rig), the best season/time, which water body, and any angler-level gate.
 * The hints are read live from the loaded {@link FishProfile}s, so JEI can never drift from the engine.
 */
public class FishingRecipeCategory implements IRecipeCategory<ResourceLocation> {
    public static final RecipeType<ResourceLocation> TYPE =
            RecipeType.create(RiverFishing.MODID, "fishing", ResourceLocation.class);

    private static final int W = 162;
    private static final int H = 104;
    private static final int LABEL = 0xFF404040;
    private static final int VALUE = 0xFF202020;
    private static final int GATE = 0xFFB05A00;

    private final IDrawable background;
    private final IDrawable icon;
    private final IDrawable slot;

    public FishingRecipeCategory(IGuiHelper guiHelper) {
        this.background = guiHelper.createBlankDrawable(W, H);
        this.slot = guiHelper.getSlotDrawable();
        Item journal = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(RiverFishing.id("fishing_journal"));
        this.icon = guiHelper.createDrawableItemStack(new ItemStack(journal != null ? journal : ModItems.fishItem(RiverFishing.id("perch"))));
    }

    @Override
    public RecipeType<ResourceLocation> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("jei.riverfishing.category");
    }

    @Override
    public IDrawable getBackground() {
        return background;
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, ResourceLocation species, IFocusGroup focuses) {
        builder.addSlot(RecipeIngredientRole.OUTPUT, 72, 2)
                .setBackground(slot, -1, -1)
                .addItemStack(new ItemStack(ModItems.fishItem(species)));
    }

    @Override
    public void draw(ResourceLocation species, IRecipeSlotsView slots, GuiGraphics g, double mouseX, double mouseY) {
        var font = Minecraft.getInstance().font;
        FishProfile p = FishProfileManager.get().byId(species);

        // Species name centred under the fish.
        Component name = Component.translatable("fish.riverfishing." + species.getPath());
        g.drawString(font, name, (W - font.width(name)) / 2, 24, VALUE, false);

        if (p == null) {
            Component none = Component.translatable("jei.riverfishing.no_data");
            g.drawString(font, none, 4, 40, LABEL, false);
            return;
        }

        int y = 38;
        y = line(g, font, y, "jei.riverfishing.bait", topBaits(p.baitScores, 3));
        y = line(g, font, y, "jei.riverfishing.tackle", rods(p) + "  ·  " + rigs(p));
        if (!p.idealGroundbaits.isEmpty()) {
            y = line(g, font, y, "jei.riverfishing.groundbait", groundbaits(p));
        }
        y = line(g, font, y, "jei.riverfishing.best",
                tr("season.riverfishing." + best(p.season)) + "  ·  " + tr("time.riverfishing." + best(p.time)));
        y = line(g, font, y, "jei.riverfishing.water", waters(p));
        if (p.minAnglerLevel > 0) {
            Component gate = Component.translatable("jei.riverfishing.level", p.minAnglerLevel);
            g.drawString(font, gate, 4, y, GATE, false);
        }
    }

    private static int line(GuiGraphics g, net.minecraft.client.gui.Font font, int y, String labelKey, String value) {
        Component label = Component.translatable(labelKey);
        g.drawString(font, label, 4, y, LABEL, false);
        int vx = 4 + font.width(label) + 4;
        // Trim to the card width — translated names are long, an overflow draws over the neighbour card.
        String v = font.plainSubstrByWidth(value.isEmpty() ? "—" : value, W - vx - 2);
        g.drawString(font, v, vx, y, VALUE, false);
        return y + 11;
    }

    // ---- profile → display helpers (§jei-i18n: everything shown TRANSLATED, never raw json keys) ----

    private static String tr(String key) {
        return Component.translatable(key).getString();
    }

    private static String rods(FishProfile p) {
        return p.idealRods.isEmpty() ? "—" : p.idealRods.stream()
                .map(r -> tr("item.riverfishing." + r + "_rod")).reduce((a, b) -> a + ", " + b).orElse("—");
    }

    private static String rigs(FishProfile p) {
        return p.idealRigs.isEmpty() ? "—" : p.idealRigs.stream()
                .map(r -> tr("item.riverfishing.rig_" + r)).reduce((a, b) -> a + ", " + b).orElse("—");
    }

    private static String groundbaits(FishProfile p) {
        return p.idealGroundbaits.stream()
                .map(gb -> tr("item.riverfishing.groundbait_" + gb)).reduce((a, b) -> a + ", " + b).orElse("—");
    }

    /** The strongest baits of the score map, as translated item names. */
    private static String topBaits(Map<String, Double> scores, int limit) {
        double max = 0;
        for (double v : scores.values()) max = Math.max(max, v);
        if (max <= 0) return "—";
        double cut = max * 0.75;
        return scores.entrySet().stream()
                .filter(e -> e.getValue() >= cut)
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .limit(limit)
                .map(e -> tr("item.riverfishing." + e.getKey()))
                .reduce((a, b) -> a + ", " + b)
                .orElse("—");
    }

    /** The single best-factor key of an environment table (season / time). */
    private static String best(Map<String, Double> table) {
        String bestKey = "";
        double bestVal = -1;
        for (Map.Entry<String, Double> e : table.entrySet()) {
            if (e.getValue() > bestVal) { bestVal = e.getValue(); bestKey = e.getKey(); }
        }
        return bestKey.isEmpty() ? "day" : bestKey;
    }

    /** Which water bodies the fish actually lives in (factor > 0), translated. */
    private static String waters(FishProfile p) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Double> e : p.waterBodies.entrySet()) {
            if (e.getValue() > 0) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(tr("water.riverfishing." + e.getKey()));
            }
        }
        return sb.length() == 0 ? "—" : sb.toString();
    }

    /** All species that have a fish item (the client-static recipe list). */
    public static List<ResourceLocation> recipes() {
        return java.util.Arrays.stream(ModItems.FISH_SPECIES).map(RiverFishing::id).toList();
    }
}
