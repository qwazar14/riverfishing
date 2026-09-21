package com.riverfishing.mixin.client;

import com.riverfishing.client.TiedLureItemRenderer;
import com.riverfishing.item.TiedLureItem;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import org.spongepowered.asm.mixin.Mixin;

import java.util.function.Consumer;

/** §tying: the Forge-client twin of {@code FryItemForgeMixin} — a tied lure draws its own canvas. */
@Mixin(TiedLureItem.class)
public abstract class TiedLureItemForgeMixin {
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return TiedLureItemRenderer.get();
            }
        });
    }
}
