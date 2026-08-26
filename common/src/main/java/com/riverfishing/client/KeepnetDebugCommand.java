package com.riverfishing.client;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.context.CommandContext;
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

    public static <S> void register(CommandDispatcher<S> dispatcher) {
        // The tree is typed to Object because the source is never touched: no getSource(), no
        // suggestions off it, nothing. Under erasure the loader's real source objects pass through
        // unchanged, so this cast cannot be wrong at runtime — it only tells javac to stop asking
        // for <S> witnesses at every receiver-position lit()/arg(), where inference cannot reach.
        @SuppressWarnings("unchecked")
        CommandDispatcher<Object> d = (CommandDispatcher<Object>) dispatcher;
        d.register(lit("rfnet")
                // bare root RUNS: "/rfrod" alone printing "Unknown or incomplete command" is
                // indistinguishable from the command not existing, and was reported as exactly that
                .executes(KeepnetDebugCommand::show)
                .then(lit("show").executes(KeepnetDebugCommand::show))
                .then(lit("reset").executes(c -> {
                    KeepnetScreen.iconScale = 1.0f;
                    say(c, "§ereset");
                    return show(c);
                }))
                .then(set("scale"))
                .then(lit("add").then(add("scale"))));
    }

    private static LiteralArgumentBuilder<Object> set(String field) {
        return lit(field)
                .then(arg("value", FloatArgumentType.floatArg(0.05f, 20f))
                        .executes(c -> apply(c, field, FloatArgumentType.getFloat(c, "value"), false)));
    }

    private static LiteralArgumentBuilder<Object> add(String field) {
        return lit(field)
                .then(arg("delta", FloatArgumentType.floatArg(-20f, 20f))
                        .executes(c -> apply(c, field, FloatArgumentType.getFloat(c, "delta"), true)));
    }

    private static int apply(CommandContext<Object> c, String field, float v, boolean delta) {
        if ("scale".equals(field)) {
            KeepnetScreen.iconScale = clamp(delta ? KeepnetScreen.iconScale + v : v);
        }
        return show(c);
    }

    private static float clamp(float v) {
        return Math.max(0.05f, Math.min(40f, v));
    }

    private static int show(CommandContext<Object> c) {
        say(c, String.format("§bkeepnet icon scale: %.2f", KeepnetScreen.iconScale));
        say(c, "§71.0 means the fish exactly fills the cells it occupies");
        return 1;
    }

    /** The context is unused: the source only ever existed to reach chat, and chat is right here. */
    private static void say(Object ignoredCtx, String text) {
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player != null) mc.player.sendSystemMessage(Component.literal(text));
    }

    private static LiteralArgumentBuilder<Object> lit(String name) {
        return LiteralArgumentBuilder.literal(name);
    }

    private static <T> RequiredArgumentBuilder<Object, T> arg(String name, ArgumentType<T> type) {
        return RequiredArgumentBuilder.argument(name, type);
    }
}
