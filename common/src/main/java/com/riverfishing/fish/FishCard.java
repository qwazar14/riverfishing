package com.riverfishing.fish;

import com.riverfishing.item.FishItem;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * §journal-card (0.8.0): everything the journal draws about one species, in NBT, built by the SERVER.
 *
 * <p>The journal used to read {@link FishProfileManager} straight from the screen. That works in
 * singleplayer only, because the profiles are a {@code PackType.SERVER_DATA} reload listener — a client
 * on a dedicated server has none of them, so half of every species page silently rendered as nothing:
 * water, bait, tackle, season, trophy weight, level gate, all behind one {@code if (p != null)} that was
 * false. Nobody had reported it because the mod is mostly played singleplayer, and it would have got four
 * times worse the moment this release added four more blocks to the page.
 *
 * <p>So the journal no longer asks the profile manager at all. It asks a card, and the card arrives in
 * the packet the journal already opens with — the same move {@code JournalOpenPacket.withOrder} makes for
 * the order board, and its comment already says why: the server is the only side that has the profiles.
 *
 * <p><b>Ids and numbers, never sentences.</b> Everything here is a bait id, a lang key stem or a float,
 * and the client turns it into words in the client's own language. A server that rendered the text would
 * hand a Russian player an English page.
 */
public final class FishCard {
    /** Fixed orders, so these tables ride as bare float lists instead of eighty string keys per species. */
    public static final String[] WATERS = {"river", "lake", "pond", "swamp", "sea", "puddle"};
    public static final String[] SEASONS = {"spring", "summer", "autumn", "winter"};
    public static final String[] TIMES = {"dawn", "day", "dusk", "night"};

    private final CompoundTag tag;

    private FishCard(CompoundTag tag) {
        this.tag = tag;
    }

    public static FishCard of(CompoundTag tag) {
        return new FishCard(tag == null ? new CompoundTag() : tag);
    }

    /** Server side: the whole table, keyed by species path, ready to ride in the journal packet. */
    public static CompoundTag buildAll() {
        CompoundTag all = new CompoundTag();
        for (FishProfile p : FishProfileManager.get().all()) {
            all.put(p.id.getPath(), build(p));
        }
        return all;
    }

    public static CompoundTag build(FishProfile p) {
        CompoundTag c = new CompoundTag();
        c.putString("g", FishGroup.of(p));
        c.putString("lat", p.latin);   // §cards-2
        c.putInt("wmin", (int) p.weightMin);
        c.putInt("wmax", (int) p.weightMax);
        c.putInt("wmean", (int) p.weightMean);
        c.putInt("lmin", (int) p.lengthMin);
        c.putInt("lmax", (int) p.lengthMax);
        c.putInt("trophy", FishItem.trophyThresholdG(p.weightMin, p.weightMax));
        c.putFloat("gbF", (float) p.gbFraction);
        c.putFloat("gbN", (float) p.gbNutrition);

        // Habitat gates. widthMax is 99999 when unbounded and depthMax 999 — the client prints those as
        // "and up" rather than as a number nobody can act on.
        c.putInt("dmin", p.depthMin);
        c.putInt("dmax", p.depthMax);
        c.putInt("wdmin", (int) Math.round(p.widthMin));
        c.putInt("wdmax", (int) Math.round(Math.min(p.widthMax, 99999)));
        c.putInt("lvl", p.minAnglerLevel);

        c.putFloat("fs", (float) p.fightStrength);
        c.putFloat("fst", (float) p.fightStamina);
        c.putInt("fr", p.fightRuns);
        c.putString("fp", p.fightPattern);

        c.put("water", floats(p.waterBodies, WATERS));
        c.put("season", floats(p.season, SEASONS));
        c.put("time", floats(p.time, TIMES));
        c.put("bio", keyed(p.biomes));
        c.put("bait", keyed(p.baitScores));
        c.put("rod", strings(p.idealRods));
        c.put("rig", strings(p.idealRigs));
        c.putInt("reel", p.reelSize);
        c.putString("line", p.lineType);
        c.putFloat("dia", (float) p.lineDiameter);
        return c;
    }

    private static ListTag floats(Map<String, Double> map, String[] order) {
        ListTag list = new ListTag();
        for (String k : order) list.add(FloatTag.valueOf(map.getOrDefault(k, 0.0).floatValue()));
        return list;
    }

    private static CompoundTag keyed(Map<String, Double> map) {
        CompoundTag t = new CompoundTag();
        for (Map.Entry<String, Double> e : map.entrySet()) t.putFloat(e.getKey(), e.getValue().floatValue());
        return t;
    }

    private static ListTag strings(Iterable<String> values) {
        ListTag list = new ListTag();
        for (String s : values) list.add(StringTag.valueOf(s));
        return list;
    }

    // ---- client side ----

    /** True when the server sent a card at all. An old server, or a species a datapack removed. */
    public boolean present() {
        return !tag.isEmpty();
    }

    public String latin() { return tag.getString("lat"); }

    public String group() {
        return tag.contains("g") ? tag.getString("g") : FishGroup.OTHER;
    }

    public int weightMin() { return tag.getInt("wmin"); }
    public int weightMax() { return tag.getInt("wmax"); }
    public int weightMean() { return tag.getInt("wmean"); }
    public int lengthMin() { return tag.getInt("lmin"); }
    public int lengthMax() { return tag.getInt("lmax"); }
    public int trophyG() { return tag.getInt("trophy"); }
    public float grind() { return tag.getFloat("gbF"); }
    public float richness() { return tag.getFloat("gbN"); }
    public int depthMin() { return tag.getInt("dmin"); }
    public int depthMax() { return tag.getInt("dmax"); }
    public int widthMin() { return tag.getInt("wdmin"); }
    public int widthMax() { return tag.getInt("wdmax"); }
    public int minLevel() { return tag.getInt("lvl"); }
    public float fightStrength() { return tag.getFloat("fs"); }
    public float fightStamina() { return tag.getFloat("fst"); }
    public int fightRuns() { return tag.getInt("fr"); }
    public String fightPattern() { return tag.contains("fp") ? tag.getString("fp") : "steady"; }
    public int reelSize() { return tag.getInt("reel"); }
    public String lineType() { return tag.contains("line") ? tag.getString("line") : "mono"; }
    public float lineDiameter() { return tag.getFloat("dia"); }

    public float[] waters() { return readFloats("water", WATERS.length); }
    public float[] seasons() { return readFloats("season", SEASONS.length); }
    public float[] times() { return readFloats("time", TIMES.length); }

    public List<String> rods() { return readStrings("rod"); }
    public List<String> rigs() { return readStrings("rig"); }

    public Map<String, Float> biomes() { return readKeyed("bio"); }
    public Map<String, Float> baits() { return readKeyed("bait"); }

    /** The named tables sorted best first — what every one of these is actually asked for. */
    public List<Map.Entry<String, Float>> baitsRanked() {
        List<Map.Entry<String, Float>> out = new ArrayList<>(baits().entrySet());
        out.sort((a, b) -> Float.compare(b.getValue(), a.getValue()));
        return out;
    }

    /** Index of the best entry in a fixed-order table, or -1 when the fish has no preference at all. */
    public static int best(float[] table) {
        int at = -1;
        float top = 0f;
        for (int i = 0; i < table.length; i++) {
            if (table[i] > top) {
                top = table[i];
                at = i;
            }
        }
        return at;
    }

    private float[] readFloats(String key, int n) {
        ListTag list = tag.getList(key, Tag.TAG_FLOAT);
        float[] out = new float[n];
        for (int i = 0; i < n && i < list.size(); i++) out[i] = list.getFloat(i);
        return out;
    }

    private List<String> readStrings(String key) {
        ListTag list = tag.getList(key, Tag.TAG_STRING);
        List<String> out = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) out.add(list.getString(i));
        return out;
    }

    private Map<String, Float> readKeyed(String key) {
        CompoundTag t = tag.getCompound(key);
        Map<String, Float> out = new LinkedHashMap<>();
        for (String k : t.getAllKeys()) out.put(k, t.getFloat(k));
        return out;
    }
}
