# -*- coding: utf-8 -*-
"""§fly stream B: the rhythm cast and the drift are wired in, and the two needles agree.

    py -X utf8 tools/check_fly_b.py [root]

  1. FlyBeatPacket (C2S) and FlyCastPacket (S2C: receiver AND the dedicated-server payload type) are
     registered in ModNetwork.
  2. FlyCast exposes the contract's API: begin / beat / release / takeQuality / cancel.
  3. The four anchors are patched: RodItem.use begins the rhythm, chargedCast takes its power from
     FlyCast.release and applies flyLanded, handleRodUse has the FLY branch before the ice one, tick
     calls flyDrift inside the FLOAT/BOTTOM wait; plus FishingSession's fields, ClientHud's render and
     yield, ClientInit's tick.
  4. FlyCast.marker is the float QTE's marker, byte for byte after whitespace — parsed out of both
     files and compared, so the server's rhythm can never drift from the timing the client draws.
  5. lang_fly_b.json: the three languages carry the same key set, and every key the Java asks for.
"""
import io, json, os, re, sys

ROOT = sys.argv[1] if len(sys.argv) > 1 else os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
J = os.path.join(ROOT, "common/src/main/java/com/riverfishing")
fails = []


def read(rel):
    return io.open(os.path.join(J, rel), encoding="utf-8").read()


def need(text, needle, what):
    if needle not in text:
        fails.append(what + ": missing `" + needle[:60] + "`")


net = read("network/ModNetwork.java")
need(net, "NetworkManager.Side.C2S, FlyBeatPacket.TYPE", "ModNetwork C2S FlyBeatPacket")
need(net, "NetworkManager.Side.S2C, FlyCastPacket.TYPE", "ModNetwork S2C FlyCastPacket receiver")
need(net, "registerS2CPayloadType(FlyCastPacket.TYPE", "ModNetwork S2C FlyCastPacket payload type")
for rel in ("network/FlyBeatPacket.java", "network/FlyCastPacket.java", "client/FlyCastClient.java", "fishing/FlyCast.java"):
    if not os.path.exists(os.path.join(J, rel)):
        fails.append("new file missing: " + rel)

fc = read("fishing/FlyCast.java") if os.path.exists(os.path.join(J, "fishing/FlyCast.java")) else ""
for sig in ("public static void begin(ServerPlayer sp, long now)",
            "public static void beat(ServerPlayer sp, long now)",
            "public static float release(ServerPlayer sp, ItemStack rod, long now)",
            "public static int takeQuality(ServerPlayer sp)",
            "public static void cancel(ServerPlayer sp)"):
    need(fc, sig, "FlyCast API")

rod = read("item/RodItem.java")
need(rod, "FishingManager.flyCastBegin(sp)", "RodItem.use begins the rhythm")
fm = read("fishing/FishingManager.java")
need(fm, "power = FlyCast.release(sp, held, level.getGameTime())", "chargedCast takes the rhythm's power")
need(fm, "flyLanded(sp, SESSIONS.get(sp.getUUID()), FlyCast.takeQuality(sp))", "chargedCast applies the landing")
need(fm, "addLineWear(session.rodStackRef, 6)", "wind knot wears the line")
i_fly = fm.find("session.ctx.rod == RodType.FLY) {\n                flyUse(")
i_ice = fm.find("} else if (session.iceFishing && session.rodClass != RodClass.ACTIVE) {")
if i_fly < 0 or i_ice < 0 or i_fly > i_ice:
    fails.append("handleRodUse: the FLY branch must come before the ice branch")
i_wait = fm.find("// FLOAT / BOTTOM: wait for the bite, then a window to strike.")
i_drift = fm.find("flyDrift(level, sp, session, now);   // §fly")
if i_wait < 0 or i_drift < 0 or i_drift - i_wait > 200:
    fails.append("tick: flyDrift is not at the top of the FLOAT/BOTTOM wait block")
need(fm, "session.biteAtTick = now + 25;\n        }\n    }", "flyDrift pushes the bite the dead-lure way")
fs = read("fishing/FishingSession.java")
for f in ("public int flyDrag;", "public long flyDriftEnd;", "public boolean flyStraight;", "public int flyMends;"):
    need(fs, f, "FishingSession fields")
hud = read("client/ClientHud.java")
need(hud, "FlyCastClient.render(graphics", "ClientHud renders the gauge")
i_r = hud.find("FlyCastClient.render(graphics")
i_c = hud.find("renderCastPower(graphics, mc);")
if i_r < 0 or i_c < 0 or i_r > i_c:
    fails.append("ClientHud: FlyCastClient.render must come before renderCastPower")
need(hud, "if (FlyCastClient.isActive()) return;", "renderCastPower yields to the gauge")
need(read("client/ClientInit.java"), "ClientTickEvent.CLIENT_POST.register(FlyCastClient::tick)", "ClientInit ticks the beats")

# 4. The needle: the same function in both files, modulo whitespace.
def marker_body(text, what):
    m = re.search(r"private static float marker\(long elapsed, int period\) \{(.*?)\n    \}", text, re.S)
    if not m:
        fails.append(what + ": marker(long, int) not found")
        return None
    return re.sub(r"\s+", " ", m.group(1)).strip()

a, b = marker_body(fm, "FishingManager"), marker_body(fc, "FlyCast")
if a is not None and b is not None and a != b:
    fails.append("FlyCast.marker differs from FishingManager.marker:\n  %s\n  %s" % (a, b))

# 5. Lang: identical key sets, and every key the code asks for.
lang = json.load(io.open(os.path.join(ROOT, "tools/patches/lang_fly_b.json"), encoding="utf-8"))
keys = {l: set(d) for l, d in lang.items()}
if set(keys) != {"en_us", "ru_ru", "uk_ua"} or len({frozenset(k) for k in keys.values()}) != 1:
    fails.append("lang_fly_b.json: the three languages do not carry the same keys")
# Only this stream's keys: stream C's fly_too_fast / fly_too_slow live in the same file and in lang_fly_c.
own = "fly_straight|fly_pickup|fly_tight|fly_splash|fly_knot|fly_beats"
used = set(re.findall(r'"((?:message|gui)\.riverfishing\.(?:%s))"' % own, fm + read("client/FlyCastClient.java")))
missing = used - keys.get("en_us", set())
if missing:
    fails.append("lang_fly_b.json lacks keys the code uses: %s" % sorted(missing))
for l, d in lang.items():
    for k, v in d.items():
        if re.search(r"%(?![sn%]|\d\$s)", v):
            fails.append("%s %s: a format the game does not understand" % (l, k))

if fails:
    print("FAILED:")
    for x in fails:
        print("  " + x)
    sys.exit(1)
print("fly B: packets both ways, FlyCast API, four anchors + session/HUD/tick patched, markers identical, lang complete")
