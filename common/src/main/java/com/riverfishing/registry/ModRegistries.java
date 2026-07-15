package com.riverfishing.registry;

/**
 * Central registration bootstrap (§multiloader). Binds every Architectury {@code DeferredRegister} to the
 * active platform's registry, in dependency order: BLOCKS before ITEMS so the block-items' suppliers can
 * resolve {@code block.get()}, and the creative tab last so it iterates a fully-bound {@link ModItems#ALL}.
 *
 * <p>Called once from {@link com.riverfishing.RiverFishing#init()}. Config, networking and the villager
 * trades/POI-state wiring are NOT here yet — they de-Forge in Stages 3–4.
 */
public final class ModRegistries {
    private ModRegistries() {}

    public static void init() {
        ModBlocks.init();        // also queues block-items into ModItems.REGISTER (class-load side effect)
        ModItems.init();
        ModBlockEntities.init();
        ModMenus.init();
        ModSounds.init();
        ModRecipes.init();
        ModVillagers.init();
        ModCreativeTabs.init();
    }
}
