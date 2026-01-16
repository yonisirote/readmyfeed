# Android CLI Guide (No Android Studio UI)

This workflow is fully runnable via CLI + emulator.

## Prereqs

- Android SDK at `~/Android/Sdk`
- Java 21 (`JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64`)
- AVD exists: `Medium_Phone_API_36.1`

## Start emulator (headless)

Run it detached so the shell returns:

- `nohup ~/Android/Sdk/emulator/emulator -avd "Medium_Phone_API_36.1" -no-window -no-audio -no-snapshot -gpu swiftshader_indirect >/tmp/rmf-emulator.log 2>&1 & disown`

Wait for full boot (prevents "device is still booting" install failures):

- `adb wait-for-device`
- `until [ "$(adb shell getprop sys.boot_completed | tr -d "\r")" = "1" ] && [ "$(adb shell getprop init.svc.bootanim | tr -d "\r")" = "stopped" ]; do echo "waiting for boot..."; sleep 5; done`

## Build, sync, install

From repo root:

- `cd client && npm run build`
- `cd client && npx cap sync android`
- `cd client/android && JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew :app:installDebug`

## Launch with intent extras

Launch and auto-run fetching without touching the device UI:

- `bash -lc 'adb shell am force-stop com.readmyfeed.app; adb logcat -c; set -a; source ./.env; set +a; adb shell am start -n com.readmyfeed.app/.MainActivity --es API_KEY "$API_KEY" --ez AUTO_RUN true'`

## Confirm tweets were fetched

Filter logcat for the first 5 tweet samples:

- `bash -lc 'adb logcat -d | rg "\\[rmf/native\\] tweet id=|\\[rmf/native\\] x status=|bearer extracted" -C 1 | tail -n 120'`

## Stop

- Stop app: `adb shell am force-stop com.readmyfeed.app`
- Stop emulator: `adb emu kill`

## Notes

- All `x-client-transaction-id` generation code is browser-compatible (`crypto.subtle`, `DOMParser`, `fetch`).
- Browser CORS/credential constraints make a pure-JS direct call to X’s GraphQL endpoints impractical from non-x.com origins.
- Capacitor native HTTP bypasses those browser constraints and is the primary approach.
