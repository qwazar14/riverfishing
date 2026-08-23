# -*- coding: utf-8 -*-
"""The interactive catch calculator embedded in the published wiki.

Imported by tools/gen_wiki_bundle.py, which swaps the `<!-- CALCULATOR -->` marker on calculator.md
for widget(lang). On GitHub the marker stays an invisible comment and the page reads as prose, which
is the honest split: markdown cannot run anything.

Every number the widget shows or computes is read from the fish profiles and the same constants the
game uses, so the calculator cannot answer differently from the engine:

  * required pull   max(0.5, fight.strength x (1 + kg) x 2)      — FishingManager
  * line strain     100 x d^2 x factor (mono 1.0, fluoro 1.1, braid 3.0)  — LineType
  * livebait floor  the size roll floors at 6x the bait's weight — FishingManager
  * lure floor      the same at 8x                                — FishingManager
  * coarse feed     only fraction above 0.5 flattens the size curve — FishingManager

The point of the thing is the question a wiki table cannot answer: not "what does a carp want" but
"what does a NINE KILO carp want", which is a different rig, a different line and a different feed.
"""
import io, json, os, re

STRAIN_K = 100.0
LINE_FACTOR = {"mono": 1.00, "fluoro": 1.10, "braid": 3.00}
LINE_SIZES = {
    "mono": [0.10, 0.14, 0.18, 0.25, 0.30, 0.40, 0.50, 0.60, 0.70, 0.80],
    "braid": [0.16, 0.20, 0.25, 0.30, 0.40, 0.50, 0.60],
    "fluoro": [0.14, 0.16, 0.20, 0.25, 0.30, 0.40],
}

T = {
    "en": {
        "fish": "Fish", "target": "Target weight", "median": "median", "trophy": "trophy",
        "where": "Where", "when": "When", "onwhat": "What on", "gear": "Gear", "feed": "Groundbait",
        "water": "Water", "biomes": "Biomes", "depth": "Depth", "width": "Width",
        "dist": "Cast distance", "layer": "Fishes at", "level": "Angler level",
        "season": "Season", "time": "Time of day", "weather": "Weather", "any": "any",
        "rods": "Rods", "reel": "Reel", "line": "Line", "rig": "Rigs", "hook": "Hook",
        "leader": "Leader needed", "yes": "yes", "no": "not needed",
        "pull": "This fish pulls", "lineneed": "Line from", "linecomfort": "comfortable from",
        "livebait": "Livebait from", "lure": "Lure from", "fraction": "Fraction", "nutrition": "Richness",
        "coarse": "Feed coarse — a big fraction calls big fish",
        "fine": "Fine feed is fine for this size",
        "note_big": "This is a big one for the species: expect the wait, and do not undersize the line.",
        "note_small": "Anything in your box will do at this size.",
        "overline": "beyond every line in the game — take the heaviest and open the drag", "gate": "needs level", "nogate": "no level gate",
        "blurb": "Pick a fish and the weight you are after. Everything below is read from that species' "
                 "profile and the game's own formulas.",
        "kg": "kg", "g": "g", "m": "m", "blocks": "blocks",
        "spring": "spring", "summer": "summer", "autumn": "autumn", "winter": "winter",
        "dawn": "dawn", "day": "day", "dusk": "dusk", "night": "night",
        "clear": "clear", "rain": "rain", "thunder": "thunder",
        "bottom": "bottom", "mid": "mid-water", "surface": "surface",
        "lake": "lake", "river": "river", "pond": "pond", "swamp": "swamp", "sea": "sea", "puddle": "puddle",
    },
    "ru": {
        "fish": "Рыба", "target": "Желаемый вес", "median": "медиана", "trophy": "трофей",
        "where": "Где", "when": "Когда", "onwhat": "На что", "gear": "Снасть", "feed": "Прикормка",
        "water": "Водоём", "biomes": "Биомы", "depth": "Глубина", "width": "Ширина",
        "dist": "Дистанция заброса", "layer": "Держится", "level": "Уровень рыбака",
        "season": "Сезон", "time": "Время суток", "weather": "Погода", "any": "любой",
        "rods": "Удилища", "reel": "Катушка", "line": "Леска", "rig": "Оснастки", "hook": "Крючок",
        "leader": "Поводок", "yes": "нужен", "no": "не нужен",
        "pull": "Тянет как", "lineneed": "Леска от", "linecomfort": "с запасом от",
        "livebait": "Живец от", "lure": "Приманка от", "fraction": "Фракция", "nutrition": "Питательность",
        "coarse": "Кормите крупно — большая фракция приманивает большую рыбу",
        "fine": "Для такого размера мелкая смесь годится",
        "note_big": "Для этого вида это крупный экземпляр: ждать дольше, леску не занижать.",
        "note_small": "На такой размер сгодится всё, что есть в коробке.",
        "overline": "сильнее любой лески в игре — берите самую толстую и открывайте фрикцион", "gate": "нужен уровень", "nogate": "без ограничения по уровню",
        "blurb": "Выберите рыбу и вес, который хотите взять. Всё ниже посчитано по профилю этого вида "
                 "и по формулам самой игры.",
        "kg": "кг", "g": "г", "m": "м", "blocks": "блоков",
        "spring": "весна", "summer": "лето", "autumn": "осень", "winter": "зима",
        "dawn": "рассвет", "day": "день", "dusk": "закат", "night": "ночь",
        "clear": "ясно", "rain": "дождь", "thunder": "гроза",
        "bottom": "у дна", "mid": "вполводы", "surface": "у поверхности",
        "lake": "озеро", "river": "река", "pond": "пруд", "swamp": "болото", "sea": "море", "puddle": "лужа",
    },
    "uk": {
        "fish": "Риба", "target": "Бажана вага", "median": "медіана", "trophy": "трофей",
        "where": "Де", "when": "Коли", "onwhat": "На що", "gear": "Снасть", "feed": "Прикормка",
        "water": "Водойма", "biomes": "Біоми", "depth": "Глибина", "width": "Ширина",
        "dist": "Дистанція закиду", "layer": "Тримається", "level": "Рівень рибалки",
        "season": "Сезон", "time": "Час доби", "weather": "Погода", "any": "будь-який",
        "rods": "Вудлища", "reel": "Котушка", "line": "Волосінь", "rig": "Оснастки", "hook": "Гачок",
        "leader": "Повідець", "yes": "потрібен", "no": "не потрібен",
        "pull": "Тягне як", "lineneed": "Волосінь від", "linecomfort": "із запасом від",
        "livebait": "Живець від", "lure": "Приманка від", "fraction": "Фракція", "nutrition": "Поживність",
        "coarse": "Годуйте крупно — велика фракція приваблює велику рибу",
        "fine": "Для такого розміру дрібний заміс годиться",
        "note_big": "Для цього виду це великий екземпляр: чекати довше, волосінь не занижувати.",
        "note_small": "На такий розмір згодиться все, що є в коробці.",
        "overline": "сильніше за будь-яку волосінь у грі — беріть найтовщу й відкривайте фрикціон", "gate": "потрібен рівень", "nogate": "без обмеження за рівнем",
        "blurb": "Оберіть рибу і вагу, яку хочете взяти. Усе нижче пораховано за профілем цього виду "
                 "та за формулами самої гри.",
        "kg": "кг", "g": "г", "m": "м", "blocks": "блоків",
        "spring": "весна", "summer": "літо", "autumn": "осінь", "winter": "зима",
        "dawn": "світанок", "day": "день", "dusk": "сутінки", "night": "ніч",
        "clear": "ясно", "rain": "дощ", "thunder": "гроза",
        "bottom": "біля дна", "mid": "у півводи", "surface": "біля поверхні",
        "lake": "озеро", "river": "річка", "pond": "став", "swamp": "болото", "sea": "море", "puddle": "калюжа",
    },
}


def _lines():
    """Every line the shop sells, with the strain the engine computes for it."""
    out = []
    for kind, sizes in LINE_SIZES.items():
        for d in sizes:
            out.append({"t": kind, "d": d, "kg": round(STRAIN_K * d * d * LINE_FACTOR[kind], 2)})
    out.sort(key=lambda x: x["kg"])
    return out


def species_data(profiles, roster, names_by_lang):
    """The per-species payload, shared by all three languages except the display name."""
    out = []
    for sid in roster:
        p = profiles.get(sid)
        if not p:
            continue
        i = p["ideal"]
        out.append({
            "id": sid,
            "n": {lang: names.get("fish.riverfishing." + sid, sid) for lang, names in names_by_lang.items()},
            "grp": p.get("group", "other"),
            "w": [p["weight_g"]["min"], p["weight_g"]["max"], p["weight_g"]["mean"]],
            "len": [p["length_cm"]["min"], p["length_cm"]["max"]],
            "str": p["fight"]["strength"],
            "pat": p["fight"].get("pattern", "steady"),
            "wb": {k: v for k, v in p["water_bodies"].items() if v > 0},
            "bio": p.get("biomes", {}),
            "hab": p.get("habitat", {}),
            "dist": p.get("distance_pref", {}),
            "layer": p.get("depth_pref", "bottom"),
            "lvl": p.get("min_angler_level", 0),
            "season": p["season"], "time": p["time"], "weather": p["weather"],
            "bait": i["bait"],
            "rod": i["rod"], "rig": i["rig"],
            "reel": [i["reel_size"], i["reel_tolerance"]],
            "line": [i["line"]["type"], i["line"]["diameter_mm"]],
            "hook": [i["hook"]["ideal"], i["hook"]["tolerance"]],
            "lead": bool(i.get("requires_leader")),
            "gb": [i["groundbait"]["fraction"], i["groundbait"]["nutrition"]] if isinstance(i.get("groundbait"), dict) else None,
            "leg": p.get("legendary", {}).get("weight_g"),
        })
    out.sort(key=lambda s: s["w"][1])
    return out


CSS = """
.rfcalc{--c-line:var(--rule,#d8d2c4);--c-dim:var(--dim,#6b6558);border:1px solid var(--c-line);
 border-radius:6px;padding:18px 20px;margin:20px 0;background:var(--raise,#fbf9f4)}
.rfcalc .row{display:flex;gap:18px;flex-wrap:wrap;align-items:flex-end;margin-bottom:6px}
.rfcalc label{display:block;font-size:12px;letter-spacing:.06em;text-transform:uppercase;
 color:var(--c-dim);margin-bottom:6px}
.rfcalc select,.rfcalc input[type=range]{font:inherit;padding:6px 8px;border:1px solid var(--c-line);
 border-radius:4px;background:var(--bg,#fff);color:inherit}
.rfcalc select{min-width:230px}
.rfcalc .wbox{flex:1 1 260px;min-width:220px}
.rfcalc input[type=range]{width:100%;padding:0}
.rfcalc .wnum{font-variant-numeric:tabular-nums;font-weight:600;margin-left:8px}
.rfcalc .cards{display:grid;grid-template-columns:repeat(auto-fit,minmax(240px,1fr));gap:14px;margin-top:16px}
.rfcalc .card{border:1px solid var(--c-line);border-radius:5px;padding:12px 14px;background:var(--bg,#fff)}
.rfcalc .card h4{margin:0 0 8px;font-size:12px;letter-spacing:.08em;text-transform:uppercase;color:var(--c-dim)}
.rfcalc .card p{margin:0 0 5px;font-size:14px;line-height:1.45}
.rfcalc .card p b{font-weight:600}
.rfcalc .k{color:var(--c-dim)}
.rfcalc .note{margin-top:14px;font-size:13.5px;line-height:1.5;padding:10px 12px;border-left:3px solid var(--c-line)}
.rfcalc .warn{border-left-color:#b0392b}
"""

JS = r"""
(function(){
  var D=RFCALC_DATA, L=RFCALC_LANG, T=RFCALC_T, LINES=RFCALC_LINES;
  var sel=document.getElementById('rfc-fish-'+L), rng=document.getElementById('rfc-w-'+L),
      num=document.getElementById('rfc-wn-'+L), out=document.getElementById('rfc-out-'+L);
  D.forEach(function(s,i){var o=document.createElement('option');o.value=i;o.textContent=s.n[L];sel.appendChild(o);});
  function wt(g){ return g>=1000 ? (Math.round(g/100)/10)+' '+T.kg : Math.round(g)+' '+T.g; }
  function top(o,n){ return Object.keys(o).sort(function(a,b){return o[b]-o[a];}).slice(0,n||3); }
  function best(o){ var m=Math.max.apply(null,Object.keys(o).map(function(k){return o[k];}));
    return Object.keys(o).filter(function(k){return o[k]>=m-1e-9;}); }
  function tr(list){ return list.map(function(k){return T[k]||k;}).join(', '); }
  // Thinnest line that still holds it — thin is less visible, and a 0.25 braid beats a 0.40 mono at
  // the same strain. null means the fish out-pulls every line in the game.
  function pickLine(kg){ var ok=LINES.filter(function(l){return l.kg>=kg;});
    if(!ok.length) return null;
    ok.sort(function(a,b){return a.d-b.d || a.kg-b.kg;}); return ok[0]; }
  function lineTxt(l){ return l ? '<b>'+l.t+' '+l.d.toFixed(2)+'</b> ('+l.kg+' '+T.kg+')' : '<b>'+T.overline+'</b>'; }

  function render(){
    var s=D[+sel.value], g=+rng.value;
    num.textContent=wt(g);
    var kg=g/1000, pull=Math.max(0.5, s.str*(1+kg)*2);
    var need=pickLine(pull), comfy=pickLine(pull*1.5);
    var span=s.w[1]-s.w[0], where=span>0?(g-s.w[0])/span:0;
    var baits=Object.keys(s.bait).sort(function(a,b){return s.bait[b]-s.bait[a];});
    var hab=s.hab||{}, dist=s.dist||{};
    var c=[];
    c.push(['where',
      '<p><span class="k">'+T.water+':</span> <b>'+top(s.wb,3).map(function(k){return T[k]||k;}).join(', ')+'</b></p>'+
      (Object.keys(s.bio).length?'<p><span class="k">'+T.biomes+':</span> '+top(s.bio,3).join(', ')+'</p>':'')+
      '<p><span class="k">'+T.depth+':</span> '+(hab.depth_min?hab.depth_min+'+':'—')+
      (hab.depth_max?' … '+hab.depth_max:'')+' '+T.blocks+'</p>'+
      '<p><span class="k">'+T.width+':</span> '+(hab.width_min?hab.width_min+'+':'—')+' '+T.blocks+'</p>'+
      '<p><span class="k">'+T.dist+':</span> '+(dist.min!=null?dist.min+'–'+dist.max:'—')+' '+T.blocks+'</p>'+
      '<p><span class="k">'+T.layer+':</span> '+(T[s.layer]||s.layer)+'</p>']);
    c.push(['when',
      '<p><span class="k">'+T.season+':</span> <b>'+tr(best(s.season))+'</b></p>'+
      '<p><span class="k">'+T.time+':</span> <b>'+tr(best(s.time))+'</b></p>'+
      '<p><span class="k">'+T.weather+':</span> '+tr(best(s.weather))+'</p>'+
      '<p><span class="k">'+T.level+':</span> '+(s.lvl?T.gate+' '+s.lvl:T.nogate)+'</p>']);
    var baitHtml=baits.slice(0,5).map(function(b){return '<p><b>'+b+'</b> <span class="k">'+s.bait[b].toFixed(2)+'</span></p>';}).join('');
    if(s.bait.livebait!=null) baitHtml+='<p><span class="k">'+T.livebait+':</span> <b>'+wt(g/6)+'</b></p>';
    var lureish=baits.some(function(b){return ['wobbler','spinner','spoon','silicone','jig','popper','crankbait','castmaster','giant_spoon','octopus_jig'].indexOf(b)>=0;});
    if(lureish) baitHtml+='<p><span class="k">'+T.lure+':</span> <b>'+wt(g/8)+'</b></p>';
    c.push(['onwhat', baitHtml]);
    c.push(['gear',
      '<p><span class="k">'+T.rods+':</span> <b>'+s.rod.join(', ')+'</b></p>'+
      '<p><span class="k">'+T.reel+':</span> '+s.reel[0]+' ±'+s.reel[1]+'</p>'+
      '<p><span class="k">'+T.pull+':</span> <b>'+pull.toFixed(1)+' '+T.kg+'</b></p>'+
      '<p><span class="k">'+T.lineneed+':</span> '+lineTxt(need)+
      (comfy?', '+T.linecomfort+' <b>'+comfy.t+' '+comfy.d.toFixed(2)+'</b>':'')+'</p>'+
      '<p><span class="k">'+T.rig+':</span> '+s.rig.join(', ')+'</p>'+
      '<p><span class="k">'+T.hook+':</span> №'+s.hook[0]+' ±'+s.hook[1]+'</p>'+
      '<p><span class="k">'+T.leader+':</span> '+(s.lead?T.yes:T.no)+'</p>']);
    if(s.gb){
      var coarse = where>0.5 ? T.coarse : T.fine;
      c.push(['feed','<p><span class="k">'+T.fraction+':</span> <b>'+s.gb[0].toFixed(2)+'</b></p>'+
        '<p><span class="k">'+T.nutrition+':</span> <b>'+s.gb[1].toFixed(2)+'</b></p>'+
        '<p>'+coarse+'</p>']);
    }
    var html='<div class="cards">'+c.map(function(x){
      return '<div class="card"><h4>'+T[x[0]]+'</h4>'+x[1]+'</div>';}).join('')+'</div>';
    html+='<div class="note'+(where>0.6?' warn':'')+'">'+(where>0.6?T.note_big:T.note_small)+'</div>';
    out.innerHTML=html;
  }
  function reset(){ var s=D[+sel.value]; rng.min=s.w[0]; rng.max=s.w[1];
    rng.step=Math.max(1,Math.round((s.w[1]-s.w[0])/200)); rng.value=s.w[2]; render(); }
  sel.addEventListener('change',reset); rng.addEventListener('input',render);
  sel.value=0; reset();
})();
"""


def widget(lang, profiles, roster, names_by_lang, emit_data=True):
    """One language's widget.

    All three sections live in ONE document on the published wiki, so the ids are suffixed per
    language — a shared id would point every widget at the English one. The species payload already
    carries all three names, so only the first call emits it: three copies of 91 profiles is most of
    a megabyte of nothing.
    """
    t = T.get(lang, T["en"])
    head = ("<style>" + CSS + "</style>\n") if emit_data else ""
    data = ("var RFCALC_DATA=" + json.dumps(species_data(profiles, roster, names_by_lang),
                                            ensure_ascii=False, separators=(",", ":")) + ";\n"
            + "var RFCALC_LINES=" + json.dumps(_lines(), separators=(",", ":")) + ";\n") if emit_data else ""
    return (
        head +
        '<div class="rfcalc">\n'
        '  <p>' + t["blurb"] + '</p>\n'
        '  <div class="row">\n'
        '    <div><label for="rfc-fish-' + lang + '">' + t["fish"] + '</label>'
        '<select id="rfc-fish-' + lang + '"></select></div>\n'
        '    <div class="wbox"><label for="rfc-w-' + lang + '">' + t["target"] +
        '<span class="wnum" id="rfc-wn-' + lang + '"></span></label>'
        '<input type="range" id="rfc-w-' + lang + '"></div>\n'
        '  </div>\n'
        '  <div id="rfc-out-' + lang + '"></div>\n'
        '</div>\n'
        "<script>\n" + data +
        "var RFCALC_LANG=" + json.dumps(lang) +
        ";\nvar RFCALC_T=" + json.dumps(t, ensure_ascii=False, separators=(",", ":")) +
        ";\n" + JS + "</script>\n")
