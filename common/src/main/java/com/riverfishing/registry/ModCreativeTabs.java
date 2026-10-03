package com.riverfishing.registry;

import com.riverfishing.RiverFishing;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;

/** Two creative tabs: the tackle and everything else in registration order, and the fish on their own. */
public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> REGISTER =
            DeferredRegister.create(RiverFishing.MODID, Registries.CREATIVE_MODE_TAB);

    public static void init() {
        REGISTER.register();
    }

    /** Rigs built into a rod (§closed-slots) — never handed out as items. Bottom rigs are NOT here. */
    private static final java.util.Set<String> INTERNAL_RIGS = java.util.Set.of(
            "rig_primitive", "rig_float_light", "rig_float", "rig_winter", "rig_predator");

    public static final RegistrySupplier<CreativeModeTab> MAIN = REGISTER.register("main", () ->
            // §multiloader: vanilla CreativeModeTab.builder() needs a (Row, column) and Forge's no-arg
            // overload isn't in common — Architectury's CreativeTabRegistry.create wraps it per platform.
            dev.architectury.registry.CreativeTabRegistry.create(builder -> builder
                    .title(Component.translatable("itemGroup.riverfishing"))
                    .icon(() -> new ItemStack(ModItems.RODS.get(0).get()))
                    .displayItems((params, output) -> {
                        for (RegistrySupplier<net.minecraft.world.item.Item> obj : ModItems.ALL) {
                            if (obj.get() instanceof com.riverfishing.item.FishItem) continue;   // their own tab
                            // Float and lure rods carry their rig internally now (§closed-slots) — hide those
                            // rigs. Bottom rigs stay listed (bottom rods still take a swappable rig).
                            if (INTERNAL_RIGS.contains(obj.getId().getPath())) continue;
                            // §boilies: every flavour, not one empty bottle
                            if (obj == ModItems.FLAVOUR) {
                                for (com.riverfishing.fish.Flavour f : com.riverfishing.fish.Flavour.values()) {
                                    output.accept(com.riverfishing.item.FlavourItem.make(f));
                                }
                                continue;
                            }
                            output.accept(new ItemStack(obj.get()));
                        }
                    })));

    /** The caught fish, A to Z by id — a species is easier found here than among 267 in registration order. */
    public static final RegistrySupplier<CreativeModeTab> FISH = REGISTER.register("fish", () ->
            dev.architectury.registry.CreativeTabRegistry.create(builder -> builder
                    .title(Component.translatable("itemGroup.riverfishing.fish"))
                    .icon(() -> new ItemStack(ModItems.fishItem(RiverFishing.id("carp"))))
                    .displayItems((params, output) -> ModItems.FISH_ITEMS.entrySet().stream()
                            .sorted(java.util.Map.Entry.comparingByKey())
                            .forEach(e -> output.accept(new ItemStack(e.getValue().get()))))));

    private ModCreativeTabs() {}
}
