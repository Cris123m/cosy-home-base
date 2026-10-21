# Install an app on the TV from the PC in one step, instead of Send files to TV + APKMirror Installer on the TV.
# Accepts a single .apk, an .apkm / .apks / .xapk bundle (as downloaded from APKMirror), or a folder of split APKs.
# Keeps the app's data (-r). ADB must be on (see docs/02-tv-first-setup.md).
#
# Usage: scripts\install-apk.ps1 <file-or-folder> [-TvHost 192.168.8.202] [-WhatIf]
#   e.g. scripts\install-apk.ps1 "$env:USERPROFILE\Downloads\com.formulaone.production_3.0.51.apkm"
param(
    [Parameter(Mandatory = $true, Position = 0)][string]$Path,
    [string]$TvHost = $(if ($env:TV_HOST) { $env:TV_HOST } else { '192.168.8.202' }),
    [switch]$WhatIf
)
$ErrorActionPreference = 'Stop'
$adb = if ($env:ADB) { $env:ADB } else { 'D:\Server\Tools\platform-tools\adb.exe' }
$dev = if ($TvHost -like '*:*') { $TvHost } else { "${TvHost}:5555" }
$Path = (Resolve-Path $Path).Path
$temp = $null

# Collect the APK files: a folder as is, a bundle unpacked into a fresh temporary folder, or one APK.
if (Test-Path $Path -PathType Container) {
    $apks = @(Get-ChildItem $Path -Filter *.apk | Select-Object -ExpandProperty FullName)
} elseif ($Path -match '\.(apkm|apks|xapk)$') {
    $temp = Join-Path ([IO.Path]::GetTempPath()) ("tv-apk-" + [guid]::NewGuid())
    New-Item -ItemType Directory $temp | Out-Null
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    [IO.Compression.ZipFile]::ExtractToDirectory($Path, $temp)
    $apks = @(Get-ChildItem $temp -Recurse -Filter *.apk | Select-Object -ExpandProperty FullName)
} else {
    $apks = @($Path)
}
if ($apks.Count -eq 0) { throw "no .apk files found in $Path" }

# Base APK first; the rest are splits (CPU, language, screen density).
$apks = @($apks | Sort-Object { if ((Split-Path $_ -Leaf) -match '^base\.apk$|^[^_]+\.apk$') { 0 } else { 1 } }, { $_ })
Write-Output ("Installing " + $apks.Count + " file(s):")
$apks | ForEach-Object { Write-Output ("  " + (Split-Path $_ -Leaf) + "  " + [math]::Round((Get-Item $_).Length / 1MB, 1) + " MB") }

try {
    if ($WhatIf) { Write-Output 'WhatIf: nothing installed.'; return }
    & $adb connect $dev | Out-Null

    # One APK installs normally; split APKs must go in together as one session.
    if ($apks.Count -eq 1) { & $adb -s $dev install -r $apks[0] }
    else { & $adb -s $dev install-multiple -r @apks }
    if ($LASTEXITCODE -ne 0) { throw 'install failed (see the message above)' }
} finally {
    # Remove the unpacked bundle.
    if ($temp) { Remove-Item -Recurse -Force -Confirm:$false $temp }
}
