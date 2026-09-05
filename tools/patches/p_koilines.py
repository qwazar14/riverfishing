# -*- coding: utf-8 -*-
"""§koi-lines: koi breed the way a breeder's koi breed — a line breeds true, a cross sorts itself out.

    py -X utf8 tools/patches/p_koilines.py <root>

The author, shown that a kohaku pond threw platinum, tancho and hi utsuri: "сделай как в реальной
жизни". In real life a kohaku spawn gives kohaku, some plain white (shiro muji), the odd tancho —
and never a metallic fish or a black-based one; those are other LINES, and surprises come only from
crossing lines. Three things in the model said otherwise:

  1. Tancho was `WW RR bb` — a kohaku pure at both its loci. So a kohaku could never be pure, every
     kohaku carried hidden recessives by construction, and kohaku × kohaku threw 44%% not-kohaku.
     Tancho is its own recessive now: T, and `tt` puts the crown on a kohaku. It comes out of a
     tancho line and nowhere else, which is what "bred, not found" was meant to mean.
  2. koiGenome() wrote every `_` as a coin — a wild kohaku was Ww half the time, i.e. half of them
     carried the BLACK GROUND. A pond koi is a mongrel, but at the red and the black (a kohaku drops a
     shiro muji, a sanke a bekko — that is a real spawn); its ground and its lustre are what its line
     is, and stay pure.
  3. The non-metallic white koi was called "Platinum". Platinum is the metallic white; the matt white
     is shiro muji. Names only — the ids on every card stay what they were.

Old worlds: a card written with four koi pairs reads its crown from the old rule (WW RR bb → tt), so
a bred tancho is still a tancho, and a cross with a koi parent writes the whole string so that reading
does not outlive the parents.
"""
import io, json, os, re, sys

ROOT = sys.argv[1]
J = os.path.join(ROOT, "common/src/main/java/com/riverfishing")
LANG = os.path.join(ROOT, "common/src/main/resources/assets/riverfishing/lang")
G = os.path.join(J, "fish/Genome.java")

s = io.open(G, encoding="utf-8").read()
if "§koi-lines" in s:
    print("  Genome.java: already patched")
else:
    def sub(old, new):
        global s
        assert s.count(old) == 1, "Genome.java anchor: %r" % old[:60]
        s = s.replace(old, new, 1)

    sub('    public static final String LOCI = "SCVFKNWRBG";', '    public static final String LOCI = "SCVFKNWRBGT";   // §koi-lines: T is the tancho crown')
    sub('    private static final String KOI_LOCI = "WRBG";', '    private static final String KOI_LOCI = "WRBGT";')

    # the table: tancho is the recessive crown on a kohaku; every other row does not look at T
    old_rows = re.search(r"    private static final String\[\] KOI_TABLE = \{\n(.*?)\n    \};", s, re.S)
    assert old_rows, "KOI_TABLE moved"
    body = old_rows.group(1)
    assert '"WWRRbbG*=tancho",' in body
    body = body.replace('"WWRRbbG*=tancho",', '"W_R_bbG*tt=tancho",   // §koi-lines: the crown is tt, a kohaku that carries it twice', 1)
    body = re.sub(r'"([WwRrBbGg_*]{8})=(\w+)"', lambda m: '"%sT*=%s"' % (m.group(1), m.group(2)), body)
    s = s.replace(old_rows.group(1), body, 1)

    # the writer: pure where the line is pure, mixed where a real spawn is mixed
    old = re.search(r"    public static String koiGenome\(String base, String variety, Random rng\) \{.*?\n    \}\n", s, re.S)
    assert old, "koiGenome moved"
    s = s.replace(old.group(0), '''    /**
     * §koi-lines: the allele a koi carries at a locus its row does not name — the COMMON one. Matt (g)
     * and crownless (T): a fish nobody bred for the lustre has none, and one nobody bred for the crown
     * carries the ordinary dominant T that hides it. Indexed like {@link #KOI_LOCI}.
     */
    private static final String KOI_COMMON = "wrbgT";

    /**
     * §koi-lines: the loci a koi out of the water is MIXED at. Its ground and its lustre are what its
     * line is — a kohaku pair never throws a dark fish or a metallic one — but the red and the black
     * ride as carriers the way a real spawn's do: a kohaku line drops a shiro muji, a sanke a bekko.
     */
    private static final String KOI_MIXED = "RB";

    public static String koiGenome(String base, String variety, Random rng) {
        String head = base.trim();
        if (pairs(head) < 6) head = head + " KK nn";     // a koi is a carp, and a bred koi is scaled
        String row = koiRow(variety);
        StringBuilder b = new StringBuilder(head);
        for (int i = 0; i < KOI_LOCI.length(); i++) {
            char L = KOI_LOCI.charAt(i), l = Character.toLowerCase(L), want = row.charAt(i * 2 + 1);
            String pair;
            if (want == '*') { char c = KOI_COMMON.charAt(i); pair = "" + c + c; }
            else if (want == l) pair = "" + l + l;
            else if (want == L) pair = "" + L + L;
            else pair = KOI_MIXED.indexOf(L) >= 0 && rng.nextBoolean() ? "" + L + l : "" + L + L;
            b.append(' ').append(pair);
        }
        return b.toString();
    }
''', 1)

    # an unknown variety name fell back to KOI_TABLE[1] — which has been yamabuki since the metallic rows
    sub("        return KOI_TABLE[1];      // kohaku: the archetype, and what an unknown name should look like",
        "        for (String row : KOI_TABLE) if (row.endsWith(\"=kohaku\")) return row;   // the archetype\n        return KOI_TABLE[0];")

    # the reader: a card with no crown pair reads it from the old rule
    sub('''                : locus == 'W' ? "WW" : locus == 'R' ? "Rr" : locus == 'B' ? "bb" : "" + l + l;
    }''', '''                : locus == 'W' ? "WW" : locus == 'R' ? "Rr" : locus == 'B' ? "bb"
                // §koi-lines: a card from before the crown locus reads it from the rule it was written
                // under — WW RR bb WAS a tancho, and a bred tancho must not wake up a kohaku.
                : locus == 'T' ? (legacyTancho(genome) ? "tt" : "TT") : "" + l + l;
    }

    /** §koi-lines: four koi pairs, pure white and pure red and no black — a tancho by the old rule. */
    private static boolean legacyTancho(String genome) {
        return token(genome, LOCI.indexOf('T')) == null && "WW".equals(token(genome, LOCI.indexOf('W')))
                && "RR".equals(token(genome, LOCI.indexOf('R'))) && "bb".equals(token(genome, LOCI.indexOf('B')));
    }''')

    # a cross with a koi parent writes the whole string, so the legacy reading stops at the parents
    sub("        int n = Math.max(4, Math.max(pairs(mother), pairs(father)));",
        "        int n = Math.max(4, Math.max(pairs(mother), pairs(father)));\n"
        "        // §koi-lines: a koi parent means the whole string, crown pair included — pair() answers the\n"
        "        // old-rule crown for a legacy card, and the child then carries it written down.\n"
        "        if (token(mother, LOCI.indexOf('W')) != null || token(father, LOCI.indexOf('W')) != null) n = LOCI.length();")

    sub("     * §koi-genes: what the WATER gives, {@code variety=weight}. Platinum and tancho are missing on\n"
        "     * purpose — both need a homozygote a wild pond never fixes, so they are BRED, not found.",
        "     * §koi-genes: what the WATER gives, {@code variety=weight}. Shiro muji and tancho are missing on\n"
        "     * purpose — the crown is a recessive no wild line carries, and a plain white koi is a cull a\n"
        "     * breeder never lets out — so they are BRED, not found (§koi-lines).")
    io.open(G, "w", encoding="utf-8", newline="\n").write(s)
    print("  Genome.java: T locus, pure lines, mixed red/black, legacy tancho")

# ---- names and the pages that state the rule -------------------------------------------------------
EDITS = {
    "en_us": [
        ("variety.riverfishing.koi_platinum", "Platinum", "Shiro Muji"),
        ("variety.riverfishing.koi_ogon", "Ogon", "Platinum Ogon"),
        ("fishdesc.riverfishing.koi_carp", "the two you will not find wild, platinum and tancho.", "the two you will not find wild, shiro muji and tancho."),
        ("fishdesc.riverfishing.koi_carp", "platinum  W_ rr bb — plain white", "shiro muji  W_ rr bb — plain white"),
        ("fishdesc.riverfishing.koi_carp", "tancho  WW RR bb — one red crown — pure at the white AND the red", "tancho  W_ R_ bb tt — one red crown — the recessive t, out of a tancho line"),
        ("guide.riverfishing.genes.text",
         "A koi carries four more: W white ground, R red, B black, G the lustre. Seventeen varieties, read top down — the first row that fits names the fish. Tancho is no locus of its own but WW RR bb, pure at the white and the red at once, so a kohaku pair throws half kohaku, one tancho in sixteen, and the rest out of what was hiding in it. Nine of the seventeen",
         "A koi carries five more: W white ground, R red, B black, G the lustre, T the crown. Seventeen varieties, read top down — the first row that fits names the fish. Tancho is the recessive tt on a kohaku — one red crown and nothing else — and it comes only out of a tancho line: a kohaku pair cannot throw one. A koi out of the water is pure at its ground and its lustre, so kohaku from kohaku is kohaku and never a dark fish or a metallic one; it is a mongrel at the red and the black, and the card shows it (Rr, Bb) — that is where a kohaku line drops a shiro muji and a sanke drops a bekko. Pick parents pure on the card and the line breeds true; cross two lines and the second generation sorts itself by Mendel. Nine of the seventeen"),
    ],
    "ru_ru": [
        ("variety.riverfishing.koi_platinum", "Платинум", "Сиро мудзи"),
        ("variety.riverfishing.koi_ogon", "Огон", "Платинум огон"),
        ("fishdesc.riverfishing.koi_carp", "и два, которых в дикой воде нет: платинум и тантю.", "и два, которых в дикой воде нет: сиро мудзи и тантё."),
        ("fishdesc.riverfishing.koi_carp", "платинум  W_ rr bb — чисто белый", "сиро мудзи  W_ rr bb — чисто белый"),
        ("fishdesc.riverfishing.koi_carp", "тантё  WW RR bb — одно алое пятно на лбу — чистота и по белому, и по алому", "тантё  W_ R_ bb tt — одно алое пятно на лбу — рецессивный t, из линии тантё"),
        ("guide.riverfishing.genes.text",
         "Карп кои несёт ещё четыре: W белый фон, R красный, B чёрный, G блеск. Семнадцать разновидностей, и таблица читается сверху вниз — рыбу называет первая подошедшая строка. Тантё — не отдельный локус, а WW RR bb, чистота по белому и красному сразу; поэтому пара кохаку даёт половину кохаку, одно тантё из шестнадцати, а остальное — то, что в ней пряталось. Из воды выходят",
         "Карп кои несёт ещё пять: W белый фон, R красный, B чёрный, G блеск, T пятно на лбу. Семнадцать разновидностей, и таблица читается сверху вниз — рыбу называет первая подошедшая строка. Тантё — рецессивный tt у кохаку: одно алое пятно на лбу и ничего больше, и берётся оно только из линии тантё — пара кохаку его не даст. Кои из воды чист по фону и по блеску, поэтому кохаку от кохаку — кохаку, и никогда не тёмная рыба и не металлик; а по красному и чёрному он дворняга, и карточка это показывает (Rr, Bb) — отсюда у линии кохаку изредка сиро мудзи, а у санкэ — бекко. Возьми родителей, чистых по карточке, — и линия закрепится; скрести две линии — и второе поколение разложится по Менделю. Из воды выходят"),
    ],
    "uk_ua": [
        ("variety.riverfishing.koi_platinum", "Платинум", "Сіро мудзі"),
        ("variety.riverfishing.koi_ogon", "Оґон", "Платинум оґон"),
        ("fishdesc.riverfishing.koi_carp", "і два, яких у дикій воді немає: платинум і тантю.", "і два, яких у дикій воді немає: сіро мудзі й тантьо."),
        ("fishdesc.riverfishing.koi_carp", "платинум  W_ rr bb — чисто білий", "сіро мудзі  W_ rr bb — чисто білий"),
        ("fishdesc.riverfishing.koi_carp", "тантьо  WW RR bb — одна червона пляма на чолі — чистота й за білим, й за червоним", "тантьо  W_ R_ bb tt — одна червона пляма на чолі — рецесивний t, з лінії тантьо"),
        ("guide.riverfishing.genes.text",
         "Короп кої несе ще чотири: W білий фон, R червоний, B чорний, G полиск. Сімнадцять різновидів, і таблиця читається згори вниз — рибу називає перший рядок, що підійшов. Тантьо — не окремий локус, а WW RR bb, чистота за білим і червоним одразу; тому пара кохаку дає половину кохаку, одне тантьо з шістнадцяти, а решту — те, що в ній ховалося. З води виходять",
         "Короп кої несе ще п'ять: W білий фон, R червоний, B чорний, G полиск, T пляма на чолі. Сімнадцять різновидів, і таблиця читається згори вниз — рибу називає перший рядок, що підійшов. Тантьо — рецесивний tt у кохаку: одна червона пляма на чолі й нічого більше, і береться воно лише з лінії тантьо — пара кохаку його не дасть. Кої з води чистий за фоном і за полиском, тому кохаку від кохаку — кохаку, і ніколи не темна риба й не металік; а за червоним і чорним він дворняга, і картка це показує (Rr, Bb) — звідси в лінії кохаку зрідка сіро мудзі, а в санке — бекко. Візьми батьків, чистих за карткою, — і лінія закріпиться; схрести дві лінії — і друге покоління розкладеться за Менделем. З води виходять"),
    ],
}
done = 0
for loc, edits in EDITS.items():
    p = os.path.join(LANG, loc + ".json")
    raw = io.open(p, encoding="utf-8").read()
    d = json.load(io.open(p, encoding="utf-8"))
    assert json.dumps(d, ensure_ascii=False, indent=2) + "\n" == raw, "%s is not json.dumps(indent=2)" % loc
    for k, old, new in edits:
        v = d[k]
        if old in v:
            d[k] = v.replace(old, new, 1); done += 1
        elif new in v or v == new:
            pass
        else:
            raise AssertionError("%s %s: neither old nor new present — %r" % (loc, k, old[:50]))
    io.open(p, "w", encoding="utf-8", newline="\n").write(json.dumps(d, ensure_ascii=False, indent=2) + "\n")
print("  lang: %d edits" % done)

# ---- the docs that state the rule (present in the primary tree only) -------------------------------
DOCS = {
    "docs/wiki/genetics.md": [
        ("| **Tancho** | `WW RR bb` | one red crown on white | bred |", "| **Tancho** | `W_ R_ bb tt` | one red crown on white — the recessive crown, out of a tancho line | bred |"),
        ("| **Ogon** | `W_ rr bb G_` | solid metallic white | bred |", "| **Platinum Ogon** | `W_ rr bb G_` | solid metallic white | bred |"),
        ("| **Platinum** | `W_ rr bb` |", "| **Shiro Muji** | `W_ rr bb` |"),
        ("| **W** | the white ground |", "| **W** | the white ground |\n| **T** | the tancho crown — recessive, `tt` shows it |"),
    ],
    "docs/patchnotes/0.9.0.md": [
        ("| Tancho | `WW RR bb` | one red crown on white | bred |", "| Tancho | `W_ R_ bb tt` | one red crown on white — a recessive, out of a tancho line | bred |"),
        ("| Ogon | `W_ rr bb G_` | solid metallic white | bred |", "| Platinum Ogon | `W_ rr bb G_` | solid metallic white | bred |"),
        ("Tancho is not a variety of its own but the cross that goes homozygous at the white and\nthe red at once, so every red pigment lands in a single crown spot;",
         "Tancho is a fifth locus, a recessive: `tt` puts the single crown on a kohaku, and it comes out of a\ntancho line and nowhere else;"),
    ],
    "docs/design/breeding-api.md": [
        ("**Tancho** is not a fourth locus: it is `WW RR bb` (homozygous white ground, homozygous red, no black)\n— the cross that concentrates every red pigment into a single spot. Rare because it needs both\nhomozygotes at once, which is exactly why it is prized.",
         "**Tancho** is a fifth locus, T, recessive: `tt` on a kohaku puts every red pigment into a single crown.\nA kohaku line carries `TT` and cannot throw one; a tancho line breeds it true, and a tancho × kohaku\ncross carries it hidden into the second generation (§koi-lines — the first draft made it `WW RR bb`,\nwhich meant a kohaku could never be pure and kohaku × kohaku threw 44% not-kohaku)."),
        ("| `W_ rr bb` | **platinum** | plain white — the blank sprite, and the rarest plain fish |", "| `W_ rr bb` | **platinum** (shown as *Shiro Muji* — platinum is the metallic white) | plain white — the blank sprite |"),
    ],
}
for rel, edits in DOCS.items():
    p = os.path.join(ROOT, rel)
    if not os.path.exists(p):
        continue
    t = io.open(p, encoding="utf-8").read()
    n = 0
    for old, new in edits:
        if old in t:
            t = t.replace(old, new, 1); n += 1
        elif new not in t:
            print("  %s: did not find %r" % (rel, old[:50]))
    io.open(p, "w", encoding="utf-8", newline="\n").write(t)
    print("  %s: %d edits" % (rel, n))
print("done")
