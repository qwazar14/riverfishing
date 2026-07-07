package com.riverfishing.water;

/** Result of classifying a body of water: its type, size, max horizontal span and biome flags. */
public record WaterBody(WaterType type, int blockCount, double width,
                        boolean river, boolean swamp, boolean ocean) {

    public static final WaterBody NONE = new WaterBody(WaterType.NONE, 0, 0, false, false, false);
}
