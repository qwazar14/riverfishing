# Setting up River Fishing, and drawing for it

For anyone joining the mod — the environment first, then what the art has to hit.

Everything about the art below is **measured from the 122 sprites already in the mod**, not asserted as
taste. If you match these numbers your work will sit beside the existing items without looking imported.

---

## 1. The environment

You need a JDK and nothing else — Gradle downloads the rest itself. Which JDK depends on the Minecraft
line, because they do not overlap:

| Branch | Minecraft | Loaders | JDK |
|---|---|---|---|
| `mc-1.21.1` | 1.21.1 | Fabric, NeoForge | **21** |
| `mc-1.20.1` | 1.20.1 | Fabric, Forge | **17** |
| `mc-26.1` | 26.1.2 and 26.2 | Fabric, NeoForge | **25** |

Start on **`mc-1.21.1`** unless you have a reason not to: it is where features land first, and the other
two are ported from it.

Get a JDK from [Adoptium](https://adoptium.net/) (Temurin). You do not need to install it system-wide —
pointing `JAVA_HOME` at it for the one command is enough.

```bash
git clone https://github.com/qwazar14/riverfishing.git
cd riverfishing
git checkout mc-1.21.1
```

Build:

```bash
./gradlew build
```

On Windows use `gradlew.bat build`. The first run downloads Minecraft and decompiles it — expect several
minutes and a few GB. After that a build is under a minute.

The jars land in:

```
fabric/build/libs/riverfishing-fabric-<version>.jar
neoforge/build/libs/riverfishing-neoforge-<version>.jar
```

### Seeing your work in game

Drop the jar into a profile's `mods/` folder together with its dependencies:

- **Architectury API** — required on every loader
- **Fabric API** — required on Fabric
- JEI and Jade are optional

Any launcher works. There is no dev-client task you need to learn; a normal profile is faster to reason
about and it is what players actually run.

**If you are only doing textures you do not need to build at all.** Resource packs load over the mod:
put your PNGs at the same paths under a pack's `assets/riverfishing/…`, and press F3+T in game to
reload. That loop is seconds long instead of minutes, and it is the right way to iterate.

---

## 2. Where the art lives

```
common/src/main/resources/assets/riverfishing/textures/item/     items — 122 sprites
common/src/main/resources/assets/riverfishing/textures/gui/journal/fish/   journal illustrations
common/src/main/resources/assets/riverfishing/models/item/       the model each sprite hangs on
```

An item's file name is its id: `boilie.png` is `riverfishing:boilie`. Replace the PNG and the item
changes — no code, no registry, nothing to rebuild but the pack.

---

## 3. What the sprites actually are

Measured across all 122 item textures in the mod today:

| | |
|---|---|
| Size | **16 × 16**, every one, no exceptions |
| Format | PNG, RGBA, 8 bit |
| Distinct opaque colours | **median 6**, maximum 14 |
| Painted area | **median 32%** of the square — most of the tile is transparent |

Those three numbers are the whole house style, and the middle one is the one AI output will miss by an
order of magnitude. A generated 1024×1024 render downsampled to 16×16 arrives with hundreds of colours
and reads as mud at the size it is actually seen. **Six colours is not a limitation, it is the look.**

So the workflow that works:

1. Generate the reference at whatever size you like — composition, silhouette, colour mood.
2. **Redraw it at 16×16 by hand**, choosing 5–8 colours deliberately.
3. Check it at 100% zoom, not zoomed in. That is the size a player sees.

A reference that never becomes a hand-placed 16×16 is not usable, however good it looks large. That is
not a rule about AI — the same is true of a photograph.

### Silhouette first

At 16 pixels an item is recognised by its outline before any detail. Held items are also drawn small in
a hotbar corner. If two items would share an outline, change the shape, not the colour.

### Tint layers

Some items are dyed at runtime — groundbait, tackle boxes, lures. Those have a **second sprite**, e.g.
`groundbait_powder_tint.png`, declared as `layer1` in the model:

```json
{
  "parent": "minecraft:item/generated",
  "textures": {
    "layer0": "riverfishing:item/groundbait_powder",
    "layer1": "riverfishing:item/groundbait_powder_tint"
  }
}
```

The tint layer is drawn **multiplied by a colour the game computes**, so it must be near-white and
single-hue — that is why those files measure one distinct colour. Paint the shape there, never the
colour; the colour arrives at runtime.

---

## 4. Handing work over

Pull requests against the branch you built from. If you would rather not use git, a zip of PNGs at their
correct paths is genuinely fine — say which item each one replaces.

Worth knowing before you spend time: the mod is **source-available, not open source** (`LICENSE.txt`).
Reading it, building it and sending changes back as pull requests is explicitly allowed; redistributing
the files outside the official pages is not. Contributions are credited in the release notes.

If something in here does not match what you find in the repo, the repo is right and this file is stale —
say so and it gets fixed.
