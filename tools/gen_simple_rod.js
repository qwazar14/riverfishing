// The reel-less blanks (§rod-3d): bamboo and pole. RodType marks them takesReel=false, so neither
// carries a reel seat or a single guide — the line is tied straight to the tip. That makes them one
// shape: a tapered tube with periodic collars, differing only in taper, collar style and grip.
//
//   node tools/gen_simple_rod.js <bamboo|pole> <out.json>
//
// The stick rod is reel-less too but is NOT this shape — it is three sticks lashed together, the way
// it is crafted. See tools/gen_stick_rod.js.
//
// The butt sits at x=32 for every rod, matching the feeder, because RodHandTransform's 3D pose set
// grips there — a rod that ended anywhere else would sit wrong in the hand.
const fs = require('fs'), path = require('path');
const { packBoxUVs } = require('./lib/box_uv');

const BUTT_X = 32, AXIS_Y = 10.5, AXIS_Z = 8.5, MIN_X = -16;

const RODS = {
  // len: units from the butt. 1 unit ~ 8 cm, the scale the feeder already established
  // (48 units drawn for a 3.9 m rod). collar: what sits at each section joint.
  bamboo: { len: 44, butt: 2.0, tip: 0.4, sections: 6, collar: 'node',    grip: 6 },
  pole:   { len: 48, butt: 1.6, tip: 0.2, sections: 5, collar: 'ferrule', grip: 9 },
  // §ice-fishing: a mormyshka rod really is this stubby — 10u = 60 cm, mostly handle, fished
  // vertically over a hole. The tip_whipping doubles as the brightly painted nod.
  winter: { len: 10, butt: 1.1, tip: 0.25, sections: 2, collar: 'ferrule', grip: 3.5 },
};

const kind = process.argv[2], outFile = process.argv[3];
const cfg = RODS[kind];
if (!cfg) throw new Error(`unknown rod "${kind}" — expected one of ${Object.keys(RODS).join(', ')}`);
if (BUTT_X - cfg.len < MIN_X) {
  throw new Error(`${kind} is ${cfg.len}u long, which runs past MC's ${MIN_X} floor`);
}

const r = n => Math.round(n * 1000) / 1000;
const at = (x0, x1, h, d, name) => ({
  name, from: [r(x1), r(AXIS_Y - h / 2), r(AXIS_Z - d / 2)], to: [r(x0), r(AXIS_Y + h / 2), r(AXIS_Z + d / 2)],
});
// diameter at distance t (0 = butt, 1 = tip). Squared falloff: a real blank thins fast near the tip.
const dia = t => cfg.butt + (cfg.tip - cfg.butt) * (t * t * 0.45 + t * 0.55);

const parts = [];
const tipX = BUTT_X - cfg.len;

if (cfg.grip) parts.push(at(BUTT_X, BUTT_X - cfg.grip, cfg.butt * 1.18, cfg.butt * 1.18, 'grip'));

const secStart = BUTT_X - cfg.grip;
const secLen = (secStart - tipX) / cfg.sections;
for (let i = 0; i < cfg.sections; i++) {
  const x0 = secStart - i * secLen, x1 = x0 - secLen;
  const t0 = (BUTT_X - x0) / cfg.len, t1 = (BUTT_X - x1) / cfg.len;
  const d = (dia(t0) + dia(t1)) / 2;                 // one box per section, so use its mid diameter
  parts.push(at(x0, x1, d, d, `section_${i + 1}`));
  if (i === cfg.sections - 1) break;                 // no collar past the last joint
  // bamboo nodes are pronounced rings; a telescopic ferrule is a slim collar
  const cd = dia(t1);
  const w = cfg.collar === 'node' ? cd * 1.35 : cd * 1.18;
  const thick = cfg.collar === 'node' ? 1.0 : 0.7;
  parts.push(at(x1 + thick / 2, x1 - thick / 2, w, w, `${cfg.collar}_${i + 1}`));
}
// where the line is tied on: a whipping of thread, the only fitting these rods have
parts.push(at(tipX + 1.2, tipX, cfg.tip * 1.6, cfg.tip * 1.6, 'tip_whipping'));

// --- UV: shelf-packed box unwraps, nothing overlapping ---
const TEX = 64;

// Collars of a kind share ONE unwrap: five bamboo nodes are five drawings of the same ring, and
// paying sheet space per copy overflowed 16 units on its own. Sections and the grip differ, so they
// keep theirs.
const dims = parts.map(e => [0, 1, 2].map(i => r(e.to[i] - e.from[i])));
const keyOf = n => /^(node|ferrule)_/.test(n) ? n.replace(/_\d+$/, '') : n;
const { uvs, lengthScale, used } = packBoxUVs(dims, parts.map(e => keyOf(e.name)));

const out = {
  format_version: '1.9.0',
  credit: 'Made with Blockbench',
  texture_size: [TEX, TEX],
  textures: { 0: 'texture', particle: 'texture' },
  elements: parts.map((e, idx) => {
    const uv = uvs[idx];
    return { name: e.name, from: e.from, to: e.to,
      rotation: { angle: 0, axis: 'y', origin: [e.from[0], AXIS_Y, AXIS_Z] },
      faces: Object.fromEntries(['north', 'east', 'south', 'west', 'up', 'down']
        .map(f => [f, { uv: uv[f], texture: '#0' }])) };
  }),
};

// --- self-checks ---
for (const e of out.elements) {
  for (const v of [...e.from, ...e.to]) {
    if (v < MIN_X || v > 32) throw new Error(`${e.name}: coord ${v} outside MC's ${MIN_X}..32`);
  }
}
const butt = Math.max(...out.elements.map(e => e.to[0]));
if (butt !== BUTT_X) throw new Error(`butt ends at ${butt}, not ${BUTT_X} — the hand pose grips there`);
// the blank must actually thin toward the tip, or the taper config is wrong
const secs = out.elements.filter(e => /^section_/.test(e.name));
for (let i = 1; i < secs.length; i++) {
  const prev = secs[i - 1].to[1] - secs[i - 1].from[1], cur = secs[i].to[1] - secs[i].from[1];
  if (cur >= prev) throw new Error(`${secs[i].name} (${r(cur)}u) is not thinner than ${secs[i - 1].name} (${r(prev)}u)`);
}

fs.mkdirSync(path.dirname(outFile), { recursive: true });
const compact = o => JSON.stringify(o, null, '\t')
  .replace(/\[\n\t+(-?[\d.]+,\n\t+)*-?[\d.]+\n\t+\]/g, x => x.replace(/\s+/g, ' ').replace(/\[ /, '[').replace(/ \]/, ']'));
fs.writeFileSync(outFile, compact(out) + '\n');

console.log(`${kind}: ${out.elements.length} elements, ${cfg.len}u = ${r(cfg.len / 16)} m at scale 1`);
console.log(`   x ${tipX}..${BUTT_X}   taper ${secs.map(e => r(e.to[1] - e.from[1])).join(' -> ')}`);
console.log(`   collars: ${cfg.collar} x${cfg.sections - 1}${cfg.grip ? `, grip ${cfg.grip}u` : ', no grip (bare wood)'}`);
console.log(`   UV sheet ${used} of 16, length squeezed ${lengthScale}x ` +
  `(${r(lengthScale * TEX / 16)} texels per unit along the rod, ${r(TEX / 16)} across)`);
