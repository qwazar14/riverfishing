// Paints the shared reel sheet (§reel-3d) in the feeder's visual language, same sampling rule as
// gen_rod_texture.js: fittings take the reel-seat metal, the handle knob takes the grip, the drag
// knob takes the tip accent — and the spool carries a pale band of wound LINE, which is what makes
// a reel read as a reel at four texels.
//
//   node tools/gen_reel_texture.js      3D/reels/reel_4000.json -> 3D/reels/texture.png
const fs = require('fs');
const { readPNG, writePNG } = require('./lib/png');

const feeder = JSON.parse(fs.readFileSync('3D/rods/feeder/blank_feeder.json', 'utf8'));
const ftex = readPNG('3D/rods/feeder/texture.png');
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
const face = (name, f = 'north') => feeder.elements.find(e => e.name === name).faces[f].uv;
const shade = (c, k) => c.map(v => Math.max(0, Math.min(255, Math.round(v * k))));

const SEAT = avgRect(ftex, fscale, face('reel_seat'));
const GRIP = avgRect(ftex, fscale, face('rear_grip'));
const TIP = avgRect(ftex, fscale, face('quivertip'));
const RING = avgRect(ftex, fscale, face('guide_1', 'east'));
const LINE = [214, 208, 186];                       // pale mono wound on the spool

const MATS = {
  foot: shade(SEAT, 0.9), stem: shade(SEAT, 0.9), body: shade(SEAT, 1.1),
  rotor: shade(SEAT, 0.8), spool: shade(RING, 1.15), drag_knob: TIP,
  bail: RING, line_roller: RING, handle_arm: shade(SEAT, 0.95), handle_knob: GRIP,
};

const m = JSON.parse(fs.readFileSync('3D/reels/reel_4000.json', 'utf8'));
const W = m.texture_size[0], H = m.texture_size[1], scale = W / 16;
const canvas = Buffer.alloc(W * H * 4);
const put = (x, y, c) => {
  if (x < 0 || y < 0 || x >= W || y >= H) return;
  const o = (y * W + x) * 4;
  canvas[o] = c[0]; canvas[o + 1] = c[1]; canvas[o + 2] = c[2]; canvas[o + 3] = 255;
};

for (const e of m.elements) {
  const base = MATS[e.name];
  if (!base) throw new Error(`no material for reel element "${e.name}"`);
  for (const f of Object.values(e.faces)) {
    const [a, b, c, d] = f.uv;
    if (a === c || b === d) continue;
    let x0 = Math.round(Math.min(a, c) * scale), x1 = Math.round(Math.max(a, c) * scale);
    let y0 = Math.round(Math.min(b, d) * scale), y1 = Math.round(Math.max(b, d) * scale);
    if (x1 === x0) x1 = x0 + 1;
    if (y1 === y0) y1 = y0 + 1;
    const h = y1 - y0;
    for (let y = y0; y < y1; y++) {
      const k = h < 3 ? 1 : y === y0 ? 1.18 : y === y1 - 1 ? 0.72 : 1;
      for (let x = x0; x < x1; x++) put(x, y, shade(base, k));
    }
    // the wound line: a pale band across the spool's middle rows on every face tall enough to show
    if (e.name === 'spool' && h >= 3) {
      const mid = (y0 + y1) >> 1;
      for (let x = x0; x < x1; x++) { put(x, mid, LINE); if (h >= 5) put(x, mid - 1, shade(LINE, 0.9)); }
    }
  }
}

writePNG('3D/reels/texture.png', canvas, W, H);
console.log(`3D/reels/texture.png ${W}x${H} painted — seat metal, grip knob, ${m.elements.length} parts covered`);
