package com.riverfishing.item;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * A caught fish (§2.1, §12, Module 8). Each species is its own {@code Item} with its own texture
 * (so it shows correctly in keepnets and on trophy stands), while the weight, length and legality
 * of the individual catch live in NBT — so every catch is unique and does not stack (§11.1).
 */
public class FishItem extends Item {
    public static final String TAG_SPECIES = "Species";
    public static final String TAG_WEIGHT = "WeightG";
    public static final String TAG_LENGTH = "LengthCm";
    public static final String TAG_LEGAL = "Legal";
    public static final String TAG_TROPHY = "Trophy";
    // §prime-fish: written at the catch for top-of-range specimens; the fisherman's BUY trades carry
    // the same two tags on their cost item, so vanilla's subset NBT matching does the weight gating.
    public static final String TAG_GRADE = "Grade";
    public static final String GRADE_PRIME = "prime";
    public static final String TAG_MIN_WEIGHT = "MinW";
    // §livebait-2 (0.4.0): weight carried by a live baitfish (on the LIVEBAIT item, not the fish).
    public static final String TAG_BAIT_WEIGHT = "BaitW";
    // legendary (0.5.0): this specimen is the server one-of-a-kind named fish.
    public static final String TAG_LEGEND = "Legend";

    /**
     * §dedication: legendaries named for a real person, who gets a line on the fish.
     *
     * <p>The Abyssal Demon is idkwho0457_07869's — they brought the mod the whole Florida wave of
     * species and found a good share of the bugs that shipped fixed with it, and picked the halibut
     * themselves. Add a species here and give it a {@code legendary.riverfishing.<species>.credit}
     * line in all three lang files; the tooltip only asks for the key when the name is on this list.
     */
    private static final java.util.Set<String> DEDICATED = java.util.Set.of("halibut");
    /**
     * §morph: how grown this specimen is, 0..100. Written at creation because only the SERVER has the
     * fish profiles — the client paints the fish and would otherwise have no idea whether a 900 g bream
     * is a youngster or an old one.
     */
    public static final String TAG_AGE = "Age";
    /** §morph: the morph id from the {@link com.riverfishing.fish.FishMorph} table, or absent. */
    public static final String TAG_MORPH = "Morph";

    private final Identifier species;

    public FishItem(Identifier species, Properties properties) {
        super(properties);
        this.species = species;
    }

    // §release: a caught fish thrown into water is let go — it shrinks away over 2 s, then vanishes
    // (the item's physics keep working the whole time).
    public static final String TAG_RELEASE_AT = "ReleaseAt";
    public static final int RELEASE_TICKS = 40;

    /**
     * §livebait-2 (0.4.0): bait up a hook by hand — a small caught fish in the MAIN hand + a hook in the
     * OFF hand + sneak-use → one live bait carrying the fish's weight (the hands-on version of the
     * §livebait crafting recipe, for the fantasy of hooking the baitfish you just pulled out).
     */
    @Override
    public net.minecraft.world.InteractionResult use(Level level,
            net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand hand) {
        ItemStack fish = player.getItemInHand(hand);
        ItemStack off = player.getItemInHand(net.minecraft.world.InteractionHand.OFF_HAND);
        int w = getWeightG(fish);
        if (player.isCrouching() && hand == net.minecraft.world.InteractionHand.MAIN_HAND
                && off.getItem() instanceof HookItem && w > 0 && w <= LivebaitRecipe.MAX_WEIGHT_G) {
            if (!level.isClientSide()) {
                var livebait = net.minecraft.core.registries.BuiltInRegistries.ITEM
                        .getValue(com.riverfishing.RiverFishing.id("livebait"));
                ItemStack bait = new ItemStack(livebait);
                int fw = w;
                StackNbt.mutate(bait, t -> t.putInt(TAG_BAIT_WEIGHT, fw));
                fish.shrink(1);
                off.shrink(1);
                if (!player.getInventory().add(bait)) player.drop(bait, false);
                level.playSound(null, player.blockPosition(),
                        net.minecraft.sounds.SoundEvents.FISHING_BOBBER_RETRIEVE,
                        net.minecraft.sounds.SoundSource.PLAYERS, 0.7f, 1.4f);
            }
            return InteractionResult.SUCCESS;
        }
        return super.use(level, player, hand);
    }

    public Identifier species() {
        return species;
    }

    /**
     * §multiloader / §koi: koi released when floating in water. Forge's {@code Item#onEntityItemUpdate} has
     * no vanilla/Fabric equivalent, so this is a static helper called from a Mixin on {@code ItemEntity#tick}
     * (see the forge/fabric mixin jsons). Returns true when the fish was released (item discarded).
     */
    public static boolean koiReleaseTick(ItemStack stack, net.minecraft.world.entity.item.ItemEntity entity) {
        net.minecraft.world.level.Level level = entity.level();
        if (level.isClientSide()) return false;
        // §release is a CHOICE, and vanilla already records whether one was made: Player#drop only
        // calls setThrower when traceItem is true, which is the Q key. An INVOLUNTARY drop records
        // none — giveFish's inventory-full fallback, Inventory#dropAll on death, a keepnet spill —
        // and those were being read as "the player let it go", so a landed fish you had no room for
        // was thrown at the water you were facing and then deleted. Chat said you caught it, the
        // journal recorded it, and there was nothing in your bag.
        //
        // The thrower was already read below, to decide who to credit. It decides whether now too:
        // one function, one answer.
        if (entity.isInWater()
                && entity.getOwner() instanceof net.minecraft.server.level.ServerPlayer thrower) {
            CompoundTag tag = StackNbt.get(stack);
            long now = level.getGameTime();
            if (!tag.contains(TAG_RELEASE_AT)) {
                StackNbt.mutate(stack, t -> t.putLong(TAG_RELEASE_AT, now + RELEASE_TICKS));
                entity.setItem(stack); // sync the countdown to clients so they shrink the render
            } else if (now >= tag.getLongOr(TAG_RELEASE_AT, 0L)) {
                if (level instanceof net.minecraft.server.level.ServerLevel sl) {
                    // §stocking 2.0: presence, settling and the weight-scaled surplus all live in
                    // FishingManager.releaseFish — see there for the whole model.
                    if (stack.getItem() instanceof com.riverfishing.item.FryItem) {   // §c: fry take the same road in
                        com.riverfishing.fishing.FishingManager.releaseFry(sl, entity.blockPosition(),
                                com.riverfishing.item.FryItem.species(stack), com.riverfishing.item.FryItem.genome(stack),
                                com.riverfishing.item.FryItem.count(stack), thrower,
                                com.riverfishing.item.RoeItem.pattern(stack));   // §pattern
                    }
                    Identifier released = stack.getItem() instanceof FishItem ? getSpecies(stack) : null;
                    if (released != null) {
                        com.riverfishing.fishing.FishingManager.releaseFish(sl, entity.blockPosition(),
                                released, getWeightG(stack), stack.getCount(),
                                com.riverfishing.fish.CatchCard.has(stack) ? com.riverfishing.fish.CatchCard.of(stack) : null,   // §c
                                thrower);
                    }
                    sl.sendParticles(net.minecraft.core.particles.ParticleTypes.BUBBLE,
                            entity.getX(), entity.getY() + 0.1, entity.getZ(), 14, 0.25, 0.1, 0.25, 0.02);
                    sl.sendParticles(net.minecraft.core.particles.ParticleTypes.SPLASH,
                            entity.getX(), entity.getY() + 0.2, entity.getZ(), 8, 0.2, 0.05, 0.2, 0.1);
                    sl.playSound(null, entity.blockPosition(), net.minecraft.sounds.SoundEvents.BUCKET_EMPTY_FISH,
                            net.minecraft.sounds.SoundSource.PLAYERS, 0.6f, 1.3f);
                }
                entity.discard();
                return true; // handled: the fish is released
            }
        } else if (StackNbt.contains(stack, TAG_RELEASE_AT)) {
            StackNbt.mutate(stack, t -> t.remove(TAG_RELEASE_AT)); // pulled back onto land — cancel release
            entity.setItem(stack);
        }
        return false; // keep the item's normal physics
    }

    /** A koi carp — a collectible ornamental fish, not really food (§koi). */
    public static boolean isKoi(ItemStack stack) {
        Identifier sp = getSpecies(stack);
        return sp != null && sp.getPath().startsWith("carp_koi_");
    }

    // §multiloader: the weight-scaled fish icon (§fish-scale) is a custom item renderer registered per
    // platform in the client bootstrap (Forge IClientItemExtensions / Fabric BuiltinItemRendererRegistry),
    // no longer via Forge's Item#initializeClient.

    public static ItemStack create(Item fishItem, Identifier species, int weightG, int lengthCm, boolean legal) {
        return create(fishItem, species, weightG, lengthCm, legal, false);
    }

    public static ItemStack create(Item fishItem, Identifier species, int weightG, int lengthCm,
                                   boolean legal, boolean trophy) {
        ItemStack stack = new ItemStack(fishItem);
        // §morph: every fish, from every source — a catch, a bait trap, a villager trade — carries how
        // grown it is, so the age shading is universal rather than a property of one code path.
        com.riverfishing.fish.FishProfile profile =
                com.riverfishing.fish.FishProfileManager.get().byId(species);
        int age = (int) Math.round(com.riverfishing.fish.FishMorph.ageFraction(profile, weightG) * 100);
        StackNbt.mutate(stack, tag -> {
            tag.putString(TAG_SPECIES, species.toString());
            tag.putInt(TAG_WEIGHT, weightG);
            tag.putInt(TAG_LENGTH, lengthCm);
            tag.putBoolean(TAG_LEGAL, legal);
            tag.putByte(TAG_AGE, (byte) age);
            if (trophy) tag.putBoolean(TAG_TROPHY, true);
        });
        stampIcon(stack);
        return stack;
    }

    /**
     * §26.x: everything the fish's LOOK is dispatched on, written into one component.
     *
     * <p>BEWLR is gone, so the two things the old renderer did per specimen are now data the item
     * carries: {@code floats[0]} is the icon scale (§fish-scale) that the client item definition
     * range_dispatches into the bucket models, and {@code colors[0]} is the §morph multiply tint the
     * definition's {@code minecraft:custom_model_data} tint source reads. The port moved the scale and
     * left the tint behind, so all 79 species rendered as their flat undyed sprite — no age shading, no
     * morphs — everywhere the item model is drawn.
     *
     * <p>Called from every path that writes one of the inputs; the tint depends on species, age AND
     * morph, and the morph is stamped after the fish is built.
     *
     * <p>One thing does NOT come back this way: the white wash that makes a young or albino fish
     * LIGHTER than its own sprite ({@link com.riverfishing.fish.FishMorph#pale}). An item tint can only
     * multiply, and multiplying cannot lighten — on 1.21.1 that half rode the renderer's overlay, which
     * has no equivalent here.
     */
    public static void stampIcon(ItemStack stack) {
        Identifier sp = getSpecies(stack);
        // §pattern: the index rides in with the species — a gem paints the fish whatever it is.
        int pattern = com.riverfishing.fish.CatchCard.pattern(stack);
        int tint = sp == null ? -1
                : com.riverfishing.fish.FishMorph.tint(sp.getPath(), getAge(stack), getMorph(stack), pattern);
        java.util.List<Integer> colors = java.util.List.of(tint);
        // §koi-genes: a koi carries FOUR tints — ground, red hi, black sumi and the tancho crown — one
        // per layer of its icon, because one white sprite paints all nine named varieties. On 1.21.1
        // the same four numbers come from FishTint.itemColor; here they are data on the stack.
        if (sp != null && "koi_carp".equals(sp.getPath())) {
            String variety = com.riverfishing.fish.CatchCard.of(stack).getStringOr("Variety", "");
            colors = java.util.List.of(
                    com.riverfishing.fish.FishMorph.koiTint(variety, 0, pattern),
                    com.riverfishing.fish.FishMorph.koiTint(variety, 1, pattern),
                    com.riverfishing.fish.FishMorph.koiTint(variety, 2, pattern),
                    com.riverfishing.fish.FishMorph.koiTint(variety, 3, pattern));
        }
        // §variety-icon: the scale variety, as a STRING the item definition selects the drawing on.
        // Empty for everything that is not a carp, which is the definition's fallback: its own sprite.
        String draw = sp == null ? "" : com.riverfishing.fish.Genome.drawnAs(sp.getPath(),
                com.riverfishing.fish.CatchCard.of(stack).getStringOr("Variety", ""));
        java.util.List<String> strings = sp == null || draw.equals(sp.getPath())
                ? java.util.List.of() : java.util.List.of(draw);
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_MODEL_DATA,
                new net.minecraft.world.item.component.CustomModelData(
                        java.util.List.of(getIconScale(stack)),
                        java.util.List.of(), strings, colors));
    }

    /**
     * §icon-topup: a fish that reached a hand without going through {@link #create} has no colours on
     * it, and 26.x draws a fish ENTIRELY from what its stack carries — so it renders as the raw sprite.
     * For a koi that means blank white, because the koi drawing is greyscale and every one of its
     * seventeen varieties is painted by the four numbers {@link #stampIcon} writes.
     *
     * <p>A /give, a command block, a datapack loot table, or a fish that predates §koi-genes all land
     * here. Stamping on the way past is the only place that catches every one of them; the guard means
     * it happens once per stack and never again.
     */
    @Override
    public void inventoryTick(ItemStack stack, net.minecraft.server.level.ServerLevel level,
                              net.minecraft.world.entity.Entity entity,
                              @Nullable net.minecraft.world.entity.EquipmentSlot slot) {
        super.inventoryTick(stack, level, entity, slot);
        net.minecraft.world.item.component.CustomModelData cmd =
                stack.get(net.minecraft.core.component.DataComponents.CUSTOM_MODEL_DATA);
        if (cmd == null || cmd.colors().isEmpty()) stampIcon(stack);
    }

    public static boolean isTrophy(ItemStack stack) {
        return StackNbt.get(stack).getBooleanOr(TAG_TROPHY, false);
    }

    /** §morph: 0..1, how grown this specimen is. Half means a typical fish of its species. */
    public static double getAge(ItemStack stack) {
        CompoundTag t = StackNbt.get(stack);
        return t.getByteOr(TAG_AGE, (byte) 50) / 100.0;
    }

    /** §morph: the morph id, or "" for an ordinary fish. */
    public static String getMorph(ItemStack stack) {
        return StackNbt.get(stack).getStringOr(TAG_MORPH, "");
    }

    public static void setMorph(ItemStack stack, String morphId) {
        StackNbt.mutate(stack, t -> t.putString(TAG_MORPH, morphId));
        stampIcon(stack);   // the morph IS half the tint — see stampIcon
    }

    /**
     * §trophy: the weight at which a specimen of this species IS a trophy. A plain reading of the size
     * range, published in the journal, so the rule is something a player can look up rather than infer.
     */
    public static int trophyThresholdG(double weightMinG, double weightMaxG) {
        return (int) Math.ceil(weightMinG + (weightMaxG - weightMinG)
                * com.riverfishing.config.RiverFishingConfig.trophyFraction());
    }

    /** The fisherman's minimum accepted weight for a species (§prime-fish). */
    public static int primeThresholdG(double weightMaxG) {
        return (int) Math.ceil(weightMaxG * com.riverfishing.registry.ModVillagers.PRIME_FRACTION);
    }

    /** Marks a fresh catch as prime grade — the buyer takes it (§prime-fish). */
    public static void gradePrime(ItemStack stack, int thresholdG) {
        StackNbt.mutate(stack, tag -> {
            tag.putString(TAG_GRADE, GRADE_PRIME);
            tag.putInt(TAG_MIN_WEIGHT, thresholdG);
        });
        // §data-components (1.21): also set the registered PRIME component the villager buy-trade's ItemCost
        // gates on — its value is the species' min accepted weight, which is the same for every prime specimen
        // of the species, so it both matches the trade's expected value and drives the "accepts from N" legend.
        stack.set(com.riverfishing.registry.ModComponents.PRIME.get(), thresholdG);
    }

    public static boolean isPrime(ItemStack stack) {
        return GRADE_PRIME.equals(StackNbt.get(stack).getStringOr(TAG_GRADE, ""));
    }

    /** Weight as a localized component (§i18n) — "1.50 kg" / "1,50 кг" / "320 g" per the client's lang. */
    public static Component weightText(int weightG) {
        return weightG >= 1000
                ? Component.translatable("unit.riverfishing.kg", String.format("%.2f", weightG / 1000.0))
                : Component.translatable("unit.riverfishing.g", weightG);
    }

    /**
     * §one-that-got-away: the weight of a fish that was never on the scales. Rounded hard on purpose —
     * you felt it on the rod and you saw it turn, you did not weigh it, and a figure to the gram would be
     * a precision the moment never had. Coarser as the fish gets bigger, the way an estimate really is.
     */
    public static Component approxWeightText(int weightG) {
        int step = weightG < 100 ? 10 : weightG < 1000 ? 50 : weightG < 10000 ? 500 : 1000;
        int rounded = Math.max(step, Math.round(weightG / (float) step) * step);
        if (rounded < 1000) return Component.translatable("unit.riverfishing.g", rounded);
        // An estimate has to LOOK like an estimate: half-kilos up to ten, whole kilos above. The normal
        // two-decimal form would read as "around 30.00 kg", which is a weighed figure, not a guess.
        return Component.translatable("unit.riverfishing.kg", step >= 1000
                ? String.valueOf(rounded / 1000)
                : String.format("%.1f", rounded / 1000.0));
    }

    /** Flat-string form of {@link #weightText} for plain-text call sites; resolves the caller-side lang. */
    public static String weightLabel(int weightG) {
        return weightText(weightG).getString();
    }

    /** Species of this catch: NBT first (authoritative for the individual), else the item's species. */
    @Nullable
    public static Identifier getSpecies(ItemStack stack) {
        CompoundTag tag = StackNbt.get(stack);
        if (tag.contains(TAG_SPECIES)) {
            return Identifier.tryParse(tag.getStringOr(TAG_SPECIES, ""));
        }
        return stack.getItem() instanceof FishItem fish ? fish.species : null;
    }

    public static int getWeightG(ItemStack stack) {
        return StackNbt.get(stack).getIntOr(TAG_WEIGHT, 0);
    }

    public static int getLengthCm(ItemStack stack) {
        return StackNbt.get(stack).getIntOr(TAG_LENGTH, 0);
    }

    /**
     * Icon scale for this catch (§fish-scale): the fish's real LENGTH — 50 cm renders at 1 block, 100 cm
     * at 2, a 380 cm mako at 7.6. Length tracks weight by the allometric L ∝ W^(1/3) law (see
     * FishingManager#rollFish), so this already reflects how heavy the fish is. All icons are drawn
     * FULL-LENGTH now (the old folded-in-half species art is gone with the 256×256 repaint), so one
     * rule fits everyone. Floor 0.45 keeps the smallest fish readable; the true giants are capped
     * PER DISPLAY CONTEXT in FishItemRenderer — huge in hand and on the ground, sane in a slot.
     */
    public static float getIconScale(ItemStack stack) {
        int len = getLengthCm(stack);
        if (len <= 0) return 1.0f; // creative-tab / JEI entry with no individual data
        return Math.max(0.45f, Math.min(8.0f, len / 50.0f));
    }

    public static boolean isLegal(ItemStack stack) {
        CompoundTag tag = StackNbt.get(stack);
        return tag.getBooleanOr(TAG_LEGAL, true);
    }

    private static String displayKey(Identifier species) {
        return "fish." + species.getNamespace() + "." + species.getPath();
    }

    /** Trophy specimens shimmer like enchanted gear — the jackpot should look like one. */
    @Override
    public boolean isFoil(ItemStack stack) {
        return isTrophy(stack) || super.isFoil(stack);
    }

    @Override
    public Component getName(ItemStack stack) {
        Identifier sp = getSpecies(stack);
        if (sp == null) return super.getName(stack);
        Component name = Component.translatable(displayKey(sp));
        int w = getWeightG(stack);
        if (w <= 0) {
            return name; // e.g. the creative-tab entry, with no individual data yet
        }
        if (isTrophy(stack)) {
            return Component.literal("★ ").append(name)
                    .append(Component.literal(" (")).append(weightText(w)).append(Component.literal(")"))
                    .withStyle(ChatFormatting.GOLD);
        }
        return name.copy()
                .append(Component.literal(" (")).append(weightText(w)).append(Component.literal(")"));
    }

    /** §catch-card: a landed fish shows its card; every other fish keeps the plain lines below. */
    @Override
    public java.util.Optional<net.minecraft.world.inventory.tooltip.TooltipComponent> getTooltipImage(ItemStack stack) {
        return com.riverfishing.fish.CatchCard.has(stack)
                ? java.util.Optional.of(new FishCardTooltip(stack)) : java.util.Optional.empty();
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, net.minecraft.world.item.component.TooltipDisplay display, java.util.function.Consumer<Component> tooltip, TooltipFlag flag) {
        CompoundTag tag = StackNbt.get(stack);
        // legendary (0.5.0): the one-of-a-kind server fish announces itself in gold.
        if (tag.getBooleanOr(TAG_LEGEND, false)) {
            Identifier lsp = getSpecies(stack);
            if (lsp != null) {
                tooltip.accept(Component.translatable("legendary.riverfishing." + lsp.getPath())
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
                // §dedication: a legendary named FOR someone says so on the fish itself. Listed in
                // code rather than guessed from the lang file because a missing translation key renders
                // as the key, and a fish claiming to honour "legendary.riverfishing.pike.credit" would
                // be worse than no line at all.
                if (DEDICATED.contains(lsp.getPath())) {
                    tooltip.accept(Component.translatable("legendary.riverfishing." + lsp.getPath() + ".credit")
                            .withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC));
                }
            }
        }
        if (getWeightG(stack) <= 0) {
            // The fisherman's buy-trade cost has no weight — show the "accepts from N" legend (§prime-fish).
            // 1.21: the cost's display stack is rebuilt on the client from the ItemCost's component predicate,
            // so the threshold arrives via the PRIME component; fall back to the legacy custom_data key.
            Integer primeMin = stack.get(com.riverfishing.registry.ModComponents.PRIME.get());
            int min = primeMin != null ? primeMin : (tag.getIntOr(TAG_MIN_WEIGHT, -1));
            if (min >= 0) {
                tooltip.accept(Component.translatable("tooltip.riverfishing.trade_min_weight", weightText(min))
                        .withStyle(ChatFormatting.YELLOW));
            }
            return;
        }
        if (com.riverfishing.fish.CatchCard.has(stack)) return;     // the card says all of this
        // §morph: named on its own line rather than folded into the item name. A prefix would have to
        // agree in gender with 79 species names in Russian and Ukrainian, and "Золотистый плотва" is
        // worse than no feature at all.
        String morph = tag.getStringOr(TAG_MORPH, "");
        if (!morph.isEmpty()) {
            tooltip.accept(Component.translatable("morph.riverfishing." + morph)
                    .withStyle(ChatFormatting.AQUA, ChatFormatting.ITALIC));
        }
        if (isTrophy(stack)) {
            tooltip.accept(Component.translatable("tooltip.riverfishing.fish_trophy")
                    .withStyle(ChatFormatting.GOLD));
        }
        if (isPrime(stack)) {
            tooltip.accept(Component.translatable("tooltip.riverfishing.fish_prime")
                    .withStyle(ChatFormatting.YELLOW));
        }
        tooltip.accept(Component.translatable("tooltip.riverfishing.fish_length", getLengthCm(stack))
                .withStyle(ChatFormatting.GRAY));
        if (!isLegal(stack)) {
            tooltip.accept(Component.translatable("tooltip.riverfishing.fish_foulhooked")
                    .withStyle(ChatFormatting.RED));
        }
    }
}
