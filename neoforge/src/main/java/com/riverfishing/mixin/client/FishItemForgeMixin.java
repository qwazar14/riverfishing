package com.riverfishing.mixin.client;

import com.riverfishing.client.FishItemRenderer;
import com.riverfishing.item.FishItem;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import org.spongepowered.asm.mixin.Mixin;

import java.util.function.Consumer;

/**
 * §multiloader: the Forge-client twin of {@code RodItemForgeMixin} — patches the weight-scaled fish
 * BEWLR (§fish-scale) onto {@link FishItem} via {@code Item#initializeClient}.
 */
@Mixin(FishItem.class)
public abstract class FishItemForgeMixin {
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return FishItemRenderer.get();
            }
        });
    }
}
