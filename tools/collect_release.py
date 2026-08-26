# -*- coding: utf-8 -*-
"""Collect every shippable jar into one folder, renamed unambiguously, with an upload sheet.

    python tools/collect_release.py

Output: build/release-<version>/ — gitignored, so nothing here pollutes the repo.

The rename is the point. The build names the 1.20.1 and the 1.21.1 Fabric jars *identically*
(`riverfishing-fabric-0.6.0.jar`), because each lives under its own branch and never meets the other.
Put all eight in one folder to upload and those two collide — and uploading the wrong one to the wrong
game version is a broken release that looks fine on the page. Everything here carries `+<mc version>`,
the way the Stonecutter builds already do.

The upload sheet is generated FROM the jars, not typed: loader, game versions, java, environment and
each dependency with its required/optional flag are read out of the metadata that will actually ship.
If the sheet and the jar disagree, the sheet is wrong by construction, not the jar.

`manifest.json` is the same extraction in machine-readable form, and it is what tools/upload_release.py
publishes from — that script never opens a jar. Anything that wants to know what is in a jar asks
read_meta() here, so there is exactly one answer per file rather than one per consumer.
"""
import glob, hashlib, io, json, os, re, shutil, sys, tomllib, zipfile

HOME = os.path.expanduser("~")
REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def mod_version():
    """The version the build actually stamps on the jars.

    This used to be a literal here and in verify_release_jars.py, which meant that the morning after
    every version bump both scripts went looking for the *previous* release's files and reported the
    new build as "NOT BUILT". gradle.properties is what the build reads; read the same thing.
    """
    p = io.open(os.path.join(REPO, "gradle.properties"), encoding="utf-8").read()
    return re.search(r"^mod_version\s*=\s*(\S+)", p, re.M).group(1)


VERSION = mod_version()
WT = os.path.join(HOME, "wt")
OUT = os.path.join(REPO, "build", "release-" + VERSION)

# Loader and runtime ids. Real dependencies of the mod, but not *related projects* on either store —
# nobody publishes "minecraft" as a CurseForge relation.
PLATFORM_IDS = ("minecraft", "java", "fabricloader", "forge", "neoforge")

# (source jar, minecraft version) — three source trees, one per Minecraft line.
SOURCES = [
    (WT + "/rf1201/fabric/build/libs/riverfishing-fabric-%s.jar" % VERSION, "1.20.1"),
    (WT + "/rf1201/forge/build/libs/riverfishing-forge-%s.jar" % VERSION, "1.20.1"),
    (REPO + "/fabric/build/libs/riverfishing-fabric-%s.jar" % VERSION, "1.21.1"),
    (REPO + "/neoforge/build/libs/riverfishing-neoforge-%s.jar" % VERSION, "1.21.1"),
    (WT + "/rf26/fabric/versions/26.1.2/build/libs/riverfishing-fabric-%s+26.1.2.jar" % VERSION, "26.1.2"),
    (WT + "/rf26/neoforge/versions/26.1.2/build/libs/riverfishing-neoforge-%s+26.1.2.jar" % VERSION, "26.1.2"),
    (WT + "/rf26/fabric/versions/26.2/build/libs/riverfishing-fabric-%s+26.2.jar" % VERSION, "26.2"),
    (WT + "/rf26/neoforge/versions/26.2/build/libs/riverfishing-neoforge-%s+26.2.jar" % VERSION, "26.2"),
]
DOCS = [("docs/patchnotes/%s.md" % VERSION, "CHANGELOG-en.md"),
        ("docs/CHANGELOG.md", "CHANGELOG-en-uk-ru.md")]


def loader_of(name):
    for l in ("fabric", "neoforge", "forge"):
        if "-%s-" % l in name:
            return l
    return "?"


def read_meta(jar):
    """Everything the two stores need to know about a jar, out of the jar that ships.

    Java and environment come back None for Forge and NeoForge: mods.toml has a field for neither. The
    `side` on a mods.toml dependency says which side *that dependency* is needed on, not which sides the
    mod runs on, so it is not the answer either. main() fills both in from the Fabric jar built from the
    same source tree for the same Minecraft version.
    """
    with zipfile.ZipFile(jar) as z:
        names = set(z.namelist())
        if "fabric.mod.json" in names:
            d = json.loads(z.read("fabric.mod.json"))
            depends = d.get("depends", {})
            deps = [(k, v, "required") for k, v in depends.items()]
            deps += [(k, v, "optional") for k, v in d.get("recommends", {}).items()]
            java = re.search(r"\d+", depends.get("java", ""))
            env = d.get("environment", "*")
            return {"loader": "Fabric", "mc_range": depends.get("minecraft", "?"),
                    "java": int(java.group()) if java else None,
                    # fabric.mod.json spells "runs on both sides" as "*".
                    "environment": "both" if env == "*" else env, "deps": deps}
        for meta in ("META-INF/neoforge.mods.toml", "META-INF/mods.toml"):
            if meta in names:
                t = tomllib.loads(z.read(meta).decode("utf-8"))
                key = list(t.get("dependencies", {}))
                deps = []
                for dep in (t["dependencies"][key[0]] if key else []):
                    req = dep.get("type") or ("required" if dep.get("mandatory") else "optional")
                    deps.append((dep["modId"], dep.get("versionRange", "*"), req))
                mc = next((v for m, v, _ in deps if m == "minecraft"), "?")
                return {"loader": "NeoForge" if "neoforge" in meta else "Forge", "mc_range": mc,
                        "java": None, "environment": None, "deps": deps}
    return {"loader": "?", "mc_range": "?", "java": None, "environment": None, "deps": []}


def main():
    missing = [s for s, _ in SOURCES if not os.path.exists(s)]
    if missing:
        for m in missing:
            print("NOT BUILT: %s" % m)
        sys.exit("build every branch first — nothing collected")

    if os.path.isdir(OUT):
        shutil.rmtree(OUT)
    os.makedirs(OUT)

    rows, sums = [], []
    for src, mc in SOURCES:
        loader = loader_of(os.path.basename(src))
        dst_name = "riverfishing-%s-%s+%s.jar" % (loader, VERSION, mc)
        dst = os.path.join(OUT, dst_name)
        shutil.copy2(src, dst)
        digest = hashlib.sha256(io.open(dst, "rb").read()).hexdigest()
        sums.append("%s  %s" % (digest, dst_name))
        m = read_meta(dst)
        m.update(file=dst_name, minecraft=mc, sha256=digest, size=os.path.getsize(dst))
        rows.append(m)
        print("  %-44s %6.1f MB  %s %s" % (dst_name, m["size"] / 1e6, m["loader"], mc))

    # The Forge and NeoForge jars borrow java and environment from the Fabric jar for the same Minecraft
    # version: same common module, same source tree, so the answer is the same one and it is still read
    # out of shipping metadata rather than typed here.
    for m in rows:
        for field in ("java", "environment"):
            if m[field] is None:
                m[field] = next((o[field] for o in rows if o["minecraft"] == m["minecraft"]
                                 and o[field] is not None), None)
            if m[field] is None:
                sys.exit("no %s declared for %s, and no sibling jar to take it from" % (field, m["file"]))

    for rel, name in DOCS:
        p = os.path.join(REPO, rel)
        if os.path.exists(p):
            shutil.copy2(p, os.path.join(OUT, name))
            print("  %-44s (%s)" % (name, rel))

    io.open(os.path.join(OUT, "SHA256SUMS.txt"), "w", encoding="utf-8", newline="\n").write(
        "\n".join(sums) + "\n")

    # The machine-readable twin of the sheet below: tools/upload_release.py publishes from this and never
    # opens a jar itself. One extraction feeds both, so the store pages and the sheet a human reads while
    # checking them cannot describe the same file differently.
    manifest = {"version": VERSION, "generated_by": "tools/collect_release.py",
                "changelog": DOCS[0][1],
                "files": [{"file": m["file"], "loader": m["loader"], "minecraft": m["minecraft"],
                           "mc_range": m["mc_range"], "java": m["java"],
                           "environment": m["environment"], "sha256": m["sha256"],
                           "dependencies": [{"mod_id": i, "range": r, "type": t}
                                            for i, r, t in m["deps"] if i not in PLATFORM_IDS]}
                          for m in rows]}
    io.open(os.path.join(OUT, "manifest.json"), "w", encoding="utf-8", newline="\n").write(
        json.dumps(manifest, indent=2) + "\n")

    # The sheet: one row per upload, so nothing has to be remembered while clicking through a form.
    L = ["# River Fishing %s — upload sheet" % VERSION, "",
         "Generated by `tools/collect_release.py` from the jars in this folder. Loader, game version and",
         "dependencies are read out of each jar's own metadata, so this cannot disagree with what ships.", "",
         "Paste `CHANGELOG-en.md` as the changelog on CurseForge and Modrinth.", "",
         "| File | Loader | Game version | Java | Env | Required | Optional |",
         "|---|---|---|---|---|---|---|"]
    for m in rows:
        deps = [(i, r) for i, _, r in m["deps"] if i not in PLATFORM_IDS]
        req = ", ".join(i for i, r in deps if r == "required") or "—"
        opt = ", ".join(i for i, r in deps if r != "required") or "—"
        L.append("| `%s` | %s | %s | %s | %s | %s | %s |"
                 % (m["file"], m["loader"], m["minecraft"], m["java"], m["environment"], req, opt))
    L += ["", "## Order of operations", "",
          "1. `python tools/upload_release.py --release-type release` — a dry run that uploads nothing.",
          "   Read what it prints, then re-run it with `--confirm` to publish all eight files.",
          "2. Post the changelog.",
          "3. Announce in Discord with the wiki link: https://qwazar14.github.io/riverfishing/",
          "4. **Last:** bump `latest` in `updates.json` on the `mc-1.21.1` branch and push. That is what",
          "   tells every running client an update exists, so it goes after the files are actually live.",
          "   Add the `26.2` key at the same time — the feed has never had one.", "",
          "## Verify a download", "",
          "`SHA256SUMS.txt` holds the hash of every file as uploaded. CurseForge and Modrinth both show a",
          "SHA-256 per file, so they can be compared without downloading anything.", ""]
    io.open(os.path.join(OUT, "UPLOAD.md"), "w", encoding="utf-8", newline="\n").write("\n".join(L))
    print("\n-> %s  (%d jars, %d files)" % (OUT, len(rows), len(os.listdir(OUT))))
    return 0


if __name__ == "__main__":
    sys.exit(main())
