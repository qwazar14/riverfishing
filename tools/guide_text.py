# -*- coding: utf-8 -*-
"""The guide shelf as ONE text file in three languages — for reading, and for putting back.

    py -X utf8 tools/guide_text.py export <out.txt> [root]
    py -X utf8 tools/guide_text.py import <in.txt>  [root]

The lang JSON is the wrong shape to proofread: every page is one line with \\n in it, three files
apart. This writes the shelf in shelf order — group by group, page by page, each key as three blocks —
with the text exactly as the player sees it (a literal % is a %, not the %% the file stores), and reads
the same file back into the three JSONs. Round trip is a no-op, and export checks that on its own file.

    ################ 4 · Reading the water
    ======== community
    === guide.riverfishing.community.title
    --- en_us
    Every water is its own
    --- ru_ru
    У каждого водоёма — своё население
    --- uk_ua
    ...

Only page keys (.title .text .table .bars) and the group headings go out; the inline keys the journal
uses for buttons stay where they are. On import, a key that is not in the file is left alone, a
language block that is empty is left alone, and a block that changed is written; then the file is
re-exported over itself so what you keep on disk is what the game has.
"""
import io, json, os, re, sys

LOCALES = ("en_us", "ru_ru", "uk_ua")
SUFFIXES = ("title", "text", "table", "bars")


def paths(root):
    lang = os.path.join(root, "common/src/main/resources/assets/riverfishing/lang")
    return {loc: os.path.join(lang, loc + ".json") for loc in LOCALES}, \
        os.path.join(root, "common/src/main/java/com/riverfishing/client/JournalScreen.java")


def shelf(js_path):
    """[(group, page)] in the order JournalScreen puts them on the shelf."""
    src = io.open(js_path, encoding="utf-8").read()
    out, group = [], None
    for m in re.finditer(r'guideGroupNow = (\d+);|addGuide\("(\w+)"', src):
        if m.group(1):
            group = int(m.group(1))
        else:
            out.append((group, m.group(2)))
    return out


def shown(v):
    """What the player reads: %% is one percent sign. %s and %n$s are left for the formatter."""
    return re.sub(r"%%", "%", v)


def stored(v):
    """Back to what String.format wants: every % that is not %s / %n$s / already %% is doubled."""
    out, i = [], 0
    while i < len(v):
        m = re.match(r"%(?:%|(?:\d+\$)?s)", v[i:])
        if m:
            tok = m.group(0)
            out.append("%%" if tok == "%" else tok)   # (never hit: %% is not in shown text)
            i += len(tok)
        elif v[i] == "%":
            out.append("%%"); i += 1
        else:
            out.append(v[i]); i += 1
    return "".join(out)


def export(root, out_path):
    lang_paths, js = paths(root)
    data = {loc: json.load(io.open(p, encoding="utf-8")) for loc, p in lang_paths.items()}
    lines, groups_done = [], set()
    lines.append("# River Fishing — the guide shelf, three languages. Edit the text under any --- block;")
    lines.append("# keep the === and --- lines as they are. A % is a %. Import with tools/guide_text.py.")
    lines.append("")
    for group, page in shelf(js):
        if group not in groups_done:
            groups_done.add(group)
            k = "guidegroup.riverfishing.%d" % group
            lines.append("################ %d · %s" % (group, data["en_us"].get(k, "?")))
            lines.append("=== " + k)
            for loc in LOCALES:
                lines.append("--- " + loc)
                lines.append(shown(data[loc].get(k, "")))
            lines.append("")
        lines.append("======== " + page)
        for suf in SUFFIXES:
            k = "guide.riverfishing.%s.%s" % (page, suf)
            if not any(k in data[loc] for loc in LOCALES):
                continue
            lines.append("=== " + k)
            for loc in LOCALES:
                lines.append("--- " + loc)
                lines.append(shown(data[loc].get(k, "")))
            lines.append("")
    text = "\n".join(lines) + "\n"
    io.open(out_path, "w", encoding="utf-8", newline="\n").write(text)
    # the round trip must be a no-op, or the file is not safe to hand out
    parsed = parse(text)
    for k, blocks in parsed.items():
        for loc, v in blocks.items():
            if k in data[loc]:
                assert stored(v) == data[loc][k], "round trip broke %s %s" % (loc, k)
    n = sum(1 for k in parsed if k.startswith("guide."))
    print("exported %d guide keys + %d group headings to %s (round trip verified)"
          % (n, len(groups_done), out_path))


def parse(text):
    """{key: {loc: text}} — tolerant of CRLF, a BOM, and trailing blank lines in a block."""
    text = text.replace("\r\n", "\n").lstrip("﻿")
    out, key, loc, buf = {}, None, None, []

    def flush():
        if key and loc is not None:
            v = "\n".join(buf)
            v = re.sub(r"\n+$", "", v)          # blank lines before the next marker are separators
            out.setdefault(key, {})[loc] = v

    for line in text.split("\n"):
        if line.startswith("=== "):
            flush(); key, loc, buf = line[4:].strip(), None, []
        elif line.startswith("--- ") and key:
            flush(); loc, buf = line[4:].strip(), []
        elif line.startswith("################") or line.startswith("======== ") or line.startswith("# ") and key is None:
            flush(); key, loc, buf = None, None, []
        elif loc is not None:
            buf.append(line)
    flush()
    return out


def do_import(root, in_path):
    lang_paths, _ = paths(root)
    parsed = parse(io.open(in_path, encoding="utf-8").read())
    changed = 0
    for loc, p in lang_paths.items():
        raw = io.open(p, encoding="utf-8").read()
        data = json.load(io.open(p, encoding="utf-8"))
        assert json.dumps(data, ensure_ascii=False, indent=2) + "\n" == raw, "%s is not json.dumps(indent=2)" % loc
        for k, blocks in parsed.items():
            if loc not in blocks or not blocks[loc].strip():
                continue
            v = stored(blocks[loc])
            if k not in data:
                print("  new key %s [%s]" % (k, loc))
            if data.get(k) != v:
                data[k] = v
                changed += 1
        io.open(p, "w", encoding="utf-8", newline="\n").write(json.dumps(data, ensure_ascii=False, indent=2) + "\n")
    print("imported: %d strings changed" % changed)
    export(root, in_path)


if __name__ == "__main__":
    if len(sys.argv) < 3 or sys.argv[1] not in ("export", "import"):
        print(__doc__); sys.exit(2)
    root = sys.argv[3] if len(sys.argv) > 3 else os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    (export if sys.argv[1] == "export" else do_import)(root, sys.argv[2])
