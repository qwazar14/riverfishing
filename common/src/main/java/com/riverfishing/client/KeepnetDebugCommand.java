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
 *   <li>{@code /rfnet scale <v>} — overall size of every fish in the grid. At 1.0 a fish exactly fills
 *       the cells it occupies, which is now the right answer for all of them: the proportions of each
 *       sprite are measured rather than assumed (see {@link FishBounds}).</li>
 *   <li>{@code /rfnet add scale <delta>} — nudge, for finding the number by feel.</li>
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
                    say(c, "§ereset");
                    return show(c);
                }))
                .then(set("scale"))
                .then(ClientCommandRegistrationEvent.literal("add").then(add("scale"))));
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
        if ("scale".equals(field)) {
            KeepnetScreen.iconScale = clamp(delta ? KeepnetScreen.iconScale + v : v);
        }
        return show(c);
    }

    private static float clamp(float v) {
        return Math.max(0.05f, Math.min(40f, v));
    }

    private static int show(CommandContext<ClientCommandSourceStack> c) {
        say(c, String.format("§bkeepnet icon scale: %.2f", KeepnetScreen.iconScale));
        say(c, "§71.0 means the fish exactly fills the cells it occupies");
        return 1;
    }

    private static void say(CommandContext<ClientCommandSourceStack> c, String text) {
        c.getSource().arch$sendSuccess(() -> Component.literal(text), false);
    }
}
