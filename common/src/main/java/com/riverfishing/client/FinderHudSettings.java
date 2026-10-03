package com.riverfishing.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * §finder-hud-settings: whether the fish finder's corner sounder and its direction dial are drawn, and where. Set on
 * the finder's own screen (its lower keys) and kept in {@code config/riverfishing-finder-hud.json} — the player's own
 * setting on this machine, so it holds for every finder they pick up and belongs to no finder item.
 *
 * <p>Positions are fractions of the scaled screen (the sounder's top-left corner, the dial's centre), so a moved piece
 * stays where it was put when the window or GUI scale changes; -1 is the shipped place in the top-right corner.
 */
public final class FinderHudSettings {
    private FinderHudSettings() {}

    public static boolean showStrip = true, showArrow = true;
    static float stripFx = -1, stripFy = -1, arrowFx = -1, arrowFy = -1;

    /** The dial's radius and the two label lines under it — what has to stay on the screen. */
    static final int DIAL_R = 13, DIAL_LABELS = 24;

    private static Path file() {
        return dev.architectury.platform.Platform.getConfigFolder().resolve("riverfishing-finder-hud.json");
    }

    public static void load() {
        try {
            Path f = file();
            if (!Files.exists(f)) return;
            JsonObject o = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
            if (o.has("strip")) showStrip = o.get("strip").getAsBoolean();
            if (o.has("arrow")) showArrow = o.get("arrow").getAsBoolean();
            if (o.has("stripX")) stripFx = o.get("stripX").getAsFloat();
            if (o.has("stripY")) stripFy = o.get("stripY").getAsFloat();
            if (o.has("arrowX")) arrowFx = o.get("arrowX").getAsFloat();
            if (o.has("arrowY")) arrowFy = o.get("arrowY").getAsFloat();
        } catch (Exception ignored) {
            // a broken settings file must never break the client; the corner stands
        }
    }

    public static void save() {
        try {
            JsonObject o = new JsonObject();
            o.addProperty("strip", showStrip);
            o.addProperty("arrow", showArrow);
            o.addProperty("stripX", stripFx);
            o.addProperty("stripY", stripFy);
            o.addProperty("arrowX", arrowFx);
            o.addProperty("arrowY", arrowFy);
            Files.writeString(file(), o.toString(), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
        }
    }

    static boolean stripMoved() {
        return stripFx >= 0;
    }

    /** The sounder's top-left corner on a {@code sw}x{@code sh} screen. */
    static int[] stripPos(int sw, int sh) {
        if (stripFx < 0) return new int[]{sw - ClientHud.STRIP_W - 6, 6};
        return new int[]{clamp(Math.round(stripFx * sw), 0, sw - ClientHud.STRIP_W), clamp(Math.round(stripFy * sh), 0, sh - ClientHud.STRIP_H)};
    }

    /** The dial's centre on a {@code sw}x{@code sh} screen: under the corner sounder until it is moved. */
    static int[] arrowPos(int sw, int sh) {
        if (arrowFx < 0) return new int[]{sw - 6 - ClientHud.STRIP_W / 2, 6 + ClientHud.STRIP_H + 18};
        return new int[]{clamp(Math.round(arrowFx * sw), DIAL_R, sw - DIAL_R), clamp(Math.round(arrowFy * sh), DIAL_R, sh - DIAL_R - DIAL_LABELS)};
    }

    static void place(int stripX, int stripY, int arrowX, int arrowY, int sw, int sh) {
        stripFx = stripX / (float) sw;
        stripFy = stripY / (float) sh;
        arrowFx = arrowX / (float) sw;
        arrowFy = arrowY / (float) sh;
    }

    static void reset() {
        stripFx = stripFy = arrowFx = arrowFy = -1;
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
