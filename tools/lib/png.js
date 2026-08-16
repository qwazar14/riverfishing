// Minimal 8-bit RGBA PNG read/write on node's zlib — enough for the rod texture pipeline.
const fs = require('fs'), zlib = require('zlib');

function readPNG(file) {
  const buf = fs.readFileSync(file);
  if (buf.slice(0, 8).toString('hex') !== '89504e470d0a1a0a') throw new Error(file + ' is not a PNG');
  const W = buf.readUInt32BE(16), H = buf.readUInt32BE(20);
  if (buf[24] !== 8 || buf[25] !== 6) throw new Error(`${file}: need 8-bit RGBA, got depth ${buf[24]} type ${buf[25]}`);
  const idat = [];
  for (let o = 8; o < buf.length;) {
    const len = buf.readUInt32BE(o);
    if (buf.slice(o + 4, o + 8).toString('ascii') === 'IDAT') idat.push(buf.slice(o + 8, o + 8 + len));
    o += 12 + len;
  }
  const raw = zlib.inflateSync(Buffer.concat(idat));
  const BPP = 4, stride = W * BPP, px = Buffer.alloc(H * stride);
  for (let y = 0; y < H; y++) {
    const f = raw[y * (stride + 1)], line = raw.slice(y * (stride + 1) + 1, (y + 1) * (stride + 1));
    for (let i = 0; i < stride; i++) {
      const a = i >= BPP ? px[y * stride + i - BPP] : 0;
      const b = y > 0 ? px[(y - 1) * stride + i] : 0;
      const c = i >= BPP && y > 0 ? px[(y - 1) * stride + i - BPP] : 0;
      let v = line[i];
      if (f === 1) v += a; else if (f === 2) v += b; else if (f === 3) v += (a + b) >> 1;
      else if (f === 4) { const p = a + b - c, pa = Math.abs(p - a), pb = Math.abs(p - b), pc = Math.abs(p - c);
        v += (pa <= pb && pa <= pc) ? a : (pb <= pc ? b : c); }
      px[y * stride + i] = v & 0xff;
    }
  }
  return { W, H, px, stride };
}

const crcTable = Array.from({ length: 256 }, (_, n) => {
  let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; return c >>> 0;
});
const crc = b => { let c = 0xffffffff; for (const v of b) c = crcTable[(c ^ v) & 0xff] ^ (c >>> 8); return (c ^ 0xffffffff) >>> 0; };

function writePNG(file, px, W, H) {
  const stride = W * 4, out = Buffer.alloc(H * (stride + 1));
  for (let y = 0; y < H; y++) { out[y * (stride + 1)] = 0; px.copy(out, y * (stride + 1) + 1, y * stride, (y + 1) * stride); }
  const chunk = (tag, data) => {
    const head = Buffer.alloc(8); head.writeUInt32BE(data.length, 0); head.write(tag, 4, 'ascii');
    const tail = Buffer.alloc(4); tail.writeUInt32BE(crc(Buffer.concat([head.slice(4), data])), 0);
    return Buffer.concat([head, data, tail]);
  };
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(W, 0); ihdr.writeUInt32BE(H, 4); ihdr[8] = 8; ihdr[9] = 6;
  fs.writeFileSync(file, Buffer.concat([
    Buffer.from('89504e470d0a1a0a', 'hex'), chunk('IHDR', ihdr),
    chunk('IDAT', zlib.deflateSync(out, { level: 9 })), chunk('IEND', Buffer.alloc(0)),
  ]));
}

module.exports = { readPNG, writePNG };
