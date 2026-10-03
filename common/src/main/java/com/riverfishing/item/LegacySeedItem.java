package com.riverfishing.item;

import com.riverfishing.RiverFishing;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * §no-seeds: the old corn / pea / barley seeds. They stay registered so a save that holds them still loads, are
 * nowhere to be had any more (no tab, no recipe, no drop, no trade), and turn into the crop item they stood for —
 * corn, peas, pearl barley — the moment they sit in a player's inventory.
 */
public class LegacySeedItem extends Item {
    private final String target;

    public LegacySeedItem(String target, Properties properties) {
        super(properties);
        this.target = target;
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (level.isClientSide() || !(entity instanceof Player player)) return;
        Item to = BuiltInRegistries.ITEM.get(RiverFishing.id(target));
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i) == stack) {
                inv.setItem(i, new ItemStack(to, stack.getCount()));
                return;
            }
        }
    }
}
