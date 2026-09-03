# -*- coding: utf-8 -*-
"""§breeding stream E: roe at the counter, fry for sale, fry contracts.

    py -X utf8 tools/patches/p_e.py <repo root> [1211|1201|26]

Anchor replacement on seven existing files; every insert carries a "§e" marker so a rerun finds it and
does nothing. Exit 1 with the missing anchor printed when a tree has drifted. Written in the 1.21.1
dialect; to26 rewrites the handful of idioms the inserts use. 1.20.1 reads the 1.21.1 text unchanged.
The roe sale itself lives in the NEW file fishing/RoeSale.java; ModEvents only routes to it.
"""
import io, os, re, sys

ROOT = sys.argv[1] if len(sys.argv) > 1 else "."
DIALECT = sys.argv[2] if len(sys.argv) > 2 else "1211"
SRC = os.path.join(ROOT, "common/src/main/java/com/riverfishing")
MARK = "§e"


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
    java = re.sub(r"\.getString\(([^()]+)\)", r'.getStringOr(\1, "")', java)
    java = java.replace("net.minecraft.world.entity.npc.Villager", "net.minecraft.world.entity.npc.villager.Villager")
    java = re.sub(r"displayClientMessage\((.+?), true\);", r"sendOverlayMessage(\1);", java, flags=re.S)
    return java


def sub1(rel, old, new):
    """Exactly one anchor, replaced once; a file already carrying the insert is left alone."""
    path = os.path.join(SRC, rel)
    text = read(path)
    raw = new
    old, new = to26(old), to26(new)   # the 26 tree was ported by the same rewrite, so anchors match it too
    if new in text or raw in text:    # applied already — in this dialect or the other
        return
    if text.count(old) != 1:
        sys.exit("p_e: anchor not found once in %s (%d hits):\n%s" % (rel, text.count(old), old))
    write(path, text.replace(old, new))


# ---------------------------------------------------------------- ModEvents: roe over the counter
sub1("event/ModEvents.java",
     "        // §contracts-b1: right-click a fisherman with a contract in hand",
     "        // §e §breeding: roe is sold the way a contract is handed in — a trade cannot match the species\n"
     "        // inside the NBT, so right-clicking the fisherman with the clutch IS the trade.\n"
     "        dev.architectury.event.events.common.InteractionEvent.INTERACT_ENTITY.register((player, entity, hand) -> {\n"
     "            if (!(entity instanceof net.minecraft.world.entity.npc.Villager v)) return EventResult.pass();\n"
     "            if (!(player.getItemInHand(hand).getItem() instanceof com.riverfishing.item.RoeItem)) return EventResult.pass();\n"
     "            if (v.getVillagerData().getProfession() != com.riverfishing.registry.ModVillagers.FISHERMAN.get()) return EventResult.pass();\n"
     "            if (player instanceof ServerPlayer sp) com.riverfishing.fishing.RoeSale.sell(sp, player.getItemInHand(hand));\n"
     "            return EventResult.interruptTrue();\n"
     "        });\n"
     "\n"
     "        // §contracts-b1: right-click a fisherman with a contract in hand")

# ---------------------------------------------------------------- ModVillagers: fry beside the order seat
sub1("registry/ModVillagers.java",
     "        orderSlot(villager, level, offers);\n        trustedSlots(villager, player, offers);",
     "        orderSlot(villager, level, offers);\n        frySlot(villager, level, offers);   // §e\n        trustedSlots(villager, player, offers);")

# The 26 tree's ModVillagers is datapack-driven (no BASE_PRICE / sellStackOf), so the insert leans only
# on what all three trees share: baseEmeralds, buyTier, the villager's random, the MerchantOffer ctor.
LEVEL = "villager.getVillagerData().level()" if DIALECT == "26" else "villager.getVillagerData().getLevel()"
COST = ("new ItemStack(net.minecraft.world.item.Items.EMERALD, FRY_EMERALDS)" if DIALECT == "1201"
        else "new net.minecraft.world.item.trading.ItemCost(net.minecraft.world.item.Items.EMERALD, FRY_EMERALDS)")
sub1("registry/ModVillagers.java",
     "    /** §contracts-b1: this fisherman's three posts, for the player who just opened his counter. */",
     '''    /**
     * §e §breeding: a bucket of fry of today's order species, beside the order seat. The seat is found
     * again by its RESULT item, never by position, so a level-up appending behind it changes nothing;
     * it is kept while it still names today's species (its uses are the daily limit) and replaced when
     * the order moves on. A stall too junior for today's species sells no fry, and yesterday's go too.
     */
    private static final int FRY_EMERALDS = 8, FRY_PER_BUCKET = 10;

    private static void frySlot(Villager villager, ServerLevel level, MerchantOffers offers) {
        String order = com.riverfishing.fishing.MarketData.orderOfTheDay(level);
        boolean sells = baseEmeralds(order) > 0 && buyTier(order) <= ''' + LEVEL + ''';
        for (int i = offers.size() - 1; i >= 0; i--) {
            ItemStack r = offers.get(i).getResult();
            if (!(r.getItem() instanceof com.riverfishing.item.FryItem)) continue;
            if (sells && RiverFishing.id(order).equals(com.riverfishing.item.FryItem.species(r))) return;
            offers.remove(i);
        }
        if (!sells) return;
        ItemStack fry = com.riverfishing.item.FryItem.of(RiverFishing.id(order), randomGenome(villager.getRandom()), FRY_PER_BUCKET);
        offers.add(new MerchantOffer(''' + COST + ''', fry, 8, 6, 0.05f));
    }

    /** "Ss Cc Vv Ff"-style: every allele a coin, the strong one written first — shop fry are ordinary fry. */
    private static String randomGenome(net.minecraft.util.RandomSource rng) {
        StringBuilder g = new StringBuilder();
        for (char L : com.riverfishing.fish.Genome.LOCI.toCharArray()) {
            char l = Character.toLowerCase(L);
            int caps = (rng.nextBoolean() ? 1 : 0) + (rng.nextBoolean() ? 1 : 0);
            if (g.length() > 0) g.append(' ');
            g.append(caps > 0 ? L : l).append(caps > 1 ? L : l);
        }
        return g.toString();
    }

    /** §contracts-b1: this fisherman's three posts, for the player who just opened his counter. */''')

# ---------------------------------------------------------------- Contracts: the fry post
sub1("fishing/Contracts.java",
     "            int count = 2 + rng.nextInt(3);          // 2..4\n",
     "            if (slot == 2 && rng.nextBoolean()) {    // §e: the third post is a fry order half the time\n"
     "                fryPost(t, species, rng);\n"
     "                out.add(t);\n"
     "                continue;\n"
     "            }\n"
     "            int count = 2 + rng.nextInt(3);          // 2..4\n")

sub1("fishing/Contracts.java",
     "    /**\n     * The terms, drawn from what the profile says the fish actually likes,",
     '''    /**
     * §e §breeding: a fry order — the fisherman restocks a water and wants 20/30/40 fry of the species,
     * no size bar, no terms: fry have no card to meet them with. Paid per fry at a fifth of a fish,
     * with the set bonus; the reputation of a two-term job, because it takes a tank and a season.
     */
    private static void fryPost(CompoundTag t, String species, Random rng) {
        int n = 20 + 10 * rng.nextInt(3);
        t.putString("Kind", "fry");
        t.putInt("N", n);
        int base = com.riverfishing.registry.ModVillagers.baseEmeralds(species);
        int em = Math.max(1, (int) Math.round(base * n / 5.0 * SET_BONUS));
        t.putInt("Em", em);
        t.putInt("Xp", Math.max(1, em * XP_PER_EMERALD));
        t.putInt("Rep", 2);
    }

    /**
     * The terms, drawn from what the profile says the fish actually likes,''')

sub1("fishing/Contracts.java",
     '''        List<Held> have = held(sp.getInventory(), species, t.getInt("W"), t);
        if (have.size() < n) {
            sp.displayClientMessage(Component.translatable("message.riverfishing.contract_short",
                    have.size(), n, Component.translatable("fish.riverfishing." + species))
                    .withStyle(ChatFormatting.YELLOW), true);
            return true;
        }
        take(sp, have.subList(0, n));
''',
     '''        if (ContractItem.isFry(t)) {   // §e: a fry order is filled out of fry buckets, not fish
            int fry = fryHeld(sp.getInventory(), species);
            if (fry < n) {
                say(sp, "contract_short_fry", ChatFormatting.YELLOW, fry, n, Component.translatable("fish.riverfishing." + species));
                return true;
            }
            takeFry(sp.getInventory(), species, n);
        } else {
            List<Held> have = held(sp.getInventory(), species, t.getInt("W"), t);
            if (have.size() < n) {
                sp.displayClientMessage(Component.translatable("message.riverfishing.contract_short",
                        have.size(), n, Component.translatable("fish.riverfishing." + species))
                        .withStyle(ChatFormatting.YELLOW), true);
                return true;
            }
            take(sp, have.subList(0, n));
        }
''')

sub1("fishing/Contracts.java",
     "    /** The lower-case name the terms use for a rod class. */",
     '''    // ---- §e the fry in the bag ----------------------------------------------------------------------

    /** Fry of this species in the bag, summed over every bucket: one counter for the row and the hand-in. */
    public static int fryHeld(net.minecraft.world.entity.player.Inventory inv, String species) {
        int n = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.getItem() instanceof com.riverfishing.item.FryItem && RiverFishing.id(species).equals(com.riverfishing.item.FryItem.species(s))) {
                n += com.riverfishing.item.FryItem.count(s);
            }
        }
        return n;
    }

    /** Take {@code n} fry out of the buckets, front of the bag first; an emptied bucket goes with them. */
    private static void takeFry(net.minecraft.world.entity.player.Inventory inv, String species, int n) {
        for (int i = 0; i < inv.getContainerSize() && n > 0; i++) {
            ItemStack s = inv.getItem(i);
            if (!(s.getItem() instanceof com.riverfishing.item.FryItem) || !RiverFishing.id(species).equals(com.riverfishing.item.FryItem.species(s))) continue;
            int c = com.riverfishing.item.FryItem.count(s);
            if (c <= n) { inv.setItem(i, ItemStack.EMPTY); n -= c; }
            else { com.riverfishing.item.FryItem.setCount(s, c - n); n = 0; }
        }
    }

    /** The lower-case name the terms use for a rod class. */''')

# ---------------------------------------------------------------- ContractItem: head line and terms
sub1("item/ContractItem.java",
     '''    public static net.minecraft.network.chat.MutableComponent headline(CompoundTag t) {
        return Component.translatable("journal.riverfishing.contract_row", t.getInt("N"),
''',
     '''    public static net.minecraft.network.chat.MutableComponent headline(CompoundTag t) {
        if (isFry(t)) return Component.translatable("journal.riverfishing.contract_fry_row", t.getInt("N"),   // §e
                Component.translatable("fish.riverfishing." + t.getString("Sp")));
        return Component.translatable("journal.riverfishing.contract_row", t.getInt("N"),
''')

sub1("item/ContractItem.java",
     '''        List<net.minecraft.network.chat.MutableComponent> out = new ArrayList<>();
        String water = t.getString("Water"),''',
     '''        List<net.minecraft.network.chat.MutableComponent> out = new ArrayList<>();
        if (isFry(t)) return out;   // §e: fry have no card, so a fry order has no terms
        String water = t.getString("Water"),''')

sub1("item/ContractItem.java",
     "    /** A weight bar the way an angler says it: grams under a kilo, kilos above. */",
     '''    /** §e §breeding: a post for fry ({@code Kind:"fry"}) rather than fish — one reader for every renderer. */
    public static boolean isFry(CompoundTag t) {
        return "fry".equals(t.getString("Kind"));
    }

    /** A weight bar the way an angler says it: grams under a kilo, kilos above. */''')

# ---------------------------------------------------------------- the card
sub1("client/FishCardClientTooltip.java",
     '''        row("contract.from", Component.literal(ContractItem.grams(t.getInt("W"))), WHITE);
''',
     '''        boolean fry = ContractItem.isFry(t);   // §e: no size bar and no terms on a fry order
        if (!fry) row("contract.from", Component.literal(ContractItem.grams(t.getInt("W"))), WHITE);
''')

sub1("client/FishCardClientTooltip.java",
     '''                : com.riverfishing.fishing.Contracts.held(mc.player.getInventory(), sp, t.getInt("W"), t).size();
        row("contract.bag", Component.literal(have + " / " + n), have >= n ? GREEN : YELLOW);''',
     '''                : fry ? com.riverfishing.fishing.Contracts.fryHeld(mc.player.getInventory(), sp)   // §e
                : com.riverfishing.fishing.Contracts.held(mc.player.getInventory(), sp, t.getInt("W"), t).size();
        row(fry ? "contract.bag_fry" : "contract.bag", Component.literal(have + " / " + n), have >= n ? GREEN : YELLOW);''')

# ---------------------------------------------------------------- the board
sub1("client/ContractBoardState.java",
     '''        List<FormattedCharSequence> out = new ArrayList<>();
        out.addAll(font.split(Component.literal(ContractItem.grams(t.getInt("W"))), TEXT_W));''',
     '''        List<FormattedCharSequence> out = new ArrayList<>();
        if (ContractItem.isFry(t)) return out;   // §e: a fry post is its head line and its foot
        out.addAll(font.split(Component.literal(ContractItem.grams(t.getInt("W"))), TEXT_W));''')

sub1("client/ContractBoardState.java",
     '''            g.drawString(font, Component.translatable("journal.riverfishing.contract_short",
                    t.getInt("N"), Component.translatable("fish.riverfishing." + t.getString("Sp"))),''',
     '''            g.drawString(font, ContractItem.isFry(t) ? ContractItem.headline(t)   // §e
                    : Component.translatable("journal.riverfishing.contract_short",
                    t.getInt("N"), Component.translatable("fish.riverfishing." + t.getString("Sp"))),''')

# ---------------------------------------------------------------- the journal
sub1("client/JournalScreen.java",
     '''        if (mc.player == null) return 0;
        return com.riverfishing.fishing.Contracts.held(
                mc.player.getInventory(), terms.getString("Sp"), terms.getInt("W"), terms).size();''',
     '''        if (mc.player == null) return 0;
        if (com.riverfishing.item.ContractItem.isFry(terms)) {   // §e
            return com.riverfishing.fishing.Contracts.fryHeld(mc.player.getInventory(), terms.getString("Sp"));
        }
        return com.riverfishing.fishing.Contracts.held(
                mc.player.getInventory(), terms.getString("Sp"), terms.getInt("W"), terms).size();''')

print("p_e: ok (%s)" % DIALECT)
