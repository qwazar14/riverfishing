# -*- coding: utf-8 -*-
"""Build the single-page wiki and publish it to the gh-pages branch.

    python tools/publish_pages.py            (build + commit + push)
    python tools/publish_pages.py --dry-run  (build only, show what would be pushed)

Why a dedicated orphan branch rather than serving /docs from a source branch:

  * The built page embeds Minecraft's own item icons. Those are read from a local client jar at build
    time and deliberately never committed to a source branch — see tools/wiki_art.py. gh-pages holds
    only generated output, so the source tree stays clean of Mojang art.
  * The repo has one branch per Minecraft version, and `main` is currently the 26.x tree. Anything that
    depends on "the /docs folder of the default branch" would break the next time the branch layout
    changes. An orphan branch has no such coupling.
  * The page is ~2.3 MB. Keeping its history out of the code branches keeps clones small; gh-pages is
    force-updated to a single commit each time, so its history never grows either.

The branch is rebuilt from scratch on every publish — it has no history worth keeping and no source of
truth in it. docs/wiki/*.md remains the only source.
"""
import io, os, subprocess, sys, tempfile

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BRANCH = "gh-pages"
PY = sys.executable


def run(args, cwd=REPO, check=True):
    r = subprocess.run(args, cwd=cwd, capture_output=True, text=True, encoding="utf-8", errors="replace")
    if check and r.returncode:
        sys.exit("failed: %s\n%s%s" % (" ".join(args), r.stdout, r.stderr))
    return r.stdout.strip()


def main(dry):
    out = os.path.join(tempfile.mkdtemp(prefix="rf-pages-"), "index.html")
    print(run([PY, os.path.join(REPO, "tools", "gen_wiki_bundle.py"), "--out", out]))
    size = os.path.getsize(out)
    if size < 500_000:
        sys.exit("page is only %d bytes — the art did not make it in, refusing to publish" % size)

    # A landing page that is one file needs .nojekyll, or GitHub runs Jekyll over it for no reason.
    work = tempfile.mkdtemp(prefix="rf-ghp-")
    run(["git", "clone", "--no-checkout", "--depth", "1", REPO, work], cwd=None)
    run(["git", "checkout", "--orphan", BRANCH], cwd=work)
    run(["git", "rm", "-rf", "--cached", "."], cwd=work, check=False)
    for f in os.listdir(work):
        if f != ".git":
            p = os.path.join(work, f)
            if os.path.isdir(p):
                import shutil
                shutil.rmtree(p)
            else:
                os.remove(p)
    io.open(os.path.join(work, "index.html"), "w", encoding="utf-8", newline="\n").write(
        io.open(out, encoding="utf-8").read())
    io.open(os.path.join(work, ".nojekyll"), "w", encoding="utf-8", newline="\n").write("")
    io.open(os.path.join(work, "README.md"), "w", encoding="utf-8", newline="\n").write(
        "# Generated — do not edit\n\n"
        "This branch is the published River Fishing wiki, built from `docs/wiki/*.md` on the development\n"
        "branch by `tools/publish_pages.py`. It is force-replaced on every publish, so any edit made here\n"
        "is lost. Edit the markdown instead.\n")

    run(["git", "add", "-A"], cwd=work)
    if dry:
        print("\n--- would publish ---")
        print(run(["git", "status", "--short"], cwd=work))
        print("page: %.1f MB" % (size / 1e6))
        return 0
    run(["git", "commit", "-q", "-m", "Publish the wiki (generated from docs/wiki)"], cwd=work)
    run(["git", "push", "-q", "--force",
         "https://github.com/qwazar14/riverfishing.git", "%s:%s" % (BRANCH, BRANCH)], cwd=work)
    print("pushed %s (%.1f MB)" % (BRANCH, size / 1e6))
    return 0


if __name__ == "__main__":
    sys.exit(main("--dry-run" in sys.argv))
