package com.riverfishing.client;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import dev.architectury.event.events.client.ClientCommandRegistrationEvent;
import dev.architectury.event.events.client.ClientCommandRegistrationEvent.ClientCommandSourceStack;
import net.minecraft.network.chat.Component;

/**
 * Live rod-transform debugger (§rod-debug). Client-only command {@code /rfrod} that tweaks
 * {@link RodHandTransform} in real time so the in-hand rod pose can be dialled in without rebuilding.
 * §multiloader: Forge's {@code RegisterClientCommandsEvent} → Architectury
 * {@code ClientCommandRegistrationEvent}, wired from {@link ClientInit}.
 * <ul>
 *   <li>{@code /rfrod set <tp|tpl|fp|fpl> <tx|ty|tz|rx|ry|rz|s> <value>} — set a field
 *       (tp/fp = third/first person RIGHT hand, tpl/fpl = LEFT hand)</li>
 *   <li>{@code /rfrod add <ctx> <field> <delta>} — nudge a field (great for live tuning)</li>
 *   <li>{@code /rfrod show} — print current values (paste them into RodHandTransform to keep)</li>
 *   <li>{@code /rfrod reset} — back to the built-in defaults</li>
 * </ul>
 */
public final class RodDebugCommand {
    private RodDebugCommand() {}

    public static void register(CommandDispatcher<ClientCommandSourceStack> dispatcher) {
        dispatcher.register(ClientCommandRegistrationEvent.literal("rfrod")
                .then(ClientCommandRegistrationEvent.literal("show").executes(RodDebugCommand::show))
                .then(ClientCommandRegistrationEvent.literal("reset").executes(c -> {
                    RodHandTransform.reset();
                    say(c, "§ereset to defaults");
                    return show(c);
                }))
                // §rod-bend debug: force a bend bucket on the held rod (-1 = back to live tension);
                // no argument = print the live client-side bend state (run it mid-fight).
                .then(ClientCommandRegistrationEvent.literal("bend")
                        .executes(c -> {
                            var mc = net.minecraft.client.Minecraft.getInstance();
                            var l = mc.player == null ? null
                                    : ClientLineState.lines().get(mc.player.getId());
                            say(c, l == null
                                    ? "§cno line entry for local player"
                                    : String.format("§etension=%.3f smooth=%.3f force=%d",
                                            l.tension, l.smoothTension, RodItemRenderer.FORCE_BEND));
                            return 1;
                        })
                        .then(ClientCommandRegistrationEvent.argument("bucket",
                                com.mojang.brigadier.arguments.IntegerArgumentType.integer(-1, 3))
                                .executes(c -> {
                                    RodItemRenderer.FORCE_BEND =
                                            com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "bucket");
                                    say(c, "§ebend force = " + RodItemRenderer.FORCE_BEND);
                                    return 1;
                                })))
                .then(ClientCommandRegistrationEvent.literal("set")
                        .then(ClientCommandRegistrationEvent.argument("ctx", StringArgumentType.word())
                                .then(ClientCommandRegistrationEvent.argument("field", StringArgumentType.word())
                                        .then(ClientCommandRegistrationEvent.argument("value", FloatArgumentType.floatArg())
                                                .executes(c -> edit(c, false))))))
                .then(ClientCommandRegistrationEvent.literal("add")
                        .then(ClientCommandRegistrationEvent.argument("ctx", StringArgumentType.word())
                                .then(ClientCommandRegistrationEvent.argument("field", StringArgumentType.word())
                                        .then(ClientCommandRegistrationEvent.argument("value", FloatArgumentType.floatArg())
                                                .executes(c -> edit(c, true))))))
                .then(ClientCommandRegistrationEvent.literal("cast")
                        .then(ClientCommandRegistrationEvent.argument("field", StringArgumentType.word())
                                .then(ClientCommandRegistrationEvent.argument("value", FloatArgumentType.floatArg())
                                        .executes(RodDebugCommand::castEdit)))));
    }

    private static int castEdit(CommandContext<ClientCommandSourceStack> c) {
        String field = StringArgumentType.getString(c, "field");
        float value = FloatArgumentType.getFloat(c, "value");
        float result = RodHandTransform.castEdit(field, value, false);
        if (Float.isNaN(result)) {
            say(c, "§cunknown field — use: /rfrod cast load|whip <degrees>");
            return 0;
        }
        say(c, "§acast " + field + " = " + result);
        return 1;
    }

    private static int edit(CommandContext<ClientCommandSourceStack> c, boolean add) {
        String ctx = StringArgumentType.getString(c, "ctx");
        String field = StringArgumentType.getString(c, "field");
        float value = FloatArgumentType.getFloat(c, "value");
        float result = RodHandTransform.edit(ctx, field, value, add);
        if (Float.isNaN(result)) {
            say(c, "§cunknown ctx/field — ctx: tp|tpl|fp|fpl, field: tx ty tz rx ry rz s");
            return 0;
        }
        say(c, "§a" + ctx + " " + field + " = " + result);
        return 1;
    }

    private static int show(CommandContext<ClientCommandSourceStack> c) {
        for (String line : RodHandTransform.showLines()) {
            say(c, line);
        }
        return 1;
    }

    private static void say(CommandContext<ClientCommandSourceStack> c, String text) {
        c.getSource().arch$sendSuccess(() -> Component.literal(text), false);
    }
}
