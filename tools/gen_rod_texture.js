// Paints a 3D rod blank's texture.png in the FEEDER's visual language (§rod-3d): every material —
// grip, reel seat, butt cap, tip paint, guide-ring metal — is SAMPLED from the hand-painted feeder
// sheet, and only the blank tone comes from the target rod's own 2D sprite, so each rod keeps the
// colour identity its sprite already established while the whole family reads as one set.
//
//   node tools/gen_rod_texture.js <kind> [...more kinds]
//
// Painting is per face rect straight from the model's UVs, so it survives whatever the UV packer did
// (length squeeze, shared collar squares). Guides get a frame with a TRANSPARENT centre — the hole in
// the ring is cut by alpha, exactly the trick the flat-quad guides were designed around.
const fs = require('fs');
const { readPNG, writePNG } = require('./lib/png');

const FEEDER_MODEL = '3D/rods/feeder/blank_feeder.json';
const FEEDER_TEX = '3D/rods/feeder/texture.png';
const SPRITES = 'common/src/main/resources/assets/riverfishing/textures/item/rod';

const kinds = process.argv.slice(2);
if (!kinds.length) { console.error('usage: gen_rod_texture.js <kind> [...]'); process.exit(2); }

// ---- sample the feeder's materials ----
const feeder = JSON.parse(fs.readFileSync(FEEDER_MODEL, 'utf8'));
const ftex = readPNG(FEEDER_TEX);
const fscale = ftex.W / 16;

function avgRect(img, scale, uv) {
  const x0 = Math.round(Math.min(uv[0], uv[2]) * scale), x1 = Math.round(Math.max(uv[0], uv[2]) * scale);
  const y0 = Math.round(Math.min(uv[1], uv[3]) * scale), y1 = Math.round(Math.max(uv[1], uv[3]) * scale);
  let r = 0, g = 0, b = 0, n = 0;
  for (let y = y0; y < y1 && y < img.H; y++) for (let x = x0; x < x1 && x < img.W; x++) {
    const o = y * img.stride + x * 4;
    if (img.px[o + 3] > 16) { r += img.px[o]; g += img.px[o + 1]; b += img.px[o + 2]; n++; }
  }
  if (!n) throw new Error('sampled rect is fully transparent');
  return [r / n, g / n, b / n];
}
const face = (name, f = 'north') => {
  const e = feeder.elements.find(e => e.name === name);
  if (!e) throw new Error(`feeder model has no element "${name}"`);
  return e.faces[f].uv;
};
const MAT = {
  cap:   avgRect(ftex, fscale, face('butt_cap')),
  grip:  avgRect(ftex, fscale, face('rear_grip')),
  seat:  avgRect(ftex, fscale, face('reel_seat')),
  blank: avgRect(ftex, fscale, face('blank_butt')),
  tip:   avgRect(ftex, fscale, face('quivertip')),
  ring:  avgRect(ftex, fscale, face('guide_1', 'east')),
};

const shade = (c, k) => c.map(v => Math.max(0, Math.min(255, Math.round(v * k))));
const materialOf = name => {
  if (/^butt_cap/.test(name)) return { c: MAT.cap };
  if (/grip$|^grip/.test(name)) return { c: MAT.grip };
  if (/^reel_seat/.test(name)) return { c: MAT.seat };
  if (/^(ferrule|node)_/.test(name)) return { c: MAT.seat };            // collars read as fittings
  if (/^(tip_top|tip_whipping)/.test(name)) return { c: MAT.tip };      // painted tip / winter nod
  if (/^(blank|section)_/.test(name)) return { c: 'TINT', wraps: true };
  if (/^guide_/.test(name)) return { ring: true };
  throw new Error(`no material rule for element "${name}"`);
};

for (const kind of kinds) {
  const modelPath = `3D/rods/${kind}/blank_${kind}.json`;
  const model = JSON.parse(fs.readFileSync(modelPath, 'utf8'));
  const W = model.texture_size[0], H = model.texture_size[1], scale = W / 16;
  const canvas = Buffer.alloc(W * H * 4);           // starts fully transparent

  // the rod's own colour identity, from its existing 2D sprite
  const spritePath = `${SPRITES}/blank_${kind}.png`;
  const tint = fs.existsSync(spritePath) ? avgRect(readPNG(spritePath), readPNG(spritePath).W / 16,
      [0, 0, 16, 16]) : MAT.blank;

  const put = (x, y, c, a = 255) => {
    if (x < 0 || y < 0 || x >= W || y >= H) return;
    const o = (y * W + x) * 4;
    canvas[o] = c[0]; canvas[o + 1] = c[1]; canvas[o + 2] = c[2]; canvas[o + 3] = a;
  };
  // sub-texel faces (a 0.2u tip end) round to zero area, but MC still samples that texel — force
  // every face to own at least one pixel so nothing renders transparent
  const rect = uv => {
    let x0 = Math.round(Math.min(uv[0], uv[2]) * scale), x1 = Math.round(Math.max(uv[0], uv[2]) * scale);
    let y0 = Math.round(Math.min(uv[1], uv[3]) * scale), y1 = Math.round(Math.max(uv[1], uv[3]) * scale);
    if (x1 === x0) x1 = x0 + 1;
    if (y1 === y0) y1 = y0 + 1;
    return [x0, y0, x1, y1];
  };

  for (const e of model.elements) {
    const mat = materialOf(e.name);
    if (mat.ring) {
      // dark frame, transparent middle — the hole in the ring is cut by alpha. East and west may
      // share one square (the feeder's convention) or own two (the generator's box unwrap): paint
      // whatever rects the model actually references.
      for (const fn of ['east', 'west']) {
        const uv = e.faces[fn] && e.faces[fn].uv;
        if (!uv || uv[0] === uv[2] || uv[1] === uv[3]) continue;
        const [x0, y0, x1, y1] = rect(uv);
        for (let y = y0; y < y1; y++) for (let x = x0; x < x1; x++) {
          if (x === x0 || x === x1 - 1 || y === y0 || y === y1 - 1) put(x, y, MAT.ring);
        }
      }
      continue;
    }
    const base = mat.c === 'TINT' ? tint : mat.c;
    for (const [fname, f] of Object.entries(e.faces)) {
      const [a, b, c, d] = f.uv;
      if (a === c || b === d) continue;                        // blanked face
      const [x0, y0, x1, y1] = rect(f.uv);
      const h = y1 - y0;
      for (let y = y0; y < y1; y++) {
        // cylinder banding, feeder-style: light crown, dark underside — only when there is room
        const k = h < 3 ? 1 : y === y0 ? 1.18 : y === y1 - 1 ? 0.72 : 1;
        for (let x = x0; x < x1; x++) put(x, y, shade(base, k));
      }
      // whipping wraps where blank sections meet, on the along-the-rod faces
      if (mat.wraps && (fname === 'north' || fname === 'south' || fname === 'up' || fname === 'down')
          && x1 - x0 >= 4) {
        for (let y = y0; y < y1; y++) { put(x0, y, shade(base, 0.5)); put(x1 - 1, y, shade(base, 0.5)); }
      }
    }
  }

  const out = `3D/rods/${kind}/texture.png`;
  writePNG(out, canvas, W, H);
  // read back and confirm every referenced face rect now carries paint
  const back = readPNG(out);
  let unpainted = 0;
  for (const e of model.elements) {
    for (const f of Object.values(e.faces)) {
      const [a, b, c, d] = f.uv;
      if (a === c || b === d) continue;
      const [x0, y0, x1, y1] = rect(f.uv);
      let hit = 0;
      for (let y = y0; y < y1; y++) for (let x = x0; x < x1; x++) {
        if (back.px[(y * back.stride ? y * back.stride : 0) + 0] !== undefined
            && back.px[y * back.stride + x * 4 + 3] > 0) hit++;
      }
      if (!hit) unpainted++;
    }
  }
  if (unpainted) throw new Error(`${kind}: ${unpainted} face rects came out fully transparent`);
  console.log(`${kind.padEnd(11)} ${W}x${H} painted — tint rgb(${tint.map(Math.round).join(',')}), all faces covered`);
}
