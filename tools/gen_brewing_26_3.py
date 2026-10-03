#!/usr/bin/env python3
"""26.3 brews from JSON: the fish-oil potions and the oil itself as minecraft:brewing recipes.

26.2 and older build the table in code (ModPotions.addMixes + PotionBrewingOilMixin); 26.3 deleted that
class and reads recipes instead, so the same mixes are written out here. The files live in
common/src/v26_3/resources, which only the 26.3 node puts on its resource path (common/build.gradle).

    python tools/gen_brewing_26_3.py
"""
import json, os, shutil

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, 'common/src/v26_3/resources/data/riverfishing/recipe/brewing')
# §oil-brew: the same nine as ModPotions.OILY and the oily_fish tag
OILY = ['herring', 'mackerel', 'salmon', 'pink_salmon', 'sabrefish', 'eel', 'bluefish', 'bluefin_tuna', 'pollock']
CONTAINERS = ['potion', 'splash_potion', 'lingering_potion']


def potion(container, pot):
    return {'item': 'minecraft:' + container, 'potion_contents': {'potions': pot}}


def out_potion(container, pot):
    return {'id': 'minecraft:' + container, 'components': {'minecraft:potion_contents': {'potion': pot}}}


def recipe(inp, reagent, out):
    return {'type': 'minecraft:brewing', 'input': inp, 'reagent': {'item': reagent}, 'output': out}


recipes = {}
for c in CONTAINERS:
    recipes['%s_water_fish_oil' % c] = recipe(potion(c, 'minecraft:water'), 'riverfishing:fish_oil', out_potion(c, 'riverfishing:fish_oil'))
    recipes['%s_awkward_fish_oil' % c] = recipe(potion(c, 'minecraft:awkward'), 'riverfishing:fish_oil', out_potion(c, 'riverfishing:fish_oil'))
    for sp in OILY:
        recipes['%s_awkward_%s' % (c, sp)] = recipe(potion(c, 'minecraft:awkward'), 'riverfishing:' + sp, out_potion(c, 'riverfishing:fish_oil'))
    recipes['%s_fish_oil_glowstone' % c] = recipe(potion(c, 'riverfishing:fish_oil'), 'minecraft:glowstone_dust', out_potion(c, 'riverfishing:strong_fish_oil'))
    recipes['%s_fish_oil_redstone' % c] = recipe(potion(c, 'riverfishing:fish_oil'), 'minecraft:redstone', out_potion(c, 'riverfishing:long_fish_oil'))
# the container swaps vanilla writes out per potion: gunpowder makes a splash, dragon's breath a lingering one
for pot in ('fish_oil', 'strong_fish_oil', 'long_fish_oil'):
    recipes['potion_%s_gunpowder' % pot] = recipe(potion('potion', 'riverfishing:' + pot), 'minecraft:gunpowder', out_potion('splash_potion', 'riverfishing:' + pot))
    recipes['splash_potion_%s_dragon_breath' % pot] = recipe(potion('splash_potion', 'riverfishing:' + pot), 'minecraft:dragon_breath', out_potion('lingering_potion', 'riverfishing:' + pot))
# §oil-stand: an oily fish over an EMPTY bottle renders down to the oil itself
for sp in OILY:
    recipes['glass_bottle_%s' % sp] = recipe({'item': 'minecraft:glass_bottle'}, 'riverfishing:' + sp, {'id': 'riverfishing:fish_oil'})

if os.path.isdir(OUT):
    shutil.rmtree(OUT)
os.makedirs(OUT)
for name, r in recipes.items():
    with open(os.path.join(OUT, name + '.json'), 'w', encoding='utf-8', newline='\n') as f:
        json.dump(r, f, indent=2)
        f.write('\n')
print(len(recipes), 'brewing recipes ->', OUT)
