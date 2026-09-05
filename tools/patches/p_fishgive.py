# -*- coding: utf-8 -*-
"""§fish-give: /rffish give — a fish made the way the water makes one, for testing.

    py -X utf8 tools/patches/p_fishgive.py <root> [1211|1201|26]

    /rffish give <species> [variety|random|all] [count] [pattern]

The author asked for "a chest of koi" on 26.2. A /give with custom_data alone is the wrong tool there:
26.x draws a fish from what its stack carries, a chest never ticks, and the fish sit white until picked
up — the exact symptom §icon-topup exists for. And a hand-written card has to guess the genome, the
sex, the nature and the size class, which the water never guesses.

So: a server command (op 2) that builds the fish through FishItem.create, CatchCard.debug — the same
body() every catch goes through, with the variety and pattern the operator names — and, on 26.x,
stampIcon. `all` on a koi hands over one of each of the seventeen varieties; `random` lets the water
draw. Weight is an ordinary specimen, ±40%.
"""
import io, os, sys

ROOT = sys.argv[1]
D = sys.argv[2] if len(sys.argv) > 2 else "1211"
J = os.path.join(ROOT, "common/src/main/java/com/riverfishing")
STAMP = "            com.riverfishing.item.FishItem.stampIcon(fish);   // 26.x: the icon is the stack\n" if D == "26" else ""
# 26.x renamed both: the permission predicate is a Commands factory, and GameProfile is a record
PERM = ("net.minecraft.commands.Commands.hasPermission(net.minecraft.commands.Commands.LEVEL_GAMEMASTERS)"
        if D == "26" else "s -> s.hasPermission(2)")
NAME = "sp.getGameProfile().name()" if D == "26" else "sp.getGameProfile().getName()"

# ---- CatchCard.debug ------------------------------------------------------------------------------
p = os.path.join(J, "fish/CatchCard.java")
s = io.open(p, encoding="utf-8").read()
if "§fish-give" in s:
    print("  CatchCard: already patched")
else:
    old = """    /**
     * The half of the card that is the FISH — size class, kind, sex, nature, genes — shared by both."""
    assert old in s, "CatchCard.body's doc moved"
    # the card's header — Angler, Day, Water, Biome, Time, Season, Weather — is copied out of netted()
    # IN THIS FILE, so every tree's own spelling of those calls comes along (26.x renames half of them)
    net = s[s.index("    public static CompoundTag netted("):]
    head = net[net.index("        CompoundTag c = new CompoundTag();"):net.index('        c.putString("Bed", "");')]
    assert 'c.putString("Rod", "net");' in head and 'c.putString("Weather"' in head, "netted()'s header changed shape"
    head = head.replace('c.putString("Rod", "net");', 'c.putString("Rod", "debug");')
    s = s.replace(old, """    /**
     * §fish-give: a card for a fish an operator asked for by name — the same body() every catch goes
     * through, with the variety and the pattern named instead of drawn. No water overlay: a debug fish
     * is exactly what was asked for. A pattern under 0 is rolled the way a catch's is.
     */
    public static CompoundTag debug(ServerPlayer sp, ServerLevel level, FishProfile p, int weightG,
                                    BlockPos pos, String variety, int pattern) {
""" + head + """        c.putString("Bed", "");
        c.putString("Spot", "");
        c.putBoolean("Ice", false);
        c.putString("Eco", "");
        c.putInt("Value", 0);
        Random rng = new Random(level.getGameTime() * 31L + sp.getUUID().hashCode() + weightG + variety.hashCode());
        int rolled = rollPattern(level, pos, p, rng);
        boolean patterned = Pattern.has(rolled);    // the roll already knows whether this species wears one
        int pat = patterned && Pattern.has(pattern) ? pattern : rolled;
        body(c, p, weightG, "", rng, (byte) -1, variety, pat, null);
        return c;
    }

    /**
     * The half of the card that is the FISH — size class, kind, sex, nature, genes — shared by both.""", 1)
    io.open(p, "w", encoding="utf-8", newline="\n").write(s)
    print("  CatchCard.debug: a card by name")

# ---- /rffish give ----------------------------------------------------------------------------------
p = os.path.join(J, "command/JournalCommand.java")
s = io.open(p, encoding="utf-8").read()
if "§fish-give" in s:
    print("  JournalCommand: already patched")
else:
    old = """                        .then(Commands.literal("unlockall")"""
    assert old in s, "JournalCommand's unlockall branch moved"
    s = s.replace(old, """                        // §fish-give: a fish by name, made the way the water makes one — see give()
                        .then(Commands.literal("give").requires(%s)
                                .then(Commands.argument("species", StringArgumentType.word())
                                        .executes(c -> give(c, "", 1, -1))
                                        .then(Commands.argument("variety", StringArgumentType.word())
                                                .executes(c -> give(c, StringArgumentType.getString(c, "variety"), 1, -1))
                                                .then(Commands.argument("count", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 64))
                                                        .executes(c -> give(c, StringArgumentType.getString(c, "variety"),
                                                                com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "count"), -1))
                                                        .then(Commands.argument("pattern", com.mojang.brigadier.arguments.IntegerArgumentType.integer(-1, 999))
                                                                .executes(c -> give(c, StringArgumentType.getString(c, "variety"),
                                                                        com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "count"),
                                                                        com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "pattern"))))))))
                        .then(Commands.literal("unlockall")""" % PERM, 1)

    old = """    private static int unlockAll(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {"""
    assert old in s
    s = s.replace(old, """    /**
     * §fish-give: {@code /rffish give <species> [variety|random|all] [count] [pattern]}. The fish is built
     * through FishItem.create and CatchCard.debug — the body() every catch goes through — so it has a
     * genome, a sex, a nature and a size class the way a caught one does, and on 26.x it is stamped.
     * A koi variety may be named bare ("kohaku") or as the card writes it ("koi_kohaku"); {@code all}
     * on a koi is one of each of the seventeen; {@code random} lets the water draw.
     */
    private static int give(CommandContext<CommandSourceStack> c, String variety, int count, int pattern)
            throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) sp.level();
        String path = StringArgumentType.getString(c, "species");
        var id = RiverFishing.id(path);
        FishProfile p = FishProfileManager.get().byId(id);
        if (p == null || ModItems.fishItem(id) == null) {
            c.getSource().sendFailure(Component.literal("no such fish: " + path));
            return 0;
        }
        boolean koi = com.riverfishing.fish.Genome.isKoiId(path);
        java.util.List<String> varieties = new java.util.ArrayList<>();
        if (koi && "all".equals(variety)) {
            for (String v : com.riverfishing.fish.Genome.koiVarieties()) varieties.add("koi_" + v);
        } else {
            String v = "random".equals(variety) ? "" : variety;
            if (koi && !v.isEmpty() && !v.startsWith("koi_")) v = "koi_" + v;
            for (int i = 0; i < count; i++) varieties.add(v);
        }
        java.util.Random rng = new java.util.Random(level.getGameTime());
        int made = 0;
        for (String v : varieties) {
            int weightG = (int) Math.round(Math.max(p.weightMin, Math.min(p.weightMax,
                    p.weightMean * (0.6 + 0.8 * rng.nextDouble()))));
            double t = p.weightMax > p.weightMin ? (weightG - p.weightMin) / (p.weightMax - p.weightMin) : 0.5;
            int lengthCm = (int) Math.round(p.lengthMin + (p.lengthMax - p.lengthMin) * Math.max(0, Math.min(1, t)));
            net.minecraft.world.item.ItemStack fish = com.riverfishing.item.FishItem.create(
                    ModItems.fishItem(id), id, weightG, lengthCm, true);
            final String vv = v;
            final int ww = weightG;
            com.riverfishing.item.StackNbt.mutate(fish, tag -> tag.put(com.riverfishing.fish.CatchCard.TAG,
                    com.riverfishing.fish.CatchCard.debug(sp, level, p, ww, sp.blockPosition(), vv, pattern)));
%s            if (!sp.getInventory().add(fish)) sp.drop(fish, false);
            made++;
        }
        final int n = made;
        c.getSource().sendSuccess(() -> Component.literal("gave " + n + " " + path
                + (variety.isEmpty() ? "" : " (" + variety + ")")), true);
        return n;
    }

    private static int unlockAll(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {""" % STAMP, 1)
    io.open(p, "w", encoding="utf-8", newline="\n").write(s)
    print("  JournalCommand: /rffish give")
print("done (%s)" % D)
