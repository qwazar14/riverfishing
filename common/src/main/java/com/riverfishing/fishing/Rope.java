package com.riverfishing.fishing;

/**
 * §rope: a fly line as a physical thing — a Verlet chain hanging from the rod tip. No Minecraft in
 * here on purpose: the world is asked through {@link Medium}, so the same class runs in the client
 * tick, on the server later, and in {@link #main} as a self-check. Everything a fly angler does
 * (cast, false cast, shoot, mend, strip, drag, tailing loop) is meant to come out of this and a
 * moving rod tip; nothing in the mod should ever need the word "backcast".
 *
 * <p>Units: blocks = metres, seconds. Point 0 is pinned to the tip; the last point is the fly.
 */
public final class Rope {
    /** What the rope asks of the world. */
    public interface Medium {
        /** Y of the water surface at (x, z) if the point at (x, y, z) is under water, else NaN. */
        double surfaceY(double x, double y, double z);
        /** True inside a block a line cannot pass through. */
        boolean solid(double x, double y, double z);
        /** Current at the point, written to out[0..2]. Still water writes zeros. */
        void flow(double x, double y, double z, double[] out);
    }

    public static final int DEFAULT_N = 32;
    /** Points in the chain — fixed for the rope's life; /rfrod rope segments rebuilds it. */
    public final int n;
    /** Leader + tippet: the last metres do not float, whatever the total length. */
    public double leaderLen = 2.5;
    public static final double MIN_LENGTH = 1.0, MAX_LENGTH = 30.0;
    static final double GRAVITY = 9.81;
    static final double AIR_DRAG = 0.35;        // per metre of speed, per second
    static final double WATER_DRAG = 12.0;      // a floating line stops almost at once
    static final int SUBSTEPS = 4;
    /** Constraint passes: a Gauss-Seidel chain needs about n/2 sweeps to carry a pull end to end. */
    private int passes() { return Math.max(8, n); }
    static final double MAX_PAY_PER_STEP = 0.08; // shooting line: metres per substep (~6 m/s)

    public final double[] x, y, z, px, py, pz;
    private double length = 3.0;
    /** How fast the fly sinks, m/s. 0 floats (dry fly). The line itself always floats. */
    public double flySink = 0.0;
    /** Tension at the tip last step, metres of stretch beyond one segment — the rod's load. */
    public double load01() { return Math.min(1.0, tipStretch / (segLen() * LOAD_STRETCH)); }
    /** Stretch of the first segment, as a fraction of its length, that reads as a fully loaded rod. */
    static final double LOAD_STRETCH = 0.3;
    public double tipStretch;
    private final double[] flow = new double[3];

    public Rope(double tx, double ty, double tz) { this(DEFAULT_N, tx, ty, tz); }

    public Rope(int n, double tx, double ty, double tz) {
        this.n = n;
        x = new double[n]; y = new double[n]; z = new double[n];
        px = new double[n]; py = new double[n]; pz = new double[n];
        for (int i = 0; i < n; i++) {
            x[i] = px[i] = tx;
            y[i] = py[i] = ty - i * segLen();
            z[i] = pz[i] = tz;
        }
    }

    public double length() { return length; }
    public double segLen() { return length / (n - 1); }

    /** Strip: line pulled in by hand. */
    public void strip(double metres) {
        length = Math.max(MIN_LENGTH, length - metres);
    }

    /**
     * One game tick. {@code handOpen}: the line hand lets line run through the guides — the rope
     * pays out only as fast as its own momentum pulls it, never by how long the button is held.
     */
    public void step(double dt, double tx, double ty, double tz, boolean handOpen, Medium m) {
        double h = dt / SUBSTEPS;
        for (int s = 0; s < SUBSTEPS; s++) {
            integrate(h, m);
            x[0] = tx; y[0] = ty; z[0] = tz;
            if (handOpen) payOut(tx, ty, tz);
            for (int p = 0, np = passes(); p < np; p++) constrain((p & 1) == 1);
            x[0] = tx; y[0] = ty; z[0] = tz;
            collide(m);
        }
        double dx = x[1] - tx, dy = y[1] - ty, dz = z[1] - tz;
        tipStretch = Math.max(0, Math.sqrt(dx * dx + dy * dy + dz * dz) - segLen());
    }

    private void integrate(double h, Medium m) {
        int leaderFrom = Math.max(1, n - 1 - (int) Math.ceil(leaderLen / segLen()));
        for (int i = 1; i < n; i++) {
            double vx = (x[i] - px[i]) / h, vy = (y[i] - py[i]) / h, vz = (z[i] - pz[i]) / h;
            double surf = m.surfaceY(x[i], y[i], z[i]);
            boolean wet = !Double.isNaN(surf);
            double ax = 0, ay = -GRAVITY, az = 0;
            if (wet) {
                m.flow(x[i], y[i], z[i], flow);
                // Water drag pulls the point's velocity toward the current.
                ax += (flow[0] - vx) * WATER_DRAG;
                ay += (flow[1] - vy) * WATER_DRAG;
                az += (flow[2] - vz) * WATER_DRAG;
                // The line floats (springs to the surface); the leader hangs neutral; the fly
                // settles toward its own sink speed through the water drag above.
                if (i < leaderFrom) ay += GRAVITY + (surf - y[i]) * 40.0;
                else if (i < n - 1) ay += GRAVITY;
                else ay += GRAVITY - flySink * WATER_DRAG;
            } else {
                double sp = Math.sqrt(vx * vx + vy * vy + vz * vz);
                double k = AIR_DRAG * sp;
                ax -= vx * k; ay -= vy * k; az -= vz * k;
            }
            px[i] = x[i]; py[i] = y[i]; pz[i] = z[i];
            x[i] += vx * h + ax * h * h;
            y[i] += vy * h + ay * h * h;
            z[i] += vz * h + az * h * h;
        }
    }

    /** Hand open: whatever pull the first segment carries beyond its length becomes new line. */
    private void payOut(double tx, double ty, double tz) {
        double dx = x[1] - tx, dy = y[1] - ty, dz = z[1] - tz;
        double stretch = Math.sqrt(dx * dx + dy * dy + dz * dz) - segLen();
        if (stretch > 0) length = Math.min(MAX_LENGTH, length + Math.min(stretch, MAX_PAY_PER_STEP));
    }

    /** One sweep; alternating direction each pass moves a pull down the chain in far fewer sweeps. */
    private void constrain(boolean back) {
        double seg = segLen();
        for (int k = 0; k < n - 1; k++) {
            int i = back ? n - 2 - k : k;
            double dx = x[i + 1] - x[i], dy = y[i + 1] - y[i], dz = z[i + 1] - z[i];
            double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (d < 1e-9) continue;
            double diff = (d - seg) / d;
            // Point 0 is the tip and never moves; everything else shares the correction.
            double wa = i == 0 ? 0 : 0.5, wb = i == 0 ? 1 : 0.5;
            x[i] += dx * diff * wa; y[i] += dy * diff * wa; z[i] += dz * diff * wa;
            x[i + 1] -= dx * diff * wb; y[i + 1] -= dy * diff * wb; z[i + 1] -= dz * diff * wb;
        }
    }

    /** A point inside a block goes back where it was and stops: the line lies on ground and snags. */
    private void collide(Medium m) {
        for (int i = 1; i < n; i++) {
            if (m.solid(x[i], y[i], z[i])) {
                x[i] = px[i]; y[i] = py[i]; z[i] = pz[i];
            }
        }
    }

    // ---- self-check: java Rope.java ---------------------------------------------------------

    public static void main(String[] args) {
        Medium still = new Medium() {
            public double surfaceY(double x, double y, double z) { return y < 60 ? 60 : Double.NaN; }
            public boolean solid(double x, double y, double z) { return y < 55; }
            public void flow(double x, double y, double z, double[] o) { o[0] = o[1] = o[2] = 0; }
        };
        // 1. Hangs straight down from a still tip in air, segment lengths kept.
        Rope r = new Rope(0, 70, 0);
        for (int t = 0; t < 100; t++) r.step(0.05, 0, 70, 0, false, still);
        assert Math.abs(r.x[r.n - 1]) < 1e-3 && Math.abs(r.y[r.n - 1] - 67) < 0.15 : "hang " + r.y[r.n - 1];
        for (int i = 0; i < r.n - 1; i++) {
            double d = Math.hypot(Math.hypot(r.x[i + 1] - r.x[i], r.y[i + 1] - r.y[i]), r.z[i + 1] - r.z[i]);
            assert Math.abs(d - r.segLen()) < 0.02 : "seg " + i + " " + d;
        }
        // 2. Whip the tip forward with the hand open: the line shoots, no NaN, length grows.
        double before = r.length();
        for (int t = 0; t < 10; t++) r.step(0.05, t * 0.8, 70 + Math.sin(t) * 0.5, 0, true, still);
        assert r.length() > before : "no shoot " + r.length();
        for (int i = 0; i < r.n; i++) assert !Double.isNaN(r.x[i] + r.y[i] + r.z[i]) : "nan";
        // 3. Let it fall on the water: the fly floats at the surface, never below the ground.
        for (int t = 0; t < 200; t++) r.step(0.05, 8, 62, 0, false, still);
        assert r.y[r.n - 1] > 59.5 && r.y[r.n - 1] < 60.5 : "float " + r.y[r.n - 1];
        // 4. Strip pulls it back in.
        double l = r.length();
        r.strip(1.0);
        assert Math.abs(r.length() - (l - 1.0)) < 1e-9;
        // 5. A sinking fly goes down and stops on the bottom.
        r.flySink = 0.5;
        for (int t = 0; t < 400; t++) r.step(0.05, 8, 62, 0, false, still);
        assert r.y[r.n - 1] < 59.0 && r.y[r.n - 1] >= 55 - 0.5 : "sink " + r.y[r.n - 1];
        Rope big = new Rope(256, 0, 70, 0);
        for (int t = 0; t < 100; t++) big.step(0.05, 0, 70, 0, false, still);
        assert Math.abs(big.y[big.n - 1] - 67) < 0.3 : "hang256 " + big.y[big.n - 1];
        System.out.println("Rope ok: length " + r.length() + " fly y " + r.y[r.n - 1]);
    }
}
