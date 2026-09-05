# -*- coding: utf-8 -*-
"""§breeding stream K: the pond as a farm — natural growth, the keepnet bulk sale, the farm lines on the finder.

    py -X utf8 tools/patches/p_k.py <repo root> [1211|1201|26]

Anchor replacement on four existing files (StockedData, FishingManager, ModEvents, FinderScreen); every
insert carries a "§k" marker so a rerun finds it and does nothing. Exit 1 with the missing anchor when a
tree has drifted. Written in the 1.21.1 dialect; 26.x gets the NBT getters, the villager package and the
message calls rewritten by to26 — applied to the ANCHORS too, because earlier streams' inserts already sit
in the 26 tree in that dialect. 1.20.1 reads the 1.21.1 text unchanged for everything touched here.

New file (in the tree already, not written here): fishing/KeepnetSale.java. Lang: tools/patches/lang_k.json.
"""
import io, os, re, sys

ROOT = sys.argv[1] if len(sys.argv) > 1 else "."
DIALECT = sys.argv[2] if len(sys.argv) > 2 else "1211"
SRC = os.path.join(ROOT, "common/src/main/java/com/riverfishing")
MARK = "§k"


def read(path):
    with io.open(path, encoding="utf-8") as f:
        return f.read()


def write(path, text):
    with io.open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write(text)


def to26(java):
    """The 26.x dialect of a 1.21.1 snippet: only the idioms this stream's text actually uses."""
    if DIALECT != "26":
        return java
    java = re.sub(r"\.getInt\(([^()]+)\)", r".getIntOr(\1, 0)", java)
    java = re.sub(r"\.getLong\(([^()]+)\)", r".getLongOr(\1, 0L)", java)
    java = re.sub(r"\.getBoolean\(([^()]+)\)", r".getBooleanOr(\1, false)", java)
    java = re.sub(r"\.getString\(([^()]+)\)", r'.getStringOr(\1, "")', java)
    java = re.sub(r"\.getCompound\(([^()]+)\)", r".getCompoundOrEmpty(\1)", java)
    java = java.replace(".getAllKeys()", ".keySet()")
    java = java.replace("sp.serverLevel()", "sp.level()")
    java = java.replace("net.minecraft.world.entity.npc.Villager", "net.minecraft.world.entity.npc.villager.Villager")
    java = re.sub(r"new ChunkPos\((\w+)\)\.toLong\(\)", r"ChunkPos.pack(\1)", java)
    java = re.sub(r"new net\.minecraft\.world\.level\.ChunkPos\((\w+)\)\.toLong\(\)", r"net.minecraft.world.level.ChunkPos.pack(\1)", java)
    java = re.sub(r"displayClientMessage\((.+?), true\);", r"sendOverlayMessage(\1);", java, flags=re.S)
    return java


def sub1(rel, old, new):
    """Exactly one anchor, replaced once. A tree already carrying the insert (its §k marker, or the
    literal replacement) is left alone — that is what makes a rerun a no-op."""
    path = os.path.join(SRC, rel)
    text = read(path)
    old, new = to26(old), to26(new)
    if new in text:
        return
    if text.count(old) != 1:
        sys.exit("p_k: anchor not found once in %s (%d hits):\n%s" % (rel, text.count(old), old))
    write(path, text.replace(old, new))


# ---------------------------------------------------------------- StockedData: the farm's ledger
SD = "fishing/StockedData.java"

# A settled pond KEEPS its pairs: they are the farm's brood from now on. Only the clocks and the fry go.
sub1(SD,
     '''        for (String k : new String[]{"F", "M", "Fry", "Since", "Due"}) t.remove(k);
        setDirty();
        return true;
    }''',
     '''        // §k §farm: the pairs stay — a settled pond's brood is what grows it (growIfDue); fry became fish.
        for (String k : new String[]{"Fry", "Since", "Due"}) t.remove(k);
        setDirty();
        return true;
    }

    // ---- §k §breeding (0.9.0): the pond as a farm ----------------------------------------------
    // A settled species with a pair on the ledger grows by itself once a year: every time its spawn
    // window CLOSES, the chunk of the last release (Pos) banks 3 units per pair, more for fertile stock
    // and for a bank with cover and oxygen. LastGrow is the world day of the last close paid out, so
    // however many times the water is touched, a window pays once. There is no world ticker: release,
    // landing and a once-a-minute player tick (ModEvents) all ask growIfDue.
    /** The last release spot — the chunk the growth lands in, and where "you are near your pond" is measured. */
    public void notePos(long region, String species, BlockPos pos) {
        entry(region, species).putLong("Pos", pos.asLong());
        setDirty();
    }

    /** Species on the farm here: settled, or with a brood in the making. */
    public Set<String> farmSpecies(long region) {
        Set<String> out = new HashSet<>();
        Set<String> s = regions.get(region);
        if (s != null) out.addAll(s);
        String prefix = region + "|";
        for (String k : brood.keySet()) if (k.startsWith(prefix)) out.add(k.substring(prefix.length()));
        return out;
    }

    /** Days since the species' window last closed, 0 on the closing day. Calendar arithmetic, like Due. */
    private static int sinceClose(ServerLevel level, com.riverfishing.fish.FishProfile p) {
        int start = p.spawnSeason.ordinal() * com.riverfishing.engine.Calendar.SEASON_DAYS
                + (p.spawnSub == null ? 0 : p.spawnSub.ordinal() * com.riverfishing.engine.Calendar.SUB_DAYS);
        int len = p.spawnSub == null ? com.riverfishing.engine.Calendar.SEASON_DAYS : com.riverfishing.engine.Calendar.SUB_DAYS;
        return Math.floorMod(com.riverfishing.engine.Calendar.dayOfYear(level) - (start + len), com.riverfishing.engine.Calendar.YEAR_DAYS);
    }

    /** Days until the window next closes, 1..96 — the farm view's "grows in". */
    public static int daysToGrow(ServerLevel level, com.riverfishing.fish.FishProfile p) {
        return com.riverfishing.engine.Calendar.YEAR_DAYS - sinceClose(level, p);
    }

    /** Every farm species in the region the position is in — the per-player tick's call. */
    public void growAround(ServerLevel level, BlockPos pos) {
        long region = region(pos);
        for (String s : farmSpecies(region)) growIfDue(level, region, s);
    }

    public void growIfDue(ServerLevel level, long region, String species) {
        if (!isStocked(region, species)) return;
        CompoundTag t = brood.get(key(region, species));
        if (t == null || !t.contains("Pos")) return;
        com.riverfishing.fish.FishProfile p = com.riverfishing.fish.FishProfileManager.get().byId(com.riverfishing.RiverFishing.id(species));
        if (p == null) return;
        long today = worldDay(level);
        long lastClose = today - sinceClose(level, p);
        long last = t.getLong("LastGrow");
        if (last <= 0) {
            // The clock starts the first time the water is looked at as a farm — no back pay for the
            // years before, and the settle day itself (Due IS a closing day) does not pay either.
            t.putLong("LastGrow", today);
            setDirty();
            return;
        }
        if (lastClose <= last) return;
        // Windows closed in (last, lastClose]: one per year, rounded up because the first close after
        // an arbitrary start day counts. ponytail: capped at 3 — a pond nobody visited for a decade
        // pays three years, not ten; and with a Serene Seasons year longer than 96 the count is off the
        // way Calendar.daysUntil is (see its note).
        int windows = (int) Math.min(3, (lastClose - last + com.riverfishing.engine.Calendar.YEAR_DAYS - 1) / com.riverfishing.engine.Calendar.YEAR_DAYS);
        t.putLong("LastGrow", lastClose);
        setDirty();
        int pairs = broodPairs(region, species);
        if (pairs <= 0) return;
        BlockPos pos = BlockPos.of(t.getLong("Pos"));
        double units = windows * 3.0 * pairs * (1.0 + 0.5 * shares(region, species)[3])
                * (1.0 + Ecosystem.frySurvival(level, pos));
        FishingPressureData pd = FishingPressureData.get(level);
        long chunk = new net.minecraft.world.level.ChunkPos(pos).toLong();
        long now = level.getGameTime();
        pd.addStock(chunk, species, now, units, FishingPressureData.FLOOR_SETTLED);
        net.minecraft.network.chat.Component msg = net.minecraft.network.chat.Component.translatable(
                "message.riverfishing.pond_grew", net.minecraft.network.chat.Component.translatable("fish.riverfishing." + species),
                pd.stockPercent(chunk, species, now)).withStyle(net.minecraft.ChatFormatting.AQUA);
        for (net.minecraft.server.level.ServerPlayer sp : level.players()) {
            if (sp.blockPosition().closerThan(pos, 64)) sp.displayClientMessage(msg, true);
        }
    }''')

# ---------------------------------------------------------------- FishingManager: release, landing, the finder
FM = "fishing/FishingManager.java"

# A settled (not native) species keeps writing its ledger: the farm's brood, and the spot growth lands in.
sub1(FM,
     '''        if (!resident) {
            ledger.accept(stocked, region);
            stocked.noteFit(region, id, fit);
            settledNow = stocked.tickSettle(level, region, id, p);
        }''',
     '''        if (!nativeHere) {   // §k §farm: a settled pond keeps its ledger — every mature fish put in is brood
            ledger.accept(stocked, region);
            stocked.noteFit(region, id, fit);
            stocked.notePos(region, id, pos);
            settledNow = stocked.tickSettle(level, region, id, p);
        }
        stocked.growIfDue(level, region, id);   // §k: a window that closed since anyone last looked pays out now''')

sub1(FM,
     '''        StockedData stocked = StockedData.get(level);
        if (stocked.isStocked(region, id) || !stocked.hasBrood(region, id)) return;''',
     '''        StockedData stocked = StockedData.get(level);
        stocked.growIfDue(level, region, id);   // §k §farm: a landing is a touch of the water too
        if (stocked.isStocked(region, id) || !stocked.hasBrood(region, id)) return;''')

sub1(FM,
     '''            root.put("map", soundingMap(level, waterPos));''',
     '''            // §k §farm: every species on the ledger here — settled, or still settling — with its brood, its
            // genome and when it next pays; and the bank's upgrades. The client has no ledger to ask.
            CompoundTag farm = new CompoundTag();
            StockedData st = StockedData.get(level);
            long region = StockedData.region(waterPos);
            for (String s : st.farmSpecies(region)) {
                FishProfile fp = FishProfileManager.get().byId(com.riverfishing.RiverFishing.id(s));
                if (fp == null) continue;
                boolean settled = st.isStocked(region, s);
                CompoundTag f = new CompoundTag();
                f.putInt("stock", settled ? stock.stockPercent(chunk, s, level.getGameTime())
                        : (int) Math.round(stock.surplusAround(waterPos.getX() >> 4, waterPos.getZ() >> 4, s, level.getGameTime()) * 100));
                f.putInt("f", st.broodCount(region, s, 0));
                f.putInt("m", st.broodCount(region, s, 1));
                f.putInt("fry", st.fryCount(region, s));
                f.putString("genome", st.genome(region, s));
                f.putInt("grow", settled ? StockedData.daysToGrow(level, fp) : st.daysToSettle(region, s, StockedData.worldDay(level)));
                f.putBoolean("settled", settled);
                farm.put(s, f);
            }
            root.put("farm", farm);
            root.putString("upgrades", String.join(";", WaterUpgrades.at(level, waterPos)));
            root.put("map", soundingMap(level, waterPos));''')

# ---------------------------------------------------------------- ModEvents: the tick and the counter
ME = "event/ModEvents.java"

sub1(ME,
     '''                FishingManager.finderHudTick(sp); // §finder-hud: the strip, while one is held''',
     '''                FishingManager.finderHudTick(sp); // §finder-hud: the strip, while one is held
                // §k §farm: once a minute, the region the player stands in pays out any spawn window that closed.
                if (sp.tickCount % 1200 == 0) com.riverfishing.fishing.StockedData.get(sp.serverLevel()).growAround(sp.serverLevel(), sp.blockPosition());''')

sub1(ME,
     '''        // §e §breeding: roe is sold the way a contract is handed in''',
     '''        // §k §farm: a keepnet held out to the fisherman sells everything in it that any fisherman buys —
        // prime at the market price, carded at half, netted (no card) at a third. Before the roe block so
        // the net is never mistaken for an ordinary right-click.
        dev.architectury.event.events.common.InteractionEvent.INTERACT_ENTITY.register((player, entity, hand) -> {
            if (!(entity instanceof net.minecraft.world.entity.npc.Villager v)) return EventResult.pass();
            if (!(player.getItemInHand(hand).getItem() instanceof com.riverfishing.item.KeepnetItem)) return EventResult.pass();
            if (v.getVillagerData().getProfession() != com.riverfishing.registry.ModVillagers.FISHERMAN.get()) return EventResult.pass();
            if (player instanceof ServerPlayer sp) com.riverfishing.fishing.KeepnetSale.sell(sp, player.getItemInHand(hand));
            return EventResult.interruptTrue();
        });

        // §e §breeding: roe is sold the way a contract is handed in''')

# ---------------------------------------------------------------- FinderScreen: the farm lines in the list
# Not a tab: the screen has a section/chart toggle, not a tab strip, and a third view would be its own
# renderer, click map and state. The list already carries heading rows (the ecosystem lines), so the
# farm goes there under its own heading — the fallback the contract names.
sub1("client/FinderScreen.java",
     '''        out.add(new Row(null, false, Component.translatable("finder.riverfishing.biting", here.size())));''',
     '''        // §k §farm: the ledger for this water — one line a species, then what stands on the bank.
        CompoundTag farm = data.getCompound("farm");
        if (!farm.isEmpty()) {
            out.add(new Row(null, false, Component.translatable("finder.riverfishing.farm")));
            for (String s : farm.getAllKeys()) {
                CompoundTag f = farm.getCompound(s);
                String line = Component.translatable(f.getBoolean("settled") ? "finder.riverfishing.farm_row" : "finder.riverfishing.farm_row_new",
                        fishName(s), f.getInt("stock"), f.getInt("f"), f.getInt("m"), f.getInt("fry"),
                        f.getString("genome"), Math.max(0, f.getInt("grow"))).getString();
                out.add(new Row(null, false, Component.literal(this.font.plainSubstrByWidth(line, LIST_W))));
            }
            String up = data.getString("upgrades");
            if (!up.isEmpty()) {
                List<String> names = new ArrayList<>();
                for (String k : up.split(";")) {
                    String block = k.equals("snags") ? "snag_pile" : k.equals("gravel") ? "gravel_bed" : k;
                    names.add(Component.translatable("block.riverfishing." + block).getString());
                }
                String line = Component.translatable("finder.riverfishing.farm_upgrades", String.join(", ", names)).getString();
                out.add(new Row(null, false, Component.literal(this.font.plainSubstrByWidth(line, LIST_W))));
            }
        }
        out.add(new Row(null, false, Component.translatable("finder.riverfishing.biting", here.size())));''')

print("p_k: ok (%s)" % DIALECT)
