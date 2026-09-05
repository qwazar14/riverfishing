package com.riverfishing.mixin.client;

import com.riverfishing.client.FryItemRenderer;
import com.riverfishing.item.FryItem;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import org.spongepowered.asm.mixin.Mixin;

import java.util.function.Consumer;

/**
 * §breeding: the Forge-client twin of {@code FishItemForgeMixin} — the fry bucket draws three of its
 * species' sprite, via {@code Item#initializeClient}.
 */
@Mixin(FryItem.class)
public abstract class FryItemForgeMixin {
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return FryItemRenderer.get();
            }
        });
    }
}
