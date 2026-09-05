# -*- coding: utf-8 -*-
"""§guide-audit: the guide pages say what the code does, in all three languages.

    py -X utf8 tools/patches/p_guideaudit.py <root>

Three agents read every guide page against the code and came back with findings; every one applied
here was re-verified by hand against the Java before it went in (the constants are named per edit).
What changed, and why it was wrong:

  community   rewritten. Stocking was "odds" — there are none since 0.9.0 (release(): no dice, refused
              outright at fit <= 0, settles on a brood + one window). Provinces were absent from a page
              titled "Every water is its own". The finder line was two versions old. Signature bite is
              x1.8 (FishingManager 3174), not "half again". RU said "вполсилы" where the code is 0.25.
  market      rewritten. Predates the contract board by six weeks; "recovers in a couple of days" is
              15%/day (DAILY_RECOVERY 0.85) — half the glut is still there after four days.
  nets        "no catch card" — every netted fish carries one that says NETTED / POACHED. The way back
              out of the debt (Warden: 5 kg per point into WILD water) and claimed ponds were missing.
  cull        "releasing one lifts the ban" — with §cull-gate it is refused at the bank instead.
  icefishing  there is no nod on any rod; the cue is the hole boiling. Bleak has winter 0.0 and could
              never be caught through ice yet sat in the table; the dashes hid real numbers; the burbot
              takes no mormyshka and nobody said what to use instead; the refreeze went unmentioned.
  keepnet     the taper rule was backwards (FishShape: three ACROSS, i.e. deep-bodied); a metre zander
              and a three-metre catfish do not exist (max 90 / 260 cm); medium OR large at level four.
  feeding     "twice as fast" and "40% off" in one sentence are the same number, and one was wrong.
  gbnumbers   the 1.00 ceiling is unreachable: the base is mandatory at 0.50, the real max is 0.94.
  stress      the kg message fires only when the rig is lost (loseChance 5-30%); the table's columns
              were exact but unlabelled for what they measure.
  drag        an open drag ALWAYS pays out line (the comment at 2222 says so); rows 10 and 15 kg name
              reels that do not exist and repeat row 5 — the bleed caps at 5 kg of drag.
  tacklebox   "seventeen line diameters, four rig types" — 23 line items, 11 rigs; uk dropped a line.
  thanks      the beluga shares level 12 with the whale shark now; oneshot's "setting" never existed —
              the 0.7.1 patchnote answers that the visible fish were never entities.
  discord     the wiki link pointed at the repo, not the published wiki.
  lurework    "no takes" on held RMB — the clock still advances, at a fifth of the rate.
  livebait    "~6x its weight" — the floor in the code is 4x; the code comment said 6 and is fixed too.
  uk          «поклювка» normalised to «поклівка», which is what the game's own messages say.

Left alone on purpose: "a third of the species list" in thanks (the author thanking a player; the
arithmetic says an eighth, the generosity is his), the вы/ты register split across pages, and three
nits (bread crumb vs loaf, soil vs clay grind, the feeder's +1 ingot) — none changes a decision.
"""
import io, json, os, sys

ROOT = sys.argv[1]
LANG = os.path.join(ROOT, "common/src/main/resources/assets/riverfishing/lang")
FM = os.path.join(ROOT, "common/src/main/java/com/riverfishing/fishing/FishingManager.java")
FZ = os.path.join(ROOT, "common/src/main/java/com/riverfishing/fishing/FeedZoneData.java")

data = {loc: json.load(io.open(os.path.join(LANG, loc + ".json"), encoding="utf-8")) for loc in ("en_us", "ru_ru", "uk_ua")}
raw = {loc: io.open(os.path.join(LANG, loc + ".json"), encoding="utf-8").read() for loc in data}
for loc in data:
    assert json.dumps(data[loc], ensure_ascii=False, indent=2) + "\n" == raw[loc], "%s is not json.dumps(indent=2)" % loc
done = {"applied": 0, "already": 0}


def rep(loc, page, old, new, suf="text"):
    """Replace `old` with `new` in one guide string. Idempotent: `new` already there counts as done."""
    k = "guide.riverfishing.%s.%s" % (page, suf)
    v = data[loc][k]
    if old in v:
        assert v.count(old) == 1, "%s %s: %r is not unique" % (loc, k, old[:40])
        data[loc][k] = v.replace(old, new)
        done["applied"] += 1
    elif new in v:
        done["already"] += 1
    else:
        raise AssertionError("%s %s: neither old nor new text present — %r" % (loc, k, old[:60]))


def rewrite(loc, page, marker, new, suf="text"):
    """Replace the whole string. `marker` is a fragment only the OLD text has."""
    k = "guide.riverfishing.%s.%s" % (page, suf)
    v = data[loc][k]
    if v == new:
        done["already"] += 1
    elif marker in v:
        data[loc][k] = new
        done["applied"] += 1
    else:
        raise AssertionError("%s %s: not the text this patch expects (no %r)" % (loc, k, marker))


# =================================================================================================
# community — rewritten; the province and stocking paragraphs are short because both have their own
# page now (geography, stocking) one shelf over.
rewrite("en_us", "community", "single-digit odds", """Each water has its own population: some species simply DON'T live here, while 1-3 are its signature fish — they bite nearly twice as eagerly.
A small pond is species-poor, a big river or lake is rich. On big water the fish hold in patches — find the productive holes and banks.
The ubiquitous smalls (rotan, roach, perch, bleak) live everywhere — no water is ever dead.

## THE PART OF THE WORLD
Above the weather there is geography: the world is cut into four faunal provinces, regions about three thousand blocks across, and a species that belongs to one is ABSENT from the others, not merely rare. Nothing you do on the bank puts a taimen and a peacock bass in the same river — travel to the fish, or bring it home and breed it. The sea is undivided.

## THE FISH FINDER
Three views on one face. The SECTION is the bed along the line you are aiming down, every fish drawn at the depth it holds, and a list beside it — click a species for what the mod knows about it here, including the gate that stops it biting. The CHART is the same bed from above, blank until somebody casts across it, and it zooms back far enough to show the province borders. The SAMPLE is everything measured that is not a fish: clarity, climate, the season's third, oxygen, cover, the ecosystem, the farm ledger.

## STOCKING
Missing a species? Stock the water — but it is breeding, not a dice roll. Water the fish cannot live in at all refuses it outright, and if the only thing wrong is the continent it says so. Everywhere else the fish goes on the ledger, and the species SETTLES for good when a brood — a mature female and a male, or thirty fry — lives through one whole spawn window in water that suits it at least halfway. Until then the transplant is TEMPORARILY catchable off the stock you banked, and disperses if the brood never completes. A species settled outside its element lives at quarter strength — but it lives.
Stock builds by weight: a trophy counts as three ordinary fish, fry count for almost nothing. Natives fatten up to 250%% bite, transplants to 150%%; an over-packed school (>125%%) thins a quarter faster.
Release your trophies — sport fishing is what feeds a water.""")

rewrite("ru_ru", "community", "единицы процентов", """У каждого водоёма — своё население: часть видов здесь просто НЕ ВОДИТСЯ, а 1–3 вида — фирменные, они клюют здесь почти вдвое охотнее.
Маленький прудик беден видами, большая река или озеро — богаты. На большой воде рыба стоит местами — ищи уловистые ямы и банки.
Вездесущая мелочь (ротан, плотва, окунь, уклейка) живёт везде — мёртвой воды не бывает.

## ЧАСТЬ СВЕТА
Над погодой есть география: мир разрезан на четыре фаунистических региона — области примерно по три тысячи блоков, — и вид, приписанный к одному, в остальных ОТСУТСТВУЕТ, а не просто редок. Ничто на берегу не поселит тайменя и павлиньего окуня в одну реку: езжай за рыбой или привези её к себе и разведи. Море не разделено.

## ЭХОЛОТ
Три вида на одном экране. РАЗРЕЗ — дно вдоль линии, куда ты целишься, каждая рыба на той глубине, где стоит, и список рядом: клик по виду покажет всё, что мод знает о нём здесь, включая ворота, которые не дают ему клевать. КАРТА — то же дно сверху, пустая, пока по ней никто не забрасывал, и отдаляется настолько, что видны границы регионов. ПРОБА ВОДЫ — всё измеренное, что не рыба: прозрачность, климат, треть сезона, кислород, укрытия, экосистема, книга хозяйства.

## ЗАРЫБЛЕНИЕ
Нет нужного вида? Зарыби водоём — но это разведение, а не бросок кубика. Вода, в которой рыба не выживет вовсе, отказывает сразу, а если не так только часть света — так и скажет. Везде иначе рыба идёт в книгу, и вид ПРИЖИВАЕТСЯ насовсем, когда стадо — половозрелые самка и самец или тридцать мальков — проживёт целое нерестовое окно в воде, подходящей ему хотя бы наполовину. До тех пор переселенец ЛОВИТСЯ временно, из того запаса, что ты внёс, а если стадо так и не доживёт — расходится. Прижившийся «не в своей» воде вид живёт на четверть силы — но живёт.
Запас копится по весу: трофей стоит трёх рядовых, мелочь — почти ничего. Родная рыба нагуливается до 250%% клёва, переселённая — до 150%%; перекормленную стаю (>125%%) выбивают на четверть быстрее.
Отпускай трофеи — спортивная рыбалка кормит водоём.""")

rewrite("uk_ua", "community", "одиниці відсотків", """У кожної водойми своє населення: частина видів тут просто НЕ ВОДИТЬСЯ, а 1–3 види — фірмові, вони клюють тут майже вдвічі охочіше.
Маленький ставок бідний на види, велика річка чи озеро — багаті. На великій воді риба стоїть місцями — шукай уловисті ями та мілини.
Всюдисуща дрібнота (ротань, плітка, окунь, верховодка) живе скрізь — мертвої води не буває.

## ЧАСТИНА СВІТУ
Над погодою є географія: світ розрізано на чотири фауністичні регіони — області приблизно по три тисячі блоків, — і вид, приписаний до одного, в інших ВІДСУТНІЙ, а не просто рідкісний. Ніщо на березі не поселить тайменя й павичевого окуня в одну річку: їдь за рибою або привези її до себе й розведи. Море не поділене.

## ЕХОЛОТ
Три види на одному екрані. РОЗРІЗ — дно вздовж лінії, куди ти цілишся, кожна риба на тій глибині, де стоїть, і список поруч: клік по виду покаже все, що мод знає про нього тут, включно з воротами, які не дають йому клювати. КАРТА — те саме дно згори, порожня, доки по ній ніхто не закидав, і віддаляється настільки, що видно межі регіонів. ПРОБА ВОДИ — усе виміряне, що не риба: прозорість, клімат, третина сезону, кисень, укриття, екосистема, книга господарства.

## ЗАРИБЛЕННЯ
Немає потрібного виду? Зарибни водойму — але це розведення, а не кидок кубика. Вода, в якій риба не виживе зовсім, відмовляє одразу, а якщо не та лише частина світу — так і скаже. Скрізь інде риба йде в книгу, і вид ПРИЖИВАЄТЬСЯ назавжди, коли стадо — статевозрілі самка й самець або тридцять мальків — проживе ціле нерестове вікно у воді, що підходить йому хоча б наполовину. Доти переселенець ЛОВИТЬСЯ тимчасово, з того запасу, що ти вніс, а якщо стадо так і не доживе — розходиться. Вид, що прижився не у своїй воді, живе на чверть сили — але живе.
Запас набирається вагою: трофей іде за трьох звичайних рибин, дрібнота — майже ні за що. Рідна риба нагулює до 250%% клювання, переселена — до 150%%; перенаселену зграю (>125%%) вибивають на чверть швидше.
Відпускай трофеї — водойму годує спортивна рибалка.""")

# =================================================================================================
# market — rewritten around the board
rewrite("en_us", "market", "in a couple of days", """The fisherman's prices are alive: every prime fish the server sells saturates that species' market a little, and a glutted one pays down to half. It recovers about 15%% a day — a fortnight coming back, not a night — so overfish the bream and the bream money dries up.
Every day he also names an ORDER OF THE DAY: that one species pays ×2.5, glut be damned.

## THE CONTRACT BOARD
Beside his counter stands a board, and that is where the money is. Each fisherman posts THREE jobs a day — his own board, so two fishermen are two boards — and the same three stand all day, for everyone, through a restart. Take one off it as a paper; you may carry two at a time.
The paper does not just name a fish, it names HOW: out of a river, on a float rod, on worm, after dark. Only a fish landed under those terms counts. The board prints the conditions as a checklist, ticked against where you are standing and what you are holding — read one and you have learnt the fish. Seven days to fill it.
Hand it back with the fish and he pays in emeralds, journal XP and REPUTATION. At 5, 15 and 30 points his counter opens a shelf the rest of the village never sees. Poaching takes that reputation away again.""")

rewrite("ru_ru", "market", "за пару дней", """Цены рыбака живые: каждая сданная на сервере рыба чуть насыщает рынок этого вида, и перенасыщенный вид он берёт вдвое дешевле. Рынок отходит примерно на 15%% в день — две недели, а не ночь, — так что выбили леща, и лещовые деньги кончились.
Каждый день он ещё и объявляет ЗАКАЗ ДНЯ: этот вид он берёт по ×2.5, вне зависимости от рынка.

## ДОСКА КОНТРАКТОВ
Рядом с прилавком стоит доска, и деньги — там. Каждый рыбак вывешивает ТРИ заказа в день — своя доска у каждого, два рыбака — две доски, — и те же три висят весь день для всех, хоть перезапускай. Снимаешь заказ бумагой; носить можно две сразу.
Бумага называет не только рыбу, но и КАК: из реки, на поплавок, на червя, ночью. Засчитывается только рыба, взятая на этих условиях. Доска печатает условия чек-листом, сверенным с тем, где ты стоишь и что держишь, — прочитал один, и рыбу ты выучил. Семь дней на выполнение.
Сдал вместе с рыбой — платит изумрудами, опытом журнала и РЕПУТАЦИЕЙ. На 5, 15 и 30 очках у прилавка открывается полка, которую остальная деревня не видит. Браконьерство эту репутацию забирает обратно.""")

rewrite("uk_ua", "market", "за пару днів", """Ціни в рибалки живі: кожна здана на сервері риба трохи насичує ринок цього виду, і перенасичений вид він бере вдвічі дешевше. Ринок відходить приблизно на 15%% за день — два тижні, а не ніч, — тож вибили ляща, і лящеві гроші скінчилися.
Щодня він ще й оголошує ЗАМОВЛЕННЯ ДНЯ: цей вид він бере за ×2.5, хай там що на ринку.

## ДОШКА КОНТРАКТІВ
Поруч із прилавком стоїть дошка, і гроші — там. Кожен рибалка вивішує ТРИ замовлення на день — своя дошка в кожного, два рибалки — дві дошки, — і ті самі три висять увесь день для всіх, хоч перезапускай. Знімаєш замовлення папером; носити можна два одразу.
Папір називає не лише рибу, а й ЯК: із річки, на поплавок, на черв'яка, вночі. Зараховується лише риба, взята на цих умовах. Дошка друкує умови чек-листом, звіреним із тим, де ти стоїш і що тримаєш, — прочитав один, і рибу ти вивчив. Сім днів на виконання.
Здав разом із рибою — платить смарагдами, досвідом журналу й РЕПУТАЦІЄЮ. На 5, 15 і 30 очках біля прилавка відкривається полиця, якої решта села не бачить. Браконьєрство цю репутацію забирає назад.""")

# =================================================================================================
# nets — the debt goes negative and can be worked off; a netted fish HAS a card
rep("en_us", "nets", "because the brood went with them.",
    "because the brood went with them. The debt is real and it goes NEGATIVE: under zero his trusted shelf shuts, and fifteen under it the board is blank. Time will not clear it and emeralds will not buy it off. Five kilograms of mature fish put back into WILD water buys one point — not into your own pond, where the fish is still yours and the village is no better off.")
rep("en_us", "nets",
    "Water you stocked yourself — a pair released and settled, or fry you put in — is yours to harvest, and there the net is exactly what it is for: the seine hauls one to three fish a minute, the cast net one every fifteen seconds, and each fish comes up as a plain fish with no catch card, because it was not caught. Both tools wear out.",
    "Water you stocked yourself — a pair released and settled, or fry you put in — is yours to harvest, and a pond you have claimed is yours entire, whatever the book says about the species in it. There the net is exactly what it is for: the seine hauls one to three fish a minute and lasts sixty-four hauls, the cast net at most one every fifteen seconds and tears after thirty-two, and on your own claimed water the wait between hauls is a third of what it is anywhere else — that pond is worked, not raided. Every fish still comes up with a card that says NETTED, and if the water was not yours, POACHED. It never loses that.")
rep("ru_ru", "nets", "— потому что вместе с ними ушла молодь.",
    "— потому что вместе с ними ушла молодь. Долг настоящий, и он уходит В МИНУС: ниже нуля закрывается доверенная полка, а на пятнадцать ниже — доска пуста. Время его не спишет и изумруды не выкупят. Одно очко — пять килограммов взрослой рыбы, отпущенной в ДИКУЮ воду; не в свой пруд, где рыба всё ещё ваша, а деревне от этого не легче.")
rep("ru_ru", "nets",
    "Вода, которую вы зарыбили сами — выпущенная и прижившаяся пара или запущенные мальки, — ваша, и её можно собирать; там сеть ровно для того и нужна: невод берёт от одной до трёх рыб в минуту, кастинговая сеть — одну раз в пятнадцать секунд, и каждая рыба поднимается просто рыбой, без карточки улова, потому что она не была поймана. Обе снасти изнашиваются.",
    "Вода, которую вы зарыбили сами — выпущенная и прижившаяся пара или запущенные мальки, — ваша, и её можно собирать, а закреплённый табличкой пруд ваш целиком, что бы книга ни говорила о видах в нём. Там сеть ровно для того и нужна: невод берёт от одной до трёх рыб в минуту и выдерживает шестьдесят четыре захода, кастинговая сеть — не больше одной раз в пятнадцать секунд и рвётся после тридцати двух, а на своём закреплённом пруду ожидание между заходами втрое короче, чем где-либо ещё: этот пруд обрабатывают, а не грабят. Каждая рыба всё равно поднимается с карточкой, где написано СЕТЬ, а если вода была не ваша — БРАКОНЬЕРСТВО. Это с неё уже не сотрётся.")
rep("uk_ua", "nets", "— бо разом із ними пішла молодь.",
    "— бо разом із ними пішла молодь. Борг справжній, і він іде В МІНУС: нижче нуля зачиняється довірена полиця, а на п'ятнадцять нижче — дошка порожня. Час його не спише і смарагди не викуплять. Одне очко — п'ять кілограмів дорослої риби, відпущеної в ДИКУ воду; не у свій ставок, де риба все ще ваша, а селу від цього не легше.")
rep("uk_ua", "nets",
    "Вода, яку ви зарибили самі — випущена й прижита пара або запущені мальки, — ваша, і її можна збирати; там сітка саме для того й потрібна: невід бере від однієї до трьох риб за хвилину, кастингова сітка — одну раз на п'ятнадцять секунд, і кожна риба піднімається просто рибою, без картки улову, бо її не було спіймано. Обидва знаряддя зношуються.",
    "Вода, яку ви зарибили самі — випущена й прижита пара або запущені мальки, — ваша, і її можна збирати, а закріплений табличкою ставок ваш цілком, хай що книга каже про види в ньому. Там сітка саме для того й потрібна: невід бере від однієї до трьох риб за хвилину й витримує шістдесят чотири заходи, кастингова сітка — не більше однієї раз на п'ятнадцять секунд і рветься після тридцяти двох, а на своєму закріпленому ставку очікування між заходами втричі коротше, ніж будь-де: цей ставок обробляють, а не грабують. Кожна риба все одно піднімається з карткою, де написано СІТКА, а якщо вода була не ваша — БРАКОНЬЄРСТВО. Це з неї вже не зітреться.")

# =================================================================================================
# cull — with §cull-gate, a released fish of a culled species is refused, not a way round the ban
rep("en_us", "cull",
    "Reversible both ways. A struck-through fish comes back on a second click, where you removed it. Releasing one of that species in the water lifts the ban too: a fish you can see swimming has to be catchable.",
    "Reversible from the screen. A struck-through fish comes back on a second click, where you removed it. Putting fish back in the water does not: a culled species is refused at the bank the way hostile water refuses one, and stays refused until whoever removed it puts it back.")
rep("ru_ru", "cull",
    "Обратимо в обе стороны. Вычеркнутая рыба возвращается вторым кликом там же, где вы её убрали. И отпущенная в этой воде рыба тоже снимает запрет: вид, который видно плавающим, обязан ловиться.",
    "Обратимо с экрана. Вычеркнутая рыба возвращается вторым кликом там же, где вы её убрали. Отпущенная в воду рыба запрет не снимает: вычеркнутый вид у берега отказывают так же, как отказывает враждебная вода, — пока тот, кто его убрал, не вернёт его сам.")
rep("uk_ua", "cull",
    "Оборотно в обидва боки. Викреслена риба повертається другим кліком там само, де ви її прибрали. І відпущена в цій воді риба теж знімає заборону: вид, який видно плавати, мусить ловитися.",
    "Оборотно з екрана. Викреслена риба повертається другим кліком там само, де ви її прибрали. Відпущена у воду риба заборони не знімає: викреслений вид біля берега відмовляють так само, як відмовляє ворожа вода, — доки той, хто його прибрав, не поверне його сам.")

# =================================================================================================
# icefishing — no nod; the refreeze; the burbot's bait; the table tells the truth about winter
rep("en_us", "icefishing",
    '## THE NOD\nThere is no bite sound and no "Bite!" text. The nod twitches, and that is the cue. Strike as you would on any other rod.',
    '## THE HOLE\nThere is no bite sound and no "Bite!" text, and nothing on the rod moves. The water in the hole boils — a short burst of bubbles and spray — and that is the whole cue. Strike as you would on any other rod.')
rep("en_us", "icefishing", "Cut it where it will last.",
    "Cut it where it will last. And it will not last forever: walk more than four blocks off in freezing weather and the hole skins over — a few minutes, and it is ice again.")
rep("en_us", "icefishing", "and while you are waiting it is the only thing you control.",
    "and while you are waiting it is the only thing you control.\nThe mormyshka is not the only thing that fits the slot. A dash in the table means the fish will not touch one at all — the burbot wants a worm, liver or a live baitfish, and a winter rig takes all three.")
rewrite("en_us", "icefishing", "Bleak|0.8|—",
        "Species|Mormyshka|Winter\nRuffe|1.0|1.0\nWhitefish|0.9|1.2\nSmelt|0.9|1.5\nRoach|0.9|0.7\nPerch|0.9|0.8\nGudgeon|0.9|0.6\nBream|0.7|0.3\nBurbot|—|1.6", suf="table")

rep("ru_ru", "icefishing",
    "## КИВОК\nНи звука поклёвки, ни надписи «Поклёвка!». Кивок дёргается — вот и вся подсказка. Дальше обычная подсечка.",
    "## ЛУНКА\nНи звука поклёвки, ни надписи «Поклёвка!» — и удилище не шевелится. В лунке вскипает вода: короткий выброс пузырей и брызг, вот и вся подсказка. Дальше обычная подсечка.")
rep("ru_ru", "icefishing", "Режьте там, где она простоит.",
    "Режьте там, где она простоит. И не навсегда: отойдите больше чем на четыре блока в мороз — и лунка затягивается, за несколько минут снова лёд.")
rep("ru_ru", "icefishing", "и пока вы ждёте — это единственное, чем вы управляете.",
    "и пока вы ждёте — это единственное, чем вы управляете.\nМормышка — не единственное, что влезает в слот. Прочерк в таблице значит, что рыба её вообще не возьмёт: налиму нужен червь, печень или живец, и зимняя оснастка принимает всё это.")
rewrite("ru_ru", "icefishing", "Уклейка|0.8|—",
        "Вид|Мормышка|Зима\nЁрш|1.0|1.0\nСиг|0.9|1.2\nКорюшка|0.9|1.5\nПлотва|0.9|0.7\nОкунь|0.9|0.8\nПескарь|0.9|0.6\nЛещ|0.7|0.3\nНалим|—|1.6", suf="table")

rep("uk_ua", "icefishing",
    "## КИВОК\nНі звуку поклювки, ні напису «Поклювка!». Кивок смикається — ось і вся підказка. Далі звичайне підсікання.",
    "## ЛУНКА\nНі звуку поклівки, ні напису «Поклівка!» — і вудлище не ворушиться. У лунці скипає вода: короткий викид бульбашок і бризок, ось і вся підказка. Далі звичайне підсікання.")
rep("uk_ua", "icefishing", "Ріжте там, де вона простоїть.",
    "Ріжте там, де вона простоїть. І не назавжди: відійдіть більш ніж на чотири блоки в мороз — і лунка затягується, за кілька хвилин знову лід.")
rep("uk_ua", "icefishing", "і поки ви чекаєте — це єдине, чим ви керуєте.",
    "і поки ви чекаєте — це єдине, чим ви керуєте.\nМормишка — не єдине, що влазить у слот. Прочерк у таблиці означає, що риба її взагалі не візьме: миню потрібен черв'як, печінка або живець, і зимова оснастка приймає все це.")
rep("uk_ua", "icefishing", "наближає поклювку на 34 тики", "наближає поклівку на 34 тики")
rewrite("uk_ua", "icefishing", "Верховодка|0.8|—",
        "Вид|Мормишка|Зима\nЙорж|1.0|1.0\nСиг|0.9|1.2\nКорюшка|0.9|1.5\nПлітка|0.9|0.7\nОкунь|0.9|0.8\nПіскар|0.9|0.6\nЛящ|0.7|0.3\nМинь|—|1.6", suf="table")

# =================================================================================================
# keepnet — the taper, the sizes, the trade
rep("en_us", "keepnet",
    "One cell is 25 cm of fish, so a 40 cm perch takes two, a metre of zander four, and a three-metre catfish hits the ceiling at seven.",
    "One cell is 25 cm of fish, so a 40 cm perch is two cells long, a 90 cm zander four, and anything past 1.75 m — a big catfish — hits the ceiling at seven.")
rep("en_us", "keepnet", "Big streamlined fish lose their corner cells — the nose and tail taper.",
    "A deep-bodied fish three cells across and four long loses its four corners — the nose and the tail taper.")
rep("en_us", "keepnet", "Four sizes: 5×3, 7×4, 8×5 and 9×6, each crafted from the one below.",
    "Four sizes: 5×3, 7×4, 8×5 and 9×6; each of the last three is crafted from the one below.")
rep("en_us", "keepnet", "The fisherman sells them: small at level three, medium and large at four, huge at five.",
    "The fisherman sells them: small at level three, medium OR large at four — one or the other, per villager — and huge at five.")
rep("ru_ru", "keepnet",
    "так что 40-сантиметровый окунь занимает две клетки, метровый судак — четыре, а трёхметровый сом упирается в потолок в семь.",
    "так что 40-сантиметровый окунь занимает две клетки в длину, 90-сантиметровый судак — четыре, а всё, что длиннее 1,75 м (крупный сом), упирается в потолок в семь.")
rep("ru_ru", "keepnet", "Крупная рыба с обтекаемым телом теряет угловые клетки — нос и хвост сужаются.",
    "Высокотелая рыба шириной в три клетки и длиной в четыре теряет угловые клетки — нос и хвост сужаются.")
rep("ru_ru", "keepnet", "средний и большой на четвёртом, огромный на пятом.",
    "средний или большой на четвёртом — у каждого рыбака что-то одно, — огромный на пятом.")
rep("uk_ua", "keepnet",
    "тож 40-сантиметровий окунь займає дві клітинки, метровий судак — чотири, а триметровий сом упирається в стелю в сім.",
    "тож 40-сантиметровий окунь займає дві клітинки завдовжки, 90-сантиметровий судак — чотири, а все, що довше за 1,75 м (великий сом), упирається в стелю в сім.")
rep("uk_ua", "keepnet", "Велика риба з обтічним тілом втрачає кутові клітинки — ніс і хвіст звужуються.",
    "Високотіла риба завширшки в три клітинки й завдовжки в чотири втрачає кутові клітинки — ніс і хвіст звужуються.")
rep("uk_ua", "keepnet", "середній і великий на четвертому, величезний на п'ятому.",
    "середній або великий на четвертому — у кожного рибалки щось одне, — величезний на п'ятому.")

# =================================================================================================
# feeding — one number, said once
rep("en_us", "feeding", "A fed spot bites up to twice as fast and cuts up to 40%% off the wait.",
    "A fed spot cuts up to 40%% off the wait — about half again as many bites in the same hour.")
rep("ru_ru", "feeding", "Прикормленная точка клюёт до двух раз быстрее и срезает до 40%% ожидания.",
    "Прикормленная точка срезает до 40%% ожидания — примерно в полтора раза больше поклёвок за тот же час.")
rep("uk_ua", "feeding", "Прикормлена точка клює до двох разів швидше й зрізає до 40%% очікування.",
    "Прикормлена точка зрізає до 40%% очікування — приблизно в півтора раза більше поклівок за ту саму годину.")

# =================================================================================================
# gbnumbers — the ceiling that exists, and the grind the size bonus starts at
rep("en_us", "gbnumbers", "the base plus four rich things reaches 1.00.",
    "the base plus four rich things reaches 0.94, and nothing you can stir in a 3x3 grid goes higher.")
rep("en_us", "gbnumbers", "Base + four, rich|1.00", "Base + four, rich|0.94", suf="table")
rep("en_us", "gbnumbers", "so the fish that come roll bigger. A better chance, never a promise.",
    "so the fish that come roll bigger — but only past a fraction of 0.50; a mix at the base's own grind flattens nothing. A better chance, never a promise.")
rep("ru_ru", "gbnumbers", "основа плюс четыре сытных компонента дают 1.00.",
    "основа плюс четыре сытных компонента дают 0.94 — выше в сетке 3x3 не собрать ничего.")
rep("ru_ru", "gbnumbers", "База + четыре, сытная|1.00", "База + четыре, сытная|0.94", suf="table")
rep("ru_ru", "gbnumbers", "так что пришедшая рыба выпадает тяжелее. Больший шанс, но не обещание.",
    "так что пришедшая рыба выпадает тяжелее — но только за фракцией 0.50; смесь с помолом самой основы не распрямляет ничего. Больший шанс, но не обещание.")
rep("uk_ua", "gbnumbers", "основа плюс чотири ситних компоненти дають 1.00.",
    "основа плюс чотири ситних компоненти дають 0.94 — вище в сітці 3x3 не зібрати нічого.")
rep("uk_ua", "gbnumbers", "База + чотири, ситна|1.00", "База + чотири, ситна|0.94", suf="table")
rep("uk_ua", "gbnumbers", "тож риба, що прийшла, випадає важчою. Більший шанс, але не обіцянка.",
    "тож риба, що прийшла, випадає важчою — але лише за фракцією 0.50; суміш із помелом самої основи не розпрямляє нічого. Більший шанс, але не обіцянка.")

# =================================================================================================
# stress — which breaks name the load, and what the two columns measure
rep("en_us", "stress", "The break message names the load in kg — match your tackle to the fish.",
    "When the line really parts and the rig goes with it, the message names the load in kg — most over-pulls only throw the hook. Match your tackle to the fish.")
rep("en_us", "stress", "Over the limit|Break per second|Held there", "Over the limit|As you cross|Once you have held it", suf="table")
rep("ru_ru", "stress", "При обрыве сообщение назовёт нагрузку в кг — подбирай снасть под рыбу.",
    "Когда леска действительно рвётся и оснастка уходит с рыбой, в сообщении будет нагрузка в кг — но чаще перегруз просто выбивает крючок. Подбирай снасть под рыбу.")
rep("ru_ru", "stress", "Перебор|Обрыв за сек|Если держать", "Перебор|В момент перехода|Если держать", suf="table")
rep("uk_ua", "stress", "При обриві повідомлення назве навантаження в кг — підбирай снасть під рибу.",
    "Коли волосінь справді рветься й оснастка йде з рибою, у повідомленні буде навантаження в кг — але частіше перевантаження просто вибиває гачок. Підбирай снасть під рибу.")
rep("uk_ua", "stress", "Перебір|Обрив за сек|Якщо тримати", "Перебір|У момент переходу|Якщо тримати", suf="table")

# =================================================================================================
# drag — an open drag always pays out; real reels in the table; the bleed cap
rep("en_us", "drag", "but clicks gain NOTHING, and a running fish takes line.",
    "but clicks gain NOTHING, and the spool keeps paying line out the whole time it is open — half again as fast during a run.")
rep("en_us", "drag", "You only gain standing up.",
    "You only gain standing up.\nThe bleed stops improving at 5 kg of drag; the strength it lends the line keeps climbing with the reel.")
rep("en_us", "drag", "10|+5.0|0.020\n15|+7.5|0.020", "9.5|+4.75|0.020\n14.5|+7.25|0.020", suf="table")
rep("ru_ru", "drag", "но и подмотка не идёт: клики впустую, а рыба в рывке забирает леску.",
    "но и подмотка не идёт: клики впустую, а шпуля сдаёт леску всё время, пока фрикцион открыт, — и в полтора раза быстрее в рывке.")
rep("ru_ru", "drag", "Тащить можно только стоя.",
    "Тащить можно только стоя.\nСброс перестаёт расти на 5 кг фрикциона; прибавка к прочности лески растёт вместе с катушкой дальше.")
rep("ru_ru", "drag", "10|+5.0|0.020\n15|+7.5|0.020", "9.5|+4.75|0.020\n14.5|+7.25|0.020", suf="table")
rep("uk_ua", "drag", "але кліки не дають НІЧОГО, а риба в ривку забирає волосінь.",
    "але кліки не дають НІЧОГО, а шпуля здає волосінь увесь час, поки фрикціон відкритий, — і в півтора раза швидше в ривку.")
rep("uk_ua", "drag", "Тягнути можна лише стоячи.",
    "Тягнути можна лише стоячи.\nСкид перестає рости на 5 кг фрикціона; прибавка до міцності волосіні росте разом із котушкою далі.")
rep("uk_ua", "drag", "10|+5.0|0.020\n15|+7.5|0.020", "9.5|+4.75|0.020\n14.5|+7.25|0.020", suf="table")

# =================================================================================================
# tacklebox — counts that match something, and uk's missing sentence
rep("en_us", "tacklebox", "Seventeen line diameters, nine hook sizes, four rig types and dozens of baits",
    "Two dozen lines, nine hook sizes, eleven rigs and dozens of baits")
rep("ru_ru", "tacklebox", "Семнадцать диаметров лески, девять размеров крючков, четыре типа оснасток и десятки наживок",
    "Два десятка лесок, девять размеров крючков, одиннадцать оснасток и десятки наживок")
rep("uk_ua", "tacklebox", "Сімнадцять діаметрів волосіні, дев'ять розмірів гачків, чотири типи оснасток і десятки наживок",
    "Два десятки волосіней, дев'ять розмірів гачків, одинадцять оснасток і десятки наживок")
rep("uk_ua", "tacklebox", "Кожен уже названий, пофарбований і набитий снастю зі стендовим клеймом.",
    "Кожен уже названий, пофарбований і набитий снастю зі стендовим клеймом: набір — це і є відповідь на питання «що потрібно на щуку», у формі, яку можна носити з собою.")

# =================================================================================================
# thanks — the beluga has company at 12; oneshot's credit says what actually came of the ask
rep("en_us", "thanks", "the frilled shark and the beluga, which now stands at the very top of the level ladder.",
    "the frilled shark and the beluga, still level 12 at the top of the ladder.")
rep("en_us", "thanks", "oneshot — the setting that stops the visible fish spawning as entities.",
    "oneshot — the question about the visible fish that put it in writing: they are drawn, not spawned, and cost a server nothing.")
rep("ru_ru", "thanks", "плащеносная акула и белуга, которая теперь стоит на самом верху лестницы уровней.",
    "плащеносная акула и белуга — по-прежнему 12-й уровень, самый верх лестницы.")
rep("ru_ru", "thanks", "oneshot — настройка, отключающая появление видимой рыбы сущностями.",
    "oneshot — вопрос о видимой рыбе, после которого это записано словами: она рисуется, а не спавнится, и серверу не стоит ничего.")
rep("uk_ua", "thanks", "плащоносна акула й білуга, яка тепер стоїть на самій вершині драбини рівнів.",
    "плащоносна акула й білуга — досі 12-й рівень, сама вершина драбини.")
rep("uk_ua", "thanks", "oneshot — налаштування, що вимикає появу видимої риби сутностями.",
    "oneshot — питання про видиму рибу, після якого це записано словами: вона малюється, а не спавниться, і серверу не коштує нічого.")

# =================================================================================================
# discord — the wiki, not the repo
for loc in ("en_us", "ru_ru", "uk_ua"):
    rep(loc, "discord", "github.com/qwazar14/riverfishing", "qwazar14.github.io/riverfishing")

# =================================================================================================
# lurework — held RMB is slow, not dead
rep("en_us", "lurework", 'Holding RMB cranks too fast: line comes in, but no fish takes that "action".',
    "Holding RMB cranks too fast: line comes in, and the takes slow to a crawl — a fifth of what a worked lure pulls.")
rep("en_us", "lurework", "Held right-click|too fast|no takes", "Held right-click|too fast|takes crawl", suf="table")
rep("ru_ru", "lurework", "Зажатый ПКМ мотает слишком часто: леска идёт, но такую «игру» рыба не берёт.",
    "Зажатый ПКМ мотает слишком часто: леска идёт, а поклёвки редеют впятеро — такую «игру» рыба почти не берёт.")
rep("ru_ru", "lurework", "Зажать ПКМ|слишком быстро|поклёвок нет", "Зажать ПКМ|слишком быстро|поклёвки редки", suf="table")
rep("uk_ua", "lurework", "Затиснута ПКМ мотає надто швидко: волосінь іде, але таку «гру» риба не бере.",
    "Затиснута ПКМ мотає надто швидко: волосінь іде, а поклівки рідшають уп'ятеро — таку «гру» риба майже не бере.")
rep("uk_ua", "lurework", "Затиснути ПКМ|занадто швидко|поклювок немає", "Затиснути ПКМ|занадто швидко|поклівки рідкі", suf="table")

# =================================================================================================
# livebait — the floor is 4x; ru's extra promise goes
rep("en_us", "livebait", "calls a predator ~6× its weight.", "calls a predator of at least 4× its weight.")
rep("ru_ru", "livebait", "зовёт хищника от ~6× своего веса.", "зовёт хищника от 4× своего веса.")
rep("ru_ru", "livebait", "Живцовую донку можно оставить на род-поде и ждать мощную поклёвку.", "Живцовую донку можно оставить на род-поде.")
rep("uk_ua", "livebait", "кличе хижака від ~6× своєї ваги.", "кличе хижака від 4× своєї ваги.")

# =================================================================================================
# uk — the word the game itself uses
rep("uk_ua", "waiting", "Звуку поклювки немає, надпису «Поклювка!» немає.", "Звуку поклівки немає, напису «Поклівка!» немає.")
rep("uk_ua", "spook", "поклювань немає", "поклівок немає")

for loc in data:
    io.open(os.path.join(LANG, loc + ".json"), "w", encoding="utf-8", newline="\n").write(
        json.dumps(data[loc], ensure_ascii=False, indent=2) + "\n")
print("  lang: %d edits applied, %d already in place" % (done["applied"], done["already"]))

# =================================================================================================
# the two code comments the audit caught lying
s = io.open(FM, encoding="utf-8").read()
old = "roughly 6× the bait's weight and up. A weighed livebait FLOORS the size roll there (capped at"
if old in s:
    s = s.replace(old, "at least 4× the bait's weight. A weighed livebait FLOORS the size roll there (capped at", 1)
    io.open(FM, "w", encoding="utf-8", newline="\n").write(s)
    print("  FishingManager: the livebait comment says 4×, which is what the constant says")

z = io.open(FZ, encoding="utf-8").read()
old = """     *   base + worm + maggot + barley           -> ~0.80
     *   five parts, rich                        -> 1.00"""
if old in z:
    z = z.replace(old, """     *   base + worm + maggot + barley           -> ~0.76
     *   base + four rich parts                  -> 0.94, and no legal 3x3 mix goes higher: the base is
     *                                              mandatory at 0.50 nutrition, so the mean never
     *                                              reaches the 1.00 the formula would need""", 1)
    io.open(FZ, "w", encoding="utf-8", newline="\n").write(z)
    print("  FeedZoneData: the javadoc's ceiling examples are the ones the formula produces")
print("done")
