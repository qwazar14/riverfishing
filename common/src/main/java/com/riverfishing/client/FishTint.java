package com.riverfishing.client;

import com.riverfishing.fish.FishMorph;
import com.riverfishing.item.FishItem;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * §morph paint: turns a fish stack into the two numbers that colour it, in ONE place.
 *
 * <p>A vanilla item tint can only multiply, so it darkens and shifts hue and can never make a fish
 * lighter than its own sprite — useless for an albino or for a young silver bream. The second knob is the
 * overlay UV, which is vanilla's white-flash channel: {@code u} from 0 to 15 washes the sprite up to 75%
 * white, and it costs nothing because every entity render type already samples it. Between a multiply and
 * a whitening there is enough range to paint every morph in the table without one new image.
 *
 * <p>Both loaders register the same function from here, and the fish sprites drifting in open water use it
 * too, so a golden tench is the same golden in the water, in the hand and in the journal.
 */
public final class FishTint {

    private FishTint() {}

    /** The tint provider both loaders register for the fish items (layer 0 of the icon model). */
    public static int itemColor(ItemStack stack, int tintIndex) {
        Identifier sp = FishItem.getSpecies(stack);
        if (sp == null) return -1;                       // a creative-tab entry with no specimen data
        // §koi-genes: one white koi sprite, four tinted layers — ground, red hi, black sumi and the
        // tancho crown — so every named variety is painted rather than drawn. The patch masks are cut
        // out of the sprite itself, so a layer this fish does not wear is given the ground colour and
        // vanishes into the body (tools/gen_koi_layers.py).
        if ("koi_carp".equals(sp.getPath())) {
            return tintIndex >= 0 && tintIndex < 4
                    ? FishMorph.koiTint(com.riverfishing.fish.CatchCard.of(stack).getStringOr("Variety", ""), tintIndex) : -1;
        }
        if (tintIndex != 0) return -1;
        return FishMorph.tint(sp.getPath(), FishItem.getAge(stack), FishItem.getMorph(stack));
    }

    /** The packed overlay UV for a stack: whiter for a young fish, and much whiter for a pale morph. */
    public static int overlay(ItemStack stack) {
        Identifier sp = FishItem.getSpecies(stack);
        if (sp == null) return OverlayTexture.NO_OVERLAY;
        return whiten(FishMorph.pale(sp.getPath(), FishItem.getAge(stack), FishItem.getMorph(stack)));
    }

    /**
     * A 0..1 whitening as an overlay UV. Vanilla's overlay texture stores, for {@code v} in the white
     * band, an alpha that FALLS as {@code u} rises, and the shader mixes towards the overlay colour by
     * one minus that alpha — so u = 0 is the untouched sprite and u = 15 is three quarters white.
     */
    public static int whiten(float amount) {
        if (amount <= 0.01f) return OverlayTexture.NO_OVERLAY;
        int u = Math.min(15, Math.round(amount * 15f));
        return OverlayTexture.pack(u, OverlayTexture.WHITE_OVERLAY_V);
    }
}
