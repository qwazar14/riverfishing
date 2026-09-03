# -*- coding: utf-8 -*-
"""§breeding stream G: the water-body upgrade blocks — registration and the place/break ledger.

    py -X utf8 tools/patches/p_g.py <repo root> [1211|1201|26]

Anchor replacement on two existing files; every insert carries a "§g" marker so a rerun finds it and
does nothing. Exit 1 with the missing anchor when a tree has drifted. Nothing inserted here touches
NBT, messages or ResourceLocation, so the three dialects read the same text — the dialect argument is
accepted for the integrator's uniform call and otherwise unused.

    ModBlocks.java   five registerSimple lines before the trophy stand (BlockItems come with them)
    ModEvents.java   BlockEvent.PLACE beside BlockEvent.BREAK, and a remove() inside BREAK

Stream E also patches ModEvents, at the "§contracts-b1" comment above; these anchors are the shovel
comment and the SpookTracker line inside BREAK, which E does not touch.
"""
import io, os, sys

ROOT = sys.argv[1] if len(sys.argv) > 1 else "."
DIALECT = sys.argv[2] if len(sys.argv) > 2 else "1211"
SRC = os.path.join(ROOT, "common/src/main/java/com/riverfishing")
MARK = "§g"


def read(path):
    with io.open(path, encoding="utf-8") as f:
        return f.read()


def write(path, text):
    with io.open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write(text)


def patch(rel, old, new):
    """sub1: exactly one anchor, replaced once. A tree already carrying the insert is left alone."""
    path = os.path.join(SRC, rel)
    text = read(path)
    if new in text:
        return
    if text.count(old) != 1:
        sys.exit("p_g: anchor not found once in %s (%d hits):\n%s" % (rel, text.count(old), old))
    write(path, text.replace(old, new))


# ---------------------------------------------------------------- ModBlocks: five plain blocks
BLOCKS_ANCHOR = "    // Trophy stand (§15.5) — mounts a caught fish.\n"
BLOCKS = '''    // §g §breeding (0.9.0): water-body upgrades — marks on the water that the ecosystem reads
    // (fishing/WaterUpgrades). One class, five kinds; the kind string is what Ecosystem asks for.
    // The bed ones may stand IN the water (waterloggable); the bank ones may not.
    public static final RegistrySupplier<Block> AERATOR = registerSimple("aerator",
            () -> new com.riverfishing.block.WaterUpgradeBlock("aerator", "aerator", true,
                    BlockBehaviour.Properties.of().strength(1.5f).sound(SoundType.METAL)));
    public static final RegistrySupplier<Block> SNAG_PILE = registerSimple("snag_pile",
            () -> new com.riverfishing.block.WaterUpgradeBlock("snag_pile", "snags", true,
                    BlockBehaviour.Properties.of().strength(1.0f).sound(SoundType.WOOD)));
    public static final RegistrySupplier<Block> GRAVEL_BED = registerSimple("gravel_bed",
            () -> new com.riverfishing.block.WaterUpgradeBlock("gravel_bed", "gravel", true,
                    BlockBehaviour.Properties.of().strength(0.6f).sound(SoundType.GRAVEL)));
    public static final RegistrySupplier<Block> WARM_OUTFLOW = registerSimple("warm_outflow",
            () -> new com.riverfishing.block.WaterUpgradeBlock("warm_outflow", "warm_outflow", false,
                    BlockBehaviour.Properties.of().strength(1.5f).sound(SoundType.COPPER)));
    public static final RegistrySupplier<Block> FEEDING_STATION = registerSimple("feeding_station",
            () -> new com.riverfishing.block.WaterUpgradeBlock("feeding_station", "feeding_station", false,
                    BlockBehaviour.Properties.of().strength(1.0f).sound(SoundType.WOOD)));

'''
patch("registry/ModBlocks.java", BLOCKS_ANCHOR, BLOCKS + BLOCKS_ANCHOR)

# ---------------------------------------------------------------- ModEvents: the ledger
PLACE_ANCHOR = "        // Worms from digging soil with a shovel (§9.6).\n"
PLACE = '''        // §g §breeding (0.9.0): a water-body upgrade goes into the ledger the moment it is placed, so a
        // bite never has to scan the swim for them. BREAK below takes it out again.
        BlockEvent.PLACE.register((level, pos, state, placer) -> {
            if (level instanceof net.minecraft.server.level.ServerLevel sl
                    && state.getBlock() instanceof com.riverfishing.block.WaterUpgradeBlock b) {
                com.riverfishing.fishing.WaterUpgrades.get(sl).put(pos, b.kind());
            }
            return EventResult.pass();
        });

'''
patch("event/ModEvents.java", PLACE_ANCHOR, PLACE + PLACE_ANCHOR)

BREAK_ANCHOR = "            if (!level.isClientSide()) com.riverfishing.fishing.SpookTracker.onBlockBreak(level, pos);\n"
BREAK = '''            // §g: a broken upgrade leaves the ledger (see BlockEvent.PLACE above).
            if (level instanceof net.minecraft.server.level.ServerLevel sl
                    && state.getBlock() instanceof com.riverfishing.block.WaterUpgradeBlock) {
                com.riverfishing.fishing.WaterUpgrades.get(sl).remove(pos);
            }
'''
patch("event/ModEvents.java", BREAK_ANCHOR, BREAK_ANCHOR + BREAK)

print("p_g: ok (%s)" % DIALECT)
