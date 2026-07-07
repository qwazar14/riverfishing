package com.riverfishing.component;

/**
 * The fishing "flow" a rod uses (Module 1). Each class has its own state machine in the
 * {@code FishingManager}:
 * <ul>
 *   <li>{@link #ACTIVE} — spinning/ultralight: cast → <b>retrieve</b> (hold right-click to reel) →
 *       strike → hook-set → fight.</li>
 *   <li>{@link #BOTTOM} — feeder/bottom/carp: long cast → wait (alarm/rod-pod in a later module) →
 *       hook-set → fight. Forgiving bite window.</li>
 *   <li>{@link #FLOAT} — pole/bamboo/stick: cast → watch the float → hook-set → fight.
 *       No reel, so <b>no retrieve</b>.</li>
 * </ul>
 */
public enum RodClass {
    ACTIVE,
    BOTTOM,
    FLOAT
}
