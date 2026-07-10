package com.riverfishing.quest;

import com.riverfishing.RiverFishing;
import net.minecraft.advancements.Advancement;
import net.minecraft.server.level.ServerPlayer;

/**
 * Code-driven advancements (§challenges). Some feats can't be expressed with vanilla item triggers —
 * they depend on HOW a fish was caught (rod class, bait, through the ice) or on a funny one-off action.
 * These advancements carry a single {@code minecraft:impossible} criterion named "code" that is never
 * met automatically; the game awards it from the relevant spot instead. Safe no-op if the advancement
 * isn't loaded.
 */
public final class AnglerAdvancements {
    private AnglerAdvancements() {}

    /** Grant a code-driven advancement by its short path (folder {@code riverfishing/<path>}). */
    public static void grant(ServerPlayer sp, String path) {
        if (sp.getServer() == null) return;
        net.minecraft.advancements.AdvancementHolder adv = sp.getServer().getAdvancements().get(RiverFishing.id("riverfishing/" + path));
        if (adv == null) return;
        var progress = sp.getAdvancements().getOrStartProgress(adv);
        if (progress.isDone()) return;
        for (String c : progress.getRemainingCriteria()) {
            sp.getAdvancements().award(adv, c);
        }
    }
}
