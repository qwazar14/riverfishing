// The stick rod (§rod-3d): three sticks lashed end to end, which is literally its recipe —
// data/riverfishing/recipe/stick_rod.json is three minecraft:stick on a diagonal. So it is NOT a
// turned taper like the other blanks: three separate crooked sticks, overlapping, bound with cord,
// each one kicked off the axis so the thing visibly does not run straight.
//
//   node tools/gen_stick_rod.js <out.json>
//
// The kinks are OFFSETS, not rotations: the only angles MC loads are 0/±22.5/±45, and 22.5° on a
// 10-unit stick throws its end 4 units sideways — a bent bow, not a crooked stick.
const fs = require('fs'), path = require('path');
const { packBoxUVs } = require('./lib/box_uv');

const BUTT_X = 32, AXIS_Y = 10.5, AXIS_Z = 8.5, TEX = 64;
const r = n => Math.round(n * 1000) / 1000;

// [name, xTip, xButt, thickness, dy, dz] — dy/dz kick this stick off the axis.
// Thickness deliberately does NOT taper evenly: whoever built this grabbed three sticks off the
// ground, so the middle one is barely thinner than the butt and then it falls off a cliff. The steps
// at the joins are the point.
const STICKS = [
  ['stick_butt', 20,   32,   2.1,   0,     0    ],   // the grip: stays on axis or the hand pose misses
  ['stick_mid',  11,   21.5, 1.75,  0.7,  -0.5  ],
  ['stick_tip',   2.5, 12.5, 0.8,  -0.85,  0.6  ],
];

const parts = [];
const boxOf = (name, x0, x1, h, d, cy, cz) => ({
  name, from: [r(x0), r(cy - h / 2), r(cz - d / 2)], to: [r(x1), r(cy + h / 2), r(cz + d / 2)],
});

for (const [name, x0, x1, t, dy, dz] of STICKS) {
  parts.push(boxOf(name, x0, x1, t, t, AXIS_Y + dy, AXIS_Z + dz));
}
// the sticks are only jammed together — no collar, no ferrule, nothing turned on a lathe
for (let i = 0; i < STICKS.length - 1; i++) {
  if (Math.min(parts[i].to[0], parts[i + 1].to[0]) <= Math.max(parts[i].from[0], parts[i + 1].from[0])) {
    throw new Error(`${parts[i].name} and ${parts[i + 1].name} do not overlap — the rod falls apart`);
  }
}

// Twigs nobody bothered to trim. [host, x, angle, length, thickness, up?] — some point up, some down,
// each on its own host so they read as leftovers of three different branches rather than a pattern.
const STUBS = [
  [0, 26.5,  45,   1.9, 0.55, true ],
  [0, 22.5, -22.5, 1.2, 0.4,  false],
  [1, 17,   -45,   1.6, 0.45, true ],
  [1, 13,    22.5, 1.0, 0.35, false],
  [2,  7.5,  45,   0.9, 0.28, true ],
];
STUBS.forEach(([h, x, angle, len, t, up], i) => {
  const host = parts[h];
  const cz = (host.from[2] + host.to[2]) / 2;
  const y = up ? host.to[1] : host.from[1];
  parts.push({ name: `twig_${i + 1}`,
    from: [r(x - t / 2), r(up ? y - 0.12 : y - len), r(cz - t / 2)],
    to:   [r(x + t / 2), r(up ? y + len : y + 0.12), r(cz + t / 2)],
    rot: { origin: [r(x), r(y), r(cz)], axis: 'z', angle } });
});

// A knob at the very end, the only "fitting" on the rod — the line gets tied behind it so it does
// not slide off. Off-centre on purpose; nothing here was made to a drawing.
const tip = parts[2];
parts.push(boxOf('tip_knob', 2.5, 3.6, 1.25, 1.25,
  (tip.from[1] + tip.to[1]) / 2 + 0.1, (tip.from[2] + tip.to[2]) / 2 - 0.08));

const dims = parts.map(e => [0, 1, 2].map(i => r(e.to[i] - e.from[i])));
const { uvs, lengthScale, used } = packBoxUVs(dims, parts.map(e => e.name.replace(/_\d+$/, '')));

const out = {
  format_version: '1.9.0',
  credit: 'Made with Blockbench',
  texture_size: [TEX, TEX],
  textures: { 0: 'texture', particle: 'texture' },
  elements: parts.map((e, i) => ({
    name: e.name, from: e.from, to: e.to,
    rotation: e.rot ? { origin: e.rot.origin, axis: e.rot.axis, angle: e.rot.angle }
                    : { angle: 0, axis: 'y', origin: [e.from[0], AXIS_Y, AXIS_Z] },
    faces: Object.fromEntries(['north', 'east', 'south', 'west', 'up', 'down']
      .map(f => [f, { uv: uvs[i][f], texture: '#0' }])),
  })),
};

// --- self-checks ---
const LEGAL = [0, 22.5, -22.5, 45, -45];
for (const e of out.elements) {
  if (!LEGAL.includes(e.rotation.angle)) throw new Error(`${e.name}: angle ${e.rotation.angle} will not load`);
  for (const v of [...e.from, ...e.to]) {
    if (v < -16 || v > 32) throw new Error(`${e.name}: coord ${v} outside MC's -16..32`);
  }
}
if (Math.max(...out.elements.map(e => e.to[0])) !== BUTT_X) {
  throw new Error(`butt is not at x=${BUTT_X} — the 3D hand pose grips there`);
}
// the grip section must sit ON the axis, or the rod pivots wrong in the hand
const grip = out.elements[0];
if (r((grip.from[1] + grip.to[1]) / 2) !== AXIS_Y || r((grip.from[2] + grip.to[2]) / 2) !== AXIS_Z) {
  throw new Error('stick_butt is off the rod axis; the hand pose assumes the grip is centred');
}
// every twig must be rooted in its host stick, not floating alongside it
STUBS.forEach(([h, x], i) => {
  const host = parts[h], twig = out.elements.find(e => e.name === `twig_${i + 1}`);
  if (x < host.from[0] || x > host.to[0]) {
    throw new Error(`twig_${i + 1} at x=${x} is not on ${host.name} (${host.from[0]}..${host.to[0]})`);
  }
  const overlapsY = twig.from[1] < host.to[1] && twig.to[1] > host.from[1];
  const overlapsZ = twig.from[2] < host.to[2] && twig.to[2] > host.from[2];
  if (!overlapsY || !overlapsZ) throw new Error(`twig_${i + 1} does not touch ${host.name}`);
});

fs.mkdirSync(path.dirname(process.argv[2]), { recursive: true });
const compact = o => JSON.stringify(o, null, '\t')
  .replace(/\[\n\t+(-?[\d.]+,\n\t+)*-?[\d.]+\n\t+\]/g, x => x.replace(/\s+/g, ' ').replace(/\[ /, '[').replace(/ \]/, ']'));
fs.writeFileSync(process.argv[2], compact(out) + '\n');

const xs = out.elements.flatMap(e => [e.from[0], e.to[0]]);
const len = Math.max(...xs) - Math.min(...xs);
console.log(`stick rod: ${out.elements.length} elements, ${r(len)}u = ${r(len / 16)} m at scale 1`);
console.log(`   three sticks ${STICKS.map(s => r(s[2] - s[1]) + 'u').join(' + ')}, jammed together, no lashings`);
console.log(`   thickness steps ${STICKS.map(s => s[3]).join(' -> ')}   kinks ${STICKS.slice(1).map(s => `(${s[4]}, ${s[5]})`).join(' then ')}`);
console.log(`   ${STUBS.length} untrimmed twigs at ${[...new Set(STUBS.map(s => s[2]))].join('/')}deg`);
console.log(`   UV sheet ${used} of 16, length squeezed ${lengthScale}x`);
