# Changelog

Full patchnotes. The short three-bullet form the in-game update checker shows lives in
[`updates.json`](../updates.json).

---

## 0.7.0 — the water you can see into

**Minecraft 1.20.1 · 1.21.1 · 26.1.2 · 26.2** — Fabric, Forge (1.20.1) and NeoForge.

*In development.*

### The fish are in the water

Lean over a lake and you can see what lives in it. Every 12-block cell of water across the 3×3 chunks
around you carries its own shoal, drawn from the species that water actually holds — the same answer the
fish finder gives, because it is the same function (`BiteEngine.environmentScore`), so the shoal, the
finder and stocking can never disagree about a species again.

- **They are the population, not the record book.** Everyday fish, weighted by habitat, season, time,
  weather and biome, thinned by how hard the swim has been fished. A hammered spot visibly empties.
- **Shoals are shoals.** Anything under 900 g comes out as a group of three to seven sharing one circuit
  at nearly the same phase, so it travels together. A pike comes out alone.
- **Size is real size.** One block is one metre. A 20 cm roach is a flicker you have to be close to see;
  a three-metre sturgeon is a shadow you notice from the bank.
- **Depth hides nothing.** Only the water does — a swamp, rain, dusk. Lean over and look and you can make
  out what is sitting on the bottom.
- **Cells are pinned to the world, not to you**, so walking adds and drops shoals at the edges instead of
  dragging every fish along with you, and each shoal holds still while you fish it and changes by the hour.

Technically it costs almost nothing: each fish is a single textured quad carrying the species' own item
sprite, because the 256px fish icons turn into roughly a thousand quads each through a normal item model.
The whole thing is one packet every couple of seconds, and only when it has changed.

### Fish are wary of you

A short-lived fright value per patch of water — the counterpart to the chunk depletion that already
existed, but measured in seconds rather than days and never written to disk.

Running and jumping within six blocks, standing **in** the water, breaking a block nearby, a boat under
way, your own shadow when the sun is low behind you, and the cast itself where the tackle lands. While the
fish are frightened the bites stop and the visible shoal leaves. The only feedback is the rings running
outward across the surface and the water emptying — no message, no HUD.

It is a **field, not a flag**: what you do on the bank frightens the water around you, and a bait twenty
blocks out is disturbed only by its own cast landing. That asymmetry is what finally makes a long cast
worth making. Crouched and still is the one state that makes no noise at all. Recovery takes 30 to 90
seconds — a murky swamp forgets you quickly, clear shallows stay wary three times as long. Preset-driven
like every other harsh mechanic, and `"spook": 0` in the config switches it off.

### Every fish shows its age, and some show more than that

Fish are now coloured by a table rather than by a single flat sprite. A specimen's colour is read off its
own size: young fish are pale and silvery, old ones darken into their species' adult colour. A young bream
is a bright silver coin; an old one is deep bronze. This applies to every fish from every source — a
catch, a bait trap, a trade — and to the fish drifting in the water.

On top of that sit **morphs**: xanthism, albinism, leucism, the scale patterns of carp strains, natural
hybrids, lamprey scars, the hooked jaw of an old male, the stunted fish of an over-fished pond and the
deep-bodied one of an over-stocked one. Each is a collection entry of its own on the species' journal
page, and each hangs off world state the mod already tracked and had never shown you: fish a swim down and
it starts handing out stunted fish; a stocked water that has taken hold starts throwing colour morphs.

**Not one new drawing.** Every morph is the species' own icon under a different tint and a whitening pass,
from one table shared by the item, the journal and the water.

---

## 0.6.1 — the hotfix

**Minecraft 1.20.1 · 1.21.1 · 26.1.2 · 26.2** — Fabric, Forge (1.20.1) and NeoForge.

A fix release. Every item came from a player report in the week 0.6.0 shipped.

### The cast no longer disappears on its own

The guard that checks you are still holding the rod your cast was made with compared the rod's item
**by memory reference**. An `ItemStack` reference goes stale as soon as anything rewrites the inventory
slot — common on a server, rare in single player — and from that tick the mod believed you had swapped
rods and ended the cast **silently, with no message**. It looked exactly like the rod reeling itself in
a moment after the cast, on every rod class. It now compares the hotbar slot, which is what "still the
same rod" was always meant to mean and cannot go stale.

### The Fishing Stall opens its bench on Forge

On **1.20.1 Forge only**, the Tackle Station screen was never registered, so right-clicking the stall
with an empty hand opened the menu server-side and drew nothing. Every other loader had it.

### The mod no longer teaches the wrong input

- `message.riverfishing.cast_spin` said **"Hold right-click to retrieve"**. Holding auto-repeats roughly
  five times a second, which both empties the retrieve at full speed and is too fast a cadence for a
  fish to take — the guide page already said the opposite. It now describes rhythmic clicks.
- **The rod tooltip names the rod's class on its first line** (`tooltip.riverfishing.rod_class.*`),
  derived from `RodType.rodClass()`: active rods are worked with clicks, float and bottom rods are cast
  and left alone. Previously every rod shared one identical hint. The winter rod is exempt — it is
  jigged in an ice hole and has its own line.
- **A new journal guide covers the waiting flow.** The shelf had thirteen guides, three of which taught
  RMB-cranking, and none about the two rod classes that are never retrieved.

### Wiki

The bottom-rod section now states that these rods are not retrieved and that a right-click outside a
bite ends the cast — in English, Russian and Ukrainian.

---

## 0.6.0 — the tackle & fight update

**Minecraft 1.20.1 · 1.21.1 · 26.1.2 · 26.2** — Fabric, Forge (1.20.1) and NeoForge.

The headline is that **tackle now has a weight, and weight is a decision**. Before this, a rig was a rig
and a lure was a lure; now the grams you tie them at decide how far you cast, how long you wait, and
which fish will even look at it.

### The Tackle Station

Right-click a **Fishing Stall** with an empty hand and it becomes a tackle bench. Pick a form, step the
weight, feed it hooks, iron and string, take the finished tackle out. The block that gives a village its
fisherman is the same block you tie your own gear on.

- **6 bottom rigs and 8 lures**, each with its own weight ladder. Rigs come out with their hooks
  already slotted.
- **The weight is read by three separate systems**: your blank's cast-weight window, the lure-size
  filter, and cast distance. A lure's mass *is* its size — a heavy pilker genuinely silences the
  tiddlers, and it floors how small the hooked fish can be.
- **Dye a lure at the bench** — the colour affects the bite.
- Every piece carries the maker's name and its weight in the tooltip.
- The two heaviest classes of spoon, wobbler, jig and castmaster are **sea sizes**, and exist only here
  — the sea spinning, boat and trolling blanks finally have tackle inside their test windows.
- Hook link, balance and blade size are written and shown but **do not affect gameplay yet**; the
  tooltip and the wiki both say so.

Hand-crafted lures carry no weight stamp, which the game reads as 0 g: they add nothing to the cast and
never enter the size filter. The crafting recipes are still there, and the wiki now says plainly that
they are the basic path.

### The fight has physics

- **The rod bends under tension** — six bend steps driven by the actual stress on the tackle, visible to
  everyone nearby, not just to you.
- **A running fish loads the tackle by itself.** You no longer have to be cranking for the line to be in
  danger.
- **Fish tire out.** A long fight is now winnable by outlasting the fish instead of out-clicking it.
- **Small fry stop fighting like monsters** — a 200 g roach no longer behaves like a carp.
- Opening the drag always pays line, so crouch-spamming is no longer free.
- Trolling starts reliably, and the boss bar reads the fight correctly.

### The fisherman is not a fish market

The 0.5.0 trade tables were mostly fish, and gear almost never appeared.

- **Three offers per level** instead of two, drawn from a per-tier pool.
- **Tier 1 always includes one simple fish**, so the first trade is never a wall.
- **Rods are sold fully assembled only** — no more bare blanks you cannot cast.
- Tackle from the stall is **real bench tackle**, carrying the same weight stamp the bench writes.
- The stall also stocks plain vanilla goods — string, a boat, prismarine, a nautilus shell.

### Crafting

- **Every rod blank and every reel is craftable**, on one cost ladder — 24 recipes, no gaps.
- Hooks #2 and #1 joined the `riverfishing:hooks` tag, which had silently locked the two biggest hooks
  out of every lure recipe.

### Four new river species — 70 total

**Common dace, Volga zander, White-eye bream** and the **Round goby**, each with its own habitat gates,
fight profile and journal page.

Plus a realism pass over the existing species: weights and lengths corrected against the real fish.

### The wiki

An **18-page player wiki** in `docs/wiki`, written from the source rather than from memory, in
**English, Russian and Ukrainian** — every rod, reel, line, rig, bait, species and mechanic with the
actual numbers. There is also a single-page build with a language switcher, the species sprites inline,
and all 91 recipes drawn as real 3×3 grids generated from the recipe files, so they cannot drift from
what the game loads.

### Ukrainian

The mod now speaks **Ukrainian** — all 805 strings, with proper Ukrainian angling vocabulary and real
Ukrainian fish names, not transliterations.

### Community

There is a **Discord**: https://discord.gg/Kk2nKvsuRh — it is in the mod list, in the journal's guide
shelf, and on every page of the wiki. Bug reports and catches both welcome.

### Fixes

- The rod's assembly screen wrote tackle into a detached copy of the rod — changes could be silently
  lost. This is the bug behind "the rod is not assembled" reports.
- The "not assembled" message now says **which part** is missing instead of just refusing to cast.
- Five registry gaps a wiki pass exposed, including the saltwater blanks' durability and the drag curve
  being computed in two different places with two different answers.
- Cast-bar and pump-reel corrections from six playtest rounds.
- The 13 rod icons left over from 0.1.0 are gone; the break particles point at the drawn blanks.

---

# Список змін

## 0.6.0 — оновлення снастей і бою

**Minecraft 1.20.1 · 1.21.1 · 26.1.2 · 26.2** — Fabric, Forge (1.20.1) і NeoForge.

Головне: **у снасті тепер є вага, і вага — це рішення**. Раніше оснастка була просто оснасткою, а
приманка просто приманкою; тепер грами, під які ти їх зв'язав, вирішують і дальність закиду, і час
очікування, і те, яка риба взагалі на це подивиться.

### Снастевий стіл

ПКМ по **Рибальській ятці** порожньою рукою — і вона стає верстаком для снастей. Вибери форму, набери
вагу, згодуй гачки, залізо й нитку, забери готове. Блок, який дає селу рибака, — той самий, на якому ти
в'яжеш собі снасть.

- **6 донних оснасток і 8 приманок**, у кожної своя вагова драбина. Оснастки виходять уже з гачками в
  слотах.
- **Вагу читають три різні системи**: тестове вікно бланка, фільтр розміру приманки й дальність закиду.
  Маса приманки — це її розмір: важка пілька справді відсікає дрібноту й задає нижню межу розміру риби.
- **Фарбування приманки на столі** — колір впливає на поклівку.
- На кожній речі стоїть ім'я майстра і вага.
- Два найважчі класи коливалки, воблера, джига й кастмастера — **морські розміри**, і вони існують лише
  тут: морський спінінг, човнове й тролінгове вудилища нарешті мають снасть у своїх тестових вікнах.
- Відступ гачка, баланс і номер пелюстки записуються й показуються, але **на гру поки не впливають** —
  про це прямо сказано і в підказці, і у вікі.

Приманка, скрафчена руками, не має штампа ваги, і гра читає це як 0 г: вона нічого не додає до закиду й
не потрапляє у фільтр розміру. Рецепти нікуди не зникли, і вікі тепер прямо каже, що це базовий шлях.

### У бою з'явилася фізика

- **Вудилище гнеться під натягом** — шість ступенів згину за справжнім навантаженням на снасть, і це
  бачать усі поруч, а не лише ти.
- **Риба на ривку сама навантажує снасть.** Тепер не обов'язково мотати, щоб волосінь була в небезпеці.
- **Риба втомлюється.** Довгий бій можна виграти витримкою, а не швидкістю кліків.
- **Дрібнота більше не воює як монстр** — плітка на 200 г не поводиться як короп.
- Відкритий фрикціон завжди віддає волосінь, тож спам присіданням більше не безкоштовний.
- Тролінг стартує надійно, а смуга боса показує бій правильно.

### Рибак — не рибний ринок

У 0.5.0 в обмінах була майже сама риба, а снасть майже не траплялася.

- **Три пропозиції на рівень** замість двох, з пулу на кожен ранг.
- **У першому ранзі завжди є одна проста риба** — перший обмін більше не глухий кут.
- **Вудки продаються лише повністю зібраними.**
- Снасть із ятки — **справжня снасть зі столу**, з тим самим штампом ваги.
- У ятці є й звичайні ванільні товари: нитка, човен, призмарин, мушля навтилуса.

### Крафт

- **Кожен бланк і кожна котушка крафтяться** — 24 рецепти, без прогалин.
- Гачки №2 і №1 додані до тегу `riverfishing:hooks`, який досі тихо не пускав два найбільші гачки в
  жоден рецепт приманки.

### Чотири нові річкові види — усього 70

**Ялець, берш, клепець** і **бичок-кругляк**, у кожного свої умови проживання, манера бою й сторінка в
щоденнику. Плюс прохід по реалізму: ваги й довжини звірені зі справжньою рибою.

### Вікі

**18 сторінок** у `docs/wiki`, написаних із коду, а не з пам'яті, **англійською, російською та
українською** — усі вудилища, котушки, волосінь, оснастки, наживки, види й механіки зі справжніми
числами. Є ще збірка в одну сторінку з перемикачем мов, спрайтами видів і всіма 91 рецептом,
намальованими справжніми сітками 3×3 просто з файлів рецептів.

### Українська

Мод тепер говорить **українською** — усі 805 рядків, справжня рибальська лексика й справжні українські
назви риб, а не транслітерації.

### Спільнота

З'явився **Discord**: https://discord.gg/Kk2nKvsuRh — він є у списку модів, на полиці гайдів у щоденнику
й на кожній сторінці вікі. Баг-репорти й улови однаково вітаються.

### Виправлення

- Екран збирання вудки писав снасть у відчеплену копію предмета, і зміни могли тихо зникати. Саме через
  це приходили скарги «вудка не зібрана».
- Повідомлення про незібрану вудку тепер каже, **якої саме частини** бракує.
- П'ять прогалин у реєстрі, які виявив прохід по вікі, зокрема міцність морських бланків і крива
  фрикціона, що рахувалася у двох місцях по-різному.
- Виправлення шкали закиду й викачування за шістьма раундами плейтесту.
- 13 іконок вудилищ, що лишилися з 0.1.0, прибрані.

---

# Список изменений

## 0.6.0 — обновление снастей и боя

**Minecraft 1.20.1 · 1.21.1 · 26.1.2 · 26.2** — Fabric, Forge (1.20.1) и NeoForge.

Главное: **у снасти теперь есть вес, и вес — это решение**. Раньше оснастка была просто оснасткой, а
приманка просто приманкой; теперь граммы, под которые ты их связал, решают и дальность заброса, и время
ожидания, и то, какая рыба вообще на это посмотрит.

### Снастевой станок

ПКМ по **Рыболовному прилавку** пустой рукой — и он становится верстаком для снастей. Выбери форму,
набери вес, скорми крючки, железо и нить, забери готовое. Блок, который даёт деревне рыбака, — тот же,
на котором ты вяжешь себе снасть.

- **6 донных оснасток и 8 приманок**, у каждой своя весовая лестница. Оснастки выходят уже с крючками в
  слотах.
- **Вес читают три разные системы**: тестовое окно бланка, фильтр размера приманки и дальность заброса.
  Масса приманки — это её размер: тяжёлая пилька действительно отсекает мелочь и задаёт нижнюю границу
  размера рыбы.
- **Покраска приманки на станке** — цвет влияет на поклёвку.
- На каждой снасти стоит имя мастера и вес.
- Два самых тяжёлых класса колебалки, воблера, джига и кастмастера — **морские размеры**, и существуют
  только здесь: морской спиннинг, лодочное и троллинговое удилища наконец получили снасть в своих
  тестовых окнах.
- Отступ крючка, огрузка и номер лепестка записываются и показываются, но **на игру пока не влияют** —
  об этом прямо сказано и в подсказке, и в вики.

Приманка, скрафченная руками, не несёт штампа веса, и игра читает это как 0 г: она ничего не добавляет к
забросу и не попадает в фильтр размера. Рецепты никуда не делись, и вики теперь прямо говорит, что это
базовый путь.

### В бою появилась физика

- **Удилище гнётся под натяжением** — шесть ступеней изгиба по настоящей нагрузке на снасть, и это видят
  все рядом, а не только ты.
- **Рыба на рывке сама нагружает снасть.** Теперь не обязательно мотать, чтобы леска была в опасности.
- **Рыба устаёт.** Долгий бой можно выиграть выдержкой, а не скоростью кликов.
- **Мелочь больше не воюет как монстр** — плотва на 200 г не ведёт себя как карп.
- Открытый фрикцион всегда отдаёт леску, так что спам приседанием больше не бесплатный.
- Троллинг стартует надёжно, а босс-бар показывает бой правильно.

### Рыбак — не рыбный рынок

В 0.5.0 в трейдах была почти одна рыба, а снасть почти не попадалась.

- **Три предложения на уровень** вместо двух, из пула на каждый ранг.
- **В первом ранге всегда есть одна простая рыба** — первый трейд больше не тупик.
- **Удочки продаются только полностью собранными.**
- Снасть с прилавка — **настоящая снасть со станка**, с тем же штампом веса.
- На прилавке есть и обычные ванильные товары: нить, лодка, призмарин, раковина наутилуса.

### Крафт

- **Каждый бланк и каждая катушка крафтятся** — 24 рецепта, без пробелов.
- Крючки №2 и №1 добавлены в тег `riverfishing:hooks`, который до этого тихо не пускал два самых больших
  крючка ни в один рецепт приманки.

### Четыре новых речных вида — всего 70

**Елец, берш, белоглазка** и **бычок-кругляк**, у каждого свои условия обитания, манера боя и страница в
дневнике. Плюс проход по реализму: веса и длины сверены с настоящей рыбой.

### Вики

**18 страниц** в `docs/wiki`, написанных из кода, а не по памяти, на **английском, русском и
украинском** — все удилища, катушки, лески, оснастки, наживки, виды и механики с настоящими числами.
Есть ещё сборка в одну страницу с переключателем языков, спрайтами видов и всеми 91 рецептами,
нарисованными настоящими сетками 3×3 прямо из файлов рецептов.

### Украинский

Мод теперь говорит **по-украински** — все 805 строк, настоящая рыболовная лексика и настоящие украинские
названия рыб, а не транслитерации.

### Сообщество

Появился **Discord**: https://discord.gg/Kk2nKvsuRh — он есть в списке модов, на полке гайдов в дневнике
и на каждой странице вики. Баг-репорты и уловы одинаково приветствуются.

### Исправления

- Экран сборки удочки писал снасть в отцепленную копию предмета, и изменения могли тихо пропадать.
  Именно из-за этого приходили жалобы «удочка не собрана».
- Сообщение о несобранной удочке теперь говорит, **какой именно части** не хватает.
- Пять пробелов в реестре, которые вскрыл проход по вики, в том числе прочность морских бланков и кривая
  фрикциона, считавшаяся в двух местах по-разному.
- Исправления шкалы заброса и выкачивания по шести раундам плейтеста.
- 13 иконок удилищ, оставшихся с 0.1.0, убраны.

---

## Earlier releases

Condensed; see the [update feed](../updates.json) for the in-game bullets.

- **0.5.0** — sea fishing: the ocean, trolling, big-game fights and 36 new species (66 total);
  legendary one-per-world named fish and a dynamic market; stocking 2.0.
- **0.4.0** — tackle stress and probabilistic breaks, live bait 2.0, topwaters and the splash attack,
  the America mini-pack.
- **0.3.0** — the carp line-up and the koi collection, aquarium and trophies, ice fishing, angler
  skills, pond stocking, bait crops, lure dyeing.
- **0.2.0** — multiloader (Fabric and Forge/NeoForge from one codebase), rod pods, bite alarms, journal
  and quests, the fisherman villager.
- **0.1.0** — the first public build: NBT rods, the data-driven bite engine, the fight mini-game.
