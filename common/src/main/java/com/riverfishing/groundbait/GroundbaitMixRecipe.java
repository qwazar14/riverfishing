package com.riverfishing.groundbait;

import com.riverfishing.registry.ModItems;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * §groundbait-mix (0.8.0): stir your own groundbait in the crafting grid.
 *
 * <p><b>One item is one spoon.</b> Put three corn, one breadcrumb and four soil in the grid and you get
 * exactly the mix those spoons make — the composition is what you can see in front of you, with no
 * sliders to read and no numbers to type.
 *
 * <p>The design called for a dedicated bench form with sliders. This is the same feature through the
 * grid instead, and deliberately so: the mod already ships three CustomRecipes ({@link
 * com.riverfishing.item.LivebaitRecipe}, LureDyeRecipe, TackleBoxDyeRecipe), the four ready-made
 * groundbaits are already crafted in a grid, and "count of items" expresses whole spoons more directly
 * than any widget would. A screen nobody could look at while building it would have been the riskiest
 * part of the release for the least gain.
 *
 * <p>Dyes are optional and behave like they do on a lure: they stain the mix without touching what it
 * feeds or what category it reads as.
 */
public class GroundbaitMixRecipe extends CustomRecipe {

    public GroundbaitMixRecipe(CraftingBookCategory category) {
        super(category);
    }

    /** The pantry id for a stack, or null when it is not something you can put in a mix. */
    private static String pantryId(ItemStack stack) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        String modKey = "riverfishing".equals(id.getNamespace()) ? id.getPath() : id.toString();
        return GroundbaitMix.PANTRY.containsKey(modKey) ? modKey : null;
    }

    /**
     * Read the grid into spoons.
     *
     * @return null when this is not a groundbait mix at all — anything unrecognised in the grid, or a
     *     recipe that could not stir (only ballast, or a single component that a plain vanilla recipe
     *     already handles better).
     */
    private static Stir stir(CraftingInput input) {
        Map<String, Integer> spoons = new LinkedHashMap<>();
        List<DyeItem> dyes = new ArrayList<>();
        int filled = 0;
        int used = 0;      // occupied non-dye slots: what the craft costs, and what it pays

        for (int i = 0; i < input.size(); i++) {
            ItemStack s = input.getItem(i);
            if (s.isEmpty()) continue;
            filled++;
            if (s.getItem() instanceof DyeItem d) {
                dyes.add(d);
                continue;
            }
            String id = pantryId(s);
            if (id == null) return null;              // anything else disqualifies the whole grid
            // ONE SLOT IS ONE SPOON. A crafting grid consumes one item per occupied slot however
            // big the stack in it is, so reading spoons off getCount() paid out for groundbait
            // nobody spent — two stacks of 64 read as 128 spoons and cost 2 items.
            spoons.merge(id, 1, Integer::sum);
            used++;
        }

        if (spoons.isEmpty() || filled < 2) return null;

        List<GroundbaitMix.Part> parts = new ArrayList<>();
        for (Map.Entry<String, Integer> e : spoons.entrySet()) {
            parts.add(new GroundbaitMix.Part(e.getKey(), e.getValue()));
        }
        // Do not steal the four basic recipes: bread + wheat is powder, wheat + seeds is grain, and
        // both are pure pantry, so "two components" cannot be the test. See qualifiesAsMix.
        if (!GroundbaitMix.qualifiesAsMix(parts, !dyes.isEmpty())) return null;
        GroundbaitMix mix = GroundbaitMix.of(parts);
        if (mix == null) return null;                  // all ballast: a bucket of mud is not groundbait
        return new Stir(mix, dyes, used);
    }

    private record Stir(GroundbaitMix mix, List<DyeItem> dyes, int used) {}

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return stir(input) != null;
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        Stir stirred = stir(input);
        if (stirred == null) return ItemStack.EMPTY;

        GroundbaitMix mix = stirred.mix();
        for (DyeItem d : stirred.dyes()) {
            // getFireworkColor is the one dye-RGB accessor that exists unchanged on every version this
            // mod ships to; getTextureDiffuseColor does not exist on 1.20.1.
            mix = mix.dyed(d.getDyeColor().getFireworkColor() & 0xFFFFFF);
        }

        // The jar it comes out in is the one matching what it reads as, so a mix that fishes as grain
        // also LOOKS like grain on the hotbar. The stamped composition is what actually counts.
        ItemStack out = new ItemStack(jarFor(mix.category()), stirred.used());
        GroundbaitNbt.write(out, mix);
        return out;
    }

    private static net.minecraft.world.item.Item jarFor(String category) {
        var supplier = ModItems.GROUNDBAITS.get(category);
        return supplier != null ? supplier.get() : ModItems.GROUNDBAITS.get("powder").get();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return com.riverfishing.registry.ModRecipes.GROUNDBAIT_MIX.get();
    }
}
