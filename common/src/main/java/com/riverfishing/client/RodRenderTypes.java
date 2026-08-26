package com.riverfishing.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;

import java.util.HashMap;
import java.util.Map;
import java.util.OptionalDouble;

/**
 * §line-strand: vanilla {@link RenderType#lines()} but with an EXPLICIT shader line width — the
 * vanilla one leaves the width empty, so 0.8 mm carp mono would draw exactly as thin as 0.1 mm
 * braid. Subclassing is the sanctioned way in: the composite shards and {@code create} are
 * protected members of the hierarchy. Width goes through the rendertype_lines shader (screen-space,
 * like vanilla's), NOT glLineWidth, so it works wherever vanilla's own lines do.
 */
public final class RodRenderTypes extends RenderType {
    private RodRenderTypes(String name, VertexFormat format, VertexFormat.Mode mode, int size,
                           boolean crumbling, boolean sort, Runnable setup, Runnable clear) {
        super(name, format, mode, size, crumbling, sort, setup, clear);
        throw new UnsupportedOperationException("holder for factory methods, never instantiated");
    }

    /** Keyed by tenths of a pixel — three line types and a handful of diameters, so a tiny cache. */
    private static final Map<Integer, RenderType> STRANDS = new HashMap<>();

    /**
     * §line-strand: how a fishing line looks anywhere it is drawn — {r, g, b, alpha, shader width}.
     * Colour and alpha come from the MATERIAL (braid is opaque woven dyneema, fluoro is nearly
     * invisible — the identity LineType's visibility factors encode); width comes from the actual
     * diameter, so 0.8 mm carp mono visibly outweighs 0.1 mm ultralight braid. Shared by the
     * on-blank thread and the world line, so the two can never disagree about one line.
     */
    public static float[] strandStyle(com.riverfishing.component.LineType type, double diameterMm) {
        float w = (float) Math.max(1.0, Math.min(6.0, 1.0 + diameterMm * 5.0));
        return switch (type) {
            case BRAID -> new float[]{58, 82, 52, 255, w};
            case FLUORO -> new float[]{210, 226, 235, 110, w};
            case MONO -> new float[]{232, 228, 208, 255, w};
        };
    }

    public static RenderType lineStrand(float width) {
        int key = Math.round(width * 10f);
        return STRANDS.computeIfAbsent(key, k -> create("riverfishing_line_strand_" + k,
                DefaultVertexFormat.POSITION_COLOR_NORMAL, VertexFormat.Mode.LINES, 1536, false, true,
                RenderType.CompositeState.builder()
                        .setShaderState(RENDERTYPE_LINES_SHADER)
                        .setLineState(new LineStateShard(OptionalDouble.of(k / 10.0)))
                        .setLayeringState(VIEW_OFFSET_Z_LAYERING)
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setOutputState(ITEM_ENTITY_TARGET)
                        .setWriteMaskState(COLOR_DEPTH_WRITE)
                        .setCullState(NO_CULL)
                        .createCompositeState(false)));
    }
}
