# -*- coding: utf-8 -*-
"""Check the built jars, and the instances they are installed into, before a release.

    python tools/verify_release_jars.py            (check)
    python tools/verify_release_jars.py --install  (disable old versions, copy the new ones in)

Two things this catches that a successful `gradlew build` does not:

  * **A jar missing content.** The build is green whether or not uk_ua.json ended up inside; only reading
    the finished jar proves it shipped. Same for the Discord link, which lives in three different
    metadata formats.
  * **Two copies of a mod in one instance.** Forge and NeoForge refuse to launch on a duplicate mod id;
    Fabric picks one arbitrarily, which means an hour spent debugging the build you did not install. This
    has already happened here — one instance had three riverfishing jars and two Architectury jars.

--install disables the previous version by renaming it to `.jar.disabled` instead of deleting it, so a
rollback mid-test is one rename. It never touches a jar that is not ours.
"""
import glob, io, json, os, shutil, sys, zipfile

from collect_release import mod_version

VERSION = mod_version()
HOME = os.path.expanduser("~")
INST = os.path.join(HOME, "AppData/Roaming/PrismLauncher/instances")
REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
WT = os.path.join(HOME, "wt")
DISCORD = "discord.gg/Kk2nKvsuRh"

# instance name -> the jar that belongs in it. Three source trees: this repo is 1.21.1, the two ports
# live in their own worktrees.
TARGETS = [
    ("1.20.1 Fabric",   WT + "/rf1201/fabric/build/libs/riverfishing-fabric-%s.jar"),
    ("1.20.1 Forge",    WT + "/rf1201/forge/build/libs/riverfishing-forge-%s.jar"),
    # Three of these read "Fabric 1.21.1" and were never a folder on disk, so the tool quietly skipped
    # three of the eight instances and still printed a per-instance heading for each. Every name here is
    # "<mc> <loader>", which is how PrismLauncher actually has them — checked against the folder list.
    ("1.21.1 Fabric",   REPO + "/fabric/build/libs/riverfishing-fabric-%s.jar"),
    ("1.21.1 NeoForge", REPO + "/neoforge/build/libs/riverfishing-neoforge-%s.jar"),
    ("26.1.2 Fabric",   WT + "/rf26/fabric/versions/26.1.2/build/libs/riverfishing-fabric-%s+26.1.2.jar"),
    ("26.1.2 NeoForge", WT + "/rf26/neoforge/versions/26.1.2/build/libs/riverfishing-neoforge-%s+26.1.2.jar"),
    ("26.2 Fabric",     WT + "/rf26/fabric/versions/26.2/build/libs/riverfishing-fabric-%s+26.2.jar"),
    ("26.2 NeoForge",   WT + "/rf26/neoforge/versions/26.2/build/libs/riverfishing-neoforge-%s+26.2.jar"),
]

LOCALES = ("en_us", "ru_ru", "uk_ua")


def mods_dir(inst):
    for sub in ("minecraft", ".minecraft"):
        d = os.path.join(INST, inst, sub, "mods")
        if os.path.isdir(d):
            return d
    return None


def inspect(jar):
    """What actually shipped inside: locales, key count, and the Discord link in the metadata."""
    out = {"locales": [], "keys": 0, "discord": False}
    with zipfile.ZipFile(jar) as z:
        names = set(z.namelist())
        for loc in LOCALES:
            p = "assets/riverfishing/lang/%s.json" % loc
            if p in names:
                out["locales"].append(loc)
                if loc == "en_us":
                    out["keys"] = len(json.loads(z.read(p).decode("utf-8")))
        for meta in ("fabric.mod.json", "META-INF/mods.toml", "META-INF/neoforge.mods.toml"):
            if meta in names and DISCORD in z.read(meta).decode("utf-8", "replace"):
                out["discord"] = True
    return out


def main(install):
    bad = 0
    for inst, pat in TARGETS:
        src = pat % VERSION
        mods = mods_dir(inst)
        print("== %s" % inst)
        if mods is None:
            print("   no mods dir under %s" % os.path.join(INST, inst)); bad += 1; continue
        if not os.path.exists(src):
            print("   NOT BUILT: %s" % src); bad += 1; continue

        info = inspect(src)
        miss = [l for l in LOCALES if l not in info["locales"]]
        if miss or not info["discord"]:
            print("   jar content: locales=%s keys=%d discord=%s  <-- %s"
                  % (",".join(info["locales"]), info["keys"], info["discord"],
                     ("missing " + ",".join(miss)) if miss else "no Discord link"))
            bad += 1
        else:
            print("   jar content: 3 locales, %d keys, Discord link present" % info["keys"])

        if install:
            for old in sorted(glob.glob(os.path.join(mods, "riverfishing-*.jar"))):
                if os.path.basename(old) == os.path.basename(src):
                    continue
                dst = old + ".disabled"
                if os.path.exists(dst):
                    os.remove(dst)
                os.rename(old, dst)
                print("   disabled  %s" % os.path.basename(old))
            shutil.copy2(src, os.path.join(mods, os.path.basename(src)))
            print("   installed %s" % os.path.basename(src))

        # A duplicate mod id is a launch failure, so it is worth checking after every install.
        for what in ("riverfishing", "architectury"):
            live = [os.path.basename(p) for p in glob.glob(os.path.join(mods, what + "-*.jar"))]
            if len(live) != 1:
                print("   %d %s jars: %s  <-- duplicate mod id, this instance will not load right"
                      % (len(live), what, ", ".join(live) or "(none)"))
                bad += 1
        stray = glob.glob(os.path.join(mods, "*.jar.duplicate"))
        if stray:
            print("   leftover: %s" % ", ".join(os.path.basename(p) for p in stray))

    print("\n%s" % ("all good" if not bad else "%d problem(s) — see above" % bad))
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main("--install" in sys.argv))
