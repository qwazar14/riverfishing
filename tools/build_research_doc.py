# -*- coding: utf-8 -*-
"""Turn the research workflow's journal into one readable reference document.

Sixteen agent результата (eight research angles plus eight screening passes) hold 250 idea entries, 147
of them unique. Grouping is by THEME rather than by agent, because the eight angles overlapped heavily —
the same "show the player the hidden state" idea arrived from the sims, the minigames and the onboarding
angle at once. Bucketing is keyword-driven and therefore approximate; the source line on every entry is
what actually matters, since it is what makes an idea checkable.
"""
import io, json, os, re, sys

JOURNAL = (r"C:/Users/Qwazar/.claude/projects/C--Users-Qwazar-VS-Code-Projects-fishing-mod"
           r"/90de1bb2-9f80-4489-9c72-5d61cae57414/subagents/workflows/wf_f3e868cd-3bd/journal.jsonl")
OUT = sys.argv[1] if len(sys.argv) > 1 else "docs/RESEARCH_0.7.0.md"

# (heading, keywords). First match wins, so the order is the priority order.
BUCKETS = [
    # Порядок = приоритет: сначала узкие темы, иначе широкие ключи съедают всё.
    ("Виды ловли и снасти, которых в моде нет",
     ["fly fishing", "tip-up", "tip up", "trotline", "longline", "spearfish", "bowfish",
      "crabbing", "lobster", "shrimp", "noodling", "downrigger", "planer", "tenkara",
      "drop-shot", "dead-bait", "kayak", "float tube", "magnet fishing", "cast net",
      "handline", "bivvy", "fyke", "keepnet", "landing net", "match fishing", "peg"]),
    ("Бой, снасть и слабое звено",
     ["tension", "weakest", "hook hold", "hook set", "drag", "friction brake", "pump",
      "turn resistance", "stress bar", "tear meter", "stamina", "rod angle", "snap",
      "line strain", "overheat", "greed decision", "checkpoint"]),
    ("Поклёвка: как её прочитать, не нарушая тишины",
     ["nibble", "pre-bite", "taps", "the take", "ripple", "shadow size", "tremble",
      "rod tip", "surface sign", "bubble trail", "dimpl", "float plunge", "bell",
      "rings a bell", "bite legibility", "shoal", "showing", "head-and"]),
    ("Коллекция, рекорды, морфы",
     ["record slot", "record grid", "record board", "world record", "state record",
      "records only count", "рекорд", "slam", "morph", "aberration", "albino", "xanthic",
      "melanist", "otolith", "gyotaku", "tag-and-release", "tagging programme", "pin item",
      "percentile", "personal best", "line-class", "line class", "encyclopa", "compendium",
      "museum", "bestiary", "relative weight", "condition factor", "age class", "igfa",
      "pokemon mark", "pokémon mark", "collection slot", "journal slot"]),
    ("Обучение, диагностика, вскрытие ошибок",
     ["ledger", "autopsy", "tooltip", "reveal stage", "tips and tricks", "goggles", "ponder",
      "moodle", "teach", "diegetic", "onboarding", "assist", "session diary", "check mark",
      "harvest check", "negative space", "super guide", "explains", "stat report", "hit-chance",
      "failure message", "why you failed", "rumor mode", "ship log"]),
    ("Вода и условия: погода, давление, прозрачность",
     ["barometr", "барометр", "pressure", "thermocline", "oxygen", "clarity", "turbid",
      "muddy water", "wind direction", "cold front", "moon phase", "solunar", "spawning",
      "water temperature", "weather radio", "lunar calendar", "clearer water"]),
    ("Презентация: глубина, дно, точка, прикормка",
     ["depth in centim", "fishing depth", "substrate", "bed type", "bottom type",
      "line clip", "marker", "plumb", "sounding", "groundbait", "overfeed", "feeding spot",
      "echo sounder", "fish finder", "sonar", "buoy", "depth contour", "leader length"]),
]
FALLBACK = "Прочее: снаряжение, экономика, мир"


def bucket_of(idea):
    blob = " ".join(str(idea.get(k, "")) for k in ("idea", "howItWorks", "whyItFits", "source")).lower()
    for name, keys in BUCKETS:
        if any(k in blob for k in keys):
            return name
    return FALLBACK


def main():
    sets = []
    for line in io.open(JOURNAL, encoding="utf-8"):
        o = json.loads(line)
        if o.get("type") != "result":
            continue
        r = o["result"]
        if isinstance(r, str):
            try:
                r = json.loads(r)
            except Exception:
                continue
        if isinstance(r, dict) and "ideas" in r:
            sets.append(r)

    uniq, order = {}, []
    for s in sets:
        for i in s["ideas"]:
            k = i["idea"].strip().lower()[:70]
            if k not in uniq:
                uniq[k] = i
                order.append(k)

    groups = {}
    for k in order:
        groups.setdefault(bucket_of(uniq[k]), []).append(uniq[k])

    srcs = []
    for s in sets:
        srcs.extend(s.get("sourcesRead") or [])
    seen = set()
    srcs = [x for x in srcs if not (x in seen or seen.add(x))]

    eff_order = {"S": 0, "M": 1, "L": 2}
    L = []
    L.append("# Исследование к 0.7.0: идеи из рыболовных игр, модов и реальной рыбалки")
    L.append("")
    L.append("Собрано восемью агентами с живым веб-поиском, затем прогнано через восемь отсеивающих")
    L.append("проходов, которые выбрасывали всё, что **уже есть в моде**, всё **серверное** (владелец это")
    L.append("исключил) и всё, что не сделать имеющимися средствами: пиксель-арт 16×16 и 256px, JSON-модели,")
    L.append("палитровые свапы и геометрия из `GL_LINES`. Ни 3D-моделей, ни анимационных ригов, ни записи звука.")
    L.append("")
    L.append("**250 записей, 147 уникальных.** По трудоёмкости для одного человека: "
             "%d малых, %d средних, %d больших. Все 147 работают в одиночной игре."
             % (sum(1 for i in uniq.values() if (i.get("effort") or "").strip().upper().startswith("S")),
                sum(1 for i in uniq.values() if (i.get("effort") or "").strip().upper().startswith("M")),
                sum(1 for i in uniq.values() if (i.get("effort") or "").strip().upper().startswith("L"))))
    L.append("")
    L.append("Что нужно знать о надёжности: Reddit закрыт для этого краулера, часть гайдов Steam и Fandom")
    L.append("отдавали 403 — там агенты работали по выдержкам из поиска и это помечали. Комментарии")
    L.append("CurseForge тоже недоступны, поэтому «о чём просят игроки» опирается на форумы Steam и GitHub-issues.")
    L.append("Группировка по темам — моя, приблизительная: восемь направлений сильно пересекались. Ценность")
    L.append("каждой записи в строке **Источник** — именно она делает идею проверяемой.")
    L.append("")
    L.append("---")
    L.append("")
    L.append("## Оглавление")
    L.append("")
    for name in [b[0] for b in BUCKETS] + [FALLBACK]:
        if name in groups:
            L.append("- [%s](#%s) — %d" % (name, re.sub(r"[^a-zа-яёєіїґ0-9]+", "-", name.lower()), len(groups[name])))
    L.append("")

    for name in [b[0] for b in BUCKETS] + [FALLBACK]:
        if name not in groups:
            continue
        items = sorted(groups[name],
                       key=lambda i: eff_order.get((i.get("effort") or "?").strip().upper()[:1], 9))
        L.append("---")
        L.append("")
        L.append("## %s" % name)
        L.append("")
        L.append("*%d идей.*" % len(items))
        L.append("")
        for i in items:
            e = (i.get("effort") or "?").strip().upper()[:1]
            L.append("### %s  `%s`" % (i["idea"].strip().rstrip("."), e))
            L.append("")
            L.append("**Как работает.** %s" % i.get("howItWorks", "—").strip())
            L.append("")
            if i.get("whyItFits"):
                L.append("**Почему подходит.** %s" % i["whyItFits"].strip())
                L.append("")
            L.append("**Источник.** %s" % i.get("source", "—").strip())
            L.append("")

    L.append("---")
    L.append("")
    L.append("## Источники, которые агенты действительно прочитали")
    L.append("")
    L.append("%d ссылок и страниц. Помеченные как «search extract» читались через выдержку поиска, "
             "потому что прямой запрос вернул 403 или ошибку сертификата." % len(srcs))
    L.append("")
    for s in srcs:
        L.append("- %s" % s)
    L.append("")

    io.open(OUT, "w", encoding="utf-8", newline="\n").write("\n".join(L))
    print("-> %s" % OUT)
    print("   %d идей в %d разделах, %d источников" % (len(uniq), len(groups), len(srcs)))
    for name in [b[0] for b in BUCKETS] + [FALLBACK]:
        if name in groups:
            print("     %-46s %d" % (name, len(groups[name])))
    return 0


if __name__ == "__main__":
    sys.exit(main())
