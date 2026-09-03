package com.riverfishing.registry;

import com.riverfishing.RiverFishing;
import com.riverfishing.menu.RigMenu;
import com.riverfishing.menu.RodAssemblyMenu;
import dev.architectury.registry.menu.MenuRegistry;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;

public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> REGISTER =
            DeferredRegister.create(RiverFishing.MODID, Registries.MENU);

    // §multiloader: IForgeMenuType.create → Architectury MenuRegistry.ofExtended (Fabric maps this to an
    // ExtendedScreenHandlerType, Forge to IForgeMenuType) so the extra FriendlyByteBuf on open works on both.
    public static final RegistrySupplier<MenuType<RodAssemblyMenu>> ROD_ASSEMBLY =
            REGISTER.register("rod_assembly",
                    () -> MenuRegistry.ofExtended(RodAssemblyMenu::fromNetwork));

    public static final RegistrySupplier<MenuType<RigMenu>> RIG =
            REGISTER.register("rig",
                    () -> MenuRegistry.ofExtended(RigMenu::fromNetwork));

    // §tackle-station (0.6.0): the universal tackle bench.
    public static final RegistrySupplier<MenuType<com.riverfishing.menu.TackleStationMenu>> TACKLE_STATION =
            REGISTER.register("tackle_station",
                    () -> MenuRegistry.ofExtended(com.riverfishing.menu.TackleStationMenu::fromNetwork));

    // §keepnet (0.7.0): the spatial catch box.
    public static final RegistrySupplier<MenuType<com.riverfishing.menu.KeepnetMenu>> KEEPNET =
            REGISTER.register("keepnet",
                    () -> MenuRegistry.ofExtended(com.riverfishing.menu.KeepnetMenu::fromNetwork));

    // §tackle-box (0.7.0): the box's slots, opened from a hand or from a placed box.
    public static final RegistrySupplier<MenuType<com.riverfishing.menu.TackleBoxMenu>> TACKLE_BOX =
            REGISTER.register("tackle_box",
                    () -> MenuRegistry.ofExtended(com.riverfishing.menu.TackleBoxMenu::fromNetwork));

    // §aquarium-window (0.9.0): the tank's slots and its ten status ints.
    public static final RegistrySupplier<MenuType<com.riverfishing.menu.AquariumMenu>> AQUARIUM =
            REGISTER.register("aquarium",
                    () -> MenuRegistry.ofExtended(com.riverfishing.menu.AquariumMenu::fromNetwork));

    public static void init() {
        REGISTER.register();
    }

    private ModMenus() {}
}
