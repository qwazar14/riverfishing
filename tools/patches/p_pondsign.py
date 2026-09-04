# -*- coding: utf-8 -*-
"""§pond-sign: the claim sign is a board on a post, and it faces you.

    py -X utf8 tools/patches/p_pondsign.py <root> [1211|1201|26]

The sign was `minecraft:block/cross` — the two crossed quads that draw a dandelion. From every angle you
saw both of them, which is why it read as a folded piece of card standing in the grass rather than a
sign, and why the art was stretched across the whole of each quad. It also had no facing, so it could
not be aimed, and its hitbox was a full 8x16x8 column of nothing.

Now: a post and a board, two boxes, mapped one texel per pixel onto the sheet tools/gen_pond_sign.py
draws; a horizontal FACING set from the player who planted it, like every other furniture block in the
mod; a hitbox that is the post and the board and not the air around them; and an item that is the sign
rather than a flat icon of its texture.

The picture on it is a fish and a waterline and no words — the mod ships in three languages.
"""
import io, os, sys

ROOT = sys.argv[1]
D = sys.argv[2] if len(sys.argv) > 2 else "1211"
J = os.path.join(ROOT, "common/src/main/java/com/riverfishing/")
A = os.path.join(ROOT, "common/src/main/resources/assets/riverfishing/")
PROP = "EnumProperty<Direction>" if D == "26" else "DirectionProperty"
IMPORT = ("import net.minecraft.world.level.block.state.properties.EnumProperty;" if D == "26"
          else "import net.minecraft.world.level.block.state.properties.DirectionProperty;")
# 1.20.1 declares getShape public; 1.21+ made it protected.
VIS = "public" if D == "1201" else "protected"


def wr(p, s):
    io.open(p, "w", encoding="utf-8", newline="\n").write(s)


def rd(p):
    return io.open(p, encoding="utf-8").read()


# ---- 1. the model: a post and a board ------------------------------------------------------------
# Every face names a REGION of the sheet, so nothing is stretched. The layout is documented once, in
# tools/gen_pond_sign.py, which is also what draws it.
MODEL = '''{
  "parent": "minecraft:block/block",
  "textures": {
    "sign": "riverfishing:block/pond_sign",
    "particle": "riverfishing:block/pond_sign"
  },
  "elements": [
    {
      "name": "post",
      "from": [7, 0, 7],
      "to": [9, 7, 9],
      "faces": {
        "north": {"uv": [0, 11, 2, 16], "texture": "#sign"},
        "south": {"uv": [0, 11, 2, 16], "texture": "#sign"},
        "west":  {"uv": [0, 11, 2, 16], "texture": "#sign"},
        "east":  {"uv": [0, 11, 2, 16], "texture": "#sign"},
        "up":    {"uv": [0, 11, 2, 13], "texture": "#sign"},
        "down":  {"uv": [0, 11, 2, 13], "texture": "#sign"}
      }
    },
    {
      "name": "board",
      "from": [1, 6, 6.5],
      "to": [15, 15, 8.5],
      "faces": {
        "north": {"uv": [0, 0, 14, 9], "texture": "#sign"},
        "south": {"uv": [0, 0, 14, 9], "texture": "#sign"},
        "west":  {"uv": [14, 0, 16, 9], "texture": "#sign"},
        "east":  {"uv": [14, 0, 16, 9], "texture": "#sign"},
        "up":    {"uv": [0, 9, 14, 11], "texture": "#sign"},
        "down":  {"uv": [0, 9, 14, 11], "texture": "#sign"}
      }
    }
  ]
}
'''
wr(A + "models/block/pond_sign.json", MODEL)
print("  models/block/pond_sign.json: a post and a board")

BLOCKSTATE = '''{
  "variants": {
    "facing=north": {"model": "riverfishing:block/pond_sign"},
    "facing=east":  {"model": "riverfishing:block/pond_sign", "y": 90},
    "facing=south": {"model": "riverfishing:block/pond_sign", "y": 180},
    "facing=west":  {"model": "riverfishing:block/pond_sign", "y": 270}
  }
}
'''
wr(A + "blockstates/pond_sign.json", BLOCKSTATE)
print("  blockstates/pond_sign.json: four ways round")

# The item is the sign, not a picture of its texture sheet — which would now show the strips too.
wr(A + "models/item/pond_sign.json", '{\n  "parent": "riverfishing:block/pond_sign"\n}\n')
print("  models/item/pond_sign.json: the block itself")

# ---- 2. the block: a facing, and a hitbox that is the sign ----------------------------------------
p = J + "block/PondSignBlock.java"
s = rd(p)
if "pond-sign" not in s:
    old = """public class PondSignBlock extends Block {
    private static final VoxelShape SHAPE = Block.box(4, 0, 4, 12, 16, 12);

    public PondSignBlock(Properties properties) {
        super(properties);
    }

    @Override
    %s VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }""" % VIS
    assert old in s, "the block's shape moved"
    s = s.replace(old, """public class PondSignBlock extends Block {
    /** §pond-sign: which way the board looks — set from whoever planted it, like the rest of the furniture. */
    public static final %s FACING = HorizontalDirectionalBlock.FACING;

    /** The post, and the board across it: the hitbox is the sign and not the air it stands in. */
    private static final VoxelShape POST = Block.box(6, 0, 6, 10, 7, 10);
    private static final VoxelShape NORTH_SOUTH = Shapes.or(POST, Block.box(1, 6, 6, 15, 15, 9));
    private static final VoxelShape EAST_WEST = Shapes.or(POST, Block.box(6, 6, 1, 9, 15, 15));

    public PondSignBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    %s VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return state.getValue(FACING).getAxis() == Direction.Axis.X ? EAST_WEST : NORTH_SOUTH;
    }""" % (PROP, VIS), 1)

    s = s.replace("import net.minecraft.world.level.block.Block;",
                  """import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.StateDefinition;
%s
import net.minecraft.world.phys.shapes.Shapes;""" % IMPORT, 1)
    # …the marker the guard above looks for, on the class doc.
    s = s.replace(" * standing sign always\n * means a standing claim.",
                  " * standing sign always\n * means a standing claim.\n *\n"
                  " * <p>§pond-sign: a board on a post, aimed at whoever planted it. It was two crossed quads —\n"
                  " * the dandelion model — which read as a folded card and could not be turned.")
    wr(p, s)
    print("  PondSignBlock: a facing, and a hitbox shaped like the sign")
print("done (%s)" % D)
