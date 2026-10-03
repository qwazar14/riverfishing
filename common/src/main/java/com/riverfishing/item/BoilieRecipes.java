package com.riverfishing.item;

import com.riverfishing.fish.Boilie;
import com.riverfishing.fish.Flavour;
import com.riverfishing.registry.ModItems;
import com.riverfishing.registry.ModRecipes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/** §boilies: the three grid recipes — the paste, the flavour bottle and the snowman. */
public final class BoilieRecipes {
    private BoilieRecipes() {}

    /**
     * Boilie paste: wheat (one to four — that is the size, 10/15/20/24 mm), one egg to bind it, up to two
     * flavour bottles (the bottles come back), up to two dried kelp to float it (one a wafter, two a
     * pop-up), and fish meal if you want a nourishing base. Two balls of paste per wheat.
     */
    public static class Paste extends CustomRecipe {
        private static ItemStack roll(CraftingInput in) {
            int wheat = 0, eggs = 0, kelp = 0, meal = 0;
            List<Flavour> flavours = new ArrayList<>();
            for (int i = 0; i < in.size(); i++) {
                ItemStack s = in.getItem(i);
                if (s.isEmpty()) continue;
                if (s.is(Items.WHEAT)) wheat++;
                else if (s.is(Items.EGG)) eggs++;
                else if (s.is(Items.DRIED_KELP)) kelp++;
                else if (s.is(ModItems.FISH_MEAL.get())) meal++;
                else if (s.getItem() instanceof FlavourItem) {
                    Flavour f = FlavourItem.of(s);
                    if (f == null || flavours.contains(f)) return ItemStack.EMPTY;
                    flavours.add(f);
                } else return ItemStack.EMPTY;
            }
            if (wheat < 1 || wheat > 4 || eggs != 1 || kelp > 2 || meal > 1 || flavours.size() > 2) return ItemStack.EMPTY;
            Boilie.Buoyancy by = kelp == 0 ? Boilie.Buoyancy.SINKER : kelp == 1 ? Boilie.Buoyancy.WAFTER : Boilie.Buoyancy.POPUP;
            Boilie b = new Boilie(flavours, by, Boilie.SIZES[wheat - 1], meal > 0, null);
            ItemStack out = new ItemStack(ModItems.BOILIE_PASTE.get(), wheat * 2);
            BoiliePasteItem.write(out, b);
            return out;
        }

        @Override public boolean matches(CraftingInput in, Level level) { return !roll(in).isEmpty(); }
        @Override public ItemStack assemble(CraftingInput in) { return roll(in); }
        @Override public RecipeSerializer<Paste> getSerializer() { return ModRecipes.BOILIE_PASTE.get(); }
    }

    /** A flavour bottle: a glass bottle, sugar, and the ingredient that names the flavour. */
    public static class Bottle extends CustomRecipe {
        private static ItemStack fill(CraftingInput in) {
            int bottles = 0, sugar = 0;
            Flavour flavour = null;
            for (int i = 0; i < in.size(); i++) {
                ItemStack s = in.getItem(i);
                if (s.isEmpty()) continue;
                if (s.is(Items.GLASS_BOTTLE)) bottles++;
                else if (s.is(Items.SUGAR)) sugar++;
                else {
                    Flavour f = Flavour.byIngredient(BuiltInRegistries.ITEM.getKey(s.getItem()).toString());
                    if (f == null || flavour != null) return ItemStack.EMPTY;
                    flavour = f;
                }
            }
            return bottles == 1 && sugar == 1 && flavour != null ? FlavourItem.make(flavour) : ItemStack.EMPTY;
        }

        @Override public boolean matches(CraftingInput in, Level level) { return !fill(in).isEmpty(); }
        @Override public ItemStack assemble(CraftingInput in) { return fill(in); }
        @Override public RecipeSerializer<Bottle> getSerializer() { return ModRecipes.FLAVOUR_BOTTLE.get(); }
    }

    /**
     * The snowman: a boilie that sinks (or a wafter) under one that floats, on one hair — both flavours, the
     * bigger size, the stack of the evening sandwich.
     */
    public static class Snowman extends CustomRecipe {
        private static ItemStack stack(CraftingInput in) {
            Boilie base = null, top = null;
            int n = 0;
            for (int i = 0; i < in.size(); i++) {
                ItemStack s = in.getItem(i);
                if (s.isEmpty()) continue;
                if (!(s.getItem() instanceof BoilieItem) || ++n > 2) return ItemStack.EMPTY;
                Boilie b = BoilieItem.read(s);
                if (b.buoyancy() == Boilie.Buoyancy.POPUP) top = b;
                else if (b.buoyancy() != Boilie.Buoyancy.SNOWMAN) base = b;
            }
            if (n != 2 || base == null || top == null) return ItemStack.EMPTY;
            List<Flavour> fl = new ArrayList<>();
            if (!base.flavours().isEmpty()) fl.add(base.flavours().get(0));
            if (!top.flavours().isEmpty() && !fl.contains(top.flavours().get(0))) fl.add(top.flavours().get(0));
            ItemStack out = new ItemStack(ModItems.BOILIE.get());
            BoilieItem.write(out, new Boilie(fl, Boilie.Buoyancy.SNOWMAN, Math.max(base.sizeMm(), top.sizeMm()),
                    base.meal() || top.meal(), null));
            return out;
        }

        @Override public boolean matches(CraftingInput in, Level level) { return !stack(in).isEmpty(); }
        @Override public ItemStack assemble(CraftingInput in) { return stack(in); }
        @Override public RecipeSerializer<Snowman> getSerializer() { return ModRecipes.SNOWMAN.get(); }
    }
}
