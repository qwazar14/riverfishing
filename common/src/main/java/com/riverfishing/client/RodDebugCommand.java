package com.riverfishing.client;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.riverfishing.item.RodData;
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

    public static <S> void register(CommandDispatcher<S> dispatcher) {
        // The tree is typed to Object because the source is never touched: no getSource(), no
        // suggestions off it, nothing. Under erasure the loader's real source objects pass through
        // unchanged, so this cast cannot be wrong at runtime — it only tells javac to stop asking
        // for <S> witnesses at every receiver-position lit()/arg(), where inference cannot reach.
        @SuppressWarnings("unchecked")
        CommandDispatcher<Object> d = (CommandDispatcher<Object>) dispatcher;
        d.register(lit("rfrod")
                // bare root RUNS: "/rfrod" alone printing "Unknown or incomplete command" is
                // indistinguishable from the command not existing, and was reported as exactly that
                .executes(RodDebugCommand::show)
                .then(lit("show").executes(RodDebugCommand::show))
                // §fight-course: how hard a running fish drags the tip over. Look at it mid-run and
                // dial it until the direction reads without the boss bar.
                .then(lit("coursetip")
                        .then(arg("x", FloatArgumentType.floatArg(-0.2f, 0.2f))
                                .then(arg("y", FloatArgumentType.floatArg(-0.2f, 0.2f))
                                        .executes(c -> {
                                            RodHandTransform.COURSE_TIP_X = FloatArgumentType.getFloat(c, "x");
                                            RodHandTransform.COURSE_TIP_Y = FloatArgumentType.getFloat(c, "y");
                                            say(c, String.format("§bcourse tip travel: x %.4f  y %.4f",
                                                    RodHandTransform.COURSE_TIP_X, RodHandTransform.COURSE_TIP_Y));
                                            return 1;
                                        }))))
                .then(lit("course")
                        .then(arg("yaw", FloatArgumentType.floatArg(0f, 60f))
                                .then(arg("pitch", FloatArgumentType.floatArg(0f, 60f))
                                        .executes(c -> {
                                            RodHandTransform.COURSE_YAW = FloatArgumentType.getFloat(c, "yaw");
                                            RodHandTransform.COURSE_PITCH = FloatArgumentType.getFloat(c, "pitch");
                                            say(c, String.format("§bcourse lean: yaw %.0f  pitch %.0f",
                                                    RodHandTransform.COURSE_YAW, RodHandTransform.COURSE_PITCH));
                                            return 1;
                                        }))))
                .then(lit("reset").executes(c -> {
                    RodHandTransform.reset();
                    say(c, "§ereset to defaults");
                    return show(c);
                }))
                // §rod-bend debug: force a bend bucket on the held rod (-1 = back to the server's live
                // value); no argument = print the live bend state (run it mid-fight).
                .then(lit("bend")
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
                        .then(arg("bucket",
                                com.mojang.brigadier.arguments.IntegerArgumentType.integer(-1, RodData.BEND_BUCKETS))
                                .executes(RodDebugCommand::forceBend)))
                // §rod-bend-tip: tune the line-anchor offset per bend bucket. Force the bucket with
                // /rfrod bend N first, then nudge dx/dy until the line sits on the bent tip.
                .then(lit("tip")
                        .executes(c -> {
                            StringBuilder sb = new StringBuilder("§etip offsets:");
                            for (int b = 1; b <= RodData.BEND_BUCKETS; b++) {
                                sb.append(String.format(" %d:(%.3f,%.3f)", b,
                                        LineRenderer.TIP_BEND_OFFSET[b][0], LineRenderer.TIP_BEND_OFFSET[b][1]));
                            }
                            say(c, sb.toString());
                            return 1;
                        })
                        .then(arg("bucket",
                                com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, RodData.BEND_BUCKETS))
                                .then(arg("dx", FloatArgumentType.floatArg(-1f, 1f))
                                        .then(arg("dy", FloatArgumentType.floatArg(-1f, 1f))
                                                .executes(c -> {
                                                    int b = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "bucket");
                                                    LineRenderer.TIP_BEND_OFFSET[b][0] = FloatArgumentType.getFloat(c, "dx");
                                                    LineRenderer.TIP_BEND_OFFSET[b][1] = FloatArgumentType.getFloat(c, "dy");
                                                    say(c, String.format("§etip[%d] = (%.3f, %.3f)", b,
                                                            LineRenderer.TIP_BEND_OFFSET[b][0], LineRenderer.TIP_BEND_OFFSET[b][1]));
                                                    return 1;
                                                })))))
                .then(lit("set")
                        .then(arg("ctx", StringArgumentType.word())
                                .then(arg("field", StringArgumentType.word())
                                        .then(arg("value", FloatArgumentType.floatArg())
                                                .executes(c -> edit(c, false))))))
                .then(lit("add")
                        .then(arg("ctx", StringArgumentType.word())
                                .then(arg("field", StringArgumentType.word())
                                        .then(arg("value", FloatArgumentType.floatArg())
                                                .executes(c -> edit(c, true))))))
                .then(lit("cast")
                        .then(arg("field", StringArgumentType.word())
                                .then(arg("value", FloatArgumentType.floatArg())
                                        .executes(RodDebugCommand::castEdit))))
                // §rod-bend-3d: the chain's own switches — the 3D blank on/off and the full-load bend.
                // §fish-3d: the rollback switch for the fish in the water.
                .then(lit("fish3d")
                        .executes(c -> {
                            say(c, "§efish3d: " + (ShoalRenderer.FISH_3D ? "§aON" : "§cOFF")
                                    + " §7(/rfrod fish3d on|off) — bodies from the sprites, or the flat wave");
                            return 1;
                        })
                        .then(lit("on").executes(c -> { ShoalRenderer.FISH_3D = true; RodClientSettings.save(); say(c, "§afish3d ON"); return 1; }))
                        .then(lit("off").executes(c -> { ShoalRenderer.FISH_3D = false; RodClientSettings.save(); say(c, "§cfish3d OFF"); return 1; })))
                .then(lit("blank")
                        .executes(c -> {
                            say(c, String.format("§eblank3d %s §fdeg=%.0f",
                                    RodChain.ENABLED ? "§aON" : "§cOFF", RodChain.MAX_BEND_DEG));
                            return 1;
                        })
                        .then(lit("on").executes(c -> { RodChain.ENABLED = true; say(c, "§ablank3d ON"); return 1; }))
                        .then(lit("off").executes(c -> { RodChain.ENABLED = false; say(c, "§cblank3d OFF"); return 1; }))
                        .then(lit("deg").then(arg("value", FloatArgumentType.floatArg(0f, 180f))
                                .executes(c -> {
                                    RodChain.MAX_BEND_DEG = FloatArgumentType.getFloat(c, "value");
                                    say(c, "§ablank deg = " + RodChain.MAX_BEND_DEG);
                                    return 1;
                                }))))
                // §rod-physics: the springs — status, switch, tunables, whip and the swing pivot.
                .then(lit("phys")
                        .executes(c -> { say(c, RodPhysics.describe()); return 1; })
                        .then(lit("on").executes(c -> { RodPhysics.ENABLED = true; say(c, "§aphys ON"); return 1; }))
                        .then(lit("off").executes(c -> { RodPhysics.ENABLED = false; say(c, "§cphys OFF"); return 1; }))
                        .then(lit("whip").then(arg("value", FloatArgumentType.floatArg(0f, 10f))
                                .executes(c -> {
                                    RodChain.WHIP_GAIN = FloatArgumentType.getFloat(c, "value");
                                    say(c, "§aphys whip = " + RodChain.WHIP_GAIN);
                                    return 1;
                                })))
                        .then(lit("pivot")
                                .then(arg("x", FloatArgumentType.floatArg(-32f, 32f))
                                        .then(arg("y", FloatArgumentType.floatArg(-32f, 32f))
                                                .then(arg("z", FloatArgumentType.floatArg(-32f, 32f))
                                                        .executes(c -> {
                                                            RodHandTransform.PIVOT[0] = FloatArgumentType.getFloat(c, "x");
                                                            RodHandTransform.PIVOT[1] = FloatArgumentType.getFloat(c, "y");
                                                            RodHandTransform.PIVOT[2] = FloatArgumentType.getFloat(c, "z");
                                                            say(c, String.format("§aphys pivot = {%.1f, %.1f, %.1f}",
                                                                    RodHandTransform.PIVOT[0], RodHandTransform.PIVOT[1], RodHandTransform.PIVOT[2]));
                                                            return 1;
                                                        })))))
                        .then(arg("field", StringArgumentType.word())
                                .then(arg("value", FloatArgumentType.floatArg())
                                        .executes(c -> {
                                            float v = RodPhysics.edit(StringArgumentType.getString(c, "field"),
                                                    FloatArgumentType.getFloat(c, "value"));
                                            if (Float.isNaN(v)) {
                                                say(c, "§cunknown field — stiffness damping drive max jerk pull");
                                                return 0;
                                            }
                                            say(c, "§aphys " + StringArgumentType.getString(c, "field") + " = " + v);
                                            return 1;
                                        }))))
                // §rod-tip-3d: constant trim for the captured FP tip anchor (the hand line needs none).
                .then(lit("tip3d")
                        .then(arg("dx", FloatArgumentType.floatArg(-1f, 1f))
                                .then(arg("dy", FloatArgumentType.floatArg(-1f, 1f))
                                        .executes(c -> {
                                            LineRenderer.TIP3D_OFFSET[0] = FloatArgumentType.getFloat(c, "dx");
                                            LineRenderer.TIP3D_OFFSET[1] = FloatArgumentType.getFloat(c, "dy");
                                            say(c, String.format("§atip3d = (%.3f, %.3f)",
                                                    LineRenderer.TIP3D_OFFSET[0], LineRenderer.TIP3D_OFFSET[1]));
                                            return 1;
                                        }))))
                // §hand-line: what fov the hand pass projects at (0 = same as the world, warp off).
                .then(lit("handfov")
                        .then(arg("deg", FloatArgumentType.floatArg(0f, 130f))
                                .executes(c -> {
                                    RodChain.HAND_FOV = FloatArgumentType.getFloat(c, "deg");
                                    say(c, "§ahand fov = " + RodChain.HAND_FOV + (RodChain.HAND_FOV <= 0f ? " §7(warp off)" : ""));
                                    return 1;
                                })))
                // §hand-space: pin the reading if the automatic measurement ever picks wrong.
                .then(lit("handspace")
                        .then(lit("auto").executes(c -> { RodChain.HAND_SPACE = -1; RodChain.resetHandSpace(); say(c, "§ahand space AUTO — measuring again, turn your head"); return 1; }))
                        .then(lit("view").executes(c -> { RodChain.HAND_SPACE = 0; say(c, "§ahand space VIEW (pinned)"); return 1; }))
                        .then(lit("world").executes(c -> { RodChain.HAND_SPACE = 1; say(c, "§ahand space WORLD (pinned)"); return 1; })))
                // §rod-tip-3d: the diagnostic that settles pose questions by MEASURING (the 1.20.1
                // port cost three rounds to a question this answers in one look).
                .then(lit("tipinfo").executes(RodDebugCommand::tipInfo)));
    }

    private static int tipInfo(CommandContext<Object> c) {
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player == null) { say(c, "§cno player"); return 0; }
        say(c, String.format("§etip NDC %s (%.3f, %.3f)  VIEW %s (%.2f, %.2f, %.2f)",
                RodChain.tipNdcFresh() ? "§afresh§e" : "§cstale§e", RodChain.TIP_NDC[0], RodChain.TIP_NDC[1],
                RodChain.tipViewFresh() ? "§afresh§e" : "§cstale§e",
                RodChain.TIP_VIEW[0], RodChain.TIP_VIEW[1], RodChain.TIP_VIEW[2]));
        say(c, "§e" + RodChain.handSpaceReport());
        say(c, String.format("§ehandline %s  handfov %.0f  load %.2f",
                RodChain.handLineFresh() ? "§afresh" : "§cstale",
                RodChain.HAND_FOV, ClientLineState.ownRodLoad()));
        return 1;
    }

    /**
     * §rod-bend debug: on 26.x the bend is a component on the STACK, not a client render flag — so
     * forcing a bucket means writing it onto the client's own copy of the held rod. That drives the
     * real item-definition dispatch, which is exactly the split we want: bucket shows = the model
     * path is fine and any missing bend is the server write; doesn't show = model/sprite problem.
     * The server overwrites it on the next inventory sync, and -1 clears it.
     */
    private static int forceBend(CommandContext<Object> c) {
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

    private static int castEdit(CommandContext<Object> c) {
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

    private static int edit(CommandContext<Object> c, boolean add) {
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

    private static int show(CommandContext<Object> c) {
        for (String line : RodHandTransform.showLines()) {
            say(c, line);
        }
        return 1;
    }

    /** The context is unused: the source only ever existed to reach chat, and chat is right here.
     *  Every command speaks, so this is also where the toggles get persisted (§rod-client-settings) —
     *  cheap, and the command is the settings file's only writer. */
    private static void say(Object ignoredCtx, String text) {
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player != null) mc.player.sendSystemMessage(Component.literal(text));
        RodClientSettings.save();
    }

    private static LiteralArgumentBuilder<Object> lit(String name) {
        return LiteralArgumentBuilder.literal(name);
    }

    private static <T> RequiredArgumentBuilder<Object, T> arg(String name, ArgumentType<T> type) {
        return RequiredArgumentBuilder.argument(name, type);
    }
}
