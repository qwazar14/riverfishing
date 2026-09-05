# -*- coding: utf-8 -*-
"""§finder2: the water sample view's payload, suitability for what the other hand holds, the chunk-bucketed soundings.

    py -X utf8 tools/patches/p_finder2.py <repo root> [1211|1201|26]

Anchor replacement on ONE existing file (fishing/FishingManager.java); every insert carries a "§finder2"
marker so a rerun finds it and does nothing. Exit 1 with the missing anchor when a tree has drifted.
Written in the 1.21.1 dialect; 26.x gets ResourceLocation -> Identifier and the villager/NBT idioms
rewritten by to26 (applied to the anchors too, because earlier streams' inserts already sit in the 26
tree in that dialect). 1.20.1 reads the 1.21.1 text unchanged for everything touched here.

Edited directly (not here): client/FinderScreen, client/FinderState, client/ClientSoundings,
fishing/SoundingData. Lang: tools/patches/lang_finder2.json.

Payload keys added:
  water.clarity (float, BiteContext.clarity), water.sub (early|mid|late), water.groups ("cold;taiga;…")
  here[]/gone[] and suit: wf (water factor), wmin/wmax, bio (biome factor), bgrp (matched group), sf (season
      factor), fit (BiteEngine.environmentScore on habitatContext — the release's number)
  farm.<sp>.spawn / ssub / in (season key, sub key or "", days until the window)
  suit (compound: sp, dmin, dmax, the habitat fields, native, settled) — only when the OTHER hand holds
      fry, roe or a fish
"""
import io, os, re, sys

ROOT = sys.argv[1] if len(sys.argv) > 1 else "."
DIALECT = sys.argv[2] if len(sys.argv) > 2 else "1211"
SRC = os.path.join(ROOT, "common/src/main/java/com/riverfishing")
MARK = "§finder2"


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
    java = re.sub(r"\.getString\(([^()]+)\)", r'.getStringOr(\1, "")', java)
    java = re.sub(r"\.getCompound\(([^()]+)\)", r".getCompoundOrEmpty(\1)", java)
    java = java.replace("ResourceLocation", "Identifier")
    java = java.replace("sp.serverLevel()", "sp.level()")
    return java


def sub1(rel, old, new):
    """Exactly one anchor, replaced once. A tree already carrying the insert (its §finder2 marker, or
    the literal replacement) is left alone — that is what makes a rerun a no-op."""
    path = os.path.join(SRC, rel)
    text = read(path)
    old, new = to26(old), to26(new)
    if new in text:
        return
    if text.count(old) != 1:
        sys.exit("p_finder2: anchor not found once in %s (%d hits):\n%s" % (rel, text.count(old), old))
    write(path, text.replace(old, new))


FM = "fishing/FishingManager.java"

# ---------------------------------------------------------------- the water tag: clarity, sub-season, biome groups
sub1(FM,
     '''        w.putByte("bed", bedType(level, waterPos));
''',
     '''        w.putByte("bed", bedType(level, waterPos));
        // §finder2: the sample view's own lines — how clear the water is (Ecosystem.apply set it), which
        // third of the season it is, and the biome groups the climate gate reads. The strip skips them.
        if (full) {
            w.putFloat("clarity", (float) env.clarity);
            w.putString("sub", com.riverfishing.engine.Calendar.sub(level).name().toLowerCase(java.util.Locale.ROOT));
            w.putString("groups", String.join(";", new java.util.TreeSet<>(env.biomeGroups)));
        }
        // §f §ecosystem: the active effects as lang-key tails; the strip has no room and asks every second.''')

# ---------------------------------------------------------------- the habitat numbers on every species tag
sub1(FM,
     '''        int anglerLevel = JournalData.getLevel(sp);

        ListTag here = new ListTag();
        ListTag gone = new ListTag();''',
     '''        int anglerLevel = JournalData.getLevel(sp);
        // §finder2: the release's own context — no community, day, clear — so "fit" here IS the number
        // a fish thrown back is settled by, not the bite chance this minute.
        BiteContext hab = full ? habitatContext(level, waterPos, body) : null;

        ListTag here = new ListTag();
        ListTag gone = new ListTag();''')

sub1(FM,
     '''            t.putInt("lvl", p.minAnglerLevel);
            if (e <= 1e-4) {''',
     '''            t.putInt("lvl", p.minAnglerLevel);
            if (full) habitatTag(p, env, hab, t);   // §finder2: the gates with their numbers, for the detail panel
            if (e <= 1e-4) {''')

# ---------------------------------------------------------------- the farm ledger: each species' spawning window
sub1(FM,
     '''                f.putBoolean("settled", settled);
                farm.put(s, f);''',
     '''                f.putBoolean("settled", settled);
                // §finder2: when it spawns, and how far off that is — the sample view lists the windows.
                f.putString("spawn", fp.spawnSeason.jsonKey());
                f.putString("ssub", fp.spawnSub == null ? "" : fp.spawnSub.name().toLowerCase(java.util.Locale.ROOT));
                f.putInt("in", com.riverfishing.engine.Calendar.daysUntil(level, fp.spawnSeason, fp.spawnSub));
                farm.put(s, f);''')

# ---------------------------------------------------------------- suitability for what the other hand holds
sub1(FM,
     '''            root.putString("upgrades", String.join(";", WaterUpgrades.at(level, waterPos)));
            root.put("map", soundingMap(level, waterPos));''',
     '''            root.putString("upgrades", String.join(";", WaterUpgrades.at(level, waterPos)));
            // §finder2: fry, roe or a fish in the OTHER hand is priced against this water gate by gate —
            // the question a player holding a bucket of fry at a pond is actually asking. Server-side,
            // because the client has no profiles to read the gates from.
            ItemStack other = isFinder(sp.getMainHandItem()) ? sp.getOffhandItem() : sp.getMainHandItem();
            ResourceLocation held = other.getItem() instanceof com.riverfishing.item.FryItem ? com.riverfishing.item.FryItem.species(other)
                    : other.getItem() instanceof com.riverfishing.item.RoeItem ? com.riverfishing.item.RoeItem.species(other)
                    : other.getItem() instanceof FishItem ? FishItem.getSpecies(other) : null;
            FishProfile hp = held == null ? null : FishProfileManager.get().byId(held);
            if (hp != null) {
                CompoundTag s = new CompoundTag();
                s.putString("sp", hp.id.getPath());
                s.putInt("dmin", hp.depthMin);
                s.putInt("dmax", hp.depthMax);
                habitatTag(hp, env, hab, s);
                s.putBoolean("native", nativeHere(level, waterPos, body, hp.id));
                s.putBoolean("settled", st.isStocked(region, hp.id.getPath()));
                root.put("suit", s);
            }
            root.put("map", soundingMap(level, waterPos));''')

# ---------------------------------------------------------------- the helper, before the barometer read-out
sub1(FM,
     '''    /**
     * The fish finder's barometer read-out (§weather-pressure): pressure in hPa, a trend arrow, and a''',
     '''    /**
     * §finder2: the habitat gates the release prices, as numbers, on a species tag — in the order
     * {@link BiteEngine#environmentScore} asks them: water factor, depth band, width band, the best
     * matching biome group and its factor, the season factor, and the fit itself. The client cannot
     * work any of these out: the profiles are server data.
     */
    private static void habitatTag(FishProfile p, BiteContext env, BiteContext hab, CompoundTag t) {
        t.putFloat("wf", (float) p.waterFactor(env.water));
        t.putFloat("wmin", (float) p.widthMin);
        t.putFloat("wmax", (float) p.widthMax);
        double bio = p.biomes.isEmpty() ? 1.0 : 0.0;
        String hit = "";
        for (var e : p.biomes.entrySet()) {
            if (env.biomeGroups.contains(e.getKey()) && e.getValue() > bio) { bio = e.getValue(); hit = e.getKey(); }
        }
        t.putFloat("bio", (float) bio);
        t.putString("bgrp", hit);
        t.putFloat("sf", (float) p.seasonFactor(env.season));
        t.putFloat("fit", (float) BiteEngine.environmentScore(p, hab));
    }

    /**
     * The fish finder's barometer read-out (§weather-pressure): pressure in hPa, a trend arrow, and a''')

# ---------------------------------------------------------------- soundingMap: the window's chunks, not the world
sub1(FM,
     '''        for (var e : data.depths().entrySet()) {
            int x = SoundingData.keyX(e.getKey()), z = SoundingData.keyZ(e.getKey());
            if (Math.abs(x - centre.getX()) > REACH || Math.abs(z - centre.getZ()) > REACH) continue;
            CompoundTag t = new CompoundTag();
            t.putInt("x", x - centre.getX());
            t.putInt("z", z - centre.getZ());
            t.putInt("d", e.getValue());
            String spot = data.spots().get(e.getKey());
            if (spot != null) t.putString("s", spot);
            out.add(t);
        }''',
     '''        // §finder2: only the chunks the window covers are walked — the store is bucketed by chunk now,
        // so a lake sounded flat no longer costs every column it holds on each screen open and cast.
        data.forEachNear(centre, REACH, (x, z, d, spot) -> {
            CompoundTag t = new CompoundTag();
            t.putInt("x", x - centre.getX());
            t.putInt("z", z - centre.getZ());
            t.putInt("d", d);
            if (spot != null) t.putString("s", spot);
            out.add(t);
        });''')

print("p_finder2: ok (%s)" % DIALECT)
