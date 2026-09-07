# -*- coding: utf-8 -*-
"""§outclassed-hint: a 154 kg sturgeon on a carp rod and 0.40 braid — the game knew the line was out-pulled
five times over and said only "tackle at its limit". Now the hook-up names the ratio and the rule (open the
drag in a run, rod across, let it tire), and says it again when the first run begins, which is the moment
a player would crank. And the played-out fight lands faster: the open-drag landing rate is 0.9 of the
fatigue rate (was 0.55), so a giant is a quarter-hour, not twenty-five minutes.
Idempotent; run on each tree.  py tools/patches/p_outclassed_hint.py [tree ...]"""
import io, json, os, sys
from collections import OrderedDict

TREES = [r"C:/Users/Qwazar/VS Code Projects/fishing mod", r"C:/Users/Qwazar/wt/rf1201", r"C:/Users/Qwazar/wt/rf26"]
FM = "common/src/main/java/com/riverfishing/fishing/FishingManager.java"
FS = "common/src/main/java/com/riverfishing/fishing/FishingSession.java"
LANG = "common/src/main/resources/assets/riverfishing/lang"
MSG = {
    "en_us": "Out-pulled ×%s: don't crank in a run — open the drag, rod across, let it tire",
    "ru_ru": "Перевес рыбы ×%s: не крутите в рывке — фрикцион открыт, удилище поперёк, пусть выматывается",
    "uk_ua": "Перевага риби ×%s: не крутіть у ривку — фрикціон відкритий, вудилище впоперек, хай вимотується",
}


def rd(p): return io.open(p, encoding="utf-8").read()
def wr(p, s): io.open(p, "w", encoding="utf-8", newline="\n").write(s)


def sub(path, old, new, what):
    s = rd(path)
    if new in s: return
    assert old in s, what + " @ " + path
    wr(path, s.replace(old, new, 1))


HINT = ('                actionbar(sp, Component.translatable("message.riverfishing.outclassed",\n'
        '                        String.format(java.util.Locale.ROOT, "%.1f", session.requiredKg / Math.max(0.5, session.requiredKg * session.tackleMargin)))\n'
        '                        .withStyle(ChatFormatting.GOLD));\n')


def run(tree):
    j = lambda *a: os.path.join(tree, *a)
    sub(j(FS), "    public boolean outclassed;", "    public boolean outclassedHinted;   // §outclassed-hint: said once more at the first run\n    public boolean outclassed;", "session field")
    fm = j(FM)
    sub(fm, "        session.outclassed = session.tackleMargin < 0.85;   // §outclassed\n",
        "        session.outclassed = session.tackleMargin < 0.85;   // §outclassed\n"
        "        if (session.outclassed) {   // §outclassed-hint: the ratio and the rule, at the hook-up\n" + HINT.replace("                ", "            ", 1).replace("\n                ", "\n            ") + "        }\n", "hook-up hint")
    sub(fm, "                session.course = FightCourse.forPattern(session.fightPattern, session.runIndex++, random);\n                session.barState = -1;   // force the bar to re-title with the new course\n",
        "                session.course = FightCourse.forPattern(session.fightPattern, session.runIndex++, random);\n                session.barState = -1;   // force the bar to re-title with the new course\n"
        "                if (session.outclassed && !session.outclassedHinted) {   // §outclassed-hint: again, as the first run starts\n"
        "                    session.outclassedHinted = true;\n" + HINT.replace("                ", "                    ", 1).replace("\n                        ", "\n                            ") + "                }\n", "run hint")
    sub(fm, "session.landProgress + session.fatigueRunTick * 0.55 * courseGain);", "session.landProgress + session.fatigueRunTick * 0.9 * courseGain);   // §outclassed-hint: 0.9, was 0.55", "landing rate")
    for code, text in MSG.items():
        p = j(LANG, code + ".json"); d = json.loads(rd(p), object_pairs_hook=OrderedDict)
        if d.get("message.riverfishing.outclassed") == text: continue
        out = OrderedDict()
        for k, v in d.items():
            if k == "message.riverfishing.outclassed": continue
            out[k] = v
            if k == "message.riverfishing.tackle_limit": out["message.riverfishing.outclassed"] = text
        wr(p, json.dumps(out, ensure_ascii=False, indent=2) + "\n")
    print("  patched", os.path.basename(tree.rstrip("/\\")))


for t in (sys.argv[1:] or TREES): run(t)
