# -*- coding: utf-8 -*-
"""§icon-topup: a fish whose stack nobody stamped renders as its raw, undyed sprite.

    py -X utf8 tools/patches/p_koiwhite.py <root-of-the-26.x-worktree>

Reported: "надо починить текстурки для кои. сейчас они белые".

They are white because a koi's sprite IS white. One greyscale drawing paints all seventeen varieties,
and the colour arrives as four numbers in the stack's custom_model_data, which FishItem.stampIcon
writes. Compositing the five layers offline confirms it exactly: with the colours, kohaku is white and
red and showa is nearly black; without them every layer multiplies by white and the fish comes out of
the drawing unchanged, which for a koi means blank.

So this is not an art bug and not a tint bug. It is that the colour lives in a component only the four
paths that BUILD a fish write — the rod, the net, the bait trap, the aquarium preview. Every other way a
koi can reach a hand has no colours at all:

    /give and command blocks (which is how this mod is tested, and the log has one)
    a fish that was already in a world before §koi-genes existed
    anything a datapack, /loot, or another mod hands out

and a koi from any of those is the leftmost fish in the strip: blank white. Non-koi fish have the same
hole — they lose the §morph age shading — it is simply invisible on a sprite that is already coloured.

The fix is not a fifth call to stampIcon. It is to stop requiring the caller to remember: Item's
inventoryTick sees every stack in every inventory, so a fish missing its colours stamps itself once and
is never asked again. The guard is the whole point — this must not run every tick on every fish.

1.21.1 and 1.20.1 need none of this: there the tint is computed at draw time by FishTint.itemColor, off
the stack, so an unstamped fish there has never been white in the first place. That is also the better
design, and it is the one 26.x cannot have, because item colour providers were removed.
"""
import io, os, sys

ROOT = sys.argv[1]
P = os.path.join(ROOT, "common/src/main/java/com/riverfishing/item/FishItem.java")

s = io.open(P, encoding="utf-8").read()
if "icon-topup" in s:
    print("  already patched")
    sys.exit(0)

anchor = "    public static boolean isTrophy(ItemStack stack) {"
assert anchor in s, "isTrophy moved — find another anchor after stampIcon"

s = s.replace(anchor, """    /**
     * §icon-topup: a fish that reached a hand without going through {@link #create} has no colours on
     * it, and 26.x draws a fish ENTIRELY from what its stack carries — so it renders as the raw sprite.
     * For a koi that means blank white, because the koi drawing is greyscale and every one of its
     * seventeen varieties is painted by the four numbers {@link #stampIcon} writes.
     *
     * <p>A /give, a command block, a datapack loot table, or a fish that predates §koi-genes all land
     * here. Stamping on the way past is the only place that catches every one of them; the guard means
     * it happens once per stack and never again.
     */
    @Override
    public void inventoryTick(ItemStack stack, net.minecraft.server.level.ServerLevel level,
                              net.minecraft.world.entity.Entity entity,
                              @Nullable net.minecraft.world.entity.EquipmentSlot slot) {
        super.inventoryTick(stack, level, entity, slot);
        net.minecraft.world.item.component.CustomModelData cmd =
                stack.get(net.minecraft.core.component.DataComponents.CUSTOM_MODEL_DATA);
        if (cmd == null || cmd.colors().isEmpty()) stampIcon(stack);
    }

""" + anchor, 1)

io.open(P, "w", encoding="utf-8", newline="\n").write(s)
print("  FishItem: an unstamped fish stamps itself the first tick it is carried")
print("done")
