package com.riverfishing.client;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.riverfishing.item.RodData;
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
                // §fight-course: how hard a running fish drags the tip over. Look at it mid-run and
                // dial it until the direction reads without the boss bar.
                .then(ClientCommandRegistrationEvent.literal("coursetip")
                        .then(ClientCommandRegistrationEvent.argument("x", FloatArgumentType.floatArg(-0.2f, 0.2f))
                                .then(ClientCommandRegistrationEvent.argument("y", FloatArgumentType.floatArg(-0.2f, 0.2f))
                                        .executes(c -> {
                                            RodHandTransform.COURSE_TIP_X = FloatArgumentType.getFloat(c, "x");
                                            RodHandTransform.COURSE_TIP_Y = FloatArgumentType.getFloat(c, "y");
                                            say(c, String.format("§bcourse tip travel: x %.4f  y %.4f",
                                                    RodHandTransform.COURSE_TIP_X, RodHandTransform.COURSE_TIP_Y));
                                            return 1;
                                        }))))
                .then(ClientCommandRegistrationEvent.literal("course")
                        .then(ClientCommandRegistrationEvent.argument("yaw", FloatArgumentType.floatArg(0f, 60f))
                                .then(ClientCommandRegistrationEvent.argument("pitch", FloatArgumentType.floatArg(0f, 60f))
                                        .executes(c -> {
                                            RodHandTransform.COURSE_YAW = FloatArgumentType.getFloat(c, "yaw");
                                            RodHandTransform.COURSE_PITCH = FloatArgumentType.getFloat(c, "pitch");
                                            say(c, String.format("§bcourse lean: yaw %.0f  pitch %.0f",
                                                    RodHandTransform.COURSE_YAW, RodHandTransform.COURSE_PITCH));
                                            return 1;
                                        }))))
                .then(ClientCommandRegistrationEvent.literal("reset").executes(c -> {
                    RodHandTransform.reset();
                    say(c, "§ereset to defaults");
                    return show(c);
                }))
                // §rod-bend debug: force a bend bucket on the held rod (-1 = back to the server's live
                // value); no argument = print the live bend state (run it mid-fight).
                .then(ClientCommandRegistrationEvent.literal("bend")
                        .executes(c -> {
                            var mc = net.minecraft.client.Minecraft.getInstance();
                            var l = mc.player == null ? null
                                    : ClientLineState.lines().get(mc.player.getId());
                            say(c, String.format("§erod bend=%d force=%d",
                                    mc.player == null ? -1 : LineRenderer.heldBend(mc.player),
                                    LineRenderer.FORCE_BEND));
                            say(c, l == null
                                    ? "§cno line entry for local player"
                                    : String.format("§etension=%.3f smooth=%.3f", l.tension, l.smoothTension));
                            return 1;
                        })
                        .then(ClientCommandRegistrationEvent.argument("bucket",
                                com.mojang.brigadier.arguments.IntegerArgumentType.integer(-1, RodData.BEND_BUCKETS))
                                .executes(RodDebugCommand::forceBend)))
                // §rod-bend-tip: tune the line-anchor offset per bend bucket. Force the bucket with
                // /rfrod bend N first, then nudge dx/dy until the line sits on the bent tip.
                .then(ClientCommandRegistrationEvent.literal("tip")
                        .executes(c -> {
                            StringBuilder sb = new StringBuilder("§etip offsets:");
                            for (int b = 1; b <= RodData.BEND_BUCKETS; b++) {
                                sb.append(String.format(" %d:(%.3f,%.3f)", b,
                                        LineRenderer.TIP_BEND_OFFSET[b][0], LineRenderer.TIP_BEND_OFFSET[b][1]));
                            }
                            say(c, sb.toString());
                            return 1;
                        })
                        .then(ClientCommandRegistrationEvent.argument("bucket",
                                com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, RodData.BEND_BUCKETS))
                                .then(ClientCommandRegistrationEvent.argument("dx", FloatArgumentType.floatArg(-1f, 1f))
                                        .then(ClientCommandRegistrationEvent.argument("dy", FloatArgumentType.floatArg(-1f, 1f))
                                                .executes(c -> {
                                                    int b = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "bucket");
                                                    LineRenderer.TIP_BEND_OFFSET[b][0] = FloatArgumentType.getFloat(c, "dx");
                                                    LineRenderer.TIP_BEND_OFFSET[b][1] = FloatArgumentType.getFloat(c, "dy");
                                                    say(c, String.format("§etip[%d] = (%.3f, %.3f)", b,
                                                            LineRenderer.TIP_BEND_OFFSET[b][0], LineRenderer.TIP_BEND_OFFSET[b][1]));
                                                    return 1;
                                                })))))
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

    /**
     * §rod-bend debug: on 26.x the bend is a component on the STACK, not a client render flag — so
     * forcing a bucket means writing it onto the client's own copy of the held rod. That drives the
     * real item-definition dispatch, which is exactly the split we want: bucket shows = the model
     * path is fine and any missing bend is the server write; doesn't show = model/sprite problem.
     * The server overwrites it on the next inventory sync, and -1 clears it.
     */
    private static int forceBend(CommandContext<ClientCommandSourceStack> c) {
        int bucket = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "bucket");
        LineRenderer.FORCE_BEND = bucket;
        var player = net.minecraft.client.Minecraft.getInstance().player;
        if (player != null) {
            for (var stack : new net.minecraft.world.item.ItemStack[]{
                    player.getMainHandItem(), player.getOffhandItem()}) {
                if (stack.getItem() instanceof com.riverfishing.item.RodItem) {
                    RodData.setBend(stack, Math.max(0, bucket));
                    break;
                }
            }
        }
        say(c, "§ebend force = " + bucket + (bucket < 0 ? " §7(live)" : ""));
        return 1;
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
