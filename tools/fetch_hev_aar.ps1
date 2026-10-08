$ErrorActionPreference = "Stop"

$Version = "2.18.0"
$Expected = "15ec8ed121663b562c99caa5bb602d1009f24e5b09e733438b81988f12feaaab"
$Url = "https://github.com/heiher/hev-socks5-tunnel/releases/download/$Version/hev-socks5-tunnel.aar"

$Root = Split-Path -Parent $PSScriptRoot
$DestDir = Join-Path $Root "app\libs"
$Dest = Join-Path $DestDir "hev-socks5-tunnel.aar"
$Tmp = "$Dest.tmp"

New-Item -ItemType Directory -Force -Path $DestDir | Out-Null
if (Test-Path $Tmp) { Remove-Item -Force $Tmp }

Write-Host "Downloading HEV $Version Android AAR..."
Invoke-WebRequest -Uri $Url -OutFile $Tmp

$Actual = (Get-FileHash $Tmp -Algorithm SHA256).Hash.ToLowerInvariant()
if ($Actual -ne $Expected) {
    Remove-Item -Force $Tmp
    throw "SHA-256 mismatch. expected=$Expected actual=$Actual"
}

Move-Item -Force $Tmp $Dest
Write-Host "HEV_AAR_READY=$Dest"
Write-Host "SHA256=$Actual"
