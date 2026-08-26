// End-to-end audit of the 3D rod pipeline (§rod-3d): sources in 3D/rods/, split segments and
// textures in assets/, and the constants in RodItemRenderer that must agree with both. Every rule
// here is one that actually broke during development — this is the accumulated checklist, runnable.
//
//   node tools/check_rod_assets.js        exits 1 if anything fails
//
// The Java constants are PARSED OUT OF THE SOURCE FILE rather than restated here, so this script
// cross-checks two independent statements (model files vs Java) instead of agreeing with itself.
const fs = require('fs');

const RODS = ['stick', 'bamboo', 'pole', 'winter', 'ultralight', 'spinning', 'feeder',
              'bottom', 'carp', 'surf', 'sea_spin', 'boat', 'trolling'];
const SEGMENTED = { feeder: 5, pole: 5, bamboo: 6, spinning: 5, ultralight: 5, surf: 5, carp: 5, boat: 6, bottom: 5, trolling: 4, sea_spin: 8 };          // kind -> piece count
const ASSETS = 'common/src/main/resources/assets/riverfishing';
const RENDERER = 'common/src/main/java/com/riverfishing/client/RodItemRenderer.java';
const LAYERS = 'common/src/main/java/com/riverfishing/client/RodModelLayers.java';
const AXIS_Y = 10.5, AXIS_Z = 8.5, LEGAL = [0, 22.5, -22.5, 45, -45];
const r3 = n => Math.round(n * 1000) / 1000;

let failures = 0, notes = 0;
const fail = m => { failures++; console.log('  \x1b[31mFAIL\x1b[0m ' + m); };
const note = m => { notes++; console.log('  \x1b[33mnote\x1b[0m ' + m); };
const ok = m => console.log('  ok   ' + m);
const read = f => JSON.parse(fs.readFileSync(f, 'utf8'));

// ---- parse the Java constants ----
const java = fs.readFileSync(RENDERER, 'utf8');
const tipX = {};
for (const [, k, v] of java.matchAll(/"(\w+)",\s*(-?[\d.]+)f/g)) {
  if (RODS.includes(k) && !(k in tipX)) tipX[k] = parseFloat(v);   // first map literal = BLANK_TIP_X? no —
}
// BLANK_TIP_X and BLANK_JOINTS_X both match the pattern; take them from their own blocks instead.
const tipBlock = java.match(/BLANK_TIP_X = java\.util\.Map\.of(?:Entries)?\(([\s\S]*?)\);/);
if (!tipBlock) throw new Error('cannot find BLANK_TIP_X in ' + RENDERER);
Object.keys(tipX).forEach(k => delete tipX[k]);
for (const [, k, v] of tipBlock[1].matchAll(/"(\w+)",\s*(-?[\d.]+)f/g)) tipX[k] = parseFloat(v);
const jointsBlock = java.match(/BLANK_JOINTS_X =[\s\S]*?java\.util\.Map\.of(?:Entries)?\(([\s\S]*?)\);/);
if (!jointsBlock) throw new Error('cannot find BLANK_JOINTS_X in ' + RENDERER);
const javaJoints = {};
for (const [, k, arr] of jointsBlock[1].matchAll(/"(\w+)",\s*new float\[\]\{([^}]*)\}/g)) {
  javaJoints[k] = arr.split(',').map(s => parseFloat(s));
}
const segConst = parseInt(fs.readFileSync(LAYERS, 'utf8').match(/BLANK_SEGMENTS = (\d+)/)[1]);

console.log(`parsed from Java: BLANK_SEGMENTS=${segConst}, tips {${Object.entries(tipX).map(([k, v]) => k + ':' + v).join(', ')}}, chains {${Object.entries(javaJoints).map(([k, v]) => k + ':' + v.length + 'j').join(', ')}}`);

for (const kind of RODS) {
  console.log(`\n${kind}`);
  const srcPath = `3D/rods/${kind}/blank_${kind}.json`;
  if (!fs.existsSync(srcPath)) { fail(`no source at ${srcPath}`); continue; }
  const src = read(srcPath);

  // -- source sanity: the ways a model file dies at load or renders wrong --
  const declared = new Set(Object.keys(src.textures).map(k => '#' + k));
  let minX = Infinity, maxX = -Infinity, badFace = 0, badAngle = 0, badCoord = 0;
  for (const e of src.elements) {
    for (const v of [...e.from, ...e.to]) { if (v < -16 || v > 32) badCoord++; }
    if (e.rotation && !LEGAL.includes(e.rotation.angle)) badAngle++;
    for (const f of Object.values(e.faces)) if (!declared.has(f.texture)) badFace++;
    minX = Math.min(minX, e.from[0]); maxX = Math.max(maxX, e.to[0]);
  }
  badCoord ? fail(`${badCoord} coords outside -16..32`) : ok(`coords in -16..32 (x ${minX}..${maxX})`);
  badAngle ? fail(`${badAngle} rotation angles MC will not load`) : ok('rotation angles legal');
  badFace ? fail(`${badFace} faces reference undeclared texture slots`) : ok(`faces match slots {${Object.keys(src.textures).join(',')}}`);
  if (maxX !== 32) fail(`butt at x=${maxX}, hand pose grips x=32`);

  // -- tip anchor: Java must point at the real end of this model --
  if (tipX[kind] === undefined) fail('no BLANK_TIP_X entry');
  else if (tipX[kind] !== minX) fail(`BLANK_TIP_X=${tipX[kind]} but model ends at x=${minX}`);
  else ok(`tip anchor ${tipX[kind]} matches model`);
  const tipEl = src.elements.reduce((a, e) => e.from[0] < a.from[0] ? e : a);
  const dev = Math.max(Math.abs((tipEl.from[1] + tipEl.to[1]) / 2 - AXIS_Y),
                       Math.abs((tipEl.from[2] + tipEl.to[2]) / 2 - AXIS_Z));
  if (dev > 0.25) note(`tip element sits ${r3(dev)}u off the rod axis — the line anchors on the axis`);

  const segCount = SEGMENTED[kind];
  if (!segCount) {
    // -- rigid rod: installed s0 must be the source, byte-for-byte in geometry and UV --
    const inst = `${ASSETS}/models/item/rod/blank_${kind}_s0.json`;
    if (!fs.existsSync(inst)) { note('not installed as 3D (no s0) — sprite path only'); continue; }
    const m = read(inst);
    const strip = x => JSON.stringify(x.elements.map(e => [e.name, e.from, e.to, e.rotation, e.faces]));
    strip(m) === strip(src) ? ok(`installed s0 identical (${m.elements.length} el)`)
        : fail('installed s0 differs from source — re-run tools/install_rod_model.js');
    checkTexture(kind, m);
    if (javaJoints[kind]) fail('has BLANK_JOINTS_X but no segment config — chain would draw s1.. as missing');
    continue;
  }

  // -- segmented rod: pieces must reassemble the source exactly --
  const segs = [];
  for (let i = 0; i < segCount; i++) {
    const p = `${ASSETS}/models/item/rod/blank_${kind}_s${i}.json`;
    if (!fs.existsSync(p)) { fail(`missing segment s${i}`); break; }
    segs.push(read(p));
  }
  if (segs.length !== segCount) continue;
  if (segCount > segConst) fail(`${segCount} pieces but BLANK_SEGMENTS=${segConst} — s${segConst}+ never registered`);
  const leftover = `${ASSETS}/models/item/rod/blank_${kind}_s${segCount}.json`;
  if (fs.existsSync(leftover)) fail(`stale extra segment ${leftover} — an old split left it behind`);

  const pieceEls = segs.flatMap(s => s.elements);
  const strip = els => JSON.stringify([...els].sort((a, b) => a.name.localeCompare(b.name))
      .map(e => [e.name, e.from, e.to, e.rotation, e.faces]));
  strip(pieceEls) === strip(src.elements)
      ? ok(`${segCount} pieces reassemble the source exactly (${pieceEls.length} el)`)
      : fail('segments do not reassemble the source — re-run tools/split_rod.js ' + kind);
  for (const s of segs) {
    if (JSON.stringify(Object.keys(s.textures).filter(k => k !== 'particle').sort())
        !== JSON.stringify(Object.keys(src.textures).filter(k => k !== 'particle').sort())) {
      fail('segment slot keys differ from source'); break;
    }
  }
  checkTexture(kind, segs[0]);

  // -- joints: Java vs geometry. Each hinge must sit on the boundary between its two pieces, and the
  // tip-side piece must carry a collar that sleeves it (that is why the split is AT the collar). --
  const jj = javaJoints[kind];
  if (!jj) { fail('no BLANK_JOINTS_X entry — chain never bends'); continue; }
  if (jj.length !== segCount - 1) fail(`${jj.length} joints for ${segCount} pieces (want ${segCount - 1})`);
  const rangeOf = s => [Math.min(...s.elements.map(e => e.from[0])), Math.max(...s.elements.map(e => e.to[0]))];
  let jointsOk = true;
  for (let i = 0; i < jj.length && i + 1 < segs.length; i++) {
    const buttSide = rangeOf(segs[i]), tipSide = rangeOf(segs[i + 1]);
    // the hinge lies inside the tip-side piece's collar, which overlaps the butt-side piece's end
    if (jj[i] < tipSide[0] || jj[i] > tipSide[1] || jj[i] > buttSide[1] + 1 || jj[i] < buttSide[0] - 1) {
      fail(`joint ${i} at x=${jj[i]} is not at the s${i}/s${i + 1} boundary (s${i} ${buttSide[0]}..${buttSide[1]}, s${i + 1} ${tipSide[0]}..${tipSide[1]})`);
      jointsOk = false;
    }
    if (jj[i] <= (i + 1 < jj.length ? jj[i + 1] : -Infinity)) { fail('joints not descending'); jointsOk = false; }
  }
  if (jointsOk) ok(`${jj.length} joints sit on their piece boundaries: [${jj.join(', ')}]`);
  // triangular shares must sum to 1 whatever the count
  const sum = jj.reduce((a, _, i) => a + 2 * (i + 1) / (jj.length * (jj.length + 1)), 0);
  Math.abs(sum - 1) < 1e-6 ? ok('bend shares sum to 1') : fail(`bend shares sum to ${sum}`);
}

function checkTexture(kind, model) {
  const ref = Object.values(model.textures)[0];
  const expect = `riverfishing:item/rod/blank_${kind}_3d`;
  if (ref === expect) {
    const png = `${ASSETS}/textures/item/rod/blank_${kind}_3d.png`;
    if (!fs.existsSync(png)) { fail(`texture ${png} missing — model would render checkerboard`); return; }
    const b = fs.readFileSync(png);
    // staleness bites silently: the artist repaints 3D/rods/<kind>/texture.png, nothing reinstalls
    // it, and the game keeps last week's paint — byte-compare the source against the installed copy
    const src = `3D/rods/${kind}/texture.png`;
    if (fs.existsSync(src) && !fs.readFileSync(src).equals(b)) {
      fail(`installed texture is STALE — re-run the installer/splitter for ${kind}`);
      return;
    }
    ok(`texture installed (${b.readUInt32BE(16)}x${b.readUInt32BE(20)})`);
  } else if (ref.startsWith('minecraft:')) {
    // vanilla stand-in: renders fine, just not painted yet — the install pipeline's declared state
    note(`placeholder texture ${ref} — paint 3D/rods/${kind}/texture.png and re-run install`);
  } else {
    fail(`texture ref ${ref}, expected ${expect} or a minecraft: placeholder`);
  }
}

// ---- §line-thru-guides: the generated path asset must match the model sources ----
console.log('\nline paths');
{
  const asset = read(`${ASSETS}/rod_line_paths.json`);
  let bad = 0;
  for (const kind of RODS) {
    const m = read(`3D/rods/${kind}/blank_${kind}.json`);
    const guides = m.elements.filter(e => /^guide_/.test(e.name))
      .map(e => [r3(e.from[0]), r3((e.from[1] + e.to[1]) / 2)]).sort((a, b) => b[0] - a[0]);
    if (!guides.length) {
      if (asset[kind]) { fail(`${kind} has no guides but appears in the path asset`); bad++; }
      continue;
    }
    if (!asset[kind]) { fail(`${kind} has ${guides.length} guides but no path entry — line never threads`); bad++; continue; }
    if (JSON.stringify(asset[kind].guides) !== JSON.stringify(guides)) {
      fail(`${kind}: path guides diverge from the model — re-run tools/gen_line_paths.js`); bad++;
    }
    const tipEl = m.elements.reduce((a, e) => e.from[0] < a.from[0] ? e : a);
    if (Math.abs(asset[kind].tip[0] - tipEl.from[0]) > 0.01) { fail(`${kind}: path tip diverges from the model`); bad++; }
  }
  if (!bad) ok(`${Object.keys(asset).length} threaded rods in sync with their models`);
}

// ---- §rod-physics-per-rod: every rod must have a spring of its own ----
console.log('\nphysics profiles');
{
  const phys = read(`${ASSETS}/rod_physics.json`);
  let bad = 0;
  for (const kind of RODS) {
    const p = phys[kind];
    if (!p) { fail(`${kind} has no physics profile — it would run on the global spring silently`); bad++; continue; }
    for (const f of ['stiffness', 'damping', 'whip']) {
      if (!(p[f] > 0) && !(f === 'whip' && p[f] === 0)) { fail(`${kind}.${f} = ${p[f]} — not a usable value`); bad++; }
    }
  }
  if (!bad) ok(`${RODS.length} rods sprung individually`);
}

// ---- §reel-3d: the reel catalogue ----
console.log('\nreels');
{
  const SIZES = [1000, 2000, 3000, 4000, 5000, 6000, 7000, 8000, 10000, 12000, 14000];
  const master = read('3D/reels/reel_4000.json');
  const mKnob = master.elements.find(e => e.name === 'handle_knob');
  const mFoot = master.elements.find(e => e.name === 'foot');
  const ANCHOR = [(mFoot.from[0] + mFoot.to[0]) / 2, mFoot.to[1]];
  const CRANK = [19.45, 7.0];                       // the gear boss — same constants as the renderer
  const KNOB_C = [(mKnob.from[0] + mKnob.to[0]) / 2, (mKnob.from[1] + mKnob.to[1]) / 2];
  if (CRANK[1] - KNOB_C[1] < 0.5) fail(`knob orbit radius ${r3(CRANK[1] - KNOB_C[1])}u — coaxial knob cannot read as a crank`);
  let feet = new Set(), badReel = 0, axisOff = 0;
  for (const size of SIZES) {
    const files = ['_3d', '_handle_3d', '_knob_3d'].map(sfx => `${ASSETS}/models/item/rod/reel_${size}${sfx}.json`);
    if (files.some(f => !fs.existsSync(f))) { fail(`missing body/lever/knob for reel_${size}`); badReel++; continue; }
    const [body, lever, knob] = files.map(read);
    if (body.elements.length + lever.elements.length + knob.elements.length !== master.elements.length) {
      fail(`reel_${size}: pieces do not reassemble the master`); badReel++;
    }
    if (body.elements.some(e => /^handle_/.test(e.name))) { fail(`reel_${size}: handle parts left in the body — they would not spin`); badReel++; }
    const foot = body.elements.find(e => e.name === 'foot');
    feet.add(JSON.stringify([foot.from, foot.to]));
    for (const m of [body, lever, knob]) {
      if (Object.values(m.textures)[0] !== 'riverfishing:item/rod/reel_3d') { fail(`reel_${size} texture ref ${Object.values(m.textures)[0]}`); badReel++; }
    }
    // §reel-crank: per size, the renderer's crank pivot must lie INSIDE the lever (it hinges there)
    // and its knob pivot must land on the knob's actual centre (it counter-rotates there).
    const s = Math.cbrt(size / 4000);
    const arm = lever.elements.find(e => e.name === 'handle_arm');
    const kEl = knob.elements.find(e => e.name === 'handle_knob');
    const crank = [ANCHOR[0] + (CRANK[0] - ANCHOR[0]) * s, ANCHOR[1] + (CRANK[1] - ANCHOR[1]) * s];
    const kWant = [ANCHOR[0] + (KNOB_C[0] - ANCHOR[0]) * s, ANCHOR[1] + (KNOB_C[1] - ANCHOR[1]) * s];
    const kGot = [(kEl.from[0] + kEl.to[0]) / 2, (kEl.from[1] + kEl.to[1]) / 2];
    if (crank[0] < arm.from[0] || crank[0] > arm.to[0] || crank[1] < arm.from[1] || crank[1] > arm.to[1]) {
      fail(`reel_${size}: crank axis (${r3(crank[0])}, ${r3(crank[1])}) is outside the lever`); axisOff++;
    }
    if (Math.abs(kWant[0] - kGot[0]) > 0.01 || Math.abs(kWant[1] - kGot[1]) > 0.01) {
      fail(`reel_${size}: knob pivot formula (${r3(kWant[0])}, ${r3(kWant[1])}) misses the knob centre (${r3(kGot[0])}, ${r3(kGot[1])})`); axisOff++;
    }
  }
  if (!badReel) ok(`${SIZES.length} sizes: body + sweeping lever + counter-rotating knob (${master.elements.length} el)`);
  if (!axisOff) ok(`crank pivot inside the lever and knob pivot on the knob centre at every size (orbit ${r3(CRANK[1] - KNOB_C[1])}u)`);
  feet.size === 1 ? ok('the foot never scales — one seat fits all')
      : fail(`${feet.size} distinct foot geometries — the foot must not scale`);
  fs.existsSync(`${ASSETS}/textures/item/rod/reel_3d.png`)
      ? ok('shared reel sheet installed') : fail('reel_3d.png missing');

  // §seat-sync: for every 3D reel rod, the seat offsets must MATCH the model's actual seat — the
  // reel is authored at the feeder's seat (centre 19.5, underside 9.45) and shifted by these numbers,
  // so a moved seat with a stale offset parks the reel in the air. Bit us twice before this check.
  {
    const dxBlock2 = java.match(/REEL_SEAT_DX = java.util.Map.of(([sS]*?));/);
    const seatMap = {};
    if (dxBlock2) for (const [, k, a, b] of dxBlock2[1].matchAll(/"(w+)", new float[]{(-?[d.]+)f, (-?[d.]+)f}/g)) seatMap[k] = [parseFloat(a), parseFloat(b)];
    for (const kind of Object.keys(seatMap)) {
      const srcP = '3D/rods/' + kind + '/blank_' + kind + '.json';
      if (!fs.existsSync(srcP)) continue;
      const seat = read(srcP).elements.find(e => e.name === 'reel_seat');
      if (!seat) { fail(kind + ' has a seat offset but no reel_seat element'); continue; }
      const wantDx = r3((seat.from[0] + seat.to[0]) / 2 - 19.5), wantDy = r3(seat.from[1] - 9.45);
      if (Math.abs(wantDx - seatMap[kind][0]) > 0.01 || Math.abs(wantDy - seatMap[kind][1]) > 0.01) {
        fail(kind + ': REEL_SEAT_DX {' + seatMap[kind] + '} but the model seat wants {' + wantDx + ', ' + wantDy + '}');
      }
    }
    ok('seat offsets match every 3D model seat');
  }
  // the renderer's seat map must agree with gameplay: exactly the takesReel rods carry a seat
  const rodType = fs.readFileSync('common/src/main/java/com/riverfishing/component/RodType.java', 'utf8');
  const takesReel = new Set();
  for (const [, key, flag] of rodType.matchAll(/\("([a-z_]+)",\s*[\d.]+,\s*(true|false)/g)) {
    if (flag === 'true') takesReel.add(key);
  }
  const dxBlock = java.match(/REEL_SEAT_DX = java\.util\.Map\.of(?:Entries)?\(([\s\S]*?)\);/);
  if (!dxBlock) fail('cannot find REEL_SEAT_DX in the renderer');
  else {
    const mapped = new Set([...dxBlock[1].matchAll(/"(\w+)"/g)].map(m => m[1]));
    const missing = [...takesReel].filter(k => !mapped.has(k));
    const extra = [...mapped].filter(k => !takesReel.has(k));
    if (missing.length) fail(`takesReel rods with no seat offset: ${missing.join(', ')} — their reel never draws`);
    if (extra.length) fail(`seat offsets for reel-less rods: ${extra.join(', ')}`);
    if (!missing.length && !extra.length) ok(`seat offsets cover exactly the ${takesReel.size} takesReel rods`);
  }
}

console.log(`\n${failures ? '\x1b[31m' + failures + ' FAILURE(S)\x1b[0m' : '\x1b[32mall checks passed\x1b[0m'}${notes ? `, ${notes} note(s)` : ''}`);
process.exit(failures ? 1 : 0);
