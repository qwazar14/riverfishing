# -*- coding: utf-8 -*-
"""Validate a translation of the mod's language file against en_us.json.

    python tools/check_lang.py                (every locale present)
    python tools/check_lang.py uk_ua

Minecraft never complains about a broken lang file, it just renders something wrong: a missing key shows
as the raw key, and a placeholder the translator dropped silently loses its number. So the things worth
checking mechanically are exactly the ones the game will not tell you about until a player finds them.

Note that Minecraft's placeholder syntax is much narrower than Java's `String.format`: only `%s`,
`%<n>$s` and `%%` exist. `TranslatableContents.decomposeTemplate` throws on anything else — including a
literal `%` — but `decompose()` catches that and falls back to rendering the template verbatim (verified
against the 26.1 bytecode). So `+5%/rank` is harmless *as long as the string takes no arguments*; the
same stray `%` in a string that does take arguments loses every one of them. That is why the check below
compares argument sequences rather than hunting for stray percent signs.

  * the key set matches en_us exactly (missing keys are visible bugs, extra keys are dead weight);
  * the placeholder sequence Minecraft will actually consume is identical to the English;
  * the same number of line breaks, because several tooltips are laid out by hand;
  * no lost `§` colour code;
  * labels have not grown so much longer than the English that they will overflow their GUI box.

Exit code is non-zero if anything in the first four categories fails, so this can gate a release.
"""
import io, json, os, re, sys

LANGDIR = "common/src/main/resources/assets/riverfishing/lang"
# net.minecraft.network.chat.contents.TranslatableContents.FORMAT_PATTERN, verbatim.
SPEC = re.compile(r"%(?:(\d+)\$)?([A-Za-z%]|$)")
# Prefixes whose strings sit in a fixed-width box: an item name, a button, a rank, an enum label.
TIGHT = ("item.", "block.", "rig.", "fish.", "rank.", "skill.", "screen.", "menu.", "hud.",
         "water.", "season.", "time.", "biomegroup.", "linetype.", "depthset.", "unit.", "itemGroup.")
SLACK = 1.6      # a Slavic label runs longer than English; past this it is a layout problem
FLOOR = 12       # short strings can double without troubling anything


def args(s):
    """The argument indices Minecraft will substitute, in order — or None if it gives up on the string.

    Mirrors decomposeTemplate: plain %s consumes the next argument, %<n>$s takes the n-th, %% is a
    literal, and any other % (including one standing alone) aborts the whole template.
    """
    out, auto, i = [], 0, 0
    for m in SPEC.finditer(s):
        if "%" in s[i:m.start()]:
            return None
        kind = m.group(2)
        if kind == "%" and m.group(0) == "%%":
            pass
        elif kind == "s":
            if m.group(1):
                out.append(int(m.group(1)) - 1)
            else:
                out.append(auto)
                auto += 1
        else:
            return None
        i = m.end()
    return None if "%" in s[i:] else out


def check(locale, en):
    path = os.path.join(LANGDIR, locale + ".json")
    tr = json.load(io.open(path, encoding="utf-8"))
    errs, warns = [], []

    missing = [k for k in en if k not in tr]
    extra = [k for k in tr if k not in en]
    if missing:
        errs.append("%d keys missing: %s%s" % (len(missing), ", ".join(missing[:8]),
                                              " …" if len(missing) > 8 else ""))
    if extra:
        errs.append("%d keys not in en_us: %s%s" % (len(extra), ", ".join(extra[:8]),
                                                   " …" if len(extra) > 8 else ""))

    for k in en:
        if k not in tr:
            continue
        a, b = en[k], tr[k]
        sa, sb = args(a), args(b)
        if sa != sb:
            errs.append("%s: Minecraft substitutes %s here, %s in English"
                        % (k, "nothing (a stray %% aborts the template)" if sb is None else sb,
                           "nothing" if sa is None else sa))
        if a.count("\n") != b.count("\n"):
            errs.append("%s: line-break count %d -> %d" % (k, a.count("\n"), b.count("\n")))
        if a.count("§") != b.count("§"):
            errs.append("%s: %d format codes -> %d" % (k, a.count("§"), b.count("§")))
        if k.startswith(TIGHT) and len(b) > max(FLOOR, len(a) * SLACK):
            warns.append("%s: %d chars vs %d in English — %r" % (k, len(b), len(a), b))
        # Proper nouns legitimately survive translation, and so does a string that is all placeholder.
        if b == a and not k.startswith(("item.", "fish.", "legendary.")) \
                and any(c.isalpha() for c in re.sub(r"%(?:\d+\$)?[A-Za-z%]", "", a)):
            warns.append("%s: identical to the English — untranslated?" % k)

    print("%s: %d keys, %d errors, %d warnings" % (locale, len(tr), len(errs), len(warns)))
    for e in errs:
        print("  ERROR " + e)
    for w in warns:
        print("  warn  " + w)
    return len(errs)


if __name__ == "__main__":
    en = json.load(io.open(os.path.join(LANGDIR, "en_us.json"), encoding="utf-8"))
    todo = sys.argv[1:] or sorted(f[:-5] for f in os.listdir(LANGDIR)
                                  if f.endswith(".json") and f != "en_us.json")
    sys.exit(min(1, sum(check(l, en) for l in todo)))
