# -*- coding: utf-8 -*-
"""§chart-server: the two ends of a chart parcel agree about what a byte means.

    py -X utf8 tools/check_chart_sync.py

A chart is written by ChartData on the server and drawn by ClientSoundings on the client, and it
travels between them as raw bytes with no schema — one long array of column keys and one byte array of
values. Nothing in the compiler connects those two classes: the server cannot import the client's
constants (a dedicated server must never load a client class), so the numbers are declared twice.

Two declarations of the same number is a bug waiting for a refactor, and the failure is silent and
ugly — every land column drawn as water, or an eleven-metre hole drawn as a bank. So this file is the
joint: it reads both ends and fails if they ever stop meaning the same thing.
"""
import io, os, re, sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
J = os.path.join(ROOT, "common/src/main/java/com/riverfishing")
SRV = io.open(os.path.join(J, "fishing/ChartData.java"), encoding="utf-8").read()
CLI = io.open(os.path.join(J, "client/ClientSoundings.java"), encoding="utf-8").read()
STATE = io.open(os.path.join(J, "client/FinderState.java"), encoding="utf-8").read()

fails = []


def die(msg):
    fails.append(msg)


def bytes_of(src, what):
    m = re.search(r"byte LAND = (\d+), WATER = (\d+), DEPTH0 = (\d+);", src)
    if not m:
        print("FAILED: cannot find the three chart bytes in %s" % what)
        sys.exit(1)
    return tuple(int(g) for g in m.groups())


# ---- 1. a byte means the same thing at both ends ---------------------------------------------------
srv, cli = bytes_of(SRV, "ChartData"), bytes_of(CLI, "ClientSoundings")
if srv != cli:
    die("the server calls them LAND/WATER/DEPTH0 = %s and the client %s — every column of every chart "
        "would be drawn as the wrong thing" % (srv, cli))
if not (srv[0] < srv[1] < srv[2]):
    die("the three bytes are not in order %s: the merge rule everywhere is 'the bigger byte knows more' "
        "(a depth beats water beats land) and it stops holding" % (srv,))

# ---- 2. a column is packed the same way ------------------------------------------------------------
def packing(src, cls):
    m = re.search(r"long key\(int x, int z\) \{\s*return ([^;]+);", src)
    if not m:
        die("%s has no key(x, z) any more — the two sides file columns differently" % cls)
        return None
    return re.sub(r"\s+", " ", m.group(1))


if packing(SRV, "ChartData") != packing(CLI, "ClientSoundings"):
    die("ChartData packs a column key as %s and ClientSoundings as %s — the charts would land at "
        "different coordinates" % (packing(SRV, "ChartData"), packing(CLI, "ClientSoundings")))

# ---- 3. every key on the wire is written by one end and read by the other ---------------------------
written = set(re.findall(r'put(?:LongArray|ByteArray|String|Boolean)\("(\w+)"', SRV))
read = set(re.findall(r'get(?:LongArray|ByteArray|String|Boolean)(?:Or)?\("(\w+)"', CLI + STATE))
for k in ("chartsync", "chart", "k", "v", "last"):
    if k not in written:
        die("the server no longer sends %r in a chart parcel" % k)
    if k not in read:
        die("the client no longer reads %r out of a chart parcel" % k)
for k in ("sk", "sv"):
    if k in written and k not in read:
        die("the server sends the marks as %r and the client never reads them" % k)

# ---- 4. the same window, measured the same way ------------------------------------------------------
for src, cls in ((SRV, "ChartData"), (CLI, "ClientSoundings")):
    if "MAP_REACH" not in src:
        die("%s folds a sounding in without FishingManager.MAP_REACH — the window it walks is its own "
            "guess at the one the other end sent" % cls)

# ---- 5. a parcel is not a sounding ------------------------------------------------------------------
head = STATE[STATE.index("public static void accept("):][:700]
if "chartsync" not in head.split("last = data")[0]:
    die("FinderState.accept reads a chart parcel as a sounding — it would land on the trace, become "
        "the screen's 'last reading' and open a window with no fish in it")
if "absorb" not in CLI:
    die("ClientSoundings cannot absorb a parcel any more; a traded sounder arrives empty")

# ---- 6. the streaming is bounded --------------------------------------------------------------------
m = re.search(r"int CHUNK = (\d+);", SRV)
if not m:
    die("ChartData has no CHUNK any more — a big chart would go out as one packet and be dropped")
elif int(m.group(1)) * 9 > 900_000:
    die("a chart parcel is %d columns, about %.1f MB — over what a custom payload will carry"
        % (int(m.group(1)), int(m.group(1)) * 9 / 1e6))

if fails:
    print("FAILED:")
    for x in fails:
        print("  " + x)
    sys.exit(1)
print("chart sync: land/water/depth %s at both ends, the same column packing, %d columns a parcel "
      "(~%d KB), %d keys on the wire" % (srv, int(m.group(1)), int(m.group(1)) * 9 / 1024, len(written)))
