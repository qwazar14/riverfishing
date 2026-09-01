# -*- coding: utf-8 -*-
"""§live-buffer: no VertexConsumer may be held across another getBuffer().

    py tools/check_live_buffer.py [--selftest]

MultiBufferSource.BufferSource hands out ONE buffer at a time per shared render type. Asking it for a
different type ENDS the batch of the one it handed out last, which turns every consumer still held
from that type into a dead buffer:

    java.lang.IllegalStateException: Not building!
        at com.mojang.blaze3d.vertex.BufferBuilder.ensureBuilding

That is a hard client crash on the render thread, and it is invisible in code review because the two
statements can be fifty lines and one method call apart. It shipped in 0.8.2 exactly like that:
LineRenderer.render() took the lines() buffer once, renderLine() then asked for the line-strand type
(ending it), and drawFloat() drew the bobber into the corpse. Only float rigs draw a bobber, so only
float and pole rods crashed — which is why it read as "casting the stick rod kills the game".

The rule this checks is the one that makes the bug impossible: ask for the buffer where you draw.

A VertexConsumer is FLAGGED when it is used on or after a line that asks for a buffer, other than the
line it was assigned on. Parameters count from the method's first getBuffer, because their buffer was
opened somewhere further up and the call cannot see it.
"""
import io, os, re, sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = "common/src/main/java/com/riverfishing/client"

GET = re.compile(r"\.getBuffer\s*\(")
# Exactly four spaces, then a real modifier: without both, "        if (" and "        line(" read as
# method declarations and the file splits into 45 two-line "methods" that can hold nothing across
# anything. That is how the first cut of this check passed the very crash it was written for.
DECL = re.compile(r"^    (?:@\w+ )*(?:public|private|protected|static|final|synchronized)\b"
                  r"[^=;(]*?(\w+)\s*\(")
ASSIGN = re.compile(r"\b(?:VertexConsumer|var)\s+(\w+)\s*=\s*[^;]*\.getBuffer\s*\(")
PARAM = re.compile(r"\bVertexConsumer\s+(\w+)\s*[,)]")


def methods(lines):
    """(name, start, end) for every method in the file, by its 4-space declaration indent."""
    starts = [i for i, ln in enumerate(lines) if DECL.match(ln) and "=" not in ln.split("(")[0]]
    out = []
    for k, i in enumerate(starts):
        end = starts[k + 1] if k + 1 < len(starts) else len(lines)
        out.append((DECL.match(lines[i]).group(1), i, end))
    return out


def faults(text):
    """Every held-across-getBuffer consumer in this source, as (method, name, line-in-method)."""
    lines = text.split("\n")
    bad = []
    for name, start, end in methods(lines):
        body = lines[start:end]
        gets = [i for i, ln in enumerate(body) if GET.search(ln)]
        if not gets:
            continue                     # nothing here can end anyone's batch
        held = {}                        # consumer -> the line it was assigned on, -1 for a parameter
        for v in PARAM.findall(" ".join(body[:3])):
            held[v] = -1
        for i, ln in enumerate(body):
            for v in ASSIGN.findall(ln):
                held[v] = i
        for v, at in sorted(held.items()):
            later = [g for g in gets if g > at]
            if not later:
                continue
            first = later[0]
            for i in range(first, len(body)):
                if re.search(r"\b%s\b" % re.escape(v), body[i]) and i != at:
                    bad.append((name, v, start + i + 1))
                    break
    return bad


def selftest():
    broken = """
    public static void render(PoseStack pose) {
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer vc = buffers.getBuffer(RenderType.lines());
        renderLine(mc, buffers, vc, m, nrm, player, state, pt);
    }

    private static void renderLine(Minecraft mc, MultiBufferSource buffers, VertexConsumer vc,
                                   Matrix4f m, Matrix3f nrm) {
        VertexConsumer sv = style == null ? vc : buffers.getBuffer(RodRenderTypes.lineStrand(style[4]));
        line(sv, m, nrm, a, b);
        drawFloat(vc, m, nrm, end, true);
    }

    private static void drawFloat(VertexConsumer vc, Matrix4f m, Matrix3f nrm, Vec3 end, boolean p) {
        line(vc, m, nrm, end, end, 1, 2, 3);
    }
"""
    fixed = """
    public static void render(PoseStack pose) {
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        renderLine(mc, buffers, m, nrm, player, state, pt);
    }

    private static void renderLine(Minecraft mc, MultiBufferSource buffers,
                                   Matrix4f m, Matrix3f nrm) {
        VertexConsumer sv = buffers.getBuffer(
                style == null ? RenderType.lines() : RodRenderTypes.lineStrand(style[4]));
        line(sv, m, nrm, a, b);
        drawFloat(buffers.getBuffer(RenderType.lines()), m, nrm, end, true);
    }

    private static void drawFloat(VertexConsumer vc, Matrix4f m, Matrix3f nrm, Vec3 end, boolean p) {
        line(vc, m, nrm, end, end, 1, 2, 3);
    }
"""
    got = faults(broken)
    names = {(m, v) for m, v, _ in got}
    assert ("renderLine", "vc") in names, "MISSED the 0.8.2 crash: %s" % got
    assert not faults(fixed), "flagged the fix: %s" % faults(fixed)
    print("self-test ok: catches the shipped crash, passes the fix")
    return 0


def main():
    folder = os.path.join(ROOT, SRC)
    bad, scanned = [], 0
    for name in sorted(os.listdir(folder)):
        if not name.endswith(".java"):
            continue
        scanned += 1
        text = io.open(os.path.join(folder, name), encoding="utf-8").read()
        for method, v, line in faults(text):
            bad.append((name, method, v, line))

    print("%d client sources scanned" % scanned)
    if bad:
        print("\n%d consumer(s) held across a getBuffer — each one throws \"Not building!\":" % len(bad))
        for name, method, v, line in bad:
            print("  %s:%d  %s() keeps '%s'" % (name, line, method, v))
        return 1
    print("every VertexConsumer is asked for where it is drawn into")
    return 0


if __name__ == "__main__":
    sys.exit(selftest() if "--selftest" in sys.argv else main())
