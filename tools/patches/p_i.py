# -*- coding: utf-8 -*-
"""§breeding stream I: the warden — profession, post, confiscation, the board ban, the work-off.

    py -X utf8 tools/patches/p_i.py <repo root> [1211|1201|26]

Anchor replacement on eight existing files; every insert carries a "§i" marker so a rerun finds it and
does nothing. Exit 1 with the missing anchor printed when a tree has drifted. Written in the 1.21.1
dialect; to26 rewrites the handful of idioms the inserts use. 1.20.1 reads the 1.21.1 text unchanged.
Everything with a body lives in the NEW file fishing/Warden.java; these are the hooks.

    ModVillagers.java      WARDEN_POI + WARDEN after the fisherman's profession; the warden's trade
                           table registered after the fisherman's; randomGenome opened for Warden;
                           sendBoard sends `banned` and no posts for a banned player
    ModBlocks.java         WARDEN_POST, a plain Block, before the trophy-stand comment (after §g's five)
    NetItem.java           the `ponytail: warden` comment becomes the Warden.onPoach call
    FishingManager.java    Warden.workOff beside stocked.addBrood in releaseFish (stream C's line;
                           streams H and K anchor on session.weightG / releaseFry / finderPayload)
    Contracts.java         take0 refuses a banned player before it looks at the slot
    ContractBoardState.java  the "no work for a poacher" caption under the rep line
    JournalScreen.java     the `warden` guide page after `nets` (§D)
    fabric/RiverFishingFabric.java  the warden's POI states mapped like the stall's (§fabric-poi)

26.x: the fisherman's trades there are a datapack (trade_set/fisherman/level_N); the warden's two
trades are NOT registered by this script in that dialect — the profession is created with an empty
trade-set map and the integrator writes data/riverfishing/trade_set/warden/level_{1,2}.json.
"""
import io, os, re, sys

ROOT = sys.argv[1] if len(sys.argv) > 1 else "."
DIALECT = sys.argv[2] if len(sys.argv) > 2 else "1211"
SRC = os.path.join(ROOT, "common/src/main/java/com/riverfishing")
FABRIC = os.path.join(ROOT, "fabric/src/main/java/com/riverfishing/fabric")
MARK = "§i"


def read(path):
    with io.open(path, encoding="utf-8") as f:
        return f.read()


def write(path, text):
    with io.open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write(text)


def to26(java):
    if DIALECT != "26":
        return java
    java = re.sub(r"\.getInt\(([^()]+)\)", r".getIntOr(\1, 0)", java)
    java = re.sub(r"\.getBoolean\(([^()]+)\)", r".getBooleanOr(\1, false)", java)
    java = re.sub(r"\.getHolderOrThrow\(([^()]+\([^()]*\))\)", r".get(\1).orElseThrow()", java)
    java = java.replace("g.drawString(", "g.text(")   # GuiGraphics renamed it; the board was ported with it
    return java


def sub1(rel, old, new, base=SRC):
    """Exactly one anchor, replaced once; a file already carrying the insert is left alone."""
    path = os.path.join(base, rel)
    text = read(path)
    raw = new
    old, new = to26(old), to26(new)   # the 26 tree was ported by the same rewrite, so anchors match it too
    if new in text or raw in text:    # applied already — in this dialect or the other
        return
    if text.count(old) != 1:
        sys.exit("p_i: anchor not found once in %s (%d hits):\n%s" % (rel, text.count(old), old))
    write(path, text.replace(old, new))


# ---------------------------------------------------------------- ModVillagers: the profession
if DIALECT == "26":
    PROF_ANCHOR = "                    tradeSets()));\n"
    PROFESSION = '''
    // §i §breeding (0.9.0): the warden — the profession fishing/Warden looks for within reach of a
    // poached haul. His post is a plain block; his trades are a datapack here (trade_set/warden/*),
    // registered by the integrator — an empty map until then, which is a villager with no counter.
    public static final RegistrySupplier<PoiType> WARDEN_POI = POI_TYPES.register("warden",
            () -> new PoiType(Set.copyOf(ModBlocks.WARDEN_POST.get().getStateDefinition().getPossibleStates()), 1, 1));

    public static final RegistrySupplier<VillagerProfession> WARDEN = PROFESSIONS.register("warden",
            () -> new VillagerProfession(
                    Component.translatable("entity.minecraft.villager.riverfishing.warden"),
                    holder -> holder.is(WARDEN_POI.getKey()),
                    holder -> holder.is(WARDEN_POI.getKey()),
                    ImmutableSet.of(), ImmutableSet.of(),
                    SoundEvents.VILLAGER_WORK_FISHERMAN,
                    new Int2ObjectOpenHashMap<>()));
'''
else:
    PROF_ANCHOR = "                    SoundEvents.VILLAGER_WORK_FISHERMAN));\n"
    PROFESSION = '''
    // §i §breeding (0.9.0): the warden — the profession fishing/Warden looks for within reach of a
    // poached haul. His post is a plain block; his two trades are built beside the hook (Warden.trades)
    // and registered below, after the fisherman's table.
    public static final RegistrySupplier<PoiType> WARDEN_POI = POI_TYPES.register("warden",
            () -> new PoiType(Set.copyOf(ModBlocks.WARDEN_POST.get().getStateDefinition().getPossibleStates()), 1, 1));

    public static final RegistrySupplier<VillagerProfession> WARDEN = PROFESSIONS.register("warden",
            () -> new VillagerProfession("river_warden",
                    holder -> holder.is(WARDEN_POI.getKey()),
                    holder -> holder.is(WARDEN_POI.getKey()),
                    ImmutableSet.of(), ImmutableSet.of(),
                    SoundEvents.VILLAGER_WORK_FISHERMAN));
'''
sub1("registry/ModVillagers.java", PROF_ANCHOR, PROF_ANCHOR + PROFESSION)

# ---------------------------------------------------------------- ModVillagers: the trade table
if DIALECT != "26":
    sub1("registry/ModVillagers.java",
         "        com.riverfishing.platform.VillagerTradeRegistry.register(FISHERMAN, t);\n",
         "        com.riverfishing.platform.VillagerTradeRegistry.register(FISHERMAN, t);\n"
         "        com.riverfishing.platform.VillagerTradeRegistry.register(WARDEN, com.riverfishing.fishing.Warden.trades());   // §i\n")

# The warden's fry bucket rolls its genome the way the fisherman's does — one owner of "ordinary fry".
sub1("registry/ModVillagers.java",
     "    private static String randomGenome(net.minecraft.util.RandomSource rng) {\n",
     "    public static String randomGenome(net.minecraft.util.RandomSource rng) {   // §i: the warden's fry too\n")

# ---------------------------------------------------------------- ModVillagers: the board
sub1("registry/ModVillagers.java",
     '        t.putInt("rep", com.riverfishing.fishing.Contracts.rep(player));\n',
     '        t.putInt("rep", com.riverfishing.fishing.Contracts.rep(player));\n'
     '        // §i: a poacher\'s board is blank — the flag tells the client why, the empty list tells it what.\n'
     '        boolean banned = com.riverfishing.fishing.Warden.banned(sp);\n'
     '        t.putBoolean("banned", banned);\n')
sub1("registry/ModVillagers.java",
     "        for (net.minecraft.nbt.CompoundTag post : com.riverfishing.fishing.Contracts.posts(villager, level)) {\n",
     "        for (net.minecraft.nbt.CompoundTag post : banned ? java.util.List.<net.minecraft.nbt.CompoundTag>of()   // §i\n"
     "                : com.riverfishing.fishing.Contracts.posts(villager, level)) {\n")

# ---------------------------------------------------------------- ModBlocks: the post
BLOCKS_ANCHOR = "    // Trophy stand (§15.5) — mounts a caught fish.\n"
sub1("registry/ModBlocks.java", BLOCKS_ANCHOR,
     "    // §i §breeding (0.9.0): the warden's booth — his job site (ModVillagers.WARDEN_POI) and nothing\n"
     "    // else, so a plain Block is the whole class.\n"
     "    public static final RegistrySupplier<Block> WARDEN_POST = registerSimple(\"warden_post\",\n"
     "            () -> new Block(BlockBehaviour.Properties.of().strength(2.0f).sound(SoundType.WOOD)));\n"
     "\n" + BLOCKS_ANCHOR)

# ---------------------------------------------------------------- NetItem: the hook
sub1("item/NetItem.java",
     "            // ponytail: warden/patrol and confiscation hook in here — a nearby fisherman villager\n"
     "            // reacting to `poached > 0` (take the net, raise the alarm) — once that feature exists.\n",
     "            // §i: the warden. In his reach the net is his and the fine is due; out of it, the record\n"
     "            // still grows (fishing/Warden).\n"
     "            com.riverfishing.fishing.Warden.onPoach(sp, level, pos, poached);\n")

# ---------------------------------------------------------------- FishingManager: the work-off
sub1("fishing/FishingManager.java",
     "                stocked.addBrood(region, species.getPath(), sex, day, genes, thrower == null ? null : thrower.getUUID());\n",
     "                stocked.addBrood(region, species.getPath(), sex, day, genes, thrower == null ? null : thrower.getUUID());\n"
     "                if (thrower != null) com.riverfishing.fishing.Warden.workOff(thrower);   // §i: a fish in pays a poach off\n")

# ---------------------------------------------------------------- Contracts: the refusal
sub1("fishing/Contracts.java",
     "        List<CompoundTag> posts = posts(v, level);\n        if (slot < 0 || slot >= posts.size()) return;\n",
     "        if (Warden.banned(sp)) return;   // §i: the board he saw was blank; a stale click gets nothing\n"
     "        List<CompoundTag> posts = posts(v, level);\n        if (slot < 0 || slot >= posts.size()) return;\n")

# ---------------------------------------------------------------- ContractBoardState: the caption
sub1("client/ContractBoardState.java",
     "        int total = HEAD + 4;\n",
     "        int total = HEAD + 4;\n"
     "        // §i: a poacher's board carries no posts — only the reason. The server sent an empty list with it.\n"
     "        List<FormattedCharSequence> banned = board.getBoolean(\"banned\")\n"
     "                ? font.split(Component.translatable(\"screen.riverfishing.contract_board.banned\"), W - 10)\n"
     "                : java.util.List.<FormattedCharSequence>of();\n"
     "        total += banned.isEmpty() ? 0 : LINE * banned.size() + 4;\n")
sub1("client/ContractBoardState.java",
     "                x + 5, y + 15, INK2, false);\n",
     "                x + 5, y + 15, INK2, false);\n"
     "        for (int i = 0; i < banned.size(); i++) {   // §i\n"
     "            g.drawString(font, banned.get(i), x + 5, y + HEAD + 4 + LINE * i, INK, false);\n"
     "        }\n")

# ---------------------------------------------------------------- JournalScreen: the guide page
sub1("client/JournalScreen.java",
     '        addGuide("nets", modStack("seine_net")); // §D\n',
     '        addGuide("nets", modStack("seine_net")); // §D\n'
     '        addGuide("warden", modStack("warden_post")); // §i\n')

# ---------------------------------------------------------------- Fabric: the POI states
sub1("RiverFishingFabric.java",
     "        PoiTypesInvoker.riverfishing$registerBlockStates(poi, poi.value().matchingStates());\n",
     "        PoiTypesInvoker.riverfishing$registerBlockStates(poi, poi.value().matchingStates());\n"
     "        // §i: the warden's post, the same way.\n"
     "        Holder<PoiType> wardenPoi = BuiltInRegistries.POINT_OF_INTEREST_TYPE.getHolderOrThrow(ModVillagers.WARDEN_POI.getKey());\n"
     "        PoiTypesInvoker.riverfishing$registerBlockStates(wardenPoi, wardenPoi.value().matchingStates());\n",
     base=FABRIC)

print("p_i: ok (%s)%s" % (DIALECT, " — warden trade_set datapack still to write" if DIALECT == "26" else ""))
