package com.riverfishing.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

/**
 * "Fish oil" (§fish-oil-potion) — the one thing in the Potion of Fish Oil that vanilla cannot express as a
 * {@code MobEffectInstance}: <em>clearing</em> Mining Fatigue. A potion is a static list of effects to ADD;
 * nothing in that list can take an effect away, so the cure has to be carried by an effect of the mod's own.
 *
 * <p><b>Why an effect and not a use hook.</b> The obvious alternative was to hook "player finished drinking
 * a Potion of Fish Oil" and strip the fatigue there. That covers exactly one of the four ways this potion is
 * delivered. Once the potion is in vanilla's brewing chain it also comes as a SPLASH bottle, a LINGERING
 * cloud and a TIPPED ARROW, and each of those hands its effect list to a {@link LivingEntity} that never
 * "used" an item — a drink hook would silently do nothing for an ally you splashed, which is precisely the
 * case the design calls out. An effect travels with the potion through all four vehicles for free.
 *
 * <p><b>Why it clears on every tick, not once.</b> The Elder Guardian re-curses on a timer; a one-shot cure
 * would be undone thirty seconds into a ninety-second potion and could not be re-applied, because vanilla
 * only fires its "effect added" path on the FIRST application. Clearing while the oil is in you is also what
 * the patchnote promises. The cost is one absent-key map lookup per tick after the first.
 *
 * <p><b>On removing another effect from inside an effect tick.</b> {@code LivingEntity.tickEffects} iterates
 * {@code activeEffects} and wraps the whole loop in {@code catch (ConcurrentModificationException)} — vanilla
 * anticipates exactly this. The map is only touched on the tick where the fatigue is actually present (the
 * first one, and again after each re-curse), so at worst a single tick of the entity's OTHER effects is
 * skipped, once, and never while the fatigue is already gone.
 */
public final class FishOilEffect extends MobEffect {
    /** The pantry's oil colour (tools/gen_fish_oil_icon.py), so the bottle looks like the ingredient. */
    public static final int COLOUR = 0xD89A30;

    public FishOilEffect() {
        super(MobEffectCategory.BENEFICIAL, COLOUR);
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        return true;
    }

    @Override
    public void applyEffectTick(LivingEntity entity, int amplifier) {
        if (!entity.level().isClientSide) {
            entity.removeEffect(MobEffects.DIG_SLOWDOWN);
        }
    }
}
