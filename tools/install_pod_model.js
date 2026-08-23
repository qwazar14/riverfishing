// Installs the hand-authored rod-pod from 3D/rod_pod/ into the block model + texture.
//
//   node tools/install_pod_model.js
//
// The source file is a SCENE, not a block model: the artist builds the pod with a feeder lying in its
// rests so the proportions can be judged, and those rod parts carry free rotations (Blockbench allows
// any angle; a block model may only carry 0/±22.5/±45 and refuses to load otherwise). So the rod is
// filtered out by element name and only the pod itself is installed — the rod on a real pod is drawn
// live by RodPodRenderer anyway, from whatever is actually docked.
//
// Texture slot VALUES are rewritten to the installed texture; slot KEYS stay as authored, and
// texture_size is taken from the PNG rather than from the file, which is how a 64px sheet ends up
// sampled as 32 and every face lands on the wrong quarter of it.
const fs = require('fs'), path = require('path');

const SRC = '3D/rod_pod/rod_pod.json';
const PNG = '3D/rod_pod/texture.png';
const A = 'common/src/main/resources/assets/riverfishing';
const MODEL_OUT = path.join(A, 'models/block/rod_pod_iron.json');   // the buzz-bar pod (tier 3)
const TEX_OUT = path.join(A, 'textures/block/rod_pod.png');
const TEX_REF = 'riverfishing:block/rod_pod';

// Element names that belong to the ROD in the scene, not to the pod.
const ROD_PART = /^(butt_cap|rear_grip|reel_seat|fore_grip|blank_|quivertip|guide_)/;
const LEGAL = [0, 22.5, -22.5, 45, -45];

const model = JSON.parse(fs.readFileSync(SRC, 'utf8'));
const pod = model.elements.filter(e => !ROD_PART.test(e.name || ''));
if (!pod.length) throw new Error('no pod elements left after filtering the rod out');

for (const e of pod) {
  if (e.rotation && !LEGAL.includes(e.rotation.angle)) {
    throw new Error(`${e.name}: rotation ${e.rotation.angle} — Minecraft loads only 0/±22.5/±45`);
  }
  for (const v of [...e.from, ...e.to]) {
    if (v < -16 || v > 32) throw new Error(`${e.name}: coord ${v} outside -16..32`);
  }
}

const png = fs.readFileSync(PNG);
if (png.slice(0, 8).toString('hex') !== '89504e470d0a1a0a') throw new Error(`${PNG} is not a PNG`);
const texW = png.readUInt32BE(16), texH = png.readUInt32BE(20);
fs.copyFileSync(PNG, TEX_OUT);
if (!fs.readFileSync(TEX_OUT).equals(png)) throw new Error('texture copy mismatch');

const slots = Object.keys(model.textures);
const out = {
  texture_size: [texW, texH],
  textures: Object.fromEntries(slots.map(k => [k, TEX_REF])),
  elements: pod,
};
const compact = o => JSON.stringify(o, null, '\t')
  .replace(/\[\n\t+(-?[\d.]+,\n\t+)*-?[\d.]+\n\t+\]/g, m => m.replace(/\s+/g, ' ').replace(/\[ /, '[').replace(/ \]/, ']'));
fs.writeFileSync(MODEL_OUT, compact(out) + '\n');

// read back: a truncated write is invisible until the block renders wrong in game
const back = JSON.parse(fs.readFileSync(MODEL_OUT, 'utf8'));
if (back.elements.length !== pod.length) throw new Error('write truncated');
const declared = new Set(Object.keys(back.textures).map(k => '#' + k));
for (const e of back.elements) {
  for (const [f, v] of Object.entries(e.faces)) {
    if (!declared.has(v.texture)) throw new Error(`${e.name}.${f} -> ${v.texture}, declared ${[...declared].join(' ')}`);
  }
}

let minX = 99, maxX = -99, minY = 99, maxY = -99, minZ = 99, maxZ = -99;
for (const e of back.elements) {
  minX = Math.min(minX, e.from[0]); maxX = Math.max(maxX, e.to[0]);
  minY = Math.min(minY, e.from[1]); maxY = Math.max(maxY, e.to[1]);
  minZ = Math.min(minZ, e.from[2]); maxZ = Math.max(maxZ, e.to[2]);
}
const r = n => Math.round(n * 100) / 100;
console.log(`rod_pod_iron  ${back.elements.length} el (${model.elements.length - pod.length} rod parts skipped), ` +
            `slots ${slots.join('/')} -> ${TEX_REF}, texture ${texW}x${texH}`);
console.log(`bounds x ${r(minX)}..${r(maxX)}  y ${r(minY)}..${r(maxY)}  z ${r(minZ)}..${r(maxZ)}` +
            `   <- RodPodBlock.SHAPE should cover this`);
