package com.riverfishing.block;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.riverfishing.RiverFishing;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * §bait-crops: a farmland crop that grows a plant bait (corn / pea / barley→pearl barley). Four visual
 * stages (the beetroot pattern, {@code AGE_3}) so each stage gets a hand-made texture; everything else —
 * bonemeal, random ticks, farmland checks, Serene Seasons fertility (via the sereneseasons block tags in
 * our datapack) — rides the vanilla {@link CropBlock} behaviour unchanged.
 */
public class BaitCropBlock extends CropBlock {
    public static final MapCodec<BaitCropBlock> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.fieldOf("seed").forGetter(b -> b.seedPath),
            propertiesCodec()).apply(i, BaitCropBlock::new));

    public static final int MAX_AGE = 3;
    public static final IntegerProperty AGE = BlockStateProperties.AGE_3;

    /** Our-namespace path of the seed item (resolved lazily — items bind after blocks). */
    private final String seedPath;

    public BaitCropBlock(String seedPath, Properties properties) {
        super(properties);
        this.seedPath = seedPath;
    }

    @Override
    public MapCodec<BaitCropBlock> codec() {
        return CODEC;
    }

    @Override
    protected ItemLike getBaseSeedId() {
        return BuiltInRegistries.ITEM.get(RiverFishing.id(seedPath));
    }

    @Override
    protected IntegerProperty getAgeProperty() {
        return AGE;
    }

    @Override
    public int getMaxAge() {
        return MAX_AGE;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(AGE);
    }
}
