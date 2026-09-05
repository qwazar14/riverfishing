# -*- coding: utf-8 -*-
"""§b/breeding — stream B's edits to EXISTING files (the new classes are plain files in the tree).

    py tools/patches/p_b.py <repo root> [1211|1201|26]

Touches ModItems (roe, fry, and the two net items on behalf of streams C/D), AquariumBlock (a server
ticker on the master cell, a breeding branch in front of the add/take-a-fish click, the roe slot popped
on removal) and AquariumBlockEntity (the four counters and their NBT). Each file gets a "§b/breeding"
marker in the inserted text, so a rerun skips it; a missing anchor exits 1 with the anchor printed.
The dialect argument only changes the anchor text and the NBT / InteractionResult spelling — the
logic lives in AquariumBreeding.java, which the integrator ports like any new file.
"""
import os
import sys

ROOT = sys.argv[1] if len(sys.argv) > 1 else "."
DIALECT = sys.argv[2] if len(sys.argv) > 2 else "1211"
MARK = "§b/breeding"
JAVA = os.path.join("common", "src", "main", "java", "com", "riverfishing")

CONSUME = {"1211": "net.minecraft.world.ItemInteractionResult.CONSUME",
           "1201": "InteractionResult.CONSUME",
           "26": "net.minecraft.world.InteractionResult.CONSUME"}[DIALECT]
CLIENT = "level.isClientSide()" if DIALECT == "26" else "level.isClientSide"


def props(name):
    # §26.1: every Item.Properties carries its registry id.
    return 'props("%s")' % name if DIALECT == "26" else "props()"


def sub1(text, old, new, path):
    n = text.count(old)
    if n != 1:
        print("%s: anchor found %d times, expected 1:\n%s" % (path, n, old))
        sys.exit(1)
    return text.replace(old, new)


def patch(rel, edits):
    path = os.path.join(ROOT, rel)
    with open(path, encoding="utf-8") as f:
        raw = f.read()
    if MARK in raw:
        print("already patched: " + rel)
        return
    crlf = "\r\n" in raw
    text = raw.replace("\r\n", "\n")
    for old, new in edits:
        text = sub1(text, old, new, rel)
    if crlf:
        text = text.replace("\n", "\r\n")
    with open(path, "w", encoding="utf-8", newline="") as f:
        f.write(text)
    print("patched: " + rel)


# ---- ModItems: the tank's produce and the nets ----------------------------------------------------
CONTRACT_ANCHOR = {
    "1211": "            () -> new com.riverfishing.item.ContractItem(new Item.Properties()));\n",
    "1201": "            () -> new com.riverfishing.item.ContractItem(new Item.Properties()));\n",
    "26":   '            () -> new com.riverfishing.item.ContractItem(props("contract")));\n',
}[DIALECT]
ITEMS = CONTRACT_ANCHOR + """
    // %s (0.9.0): what a live tank produces and what a net hauls. The net classes belong to the nets
    // stream; they are registered HERE because item registration is one file's job (breeding-api.md).
    public static final RegistrySupplier<Item> ROE = reg("roe", () -> new com.riverfishing.item.RoeItem(%s));
    public static final RegistrySupplier<Item> FRY = reg("fry", () -> new com.riverfishing.item.FryItem(%s));
    public static final RegistrySupplier<Item> SEINE_NET = reg("seine_net", () -> new com.riverfishing.item.SeineNetItem(%s));
    public static final RegistrySupplier<Item> CAST_NET = reg("cast_net", () -> new com.riverfishing.item.CastNetItem(%s));
""" % (MARK, props("roe"), props("fry"), props("seine_net"), props("cast_net"))
patch(os.path.join(JAVA, "registry", "ModItems.java"), [(CONTRACT_ANCHOR, ITEMS)])

# ---- AquariumBlock: ticker, the breeding click, the roe slot on removal ---------------------------
HELD = "        ItemStack held = player.getItemInHand(hand);\n"
CLICK = HELD + """        // %s: food, roe and the roe slot come first; a fish in hand still goes in below.
        if (AquariumBreeding.use(level, be, player, held)) return %s;
""" % (MARK, CONSUME)
RENDER = "    @Override\n    public RenderShape getRenderShape(BlockState state) {\n"
TICKER = """    // %s: the master cell ticks the tank on the server; the other cells have no entity to tick.
    @Nullable
    @Override
    public <T extends BlockEntity> net.minecraft.world.level.block.entity.BlockEntityTicker<T> getTicker(
            Level level, BlockState state, net.minecraft.world.level.block.entity.BlockEntityType<T> type) {
        if (%s || type != com.riverfishing.registry.ModBlockEntities.AQUARIUM.get()) return null;
        return (lvl, pos, st, be) -> AquariumBreeding.tick(lvl, (AquariumBlockEntity) be);
    }

""" % (MARK, CLIENT) + RENDER
block_edits = [(HELD, CLICK), (RENDER, TICKER)]
if DIALECT != "26":
    POP = "                for (ItemStack f : be.getFishes()) popResource(level, pos, f);\n"
    block_edits.append((POP, POP + "                if (!be.roe.isEmpty()) popResource(level, pos, be.roe);\n"))
patch(os.path.join(JAVA, "block", "AquariumBlock.java"), block_edits)

# ---- AquariumBlockEntity: the counters and their NBT ----------------------------------------------
FIELDS_ANCHOR = "    private final List<ItemStack> fishes = new ArrayList<>();\n"
FIELDS = FIELDS_ANCHOR + """
    // %s (0.9.0): the tank is a live one — the rules are in AquariumBreeding, next door, which is the
    // only thing that reads or writes these (package-private on purpose; no getters for one caller).
    long fedUntil;                   // world day until which the fish count as fed (exclusive)
    int spawnTicks;                  // unbroken ticks of good conditions towards a clutch
    int incubate;                    // ticks the roe in the slot has been incubating (tank without fish)
    ItemStack roe = ItemStack.EMPTY; // the roe slot: a RoeItem, or the FryItem it hatched into
""" % MARK
SYNC = ("    private void sync() {\n", "    void sync() {\n")
if DIALECT == "26":
    SAVE_ANCHOR = "        tag.store(\"Fishes\", ItemStack.OPTIONAL_CODEC.listOf(), java.util.List.copyOf(fishes));\n"
    SAVE = SAVE_ANCHOR + """        tag.putLong("FedUntil", fedUntil);
        tag.putInt("SpawnTicks", spawnTicks);
        tag.putInt("Incubate", incubate);
        tag.store("Roe", ItemStack.OPTIONAL_CODEC, roe);
"""
    LOAD_ANCHOR = "        super.loadAdditional(tag);\n        fishes.clear();\n"
    LOAD = LOAD_ANCHOR + """        fedUntil = tag.getLongOr("FedUntil", 0L);
        spawnTicks = tag.getIntOr("SpawnTicks", 0);
        incubate = tag.getIntOr("Incubate", 0);
        roe = tag.read("Roe", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
"""
    POP_ANCHOR = "            fishes.clear();\n        }\n        super.preRemoveSideEffects(pos, state);\n"
    POP = ("            if (!roe.isEmpty()) net.minecraft.world.level.block.Block.popResource(level, pos, roe);\n"
           "            roe = ItemStack.EMPTY;\n") + POP_ANCHOR
    be_edits = [(FIELDS_ANCHOR, FIELDS), SYNC, (SAVE_ANCHOR, SAVE), (LOAD_ANCHOR, LOAD), (POP_ANCHOR, POP)]
else:
    SAVE_ANCHOR = "        tag.put(\"Fishes\", list);\n"
    roe_save = "roe.save(registries)" if DIALECT == "1211" else "roe.save(new CompoundTag())"
    SAVE = SAVE_ANCHOR + """        tag.putLong("FedUntil", fedUntil);
        tag.putInt("SpawnTicks", spawnTicks);
        tag.putInt("Incubate", incubate);
        if (!roe.isEmpty()) tag.put("Roe", %s);
""" % roe_save
    LOAD_ANCHOR = ("        super.loadAdditional(tag, registries);\n" if DIALECT == "1211"
                   else "        super.load(tag);\n") + "        fishes.clear();\n"
    roe_load = ("ItemStack.parseOptional(registries, tag.getCompound(\"Roe\"))" if DIALECT == "1211"
                else "ItemStack.of(tag.getCompound(\"Roe\"))")
    LOAD = LOAD_ANCHOR + """        fedUntil = tag.getLong("FedUntil");
        spawnTicks = tag.getInt("SpawnTicks");
        incubate = tag.getInt("Incubate");
        roe = tag.contains("Roe") ? %s : ItemStack.EMPTY;
""" % roe_load
    be_edits = [(FIELDS_ANCHOR, FIELDS), SYNC, (SAVE_ANCHOR, SAVE), (LOAD_ANCHOR, LOAD)]
patch(os.path.join(JAVA, "block", "AquariumBlockEntity.java"), be_edits)

# ---- 26.x client item definitions (assets/<ns>/items/<id>.json), which the model alone no longer is -
if DIALECT == "26":
    for item in ("roe", "fry"):
        p = os.path.join(ROOT, "common", "src", "main", "resources", "assets", "riverfishing", "items", item + ".json")
        if not os.path.exists(p):
            os.makedirs(os.path.dirname(p), exist_ok=True)
            with open(p, "w", encoding="utf-8", newline="\n") as f:
                f.write('{\n  "model": {\n    "type": "minecraft:model",\n    "model": "riverfishing:item/%s"\n  }\n}\n' % item)
            print("wrote: " + os.path.relpath(p, ROOT))
