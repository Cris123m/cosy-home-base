# Switch the TV's Private DNS to AdGuard (blocks ads and trackers in every app, but not YouTube ads),
# or back to the Android default with -Off.
#
# Usage: scripts\dns.ps1 [-Hostname dns.adguard-dns.com] [-Off] [-TvHost 192.168.8.202]
#   Family filter (also blocks adult sites): -Hostname family.adguard-dns.com
param(
    [string]$Hostname = 'dns.adguard-dns.com',
    [switch]$Off,
    [string]$TvHost = $(if ($env:TV_HOST) { $env:TV_HOST } else { '192.168.8.202' })
)
$ErrorActionPreference = 'Stop'
$adb = if ($env:ADB) { $env:ADB } else { 'D:\Server\Tools\platform-tools\adb.exe' }
$dev = if ($TvHost -like '*:*') { $TvHost } else { "${TvHost}:5555" }
& $adb connect $dev | Out-Null

# Set the resolver first, then the mode, one settings call each.
if ($Off) {
    & $adb -s $dev shell settings put global private_dns_mode opportunistic
    & $adb -s $dev shell settings delete global private_dns_specifier
} else {
    & $adb -s $dev shell settings put global private_dns_specifier $Hostname
    & $adb -s $dev shell settings put global private_dns_mode hostname
}

# Show the result.
Write-Output ("mode:      " + (& $adb -s $dev shell settings get global private_dns_mode))
Write-Output ("specifier: " + (& $adb -s $dev shell settings get global private_dns_specifier))
