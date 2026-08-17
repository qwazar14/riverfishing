// Installs a hand-authored 3D rod blank from 3D/rods/<kind>/ into the mod assets.
//
//   node tools/install_rod_model.js <kind> [fallbackTexture]
//
// It rewrites texture VALUES only. Slot keys stay as authored — the stick uses slot "1" and its faces
// reference #1, so hardcoding "0" left every face pointing at a slot that did not exist. Elements are
// copied untouched: these files get edited by hand in Blockbench and the generators must not win.
const fs = require('fs'), path = require('path');

const kind = process.argv[2];
const fallback = process.argv[3];

// Segmented rods are installed by the splitter, which writes s0..s4 — this script writes the WHOLE
// rod as s0 and would flatten the chain back into one rigid piece.
const SEGMENTED = new Set(['feeder', 'pole', 'bamboo', 'spinning', 'ultralight', 'surf']);
if (SEGMENTED.has(kind)) {
  console.error(`${kind} is a segmented (bone-chain) rod — run: node tools/split_rod.js ${kind}`);
  process.exit(2);
}
const src = `3D/rods/${kind}/blank_${kind}.json`;
const png = `3D/rods/${kind}/texture.png`;
const modelOut = `common/src/main/resources/assets/riverfishing/models/item/rod/blank_${kind}_s0.json`;
const texName = `blank_${kind}_3d`;
const texOut = `common/src/main/resources/assets/riverfishing/textures/item/rod/${texName}.png`;

if (!fs.existsSync(src)) throw new Error(`no source model at ${src}`);
const m = JSON.parse(fs.readFileSync(src, 'utf8'));

let ref;
if (fs.existsSync(png)) {
  fs.copyFileSync(png, texOut);
  const b = fs.readFileSync(texOut);
  if (b.slice(0, 8).toString('hex') !== '89504e470d0a1a0a') throw new Error(`${png} is not a PNG`);
  ref = `riverfishing:item/rod/${texName}`;
  var note = `painted ${b.readUInt32BE(16)}x${b.readUInt32BE(20)}`;
} else {
  if (!fallback) throw new Error(`${kind} has no texture.png and no fallback texture was given`);
  ref = fallback;
  var note = 'placeholder, not painted yet';
}

const slots = Object.keys(m.textures);
m.textures = Object.fromEntries(slots.map(k => [k, ref]));

const compact = o => JSON.stringify(o, null, '\t')
  .replace(/\[\n\t+(-?[\d.]+,\n\t+)*-?[\d.]+\n\t+\]/g, x => x.replace(/\s+/g, ' ').replace(/\[ /, '[').replace(/ \]/, ']'));
fs.mkdirSync(path.dirname(modelOut), { recursive: true });
fs.writeFileSync(modelOut, compact(m) + '\n');

// read back: a truncated write here is invisible until the model renders wrong in game
const back = JSON.parse(fs.readFileSync(modelOut, 'utf8'));
if (back.elements.length !== m.elements.length) {
  throw new Error(`wrote ${m.elements.length} elements, file has ${back.elements.length}`);
}
// every face must point at a slot that exists, which is exactly what broke the stick
const declared = new Set(Object.keys(back.textures).map(k => '#' + k));
for (const e of back.elements) {
  for (const [f, v] of Object.entries(e.faces)) {
    if (!declared.has(v.texture)) {
      throw new Error(`${e.name}.${f} references ${v.texture}, but the model only declares ${[...declared].join(' ')}`);
    }
  }
}

console.log(`${kind.padEnd(11)} ${back.elements.length} el, slots ${slots.join('/')} -> ${ref}  (${note})`);
