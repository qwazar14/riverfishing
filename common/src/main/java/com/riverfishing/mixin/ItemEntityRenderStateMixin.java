package com.riverfishing.mixin;

import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** §fish-seat: storage for {@link SeatedFish} — see there for why the state has to carry it. */
@Mixin(ItemEntityRenderState.class)
public class ItemEntityRenderStateMixin implements SeatedFish {
    @Unique
    private float riverfishing$bottomMargin;

    @Override
    public float riverfishing$bottomMargin() {
        return riverfishing$bottomMargin;
    }

    @Override
    public void riverfishing$setBottomMargin(float margin) {
        this.riverfishing$bottomMargin = margin;
    }
}
