#!/usr/bin/env python3
"""26.3's data, rewritten from the shared files: loot tables, villager trades and the cherry-pond feature.

26.3 reshaped the loot DSL — a table's "functions" list is a "modifier" (one, or a list), "conditions" a
"condition" (several go under minecraft:all_of "terms"), each keyed by "type" instead of "function"/
"condition", and block_state_property became match_block {blocks, state}. Villager trades take a
"given_item_modifier". Configured features moved to worldgen/feature, flattened, and a simple block state
provider is just the state {id, properties}. The shared files stay in the 26.2 form; this writes the 26.3
copies into common/src/v26_3/resources, which the 26.3 node reads first (common/build.gradle).

    python tools/gen_data_26_3.py
"""
import copy, glob, json, os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, 'common/src/main/resources/data/riverfishing')
OUT = os.path.join(ROOT, 'common/src/v26_3/resources/data/riverfishing')


def cond(c):
    c = dict(c)
    t = c.pop('condition')
    if t == 'minecraft:block_state_property':
        return {'type': 'minecraft:match_block', 'blocks': c['block'], 'state': c.get('properties', {})}
    out = {'type': t}
    out.update(convert(c))
    return out


def fn(f):
    f = dict(f)
    out = {'type': f.pop('function')}
    out.update(convert(f))
    return out


def convert(o):
    if isinstance(o, list):
        return [convert(x) for x in o]
    if not isinstance(o, dict):
        return o
    # a lone condition or function object, wherever it sits (a trade's merchant_predicate, say)
    if isinstance(o.get('condition'), str):
        return cond(o)
    if isinstance(o.get('function'), str):
        return fn(o)
    d = {}
    for k, v in o.items():
        if k == 'functions':
            mods = [fn(x) for x in v]
            d['modifier'] = mods[0] if len(mods) == 1 else mods
        elif k == 'conditions':
            cs = [cond(x) for x in v]
            d['condition'] = cs[0] if len(cs) == 1 else {'type': 'minecraft:all_of', 'terms': cs}
        elif k == 'given_item_modifiers':
            d['given_item_modifier'] = [fn(x) for x in v]
        else:
            d[k] = convert(v)
    return d


def state(provider):
    s = provider['state']
    out = {'id': s['Name']}
    if s.get('Properties'):
        out['properties'] = s['Properties']
    return out


def write(rel, data):
    p = os.path.join(OUT, rel)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    with open(p, 'w', encoding='utf-8', newline='\n') as f:
        json.dump(data, f, indent=2, ensure_ascii=False)
        f.write('\n')


n = 0
for p in glob.glob(os.path.join(SRC, 'loot_table', '**', '*.json'), recursive=True):
    write(os.path.relpath(p, SRC), convert(json.load(open(p, encoding='utf-8'))))
    n += 1
for p in glob.glob(os.path.join(SRC, 'villager_trade', '**', '*.json'), recursive=True):
    write(os.path.relpath(p, SRC), convert(json.load(open(p, encoding='utf-8'))))
    n += 1
def states(o):
    """Every block state {Name, Properties} (bare or in a simple_state_provider) as 26.3's {id, properties}."""
    if isinstance(o, list):
        return [states(x) for x in o]
    if not isinstance(o, dict):
        return o
    if o.get('type') == 'minecraft:simple_state_provider':
        return state(o)
    if 'Name' in o and set(o) <= {'Name', 'Properties'}:
        return state({'state': o})
    return {k: states(v) for k, v in o.items()}


def tri(r):
    return {'type': 'minecraft:trapezoid', 'min': -r, 'max': r, 'plateau': 0}


PLACED = os.path.join(SRC, 'worldgen', 'placed_feature')
spread = set()
for p in glob.glob(os.path.join(SRC, 'worldgen', 'configured_feature', '*.json')):
    d = json.load(open(p, encoding='utf-8'))
    name = os.path.basename(p)
    if d['type'] == 'minecraft:random_patch':
        # 26.3 has no random_patch: the patch is its inner feature, and the spread moves into the placed
        # feature as count + offset + the inner filters — the shape vanilla's sugar cane took
        c = d['config']
        inner = c['feature']
        flat = {'type': inner['feature']['type']}
        flat.update(states(inner['feature']['config']))
        write(os.path.join('worldgen', 'feature', name), flat)
        placed = json.load(open(os.path.join(PLACED, name), encoding='utf-8'))
        placed['placement'] = states(placed['placement']) + [
            {'type': 'minecraft:count', 'count': c['tries']},
            {'type': 'minecraft:offset', 'x': tri(c['xz_spread']), 'y': tri(c['y_spread']), 'z': tri(c['xz_spread'])},
        ] + states(inner.get('placement', []))
        write(os.path.join('worldgen', 'placed_feature', name), placed)
        spread.add(name)
        n += 2
        continue
    flat = {'type': d['type']}
    flat.update(states(d['config']))
    write(os.path.join('worldgen', 'feature', name), flat)
    n += 1
for p in glob.glob(os.path.join(PLACED, '*.json')):
    d = json.load(open(p, encoding='utf-8'))
    if os.path.basename(p) not in spread and states(d) != d:
        write(os.path.join('worldgen', 'placed_feature', os.path.basename(p)), states(d))
        n += 1

# village farms: our crop rules on 26.3's OWN vanilla lists (their states are plain ids now) — prepended to the
# farm lists, appended to the savanna street fields, as in the shared 26.2 copies
import zipfile
JAR = os.path.expanduser('~/.gradle/caches/fabric-loom/26.3/minecraft-merged.jar')
SHARED_MC = os.path.join(ROOT, 'common/src/main/resources/data/minecraft/worldgen/processor_list')
OUT_MC = os.path.join(ROOT, 'common/src/v26_3/resources/data/minecraft/worldgen/processor_list')
if os.path.isdir(SHARED_MC):
    jar = zipfile.ZipFile(JAR)
    for p in glob.glob(os.path.join(SHARED_MC, '*.json')):
        name = os.path.basename(p)
        ours = [r for r in json.load(open(p, encoding='utf-8'))['processors'][0]['rules']
                if str(r['output_state'].get('Name', '')).startswith('riverfishing:')]
        for r in ours:
            r['output_state'] = r['output_state']['Name']   # age 0 is the default state
        vanilla = json.loads(jar.read('data/minecraft/worldgen/processor_list/' + name))
        (proc,) = vanilla['processors']
        proc['rules'] = proc['rules'] + ours if name.startswith('street_') else ours + proc['rules']
        os.makedirs(OUT_MC, exist_ok=True)
        with open(os.path.join(OUT_MC, name), 'w', encoding='utf-8', newline='\n') as f:
            json.dump(vanilla, f, indent=2, ensure_ascii=False)
            f.write('\n')
        n += 1
print(n, 'files ->', OUT)
