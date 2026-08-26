// The reel-carrying casting blanks (§rod-3d): spinning and ultralight. RodType gives both
// takesReel=true, so unlike the stick/bamboo/pole family these get the full kit — split grip, reel
// seat, and a train of guides UNDER the blank (a spinning reel hangs below, the line runs below).
//
//   node tools/gen_spin_rod.js <spinning|ultralight> <out.json>
//
// Butt at x=32 like every other 3D blank, because RodHandTransform's 3D pose grips there.
// Guides follow the feeder's convention: zero thickness in X, so only the ±X faces have area, and
// the ring itself is cut out of the texture rather than modelled.
const fs = require('fs'), path = require('path');
const { packBoxUVs } = require('./lib/box_uv');

const BUTT_X = 32, AXIS_Y = 10.5, AXIS_Z = 8.5, TEX = 64;
const SEAT_UNDERSIDE = 9.45;      // where a reel foot docks — must match the reel models
const r = n => Math.round(n * 1000) / 1000;

const RODS = {
  // len in units at ~8 cm each, the scale the feeder set. seat = [xTip, xButt] of the reel seat.
  // Proportions follow RodType's classes: reel size sets how beefy the seat end is, cast weight how
  // fast the blank tapers, and the working style sets the length.
  spinning:   { len: 30, butt: 1.30, tip: 0.30, cap: 1.55, seat: [24,   29.5], fore: 2.5, guides: 7 },
  ultralight: { len: 25, butt: 1.00, tip: 0.22, cap: 1.25, seat: [25,   29.5], fore: 2.2, guides: 6 },
  sea_spin:   { len: 34, butt: 1.50, tip: 0.40, cap: 1.75, seat: [24.5, 30],   fore: 2.5, guides: 7 },
  bottom:     { len: 42, butt: 1.60, tip: 0.45, cap: 1.85, seat: [23.5, 29.5], fore: 3,   guides: 6 },
  carp:       { len: 46, butt: 1.50, tip: 0.40, cap: 1.70, seat: [24,   29.5], fore: 2.8, guides: 6 },
  // a surf rod is all butt: the seat sits far up so both hands fit below it for the power cast
  surf:       { len: 48, butt: 1.80, tip: 0.50, cap: 2.00, seat: [22,   28],   fore: 4,   guides: 6 },
  boat:       { len: 26, butt: 1.70, tip: 0.55, cap: 2.00, seat: [19.5, 25],   fore: 3,   guides: 6 },
  // §trolling: short, thick, and mostly handle — it fights from a holder, not from a cast
  trolling:   { len: 24, butt: 1.90, tip: 0.70, cap: 2.20, seat: [19,   24.5], fore: 3.5, guides: 5 },
};

const kind = process.argv[2], outFile = process.argv[3];
const cfg = RODS[kind];
if (!cfg) throw new Error(`unknown rod "${kind}" — expected ${Object.keys(RODS).join(' or ')}`);

const tipX = BUTT_X - cfg.len;
if (tipX < -16) throw new Error(`${kind} runs to x=${tipX}, past MC's -16 floor`);

const at = (name, x0, x1, h, d, cy = AXIS_Y, cz = AXIS_Z) => ({
  name, from: [r(x0), r(cy - h / 2), r(cz - d / 2)], to: [r(x1), r(cy + h / 2), r(cz + d / 2)],
});

const parts = [];
const [seatTip, seatButt] = cfg.seat;
const foreEnd = seatTip - cfg.fore;              // where the blank starts, in front of the fore grip

// --- handle: split grip, the modern spinning layout ---
parts.push(at('butt_cap',  BUTT_X - 1,  BUTT_X,   cfg.cap,        cfg.cap));
parts.push(at('rear_grip', seatButt,    BUTT_X - 1, cfg.cap * 0.85, cfg.cap * 0.85));
// the seat is boxier and hangs a little lower — its underside is the reel's docking face
const seatH = (AXIS_Y - SEAT_UNDERSIDE) * 2 + 0.5;
parts.push(at('reel_seat', seatTip, seatButt, seatH, cfg.cap * 0.8, AXIS_Y + 0.25));
parts.push(at('fore_grip', foreEnd, seatTip, cfg.cap * 0.72, cfg.cap * 0.72));

// --- blank: four sections, thinning fast toward the tip ---
const SECTIONS = 4;
const dia = t => cfg.butt + (cfg.tip - cfg.butt) * (t * t * 0.5 + t * 0.5);
const secLen = (foreEnd - tipX) / SECTIONS;
for (let i = 0; i < SECTIONS; i++) {
  const x0 = foreEnd - i * secLen, x1 = x0 - secLen;
  const d = (dia(i / SECTIONS) + dia((i + 1) / SECTIONS)) / 2;
  parts.push(at(`blank_${i + 1}`, x1, x0, d, d));
}

// --- guides, hung UNDER the blank and bunching toward the tip ---
// Sprite is always 5x5 or 3x3 texels (odd => a centre pixel to cut the ring out of); the physical
// size is set independently, so a big butt guide is a scaled-up 5x5, not a 9x9 sprite.
const RING_DROP = 0.25;
const guides = [];
for (let i = 0; i < cfg.guides; i++) {
  const u = i / (cfg.guides - 1);                       // 0 at the butt guide, 1 at the tip guide
  const x = foreEnd - 1.2 - (foreEnd - 1.2 - (tipX + 0.8)) * Math.pow(u, 1.35);  // tighter near the tip
  const host = parts.find(p => /^blank_/.test(p.name) && x >= p.from[0] && x <= p.to[0])
            || parts.filter(p => /^blank_/.test(p.name)).pop();
  const hostThick = host.to[1] - host.from[1];
  // biggest at the butt, like a real guide train — but never narrower than the blank it rings
  const size = Math.max(cfg.butt * (1.9 - 1.15 * u), hostThick * 1.25);
  // The ring hangs below the axis, but only as far as still swallows the blank: dropping it by a
  // flat fraction let the fat butt end poke out through the top of the frame.
  const hi = Math.min(Math.max(AXIS_Y + size / 2 - size * RING_DROP, host.to[1]), host.from[1] + size);
  guides.push({ name: `guide_${i + 1}`, px: size > cfg.butt * 1.3 ? 5 : 3, host,
    from: [r(x), r(hi - size), r(AXIS_Z - size / 2)],
    to:   [r(x), r(hi),        r(AXIS_Z + size / 2)] });
}
parts.push(...guides);
parts.push(at('tip_top', tipX, tipX + 1.1, cfg.tip * 1.5, cfg.tip * 1.5));

// --- UV. Guides of one sprite class share a square; solid parts get their own. ---
const dims = parts.map(e => [0, 1, 2].map(i => r(e.to[i] - e.from[i])));
const keys = parts.map(e => {
  const g = guides.find(x => x.name === e.name);
  return g ? `ring${g.px}` : e.name;
});
// a zero-thickness quad unwraps as a flat square, not a box
dims.forEach((d, i) => { if (d[0] === 0) dims[i] = [0, d[1], d[2]]; });
const { uvs, lengthScale, used } = packBoxUVs(dims, keys);

const ZERO = [0, 0, 0, 0];
const out = {
  format_version: '1.9.0',
  credit: 'Made with Blockbench',
  texture_size: [TEX, TEX],
  textures: { 0: 'texture', particle: 'texture' },
  elements: parts.map((e, i) => {
    const flat = e.from[0] === e.to[0];
    const uv = uvs[i];
    return {
      name: e.name, from: e.from, to: e.to,
      rotation: { angle: 0, axis: 'y', origin: [e.from[0], AXIS_Y, AXIS_Z] },
      faces: Object.fromEntries(['north', 'east', 'south', 'west', 'up', 'down'].map(f => [f,
        { uv: flat && f !== 'east' && f !== 'west' ? ZERO : uv[f], texture: '#0' }])),
    };
  }),
};

// --- self-checks ---
for (const e of out.elements) {
  for (const v of [...e.from, ...e.to]) {
    if (v < -16 || v > 32) throw new Error(`${e.name}: coord ${v} outside MC's -16..32`);
  }
}
if (Math.max(...out.elements.map(e => e.to[0])) !== BUTT_X) throw new Error('butt is not at x=32');
const seat = out.elements.find(e => e.name === 'reel_seat');
if (r(seat.from[1]) !== SEAT_UNDERSIDE) {
  throw new Error(`reel seat underside is ${seat.from[1]}, not ${SEAT_UNDERSIDE} — the reel would not dock`);
}
for (const g of guides) {
  if (g.from[0] !== g.to[0]) throw new Error(`${g.name}: not zero-thickness`);
  if ((g.from[1] + g.to[1]) / 2 >= AXIS_Y) throw new Error(`${g.name}: ring is not below the rod axis`);
  if (g.from[1] > g.host.from[1] || g.to[1] < g.host.to[1]) {
    throw new Error(`${g.name} does not enclose ${g.host.name} — the blank would miss the ring`);
  }
}
const blanks = out.elements.filter(e => /^blank_/.test(e.name));
for (let i = 1; i < blanks.length; i++) {
  if (blanks[i].to[1] - blanks[i].from[1] >= blanks[i - 1].to[1] - blanks[i - 1].from[1]) {
    throw new Error(`${blanks[i].name} is not thinner than ${blanks[i - 1].name}`);
  }
}

fs.mkdirSync(path.dirname(outFile), { recursive: true });
const compact = o => JSON.stringify(o, null, '\t')
  .replace(/\[\n\t+(-?[\d.]+,\n\t+)*-?[\d.]+\n\t+\]/g, x => x.replace(/\s+/g, ' ').replace(/\[ /, '[').replace(/ \]/, ']'));
fs.writeFileSync(outFile, compact(out) + '\n');
if (JSON.parse(fs.readFileSync(outFile, 'utf8')).elements.length !== out.elements.length) {
  throw new Error('the file on disk is short — the write was truncated');
}

const seatMid = r((cfg.seat[0] + cfg.seat[1]) / 2);
console.log(`${kind}: ${out.elements.length} elements, ${cfg.len}u = ${r(cfg.len / 16)} m at scale 1 (x ${tipX}..${BUTT_X})`);
console.log(`   taper ${blanks.map(e => r(e.to[1] - e.from[1])).join(' -> ')}`);
console.log(`   ${cfg.guides} guides at ${guides.map(g => r(g.from[0])).join(', ')}`);
console.log(`   sprites ${guides.map(g => g.px).join('/')}  sizes ${guides.map(g => r(g.to[1] - g.from[1])).join(', ')}`);
console.log(`   reel seat ${cfg.seat[0]}..${cfg.seat[1]} (centre ${seatMid}) — feeder's is 16..23, centre 19.5, ` +
  `so the reel layer needs dx ${r(seatMid - 19.5) >= 0 ? '+' : ''}${r(seatMid - 19.5)}`);
console.log(`   UV sheet ${used} of 16, length squeezed ${lengthScale}x`);
