# -*- coding: utf-8 -*-
"""§fly stream B: the rhythm cast and the drift, wired into the existing files.

    py -X utf8 tools/patches/p_fly_b.py <repo root>

Idempotent: every inserted block carries a `§fly` marker and is skipped when already present, so a
rerun is a no-op. A missing or non-unique anchor is printed and the script exits 1 — a silent partial
patch is worse than none. New classes (FlyCast, FlyBeatPacket, FlyCastPacket, FlyCastClient) are
written as files, not patched in. Stream A's RodType.FLY is referenced, never added here.
"""
import io, os, sys

ROOT = sys.argv[1] if len(sys.argv) > 1 else os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
SRC = os.path.join(ROOT, "common", "src", "main", "java", "com", "riverfishing")
MARK = "§fly"


def sub1(rel, old, new):
    path = os.path.join(SRC, rel)
    text = io.open(path, encoding="utf-8").read()
    if new in text:
        return False
    if text.count(old) != 1:
        print("anchor not found (or not unique) in %s:\n%s" % (rel, old))
        sys.exit(1)
    io.open(path, "w", encoding="utf-8", newline="\n").write(text.replace(old, new))
    return True


changed = 0

# ---- item/RodItem: the hold on a fly rod with no line out begins the rhythm ----------------------
A = ("        player.startUsingItem(hand);\n"
     "        return InteractionResultHolder.consume(rod);\n"
     "    }\n"
     "\n"
     "    /**\n"
     "     * Anvil repair")
changed += sub1("item/RodItem.java", A,
    "        // " + MARK + ": on a fly rod the hold is the RHYTHM, not a charge — the needle starts here.\n"
    "        if (!level.isClientSide && rodType == RodType.FLY && player instanceof ServerPlayer sp) FishingManager.flyCastBegin(sp);\n"
    + A)

# ---- fishing/FishingSession: the drift's state --------------------------------------------------
A = "    public boolean outclassed;      // §outclassed: the line is weaker than the pull — play it out, never reel it\n"
changed += sub1("fishing/FishingSession.java", A, A +
    "    // " + MARK + ": the drift — the line bows across the current (drag 0..100), the drift's last tick,\n"
    "    // whether it has come tight straight below the angler, and how many mends this drift has had.\n"
    "    public int flyDrag;\n"
    "    public long flyDriftEnd;\n"
    "    public boolean flyStraight;\n"
    "    public int flyMends;\n")

# ---- fishing/FishingManager: chargedCast takes its power from the rhythm; the landing's quality ----
A = ("    public static boolean chargedCast(ServerPlayer sp, InteractionHand hand, float power) {\n"
     "        ServerLevel level = sp.serverLevel();\n"
     "        if (SESSIONS.containsKey(sp.getUUID())) return false;\n"
     "        return startCast(sp, level, hand, level.getGameTime(), Mth.clamp(power, 0.05f, 1.0f));\n"
     "    }\n")
changed += sub1("fishing/FishingManager.java", A,
    "    public static boolean chargedCast(ServerPlayer sp, InteractionHand hand, float power) {\n"
    "        ServerLevel level = sp.serverLevel();\n"
    "        // " + MARK + ": on a fly rod the rhythm decides the power, not the charge — and the delivery's\n"
    "        // quality (tight / splash / wind knot) lands with the line.\n"
    "        ItemStack held = sp.getItemInHand(hand);\n"
    "        boolean fly = held.getItem() instanceof RodItem ri && ri.rodType() == RodType.FLY;\n"
    "        if (fly) power = FlyCast.release(sp, held, level.getGameTime());\n"
    "        if (SESSIONS.containsKey(sp.getUUID())) {\n"
    "            if (fly) FlyCast.cancel(sp);\n"
    "            return false;\n"
    "        }\n"
    "        boolean cast = startCast(sp, level, hand, level.getGameTime(), Mth.clamp(power, 0.05f, 1.0f));\n"
    "        if (fly) {\n"
    "            if (cast) flyLanded(sp, SESSIONS.get(sp.getUUID()), FlyCast.takeQuality(sp));\n"
    "            else FlyCast.cancel(sp);\n"
    "        }\n"
    "        return cast;\n"
    "    }\n"
    "\n"
    "    /** " + MARK + ": the hold on a fly rod began — start the needle. */\n"
    "    public static void flyCastBegin(ServerPlayer sp) {\n"
    "        FlyCast.begin(sp, sp.serverLevel().getGameTime());\n"
    "    }\n"
    "\n"
    "    /**\n"
    "     * " + MARK + ": what the delivery did to the water. A tight loop lands soft; an open loop dumps the\n"
    "     * line on the fish (the spot is wary for a few seconds); a tailing loop knots the tippet as well.\n"
    "     */\n"
    "    private static void flyLanded(ServerPlayer sp, FishingSession session, int quality) {\n"
    "        if (session == null) return;\n"
    "        if (quality == 0) {\n"
    "            actionbar(sp, Component.translatable(\"message.riverfishing.fly_tight\").withStyle(ChatFormatting.GREEN));\n"
    "            return;\n"
    "        }\n"
    "        SpookTracker.onCastLanded(sp.serverLevel(), session.target, 0.15);\n"
    "        if (quality == 2) {\n"
    "            addLineWear(session.rodStackRef, 6);\n"
    "            actionbar(sp, Component.translatable(\"message.riverfishing.fly_knot\").withStyle(ChatFormatting.RED));\n"
    "        } else {\n"
    "            actionbar(sp, Component.translatable(\"message.riverfishing.fly_splash\").withStyle(ChatFormatting.YELLOW));\n"
    "        }\n"
    "    }\n"
    "\n"
    "    /**\n"
    "     * " + MARK + ": the drift. The fly rides the flow a block every half second; the line bows across the\n"
    "     * current and DRAGS the fly (past 60 the fish refuse it — mend to reset); after twelve seconds the\n"
    "     * line is straight below the angler and catches nothing until it is picked up and cast again.\n"
    "     */\n"
    "    private static void flyDrift(ServerLevel level, ServerPlayer sp, FishingSession session, long now) {\n"
    "        if (session.flyDriftEnd == 0) session.flyDriftEnd = now + 240;\n"
    "        if (!session.flyStraight && now >= session.flyDriftEnd) {\n"
    "            session.flyStraight = true;\n"
    "            actionbar(sp, Component.translatable(\"message.riverfishing.fly_straight\").withStyle(ChatFormatting.GRAY));\n"
    "        }\n"
    "        if (!session.flyStraight && now % 10 == 0) {\n"
    "            BlockPos t = session.target;\n"
    "            net.minecraft.world.phys.Vec3 flow = level.getFluidState(t).getFlow(level, t);\n"
    "            double fl = Math.sqrt(flow.x * flow.x + flow.z * flow.z);\n"
    "            if (fl >= 0.05) {\n"
    "                // Still water leaves the line lying slack; only a current bows it.\n"
    "                session.flyDrag = Math.min(100, session.flyDrag + 3);\n"
    "                BlockPos next = findWaterColumn(level, t.getX() + 0.5 + Math.round(flow.x / fl),\n"
    "                        t.getY() + 1.0, t.getZ() + 0.5 + Math.round(flow.z / fl));\n"
    "                if (next != null && !next.equals(t)) {\n"
    "                    session.target = next;\n"
    "                    ModNetwork.toTracking(sp, new LineSyncPacket(sp.getId(), true, next, 0f,\n"
    "                            session.lineColor, session.floatKind, false));\n"
    "                }\n"
    "            }\n"
    "        }\n"
    "        // A dragging fly and a straight line are the dead lure's rule: the take keeps getting pushed out.\n"
    "        if ((session.flyDrag > 60 || session.flyStraight) && now >= session.biteAtTick - 5) {\n"
    "            session.biteAtTick = now + 25;\n"
    "        }\n"
    "    }\n"
    "\n"
    "    /** " + MARK + ": a click on a drifting fly line — sneak picks up, otherwise it is a mend. */\n"
    "    private static void flyUse(ServerPlayer sp, ServerLevel level, FishingSession session, long now) {\n"
    "        if (sp.isShiftKeyDown()) {\n"
    "            endSession(sp, session);\n"
    "            actionbar(sp, Component.translatable(\"message.riverfishing.fly_pickup\"));\n"
    "            return;\n"
    "        }\n"
    "        session.flyDrag = 0;\n"
    "        session.flyMends++;\n"
    "        level.playSound(null, session.target, SoundEvents.FISHING_BOBBER_THROW, SoundSource.PLAYERS, 0.35f, 1.6f);\n"
    "        // A third flip of the line in one drift is a line slapped on the water: the fish under it notice.\n"
    "        if (session.flyMends >= 3) SpookTracker.onCastLanded(level, session.target, 0.15);\n"
    "    }\n")

# ---- fishing/FishingManager: handleRodUse — the mend and the pickup ------------------------------
A = "            } else if (session.iceFishing && session.rodClass != RodClass.ACTIVE) {\n"
changed += sub1("fishing/FishingManager.java", A,
    "            } else if (session.ctx != null && session.ctx.rod == RodType.FLY) {\n"
    "                flyUse(sp, level, session, now);               // " + MARK + ": mend, or sneak to pick up\n"
    + A)

# ---- fishing/FishingManager: tick — the drift runs while the fly line waits ----------------------
A = ("        // FLOAT / BOTTOM: wait for the bite, then a window to strike.\n"
     "        if (!session.bitten) {\n")
changed += sub1("fishing/FishingManager.java", A, A +
    "            if (session.ctx != null && session.ctx.rod == RodType.FLY) flyDrift(level, sp, session, now);   // " + MARK + "\n")

# ---- network/ModNetwork: the two packets, both ways ---------------------------------------------
A = ("        NetworkManager.registerReceiver(NetworkManager.Side.C2S, FightInputPacket.TYPE, FightInputPacket.STREAM_CODEC,\n"
     "                (payload, ctx) -> ctx.queue(() -> payload.handleServer(ctx)));\n")
changed += sub1("network/ModNetwork.java", A, A +
    "        // " + MARK + ": a sneak tap on a stop of the fly cast's rhythm.\n"
    "        NetworkManager.registerReceiver(NetworkManager.Side.C2S, FlyBeatPacket.TYPE, FlyBeatPacket.STREAM_CODEC,\n"
    "                (payload, ctx) -> ctx.queue(() -> payload.handleServer(ctx)));\n")
A = "            NetworkManager.registerS2CPayloadType(FloatTimingPacket.TYPE, FloatTimingPacket.STREAM_CODEC);\n"
changed += sub1("network/ModNetwork.java", A, A +
    "            NetworkManager.registerS2CPayloadType(FlyCastPacket.TYPE, FlyCastPacket.STREAM_CODEC);   // " + MARK + "\n")
A = ("        NetworkManager.registerReceiver(NetworkManager.Side.S2C, FloatTimingPacket.TYPE, FloatTimingPacket.STREAM_CODEC,\n"
     "                (payload, ctx) -> ctx.queue(payload::handleClient));\n")
changed += sub1("network/ModNetwork.java", A, A +
    "        // " + MARK + ": the rhythm gauge, on/off and after every beat.\n"
    "        NetworkManager.registerReceiver(NetworkManager.Side.S2C, FlyCastPacket.TYPE, FlyCastPacket.STREAM_CODEC,\n"
    "                (payload, ctx) -> ctx.queue(payload::handleClient));\n")

# ---- client/ClientHud: the gauge, and the charge bar steps aside for it -------------------------
A = "        renderCastPower(graphics, mc);\n"
changed += sub1("client/ClientHud.java", A,
    "        // " + MARK + ": the rhythm gauge sits where the charge bar would — that one yields (below).\n"
    "        FlyCastClient.render(graphics, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight(), partialTick);\n"
    + A)
A = ("        // charging. Once a line is out, holding is a RETRIEVE, not a charge, so hide it (next line).\n"
     "        if (ClientLineState.active()) return;\n")
changed += sub1("client/ClientHud.java", A, A +
    "        if (FlyCastClient.isActive()) return;   // " + MARK + ": the fly rod's hold is a rhythm, not a charge\n")

# ---- client/ClientInit: the sneak taps are read on the tick -------------------------------------
A = "        ClientTickEvent.CLIENT_POST.register(mc -> ClientLineState.pollFightInput());\n"
changed += sub1("client/ClientInit.java", A, A +
    "        ClientTickEvent.CLIENT_POST.register(FlyCastClient::tick);   // " + MARK + ": the sneak taps on the beats\n")

print("p_fly_b: %d edit(s) applied" % changed if changed else "p_fly_b: already applied (no-op)")
