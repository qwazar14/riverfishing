// Splits a one-piece 3D rod model into rigid segments for the renderer's bone chain (§rod-bend-3d).
// Vanilla item models have no bone hierarchy and only allow rotation angles 0/±22.5/±45 baked at
// load, so the chain has to live in RodItemRenderer — this just cuts the geometry along the joints.
//
//   node tools/split_rod.js <feeder|pole>
//
// Per-rod config below: which elements form each rigid piece and where the hinges sit. Joints can be
// explicit x values or derived from a named element's centre (a pole hinges exactly at each ferrule —
// and the ferrule rides the TIP-side segment, so it sleeves the joint like a real telescopic collar
// instead of leaving a crack when the chain bends).
//
// Elements are copied untouched and texture slot KEYS are preserved (the stick taught us: authors
// pick their own slot numbers, and rewriting them to "0" points every face at a slot that is gone).
// Only slot VALUES are rewritten, to the installed texture. Guides (guide_*) auto-ride whichever
// segment owns the section under them.
const fs = require('fs'), path = require('path');

const OUT_DIR = 'common/src/main/resources/assets/riverfishing/models/item/rod';

const RODS = {
  feeder: {
    src: '3D/rods/feeder/blank_feeder.json',
    texRef: 'riverfishing:item/rod/blank_feeder_3d',
    segments: [
      { name: 's0', members: ['butt_cap', 'rear_grip', 'reel_seat', 'fore_grip'] },
      { name: 's1', members: ['blank_butt'], joint: 14 },
      { name: 's2', members: ['blank_mid'], joint: 3 },
      { name: 's3', members: ['blank_tip'], joint: -5 },
      { name: 's4', members: ['quivertip'], joint: -10 },
    ],
  },
  pole: {
    src: '3D/rods/pole/blank_pole.json',
    texRef: 'riverfishing:item/rod/blank_pole_3d',
    segments: [
      { name: 's0', members: ['grip', 'section_1'] },
      { name: 's1', members: ['ferrule_1', 'section_2'], jointAt: 'ferrule_1' },
      { name: 's2', members: ['ferrule_2', 'section_3'], jointAt: 'ferrule_2' },
      { name: 's3', members: ['ferrule_3', 'section_4'], jointAt: 'ferrule_3' },
      { name: 's4', members: ['ferrule_4', 'section_5'], jointAt: 'ferrule_4' },
    ],
  },
  // §spinning-3d: the artist rebuilt spinning ON the feeder's skeleton — same section names, same
  // joints — so it splits with the feeder's own config and bends identically.
  spinning: {
    src: '3D/rods/spinning/blank_spinning.json',
    texRef: 'riverfishing:item/rod/blank_spinning_3d',
    segments: [
      { name: 's0', members: ['butt_cap', 'rear_grip', 'reel_seat', 'fore_grip'] },
      { name: 's1', members: ['blank_butt'], joint: 14 },
      { name: 's2', members: ['blank_mid'], joint: 3 },
      { name: 's3', members: ['blank_tip'], joint: -5 },
      { name: 's4', members: ['quivertip'], joint: -10 },
    ],
  },
  // §ultralight-3d: feeder skeleton again, but with its own joints and 0.1u section overlaps that
  // sleeve the hinges without collars.
  ultralight: {
    src: '3D/rods/ultralight/blank_ultralight.json',
    texRef: 'riverfishing:item/rod/blank_ultralight_3d',
    segments: [
      { name: 's0', members: ['butt_cap', 'rear_grip', 'reel_seat', 'fore_grip'] },
      { name: 's1', members: ['blank_butt'], joint: 14.8 },
      { name: 's2', members: ['blank_mid'], joint: 5.5 },
      { name: 's3', members: ['blank_tip'], joint: -0.8 },
      { name: 's4', members: ['quivertip'], joint: -4.1 },
    ],
  },
  // §surf-3d: five sections, blank_1 rides the handle piece; joints at the section boundaries,
  // 0.025u overlaps sleeving them.
  surf: {
    src: '3D/rods/surf/blank_surf.json',
    texRef: 'riverfishing:item/rod/blank_surf_3d',
    segments: [
      { name: 's0', members: ['butt_cap', 'rear_grip', 'reel_seat', 'fore_grip', 'blank_1'] },
      { name: 's1', members: ['blank_2'], joint: 12.9 },
      { name: 's2', members: ['blank_3'], joint: 5.3 },
      { name: 's3', members: ['blank_4'], joint: -2.3 },
      { name: 's4', members: ['blank_5', 'tip_top'], joint: -9.9 },
    ],
  },
  // §carp-3d: five sections; the authored pivots drift off the boundaries, so the joints sit at
  // the section-overlap centres instead.
  carp: {
    src: '3D/rods/carp/blank_carp.json',
    texRef: 'riverfishing:item/rod/blank_carp_3d',
    segments: [
      { name: 's0', members: ['butt_cap', 'rear_grip', 'reel_seat', 'fore_grip', 'blank_1'] },
      { name: 's1', members: ['blank_2'], joint: 13.8 },
      { name: 's2', members: ['blank_3'], joint: 6.46 },
      { name: 's3', members: ['blank_4'], joint: -0.88 },
      { name: 's4', members: ['blank_5', 'tip_top'], joint: -8.21 },
    ],
  },
  // §boat-3d: six sections, joints at the overlap centres.
  boat: {
    src: '3D/rods/boat/blank_boat.json',
    texRef: 'riverfishing:item/rod/blank_boat_3d',
    segments: [
      { name: 's0', members: ['butt_cap', 'rear_grip', 'reel_seat', 'fore_grip', 'blank_1'] },
      { name: 's1', members: ['blank_2'], joint: 10.94 },
      { name: 's2', members: ['blank_3'], joint: 5.39 },
      { name: 's3', members: ['blank_4'], joint: -0.16 },
      { name: 's4', members: ['blank_5'], joint: -5.66 },
      { name: 's5', members: ['blank_6', 'tip_top'], joint: -11.16 },
    ],
  },
  // §bottom-3d: reworked from rigid to five sections, same school as surf/carp.
  bottom: {
    src: '3D/rods/bottom/blank_bottom.json',
    texRef: 'riverfishing:item/rod/blank_bottom_3d',
    segments: [
      { name: 's0', members: ['butt_cap', 'rear_grip', 'reel_seat', 'fore_grip', 'blank_1'] },
      { name: 's1', members: ['blank_2'], joint: 12.99 },
      { name: 's2', members: ['blank_3'], joint: 5.59 },
      { name: 's3', members: ['blank_4'], joint: -1.81 },
      { name: 's4', members: ['blank_5', 'tip_top'], joint: -9.21 },
    ],
  },
  // §trolling-3d: short and stout — four sections, the shallowest chain of the takes-reel rods.
  trolling: {
    src: '3D/rods/trolling/blank_trolling.json',
    texRef: 'riverfishing:item/rod/blank_trolling_3d',
    segments: [
      { name: 's0', members: ['butt_cap', 'rear_grip', 'reel_seat', 'fore_grip', 'blank_1'] },
      { name: 's1', members: ['blank_2'], joint: 10.31 },
      { name: 's2', members: ['blank_3'], joint: 2.21 },
      { name: 's3', members: ['blank_4', 'tip_top'], joint: -2.54 },
    ],
  },
  // §sea-spin-3d: EIGHT sections — the deepest chain in the fleet (BLANK_SEGMENTS was raised to 8
  // for it); joints at the overlap centres.
  sea_spin: {
    src: '3D/rods/sea_spin/blank_sea_spin.json',
    texRef: 'riverfishing:item/rod/blank_sea_spin_3d',
    segments: [
      { name: 's0', members: ['butt_cap', 'rear_grip', 'reel_seat', 'fore_grip', 'blank_1'] },
      { name: 's1', members: ['blank_2'], joint: 9.15 },
      { name: 's2', members: ['blank_3'], joint: 3.15 },
      { name: 's3', members: ['blank_4'], joint: -3.15 },
      { name: 's4', members: ['blank_5'], joint: -7.95 },
      { name: 's5', members: ['blank_6'], joint: -10.95 },
      { name: 's6', members: ['blank_7'], joint: -12.95 },
      { name: 's7', members: ['blank_8', 'tip_top'], joint: -14.58 },
    ],
  },
  bamboo: {
    src: '3D/rods/bamboo/blank_bamboo.json',
    texRef: 'riverfishing:item/rod/blank_bamboo_3d',
    // hinged at the culm nodes — bamboo genuinely flexes least at the nodes and most between them,
    // but at this scale hinging AT each node keeps the ring sleeving the joint, same as pole ferrules
    segments: [
      { name: 's0', members: ['grip', 'section_1'] },
      { name: 's1', members: ['node_1', 'section_2'], jointAt: 'node_1' },
      { name: 's2', members: ['node_2', 'section_3'], jointAt: 'node_2' },
      { name: 's3', members: ['node_3', 'section_4'], jointAt: 'node_3' },
      { name: 's4', members: ['node_4', 'section_5'], jointAt: 'node_4' },
      { name: 's5', members: ['node_5', 'section_6', 'tip_whipping'], jointAt: 'node_5' },
    ],
  },
};

const kind = process.argv[2];
const cfg = RODS[kind];
if (!cfg) {
  console.error(`usage: split_rod.js <${Object.keys(RODS).join('|')}>`);
  process.exit(2);
}

const model = JSON.parse(fs.readFileSync(cfg.src, 'utf8'));
const byName = Object.fromEntries(model.elements.map(e => [e.name, e]));
const r = n => Math.round(n * 1000) / 1000;

// resolve each segment's joint: explicit x, or the x-centre of the named element
const joints = cfg.segments.filter(s => s.joint !== undefined || s.jointAt).map(s => {
  if (s.joint !== undefined) return s.joint;
  const e = byName[s.jointAt];
  if (!e) throw new Error(`jointAt "${s.jointAt}" not in ${path.basename(cfg.src)}`);
  return r((e.from[0] + e.to[0]) / 2);
});

// guides ride the segment whose named section sits under them
const guides = model.elements.filter(e => /^guide_/.test(e.name));
const sectionOf = g => model.elements.find(e =>
  !/^guide_/.test(e.name) && g.from[0] >= e.from[0] && g.from[0] <= e.to[0]);

const assigned = new Set();
const out = cfg.segments.map(seg => {
  const own = seg.members.map(n => {
    if (!byName[n]) throw new Error(`section "${n}" not in ${path.basename(cfg.src)}`);
    return byName[n];
  });
  const riders = guides.filter(g => {
    const host = sectionOf(g);
    if (!host) throw new Error(`${g.name} at x=${g.from[0]} sits on no section`);
    return seg.members.includes(host.name);
  });
  [...own, ...riders].forEach(e => assigned.add(e.name));
  return { seg, elements: [...own, ...riders] };
});

// nothing may be silently dropped on the floor
const missed = model.elements.filter(e => !assigned.has(e.name)).map(e => e.name);
if (missed.length) throw new Error(`unassigned elements: ${missed.join(', ')}`);
// hinges must march butt -> tip, or JOINT_SHARE weights land on the wrong sections
for (let i = 1; i < joints.length; i++) {
  if (joints[i] >= joints[i - 1]) throw new Error(`joints not descending: ${joints.join(', ')}`);
}

// keep numeric arrays on one line, like every other model in assets/
const compact = o => JSON.stringify(o, null, '\t')
  .replace(/\[\n\t+(-?[\d.]+,\n\t+)*-?[\d.]+\n\t+\]/g, m => m.replace(/\s+/g, ' ').replace(/\[ /, '[').replace(/ \]/, ']'));

for (const { seg, elements } of out) {
  const file = path.join(OUT_DIR, `blank_${kind}_${seg.name}.json`);
  // No display block on purpose: RodItemRenderer draws every segment with ItemDisplayContext.NONE
  // in one shared frame, so a per-segment transform would break the chain's pivot maths.
  fs.writeFileSync(file, compact({
    texture_size: model.texture_size,
    textures: Object.fromEntries([...Object.keys(model.textures), 'particle'].map(k => [k, cfg.texRef])),
    elements,
  }) + '\n');
  // read back: every face must reference a declared slot, and nothing may be truncated
  const back = JSON.parse(fs.readFileSync(file, 'utf8'));
  if (back.elements.length !== elements.length) throw new Error(`${file}: write truncated`);
  const declared = new Set(Object.keys(back.textures).map(k => '#' + k));
  for (const e of back.elements) {
    for (const [f, v] of Object.entries(e.faces)) {
      if (!declared.has(v.texture)) throw new Error(`${e.name}.${f} references ${v.texture}, model declares ${[...declared].join(' ')}`);
    }
  }
  console.log(`${path.basename(file)}  ${elements.map(e => e.name).join(', ')}`);
}

// The texture ships with the split, same as install_rod_model.js does for rigid rods — a re-split
// after the artist repainted must carry the new sheet, or the game keeps rendering last week's.
const texSrc = path.join(path.dirname(cfg.src), 'texture.png');
if (fs.existsSync(texSrc)) {
  const texOut = `common/src/main/resources/assets/riverfishing/textures/item/rod/blank_${kind}_3d.png`;
  fs.copyFileSync(texSrc, texOut);
  if (!fs.readFileSync(texOut).equals(fs.readFileSync(texSrc))) throw new Error('texture copy mismatch');
  console.log(`\ntexture -> ${texOut}`);
} else {
  console.log(`\nno ${texSrc} — texture left as installed`);
}

console.log(`joints (butt -> tip), model units: [${joints.join(', ')}]`);
console.log(`paste into RodItemRenderer.BLANK_JOINTS_X as "${kind}"`);
