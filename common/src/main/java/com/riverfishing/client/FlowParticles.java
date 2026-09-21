package com.riverfishing.client;

import com.riverfishing.fishing.Flow;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;

/**
 * §flow-foam: the current made visible — foam and bubbles born on moving river water near the
 * player and carried by {@link Flow}. Client only, nothing is synced; vanilla particles for now.
 */
public final class FlowParticles {
    private FlowParticles() {}

    private static final int TRIES = 6, RADIUS = 14;
    private static final double[] flow = new double[3];

    public static void tick(Minecraft mc) {
        if (mc.level == null || mc.player == null || mc.isPaused()) return;
        var rnd = mc.level.random;
        double cx = mc.player.getX(), cy = mc.player.getY(), cz = mc.player.getZ();
        for (int t = 0; t < TRIES; t++) {
            double x = cx + (rnd.nextDouble() * 2 - 1) * RADIUS;
            double z = cz + (rnd.nextDouble() * 2 - 1) * RADIUS;
            double y = cy + (rnd.nextDouble() * 2 - 1) * 4;
            BlockPos pos = BlockPos.containing(x, y, z);
            var fs = mc.level.getFluidState(pos);
            if (fs.isEmpty() || !mc.level.getFluidState(pos.above()).isEmpty()) continue;   // surface only
            Flow.at(mc.level, x, y, z, flow);
            double sp = Math.hypot(flow[0], flow[2]);
            if (sp < 0.15) continue;
            double top = pos.getY() + fs.getOwnHeight();
            // Foam rides the surface; bubbles come up through it — both drift with the current.
            if (rnd.nextInt(3) == 0) {
                mc.level.addParticle(ParticleTypes.CLOUD, x, top + 0.02, z, flow[0] * 0.05, 0.0, flow[2] * 0.05);
            } else {
                mc.level.addParticle(ParticleTypes.BUBBLE_POP, x, top - 0.05, z, flow[0] * 0.05, 0.02, flow[2] * 0.05);
            }
        }
    }
}
