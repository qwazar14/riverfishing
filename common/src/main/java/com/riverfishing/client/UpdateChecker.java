package com.riverfishing.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.riverfishing.config.RiverFishingConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * §update-check (0.4.0): on joining a world the client quietly fetches the update feed and, when the
 * installed version is behind, prints ONE compact chat digest — the latest version, plus up to three
 * bullet lines for EVERY release the player missed (join with 0.1.0 and you get the whole story in a
 * screenful). Silent on: up-to-date, offline, feed missing this MC line, or the config toggle off.
 *
 * <p>The feed is a single updates.json served from the repo (branch-independent content: per-MC-line
 * latest + a shared changelog with ru/en lines). Checked once per game launch, off-thread, with tight
 * timeouts — a dead feed can never stall a login.
 */
public final class UpdateChecker {
    private static final String FEED =
            "https://raw.githubusercontent.com/qwazar14/riverfishing/mc-1.21.1/updates.json";
    private static final String RELEASES = "https://github.com/qwazar14/riverfishing/releases";
    private static final int MAX_VERSIONS = 5; // a very old install still gets a bounded digest

    private static volatile boolean checked;

    private UpdateChecker() {}

    /** Client join hook (ClientInit). Fire-and-forget; never blocks the client thread. */
    public static void onJoin() {
        if (checked || !RiverFishingConfig.updateCheck) return;
        checked = true;
        Thread t = new Thread(UpdateChecker::run, "riverfishing-update-check");
        t.setDaemon(true);
        t.start();
    }

    private static void run() {
        try {
            HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build();
            HttpResponse<String> resp = http.send(HttpRequest.newBuilder(URI.create(FEED))
                    .timeout(Duration.ofSeconds(6)).GET().build(), HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) return;
            JsonObject root = JsonParser.parseString(resp.body()).getAsJsonObject();

            String current = strip(dev.architectury.platform.Platform.getMod("riverfishing").getVersion());
            String mc = net.minecraft.SharedConstants.getCurrentVersion().getName();
            JsonObject latest = root.getAsJsonObject("latest");
            if (latest == null || !latest.has(mc)) return;        // this MC line has no feed entry yet
            String remote = latest.get(mc).getAsString();
            if (cmp(remote, current) <= 0) return;                 // up to date (or ahead: dev build)

            List<Component> lines = digest(root, current, remote);
            Minecraft mcClient = Minecraft.getInstance();
            mcClient.execute(() -> {
                if (mcClient.player == null) return;
                for (Component line : lines) mcClient.player.displayClientMessage(line, false);
            });
        } catch (Exception ignored) {
            // Offline, rate-limited, malformed feed — an update hint is never worth a log worry.
        }
    }

    /** Header (clickable → releases page) + up to three bullets per missed version, newest first. */
    private static List<Component> digest(JsonObject root, String current, String remote) {
        boolean ru = Minecraft.getInstance().options.languageCode.startsWith("ru");
        List<Component> out = new ArrayList<>();
        out.add(Component.translatable("message.riverfishing.update_available", remote, current)
                .withStyle(s -> s.withColor(ChatFormatting.GOLD)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, RELEASES))));
        JsonArray changelog = root.getAsJsonArray("changelog");
        if (changelog == null) return out;
        int shown = 0;
        for (JsonElement e : changelog) { // feed keeps newest first
            JsonObject entry = e.getAsJsonObject();
            String v = strip(entry.get("version").getAsString());
            // Only versions the player missed — and never leak entries newer than the released latest.
            if (cmp(v, current) <= 0 || cmp(v, remote) > 0) continue;
            if (++shown > MAX_VERSIONS) break;
            out.add(Component.literal(v).withStyle(ChatFormatting.YELLOW));
            JsonArray bullets = entry.getAsJsonArray(ru && entry.has("ru") ? "ru" : "en");
            if (bullets == null) continue;
            for (int i = 0; i < Math.min(3, bullets.size()); i++) {
                out.add(Component.literal("  • " + bullets.get(i).getAsString())
                        .withStyle(ChatFormatting.GRAY));
            }
        }
        return out;
    }

    /** "0.3.0+26.1.2" → "0.3.0" (build metadata never affects ordering). */
    private static String strip(String v) {
        int plus = v.indexOf('+');
        return plus >= 0 ? v.substring(0, plus) : v;
    }

    /** Numeric dotted compare; missing parts are 0, non-numeric parts compare as 0. */
    private static int cmp(String a, String b) {
        String[] as = strip(a).split("\\."), bs = strip(b).split("\\.");
        for (int i = 0; i < Math.max(as.length, bs.length); i++) {
            int ai = i < as.length ? parse(as[i]) : 0;
            int bi = i < bs.length ? parse(bs[i]) : 0;
            if (ai != bi) return Integer.compare(ai, bi);
        }
        return 0;
    }

    private static int parse(String s) {
        try { return Integer.parseInt(s.replaceAll("\\D.*$", "")); } catch (Exception e) { return 0; }
    }
}
