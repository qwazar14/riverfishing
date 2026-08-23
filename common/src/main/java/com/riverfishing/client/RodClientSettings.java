package com.riverfishing.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * §rod-client-settings: the /rfrod switches and tunables, persisted to
 * {@code config/riverfishing-rod-client.json} — a toggle that silently reverts on relaunch reads as
 * a bug ("physics is on but does nothing"), because nobody remembers which session they tuned in.
 * Loaded once at client init; saved by {@link RodDebugCommand} on every command (cheap, and the
 * command is the only writer). Absent file or absent keys = the shipped defaults.
 *
 * <p>Deliberately NOT covering the hand-pose arrays (paste into RodHandTransform, rebuild) and the
 * per-rod spring profiles (paste into assets rod_physics.json) — those are authoring workflows with
 * a source of truth in the repo; this file holds the PLAYER-side knobs.
 */
public final class RodClientSettings {
    private RodClientSettings() {}

    private static Path file() {
        return dev.architectury.platform.Platform.getConfigFolder().resolve("riverfishing-rod-client.json");
    }

    public static void load() {
        try {
            Path f = file();
            if (!Files.exists(f)) return;
            JsonObject o = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
            if (o.has("blank3d")) RodItemRenderer.BLANK_3D = o.get("blank3d").getAsBoolean();
            if (o.has("bendDeg")) RodItemRenderer.MAX_BEND_DEG = o.get("bendDeg").getAsFloat();
            if (o.has("whip")) RodItemRenderer.WHIP_GAIN = o.get("whip").getAsFloat();
            if (o.has("phys")) RodPhysics.ENABLED = o.get("phys").getAsBoolean();
            if (o.has("stiffness")) RodPhysics.STIFFNESS = o.get("stiffness").getAsFloat();
            if (o.has("damping")) RodPhysics.DAMPING = o.get("damping").getAsFloat();
            if (o.has("drive")) RodPhysics.DRIVE = o.get("drive").getAsFloat();
            if (o.has("max")) RodPhysics.MAX_DEG = o.get("max").getAsFloat();
            if (o.has("jerk")) RodPhysics.JERK_GAIN = o.get("jerk").getAsFloat();
            if (o.has("pull")) RodPhysics.PULL_GAIN = o.get("pull").getAsFloat();
            if (o.has("handSpace")) RodItemRenderer.HAND_SPACE = o.get("handSpace").getAsInt();
            readVec(o, "pivot", RodHandTransform.PIVOT);
            readVec(o, "tip3d", LineRenderer.TIP3D_OFFSET);
        } catch (Exception ignored) {
            // a broken settings file must never break the client; defaults stand
        }
    }

    public static void save() {
        try {
            JsonObject o = new JsonObject();
            o.addProperty("blank3d", RodItemRenderer.BLANK_3D);
            o.addProperty("bendDeg", RodItemRenderer.MAX_BEND_DEG);
            o.addProperty("whip", RodItemRenderer.WHIP_GAIN);
            o.addProperty("phys", RodPhysics.ENABLED);
            o.addProperty("stiffness", RodPhysics.STIFFNESS);
            o.addProperty("damping", RodPhysics.DAMPING);
            o.addProperty("drive", RodPhysics.DRIVE);
            o.addProperty("max", RodPhysics.MAX_DEG);
            o.addProperty("jerk", RodPhysics.JERK_GAIN);
            o.addProperty("pull", RodPhysics.PULL_GAIN);
            o.addProperty("handSpace", RodItemRenderer.HAND_SPACE);
            o.add("pivot", vec(RodHandTransform.PIVOT));
            o.add("tip3d", vec(LineRenderer.TIP3D_OFFSET));
            Files.writeString(file(), o.toString(), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
        }
    }

    private static void readVec(JsonObject o, String key, float[] into) {
        if (!o.has(key)) return;
        JsonArray a = o.getAsJsonArray(key);
        for (int i = 0; i < into.length && i < a.size(); i++) into[i] = a.get(i).getAsFloat();
    }

    private static JsonArray vec(float[] v) {
        JsonArray a = new JsonArray();
        for (float f : v) a.add(f);
        return a;
    }
}
