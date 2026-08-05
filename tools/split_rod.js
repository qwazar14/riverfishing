// Splits a one-piece 3D rod model into rigid segments for the renderer's bone chain (§rod-bend-3d).
// Vanilla item models have no bone hierarchy and only allow rotation angles 0/±22.5/±45 baked at
// load, so the chain has to live in RodItemRenderer — this just cuts the geometry along the joints.
// Each guide rides in the segment whose section it sits on, so it follows that bone automatically.
const fs = require('fs'), path = require('path');
const [src, outDir, prefix, texRef] = process.argv.slice(2);
const model = JSON.parse(fs.readFileSync(src, 'utf8'));

// Butt -> tip. Sections are matched by name; joints are the +X end of each bending section, which is
// exactly where the author already put each element's rotation origin.
const SEGMENTS = [
  { name: 's0', sections: ['butt_cap', 'rear_grip', 'reel_seat', 'fore_grip'], joint: null },
  { name: 's1', sections: ['blank_butt'], joint: 14 },
  { name: 's2', sections: ['blank_mid'], joint: 3 },
  { name: 's3', sections: ['blank_tip'], joint: -5 },
  { name: 's4', sections: ['quivertip'], joint: -10 },
];

const byName = Object.fromEntries(model.elements.map(e => [e.name, e]));
const guides = model.elements.filter(e => /^guide_/.test(e.name));
const sectionOf = g => model.elements.find(e =>
  !/^guide_/.test(e.name) && g.from[0] >= e.from[0] && g.from[0] <= e.to[0]);

const assigned = new Set();
const out = SEGMENTS.map(seg => {
  const own = seg.sections.map(n => {
    if (!byName[n]) throw new Error(`section "${n}" not in ${path.basename(src)}`);
    return byName[n];
  });
  const riders = guides.filter(g => {
    const host = sectionOf(g);
    if (!host) throw new Error(`${g.name} at x=${g.from[0]} sits on no section`);
    return seg.sections.includes(host.name);
  });
  [...own, ...riders].forEach(e => assigned.add(e.name));
  return { seg, elements: [...own, ...riders] };
});

// nothing may be silently dropped on the floor
const missed = model.elements.filter(e => !assigned.has(e.name)).map(e => e.name);
if (missed.length) throw new Error(`unassigned elements: ${missed.join(', ')}`);

// keep numeric arrays on one line, like every other model in assets/
const compact = o => JSON.stringify(o, null, '\t')
  .replace(/\[\n\t+(-?[\d.]+,\n\t+)*-?[\d.]+\n\t+\]/g, m => m.replace(/\s+/g, ' ').replace(/\[ /, '[').replace(/ \]/, ']'));

fs.mkdirSync(outDir, { recursive: true });
for (const { seg, elements } of out) {
  const file = path.join(outDir, `${prefix}_${seg.name}.json`);
  fs.writeFileSync(file, compact({
    // No display block on purpose: RodItemRenderer draws every segment with ItemDisplayContext.NONE
    // in one shared frame, so a per-segment transform would break the chain's pivot maths.
    texture_size: model.texture_size,
    textures: { 0: texRef, particle: texRef },
    elements: elements.map(e => ({ ...e, faces: e.faces })),
  }) + '\n');
  console.log(`${path.basename(file)}  joint x=${seg.joint ?? '-'}  ${elements.map(e => e.name).join(', ')}`);
}

// the joint table the renderer needs, so the Java constant is derived, not retyped by hand
console.log('\njoints (butt -> tip), model units:',
  JSON.stringify(SEGMENTS.filter(s => s.joint !== null).map(s => s.joint)));
