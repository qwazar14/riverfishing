package com.riverfishing.block;

import com.riverfishing.engine.Calendar;
import com.riverfishing.fish.CatchCard;
import com.riverfishing.fish.FishProfile;
import com.riverfishing.fish.FishProfileManager;
import com.riverfishing.fish.Genome;
import com.riverfishing.item.BaitItem;
import com.riverfishing.item.FishItem;
import com.riverfishing.item.FishMealItem;
import com.riverfishing.item.FishOilItem;
import com.riverfishing.item.FryItem;
import com.riverfishing.item.GroundbaitItem;
import com.riverfishing.item.RoeItem;
import com.riverfishing.registry.ModBlocks;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import java.util.Random;

/**
 * §breeding (0.9.0), §aq-b (the window): the rules that make the display aquarium a live tank.
 *
 * <p>Kept beside the block entity rather than inside it so the entity stays what it is — twelve slots
 * and a few counters — and the rules read top to bottom in one place. The counters are
 * package-private fields on the entity and this class is the only thing that writes them; the window
 * (menu/AquariumMenu) only reads, through {@link AquariumBlockEntity#data()}, which this fills once a
 * second.
 *
 * <p>Days are world time ({@code level.getDayTime()}): a run remembers WHEN it started and progress is
 * the clock minus that (§tank-days), so sleeping, /time set and a walk to the village all count.
 * The two things that PAUSE (incubation in foul water, water decay) push their own clock forward by
 * the step just taken instead.
 */
public final class AquariumBreeding {
    /** In the order the ticker checks them: the first thing the tank lacks, or what it is doing. */
    public enum Status { EMPTY, NO_PAIR, NOT_MATURE, OUT_OF_SEASON, HUNGRY, BAD_WATER, SPAWNING, ROE_READY, INCUBATING, FRY_READY, BUSY }

    /** The ticker looks once a second: the shortest thing it times is hours long. */
    static final int STEP = 20;
    private static final int DAY = 24000;
    private static final int SPAWN_DAYS = 3;
    private static final Random RNG = new Random();

    private AquariumBreeding() {}

    // ---- the ticker (master cell, server) ----

    static void tick(Level level, AquariumBlockEntity be) {
        if (level.getGameTime() % STEP != 0) return;
        long now = level.getDayTime();
        // The step the world clock took since we last looked; 0 after a clock set backwards.
        long dt = be.clock == 0 ? 0 : Math.max(0, now - be.clock);
        be.clock = now;

        water(be, dt);
        boolean fish = !be.getFishes().isEmpty();
        if (fish) feed(be, now);

        Status st;
        if (be.roe.getItem() instanceof FryItem) {
            st = Status.FRY_READY;
        } else if (be.roe.getItem() instanceof RoeItem) {
            st = incubate(level, be, now, dt, fish);
        } else {
            st = spawn(level, be, now, fish);
        }
        view(level, be, st, now);
    }

    // ---- water ----

    /**
     * −8 a day for the tank itself, −2 more for every fish; the aerator halves that and blows +5 a day
     * back in. A bucket in slot 8 is a fresh fill and becomes an empty bucket. The rate is applied over
     * the world-clock step, so a night slept through evaporates like a night watched.
     */
    private static void water(AquariumBlockEntity be, long dt) {
        if (be.getItem(8).is(Items.WATER_BUCKET) && be.water < 100) {
            be.water = 100;
            be.setItem(8, new ItemStack(Items.BUCKET));
        }
        if (dt == 0 || be.water == 0 && !module(be, ModBlocks.AERATOR.get())) return;
        boolean aerator = module(be, ModBlocks.AERATOR.get());
        int perDay = (aerator ? 5 : 0) - (8 + 2 * be.getFishes().size()) / (aerator ? 2 : 1);
        // ponytail: whole percent per day-fraction accumulated in ticks; the remainder is dropped on
        // reload (< 1%), not saved — nobody will measure it.
        be.waterAcc += perDay * dt;
        int whole = (int) (be.waterAcc / DAY);
        if (whole != 0) {
            be.waterAcc -= (long) whole * DAY;
            be.water = Math.max(0, Math.min(100, be.water + whole));
            be.setChanged();
        }
    }

    // ---- feeding ----

    /**
     * When the last meal lapses, one unit comes out of the food slot, then the groundbait slot: bait a
     * day, groundbait two, fish meal three; the feeding station doubles them. Fish oil is not food — it
     * waits in the slot for a spawn run. Nothing to take → the fish go hungry and the status says so.
     */
    private static void feed(AquariumBlockEntity be, long now) {
        if (now < be.fedUntil) return;
        int days;
        String food;
        ItemStack s = be.getItem(6);
        if (s.getItem() instanceof FishMealItem) { days = 3; food = "fish_meal"; }
        else if (s.getItem() instanceof BaitItem b && !b.artificial()) { days = 1; food = "bait"; }
        else if (be.getItem(7).getItem() instanceof GroundbaitItem) { s = be.getItem(7); days = 2; food = "groundbait"; }
        else return;
        s.shrink(1);
        if (module(be, ModBlocks.FEEDING_STATION.get())) days *= 2;
        be.fedUntil = now + (long) days * DAY;
        be.lastFood = food;
        be.setChanged();
    }

    // ---- spawning ----

    private static Status spawn(Level level, AquariumBlockEntity be, long now, boolean fish) {
        ItemStack[] pair = fish ? pair(be) : null;
        FishProfile p = pair == null ? null : profile(FishItem.getSpecies(pair[0]));
        Status lack = !fish ? Status.EMPTY
                : p == null ? Status.NO_PAIR
                : !mature(pair) ? Status.NOT_MATURE
                : !inWindow(level, be, p) ? Status.OUT_OF_SEASON
                : now >= be.fedUntil ? Status.HUNGRY
                : be.water < 50 ? Status.BAD_WATER
                : !be.roe.isEmpty() ? Status.BUSY   // slot 9 holds something that is neither roe nor fry: hand-placed
                : null;
        if (lack != null) {
            // Continuous conditions, or none: a missed feeding starts the days over.
            if (be.spawnTicks != 0) { be.spawnTicks = 0; be.oil = false; be.setChanged(); }
            return lack;
        }
        if (be.spawnTicks == 0) {
            be.spawnTicks = Math.max(1, now);
            // Fish oil is taken once, at the start, and remembered for this run only.
            if (be.getItem(6).getItem() instanceof FishOilItem) { be.getItem(6).shrink(1); be.oil = true; }
            be.setChanged();
            return Status.SPAWNING;
        }
        if (now - be.spawnTicks < (long) spawnDays(be) * DAY) return Status.SPAWNING;
        ItemStack mother = pair[0];
        String genome = Genome.cross(Genome.of(mother), Genome.of(pair[1]), RNG);
        be.roe = RoeItem.of(FishItem.getSpecies(mother), genome, clutch(be, mother, p), now / DAY);
        be.spawnTicks = 0;
        be.oil = false;
        be.sync();
        return Status.ROE_READY;
    }

    /** Three days; fish oil at the start and a gravel bed each take one off, never below one. */
    private static int spawnDays(AquariumBlockEntity be) {
        return Math.max(1, SPAWN_DAYS - (be.oil ? 1 : 0) - (module(be, ModBlocks.GRAVEL_BED.get()) ? 1 : 0));
    }

    /** The clutch for this mother, a quarter more when groundbait stands in the tank or the last meal was fish meal. */
    private static int clutch(AquariumBlockEntity be, ItemStack mother, FishProfile p) {
        int eggs = Genome.clutch(Genome.of(mother), FishItem.getWeightG(mother), p, RNG);
        boolean rich = be.getItem(7).getItem() instanceof GroundbaitItem || "fish_meal".equals(be.lastFood);
        return rich ? (int) Math.round(eggs * 1.25) : eggs;
    }

    /** The species' window; a warm outflow stretches it over the neighbouring thirds of the same season. */
    private static boolean inWindow(Level level, AquariumBlockEntity be, FishProfile p) {
        if (Calendar.inWindow(level, p)) return true;
        if (p.spawnSub == null || !module(be, ModBlocks.WARM_OUTFLOW.get())) return false;
        Calendar.Sub[] subs = Calendar.Sub.values();
        int i = p.spawnSub.ordinal();
        return i > 0 && Calendar.daysUntil(level, p.spawnSeason, subs[i - 1]) == 0
                || i < subs.length - 1 && Calendar.daysUntil(level, p.spawnSeason, subs[i + 1]) == 0;
    }

    // ---- incubation ----

    /**
     * Roe in slot 9 with no adults about (they would eat it) ripens in four days where the climate suits
     * the species, eight where it does not — a warm outflow makes any climate a good one. Foul water
     * (< 25) or fish in the tank pause it: the start time slides forward by the step instead.
     */
    private static Status incubate(Level level, AquariumBlockEntity be, long now, long dt, boolean fish) {
        // §roe-no-species: a creative-tab roe names no species; it hatches into nothing rather than
        // into a crash the tank repeats every tick from the moment the world loads.
        if (RoeItem.species(be.roe) == null) { be.roe = ItemStack.EMPTY; be.incubate = 0; be.sync(); return Status.EMPTY; }
        if (be.spawnTicks != 0) { be.spawnTicks = 0; be.oil = false; be.setChanged(); }
        if (be.incubate == 0) {
            // Laid here, and the parents still about: it is theirs to take out. Hand-placed in an empty
            // tank: the clock starts. (sync, not setChanged: the renderer counts the days from this.)
            if (fish) return Status.ROE_READY;
            be.incubate = Math.max(1, now);
            be.sync();
            return Status.INCUBATING;
        }
        if (fish || be.water < 25) {
            be.incubate += dt;
            be.setChanged();
            return fish ? Status.BUSY : Status.BAD_WATER;
        }
        if (now - be.incubate < (long) incubateDays(level, be) * DAY) return Status.INCUBATING;
        hatch(be);
        return Status.FRY_READY;
    }

    /** Vigour is what survives the egg: VV nine in ten, vv one in two; a snag pile to hide in adds 0.15. Never fewer than one fry. */
    private static void hatch(AquariumBlockEntity be) {
        String g = RoeItem.genome(be.roe);
        double survival = !Genome.dominant(g, 'V') ? 0.5 : Genome.pure(g, 'V') ? 0.9 : 0.7;
        if (module(be, ModBlocks.SNAG_PILE.get())) survival = Math.min(0.95, survival + 0.15);
        int n = Math.max(1, (int) Math.round(RoeItem.count(be.roe) * survival));
        be.roe = FryItem.of(RoeItem.species(be.roe), g, n);
        be.incubate = 0;
        be.sync();
    }

    /**
     * Only the climate group is read (cold / temperate / warm, the same thresholds as
     * {@code FishingManager.addBiomeGroups}) because the terrain groups (taiga, river_biome, beach…)
     * describe where the WATER lies, and a tank stands in a house. A profile that lists no biomes lives
     * anywhere.
     */
    private static int incubateDays(Level level, AquariumBlockEntity be) {
        if (module(be, ModBlocks.WARM_OUTFLOW.get())) return 4;
        FishProfile p = profile(RoeItem.species(be.roe));
        float t = level.getBiome(be.getBlockPos()).value().getBaseTemperature();
        String climate = t < 0.3f ? "cold" : t > 0.95f ? "warm" : "temperate";
        boolean suits = p == null || p.biomes.isEmpty() || p.biomes.getOrDefault(climate, 0.0) > 0;
        return suits ? 4 : 8;
    }

    // ---- the window's numbers ----

    /** The ten ints the menu shows, in the contract's order (docs/design/breeding-api.md, Layer 4). */
    private static void view(Level level, AquariumBlockEntity be, Status st, long now) {
        int[] v = be.view;
        v[0] = st.ordinal();
        // Spawn progress in thirds of the run, whatever its length: the arrow is drawn 0..3.
        v[1] = be.spawnTicks == 0 ? 0 : (int) Math.min(3, (now - be.spawnTicks) * 3 / ((long) spawnDays(be) * DAY));
        boolean roe = be.roe.getItem() instanceof RoeItem;
        v[3] = roe ? incubateDays(level, be) : 0;
        v[2] = roe && be.incubate != 0 ? (int) Math.min(v[3], (now - be.incubate) / DAY) : 0;
        v[4] = (int) Math.max(0, Math.min(DAY, be.fedUntil - now));
        v[5] = be.water;
        // The window of the first fish that has a profile; a lone fish still says when its kind spawns.
        FishProfile p = null;
        for (ItemStack f : be.getFishes()) if ((p = profile(FishItem.getSpecies(f))) != null) break;
        v[6] = p == null ? -1 : p.spawnSeason.ordinal();
        v[7] = p == null || p.spawnSub == null ? -1 : p.spawnSub.ordinal();
        v[8] = be.getFishes().size();
        ItemStack[] pair = v[8] == 0 ? null : pair(be);
        FishProfile pp = pair == null ? null : profile(FishItem.getSpecies(pair[0]));
        v[9] = pp == null ? 0 : clutch(be, pair[0], pp);
    }

    // ---- helpers ----

    /** True when either module slot holds this upgrade block's item. */
    private static boolean module(AquariumBlockEntity be, Block b) {
        return be.getItem(10).is(b.asItem()) || be.getItem(11).is(b.asItem());
    }

    /**
     * The largest ♀ and a ♂ of her species, mother first; null when the tank holds no such pair. Only
     * fish with a catch card have a sex — a netted fish is nobody's parent. Maturity is checked apart so
     * the status can say "too young" rather than "no pair".
     */
    private static ItemStack[] pair(AquariumBlockEntity be) {
        ItemStack[] best = null;
        for (ItemStack f : be.getFishes()) {
            if (!CatchCard.has(f) || CatchCard.of(f).getByte("Sex") != 0) continue;
            if (best != null && FishItem.getWeightG(f) <= FishItem.getWeightG(best[0])) continue;
            ResourceLocation sp = FishItem.getSpecies(f);
            if (sp == null) continue;
            for (ItemStack m : be.getFishes()) {
                if (m != f && CatchCard.has(m) && CatchCard.of(m).getByte("Sex") == 1
                        && sp.equals(FishItem.getSpecies(m))) {
                    best = new ItemStack[]{f, m};
                    break;
                }
            }
        }
        return best;
    }

    /** Both at least an adult (Card.Size 2): babies and juveniles keep growing, they do not spawn. */
    private static boolean mature(ItemStack[] pair) {
        return CatchCard.of(pair[0]).getByte("Size") >= 2 && CatchCard.of(pair[1]).getByte("Size") >= 2;
    }

    private static FishProfile profile(ResourceLocation species) {
        return species == null ? null : FishProfileManager.get().byId(species);
    }
}
