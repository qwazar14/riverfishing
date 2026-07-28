package com.riverfishing.client;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.context.CommandContext;
import dev.architectury.event.events.client.ClientCommandRegistrationEvent;
import dev.architectury.event.events.client.ClientCommandRegistrationEvent.ClientCommandSourceStack;
import net.minecraft.network.chat.Component;

/**
 * §keepnet-tune: live tuning for how big a fish is drawn inside the keepnet grid, the same way
 * {@link RodDebugCommand} dials in the in-hand rod pose — open the box, nudge, look, repeat, and paste
 * the number that looked right back into {@link KeepnetScreen}.
 *
 * <p>Client-only, and every value is a plain multiplier so the printout can be read straight into source.
 *
 * <ul>
 *   <li>{@code /rfnet scale <v>} — overall size of every fish in the grid. 1.0 fills the footprint.</li>
 *   <li>{@code /rfnet canvasw <v>} / {@code canvash <v>} — how much of its square icon a fish is assumed
 *       to fill, across and down. Raising these SHRINKS the fish; the height one is what governs whether
 *       a long fish spills out of a one-cell-tall footprint.</li>
 *   <li>{@code /rfnet add <field> <delta>} — nudge, for finding the number by feel.</li>
 *   <li>{@code /rfnet show} · {@code /rfnet reset}</li>
 * </ul>
 */
public final class KeepnetDebugCommand {
    private KeepnetDebugCommand() {}

    public static void register(CommandDispatcher<ClientCommandSourceStack> dispatcher) {
        dispatcher.register(ClientCommandRegistrationEvent.literal("rfnet")
                .then(ClientCommandRegistrationEvent.literal("show").executes(KeepnetDebugCommand::show))
                .then(ClientCommandRegistrationEvent.literal("reset").executes(c -> {
                    KeepnetScreen.iconScale = 1.0f;
                    KeepnetScreen.canvasW = 16.0f;
                    KeepnetScreen.canvasH = 7.0f;
                    say(c, "§ereset");
                    return show(c);
                }))
                .then(set("scale"))
                .then(set("canvasw"))
                .then(set("canvash"))
                .then(ClientCommandRegistrationEvent.literal("add")
                        .then(add("scale")).then(add("canvasw")).then(add("canvash"))));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<ClientCommandSourceStack> set(String field) {
        return ClientCommandRegistrationEvent.literal(field)
                .then(ClientCommandRegistrationEvent.argument("value", FloatArgumentType.floatArg(0.05f, 20f))
                        .executes(c -> apply(c, field, FloatArgumentType.getFloat(c, "value"), false)));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<ClientCommandSourceStack> add(String field) {
        return ClientCommandRegistrationEvent.literal(field)
                .then(ClientCommandRegistrationEvent.argument("delta", FloatArgumentType.floatArg(-20f, 20f))
                        .executes(c -> apply(c, field, FloatArgumentType.getFloat(c, "delta"), true)));
    }

    private static int apply(CommandContext<ClientCommandSourceStack> c, String field, float v, boolean delta) {
        switch (field) {
            case "scale" -> KeepnetScreen.iconScale = clamp(delta ? KeepnetScreen.iconScale + v : v);
            case "canvasw" -> KeepnetScreen.canvasW = clamp(delta ? KeepnetScreen.canvasW + v : v);
            case "canvash" -> KeepnetScreen.canvasH = clamp(delta ? KeepnetScreen.canvasH + v : v);
            default -> { }
        }
        return show(c);
    }

    private static float clamp(float v) {
        return Math.max(0.05f, Math.min(40f, v));
    }

    private static int show(CommandContext<ClientCommandSourceStack> c) {
        say(c, String.format("§bkeepnet icon:  scale %.2f   canvasW %.1f   canvasH %.1f",
                KeepnetScreen.iconScale, KeepnetScreen.canvasW, KeepnetScreen.canvasH));
        say(c, "§7paste into KeepnetScreen: ICON scale/W/H");
        return 1;
    }

    private static void say(CommandContext<ClientCommandSourceStack> c, String text) {
        c.getSource().arch$sendSuccess(() -> Component.literal(text), false);
    }
}
