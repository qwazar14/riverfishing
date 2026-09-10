package com.riverfishing.item;

import com.riverfishing.RiverFishing;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * §cooking: a caught fish goes into a furnace or a smoker and comes out the same fish, cooked — same
 * species, weight, length, card and size on the ground; only the icon and what it does for you change.
 * What it does is the table {@code data/riverfishing/cooked_effects.tsv}: a prime fish gives all of
 * its effects, good and bad; an ordinary one only feeds you, plus whatever harm the table lists.
 * A cooked fish is food and nothing else — not released, not kept, not put in an aquarium, not filleted.
 */
public final class CookedFish {
    private CookedFish() {}

    public static final String TAG_COOKED = "Cooked";

    /** species path -> {effect id, amplifier, seconds}... */
    private static Map<String, List<String[]>> table;

    public static boolean isCooked(ItemStack stack) {
        return stack.getItem() instanceof FishItem && StackNbt.get(stack).getBoolean(TAG_COOKED);
    }

    /** The cooked copy of a raw fish: the same stack, marked, with its food written on it. */
    public static ItemStack cook(ItemStack raw) {
        ItemStack out = raw.copyWithCount(1);
        StackNbt.mutate(out, t -> t.putBoolean(TAG_COOKED, true));
        out.set(DataComponents.FOOD, food(out));
        return out;
    }

    /** What this cooked fish does when eaten. */
    public static FoodProperties food(ItemStack fish) {
        double kg = Math.max(0.05, FishItem.getWeightG(fish) / 1000.0);
        // a gudgeon is a mouthful, a kilo is a meal, ten kilos is as much as a stomach holds
        int nutrition = (int) Math.max(2, Math.min(20, Math.round(2 + 6 * Math.sqrt(kg))));
        boolean prime = FishItem.isPrime(fish);
        FoodProperties.Builder b = new FoodProperties.Builder().nutrition(nutrition).saturationModifier(prime ? 0.6f : 0.35f);
        ResourceLocation sp = FishItem.getSpecies(fish);
        if (sp != null) {
            for (String[] e : effects(sp.getPath())) {
                ResourceLocation id = e[0].contains(":") ? ResourceLocation.parse(e[0]) : ResourceLocation.withDefaultNamespace(e[0]);
                Holder<MobEffect> effect = BuiltInRegistries.MOB_EFFECT.getHolder(id).orElse(null);
                if (effect == null) {
                    RiverFishing.LOGGER.warn("cooked_effects: unknown effect {} on {}", e[0], sp);
                    continue;
                }
                boolean harmful = effect.value().getCategory() == MobEffectCategory.HARMFUL;
                if (!prime && !harmful) continue;   // an ordinary fish only feeds you — and still bites back
                int amp = Integer.parseInt(e[1]), ticks = Integer.parseInt(e[2]) * 20;
                b.effect(new MobEffectInstance(effect, ticks, amp), 1.0f);
            }
        }
        return b.build();
    }

    public static List<String[]> effects(String species) {
        if (table == null) load();
        return table.getOrDefault(species, List.of());
    }

    private static synchronized void load() {
        Map<String, List<String[]>> t = new HashMap<>();
        try (var in = CookedFish.class.getResourceAsStream("/data/riverfishing/cooked_effects.tsv")) {
            if (in != null) {
                BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                String line;
                while ((line = r.readLine()) != null) {
                    String[] cols = line.split("\t");
                    if (cols.length < 2 || cols[0].equals("id") || cols[0].isBlank()) continue;
                    List<String[]> list = new ArrayList<>();
                    for (String part : cols[1].split(";")) {
                        String[] f = part.trim().split(":");
                        // "ns:name:amp:sec" or "name:amp:sec"
                        if (f.length == 4) list.add(new String[] {f[0] + ":" + f[1], f[2], f[3]});
                        else if (f.length == 3) list.add(new String[] {f[0], f[1], f[2]});
                    }
                    t.put(cols[0].trim(), list);
                }
            }
        } catch (Exception e) {
            RiverFishing.LOGGER.warn("cooked_effects.tsv could not be read", e);
        }
        table = t;
    }
}
