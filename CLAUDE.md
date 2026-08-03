# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What This Project Does

A minimal Android APK (`com.ablesign.bootlauncher`, "AbleSign Watchdog") that automatically launches the AbleSign digital signage app (`tv.ablesign.app`) after every Android TV reboot — permanently, with no manual interaction.

The core insight: Android's `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES` is stored in SettingsProvider's SQLite DB and survives reboots on all OEMs, unlike `appops`, `device_config`, `pm disable-user`, or the deviceidle whitelist (which Realtek OEM resets). The APK registers as an Accessibility Service solely to get this persistence guarantee.

## Build Commands

**Prerequisites:** Android SDK at `C:\Users\<YOU>\AppData\Local\Android\Sdk`, build-tools `37.0.0`, platform `android-37.0`, and any JDK 8+.

```powershell
$dir = "E:\IMP PROJECT 2\ablesign launcher"
$bt  = "C:\Users\<YOU>\AppData\Local\Android\Sdk\build-tools\37.0.0"
$plat= "C:\Users\<YOU>\AppData\Local\Android\Sdk\platforms\android-37.0"
$jdk = "C:\path\to\jdk"
$env:JAVA_HOME = $jdk
$android = "$plat\android.jar"
Set-Location $dir

New-Item gen,obj,bin -ItemType Directory -Force | Out-Null
& "$bt\aapt.exe" package -f -m -J gen -S res -M AndroidManifest.xml -I $android
$src = @((Get-ChildItem src -Recurse -Filter "*.java").FullName) + @((Get-ChildItem gen -Recurse -Filter "*.java").FullName)
& "$jdk\bin\javac.exe" -source 8 -target 8 -cp $android -d obj $src
& "$bt\d8.bat" --output bin (Get-ChildItem obj -Recurse -Filter "*.class").FullName
& "$bt\aapt.exe" package -f -M AndroidManifest.xml -S res -I $android -F bin\app.unsigned.apk bin
& "$bt\apksigner.bat" sign --ks launcher.keystore --ks-pass pass:android --out app.apk bin\app.unsigned.apk
```

Keystore password: `android`

## Deploy to TV

```bash
# Disable Play Protect (required once per TV)
adb shell settings put global verifier_verify_adb_installs 0
adb shell settings put global package_verifier_enable 0

# Install
adb install -r --no-streaming app.apk

# Enable accessibility service (the persistent trigger)
adb shell am force-stop com.ablesign.bootlauncher
adb shell settings put secure accessibility_enabled 0
adb shell settings put secure enabled_accessibility_services com.ablesign.bootlauncher/.WatchdogAccessibilityService
adb shell settings put secure accessibility_enabled 1

# Battery whitelist (keeps AbleSign from being killed)
adb shell dumpsys deviceidle whitelist +tv.ablesign.app
adb shell dumpsys deviceidle whitelist +com.ablesign.bootlauncher
```

The `am force-stop` before the toggle is required — without it, on some OEMs the accessibility framework keeps a stale binding and never calls `onCreate()`.

## Architecture

Three components work in layers, ordered by reliability on stock Android vs. OEM-hardened devices:

### 1. `WatchdogAccessibilityService` — Primary (always works)
The only mechanism that survives reboots on all OEMs. The accessibility service is started by the Android framework itself at boot. Launch logic lives in `onCreate()`, **not** `onServiceConnected()` — on Realtek (and some other OEMs), `onServiceConnected()` is never called at boot without a manual toggle; `onCreate()` always fires.

- 12-second delay: lets the system finish loading before the activity start
- `onServiceConnected()` has a 3-second fallback in case `onCreate()` was somehow skipped
- Static `launched` flag prevents double-launch if both callbacks fire

### 2. `BootReceiver` — Secondary (blocked on restricted OEMs)
Listens for `BOOT_COMPLETED` and `QUICKBOOT_POWERON`. Uses `goAsync()` + thread with a 6-second delay. On Android 14 with OEM battery restriction (`background_restricted/DENIED`), this receiver is silently blocked and never fires. It still works on less-restricted devices/Android versions.

### 3. `MainActivity` — Fallback (HOME role)
Declared as a `HOME`/`LEANBACK_LAUNCHER` activity. If this app ever becomes the system HOME (via `cmd role add-role-holder android.app.role.HOME com.ablesign.bootlauncher`), it launches AbleSign 3 seconds after being shown. The 10-second `MIN_RELAUNCH_MS` guard prevents boot loops if AbleSign crashes and the system keeps sending HOME intents back here. **Never calls `finish()`** — must stay resident to avoid the system launching a different HOME and looping.

## Critical Implementation Detail

**Always use `ComponentName` directly, never `getPackageManager().getLaunchIntentForPackage()`.**

Android 11+ package visibility rules cause `getLaunchIntentForPackage("tv.ablesign.app")` to return `null` unless `QUERY_ALL_PACKAGES` is declared or a `<queries>` block is added. Using `ComponentName` bypasses this entirely and is the reason all previous iterations silently failed.

```java
// WRONG — returns null on Android 11+
Intent i = getPackageManager().getLaunchIntentForPackage("tv.ablesign.app");

// CORRECT
Intent i = new Intent(Intent.ACTION_MAIN);
i.setComponent(new ComponentName("tv.ablesign.app", "tv.ablesign.app.MainActivity"));
```

## Debugging

```bash
# Verify accessibility service is registered and alive
adb shell settings get secure enabled_accessibility_services
adb shell ps -A | grep ablesign
adb shell dumpsys accessibility | grep -A3 "AbleSign Watchdog"

# Watch live logs from the watchdog
adb shell logcat -s WatchdogA11y BootLauncher

# If AbleSign never appears after reboot, re-run the force-stop+toggle deploy sequence above
```

## Target TV

Tested on: 2K D5STV, Realtek chipset, Android 14, Tailscale IP `100.96.231.22:5555`.
Works on any Android TV (API 21+) — the accessibility service DB mechanism is universal.
