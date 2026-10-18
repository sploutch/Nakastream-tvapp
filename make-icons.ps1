# Genere l'icone (mipmap) et la banniere Android TV (320x180) dans app/res a partir de logo.png.
Add-Type -AssemblyName System.Drawing
$res = Join-Path $PSScriptRoot "app\res"
$logo = [System.Drawing.Image]::FromFile((Join-Path $PSScriptRoot "logo.png"))

function New-Canvas($w, $h) {
    $bmp = New-Object System.Drawing.Bitmap $w, $h
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.SmoothingMode = 'AntiAlias'
    $g.InterpolationMode = 'HighQualityBicubic'
    $g.PixelOffsetMode = 'HighQuality'
    $g.TextRenderingHint = 'AntiAliasGridFit'
    return $bmp, $g
}

function Save($bmp, $g, $path) {
    New-Item -ItemType Directory -Force (Split-Path $path) | Out-Null
    $g.Dispose()
    $bmp.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()
}

# Icones : le logo redimensionne.
foreach ($d in @(@("mdpi", 48), @("hdpi", 72), @("xhdpi", 96), @("xxhdpi", 144), @("xxxhdpi", 192))) {
    $bmp, $g = New-Canvas $d[1] $d[1]
    $g.DrawImage($logo, 0, 0, $d[1], $d[1])
    Save $bmp $g "$res\mipmap-$($d[0])\ic_launcher.png"
}

# Banniere : fond du logo, ours a gauche, nom en dore a droite.
$bmp, $g = New-Canvas 320 180
$g.DrawImage($logo, 0, 0, 180, 180)
# Prolonge le bord droit du logo pour que le fond reste continu.
$edge = New-Object System.Drawing.Rectangle ($logo.Width - 3), 0, 2, $logo.Height
$g.DrawImage($logo, (New-Object System.Drawing.Rectangle 179, 0, 141, 180), $edge, 'Pixel')
$gold = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255, 214, 178, 102))
$font = New-Object System.Drawing.Font "Segoe UI", 30, ([System.Drawing.FontStyle]::Bold), ([System.Drawing.GraphicsUnit]::Pixel)
$fmt = New-Object System.Drawing.StringFormat
$fmt.Alignment = 'Center'; $fmt.LineAlignment = 'Center'
$g.DrawString("NAKA", $font, $gold, (New-Object System.Drawing.RectangleF 165, 0, 155, 180), $fmt)
Save $bmp $g "$res\drawable-xhdpi\banner.png"

$logo.Dispose()
Write-Host "Icones generees."
