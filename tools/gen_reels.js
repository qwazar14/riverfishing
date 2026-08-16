// Derives the whole reel catalogue from ONE hand-editable master (§reel-3d): 3D/reels/reel_4000.json.
// Spool size grows as the cube root of the nominal size, so 1000..14000 spans just 0.63x..1.52x; the
// FOOT never scales, because a real reel foot fits a standard seat whatever the size — and every size
// keeps the master's UVs verbatim, so all eleven share one painted sheet, texels merely stretched.
//
//   node tools/gen_reels.js       writes assets models reel_<size>_3d.json + installs the texture
//
// Sized models are pure derivations, so they go straight into assets — 3D/reels/ holds only the
// master (edit it, re-run this). The master is authored IN PLACE on the feeder blank: foot docked
// into the seat at x 16..23, underside y 9.45. RodItemRenderer shifts it per rod's own seat.
const fs = require('fs'), path = require('path');

const SIZES = [1000, 2000, 3000, 4000, 5000, 6000, 7000, 8000, 10000, 12000, 14000];
const MASTER = '3D/reels/reel_4000.json';
const TEX_SRC = '3D/reels/texture.png';
const OUT_DIR = 'common/src/main/resources/assets/riverfishing/models/item/rod';
const TEX_OUT = 'common/src/main/resources/assets/riverfishing/textures/item/rod/reel_3d.png';
const TEX_REF = 'riverfishing:item/rod/reel_3d';
const r = n => Math.round(n * 1000) / 1000;

const master = JSON.parse(fs.readFileSync(MASTER, 'utf8'));
const foot = master.elements.find(e => e.name === 'foot');
if (!foot) throw new Error('master has no "foot" element — the scale anchor');
const ANCHOR = [(foot.from[0] + foot.to[0]) / 2, foot.to[1], (foot.from[2] + foot.to[2]) / 2];

if (!fs.existsSync(TEX_SRC)) throw new Error(`${TEX_SRC} missing — run tools/gen_reel_texture.js first`);
fs.copyFileSync(TEX_SRC, TEX_OUT);

const compact = o => JSON.stringify(o, null, '\t')
  .replace(/\[\n\t+(-?[\d.]+,\n\t+)*-?[\d.]+\n\t+\]/g, m => m.replace(/\s+/g, ' ').replace(/\[ /, '[').replace(/ \]/, ']'));

// §reel-crank: the lever and the knob each ship as their OWN model — the vanilla format cannot
// animate, so every split is an animation seam, same as the blank's bone chain. The lever sweeps
// about the gear boss; the knob rides the lever's end but counter-rotates about its own centre, so
// it orbits while staying level, the way a free-spinning knob really behaves.
const ARM = new Set(['handle_arm']), KNOB = new Set(['handle_knob']);
const arm = master.elements.find(e => e.name === 'handle_arm');
const knob = master.elements.find(e => e.name === 'handle_knob');
if (!arm || !knob) throw new Error('master needs "handle_arm" and "handle_knob"');
// the crank axis is the gear boss the lever hangs from: the lever's x centre, at its top end
const CRANK = [(arm.from[0] + arm.to[0]) / 2, Math.max(arm.from[1], arm.to[1]) - 0.35];
const KNOB_C = [(knob.from[0] + knob.to[0]) / 2, (knob.from[1] + knob.to[1]) / 2];
if (Math.abs(CRANK[1] - KNOB_C[1]) < 0.5) {
  throw new Error(`knob centre y ${KNOB_C[1]} sits on the crank axis y ${CRANK[1]} — no orbit radius, the crank cannot read`);
}

for (const size of SIZES) {
  const s = Math.cbrt(size / 4000);
  const scaled = master.elements.map(e => ({
    ...e,
    from: e.name === 'foot' ? e.from : e.from.map((v, i) => r(ANCHOR[i] + (v - ANCHOR[i]) * s)),
    to:   e.name === 'foot' ? e.to   : e.to.map((v, i) => r(ANCHOR[i] + (v - ANCHOR[i]) * s)),
  }));
  // the reel must stay under the rod and its foot must still reach the seat, at every size
  for (const e of scaled) {
    for (const v of [...e.from, ...e.to]) {
      if (v < -16 || v > 32) throw new Error(`reel_${size}/${e.name}: coord ${r(v)} outside -16..32`);
    }
    if (e.name !== 'foot' && e.to[1] > 10.5 + 0.01) {
      throw new Error(`reel_${size}/${e.name} rises above the rod axis`);
    }
  }
  const write = (suffix, elements) => {
    const file = path.join(OUT_DIR, `reel_${size}${suffix}.json`);
    fs.writeFileSync(file, compact({
      texture_size: master.texture_size,
      textures: Object.fromEntries([...Object.keys(master.textures), 'particle'].map(k => [k, TEX_REF])),
      elements,
    }) + '\n');
    if (JSON.parse(fs.readFileSync(file, 'utf8')).elements.length !== elements.length) {
      throw new Error(`${file}: write truncated`);
    }
    return elements.length;
  };
  const nb = write('_3d', scaled.filter(e => !ARM.has(e.name) && !KNOB.has(e.name)));
  const nh = write('_handle_3d', scaled.filter(e => ARM.has(e.name)));
  const nk = write('_knob_3d', scaled.filter(e => KNOB.has(e.name)));
  console.log(`reel_${size}_3d`.padEnd(14) + ` scale ${r(s)}x — body ${nb} el, lever ${nh}, knob ${nk}`);
}
console.log(`texture -> ${TEX_OUT}`);
console.log(`crank axis (master): ${CRANK.map(r).join(', ')}   knob centre: ${KNOB_C.map(r).join(', ')}`
  + `   orbit radius ${r(CRANK[1] - KNOB_C[1])}u   anchor ${ANCHOR.map(r).join('/')}`);
console.log('RodItemRenderer scales both pivots per size with the same cbrt');
