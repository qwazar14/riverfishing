# -*- coding: utf-8 -*-
"""§breeding stream C: the brood ledger replaces the settle roll.

    py -X utf8 tools/patches/p_c.py <repo root> [1211|1201|26]

Anchor replacement on five existing files; every insert carries a "§c" marker so a rerun finds it and
does nothing. Exit 1 with the missing anchor when a tree has drifted. The Java is written once in the
1.21.1 dialect and rewritten for 26.x by the regexes in to26 (NBT getters, Identifier, overlay
messages, ChunkPos.pack); 1.20.1 reads the 1.21.1 text unchanged for everything touched here.
"""
import io, os, re, sys

ROOT = sys.argv[1] if len(sys.argv) > 1 else "."
DIALECT = sys.argv[2] if len(sys.argv) > 2 else "1211"
SRC = os.path.join(ROOT, "common/src/main/java/com/riverfishing")
MARK = "§c"


def read(path):
    with io.open(path, encoding="utf-8") as f:
        return f.read()


def write(path, text):
    with io.open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write(text)


def to26(java):
    """The 26.x dialect of a 1.21.1 snippet: only the idioms this stream's inserts actually use."""
    if DIALECT != "26":
        return java
    java = re.sub(r"\.getInt\(([^()]+)\)", r".getIntOr(\1, 0)", java)
    java = re.sub(r"\.getLong\(([^()]+)\)", r".getLongOr(\1, 0L)", java)
    java = re.sub(r"\.getDouble\(([^()]+)\)", r".getDoubleOr(\1, 0.0)", java)
    java = re.sub(r"\.getByte\(([^()]+)\)", r".getByteOr(\1, (byte) 0)", java)
    java = re.sub(r"\.getString\(([^()]+)\)", r'.getStringOr(\1, "")', java)
    java = re.sub(r"\.getCompound\(([^()]+)\)", r".getCompoundOrEmpty(\1)", java)
    java = java.replace(".getAllKeys()", ".keySet()")
    java = java.replace("ResourceLocation", "Identifier")
    java = re.sub(r"new ChunkPos\((\w+)\)\.toLong\(\)", r"ChunkPos.pack(\1)", java)
    java = re.sub(r"displayClientMessage\((.+), true\);", r"sendOverlayMessage(\1);", java)
    return java


def patch(rel, old, new, flags=0, mark=None):
    """sub1: exactly one anchor, replaced once. A tree already carrying the insert (found by {@code mark},
    or by the literal replacement itself) is left alone — that is what makes a rerun a no-op."""
    path = os.path.join(SRC, rel)
    text = read(path)
    if (mark or new) in text:
        return
    if flags:
        m = list(re.finditer(old, text, flags))
        if len(m) != 1:
            sys.exit("p_c: anchor not found once in %s (%d hits):\n%s" % (rel, len(m), old))
        text = text[:m[0].start()] + m[0].expand(new) + text[m[0].end():]
    else:
        if text.count(old) != 1:
            sys.exit("p_c: anchor not found once in %s (%d hits):\n%s" % (rel, text.count(old), old))
        text = text.replace(old, new)
    write(path, text)


def done(rel, marker):
    return marker in read(os.path.join(SRC, rel))


# ---------------------------------------------------------------- StockedData: the brood ledger
LEDGER = '''
    // ---- §c §breeding (0.9.0): the brood ledger ------------------------------------------------
    // Settling is no longer a dice roll: a species belongs to a water once a BROOD has lived through
    // one full spawn window there. One compound per region+species, kept as raw NBT because every
    // field is a counter or a string and save/load is then a copy:
    //   F, M     mature ♀ / ♂ released (Card.Size ≥ 2)        Fry    fry released, still fry
    //   Since    world day the settle condition first held    Due    day one full window has passed (0 = unpriced)
    //   Fit      best habitat fit any release measured        Genome last brood/fry genome (ponytail: last writer wins)
    //   Owner    UUID of the releasing player — the nets ask it (§poaching)
    private final Map<String, CompoundTag> brood = new HashMap<>();
    /** Fry short of this settle nothing: a couple of pairs' worth of a real spawn. */
    public static final int FRY_TO_SETTLE = 30;
    /** Water the species cannot really live in fills the ledger but never settles. */
    public static final double FIT_TO_SETTLE = 0.5;

    public static long worldDay(ServerLevel level) {
        return level.getServer().overworld().getDayTime() / 24000L;
    }

    private static String key(long region, String species) {
        return region + "|" + species;
    }

    private CompoundTag entry(long region, String species) {
        return brood.computeIfAbsent(key(region, species), k -> new CompoundTag());
    }

    /** sex: 0 ♀, 1 ♂ (Card.Sex), -1 unknown — an old or villager-bought fish has no card and fills whichever side the pair is missing. */
    public void addBrood(long region, String species, int sex, long day, String genome, java.util.UUID owner) {
        CompoundTag t = entry(region, species);
        String side = sex == 1 || (sex < 0 && t.getInt("M") < t.getInt("F")) ? "M" : "F";
        t.putInt(side, t.getInt(side) + 1);
        stamp(t, day, genome, owner);
    }

    public void addFry(long region, String species, int count, long day, String genome, java.util.UUID owner) {
        CompoundTag t = entry(region, species);
        t.putInt("Fry", t.getInt("Fry") + Math.max(0, count));
        stamp(t, day, genome, owner);
    }

    /** The clock starts the first day the condition holds; a brood that merely grew keeps its date. */
    private void stamp(CompoundTag t, long day, String genome, java.util.UUID owner) {
        if (genome != null && !genome.isEmpty()) t.putString("Genome", genome);
        if (owner != null) t.putString("Owner", owner.toString());
        if (t.getLong("Since") <= 0 && ready(t)) t.putLong("Since", Math.max(1, day));
        setDirty();
    }

    private static boolean ready(CompoundTag t) {
        return Math.min(t.getInt("F"), t.getInt("M")) >= 1 || t.getInt("Fry") >= FRY_TO_SETTLE;
    }

    /** Mature fish of one sex on the ledger: 0 ♀, 1 ♂. */
    public int broodCount(long region, String species, int sex) {
        CompoundTag t = brood.get(key(region, species));
        return t == null ? 0 : t.getInt(sex == 1 ? "M" : "F");
    }

    public int broodPairs(long region, String species) {
        return Math.min(broodCount(region, species, 0), broodCount(region, species, 1));
    }

    public int fryCount(long region, String species) {
        CompoundTag t = brood.get(key(region, species));
        return t == null ? 0 : t.getInt("Fry");
    }

    /** Anything at all on the ledger — the landing hook's cheap first question. */
    public boolean hasBrood(long region, String species) {
        CompoundTag t = brood.get(key(region, species));
        return t != null && (t.getInt("F") + t.getInt("M") + t.getInt("Fry")) > 0;
    }

    /** Net up to {@code n} fry; returns how many actually came out. */
    public int takeFry(long region, String species, int n) {
        int take = Math.min(n, fryCount(region, species));
        if (take > 0) {
            CompoundTag t = entry(region, species);
            t.putInt("Fry", t.getInt("Fry") - take);
            setDirty();
        }
        return take;
    }

    /** The species with the most fry in a region, or null — what a fry net pulls up first. */
    public String richestFry(long region) {
        String best = null;
        int most = 0;
        String prefix = region + "|";
        for (Map.Entry<String, CompoundTag> e : brood.entrySet()) {
            if (e.getKey().startsWith(prefix) && e.getValue().getInt("Fry") > most) {
                most = e.getValue().getInt("Fry");
                best = e.getKey().substring(prefix.length());
            }
        }
        return best;
    }

    /** The population's genome — the last brood or fry released; "" when nobody stocked it. */
    public String genome(long region, String species) {
        CompoundTag t = brood.get(key(region, species));
        return t == null ? "" : t.getString("Genome");
    }

    /** Who stocked this water, or null: natives and seed communities have no owner. */
    public java.util.UUID owner(long region, String species) {
        CompoundTag t = brood.get(key(region, species));
        String s = t == null ? "" : t.getString("Owner");
        return s.isEmpty() ? null : java.util.UUID.fromString(s);
    }

    /** The water's fit for the species as the release measured it; the best of several releases counts. */
    public void noteFit(long region, String species, double fit) {
        CompoundTag t = entry(region, species);
        if (fit > t.getDouble("Fit")) {
            t.putDouble("Fit", fit);
            setDirty();
        }
    }

    public void clearBrood(long region, String species) {
        if (brood.remove(key(region, species)) != null) setDirty();
    }

    /**
     * A landed fish of an unsettled species came out of the brood — it is the only population there is.
     * Adults go first, from the side with the surplus (a pair holds as long as it can), then fry ten
     * at a time. Returns true when the ledger just emptied; a brood that drops below the settle
     * condition loses its clock and starts over when it is complete again.
     */
    public boolean catchFromBrood(long region, String species) {
        CompoundTag t = brood.get(key(region, species));
        if (t == null) return false;
        int f = t.getInt("F"), m = t.getInt("M");
        if (f + m > 0) t.putInt(f >= m ? "F" : "M", (f >= m ? f : m) - 1);
        else t.putInt("Fry", Math.max(0, t.getInt("Fry") - 10));
        if (!ready(t)) {
            t.remove("Since");
            t.remove("Due");
        }
        setDirty();
        if (hasBrood(region, species)) return false;
        clearBrood(region, species);
        return true;
    }

    /** Days until the brood settles as tickSettle last priced it; -1 when nothing is on the clock. */
    public int daysToSettle(long region, String species, long today) {
        CompoundTag t = brood.get(key(region, species));
        return t == null || t.getLong("Due") <= 0 ? -1 : (int) Math.max(0, t.getLong("Due") - today);
    }

    /**
     * The settle check, run at every release and every landing of the species in the region (there
     * is no world ticker; the water comes of age the next time somebody touches it). Settles when the
     * condition holds — a pair, or {@link #FRY_TO_SETTLE} fry — the water fits ({@link #FIT_TO_SETTLE})
     * and one FULL spawn window of the species has passed since the condition first held. Returns
     * true on the tick that settled it.
     */
    public boolean tickSettle(ServerLevel level, long region, String species, com.riverfishing.fish.FishProfile p) {
        if (isStocked(region, species)) return false;
        CompoundTag t = brood.get(key(region, species));
        if (t == null || !ready(t) || t.getDouble("Fit") < FIT_TO_SETTLE) return false;
        long today = worldDay(level);
        if (t.getLong("Due") <= 0) {
            // Priced ONCE, when the brood is complete: the next whole window, start to end. A window the
            // fish entered halfway does not count — a spawn is a season's work, not a weekend's — so
            // inside one the clock runs to the window after it.
            int len = p.spawnSub == null ? com.riverfishing.engine.Calendar.SEASON_DAYS : com.riverfishing.engine.Calendar.SUB_DAYS;
            long due;
            if (com.riverfishing.engine.Calendar.inWindow(level, p)) {
                // Calendar.daysUntil's arithmetic asked the other way round: how far INTO the window are we.
                int start = p.spawnSeason.ordinal() * com.riverfishing.engine.Calendar.SEASON_DAYS
                        + (p.spawnSub == null ? 0 : p.spawnSub.ordinal() * com.riverfishing.engine.Calendar.SUB_DAYS);
                int into = Math.floorMod(com.riverfishing.engine.Calendar.dayOfYear(level) - start, com.riverfishing.engine.Calendar.YEAR_DAYS);
                due = today + (into == 0 ? 0 : com.riverfishing.engine.Calendar.YEAR_DAYS - into) + len;
            } else {
                due = today + com.riverfishing.engine.Calendar.daysUntil(level, p.spawnSeason, p.spawnSub) + len;
            }
            t.putLong("Due", due);
            setDirty();
        }
        if (today < t.getLong("Due")) return false;
        markStocked(region, species);
        // The brood IS the population now: pairs and fry grew into it. Genome and owner stay — the
        // nets ask who stocked a water long after it settled.
        for (String k : new String[]{"F", "M", "Fry", "Since", "Due"}) t.remove(k);
        setDirty();
        return true;
    }

    private CompoundTag saveBrood() {
        CompoundTag out = new CompoundTag();
        for (Map.Entry<String, CompoundTag> e : brood.entrySet()) out.put(e.getKey(), e.getValue().copy());
        return out;
    }

    private void loadBrood(CompoundTag in) {
        for (String k : in.getAllKeys()) brood.put(k, in.getCompound(k).copy());
    }

'''

patch("fishing/StockedData.java",
      "    /** §cull: is this species banned from this region? */",
      to26(LEDGER) + "    /** §cull: is this species banned from this region? */")
patch("fishing/StockedData.java",
      '        tag.put("Culled", out);\n        return tag;',
      '        tag.put("Culled", out);\n        tag.put("Brood", saveBrood());   // §c\n        return tag;')
patch("fishing/StockedData.java",
      '            d.culled.put(Long.parseLong(key), s);\n        }\n        return d;',
      '            d.culled.put(Long.parseLong(key), s);\n        }\n'
      + to26('        d.loadBrood(tag.getCompound("Brood"));   // §c\n') + '        return d;')

# ---------------------------------------------------------------- FishingManager: releaseFish / releaseFry
RELEASE = '''    /**
     * §c §breeding (0.9.0): a fish RELEASED into water. No dice any more — the species settles when a
     * BROOD lives through one spawn window here (StockedData.tickSettle): a ♀ and a ♂ of breeding size,
     * or thirty-odd fry, in water that fits it. Hostile water (fit ≤ 0) refuses the fish outright;
     * everywhere else the fish banks its weight units as the temporary population it always did, and a
     * mature one (Card.Size ≥ 2 — or, with no card, half the species mean) goes on the ledger with its
     * sex and genes. Natives and settled species skip the ledger — there is nothing left to settle.
     */
    public static void releaseFish(ServerLevel level, BlockPos pos, ResourceLocation species,
                                   int weightG, int count, @org.jetbrains.annotations.Nullable CompoundTag card,
                                   @org.jetbrains.annotations.Nullable ServerPlayer thrower) {
        FishProfile p = FishProfileManager.get().byId(species);
        if (p == null) return;
        // §stock-units (0.5.1): SUPERLINEAR in size — 0.5·(w/mean)^1.5. A mean fish is half a unit
        // (a native pond needs ~17 of them for the full 250%), a double-mean trophy ~1.4 units
        // (~6 trophies), fry a rounding error. Packing a water stays real work.
        double sizeRatio = weightG / Math.max(1.0, p.weightMean);
        double units = 0.5 * Math.pow(Mth.clamp(sizeRatio, 0.0, 3.0), 1.5) * Math.max(1, count);
        boolean mature = card != null ? card.getByte("Size") >= 2 : sizeRatio >= 0.5;
        int sex = card == null ? -1 : card.getByte("Sex");
        String genes = card == null ? "" : card.getString("Genes");
        release(level, pos, p, units, thrower, (stocked, region) -> {
            if (!mature) return;
            long day = StockedData.worldDay(level);
            for (int i = 0; i < Math.max(1, count); i++) {
                stocked.addBrood(region, species.getPath(), sex, day, genes, thrower == null ? null : thrower.getUUID());
            }
        });
    }

    /** §c §breeding: a FryItem thrown into water — fry on the ledger, a sliver of stock each (fry disperse and die). */
    public static void releaseFry(ServerLevel level, BlockPos pos, ResourceLocation species, String genome, int count,
                                  @org.jetbrains.annotations.Nullable ServerPlayer thrower) {
        FishProfile p = FishProfileManager.get().byId(species);
        if (p == null || count <= 0) return;
        release(level, pos, p, count * 0.02, thrower, (stocked, region) ->
                stocked.addFry(region, species.getPath(), count, StockedData.worldDay(level), genome,
                        thrower == null ? null : thrower.getUUID()));
    }

    /**
     * The one release path: find the water, judge it, bank the stock, write the ledger, run the settle
     * clock, and tell the angler where things stand — as a checklist, not a percentage. {@code ledger}
     * runs only for a species that is neither native nor settled here.
     */
    private static void release(ServerLevel level, BlockPos pos, FishProfile p, double units,
                                @org.jetbrains.annotations.Nullable ServerPlayer thrower,
                                java.util.function.ObjLongConsumer<StockedData> ledger) {
        // A floating item sits in the AIR block above the surface — resolve to the actual water.
        if (!level.getFluidState(pos).is(net.minecraft.tags.FluidTags.WATER)) {
            if (level.getFluidState(pos.below()).is(net.minecraft.tags.FluidTags.WATER)) pos = pos.below();
        }
        WaterBody body = WaterBodyCache.forLevel(level).get(level, pos);
        if (body.type() == WaterType.NONE) return;
        String id = p.id.getPath();
        long region = StockedData.region(pos);
        long chunk = new ChunkPos(pos).toLong();
        long now = level.getGameTime();
        net.minecraft.network.chat.Component name = fishName(p.id);
        double fit = BiteEngine.environmentScore(p, habitatContext(level, pos, body));
        if (fit <= 0) {
            // §residency-guard: water the species cannot live in at all takes nothing — no ledger, no stock.
            if (thrower != null) thrower.displayClientMessage(Component.translatable("message.riverfishing.stock_hostile", name).withStyle(ChatFormatting.RED), true);
            return;
        }
        boolean nativeHere = nativeHere(level, pos, body, p.id);
        StockedData stocked = StockedData.get(level);
        boolean resident = nativeHere || stocked.isStocked(region, id);
        FishingPressureData pressure = FishingPressureData.get(level);
        // §residency: how deep the bank goes depends on the species' standing HERE —
        // native 250%, settled transplant 150%, an unsettled one builds a 0..100% temp population.
        pressure.addStock(chunk, id, now, units, nativeHere ? FishingPressureData.FLOOR_NATIVE
                : resident ? FishingPressureData.FLOOR_SETTLED : FishingPressureData.FLOOR_TRANSPLANT);
        boolean settledNow = false;
        if (!resident) {
            ledger.accept(stocked, region);
            stocked.noteFit(region, id, fit);
            settledNow = stocked.tickSettle(level, region, id, p);
        }
        if (thrower == null) return;
        String fitText = String.format(java.util.Locale.ROOT, "%.1f", fit);
        Component msg;
        if (settledNow) {
            msg = Component.translatable("message.riverfishing.stock_settled", name).withStyle(ChatFormatting.GREEN);
        } else if (resident) {
            msg = Component.translatable("message.riverfishing.stocked", name, pressure.stockPercent(chunk, id, now)).withStyle(ChatFormatting.AQUA);
        } else if (fit < StockedData.FIT_TO_SETTLE) {
            msg = Component.translatable("message.riverfishing.stock_unfit", name, fitText).withStyle(ChatFormatting.GRAY);
        } else {
            int days = stocked.daysToSettle(region, id, StockedData.worldDay(level));
            msg = Component.translatable(days < 0 ? "message.riverfishing.stock_waiting" : "message.riverfishing.stock_checklist",
                    name, stocked.broodCount(region, id, 0), stocked.broodCount(region, id, 1), stocked.fryCount(region, id),
                    fitText, Math.max(0, days)).withStyle(ChatFormatting.AQUA);
        }
        thrower.displayClientMessage(msg, true);
    }

    /**
     * §c §breeding: the landing's word on the ledger. First the settle check — a brood that has lived
     * through its window comes of age the next time anyone fishes the water, nobody has to throw a fish
     * in to wake it. Then the bill: an unsettled species IS its brood, so every fish landed comes off
     * the ledger, and fishing the last one out before the window closes ends the attempt.
     */
    private static void broodAfterCatch(ServerLevel level, ServerPlayer sp, BlockPos pos, ResourceLocation species) {
        FishProfile p = FishProfileManager.get().byId(species);
        if (p == null) return;
        String id = species.getPath();
        long region = StockedData.region(pos);
        StockedData stocked = StockedData.get(level);
        if (stocked.isStocked(region, id) || !stocked.hasBrood(region, id)) return;
        if (stocked.tickSettle(level, region, id, p)) {
            sp.displayClientMessage(Component.translatable("message.riverfishing.stock_settled", fishName(species)).withStyle(ChatFormatting.GREEN), true);
        } else if (stocked.catchFromBrood(region, id)) {
            sp.displayClientMessage(Component.translatable("message.riverfishing.stock_brood_lost", fishName(species)).withStyle(ChatFormatting.RED), true);
        }
    }

'''

if not done("fishing/FishingManager.java", "§c §breeding (0.9.0): a fish RELEASED"):
    path = os.path.join(SRC, "fishing/FishingManager.java")
    text = read(path)
    # The whole old method, javadoc included, from its §stocking 2.0 header to the javadoc of the
    # method after it — replaced as a block so no line of the roll survives.
    start = text.find("    /**\n     * §stocking 2.0: a fish RELEASED into water.")
    end = text.find("    /**\n     * §stocking: the context to ask")
    if start < 0 or end < 0 or end < start:
        sys.exit("p_c: releaseFish block not found in FishingManager.java")
    text = text[:start] + to26(RELEASE) + text[end:]
    write(path, text)

patch("fishing/FishingManager.java",
      r"(        FishingPressureData\.get\(level\)\.addCatch\((?:new ChunkPos\(session\.target\)\.toLong\(\)|ChunkPos\.pack\(session\.target\)),\n"
      r"                session\.species\.getPath\(\), level\.getGameTime\(\)\);\n)",
      r"\1        broodAfterCatch(level, sp, session.target, session.species);   // §c\n", re.M,
      mark="broodAfterCatch(level, sp, session.target, session.species);   // §c")

# ---------------------------------------------------------------- FishItem: fry take the fish's road into the water
patch("item/FishItem.java",
      r"(ResourceLocation|Identifier) released = getSpecies\(stack\);\n(\s+)if \(released != null\) \{",
      r"if (stack.getItem() instanceof com.riverfishing.item.FryItem) {   // §c: fry take the same road in\n"
      r"\2    com.riverfishing.fishing.FishingManager.releaseFry(sl, entity.blockPosition(),\n"
      r"\2            com.riverfishing.item.FryItem.species(stack), com.riverfishing.item.FryItem.genome(stack),\n"
      r"\2            com.riverfishing.item.FryItem.count(stack), thrower);\n"
      r"\2}\n"
      r"\2\1 released = stack.getItem() instanceof FishItem ? getSpecies(stack) : null;\n"
      r"\2if (released != null) {", re.M, mark="// §c: fry take the same road in")
patch("item/FishItem.java",
      r"released, getWeightG\(stack\), stack\.getCount\(\),\s*thrower\);",
      "released, getWeightG(stack), stack.getCount(),\n"
      "                                com.riverfishing.fish.CatchCard.has(stack) ? com.riverfishing.fish.CatchCard.of(stack) : null,   // §c\n"
      "                                thrower);", re.M, mark="CatchCard.of(stack) : null,   // §c")

patch("mixin/ItemEntityMixin.java",
      "        if (stack.getItem() instanceof FishItem && FishItem.koiReleaseTick(stack, self)) {",
      "        // §c: a FryItem is let go the same way — koiReleaseTick branches on the item.\n"
      "        if ((stack.getItem() instanceof FishItem || stack.getItem() instanceof com.riverfishing.item.FryItem)\n"
      "                && FishItem.koiReleaseTick(stack, self)) {")

# ---------------------------------------------------------------- BaitTrapBlockEntity: the fry net
patch("block/BaitTrapBlockEntity.java",
      "    void collect(Player player) {\n        if (stored <= 0 && fishes.isEmpty()) {\n            player.",
      "    void collect(Player player) {\n"
      "        // §c §breeding: the second job — fry of a brood the ledger says swims here, handed over first\n"
      "        // so a trap with nothing else in it still gives them.\n"
      "        ItemStack fry = level instanceof ServerLevel sl ? netFry(sl) : ItemStack.EMPTY;\n"
      "        if (!fry.isEmpty() && !player.getInventory().add(fry)) player.drop(fry, false);\n"
      "        if (stored <= 0 && fishes.isEmpty()) {\n"
      "            if (!fry.isEmpty()) return;\n"
      "            player.")
patch("block/BaitTrapBlockEntity.java",
      "    private BlockPos waterAt(ServerLevel level) {",
      "    /** §c: up to 10 fry of whichever brood has the most in this region, off the ledger, with the population genome. */\n"
      "    private ItemStack netFry(ServerLevel server) {\n"
      "        BlockPos waterPos = waterAt(server);\n"
      "        if (waterPos == null) return ItemStack.EMPTY;\n"
      "        var stocked = com.riverfishing.fishing.StockedData.get(server);\n"
      "        long region = com.riverfishing.fishing.StockedData.region(waterPos);\n"
      "        String species = stocked.richestFry(region);\n"
      "        int n = species == null ? 0 : stocked.takeFry(region, species, 10);\n"
      "        if (n <= 0) return ItemStack.EMPTY;\n"
      "        return com.riverfishing.item.FryItem.of(com.riverfishing.RiverFishing.id(species), stocked.genome(region, species), n);\n"
      "    }\n\n"
      "    private BlockPos waterAt(ServerLevel level) {")

print("p_c: ok (%s)" % DIALECT)
