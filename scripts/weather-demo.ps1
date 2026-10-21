# Take the launcher's weather demo screenshots: each shows a time of day, a weather scene and a matching
# weather line and clock, saved as screenshots\demo-live-weather-<name>.png. Back to live weather at the end.
#
# Usage: scripts\weather-demo.ps1 [-TvHost 192.168.8.202]
param(
    [string]$TvHost = $(if ($env:TV_HOST) { $env:TV_HOST } else { '192.168.8.202' })
)
$ErrorActionPreference = 'Stop'
$adb = if ($env:ADB) { $env:ADB } else { 'D:\Server\Tools\platform-tools\adb.exe' }
$dev = if ($TvHost -like '*:*') { $TvHost } else { "${TvHost}:5555" }
$shot = Join-Path $PSScriptRoot 'shot.ps1'

# Phase, weather preview, demo temperature, demo clock, file name.
$demos = @(
    @('dawn',  'fog',    '4',   '07:12', 'dawn-fog'),
    @('day',   'partly', '12',  '12:30', 'day-partly-cloudy'),
    @('day',   'snow',   '-3',  '14:05', 'day-snow'),
    @('dusk',  'rain',   '7',   '17:48', 'dusk-rain'),
    @('night', 'storm',  '15',  '22:40', 'night-thunderstorm'),
    @('night', 'clear',  '-12', '23:55', 'night-clear')
)

& $adb connect $dev | Out-Null
& $adb -s $dev shell input keyevent KEYCODE_HOME
Start-Sleep -Seconds 2

# One preview intent per demo, then a screenshot once the scene has redrawn.
foreach ($d in $demos) {
    & $adb -s $dev shell am start -n com.cosyhome.tv/.HomeActivity --es phase $d[0] --es weather $d[1] --ei temp $d[2] --es clock $d[3] | Out-Null
    & $shot ("demo-live-weather-" + $d[4]) -TvHost $TvHost -Wait 4 | Out-Null
    Write-Output ("demo-live-weather-" + $d[4] + ".png")
}

# Back to the real time and weather.
& $adb -s $dev shell am start -n com.cosyhome.tv/.HomeActivity --es phase auto --es weather auto | Out-Null
