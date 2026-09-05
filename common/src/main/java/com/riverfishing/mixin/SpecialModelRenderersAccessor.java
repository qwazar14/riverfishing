package com.riverfishing.mixin;

import com.mojang.serialization.MapCodec;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.client.renderer.special.SpecialModelRenderers;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * §fry-icon (26.x): the id → codec map a client item definition's {@code "type"} is looked up in.
 * Vanilla keeps it private and fills it in {@code bootstrap()}; NeoForge opens it through
 * {@code RegisterSpecialModelRendererEvent}, Fabric API (0.154/0.155) not at all — so Fabric registers
 * the fry renderer through this accessor from {@code ClientPlatformImpl}.
 */
@Mixin(SpecialModelRenderers.class)
public interface SpecialModelRenderersAccessor {
    @Accessor("ID_MAPPER")
    static ExtraCodecs.LateBoundIdMapper<Identifier, MapCodec<? extends SpecialModelRenderer.Unbaked<?>>> riverfishing$idMapper() {
        throw new AssertionError("mixin accessor — replaced at runtime");
    }
}
