// Lossy palette shrink for journal art (§journal-art): median-cut quantization to a colour budget,
// then TWO remap variants per file — flat nearest-colour and ordered Bayer 4x4 — and whichever
// DEFLATES smaller wins. That is the size-first rule: error-diffusion dither (Floyd-Steinberg et al.)
// is deliberately not offered, because its noise is the least compressible signal there is; ordered
// dither is periodic, so the filter+deflate stage can still eat it.
//
//   node tools/quantize_art.js <colors> <dir-or-file> [...more]
//   node tools/quantize_art.js 128 common/src/main/resources/assets/riverfishing/textures/gui/journal/fish
//
// Indexed 8-bit PNGs only (that is what the art exporter emits). Alpha survives via tRNS.
const fs = require('fs'), path = require('path'), zlib = require('zlib');

const COLORS = parseInt(process.argv[2], 10);
if (!(COLORS >= 2 && COLORS <= 256)) { console.error('usage: quantize_art.js <colors 2..256> <dir|file>...'); process.exit(2); }

const crcTable = Array.from({ length: 256 }, (_, n) => {
  let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; return c >>> 0;
});
const crc = b => { let c = 0xffffffff; for (const v of b) c = crcTable[(c ^ v) & 0xff] ^ (c >>> 8); return (c ^ 0xffffffff) >>> 0; };
const chunk = (tag, data) => {
  const head = Buffer.alloc(8); head.writeUInt32BE(data.length, 0); head.write(tag, 4, 'ascii');
  const tail = Buffer.alloc(4); tail.writeUInt32BE(crc(Buffer.concat([head.slice(4), data])), 0);
  return Buffer.concat([head, data, tail]);
};

function unfilter(raw, W, H) {                       // bpp = 1 (indexed)
  const px = Buffer.alloc(H * W);
  for (let y = 0; y < H; y++) {
    const f = raw[y * (W + 1)], line = raw.slice(y * (W + 1) + 1, (y + 1) * (W + 1));
    for (let i = 0; i < W; i++) {
      const a = i >= 1 ? px[y * W + i - 1] : 0;
      const b = y > 0 ? px[(y - 1) * W + i] : 0;
      const c = i >= 1 && y > 0 ? px[(y - 1) * W + i - 1] : 0;
      let v = line[i];
      if (f === 1) v += a; else if (f === 2) v += b; else if (f === 3) v += (a + b) >> 1;
      else if (f === 4) { const p = a + b - c, pa = Math.abs(p - a), pb = Math.abs(p - b), pc = Math.abs(p - c);
        v += (pa <= pb && pa <= pc) ? a : (pb <= pc ? b : c); }
      else if (f !== 0) throw new Error(`bad filter ${f}`);
      px[y * W + i] = v & 0xff;
    }
  }
  return px;
}

function refilterBest(px, W, H) {
  const out = Buffer.alloc(H * (W + 1)), cand = Buffer.alloc(W);
  for (let y = 0; y < H; y++) {
    let bestF = 0, bestSum = Infinity, bestLine = null;
    for (let f = 0; f <= 4; f++) {
      let sum = 0;
      for (let i = 0; i < W; i++) {
        const x = px[y * W + i];
        const a = i >= 1 ? px[y * W + i - 1] : 0;
        const b = y > 0 ? px[(y - 1) * W + i] : 0;
        const c = i >= 1 && y > 0 ? px[(y - 1) * W + i - 1] : 0;
        let v;
        if (f === 0) v = x; else if (f === 1) v = x - a; else if (f === 2) v = x - b;
        else if (f === 3) v = x - ((a + b) >> 1);
        else { const p = a + b - c, pa = Math.abs(p - a), pb = Math.abs(p - b), pc = Math.abs(p - c);
          v = x - ((pa <= pb && pa <= pc) ? a : (pb <= pc ? b : c)); }
        v &= 0xff; cand[i] = v; sum += v < 128 ? v : 256 - v;
      }
      if (sum < bestSum) { bestSum = sum; bestF = f; bestLine = Buffer.from(cand); }
    }
    out[y * (W + 1)] = bestF;
    bestLine.copy(out, y * (W + 1) + 1);
  }
  return out;
}

/** Weighted median cut over RGBA palette entries — boxes split on the widest channel. */
function medianCut(entries, budget) {
  let boxes = [entries.filter(e => e.count > 0)];
  while (boxes.length < budget) {
    let bi = -1, bRange = -1, bCh = 0;
    for (let i = 0; i < boxes.length; i++) {
      if (boxes[i].length < 2) continue;
      for (let ch = 0; ch < 4; ch++) {
        let lo = 255, hi = 0;
        for (const e of boxes[i]) { lo = Math.min(lo, e.rgba[ch]); hi = Math.max(hi, e.rgba[ch]); }
        if (hi - lo > bRange) { bRange = hi - lo; bi = i; bCh = ch; }
      }
    }
    if (bi < 0 || bRange <= 0) break;
    const box = boxes[bi].sort((a, b) => a.rgba[bCh] - b.rgba[bCh]);
    const half = box.reduce((s, e) => s + e.count, 0) / 2;
    let acc = 0, cut = 1;
    for (let i = 0; i < box.length - 1; i++) { acc += box[i].count; if (acc >= half) { cut = i + 1; break; } }
    boxes.splice(bi, 1, box.slice(0, cut), box.slice(cut));
  }
  return boxes.map(box => {
    const w = box.reduce((s, e) => s + e.count, 0) || 1;
    return [0, 1, 2, 3].map(ch => Math.round(box.reduce((s, e) => s + e.rgba[ch] * e.count, 0) / w));
  });
}

const BAYER = [[0, 8, 2, 10], [12, 4, 14, 6], [3, 11, 1, 9], [15, 7, 13, 5]];

function encodeIndexed(indices, W, H, palette) {
  const plte = Buffer.alloc(palette.length * 3);
  let lastAlpha = -1;
  palette.forEach((c, i) => {
    plte[i * 3] = c[0]; plte[i * 3 + 1] = c[1]; plte[i * 3 + 2] = c[2];
    if (c[3] !== 255) lastAlpha = i;
  });
  const parts = [Buffer.from('89504e470d0a1a0a', 'hex')];
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(W, 0); ihdr.writeUInt32BE(H, 4); ihdr[8] = 8; ihdr[9] = 3;
  parts.push(chunk('IHDR', ihdr), chunk('PLTE', plte));
  if (lastAlpha >= 0) {
    const trns = Buffer.alloc(lastAlpha + 1);
    for (let i = 0; i <= lastAlpha; i++) trns[i] = palette[i][3];
    parts.push(chunk('tRNS', trns));
  }
  const filtered = refilterBest(indices, W, H);
  parts.push(chunk('IDAT', zlib.deflateSync(filtered, { level: 9, memLevel: 9 })), chunk('IEND', Buffer.alloc(0)));
  return Buffer.concat(parts);
}

function processFile(file) {
  const buf = fs.readFileSync(file);
  if (buf.slice(0, 8).toString('hex') !== '89504e470d0a1a0a') return null;
  const W = buf.readUInt32BE(16), H = buf.readUInt32BE(20);
  if (buf[24] !== 8 || buf[25] !== 3 || buf[28] !== 0) return null;    // indexed 8-bit only
  let plte = null, trns = null; const idat = [];
  for (let o = 8; o < buf.length;) {
    const len = buf.readUInt32BE(o), tag = buf.slice(o + 4, o + 8).toString('ascii');
    if (tag === 'PLTE') plte = buf.slice(o + 8, o + 8 + len);
    else if (tag === 'tRNS') trns = buf.slice(o + 8, o + 8 + len);
    else if (tag === 'IDAT') idat.push(buf.slice(o + 8, o + 8 + len));
    o += 12 + len;
  }
  const indices = unfilter(zlib.inflateSync(Buffer.concat(idat)), W, H);
  const n = plte.length / 3;
  const counts = new Array(n).fill(0);
  for (const i of indices) counts[i]++;
  const src = Array.from({ length: n }, (_, i) => ({
    rgba: [plte[i * 3], plte[i * 3 + 1], plte[i * 3 + 2], trns && i < trns.length ? trns[i] : 255],
    count: counts[i],
  }));

  const used = src.filter(e => e.count > 0).length;
  const palette = used <= COLORS ? src.filter(e => e.count > 0).map(e => e.rgba)
      : medianCut(src, COLORS);
  // transparency must not bleed: fully transparent stays exactly that
  for (const c of palette) if (c[3] < 8) { c[3] = 0; }

  const nearest = rgba => {
    let bi = 0, bd = Infinity;
    for (let i = 0; i < palette.length; i++) {
      const p = palette[i];
      const d = (rgba[0] - p[0]) ** 2 + (rgba[1] - p[1]) ** 2 + (rgba[2] - p[2]) ** 2
              + 2 * (rgba[3] - p[3]) ** 2;                 // alpha errors are twice as visible
      if (d < bd) { bd = d; bi = i; }
    }
    return bi;
  };

  // variant A: flat nearest — a 256-entry LUT
  const flatLut = src.map(e => nearest(e.rgba));
  const flat = Buffer.alloc(indices.length);
  for (let i = 0; i < indices.length; i++) flat[i] = flatLut[indices[i]];

  // variant B: ordered Bayer 4x4 — (srcIndex, cell) LUT, spread sized to the palette coarseness
  const SPREAD = 12;
  const bayerLut = [];
  for (let cell = 0; cell < 16; cell++) {
    const off = ((BAYER[cell >> 2][cell & 3] + 0.5) / 16 - 0.5) * SPREAD;
    bayerLut.push(src.map(e => nearest([
      Math.max(0, Math.min(255, e.rgba[0] + off)),
      Math.max(0, Math.min(255, e.rgba[1] + off)),
      Math.max(0, Math.min(255, e.rgba[2] + off)),
      e.rgba[3]])));
  }
  const dithered = Buffer.alloc(indices.length);
  for (let y = 0; y < H; y++) {
    for (let x = 0; x < W; x++) {
      dithered[y * W + x] = bayerLut[(y & 3) * 4 + (x & 3)][indices[y * W + x]];
    }
  }

  const outFlat = encodeIndexed(flat, W, H, palette);
  const outDith = encodeIndexed(dithered, W, H, palette);
  const win = outFlat.length <= outDith.length ? outFlat : outDith;
  fs.writeFileSync(file, win);
  return { before: buf.length, after: win.length, used, pal: palette.length,
           mode: outFlat.length <= outDith.length ? 'flat' : 'bayer' };
}

let before = 0, after = 0, count = 0;
for (const arg of process.argv.slice(3)) {
  const files = fs.statSync(arg).isDirectory()
      ? fs.readdirSync(arg).filter(f => f.endsWith('.png')).map(f => path.join(arg, f)) : [arg];
  for (const f of files) {
    const r = processFile(f);
    if (!r) { console.log(`${path.basename(f)}: skipped (not 8-bit indexed)`); continue; }
    before += r.before; after += r.after; count++;
    console.log(`${path.basename(f).padEnd(28)} ${r.used}->${r.pal} colours, ${r.mode.padEnd(5)} `
        + `${(r.before / 1024).toFixed(0)}K -> ${(r.after / 1024).toFixed(0)}K (-${(100 - r.after / r.before * 100).toFixed(0)}%)`);
  }
}
console.log(`\n${count} files: ${(before / 1048576).toFixed(2)} MB -> ${(after / 1048576).toFixed(2)} MB `
    + `(-${(100 - after / before * 100).toFixed(1)}%)`);
