# Take a screenshot from the TV into screenshots\<name>.png, optionally pressing keys first.
#
# Usage: scripts\shot.ps1 <name> [-Keys HOME,DPAD_DOWN] [-Wait 2] [-TvHost 192.168.8.202]
param(
    [Parameter(Mandatory = $true, Position = 0)][string]$Name,
    [string[]]$Keys = @(),
    [int]$Wait = 2,
    [string]$TvHost = $(if ($env:TV_HOST) { $env:TV_HOST } else { '192.168.8.202' })
)
$ErrorActionPreference = 'Stop'
$adb = if ($env:ADB) { $env:ADB } else { 'D:\Server\Tools\platform-tools\adb.exe' }
$dev = if ($TvHost -like '*:*') { $TvHost } else { "${TvHost}:5555" }
$repo = Split-Path -Parent $PSScriptRoot
$dir = Join-Path $repo 'screenshots'
New-Item -ItemType Directory -Force $dir | Out-Null

# Inject the remote keys, then give the screen time to settle.
foreach ($k in $Keys) { & $adb -s $dev shell input keyevent "KEYCODE_$k"; Start-Sleep -Milliseconds 600 }
Start-Sleep -Seconds $Wait

& $adb -s $dev shell screencap -p /sdcard/shot.png
& $adb -s $dev pull /sdcard/shot.png (Join-Path $dir "$Name.png") | Out-Null
& $adb -s $dev shell rm /sdcard/shot.png
Write-Output (Join-Path $dir "$Name.png")
