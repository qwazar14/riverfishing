package com.riverfishing.item;

import com.riverfishing.component.ComponentSlot;
import com.riverfishing.component.LineType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/** Fishing line (§3.3): a material type plus a diameter in millimetres. */
public class LineItem extends Item implements RodComponentItem {
    private final LineType type;
    private final double diameterMm;

    public LineItem(LineType type, double diameterMm, Properties properties) {
        super(properties);
        this.type = type;
        this.diameterMm = diameterMm;
    }

    public LineType lineType() { return type; }
    public double diameterMm() { return diameterMm; }
    public double breakingStrainKg() { return type.breakingStrainKg(diameterMm); }

    @Override
    public ComponentSlot componentSlot() {
        return ComponentSlot.LINE;
    }

    @Override
    public void appendHoverText(ItemStack stack, net.minecraft.world.level.Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.riverfishing.line_spec",
                Component.translatable("linetype.riverfishing." + type.jsonKey()),
                String.format("%.2f", diameterMm)).withStyle(s -> s.withColor(0xA0A0A0)));
        tooltip.add(Component.translatable("tooltip.riverfishing.line_strain",
                String.format("%.1f", breakingStrainKg())).withStyle(s -> s.withColor(0xA0A0A0)));
        int wear = WearData.get(stack);
        if (wear > 0) {
            tooltip.add(Component.translatable("tooltip.riverfishing.wear", wear)
                    .withStyle(s -> s.withColor(wear >= 70 ? 0xD05050 : 0xC0A060)));
        }
        if (wear >= 100) {
            tooltip.add(Component.translatable("tooltip.riverfishing.line_worn_out")
                    .withStyle(net.minecraft.ChatFormatting.RED, net.minecraft.ChatFormatting.BOLD));
        }
    }
}
