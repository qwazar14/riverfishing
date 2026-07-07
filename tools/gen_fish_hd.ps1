# High-res (128x128) fish icons drawn with GDI+ (anti-aliased bodies, gradient shading, fins, scales,
# per-species markings). Head faces LEFT (vanilla fish convention). ASCII-only. Overwrites item/fish/*.png.
Add-Type -AssemblyName System.Drawing

$S = 128
$fishDir = "C:\Users\Qwazar\VS Code Projects\fishing mod\src\main\resources\assets\riverfishing\textures\item\fish"
New-Item -ItemType Directory -Force -Path $fishDir | Out-Null

function Col($h,$a=255){ $r=[Convert]::ToInt32($h.Substring(0,2),16); $g=[Convert]::ToInt32($h.Substring(2,2),16); $b=[Convert]::ToInt32($h.Substring(4,2),16); return [System.Drawing.Color]::FromArgb($a,$r,$g,$b) }
function Pt($x,$y){ return New-Object System.Drawing.PointF([single]$x,[single]$y) }
function Arr($pts){ return [System.Drawing.PointF[]]$pts }

function DrawFish($name,$back,$belly,$fin,$eye,$lenF,$hF,$fork,$marks){
  $bmp = New-Object System.Drawing.Bitmap($S,$S,[System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
  $g = [System.Drawing.Graphics]::FromImage($bmp)
  $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias

  $cx = $S/2; $cy = $S/2 + 4
  $hl = $S*$lenF/2.0          # half body length
  $h  = $S*$hF                # half body height

  $cBack=Col $back; $cBelly=Col $belly; $cFin=Col $fin; $cEye=Col $eye
  $cOL = Col "241C12"
  $penOL = New-Object System.Drawing.Pen($cOL,2.4)
  $penFin = New-Object System.Drawing.Pen((Col $fin 220),1.6)
  $finBrush = New-Object System.Drawing.SolidBrush((Col $fin 235))

  # ---- tail (behind body), attaches at right (cx+hl) ----
  $bx = $cx + $hl
  $tl = $hl*0.55
  $sp = $h*1.15
  $tail = New-Object System.Drawing.Drawing2D.GraphicsPath
  $tail.AddClosedCurve((Arr @( (Pt $bx ($cy-$h*0.2)), (Pt ($bx+$tl) ($cy-$sp)), (Pt ($bx+$tl*$fork) $cy), (Pt ($bx+$tl) ($cy+$sp)), (Pt $bx ($cy+$h*0.2)) )),0.3)
  $g.FillPath($finBrush,$tail); $g.DrawPath($penFin,$tail)

  # ---- dorsal fin (behind body, on the back) ----
  $dor = New-Object System.Drawing.Drawing2D.GraphicsPath
  $dor.AddClosedCurve((Arr @( (Pt ($cx-$hl*0.25) ($cy-$h*0.85)), (Pt ($cx-$hl*0.05) ($cy-$h*1.55)), (Pt ($cx+$hl*0.45) ($cy-$h*1.35)), (Pt ($cx+$hl*0.5) ($cy-$h*0.75)) )),0.4)
  $g.FillPath($finBrush,$dor); $g.DrawPath($penFin,$dor)

  # ---- anal fin (bottom) ----
  $anal = New-Object System.Drawing.Drawing2D.GraphicsPath
  $anal.AddClosedCurve((Arr @( (Pt ($cx+$hl*0.15) ($cy+$h*0.8)), (Pt ($cx+$hl*0.45) ($cy+$h*1.35)), (Pt ($cx+$hl*0.6) ($cy+$h*0.7)) )),0.4)
  $g.FillPath($finBrush,$anal)

  # ---- body ----
  $body = New-Object System.Drawing.Drawing2D.GraphicsPath
  $body.AddClosedCurve((Arr @(
    (Pt ($cx-$hl) $cy),
    (Pt ($cx-$hl*0.55) ($cy-$h*0.72)),
    (Pt ($cx+$hl*0.05) ($cy-$h)),
    (Pt ($cx+$hl*0.72) ($cy-$h*0.55)),
    (Pt ($cx+$hl) ($cy-$h*0.28)),
    (Pt ($cx+$hl) ($cy+$h*0.28)),
    (Pt ($cx+$hl*0.72) ($cy+$h*0.6)),
    (Pt ($cx+$hl*0.05) ($cy+$h)),
    (Pt ($cx-$hl*0.55) ($cy+$h*0.62))
  )),0.5)
  $grad = New-Object System.Drawing.Drawing2D.LinearGradientBrush((Pt $cx ($cy-$h)),(Pt $cx ($cy+$h)),$cBack,$cBelly)
  $g.FillPath($grad,$body)

  # ---- markings (clipped to body) ----
  $g.SetClip($body)
  switch($marks){
    "bars" {
      $bcol = New-Object System.Drawing.SolidBrush((Col "20180E" 90))
      for($i=-2;$i -le 3;$i++){ $x=$cx+$i*$hl*0.28; $g.FillRectangle($bcol,[single]($x-3),[single]($cy-$h),[single]6,[single]($h*2)) }
    }
    "spots" {
      $scol = New-Object System.Drawing.SolidBrush((Col $belly 150))
      $rnd = New-Object System.Random(7)
      for($i=0;$i -lt 26;$i++){ $x=$cx-$hl+$rnd.NextDouble()*$hl*1.8; $y=$cy-$h+$rnd.NextDouble()*$h*2; $r=2+$rnd.NextDouble()*3; $g.FillEllipse($scol,[single]$x,[single]$y,[single]$r,[single]$r) }
    }
    "scales" {
      $pen = New-Object System.Drawing.Pen((Col "20180E" 70),1.3)
      for($row=-3;$row -le 3;$row++){ for($coln=-3;$coln -le 4;$coln++){ $x=$cx+$coln*$hl*0.22+($row%2)*$hl*0.11; $y=$cy+$row*$h*0.28; $g.DrawArc($pen,[single]($x-5),[single]($y-3),[single]10,[single]10,200,140) } }
    }
    "mottle" {
      $mcol = New-Object System.Drawing.SolidBrush((Col "20180E" 70))
      $rnd = New-Object System.Random(3)
      for($i=0;$i -lt 20;$i++){ $x=$cx-$hl+$rnd.NextDouble()*$hl*1.9; $y=$cy-$h+$rnd.NextDouble()*$h*2; $w=5+$rnd.NextDouble()*7; $g.FillEllipse($mcol,[single]$x,[single]$y,[single]$w,[single]($w*0.7)) }
    }
  }
  # lateral line + belly sheen
  $llpen = New-Object System.Drawing.Pen((Col "FFFFFF" 60),2)
  $g.DrawLine($llpen,[single]($cx-$hl*0.5),[single]($cy+$h*0.5),[single]($cx+$hl*0.7),[single]($cy+$h*0.35))
  $g.ResetClip()

  # ---- body outline + pectoral fin ----
  $g.DrawPath($penOL,$body)
  $pec = New-Object System.Drawing.Drawing2D.GraphicsPath
  $pec.AddClosedCurve((Arr @( (Pt ($cx-$hl*0.35) ($cy+$h*0.15)), (Pt ($cx-$hl*0.1) ($cy+$h*0.75)), (Pt ($cx-$hl*0.05) ($cy+$h*0.2)) )),0.4)
  $g.FillPath((New-Object System.Drawing.SolidBrush((Col $fin 200))),$pec)

  # ---- gill line ----
  $g.DrawArc((New-Object System.Drawing.Pen($cOL,1.6)),[single]($cx-$hl*0.75),[single]($cy-$h*0.55),[single]($h*1.1),[single]($h*1.4),300,120)

  # ---- barbels (whiskers) for catfish / carp ----
  if($marks -eq "mottle" -or $name -eq "carp"){
    $wp = New-Object System.Drawing.Pen($cOL,2)
    $mx=$cx-$hl*0.9; $my=$cy+$h*0.1
    $p1=New-Object System.Drawing.Drawing2D.GraphicsPath; $p1.AddBezier((Pt $mx $my),(Pt ($mx-8) ($my+6)),(Pt ($mx-14) ($my+16)),(Pt ($mx-10) ($my+26))); $g.DrawPath($wp,$p1)
    if($marks -eq "mottle"){ $p2=New-Object System.Drawing.Drawing2D.GraphicsPath; $p2.AddBezier((Pt $mx ($my-4)),(Pt ($mx-12) ($my-8)),(Pt ($mx-22) ($my-6)),(Pt ($mx-30) ($my-2))); $g.DrawPath($wp,$p2) }
  }

  # ---- eye (near head, left) ----
  $ex=$cx-$hl*0.62; $ey=$cy-$h*0.18; $er=$h*0.28; if($er -lt 5){$er=5}
  $g.FillEllipse((New-Object System.Drawing.SolidBrush($cEye)),[single]($ex-$er),[single]($ey-$er),[single]($er*2),[single]($er*2))
  $g.FillEllipse((New-Object System.Drawing.SolidBrush($cOL)),[single]($ex-$er*0.5),[single]($ey-$er*0.5),[single]$er,[single]$er)
  $g.FillEllipse((New-Object System.Drawing.SolidBrush((Col "FFFFFF" 220))),[single]($ex-$er*0.3),[single]($ey-$er*0.6),[single]($er*0.5),[single]($er*0.5))

  $g.Dispose()
  $bmp.Save("$fishDir\$name.png",[System.Drawing.Imaging.ImageFormat]::Png)
  $bmp.Dispose()
}

#         name           back     belly    fin      eye     lenF  hF     fork marks
DrawFish "pike"         "4E5A22" "9AA86A" "6E6234" "C8B84A" 0.86 0.15  0.55 "spots"
DrawFish "perch"        "566B2C" "C6C89A" "B0472B" "D8A030" 0.66 0.30  0.50 "bars"
DrawFish "zander"       "6A7058" "C0C4A8" "8A9078" "C8B040" 0.82 0.20  0.50 "bars"
DrawFish "roach"        "6E7E8A" "ECEFF0" "B0472B" "C03020" 0.66 0.27  0.55 "none"
DrawFish "rudd"         "8A7E4A" "E8DCA8" "C03020" "C8A030" 0.66 0.29  0.55 "none"
DrawFish "bream"        "6E5E3A" "B0A070" "3A3228" "8A7040" 0.60 0.42  0.55 "scales"
DrawFish "white_bream"  "7E8A8E" "E4E8E6" "6A7072" "9AA0A0" 0.60 0.38  0.55 "scales"
DrawFish "carp"         "8A6A3A" "D0B070" "6A5232" "A08040" 0.74 0.42  0.55 "scales"
DrawFish "crucian_carp" "9A7A30" "E0C060" "7A5A20" "C0A040" 0.62 0.44  0.90 "scales"
DrawFish "catfish"      "4A4A3E" "8A8A72" "3A3A30" "3A3226" 0.90 0.19  0.95 "mottle"
DrawFish "gudgeon"      "8A7A56" "D6CCAE" "6E6244" "8A7040" 0.56 0.18  0.55 "mottle"
DrawFish "ruffe"        "6E7250" "C2C4A2" "8A8C6A" "B0902A" 0.52 0.24  0.50 "spots"
DrawFish "bleak"        "8A98A6" "F0F4F6" "9AA8B2" "C8C8C0" 0.58 0.15  0.60 "none"
DrawFish "ide"          "7A7048" "E0D4A0" "B0472B" "D8A030" 0.68 0.30  0.55 "scales"
DrawFish "chub"         "525E46" "D8DCC8" "6E6234" "C03020" 0.70 0.28  0.55 "scales"
DrawFish "asp"          "5E7086" "E4EAF0" "6A7A8C" "C8B040" 0.80 0.24  0.55 "none"
DrawFish "tench"        "4E5A2A" "C0B060" "3A4420" "C03020" 0.60 0.38  0.75 "scales"
DrawFish "burbot"       "5A4E36" "B0A280" "463C28" "8A7040" 0.86 0.20  0.85 "mottle"
DrawFish "eel"          "44503C" "C8C8A0" "3A4434" "6A7058" 0.96 0.11  0.90 "none"
DrawFish "grayling"     "6E7A96" "D8DCE8" "8A5AA0" "C8B040" 0.70 0.24  0.50 "spots"
DrawFish "trout"        "5E6E50" "E8C8B0" "B0472B" "D8A030" 0.72 0.26  0.55 "spots"
DrawFish "sterlet"      "6A625A" "C8C0B2" "564E46" "8A8070" 0.92 0.15  0.60 "mottle"

Write-Host "HD fish icons (128x128) written to $fishDir"
Get-ChildItem $fishDir | Select-Object Name, Length
