"""Build the tackle station block: a barrel with drawers, and the tackle laid out on its lid.

Writes the Minecraft block model and the texture it wears, from one description:

    py tools/gen_tackle_station.py

    assets/riverfishing/models/block/fishing_stall.json
    assets/riverfishing/textures/block/tackle_station.png
    fish3d/tackle_station.bbmodel      (the same model, for looking at in Blockbench)

Two things constrain every decision here, and both are Minecraft's, not mine:

  * A block model element may carry ONE rotation, on ONE axis, at one of -45/-22.5/0/22.5/45 degrees.
    So nothing here is at a free angle, and a round barrel cannot be an octagon - the union of a box
    and a 45-degree box is an eight-pointed star, never an octagon (each square's corners always
    escape the other's faces). A barrel therefore reads by its BULGE and its hoops instead: three
    stacked bands, narrow-wide-narrow, which is the silhouette anyone recognises.
  * Face UVs are always 0..16 regardless of how big the texture actually is. The atlas here is 64px,
    so one uv unit is four texels.

The barrel's staves are mapped by WORLD position rather than per box, so the vertical seams and the two
iron hoops run continuously across the three bands instead of restarting at every seam.
"""
import base64
import json
import os
import struct
import uuid as uuidlib
import zlib

ATLAS = 64          # texels
T = ATLAS // 16     # texels per uv unit
ASSETS = 'common/src/main/resources/assets/riverfishing'
MODEL = os.path.join(ASSETS, 'models/block/fishing_stall.json')
TEX = os.path.join(ASSETS, 'textures/block/tackle_station.png')
PREVIEW = 'fish3d/tackle_station.bbmodel'

BODY_TOP = 11.0     # the barrel, lid included, ends here; the tackle sits above it

# One palette, three ramps, and nothing outside them. The first pass gave every material its own
# colours and then scattered per-texel random noise through all of them; that is what made it read as
# motley up close and SHIMMER at distance - high-frequency noise is exactly what a mipmap cannot
# average, so the block sparkled as you walked. Every surface below is shaded by STRUCTURE instead:
# a seam, a highlight, a bevel, a ring. Light comes from the upper left throughout.
WOOD_LT = (178, 142, 98)
WOOD = (150, 116, 76)
WOOD_DK = (124, 94, 60)
WOOD_SH = (99, 74, 47)

MET_LT = (178, 180, 178)
MET = (140, 142, 142)
MET_DK = (104, 106, 108)
MET_SH = (74, 76, 80)

LINE_LT = (224, 226, 220)
LINE = (196, 198, 190)
LINE_DK = (160, 164, 158)

RED_LT = (198, 86, 68)
RED = (168, 60, 48)
RED_DK = (128, 42, 34)
CREAM = (232, 224, 206)
GEM_LT = (122, 226, 158)
GEM = (62, 186, 112)
GEM_DK = (34, 132, 78)
DARK = (54, 46, 40)

STAVE = 8               # texels across one stave: two model pixels
WOOD_RAMP = (WOOD_SH, WOOD_DK, WOOD, WOOD_LT)


def step(ramp, c, n):
    """Move a colour n places along its ramp, clamped. How every shadow and highlight here is made."""
    i = ramp.index(c) + n
    return ramp[max(0, min(len(ramp) - 1, i))]


def grain(sx, band):
    """Which tone a stave is, and where its one long grain line runs.

    Deterministic per stave, and only ONE line each - a plank reads as a plank because of its edges,
    not because it is peppered."""
    h = ((sx * 2654435761) ^ (band * 40503)) & 0xffff
    return h


# --- the painters ------------------------------------------------------------------------------

def stave(bx, by):
    """The barrel's side, in texels of the strip: staves with shaded edges, and two iron hoops.

    bx runs across the barrel, by from its lid (0) down to its foot (43)."""
    for hoop_y in (2.0, 8.4):
        top = (BODY_TOP - hoop_y - 0.9) * T
        if top <= by < top + 1.8 * T:
            r = by - top
            if r < 1:
                return MET_LT
            if r > 1.8 * T - 2:
                return MET_SH
            return MET if r > 2 else MET_DK

    sx = bx // STAVE
    p = bx % STAVE
    base = WOOD if grain(sx, 0) % 3 else WOOD_DK       # one stave in three is a shade darker
    # Staves are barrel slats: a dark seam, a lit edge beside it, a shaded edge before the next seam.
    if p == 0:
        c = WOOD_SH
    elif p == 1:
        c = step(WOOD_RAMP, base, 1)
    elif p >= STAVE - 2:
        c = step(WOOD_RAMP, base, -1)
    else:
        c = base
    # A single grain line down each stave, in the same place every time.
    if 2 <= p < STAVE - 2 and p == 2 + grain(sx, 1) % 3 and (by + grain(sx, 2)) % 19 > 5:
        c = step(WOOD_RAMP, c, -1)
    # The barrel is darker toward its foot, the way anything standing on the ground is.
    if by > 34:
        c = step(WOOD_RAMP, c, -1)
    return c


def lid_top(u, v, w, h):
    """The lid seen from above: boards across it, an iron rim, and the bung."""
    cx, cy = w / 2.0, h / 2.0
    d = max(abs(u + 0.5 - cx), abs(v + 0.5 - cy)) / cx
    if d > 0.95:
        return MET_SH
    if d > 0.84:
        return MET_LT if (u < cx and v < cy) else MET
    if abs(u + 0.5 - cx) < 1.6 and abs(v + 0.5 - cy) < 1.6:
        return WOOD_SH if abs(u + 0.5 - cx) < 1.0 and abs(v + 0.5 - cy) < 1.0 else WOOD_DK
    board = int(v // 4)
    c = WOOD if board % 2 == 0 else WOOD_DK
    if v % 4 == 0:
        c = WOOD_SH                                    # the line between two boards
    elif v % 4 == 1:
        c = step(WOOD_RAMP, c, 1)
    return c


def drawer_front(u, v, w, h):
    """A drawer face: a sunken panel, lit along its top and left, shadowed under and right."""
    if u == 0 or v == 0:
        return WOOD_LT
    if u >= w - 1 or v >= h - 1:
        return WOOD_SH
    if u == 1 or v == 1:
        return WOOD_SH                                 # the panel's cut edge
    if u >= w - 2 or v >= h - 2:
        return step(WOOD_RAMP, WOOD_DK, 1)
    return WOOD_DK


def metal(u, v, w, h):
    """Plain iron: lit top edge, shadowed bottom edge, flat between."""
    if v == 0:
        return MET_LT
    if v >= h - 1:
        return MET_SH
    return MET if v < h - 2 else MET_DK


def coil_top(u, v, w, h):
    """Line wound on a spool: rings of monofilament round a dark core."""
    cx, cy = w / 2.0, h / 2.0
    d = (((u + 0.5 - cx) / cx) ** 2 + ((v + 0.5 - cy) / cy) ** 2) ** 0.5
    if d > 1.0:
        return (0, 0, 0, 0)
    if d < 0.26:
        return DARK
    if d < 0.36:
        return MET_DK
    lit = (u < cx) == (v < cy)
    c = LINE if int(d * 7) % 2 == 0 else LINE_DK
    return LINE_LT if (lit and c is LINE) else c


def coil_side(u, v, w, h):
    if v == 0:
        return LINE_LT
    if v >= h - 1:
        return LINE_DK
    return LINE if (v // 2) % 2 == 0 else LINE_DK


def float_skin(u, v, w, h):
    """A float: red shoulder, dark waist, pale body, with the light down its left."""
    t = v / max(1.0, h - 1)
    lit = u < w / 3
    if t < 0.34:
        return RED_LT if lit else RED
    if t < 0.44:
        return DARK
    return CREAM if lit else (210, 202, 186)


def emerald(u, v, w, h):
    """A cut stone: a bright facet up and left, a dark one down and right, a firm outline."""
    cx, cy = w / 2.0, h / 2.0
    d = (abs(u + 0.5 - cx) + abs(v + 0.5 - cy)) / (cx + cy)
    if d > 0.9:
        return GEM_DK
    if u + 0.5 < cx and v + 0.5 < cy:
        return GEM_LT
    return GEM if d < 0.62 else GEM_DK


def hook_tray(u, v, w, h):
    """A shallow wooden tray with three hooks in it: a shank, a bend, a point.

    Wooden, not black. Near-black was the only value that dark anywhere on the block and it read as a
    hole punched in the lid rather than as a tray."""
    c = WOOD_SH if (u + v) % 6 else WOOD_DK
    if u == 0 or v == 0:
        c = WOOD_DK
    if u >= w - 1 or v >= h - 1:
        c = DARK
    for sx in (0.16, 0.44, 0.72):
        x = int(sx * w)
        if u == x and 1 <= v < h - 3:
            c = MET_LT
        if x <= u <= x + 2 and v == h - 3:
            c = MET_LT
        if u == x + 2 and v == h - 4:
            c = MET
    return c


def steel(u, v, w, h):
    """Polished steel, lit along the top."""
    if v == 0:
        return MET_LT
    if v >= h - 2:
        return MET_DK
    return MET


# --- atlas -------------------------------------------------------------------------------------
# Each region is (texel x, texel y, texel w, texel h, painter). The barrel's side strip is the whole
# top of the atlas so world-mapped uvs can run across it without a seam.
REGIONS = {
    'side':    (0, 0, 64, 44, None),          # painted specially, by world position
    'lid':     (0, 44, 16, 16, lid_top),
    'drawer':  (16, 44, 16, 12, drawer_front),
    'metal':   (16, 56, 16, 8, metal),
    'coil':    (32, 44, 12, 12, coil_top),
    'coilsd':  (44, 44, 8, 12, coil_side),
    'float':   (52, 44, 6, 16, float_skin),
    'gem':     (58, 44, 6, 10, emerald),
    'hooks':   (32, 56, 12, 8, hook_tray),
    'steel':   (44, 56, 8, 8, steel),
}

pixels = [[(0, 0, 0, 0)] * ATLAS for _ in range(ATLAS)]


def paint_atlas():
    x0, y0, w, h, _ = REGIONS['side']
    for j in range(h):
        row = pixels[y0 + j][:]
        for i in range(w):
            row[x0 + i] = stave(i, j) + (255,)
        pixels[y0 + j] = row
    for name, (x0, y0, w, h, fn) in REGIONS.items():
        if fn is None:
            continue
        for j in range(h):
            row = pixels[y0 + j][:]
            for i in range(w):
                c = fn(i, j, w, h)
                row[x0 + i] = c if len(c) == 4 else c + (255,)
            pixels[y0 + j] = row


def uv(name, fx=0.0, fy=0.0, fw=1.0, fh=1.0):
    """A uv rect (0..16) over a fraction of a named region."""
    x0, y0, w, h, _ = REGIONS[name]
    return [round((x0 + fx * w) / T, 3), round((y0 + fy * h) / T, 3),
            round((x0 + (fx + fw) * w) / T, 3), round((y0 + (fy + fh) * h) / T, 3)]


def side_uv(a, b, y0, y1):
    """Barrel side uv for a face running across from a to b (model px) between heights y0..y1.

    Mapped by world position, so the staves and hoops line up where two bands meet."""
    return [round(a, 3), round(BODY_TOP - y1, 3), round(b, 3), round(BODY_TOP - y0, 3)]


def band(name, x0, y0, x1, y1, up='lid', down='lid'):
    """One of the barrel's stacked bands.

    Every band gets a top AND a bottom, always. The bands are different widths - that is what makes the
    barrel bulge - so at each step a ring of the wider band is left facing the sky with nothing above it.
    Leave that ring unfaced and you look straight down into a hollow barrel and out the other side: the
    first build had a clean hole through it between the shoulder and the bulge."""
    faces = {}
    for f in ('north', 'south', 'east', 'west'):
        faces[f] = {'uv': side_uv(x0, x1, y0, y1), 'texture': '#0'}
    faces['up'] = {'uv': uv(up), 'texture': '#0'}
    faces['down'] = {'uv': uv(down), 'texture': '#0'}
    return {'name': name, 'from': [x0, y0, x0], 'to': [x1, y1, x1], 'faces': faces}


def box(name, frm, to, mat, rot=None, up=None, down=None, north=None, frac=None):
    """A box wearing one material on every face, with optional per-face overrides.

    `frac` is (x, y, w, h) as fractions of the region: a float's antenna wants only the red stripe at
    the top of the float, not the whole red-band-cream run squashed into its two texels."""
    faces = {}
    base = uv(mat, *frac) if frac else uv(mat)
    for f in ('north', 'south', 'east', 'west', 'up', 'down'):
        faces[f] = {'uv': base, 'texture': '#0'}
    for f, m in (('up', up), ('down', down), ('north', north)):
        if m:
            faces[f] = {'uv': uv(m), 'texture': '#0'}
    e = {'name': name, 'from': frm, 'to': to, 'faces': faces}
    if rot:
        e['rotation'] = rot
    return e


def rot_y(angle, origin):
    return {'origin': origin, 'axis': 'y', 'angle': angle}


def build():
    els = []

    # --- the barrel: narrow foot, bulge, narrow shoulder, then the lid ---------------------------
    els.append(band('barrel_foot', 3.5, 0.0, 12.5, 3.0, down='lid'))
    els.append(band('barrel_bulge', 2.5, 3.0, 13.5, 7.0))
    els.append(band('barrel_shoulder', 3.5, 7.0, 12.5, 10.0))
    els.append(band('barrel_lid', 2.8, 10.0, 13.2, 11.0, up='lid'))

    # --- the drawers, on the block's north face (the blockstate turns the block to the player) ---
    for i, (y0, y1) in enumerate(((0.9, 4.0), (4.7, 7.8))):
        els.append(box('drawer_%d' % i, [4.5, y0, 1.9], [11.5, y1, 3.7], 'drawer',
                       north=None, up='drawer', down='drawer'))
        hy = (y0 + y1) / 2 - 0.35
        els.append(box('handle_%d' % i, [6.8, hy, 1.3], [9.2, hy + 0.7, 2.0], 'metal'))

    # --- what is laid out on the lid -------------------------------------------------------------
    # Two coils of line. Each is a box plus the same box turned 45 degrees: the union is an
    # eight-pointed star, which at four pixels across is exactly what a wound spool looks like.
    els.append(box('coil_big', [3.6, 11.0, 4.4], [7.4, 12.3, 8.2], 'coilsd', up='coil', down='coil'))
    els.append(box('coil_big_x', [3.6, 11.0, 4.4], [7.4, 12.3, 8.2], 'coilsd',
                   rot=rot_y(45, [5.5, 11.65, 6.3]), up='coil', down='coil'))
    els.append(box('coil_small', [4.6, 12.3, 5.4], [6.4, 13.2, 7.2], 'coilsd', up='coil', down='coil'))
    els.append(box('coil_small_x', [4.6, 12.3, 5.4], [6.4, 13.2, 7.2], 'coilsd',
                   rot=rot_y(45, [5.5, 12.75, 6.3]), up='coil', down='coil'))

    # A float STANDING, which is the only way it reads as a float. Lying down it was a red brick.
    els.append(box('float_body', [9.3, 11.0, 4.1], [10.7, 13.5, 5.5], 'float'))
    els.append(box('float_tip', [9.8, 13.5, 4.6], [10.2, 15.4, 5.0], 'float',
                   frac=(0.0, 0.0, 1.0, 0.22)))

    # The emerald, stood on its corner and drawn up into a crown so it is a cut stone, not a green cube.
    els.append(box('emerald', [11.4, 11.0, 4.2], [12.6, 12.5, 5.4], 'gem',
                   rot=rot_y(45, [12.0, 11.75, 4.8])))
    els.append(box('emerald_crown', [11.65, 12.5, 4.45], [12.35, 13.5, 5.15], 'gem',
                   rot=rot_y(45, [12.0, 13.0, 4.8])))

    # The tying pliers: two handles opening into a V, and the nose they close on. Kept clear of the
    # hook tray - the first layout drove the nose straight through it.
    els.append(box('pliers_a', [3.2, 11.0, 9.6], [8.6, 11.6, 10.4], 'steel',
                   rot=rot_y(22.5, [8.6, 11.3, 10.0])))
    els.append(box('pliers_b', [3.2, 11.0, 9.6], [8.6, 11.6, 10.4], 'steel',
                   rot=rot_y(-22.5, [8.6, 11.3, 10.0])))
    els.append(box('pliers_nose', [8.6, 11.0, 9.65], [9.9, 11.55, 10.35], 'steel'))

    # A tray of hooks.
    els.append(box('hook_tray', [10.3, 11.0, 8.6], [13.0, 11.5, 12.3], 'metal',
                   up='hooks', down='metal'))
    return els


def write_png(path, rows):
    raw = b''.join(b'\x00' + b''.join(struct.pack('BBBB', *px) for px in row) for row in rows)

    def chunk(tag, data):
        c = tag + data
        return struct.pack('>I', len(data)) + c + struct.pack('>I', zlib.crc32(c) & 0xffffffff)

    open(path, 'wb').write(b'\x89PNG\r\n\x1a\n'
                           + chunk(b'IHDR', struct.pack('>IIBBBBB', ATLAS, ATLAS, 8, 6, 0, 0, 0))
                           + chunk(b'IDAT', zlib.compress(raw, 9))
                           + chunk(b'IEND', b''))


NS = uuidlib.UUID('1b4e28ba-2fa1-11d2-883f-0016d3cca427')


def uid(name):
    return str(uuidlib.uuid5(NS, 'tackle:' + name))


def write_bbmodel(path, els, png_path):
    """The same model as a Blockbench project, with the texture embedded.

    Embedded, and with no path: given a path Blockbench loads the file through a cache that never
    notices it changing, and ignores the bytes stored here."""
    elements = []
    for e in els:
        el = {'name': e['name'], 'box_uv': False, 'render_order': 'default', 'locked': False,
              'export': True, 'scope': 0, 'allow_mirror_modeling': True,
              'from': e['from'], 'to': e['to'], 'autouv': 0, 'color': 0,
              'origin': e.get('rotation', {}).get('origin', [8, 8, 8]),
              'faces': {f: {'uv': v['uv'], 'texture': 0} for f, v in e['faces'].items()},
              'type': 'cube', 'uuid': uid(e['name'])}
        if 'rotation' in e:
            r = e['rotation']
            el['rotation'] = {'x': [r['angle'], 0, 0], 'y': [0, r['angle'], 0],
                              'z': [0, 0, r['angle']]}[r['axis']]
        elements.append(el)
    src = 'data:image/png;base64,' + base64.b64encode(open(png_path, 'rb').read()).decode('ascii')
    model = {
        'meta': {'format_version': '5.0', 'model_format': 'java_block', 'box_uv': False},
        'name': 'tackle_station', 'model_identifier': '', 'visible_box': [2, 2, 0],
        'variable_placeholders': '', 'multi_file_ruleset': '', 'variable_placeholder_buttons': [],
        'timeline_setups': [], 'unhandled_root_fields': {},
        'resolution': {'width': ATLAS, 'height': ATLAS},
        'elements': elements, 'groups': [],
        'outliner': [e['uuid'] for e in elements],
        'textures': [{
            'name': 'tackle_station.png', 'path': '', 'folder': 'block',
            'namespace': 'riverfishing', 'id': '0', 'group': '', 'scope': 0,
            'width': ATLAS, 'height': ATLAS, 'uv_width': 16, 'uv_height': 16,
            'particle': True, 'use_as_default': False, 'layers_enabled': False,
            'sync_to_project': '', 'file_format': 'png', 'internal': True,
            'render_mode': 'default', 'render_sides': 'auto', 'wrap_mode': 'limited',
            'pbr_channel': 'color', 'fps': 7, 'frame_time': 1, 'frame_order_type': 'loop',
            'frame_order': '', 'frame_interpolate': False, 'visible': True, 'saved': False,
            'uuid': uid('texture'), 'source': src,
        }],
    }
    json.dump(model, open(path, 'w', encoding='utf-8'), indent=1)


paint_atlas()
os.makedirs(os.path.dirname(TEX), exist_ok=True)
write_png(TEX, pixels)

elements = build()
model = {
    'parent': 'block/block',
    'textures': {'0': 'riverfishing:block/tackle_station',
                 'particle': 'riverfishing:block/tackle_station'},
    'elements': elements,
}
os.makedirs(os.path.dirname(MODEL), exist_ok=True)
json.dump(model, open(MODEL, 'w', encoding='utf-8'), indent=2)

os.makedirs('fish3d', exist_ok=True)
write_bbmodel(PREVIEW, elements, TEX)

top = max(e['to'][1] for e in elements)
wide = max(max(e['to'][0], e['to'][2]) for e in elements)
low = min(min(e['from'][0], e['from'][2]) for e in elements)
print('%d elements, height %.2f/16, footprint %.1f..%.1f -> %s' % (len(elements), top, low, wide, MODEL))
