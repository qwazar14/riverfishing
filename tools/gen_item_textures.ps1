# Draws a cohesive 16x16 icon set for every item (warm palette + 1px outline), dispatched by filename.
# ASCII-only. Overwrites the flat placeholders; re-run any time. Fish get per-species tints/shapes.
Add-Type -AssemblyName System.Drawing

$itemDir = "C:\Users\Qwazar\VS Code Projects\fishing mod\src\main\resources\assets\riverfishing\textures\item"
$fishDir = "$itemDir\fish"

function NewImg { return New-Object System.Drawing.Bitmap(16,16,[System.Drawing.Imaging.PixelFormat]::Format32bppArgb) }
function Save($b,$p){ $b.Save($p,[System.Drawing.Imaging.ImageFormat]::Png); $b.Dispose() }
function Hex($h){ return @([Convert]::ToInt32($h.Substring(0,2),16),[Convert]::ToInt32($h.Substring(2,2),16),[Convert]::ToInt32($h.Substring(4,2),16)) }
function Op($b,$x,$y,$h){ if($x -ge 0 -and $x -lt 16 -and $y -ge 0 -and $y -lt 16){ $c=Hex $h; $b.SetPixel($x,$y,[System.Drawing.Color]::FromArgb(255,$c[0],$c[1],$c[2])) } }
function Rect($b,$x0,$y0,$x1,$y1,$h){ for($y=$y0;$y -le $y1;$y++){ for($x=$x0;$x -le $x1;$x++){ Op $b $x $y $h } } }
function Disc($b,$cx,$cy,$r,$h){ for($y=$cy-$r;$y -le $cy+$r;$y++){ for($x=$cx-$r;$x -le $cx+$r;$x++){ if((($x-$cx)*($x-$cx)+($y-$cy)*($y-$cy)) -le ($r*$r)){ Op $b $x $y $h } } } }
function Ln($b,$x0,$y0,$x1,$y1,$h){
  $dx=[math]::Abs($x1-$x0); $dy=-[math]::Abs($y1-$y0)
  $sx= if($x0 -lt $x1){1}else{-1}; $sy= if($y0 -lt $y1){1}else{-1}; $err=$dx+$dy
  while($true){ Op $b $x0 $y0 $h; if(($x0 -eq $x1) -and ($y0 -eq $y1)){break}; $e2=2*$err
    if($e2 -ge $dy){$err+=$dy;$x0+=$sx}; if($e2 -le $dx){$err+=$dx;$y0+=$sy} } }

$OL="2A1E12"; $WOOD="6B4E2E"; $WOODD="4A3620"; $WOODL="8A6A42"

# Stardew-style finishing pass: grow a warm dark outline around the sprite, then add a top rim-light
# and a bottom rim-shade so every icon reads as chunky and hand-shaded.
function Stylize($b){
  $a = New-Object 'int[,]' 16,16
  for($y=0;$y -lt 16;$y++){ for($x=0;$x -lt 16;$x++){ $a[$x,$y] = $b.GetPixel($x,$y).A } }
  # 1) outline: transparent pixels touching the sprite become warm dark outline
  for($y=0;$y -lt 16;$y++){ for($x=0;$x -lt 16;$x++){
    if($a[$x,$y] -lt 40){
      $adj=$false
      if($x -gt 0  -and $a[($x-1),$y] -ge 40){$adj=$true}
      if($x -lt 15 -and $a[($x+1),$y] -ge 40){$adj=$true}
      if($y -gt 0  -and $a[$x,($y-1)] -ge 40){$adj=$true}
      if($y -lt 15 -and $a[$x,($y+1)] -ge 40){$adj=$true}
      if($adj){ $b.SetPixel($x,$y,[System.Drawing.Color]::FromArgb(255,46,28,16)) }
    }
  } }
  # 2) rim light (top edge) / rim shade (bottom edge) on the original sprite pixels
  for($y=0;$y -lt 16;$y++){ for($x=0;$x -lt 16;$x++){
    if($a[$x,$y] -ge 40){
      $c = $b.GetPixel($x,$y)
      $topOpen = ($y -eq 0) -or ($a[$x,($y-1)] -lt 40)
      $botOpen = ($y -eq 15) -or ($a[$x,($y+1)] -lt 40)
      if($topOpen){
        $r=[int][Math]::Min(255,$c.R*1.25+18); $g=[int][Math]::Min(255,$c.G*1.25+18); $bl=[int][Math]::Min(255,$c.B*1.25+18)
        $b.SetPixel($x,$y,[System.Drawing.Color]::FromArgb(255,$r,$g,$bl))
      } elseif($botOpen){
        $b.SetPixel($x,$y,[System.Drawing.Color]::FromArgb(255,[int]($c.R*0.62),[int]($c.G*0.62),[int]($c.B*0.62)))
      }
    }
  } }
}
$STEEL="9AA3AD"; $STEELD="5C636B"; $STEELL="C9D0D8"
$BRASS="C8A24A"; $BRASSD="8A6E28"; $BRASSL="E6C868"
$LINEC="D8D2C0"; $RED="C0392B"; $REDL="E05A4A"; $WHITE="ECECE2"

# ---------- drawers ----------
function DrawRod($b,$shaft){
  Ln $b 3 14 14 2 $OL; Ln $b 4 14 15 2 $OL          # outline
  Ln $b 3 13 13 2 $shaft; Ln $b 4 13 14 2 $shaft     # shaft
  Rect $b 2 12 5 14 $WOODD; Op $b 2 12 $WOODL; Op $b 3 12 $WOODL   # cork handle
  Disc $b 5 11 1 $STEELD; Op $b 5 11 $STEELL          # reel seat
  Ln $b 13 2 13 7 $LINEC; Op $b 13 8 $OL; Op $b 12 8 $OL  # line + hook
}
function DrawReel($b){
  Disc $b 8 9 5 $OL; Disc $b 8 9 4 $STEEL; Disc $b 8 9 2 $STEELD
  Op $b 6 7 $STEELL; Op $b 7 6 $STEELL; Op $b 8 9 $STEELL
  Rect $b 11 3 13 4 $STEELD; Op $b 13 3 $BRASS         # handle knob
  Rect $b 7 3 9 4 $WOODD                                 # foot
}
function DrawLine($b,$tint){
  Rect $b 4 3 11 4 $OL; Rect $b 4 11 11 12 $OL          # flanges
  Rect $b 4 4 11 5 $STEELD; Rect $b 4 10 11 11 $STEELD
  Rect $b 5 5 10 10 $tint
  Ln $b 5 6 10 6 $OL; Ln $b 5 8 10 8 $OL                # wound line
}
function DrawHook($b,$col){
  Ln $b 9 3 9 9 $OL; Op $b 8 3 $OL; Op $b 10 3 $OL      # shank + eye
  Ln $b 9 9 7 11 $OL; Op $b 6 11 $OL; Op $b 6 10 $OL; Op $b 7 9 $OL  # bend + barb
  Op $b 9 4 $col; Op $b 9 6 $col; Op $b 9 8 $col; Op $b 7 10 $col
}
function DrawRig($b){
  Ln $b 8 2 8 13 $LINEC                                  # main line
  Disc $b 8 3 1 $BRASS; Op $b 8 3 $BRASSL                # swivel
  Disc $b 8 7 2 $OL; Disc $b 8 7 1 $STEELD               # weight
  DrawHook $b $STEELL
}
function DrawLeader($b,$tint){
  Ln $b 3 8 12 8 $OL
  for($x=3;$x -le 12;$x+=1){ $yy= if(($x % 2) -eq 0){7}else{9}; Op $b $x $yy $tint }
  Disc $b 3 8 1 $BRASS; Disc $b 12 8 1 $BRASS            # clasps
}
function DrawFloat($b){
  Ln $b 8 1 8 4 $RED                                     # antenna
  Disc $b 8 8 3 $OL; Disc $b 8 7 2 $RED; Disc $b 8 9 2 $WHITE
  Op $b 7 6 $REDL; Ln $b 8 11 8 13 $WOODD                # stem
}
function DrawBell($b){
  Op $b 8 3 $BRASSD                                      # loop
  Rect $b 6 5 10 9 $OL
  Rect $b 6 5 10 9 $BRASS; Op $b 6 5 $BRASSL; Op $b 7 6 $BRASSL
  Rect $b 5 9 11 10 $BRASSD; Op $b 8 12 $OL              # rim + clapper
}
function DrawDigital($b){
  Rect $b 3 4 12 12 $OL; Rect $b 4 5 11 11 $STEELD
  Rect $b 5 6 10 8 "3A6B3A"; Op $b 6 7 "7CE07C"; Op $b 8 7 "7CE07C"  # screen
  Op $b 10 10 $RED; Op $b 5 10 $STEELL                   # led + button
}
function DrawKnife($b){
  Ln $b 5 12 12 4 $OL; Ln $b 6 12 13 4 $OL
  Ln $b 6 11 12 5 $STEELL; Ln $b 7 12 13 5 $STEEL        # blade
  Rect $b 3 11 6 14 $WOODD; Op $b 3 11 $WOODL            # handle
}
function DrawWhetstone($b){
  Rect $b 2 10 13 13 $WOODD                              # base
  Rect $b 3 6 12 10 $OL; Rect $b 4 7 11 9 "8C8C86"; Op $b 5 7 "B0B0AA"; Op $b 9 8 "6E6E68"
}
function DrawBook($b){
  Rect $b 3 2 12 14 $OL; Rect $b 4 3 11 13 "7A4A2A"     # cover
  Rect $b 4 3 5 13 $WOODD; Rect $b 11 3 11 13 $LINEC     # spine + pages
  Op $b 8 7 $BRASSL; Op $b 8 8 $BRASSL; Op $b 7 8 $BRASSL; Op $b 9 8 $BRASSL  # emblem
  Op $b 10 2 $RED; Op $b 10 3 $RED                       # bookmark
}
function DrawFillet($b,$base,$edge){
  Disc $b 8 8 5 $OL
  for($y=4;$y -le 12;$y++){ for($x=4;$x -le 12;$x++){ if((($x-8)*($x-8)+($y-8)*($y-8)) -le 20){ Op $b $x $y $base } } }
  Ln $b 5 8 11 8 $edge; Ln $b 6 6 10 6 $edge; Ln $b 6 10 10 10 $edge
}
function DrawBall($b,$base,$hi){
  Disc $b 8 9 5 $OL; Disc $b 8 9 4 $base; Op $b 6 7 $hi; Op $b 7 7 $hi; Op $b 6 8 $hi
}
function DrawWorm($b,$col,$dark){
  Op $b 3 6 $OL;Op $b 4 5 $OL;Op $b 5 5 $OL;Op $b 6 6 $OL;Op $b 7 7 $OL;Op $b 8 8 $OL;Op $b 9 9 $OL;Op $b 10 10 $OL;Op $b 11 10 $OL;Op $b 12 9 $OL
  Op $b 4 6 $col;Op $b 5 6 $col;Op $b 6 7 $col;Op $b 7 8 $col;Op $b 8 9 $col;Op $b 9 10 $col;Op $b 10 11 $col;Op $b 11 11 $col
  Op $b 5 7 $dark; Op $b 8 10 $dark
}
function DrawGrain($b,$col,$hi){
  foreach($p in @(@(5,6),@(8,5),@(10,8),@(6,10),@(9,11))){ Disc $b $p[0] $p[1] 1 $OL; Op $b $p[0] $p[1] $col; Op $b ($p[0]-1) ($p[1]-1) $hi }
}
function DrawGroundbait($b,$base){
  Disc $b 8 10 5 $OL
  for($y=6;$y -le 13;$y++){ for($x=3;$x -le 13;$x++){ if((($x-8)*($x-8)+($y-11)*($y-11)) -le 20){ Op $b $x $y $base } } }
  Op $b 6 9 $WOODL; Op $b 10 10 $WOODL; Op $b 8 8 "8A6A42"; Op $b 7 12 $WOODD; Op $b 11 11 $WOODD
}
function DrawSpoon($b){
  Disc $b 7 6 3 $OL; Disc $b 7 6 2 $STEELL; Op $b 6 5 $WHITE
  Ln $b 8 8 10 11 $STEELD; DrawHook $b $STEEL
}
function DrawSpinner($b){
  Ln $b 8 2 8 12 $STEELD; Disc $b 6 6 2 $OL; Disc $b 6 6 1 $BRASSL   # blade
  Op $b 8 3 $BRASS; DrawHook $b $STEEL
}
function DrawWobbler($b){
  for($x=4;$x -le 11;$x++){ for($y=6;$y -le 10;$y++){ if((($x-8)*($x-8)/9.0+($y-8)*($y-8)/4.0) -le 1){ Op $b $x $y "3A7A6A" } } }
  Disc $b 8 8 3 $OL; for($x=5;$x -le 11;$x++){ for($y=6;$y -le 10;$y++){ if((($x-8)*($x-8)/9.0+($y-8)*($y-8)/4.0) -le 0.9){ Op $b $x $y "3A7A6A" } } }
  Op $b 5 8 $WHITE; Op $b 6 7 $REDL; Ln $b 11 8 13 9 $STEELD  # eye/lip
  Op $b 6 11 $OL; Op $b 10 11 $OL                             # trebles
}
function DrawSilicone($b){ DrawWorm $b "8A5AA0" "5A3A70" }
function DrawLivebait($b){ DrawFish $b "B9C0C4" "8A8A80" 4 2 "" }

function DrawFish($b,$base,$fin,$hw,$hh,$mark){
  $cx=8; $cy=8
  for($y=$cy-$hh;$y -le $cy+$hh;$y++){ for($x=$cx-$hw;$x -le $cx+$hw;$x++){
    $nx=($x-$cx)/[double]$hw; $ny=($y-$cy)/[double]$hh; $d=$nx*$nx+$ny*$ny
    if($d -le 1.02){ if($d -ge 0.72){ Op $b $x $y $OL } else { Op $b $x $y $base } } } }
  # tail (left)
  Op $b ($cx-$hw) $cy $fin; Op $b ($cx-$hw-1) ($cy-2) $fin; Op $b ($cx-$hw-1) ($cy+2) $fin; Op $b ($cx-$hw-1) $cy $OL
  Ln $b ($cx-$hw-1) ($cy-2) ($cx-$hw-1) ($cy+2) $fin
  # dorsal fin (top)
  Ln $b ($cx-1) ($cy-$hh-1) ($cx+2) ($cy-$hh-1) $fin
  # eye + mouth (right = head)
  Op $b ($cx+$hw-2) ($cy-1) $WHITE; Op $b ($cx+$hw-2) $cy $OL; Op $b ($cx+$hw) $cy $OL
  # belly shading
  Ln $b ($cx-1) ($cy+$hh-1) ($cx+2) ($cy+$hh-1) $WHITE
  switch($mark){
    "bars"    { Ln $b ($cx-2) ($cy-1) ($cx-2) ($cy+1) $OL; Ln $b $cx ($cy-2) $cx ($cy+2) $OL; Ln $b ($cx+2) ($cy-1) ($cx+2) ($cy+1) $OL }
    "spots"   { Op $b ($cx-2) ($cy-1) $WHITE; Op $b $cx ($cy+1) $WHITE; Op $b ($cx+2) ($cy-1) $WHITE }
    "barbels" { Ln $b ($cx+$hw) ($cy-1) ($cx+$hw+1) ($cy-2) $OL; Ln $b ($cx+$hw) ($cy+1) ($cx+$hw+1) ($cy+2) $OL }
    "scales"  { Op $b ($cx-1) $cy $WOODD; Op $b ($cx+1) ($cy+1) $WOODD }
  }
}

# ---------- dispatch over item/*.png ----------
Get-ChildItem "$itemDir\*.png" | ForEach-Object {
  $n = $_.BaseName; $b = NewImg
  switch -Regex ($n) {
    'bamboo_rod'      { DrawRod $b "A8B060"; break }
    'carp_rod'        { DrawRod $b "5A4530"; break }
    'ultralight_rod'  { DrawRod $b "B79A6A"; break }
    '_rod$|^rod$'     { DrawRod $b $WOOD; break }
    '^reel'           { DrawReel $b; break }
    'line_braid'      { DrawLine $b "6E6E5E"; break }
    'line_fluoro'     { DrawLine $b "AEC8D2"; break }
    'line_mono|^line$'{ DrawLine $b "CFC9B6"; break }
    '^rig'            { DrawRig $b; break }
    '^hook'           { DrawHook $b $STEELL; break }
    'leader_fluoro'   { DrawLeader $b "AEC8D2"; break }
    'leader_titanium' { DrawLeader $b "6A6A66"; break }
    '^leader'         { DrawLeader $b $STEEL; break }
    '^float$'         { DrawFloat $b; break }
    'bell'            { DrawBell $b; break }
    'fish_finder'     { DrawDigital $b; break }
    'hydro_probe'     { DrawBook $b; break }
    'digital'         { DrawDigital $b; break }
    'knife'           { DrawKnife $b; break }
    'whetstone'       { DrawWhetstone $b; break }
    'journal'         { DrawBook $b; break }
    'raw_fillet'      { DrawFillet $b "D98A8A" "E8B0B0"; break }
    'cooked_fillet'   { DrawFillet $b "B07A46" "D0A060"; break }
    'chicken_liver'   { DrawBall $b "6E2A2A" "A04A4A"; break }
    'bloodworm'       { DrawWorm $b "9A2A2A" "5A1A1A"; break }
    'maggot'          { DrawWorm $b "E8E0C0" "B8B090"; break }
    '^worm$'          { DrawWorm $b "C97A7A" "8A4A4A"; break }
    '^corn$'          { DrawGrain $b "E6C24A" "F0D878"; break }
    '^pea$'           { DrawGrain $b "6E9A3A" "9AC060"; break }
    'pearl_barley'    { DrawGrain $b "E8DEC0" "F4EED8"; break }
    '^boilie$'        { DrawBall $b "8A5A3A" "B08050"; break }
    '^dough$'         { DrawBall $b "E0D2A6" "F0E8C8"; break }
    '^bread$'         { DrawBall $b "D8A860" "ECC888"; break }
    'silicone'        { DrawSilicone $b; break }
    'livebait'        { DrawLivebait $b; break }
    'wobbler'         { DrawWobbler $b; break }
    'spoon'           { DrawSpoon $b; break }
    'spinner'         { DrawSpinner $b; break }
    '^lure$'          { DrawSpoon $b; break }
    'groundbait|^bait$' { DrawGroundbait $b $WOOD; break }
    default           { DrawBall $b $WOOD $WOODL; break }
  }
  Stylize $b
  Save $b $_.FullName
}

# NOTE: fish icons are NOT touched here — they are hi-res (128x128) and owned by gen_fish_hd.ps1.

Write-Host "Item icons regenerated (fish skipped; run gen_fish_hd.ps1 for fish)."
