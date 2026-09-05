# -*- coding: utf-8 -*-
"""The interactive genetics calculator embedded in the published wiki (genetics.md).

Imported by tools/gen_wiki_bundle.py, which swaps the `<!-- GENETICS -->` marker on genetics.md for
widget(lang). On GitHub the marker stays an invisible comment and the page reads as prose.

Every rule here is the game's own, copied from Genome.java / AquariumBreeding.java / Pattern.java —
not a description of them:

  * a cross draws ONE allele from each parent per locus, no linkage, no mutation      — Genome.cross
  * carp variety: K dominant -> (N dominant ? linear : scaled), else (naked : mirror)  — Genome.variety
  * NN never develops                                                                  — Genome.lethal
  * koi variety: the KOI_TABLE read top to bottom, first row that fits                 — Genome.koiVariety
  * clutch: max(4, round((10 + 30 r / 2) x fertility)), ff 0.6 / Ff 1.0 / FF 1.5      — Genome.clutch
    then x1.25 fed rich, x3/4 when both parents carry N, x cross strength              — AquariumBreeding.clutch
  * hatch: vv 0.5 / Vv 0.7 / VV 0.9, a snag pile +0.15 capped 0.95, never below 1 fry — AquariumBreeding.hatch
  * the roe carries ONE genome for the whole clutch                                    — AquariumBreeding
  * pattern: floor((m + f) / 2) + round(gaussian x 12), clamped 0..999                 — Pattern.inherit

The names of varieties, families and gems are read from the lang files at build time, so the widget
calls a fish what the game calls it in that language.
"""
import io, json, os

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
LANG = os.path.join(REPO, "common/src/main/resources/assets/riverfishing/lang")
LOCALE = {"en": "en_us", "ru": "ru_ru", "uk": "uk_ua"}

# Genome.KOI_TABLE, verbatim: W R B G T, '_' at least one dominant, lowercase none, '*' either way.
KOI_TABLE = [
    "W_R_bbG*tt=tancho", "wwrrbbG_T*=yamabuki", "W_rrbbG_T*=ogon", "W_R_bbG_T*=sakura_ogon",
    "W_R_B_G_T*=yamatonishiki", "wwR_B_G_T*=kin_showa", "W_rrB_G_T*=gin_bekko", "wwrrB_G_T*=kujaku",
    "wwR_bbG_T*=kin_hi_utsuri", "W_R_bbG*T*=kohaku", "W_R_B_G*T*=taisho_sanke", "wwR_B_G*T*=showa",
    "W_rrB_G*T*=bekko", "wwrrB_G*T*=asagi", "W_rrbbG*T*=platinum", "wwR_bbG*T*=hi_utsuri", "wwrrbbG*T*=karasu",
]
# Pattern.BAND / FAMILY / GEM_AT / GEM
BAND = [0, 90, 200, 290, 400, 480, 560, 640, 710, 780, 850, 930]
FAMILY = ["plain", "drift", "crown", "banded", "speckled", "mask", "marbled", "veined", "dappled", "ghost", "ember", "aurora"]
GEM_AT = [13, 127, 239, 341, 439, 512, 601, 677, 733, 811, 887, 971]
GEM = ["sapphire", "gold", "emerald", "jet", "amethyst", "pearl", "ruby", "copper", "jade", "amber", "opal", "obsidian"]

T = {
    "en": {
        "blurb": "Set the two parents the way their cards write them and the calculator answers what the roe "
                 "will carry — every rule is the game's own. One cross, one genome for the whole clutch: the "
                 "odds below are the odds of the clutch, not of each fry.",
        "kind": "Fish", "any": "Any fish (4 loci)", "carp": "Carp family (6 loci)", "koi": "Koi (11 loci)",
        "mother": "Mother ♀", "father": "Father ♂", "paste": "…or paste the card's genes",
        "weight": "Mother's weight, × the species' ordinary", "fed": "Groundbait in the tank / fish meal last (+25 % eggs)",
        "snag": "Snag pile module (+15 % hatch)", "cross": "Cross", "same": "same species (×1.0)",
        "pm": "Pattern index, mother", "pf": "Pattern index, father", "nopat": "leave blank for a fish with no pattern",
        "loci": "The loci", "child": "Roe genotype odds", "pheno": "What it looks like", "clutch": "The clutch",
        "pattern": "The pattern", "true": "breeds true", "carrier": "carriers", "dead": "dead eggs (NN)",
        "eggs": "eggs", "fry": "fry", "expected": "expected", "hatch": "hatch", "gem": "gem of any kind",
        "family": "family", "mean": "mean index", "dom": "dominant trait shows", "rec": "recessive shows",
        "L": {"S": "size", "C": "colour (morphs)", "V": "vigour", "F": "fertility", "K": "scaled / mirror",
              "N": "nude (lethal doubled)", "W": "white ground", "R": "red (hi)", "B": "black (sumi)",
              "G": "lustre", "T": "crown (recessive)"},
        "note_lethal": "Both parents carry N: a quarter of the eggs are NN and never develop — the game lays a quarter fewer.",
        "note_koi": "The whole clutch is ONE variety. The list is what it can be, with the odds.",
        "pool": {"0.9": "bream × white bream (×0.9)", "0.8": "sturgeons — bester (×0.8)", "0.5": "salmon × trout / char (×0.5)",
                 "0.35": "zander × volga zander, roach × rudd (×0.35)", "0.3": "whitefish × nelma (×0.3)", "0.2": "the other breams (×0.2)"},
    },
    "ru": {
        "blurb": "Задайте обоих родителей так, как записано на их карточках, и калькулятор скажет, что понесёт "
                 "икра — все правила игровые. Одно скрещивание — один генотип на всю кладку: проценты ниже — "
                 "шансы кладки, а не каждого малька.",
        "kind": "Рыба", "any": "Любая рыба (4 локуса)", "carp": "Карповые (6 локусов)", "koi": "Кои (11 локусов)",
        "mother": "Самка ♀", "father": "Самец ♂", "paste": "…или вставьте гены с карточки",
        "weight": "Вес самки, × обычный вес вида", "fed": "Прикормка в баке / последний корм — рыбная мука (+25 % икры)",
        "snag": "Модуль «коряжник» (+15 % выклева)", "cross": "Скрещивание", "same": "один вид (×1.0)",
        "pm": "Индекс узора, самка", "pf": "Индекс узора, самец", "nopat": "пусто — рыба без узора",
        "loci": "Локусы", "child": "Шансы генотипа икры", "pheno": "Как это выглядит", "clutch": "Кладка",
        "pattern": "Узор", "true": "чистая линия", "carrier": "носители", "dead": "мёртвые икринки (NN)",
        "eggs": "икринок", "fry": "мальков", "expected": "ожидание", "hatch": "выклев", "gem": "самоцвет любого вида",
        "family": "семейство", "mean": "средний индекс", "dom": "проявится доминантный признак", "rec": "проявится рецессивный",
        "L": {"S": "размер", "C": "окрас (морфы)", "V": "живучесть", "F": "плодовитость", "K": "чешуйчатый / зеркальный",
              "N": "голый (летален в паре)", "W": "белый фон", "R": "красный (хи)", "B": "чёрный (суми)",
              "G": "блеск", "T": "корона (рецессив)"},
        "note_lethal": "Оба родителя несут N: четверть икринок — NN, они не развиваются; игра откладывает на четверть меньше.",
        "note_koi": "Вся кладка — ОДНА разновидность. В списке — какой она может быть и с какими шансами.",
        "pool": {"0.9": "лещ × густера (×0.9)", "0.8": "осетровые — бестер (×0.8)", "0.5": "лосось × форель / голец (×0.5)",
                 "0.35": "судак × берш, плотва × краснопёрка (×0.35)", "0.3": "сиг × нельма (×0.3)", "0.2": "остальные лещи (×0.2)"},
    },
    "uk": {
        "blurb": "Задай обох батьків так, як записано на їхніх картках, і калькулятор скаже, що понесе ікра — "
                 "усі правила ігрові. Одне схрещування — один генотип на всю кладку: відсотки нижче — шанси "
                 "кладки, а не кожного малька.",
        "kind": "Риба", "any": "Будь-яка риба (4 локуси)", "carp": "Коропові (6 локусів)", "koi": "Кої (11 локусів)",
        "mother": "Самиця ♀", "father": "Самець ♂", "paste": "…або встав гени з картки",
        "weight": "Вага самиці, × звичайна вага виду", "fed": "Підгодівля в баку / останній корм — рибне борошно (+25 % ікри)",
        "snag": "Модуль «коряжник» (+15 % виклеву)", "cross": "Схрещування", "same": "один вид (×1.0)",
        "pm": "Індекс візерунка, самиця", "pf": "Індекс візерунка, самець", "nopat": "порожньо — риба без візерунка",
        "loci": "Локуси", "child": "Шанси генотипу ікри", "pheno": "Як це виглядає", "clutch": "Кладка",
        "pattern": "Візерунок", "true": "чиста лінія", "carrier": "носії", "dead": "мертві ікринки (NN)",
        "eggs": "ікринок", "fry": "мальків", "expected": "очікування", "hatch": "виклев", "gem": "самоцвіт будь-якого виду",
        "family": "родина", "mean": "середній індекс", "dom": "проявиться домінантна ознака", "rec": "проявиться рецесивна",
        "L": {"S": "розмір", "C": "забарвлення (морфи)", "V": "живучість", "F": "плодючість", "K": "лускатий / дзеркальний",
              "N": "голий (летальний у парі)", "W": "білий фон", "R": "червоний (хі)", "B": "чорний (сумі)",
              "G": "блиск", "T": "корона (рецесив)"},
        "note_lethal": "Обоє батьків несуть N: чверть ікринок — NN, вони не розвиваються; гра відкладає на чверть менше.",
        "note_koi": "Уся кладка — ОДИН різновид. У списку — яким він може бути і з якими шансами.",
        "pool": {"0.9": "лящ × плоскирка (×0.9)", "0.8": "осетрові — бестер (×0.8)", "0.5": "лосось × форель / голець (×0.5)",
                 "0.35": "судак × берш, плітка × краснопірка (×0.35)", "0.3": "сиг × нельма (×0.3)", "0.2": "інші лящі (×0.2)"},
    },
}

CSS = """
.rfcalc.rfgen .row>div{min-width:0;max-width:100%}
.rfcalc.rfgen .wbox{min-width:0}
.rfgen .parents{display:grid;grid-template-columns:repeat(auto-fit,minmax(300px,1fr));gap:14px;margin-top:8px}
.rfgen .parent{border:1px solid var(--rule,#d6dbd5);border-radius:5px;padding:10px 12px;background:var(--paper,#f2f4f1)}
.rfgen .parent h4{margin:0 0 8px;font-size:12px;letter-spacing:.08em;text-transform:uppercase;color:var(--brass,#8a5e1e)}
.rfgen .loci{display:grid;grid-template-columns:repeat(auto-fill,minmax(150px,1fr));gap:6px 10px}
.rfcalc.rfgen .loci label{font-size:11px;margin-bottom:2px;text-transform:none;letter-spacing:0}
.rfcalc.rfgen select{min-width:0;font-size:14px;padding:4px 6px;width:100%}
.rfcalc.rfgen input[type=text],.rfcalc.rfgen input[type=number]{font:inherit;font-size:14px;padding:5px 8px;border:1px solid var(--rule,#d6dbd5);
 border-radius:4px;background:var(--paper,#f2f4f1);color:var(--ink,#171c1a);width:100%;box-sizing:border-box}
.rfgen .paste{margin-top:8px}
.rfgen .opts{display:flex;gap:16px;flex-wrap:wrap;align-items:center;margin-top:10px;font-size:14px}
.rfcalc.rfgen .opts label{display:inline;text-transform:none;letter-spacing:0;font-size:14px;color:var(--ink,#171c1a)}
.rfgen table{border-collapse:collapse;font-size:13.5px;width:100%}
.rfgen td,.rfgen th{padding:3px 6px;text-align:left;border-bottom:1px solid var(--rule,#d6dbd5)}
.rfgen th{font-weight:400;color:var(--dim,#5c6660);font-size:11px;text-transform:uppercase;letter-spacing:.06em}
.rfgen td.n{text-align:right;font-variant-numeric:tabular-nums;white-space:nowrap;padding-left:10px}
.rfgen td:first-child{width:38%}
.rfgen .tag{font-size:10px;letter-spacing:.06em;text-transform:uppercase;color:var(--brass,#8a5e1e);white-space:nowrap}
.rfgen .bar{display:inline-block;height:8px;background:var(--brass-lift,#b0812f);vertical-align:middle;margin-right:6px;border-radius:2px}
"""

JS = r"""
(function(){
  var T=RFGEN_T, L=RFGEN_LANG, N=RFGEN_NAMES, KT=RFGEN_KOI, BAND=RFGEN_BAND, FAM=RFGEN_FAM, GEM_AT=RFGEN_GEM_AT, GEM=RFGEN_GEM;
  var KIND={any:"SCVF",carp:"SCVFKN",koi:"SCVFKNWRBGT"};
  // Genome.pair(): what a card that does not carry the locus reads as. 2 = XX, 1 = Xx, 0 = xx.
  var DEF={S:1,C:1,V:1,F:1,K:2,N:0,W:2,R:1,B:0,G:0,T:2};
  var root=document.getElementById('rfg-'+L), out=document.getElementById('rfg-out-'+L);
  var kindSel=root.querySelector('.kind'), fed=root.querySelector('.fed'), snag=root.querySelector('.snag'),
      cross=root.querySelector('.cross'), wr=root.querySelector('.wr'), wn=root.querySelector('.wn'),
      pm=root.querySelector('.pm'), pf=root.querySelector('.pf');
  function loci(){ return KIND[kindSel.value]; }
  function txt(loc,st){ var u=loc,l=loc.toLowerCase(); return st==2?u+u:st==1?u+l:l+l; }
  function buildParent(box){
    var grid=box.querySelector('.loci'); grid.innerHTML='';
    loci().split('').forEach(function(loc){
      var d=document.createElement('div'), lab=document.createElement('label'), s=document.createElement('select');
      lab.textContent=loc+' — '+T.L[loc]; s.setAttribute('data-l',loc);
      [2,1,0].forEach(function(st){var o=document.createElement('option');o.value=st;o.textContent=txt(loc,st);s.appendChild(o);});
      s.value=DEF[loc]; s.addEventListener('change',render); d.appendChild(lab); d.appendChild(s); grid.appendChild(d);
    });
  }
  function readParent(box){ var g={}; box.querySelectorAll('select[data-l]').forEach(function(s){g[s.getAttribute('data-l')]=+s.value;}); return g; }
  function paste(box){ // "Ss Cc VV ff KK Nn" -> the selects; tokens the string does not carry keep their default
    var v=box.querySelector('.paste input').value.trim().split(/\s+/), order="SCVFKNWRBGT";
    v.forEach(function(tok,i){ if(tok.length!=2) return; var loc=order[i]; var s=box.querySelector('select[data-l="'+loc+'"]'); if(!s) return;
      var a=tok[0]===tok[0].toUpperCase(), b=tok[1]===tok[1].toUpperCase(); s.value=(a?1:0)+(b?1:0); });
    render();
  }
  function dist(sm,sf){ var p=sm/2,q=sf/2; var d2=p*q, d0=(1-p)*(1-q); return [d0,1-d2-d0,d2]; }
  function pct(x){ return (Math.round(x*1000)/10)+' %'; }
  function bar(x){ return '<span class="bar" style="width:'+Math.round(x*80)+'px"></span>'; }
  function erf(x){ var s=x<0?-1:1; x=Math.abs(x); var t=1/(1+0.3275911*x);
    var y=1-(((((1.061405429*t-1.453152027)*t)+1.421413741)*t-0.284496736)*t+0.254829592)*t*Math.exp(-x*x); return s*y; }
  function cdf(z){ return 0.5*(1+erf(z/Math.SQRT2)); }
  function koiName(states){ // states: {W,R,B,G,T} -> variety id, Genome.koiMatch on each row
    for(var r=0;r<KT.length;r++){ var row=KT[r].split('=')[0], ok=true; "WRBGT".split('').forEach(function(loc,i){
        var want=row[i*2+1], dom=states[loc]>0; if(want==='*') return; if(want==='_'?!dom:dom) ok=false; });
      if(ok) return KT[r].split('=')[1]; }
    return 'karasu';
  }
  function render(){
    var m=readParent(root.querySelector('.mother')), f=readParent(root.querySelector('.father')), ls=loci();
    wn.textContent='× '+(+wr.value).toFixed(2);
    var D={}; ls.split('').forEach(function(loc){ D[loc]=dist(m[loc],f[loc]); });
    // 1. the loci
    var rows=ls.split('').map(function(loc){ var d=D[loc];
      var trueLine=(d[0]>0.999||d[2]>0.999)?' <span class="tag">'+T['true']+'</span>':'';
      return '<tr><td><b>'+loc+'</b> <span class="k">'+T.L[loc]+'</span>'+trueLine+'</td>'+
        '<td class="n">'+txt(loc,2)+' '+pct(d[2])+'</td><td class="n">'+txt(loc,1)+' '+pct(d[1])+'</td><td class="n">'+txt(loc,0)+' '+pct(d[0])+'</td></tr>'; }).join('');
    var c=[];
    c.push([T.child,'<table><tr><th>'+T.loci+'</th><th></th><th></th><th></th></tr>'+rows+'</table>']);
    // 2. phenotype
    var ph='';
    ["S","C","V","F"].forEach(function(loc){ var d=D[loc]; ph+='<p><b>'+loc+'</b> <span class="k">'+T.L[loc]+':</span> '+T.dom+' '+pct(d[1]+d[2])+', '+T.rec+' '+pct(d[0])+'</p>'; });
    var dead=0, note='';
    if(kindSel.value!=='any'){
      var K=D.K, Nn=D.N; dead=Nn[2]; var alive=1-dead;
      var vs={scaled:(K[1]+K[2])*Nn[0]/alive, linear:(K[1]+K[2])*Nn[1]/alive, mirror:K[0]*Nn[0]/alive, naked:K[0]*Nn[1]/alive};
      ph+='<table>'+["scaled","mirror","linear","naked"].map(function(v){return '<tr><td>'+N.variety[v]+'</td><td class="n">'+bar(vs[v])+pct(vs[v])+'</td></tr>';}).join('')+
        (dead>0?'<tr><td class="k">'+T.dead+'</td><td class="n">'+pct(dead)+'</td></tr>':'')+'</table>';
      if(m.N>0&&f.N>0) note=T.note_lethal;
    }
    if(kindSel.value==='koi'){
      var acc={}, K5="WRBGT".split('');
      (function rec(i,st,p){ if(i==5){ var v=koiName(st); acc[v]=(acc[v]||0)+p; return; }
        var d=D[K5[i]]; for(var s=0;s<3;s++) if(d[s]>0){ st[K5[i]]=s; rec(i+1,st,p*d[s]); } })(0,{},1);
      var ks=Object.keys(acc).sort(function(a,b){return acc[b]-acc[a];});
      ph+='<table>'+ks.map(function(v){return '<tr><td>'+N.koi[v]+'</td><td class="n">'+bar(acc[v])+pct(acc[v])+'</td></tr>';}).join('')+'</table>';
      ph+='<p class="k">'+T.note_koi+'</p>';
    }
    c.push([T.pheno,ph]);
    // 3. the clutch — AquariumBreeding.clutch, then hatch over the roe's own V
    var r=Math.max(0,Math.min(2,+wr.value)), base=10+30*r/2, fert=m.F==0?0.6:m.F==2?1.5:1.0;
    var eggs=Math.max(4,Math.round(base*fert)); if(fed.checked) eggs=Math.round(eggs*1.25);
    if(m.N>0&&f.N>0) eggs=Math.max(1,Math.floor(eggs*3/4));
    eggs=Math.max(1,Math.round((+cross.value)*eggs));
    var V=D.V, surv=[0.5,0.7,0.9].map(function(s){return snag.checked?Math.min(0.95,s+0.15):s;});
    var exp=0, hs=''; for(var s=0;s<3;s++){ if(V[s]<=0) continue; var n=Math.max(1,Math.round(eggs*surv[s])); exp+=V[s]*n;
      hs+='<p>'+txt('V',s)+' <span class="k">'+pct(V[s])+':</span> '+T.hatch+' '+pct(surv[s])+' → <b>'+n+'</b> '+T.fry+'</p>'; }
    c.push([T.clutch,'<p><b>'+eggs+'</b> '+T.eggs+'</p>'+hs+'<p><span class="k">'+T.expected+':</span> <b>'+Math.round(exp)+'</b> '+T.fry+'</p>']);
    // 4. the pattern — Pattern.inherit: floor((m+f)/2) + round(N(0,12)), clamped
    var a=pm.value.trim(), b=pf.value.trim();
    if(a!==''||b!==''){ var ia=a===''?null:Math.max(0,Math.min(999,+a|0)), ib=b===''?null:Math.max(0,Math.min(999,+b|0));
      if(ia===null) ia=ib; if(ib===null) ib=ia;
      var mean=Math.floor((ia+ib)/2), SD=12;
      var fam=BAND.map(function(lo,i){ var hi=(i+1<BAND.length?BAND[i+1]:1000)-1;
        var pLo=lo==0?0:cdf((lo-0.5-mean)/SD), pHi=hi==999?1:cdf((hi+0.5-mean)/SD); return [FAM[i],pHi-pLo]; })
        .filter(function(x){return x[1]>0.0005;}).sort(function(x,y){return y[1]-x[1];});
      var gem=0; GEM_AT.forEach(function(g,i){ var p=cdf((g+0.5-mean)/SD)-cdf((g-0.5-mean)/SD); gem+=p; });
      c.push([T.pattern,'<p><span class="k">'+T.mean+':</span> <b>'+mean+'</b> ± 12</p>'+
        '<table>'+fam.map(function(x){return '<tr><td>'+N.pattern[x[0]]+'</td><td class="n">'+bar(x[1])+pct(x[1])+'</td></tr>';}).join('')+'</table>'+
        '<p><span class="k">'+T.gem+':</span> <b>'+(Math.round(gem*10000)/100)+' %</b></p>']);
    }
    out.innerHTML='<div class="cards">'+c.map(function(x){return '<div class="card"><h4>'+x[0]+'</h4>'+x[1]+'</div>';}).join('')+'</div>'+
      (note?'<div class="note warn">'+note+'</div>':'');
  }
  function rebuild(){ buildParent(root.querySelector('.mother')); buildParent(root.querySelector('.father')); render(); }
  kindSel.addEventListener('change',rebuild);
  [fed,snag,cross,wr,pm,pf].forEach(function(e){ e.addEventListener('input',render); e.addEventListener('change',render); });
  root.querySelectorAll('.parent').forEach(function(box){ box.querySelector('.paste input').addEventListener('change',function(){paste(box);}); });
  rebuild();
})();
"""


def names(lang):
    d = json.load(io.open(os.path.join(LANG, LOCALE[lang] + ".json"), encoding="utf-8"))
    koi = {r.split("=")[1]: d.get("variety.riverfishing.koi_" + r.split("=")[1], r.split("=")[1]) for r in KOI_TABLE}
    variety = {v: d.get("variety.riverfishing." + v, v) for v in ("scaled", "mirror", "linear", "naked")}
    pattern = {f: d.get("pattern.riverfishing." + f, f) for f in FAMILY}
    gem = {g: d.get("gem.riverfishing." + g, g) for g in GEM}
    return {"koi": koi, "variety": variety, "pattern": pattern, "gem": gem}


def widget(lang, emit_css=True):
    t = T.get(lang, T["en"])
    j = lambda x: json.dumps(x, ensure_ascii=False, separators=(",", ":"))
    pools = "".join('<option value="%s">%s</option>' % (k, v) for k, v in t["pool"].items())

    def parent(cls, label):
        return ('<div class="parent %s"><h4>%s</h4><div class="loci"></div>'
                '<div class="paste"><input type="text" placeholder="%s" spellcheck="false"></div></div>' % (cls, label, t["paste"]))
    return (
        ("<style>" + CSS + "</style>\n" if emit_css else "") +
        '<div class="rfcalc rfgen" id="rfg-' + lang + '">\n'
        '  <p>' + t["blurb"] + '</p>\n'
        '  <div class="row"><div><label>' + t["kind"] + '</label><select class="kind">'
        '<option value="any">' + t["any"] + '</option><option value="carp">' + t["carp"] + '</option>'
        '<option value="koi" selected>' + t["koi"] + '</option></select></div>\n'
        '    <div class="wbox"><label>' + t["weight"] + '<span class="wnum wn"></span></label>'
        '<input type="range" class="wr" min="0" max="2" step="0.05" value="1"></div></div>\n'
        '  <div class="parents">' + parent("mother", t["mother"]) + parent("father", t["father"]) + '</div>\n'
        '  <div class="opts"><label><input type="checkbox" class="fed"> ' + t["fed"] + '</label>'
        '<label><input type="checkbox" class="snag"> ' + t["snag"] + '</label>'
        '<span><label>' + t["cross"] + ' </label><select class="cross"><option value="1">' + t["same"] + '</option>' + pools + '</select></span></div>\n'
        '  <div class="row" style="margin-top:10px"><div><label>' + t["pm"] + '</label><input type="number" class="pm" min="0" max="999" placeholder="—"></div>'
        '<div><label>' + t["pf"] + '</label><input type="number" class="pf" min="0" max="999" placeholder="—"></div>'
        '<div class="k" style="font-size:12px;color:var(--dim,#5c6660)">' + t["nopat"] + '</div></div>\n'
        '  <div class="rfg-out" id="rfg-out-' + lang + '"></div>\n'
        '</div>\n'
        "<script>\nvar RFGEN_LANG=" + j(lang) + ";var RFGEN_T=" + j(t) + ";var RFGEN_NAMES=" + j(names(lang)) +
        ";var RFGEN_KOI=" + j(KOI_TABLE) + ";var RFGEN_BAND=" + j(BAND) + ";var RFGEN_FAM=" + j(FAMILY) +
        ";var RFGEN_GEM_AT=" + j(GEM_AT) + ";var RFGEN_GEM=" + j(GEM) + ";\n" + JS + "</script>\n")


if __name__ == "__main__":
    # a runnable check: the koi table names every variety, and the widget builds in three languages
    import re
    ids = {r.split("=")[1] for r in KOI_TABLE}
    assert len(ids) == 17, ids
    for lang in ("en", "ru", "uk"):
        w = widget(lang)
        assert 'id="rfg-%s"' % lang in w and "RFGEN_NAMES" in w
        n = names(lang)
        assert len(n["koi"]) == 17 and len(n["pattern"]) == 12 and len(n["gem"]) == 12
        # every lang key resolved to a real name, not its id
        assert all(re.search(r"[A-Za-zА-Яа-яЇїІіЄєҐґ]", v) for v in n["koi"].values())
    print("wiki_genetics: 17 varieties, 12 families, 12 gems, widgets in en/ru/uk")
