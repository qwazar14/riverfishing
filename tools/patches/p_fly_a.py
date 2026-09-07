# -*- coding: utf-8 -*-
"""§fly — Stream A, the tackle: a fly rod, a fly rig, the fly-only lure slot, the tooltip line.

    py -X utf8 tools/patches/p_fly_a.py <repo root>

Contract: docs/design/fly-api.md, "Stream A". Idempotent: every inserted block carries a §fly marker
and is skipped when already present; every anchor must occur exactly once or the script exits 1.
"""
import io, os, sys

ROOT = sys.argv[1]
J = os.path.join(ROOT, "common/src/main/java/com/riverfishing")


def patch(path, pairs):
    s = io.open(path, encoding="utf-8").read()
    done = 0
    for old, new in pairs:
        if new in s:
            continue  # §fly marker already there: a rerun is a no-op
        n = s.count(old)
        if n != 1:
            print("ANCHOR x%d in %s:\n%s" % (n, path, old))
            sys.exit(1)
        s = s.replace(old, new, 1)
        done += 1
    io.open(path, "w", encoding="utf-8", newline="\n").write(s)
    print("  %-28s %d block(s) applied" % (os.path.basename(path), done))


# 1. RodType.FLY — a FLOAT-flow rod (wait for the bite, strike QTE) that takes the small reels.
patch(os.path.join(J, "component/RodType.java"), [
    ("    TROLLING  (\"trolling\",  12,   true,     10000,  14000,  150,    600,    false);\n",
     "    TROLLING  (\"trolling\",  12,   true,     10000,  14000,  150,    600,    false),\n"
     "    // §fly: the line is the weight — no cast range, distance comes from the rhythm cast. The\n"
     "    // small reels (1000–2000) stand in for a fly reel in phase 1.\n"
     "    FLY       (\"fly\",        9,   true,     1000,   2000,   0,      0,      false);\n"),
    ("    public String jsonKey() { return jsonKey; }\n",
     "    public String jsonKey() { return jsonKey; }\n"
     "    /**\n"
     "     * §fly: the rod whose sprites and 3D blank this rod is drawn with. ponytail: the fly rod borrows\n"
     "     * the ultralight blank until phase 3 draws its own — then this returns jsonKey for it too.\n"
     "     */\n"
     "    public String modelKey() { return this == FLY ? \"ultralight\" : jsonKey; }\n"),
    ("            case STICK, BAMBOO, POLE, WINTER -> RodClass.FLOAT;\n",
     "            case STICK, BAMBOO, POLE, WINTER, FLY -> RodClass.FLOAT;   // §fly: wait, then strike\n"),
    ("            case WINTER -> RigType.WINTER;               // a single mormyshka\n",
     "            case WINTER -> RigType.WINTER;               // a single mormyshka\n"
     "            case FLY -> RigType.FLY;                     // §fly: tippet + a tied fly\n"),
])

# 2. RigType.FLY + its layout: a leader slot (the tippet) and a lure slot (the fly).
patch(os.path.join(J, "component/RigType.java"), [
    ("    CATFISH   (\"catfish\",     95,   1,   true);\n",
     "    CATFISH   (\"catfish\",     95,   1,   true),\n"
     "    FLY       (\"fly\",          2,   1,   true);   // §fly: a fly weighs nothing; the leader is the tippet\n"),
])
patch(os.path.join(J, "rig/RigLayout.java"), [
    ("            case CATFISH -> new SlotRole[]{LEADER, HOOK, BAIT};\n",
     "            case CATFISH -> new SlotRole[]{LEADER, HOOK, BAIT};\n"
     "            case FLY -> new SlotRole[]{LEADER, LURE};      // §fly: tippet + fly\n"),
])
patch(os.path.join(J, "menu/RigMenu.java"), [
    ("        public boolean mayPlace(ItemStack stack) {\n            return role.accepts(stack);\n",
     "        public boolean mayPlace(ItemStack stack) {\n"
     "            // §fly: the fly rig's lure slot takes ONLY a tied fly — a spoon on a tippet casts nothing\n"
     "            if (type == RigType.FLY && role == SlotRole.LURE) return stack.getItem() instanceof com.riverfishing.item.TiedLureItem;\n"
     "            return role.accepts(stack);\n"),
])

# 3. The rod item: the registration loops already cover fly_rod and rig_fly; only the durability
#    decision and the tooltip line are new.
patch(os.path.join(J, "registry/ModItems.java"), [
    ("        if (\"ultralight\".equals(key)) return 144;\n",
     "        if (\"ultralight\".equals(key)) return 144;\n"
     "        if (\"fly\".equals(key)) return 144;          // §fly: as light a blank as the ultralight it borrows\n"),
])
patch(os.path.join(J, "item/RodItem.java"), [
    ("        if (rodType != com.riverfishing.component.RodType.WINTER) {\n"
     "            tooltip.add(Component.translatable(\"tooltip.riverfishing.rod_class.\"\n",
     "        if (rodType == com.riverfishing.component.RodType.FLY) {\n"
     "            // §fly: FLOAT flow underneath, but \"never reel\" is not what a fly rod wants to hear\n"
     "            tooltip.add(Component.translatable(\"tooltip.riverfishing.rod_class.fly\").withStyle(ChatFormatting.GOLD));\n"
     "        } else if (rodType != com.riverfishing.component.RodType.WINTER) {\n"
     "            tooltip.add(Component.translatable(\"tooltip.riverfishing.rod_class.\"\n"),
])

# 4. The client draws the fly rod as the ultralight: every blank lookup goes through modelKey().
patch(os.path.join(J, "client/RodItemRenderer.java"), [
    ("        String rodKey = rod.rodType().jsonKey();\n",
     "        String rodKey = rod.rodType().modelKey();   // §fly: the fly rod borrows the ultralight blank\n"),
    ("        String rodKey = stack.getItem() instanceof RodItem r ? r.rodType().jsonKey() : \"bamboo\";\n",
     "        String rodKey = stack.getItem() instanceof RodItem r ? r.rodType().modelKey() : \"bamboo\";   // §fly\n"),
])
patch(os.path.join(J, "client/RodModelLayers.java"), [
    ("    public static final String[] ROD_KEYS =\n",
     "    // §fly: no \"fly\" here on purpose — the fly rod is drawn with the ultralight's layers\n"
     "    // (RodType.modelKey), which are already listed. Add it when it gets a blank of its own.\n"
     "    public static final String[] ROD_KEYS =\n"),
])

print("fly-a: FLY rod + rig, the fly-only lure slot, the tooltip line, the ultralight blank borrowed")
