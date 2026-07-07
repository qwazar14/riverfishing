# Generates placeholder item textures, item model JSONs, and en/ru lang files for River Fishing.
# Re-runnable: overwrites generated assets. Hand-authored data (fish profiles, recipes, tags) is untouched.
Add-Type -AssemblyName System.Drawing

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$assets = Join-Path $root "src\main\resources\assets\riverfishing"
$texDir = Join-Path $assets "textures\item"
$modelDir = Join-Path $assets "models\item"
$langDir = Join-Path $assets "lang"
New-Item -ItemType Directory -Force -Path $texDir, $modelDir, $langDir | Out-Null

function Write-Utf8NoBom($path, $text) {
    $enc = New-Object System.Text.UTF8Encoding $false
    [System.IO.File]::WriteAllText($path, $text, $enc)
}

# Draws a simple 16x16 fish (body + tail + eye) in the given colour.
function New-FishTexture($path, $r, $g, $b) {
    $bmp = New-Object System.Drawing.Bitmap 16, 16
    $gfx = [System.Drawing.Graphics]::FromImage($bmp)
    $gfx.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::None
    $gfx.Clear([System.Drawing.Color]::FromArgb(0, 0, 0, 0))
    $body = [System.Drawing.Color]::FromArgb(255, $r, $g, $b)
    $dark = [System.Drawing.Color]::FromArgb(255, [Math]::Max(0, $r - 55), [Math]::Max(0, $g - 55), [Math]::Max(0, $b - 55))
    $brush = New-Object System.Drawing.SolidBrush $body
    $gfx.FillEllipse($brush, 2, 5, 10, 6)
    $tail = New-Object 'System.Drawing.Point[]' 3
    $tail[0] = New-Object System.Drawing.Point 11, 8
    $tail[1] = New-Object System.Drawing.Point 15, 4
    $tail[2] = New-Object System.Drawing.Point 15, 12
    $gfx.FillPolygon($brush, $tail)
    $brush.Color = $dark
    $gfx.FillRectangle($brush, 4, 7, 1, 1)   # eye
    $gfx.Dispose()
    $bmp.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()
}

# ---------- Textures: one 16x16 placeholder per category ----------
$catColors = @{
    rod        = @(120, 78, 40)
    reel       = @(70, 70, 80)
    line       = @(150, 200, 220)
    rig        = @(90, 90, 100)
    hook       = @(200, 200, 210)
    bait       = @(180, 120, 90)
    lure       = @(80, 160, 120)
    groundbait = @(140, 110, 70)
    leader     = @(120, 125, 140)
    float      = @(205, 80, 80)
    bell          = @(200, 175, 60)
    digital       = @(45, 65, 120)
    knife         = @(180, 180, 195)
    raw_fillet    = @(220, 150, 150)
    cooked_fillet = @(180, 120, 80)
    whetstone     = @(150, 150, 160)
    journal       = @(120, 90, 60)
}
# Draws a 16x16 placeholder tile in the given colour (used until you draw a real icon).
function New-PlaceholderTexture($path, $r, $g, $b) {
    $bmp = New-Object System.Drawing.Bitmap 16, 16
    $gfx = [System.Drawing.Graphics]::FromImage($bmp)
    $base = [System.Drawing.Color]::FromArgb(255, $r, $g, $b)
    $dark = [System.Drawing.Color]::FromArgb(255, [Math]::Max(0, $r - 45), [Math]::Max(0, $g - 45), [Math]::Max(0, $b - 45))
    $light = [System.Drawing.Color]::FromArgb(255, [Math]::Min(255, $r + 45), [Math]::Min(255, $g + 45), [Math]::Min(255, $b + 45))
    $gfx.Clear([System.Drawing.Color]::FromArgb(0, 0, 0, 0))
    $brush = New-Object System.Drawing.SolidBrush $base
    $gfx.FillRectangle($brush, 3, 3, 10, 10)
    $brush.Color = $light; $gfx.FillRectangle($brush, 3, 3, 10, 2)
    $brush.Color = $dark;  $gfx.FillRectangle($brush, 3, 11, 10, 2)
    $brush.Color = $dark;  $gfx.FillRectangle($brush, 3, 3, 2, 10)
    $gfx.Dispose()
    $bmp.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()
}

# ---------- Item definitions ----------
$items = New-Object System.Collections.Generic.List[object]
function Add-Item($id, $cat, $en, $ru) {
    $items.Add([pscustomobject]@{ id = $id; cat = $cat; en = $en; ru = $ru })
}

# Fish species (Module 8): names shared by item lang, fish.* lang and per-species textures.
$fishEn = [ordered]@{ bream = "Bream"; crucian_carp = "Crucian Carp"; roach = "Roach"; rudd = "Rudd"; white_bream = "White Bream"; carp = "Carp"; catfish = "Catfish"; perch = "Perch"; pike = "Pike"; zander = "Zander"; gudgeon = "Gudgeon"; ruffe = "Ruffe"; bleak = "Bleak"; ide = "Ide"; chub = "Chub"; asp = "Asp"; tench = "Tench"; burbot = "Burbot"; eel = "Eel"; grayling = "Grayling"; trout = "Trout"; sterlet = "Sterlet"; wild_carp = "Wild Carp"; mirror_carp = "Mirror Carp"; carp_koi_kohaku = "Koi Kohaku"; carp_koi_tancho_sanke = "Koi Tancho Sanke"; carp_koi_showa_sanke = "Koi Showa Sanke"; carp_koi_asagi = "Koi Asagi"; carp_koi_bekko = "Koi Bekko" }
$fishRu = [ordered]@{ bream = "Лещ"; crucian_carp = "Карась"; roach = "Плотва"; rudd = "Краснопёрка"; white_bream = "Густера"; carp = "Карп"; catfish = "Сом"; perch = "Окунь"; pike = "Щука"; zander = "Судак"; gudgeon = "Пескарь"; ruffe = "Ёрш"; bleak = "Уклейка"; ide = "Язь"; chub = "Голавль"; asp = "Жерех"; tench = "Линь"; burbot = "Налим"; eel = "Угорь"; grayling = "Хариус"; trout = "Форель"; sterlet = "Стерлядь"; wild_carp = "Сазан"; mirror_carp = "Зеркальный карп"; carp_koi_kohaku = "Кои Кохаку"; carp_koi_tancho_sanke = "Кои Танчо Санке"; carp_koi_showa_sanke = "Кои Сёва Санке"; carp_koi_asagi = "Кои Асаги"; carp_koi_bekko = "Кои Бекко" }
$fishColors = [ordered]@{ bream = @(190, 180, 150); crucian_carp = @(200, 170, 90); roach = @(180, 190, 205); rudd = @(210, 160, 80); white_bream = @(205, 210, 215); carp = @(150, 130, 70); catfish = @(85, 95, 75); perch = @(95, 140, 70); pike = @(70, 110, 80); zander = @(120, 140, 120); gudgeon = @(160, 145, 105); ruffe = @(130, 135, 95); bleak = @(200, 210, 220); ide = @(180, 165, 100); chub = @(140, 150, 120); asp = @(170, 185, 200); tench = @(110, 120, 60); burbot = @(120, 105, 75); eel = @(100, 110, 85); grayling = @(140, 150, 180); trout = @(170, 140, 120); sterlet = @(130, 125, 115); wild_carp = @(150, 120, 60); mirror_carp = @(190, 185, 170); carp_koi_kohaku = @(220, 180, 180); carp_koi_tancho_sanke = @(225, 220, 215); carp_koi_showa_sanke = @(120, 90, 90); carp_koi_asagi = @(140, 160, 185); carp_koi_bekko = @(200, 200, 205) }

# Rods
Add-Item "stick_rod" "rod" "Stick Rod" "Удочка из палки"
Add-Item "bamboo_rod" "rod" "Bamboo Rod" "Бамбуковая удочка"
Add-Item "pole_rod" "rod" "Pole Rod" "Маховое удилище"
Add-Item "winter_rod" "rod" "Winter Rod" "Зимняя удочка"
Add-Item "ultralight_rod" "rod" "Ultralight Rod" "Ультралайт"
Add-Item "spinning_rod" "rod" "Spinning Rod" "Спиннинг"
Add-Item "feeder_rod" "rod" "Feeder Rod" "Фидерное удилище"
Add-Item "bottom_rod" "rod" "Bottom Rod" "Донное удилище"
Add-Item "carp_rod" "rod" "Carp Rod" "Карповое удилище"

# Reels
foreach ($s in 1000, 2000, 3000, 4000, 5000, 6000, 7000) {
    Add-Item "reel_$s" "reel" "Reel $s" "Катушка $s"
}

# Lines
function DiaLabel($suffix) { return ([double]$suffix / 100).ToString("0.00", [Globalization.CultureInfo]::InvariantCulture) }
foreach ($suffix in 10, 14, 18, 25, 30, 40) { $d = DiaLabel $suffix; Add-Item ("line_mono_{0:000}" -f $suffix) "line" "Mono Line $d" "Монолеска $d" }
foreach ($suffix in 16, 20, 25, 30) { $d = DiaLabel $suffix; Add-Item ("line_braid_{0:000}" -f $suffix) "line" "Braided Line $d" "Плетёнка $d" }
foreach ($suffix in 14, 16, 20, 25, 30) { $d = DiaLabel $suffix; Add-Item ("line_fluoro_{0:000}" -f $suffix) "line" "Fluorocarbon $d" "Флюорокарбон $d" }

# Rigs
Add-Item "rig_primitive" "rig" "Primitive Rig" "Примитивная оснастка"
Add-Item "rig_float_light" "rig" "Light Float Rig" "Лёгкая поплавочная оснастка"
Add-Item "rig_float" "rig" "Float Rig" "Поплавочная оснастка"
Add-Item "rig_winter" "rig" "Winter Rig" "Зимняя оснастка"
Add-Item "rig_feeder" "rig" "Feeder Rig" "Фидерная оснастка"
Add-Item "rig_flat_feeder" "rig" "Flat Feeder Rig" "Флет-фидер"
Add-Item "rig_ground" "rig" "Ledger Rig" "Донная оснастка"
Add-Item "rig_grusha" "rig" "Grusha Rig" "Груша (3 крючка)"
Add-Item "rig_carp" "rig" "Carp Rig" "Карповая оснастка"
Add-Item "rig_predator" "rig" "Predator Rig" "Хищная оснастка"
Add-Item "rig_catfish" "rig" "Catfish Rig" "Сомовья оснастка"

# In-rig components (Module 4)
Add-Item "leader" "leader" "Steel Leader" "Стальной поводок"
Add-Item "leader_fluoro" "leader" "Fluorocarbon Leader" "Флюорокарбоновый поводок"
Add-Item "leader_titanium" "leader" "Titanium Leader" "Титановый поводок"
Add-Item "float" "float" "Float" "Поплавок"

# Hooks
foreach ($n in 16, 14, 12, 10, 8, 6, 4) { Add-Item "hook_$n" "hook" "Hook No.$n" "Крючок №$n" }

# Natural baits
Add-Item "maggot" "bait" "Maggot" "Опарыш"
Add-Item "worm" "bait" "Worm" "Червяк"
Add-Item "bloodworm" "bait" "Bloodworm" "Мотыль"
Add-Item "corn" "bait" "Corn" "Кукуруза"
Add-Item "pea" "bait" "Pea" "Горох"
Add-Item "pearl_barley" "bait" "Pearl Barley" "Перловка"
Add-Item "dough" "bait" "Dough" "Тесто"
Add-Item "bread" "bait" "Bread Crumb" "Хлебный мякиш"
Add-Item "boilie" "bait" "Boilie" "Бойлы"
Add-Item "livebait" "bait" "Live Bait" "Живец"
Add-Item "chicken_liver" "bait" "Chicken Liver" "Куриная печень"
Add-Item "mormyshka" "lure" "Mormyshka" "Мормышка"
# Artificial baits (lures)
Add-Item "spinner" "lure" "Spinner" "Вращающаяся блесна"
Add-Item "spoon" "lure" "Spoon Lure" "Колеблющаяся блесна"
Add-Item "wobbler" "lure" "Wobbler" "Воблер"
Add-Item "silicone" "lure" "Soft Plastic" "Силиконовая приманка"

# Groundbaits
Add-Item "groundbait_powder" "groundbait" "Powder Groundbait" "Сыпучая прикормка"
Add-Item "groundbait_grain" "groundbait" "Grain Groundbait" "Зерновая прикормка"
Add-Item "groundbait_pellet" "groundbait" "Pellet Groundbait" "Гранулированная прикормка"
Add-Item "groundbait_cake" "groundbait" "Oil Cake Groundbait" "Жмых"

# Bite alarms (Module 3)
Add-Item "bell_alarm" "bell" "Bite Alarm (Bell)" "Сигнализатор-колокольчик"
Add-Item "digital_alarm" "digital" "Digital Bite Alarm" "Цифровой сигнализатор"

# Processing (§11)
Add-Item "fillet_knife" "knife" "Filleting Knife" "Филейный нож"
Add-Item "raw_fillet" "raw_fillet" "Raw Fish Fillet" "Сырое филе"
Add-Item "cooked_fillet" "cooked_fillet" "Cooked Fish Fillet" "Жареное филе"

# Maintenance (§3.8)
Add-Item "whetstone" "whetstone" "Whetstone" "Точильный брусок"

# Ice fishing (§ice-fishing)
Add-Item "ice_auger" "knife" "Ice Auger" "Ледобур"

# Records (§15)
Add-Item "fishing_journal" "journal" "Fishing Journal" "Рыболовный дневник"
Add-Item "fish_finder" "digital" "Fish Finder" "Эхолот"
Add-Item "hydro_probe" "journal" "Ichthyologist's Tablet" "Планшет ихтиолога"

# Caught fish: one item + unique texture per species (Module 8)
New-Item -ItemType Directory -Force -Path (Join-Path $texDir "fish") | Out-Null
foreach ($sp in $fishEn.Keys) {
    $texPath = Join-Path $texDir "fish\$sp.png"
    if (-not (Test-Path $texPath)) {           # keep any icon you've drawn
        $col = $fishColors[$sp]
        New-FishTexture $texPath $col[0] $col[1] $col[2]
    }
    Add-Item $sp "fish/$sp" $fishEn[$sp] $fishRu[$sp]
}

# ---------- Item models + per-item placeholder textures ----------
# Each item points at its OWN texture (item/<id>, or item/fish/<sp> for fish), so you can draw a
# unique icon per item. Placeholders are only created when a texture is missing — your art is never
# overwritten.
# Display transforms for the assembled-rod BEWLR model (matches vanilla handheld_rod so it poses
# like a rod in hand while our custom renderer stacks the sprite layers).
$rodDisplay = @'
  "display": {
    "gui":                   { "rotation": [0, 0, 0],    "translation": [0, 0, 0],     "scale": [1, 1, 1] },
    "ground":                { "rotation": [0, 0, 0],    "translation": [0, 2, 0],     "scale": [0.5, 0.5, 0.5] },
    "fixed":                 { "rotation": [0, 180, 0],  "translation": [0, 0, 0],     "scale": [1, 1, 1] },
    "head":                  { "rotation": [0, 180, 0],  "translation": [0, 13, 7],    "scale": [1, 1, 1] },
    "thirdperson_righthand": { "rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1, 1, 1] },
    "thirdperson_lefthand":  { "rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1, 1, 1] },
    "firstperson_righthand": { "rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1, 1, 1] },
    "firstperson_lefthand":  { "rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1, 1, 1] }
  }
'@

# Standard item display (matches vanilla item/generated) for the fish BEWLR model, so a caught fish
# behaves like a normal item apart from the weight-based scale applied by FishItemRenderer.
$fishDisplay = @'
  "display": {
    "gui":                   { "rotation": [0, 0, 0],    "translation": [0, 0, 0],        "scale": [1, 1, 1] },
    "ground":                { "rotation": [0, 0, 0],    "translation": [0, 2, 0],        "scale": [0.5, 0.5, 0.5] },
    "fixed":                 { "rotation": [0, 180, 0],  "translation": [0, 0, 0],        "scale": [1, 1, 1] },
    "head":                  { "rotation": [0, 180, 0],  "translation": [0, 13, 7],       "scale": [1, 1, 1] },
    "thirdperson_righthand": { "rotation": [0, 0, 0],    "translation": [0, 3, 1],        "scale": [0.55, 0.55, 0.55] },
    "thirdperson_lefthand":  { "rotation": [0, 0, 0],    "translation": [0, 3, 1],        "scale": [0.55, 0.55, 0.55] },
    "firstperson_righthand": { "rotation": [0, -90, 25], "translation": [1.13, 3.2, 1.13],"scale": [0.68, 0.68, 0.68] },
    "firstperson_lefthand":  { "rotation": [0, 90, -25], "translation": [1.13, 3.2, 1.13],"scale": [0.68, 0.68, 0.68] }
  }
'@

# The scalable sprite the fish BEWLR renders lives here (a plain generated model per species).
$fishIconDir = Join-Path $modelDir "fish_icon"
New-Item -ItemType Directory -Force -Path $fishIconDir | Out-Null
function Write-FishIcon($name, $texRef) {
    $j = @"
{
  "parent": "minecraft:item/generated",
  "textures": {
    "layer0": "$texRef"
  }
}
"@
    Write-Utf8NoBom (Join-Path $fishIconDir "$name.json") $j
}

foreach ($it in $items) {
    if ($it.cat -eq "rod") {
        # Assembled rods render through a custom BEWLR (§rod-layers) — builtin/entity so the engine
        # hands off to RodItemRenderer; a particle texture keeps break/eat particles sane.
        $model = @"
{
  "parent": "minecraft:builtin/entity",
  "gui_light": "front",
  "textures": {
    "particle": "riverfishing:item/$($it.id)"
  },
$rodDisplay
}
"@
        Write-Utf8NoBom (Join-Path $modelDir "$($it.id).json") $model
        continue
    }
    if ($it.cat -like "fish/*") {
        # Caught fish scale their icon by weight (§fish-scale): builtin/entity defers to
        # FishItemRenderer, which draws the item/fish_icon/<sp> sprite scaled.
        $model = @"
{
  "parent": "minecraft:builtin/entity",
  "gui_light": "front",
  "textures": {
    "particle": "riverfishing:item/$($it.cat)"
  },
$fishDisplay
}
"@
        Write-Utf8NoBom (Join-Path $modelDir "$($it.id).json") $model
        Write-FishIcon $it.id "riverfishing:item/$($it.cat)"
        continue
    }
    if ($false) {
        $layer = "riverfishing:item/$($it.cat)"
    } else {
        $layer = "riverfishing:item/$($it.id)"
        $texPath = Join-Path $texDir "$($it.id).png"
        if (-not (Test-Path $texPath)) {
            $c = $catColors[$it.cat]
            New-PlaceholderTexture $texPath $c[0] $c[1] $c[2]
        }
    }
    $model = @"
{
  "parent": "minecraft:item/generated",
  "textures": {
    "layer0": "$layer"
  }
}
"@
    Write-Utf8NoBom (Join-Path $modelDir "$($it.id).json") $model
}

# ---------- Assembled-rod sprite layers (§rod-layers) ----------
# The rod icon is composited at render time from a blank + reel + line + rig sprite. Drop your art
# into textures/item/rod/ and a matching layer model is generated here (any *.png -> a model of the
# same name). Blanks always get a model: your rod/blank_<key>.png if present, else the existing
# item/<key>_rod.png as a fallback, so rods stay visible until you draw the new blanks.
$rodTexDir = Join-Path $texDir "rod"
$rodModelDir = Join-Path $modelDir "rod"
New-Item -ItemType Directory -Force -Path $rodTexDir, $rodModelDir | Out-Null

function Write-LayerModel($name, $texRef) {
    $j = @"
{
  "parent": "minecraft:item/generated",
  "textures": {
    "layer0": "$texRef"
  }
}
"@
    Write-Utf8NoBom (Join-Path $rodModelDir "$name.json") $j
}

# One model per hand-drawn overlay in textures/item/rod/*.png.
Get-ChildItem $rodTexDir -Filter *.png -ErrorAction SilentlyContinue | ForEach-Object {
    Write-LayerModel $_.BaseName "riverfishing:item/rod/$($_.BaseName)"
}

# Guarantee a blank model per rod key (fallback to the existing standalone rod texture).
$rodKeys = "stick", "bamboo", "pole", "ultralight", "spinning", "feeder", "bottom", "carp"
foreach ($k in $rodKeys) {
    if (-not (Test-Path (Join-Path $rodModelDir "blank_$k.json"))) {
        Write-LayerModel "blank_$k" "riverfishing:item/${k}_rod"
    }
}

# ---------- Mirrored rod layers for the HAND (§rod-mirror) ----------
# A held rod is shown edge-on (handheld's y:-90), which MIRRORS a flat sprite. For asymmetric rod art
# (a reel on one side) that looks wrong. Fix: draw the rod in-hand from a horizontally-flipped copy of
# each layer, so the hand's own mirror cancels it and the rod reads correctly (reel side + tip up),
# while the inventory keeps the normal, un-flipped sprite. These copies are generated, never your art.
$rodMTexDir = Join-Path $texDir "rod_m"
$rodMModelDir = Join-Path $modelDir "rod_m"
New-Item -ItemType Directory -Force -Path $rodMTexDir, $rodMModelDir | Out-Null
Get-ChildItem $rodTexDir -Filter *.png -ErrorAction SilentlyContinue | ForEach-Object {
    $img = [System.Drawing.Image]::FromFile($_.FullName)
    $flip = New-Object System.Drawing.Bitmap $img
    $img.Dispose()
    $flip.RotateFlip([System.Drawing.RotateFlipType]::RotateNoneFlipX)
    $flip.Save((Join-Path $rodMTexDir $_.Name), [System.Drawing.Imaging.ImageFormat]::Png)
    $flip.Dispose()
    $mj = @"
{
  "parent": "minecraft:item/generated",
  "textures": {
    "layer0": "riverfishing:item/rod_m/$($_.BaseName)"
  }
}
"@
    Write-Utf8NoBom (Join-Path $rodMModelDir "$($_.BaseName).json") $mj
}
# Mirrored blank fallback: flip the standalone rod texture when no rod/blank_<k>.png was supplied.
foreach ($k in $rodKeys) {
    if (Test-Path (Join-Path $rodMModelDir "blank_$k.json")) { continue }
    $src = Join-Path $texDir "${k}_rod.png"
    if (Test-Path $src) {
        $img = [System.Drawing.Image]::FromFile($src)
        $flip = New-Object System.Drawing.Bitmap $img
        $img.Dispose()
        $flip.RotateFlip([System.Drawing.RotateFlipType]::RotateNoneFlipX)
        $flip.Save((Join-Path $rodMTexDir "blank_$k.png"), [System.Drawing.Imaging.ImageFormat]::Png)
        $flip.Dispose()
        Write-Utf8NoBom (Join-Path $rodMModelDir "blank_$k.json") (@"
{
  "parent": "minecraft:item/generated",
  "textures": {
    "layer0": "riverfishing:item/rod_m/blank_$k"
  }
}
"@)
    }
}

# ---------- Lang files (fish name maps are defined near the top) ----------
function Build-Lang($lang) {
    $o = [ordered]@{}
    $o["itemGroup.riverfishing"] = if ($lang -eq "ru") { "Речная рыбалка" } else { "River Fishing" }
    foreach ($it in $items) { $o["item.riverfishing.$($it.id)"] = if ($lang -eq "ru") { $it.ru } else { $it.en } }
    foreach ($k in $fishEn.Keys) { $o["fish.riverfishing.$k"] = if ($lang -eq "ru") { $fishRu[$k] } else { $fishEn[$k] } }

    if ($lang -eq "ru") {
        $o["linetype.riverfishing.mono"] = "моно"
        $o["linetype.riverfishing.braid"] = "плетёнка"
        $o["linetype.riverfishing.fluoro"] = "флюорокарбон"
        $o["menu.riverfishing.rod_assembly"] = "Сборка удочки"
        $o["menu.riverfishing.slot.reel"] = "Катушка"
        $o["menu.riverfishing.slot.line"] = "Леска"
        $o["menu.riverfishing.slot.rig"] = "Оснастка"
        $o["menu.riverfishing.slot.hook"] = "Крючок"
        $o["menu.riverfishing.assembly_hint"] = "Перетащи катушку, леску и оснастку"
        $o["menu.riverfishing.rig_hint"] = "Загрузи крючки, наживку и прикормку"
        $o["tooltip.riverfishing.reel_size"] = "Размер: %s"
        $o["tooltip.riverfishing.reel_drag"] = "Фрикцион: %s кг"
        $o["tooltip.riverfishing.reel_line"] = "Рабочая леска: %s–%s мм"
        $o["tooltip.riverfishing.line_spec"] = "%s, %s мм"
        $o["tooltip.riverfishing.line_strain"] = "Тест на разрыв: %s кг"
        $o["tooltip.riverfishing.rig_mass"] = "Масса: %s г"
        $o["tooltip.riverfishing.rig_leader"] = "Со стальным поводком"
        $o["tooltip.riverfishing.rig_open"] = "Shift+ПКМ — открыть оснастку"
        $o["tooltip.riverfishing.rig_contents"] = "В оснастке:"
        $o["tooltip.riverfishing.rig_empty"] = "Оснастка пустая"
        $o["tooltip.riverfishing.leader_protection"] = "Защита от перекуса: %s%%"
        $o["tooltip.riverfishing.leader_stealth"] = "Незаметность: %s%%"
        $o["rig.riverfishing.role.hook"] = "Крючок"
        $o["rig.riverfishing.role.bait"] = "Наживка"
        $o["rig.riverfishing.role.groundbait"] = "Прикормка"
        $o["rig.riverfishing.role.float"] = "Поплавок"
        $o["rig.riverfishing.role.leader"] = "Поводок"
        $o["rig.riverfishing.role.lure"] = "Приманка"
        $o["validation.riverfishing.reel_none"] = "На этом бланке нет места под катушку"
        $o["validation.riverfishing.reel_size"] = "Неподходящий размер катушки для этого бланка"
        $o["validation.riverfishing.line_reel"] = "Слишком толстая леска для этой катушки"
        $o["validation.riverfishing.reel_line"] = "Катушка мала — не вмещает установленную леску"
        $o["validation.riverfishing.line_no_reel"] = "Сначала установите катушку — леска наматывается на неё"
        $o["tooltip.riverfishing.hook_size"] = "Размер: №%s"
        $o["tooltip.riverfishing.bait_natural"] = "Натуральная наживка"
        $o["tooltip.riverfishing.bait_artificial"] = "Искусственная приманка (только хищник)"
        $o["tooltip.riverfishing.groundbait_use"] = "ПКМ по воде — закормить точку"
        $o["tooltip.riverfishing.fish_length"] = "Длина: %s см"
        $o["tooltip.riverfishing.fish_foulhooked"] = "Багорная — не в зачёт"
        $o["tooltip.riverfishing.rod_assembled"] = "Удочка собрана"
        $o["tooltip.riverfishing.rod_unassembled"] = "Не собрана (нужны леска и оснастка)"
        $o["message.riverfishing.rod_overload_break"] = "Удилище сломалось — оснастка слишком тяжёлая!"
        $o["message.riverfishing.rod_overload_crack"] = "Бланк затрещал под тяжестью — потеряна треть прочности!"
        $o["message.riverfishing.rod_overloaded"] = "Оснастка тяжела для бланка — риск поломки"
        $o["tooltip.riverfishing.rod_hint"] = "Sneak + ПКМ — собрать. ПКМ по воде — заброс."
        $o["message.riverfishing.not_assembled"] = "Удочка не собрана"
        $o["message.riverfishing.no_water"] = "Нет воды для заброса"
        $o["message.riverfishing.too_narrow"] = "Слишком узкий водоём для дальнего заброса"
        $o["message.riverfishing.no_bait"] = "На крючке нет наживки"
        $o["message.riverfishing.no_bites_here"] = "Здесь сейчас не клюёт"
        $o["message.riverfishing.depleted"] = "Здесь рыба выбита — смени место"
        $o["message.riverfishing.cast_out"] = "Заброс выполнен. Ждите поклёвку…"
        $o["message.riverfishing.ice_fishing"] = "Подлёдная ловля — играй мормышкой (ПКМ)"
        $o["message.riverfishing.jig"] = "Мормышка играет…"
        $o["message.riverfishing.jig_good"] = "Ровная игра — рыба подходит!"
        $o["message.riverfishing.need_hole"] = "Прорубите лунку во льду ледобуром"
        $o["message.riverfishing.need_winter_rod"] = "Нужна собранная зимняя удочка"
        $o["message.riverfishing.winter_needs_hole"] = "Зимней удочкой ловят только в лунке — ПКМ по пробуренному льду"
        $o["message.riverfishing.auger_hole"] = "Пробурена лунка"
        $o["message.riverfishing.auger_no_water"] = "Подо льдом нет воды"
        $o["tooltip.riverfishing.ice_auger"] = "ПКМ по льду над водой — пробурить лунку"
        $o["message.riverfishing.bite"] = "Поклёвка! Подсекай!"
        $o["message.riverfishing.mistimed"] = "Мимо! Не попал в момент подсечки"
        $o["hud.riverfishing.strike_timing"] = "Подсекай в зелёной зоне!"
        $o["message.riverfishing.missed"] = "Рыба сошла…"
        $o["message.riverfishing.reeled_in"] = "Смотал снасть"
        $o["message.riverfishing.caught"] = "Поймана: %s — %s, %s см"
        $o["message.riverfishing.caught_foul"] = "Забагренная рыба: %s — %s, %s см (не в зачёт)"
        $o["message.riverfishing.snag_free"] = "Зацеп! Удалось отцепить"
        $o["message.riverfishing.snag_lost"] = "Глухой зацеп — оснастка оборвана"
        $o["message.riverfishing.foul_hooked"] = "Багор! Рыба засеклась за тело — будет тяжело"
        $o["message.riverfishing.caught_multi"] = "Поймано: %s ×%s!"
        $o["message.riverfishing.fed_spot"] = "Точка закормлена"
        $o["message.riverfishing.hooked"] = "На крючке! Тяни — но не перетяни!"
        $o["message.riverfishing.line_break"] = "Обрыв! Рыба и оснастка потеряны"
        $o["message.riverfishing.leader_bite_off"] = "Перекусила леску — нужен поводок!"
        $o["message.riverfishing.fighting"] = "Вываживание — не упусти!"
        $o["message.riverfishing.strike"] = "Удар! Подсекай!"
        $o["message.riverfishing.retrieve_empty"] = "Пусто. Перезаброс."
        $o["message.riverfishing.shake_off"] = "Сход! Рыба сорвалась"
        $o["message.riverfishing.cast_spin"] = "Заброс. Зажми ПКМ — подтяжка."
        $o["item.riverfishing.rod_pod_1"] = "Род-под (1 слот)"
        $o["item.riverfishing.rod_pod_3"] = "Род-под (3 слота)"
        $o["item.riverfishing.bait_trap"] = "Малявочник"
        $o["block.riverfishing.bait_trap"] = "Малявочник"
        $o["tooltip.riverfishing.bait_trap"] = "Поставь в воду — со временем набирает живца"
        $o["message.riverfishing.trap_empty"] = "Малявочник пока пуст"
        $o["entity.minecraft.villager.riverfishing.fisherman"] = "Рыбак"
        $o["entity.riverfishing.villager.fisherman"] = "Рыбак"
        $o["item.riverfishing.fishing_stall"] = "Рыболовный прилавок"
        $o["item.riverfishing.trophy_stand"] = "Трофейная подставка"
        $o["item.riverfishing.ice_hole"] = "Пробуренная лунка"
        $o["block.riverfishing.ice_hole"] = "Пробуренная лунка"
        # Block display names (BlockItems use block.* keys, not item.*)
        $o["block.riverfishing.rod_pod_1"] = "Род-под (1 слот)"
        $o["block.riverfishing.rod_pod_3"] = "Род-под (3 слота)"
        $o["block.riverfishing.fishing_stall"] = "Рыболовный прилавок"
        $o["block.riverfishing.trophy_stand"] = "Трофейная подставка"
        $o["block.riverfishing.aquarium"] = "Аквариум"
        $o["message.riverfishing.pod_wrong_rod"] = "На подставку — только донные снасти"
        $o["message.riverfishing.pod_full"] = "Нет свободных слотов на подставке"
        $o["message.riverfishing.pod_cast_first"] = "Сначала забрось снасть"
        $o["message.riverfishing.pod_docked"] = "Удочка на подставке"
        $o["message.riverfishing.pod_taken"] = "Снял удочку"
        $o["message.riverfishing.pod_phantom"] = "Пусто — фантомная поклёвка"
        $o["message.riverfishing.pod_self_hooked"] = "Рыба засеклась сама — вываживай!"
        $o["tooltip.riverfishing.alarm_use"] = "ПКМ по подставке с удочкой — повесить сигнализатор"
        $o["tooltip.riverfishing.knife_use"] = "ПКМ: разделать рыбу из другой руки на филе"
        $o["tooltip.riverfishing.whetstone_use"] = "ПКМ: заточить крючок из другой руки"
        $o["tooltip.riverfishing.journal_use"] = "ПКМ — показать записи"
        $o["journal.riverfishing.header"] = "— Рыболовный дневник —"
        $o["journal.riverfishing.empty"] = "Пока ничего не поймано"
        $o["journal.riverfishing.total"] = "Всего поймано: %s | Видов: %s"
        $o["journal.riverfishing.tab_fish"] = "Рыбы"
        $o["journal.riverfishing.tab_bait"] = "Наживки"
        $o["journal.riverfishing.tab_bait_hint"] = "Наживки и приманки — на кого работают"
        $o["journal.riverfishing.bait_natural"] = "Наживка"
        $o["journal.riverfishing.bait_artificial"] = "Приманка"
        $o["journal.riverfishing.bait_groundbait"] = "Прикормка"
        $o["journal.riverfishing.bait_catches"] = "Ловит:"
        $o["journal.riverfishing.bait_attracts"] = "Привлекает:"
        $o["journal.riverfishing.sec_natural"] = "Наживки"
        $o["journal.riverfishing.sec_lure"] = "Приманки"
        $o["journal.riverfishing.sec_groundbait"] = "Прикормки"
        $o["journal.riverfishing.tab_gear"] = "Снаряжение"
        $o["journal.riverfishing.sec_rod"] = "Удилища"
        $o["journal.riverfishing.sec_reel"] = "Катушки"
        $o["journal.riverfishing.sec_line"] = "Лески"
        $o["journal.riverfishing.sec_rig"] = "Оснастки"
        $o["journal.riverfishing.obtain_craft"] = "Как получить — крафт:"
        $o["journal.riverfishing.compat_reel"] = "Катушка:"
        $o["journal.riverfishing.compat_no_reel"] = "без катушки"
        $o["journal.riverfishing.compat_rods"] = "Удилища:"
        $o["journal.riverfishing.compat_line"] = "Леска, мм:"
        $o["journal.riverfishing.compat_reel_from"] = "Катушка от:"
        $o["journal.riverfishing.obtain_other"] = "Добывается в мире или у рыболова"
        # Angler quests (§quests)
        $o["journal.riverfishing.tab_quest"] = "Задания"
        $o["message.riverfishing.quest_done"] = "Награда получена: %s"
        $o["message.riverfishing.quest_ready"] = "Задание выполнено: %s — награда ждёт в дневнике!"
        $o["quest.riverfishing.claim"] = "Забрать!"
        $o["quest.riverfishing.claim_hint"] = "Нажми, чтобы забрать награду"
        $o["quest.riverfishing.reward"] = "Награда: %s"
        $o["quest.riverfishing.stage.1"] = "Стадия 1 — Новичок"
        $o["quest.riverfishing.stage.2"] = "Стадия 2 — Поплавок и фидер"
        $o["quest.riverfishing.stage.3"] = "Стадия 3 — Хищник"
        $o["quest.riverfishing.stage.4"] = "Стадия 4 — Тяжёлая снасть"
        $o["quest.riverfishing.stage.5"] = "Стадия 5 — Мастер"
        $o["quest.riverfishing.stage.6"] = "Стадия 6 — Подо льдом"
        $o["quest.riverfishing.stage_locked"] = "Закрыто — заверши стадию %s"
        $o["quest.riverfishing.q_first_fish"] = "Поймай первую рыбу"
        $o["quest.riverfishing.q_species3"] = "Открой 3 вида рыбы"
        $o["quest.riverfishing.q_crucian"] = "Поймай карася"
        $o["quest.riverfishing.q_bream"] = "Поймай леща"
        $o["quest.riverfishing.q_bream_big"] = "Поймай леща 2+ кг"
        $o["quest.riverfishing.q_species8"] = "Открой 8 видов рыбы"
        $o["quest.riverfishing.q_perch"] = "Поймай окуня"
        $o["quest.riverfishing.q_pike"] = "Поймай щуку"
        $o["quest.riverfishing.q_zander"] = "Поймай судака"
        $o["quest.riverfishing.q_asp"] = "Поймай жереха"
        $o["quest.riverfishing.q_carp"] = "Поймай карпа"
        $o["quest.riverfishing.q_carp_big"] = "Поймай карпа 8+ кг"
        $o["quest.riverfishing.q_catfish"] = "Поймай сома"
        $o["quest.riverfishing.q_trout"] = "Поймай форель"
        $o["quest.riverfishing.q_species15"] = "Открой 15 видов рыбы"
        $o["quest.riverfishing.q_sterlet"] = "Поймай стерлядь"
        $o["quest.riverfishing.q_koi"] = "Поймай карпа кои"
        $o["quest.riverfishing.q_trophy"] = "Поймай трофейный экземпляр"
        $o["quest.riverfishing.q_master"] = "Достигни ранга мастера (ур. 20)"
        $o["quest.riverfishing.q_roach"] = "Поймай плотву"
        $o["quest.riverfishing.q_ten_fish"] = "Поймай 10 рыб"
        $o["quest.riverfishing.q_rudd"] = "Поймай красноперку"
        $o["quest.riverfishing.q_tench"] = "Поймай линя"
        $o["quest.riverfishing.q_pike_big"] = "Поймай щуку 5+ кг"
        $o["quest.riverfishing.q_catfish_big"] = "Поймай сома 20+ кг"
        $o["quest.riverfishing.q_hundred"] = "Поймай 100 рыб"
        $o["quest.riverfishing.q_grayling"] = "Поймай хариуса"
        $o["quest.riverfishing.q_trophy5"] = "Поймай 5 трофеев"
        $o["quest.riverfishing.q_species20"] = "Открой 20 видов рыбы"
        $o["quest.riverfishing.q_ice_first"] = "Поймай первую рыбу со льда"
        $o["quest.riverfishing.q_ice_burbot"] = "Поймай налима"
        $o["quest.riverfishing.q_ice_ruffe"] = "Поймай ерша"
        $o["quest.riverfishing.q_ice_ten"] = "Поймай 10 рыб со льда"
        $o["quest.riverfishing.q_ice_thirty"] = "Поймай 30 рыб со льда"
        $o["quest.riverfishing.q_stage1_done"] = "Заверши стадию 1 полностью"
        $o["quest.riverfishing.q_stage2_done"] = "Заверши стадию 2 полностью"
        $o["quest.riverfishing.q_stage3_done"] = "Заверши стадию 3 полностью"
        $o["quest.riverfishing.q_stage4_done"] = "Заверши стадию 4 полностью"
        $o["quest.riverfishing.q_stage5_done"] = "Заверши стадию 5 полностью"
        $o["quest.riverfishing.q_stage6_done"] = "Заверши стадию 6 полностью"
        # Angler skill tree (§skills)
        $o["journal.riverfishing.tab_skill"] = "Навыки"
        $o["journal.riverfishing.skill_points"] = "Очки навыков: %s"
        $o["skill.riverfishing.learned"] = "— навык улучшен!"
        $o["skill.riverfishing.maxed"] = "макс."
        $o["skill.riverfishing.branch.bait"] = "Наживка"
        $o["skill.riverfishing.branch.sense"] = "Чуткость"
        $o["skill.riverfishing.branch.knowledge"] = "Знание"
        $o["skill.riverfishing.branch.hand"] = "Рука"
        $o["skill.riverfishing.branch.fortune"] = "Удача"
        $o["skill.riverfishing.branch.skill"] = "Умение"
        $o["skill.riverfishing.frugal"] = "Бережливость"
        $o["skill.riverfishing.frugal.desc"] = "+5%/ур. шанс сохранить наживку после поклёвки."
        $o["skill.riverfishing.quick_bite"] = "Чуткость"
        $o["skill.riverfishing.quick_bite.desc"] = "+5%/ур. — поклёвка приходит быстрее."
        $o["skill.riverfishing.naturalist"] = "Натуралист"
        $o["skill.riverfishing.naturalist.desc"] = "+5%/ур. к общему шансу клёва."
        $o["skill.riverfishing.strong_line"] = "Крепкая рука"
        $o["skill.riverfishing.strong_line.desc"] = "+5%/ур. — леска держит больше нагрузки до обрыва."
        $o["skill.riverfishing.anglers_luck"] = "Рыбацкая удача"
        $o["skill.riverfishing.anglers_luck.desc"] = "+1%/ур. к шансу трофея."
        $o["skill.riverfishing.finesse"] = "Умение"
        $o["skill.riverfishing.finesse.desc"] = "+1%/ур. — шире зона подсечки."
        # Fish descriptions (bestiary lore + catch hints)
        $o["fishdesc.riverfishing.bream"] = "Стайная донная рыба с высоким сплющенным телом. Держится ям и бровок, кормится со дна. Фидер и поплавок с прикормкой, на червя, опарыша и перловку."
        $o["fishdesc.riverfishing.crucian_carp"] = "Неприхотливая рыба заросших прудов и стариц. Осторожен, лучше клюёт в тепло. Поплавок, тонкая леска, тесто, червь или перловка."
        $o["fishdesc.riverfishing.roach"] = "Самая обычная бель наших вод. Клюёт почти везде и круглый год. Поплавок с мелким крючком, опарыш, мотыль, тесто."
        $o["fishdesc.riverfishing.rudd"] = "Ярко-красные плавники, любит верхние слои у зарослей. Берёт вполводы. Поплавок, тесто, опарыш, кусочек хлеба."
        $o["fishdesc.riverfishing.white_bream"] = "Похожа на леща, но мельче и серебристее. Стайная, донная. Фидер и поплавок, червь, опарыш, прикормка."
        $o["fishdesc.riverfishing.carp"] = "Сильная, осторожная рыба тёплых водоёмов. Мощно сопротивляется. Карповое удилище, прочная леска, бойлы и кукуруза, обязательно прикормка."
        $o["fishdesc.riverfishing.catfish"] = "Крупнейший пресноводный хищник, ночной донный охотник глубоких ям. Донка с поводком, живец или крупная наживка; ночью и в тепло."
        $o["fishdesc.riverfishing.perch"] = "Полосатый хищник, держится стаями у коряг и бровок. Жадно берёт. Спиннинг с мелкой блесной или силиконом, поплавок с червём."
        $o["fishdesc.riverfishing.pike"] = "Зубастый засадный хищник камыша и коряг. Атакует из укрытия. Спиннинг, воблеры и блёсны, обязательно поводок; лучше по холодной воде."
        $o["fishdesc.riverfishing.zander"] = "Ночной хищник глубоких бровок и песчаного дна. Клюёт в сумерках и ночью. Спиннинг с силиконом или воблером и поводком, живец."
        $o["fishdesc.riverfishing.gudgeon"] = "Мелкая донная рыбка чистых песчаных перекатов. Отличный живец. Поплавок у дна, мелкий крючок, мотыль или червь."
        $o["fishdesc.riverfishing.ruffe"] = "Колючий мелкий обитатель дна, активен и ночью. Жадно берёт червя и мотыля со дна. Часто мешает ловле более крупной рыбы."
        $o["fishdesc.riverfishing.bleak"] = "Мелкая стайная рыбка верхних слоёв, постоянно у поверхности. Лёгкий поплавок вполводы, опарыш, крошка хлеба; хороший живец."
        $o["fishdesc.riverfishing.ide"] = "Крупная сильная бель, всеядна и осторожна. Держится ям и течения. Фидер и поплавок, червь, тесто, зелень; берёт и на спиннинг."
        $o["fishdesc.riverfishing.chub"] = "Осторожный обитатель рек с сильным течением, всеяден и хватает малька. Поплавок и спиннинг, насекомые, хлеб, мелкие блёсны."
        $o["fishdesc.riverfishing.asp"] = "Стремительный речной хищник открытой воды, бьёт малька у поверхности. Спиннинг, дальний заброс, тяжёлые блёсны; силён и осторожен."
        $o["fishdesc.riverfishing.tench"] = "Тёмная, слизистая рыба заросших тёплых водоёмов, кормится в тине у дна. Поплавок и донка, червь, кукуруза; клёв на заре в тепло."
        $o["fishdesc.riverfishing.burbot"] = "Единственная тресковая пресных вод, любит холод, активен ночью и зимой. Донка со дна, живец или кусок рыбы; лучший клёв поздней осенью."
        $o["fishdesc.riverfishing.eel"] = "Змеевидная ночная рыба, днём зарывается в ил. Активен в темноте. Донка со дна, выползок или живец; крепкая снасть — сильно упирается."
        $o["fishdesc.riverfishing.grayling"] = "Красивая рыба холодных чистых рек с высоким плавником, держится струи. Лёгкая снасть, насекомые, мелкие блёсны; вода должна быть холодной."
        $o["fishdesc.riverfishing.trout"] = "Хищник холодных быстрых рек и ручьёв, осторожна и сильна. Спиннинг с мелкими блёснами и воблерами; только чистая холодная вода."
        $o["fishdesc.riverfishing.sterlet"] = "Небольшой осётр с рядами костяных жучек, кормится у дна на течении. Донка, червь; редкая и ценная — трофей коллекции."
        $o["fishdesc.riverfishing.wild_carp"] = "Дикий предок карпа — мощный и злой боец, держится ям и коряжника. Карповая снасть, прочнейшая леска, бойлы и кукуруза; вываживать тяжело."
        $o["fishdesc.riverfishing.mirror_carp"] = "Карп с крупными редкими чешуйками-зеркалами. Повадки как у карпа, чуть спокойнее. Карповое удилище, бойлы, кукуруза, прикормка."
        $o["fishdesc.riverfishing.carp_koi_kohaku"] = "Декоративный карп: белое тело с алыми пятнами. Коллекционный, попадается на карповые снасти, чаще у цветущей сакуры. Есть — глупо."
        $o["fishdesc.riverfishing.carp_koi_tancho_sanke"] = "Кои с единственным алым пятном на голове, как у журавля танчо. Редкий коллекционный трофей; берёт на карповую снасть у сакуры."
        $o["fishdesc.riverfishing.carp_koi_showa_sanke"] = "Чёрный кои с красным и белым узором. Коллекционный, попадается на карповые снасти, чаще в вишнёвых биомах."
        $o["fishdesc.riverfishing.carp_koi_asagi"] = "Голубовато-серая сетчатая спина и красные бока. Коллекционный кои, случайная поимка на карповую снасть у сакуры."
        $o["fishdesc.riverfishing.carp_koi_bekko"] = "Однотонный кои с чёрными пятнами. Редкий коллекционный, ловится на карповые снасти в вишнёвых биомах."
        $o["journal.riverfishing.no_illustration"] = "иллюстрация не добавлена"
        $o["tooltip.riverfishing.wear"] = "Износ: %s%%"
        $o["tooltip.riverfishing.line_worn_out"] = "Леска изношена — может порваться в любой момент!"
        $o["message.riverfishing.line_worn_out"] = "Леска изношена — может порваться в любой момент!"
        $o["tooltip.riverfishing.hook_dull"] = "Затупление: %s%%"
        $o["message.riverfishing.empty_set"] = "Пустая подсечка — крючок затупился"
        $o["message.riverfishing.alarm_attached"] = "Сигнализатор установлен"
        $o["message.riverfishing.alarm_needs_rod"] = "Нужна удочка на подставке без сигнализатора"
        $o["message.riverfishing.alarm_bite"] = "Поклёвка на подставке!"
        $o["message.riverfishing.alarm_phantom"] = "Сигнал… (возможно, фантом)"
        # Angler progression (XP / levels / ranks)
        $o["journal.riverfishing.angler"] = "Рыболов: ур. %s (%s) — %s XP, до след. %s"
        $o["message.riverfishing.xp_gained"] = "+%s XP рыболова"
        $o["message.riverfishing.new_species"] = "Новый вид в дневнике!"
        $o["message.riverfishing.level_up"] = "Уровень рыболова: %s!"
        $o["message.riverfishing.rank_up"] = "Новый ранг: %s!"
        $o["rank.riverfishing.bronze"] = "Бронза"
        $o["rank.riverfishing.silver"] = "Серебро"
        $o["rank.riverfishing.gold"] = "Золото"
        $o["rank.riverfishing.master"] = "Мастер"
        # JEI category
        $o["jei.riverfishing.category"] = "Рыбалка"
        $o["jei.riverfishing.no_data"] = "Войдите в мир, чтобы увидеть данные"
        $o["jei.riverfishing.bait"] = "Наживка:"
        $o["jei.riverfishing.tackle"] = "Снасть:"
        $o["jei.riverfishing.best"] = "Лучше:"
        $o["jei.riverfishing.water"] = "Водоём:"
        $o["jei.riverfishing.level"] = "Рекомендуемый уровень: %s"
        $o["jei.riverfishing.groundbait"] = "Прикормка:"
        $o["item.riverfishing.worm_farm"] = "Червятник"
        $o["block.riverfishing.worm_farm"] = "Червятник"
        $o["message.riverfishing.farm_empty"] = "Черви ещё не расплодились"
        $o["item.riverfishing.maggot_farm"] = "Ферма опарыша"
        $o["block.riverfishing.maggot_farm"] = "Ферма опарыша"
        $o["message.riverfishing.maggot_farm_fill"] = "Гнилой плоти заложено: %s"
        $o["message.riverfishing.maggot_farm_full"] = "Ферма забита плотью доверху"
        $o["message.riverfishing.maggot_farm_empty"] = "Опарыши ещё не вывелись"
        # Living-fishing wave: trophies, final surge, frenzy, bycatch
        $o["message.riverfishing.trophy_catch"] = "Трофейный экземпляр!"
        $o["tooltip.riverfishing.fish_trophy"] = "★ Трофей"
        $o["tooltip.riverfishing.fish_prime"] = "Отборный экземпляр — скупщик заинтересован"
        $o["tooltip.riverfishing.trade_min_weight"] = "Принимает: от %s"
        $o["trade.riverfishing.assembled"] = "%s (в сборе)"
        $o["message.riverfishing.final_surge"] = "Рыба рванула у самого берега!"
        $o["message.riverfishing.cast_frenzy"] = "Жор! Вода кипит от рыбы"
        $o["message.riverfishing.junk_catch"] = "Зацепил хлам: %s"
        $o["message.riverfishing.treasure_catch"] = "Неожиданный улов: %s!"
        # QoL: pole limit, fish finder, admin probe
        $o["message.riverfishing.pole_too_far"] = "Маховой удочкой не добросить дальше 6 блоков"
        $o["finder.riverfishing.header"] = "Эхолот — сейчас здесь может клюнуть:"
        $o["finder.riverfishing.none"] = "Эхолот молчит — здесь сейчас пусто"
        $o["finder.riverfishing.pressure"] = "Давление: %s гПа %s  —  клёв:"
        $o["finder.riverfishing.outlook.great"] = "жор"
        $o["finder.riverfishing.outlook.good"] = "активный"
        $o["finder.riverfishing.outlook.fair"] = "средний"
        $o["finder.riverfishing.outlook.poor"] = "вялый"
        $o["tooltip.riverfishing.fish_finder"] = "ПКМ по воде — показать активную рыбу"
        $o["tooltip.riverfishing.hydro_probe"] = "ПКМ по воде — полная сводка водоёма (админ)"
        # Float QTE + in-journal guide
        $o["message.riverfishing.qte_start"] = "Держи её! Подсекай в зелёной зоне!"
        $o["guide.riverfishing.hint_open"] = "Клик — как поймать"
        $o["guide.riverfishing.back"] = "Клик — назад к списку"
        $o["guide.riverfishing.anywhere"] = "любые"
        $o["guide.riverfishing.water"] = "Вода:"
        $o["guide.riverfishing.depth"] = "Глубина:"
        $o["guide.riverfishing.size"] = "Ширина:"
        $o["guide.riverfishing.biomes"] = "Биомы:"
        $o["guide.riverfishing.best"] = "Лучше:"
        $o["guide.riverfishing.bait"] = "Наживка:"
        $o["guide.riverfishing.tackle"] = "Снасть:"
        $o["water.riverfishing.river"] = "река"
        $o["water.riverfishing.lake"] = "озеро"
        $o["water.riverfishing.pond"] = "пруд"
        $o["water.riverfishing.swamp"] = "болото"
        $o["water.riverfishing.sea"] = "море"
        $o["water.riverfishing.puddle"] = "лужа"
        $o["season.riverfishing.spring"] = "весна"
        $o["season.riverfishing.summer"] = "лето"
        $o["season.riverfishing.autumn"] = "осень"
        $o["season.riverfishing.winter"] = "зима"
        $o["time.riverfishing.dawn"] = "рассвет"
        $o["time.riverfishing.day"] = "день"
        $o["time.riverfishing.dusk"] = "закат"
        $o["time.riverfishing.night"] = "ночь"
        $o["biomegroup.riverfishing.cold"] = "холодные"
        $o["biomegroup.riverfishing.temperate"] = "умеренные"
        $o["biomegroup.riverfishing.warm"] = "тёплые"
        $o["biomegroup.riverfishing.swamp"] = "болота"
        $o["biomegroup.riverfishing.taiga"] = "тайга"
        $o["biomegroup.riverfishing.mountain"] = "горы"
        $o["biomegroup.riverfishing.jungle"] = "джунгли"
        $o["biomegroup.riverfishing.forest"] = "леса"
        $o["biomegroup.riverfishing.river_biome"] = "речные"
        $o["biomegroup.riverfishing.ocean_biome"] = "океан"
        $o["biomegroup.riverfishing.beach"] = "побережье"
        $o["biomegroup.riverfishing.dry"] = "засушливые"
        # Float depth ("спуск") + rod test
        $o["message.riverfishing.float_depth"] = "Спуск поплавка: %s"
        $o["tooltip.riverfishing.float_depth"] = "Спуск: %s"
        $o["tooltip.riverfishing.float_use"] = "ПКМ — изменить спуск (до установки в оснастку)"
        $o["depthset.riverfishing.surface"] = "у поверхности"
        $o["depthset.riverfishing.mid"] = "вполводы"
        $o["depthset.riverfishing.bottom"] = "у дна"
        $o["tooltip.riverfishing.rod_test"] = "Тест: %s–%s г"
        $o["message.riverfishing.rod_underloaded"] = "Оснастка слишком легка для бланка — бросок короткий"
        $o["guide.riverfishing.depth_short"] = "Спуск"
        # Advancements
        $o["advancement.riverfishing.root.title"] = "Речная рыбалка"
        $o["advancement.riverfishing.root.description"] = "Поймайте свою первую рыбу"
        $o["advancement.riverfishing.trophy.title"] = "Трофейный экземпляр"
        $o["advancement.riverfishing.trophy.description"] = "Поймайте рыбу трофейного размера"
        $o["advancement.riverfishing.predator.title"] = "Зубастая"
        $o["advancement.riverfishing.predator.description"] = "Поймайте щуку на спиннинг"
        $o["advancement.riverfishing.giant.title"] = "Хозяин ямы"
        $o["advancement.riverfishing.giant.description"] = "Вытащите сома из глубокой ямы"
        $o["advancement.riverfishing.sterlet.title"] = "Царская рыба"
        $o["advancement.riverfishing.sterlet.description"] = "Поймайте стерлядь — редчайшую рыбу больших рек"
        $o["advancement.riverfishing.night_king.title"] = "Король зимней ночи"
        $o["advancement.riverfishing.night_king.description"] = "Поймайте налима — он клюёт только холодной ночью"
        $o["advancement.riverfishing.master.title"] = "Мастер-рыболов"
        $o["advancement.riverfishing.master.description"] = "Достигните ранга Мастер"
        $o["advancement.riverfishing.all_species.title"] = "Полный бестиарий"
        $o["advancement.riverfishing.all_species.description"] = "Поймайте все 24 вида рыб (кои не в счёт)"
        $o["advancement.riverfishing.sazan.title"] = "Речной боец"
        $o["advancement.riverfishing.sazan.description"] = "Одолейте сазана — самого злого бойца среди карповых"
        $o["advancement.riverfishing.koi.title"] = "Живая драгоценность"
        $o["advancement.riverfishing.koi.description"] = "Поймайте карпа кои — редчайшую находку на карповую снасть"
        $o["advancement.riverfishing.koi_master.title"] = "Коллекционер кои"
        $o["advancement.riverfishing.koi_master.description"] = "Соберите всех пятерых кои: Кохаку, Танчо, Сёва, Асаги и Бекко"
        $o["message.riverfishing.koi_filleted"] = "ты серьезно её на филе пустил?"
        # Code-driven advancements (§challenges)
        $o["advancement.riverfishing.pike_on_wood.title"] = "Дедовским способом"
        $o["advancement.riverfishing.pike_on_wood.description"] = "Поймайте щуку 4+ кг на живца обычной деревянной удочкой"
        $o["advancement.riverfishing.ice_burbot.title"] = "Из-подо льда"
        $o["advancement.riverfishing.ice_burbot.description"] = "Вытащите налима зимней удочкой через лунку"
        $o["advancement.riverfishing.trophy_on_pole.title"] = "Голыми руками"
        $o["advancement.riverfishing.trophy_on_pole.description"] = "Возьмите трофей маховой удочкой без катушки — только нервы"
        $o["advancement.riverfishing.frenzy_feast.title"] = "В самый жор"
        $o["advancement.riverfishing.frenzy_feast.description"] = "Поймайте рыбу во время жора"
        $o["advancement.riverfishing.old_boot.title"] = "Улов десятилетия"
        $o["advancement.riverfishing.old_boot.description"] = "Выловите старый сапог"
        $o["advancement.riverfishing.koi_fillet.title"] = "Бессердечный повар"
        $o["advancement.riverfishing.koi_fillet.description"] = "Пустите карпа кои на филе. Зачем?.."
    } else {
        $o["linetype.riverfishing.mono"] = "mono"
        $o["linetype.riverfishing.braid"] = "braid"
        $o["linetype.riverfishing.fluoro"] = "fluorocarbon"
        $o["menu.riverfishing.rod_assembly"] = "Rod Assembly"
        $o["menu.riverfishing.slot.reel"] = "Reel"
        $o["menu.riverfishing.slot.line"] = "Line"
        $o["menu.riverfishing.slot.rig"] = "Rig"
        $o["menu.riverfishing.slot.hook"] = "Hook"
        $o["menu.riverfishing.assembly_hint"] = "Drag a reel, line and rig in"
        $o["menu.riverfishing.rig_hint"] = "Load hooks, bait and groundbait"
        $o["tooltip.riverfishing.reel_size"] = "Size: %s"
        $o["tooltip.riverfishing.reel_drag"] = "Drag: %s kg"
        $o["tooltip.riverfishing.reel_line"] = "Working line: %s-%s mm"
        $o["tooltip.riverfishing.line_spec"] = "%s, %s mm"
        $o["tooltip.riverfishing.line_strain"] = "Breaking strain: %s kg"
        $o["tooltip.riverfishing.rig_mass"] = "Mass: %s g"
        $o["tooltip.riverfishing.rig_leader"] = "With steel leader"
        $o["tooltip.riverfishing.rig_open"] = "Shift+right-click to open the rig"
        $o["tooltip.riverfishing.rig_contents"] = "Loaded:"
        $o["tooltip.riverfishing.rig_empty"] = "Rig is empty"
        $o["tooltip.riverfishing.leader_protection"] = "Bite-through protection: %s%%"
        $o["tooltip.riverfishing.leader_stealth"] = "Stealth: %s%%"
        $o["rig.riverfishing.role.hook"] = "Hook"
        $o["rig.riverfishing.role.bait"] = "Bait"
        $o["rig.riverfishing.role.groundbait"] = "Groundbait"
        $o["rig.riverfishing.role.float"] = "Float"
        $o["rig.riverfishing.role.leader"] = "Leader"
        $o["rig.riverfishing.role.lure"] = "Lure"
        $o["validation.riverfishing.reel_none"] = "This blank has no reel seat"
        $o["validation.riverfishing.reel_size"] = "Wrong reel size for this blank"
        $o["validation.riverfishing.line_reel"] = "Line too thick for this reel"
        $o["validation.riverfishing.reel_line"] = "Reel too small for the fitted line"
        $o["validation.riverfishing.line_no_reel"] = "Fit a reel first - line spools onto the reel"
        $o["tooltip.riverfishing.hook_size"] = "Size: No.%s"
        $o["tooltip.riverfishing.bait_natural"] = "Natural bait"
        $o["tooltip.riverfishing.bait_artificial"] = "Artificial lure (predators only)"
        $o["tooltip.riverfishing.groundbait_use"] = "Right-click water to feed a spot"
        $o["tooltip.riverfishing.fish_length"] = "Length: %s cm"
        $o["tooltip.riverfishing.fish_foulhooked"] = "Foul-hooked — not counted"
        $o["tooltip.riverfishing.rod_assembled"] = "Rod assembled"
        $o["tooltip.riverfishing.rod_unassembled"] = "Not assembled (needs line and rig)"
        $o["message.riverfishing.rod_overload_break"] = "The blank snapped — the rig is far too heavy!"
        $o["message.riverfishing.rod_overload_crack"] = "The blank cracked under the weight - lost a third of its durability!"
        $o["message.riverfishing.rod_overloaded"] = "Rig too heavy for this blank — risk of breakage"
        $o["tooltip.riverfishing.rod_hint"] = "Sneak + right-click to assemble. Right-click water to cast."
        $o["message.riverfishing.not_assembled"] = "The rod is not assembled"
        $o["message.riverfishing.no_water"] = "No water to cast into"
        $o["message.riverfishing.too_narrow"] = "Water too narrow for a long cast"
        $o["message.riverfishing.no_bait"] = "No bait on the hook"
        $o["message.riverfishing.no_bites_here"] = "Nothing is biting here right now"
        $o["message.riverfishing.depleted"] = "This spot is fished out — move on"
        $o["message.riverfishing.cast_out"] = "Cast out. Wait for a bite…"
        $o["message.riverfishing.ice_fishing"] = "Ice fishing - jig the mormyshka (right-click)"
        $o["message.riverfishing.jig"] = "Jigging the mormyshka…"
        $o["message.riverfishing.jig_good"] = "Steady rhythm - fish are coming!"
        $o["message.riverfishing.need_hole"] = "Drill a hole with an ice auger"
        $o["message.riverfishing.need_winter_rod"] = "Needs an assembled winter rod"
        $o["message.riverfishing.winter_needs_hole"] = "The winter rod only fishes a hole - right-click drilled ice"
        $o["message.riverfishing.auger_hole"] = "Drilled a fishing hole"
        $o["message.riverfishing.auger_no_water"] = "No water under the ice"
        $o["tooltip.riverfishing.ice_auger"] = "Right-click ice over water to drill a hole"
        $o["message.riverfishing.bite"] = "Bite! Strike!"
        $o["message.riverfishing.mistimed"] = "Mistimed! You missed the strike window"
        $o["hud.riverfishing.strike_timing"] = "Strike in the green zone!"
        $o["message.riverfishing.missed"] = "The fish got away…"
        $o["message.riverfishing.reeled_in"] = "Reeled in"
        $o["message.riverfishing.caught"] = "Caught: %s — %s, %s cm"
        $o["message.riverfishing.caught_foul"] = "Foul-hooked: %s — %s, %s cm (not counted)"
        $o["message.riverfishing.snag_free"] = "Snagged! Pulled it free"
        $o["message.riverfishing.snag_lost"] = "Snagged solid — lost the rig"
        $o["message.riverfishing.foul_hooked"] = "Foul-hooked! Snagged by the body — this'll be tough"
        $o["message.riverfishing.caught_multi"] = "Caught: %s ×%s!"
        $o["message.riverfishing.fed_spot"] = "Spot fed"
        $o["message.riverfishing.hooked"] = "Hooked! Reel — but don't over-pull!"
        $o["message.riverfishing.line_break"] = "Snap! Fish and rig lost"
        $o["message.riverfishing.leader_bite_off"] = "Bitten through the line — use a leader!"
        $o["message.riverfishing.fighting"] = "Reeling in — keep it on!"
        $o["message.riverfishing.strike"] = "Strike! Set the hook!"
        $o["message.riverfishing.retrieve_empty"] = "Nothing. Recast."
        $o["message.riverfishing.shake_off"] = "Off! The fish threw the hook"
        $o["message.riverfishing.cast_spin"] = "Cast. Hold right-click to retrieve."
        $o["item.riverfishing.rod_pod_1"] = "Rod Pod (1 slot)"
        $o["item.riverfishing.rod_pod_3"] = "Rod Pod (3 slots)"
        $o["item.riverfishing.bait_trap"] = "Bait Trap"
        $o["block.riverfishing.bait_trap"] = "Bait Trap"
        $o["tooltip.riverfishing.bait_trap"] = "Stand it in water — it gathers live bait over time"
        $o["message.riverfishing.trap_empty"] = "The trap is empty for now"
        $o["entity.minecraft.villager.riverfishing.fisherman"] = "Fisherman"
        $o["entity.riverfishing.villager.fisherman"] = "Fisherman"
        $o["item.riverfishing.fishing_stall"] = "Fishing Stall"
        $o["item.riverfishing.trophy_stand"] = "Trophy Stand"
        $o["item.riverfishing.ice_hole"] = "Drilled Ice Hole"
        $o["block.riverfishing.ice_hole"] = "Drilled Ice Hole"
        # Block display names (BlockItems use block.* keys, not item.*)
        $o["block.riverfishing.rod_pod_1"] = "Rod Pod (1 slot)"
        $o["block.riverfishing.rod_pod_3"] = "Rod Pod (3 slots)"
        $o["block.riverfishing.fishing_stall"] = "Fishing Stall"
        $o["block.riverfishing.trophy_stand"] = "Trophy Stand"
        $o["block.riverfishing.aquarium"] = "Aquarium"
        $o["message.riverfishing.pod_wrong_rod"] = "Only bottom rods go on the pod"
        $o["message.riverfishing.pod_full"] = "No free pod slots"
        $o["message.riverfishing.pod_cast_first"] = "Cast the rig first"
        $o["message.riverfishing.pod_docked"] = "Rod on the pod"
        $o["message.riverfishing.pod_taken"] = "Took the rod"
        $o["message.riverfishing.pod_phantom"] = "Nothing — false alarm"
        $o["message.riverfishing.pod_self_hooked"] = "The fish hooked itself — bring it in!"
        $o["tooltip.riverfishing.alarm_use"] = "Right-click a pod with a rod to attach"
        $o["tooltip.riverfishing.knife_use"] = "Right-click to fillet the fish in your other hand"
        $o["tooltip.riverfishing.whetstone_use"] = "Right-click to sharpen the hook in your other hand"
        $o["tooltip.riverfishing.journal_use"] = "Right-click to read your records"
        $o["journal.riverfishing.header"] = "— Fishing Journal —"
        $o["journal.riverfishing.empty"] = "Nothing caught yet"
        $o["journal.riverfishing.total"] = "Total caught: %s | Species: %s"
        $o["journal.riverfishing.tab_fish"] = "Fish"
        $o["journal.riverfishing.tab_bait"] = "Baits & Lures"
        $o["journal.riverfishing.tab_bait_hint"] = "Baits & lures — what they catch"
        $o["journal.riverfishing.bait_natural"] = "Natural bait"
        $o["journal.riverfishing.bait_artificial"] = "Lure"
        $o["journal.riverfishing.bait_groundbait"] = "Groundbait"
        $o["journal.riverfishing.bait_catches"] = "Catches:"
        $o["journal.riverfishing.bait_attracts"] = "Attracts:"
        $o["journal.riverfishing.sec_natural"] = "Natural baits"
        $o["journal.riverfishing.sec_lure"] = "Lures"
        $o["journal.riverfishing.sec_groundbait"] = "Groundbait"
        $o["journal.riverfishing.tab_gear"] = "Gear"
        $o["journal.riverfishing.sec_rod"] = "Rods"
        $o["journal.riverfishing.sec_reel"] = "Reels"
        $o["journal.riverfishing.sec_line"] = "Lines"
        $o["journal.riverfishing.sec_rig"] = "Rigs"
        $o["journal.riverfishing.obtain_craft"] = "How to get - craft:"
        $o["journal.riverfishing.compat_reel"] = "Reel:"
        $o["journal.riverfishing.compat_no_reel"] = "no reel"
        $o["journal.riverfishing.compat_rods"] = "Rods:"
        $o["journal.riverfishing.compat_line"] = "Line, mm:"
        $o["journal.riverfishing.compat_reel_from"] = "Reel from:"
        $o["journal.riverfishing.obtain_other"] = "Found in the world or from the fisherman"
        # Angler quests
        $o["journal.riverfishing.tab_quest"] = "Quests"
        $o["message.riverfishing.quest_done"] = "Reward claimed: %s"
        $o["message.riverfishing.quest_ready"] = "Quest complete: %s - the reward is waiting in the journal!"
        $o["quest.riverfishing.claim"] = "Claim!"
        $o["quest.riverfishing.claim_hint"] = "Click to claim the reward"
        $o["quest.riverfishing.reward"] = "Reward: %s"
        $o["quest.riverfishing.stage.1"] = "Stage 1 - Beginner"
        $o["quest.riverfishing.stage.2"] = "Stage 2 - Float & feeder"
        $o["quest.riverfishing.stage.3"] = "Stage 3 - Predators"
        $o["quest.riverfishing.stage.4"] = "Stage 4 - Heavy tackle"
        $o["quest.riverfishing.stage.5"] = "Stage 5 - Master"
        $o["quest.riverfishing.stage.6"] = "Stage 6 - Under the ice"
        $o["quest.riverfishing.stage_locked"] = "Locked - finish stage %s"
        $o["quest.riverfishing.q_first_fish"] = "Catch your first fish"
        $o["quest.riverfishing.q_species3"] = "Discover 3 species"
        $o["quest.riverfishing.q_crucian"] = "Catch a crucian carp"
        $o["quest.riverfishing.q_bream"] = "Catch a bream"
        $o["quest.riverfishing.q_bream_big"] = "Catch a bream 2+ kg"
        $o["quest.riverfishing.q_species8"] = "Discover 8 species"
        $o["quest.riverfishing.q_perch"] = "Catch a perch"
        $o["quest.riverfishing.q_pike"] = "Catch a pike"
        $o["quest.riverfishing.q_zander"] = "Catch a zander"
        $o["quest.riverfishing.q_asp"] = "Catch an asp"
        $o["quest.riverfishing.q_carp"] = "Catch a carp"
        $o["quest.riverfishing.q_carp_big"] = "Catch a carp 8+ kg"
        $o["quest.riverfishing.q_catfish"] = "Catch a catfish"
        $o["quest.riverfishing.q_trout"] = "Catch a trout"
        $o["quest.riverfishing.q_species15"] = "Discover 15 species"
        $o["quest.riverfishing.q_sterlet"] = "Catch a sterlet"
        $o["quest.riverfishing.q_koi"] = "Catch a koi carp"
        $o["quest.riverfishing.q_trophy"] = "Land a trophy specimen"
        $o["quest.riverfishing.q_master"] = "Reach Master rank (lvl 20)"
        $o["quest.riverfishing.q_roach"] = "Catch a roach"
        $o["quest.riverfishing.q_ten_fish"] = "Catch 10 fish"
        $o["quest.riverfishing.q_rudd"] = "Catch a rudd"
        $o["quest.riverfishing.q_tench"] = "Catch a tench"
        $o["quest.riverfishing.q_pike_big"] = "Catch a pike 5+ kg"
        $o["quest.riverfishing.q_catfish_big"] = "Catch a catfish 20+ kg"
        $o["quest.riverfishing.q_hundred"] = "Catch 100 fish"
        $o["quest.riverfishing.q_grayling"] = "Catch a grayling"
        $o["quest.riverfishing.q_trophy5"] = "Land 5 trophies"
        $o["quest.riverfishing.q_species20"] = "Discover 20 species"
        $o["quest.riverfishing.q_ice_first"] = "Catch your first fish through the ice"
        $o["quest.riverfishing.q_ice_burbot"] = "Catch a burbot"
        $o["quest.riverfishing.q_ice_ruffe"] = "Catch a ruffe"
        $o["quest.riverfishing.q_ice_ten"] = "Catch 10 fish through the ice"
        $o["quest.riverfishing.q_ice_thirty"] = "Catch 30 fish through the ice"
        $o["quest.riverfishing.q_stage1_done"] = "Fully complete stage 1"
        $o["quest.riverfishing.q_stage2_done"] = "Fully complete stage 2"
        $o["quest.riverfishing.q_stage3_done"] = "Fully complete stage 3"
        $o["quest.riverfishing.q_stage4_done"] = "Fully complete stage 4"
        $o["quest.riverfishing.q_stage5_done"] = "Fully complete stage 5"
        $o["quest.riverfishing.q_stage6_done"] = "Fully complete stage 6"
        # Angler skill tree (§skills)
        $o["journal.riverfishing.tab_skill"] = "Skills"
        $o["journal.riverfishing.skill_points"] = "Skill points: %s"
        $o["skill.riverfishing.learned"] = "- skill improved!"
        $o["skill.riverfishing.maxed"] = "max"
        $o["skill.riverfishing.branch.bait"] = "Bait"
        $o["skill.riverfishing.branch.sense"] = "Sense"
        $o["skill.riverfishing.branch.knowledge"] = "Knowledge"
        $o["skill.riverfishing.branch.hand"] = "Hand"
        $o["skill.riverfishing.branch.fortune"] = "Fortune"
        $o["skill.riverfishing.branch.skill"] = "Finesse"
        $o["skill.riverfishing.frugal"] = "Frugal"
        $o["skill.riverfishing.frugal.desc"] = "+5%/rank chance to keep the bait after a bite."
        $o["skill.riverfishing.quick_bite"] = "Keen Sense"
        $o["skill.riverfishing.quick_bite.desc"] = "+5%/rank - bites come sooner."
        $o["skill.riverfishing.naturalist"] = "Naturalist"
        $o["skill.riverfishing.naturalist.desc"] = "+5%/rank to the overall bite chance."
        $o["skill.riverfishing.strong_line"] = "Steady Hand"
        $o["skill.riverfishing.strong_line.desc"] = "+5%/rank - the line holds more tension before it snaps."
        $o["skill.riverfishing.anglers_luck"] = "Angler's Luck"
        $o["skill.riverfishing.anglers_luck.desc"] = "+1%/rank to the trophy chance."
        $o["skill.riverfishing.finesse"] = "Finesse"
        $o["skill.riverfishing.finesse.desc"] = "+1%/rank - a wider strike zone."
        # Fish descriptions
        $o["fishdesc.riverfishing.bream"] = "A deep-bodied shoaling bottom fish. Holds in pits and drop-offs, feeds off the bottom. Feeder or float with groundbait; worm, maggot, grain."
        $o["fishdesc.riverfishing.crucian_carp"] = "A hardy fish of weedy ponds and backwaters. Shy; bites best in warm weather. Float, thin line, dough, worm or grain."
        $o["fishdesc.riverfishing.roach"] = "The most common silver fish around. Bites almost everywhere, year-round. Float with a small hook; maggot, bloodworm, dough."
        $o["fishdesc.riverfishing.rudd"] = "Bright red fins; likes the upper layers near weed. Takes mid-water. Float; dough, maggot, a pinch of bread."
        $o["fishdesc.riverfishing.white_bream"] = "Like a bream but smaller and more silver. Shoaling, bottom-dwelling. Feeder or float; worm, maggot, groundbait."
        $o["fishdesc.riverfishing.carp"] = "A strong, wary fish of warm waters that fights hard. Carp rod, strong line, boilies and corn, and always groundbait."
        $o["fishdesc.riverfishing.catfish"] = "The largest freshwater predator, a nocturnal bottom hunter of deep pits. Ledger with a leader; livebait or big bait, at night and in warmth."
        $o["fishdesc.riverfishing.perch"] = "A striped predator that shoals near snags and drop-offs and bites boldly. Spinning with a small spinner or soft plastic, or float with a worm."
        $o["fishdesc.riverfishing.pike"] = "A toothy ambush predator of reeds and snags. Spinning with wobblers and spoons; always a leader; best in cold water."
        $o["fishdesc.riverfishing.zander"] = "A nocturnal predator of deep drop-offs and sandy bottoms. Bites at dusk and night. Spinning with soft plastic or a wobbler and a leader; livebait."
        $o["fishdesc.riverfishing.gudgeon"] = "A small bottom fish of clean sandy riffles, and great livebait. Float near the bottom, small hook, bloodworm or worm."
        $o["fishdesc.riverfishing.ruffe"] = "A small spiny bottom-dweller, active even at night. Grabs worm and bloodworm off the bottom; often a nuisance when after bigger fish."
        $o["fishdesc.riverfishing.bleak"] = "A tiny shoaling fish of the upper layers, always near the surface. Light float mid-water; maggot, breadcrumb; good livebait."
        $o["fishdesc.riverfishing.ide"] = "A big, strong silver fish, omnivorous and wary. Holds in pits and current. Feeder and float; worm, dough, greens; also takes lures."
        $o["fishdesc.riverfishing.chub"] = "A wary fish of fast rivers, omnivorous and will grab fry. Float and spinning; insects, bread, small spinners."
        $o["fishdesc.riverfishing.asp"] = "A fast open-water river predator that smashes fry at the surface. Spinning, long casts, heavy spoons; strong and wary."
        $o["fishdesc.riverfishing.tench"] = "A dark, slimy fish of weedy warm waters, feeding in the silt. Float and ledger; worm, corn; bites at dawn in warmth."
        $o["fishdesc.riverfishing.burbot"] = "The only freshwater cod, a cold-lover active at night and in winter. Ledger on the bottom; livebait or cut fish; best in late autumn."
        $o["fishdesc.riverfishing.eel"] = "A snake-like nocturnal fish that buries in silt by day. Ledger on the bottom; lobworm or livebait; strong tackle - it fights hard."
        $o["fishdesc.riverfishing.grayling"] = "A handsome fish of cold clear rivers with a tall fin, holding in the current. Light tackle; insects, small spinners; needs cold water."
        $o["fishdesc.riverfishing.trout"] = "A predator of cold fast rivers and streams; wary and strong. Spinning with small spinners and wobblers; clean cold water only."
        $o["fishdesc.riverfishing.sterlet"] = "A small sturgeon with rows of bony scutes, feeding on the bottom in current. Ledger, worm; rare and prized - a collector's trophy."
        $o["fishdesc.riverfishing.wild_carp"] = "The wild ancestor of the carp - a powerful, angry fighter of pits and snags. Carp tackle, the strongest line, boilies and corn; a hard fight."
        $o["fishdesc.riverfishing.mirror_carp"] = "A carp with large, sparse mirror scales. Behaves like a carp, a touch calmer. Carp rod, boilies, corn, groundbait."
        $o["fishdesc.riverfishing.carp_koi_kohaku"] = "An ornamental carp: white body with crimson patches. A collectible caught by chance on carp tackle, more so near cherry blossom. Eating it is silly."
        $o["fishdesc.riverfishing.carp_koi_tancho_sanke"] = "A koi with a single crimson spot on the head, like a tancho crane. A rare collectible; takes carp tackle near cherry groves."
        $o["fishdesc.riverfishing.carp_koi_showa_sanke"] = "A black koi with red and white markings. A collectible caught on carp tackle, more often in cherry biomes."
        $o["fishdesc.riverfishing.carp_koi_asagi"] = "A blue-grey netted back with red flanks. A collectible koi, a chance catch on carp tackle near cherry blossom."
        $o["fishdesc.riverfishing.carp_koi_bekko"] = "A solid-colour koi with black tortoiseshell spots. A rare collectible caught on carp tackle in cherry biomes."
        $o["journal.riverfishing.no_illustration"] = "no illustration yet"
        $o["tooltip.riverfishing.wear"] = "Wear: %s%%"
        $o["tooltip.riverfishing.line_worn_out"] = "Line worn out — may snap at any moment!"
        $o["message.riverfishing.line_worn_out"] = "Line worn out — may snap at any moment!"
        $o["tooltip.riverfishing.hook_dull"] = "Bluntness: %s%%"
        $o["message.riverfishing.empty_set"] = "Empty strike — the hook was blunt"
        $o["message.riverfishing.alarm_attached"] = "Alarm attached"
        $o["message.riverfishing.alarm_needs_rod"] = "Need a rod on the pod without an alarm"
        $o["message.riverfishing.alarm_bite"] = "Bite on the pod!"
        $o["message.riverfishing.alarm_phantom"] = "Signal… (maybe a phantom)"
        # Angler progression (XP / levels / ranks)
        $o["journal.riverfishing.angler"] = "Angler: lvl %s (%s) — %s XP, next in %s"
        $o["message.riverfishing.xp_gained"] = "+%s Angler XP"
        $o["message.riverfishing.new_species"] = "New species in your journal!"
        $o["message.riverfishing.level_up"] = "Angler level %s!"
        $o["message.riverfishing.rank_up"] = "New rank: %s!"
        $o["rank.riverfishing.bronze"] = "Bronze"
        $o["rank.riverfishing.silver"] = "Silver"
        $o["rank.riverfishing.gold"] = "Gold"
        $o["rank.riverfishing.master"] = "Master"
        # JEI category
        $o["jei.riverfishing.category"] = "Fishing"
        $o["jei.riverfishing.no_data"] = "Enter a world to see data"
        $o["jei.riverfishing.bait"] = "Bait:"
        $o["jei.riverfishing.tackle"] = "Tackle:"
        $o["jei.riverfishing.best"] = "Best:"
        $o["jei.riverfishing.water"] = "Water:"
        $o["jei.riverfishing.level"] = "Recommended level: %s"
        $o["jei.riverfishing.groundbait"] = "Groundbait:"
        $o["item.riverfishing.worm_farm"] = "Worm Farm"
        $o["block.riverfishing.worm_farm"] = "Worm Farm"
        $o["message.riverfishing.farm_empty"] = "No worms bred yet"
        $o["item.riverfishing.maggot_farm"] = "Maggot Farm"
        $o["block.riverfishing.maggot_farm"] = "Maggot Farm"
        $o["message.riverfishing.maggot_farm_fill"] = "Rotten flesh loaded: %s"
        $o["message.riverfishing.maggot_farm_full"] = "The farm is packed full of flesh"
        $o["message.riverfishing.maggot_farm_empty"] = "No maggots bred yet"
        # Living-fishing wave: trophies, final surge, frenzy, bycatch
        $o["message.riverfishing.trophy_catch"] = "A trophy specimen!"
        $o["tooltip.riverfishing.fish_trophy"] = "★ Trophy"
        $o["tooltip.riverfishing.fish_prime"] = "Prime specimen — the buyer wants this"
        $o["tooltip.riverfishing.trade_min_weight"] = "Accepts: from %s"
        $o["trade.riverfishing.assembled"] = "%s (rigged)"
        $o["message.riverfishing.final_surge"] = "A last dash at the bank!"
        $o["message.riverfishing.cast_frenzy"] = "Feeding frenzy! The water is boiling"
        $o["message.riverfishing.junk_catch"] = "Dredged up junk: %s"
        $o["message.riverfishing.treasure_catch"] = "Surprise haul: %s!"
        # QoL: pole limit, fish finder, admin probe
        $o["message.riverfishing.pole_too_far"] = "A pole cannot reach past 6 blocks"
        $o["finder.riverfishing.header"] = "Fish finder — biting here right now:"
        $o["finder.riverfishing.none"] = "The finder is silent — nothing here now"
        $o["finder.riverfishing.pressure"] = "Pressure: %s hPa %s  —  bite:"
        $o["finder.riverfishing.outlook.great"] = "frenzy"
        $o["finder.riverfishing.outlook.good"] = "active"
        $o["finder.riverfishing.outlook.fair"] = "fair"
        $o["finder.riverfishing.outlook.poor"] = "sluggish"
        $o["tooltip.riverfishing.fish_finder"] = "Right-click water to scan for active fish"
        $o["tooltip.riverfishing.hydro_probe"] = "Right-click water for the full summary (admin)"
        # Float QTE + in-journal guide
        $o["message.riverfishing.qte_start"] = "Hold her! Strike in the green zone!"
        $o["guide.riverfishing.hint_open"] = "Click — how to catch"
        $o["guide.riverfishing.back"] = "Click — back to the list"
        $o["guide.riverfishing.anywhere"] = "any"
        $o["guide.riverfishing.water"] = "Water:"
        $o["guide.riverfishing.depth"] = "Depth:"
        $o["guide.riverfishing.size"] = "Width:"
        $o["guide.riverfishing.biomes"] = "Biomes:"
        $o["guide.riverfishing.best"] = "Best:"
        $o["guide.riverfishing.bait"] = "Bait:"
        $o["guide.riverfishing.tackle"] = "Tackle:"
        $o["water.riverfishing.river"] = "river"
        $o["water.riverfishing.lake"] = "lake"
        $o["water.riverfishing.pond"] = "pond"
        $o["water.riverfishing.swamp"] = "swamp"
        $o["water.riverfishing.sea"] = "sea"
        $o["water.riverfishing.puddle"] = "puddle"
        $o["season.riverfishing.spring"] = "spring"
        $o["season.riverfishing.summer"] = "summer"
        $o["season.riverfishing.autumn"] = "autumn"
        $o["season.riverfishing.winter"] = "winter"
        $o["time.riverfishing.dawn"] = "dawn"
        $o["time.riverfishing.day"] = "day"
        $o["time.riverfishing.dusk"] = "dusk"
        $o["time.riverfishing.night"] = "night"
        $o["biomegroup.riverfishing.cold"] = "cold"
        $o["biomegroup.riverfishing.temperate"] = "temperate"
        $o["biomegroup.riverfishing.warm"] = "warm"
        $o["biomegroup.riverfishing.swamp"] = "swamps"
        $o["biomegroup.riverfishing.taiga"] = "taiga"
        $o["biomegroup.riverfishing.mountain"] = "mountains"
        $o["biomegroup.riverfishing.jungle"] = "jungle"
        $o["biomegroup.riverfishing.forest"] = "forests"
        $o["biomegroup.riverfishing.river_biome"] = "river biomes"
        $o["biomegroup.riverfishing.ocean_biome"] = "ocean"
        $o["biomegroup.riverfishing.beach"] = "coast"
        $o["biomegroup.riverfishing.dry"] = "arid"
        # Float depth + rod test
        $o["message.riverfishing.float_depth"] = "Float depth: %s"
        $o["tooltip.riverfishing.float_depth"] = "Depth: %s"
        $o["tooltip.riverfishing.float_use"] = "Right-click to change depth (before loading into a rig)"
        $o["depthset.riverfishing.surface"] = "near surface"
        $o["depthset.riverfishing.mid"] = "mid-water"
        $o["depthset.riverfishing.bottom"] = "near bottom"
        $o["tooltip.riverfishing.rod_test"] = "Test: %s–%s g"
        $o["message.riverfishing.rod_underloaded"] = "The rig is too light for this blank — short throw"
        $o["guide.riverfishing.depth_short"] = "Depth"
        # Advancements
        $o["advancement.riverfishing.root.title"] = "River Fishing"
        $o["advancement.riverfishing.root.description"] = "Catch your first fish"
        $o["advancement.riverfishing.trophy.title"] = "A Trophy Specimen"
        $o["advancement.riverfishing.trophy.description"] = "Catch a trophy-sized fish"
        $o["advancement.riverfishing.predator.title"] = "Toothy"
        $o["advancement.riverfishing.predator.description"] = "Catch a pike on a lure"
        $o["advancement.riverfishing.giant.title"] = "Master of the Hole"
        $o["advancement.riverfishing.giant.description"] = "Pull a catfish out of a deep hole"
        $o["advancement.riverfishing.sterlet.title"] = "The Tsar Fish"
        $o["advancement.riverfishing.sterlet.description"] = "Catch a sterlet — the rarest fish of the big rivers"
        $o["advancement.riverfishing.night_king.title"] = "King of the Winter Night"
        $o["advancement.riverfishing.night_king.description"] = "Catch a burbot — it only bites on cold nights"
        $o["advancement.riverfishing.master.title"] = "Master Angler"
        $o["advancement.riverfishing.master.description"] = "Reach the Master rank"
        $o["advancement.riverfishing.all_species.title"] = "The Full Bestiary"
        $o["advancement.riverfishing.all_species.description"] = "Catch all 24 fish species (koi don't count)"
        $o["advancement.riverfishing.sazan.title"] = "River Brawler"
        $o["advancement.riverfishing.sazan.description"] = "Best a wild carp — the fiercest fighter of the carp family"
        $o["advancement.riverfishing.koi.title"] = "A Living Jewel"
        $o["advancement.riverfishing.koi.description"] = "Catch a koi carp — the rarest find on carp tackle"
        $o["advancement.riverfishing.koi_master.title"] = "Koi Collector"
        $o["advancement.riverfishing.koi_master.description"] = "Collect all five koi: Kohaku, Tancho, Showa, Asagi and Bekko"
        $o["message.riverfishing.koi_filleted"] = "you seriously filleted it?"
        # Code-driven advancements (§challenges)
        $o["advancement.riverfishing.pike_on_wood.title"] = "The Old Way"
        $o["advancement.riverfishing.pike_on_wood.description"] = "Land a 4+ kg pike on live bait with a plain wooden rod"
        $o["advancement.riverfishing.ice_burbot.title"] = "From Under the Ice"
        $o["advancement.riverfishing.ice_burbot.description"] = "Pull a burbot through a hole with the winter rod"
        $o["advancement.riverfishing.trophy_on_pole.title"] = "Bare-Handed"
        $o["advancement.riverfishing.trophy_on_pole.description"] = "Land a trophy on a reel-less pole — just nerve"
        $o["advancement.riverfishing.frenzy_feast.title"] = "Feeding Time"
        $o["advancement.riverfishing.frenzy_feast.description"] = "Land a fish during a feeding frenzy"
        $o["advancement.riverfishing.old_boot.title"] = "Catch of the Decade"
        $o["advancement.riverfishing.old_boot.description"] = "Fish up an old boot"
        $o["advancement.riverfishing.koi_fillet.title"] = "Heartless Cook"
        $o["advancement.riverfishing.koi_fillet.description"] = "Fillet a koi carp. But why?.."
    }
    return $o
}

$enJson = (Build-Lang "en") | ConvertTo-Json -Depth 4
$ruJson = (Build-Lang "ru") | ConvertTo-Json -Depth 4
Write-Utf8NoBom (Join-Path $langDir "en_us.json") $enJson
Write-Utf8NoBom (Join-Path $langDir "ru_ru.json") $ruJson

Write-Output "Generated $($items.Count) item models, $($catColors.Count) textures, and 2 lang files."
