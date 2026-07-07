package com.riverfishing.client;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.riverfishing.RiverFishing;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Live rod-transform debugger (§rod-debug). Client-only command {@code /rfrod} that tweaks
 * {@link RodHandTransform} in real time so the in-hand rod pose can be dialled in without rebuilding:
 * <ul>
 *   <li>{@code /rfrod set <tp|tpl|fp|fpl> <tx|ty|tz|rx|ry|rz|s> <value>} — set a field
 *       (tp/fp = third/first person RIGHT hand, tpl/fpl = LEFT hand)</li>
 *   <li>{@code /rfrod add <ctx> <field> <delta>} — nudge a field (great for live tuning)</li>
 *   <li>{@code /rfrod show} — print current values (paste them into RodHandTransform to keep)</li>
 *   <li>{@code /rfrod reset} — back to the built-in defaults</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = RiverFishing.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class RodDebugCommand {
    private RodDebugCommand() {}

    @SubscribeEvent
    public static void onRegister(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("rfrod")
                .then(Commands.literal("show").executes(RodDebugCommand::show))
                .then(Commands.literal("reset").executes(c -> {
                    RodHandTransform.reset();
                    say(c, "§ereset to defaults");
                    return show(c);
                }))
                .then(Commands.literal("set")
                        .then(Commands.argument("ctx", StringArgumentType.word())
                                .then(Commands.argument("field", StringArgumentType.word())
                                        .then(Commands.argument("value", FloatArgumentType.floatArg())
                                                .executes(c -> edit(c, false))))))
                .then(Commands.literal("add")
                        .then(Commands.argument("ctx", StringArgumentType.word())
                                .then(Commands.argument("field", StringArgumentType.word())
                                        .then(Commands.argument("value", FloatArgumentType.floatArg())
                                                .executes(c -> edit(c, true))))))
                .then(Commands.literal("cast")
                        .then(Commands.argument("field", StringArgumentType.word())
                                .then(Commands.argument("value", FloatArgumentType.floatArg())
                                        .executes(RodDebugCommand::castEdit)))));
    }

    private static int castEdit(CommandContext<CommandSourceStack> c) {
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

    private static int edit(CommandContext<CommandSourceStack> c, boolean add) {
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

    private static int show(CommandContext<CommandSourceStack> c) {
        for (String line : RodHandTransform.showLines()) {
            say(c, line);
        }
        return 1;
    }

    private static void say(CommandContext<CommandSourceStack> c, String text) {
        c.getSource().sendSystemMessage(Component.literal(text));
    }
}
