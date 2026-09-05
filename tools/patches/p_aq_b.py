# -*- coding: utf-8 -*-
"""§aq-b — stream B of the aquarium window (0.9.0): AquariumBlockEntity becomes a 12-slot Container.

    py tools/patches/p_aq_b.py <repo root> [1211|1201|26]

The rules live in AquariumBreeding.java (edited directly, ported like a new file); this script only
patches the ENTITY, whose update tag stream C (the renderer) may be editing at the same time — so every
anchor is a signature or a line C has no reason to touch, never getUpdateTag. The fish list becomes
slots 0..5 of a NonNullList (slot 9 stays the `roe` field so the renderer's getRoe()/getIncubate() keep
working), plus the water and the window's ten ints. A rerun is a no-op (the "§aq-b" marker); a missing
anchor exits 1 with the anchor printed. Old tanks migrate: "Fishes" (or the older "Fish") into slots 0..2.

Apply AFTER stream C's patch. The block (stream A) must drop the contents on removal with
`net.minecraft.world.Containers.dropContents(level, pos, be)` — addFish/removeLastFish/isFull are gone.
"""
import os
import sys

ROOT = sys.argv[1] if len(sys.argv) > 1 else "."
DIALECT = sys.argv[2] if len(sys.argv) > 2 else "1211"
MARK = "§aq-b"
REL = os.path.join("common", "src", "main", "java", "com", "riverfishing", "block", "AquariumBlockEntity.java")


def sub1(text, old, new):
    n = text.count(old)
    if n != 1:
        print("%s: anchor found %d times, expected 1:\n%s" % (REL, n, old))
        sys.exit(1)
    return text.replace(old, new)


# ---- the anchors that are the same in every dialect -----------------------------------------------
CLASS = "public class AquariumBlockEntity extends BlockEntity {\n"
MAX = "    public static final int MAX_FISH = 3;\n"
FIELDS = "    private final List<ItemStack> fishes = new ArrayList<>();\n"
ROE = "    ItemStack roe = ItemStack.EMPTY; // the roe slot: a RoeItem, or the FryItem it hatched into\n"
IMPORT = "import net.minecraft.world.item.ItemStack;\n"
GETFISHES = """    /** The mounted fish (0..3), for the renderer and interaction. */
    public List<ItemStack> getFishes() {
        return fishes;
    }
"""
ISFULL = """    public boolean isFull() {
        return fishes.size() >= MAX_FISH;
    }

    public boolean isEmpty() {
        return fishes.isEmpty();
    }

"""
ADDFISH = """    /** Add one fish if there's room. Returns true when it went in. */
    public boolean addFish(ItemStack stack) {
        if (isFull() || stack.isEmpty()) return false;
        fishes.add(stack.copyWithCount(1));
        sync();
        return true;
    }

"""
REMOVELAST = """    /** Remove and return the most-recently added fish, or EMPTY when the tank is empty. */
    public ItemStack removeLastFish() {
        if (fishes.isEmpty()) return ItemStack.EMPTY;
        ItemStack out = fishes.remove(fishes.size() - 1);
        sync();
        return out;
    }

"""

NEW_MAX = """    /** Slots 0..5 hold fish (%s: the window's 3x2 grid). */
    public static final int MAX_FISH = 6;
    /** 0-5 fish, 6 food, 7 groundbait, 8 water bucket, 9 roe/fry (the {@link #roe} field), 10-11 modules. */
    public static final int SLOTS = 12;
""" % MARK
NEW_FIELDS = "    private final NonNullList<ItemStack> items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);\n"
NEW_ROE = ROE + """    int water;                       // 0..100, a fresh bucket is 100
    long waterAcc;                   // water change in percent-ticks not yet a whole percent (not saved)
    long clock;                      // world time the ticker last saw (not saved: a reload skips the gap)
    boolean oil;                     // fish oil was taken at the start of the current spawn run
    String lastFood = "";            // what the last feeding was ("fish_meal" makes the clutch richer)
    final int[] view = new int[10];  // the ten ints the window reads, filled by the rules once a second
"""
NEW_GETFISHES = """    /** The fish in slots 0..5, the empty ones skipped — the renderer, Jade and the rules read this. */
    public List<ItemStack> getFishes() {
        List<ItemStack> out = new ArrayList<>(MAX_FISH);
        for (int i = 0; i < MAX_FISH; i++) if (!items.get(i).isEmpty()) out.add(items.get(i));
        return out;
    }

    /** Ten ints for the window (docs/design/breeding-api.md, Layer 4), filled by the rules once a second. */
    public net.minecraft.world.inventory.ContainerData data() {
        return new net.minecraft.world.inventory.ContainerData() {
            @Override public int get(int i) { return view[i]; }
            @Override public void set(int i, int v) { view[i] = v; }
            @Override public int getCount() { return view.length; }
        };
    }

    // ---- Container: twelve slots. Slot 9 IS the roe field — the renderer and the rules call it by
    // name, the menu by number — so the two never disagree. ----

    @Override
    public int getContainerSize() {
        return SLOTS;
    }

    @Override
    public boolean isEmpty() {
        for (int i = 0; i < SLOTS; i++) if (!getItem(i).isEmpty()) return false;
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return slot == 9 ? roe : items.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int count) {
        ItemStack out = slot == 9 ? roe.split(count) : ContainerHelper.removeItem(items, slot, count);
        if (!out.isEmpty()) changed(slot);
        return out;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        ItemStack out = getItem(slot);
        if (slot == 9) roe = ItemStack.EMPTY; else items.set(slot, ItemStack.EMPTY);
        return out;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot == 9) roe = stack; else items.set(slot, stack);
        if (stack.getCount() > getMaxStackSize()) stack.setCount(getMaxStackSize());
        changed(slot);
    }

    /** The menu's own filters, mirrored so a hopper obeys the same table as a hand. */
    @Override
    public boolean canPlaceItem(int slot, ItemStack s) {
        if (slot < MAX_FISH) return s.getItem() instanceof com.riverfishing.item.FishItem && com.riverfishing.fish.CatchCard.has(s);
        return switch (slot) {
            case 6 -> s.getItem() instanceof com.riverfishing.item.BaitItem b && !b.artificial()
                    || s.getItem() instanceof com.riverfishing.item.FishMealItem
                    || s.getItem() instanceof com.riverfishing.item.FishOilItem;
            case 7 -> s.getItem() instanceof com.riverfishing.item.GroundbaitItem;
            case 8 -> s.is(net.minecraft.world.item.Items.WATER_BUCKET);
            case 9 -> s.getItem() instanceof com.riverfishing.item.RoeItem && getFishes().isEmpty(); // roe to hatch, in an empty tank
            default -> s.getItem() instanceof net.minecraft.world.item.BlockItem bi && bi.getBlock() instanceof WaterUpgradeBlock;
        };
    }

    @Override
    public boolean stillValid(Player player) {
        return net.minecraft.world.Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent() {
        items.clear();
        roe = ItemStack.EMPTY;
        incubate = 0;
        sync();
    }

    /** Fish and the roe slot are drawn in the world, so they sync; the rest only needs saving. */
    private void changed(int slot) {
        if (slot < MAX_FISH || slot == 9) {
            // Roe taken out (or hatched) forgets its days; roe put in starts them on the next tick.
            if (slot == 9 && !(roe.getItem() instanceof com.riverfishing.item.RoeItem)) incubate = 0;
            sync();
        } else {
            setChanged();
        }
    }
"""
NEW_IMPORT = ("import net.minecraft.core.NonNullList;\n" + IMPORT
              + "import net.minecraft.world.ContainerHelper;\nimport net.minecraft.world.entity.player.Player;\n")
EXTRA_SAVE = """        tag.putInt("Water", water);
        tag.putBoolean("Oil", oil);
        tag.putString("LastFood", lastFood);
"""

edits = [
    (CLASS, "public class AquariumBlockEntity extends BlockEntity implements net.minecraft.world.Container {\n"),
    (MAX, NEW_MAX),
    (FIELDS, NEW_FIELDS),
    (ROE, NEW_ROE),
    (IMPORT, NEW_IMPORT),
    (GETFISHES, NEW_GETFISHES),
    (ISFULL, ""),
    (ADDFISH, ""),
    (REMOVELAST, ""),
]

# ---- persistence, per dialect ----------------------------------------------------------------------
if DIALECT == "26":
    edits += [
        ("        tag.store(\"Fishes\", ItemStack.OPTIONAL_CODEC.listOf(), java.util.List.copyOf(fishes));\n",
         "        ContainerHelper.saveAllItems(tag, items);\n" + EXTRA_SAVE),
        ("        super.loadAdditional(tag);\n        fishes.clear();\n",
         """        super.loadAdditional(tag);
        items.clear();
        ContainerHelper.loadAllItems(tag, items);
        water = tag.getIntOr("Water", 0);
        oil = tag.getBooleanOr("Oil", false);
        lastFood = tag.getStringOr("LastFood", "");
"""),
        ("""        for (ItemStack s : tag.read("Fishes", ItemStack.OPTIONAL_CODEC.listOf()).orElse(java.util.List.of())) {
            if (!s.isEmpty() && fishes.size() < MAX_FISH) fishes.add(s);
        }
        if (fishes.isEmpty()) { // migrate the old single-fish format
            tag.read("Fish", ItemStack.OPTIONAL_CODEC).filter(s -> !s.isEmpty()).ifPresent(fishes::add);
        }
""", """        int n = 0; // migrate the pre-window tank: its mounted fish (or the older single one) into the first slots
        for (ItemStack s : tag.read("Fishes", ItemStack.OPTIONAL_CODEC.listOf()).orElse(java.util.List.of())) {
            if (!s.isEmpty() && n < MAX_FISH) items.set(n++, s);
        }
        if (n == 0) tag.read("Fish", ItemStack.OPTIONAL_CODEC).filter(s -> !s.isEmpty()).ifPresent(s -> items.set(0, s));
"""),
        # the entity pops its own contents on removal in 26.x: every slot now, not the fish and the roe
        ("""            for (ItemStack f : fishes) {
                net.minecraft.world.level.block.Block.popResource(level, pos, f);
            }
            if (!roe.isEmpty()) net.minecraft.world.level.block.Block.popResource(level, pos, roe);
            roe = ItemStack.EMPTY;
            fishes.clear();
""", "            net.minecraft.world.Containers.dropContents(level, pos, this);\n"),
    ]
else:
    reg = ", registries" if DIALECT == "1211" else ""
    save_one = "s.save(registries, new CompoundTag())" if DIALECT == "1211" else "s.save(new CompoundTag())"
    parse = ("ItemStack.parseOptional(registries, %s)" if DIALECT == "1211" else "ItemStack.of(%s)")
    edits += [
        ("        ListTag list = new ListTag();\n        for (ItemStack s : fishes) list.add(%s);\n        tag.put(\"Fishes\", list);\n" % save_one,
         "        ContainerHelper.saveAllItems(tag, items%s);\n" % reg + EXTRA_SAVE),
        ("        fishes.clear();\n",
         """        items.clear();
        ContainerHelper.loadAllItems(tag, items%s);
        water = tag.getInt("Water");
        oil = tag.getBoolean("Oil");
        lastFood = tag.getString("LastFood");
""" % reg),
        ("""        if (tag.contains("Fishes")) {
            ListTag list = tag.getList("Fishes", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size() && fishes.size() < MAX_FISH; i++) {
                ItemStack s = %s;
                if (!s.isEmpty()) fishes.add(s);
            }
        } else if (tag.contains("Fish")) { // migrate the old single-fish format
            ItemStack s = %s;
            if (!s.isEmpty()) fishes.add(s);
        }
""" % (parse % 'list.getCompound(i)', parse % 'tag.getCompound("Fish")'),
         """        if (tag.contains("Fishes")) { // migrate the pre-window tank: its mounted fish into the first slots
            ListTag list = tag.getList("Fishes", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size() && i < MAX_FISH; i++) items.set(i, %s);
        } else if (tag.contains("Fish")) { // older still: the single-fish format
            items.set(0, %s);
        }
""" % (parse % 'list.getCompound(i)', parse % 'tag.getCompound("Fish")')),
    ]

path = os.path.join(ROOT, REL)
with open(path, encoding="utf-8") as f:
    raw = f.read()
if MARK in raw:
    print("already patched: " + REL)
    sys.exit(0)
crlf = "\r\n" in raw
text = raw.replace("\r\n", "\n")
for old, new in edits:
    text = sub1(text, old, new)
if crlf:
    text = text.replace("\n", "\r\n")
with open(path, "w", encoding="utf-8", newline="") as f:
    f.write(text)
print("patched: " + REL)
