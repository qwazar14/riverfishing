# Generates the mod's themed GUI textures, Stardew-style: a dark-outlined wood frame with rounded
# corners and a brass keyline over warm parchment, plus a sunken leather-wood slot.
# ASCII-only so PowerShell 5.1 parses it without a BOM.
Add-Type -AssemblyName System.Drawing

$guiDir = "C:\Users\Qwazar\VS Code Projects\fishing mod\src\main\resources\assets\riverfishing\textures\gui"
New-Item -ItemType Directory -Force -Path $guiDir | Out-Null

function C([int]$r,[int]$g,[int]$b) { return [System.Drawing.Color]::FromArgb(255,$r,$g,$b) }
$CLEAR = [System.Drawing.Color]::FromArgb(0,0,0,0)

# ---- panel.png : 64x64, 8px border for 9-slicing ----
$P = 64; $B = 8
$bmp = New-Object System.Drawing.Bitmap($P,$P,[System.Drawing.Imaging.PixelFormat]::Format32bppArgb)

$outline  = C 46 28 16
$woodBase = C 122 84 48
$woodDark = C 86 58 32
$woodLite = C 158 116 70
$brass    = C 196 156 66
$brassHi  = C 228 196 104
$parch    = C 240 226 192
$parchSh  = C 219 202 164
$parchHi  = C 250 240 212

for ($y=0; $y -lt $P; $y++) {
  for ($x=0; $x -lt $P; $x++) {
    $inBorder = ($x -lt $B -or $x -ge ($P-$B) -or $y -lt $B -or $y -ge ($P-$B))
    if ($inBorder) {
      $c = $woodBase
      if ((($y % 4) -eq 0)) { $c = $woodDark }
      elseif ((($y % 4) -eq 1)) { $c = $woodLite }
      $bmp.SetPixel($x,$y,$c)
    } else {
      $c = $parch
      if ((($x + $y) % 9) -eq 0) { $c = $parchSh }
      $bmp.SetPixel($x,$y,$c)
    }
  }
}

# outer dark outline ring (Stardew chunky border)
for ($i=0; $i -lt $P; $i++) {
  $bmp.SetPixel($i,0,$outline); $bmp.SetPixel(0,$i,$outline)
  $bmp.SetPixel($i,$P-1,$outline); $bmp.SetPixel($P-1,$i,$outline)
}
# inner light bevel just inside the outline (top/left) and shade (bottom/right)
for ($i=1; $i -lt ($P-1); $i++) {
  $bmp.SetPixel($i,1,$woodLite); $bmp.SetPixel(1,$i,$woodLite)
  $bmp.SetPixel($i,$P-2,$woodDark); $bmp.SetPixel($P-2,$i,$woodDark)
}

# rounded corners: clear a 3-2-1 step and re-outline the curve
foreach ($cr in @(@(0,0,1,1),@(1,0,-1,1),@(0,1,1,-1),@(1,1,-1,-1))) {
  $bx = $cr[0]*($P-1); $by = $cr[1]*($P-1); $dx = $cr[2]; $dy = $cr[3]
  # clear steps
  foreach ($p in @(@(0,0),@(1,0),@(2,0),@(0,1),@(0,2),@(1,1))) {
    $bmp.SetPixel($bx+$dx*$p[0], $by+$dy*$p[1], $CLEAR)
  }
  # curved outline
  foreach ($p in @(@(3,0),@(2,1),@(1,2),@(0,3))) {
    $bmp.SetPixel($bx+$dx*$p[0], $by+$dy*$p[1], $outline)
  }
}

# brass keyline framing the parchment
$k0 = $B-1; $k1 = $P-$B
for ($i=$k0; $i -le $k1; $i++) {
  $bmp.SetPixel($i,$k0,$brassHi); $bmp.SetPixel($k0,$i,$brassHi)
  $bmp.SetPixel($i,$k1,$brass);   $bmp.SetPixel($k1,$i,$brass)
}

# parchment inner shadow (top/left) + highlight (bottom/right)
for ($i=$B; $i -lt ($P-$B); $i++) {
  $bmp.SetPixel($i,$B,$parchSh);      $bmp.SetPixel($B,$i,$parchSh)
  $bmp.SetPixel($i,$P-$B-1,$parchHi); $bmp.SetPixel($P-$B-1,$i,$parchHi)
}

$bmp.Save("$guiDir\panel.png",[System.Drawing.Imaging.ImageFormat]::Png)
$bmp.Dispose()

# ---- slot.png : 18x18 sunken leather-wood slot with rounded outline ----
$S = 18
$slot = New-Object System.Drawing.Bitmap($S,$S,[System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$frame = C 46 28 16
$fill  = C 104 82 56
$shad  = C 74 56 36
$hi    = C 136 110 76

for ($y=0; $y -lt $S; $y++) {
  for ($x=0; $x -lt $S; $x++) {
    if ($x -eq 0 -or $y -eq 0 -or $x -eq ($S-1) -or $y -eq ($S-1)) { $slot.SetPixel($x,$y,$frame) }
    else { $slot.SetPixel($x,$y,$fill) }
  }
}
for ($i=1; $i -lt ($S-1); $i++) {
  $slot.SetPixel($i,1,$shad); $slot.SetPixel(1,$i,$shad)
  $slot.SetPixel($i,$S-2,$hi); $slot.SetPixel($S-2,$i,$hi)
}
# round the slot corners
foreach ($cr in @(@(0,0,1,1),@(1,0,-1,1),@(0,1,1,-1),@(1,1,-1,-1))) {
  $bx = $cr[0]*($S-1); $by = $cr[1]*($S-1); $dx = $cr[2]; $dy = $cr[3]
  $slot.SetPixel($bx, $by, $CLEAR)
  $slot.SetPixel($bx+$dx, $by, $frame)
  $slot.SetPixel($bx, $by+$dy, $frame)
}
$slot.Save("$guiDir\slot.png",[System.Drawing.Imaging.ImageFormat]::Png)
$slot.Dispose()

Write-Host "GUI textures written to $guiDir"
Get-ChildItem $guiDir | Select-Object Name, Length
