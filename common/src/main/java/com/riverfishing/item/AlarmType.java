package com.riverfishing.item;

/**
 * Bite-alarm kinds for a podded rod (Module 3).
 * <ul>
 *   <li>{@link #BELL} — short range, no HUD, <b>high</b> phantom-bite rate (wind/current set it off).</li>
 *   <li>{@link #DIGITAL} — long range, sends a HUD/action-bar alert, <b>low</b> phantom rate.</li>
 * </ul>
 * Phantom rate / range are soft-balanced here; §14 wants these in config eventually.
 */
public enum AlarmType {
    NONE(0.0, 0.0, false),
    BELL(0.0008, 1.0, false),   // ~16-block ring, many false alarms
    DIGITAL(0.00004, 2.0, true); // ~32-block beep + HUD, very rare false alarms (§alarm-tuning)

    private final double phantomPerTick;
    private final double soundVolume; // audible range ≈ 16 * volume blocks
    private final boolean hud;

    AlarmType(double phantomPerTick, double soundVolume, boolean hud) {
        this.phantomPerTick = phantomPerTick;
        this.soundVolume = soundVolume;
        this.hud = hud;
    }

    public double phantomPerTick() { return phantomPerTick; }
    public double soundVolume() { return soundVolume; }
    public boolean hud() { return hud; }
    public double rangeBlocks() { return soundVolume * 16.0; }
}
