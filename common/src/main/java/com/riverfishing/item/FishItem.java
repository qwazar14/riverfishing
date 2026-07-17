package com.riverfishing.item;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * A caught fish (Â§2.1, Â§12, Module 8). Each species is its own {@code Item} with its own texture
 * (so it shows correctly in keepnets and on trophy stands), while the weight, length and legality
 * of the individual catch live in NBT â so every catch is unique and does not stack (Â§11.1).
 */
public class FishItem extends Item {
    public static final String TAG_SPECIES = "Species";
    public static final String TAG_WEIGHT = "WeightG";
    public static final String TAG_LENGTH = "LengthCm";
    public static final String TAG_LEGAL = "Legal";
    public static final String TAG_TROPHY = "Trophy";
    // Â§prime-fish: written at the catch for top-of-range specimens; the fisherman's BUY trades carry
    // the same two tags on their cost item, so vanilla's subset NBT matching does the weight gating.
    public static final String TAG_GRADE = "Grade";
    public static final String GRADE_PRIME = "prime";
    public static final String TAG_MIN_WEIGHT = "MinW";
    // Â§livebait-2 (0.4.0): weight carried by a live baitfish (on the LIVEBAIT item, not the fish).
    public static final String TAG_BAIT_WEIGHT = "BaitW";

    private final Identifier species;

    public FishItem(Identifier species, Properties properties) {
        super(properties);
        this.species = species;
    }

    // Â§release: a caught fish thrown into water is let go â it shrinks away over 2 s, then vanishes
    // (the item's physics keep working the whole time).
    public static final String TAG_RELEASE_AT = "ReleaseAt";
    public static final int RELEASE_TICKS = 40;

    /**
     * §livebait-2 (0.4.0): bait up a hook by hand — a small caught fish in the MAIN hand + a hook in
     * the OFF hand + sneak-use → one live bait carrying the fish's weight.
     */
    @Override
    public net.minecraft.world.InteractionResult use(Level level,
            net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand hand) {
        ItemStack fish = player.getItemInHand(hand);
        ItemStack off = player.getItemInHand(net.minecraft.world.InteractionHand.OFF_HAND);
        int w = getWeightG(fish);
        if (player.isCrouching() && hand == net.minecraft.world.InteractionHand.MAIN_HAND
                && off.getItem() instanceof HookItem && w > 0 && w <= LivebaitRecipe.MAX_WEIGHT_G) {
            if (!level.isClientSide) {
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
            return net.minecraft.world.InteractionResult.SUCCESS;
        }
        return super.use(level, player, hand);
    }

    public Identifier species() {
        return species;
    }

    /**
     * Â§multiloader / Â§koi: koi released when floating in water. Forge's {@code Item#onEntityItemUpdate} has
     * no vanilla/Fabric equivalent, so this is a static helper called from a Mixin on {@code ItemEntity#tick}
     * (see the forge/fabric mixin jsons). Returns true when the fish was released (item discarded).
     */
    public static boolean koiReleaseTick(ItemStack stack, net.minecraft.world.entity.item.ItemEntity entity) {
        net.minecraft.world.level.Level level = entity.level();
        if (level.isClientSide()) return false;
        if (entity.isInWater()) {
            CompoundTag tag = StackNbt.get(stack);
            long now = level.getGameTime();
            if (!tag.contains(TAG_RELEASE_AT)) {
                StackNbt.mutate(stack, t -> t.putLong(TAG_RELEASE_AT, now + RELEASE_TICKS));
                entity.setItem(stack); // sync the countdown to clients so they shrink the render
            } else if (now >= tag.getLongOr(TAG_RELEASE_AT, 0L)) {
                if (level instanceof net.minecraft.server.level.ServerLevel sl) {
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
            StackNbt.mutate(stack, t -> t.remove(TAG_RELEASE_AT)); // pulled back onto land â cancel release
            entity.setItem(stack);
        }
        return false; // keep the item's normal physics
    }

    /** A koi carp â a collectible ornamental fish, not really food (Â§koi). */
    public static boolean isKoi(ItemStack stack) {
        Identifier sp = getSpecies(stack);
        return sp != null && sp.getPath().startsWith("carp_koi_");
    }

    // Â§multiloader: the weight-scaled fish icon (Â§fish-scale) is a custom item renderer registered per
    // platform in the client bootstrap (Forge IClientItemExtensions / Fabric BuiltinItemRendererRegistry),
    // no longer via Forge's Item#initializeClient.

    public static ItemStack create(Item fishItem, Identifier species, int weightG, int lengthCm, boolean legal) {
        return create(fishItem, species, weightG, lengthCm, legal, false);
    }

    public static ItemStack create(Item fishItem, Identifier species, int weightG, int lengthCm,
                                   boolean legal, boolean trophy) {
        ItemStack stack = new ItemStack(fishItem);
        StackNbt.mutate(stack, tag -> {
            tag.putString(TAG_SPECIES, species.toString());
            tag.putInt(TAG_WEIGHT, weightG);
            tag.putInt(TAG_LENGTH, lengthCm);
            tag.putBoolean(TAG_LEGAL, legal);
            if (trophy) tag.putBoolean(TAG_TROPHY, true);
        });
        // Â§26.1 Â§fish-scale: the icon scale rides custom_model_data float[0] â the client item
        // definition range_dispatches on it into the scale-bucket models (BEWLR is gone).
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_MODEL_DATA,
                new net.minecraft.world.item.component.CustomModelData(
                        java.util.List.of(getIconScale(stack)),
                        java.util.List.of(), java.util.List.of(), java.util.List.of()));
        return stack;
    }

    public static boolean isTrophy(ItemStack stack) {
        return StackNbt.get(stack).getBooleanOr(TAG_TROPHY, false);
    }

    /** The fisherman's minimum accepted weight for a species (Â§prime-fish). */
    public static int primeThresholdG(double weightMaxG) {
        return (int) Math.ceil(weightMaxG * com.riverfishing.registry.ModVillagers.PRIME_FRACTION);
    }

    /** Marks a fresh catch as prime grade â the buyer takes it (Â§prime-fish). */
    public static void gradePrime(ItemStack stack, int thresholdG) {
        StackNbt.mutate(stack, tag -> {
            tag.putString(TAG_GRADE, GRADE_PRIME);
            tag.putInt(TAG_MIN_WEIGHT, thresholdG);
        });
        // Â§data-components (1.21): also set the registered PRIME component the villager buy-trade's ItemCost
        // gates on â its value is the species' min accepted weight, which is the same for every prime specimen
        // of the species, so it both matches the trade's expected value and drives the "accepts from N" legend.
        stack.set(com.riverfishing.registry.ModComponents.PRIME.get(), thresholdG);
    }

    public static boolean isPrime(ItemStack stack) {
        return GRADE_PRIME.equals(StackNbt.get(stack).getStringOr(TAG_GRADE, ""));
    }

    /** Weight as a localized component (Â§i18n) â "1.50 kg" / "1,50 ÐºÐ³" / "320 g" per the client's lang. */
    public static Component weightText(int weightG) {
        return weightG >= 1000
                ? Component.translatable("unit.riverfishing.kg", String.format("%.2f", weightG / 1000.0))
                : Component.translatable("unit.riverfishing.g", weightG);
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

    /** Species drawn FOLDED in half on their icon, so they use half the lengthâscale rule (Â§fish-scale). */
    private static final java.util.Set<String> FOLDED_ICON =
            java.util.Set.of("catfish", "pike", "burbot", "eel", "sterlet");

    /**
     * Icon scale for this catch (Â§fish-scale): the fish's real LENGTH â 50 cm renders at 1 block, 100 cm at
     * 2, 25 cm at Â½. Length now tracks weight by the allometric L â W^(1/3) law (see FishingManager#rollFish),
     * so this length-based scale already reflects how heavy the fish is â a big/heavy fish is long, a small
     * one short. Long species drawn FOLDED in half divide by 100 (their art is already half-length). Floor
     * 0.45 keeps the smallest fish readable; ceiling 2.0 stops a giant swallowing the inventory.
     */
    public static float getIconScale(ItemStack stack) {
        int len = getLengthCm(stack);
        if (len <= 0) return 1.0f; // creative-tab / JEI entry with no individual data
        Identifier sp = getSpecies(stack);
        boolean folded = sp != null && FOLDED_ICON.contains(sp.getPath());
        float scale = len / (folded ? 100.0f : 50.0f);
        return Math.max(0.45f, Math.min(2.0f, scale));
    }

    public static boolean isLegal(ItemStack stack) {
        CompoundTag tag = StackNbt.get(stack);
        return !tag.contains(TAG_LEGAL) || tag.getBooleanOr(TAG_LEGAL, false);
    }

    private static String displayKey(Identifier species) {
        return "fish." + species.getNamespace() + "." + species.getPath();
    }

    /** Trophy specimens shimmer like enchanted gear â the jackpot should look like one. */
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
            return Component.literal("â ").append(name)
                    .append(Component.literal(" (")).append(weightText(w)).append(Component.literal(")"))
                    .withStyle(ChatFormatting.GOLD);
        }
        return name.copy()
                .append(Component.literal(" (")).append(weightText(w)).append(Component.literal(")"));
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, net.minecraft.world.item.component.TooltipDisplay display, java.util.function.Consumer<Component> tooltip, TooltipFlag flag) {
        CompoundTag tag = StackNbt.get(stack);
        if (getWeightG(stack) <= 0) {
            // The fisherman's buy-trade cost has no weight â show the "accepts from N" legend (Â§prime-fish).
            // 1.21: the cost's display stack is rebuilt on the client from the ItemCost's component predicate,
            // so the threshold arrives via the PRIME component; fall back to the legacy custom_data key.
            Integer primeMin = stack.get(com.riverfishing.registry.ModComponents.PRIME.get());
            int min = primeMin != null ? primeMin : (tag.contains(TAG_MIN_WEIGHT) ? tag.getIntOr(TAG_MIN_WEIGHT, 0) : -1);
            if (min >= 0) {
                tooltip.accept(Component.translatable("tooltip.riverfishing.trade_min_weight", weightText(min))
                        .withStyle(ChatFormatting.YELLOW));
            }
            return;
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
