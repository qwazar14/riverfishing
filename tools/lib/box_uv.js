// Shelf-packs Blockbench box unwraps into MC's fixed 0..16 UV space.
//
// The two things that bite every time: UV space is 0..16 whatever `texture_size` says (that only
// sets texels per UV unit, not room), and a long rod section is wider than half the sheet, so two
// never sit side by side. Hence `lengthScale`: the X extent is squeezed by the smallest factor that
// still fits, found by trying, while the cross-section stays 1:1 where the detail is.
const r = n => Math.round(n * 1000) / 1000;
const SHEET = 16;

function shelf(uvLen, uvCross) {
  const s = { x: 0, y: 0, rowH: 0 };
  return {
    used: () => r(s.y + s.rowH),
    box(dxRaw, dyRaw, dzRaw) {
      const dx = dxRaw * uvLen, dy = dyRaw * uvCross, dz = dzRaw * uvCross;
      const w = 2 * dx + 2 * dz, h = dy + dz;
      if (w > SHEET) return null;
      if (s.x + w > SHEET) { s.y += s.rowH; s.x = 0; s.rowH = 0; }
      const u = s.x, v = s.y;
      s.x += w; s.rowH = Math.max(s.rowH, h);
      if (v + h > SHEET) return null;
      const b = (a, c, d, e) => [r(a), r(c), r(d), r(e)];
      return { up:    b(u + dz, v, u + dz + dx, v + dz),
               down:  b(u + dz + dx, v, u + 2 * dx + dz, v + dz),
               east:  b(u, v + dz, u + dz, v + dz + dy),
               north: b(u + dz, v + dz, u + dz + dx, v + dz + dy),
               west:  b(u + dz + dx, v + dz, u + 2 * dz + dx, v + dz + dy),
               south: b(u + 2 * dz + dx, v + dz, u + 2 * dx + 2 * dz, v + dz + dy) };
    },
  };
}

/**
 * @param dims  [dx,dy,dz] per element, in model units
 * @param keys  one key per element; elements sharing a key share ONE unwrap, cut from the fattest
 *              of the group — five identical bamboo nodes should not cost five squares
 * @returns {uvs, lengthScale, used}
 */
function packBoxUVs(dims, keys) {
  const pick = new Map();
  keys.forEach((k, i) => {
    const cur = pick.get(k);
    if (cur === undefined || dims[i][1] > dims[cur][1]) pick.set(k, i);
  });
  // tallest first, or a short row wastes its whole height on whatever landed there first
  const order = [...pick.values()].sort((a, b) => (dims[b][1] + dims[b][2]) - (dims[a][1] + dims[a][2]));

  // Length squeezes first (the featureless axis), the cross-section only when it must: a fat rod's
  // unwrap heights do not shrink with length, so a thick-butt blank can overflow the sheet at ANY
  // length scale — only then is detail-carrying cross resolution traded away, and as little as fits.
  for (const crossScale of [1, 0.8, 0.65, 0.5]) {
    for (const lengthScale of [1, 0.75, 0.5, 0.4, 0.3, 0.25, 0.2, 0.15, 0.1]) {
      const s = shelf(lengthScale, crossScale), m = new Map();
      let ok = true;
      for (const i of order) {
        const uv = s.box(...dims[i]);
        if (!uv) { ok = false; break; }
        m.set(keys[i], uv);
      }
      if (ok) return { uvs: keys.map(k => m.get(k)), lengthScale, crossScale, used: s.used() };
    }
  }
  throw new Error('cannot fit the unwrap in 16 UV units even at 0.1 length x 0.5 cross scale');
}

module.exports = { packBoxUVs, SHEET };
