# -*- coding: utf-8 -*-
"""§koi-genes (0.9.0): wire ONE species — `koi_carp` — into all three trees.

    py -X utf8 tools/wire_koi_carp.py

The five koi ids the mod shipped (`carp_koi_kohaku` and friends) are not species: they are colour
genotypes of one fish, exactly as `mirror_carp` was a scale genotype of `carp`. This adds the fish they
are genotypes OF. It does not remove them — an old world must keep every koi in every chest — it only
changes what the WATER hands out (see FishingManager.maybeKoi and Genome.landed).

Nothing here is new machinery: `wire_species_wave.py` already knows how to add a species to three trees
in three dialects (profile from a donor, models, art copy, lang, roster, trades, tags, generators), so
this file is its data block and nothing else.
"""
import io, os, shutil, sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import wire_species_wave as w                                   # noqa: E402

DONOR = "carp_koi_kohaku"

w.TAG = "§koi-genes (0.9.0)"
w.WAVE = ("§koi-genes (0.9.0): the koi. Five ids became one species whose VARIETY is three\n"
          "            // colour loci — the five stay registered so no old world loses a fish.")

w.SPECIES = {
    "koi_carp": (DONOR, {
        # Everything else is the kohaku's profile verbatim: same water, same tackle, same fight, and
        # the same `base` 0 + cherry-grove gate that makes a wild koi a thing you stumble on.
        "display": "Кои",
    },
        ("Koi carp", "Карп кои", "Короп кої"),
        ("An ornamental carp bred for its colours: white ground, red hi, black sumi, and the named "
         "varieties every keeper trades in — kohaku, sanke, showa, bekko, asagi, hi utsuri, karasu, "
         "and the two you will not find wild, platinum and tancho. Caught by chance on carp tackle, "
         "far more often by blossom; the rest are bred in a tank.",
         "Декоративный карп, выведенный ради окраски: белый фон, алое «хи», чёрное «суми» — и именные сорта, которыми торгуют заводчики: кохаку, санкэ, сёва, бекко, асаги, хи уцури, карасу — и два, которых в дикой воде нет: платинум и тантю. Попадается на карповые снасти, чаще у цветущей сакуры; остальное выводят в аквариуме.",
         "Декоративний короп, виведений заради забарвлення: біле тло, червоне «хі», чорне «сумі» — і іменні сорти, якими торгують заводчики: кохаку, санке, сьова, бекко, асаґі, хі уцурі, карасу — і два, яких у дикій воді немає: платинум і тантю. Трапляється на коропові снасті, частіше біля квітучої сакури; решту виводять в акваріумі."),
        # §koi-genes: the counter takes a koi now (it never did), at the sazan's price — the VARIETY
        # multiplies it where a fish is priced from the stack (Genome.varietyValue), which a vanilla
        # trade cannot see. Tier 4: a koi is not a starter fish.
        (4, 8, 14)),
}


def art():
    """The journal picture. The author drew the item sprite; the page art is the kohaku's for now."""
    src = os.path.join(w.MAIN, w.A, "textures/gui/journal/fish", DONOR + ".png")
    dst = os.path.join(w.MAIN, w.A, "textures/gui/journal/fish", "koi_carp.png")
    if not os.path.isfile(dst):
        shutil.copy2(src, dst)
        print("journal art: copied %s.png as a PLACEHOLDER" % DONOR)


if __name__ == "__main__":
    art()
    sys.exit(w.main())
