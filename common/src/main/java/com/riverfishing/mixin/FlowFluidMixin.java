package com.riverfishing.mixin;

import com.riverfishing.fishing.Flow;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.WaterFluid;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * §flow: source water in a river answers with the river's current. Vanilla's own flow is kept for
 * every non-source block (that is how a waterfall or a placed bucket still runs downhill); a still
 * source that {@link Flow} knows nothing about stays still. The liquid renderer turns its moving
 * texture to this vector and entity pushing carries boats, items and swimmers along it.
 */
@Mixin(FlowingFluid.class)
public abstract class FlowFluidMixin {

    @Inject(method = "getFlow(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/material/FluidState;)Lnet/minecraft/world/phys/Vec3;",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void riverfishing$riverFlow(BlockGetter level, BlockPos pos, FluidState state,
                                        CallbackInfoReturnable<Vec3> cir) {
        if (!state.isSource() || !((Object) this instanceof WaterFluid)) return;
        float[] v = Flow.at(level, pos);
        if (v == null || (v[0] == 0f && v[2] == 0f)) return;
        cir.setReturnValue(new Vec3(v[0], 0.0, v[2]));
    }
}
