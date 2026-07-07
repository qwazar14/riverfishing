package com.riverfishing.mixin;

import com.riverfishing.item.FishItem;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * §multiloader / §release: Forge's {@code Item#onEntityItemUpdate} has no vanilla/Fabric twin, so the
 * "fish thrown in water is let go" behaviour rides a shared mixin on {@link ItemEntity#tick()} instead.
 * Delegates to {@link FishItem#koiReleaseTick} for any caught-fish stack; when it releases the fish
 * (discards the entity) we cancel the rest of the tick.
 */
@Mixin(ItemEntity.class)
public abstract class ItemEntityMixin {
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void riverfishing$koiRelease(CallbackInfo ci) {
        ItemEntity self = (ItemEntity) (Object) this;
        ItemStack stack = self.getItem();
        if (stack.getItem() instanceof FishItem && FishItem.koiReleaseTick(stack, self)) {
            ci.cancel();
        }
    }
}
