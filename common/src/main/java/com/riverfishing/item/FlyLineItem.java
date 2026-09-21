package com.riverfishing.item;

import com.riverfishing.component.LineType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * §fly-lines: a fly line is a LineItem of type FLY with two things a mono spool has not — how it
 * floats and how it is shaped. Buoyancy is what the rope does with it (F rides the surface, I
 * hovers, S goes down); geometry is how it casts (WF shoots, DT lays down soft and mends, SH shoots
 * hardest and shortest). One weight for now: the rod's class carries the line's weight.
 */
public class FlyLineItem extends LineItem {
    public enum Geometry {
        WF("wf", 1.00, 0xEBE1AA),   // weight forward: the all-rounder, shoots well
        DT("dt", 0.75, 0xE8A050),   // double taper: gentle, reversible, mends best
        SH("sh", 1.40, 0x9AA3AA);   // shooting head: the longest shoot, no finesse
        public final String key; public final double shoot; public final int rgb;
        Geometry(String key, double shoot, int rgb) { this.key = key; this.shoot = shoot; this.rgb = rgb; }
    }
    public enum Buoyancy {
        F("f", 0.0), I("i", 0.06), S("s", 0.35);   // metres per second the line goes down — a game second is short
        public final String key; public final double sink;
        Buoyancy(String key, double sink) { this.key = key; this.sink = sink; }
    }

    private final Geometry geometry;
    private final Buoyancy buoyancy;

    public FlyLineItem(Geometry geometry, Buoyancy buoyancy, Properties properties) {
        super(LineType.FLY, 1.00, properties);
        this.geometry = geometry;
        this.buoyancy = buoyancy;
    }

    public Geometry geometry() { return geometry; }
    public Buoyancy buoyancy() { return buoyancy; }

    @Override
    public void appendHoverText(ItemStack stack, net.minecraft.world.item.Item.TooltipContext context, net.minecraft.world.item.component.TooltipDisplay display, java.util.function.Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable("tooltip.riverfishing.fly_line_geometry_" + geometry.key).withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("tooltip.riverfishing.fly_line_buoyancy_" + buoyancy.key).withStyle(ChatFormatting.DARK_AQUA));
        super.appendHoverText(stack, context, display, tooltip, flag);
    }
}
