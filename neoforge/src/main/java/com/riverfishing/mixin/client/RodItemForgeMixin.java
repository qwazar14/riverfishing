package com.riverfishing.mixin.client;

import com.riverfishing.client.RodItemRenderer;
import com.riverfishing.item.RodItem;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import org.spongepowered.asm.mixin.Mixin;

import java.util.function.Consumer;

/**
 * §multiloader: Forge attaches a custom item renderer only through {@code Item#initializeClient} (there
 * is no registration event on 1.20.1). Our items live in {@code common}, so this Forge-client mixin
 * patches the composited-rod BEWLR (§rod-layers) onto {@link RodItem}. Fabric registers the same
 * renderer through {@code BuiltinItemRendererRegistry} instead.
 */
@Mixin(RodItem.class)
public abstract class RodItemForgeMixin {
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return RodItemRenderer.get();
            }
        });
    }
}
