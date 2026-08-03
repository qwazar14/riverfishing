# -*- coding: utf-8 -*-
"""Publish the eight release jars to CurseForge and Modrinth with the metadata each store actually wants.

    python tools/upload_release.py --release-type release             (dry run — uploads nothing)
    python tools/upload_release.py --release-type release --confirm   (the real thing, asks first)
    python tools/upload_release.py --self-check                       (is the mapping still sane?)

A release here is eight files across two stores, and the fields that go wrong are the ones nobody can
see. A jar uploaded without its loader, its Java version or its dependencies still appears on the page
and still downloads; it just never turns up for anyone filtering, and nothing warns the player that
Architectury is missing until the game refuses to start. 0.6.1 shipped exactly that way — all four
NeoForge versions went up on Modrinth with an empty dependency list. Both pages looked fine.

The two stores model the same facts completely differently, which is the whole reason this exists:

  * **Modrinth** has a field per fact — `loaders`, `game_versions`, `dependencies` — so the mapping is
    nearly direct. It wants one version per file: a version carries a single `game_versions`/`loaders`
    pair that applies to every file beneath it, so folding all eight into one version would advertise the
    26.2 NeoForge jar as a 1.20.1 Fabric download.
  * **CurseForge** has no field for loader, Java version or environment at all. All three ride in
    `gameVersions` beside the Minecraft version, as numeric ids drawn from different *version types*, so
    "Fabric", "Java 21" and "Client" must each be looked up in a table before anything can be sent. A
    name that does not resolve stops the run — dropping it silently is the invisible failure above.

Every fact about a jar comes from `build/release-<version>/manifest.json`, which collect_release.py
extracts from the jars themselves. This script never opens a jar, so it cannot form a second opinion
about what one contains; if the store page and the upload sheet ever disagree, both are wrong together.

Safety, because the other end of this is the public:

  * The dry run is the default. `--confirm` *and* a typed confirmation are the only route to a POST.
  * Tokens come from $CURSEFORGE_TOKEN and $MODRINTH_TOKEN. They are never written, printed or put in a
    URL, and HTTP failures report the response body without the request headers.
  * Anything already published is detected and skipped, so a run that dies halfway can just be re-run.
  * `updates.json` is never touched. Bumping it is what notifies every running client, and it belongs
    after the files are actually live — the order is in the sheet collect_release.py writes.
"""
import argparse, io, json, os, sys, urllib.error, urllib.request, uuid

from collect_release import mod_version
from verify_release_jars import inspect, LOCALES

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
VERSION = mod_version()

# Config lives in the home directory rather than the repo. Three worktrees build this mod and the agents
# make more; a gitignored file in the tree would have to be recreated in each one, and one careless
# .gitignore edit would put project ids into a public repo. These are machine-scoped facts, so they live
# in a machine-scoped place. No token ever goes in it.
CONFIG = os.path.join(os.path.expanduser("~"), ".riverfishing-release.json")
# The version table is ~10k rows and changes when Mojang ships, not when we do.
CACHE = os.path.join(os.path.expanduser("~"), ".riverfishing-release-cache.json")

CF_API = "https://minecraft.curseforge.com/api"
MR_API = "https://api.modrinth.com/v2"
UA = "qwazar14/riverfishing-release/%s (zenez89@gmail.com)" % VERSION  # Modrinth requires an identity.

CONFIG_TEMPLATE = {
    "_comment": "River Fishing release targets. Project ids only — NEVER put an API token in here. "
                "tools/upload_release.py reads those from $CURSEFORGE_TOKEN and $MODRINTH_TOKEN.",
    "curseforge_project_id": "",
    "modrinth_project_id": "river-fishing",
    # Mod id, as the jar declares it, -> the same project on each store. The jar knows it needs
    # "architectury"; only a human knows that is lhGA9TYQ on Modrinth. These four were read back off the
    # published 0.6.1 versions and the mod's own README, so they match what the pages already link to.
    "dependencies": {
        "architectury":  {"modrinth": "lhGA9TYQ", "curseforge": "architectury-api"},
        "fabric-api":    {"modrinth": "P7dR8mSH", "curseforge": "fabric-api"},
        "sereneseasons": {"modrinth": "e0bNACJD", "curseforge": "serene-seasons"},
        "biomesoplenty": {"modrinth": "HXF82T3G", "curseforge": "biomes-o-plenty"},
    },
}

# Modrinth spells loaders in lower case; CurseForge capitalises them in its version table.
MR_LOADER = {"Fabric": "fabric", "Forge": "forge", "NeoForge": "neoforge"}
CF_DEP_TYPE = {"required": "requiredDependency", "optional": "optionalDependency"}
# CurseForge encodes the environment as game versions named exactly this.
CF_ENVIRONMENT = {"both": ["Client", "Server"], "client": ["Client"], "server": ["Server"]}
# ...and Modrinth v2 encodes it on the *project*, as this pair. See modrinth_environment() below.
MR_PROJECT_ENV = {"both": ("required", "required"), "client": ("required", "unsupported"),
                  "server": ("unsupported", "required")}


def http(url, header=None, data=None, content_type=None):
    """One request. Failures carry the server's response body and never the request headers — a token in
    a traceback is a leaked token, and this script's whole job is to be run with two of them in the
    environment."""
    req = urllib.request.Request(url, data=data)
    req.add_header("User-Agent", UA)
    if header:
        req.add_header(*header)
    if content_type:
        req.add_header("Content-Type", content_type)
    try:
        with urllib.request.urlopen(req) as r:
            body = r.read().decode("utf-8")
            return json.loads(body) if body else {}
    except urllib.error.HTTPError as e:
        sys.exit("%s from %s\n%s" % (e.code, url.split("?")[0],
                                     e.read().decode("utf-8", "replace")[:2000]))


def multipart(fields, filename, blob, file_field):
    """An RFC 2388 body. Both stores take their JSON metadata as a form field beside the file and neither
    accepts a plain JSON POST, so this is unavoidable — but it is fifteen lines, not a dependency."""
    b = ("----rf" + uuid.uuid4().hex).encode()
    out = io.BytesIO()
    for name, value in fields.items():
        out.write(b"--" + b + b"\r\n")
        out.write(('Content-Disposition: form-data; name="%s"\r\n\r\n' % name).encode())
        out.write(value.encode("utf-8") + b"\r\n")
    out.write(b"--" + b + b"\r\n")
    out.write(('Content-Disposition: form-data; name="%s"; filename="%s"\r\n'
               % (file_field, filename)).encode())
    out.write(b"Content-Type: application/java-archive\r\n\r\n" + blob + b"\r\n")
    out.write(b"--" + b + b"--\r\n")
    return out.getvalue(), "multipart/form-data; boundary=" + b.decode()


# --- CurseForge's version table -------------------------------------------------------------------

def cf_index(token, refresh=False):
    """name -> [(id, version type)], across every version type at once.

    CurseForge keeps Minecraft versions, modloaders, Java versions and Client/Server in one flat table,
    each row tagged with the type it belongs to. Looking a name up across the whole table is fine while
    names stay unique, and stopping on a collision beats guessing which "1.21.1" was meant.
    """
    if not refresh and os.path.exists(CACHE):
        return {k: [tuple(h) for h in v] for k, v in
                json.loads(io.open(CACHE, encoding="utf-8").read()).items()}
    auth = ("X-Api-Token", token)
    types = {t["id"]: t.get("name") or t.get("slug") for t in http(CF_API + "/game/version-types", auth)}
    index = {}
    for v in http(CF_API + "/game/versions", auth):
        index.setdefault(v["name"], []).append((v["id"], types.get(v["gameVersionTypeID"], "?")))
    io.open(CACHE, "w", encoding="utf-8", newline="\n").write(json.dumps(index))
    return index


def cf_resolve(index, names):
    """Every name, or none of them.

    CurseForge will also accept `gameVersionNames` and resolve them itself, but then a name it does not
    recognise is simply not applied and the upload succeeds anyway. Resolving here means a renamed
    version type or a Java release CurseForge has not listed yet stops the release instead of shipping a
    file that is invisible to every filter.
    """
    ids, bad = [], []
    for n in names:
        hits = index.get(n, [])
        if len(hits) == 1:
            ids.append(hits[0][0])
        else:
            bad.append("%r %s" % (n, "is not in CurseForge's version table" if not hits else
                                  "is ambiguous — " + ", ".join("id %s (%s)" % h for h in hits)))
    if bad:
        sys.exit("cannot map this release onto CurseForge's version ids:\n  " + "\n  ".join(bad)
                 + "\n(try --refresh-versions; the cached table may predate the version you need)")
    return ids


def cf_names(m):
    """The four different kinds of thing CurseForge wants in one flat array."""
    return [m["minecraft"], m["loader"], "Java %d" % m["java"]] + CF_ENVIRONMENT[m["environment"]]


# --- building the two payloads for one jar --------------------------------------------------------

def version_name(m):
    """The human subtitle. 0.6.1 named its 1.x versions plain "River Fishing 0.6.1" and only its 26.x
    ones "…+26.1.2", which left four identically-named entries in the version list. Everything carries
    its Minecraft version and loader now — with eight files up, that is the only thing distinguishing
    them at a glance."""
    return "River Fishing %s — %s %s" % (VERSION, m["minecraft"], m["loader"])


def version_number(m):
    """One version per file, so the number has to be unique per file. This is the scheme 0.6.1 already
    published under; keeping it means the version list stays sortable across releases."""
    return "%s-%s+%s" % (VERSION, m["minecraft"], m["loader"])


def plan(m, cfg, changelog, release_type):
    """The same facts in each store's shape."""
    deps = []
    for d in m["dependencies"]:
        ids = cfg["dependencies"].get(d["mod_id"])
        if not ids:
            sys.exit("%s declares a dependency on %r and %s has no project id for it.\nAdd it under "
                     '"dependencies" — shipping without it is how 0.6.1\'s NeoForge files went up with '
                     "nothing telling anyone they needed Architectury." % (m["file"], d["mod_id"], CONFIG))
        deps.append((d, ids))

    mr = {"project_id": cfg["modrinth_project_id"], "name": version_name(m),
          "version_number": version_number(m), "changelog": changelog, "version_type": release_type,
          "loaders": [MR_LOADER[m["loader"]]], "game_versions": [m["minecraft"]], "featured": False,
          "dependencies": [{"project_id": i["modrinth"], "dependency_type": d["type"]} for d, i in deps],
          "file_parts": ["file"], "primary_file": "file"}

    cf = {"changelog": changelog, "changelogType": "markdown", "displayName": version_name(m),
          "releaseType": release_type}
    if deps:
        cf["relations"] = {"projects": [{"slug": i["curseforge"], "type": CF_DEP_TYPE[d["type"]]}
                                        for d, i in deps]}
    return mr, cf


# --- what is already up ---------------------------------------------------------------------------

def published_modrinth(cfg):
    """Listing versions needs no auth, so this is the authoritative answer and not a local guess."""
    return {v["version_number"]
            for v in http("%s/project/%s/version" % (MR_API, cfg["modrinth_project_id"]))}


def cf_state_path(folder):
    """CurseForge's upload API can create a file and update a file, but it cannot list them, so "did this
    already go up?" has to be remembered locally. The note sits in the release folder beside the jars it
    describes — gitignored build output, thrown away whenever the release is recollected.

    ponytail: local state, not the server's. A run killed between the POST and this write duplicates that
    one file; check the page. A listing endpoint on CurseForge's side would fix it properly.
    """
    return os.path.join(folder, "uploaded-curseforge.json")


def modrinth_environment(cfg, manifest):
    """What Modrinth can and cannot be told about client/server, reported rather than guessed.

    Modrinth v2 has **no per-version environment field** — a v2 version object carries loaders,
    game_versions and dependencies and nothing else about sides. Environment is `client_side` and
    `server_side` on the *project*, set once in the web UI and shared by every version.

    v3 does have a per-version `environment` with nine values, and it is live, but Modrinth's own
    announcement calls v3 experimental and not intended for general use, with v2 integration still to
    come. So this reads the project setting and complains when it contradicts the jars, rather than
    writing through an API documented as liable to change under it.
    """
    want = {m["environment"] for m in manifest["files"]}
    p = http("%s/project/%s" % (MR_API, cfg["modrinth_project_id"]))
    have = (p.get("client_side"), p.get("server_side"))
    if len(want) != 1:
        return "jars disagree about environment (%s) — Modrinth can only hold one answer per project" \
               % ", ".join(sorted(want))
    expect = MR_PROJECT_ENV[want.pop()]
    if have == expect:
        return None
    return ("project says client_side=%s server_side=%s, jars say it should be %s/%s.\n"
            "    Modrinth v2 cannot set this per version and this script will not change a project-wide\n"
            "    setting on its own — fix it once at https://modrinth.com/mod/%s/settings"
            % (have[0], have[1], expect[0], expect[1], cfg["modrinth_project_id"]))


# --- the run --------------------------------------------------------------------------------------

def load_config():
    if not os.path.exists(CONFIG):
        io.open(CONFIG, "w", encoding="utf-8", newline="\n").write(
            json.dumps(CONFIG_TEMPLATE, indent=2) + "\n")
        sys.exit("wrote a config template to %s — fill in curseforge_project_id (the numeric id on the\n"
                 "project's CurseForge page) and re-run. No token goes in that file." % CONFIG)
    cfg = json.loads(io.open(CONFIG, encoding="utf-8").read())
    for key in ("curseforge_project_id", "modrinth_project_id"):
        if not cfg.get(key):
            sys.exit("%s has no %s — fill it in before publishing anything" % (CONFIG, key))
    return cfg


def confirm(n_cf, n_mr, env_warning):
    print("\n" + "=" * 78)
    print("ABOUT TO PUBLISH River Fishing %s WHERE EVERYONE CAN SEE IT" % VERSION)
    print("  CurseForge : %d file(s)" % n_cf)
    print("  Modrinth   : %d version(s)" % n_mr)
    if env_warning:
        print("  WARNING    : %s" % env_warning.split("\n")[0])
    print("This is live the moment it succeeds and cannot be quietly undone.")
    print("=" * 78)
    want = "publish %s" % VERSION
    if input('Type "%s" to go ahead, anything else to stop: ' % want).strip() != want:
        sys.exit("stopped — nothing was uploaded")


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("--release-type", choices=("release", "beta", "alpha"),
                    help="required — this script will not guess whether a build is a release")
    ap.add_argument("--confirm", action="store_true",
                    help="actually upload; without it nothing leaves this machine")
    ap.add_argument("--refresh-versions", action="store_true", help="refetch CurseForge's version table")
    ap.add_argument("--self-check", action="store_true", help="check the metadata mapping and exit")
    a = ap.parse_args()
    if a.self_check:
        return self_check()
    if not a.release_type:
        ap.error("--release-type release|beta|alpha is required")

    folder = os.path.join(REPO, "build", "release-" + VERSION)
    man_path = os.path.join(folder, "manifest.json")
    if not os.path.exists(man_path):
        sys.exit("no %s — run `python tools/collect_release.py` first; that is what reads the jars"
                 % man_path)
    manifest = json.loads(io.open(man_path, encoding="utf-8").read())
    if manifest["version"] != VERSION:
        sys.exit("manifest is for %s but gradle.properties says %s — recollect the release"
                 % (manifest["version"], VERSION))

    # Not the whole of verify_release_jars.py: its other half checks the PrismLauncher instances on this
    # machine, which has nothing to do with publishing and fails anywhere else. Its jar-content check is
    # the release-relevant part, and a jar that lost a translation is not one to put on a store page.
    for m in manifest["files"]:
        info = inspect(os.path.join(folder, m["file"]))
        missing = [l for l in LOCALES if l not in info["locales"]]
        if missing or not info["discord"]:
            sys.exit("%s is missing %s — fix the build before publishing it"
                     % (m["file"], ", ".join(missing) or "the Discord link"))

    # The store changelog is docs/patchnotes/<version>.md, which collect_release.py copies in here. It is
    # written as page copy — headings, prose, the dependency line. updates.json also holds a changelog,
    # but that is three bullets per language sized for an in-game toast, and it is the update feed: this
    # script reads nothing from it and writes nothing to it.
    changelog = io.open(os.path.join(folder, manifest["changelog"]), encoding="utf-8").read()

    cfg = load_config()
    tokens = {}
    for var in ("CURSEFORGE_TOKEN", "MODRINTH_TOKEN"):
        tokens[var] = os.environ.get(var)
        if not tokens[var]:
            sys.exit("$%s is not set. Both are needed even for a dry run: CurseForge's version table is\n"
                     "behind the same token, and resolving it is most of what there is to check." % var)

    index = cf_index(tokens["CURSEFORGE_TOKEN"], a.refresh_versions)
    done_mr = published_modrinth(cfg)
    done_cf = json.loads(io.open(cf_state_path(folder), encoding="utf-8").read()) \
        if os.path.exists(cf_state_path(folder)) else {}
    env_warning = modrinth_environment(cfg, manifest)

    todo = []
    print("River Fishing %s — %s\n" % (VERSION, folder))
    for m in manifest["files"]:
        mr, cf = plan(m, cfg, changelog, a.release_type)
        cf["gameVersions"] = cf_resolve(index, cf_names(m))
        skip_mr = mr["version_number"] in done_mr
        skip_cf = m["file"] in done_cf
        todo.append((m, mr, cf, skip_mr, skip_cf))
        print("%s" % m["file"])
        print("  CurseForge  %s -> ids %s" % (" + ".join(cf_names(m)), cf["gameVersions"]))
        print("              %s%s" % (json.dumps({k: v for k, v in cf.items() if k != "changelog"}),
                                      "   [already uploaded, will skip]" if skip_cf else ""))
        print("  Modrinth    %s%s" % (json.dumps({k: v for k, v in mr.items() if k != "changelog"}),
                                      "   [already published, will skip]" if skip_mr else ""))
    print("\nchangelog: %s, %d chars, sent as markdown to both"
          % (manifest["changelog"], len(changelog)))
    if env_warning:
        print("\nENVIRONMENT: %s" % env_warning)

    n_cf = sum(1 for _, _, _, _, s in todo if not s)
    n_mr = sum(1 for _, _, _, s, _ in todo if not s)
    if not a.confirm:
        print("\nDRY RUN — nothing was uploaded. %d CurseForge file(s) and %d Modrinth version(s) would "
              "go up.\nRe-run with --confirm to publish." % (n_cf, n_mr))
        return 0
    if not n_cf and not n_mr:
        print("\neverything in this release is already published — nothing to do")
        return 0
    confirm(n_cf, n_mr, env_warning)

    for m, mr, cf, skip_mr, skip_cf in todo:
        blob = io.open(os.path.join(folder, m["file"]), "rb").read()
        if not skip_cf:
            body, ctype = multipart({"metadata": json.dumps(cf)}, m["file"], blob, "file")
            r = http("%s/projects/%s/upload-file" % (CF_API, cfg["curseforge_project_id"]),
                     ("X-Api-Token", tokens["CURSEFORGE_TOKEN"]), body, ctype)
            done_cf[m["file"]] = r.get("id")
            # Written after every single file, so a crash costs one duplicate at worst, never eight.
            io.open(cf_state_path(folder), "w", encoding="utf-8", newline="\n").write(
                json.dumps(done_cf, indent=2) + "\n")
            print("CurseForge  %s -> file %s" % (m["file"], r.get("id")))
        if not skip_mr:
            body, ctype = multipart({"data": json.dumps(mr)}, m["file"], blob, "file")
            # Modrinth PATs go in Authorization raw — no "Bearer " prefix, unlike almost everything else.
            r = http(MR_API + "/version", ("Authorization", tokens["MODRINTH_TOKEN"]), body, ctype)
            print("Modrinth    %s -> version %s" % (mr["version_number"], r.get("id")))

    print("\nfiles are live. updates.json is deliberately untouched — bump it last, by hand.")
    return 0


def self_check():
    """The metadata mapping, against a captured shape of CurseForge's version table.

    The real table is behind the same token as the upload, so this fixture is built to the documented
    shape rather than downloaded. What it pins is the part that breaks silently: that a loader, a Java
    version and an environment are looked up exactly the way a Minecraft version is, and that a name
    which is missing or ambiguous stops the run instead of quietly falling out of the array.
    """
    index = {"1.21.1": [(9990, "Minecraft 1.21")], "1.20.1": [(9001, "Minecraft 1.20")],
             "26.2": [(9995, "Minecraft 26.2")], "Fabric": [(7499, "Modloader")],
             "Forge": [(7498, "Modloader")], "NeoForge": [(10150, "Modloader")],
             "Java 17": [(8326, "Java")], "Java 21": [(9990, "Java")], "Java 25": [(10500, "Java")],
             "Client": [(9638, "Environment")], "Server": [(9639, "Environment")],
             # CurseForge really does reuse names across types; "1.21.1" as a Bukkit version is the shape
             # of collision this has to refuse rather than resolve.
             "1.16.5": [(8203, "Minecraft 1.16"), (8517, "Bukkit")]}

    m = {"minecraft": "1.21.1", "loader": "Fabric", "java": 21, "environment": "both"}
    assert cf_names(m) == ["1.21.1", "Fabric", "Java 21", "Client", "Server"], cf_names(m)
    assert cf_resolve(index, cf_names(m)) == [9990, 7499, 9990, 9638, 9639]

    neo = {"minecraft": "26.2", "loader": "NeoForge", "java": 25, "environment": "both"}
    assert cf_resolve(index, cf_names(neo)) == [9995, 10150, 10500, 9638, 9639]
    assert cf_resolve(index, cf_names({"minecraft": "1.20.1", "loader": "Forge", "java": 17,
                                       "environment": "client"})) == [9001, 7498, 8326, 9638]

    for names, why in ((["Java 26"], "a Java version CurseForge has not listed"),
                       (["1.16.5"], "a name that means two different things")):
        try:
            cf_resolve(index, names)
            raise AssertionError("resolved %s — %s must stop the run" % (names, why))
        except SystemExit:
            pass

    # The Modrinth side: one version per file, uniquely numbered, under the scheme 0.6.1 published with.
    cfg = {"modrinth_project_id": "river-fishing", "dependencies": CONFIG_TEMPLATE["dependencies"]}
    fabric = {"file": "riverfishing-Fabric-%s+1.21.1.jar" % VERSION, "minecraft": "1.21.1",
              "loader": "Fabric", "java": 21, "environment": "both",
              "dependencies": [{"mod_id": "architectury", "type": "required"},
                               {"mod_id": "fabric-api", "type": "required"},
                               {"mod_id": "sereneseasons", "type": "optional"}]}
    mr, cf = plan(fabric, cfg, "notes", "release")
    assert mr["loaders"] == ["fabric"] and mr["game_versions"] == ["1.21.1"]
    assert mr["version_number"] == "%s-1.21.1+Fabric" % VERSION, mr["version_number"]
    assert mr["name"] != mr["version_number"], "the subtitle is not the version number"
    assert mr["dependencies"] == [{"project_id": "lhGA9TYQ", "dependency_type": "required"},
                                  {"project_id": "P7dR8mSH", "dependency_type": "required"},
                                  {"project_id": "e0bNACJD", "dependency_type": "optional"}]
    assert cf["relations"]["projects"][2] == {"slug": "serene-seasons", "type": "optionalDependency"}
    assert cf["changelogType"] == "markdown" and "gameVersions" not in cf

    numbers = {version_number(dict(fabric, minecraft=v, loader=l))
               for v in ("1.20.1", "1.21.1", "26.1.2", "26.2") for l in ("Fabric", "NeoForge")}
    assert len(numbers) == 8, "eight files need eight distinct version numbers, got %d" % len(numbers)

    # A dependency the config has never heard of must stop the release, not ship unlinked.
    try:
        plan(dict(fabric, dependencies=[{"mod_id": "jei", "type": "optional"}]), cfg, "notes", "release")
        raise AssertionError("an unmapped dependency must stop the run")
    except SystemExit:
        pass

    print("self-check passed — %d names resolved, ambiguity and unmapped dependencies both refused"
          % len(index))
    return 0


if __name__ == "__main__":
    sys.exit(main())
