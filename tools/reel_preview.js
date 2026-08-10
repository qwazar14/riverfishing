// Position-check rig for a part that is authored in another model's coordinate space (§rod-3d).
// The reel is drawn in the blank's frame, so "is it in the right place" can only be answered next to
// the blank — but Blockbench's Import Model MERGES, and saving that would bury the rod inside the
// reel file. So: build a throwaway preview, nudge the part there, extract it back by name.
//
//   node tools/reel_preview.js build  <base.json> <part.json> <preview.json>
//   node tools/reel_preview.js extract <base.json> <preview.json> <part.json>
//
// Extraction keys off the BASE model's element names, read live from the file — so adding parts to
// the preview is safe (anything the base does not claim is treated as part geometry) and renaming
// things in the base cannot silently strand an element.
const fs = require('fs');

const [mode, baseFile, b, c] = process.argv.slice(2);
const read = f => JSON.parse(fs.readFileSync(f, 'utf8'));
const compact = o => JSON.stringify(o, null, '\t')
  .replace(/\[\n\t+(-?[\d.]+,\n\t+)*-?[\d.]+\n\t+\]/g, x => x.replace(/\s+/g, ' ').replace(/\[ /, '[').replace(/ \]/, ']'));
const names = m => new Set(m.elements.map(e => e.name));

const retex = (el, slot) => ({
  ...el,
  faces: Object.fromEntries(Object.entries(el.faces).map(([f, v]) => [f, { ...v, texture: slot }])),
});

if (mode === 'build') {
  const [partFile, outFile] = [b, c];
  const base = read(baseFile), part = read(partFile);
  if (String(base.texture_size) !== String(part.texture_size)) {
    throw new Error(`texture_size differs: base ${base.texture_size} vs part ${part.texture_size} — ` +
      `UVs would be read at different densities in the same file`);
  }
  const clash = [...names(base)].filter(n => names(part).has(n));
  if (clash.length) throw new Error(`both models use these element names: ${clash.join(', ')} — extraction would be ambiguous`);

  fs.writeFileSync(outFile, compact({
    format_version: '1.9.0',
    credit: 'PREVIEW ONLY — edit the part, then run: node tools/reel_preview.js extract',
    texture_size: base.texture_size,
    // both slots point at the base's sheet: this rig answers "where does it sit", not "how does it look"
    textures: { 0: 'texture', 1: 'texture', particle: 'texture' },
    elements: [...base.elements.map(e => retex(e, '#0')), ...part.elements.map(e => retex(e, '#1'))],
  }) + '\n');
  console.log(`built ${outFile}`);
  console.log(`  ${base.elements.length} base elements (#0) + ${part.elements.length} part elements (#1)`);
  console.log(`  part: ${[...names(part)].join(', ')}`);
  console.log(`  move ONLY those, then extract — everything else is scenery`);

} else if (mode === 'extract') {
  const [previewFile, outFile] = [b, c];
  const base = read(baseFile), preview = read(previewFile), old = read(outFile);
  const baseNames = names(base);
  const kept = preview.elements.filter(e => !baseNames.has(e.name));
  if (!kept.length) throw new Error('preview holds nothing that is not part of the base model');

  const before = old.elements.length;
  const moved = kept.filter(e => {
    const was = old.elements.find(o => o.name === e.name);
    return was && String([was.from, was.to]) !== String([e.from, e.to]);
  });
  fs.writeFileSync(outFile, compact({ ...old, elements: kept.map(e => retex(e, '#0')) }) + '\n');
  console.log(`extracted ${kept.length} elements -> ${outFile} (was ${before})`);
  for (const e of moved) {
    const was = old.elements.find(o => o.name === e.name);
    console.log(`  ${e.name.padEnd(12)} ${JSON.stringify(was.from)} -> ${JSON.stringify(e.from)}`);
  }
  if (!moved.length) console.log('  no element moved');

} else {
  console.error('usage: reel_preview.js build|extract <base> <part|preview> <out>');
  process.exit(2);
}
