# Види риб

Сто сім видів. Кожне число на цій сторінці взяте з профілю цього виду в `data/riverfishing/fish_profiles/`, а профіль повністю перевизначається датапаком — схему описано в [`docs/FISH_PROFILES.md`](../../FISH_PROFILES.md).

Парна сторінка: **[Довідник видів](species-reference.md)** — там жорсткі умови проживання, таблиці сезону / часу / погоди і статистика виважування.

## Як це читати

- **Вага (мін. – макс.)** — увесь можливий розкид виду. **Медіанний улов** — це `mean` із профілю, і це справді медіана: половина твоїх риб цього виду виявиться легшою. Див. [розрахунок ваги](fishing-mechanics.md#вага).
- **Водойми** — усі типи, у яких вид живе, з коефіцієнтом присутності. У типу, якого немає в списку, коефіцієнт 0, і риби там **ніколи** не буде.
- **Рівень** — це `min_angler_level`. Обмеження м'яке: кожен недобраний рівень множить вагу поклівки цієї риби на 0.6, але не нижче 3 %. Новачок може випадково витягнути трофей — з правильною снастю і в правильному місці, просто рідко.
- **Найкращі наживки** оцінюються від 0 до 1.3. Рушій бере з оснастки одну наживку — з найкращою оцінкою. Наживка, якої немає в списку, отримує 0, і **якщо в оснастці немає жодної з перелічених, риба не візьме взагалі**.
- Ідентифікатори наживок відповідають предметам так, як розписано на сторінці [Оснастки і наживки](rigs-and-baits.md#натуральні-наживки): `pearl_barley` = Перлівка, `bread` = Хлібний м'якуш, `silicone` = Силіконова приманка, `jig` = Джиг, `mormyshka` = Мормишка, `fish_strip` = Сире філе, `livebait` = Живець.

## Родини

Кожен вид віднесено до однієї з семи родин. Це поле `group` у профілі, і саме за ним розкладає
свій список [електровудка](electrofisher.md#екран) — сто сім імен одним списком — це список,
якого ніхто не читає. Вид із датапака, який не назвав родини, потрапляє в **Інші**: видний й
доступний, але не приписаний мовчки куди попало.

Родина — це твердження про рибу, а не ярлик, виведений з її цифр: жерех полює як хижак,
але він короповий і бере коропову [прикормку](groundbait.md).

| Родина | Види |
|---|---|
| **Коропові** (29) | Білизна, Білий амур, В'язь, Верхівка, Верховодка, В’юн, Гірчак, Голий короп, Головень, Дзеркальний короп, Золотий карась, Карась, Клепець, Короп, Краснопірка, Кутум, Лин, Лінійний короп, Лящ, Підуст, Пічкур, Плітка, Плоскирка, Рибець, Сазан, Синець, Товстолобик, Чехоня, Ялець |
| **Хижаки** (20) | Астронотус, Бабець, Берш, Блюгіл, Великоротий бас, Вугор, Золотий дорадо, Йорж, Канальний сомик, Минь, Окунь, Павлиній окунь, Пірайба, Плямистий змієголов, Ротань, Сом, Судак, Цихлазома майя, Червоночерева піранья, Щука |
| **Лососеві** (11) | Атлантичний лосось, Горбуша, Корюшка, Ленок, Нельма, Палія арктична, Райдужна форель, Сиг, Таймень, Форель, Харіус |
| **Осетрові** (3) | Білуга, Осетер, Стерлядь |
| **Кої** (6) | Кої Асагі, Кої Бекко, Кої Кохаку, Кої Сьова Санке, Кої Танчо Санке, Короп кої |
| **Морські** (21) | Барракуда, Бичок-кругляк, Бичок-цуцик, Камбала, Каранкс, Кефаль, Лаврак, Луфар, Мінтай, Місяць-риба, Морський вугор, Морський чорт, Оселедець, Риба-крапля, Сайда, Сарган, Скат, Скумбрія, Смугастий лаврак, Снук, Тріска |
| **Велика гра** (17) | Акула-мако, Арапайма, Ваху, Вітрильник, Голіафовий групер, Жовтоперий тунець, Китова акула, Махі-махі, Палтус, Плащоносна акула, Риба-меч, Синій марлін, Синьоперий тунець, Тарпон, Тигрова акула, Тупорила акула, Чорний марлін |

## Усі види

| # | Вид | ID предмета | Вага (мін. – макс.) | Медіанний улов | Довжина | Водойми (коефіцієнт присутності) | Рівень |
|---|---|---|---|---|---|---|---|
| 1 | <img src="../../../common/src/main/resources/assets/riverfishing/textures/item/fish/bream.png" width="28" alt=""> Лящ | `bream` | 300 г – 4 кг | 900 г | 25–55 см | озеро 1.1, річка 1.0, ставок 0.9, болото 0.4 | — |
| 2 | <img src="../../../common/src/main/resources/assets/riverfishing/textures/item/fish/crucian_carp.png" width="28" alt=""> Карась | `crucian_carp` | 50 г – 1.5 кг | 250 г | 10–38 см | ставок 1.2, болото 1.1, озеро 1.0, річка 0.5, калюжа 0.3 | — |
| 3 | <img src="../../../common/src/main/resources/assets/riverfishing/textures/item/fish/roach.png" width="28" alt=""> Плітка | `roach` | 50 г – 1 кг | 120 г | 10–40 см | річка 1.0, озеро 1.0, ставок 0.7, болото 0.4 | — |
| 4 | <img src="../../../common/src/main/resources/assets/riverfishing/textures/item/fish/rudd.png" width="28" alt=""> Краснопірка | `rudd` | 50 г – 1 кг | 110 г | 10–40 см | озеро 1.1, болото 1.0, ставок 0.9, річка 0.6 | — |
| 5 | <img src="../../../common/src/main/resources/assets/riverfishing/textures/item/fish/white_bream.png" width="28" alt=""> Плоскирка | `white_bream` | 100 г – 1.2 кг | 300 г | 12–35 см | річка 1.0, озеро 1.0, ставок 0.6, болото 0.3 | — |
| 6 | <img src="../../../common/src/main/resources/assets/riverfishing/textures/item/fish/carp.png" width="28" alt=""> Короп | `carp` | 1 кг – 15 кг | 3.5 кг | 35–100 см | озеро 1.2, ставок 1.1, річка 0.6, болото 0.4 | 3 |
| 7 | <img src="../../../common/src/main/resources/assets/riverfishing/textures/item/fish/catfish.png" width="28" alt=""> Сом | `catfish` | 2 кг – 120 кг | 7 кг | 60–260 см | річка 1.1, озеро 1.0, болото 0.3, ставок 0.2 | 6 |
| 8 | <img src="../../../common/src/main/resources/assets/riverfishing/textures/item/fish/perch.png" width="28" alt=""> Окунь | `perch` | 50 г – 2 кг | 250 г | 10–45 см | озеро 1.1, річка 1.0, ставок 0.8, болото 0.5 | — |
| 9 | <img src="../../../common/src/main/resources/assets/riverfishing/textures/item/fish/pike.png" width="28" alt=""> Щука | `pike` | 500 г – 10 кг | 2 кг | 35–120 см | озеро 1.1, річка 1.0, болото 0.7, ставок 0.6 | 4 |
| 10 | <img src="../../../common/src/main/resources/assets/riverfishing/textures/item/fish/zander.png" width="28" alt=""> Судак | `zander` | 500 г – 6 кг | 1.5 кг | 35–90 см | річка 1.1, озеро 1.0, ставок 0.3, болото 0.2 | 4 |
| 11 | <img src="../../../common/src/main/resources/assets/riverfishing/textures/item/fish/gudgeon.png" width="28" alt=""> Пічкур | `gudgeon` | 20 г – 150 г | 60 г | 8–20 см | річка 1.2, озеро 0.3, ставок 0.2 | — |
| 12 | <img src="../../../common/src/main/resources/assets/riverfishing/textures/item/fish/ruffe.png" width="28" alt=""> Йорж | `ruffe` | 20 г – 150 г | 60 г | 8–20 см | озеро 1.1, річка 1.0, ставок 0.4, болото 0.2 | — |
| 13 | <img src="../../../common/src/main/resources/assets/riverfishing/textures/item/fish/bleak.png" width="28" alt=""> Верховодка | `bleak` | 10 г – 100 г | 30 г | 6–18 см | річка 1.1, озеро 1.0, ставок 0.5 | — |
| 14 | <img src="../../../common/src/main/resources/assets/riverfishing/textures/item/fish/ide.png" width="28" alt=""> В'язь | `ide` | 300 г – 3 кг | 800 г | 25–60 см | річка 1.2, озеро 0.7, ставок 0.2, болото 0.1 | 2 |
| 15 | <img src="../../../common/src/main/resources/assets/riverfishing/textures/item/fish/chub.png" width="28" alt=""> Головень | `chub` | 200 г – 4 кг | 700 г | 20–60 см | річка 1.2 | 3 |
| 16 | <img src="../../../common/src/main/resources/assets/riverfishing/textures/item/fish/asp.png" width="28" alt=""> Білизна | `asp` | 500 г – 8 кг | 2 кг | 30–90 см | річка 1.2 | 5 |
| 17 | <img src="../../../common/src/main/resources/assets/riverfishing/textures/item/fish/tench.png" width="28" alt=""> Лин | `tench` | 300 г – 3.5 кг | 800 г | 20–60 см | ставок 1.2, болото 1.2, озеро 1.0, річка 0.2 | 2 |
| 18 | <img src="../../../common/src/main/resources/assets/riverfishing/textures/item/fish/burbot.png" width="28" alt=""> Минь | `burbot` | 500 г – 8 кг | 1.5 кг | 30–100 см | річка 1.1, озеро 0.9, ставок 0.1 | 4 |
| 19 | <img src="../../../common/src/main/resources/assets/riverfishing/textures/item/fish/eel.png" width="28" alt=""> Вугор | `eel` | 300 г – 4 кг | 900 г | 40–130 см | озеро 1.1, річка 0.9, ставок 0.6, болото 0.4 | 5 |
| 20 | <img src="../../../common/src/main/resources/assets/riverfishing/textures/item/fish/grayling.png" width="28" alt=""> Харіус | `grayling` | 150 г – 2.5 кг | 500 г | 18–55 см | річка 1.3, озеро 0.4 | 3 |
| 21 | <img src="../../../common/src/main/resources/assets/riverfishing/textures/item/fish/trout.png" width="28" alt=""> Форель | `trout` | 300 г – 5 кг | 1 кг | 25–80 см | річка 1.2, озеро 0.8, ставок 0.2 | 5 |
| 22 | <img src="../../../common/src/main/resources/assets/riverfishing/textures/item/fish/sterlet.png" width="28" alt=""> Стерлядь | `sterlet` | 1 кг – 16 кг | 3 кг | 40–125 см | річка 1.2 | 8 |
| 23 | <img src="../../../common/src/main/resources/assets/riverfishing/textures/item/fish/wild_carp.png" width="28" alt=""> Сазан | `wild_carp` | 1.5 кг – 18 кг | 4.2 кг | 40–110 см | річка 1.3, озеро 0.9, ставок 0.5, болото 0.3 | 4 |
| 24 | <img src="../../../common/src/main/resources/assets/riverfishing/textures/item/fish/mirror_carp.png" width="28" alt=""> Дзеркальний короп | `mirror_carp` | 1 кг – 14 кг | 3.2 кг | 33–95 см | озеро 1.2, ставок 1.2, річка 0.5, болото 0.4 | 3 |
| 25 | <img src="../../../common/src/main/resources/assets/riverfishing/textures/item/fish/grass_carp.png" width="28" alt=""> Білий амур | `grass_carp` | 1.5 кг – 25 кг | 5 кг | 40–120 см | озеро 1.3, ставок 1.2, річка 0.7, болото 0.6 | 4 |
| 26 | <img src="../../../common/src/main/resources/assets/riverfishing/textures/item/fish/carp_koi_kohaku.png" width="28" alt=""> Кої Кохаку | `carp_koi_kohaku` | 800 г – 8 кг | 2.5 кг | 25–90 см | ставок 1.0, озеро 1.0, річка 0.4 | 3 |
| 27 | <img src="../../../common/src/main/resources/assets/riverfishing/textures/item/fish/carp_koi_tancho_sanke.png" width="28" alt=""> Кої Танчо Санке | `carp_koi_tancho_sanke` | 800 г – 8 кг | 2.5 кг | 25–90 см | ставок 1.0, озеро 1.0, річка 0.4 | 3 |
| 28 | <img src="../../../common/src/main/resources/assets/riverfishing/textures/item/fish/carp_koi_showa_sanke.png" width="28" alt=""> Кої Сьова Санке | `carp_koi_showa_sanke` | 800 г – 8 кг | 2.5 кг | 25–90 см | ставок 1.0, озеро 1.0, річка 0.4 | 3 |
| 29 | <img src="../../../common/src/main/resources/assets/riverfishing/textures/item/fish/carp_koi_asagi.png" width="28" alt=""> Кої Асагі | `carp_koi_asagi` | 800 г – 8 кг | 2.5 кг | 25–90 см | ставок 1.0, озеро 1.0, річка 0.4 | 3 |
| 30 | <img src="../../../common/src/main/resources/assets/riverfishing/textures/item/fish/carp_koi_bekko.png" width="28" alt=""> Кої Бекко | `carp_koi_bekko` | 800 г – 8 кг | 2.5 кг | 25–90 см | ставок 1.0, озеро 1.0, річка 0.4 | 3 |
| 31 | <img src="../../../common/src/main/resources/assets/riverfishing/textures/item/fish/bluegill.png" width="28" alt=""> Блюгіл | `bluegill` | 40 г – 800 г | 150 г | 8–35 см | ставок 1.3, озеро 1.2, річка 0.6, болото 0.4 | — |
| 32 | <img src="../../../common/src/main/resources/assets/riverfishing/textures/item/fish/largemouth_bass.png" width="28" alt=""> Великоротий бас | `largemouth_bass` | 400 г – 8 кг | 1.5 кг | 25–75 см | озеро 1.3, ставок 1.1, болото 0.8, річка 0.7 | 3 |
| 33 | <img src="../../../common/src/main/resources/assets/riverfishing/textures/item/fish/rainbow_trout.png" width="28" alt=""> Райдужна форель | `rainbow_trout` | 300 г – 6 кг | 1.1 кг | 25–85 см | річка 1.3, озеро 0.9, ставок 0.2 | 4 |
| 34 | <img src="../../../common/src/main/resources/assets/riverfishing/textures/item/fish/channel_catfish.png" width="28" alt=""> Канальний сомик | `channel_catfish` | 800 г – 18 кг | 3.5 кг | 35–110 см | річка 1.2, озеро 1.0, ставок 0.6, болото 0.5 | 5 |
| 35 | <img src="../../../common/src/main/resources/assets/riverfishing/textures/item/fish/silver_carp.png" width="28" alt=""> Товстолобик | `silver_carp` | 2 кг – 25 кг | 6 кг | 50–120 см | озеро 1.3, ставок 0.9, річка 0.8, болото 0.2 | 6 |
| 36 | <img src="../../../common/src/main/resources/assets/riverfishing/textures/item/fish/sabrefish.png" width="28" alt=""> Чехоня | `sabrefish` | 150 г – 1.5 кг | 400 г | 20–60 см | річка 1.3, озеро 0.8, ставок 0.1 | 2 |
| 37 | <img src="../../../common/src/main/resources/assets/riverfishing/textures/item/fish/blue_bream.png" width="28" alt=""> Синець | `blue_bream` | 150 г – 800 г | 350 г | 15–45 см | річка 1.1, озеро 1.0, ставок 0.3, болото 0.2 | 2 |
| 38 | <img src="../../../common/src/main/resources/assets/riverfishing/textures/item/fish/mackerel.png" width="28" alt=""> Скумбрія | `mackerel` | 300 г – 2 кг | 600 г | 25–60 см | море 1.2 | 4 |
| 39 | <img src="../../../common/src/main/resources/assets/riverfishing/textures/item/fish/herring.png" width="28" alt=""> Оселедець | `herring` | 100 г – 600 г | 250 г | 15–40 см | море 1.3 | 4 |
| 40 | <img src="../../../common/src/main/resources/assets/riverfishing/textures/item/fish/garfish.png" width="28" alt=""> Сарган | `garfish` | 300 г – 1.5 кг | 600 г | 40–95 см | море 1.1 | 4 |
| 41 | <img src="../../../common/src/main/resources/assets/riverfishing/textures/item/fish/seabass.png" width="28" alt=""> Лаврак | `seabass` | 500 г – 8 кг | 1.5 кг | 30–90 см | море 1.2 | 5 |
| 42 | <img src="../../../common/src/main/resources/assets/riverfishing/textures/item/fish/flounder.png" width="28" alt=""> Камбала | `flounder` | 300 г – 4 кг | 900 г | 20–60 см | море 1.2 | 4 |
| 43 | <img src="../../../common/src/main/resources/assets/riverfishing/textures/item/fish/cod.png" width="28" alt=""> Тріска | `cod` | 2 кг – 40 кг | 6 кг | 50–150 см | море 1.2 | 6 |
| 44 | <img src="../../../common/src/main/resources/assets/riverfishing/textures/item/fish/saithe.png" width="28" alt=""> Сайда | `saithe` | 1 кг – 15 кг | 3 кг | 40–110 см | море 1.1 | 5 |
| 45 | <img src="../../../common/src/main/resources/assets/riverfishing/textures/item/fish/conger.png" width="28" alt=""> Морський вугор | `conger` | 3 кг – 60 кг | 9 кг | 80–250 см | море 1.1 | 7 |
| 46 | <img src="../../../common/src/main/resources/assets/riverfishing/textures/item/fish/ray.png" width="28" alt=""> Скат | `ray` | 2 кг – 50 кг | 8 кг | 40–180 см | море 1.1 | 6 |
| 47 | <img src="../../../common/src/main/resources/assets/riverfishing/textures/item/fish/mahi.png" width="28" alt=""> Махі-махі | `mahi` | 2 кг – 20 кг | 5 кг | 50–160 см | море 1.1 | 7 |
| 48 | <img src="../../../common/src/main/resources/assets/riverfishing/textures/item/fish/wahoo.png" width="28" alt=""> Ваху | `wahoo` | 5 кг – 40 кг | 12 кг | 80–210 см | море 1.0 | 7 |
| 49 | <img src="../../../common/src/main/resources/assets/riverfishing/textures/item/fish/yellowfin_tuna.png" width="28" alt=""> Жовтоперий тунець | `yellowfin_tuna` | 10 кг – 150 кг | 30 кг | 90–220 см | море 1.0 | 7 |
| 50 | <img src="../../../common/src/main/resources/assets/riverfishing/textures/item/fish/barracuda.png" width="28" alt=""> Барракуда | `barracuda` | 2 кг – 20 кг | 6 кг | 60–180 см | море 1.1 | 6 |
| 51 | <img src="../../../common/src/main/resources/assets/riverfishing/textures/item/fish/blue_marlin.png" width="28" alt=""> Синій марлін | `blue_marlin` | 50 кг – 400 кг | 110 кг | 200–450 см | море 1.0 | 7 |
| 52 | <img src="../../../common/src/main/resources/assets/riverfishing/textures/item/fish/sailfish.png" width="28" alt=""> Вітрильник | `sailfish` | 20 кг – 80 кг | 35 кг | 150–320 см | море 1.0 | 7 |
| 53 | <img src="../../../common/src/main/resources/assets/riverfishing/textures/item/fish/swordfish.png" width="28" alt=""> Риба-меч | `swordfish` | 30 кг – 300 кг | 80 кг | 150–400 см | море 1.0 | 7 |
| 54 | <img src="../../../common/src/main/resources/assets/riverfishing/textures/item/fish/mako.png" width="28" alt=""> Акула-мако | `mako` | 20 кг – 200 кг | 60 кг | 150–380 см | море 1.0 | 7 |
| 55 | <img src="../../../common/src/main/resources/assets/riverfishing/textures/item/fish/rotan.png" width="28" alt=""> Ротань | `rotan` | 20 г – 600 г | 90 г | 8–35 см | ставок 1.3, болото 1.3, калюжа 1.0, озеро 0.4, річка 0.2 | — |
| 56 | <img src="../../../common/src/main/resources/assets/riverfishing/textures/item/fish/nase.png" width="28" alt=""> Підуст | `nase` | 100 г – 1 кг | 400 г | 15–45 см | річка 1.3, озеро 0.1 | 2 |
| 57 | <img src="../../../common/src/main/resources/assets/riverfishing/textures/item/fish/vimba.png" width="28" alt=""> Рибець | `vimba` | 200 г – 1.5 кг | 700 г | 20–50 см | річка 1.2, озеро 0.3, море 0.2 | 3 |
| 58 | <img src="../../../common/src/main/resources/assets/riverfishing/textures/item/fish/smelt.png" width="28" alt=""> Корюшка | `smelt` | 20 г – 250 г | 60 г | 10–30 см | море 1.2, річка 0.3, озеро 0.2 | 1 |
| 59 | <img src="../../../common/src/main/resources/assets/riverfishing/textures/item/fish/whitefish.png" width="28" alt=""> Сиг | `whitefish` | 300 г – 4 кг | 1 кг | 25–70 см | озеро 1.3, річка 0.5, ставок 0.1 | 4 |
| 60 | <img src="../../../common/src/main/resources/assets/riverfishing/textures/item/fish/char.png" width="28" alt=""> Палія арктична | `char` | 300 г – 6 кг | 1.2 кг | 25–85 см | озеро 1.1, річка 1.0, море 0.2, ставок 0.1 | 5 |
| 61 | <img src="../../../common/src/main/resources/assets/riverfishing/textures/item/fish/lenok.png" width="28" alt=""> Ленок | `lenok` | 500 г – 6 кг | 1.5 кг | 30–90 см | річка 1.2, озеро 0.4 | 5 |
| 62 | <img src="../../../common/src/main/resources/assets/riverfishing/textures/item/fish/taimen.png" width="28" alt=""> Таймень | `taimen` | 3 кг – 60 кг | 11 кг | 60–180 см | річка 1.3, озеро 0.4 | 8 |
| 63 | <img src="../../../common/src/main/resources/assets/riverfishing/textures/item/fish/salmon.png" width="28" alt=""> Атлантичний лосось | `salmon` | 1.5 кг – 25 кг | 5 кг | 50–130 см | річка 1.1, море 1.0, озеро 0.2 | 6 |
| 64 | <img src="../../../common/src/main/resources/assets/riverfishing/textures/item/fish/pink_salmon.png" width="28" alt=""> Горбуша | `pink_salmon` | 800 г – 3.5 кг | 1.4 кг | 35–70 см | море 1.1, річка 1.0, озеро 0.1 | 3 |
| 65 | <img src="../../../common/src/main/resources/assets/riverfishing/textures/item/fish/sturgeon.png" width="28" alt=""> Осетер | `sturgeon` | 5 кг – 150 кг | 22 кг | 80–250 см | річка 1.2, озеро 0.6, море 0.3 | 9 |
| 66 | <img src="../../../common/src/main/resources/assets/riverfishing/textures/item/fish/halibut.png" width="28" alt=""> Палтус | `halibut` | 2 кг – 200 кг | 18 кг | 50–250 см | море 1.2 | 9 |
| 67 | <img src="../../../common/src/main/resources/assets/riverfishing/textures/item/fish/common_dace.png" width="28" alt=""> Ялець | `common_dace` | 20 г – 1 кг | 150 г | 15–40 см | річка 1.3, озеро 0.2 | — |
| 68 | <img src="../../../common/src/main/resources/assets/riverfishing/textures/item/fish/volga_zander.png" width="28" alt=""> Берш | `volga_zander` | 100 г – 2 кг | 450 г | 20–40 см | річка 1.3, озеро 0.6 | 3 |
| 69 | <img src="../../../common/src/main/resources/assets/riverfishing/textures/item/fish/white_eye_bream.png" width="28" alt=""> Клепець | `white_eye_bream` | 50 г – 1.3 кг | 300 г | 15–35 см | річка 1.3, озеро 0.3 | 2 |
| 70 | <img src="../../../common/src/main/resources/assets/riverfishing/textures/item/fish/round_goby.png" width="28" alt=""> Бичок-кругляк | `round_goby` | 10 г – 380 г | 100 г | 10–35 см | море 1.1, річка 1.0, озеро 0.6, ставок 0.2 | — |
| 71 | <img src="../../../common/src/main/resources/assets/riverfishing/textures/item/fish/bluefish.png" width="28" alt=""> Луфар | `bluefish` | 400 г – 14 кг | 2 кг | 30–110 см | море 1.2 | 6 |
| 72 | <img src="../../../common/src/main/resources/assets/riverfishing/textures/item/fish/bullseye_snakehead.png" width="28" alt=""> Плямистий змієголов | `bullseye_snakehead` | 400 г – 8 кг | 1.5 кг | 30–90 см | озеро 1.2, ставок 1.1, річка 1, болото 0.9 | 5 |
| 73 | <img src="../../../common/src/main/resources/assets/riverfishing/textures/item/fish/jack_crevalle.png" width="28" alt=""> Каранкс | `jack_crevalle` | 800 г – 30 кг | 4.5 кг | 35–120 см | море 1.2, річка 0.5, болото 0.3 | 7 |
| 74 | <img src="../../../common/src/main/resources/assets/riverfishing/textures/item/fish/mayan_cichlid.png" width="28" alt=""> Цихлазома майя | `mayan_cichlid` | 80 г – 1.2 кг | 300 г | 12–35 см | озеро 1.2, ставок 1.1, річка 1, болото 0.9 | 3 |
| 75 | <img src="../../../common/src/main/resources/assets/riverfishing/textures/item/fish/oscar.png" width="28" alt=""> Астронотус | `oscar` | 150 г – 1.6 кг | 450 г | 15–40 см | озеро 1.2, ставок 1.1, річка 1, болото 0.9 | 3 |
| 76 | <img src="../../../common/src/main/resources/assets/riverfishing/textures/item/fish/peacock_bass.png" width="28" alt=""> Павлиній окунь | `peacock_bass` | 300 г – 12 кг | 1.8 кг | 25–75 см | озеро 1.2, ставок 1.1, річка 1, болото 0.9 | 5 |
| 77 | <img src="../../../common/src/main/resources/assets/riverfishing/textures/item/fish/snook.png" width="28" alt=""> Снук | `snook` | 700 г – 25 кг | 3.5 кг | 35–140 см | море 1.2, річка 0.5, болото 0.3 | 7 |
| 78 | <img src="../../../common/src/main/resources/assets/riverfishing/textures/item/fish/striped_bass.png" width="28" alt=""> Смугастий лаврак | `striped_bass` | 500 г – 35 кг | 4 кг | 30–130 см | море 1.2, річка 0.5, болото 0.3 | 6 |
| 79 | <img src="../../../common/src/main/resources/assets/riverfishing/textures/item/fish/tarpon.png" width="28" alt=""> Тарпон | `tarpon` | 5 кг – 130 кг | 30 кг | 90–250 см | море 1.2, річка 0.5, болото 0.3 | 9 |
| 80 | <img src="../../../common/src/main/resources/assets/riverfishing/textures/item/fish/arapaima.png" width="28" alt=""> Арапайма | `arapaima` | 20 кг – 180 кг | 45 кг | 120–300 см | річка 1.2, озеро 1.0, болото 0.9, став 0.3 | 10 |
| 81 | <img src="../../../common/src/main/resources/assets/riverfishing/textures/item/fish/beluga.png" width="28" alt=""> Білуга | `beluga` | 40 кг – 600 кг | 90 кг | 150–500 см | річка 1.0, море 1.0, озеро 0.3 | 12 |
| 82 | <img src="../../../common/src/main/resources/assets/riverfishing/textures/item/fish/piraiba.png" width="28" alt=""> Пірайба | `piraiba` | 15 кг – 160 кг | 32 кг | 100–280 см | річка 1.3, озеро 0.5, болото 0.4, став 0.1 | 10 |
| 83 | <img src="../../../common/src/main/resources/assets/riverfishing/textures/item/fish/goliath_grouper.png" width="28" alt=""> Голіафовий групер | `goliath_grouper` | 20 кг – 320 кг | 55 кг | 100–250 см | море 1.2 | 10 |
| 84 | <img src="../../../common/src/main/resources/assets/riverfishing/textures/item/fish/bull_shark.png" width="28" alt=""> Тупорила акула | `bull_shark` | 30 кг – 230 кг | 65 кг | 150–350 см | море 1.1, річка 0.6, озеро 0.25 | 9 |
| 85 | <img src="../../../common/src/main/resources/assets/riverfishing/textures/item/fish/frilled_shark.png" width="28" alt=""> Плащоносна акула | `frilled_shark` | 8 кг – 50 кг | 16 кг | 90–200 см | море 1.0 | 11 |
| 86 | <img src="../../../common/src/main/resources/assets/riverfishing/textures/item/fish/golden_dorado.png" width="28" alt=""> Золотий дорадо | `golden_dorado` | 1.5 кг – 30 кг | 5.5 кг | 40–120 см | річка 1.3, озеро 0.6, болото 0.3, став 0.2 | 6 |
| 87 | <img src="../../../common/src/main/resources/assets/riverfishing/textures/item/fish/golden_crucian.png" width="28" alt=""> Золотий карась | `golden_crucian` | 60 г – 3 кг | 350 г | 12–45 см | став 1.4, болото 1.3, озеро 0.9, калюжа 0.5, річка 0.3 | 2 |
| 88 | <img src="../../../common/src/main/resources/assets/riverfishing/textures/item/fish/gorchak.png" width="28" alt=""> Гірчак | `gorchak` | 3 г – 30 г | 9 г | 3–9 см | став 1.2, озеро 1.0, річка 0.9, болото 0.8, калюжа 0.4 | — |
| 89 | <img src="../../../common/src/main/resources/assets/riverfishing/textures/item/fish/verkhovka.png" width="28" alt=""> Верхівка | `verkhovka` | 2 г – 18 г | 6 г | 3–8 см | став 1.4, озеро 1.0, болото 0.9, калюжа 0.9, річка 0.4 | — |
| 90 | <img src="../../../common/src/main/resources/assets/riverfishing/textures/item/fish/sculpin.png" width="28" alt=""> Бабець | `sculpin` | 5 г – 90 г | 25 г | 5–16 см | річка 1.3, озеро 0.4, став 0.1 | — |
| 91 | <img src="../../../common/src/main/resources/assets/riverfishing/textures/item/fish/tubenose_goby.png" width="28" alt=""> Бичок-цуцик | `tubenose_goby` | 3 г – 30 г | 10 г | 4–11 см | річка 1.1, озеро 0.7, став 0.5, море 0.5, болото 0.4 | — |
| 92 | <img src="../../../common/src/main/resources/assets/riverfishing/textures/item/fish/kutum.png" width="28" alt=""> Кутум | `kutum` | 500 г – 8 кг | 1.4 кг | 30–70 см | річка 1.1, море 1.0, озеро 0.4 | 4 |
| 93 | <img src="../../../common/src/main/resources/assets/riverfishing/textures/item/fish/naked_carp.png" width="28" alt=""> Голий короп | `naked_carp` | 2 кг – 20 кг | 4.5 кг | 40–105 см | озеро 1.2, став 1.1, річка 0.6, болото 0.4 | 5 |
| 94 | <img src="../../../common/src/main/resources/assets/riverfishing/textures/item/fish/mullet.png" width="28" alt=""> Кефаль | `mullet` | 300 г – 8 кг | 900 г | 25–80 см | море 1.2, річка 0.6, озеро 0.2 | 2 |
| 95 | <img src="../../../common/src/main/resources/assets/riverfishing/textures/item/fish/anglerfish.png" width="28" alt=""> Морський чорт | `anglerfish` | 2 кг – 40 кг | 7 кг | 40–150 см | море 1.0 | 8 |
| 96 | <img src="../../../common/src/main/resources/assets/riverfishing/textures/item/fish/black_marlin.png" width="28" alt=""> Чорний марлін | `black_marlin` | 30 кг – 700 кг | 95 кг | 150–460 см | море 1.0 | 9 |
| 97 | <img src="../../../common/src/main/resources/assets/riverfishing/textures/item/fish/blobfish.png" width="28" alt=""> Риба-крапля | `blobfish` | 1 кг – 10 кг | 2.5 кг | 25–70 см | море 1.0 | 8 |
| 98 | <img src="../../../common/src/main/resources/assets/riverfishing/textures/item/fish/bluefin_tuna.png" width="28" alt=""> Синьоперий тунець | `bluefin_tuna` | 20 кг – 400 кг | 60 кг | 100–300 см | море 1.0 | 8 |
| 99 | <img src="../../../common/src/main/resources/assets/riverfishing/textures/item/fish/loach.png" width="28" alt=""> В’юн | `loach` | 20 г – 150 г | 55 г | 10–30 см | болото 1.2, став 1.1, річка 0.8, озеро 0.7, калюжа 0.5 | — |
| 100 | <img src="../../../common/src/main/resources/assets/riverfishing/textures/item/fish/whale_shark.png" width="28" alt=""> Китова акула | `whale_shark` | 500 кг – 20000 кг | 2500 кг | 400–1200 см | море 1.0 | 12 |
| 101 | <img src="../../../common/src/main/resources/assets/riverfishing/textures/item/fish/nelma.png" width="28" alt=""> Нельма | `nelma` | 2 кг – 30 кг | 5 кг | 40–130 см | річка 1.1, озеро 0.9, море 0.2 | 6 |
| 102 | <img src="../../../common/src/main/resources/assets/riverfishing/textures/item/fish/ocean_sunfish.png" width="28" alt=""> Місяць-риба | `ocean_sunfish` | 100 кг – 1000 кг | 220 кг | 100–330 см | море 1.0 | 8 |
| 103 | <img src="../../../common/src/main/resources/assets/riverfishing/textures/item/fish/pollock.png" width="28" alt=""> Мінтай | `pollock` | 500 г – 15 кг | 1.8 кг | 25–90 см | море 1.2 | 4 |
| 104 | <img src="../../../common/src/main/resources/assets/riverfishing/textures/item/fish/red_piranha.png" width="28" alt=""> Червоночерева піранья | `red_piranha` | 300 г – 4 кг | 900 г | 15–45 см | річка 1.1, болото 0.9, озеро 0.8, став 0.5 | 4 |
| 105 | <img src="../../../common/src/main/resources/assets/riverfishing/textures/item/fish/tiger_shark.png" width="28" alt=""> Тигрова акула | `tiger_shark` | 50 кг – 800 кг | 140 кг | 200–500 см | море 1.1 | 9 |
| 106 | <img src="../../../common/src/main/resources/assets/riverfishing/textures/item/fish/koi_carp.png" width="28" alt=""> Короп кої | `koi_carp` | 800 г – 8 кг | 2.5 кг | 25–90 см | став 1.0, озеро 1.0, річка 0.4 | 3 |
| 107 | <img src="../../../common/src/main/resources/assets/riverfishing/textures/item/fish/linear_carp.png" width="28" alt=""> Лінійний короп | `linear_carp` | 1 кг – 14 кг | 3.2 кг | 33–95 см | озеро 1.2, став 1.2, річка 0.5, болото 0.4 | 3 |

## Ідеальна снасть

Збіглося — і вага поклівки різко йде вгору; що більша риба, то різкіше. Див. [коефіцієнт відповідності](fishing-mechanics.md#коефіцієнт-відповідності-m--твоя-снасть).

| Вид | Найкращі наживки (оцінка) | Гачок | Волосінь | Прикормка (фракція / поживність) | Повідець |
|---|---|---|---|---|---|
| Азійська арована | livebait 1, fish_strip 0.9, wobbler 0.9, silicone 0.85, popper 0.75, fly_streamer 0.7, wacky_worm 0.55, fly_dry_fly 0.4 | №5 | braid 0.2 ±0.06 | 0.1 / 0.24 | — |
| Акантикус адоніс | worm 1, dough 0.75, boilie 0.7, fish_strip 0.7, jig 0.6, fly_pellet 0.55, silicone 0.55 | №4 | braid 0.22 ±0.06 | 0.7 / 0.75 | — |
| Акантикус гістрикс | dough 1, corn 0.9, worm 0.85, pea 0.8, fly_pellet 0.7 | №4 | braid 0.21 ±0.06 | 0.8 / 0.83 | — |
| Акула-мако | giant_spoon 1, livebait 1, octopus_jig 0.95, swimbait 0.95, fish_strip 0.9, wobbler 0.7 | №1 | braid 0.4 ±0.06 | 1 / 0.75 | **yes** |
| Акулячий сом | dough 1, bread 0.9, corn 0.9, worm 0.85, fly_pellet 0.7, fish_strip 0.3 | №2 | braid 0.29 ±0.06 | 0.75 / 0.88 | — |
| Амфіпріон оцеляріс | bloodworm 1, maggot 0.95, worm 0.9, fly_shrimp 0.6 | №16 | fluoro 0.09 ±0.06 | 0.2 / 0.4 | — |
| Амія мулова | livebait 1, fish_strip 0.9, wobbler 0.9, silicone 0.85, crankbait 0.8, spoon 0.8, swimbait 0.8, spinnerbait 0.75, fly_streamer 0.7, chicken_liver 0.6, wacky_worm 0.55, worm 0.55 | №5 | braid 0.21 ±0.06 | 0.03 / 0.14 | **yes** |
| Анабас | worm 1, bloodworm 0.85, maggot 0.8, fish_strip 0.7, fly_nymph 0.6, jig 0.6, silicone 0.55, fly_ant 0.5, fly_dry_fly 0.5 | №12 | mono 0.14 ±0.06 | 0.65 / 0.75 | — |
| Арапайма | livebait 1, fish_strip 0.9, giant_spoon 0.85, swimbait 0.85, wobbler 0.75, silicone 0.6 | №1 | braid 0.45 ±0.08 | 0.95 / 0.8 | **yes** |
| Аротрон зірчастий | fish_strip 1, jig 0.95, octopus_jig 0.8, fly_shrimp 0.6 | №6 | fluoro 0.36 ±0.06 | 0.12 / 0.32 | **yes** |
| Аротрон колючий | fish_strip 1, jig 0.95, octopus_jig 0.8, fly_shrimp 0.6 | №9 | fluoro 0.22 ±0.06 | 0.12 / 0.32 | **yes** |
| Астатотилапія каліптера | bloodworm 1, maggot 0.95, worm 0.9, fly_nymph 0.85, silicone 0.5 | №13 | fluoro 0.12 ±0.06 | 0.25 / 0.45 | — |
| Астронотус | worm 1.2, livebait 1.1, wacky_worm 1, maggot 0.9, silicone 0.9, jig 0.8 | №8 | mono 0.16 ±0.04 | 0.47 / 0.68 | — |
| Атлантичний лосось | spoon 1, wobbler 0.9, spinner 0.8, fish_strip 0.5 | №4 | braid 0.25 ±0.06 | 0.77 / 0.75 | — |
| Африканська риба-ніж | worm 1, bloodworm 0.85, maggot 0.8, fish_strip 0.7, fly_nymph 0.6, jig 0.6, spinner 0.6, silicone 0.55, fly_streamer 0.5 | №9 | braid 0.1 ±0.06 | 0.1 / 0.25 | — |
| Африканська тигрова риба | livebait 1, fish_strip 0.9, wobbler 0.9, jig 0.85, spinner 0.85, spoon 0.8 | №4 | braid 0.26 ±0.06 | 0 / 0.07 | **yes** |
| Африканська щука | livebait 1, fish_strip 0.9, wobbler 0.9, jig 0.85, spinner 0.85, fly_streamer 0.7 | №6 | braid 0.15 ±0.06 | 0 / 0.07 | **yes** |
| Африканський кларієвий сом | worm 1, dough 0.75, boilie 0.7, chicken_liver 0.7, fish_strip 0.7, livebait 0.6, silicone 0.55, wobbler 0.55, spoon 0.5, swimbait 0.5 | №2 | braid 0.31 ±0.06 | 0.7 / 0.8 | — |
| Африканський очний ніж | livebait 1, fish_strip 0.9, wobbler 0.9, silicone 0.85, spinner 0.85, spoon 0.8, fly_streamer 0.7, worm 0.55 | №6 | braid 0.15 ±0.06 | 0.05 / 0.17 | — |
| Бабець | worm 1, bloodworm 0.9, maggot 0.8, livebait 0.3 | №14 | mono 0.14 ±0.04 | 0.12 / 0.5 | — |
| Багатопер нільський | livebait 1, fish_strip 0.9, jig 0.85, chicken_liver 0.6, worm 0.55 | №7 | mono 0.22 ±0.06 | 0.15 / 0.39 | — |
| Багатопер сенегальський | livebait 1, fish_strip 0.9, worm 0.55, bloodworm 0.35 | №7 | mono 0.19 ±0.06 | 0.15 / 0.39 | — |
| Багрус докмак | livebait 1, fish_strip 0.9, jig 0.85, chicken_liver 0.6, worm 0.55 | №3 | braid 0.27 ±0.06 | 0.35 / 0.56 | — |
| Барамунді | livebait 1, fish_strip 0.9, wobbler 0.9, silicone 0.85, castmaster 0.8, crankbait 0.8, spoon 0.8, swimbait 0.8, popper 0.75, spinnerbait 0.75 | №2 | braid 0.32 ±0.06 | 0.03 / 0.14 | **yes** |
| Барракуда | giant_spoon 1.1, swimbait 1.05, wobbler 1, octopus_jig 0.9, silicone 0.9, spinner 0.7, spinnerbait 0.7, fish_strip 0.6 | №2 | braid 0.3 ±0.08 | 0.79 / 0.75 | **yes** |
| Батибатес лютий | livebait 1, fish_strip 0.9, wobbler 0.9, jig 0.85, spinner 0.85, fly_streamer 0.7 | №10 | fluoro 0.18 ±0.06 | 0.12 / 0.32 | — |
| Берш | silicone 1, bladebait 0.95, jig 0.95, livebait 0.9, worm 0.7, crankbait 0.6, wobbler 0.55 | №6 | braid 0.1 ±0.04 | 0.47 / 0.7 | — |
| Бестер | worm 1, bloodworm 0.85, dough 0.75, fish_strip 0.7, fly_pellet 0.55 | №2 | braid 0.27 ±0.06 | 0.55 / 0.65 | — |
| Бичок-кругляк | worm 1, fish_strip 0.9, bloodworm 0.7, maggot 0.6, silicone 0.5 | №8 | mono 0.2 ±0.06 | 0.29 / 0.62 | — |
| Бичок-цуцик | worm 1, bloodworm 0.95, maggot 0.9, fish_strip 0.4 | №16 | mono 0.12 ±0.04 | 0.12 / 0.5 | — |
| Блакитна тиляпія | dough 1, bread 0.9, corn 0.9, worm 0.85, pea 0.8, pearl_barley 0.8, bloodworm 0.7, maggot 0.7, fish_strip 0.3, jig 0.25 | №10 | mono 0.21 ±0.06 | 0.8 / 0.83 | — |
| Блакитний хірург | bloodworm 1, maggot 0.95, worm 0.9, fly_shrimp 0.6 | №13 | fluoro 0.17 ±0.06 | 0.2 / 0.4 | — |
| Блюгіл | worm 1, maggot 0.9, bloodworm 0.8, corn 0.5 | №12 | mono 0.12 ±0.05 | 0.34 / 0.61 | — |
| Білизна | spoon 1, castmaster 0.9, wobbler 0.9, popper 0.85, spinnerbait 0.85, spinner 0.8, bladebait 0.7, crankbait 0.7, swimbait 0.7, wacky_worm 0.5 | №6 | braid 0.12 ±0.04 | 0.66 / 0.5 | — |
| Білий американський лаврак | livebait 1, jig 0.85, silicone 0.85, spinner 0.85, castmaster 0.8, spoon 0.8 | №9 | braid 0.17 ±0.06 | 0.03 / 0.14 | — |
| Білий амур | corn 1, bread 0.9, dough 0.8, pea 0.7, boilie 0.5 | №6 | mono 0.3 ±0.08 | 0.77 / 0.66 | — |
| Білий краппі | livebait 1, jig 0.85, silicone 0.85, spinner 0.85, worm 0.55, bloodworm 0.35 | №11 | mono 0.21 ±0.06 | 0.17 / 0.32 | — |
| Білуга | livebait 1, fish_strip 0.9, chicken_liver 0.85, worm 0.5 | №1 | braid 0.55 ±0.1 | 0.98 / 0.82 | **yes** |
| В'язь | worm 1, popper 0.9, corn 0.8, maggot 0.8, bread 0.7, crankbait 0.7, bladebait 0.6, pea 0.6, spinnerbait 0.6, wacky_worm 0.6 | №10 | mono 0.18 ±0.05 | 0.54 / 0.66 | — |
| Ваху | giant_spoon 1.15, octopus_jig 1, swimbait 1, wobbler 1, castmaster 0.8, silicone 0.7 | №1 | braid 0.4 ±0.08 | 0.88 / 0.5 | **yes** |
| Великоротий бас | popper 1.2, spinnerbait 1.1, wacky_worm 1.05, swimbait 1, wobbler 1, silicone 0.95, crankbait 0.9, jig 0.9, livebait 0.8, spinner 0.7 | №4 | braid 0.16 ±0.05 | 0.62 / 0.5 | — |
| Верховодка | maggot 1, bread 0.9, dough 0.8, mormyshka 0.8, bloodworm 0.7 | №16 | mono 0.14 ±0.04 | 0.14 / 0.46 | — |
| Верхівка | maggot 1, bread 0.95, bloodworm 0.85, dough 0.8 | №16 | mono 0.1 ±0.03 | 0.08 / 0.38 | — |
| Веслоніс | jig 1, castmaster 0.85, giant_spoon 0.7 | №2 | braid 0.33 ±0.06 | 0 / 0 | — |
| Вугор | worm 1, livebait 0.8, chicken_liver 0.7, jig 0.7 | №8 | mono 0.25 ±0.06 | 0.56 / 0.74 | — |
| Вугор електричний | livebait 1, fish_strip 0.9, jig 0.85, worm 0.55 | №2 | braid 0.25 ±0.06 | 0.05 / 0.21 | **yes** |
| Вугор мармуровий | livebait 1, fish_strip 0.9, chicken_liver 0.6, worm 0.55, bloodworm 0.35 | №3 | braid 0.25 ±0.06 | 0.23 / 0.45 | **yes** |
| Вусач Валецького | worm 1, bloodworm 0.85, maggot 0.8, dough 0.75, bread 0.7, fly_nymph 0.6, jig 0.6, fly_ant 0.5 | №8 | mono 0.2 ±0.06 | 0.8 / 0.8 | — |
| Вухатий окунь | worm 1, bloodworm 0.85, maggot 0.8, dough 0.75, bread 0.7, fish_strip 0.7, fly_nymph 0.6, jig 0.6, spinner 0.6, mormyshka 0.55, silicone 0.55, fly_ant 0.5, fly_dry_fly 0.5, fly_streamer 0.5 | №15 | mono 0.16 ±0.06 | 0.45 / 0.55 | — |
| Вітрильник | livebait 1.1, octopus_jig 1, wobbler 1, giant_spoon 0.95, popper 0.8, silicone 0.7 | №1 | braid 0.3 ±0.08 | 1 / 0.5 | — |
| В’юн | bloodworm 1.1, worm 1, maggot 0.8, mormyshka 0.6, dough 0.4 | №16 | mono 0.12 ±0.04 | 0.2 / 0.5 | — |
| Гетеротис нільський | dough 1, bread 0.9, corn 0.9, worm 0.85, pea 0.8, fly_pellet 0.7 | №5 | braid 0.21 ±0.06 | 0.23 / 0.39 | — |
| Гнатонем Петерса | bloodworm 1, maggot 0.95, worm 0.9, fly_nymph 0.85 | №10 | fluoro 0.16 ±0.06 | 0.4 / 0.6 | — |
| Голий короп | boilie 1, corn 0.85, pea 0.6, pearl_barley 0.55, dough 0.5 | №4 | mono 0.35 ±0.08 | 0.75 / 0.88 | — |
| Головень | popper 1, wobbler 0.9, bread 0.8, spinner 0.8, crankbait 0.75, castmaster 0.7, spinnerbait 0.7, worm 0.7, bladebait 0.6, wacky_worm 0.6, corn 0.5 | №8 | mono 0.16 ±0.05 | 0.53 / 0.56 | — |
| Голіафовий групер | livebait 1, fish_strip 0.95, octopus_jig 0.8, swimbait 0.8, giant_spoon 0.5 | №1 | braid 0.55 ±0.1 | 0.97 / 0.78 | **yes** |
| Горбань плямистий | livebait 1, fish_strip 0.9, wobbler 0.9, jig 0.85, silicone 0.85, popper 0.75 | №4 | braid 0.2 ±0.06 | 0.15 / 0.39 | — |
| Горбань темний | livebait 1, fish_strip 0.9, silicone 0.85, castmaster 0.8, spoon 0.8, chicken_liver 0.6, worm 0.55 | №3 | braid 0.3 ±0.06 | 0.15 / 0.39 | — |
| Горбань червоний | livebait 1, wobbler 0.9, jig 0.85, silicone 0.85, spoon 0.8, worm 0.55 | №3 | braid 0.29 ±0.06 | 0.15 / 0.39 | — |
| Горбуша | spoon 1, spinner 0.9, castmaster 0.8, fish_strip 0.5 | №6 | braid 0.18 ±0.05 | 0.61 / 0.75 | — |
| Гібрид американської палії та бичачої форелі | worm 1, fly_nymph 0.6, spinner 0.6, fly_streamer 0.5, spoon 0.5 | №7 | fluoro 0.27 ±0.06 | 0.05 / 0.25 | — |
| Гібрид великоротого та малоротого окуня | jig 1, silicone 0.95, crankbait 0.85, swimbait 0.85, wacky_worm 0.85, spinnerbait 0.7 | №7 | fluoro 0.26 ±0.06 | 0.05 / 0.2 | — |
| Гібрид калуги і стерляді | worm 1, bloodworm 0.85, dough 0.75, chicken_liver 0.7, fish_strip 0.7 | №2 | braid 0.28 ±0.06 | 0.55 / 0.65 | — |
| Гібрид канального та блакитного сомика | worm 1, dough 0.75, chicken_liver 0.7, fish_strip 0.7, livebait 0.6 | №3 | braid 0.25 ±0.06 | 0.75 / 0.8 | — |
| Гібрид лабео гоніус та катли | dough 1, corn 0.9, worm 0.85, fly_pellet 0.7 | №5 | mono 0.31 ±0.06 | 0.98 / 0.94 | — |
| Гібрид ляща та плітки | dough 1, bread 0.9, corn 0.9, worm 0.85, pearl_barley 0.8, maggot 0.7 | №10 | mono 0.22 ±0.06 | 0.98 / 0.88 | — |
| Гібрид мальми та бичачої форелі | fish_strip 1, spinner 0.95, spoon 0.9, fly_streamer 0.8, worm 0.6 | №7 | fluoro 0.29 ±0.06 | 0.03 / 0.17 | — |
| Гібрид панцирної щуки та міссісіпського панцирника | livebait 1, fish_strip 0.9, wobbler 0.9, jig 0.85, fly_streamer 0.7 | №2 | braid 0.3 ±0.06 | 0 / 0.03 | **yes** |
| Гібрид плітки та червонопірки | dough 1, bread 0.9, corn 0.9, worm 0.85, maggot 0.7 | №10 | mono 0.2 ±0.06 | 0.98 / 0.88 | — |
| Гібрид роху та катли | dough 1, corn 0.9, worm 0.85, fly_pellet 0.7 | №4 | mono 0.34 ±0.06 | 0.98 / 0.94 | — |
| Гібрид роху та лабео гоніус | dough 1, corn 0.9, worm 0.85, fly_pellet 0.7 | №5 | mono 0.3 ±0.06 | 0.98 / 0.94 | — |
| Гібрид синьозябрового та червоновухого сонячника | corn 1, bread 0.95, worm 0.9, maggot 0.75, jig 0.25 | №14 | mono 0.19 ±0.06 | 0.52 / 0.61 | — |
| Гібрид сонячного окуня синьозябрового та звичайного | corn 1, bread 0.95, worm 0.9, maggot 0.75, jig 0.25, silicone 0.25 | №15 | mono 0.17 ±0.06 | 0.52 / 0.61 | — |
| Гібрид сонячного окуня синьозябрового та зеленого | corn 1, bread 0.95, worm 0.9, maggot 0.75, jig 0.25, silicone 0.25 | №14 | mono 0.18 ±0.06 | 0.52 / 0.61 | — |
| Гібрид сонячного окуня синьозябрового та червоногрудого | worm 1, maggot 0.8, bread 0.7, jig 0.6, silicone 0.55 | №15 | mono 0.16 ±0.06 | 0.45 / 0.55 | — |
| Гібрид сонячного окуня червоновухого та зеленого | worm 1, maggot 0.8, bread 0.7, jig 0.6, silicone 0.55 | №14 | mono 0.19 ±0.06 | 0.45 / 0.55 | — |
| Гібрид чавичі та горбуші | jig 1, spinner 1, wobbler 0.95, spoon 0.9, fly_streamer 0.85 | №6 | braid 0.21 ±0.06 | 0 / 0.1 | — |
| Гібрид чавичі та кижуча | fish_strip 1, wobbler 1, spinner 0.95, spoon 0.9, fly_streamer 0.8 | №5 | braid 0.25 ±0.06 | 0 / 0.07 | — |
| Гібрид чорного та білого крапі | livebait 1, jig 0.85, silicone 0.85, spinner 0.85, worm 0.55 | №11 | mono 0.21 ±0.06 | 0.17 / 0.32 | — |
| Гібридна тиляпія (блакитна × мозамбікська) | dough 1, bread 0.9, corn 0.9, worm 0.85, pea 0.8, fly_pellet 0.7 | №10 | mono 0.22 ±0.06 | 0.8 / 0.83 | — |
| Гібридна тиляпія (нільська х блакитна) | dough 1, bread 0.9, corn 0.9, worm 0.85, pea 0.8, pearl_barley 0.8 | №9 | mono 0.24 ±0.06 | 0.8 / 0.83 | — |
| Гібридний смугастий лаврак (вайпер) | livebait 1, jig 0.85, silicone 0.85, castmaster 0.8, crankbait 0.8, spoon 0.8 | №6 | braid 0.22 ±0.06 | 0.03 / 0.14 | — |
| Гігантська цихліда | livebait 1, fish_strip 0.9, wobbler 0.9, jig 0.85, spinner 0.85, worm 0.55 | №7 | fluoro 0.26 ±0.06 | 0.12 / 0.32 | — |
| Гігантський гурамі | dough 1, bread 0.9, corn 0.9, worm 0.85, pea 0.8, maggot 0.7, fly_ant 0.45, fly_dry_fly 0.45, fish_strip 0.3 | №8 | mono 0.29 ±0.06 | 0.75 / 0.83 | — |
| Гігантський змієголов | livebait 1, fish_strip 0.9, wobbler 0.9, silicone 0.85, castmaster 0.8, crankbait 0.8, spoon 0.8, swimbait 0.8, popper 0.75, spinnerbait 0.75, chicken_liver 0.6, wacky_worm 0.55 | №4 | braid 0.25 ±0.06 | 0 / 0.03 | **yes** |
| Гігантський меконгський сом | dough 1, bread 0.9, corn 0.9, boilie 0.85, pea 0.8, pearl_barley 0.8 | №1 | braid 0.43 ±0.06 | 0.75 / 0.88 | — |
| Гігантський прісноводний скат | livebait 1, fish_strip 0.9, chicken_liver 0.6, worm 0.55 | №1 | braid 0.49 ±0.06 | 0.28 / 0.49 | **yes** |
| Гідролік скумбрієподібний | livebait 1, fish_strip 0.9, wobbler 0.9, jig 0.85, spoon 0.8 | №4 | braid 0.24 ±0.06 | 0 / 0.07 | **yes** |
| Гімнарх нільський | livebait 1, fish_strip 0.9, wobbler 0.9, jig 0.85, worm 0.55 | №4 | fluoro 0.34 ±0.06 | 0.2 / 0.42 | **yes** |
| Гірчак | bloodworm 1, maggot 1, bread 0.8, dough 0.7 | №16 | mono 0.1 ±0.03 | 0.1 / 0.4 | — |
| Дзеркальний короп | boilie 1, corn 0.8, pea 0.6, pearl_barley 0.5 | №6 | mono 0.3 ±0.08 | 0.72 / 0.85 | — |
| Дистиходус довгоносий | dough 1, bread 0.9, corn 0.9, pea 0.8, fly_pellet 0.7 | №8 | mono 0.18 ±0.06 | 0.92 / 0.94 | — |
| Дистиходус шестисмугий | dough 1, bread 0.9, corn 0.9, worm 0.85, pea 0.8, fly_pellet 0.7 | №6 | mono 0.24 ±0.06 | 0.92 / 0.94 | — |
| Електричний сом звичайний | worm 1, dough 0.75, chicken_liver 0.7, fish_strip 0.7, livebait 0.6 | №3 | braid 0.25 ±0.06 | 0.7 / 0.8 | — |
| Жовтий американський лаврак | livebait 1, jig 0.85, silicone 0.85, spinner 0.85, worm 0.55 | №9 | braid 0.14 ±0.06 | 0.03 / 0.14 | — |
| Жовтоперий тунець | giant_spoon 1.05, octopus_jig 1, swimbait 1, livebait 0.9, wobbler 0.9, fish_strip 0.8, silicone 0.7 | №1 | braid 0.4 ±0.08 | 0.99 / 0.75 | — |
| Зелений сонячний окунь | worm 1, maggot 0.8, bread 0.7, jig 0.6, silicone 0.55 | №14 | mono 0.18 ±0.06 | 0.45 / 0.55 | — |
| Золотий дорадо | wobbler 1, swimbait 0.95, spinner 0.9, spinnerbait 0.9, spoon 0.9, popper 0.85, crankbait 0.8, silicone 0.8, livebait 0.7 | №2 | braid 0.28 ±0.06 | 0.6 / 0.7 | **yes** |
| Золотий карась | worm 1, bread 0.9, dough 0.9, maggot 0.85, corn 0.7, pearl_barley 0.6 | №12 | mono 0.18 ±0.05 | 0.35 / 0.6 | — |
| Золотий махсир | dough 1, boilie 0.95, fish_strip 0.95, livebait 0.85, spinner 0.75, wobbler 0.75, spoon 0.7, castmaster 0.65, crankbait 0.65, fly_streamer 0.65, swimbait 0.65, bladebait 0.55 | №2 | braid 0.39 ±0.06 | 0.65 / 0.8 | — |
| Йорж | bloodworm 1, mormyshka 1, worm 1, maggot 0.7 | №14 | mono 0.14 ±0.04 | 0.22 / 0.54 | — |
| Каламоїхт | worm 1, bloodworm 0.85, maggot 0.8, fish_strip 0.7, jig 0.6, silicone 0.55, fly_streamer 0.5 | №9 | mono 0.14 ±0.06 | 0.3 / 0.55 | — |
| Калуга | livebait 1, fish_strip 0.9, wobbler 0.9, silicone 0.85, spoon 0.8, swimbait 0.8, giant_spoon 0.75, chicken_liver 0.6 | №1 | braid 0.54 ±0.06 | 0.28 / 0.45 | — |
| Камбала | fish_strip 1, worm 0.9, maggot 0.5 | №6 | mono 0.3 ±0.08 | 0.56 / 0.71 | — |
| Кампіломормірус хоботконосий | bloodworm 1, maggot 0.95, worm 0.9, fly_nymph 0.85 | №11 | fluoro 0.12 ±0.06 | 0.4 / 0.6 | — |
| Канадський судак | livebait 1, jig 0.85, silicone 0.85, crankbait 0.8, worm 0.55 | №6 | fluoro 0.25 ±0.06 | 0.05 / 0.21 | **yes** |
| Канальний сомик | livebait 1.1, chicken_liver 1, worm 0.8, swimbait 0.7, maggot 0.6, boilie 0.5 | №2 | mono 0.35 ±0.08 | 0.73 / 0.77 | — |
| Каранкс | popper 1.25, giant_spoon 1.15, castmaster 1.1, spoon 1.1, swimbait 1.1, livebait 1, silicone 1, wobbler 0.95, spinnerbait 0.8 | №1 | braid 0.35 ±0.08 | 0.76 / 0.5 | — |
| Карасекороп | dough 1, bread 0.9, corn 0.9, boilie 0.85, worm 0.85, pearl_barley 0.8 | №9 | mono 0.27 ±0.06 | 0.98 / 0.88 | — |
| Карась | worm 1, dough 0.9, maggot 0.8, corn 0.6, bread 0.5 | №12 | mono 0.18 ±0.06 | 0.4 / 0.63 | — |
| Карпозубик дияволів | bloodworm 1, maggot 0.95, worm 0.9, fly_nymph 0.85 | №16 | mono 0.06 ±0.06 | 0.4 / 0.5 | — |
| Карпозубик пустельний | bloodworm 1, maggot 0.95, worm 0.9, fly_nymph 0.85 | №16 | mono 0.07 ±0.06 | 0.4 / 0.5 | — |
| Карпозубик солонуватоводний | bloodworm 1, maggot 0.95, worm 0.9, fly_nymph 0.85 | №16 | mono 0.07 ±0.06 | 0.4 / 0.5 | — |
| Картографічний аротрон | fish_strip 1, jig 0.95, silicone 0.9, worm 0.6 | №8 | fluoro 0.26 ±0.06 | 0.12 / 0.32 | **yes** |
| Катбоу (гібрид райдужної форелі та лосося Кларка) | worm 1, fly_nymph 0.6, spinner 0.6, fly_dry_fly 0.5, spoon 0.5 | №6 | braid 0.22 ±0.06 | 0 / 0.1 | — |
| Катля | dough 1, bread 0.9, corn 0.9, boilie 0.85, worm 0.85, fly_pellet 0.7, fly_dry_fly 0.45 | №4 | mono 0.45 ±0.06 | 0.98 / 0.94 | — |
| Кета | jig 1, spinner 1, wobbler 0.95, spoon 0.9, fly_streamer 0.85 | №6 | braid 0.23 ±0.06 | 0 / 0.1 | — |
| Кефаль | bread 1, dough 0.95, maggot 0.7, worm 0.6, corn 0.4, pea 0.3 | №12 | mono 0.18 ±0.05 | 0.45 / 0.55 | — |
| Кижуч | fish_strip 1, wobbler 1, spinner 0.95, spoon 0.9, fly_streamer 0.8 | №5 | braid 0.23 ±0.06 | 0 / 0.07 | — |
| Китайський махсир | livebait 1, wobbler 0.9, jig 0.85, spinner 0.85, spoon 0.8, fly_streamer 0.7, worm 0.55, fly_nymph 0.4 | №7 | braid 0.15 ±0.06 | 0.33 / 0.56 | — |
| Китова акула | fish_strip 0.35, livebait 0.3 | №1 | braid 0.6 ±0.05 | 1 / 1 | — |
| Кларіас ангольський | worm 1, dough 0.75, chicken_liver 0.7, fish_strip 0.7, livebait 0.6 | №5 | braid 0.11 ±0.06 | 0.7 / 0.8 | — |
| Клепець | worm 1, maggot 0.95, bloodworm 0.85, pearl_barley 0.5, corn 0.4 | №12 | mono 0.18 ±0.05 | 0.42 / 0.61 | — |
| Короп | boilie 1, corn 0.8, pea 0.6, pearl_barley 0.5 | №6 | mono 0.3 ±0.08 | 0.73 / 0.85 | — |
| Короп кої | boilie 1, corn 0.8, bread 0.6, pea 0.6 | №6 | mono 0.3 ±0.08 | 0.69 / 0.75 | — |
| Короткохвостий річковий хвостокіл | livebait 1, fish_strip 0.9, chicken_liver 0.6, worm 0.55 | №1 | braid 0.4 ±0.06 | 0.28 / 0.49 | **yes** |
| Корюшка | bloodworm 1, mormyshka 0.9, fish_strip 0.8, worm 0.7 | №16 | mono 0.12 ±0.05 | 0.22 / 0.56 | — |
| Кої Асагі | boilie 1, corn 0.8, bread 0.6, pea 0.6 | №6 | mono 0.3 ±0.08 | 0.69 / 0.75 | — |
| Кої Бекко | boilie 1, corn 0.8, bread 0.6, pea 0.6 | №6 | mono 0.3 ±0.08 | 0.69 / 0.75 | — |
| Кої Кохаку | boilie 1, corn 0.8, bread 0.6, pea 0.6 | №6 | mono 0.3 ±0.08 | 0.69 / 0.75 | — |
| Кої Сьова Санке | boilie 1, corn 0.8, bread 0.6, pea 0.6 | №6 | mono 0.3 ±0.08 | 0.69 / 0.75 | — |
| Кої Танчо Санке | boilie 1, corn 0.8, bread 0.6, pea 0.6 | №6 | mono 0.3 ±0.08 | 0.69 / 0.75 | — |
| Краснопірка | bread 1, dough 0.9, maggot 0.8 | №14 | mono 0.14 ±0.04 | 0.3 / 0.49 | — |
| Кутум | worm 1, bloodworm 0.9, maggot 0.8, fish_strip 0.5, pea 0.4 | №8 | mono 0.25 ±0.06 | 0.6 / 0.7 | — |
| Лабео дрібнолускатий | dough 1, bread 0.9, corn 0.9, worm 0.85, pearl_barley 0.8, bloodworm 0.7, maggot 0.7, jig 0.25 | №4 | mono 0.36 ±0.06 | 0.98 / 0.94 | — |
| Лабеобарбус кімберлейський | worm 1, corn 0.7, fish_strip 0.7, livebait 0.6, spinner 0.6, fly_streamer 0.5 | №5 | braid 0.25 ±0.06 | 0.65 / 0.8 | — |
| Лаврак | wobbler 1, silicone 0.95, livebait 0.9, popper 0.8, swimbait 0.8, fish_strip 0.7 | №4 | braid 0.25 ±0.06 | 0.62 / 0.75 | — |
| Ленок | wobbler 1, spinner 0.9, spoon 0.9, crankbait 0.8, worm 0.5 | №6 | braid 0.14 ±0.05 | 0.62 / 0.7 | — |
| Лепідіолампрологус видовжений | livebait 1, fish_strip 0.9, jig 0.85, silicone 0.85, spinner 0.85, fly_streamer 0.7 | №11 | fluoro 0.16 ±0.06 | 0.12 / 0.32 | — |
| Лин | worm 1, dough 0.8, corn 0.7, bread 0.6, maggot 0.6 | №10 | mono 0.2 ±0.05 | 0.54 / 0.63 | — |
| Лопатоніс звичайний | worm 1, bloodworm 0.85, dough 0.75, chicken_liver 0.7, fish_strip 0.7 | №3 | braid 0.19 ±0.06 | 0.55 / 0.65 | — |
| Луфар | giant_spoon 1.2, spoon 1.2, castmaster 1.15, fish_strip 1.1, swimbait 1.1, wobbler 1, livebait 0.9, silicone 0.9 | №2 | braid 0.28 ±0.07 | 0.66 / 0.75 | **yes** |
| Луціан чорнохвостий | livebait 1, fish_strip 0.9, wobbler 0.9, silicone 0.85, castmaster 0.8, spoon 0.8 | №12 | fluoro 0.2 ±0.06 | 0.1 / 0.28 | — |
| Лящ | maggot 1, worm 0.9, pearl_barley 0.8, mormyshka 0.7, corn 0.6, bread 0.4, boilie 0.3 | №10 | braid 0.1 ±0.04 | 0.56 / 0.68 | — |
| Лінійний короп | boilie 1, corn 0.8, pea 0.6, pearl_barley 0.5 | №6 | mono 0.3 ±0.08 | 0.72 / 0.85 | — |
| Малайський махсир | dough 1, boilie 0.95, fish_strip 0.95, livebait 0.85, fly_nymph 0.75, spinner 0.75, wobbler 0.75, fly_dry_fly 0.7, spoon 0.7, crankbait 0.65, fly_streamer 0.65 | №4 | braid 0.24 ±0.06 | 0.65 / 0.8 | — |
| Мальма (палія мальма) | fish_strip 1, wobbler 1, spinner 0.95, spoon 0.9, fly_streamer 0.8, worm 0.6 | №6 | fluoro 0.34 ±0.06 | 0.03 / 0.17 | — |
| Марена звичайна | dough 1, bread 0.9, corn 0.9, boilie 0.85, worm 0.85, pea 0.8, pearl_barley 0.8, bloodworm 0.7, maggot 0.7, fish_strip 0.3, silicone 0.2 | №5 | mono 0.32 ±0.06 | 0.92 / 0.88 | — |
| Марена кримська | worm 1, bloodworm 0.85, maggot 0.8, dough 0.75, bread 0.7, fly_nymph 0.6, jig 0.6, fly_ant 0.5 | №7 | mono 0.24 ±0.06 | 0.8 / 0.8 | — |
| Мармуровий протоптер | worm 1, dough 0.75, chicken_liver 0.7, fish_strip 0.7, livebait 0.6 | №3 | braid 0.24 ±0.06 | 0.35 / 0.6 | **yes** |
| Махі-махі | livebait 1.05, giant_spoon 1, octopus_jig 1, swimbait 1, wobbler 1, popper 0.9, silicone 0.8, fish_strip 0.6 | №2 | braid 0.3 ±0.08 | 0.77 / 0.75 | — |
| Минь | livebait 1, chicken_liver 0.9, worm 0.9, bladebait 0.8, jig 0.75, swimbait 0.6 | №6 | mono 0.3 ±0.08 | 0.62 / 0.75 | — |
| Мозамбіцька тиляпія | dough 1, bread 0.9, corn 0.9, worm 0.85, pea 0.8, pearl_barley 0.8, bloodworm 0.7, maggot 0.7, fish_strip 0.3, jig 0.25 | №11 | mono 0.18 ±0.06 | 0.8 / 0.83 | — |
| Морміропс вугроподібний | livebait 1, fish_strip 0.9, jig 0.85, worm 0.55, bloodworm 0.35 | №5 | fluoro 0.32 ±0.06 | 0.2 / 0.42 | **yes** |
| Морський вугор | fish_strip 1, livebait 1, worm 0.4 | №1 | mono 0.5 ±0.1 | 0.84 / 0.74 | **yes** |
| Морський чорт | livebait 1.1, fish_strip 1 | №1 | braid 0.3 ±0.08 | 0.72 / 0.8 | **yes** |
| Мурена білоточкова | livebait 1, fish_strip 0.9, silicone 0.85, spoon 0.8, chicken_liver 0.6 | №4 | braid 0.19 ±0.06 | 0.07 / 0.28 | **yes** |
| Мурена леопардова | livebait 1, fish_strip 0.9, jig 0.85, octopus_jig 0.7 | №2 | braid 0.27 ±0.06 | 0.07 / 0.28 | **yes** |
| Мінмаут (гібрид малоротого та плямистого окуня) | jig 1, silicone 0.95, crankbait 0.85, wacky_worm 0.85, spinnerbait 0.7 | №8 | fluoro 0.25 ±0.06 | 0.05 / 0.2 | — |
| Мінтай | jig 1.05, livebait 1, fish_strip 0.95, bladebait 0.85, octopus_jig 0.85, silicone 0.85, castmaster 0.8, swimbait 0.75, giant_spoon 0.7 | №4 | braid 0.22 ±0.06 | 0.7 / 0.7 | — |
| Місяць-риба | octopus_jig 1, silicone 0.85, fish_strip 0.6, livebait 0.35 | №2 | braid 0.4 ±0.1 | 0.9 / 0.5 | — |
| Нанохаракс Анзорга | bloodworm 1, maggot 0.95, worm 0.9, fly_nymph 0.85 | №16 | mono 0.06 ±0.06 | 0.5 / 0.6 | — |
| Нельма | spoon 1.1, spinner 1, castmaster 0.95, livebait 0.95, swimbait 0.85, wobbler 0.85, jig 0.7, silicone 0.7 | №4 | braid 0.2 ±0.05 | 0.7 / 0.6 | — |
| Нерка | livebait 1, fish_strip 0.9, wobbler 0.9, silicone 0.85, spinner 0.85, castmaster 0.8, spoon 0.8, swimbait 0.8, fly_streamer 0.7, worm 0.55, fly_nymph 0.4 | №6 | braid 0.2 ±0.06 | 0 / 0.07 | — |
| Нільська тиляпія | dough 1, bread 0.9, corn 0.9, worm 0.85, pea 0.8, pearl_barley 0.8, bloodworm 0.7, maggot 0.7, fly_nymph 0.45, fish_strip 0.3, jig 0.25 | №9 | mono 0.24 ±0.06 | 0.8 / 0.83 | — |
| Озерна форель | livebait 1, fish_strip 0.9, wobbler 0.9, silicone 0.85, spinner 0.85, castmaster 0.8, spoon 0.8, swimbait 0.8, giant_spoon 0.75, bladebait 0.7, fly_streamer 0.7, mormyshka 0.45 | №5 | fluoro 0.4 ±0.06 | 0.03 / 0.17 | — |
| Окунь | crankbait 1, bladebait 0.95, silicone 0.95, livebait 0.9, mormyshka 0.9, spinner 0.9, wacky_worm 0.85, jig 0.8, popper 0.7, spinnerbait 0.7, worm 0.6 | №8 | braid 0.1 ±0.04 | 0.4 / 0.7 | — |
| Окунь жовтий | livebait 1, jig 0.85, silicone 0.85, worm 0.55, mormyshka 0.45, bloodworm 0.35 | №8 | fluoro 0.21 ±0.06 | 0.05 / 0.21 | — |
| Окунь малоротий | worm 1, jig 0.6, spinner 0.6, silicone 0.55, crankbait 0.5, wacky_worm 0.5 | №7 | fluoro 0.26 ±0.06 | 0.05 / 0.2 | — |
| Окунь нільський | livebait 1, fish_strip 0.9, wobbler 0.9, jig 0.85, swimbait 0.8, giant_spoon 0.75 | №2 | braid 0.39 ±0.06 | 0.03 / 0.14 | **yes** |
| Оселедець | fish_strip 0.8, bloodworm 0.7, maggot 0.6, castmaster 0.5 | №10 | mono 0.18 ±0.06 | 0.4 / 0.57 | — |
| Осетер | chicken_liver 1, worm 0.9, livebait 0.7, boilie 0.5 | №1 | braid 0.45 ±0.1 | 0.95 / 0.79 | — |
| Осетер білий | livebait 1, fish_strip 0.9, chicken_liver 0.6, worm 0.55, bloodworm 0.35 | №1 | braid 0.52 ±0.06 | 0.28 / 0.45 | — |
| Осетер озерний | worm 1, bloodworm 0.85, dough 0.75, chicken_liver 0.7, fish_strip 0.7 | №1 | braid 0.35 ±0.06 | 0.55 / 0.65 | — |
| Очкастий хвостокіл | livebait 1, fish_strip 0.9, silicone 0.85, spoon 0.8, chicken_liver 0.6, worm 0.55 | №3 | braid 0.27 ±0.06 | 0.28 / 0.49 | **yes** |
| Павлиній окунь | wobbler 1.2, popper 1.15, swimbait 1.05, crankbait 1, silicone 0.95, spinner 0.9, spinnerbait 0.9, wacky_worm 0.9, livebait 0.85, jig 0.8 | №4 | braid 0.2 ±0.05 | 0.64 / 0.5 | — |
| Паку бурий | dough 1, bread 0.9, corn 0.9, boilie 0.85, worm 0.85 | №5 | mono 0.38 ±0.06 | 0.92 / 0.94 | — |
| Палтус | fish_strip 1, octopus_jig 1, livebait 0.9, swimbait 0.9, silicone 0.8, giant_spoon 0.7, jig 0.7 | №1 | braid 0.5 ±0.1 | 0.93 / 0.75 | — |
| Палія арктична | spinner 1, castmaster 0.9, spoon 0.9, wobbler 0.7, worm 0.6 | №8 | fluoro 0.2 ±0.05 | 0.59 / 0.7 | — |
| Палія бичоголова | fish_strip 1, wobbler 1, jig 0.95, spinner 0.95, spoon 0.9, fly_streamer 0.8 | №6 | fluoro 0.32 ±0.06 | 0.03 / 0.17 | — |
| Палія струмкова (американська палія) | worm 1, fly_nymph 0.6, spinner 0.6, wobbler 0.55, fly_streamer 0.5, spoon 0.5 | №7 | fluoro 0.3 ±0.06 | 0.05 / 0.25 | — |
| Панцирник довгорилий (панцирна щука) | livebait 1, fish_strip 0.9, wobbler 0.9, spinner 0.85 | №2 | braid 0.25 ±0.06 | 0 / 0.03 | **yes** |
| Панцирник міссісіпський | livebait 1, fish_strip 0.9, wobbler 0.9, silicone 0.85, castmaster 0.8, crankbait 0.8, spoon 0.8, swimbait 0.8, giant_spoon 0.75, popper 0.75, chicken_liver 0.6 | №1 | braid 0.37 ±0.06 | 0 / 0.03 | **yes** |
| Панцирник плямистий | livebait 1, fish_strip 0.9, wobbler 0.9, silicone 0.85, spoon 0.8, swimbait 0.8, spinnerbait 0.75, fly_streamer 0.7, worm 0.55 | №3 | braid 0.18 ±0.06 | 0 / 0.03 | **yes** |
| Панцирник флоридський | livebait 1, fish_strip 0.9, jig 0.85, spinner 0.85, worm 0.55 | №3 | braid 0.21 ±0.06 | 0 / 0.03 | **yes** |
| Папірокранус конголезький | livebait 1, fish_strip 0.9, jig 0.85, worm 0.55, bloodworm 0.35 | №9 | braid 0.12 ±0.06 | 0.05 / 0.17 | — |
| Плащоносна акула | fish_strip 1, octopus_jig 0.95, livebait 0.85 | №1 | braid 0.4 ±0.08 | 0.9 / 0.7 | **yes** |
| Плоскирка | maggot 1, worm 0.9, bloodworm 0.7 | №12 | braid 0.1 ±0.04 | 0.42 / 0.57 | — |
| Плямистий змієголов | livebait 1.2, silicone 1.05, popper 1, spinnerbait 1, swimbait 0.95, wobbler 0.95, jig 0.85, worm 0.6 | №2 | braid 0.22 ±0.06 | 0.62 / 0.7 | — |
| Плямистий чорний окунь | livebait 1, wobbler 0.9, jig 0.85, silicone 0.85, crankbait 0.8, spinnerbait 0.75 | №8 | fluoro 0.26 ±0.06 | 0.03 / 0.14 | — |
| Плітка | maggot 1, bloodworm 0.9, mormyshka 0.9, dough 0.7, bread 0.5 | №14 | mono 0.14 ±0.04 | 0.31 / 0.47 | — |
| Поліптерус Ендліхера | livebait 1, fish_strip 0.9, wobbler 0.9, silicone 0.85, spoon 0.8, chicken_liver 0.6, worm 0.55 | №7 | mono 0.24 ±0.06 | 0.15 / 0.39 | — |
| Протоптер бурий | livebait 1, fish_strip 0.9, chicken_liver 0.6, worm 0.55 | №4 | braid 0.18 ±0.06 | 0.17 / 0.42 | **yes** |
| Підуст | maggot 1, bloodworm 0.8, worm 0.8, pearl_barley 0.7 | №12 | mono 0.16 ±0.05 | 0.46 / 0.59 | — |
| Пірайба | livebait 1, fish_strip 0.95, chicken_liver 0.85, swimbait 0.7, worm 0.5 | №1 | braid 0.5 ±0.08 | 0.96 / 0.8 | **yes** |
| Пічкур | bloodworm 1, mormyshka 0.9, worm 0.9, maggot 0.8 | №16 | mono 0.14 ±0.04 | 0.22 / 0.54 | — |
| Райдужна форель | spinner 1, castmaster 0.95, wobbler 0.85, crankbait 0.8, silicone 0.7, worm 0.6 | №8 | fluoro 0.18 ±0.05 | 0.58 / 0.7 | — |
| Риба голіаф | livebait 1, fish_strip 0.9, wobbler 0.9, jig 0.85, spoon 0.8, swimbait 0.8 | №3 | braid 0.3 ±0.06 | 0 / 0.07 | **yes** |
| Риба-крапля | fish_strip 0.9, worm 0.8, bloodworm 0.7, chicken_liver 0.5 | №6 | braid 0.25 ±0.08 | 0.55 / 0.45 | — |
| Риба-меч | livebait 1, octopus_jig 1, fish_strip 0.9, giant_spoon 0.85, wobbler 0.6 | №1 | braid 0.45 ±0.08 | 1 / 0.75 | — |
| Рибець | worm 1, maggot 0.9, bloodworm 0.8, pea 0.5 | №10 | mono 0.2 ±0.05 | 0.53 / 0.6 | — |
| Ротань | worm 1, bloodworm 0.9, maggot 0.8, livebait 0.7, chicken_liver 0.6, silicone 0.6 | №12 | mono 0.18 ±0.08 | 0.27 / 0.6 | — |
| Роху | dough 1, bread 0.9, corn 0.9, boilie 0.85, worm 0.85, pea 0.8, pearl_barley 0.8, bloodworm 0.7, fly_pellet 0.7 | №3 | mono 0.45 ±0.06 | 0.98 / 0.94 | — |
| Ріпонський вусач | worm 1, maggot 0.8, dough 0.75, boilie 0.7, corn 0.7, fish_strip 0.7, spinner 0.6, spoon 0.5 | №6 | mono 0.29 ±0.06 | 0.8 / 0.8 | — |
| Сазан | boilie 1, corn 0.85, pea 0.7, pearl_barley 0.55 | №4 | mono 0.3 ±0.07 | 0.75 / 0.84 | — |
| Сайда | jig 1, octopus_jig 0.95, giant_spoon 0.9, bladebait 0.8, silicone 0.8, swimbait 0.8, castmaster 0.7, fish_strip 0.7 | №4 | braid 0.25 ±0.06 | 0.71 / 0.75 | — |
| Сарган | fish_strip 1, castmaster 0.7, spinner 0.7, silicone 0.5 | №8 | mono 0.2 ±0.06 | 0.51 / 0.75 | — |
| Сиг | bloodworm 1, mormyshka 0.9, maggot 0.8, worm 0.6 | №10 | fluoro 0.18 ±0.05 | 0.57 / 0.52 | — |
| Синець | bloodworm 1, maggot 0.85, worm 0.7, pearl_barley 0.5 | №12 | mono 0.14 ±0.05 | 0.44 / 0.55 | — |
| Синодонтис ангельський | worm 1, bloodworm 0.85, dough 0.75, chicken_liver 0.7, fish_strip 0.7 | №12 | mono 0.2 ±0.06 | 0.6 / 0.7 | — |
| Синьоперий тунець | livebait 1.1, giant_spoon 1, swimbait 1, fish_strip 0.9, octopus_jig 0.9, castmaster 0.8, silicone 0.6 | №1 | braid 0.4 ±0.06 | 1 / 0.85 | — |
| Синій марлін | fish_strip 1.1, octopus_jig 1, wobbler 1, giant_spoon 0.95, silicone 0.6 | №1 | braid 0.4 ±0.06 | 1 / 0.75 | — |
| Скат | fish_strip 1, worm 0.7, livebait 0.6 | №2 | mono 0.5 ±0.1 | 0.83 / 0.73 | — |
| Скумбрія | castmaster 1, spinner 0.9, silicone 0.8, fish_strip 0.6 | №6 | braid 0.2 ±0.06 | 0.51 / 0.75 | — |
| Смугастий лаврак | livebait 1.2, swimbait 1.15, fish_strip 1.1, giant_spoon 1.05, bladebait 1, wobbler 1, silicone 0.95, spoon 0.9, jig 0.85 | №2 | braid 0.3 ±0.08 | 0.74 / 0.75 | — |
| Снук | livebait 1.25, swimbait 1.15, silicone 1.1, wobbler 1.05, popper 1, jig 0.9, fish_strip 0.85, spinnerbait 0.8 | №2 | braid 0.3 ±0.08 | 0.73 / 0.75 | — |
| Согай (гібрид судака) | livebait 1, jig 0.85, silicone 0.85, crankbait 0.8, bladebait 0.7, worm 0.55 | №6 | fluoro 0.27 ±0.06 | 0.05 / 0.21 | **yes** |
| Сом | chicken_liver 1, livebait 1, swimbait 0.9, jig 0.85, worm 0.7, boilie 0.6 | №4 | braid 0.18 ±0.04 | 0.81 / 0.81 | — |
| Сом валлаго | livebait 1, fish_strip 0.9, wobbler 0.9, silicone 0.85, spoon 0.8, swimbait 0.8, chicken_liver 0.6 | №2 | braid 0.29 ±0.06 | 0.33 / 0.56 | **yes** |
| Сом вунду | worm 1, dough 0.75, chicken_liver 0.7, fish_strip 0.7, jig 0.6, livebait 0.6 | №3 | braid 0.3 ±0.06 | 0.7 / 0.8 | — |
| Сом плоскоголовий | livebait 1, fish_strip 0.9, jig 0.85, chicken_liver 0.6, worm 0.55 | №3 | braid 0.3 ±0.06 | 0.38 / 0.56 | — |
| Сом тапах | livebait 1, fish_strip 0.9, silicone 0.85, spoon 0.8, swimbait 0.8, giant_spoon 0.75, chicken_liver 0.6 | №2 | braid 0.34 ±0.06 | 0.33 / 0.56 | **yes** |
| Сомик-перевертень | bloodworm 1, maggot 0.95, worm 0.9, fly_nymph 0.85 | №16 | mono 0.07 ±0.06 | 0.6 / 0.7 | — |
| Сонячний окунь | worm 1, bloodworm 0.85, maggot 0.8, dough 0.75, bread 0.7, corn 0.7, fish_strip 0.7, fly_nymph 0.6, jig 0.6, spinner 0.6, mormyshka 0.55, silicone 0.55, fly_ant 0.5, fly_dry_fly 0.5, fly_streamer 0.5 | №13 | mono 0.16 ±0.06 | 0.45 / 0.55 | — |
| Сплейк | worm 1, jig 0.6, spinner 0.6, fly_streamer 0.5, spoon 0.5 | №7 | fluoro 0.3 ±0.06 | 0.05 / 0.25 | — |
| Стерлядь | worm 1, bloodworm 0.7, maggot 0.5 | №6 | braid 0.14 ±0.04 | 0.71 / 0.56 | — |
| Судак | bladebait 1, silicone 1, jig 0.95, livebait 0.95, crankbait 0.85, swimbait 0.8, wobbler 0.8, spinnerbait 0.6 | №4 | braid 0.12 ±0.04 | 0.62 / 0.5 | **yes** |
| Судак жовтий | livebait 1, wobbler 0.9, jig 0.85, silicone 0.85, spinner 0.85, worm 0.55 | №5 | fluoro 0.31 ±0.06 | 0.05 / 0.21 | **yes** |
| Таймень | wobbler 1, swimbait 0.95, spoon 0.9, popper 0.85, crankbait 0.8, livebait 0.8 | №2 | braid 0.35 ±0.08 | 0.87 / 0.5 | **yes** |
| Тарпон | livebait 1.3, fish_strip 1.1, swimbait 1.1, popper 1, silicone 1, jig 0.9, giant_spoon 0.85 | №1 | braid 0.45 ±0.1 | 0.99 / 0.75 | — |
| Терський вусач | worm 1, bloodworm 0.85, maggot 0.8, dough 0.75, bread 0.7, fly_nymph 0.6, jig 0.6, fly_ant 0.5 | №8 | mono 0.2 ±0.06 | 0.8 / 0.8 | — |
| Тигрова акула | fish_strip 1.15, livebait 1.1, swimbait 0.9, chicken_liver 0.85, octopus_jig 0.7, giant_spoon 0.6 | №1 | braid 0.45 ±0.08 | 1 / 0.95 | **yes** |
| Тигровий маскінонг | livebait 1, wobbler 0.9, spoon 0.8, swimbait 0.8, giant_spoon 0.75, spinnerbait 0.75 | №3 | braid 0.25 ±0.06 | 0 / 0 | **yes** |
| Товстолобик | pearl_barley 0.5, corn 0.4, boilie 0.3 | №6 | mono 0.4 ±0.08 | 0.79 / 0.81 | — |
| Трахіра | livebait 1, fish_strip 0.9, wobbler 0.9, silicone 0.85, crankbait 0.8, spoon 0.8, swimbait 0.8, popper 0.75, spinnerbait 0.75, fly_streamer 0.7 | №5 | braid 0.18 ±0.06 | 0 / 0.07 | **yes** |
| Тріска | fish_strip 1, octopus_jig 1, jig 0.95, livebait 0.9, bladebait 0.85, giant_spoon 0.8, swimbait 0.8, silicone 0.7 | №2 | braid 0.3 ±0.08 | 0.79 / 0.75 | — |
| Тупорила акула | fish_strip 1, livebait 1, swimbait 0.9, octopus_jig 0.75, giant_spoon 0.7 | №1 | braid 0.5 ±0.08 | 1 / 0.76 | **yes** |
| Форель | castmaster 1, spinner 0.95, wobbler 0.9, crankbait 0.85, silicone 0.7, worm 0.6 | №8 | fluoro 0.2 ±0.05 | 0.57 / 0.7 | — |
| Харіус | spinner 0.95, worm 0.9, castmaster 0.8, maggot 0.8, bloodworm 0.7, crankbait 0.6 | №12 | mono 0.16 ±0.04 | 0.49 / 0.57 | — |
| Хілогланіс багатовусий | bloodworm 1, maggot 0.95, worm 0.9, fly_nymph 0.85 | №16 | mono 0.06 ±0.06 | 0.6 / 0.7 | — |
| Цитарина звичайна | dough 1, bread 0.9, corn 0.9, pea 0.8, fly_pellet 0.7 | №7 | mono 0.27 ±0.06 | 0.92 / 0.94 | — |
| Цифотиляпія фронтоза | livebait 1, fish_strip 0.9, jig 0.85, worm 0.55, bloodworm 0.35 | №11 | fluoro 0.2 ±0.06 | 0.12 / 0.32 | — |
| Цихлазома майя | worm 1.2, bloodworm 1, maggot 1, wacky_worm 0.9, silicone 0.8, bread 0.7 | №10 | mono 0.14 ±0.04 | 0.42 / 0.5 | — |
| Цихліда-форель | livebait 1, fish_strip 0.9, wobbler 0.9, jig 0.85, spinner 0.85, fly_streamer 0.7 | №11 | fluoro 0.16 ±0.06 | 0.12 / 0.32 | — |
| Чавича | fish_strip 1, wobbler 1, spinner 0.95, spoon 0.9, fly_streamer 0.8 | №4 | braid 0.31 ±0.06 | 0 / 0.07 | — |
| Червона тиляпія (гібрид нільської та мозамбікської тиляпії) | dough 1, bread 0.9, corn 0.9, worm 0.85, pea 0.8, fly_pellet 0.7 | №9 | mono 0.24 ±0.06 | 0.8 / 0.83 | — |
| Червоновухий сонячний окунь | worm 1, bloodworm 0.85, maggot 0.8, corn 0.7, jig 0.6 | №13 | mono 0.22 ±0.06 | 0.45 / 0.55 | — |
| Червоногорла форель | worm 1, fly_nymph 0.6, spinner 0.6, fly_dry_fly 0.5, fly_streamer 0.5, spoon 0.5 | №6 | braid 0.24 ±0.06 | 0 / 0.1 | — |
| Червоногрудий сонячний окунь | bloodworm 1, maggot 0.95, worm 0.9, fly_nymph 0.85, jig 0.55 | №15 | mono 0.17 ±0.06 | 0.45 / 0.55 | — |
| Червоноокий окунь | livebait 1, jig 0.85, silicone 0.85, spinner 0.85, worm 0.55 | №13 | mono 0.19 ±0.06 | 0.23 / 0.39 | — |
| Червоноперий махсир | worm 1, dough 0.75, boilie 0.7, fish_strip 0.7, livebait 0.6, spinner 0.6, wobbler 0.55, castmaster 0.5, crankbait 0.5, fly_streamer 0.5, spoon 0.5 | №3 | braid 0.31 ±0.06 | 0.65 / 0.8 | — |
| Червонохвостий сом | livebait 1, fish_strip 0.9, wobbler 0.9, silicone 0.85, spoon 0.8, swimbait 0.8, giant_spoon 0.75, chicken_liver 0.6 | №2 | braid 0.32 ±0.06 | 0.28 / 0.52 | — |
| Червоночерева піранья | fish_strip 1.1, chicken_liver 1, livebait 0.95, worm 0.7, silicone 0.6, spinner 0.5 | №8 | mono 0.2 ±0.06 | 0.55 / 0.85 | **yes** |
| Чехоня | castmaster 1, maggot 0.9, spinner 0.8, worm 0.8, bloodworm 0.7, silicone 0.6 | №10 | mono 0.16 ±0.05 | 0.46 / 0.56 | — |
| Чорний краппі | livebait 1, fish_strip 0.9, wobbler 0.9, jig 0.85, silicone 0.85, spinner 0.85, castmaster 0.8, crankbait 0.8, spoon 0.8, fly_streamer 0.7, worm 0.55, mormyshka 0.45, bloodworm 0.35, maggot 0.3 | №12 | mono 0.22 ±0.06 | 0.17 / 0.32 | — |
| Чорний марлін | giant_spoon 1.05, octopus_jig 1, swimbait 0.95, fish_strip 0.9, wobbler 0.9, silicone 0.5 | №1 | braid 0.45 ±0.06 | 1 / 0.75 | **yes** |
| Чорний махсир | worm 1, dough 0.75, boilie 0.7, fish_strip 0.7, livebait 0.6, spinner 0.6, wobbler 0.55, crankbait 0.5, fly_streamer 0.5, spoon 0.5 | №4 | braid 0.24 ±0.06 | 0.65 / 0.8 | — |
| Чорний сонячний окунь | livebait 1, jig 0.85, silicone 0.85, worm 0.55, maggot 0.3 | №14 | mono 0.18 ±0.06 | 0.23 / 0.39 | — |
| Чітала Блана | livebait 1, fish_strip 0.9, wobbler 0.9, jig 0.85, swimbait 0.8 | №5 | braid 0.22 ±0.06 | 0.05 / 0.17 | — |
| Чітала гігантська | livebait 1, fish_strip 0.9, wobbler 0.9, jig 0.85, spinner 0.85 | №4 | braid 0.23 ±0.06 | 0.05 / 0.17 | — |
| Шильб сріблястий | livebait 1, fish_strip 0.9, spinner 0.85, worm 0.55, bloodworm 0.35 | №4 | braid 0.14 ±0.06 | 0.35 / 0.56 | — |
| Щука | swimbait 1, wobbler 1, spoon 0.95, crankbait 0.9, livebait 0.9, spinner 0.9, spinnerbait 0.9, jig 0.85, bladebait 0.7, popper 0.7 | №4 | braid 0.14 ±0.04 | 0.66 / 0.5 | **yes** |
| Щука звичайна | livebait 1, wobbler 0.9, jig 0.85, silicone 0.85, spinner 0.85, spoon 0.8 | №3 | braid 0.26 ±0.06 | 0 / 0 | **yes** |
| Щука чорна | livebait 1, wobbler 0.9, jig 0.85, silicone 0.85, spinner 0.85, spoon 0.8 | №3 | braid 0.18 ±0.06 | 0 / 0 | **yes** |
| Щука-маскінонг | livebait 1, fish_strip 0.9, wobbler 0.9, silicone 0.85, spinner 0.85, castmaster 0.8, crankbait 0.8, spoon 0.8, swimbait 0.8, giant_spoon 0.75, popper 0.75, spinnerbait 0.75, bladebait 0.7, fly_streamer 0.7 | №2 | braid 0.27 ±0.06 | 0 / 0 | **yes** |
| Ялець | maggot 1, worm 0.9, bread 0.7, bloodworm 0.65, dough 0.6, spinner 0.4 | №14 | mono 0.14 ±0.04 | 0.34 / 0.52 | — |
| Ікталур блакитний | worm 1, dough 0.75, chicken_liver 0.7, fish_strip 0.7, livebait 0.6 | №2 | braid 0.31 ±0.06 | 0.75 / 0.8 | — |

## Нотатки за видами

### П'ятеро кої

Кої Кохаку, Кої Танчо Санке, Кої Сьова Санке, Кої Асагі та Кої Бекко — це **прихована колекція**, а не звичайна риба. У їхньому профілі `base` дорівнює **0.0**, тож зі звичайного пулу поклівок вони не випадають ніколи.

Натомість щоразу, коли ти береш **коропа, дзеркального коропа чи сазана на коропову оснастку**, є шанс, що улов виявиться кої:

- **0.5 %** будь-де
- **35 %** у біомі вишневого гаю

Єдина вказана в них група біомів — `cherry`, тож ставок у вишневому гаю — єдине місце, де їм узагалі належить бути. У всіх п'ятьох характеристики однакові (800 г – 8 кг, медіана 2.5 кг, 25–90 см, манера бою `burst`, рівень 3).

Кої **не йдуть у залік за кількістю видів**: ні в східчастих досягненнях на «N видів», ні в *Повному бестіарії* — у них свої випробування, *Жива коштовність* і *Колекціонер кої*. Пустити кої на філе можна: твоє ім'я піде в чат сервера з підписом *«ти серйозно пустив її на філе?»*, а слідом прийде досягнення *Безсердечний кухар*.

### Легендарні екземпляри

У восьми видів захований один іменний екземпляр — один на весь сервер. Уся механіка — в [Механіці риболовлі](fishing-mechanics.md#легендарні-риби).

| Вид | Ім'я | Вага | Шанс |
|---|---|---|---|
| Щука | Цариця Корчів | 14 кг | 0.6 % |
| Сазан | Дід Сазан | 17.5 кг | 0.6 % |
| Сом | Хазяїн Ями | 150 кг | 0.5 % |
| Жовтоперий тунець | Старий Хребет | 140 кг | 0.6 % |
| Синій марлін | Левіафан | 380 кг | 0.8 % |
| Осетер | Цар-риба | 145 кг | 0.4 % |
| Акула-мако | Мегалодон | 390 кг | 0.4 % |
| Палтус | Демон Безодні | 250 кг | 0.4 % |
| Арапайма | — | 175 кг | 0.4 % |
| Білуга | — | 580 кг | 0.3 % |
| Пірайба | — | 155 кг | 0.4 % |
| Голіафовий групер | — | 310 кг | 0.4 % |
| Тупорила акула | — | 225 кг | 0.4 % |
| Плащоносна акула | — | 48 кг | 0.3 % |

Четверо з них **важчі за звичайний максимум свого виду**: щука (14 кг проти стелі в 10 кг), сом (150 кг проти 120 кг), палтус (250 кг проти 200 кг) і особливо мако (390 кг проти 200 кг). Легендарна риба справді виходить за той розмір, до якого інакше не дістатися.

### Незвичайні профілі

**Товстолобик** — планктонофаг-фільтратор і єдиний вид, у якого *найкраща* наживка оцінена лише в **0.5** (перлівка). Швидкість поклівки прямо залежить від оцінки наживки, тож товстолобик бере повільно завжди, хоч би що ти робив; вирішують сипка прикормка і тонка волосінь. Рівень 6 і невтомний боєць на 25 кг.

**Ротань** і **Корюшка** — єдині два види, у яких ідеальний `reel_size` дорівнює **0**: вони прямо віддають перевагу вудці без котушки. З котушкою відповідний компонент дає 0.6 замість 1.0. Ротань до того ж єдиний вид зі справжньою присутністю в **калюжі** (1.0) — він і справді живе в будь-якій канаві, тому з нього всі й починають. У корюшки — єдиний у моді поріг у **1 рівень**.

**Минь** — найзатисненіша умовами риба в моді: `summer: 0.0` **і** `day: 0.0`. Він існує лише холодними ночами, з піком узимку (1.6) і вночі (1.5). Власне досягнення *Король зимової ночі* з'явилося саме через це.

**Верховодка** і **Пічкур** — `night: 0.0`. З темрявою вони перестають клювати повністю.

**Скат** — два ривки, манера `active_then_passive` й агресія 0.2, але сила 0.95 на діапазоні 2–50 кг. Він не бореться, він просто важкий. У профілі це описано як підйом плити морського дна.

**У сімнадцяти видів** оцінка наживки вище 1.0: улюблена наживка дає невеликий бонус понад ідеальний збіг, і рушій ставить цьому бонусу стелю 1.3. На самій стелі — **Тарпон** (livebait 1.3), слідом **Каранкс** (popper 1.25) і **Снук** (livebait 1.25).

**Головень, Білизна, Стерлядь** живуть **лише в річці** (`river` 1.2, решта водойм — 0). В озері їх не буде, хоч би що ти робив.

**Бичок-кругляк** — єдиний вид, якому однаково добре в солоній і в прісній воді: `sea` 1.1 і `river` 1.0, плюс озеро 0.6 і ставок 0.2.

**Напівпрохідні та прохідні** — у чотирнадцяти видів поряд із прісною водою стоїть ненульовий коефіцієнт `sea`: Рибець (0.2), Корюшка (1.2 море / 0.3 річка), Палія арктична (0.2), Атлантичний лосось (1.1 річка / 1.0 море), Горбуша (1.1 море / 1.0 річка), Осетер (0.3), Білуга (1.0 море / 1.0 річка), Тупорила акула (1.1 море / 0.6 річка), Каранкс (1.2 море / 0.5 річка), Бичок-кругляк (1.1 море / 1.0 річка), Снук (1.2 море / 0.5 річка), Смугастий лаврак (1.2 море / 0.5 річка), Тарпон (1.2 море / 0.5 річка) і Бичок-цуцик (0.5 море / 1.1 річка). Справжні ходові риби тут — лосось і горбуша: у лосося пік восени (1.4), у горбуші влітку (1.5).

**Білий амур** — гігант-вегетаріанець: corn 1.0, bread 0.9, dough 0.8, і єдиний «короп», який тримається `mid` — у півводи, а не біля дна. Манера `relentless`: біля підсака він упирається так само, як на підсічці.

**Види під зимову вудку** — зимова вудка стоїть в ідеальній снасті лише в **Корюшки** і **Сига**, і тільки в них указана зимова оснастка. Усе інше, що беруть з-під льоду, беруть на снасть, якої риба, строго кажучи, не просила.

**Види під вудку з палиці** — Пічкур, Верховодка, Ротань, Ялець, Блюгіл, Гірчак, Золотий карась, Бабець, Верхівка і Бичок-цуцик: десять риб, у яких найпростіший бланк указано як ідеальний. **Бамбукова вудка** трапляється лише у трьох — у Блюгіла, Синця і Золотого карася.

**Види з нульовою зимою** — у Карася, Краснопірки, Верховодки, Головня, Лина, Сома, Вугра, Гірчака і Верхівки стоїть `winter: 0.0`; Білий амур, Короп, Дзеркальний короп, Сазан і Товстолобик закриті фактично теж (0.02–0.05). Зима — це справді інша гра.

## Дивись також

- [Довідник видів](species-reference.md) — жорсткі умови проживання, таблиці умов, статистика виважування
- [Вода та умови](water-and-conditions.md) · [Механіка риболовлі](fishing-mechanics.md)
- [Морська риболовля](sea-fishing.md) · [Підлідна риболовля](ice-fishing.md)
- [Житель](villager.md) — які види купує рибак і за скільки
