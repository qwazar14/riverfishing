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

    public static void init() {
        REGISTER.register();
    }

    private ModMenus() {}
}
