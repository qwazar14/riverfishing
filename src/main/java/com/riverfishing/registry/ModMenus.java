package com.riverfishing.registry;

import com.riverfishing.RiverFishing;
import com.riverfishing.menu.RigMenu;
import com.riverfishing.menu.RodAssemblyMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> REGISTER =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, RiverFishing.MODID);

    public static final RegistryObject<MenuType<RodAssemblyMenu>> ROD_ASSEMBLY =
            REGISTER.register("rod_assembly",
                    () -> IForgeMenuType.create(RodAssemblyMenu::fromNetwork));

    public static final RegistryObject<MenuType<RigMenu>> RIG =
            REGISTER.register("rig",
                    () -> IForgeMenuType.create(RigMenu::fromNetwork));

    private ModMenus() {}
}
