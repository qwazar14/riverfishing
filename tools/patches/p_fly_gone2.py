# -*- coding: utf-8 -*-
"""What p_fly_gone left behind: one live branch it could not match, and the comments that still name a
rod nobody can hold. Run after it, on each tree.  py -X utf8 tools/patches/p_fly_gone2.py [tree ...]"""
import io, os, sys

MAIN = r"C:/Users/Qwazar/VS Code Projects/fishing mod"
TREES = [MAIN, r"C:/Users/Qwazar/wt/rf1201", r"C:/Users/Qwazar/wt/rf26"]
J = "common/src/main/java/com/riverfishing/"


def rd(p): return io.open(p, encoding="utf-8").read()
def wr(p, s): io.open(p, "w", encoding="utf-8", newline="\n").write(s)


def swap(path, old, new, what):
    if not os.path.exists(path):   # 26.x draws the rod from RodChain, not RodItemRenderer
        return
    s = rd(path)
    if old not in s:
        return
    assert s.count(old) >= 1, "%s @ %s" % (what, path)
    wr(path, s.replace(old, new))


EDITS = {
    "fishing/FishingManager.java": [
        ("                : session.fly != null ? Mth.clamp(session.fly.reel, 0.0, 0.85)   // §fly-3: the fight starts where the fly was\n"
         "                : 0.0;", "                : 0.0;"),
        ("            {   // §progression: the traits, the province and the fly rod, counted before the quests look",
         "            {   // §progression: the traits and the province, counted before the quests look"),
    ],
    "network/ModNetwork.java": [
        ("            NetworkManager.registerS2CPayloadType(JigGaugePacket.TYPE, JigGaugePacket.STREAM_CODEC);   // §fly",
         "            NetworkManager.registerS2CPayloadType(JigGaugePacket.TYPE, JigGaugePacket.STREAM_CODEC);"),
    ],
    "client/ClientLineState.java": [
        ("        /** §fly: client game time the rise began, -1 when none is on — the body climbs over its first eight ticks. */",
         "        /** §hooked-fish: client game time the take began, -1 when none is on — the body climbs over its first eight ticks. */"),
        ("                if (biting && !species.isEmpty()) heading = (float) Math.atan2(fwdZ, fwdX);   // §fly: a rising fish faces away from the angler, under the fly",
         "                if (biting && !species.isEmpty()) heading = (float) Math.atan2(fwdZ, fwdX);   // §hooked-fish: a fish on the take faces away from the angler"),
        ("            // §line-glide: the water end walks between the server's block centres (the fly's drift, a strip)",
         "            // §line-glide: the water end walks between the server's block centres (a drifting float, a retrieve)"),
        ("        // §fly: the 40-tick refresh names no fish; during a rise it must not wipe the one the rise sent",
         "        // §hooked-fish: the 40-tick refresh names no fish; during a take it must not wipe the one the take sent"),
        ("    /** §fly-2: our own line is out and nothing is happening on it — a click on the fly rod is a hold. */",
         "    /** §jig-2: our own line is out and nothing is happening on it — a click over an ice hole is a hold. */"),
    ],
    "client/HookedFishRenderer.java": [
        ("        if (!(state.fighting || state.biting) || state.species.isEmpty() || mc.level == null) return;   // §fly: drawn on the rise too",
         "        if (!(state.fighting || state.biting) || state.species.isEmpty() || mc.level == null) return;   // §hooked-fish: drawn on the take too"),
        ("        // §fly: the rise — the body comes up under the fly, nose up, over the first eight ticks of the take",
         "        // §hooked-fish: the body comes up under the bait, nose up, over the first eight ticks of the take"),
        ("        pose.translate(at.x, at.y + riseY, at.z);   // §fly", "        pose.translate(at.x, at.y + riseY, at.z);"),
        ("        pose.mulPose(Axis.ZP.rotationDegrees((rising ? risePitch : state.pitch) + Mth.sin(time * 0.05f) * 2f));   // §fly",
         "        pose.mulPose(Axis.ZP.rotationDegrees((rising ? risePitch : state.pitch) + Mth.sin(time * 0.05f) * 2f));"),
    ],
    "client/RodItemRenderer.java": [
        ("        String rodKey = rod.rodType().modelKey();   // §fly: the fly rod borrows the ultralight blank",
         "        String rodKey = rod.rodType().modelKey();"),
        ("        String rodKey = stack.getItem() instanceof RodItem r ? r.rodType().modelKey() : \"bamboo\";   // §fly",
         "        String rodKey = stack.getItem() instanceof RodItem r ? r.rodType().modelKey() : \"bamboo\";"),
    ],
    "engine/BiteEngine.java": [
        ("            // §fly-bait-id: a profile may rate a fly TYPE by name (fly_dry_fly, fly_nymph, ...) the way it rates\n"
         "            // any bait; a species that says nothing takes the family's generic affinity for the template.",
         "            // §tying: a profile may rate a tied TEMPLATE by name (fly_nymph, fly_streamer, fly_pellet, ...) the\n"
         "            // way it rates any bait; a species that says nothing takes the family's generic affinity for it."),
    ],
}


def run(tree):
    for rel, edits in EDITS.items():
        p = os.path.join(tree, J, rel)
        for old, new in edits:
            swap(p, old, new, rel)
    print("  tidied:", os.path.basename(tree.rstrip("/\\")))


for t in (sys.argv[1:] or TREES):
    run(t)
