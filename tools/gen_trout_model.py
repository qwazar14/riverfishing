"""Draw a rainbow trout: the pixel-art texture and the cube list that wears it, from one description.

Why this exists: a hand-built fish is fourteen boxes and eighty-four faces, and every face needs a
rectangle of texture that lines up with its neighbours. Doing that by hand is where the mistakes live. So
the parts are described once, here, and this script does both jobs from that description - packs an
atlas, paints every face by its WORLD position (so the trout's pink stripe runs unbroken from the jaw to
the tail no matter which box a texel belongs to), and writes the cube definitions with the uv rects
already filled in.

    py tools/gen_trout_model.py

Writes fish3d/rainbow_trout.png and fish3d/rainbow_trout_cubes.json.

Everything that has a SIZE - a speckle, a fin ray, the eye - is measured in model units and converted to
texels at the last moment, so DETAIL can be turned up or down and the fish stays the same fish, just
drawn finer or coarser.
"""
import base64
import json
import math
import os
import struct
import uuid as uuidlib
import zlib

# Texels per model unit. At 4 the trout's 26-unit body gets a 104-texel flank, which is where the
# speckling starts to read as speckling rather than as noise.
DETAIL = 4
ATLAS = 256
# The .bbmodel this writes declares its own UV space, so make it the atlas itself: one uv unit is one
# texel, which is what the UV editor needs if a human is ever going to touch these rects by hand.
UV_SPACE = ATLAS
OUT = 'fish3d'

# Rainbow trout, read off the reference: olive-tan back under dark speckles, the magenta band down the
# lateral line that gives the fish its name, cream flank, white belly, tan fins with darker rays.
BACK_DARK = (74, 78, 48)
BACK = (139, 134, 86)
FLANK_HI = (176, 168, 120)
STRIPE = (216, 74, 122)
STRIPE_HI = (238, 122, 158)
FLANK_LO = (232, 220, 192)
BELLY = (245, 240, 228)
FIN = (150, 143, 97)
FIN_RAY = (104, 98, 60)
SPECKLE = (54, 56, 34)
EYE = (26, 22, 16)
EYE_RING = (216, 208, 176)
MOUTH = (58, 40, 40)

# The model's Y extent. The stripe and the back are placed by world height rather than per box, which is
# what keeps the stripe continuous where a deep body meets a narrow tail.
Y_TOP, Y_BOT = 3.2, -3.2

# Sizes in MODEL UNITS, not texels - this is what makes DETAIL a dial and not a rewrite.
SPECKLE_CELL = 0.55     # how big one speckle is
MOTTLE_CELL = 0.7       # the belly's soft blotching
RAY_WIDTH = 0.5         # one fin ray plus its gap
EYE_R = 0.30            # pupil radius
EYE_RING_R = 0.58


def noise(x, y, z, cell=SPECKLE_CELL):
    """Deterministic value noise on a grid of `cell` model units.

    Quantise to integers FIRST. Multiplying raw floats by big primes and taking int() of the result
    throws the low bits away, so neighbouring points hashed to the same value and whole faces came out
    one flat colour - the upper jaw was a single dark slab before this."""
    a = int(math.floor(x / cell)) & 0xffff
    b = int(math.floor(y / cell)) & 0xffff
    c = int(math.floor(z / cell)) & 0xffff
    h = ((a * 73856093) ^ (b * 19349663) ^ (c * 83492791)) & 0xffffffff
    h = ((h ^ (h >> 13)) * 1274126177) & 0xffffffff
    return ((h ^ (h >> 16)) & 0xffff) / 65535.0


def flank(y, z):
    """The side of the fish at a world point: back, stripe, belly, and speckles over the top half."""
    t = (y - Y_BOT) / (Y_TOP - Y_BOT)          # 0 at the belly, 1 at the back
    # Ragged the band edges very slightly, so the stripe is a fish's stripe and not a printed line.
    t += (noise(7, y, z, 0.45) - 0.5) * 0.035
    if t > 0.80:
        c = BACK_DARK
    elif t > 0.62:
        c = BACK
    elif t > 0.50:
        c = FLANK_HI
    elif t > 0.40:
        c = STRIPE_HI
    elif t > 0.28:
        c = STRIPE
    elif t > 0.16:
        c = FLANK_LO
    else:
        c = BELLY
    # Speckles live on the back and the upper flank only, the way they do on the fish. x is pinned so the
    # two flanks speckle identically - they share one rectangle of atlas.
    if t > 0.52 and noise(0, y, z) > 0.70:
        c = SPECKLE
    return c


def back(z, x):
    c = BACK_DARK if noise(x, 99, z, MOTTLE_CELL) > 0.55 else BACK
    return SPECKLE if noise(x, 7, z) > 0.66 else c


def belly(z, x):
    return BELLY if noise(x, 3, z, MOTTLE_CELL) > 0.25 else FLANK_LO


def fin(u, v, spotted=False):
    """Fin webbing. The rays run ALONG the fin, the way they grow - across it they read as a stack of
    dark bars, which on a fin only a few texels tall is the whole fin."""
    ray = max(1, int(round(RAY_WIDTH * DETAIL)))
    c = FIN_RAY if (u // ray) % 2 == 0 else FIN
    if spotted and noise(u / DETAIL, 11, v / DETAIL) > 0.68:
        c = SPECKLE
    return c


# name, extents, rotation, pivot, and which painter the faces get.
# Head toward -Z, which is where a Minecraft entity model looks.
PARTS = [
    # The snout is ONE box stood on its corner. A cube turned 45 degrees about X shows a diamond in
    # profile, and the forward corner of that diamond is the point of the nose - which is how the
    # reference gets a tapered head out of a box. Two jaws meeting in a duck's bill was the wrong read.
    dict(name='head_wedge', frm=[-1.7, -1.35, -13.45], to=[1.7, 1.35, -10.75],
         rot=[45, 0, 0], origin=[0, 0, -12.1], kind='snout'),
    # The mouth is its own piece, tucked under the point of that diamond.
    dict(name='mouth', frm=[-1.45, -1.85, -13.25], to=[1.45, -0.85, -11.3],
         rot=[20, 0, 0], origin=[0, -1.35, -11.3], kind='mouth'),
    # Named skull, not head: a cube sharing its name with its own group makes every by-name operation
    # ambiguous, and apply_texture silently picks the cube.
    dict(name='skull', frm=[-2.0, -2.2, -11.6], to=[2.0, 2.35, -6.8],
         rot=[0, 0, 0], origin=[0, 0, -11.6], kind='head_eye'),
    dict(name='body_front', frm=[-2.5, -3.2, -6.8], to=[2.5, 3.2, -1.0],
         rot=[0, 0, 0], origin=[0, 0, -6.8], kind='body'),
    dict(name='body_rear', frm=[-2.0, -2.7, -1.0], to=[2.0, 2.7, 4.0],
         rot=[0, 0, 0], origin=[0, 0, -1.0], kind='body'),
    dict(name='peduncle', frm=[-1.0, -1.7, 4.0], to=[1.0, 1.7, 7.2],
         rot=[0, 0, 0], origin=[0, 0, 4.0], kind='body'),
    dict(name='tail', frm=[-0.5, -3.2, 7.2], to=[0.5, 3.2, 11.0],
         rot=[0, 0, 0], origin=[0, 0, 7.2], kind='tail'),
    dict(name='dorsal', frm=[-0.5, 3.0, -3.2], to=[0.5, 5.2, 0.4],
         rot=[-22, 0, 0], origin=[0, 3.0, 0.4], kind='fin'),
    dict(name='adipose', frm=[-0.4, 2.4, 4.4], to=[0.4, 3.7, 6.0],
         rot=[-16, 0, 0], origin=[0, 2.4, 6.0], kind='fin'),
    dict(name='anal', frm=[-0.4, -4.2, 2.4], to=[0.4, -2.4, 5.4],
         rot=[18, 0, 0], origin=[0, -2.4, 5.4], kind='fin'),
    dict(name='pectoral_right', frm=[1.9, -2.7, -6.6], to=[3.8, -2.3, -4.2],
         rot=[0, 0, -32], origin=[1.9, -2.5, -6.6], kind='fin'),
    dict(name='pectoral_left', frm=[-3.8, -2.7, -6.6], to=[-1.9, -2.3, -4.2],
         rot=[0, 0, 32], origin=[-1.9, -2.5, -6.6], kind='fin'),
    dict(name='pelvic_right', frm=[1.5, -3.3, -0.6], to=[3.0, -3.0, 1.6],
         rot=[0, 0, -26], origin=[1.5, -3.1, -0.6], kind='fin'),
    dict(name='pelvic_left', frm=[-3.0, -3.3, -0.6], to=[-1.5, -3.0, 1.6],
         rot=[0, 0, 26], origin=[-1.5, -3.1, -0.6], kind='fin'),
]

FACES = ('east', 'west', 'up', 'down', 'north', 'south')

pixels = [[(0, 0, 0, 0)] * ATLAS for _ in range(ATLAS)]
shelf = {'x': 0, 'y': 0, 'h': 0}


def alloc(w, h):
    """Shelf packer. The atlas is small and the faces are few; anything cleverer would be for its own sake."""
    if shelf['x'] + w > ATLAS:
        shelf['y'] += shelf['h']
        shelf['x'], shelf['h'] = 0, 0
    if shelf['y'] + h > ATLAS:
        raise SystemExit('atlas full at %dx%d - raise ATLAS or lower DETAIL' % (w, h))
    r = (shelf['x'], shelf['y'])
    shelf['x'] += w
    shelf['h'] = max(shelf['h'], h)
    return r


def paint(rect, w, h, fn):
    ox, oy = rect
    for j in range(h):
        row = pixels[oy + j][:]
        for i in range(w):
            row[ox + i] = fn(i, j) + (255,)
        pixels[oy + j] = row


def face_size(p, face):
    dx = p['to'][0] - p['frm'][0]
    dy = p['to'][1] - p['frm'][1]
    dz = p['to'][2] - p['frm'][2]

    def n(v):
        return max(1, int(round(v * DETAIL)))

    if face in ('east', 'west'):
        return n(dz), n(dy)
    if face in ('up', 'down'):
        return n(dz), n(dx)
    return n(dx), n(dy)


def make_painter(face, w, h, p):
    x0, y0, z0 = p['frm']
    x1, y1, z1 = p['to']
    kind = p['kind']

    def painter(i, j):
        if face in ('east', 'west'):
            z = z0 + (i + 0.5) * (z1 - z0) / w
            y = y1 - (j + 0.5) * (y1 - y0) / h
            if kind in ('fin', 'tail'):
                return fin(i, j, kind == 'tail')
            if kind == 'mouth':
                return MOUTH
            if kind == 'snout':
                # Dark on top, pale underneath, along the box's OWN axis - after the 45 degree turn that
                # lands as a dark upper nose over a pale chin, which is what a trout's head does.
                u = j / h
                if u > 0.62:
                    return SPECKLE if noise(0, y, z) > 0.72 else BACK_DARK
                if u > 0.40:
                    return BACK if noise(0, y, z) > 0.78 else FLANK_HI
                return FLANK_LO if u > 0.2 else BELLY
            if kind == 'head_eye':
                # One dark eye with a ring, sized in model units - the head is only five units tall, and
                # a radius quoted in texels ate the whole side of the fish the first time round.
                ex = w * 0.22
                ey = h * 0.30
                d = (((i + 0.5 - ex) / DETAIL) ** 2 + ((j + 0.5 - ey) / DETAIL) ** 2) ** 0.5
                if d < EYE_R:
                    return EYE
                if d < EYE_RING_R:
                    return EYE_RING
            return flank(y, z)
        if face == 'up':
            z = z0 + (i + 0.5) * (z1 - z0) / w
            x = x0 + (j + 0.5) * (x1 - x0) / h
            if kind in ('fin', 'tail'):
                return fin(i, j)
            if kind == 'mouth':
                return MOUTH
            if kind == 'snout':
                return back(z, x)
            return back(z, x)
        if face == 'down':
            z = z0 + (i + 0.5) * (z1 - z0) / w
            x = x0 + (j + 0.5) * (x1 - x0) / h
            if kind in ('fin', 'tail'):
                return fin(i, j)
            if kind == 'mouth':
                # The underside of the mouth block is the chin, not the gape.
                return FLANK_LO
            return belly(z, x)
        # north / south: the cross-section, painted by height so the cut matches the flanks either side.
        y = y1 - (j + 0.5) * (y1 - y0) / h
        if kind in ('fin', 'tail'):
            return fin(i, j)
        # Only the mouth block is the mouth. Painting the skull's front face dark too put a maroon mask
        # across the whole head, because the wedge does not cover its corners.
        if kind == 'mouth':
            return MOUTH
        if kind == 'snout':
            return BACK_DARK if face == 'north' else FLANK_LO
        return flank(y, z0 if face == 'north' else z1)

    return painter


cubes = []
for p in PARTS:
    uv = {}
    for face in FACES:
        w, h = face_size(p, face)
        rect = alloc(w, h)
        paint(rect, w, h, make_painter(face, w, h, p))
        u, v = rect
        k = UV_SPACE / ATLAS
        # East and west read the same rectangle from opposite ends, so the fish faces the same way on
        # both flanks instead of swimming backwards on one of them.
        box = [u + w, v, u, v + h] if face == 'west' else [u, v, u + w, v + h]
        uv[face] = [round(c * k, 4) for c in box]
    cubes.append({'name': p['name'], 'from': p['frm'], 'to': p['to'],
                  'origin': p['origin'], 'rotation': p['rot'],
                  'faces': [{'face': f, 'uv': uv[f]} for f in FACES]})

os.makedirs(OUT, exist_ok=True)


def write_png(path, rows):
    raw = b''.join(b'\x00' + b''.join(struct.pack('BBBB', *px) for px in row) for row in rows)

    def chunk(tag, data):
        c = tag + data
        return struct.pack('>I', len(data)) + c + struct.pack('>I', zlib.crc32(c) & 0xffffffff)

    open(path, 'wb').write(b'\x89PNG\r\n\x1a\n'
                           + chunk(b'IHDR', struct.pack('>IIBBBBB', ATLAS, ATLAS, 8, 6, 0, 0, 0))
                           + chunk(b'IDAT', zlib.compress(raw, 9))
                           + chunk(b'IEND', b''))


GROUPS = [
    ('head', [0, 0, -11.6], ['head_wedge', 'mouth', 'skull']),
    ('body', [0, 0, 0], ['body_front', 'body_rear', 'peduncle']),
    ('fins', [0, 0, 0], ['tail', 'dorsal', 'adipose', 'anal',
                         'pectoral_right', 'pectoral_left', 'pelvic_right', 'pelvic_left']),
]

NS = uuidlib.UUID('1b4e28ba-2fa1-11d2-883f-0016d3cca427')


def uid(name):
    """Stable ids, so re-running the script rewrites the same file rather than a different one."""
    return str(uuidlib.uuid5(NS, 'trout:' + name))


def write_bbmodel(path, png_path):
    """Write the Blockbench project directly.

    Doing this here rather than through the editor is deliberate: the texture rides INSIDE the file as a
    data url, so the model can never drift from the atlas it was drawn against - which is exactly what
    happens when a project points at a texture on disk and then holds a stale copy of it."""
    elements = []
    for c in cubes:
        e = {
            'name': c['name'], 'box_uv': False, 'render_order': 'default', 'locked': False,
            'export': True, 'scope': 0, 'allow_mirror_modeling': True,
            'from': c['from'], 'to': c['to'], 'autouv': 0, 'color': 0, 'origin': c['origin'],
            'faces': {f['face']: {'uv': f['uv'], 'texture': 0} for f in c['faces']},
            'type': 'cube', 'uuid': uid(c['name']),
        }
        if any(c['rotation']):
            e['rotation'] = c['rotation']
        elements.append(e)

    groups, outliner = [], []
    for name, origin, members in GROUPS:
        gid = uid('group:' + name)
        kids = [uid(m) for m in members]
        groups.append({'name': name, 'uuid': gid, 'export': True, 'locked': False, 'scope': 0,
                       'selected': False, '_static': {'properties': {}, 'temp_data': {}},
                       'origin': origin, 'rotation': [0, 0, 0], 'color': 0, 'children': [],
                       'reset': False, 'shade': False, 'mirror_uv': False, 'visibility': True,
                       'autouv': 0, 'isOpen': True, 'primary_selected': False})
        outliner.append({'uuid': gid, 'isOpen': True, 'children': kids})

    src = 'data:image/png;base64,' + base64.b64encode(open(png_path, 'rb').read()).decode('ascii')
    # path stays EMPTY and internal stays true on purpose. Give Blockbench a path and it loads the file
    # from disk - through a cache that does not notice the file changing - and quietly ignores the bytes
    # embedded here. That cost an hour of chasing a texture that was correct in every file and wrong on
    # screen. With no path there is nothing to prefer, so the embedded atlas is the only atlas.
    texture = {
        'name': 'rainbow_trout.png', 'path': '', 'folder': '', 'namespace': '',
        'id': '0', 'group': '', 'scope': 0, 'width': ATLAS, 'height': ATLAS,
        'uv_width': ATLAS, 'uv_height': ATLAS, 'particle': False, 'use_as_default': False,
        'layers_enabled': False, 'sync_to_project': '', 'file_format': 'png', 'internal': True,
        'render_mode': 'default', 'render_sides': 'auto', 'wrap_mode': 'limited',
        'pbr_channel': 'color', 'fps': 7, 'frame_time': 1, 'frame_order_type': 'loop',
        'frame_order': '', 'frame_interpolate': False, 'visible': True, 'internal': True,
        'saved': False, 'uuid': uid('texture'), 'source': src,
    }

    model = {
        'meta': {'format_version': '5.0', 'model_format': 'free', 'box_uv': False},
        'name': 'rainbow_trout', 'model_identifier': '', 'visible_box': [2, 2, 0],
        'variable_placeholders': '', 'multi_file_ruleset': '', 'variable_placeholder_buttons': [],
        'timeline_setups': [], 'unhandled_root_fields': {},
        'resolution': {'width': ATLAS, 'height': ATLAS},
        'elements': elements, 'groups': groups, 'outliner': outliner, 'textures': [texture],
    }
    with open(path, 'w', encoding='utf-8') as f:
        json.dump(model, f, indent=1)
    return len(elements)


write_png(os.path.join(OUT, 'rainbow_trout.png'), pixels)
json.dump(cubes, open(os.path.join(OUT, 'rainbow_trout_cubes.json'), 'w'), indent=1)
n = write_bbmodel(os.path.join(OUT, 'rainbow_trout.bbmodel'), os.path.join(OUT, 'rainbow_trout.png'))
print('%d cubes into %s/rainbow_trout.bbmodel, texture embedded' % (n, OUT))
print('%d cubes, %d texels per unit, atlas %d x %d using %d rows -> %s/rainbow_trout.png'
      % (len(cubes), DETAIL, ATLAS, ATLAS, shelf['y'] + shelf['h'], OUT))
