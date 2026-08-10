package com.riverfishing.groundbait;

import com.riverfishing.registry.ModItems;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
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
 * <p><b>One SLOT is one spoon, and one spoon of FOOD is one jar.</b> Put three corn, one breadcrumb and
 * four soil in the grid and you get exactly the mix those spoons make — the composition is what you can
 * see in front of you, with no sliders to read and no numbers to type.
 *
 * <p>A stack of 64 in one slot is still one spoon, because one item per slot is what the craft actually
 * consumes; reading the stack size instead was a 32x item printer. Ballast pays no jars at all — soil is
 * what you dilute a mix WITH, and counting it as substance turned two dirt into eight jars of groundbait.
 * Both numbers now come off the same list of parts, and a jar this recipe made can never be an ingredient
 * for it. Those two rules together are §groundbait-value; each one alone still leaks.
 *
 * <p>The design called for a dedicated bench form with sliders. This is the same feature through the
 * grid instead, and deliberately so: the mod already ships three CustomRecipes ({@link
 * com.riverfishing.item.LivebaitRecipe}, LureDyeRecipe, TackleBoxDyeRecipe), the four ready-made
 * groundbaits were themselves grid recipes once, and "count of items" expresses whole spoons more directly
 * than any widget would. A screen nobody could look at while building it would have been the riskiest
 * part of the release for the least gain.
 *
 * <p>Dyes are optional and behave like they do on a lure: they stain the mix without touching what it
 * feeds or what category it reads as.
 */
public class GroundbaitMixRecipe extends CustomRecipe {

    /** The pantry id for a stack, or null when it is not something you can put in a mix. */
    private static String pantryId(ItemStack stack) {
        // §groundbait-value: YOUR OWN MIX IS FINISHED GOODS, not an ingredient. This one line is what
        // closes the loop. Everything this recipe makes is stamped, so nothing it makes can come back
        // in — and without it the arithmetic alone is not enough: one free wheat seed beside a shop jar
        // pays two jars out, and both would re-enter at the fat preset numbers of their item id,
        // doubling every craft. The ready-made jars are still a legal base; that is the point of them.
        if (GroundbaitNbt.isStamped(stack)) return null;
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
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
        List<DyeColor> dyes = new ArrayList<>();
        int filled = 0;

        for (int i = 0; i < input.size(); i++) {
            ItemStack s = input.getItem(i);
            if (s.isEmpty()) continue;
            filled++;
            // §26.1: a dye is marked by the DYE component, not by being a DyeItem.
            DyeColor dye = s.get(net.minecraft.core.component.DataComponents.DYE);
            if (dye != null) {
                dyes.add(dye);
                continue;
            }
            String id = pantryId(s);
            if (id == null) return null;              // anything else disqualifies the whole grid
            // ONE SLOT IS ONE SPOON. A crafting grid consumes one item per occupied slot however
            // big the stack in it is, so reading spoons off getCount() paid out for groundbait
            // nobody spent — two stacks of 64 read as 128 spoons and cost 2 items.
            spoons.merge(id, 1, Integer::sum);
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
        return new Stir(mix, dyes);
    }

    private record Stir(GroundbaitMix mix, List<DyeColor> dyes) {}

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return stir(input) != null;
    }

    @Override
    public ItemStack assemble(CraftingInput input) {
        Stir stirred = stir(input);
        if (stirred == null) return ItemStack.EMPTY;

        GroundbaitMix mix = stirred.mix();
        for (DyeColor d : stirred.dyes()) {
            mix = mix.dyed(d.getFireworkColor() & 0xFFFFFF);
        }

        // The jar it comes out in is the one matching what it reads as, so a mix that fishes as grain
        // also LOOKS like grain on the hotbar. The stamped composition is what actually counts.
        // §groundbait-value: HOW MANY comes off the mix itself — one jar per spoon of food, ballast
        // paying nothing — so the count and the composition are read from one list and cannot drift.
        // The clamp is for grids bigger than vanilla's, where 9 components x 9 spoons can pass a stack.
        ItemStack out = new ItemStack(jarFor(mix.category()));
        out.setCount(Math.min(GroundbaitMix.jars(mix), out.getMaxStackSize()));
        GroundbaitNbt.write(out, mix);
        return out;
    }

    private static net.minecraft.world.item.Item jarFor(String category) {
        var supplier = ModItems.GROUNDBAITS.get(category);
        return supplier != null ? supplier.get() : ModItems.GROUNDBAITS.get("powder").get();
    }

    @Override
    public RecipeSerializer<GroundbaitMixRecipe> getSerializer() {
        return com.riverfishing.registry.ModRecipes.GROUNDBAIT_MIX.get();
    }
}
