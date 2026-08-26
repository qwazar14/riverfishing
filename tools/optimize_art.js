// Lossless PNG shrink for art assets (§journal-art): re-filters every row adaptively, re-deflates at
// maximum, and drops ancillary chunks (tEXt/pHYs/gAMA/...) that MC never reads. Pixels are verified
// byte-identical after — a smaller file that changed a pixel is not an optimization, it is damage.
//
//   node tools/optimize_art.js <dir-or-file> [...more]
//
// Handles 8-bit greyscale (0), truecolor (2), indexed (3), grey+alpha (4) and RGBA (6). Files it
// cannot parse, or cannot shrink, are left untouched.
const fs = require('fs'), path = require('path'), zlib = require('zlib');

const CRITICAL = new Set(['IHDR', 'PLTE', 'tRNS', 'IDAT', 'IEND']);
const crcTable = Array.from({ length: 256 }, (_, n) => {
  let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; return c >>> 0;
});
const crc = b => { let c = 0xffffffff; for (const v of b) c = crcTable[(c ^ v) & 0xff] ^ (c >>> 8); return (c ^ 0xffffffff) >>> 0; };
const chunk = (tag, data) => {
  const head = Buffer.alloc(8); head.writeUInt32BE(data.length, 0); head.write(tag, 4, 'ascii');
  const tail = Buffer.alloc(4); tail.writeUInt32BE(crc(Buffer.concat([head.slice(4), data])), 0);
  return Buffer.concat([head, data, tail]);
};

const BPP = { 0: 1, 2: 3, 3: 1, 4: 2, 6: 4 };

function unfilter(raw, W, H, bpp) {
  const stride = W * bpp, px = Buffer.alloc(H * stride);
  for (let y = 0; y < H; y++) {
    const f = raw[y * (stride + 1)], line = raw.slice(y * (stride + 1) + 1, (y + 1) * (stride + 1));
    for (let i = 0; i < stride; i++) {
      const a = i >= bpp ? px[y * stride + i - bpp] : 0;
      const b = y > 0 ? px[(y - 1) * stride + i] : 0;
      const c = i >= bpp && y > 0 ? px[(y - 1) * stride + i - bpp] : 0;
      let v = line[i];
      if (f === 1) v += a; else if (f === 2) v += b; else if (f === 3) v += (a + b) >> 1;
      else if (f === 4) { const p = a + b - c, pa = Math.abs(p - a), pb = Math.abs(p - b), pc = Math.abs(p - c);
        v += (pa <= pb && pa <= pc) ? a : (pb <= pc ? b : c); }
      else if (f !== 0) throw new Error(`bad filter ${f}`);
      px[y * stride + i] = v & 0xff;
    }
  }
  return px;
}

// standard minimum-sum-of-absolutes heuristic: per row, keep whichever filter encodes smallest
function refilter(px, W, H, bpp) {
  const stride = W * bpp, out = Buffer.alloc(H * (stride + 1));
  const cand = Buffer.alloc(stride);
  for (let y = 0; y < H; y++) {
    let bestF = 0, bestSum = Infinity, bestLine = null;
    for (let f = 0; f <= 4; f++) {
      let sum = 0;
      for (let i = 0; i < stride; i++) {
        const x = px[y * stride + i];
        const a = i >= bpp ? px[y * stride + i - bpp] : 0;
        const b = y > 0 ? px[(y - 1) * stride + i] : 0;
        const c = i >= bpp && y > 0 ? px[(y - 1) * stride + i - bpp] : 0;
        let v;
        if (f === 0) v = x;
        else if (f === 1) v = x - a;
        else if (f === 2) v = x - b;
        else if (f === 3) v = x - ((a + b) >> 1);
        else { const p = a + b - c, pa = Math.abs(p - a), pb = Math.abs(p - b), pc = Math.abs(p - c);
          v = x - ((pa <= pb && pa <= pc) ? a : (pb <= pc ? b : c)); }
        v &= 0xff;
        cand[i] = v;
        sum += v < 128 ? v : 256 - v;
      }
      if (sum < bestSum) { bestSum = sum; bestF = f; bestLine = Buffer.from(cand); }
    }
    out[y * (stride + 1)] = bestF;
    bestLine.copy(out, y * (stride + 1) + 1);
  }
  return out;
}

function optimize(file) {
  const buf = fs.readFileSync(file);
  if (buf.length < 8 || buf.slice(0, 8).toString('hex') !== '89504e470d0a1a0a') return null;
  const W = buf.readUInt32BE(16), H = buf.readUInt32BE(20), depth = buf[24], ctype = buf[25];
  if (depth !== 8 || !(ctype in BPP) || buf[28] !== 0) return null;   // interlaced/odd depth: skip
  const bpp = BPP[ctype];
  const keep = [], idat = [];
  for (let o = 8; o < buf.length;) {
    const len = buf.readUInt32BE(o), tag = buf.slice(o + 4, o + 8).toString('ascii');
    if (tag === 'IDAT') idat.push(buf.slice(o + 8, o + 8 + len));
    else if (CRITICAL.has(tag) && tag !== 'IEND') keep.push([tag, buf.slice(o + 8, o + 8 + len)]);
    o += 12 + len;
  }
  const px = unfilter(zlib.inflateSync(Buffer.concat(idat)), W, H, bpp);
  const candidates = [
    refilter(px, W, H, bpp),
    // filter NONE everywhere — often wins on dithered/indexed art where neighbours do not correlate
    (() => { const s = W * bpp, o = Buffer.alloc(H * (s + 1));
      for (let y = 0; y < H; y++) { o[y * (s + 1)] = 0; px.copy(o, y * (s + 1) + 1, y * s, (y + 1) * s); }
      return o; })(),
  ];
  let best = null;
  for (const raw of candidates) {
    const z = zlib.deflateSync(raw, { level: 9, memLevel: 9 });
    if (best == null || z.length < best.length) best = z;
  }
  const out = Buffer.concat([
    buf.slice(0, 8),
    ...keep.map(([tag, data]) => chunk(tag, data)),
    chunk('IDAT', best), chunk('IEND', Buffer.alloc(0)),
  ]);
  if (out.length >= buf.length) return { before: buf.length, after: buf.length, kept: true };
  // prove losslessness before touching the file: decode our own output and compare every pixel
  const vIdat = [];
  for (let o = 8; o < out.length;) {
    const len = out.readUInt32BE(o);
    if (out.slice(o + 4, o + 8).toString('ascii') === 'IDAT') vIdat.push(out.slice(o + 8, o + 8 + len));
    o += 12 + len;
  }
  if (!unfilter(zlib.inflateSync(Buffer.concat(vIdat)), W, H, bpp).equals(px)) {
    throw new Error(`${file}: verification failed — output pixels differ, file NOT written`);
  }
  fs.writeFileSync(file, out);
  return { before: buf.length, after: out.length };
}

let before = 0, after = 0, n = 0, skipped = 0;
for (const arg of process.argv.slice(2)) {
  const files = fs.statSync(arg).isDirectory()
      ? fs.readdirSync(arg).filter(f => f.endsWith('.png')).map(f => path.join(arg, f)) : [arg];
  for (const f of files) {
    const r = optimize(f);
    if (!r) { skipped++; continue; }
    before += r.before; after += r.after; n++;
    if (!r.kept) console.log(`${path.basename(f).padEnd(28)} ${(r.before / 1024).toFixed(0)}K -> ${(r.after / 1024).toFixed(0)}K  (-${(100 - r.after / r.before * 100).toFixed(0)}%)`);
  }
}
console.log(`\n${n} files: ${(before / 1048576).toFixed(2)} MB -> ${(after / 1048576).toFixed(2)} MB `
  + `(-${(100 - after / before * 100).toFixed(1)}%)${skipped ? `, ${skipped} skipped` : ''}`);
