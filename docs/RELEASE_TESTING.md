# Pre-release testing

One procedure for every release, across every version the mod ships on. Written for 0.6.0 but the shape
does not change: only §6 (what is new this release) gets rewritten each time.

The point of the ordering is that **the expensive pass runs once**. Eight instances × a full gameplay
sweep is a day of work and nobody does it twice. Instead: machine checks first, a cheap smoke on all
eight, one deep pass on the primary instance, then only the handful of things that genuinely differ per
version.

**Eight instances**, all in PrismLauncher:

| MC | Loaders |
|---|---|
| 1.20.1 | Fabric · Forge |
| 1.21.1 | Fabric · NeoForge — **primary, the deep pass happens here** |
| 26.1.2 | Fabric · NeoForge |
| 26.2 | Fabric · NeoForge |

---

## 1. Machine gates — run before touching the game

From the repo root. All four must be clean; each one has caught a real shipped-quality bug.

```bash
python tools/check_lang.py && python tools/check_wiki_vanilla.py && python tools/wiki_anchors.py --self-test
```

| Check | Passes when |
|---|---|
| `check_lang.py` | every locale has en_us's key set, the same placeholders, and no species named two ways |
| `check_wiki_vanilla.py` | the translated wiki uses Mojang's own names for vanilla items |
| `wiki_anchors.py --self-test` | every cross-page anchor in the wiki resolves |
| `gradlew build` on each branch | all eight jars build |

Then confirm the jars actually contain what you think:

```bash
python tools/verify_release_jars.py
```

## 2. Install — one jar per instance, nothing else

The installer disables the previous version by renaming it to `.jar.disabled` rather than deleting it,
so a mid-test rollback is one rename.

**Before launching anything, check each instance has exactly ONE `riverfishing-*.jar` and exactly ONE
`architectury-*.jar`.** Two of either is a duplicate mod id: Forge and NeoForge refuse to launch,
Fabric picks one arbitrarily and you spend an hour debugging the wrong build. `verify_release_jars.py`
reports this.

## 3. Smoke, on all eight — about 5 minutes each

The goal is only to prove the build loads and its content registered. Creative, superflat, no world
generation needed.

1. **It launches.** No crash, no "duplicate mods", no missing-dependency screen.
2. **The log is clean.** Search `latest.log` for `riverfishing` — no `ERROR`, no `Exception`, no
   `Unknown recipe`, no missing-texture warnings for our namespace.
3. **The creative tab is populated.** One River Fishing tab, every item has an icon — no black-and-magenta
   squares, no `item.riverfishing.…` raw keys.
4. **Language.** Switch to Русский, then Українська. The tab, the rods and the fish are translated in
   both; nothing shows a raw key.
5. **Journal opens.** All six tabs render. The Guides shelf has **13** entries and the last one is
   Discord.
6. **JEI/recipe book knows us** (where JEI is installed): search "rod", the 13 blanks appear with recipes.
7. **Quit cleanly.** No hang on world save.

A failure here stops the release for that version — it is a build problem, not a balance problem.

## 4. The deep pass — once, on 1.21.1 Fabric

Survival, a fresh world, a real river. This is the pass that decides whether the release is good, and
it only tells you the truth in survival.

1. **Bait and a first rod.** Dig for worms, craft a stick rod, a line, a hook. Cast. Catch something.
2. **The assembly hint.** Try to cast a rod with no line: it must name the missing part. Then with a line
   but no rig. Then a reeled blank with no reel — it must say *reel*, not *line*.
3. **Open the rod's screen while it is in your hand, change tackle, close, cast.** The change must
   survive. (This is the §live-rod bug: the screen used to write into a detached copy.)
4. **The fight.** Hook something over 1 kg. The rod must visibly bend, and the bend must follow the
   tension, not the clock. Let it run without cranking — the tackle must load anyway. Fight a long one and
   feel it tire. Then hook something small and confirm it does *not* fight like a monster.
5. **Break a line on purpose.** Crank a big fish on 0.10 mono. It should break, and the message should
   say so.
6. **The Tackle Station.** Right-click a Fishing Stall empty-handed. Tie a rig at each weight step; tie a
   lure; dye one. Check the tooltip carries the maker's name and the grams. Take the tackle out and
   confirm the materials were consumed.
7. **Weight actually matters** — the headline of this release, so prove it three ways:
   - a lure below the blank's window gives a short cast and a longer wait;
   - 2.5× over the window cracks the blank on the cast;
   - a 160 g+ lure stops producing small fish.
8. **The fisherman.** Find or cure one. Every level must offer **3** trades; level 1 must include one
   simple fish; every rod on offer must be fully assembled and castable as bought.
9. **Progression.** Earn a level, buy a skill, complete a quest, claim its reward, get an advancement.
10. **Blocks.** Rod pod + alarm, bait trap, a worm farm, an aquarium with a fish in it.
11. **Sea and ice**, if the world allows: one ocean fish on a sea blank, one hole drilled and one fish
    under the ice.
12. **Restart the world.** Journal records, angler level, skills, stocking and tackle NBT all survive.

## 5. Dialect spot checks — only where the ports genuinely differ

Everything above is loader-independent *except* these, which is why they get checked per version instead
of repeating §4 eight times. Each line is a place where the code actually forks.

| Instance | Check | Why it is here |
|---|---|---|
| Forge 1.20.1 · NeoForge 1.21.1/26.x | the assembled rod's **layered icon** shows reel/line/rig | Forge uses a BEWLR mixin, NeoForge an event — two different implementations |
| all four Forge/NeoForge | **rod bends** during a fight | same fork as above |
| 26.1.2 vs 26.2, both loaders | **the cast line renders** for you *and* for a second player | 26.1 is immediate-mode rendering, 26.2 is submit/collect — the only architectural difference between them |
| 1.20.1 both | **dyed lure keeps its colour** after a reload | 1.20.1 stores dye in `display.color` NBT, later versions in a component |
| 1.20.1 both | **tackle NBT survives a world reload** | the whole component→NBT seam is 1.20.1-only code |
| 26.x all four | **breaking a block still drops bait** | Architectury dropped the `xp` parameter from `BlockEvent.BREAK` in 21.x |
| 26.2 both | **the journal reopens on tab switch** | `setScreen` → `setScreenAndShow` |
| 26.2 both | **the Tackle Station's red-dye ghost icon** appears | `Items.RED_DYE` → `Items.DYE.red()` |
| Fabric ×4 | **the fish finder's HUD line** shows | `options.hideGui` → `gui.hud.isHidden()` on 26.2 |

## 6. What 0.8.0 changed — test these before anything else

Everything below is new or rewritten this release. A pass here is what the release gate is actually
waiting on.

**Groundbait, which is the whole headline.** The old four jars are gone; anything that still mentions
them is a bug.

- Wheat Seeds + Bread → **2 × Base Groundbait**. The fisherman still sells it.
- Base + up to 8 more items in the grid makes a mix. **Base alone is craftable back into a mix; a
  finished mix is not.** One SLOT is one item — put a stack of 64 in a slot and it still counts once.
- **Overfeeding is impossible.** Throw twenty jars at one spot and the bite never gets worse.
- **Last mix in wins.** Feed a spot with mix A, then mix B: B takes over outright, no averaging.
- **The ★ grind claim.** Feed pure boilies and pure dust in the same water and confirm the coarse table
  brings bigger fish and the dust brings bleak.
- **A loaded feeder cage no longer counts as feed.** This was the bug: a cage scored the swim at the
  *cast surface*, flat 0.5 freshness at the finest grind, usually beating the mix actually on the bottom.
  Fish a fed swim with a loaded feeder and confirm the mix still decides.

**One fillet.** The knife makes **Raw Fish Fillet** only, 200 g a piece, no crouch modifier. Confirm it
still smokes/smelts/campfires into Cooked Fish Fillet, still works as saltwater bait, still goes in a
mix — and that no crafting grid turns a whole fish into anything without the knife.

**The electrofisher (creative only).** Cull a species, close the screen, **reopen at the same spot** and
confirm the fish is still listed, struck through, in **This water** — that exact round trip is what was
broken. Then put a species in that was never there, including one the water plainly does not suit, and
confirm it turns up. Check the family column counts.

**Hook picking at the bench.** A button either side of the size, the tied hook matches the choice, and
the iron is billed.

### Still known and accepted — confirm, do not chase

- **Hook link, balance and blade size do nothing.** Written to NBT and shown on the tooltip, read by
  nothing. The tooltip and the wiki both say so.
- **The dynamic fish market is absent on 26.x.** Lost in the 0.5.0 port and not yet restored. Prices are
  static there; on 1.20.1 and 1.21.1 they move.
- **There is no config file.** Difficulty is fixed at the *realism* preset in code.
- **Hand-crafted lures weigh 0 g.** Deliberate — it is what makes the bench worth using.
- **Old worlds keep their fed spots as unfed.** Pre-0.8.0 feed zones are dropped on load rather than
  migrated — there is no sane translation from four categories to a grind/richness pair.

## 7. Release gate

Ship only when all of these hold:

- [ ] §1 machine gates clean, all eight jars built from a **pushed** commit
- [ ] §3 smoke green on all eight instances
- [ ] §4 deep pass green on the primary
- [ ] §5 spot checks green
- [ ] §6 confirmed as documented, nothing new hiding
- [ ] the patchnote in `docs/CHANGELOG.md` matches what actually shipped
- [ ] **`updates.json`: bump `latest` LAST.** It is what the in-game checker compares against, so bumping
      it announces the release to every player. Do it after the files are live, not before — and add the
      `26.2` key the first time 26.2 ships.
- [ ] Discord post, with the wiki link

**A failure in §3 blocks that version only. A failure in §4 blocks the whole release** — the deep pass
covers loader-independent code, so a bug there exists everywhere.
