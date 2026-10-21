# Live demo on the TV: cycle the Cosy Home launcher through times of day and weather, each with a matching
# demo clock and weather line, then go back to the live time and weather. Nothing is changed permanently.
#
# Usage: scripts\tv-demo.ps1 [-Seconds 8] [-Loop] [-TvHost 192.168.8.202]
#   -Loop repeats until Ctrl+C. After stopping a loop, the launcher stays on the last demo scene until the next
#   run ends, or: adb shell am start -n com.cosyhome.tv/.HomeActivity --es phase auto --es weather auto
param(
    [int]$Seconds = 8,
    [switch]$Loop,
    [string]$TvHost = $(if ($env:TV_HOST) { $env:TV_HOST } else { '192.168.8.202' })
)
$ErrorActionPreference = 'Stop'
$adb = if ($env:ADB) { $env:ADB } else { 'D:\Server\Tools\platform-tools\adb.exe' }
$dev = if ($TvHost -like '*:*') { $TvHost } else { "${TvHost}:5555" }

# Phase, weather preview, demo temperature, demo clock.
$demo = @(
    @('dawn',  'clear',     '2',   '06:45'), @('dawn',  'fog',       '4',   '07:12'),
    @('day',   'clear',     '18',  '11:00'), @('day',   'partly',    '12',  '12:30'), @('day', 'cloudy', '8', '13:10'),
    @('day',   'rain',      '9',   '14:20'), @('day',   'heavyrain', '11',  '15:05'), @('day', 'snow',   '-3', '14:05'),
    @('dusk',  'clear',     '6',   '17:30'), @('dusk',  'rain',      '7',   '17:48'),
    @('night', 'clear',     '-12', '23:55'), @('night', 'snow',      '-8',  '22:10'), @('night', 'storm', '15', '22:40')
)

& $adb connect $dev | Out-Null
& $adb -s $dev shell input keyevent KEYCODE_HOME
Start-Sleep -Seconds 2

# One preview intent per scene; the launcher redraws in place.
do {
    foreach ($x in $demo) {
        & $adb -s $dev shell am start -n com.cosyhome.tv/.HomeActivity --es phase $x[0] --es weather $x[1] --ei temp $x[2] --es clock $x[3] 2>$null | Out-Null
        Write-Output ("{0,-6} {1,-10} {2,4} deg  {3}" -f $x[0], $x[1], $x[2], $x[3])
        Start-Sleep -Seconds $Seconds
    }
} while ($Loop)

# Back to the real time and weather.
& $adb -s $dev shell am start -n com.cosyhome.tv/.HomeActivity --es phase auto --es weather auto 2>$null | Out-Null
Write-Output 'Back to live time and weather.'
