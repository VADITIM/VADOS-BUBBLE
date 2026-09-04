# Grants everything the bubble needs over adb, for when One UI refuses to hand it
# over in Settings because the app was sideloaded ("Restricted setting").
# Usage: powershell -ExecutionPolicy Bypass -File grant.ps1

$adb = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
$package = 'com.v.island'
$listener = "$package/$package.IslandNotificationListener"
$bubble = "$package/$package.BubbleService"

& $adb wait-for-device

# ACCESS_RESTRICTED_SETTINGS is what the greyed-out toggle is waiting on; the rest
# are the grants themselves, which Settings would have done.
& $adb shell appops set $package ACCESS_RESTRICTED_SETTINGS allow
& $adb shell pm grant $package android.permission.POST_NOTIFICATIONS
# The mobile link's generation (4G / 5G / E) for the Status bubble. Nothing else reads it.
& $adb shell pm grant $package android.permission.READ_PHONE_STATE
& $adb shell cmd notification allow_listener $listener

# The accessibility list is written directly: it is a colon-separated string, and
# overwriting it would silently switch off every other accessibility service.
$enabled = (& $adb shell settings get secure enabled_accessibility_services).Trim()
if ($enabled -eq 'null' -or $enabled -eq '') { $enabled = '' }
if (-not ($enabled -split ':' | Where-Object { $_ -eq $bubble })) {
    $updated = if ($enabled -eq '') { $bubble } else { "$enabled`:$bubble" }
    & $adb shell settings put secure enabled_accessibility_services $updated
}
& $adb shell settings put secure accessibility_enabled 1

# SystemUI's own pop-up, handed over to the bubble. The notification still lands in
# the shade and still reaches the listener; only the banner is gone.
& $adb shell settings put global heads_up_notifications_enabled 0
# Samsung's own "brief" pop-up is a separate one, drawn by Notilus off its own
# notification listener; the global setting above does not touch it.
& $adb shell cmd notification disallow_listener com.samsung.systemui.notilus/com.samsung.systemui.notilus.service.NotificationListener

Write-Host ''
Write-Host 'Accessibility:' (& $adb shell settings get secure enabled_accessibility_services)
Write-Host 'Listeners    :' (& $adb shell settings get secure enabled_notification_listeners)
Write-Host 'System popups:' (& $adb shell settings get global heads_up_notifications_enabled)
