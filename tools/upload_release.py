# -*- coding: utf-8 -*-
"""Publish a release: the GitHub tag, then the eight jars to Modrinth and CurseForge.

    python tools/upload_release.py --release-type release                    (dry run — publishes nothing)
    python tools/upload_release.py --release-type release --stage github     (GitHub only, no API tokens)
    python tools/upload_release.py --release-type release --confirm          (the real thing, asks first)
    python tools/upload_release.py --self-check                              (is the mapping still sane?)

Three stages, in the order a player experiences them. GitHub first, because the in-game update notice
points there and the store pages sit in approval for hours — and because it needs only the gh CLI, which
already holds its own credential, so it must not be gated behind two API tokens the operator may not have
yet. `--stage github` and `--stage stores` run the halves separately; the default runs both.

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
import argparse, hashlib, io, json, os, shutil, subprocess, sys, urllib.error, urllib.request, uuid

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
# Reads of the per-version side have to come from v3. v2 ACCEPTS `environment` when a version is created
# — it is in its own CreatableVersion schema — and then never reports it again: GET /v2/version/<id>
# returns no such key, so v2 can write this fact and cannot show it back. The upload stays on v2 with the
# rest of the release; anything that needs to READ a side asks v3.
MR_API3 = "https://api.modrinth.com/v3"
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
# ...and Modrinth keeps the same fact per VERSION, spelled like this. There is no project-level pair
# left to set; the one v2 still reports is computed from these.
#
# Sending it is NOT optional even though the field is. Asked to create a version with no `environment`,
# labrinth's v2 shim copies the value off another version of the same project and falls back to
# "unknown" — the comment in its own source calls this "inherently lossy". Every live version of this
# mod currently says client_only, so deleting the one line in plan() that sets this would not fall back
# to a sane default: it would quietly publish eight more client-only files.
#
# Ten values exist in the enum. This mod is required on both ends, so only these three come up.
MR_VERSION_ENV = {"both": "client_and_server", "client": "client_only", "server": "server_only"}


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


# Which version TYPE each kind of name must come from. A bare name is not enough: "1.20.1" is a row in
# three types at once — the real one, the `Addons` bucket for Bukkit-side plugins, and a type the
# version-types endpoint returns no name for.
#
# "game" is a blacklist and the others are exact, which is not laziness. CurseForge names its Minecraft
# types inconsistently: 1.20.1 lives under "Minecraft 1.20" and 26.1.2 under "Minecraft 26.1", but 26.2
# lives under a type called just "26.2". A "starts with Minecraft" rule would silently drop 26.2 — the
# version most of this mod's players are on. What is stable is which buckets are WRONG.
CF_TYPE = {
    # Every type that is NOT a Minecraft game version, so a loader or a Java name looked up as a game
    # version finds nothing instead of matching by accident. Caught by the self-check, which asks for
    # "Fabric" as a game version and requires the run to stop.
    "game": lambda t: t not in ("Addons", "?", "Modloader", "Java", "Environment", "Bukkit"),
    "loader": lambda t: t == "Modloader",
    "java": lambda t: t == "Java",
    "env": lambda t: t == "Environment",
}


def cf_resolve(index, named):
    """Every name, or none of them.

    CurseForge will also accept `gameVersionNames` and resolve them itself, but then a name it does not
    recognise is simply not applied and the upload succeeds anyway. Resolving here means a renamed
    version type or a Java release CurseForge has not listed yet stops the release instead of shipping a
    file that is invisible to every filter.

    @param named (name, kind) pairs — see CF_TYPE. The kind is what makes an ambiguous name resolvable.
    """
    ids, bad = [], []
    for n, kind in named:
        hits = [h for h in index.get(n, []) if CF_TYPE[kind](h[1])]
        if len(hits) == 1:
            ids.append(hits[0][0])
        else:
            bad.append("%r (%s) %s" % (n, kind,
                                       "is not in CurseForge's version table" if not hits else
                                       "is still ambiguous — " + ", ".join("id %s (%s)" % h for h in hits)))
    if bad:
        sys.exit("cannot map this release onto CurseForge's version ids:\n  " + "\n  ".join(bad)
                 + "\n(try --refresh-versions; the cached table may predate the version you need)")
    return ids


def cf_names(m):
    """The four different kinds of thing CurseForge wants in one flat array — each carrying its kind.

    They go up as one undifferentiated `gameVersions` array, which is why this used to return bare
    strings. But the array is the WIRE format, not the lookup: a name has to be resolved as the kind of
    thing it is before it can be flattened, and dropping the kind here is what made "1.20.1" unresolvable.
    """
    return ([(m["minecraft"], "game"), (m["loader"], "loader"), ("Java %d" % m["java"], "java")]
            + [(e, "env") for e in CF_ENVIRONMENT[m["environment"]]])


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

    mr = {"project_id": cfg["modrinth_id"], "name": version_name(m),
          "version_number": version_number(m), "changelog": changelog, "version_type": release_type,
          "loaders": [MR_LOADER[m["loader"]]], "game_versions": [m["minecraft"]], "featured": False,
          "environment": MR_VERSION_ENV[m["environment"]],
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
    """Every version already up, whole rather than just its number.

    Listing needs no auth, so this is the authoritative answer and not a local guess. It reads v3 because
    the side of a version is invisible in v2 — see MR_API3 — and one list of versions should answer both
    "is this already published?" and "what does it claim about client and server".
    """
    return http("%s/project/%s/version" % (MR_API3, cfg["modrinth_project_id"]))


def cf_state_path(folder):
    """CurseForge's upload API can create a file and update a file, but it cannot list them, so "did this
    already go up?" has to be remembered locally. The note sits in the release folder beside the jars it
    describes — gitignored build output, thrown away whenever the release is recollected.

    ponytail: local state, not the server's. A run killed between the POST and this write duplicates that
    one file; check the page. A listing endpoint on CurseForge's side would fix it properly.
    """
    # NOT inside the release folder: collect_release.py opens with shutil.rmtree on it, so the natural
    # mid-release reaction — fix one jar, re-collect — silently destroyed the only record of what had
    # already gone up, and the resume re-uploaded all eight.
    return os.path.join(os.path.dirname(CONFIG), ".riverfishing-uploaded-%s.json" % VERSION)


def modrinth_environment(cfg, manifest, published):
    """What the versions ALREADY up claim about client and server, checked against what these jars are.

    This used to read the project's `client_side`/`server_side` pair and tell the operator to go and fix
    it in the project settings. Both halves were wrong. Modrinth moved sides onto the version — the
    project carries a `side_types_migration_review_status` field as the scar — and the settings page no
    longer has that control, so the advice sent someone looking for a switch that is not there.

    The pair v2 still reports is now DERIVED from the versions, which is checkable rather than assumed:
    Create's versions are a mix of `server_only_client_optional` and `client_and_server`, and its pair
    reads `optional/required` — neither of those, their merge. So there is nothing project-wide left to
    set, and uploading correct versions moves the pair by itself.

    What that leaves worth checking is the versions already published, because they keep voting in that
    merge forever. This names them and stops there. Not because it cannot reach them — PATCH /v3/version
    takes `environment` and has no published-version gate — but because editing a file that is already
    public is a change of its own, and a release script should not make one nobody asked for.
    """
    want = {m["environment"] for m in manifest["files"]}
    if len(want) != 1:
        return ("jars disagree about environment (%s) — every file in one release should claim the same "
                "sides" % ", ".join(sorted(want)))
    expect = MR_VERSION_ENV[want.pop()]
    wrong = [v for v in published if v.get("environment") != expect]
    if not wrong:
        return None
    return ("%d version(s) already up say %s; these jars are %s. The project's client_side/server_side\n"
            "    pair is derived from all of them, so it keeps reporting the merge until they are fixed.\n"
            "    What this script uploads will be right; the ones below need the Environment selector on\n"
            "    their own edit page:\n"
            "    %s"
            % (len(wrong), "/".join(sorted({str(v.get("environment")) for v in wrong})), expect,
               # Not the version number: all eight files of a release share one, so on its own it names
               # nothing. The loader and the Minecraft line are what tell them apart on the page.
               "\n    ".join("%-8s %-9s %-8s https://modrinth.com/mod/%s/version/%s"
                             % (v["version_number"], "/".join(v.get("loaders") or ["?"]),
                                "/".join(v.get("game_versions") or ["?"]),
                                cfg["modrinth_project_id"], v["id"])
                             for v in wrong)))


# --- the run --------------------------------------------------------------------------------------

# --- GitHub ---------------------------------------------------------------------------------------

def gh(*args, capture=True):
    """The gh CLI, which is already authenticated on this machine.

    A third API token would be a third secret to keep out of a traceback for no gain: gh holds its own
    credential in the OS keyring and this script never sees it. `gh` also uploads assets in one call,
    where the raw API wants a separate upload host per file.
    """
    p = subprocess.run(("gh",) + args, capture_output=capture, text=True, encoding="utf-8",
                       cwd=REPO)
    if p.returncode != 0:
        sys.exit("gh %s failed:\n%s" % (" ".join(args), (p.stderr or p.stdout or "").strip()[:2000]))
    return (p.stdout or "").strip()


def github_asset_name(m):
    """The jar's name ON THE RELEASE PAGE, which cannot be the name in the folder.

    This once claimed GitHub rewrites a "+" in an asset name. It does not — v0.6.1 of this repo is
    live with `riverfishing-fabric-0.6.1+1.20.1.jar`, the plus stored verbatim and percent-encoded in
    the download URL. The claim was asserted without being checked, and it is recorded here rather than
    quietly deleted because it is exactly the kind of tidy-sounding reason that survives review.

    The real reason is smaller: v0.7.0 and v0.7.1 are already public under this scheme, and a download
    link a human retypes reads better without an escape in it. Derived from the same manifest as
    everything else, so the release name and the store name cannot drift apart.
    """
    return "riverfishing-%s-mc%s-%s.jar" % (VERSION, m["minecraft"], m["loader"].lower())


def github_release(folder, manifest, changelog, release_type, confirmed):
    """Create the release, or report that it is already there.

    First of the three, because it is the one the in-game update notice points at: a player told there
    is a new version must find it, and the store pages take hours to approve.
    """
    tag = "v" + VERSION
    # `gh release create` without --target tags the repository's DEFAULT branch, not the commit the jars
    # were built from. main here is 169 commits behind and says mod_version=0.6.0, so v0.7.1 was created
    # naming 0.6.0 source: the download was right and "build it from the tag" gave you the wrong mod.
    head = subprocess.run(("git", "rev-parse", "HEAD"), cwd=REPO,
                          capture_output=True, text=True, encoding="utf-8")
    if head.returncode != 0 or not head.stdout.strip():
        sys.exit("cannot read HEAD — a release has to name the commit it was built from")
    target = head.stdout.strip()
    p = subprocess.run(("gh", "release", "view", tag, "--json", "tagName,isDraft"),
                       capture_output=True, text=True, encoding="utf-8", cwd=REPO)
    if p.returncode == 0:
        # `gh release create` with assets uploads into a DRAFT and publishes at the end, so a run killed
        # mid-upload leaves one behind. Treating that as "already released" would skip the stage and
        # leave a half-uploaded release invisible on the page.
        if json.loads(p.stdout).get("isDraft"):
            sys.exit("GitHub %s exists as an unpublished DRAFT — an earlier run died mid-upload.\n"
                     "Delete it (gh release delete %s --cleanup-tag) and run this again." % (tag, tag))
        print("GitHub      %s already exists — skipping" % tag)
        return False

    staged = os.path.join(folder, "github")
    pre = release_type != "release"
    if not confirmed:
        print("GitHub      would create %s%s at %s with %d assets:"
              % (tag, " (pre-release)" if pre else "", target[:12], len(manifest["files"]) + 1))
        for m in manifest["files"]:
            print("              %s" % github_asset_name(m))
        print("              SHA256SUMS.txt")
        return True

    # Renamed into a subfolder rather than in place: the folder is what the stores upload from, and
    # renaming under them would break the manifest that describes it.
    if os.path.isdir(staged):
        shutil.rmtree(staged)
    os.makedirs(staged)
    for m in manifest["files"]:
        shutil.copy2(os.path.join(folder, m["file"]), os.path.join(staged, github_asset_name(m)))
    # Hashed from the file being uploaded, not copied from the manifest. The manifest records what
    # collect_release saw; between then and now a jar can have been rebuilt, and a sums file that asserts
    # a hash nobody re-checked is worse than none — it is the thing players verify a download against.
    sums = os.path.join(staged, "SHA256SUMS.txt")
    io.open(sums, "w", encoding="utf-8", newline="\n").write("".join(
        "%s  %s\n" % (hashlib.sha256(io.open(os.path.join(staged, github_asset_name(m)), "rb").read())
                       .hexdigest(), github_asset_name(m))
        for m in manifest["files"]))

    notes = os.path.join(staged, "NOTES.md")
    io.open(notes, "w", encoding="utf-8", newline="\n").write(changelog)
    # A beta or an alpha is a PRE-release on GitHub too. Without this the stores would label it beta
    # and the release page would call the same build final — one fact, two answers, in public.
    args = ["release", "create", tag, "--target", target,
            "--title", "River Fishing " + VERSION, "--notes-file", notes]
    if pre:
        args.append("--prerelease")
    gh(*args, *[os.path.join(staged, github_asset_name(m)) for m in manifest["files"]], sums)
    print("GitHub      %s created%s at %s with %d assets"
          % (tag, " as a pre-release" if pre else "", target[:12], len(manifest["files"]) + 1))
    return True


def load_config(publishing):
    """Config, strict only when something is about to be written.

    A dry run must be runnable by someone who has not collected the secrets yet — that run is how they
    find out which secrets they need.
    """
    if not os.path.exists(CONFIG):
        io.open(CONFIG, "w", encoding="utf-8", newline="\n").write(
            json.dumps(CONFIG_TEMPLATE, indent=2) + "\n")
        sys.exit("wrote a config template to %s — fill in curseforge_project_id (the numeric id on the\n"
                 "project's CurseForge page) and re-run. No token goes in that file." % CONFIG)
    cfg = json.loads(io.open(CONFIG, encoding="utf-8").read())
    if not cfg.get("modrinth_project_id"):
        sys.exit("%s has no modrinth_project_id — nothing can be planned without it" % CONFIG)
    if publishing and not cfg.get("curseforge_project_id"):
        sys.exit("%s has no curseforge_project_id. It is the numeric id on the project's CurseForge\n"
                 "page, under 'About Project'. A dry run works without it; publishing does not." % CONFIG)
    return cfg


def confirm(lines, env_warning=None):
    """The typed gate. EVERY write goes through here, including the GitHub stage.

    It used to take the two store counts, which put it after the github-only early return — so that
    stage, added later, reached a public write with nothing but --confirm behind it."""
    print("\n" + "=" * 78)
    print("ABOUT TO PUBLISH River Fishing %s WHERE EVERYONE CAN SEE IT" % VERSION)
    for line in lines:
        print("  %s" % line)
    if env_warning:
        print("  WARNING    : %s" % env_warning.split("\n")[0])
    print("This is live the moment it succeeds and cannot be quietly undone.")
    print("=" * 78)
    want = "publish %s" % VERSION
    if input('Type "%s" to go ahead, anything else to stop: ' % want).strip() != want:
        sys.exit("stopped — nothing FURTHER was published; anything printed above already is")


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("--release-type", choices=("release", "beta", "alpha"),
                    help="required — this script will not guess whether a build is a release")
    ap.add_argument("--stage", choices=("all", "github", "stores"), default="all",
                    help="github needs only the gh CLI; stores needs both API tokens")
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
    # manifest["version"] and VERSION both come out of gradle.properties, so they agree however stale
    # the folder is. This is the check that would have caught 0.7.0 going out eight jars behind what was
    # actually built: the recorded hash against the bytes on disk.
    for m in manifest["files"]:
        path = os.path.join(folder, m["file"])
        if hashlib.sha256(io.open(path, "rb").read()).hexdigest() != m["sha256"]:
            sys.exit("%s does not match the hash collect_release recorded — the folder is stale.\n"
                     "Run `python tools/collect_release.py` and try again." % m["file"])
        info = inspect(path)
        missing = [l for l in LOCALES if l not in info["locales"]]
        if missing or not info["discord"]:
            sys.exit("%s is missing %s — fix the build before publishing it"
                     % (m["file"], ", ".join(missing) or "the Discord link"))

    # The store changelog is docs/patchnotes/<version>.md, which collect_release.py copies in here. It is
    # written as page copy — headings, prose, the dependency line. updates.json also holds a changelog,
    # but that is three bullets per language sized for an in-game toast, and it is the update feed: this
    # script reads nothing from it and writes nothing to it.
    changelog = io.open(os.path.join(folder, manifest["changelog"]), encoding="utf-8").read()

    print("River Fishing %s — %s\n" % (VERSION, folder))

    # §github-stage: first, and standalone. It is the release the in-game update notice points at, so a
    # player told there is a new version has somewhere to go while the store pages sit in approval — and
    # it needs no API token of ours, so it must not be gated behind two the operator may not have yet.
    if a.stage in ("all", "github"):
        # The typed gate sits HERE for EVERY run that publishes to GitHub. It was once written
        # `a.confirm and a.stage == "github"`, which covered the github-only path and left the DEFAULT
        # one — --stage all — writing a public release before a single question was asked, and before
        # load_config or the token check could abort. A guard that covers some of the paths to a write
        # is the same mistake it was written to fix.
        if a.confirm:
            confirm(["GitHub     : release v%s with %d assets"
                     % (VERSION, len(manifest["files"]) + 1)])
        github_release(folder, manifest, changelog, a.release_type, a.confirm)
        if a.stage == "github":
            print("\nDRY RUN — nothing was created. Re-run with --confirm." if not a.confirm
                  else "\nGitHub done. The stores are --stage stores, once both tokens are set.")
            return 0
        print()

    cfg = load_config(a.confirm)
    # Modrinth takes "the id OR the slug" in a URL path and only the base62 id in a version payload —
    # a distinction its own docs make and nothing enforces until the POST. The config holds the slug,
    # because a slug is what a human can read off the page, so resolve it once here rather than asking
    # anyone to keep a base62 string in a file by hand. GET /project/<id-or-slug> accepts either, so
    # this is correct whichever the config happens to hold.
    cfg["modrinth_id"] = http("%s/project/%s" % (MR_API, cfg["modrinth_project_id"]))["id"]

    tokens = {v: os.environ.get(v) for v in ("CURSEFORGE_TOKEN", "MODRINTH_TOKEN")}
    absent = [v for v, t in tokens.items() if not t]
    if a.confirm and absent:
        sys.exit("cannot publish: $%s not set" % ", $".join(absent))

    # CurseForge's version table is behind its token, so without it the numeric ids cannot be resolved.
    # The NAMES can still be shown, which is the part a human can actually check.
    index = cf_index(tokens["CURSEFORGE_TOKEN"], a.refresh_versions) \
        if tokens["CURSEFORGE_TOKEN"] else None
    mr_published = published_modrinth(cfg)
    done_mr = {v["version_number"] for v in mr_published}
    done_cf = json.loads(io.open(cf_state_path(folder), encoding="utf-8").read()) \
        if os.path.exists(cf_state_path(folder)) else {}
    env_warning = modrinth_environment(cfg, manifest, mr_published)

    todo = []
    for m in manifest["files"]:
        mr, cf = plan(m, cfg, changelog, a.release_type)
        cf["gameVersions"] = cf_resolve(index, cf_names(m)) if index else None
        skip_mr = mr["version_number"] in done_mr
        skip_cf = m["file"] in done_cf
        todo.append((m, mr, cf, skip_mr, skip_cf))
        print("%s" % m["file"])
        print("  CurseForge  %s -> %s" % (" + ".join(n for n, _ in cf_names(m)),
                                            cf["gameVersions"] if index else "ids need $CURSEFORGE_TOKEN"))
        print("              %s%s" % (json.dumps({k: v for k, v in cf.items() if k != "changelog"}),
                                      "   [already uploaded, will skip]" if skip_cf else ""))
        print("  Modrinth    %s%s" % (json.dumps({k: v for k, v in mr.items() if k != "changelog"}),
                                      "   [already published, will skip]" if skip_mr else ""))
    print("\nchangelog: %s, %d chars, sent as markdown to both"
          % (manifest["changelog"], len(changelog)))
    if absent:
        print("MISSING    : $%s — a dry run works without them, publishing does not"
              % ", $".join(absent))
    if not cfg.get("curseforge_project_id"):
        print("MISSING    : curseforge_project_id in %s (the numeric id under 'About Project')" % CONFIG)
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
    confirm(["GitHub     : v%s (created above, before this prompt)" % VERSION,
             "CurseForge : %d file(s)" % n_cf,
             "Modrinth   : %d version(s)" % n_mr], env_warning)

    for m, mr, cf, skip_mr, skip_cf in todo:
        blob = io.open(os.path.join(folder, m["file"]), "rb").read()
        # Modrinth first, and not for atomicity — there is none. The Modrinth payload is the same shape
        # for all eight files, so a rejection of any kind fires on file one, with nothing published
        # anywhere. Fail the other way round and CurseForge is already advertising a lone 1.20.1 Fabric
        # jar as the release. It is also the recoverable order: a Modrinth version can be deleted, a
        # CurseForge file cannot.
        if not skip_mr:
            body, ctype = multipart({"data": json.dumps(mr)}, m["file"], blob, "file")
            # Modrinth PATs go in Authorization raw — no "Bearer " prefix, unlike almost everything else.
            r = http(MR_API + "/version", ("Authorization", tokens["MODRINTH_TOKEN"]), body, ctype)
            print("Modrinth    %s -> version %s" % (mr["version_number"], r.get("id")))
        if not skip_cf:
            body, ctype = multipart({"metadata": json.dumps(cf)}, m["file"], blob, "file")
            r = http("%s/projects/%s/upload-file" % (CF_API, cfg["curseforge_project_id"]),
                     ("X-Api-Token", tokens["CURSEFORGE_TOKEN"]), body, ctype)
            done_cf[m["file"]] = r.get("id")
            # Written after every single file, so a crash costs one duplicate at worst, never eight.
            io.open(cf_state_path(folder), "w", encoding="utf-8", newline="\n").write(
                json.dumps(done_cf, indent=2) + "\n")
            print("CurseForge  %s -> file %s" % (m["file"], r.get("id")))

    print("\nfiles are live. updates.json is deliberately untouched — bump it last, by hand.")
    return 0


def self_check():
    """The metadata mapping, against a captured shape of CurseForge's version table.

    The real table is behind the same token as the upload, so this fixture is built to the documented
    shape rather than downloaded. What it pins is the part that breaks silently: that a loader, a Java
    version and an environment are looked up exactly the way a Minecraft version is, and that a name
    which is missing or ambiguous stops the run instead of quietly falling out of the array.
    """
    # Shaped like the LIVE table, which the old fixture was not: every Minecraft version is a row in
    # three types at once — the real one, an `Addons` bucket for Bukkit-side plugins, and a type whose id
    # /game/version-types returns no name for. The old one-row-per-version fixture is why this check
    # passed for months while the resolver could not resolve a single Minecraft version.
    #
    # 26.2's type is named "26.2" and 1.20.1's is "Minecraft 1.20" — that inconsistency is real, and it
    # is why the game kind is a blacklist rather than a "starts with Minecraft" prefix.
    index = {"1.21.1": [(11779, "Minecraft 1.21"), (12735, "?"), (16115, "Addons")],
             "1.20.1": [(9990, "Minecraft 1.20"), (9993, "Addons"), (9994, "?")],
             "26.2": [(16498, "26.2"), (16500, "?")],
             "Fabric": [(7499, "Modloader")], "Forge": [(7498, "Modloader")],
             "NeoForge": [(10150, "Modloader")],
             "Java 17": [(8326, "Java")], "Java 21": [(11135, "Java")], "Java 25": [(14454, "Java")],
             "Client": [(9638, "Environment")], "Server": [(9639, "Environment")],
             # Two REAL game types for one name is the collision that must still stop the run: no rule
             # can pick between them, and guessing would ship a file onto the wrong version.
             "1.16.5": [(8203, "Minecraft 1.16"), (8517, "Minecraft 1.16 (snapshot)")]}

    m = {"minecraft": "1.21.1", "loader": "Fabric", "java": 21, "environment": "both"}
    assert cf_names(m) == [("1.21.1", "game"), ("Fabric", "loader"), ("Java 21", "java"),
                           ("Client", "env"), ("Server", "env")], cf_names(m)
    assert cf_resolve(index, cf_names(m)) == [11779, 7499, 11135, 9638, 9639]

    neo = {"minecraft": "26.2", "loader": "NeoForge", "java": 25, "environment": "both"}
    assert cf_resolve(index, cf_names(neo)) == [16498, 10150, 14454, 9638, 9639]
    assert cf_resolve(index, cf_names({"minecraft": "1.20.1", "loader": "Forge", "java": 17,
                                       "environment": "client"})) == [9990, 7498, 8326, 9638]

    for names, why in (([("Java 26", "java")], "a Java version CurseForge has not listed"),
                       ([("1.16.5", "game")], "two real game types for one name"),
                       ([("Fabric", "game")], "the right name looked up as the wrong kind")):
        try:
            cf_resolve(index, names)
            raise AssertionError("resolved %s — %s must stop the run" % (names, why))
        except SystemExit:
            pass

    # The Modrinth side: one version per file, uniquely numbered, under the scheme 0.6.1 published with.
    cfg = {"modrinth_project_id": "river-fishing", "modrinth_id": "5acJO3EU",
           "dependencies": CONFIG_TEMPLATE["dependencies"]}
    fabric = {"file": "riverfishing-Fabric-%s+1.21.1.jar" % VERSION, "minecraft": "1.21.1",
              "loader": "Fabric", "java": 21, "environment": "both",
              "dependencies": [{"mod_id": "architectury", "type": "required"},
                               {"mod_id": "fabric-api", "type": "required"},
                               {"mod_id": "sereneseasons", "type": "optional"}]}
    mr, cf = plan(fabric, cfg, "notes", "release")
    assert mr["loaders"] == ["fabric"] and mr["game_versions"] == ["1.21.1"]
    assert mr["version_number"] == "%s-1.21.1+Fabric" % VERSION, mr["version_number"]
    assert mr["name"] != mr["version_number"], "the subtitle is not the version number"
    # base62 has no hyphen, so a slug that leaked into the payload fails here rather than as a 400 on
    # the first POST of a real release — which is where it failed before, one CurseForge file too late.
    assert mr["project_id"].isalnum(), "project_id must be the base62 id, not %r" % mr["project_id"]
    assert mr["environment"] == "client_and_server", mr["environment"]
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

    # Sides, against the versions rather than the project. The manifest here is both jars claiming both
    # ends, so anything already up that says otherwise has to be named.
    both = {"files": [fabric, dict(fabric, minecraft="1.20.1")]}
    assert modrinth_environment(cfg, both, []) is None, "nothing published yet is nothing to report"
    assert modrinth_environment(cfg, both, [{"id": "aa", "version_number": "0.7.0",
                                             "environment": "client_and_server"}]) is None
    # The real state of this project before 0.7.1: everything up is client_only, inherited from the
    # migration off the old project-wide pair.
    stale = modrinth_environment(cfg, both, [{"id": "aa", "version_number": "0.7.0",
                                              "environment": "client_only"},
                                             # `environment` ABSENT, not null — that is how an
                                             # unmigrated version reads, and it is not "fine".
                                             {"id": "bb", "version_number": "0.6.1"}])
    assert stale and "2 version(s)" in stale and "modrinth.com/mod/river-fishing/version/bb" in stale, stale
    # One release must not claim two different things.
    mixed = modrinth_environment(cfg, {"files": [fabric, dict(fabric, environment="client")]}, [])
    assert mixed and "disagree" in mixed, mixed

    print("self-check passed — %d names resolved; ambiguity, unmapped dependencies and stale sides "
          "all refused" % len(index))
    return 0


if __name__ == "__main__":
    sys.exit(main())
