package com.riverfishing.client;

import com.riverfishing.block.TackleBoxBlockEntity;
import com.riverfishing.item.TackleBoxItem;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.color.block.BlockTintSources;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * §tackle-box §26.x: the insert colour of a box that is standing on the ground.
 *
 * <p>The box's ITEM is tinted from data now (a {@code minecraft:dye} tint in
 * {@code assets/riverfishing/items/tackle_box_*.json}), and the port took that to mean colour was
 * handled — but a block on the ground is not an item, and blocks still take their tint from Java. So a
 * box you had painted went down grey, which is the one thing the colours exist to prevent: you paint
 * four boxes so you can tell them apart on the bank.
 *
 * <p>The colour is read from the block entity, which holds the box stack that was placed — the same
 * stack the item tint reads, so the thing in your hand and the thing on the ground cannot disagree.
 */
public final class TackleBoxTint implements BlockTintSource {
    /**
     * The tint list a tackle box is registered with; the index IS the model's {@code tintindex}. Only
     * the insert band (1) is coloured — 0 is untinted white for everything else in the model.
     */
    public static final List<BlockTintSource> LAYERS =
            List.of(BlockTintSources.constant(-1), new TackleBoxTint());

    private TackleBoxTint() {}

    /** No level, so no box: the colour the sprite is drawn in, which is what an unpainted box is. */
    @Override
    public int color(BlockState state) {
        return 0xFF000000 | TackleBoxItem.color(ItemStack.EMPTY);
    }

    @Override
    public int colorInWorld(BlockState state, BlockAndTintGetter view, BlockPos pos) {
        return view.getBlockEntity(pos) instanceof TackleBoxBlockEntity be
                ? 0xFF000000 | be.color()
                : color(state);
    }
}
