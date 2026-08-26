// Extracts each rod's line path (§line-thru-guides) from the 3D sources: the ring centre of every
// guide, butt to tip, plus the tip exit — the points the rendered line threads through on its way
// from the reel. Written as a data asset the client parses, so the renderer never restates model
// geometry by hand and tools/check_rod_assets.js can hold the two in sync.
//
//   node tools/gen_line_paths.js
const fs = require('fs');

const RODS = ['stick', 'bamboo', 'pole', 'winter', 'ultralight', 'spinning', 'feeder',
              'bottom', 'carp', 'surf', 'sea_spin', 'boat', 'trolling'];
const OUT = 'common/src/main/resources/assets/riverfishing/rod_line_paths.json';
const r = n => Math.round(n * 1000) / 1000;

const out = {};
for (const kind of RODS) {
  const m = JSON.parse(fs.readFileSync(`3D/rods/${kind}/blank_${kind}.json`, 'utf8'));
  const guides = m.elements.filter(e => /^guide_/.test(e.name))
    .map(e => [r(e.from[0]), r((e.from[1] + e.to[1]) / 2)])
    .sort((a, b) => b[0] - a[0]);                         // butt -> tip, x descending
  if (!guides.length) continue;                           // reel-less rods: the line ties at the tip
  for (let i = 1; i < guides.length; i++) {
    if (guides[i][0] >= guides[i - 1][0]) throw new Error(`${kind}: guides not strictly descending in x`);
  }
  const tipEl = m.elements.reduce((a, e) => e.from[0] < a.from[0] ? e : a);
  out[kind] = { guides, tip: [r(tipEl.from[0]), r((tipEl.from[1] + tipEl.to[1]) / 2)] };
  console.log(kind.padEnd(11) + `${guides.length} guides, tip at ${out[kind].tip.join('/')}`);
}

fs.writeFileSync(OUT, JSON.stringify(out, null, '\t') + '\n');
const back = JSON.parse(fs.readFileSync(OUT, 'utf8'));
if (Object.keys(back).length !== Object.keys(out).length) throw new Error('write truncated');
console.log(`-> ${OUT} (${Object.keys(out).length} rods)`);
