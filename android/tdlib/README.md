# TDLib Module

This module packages upstream TDLib Android artifacts for `readmyfeed`.

## Contents

- generated Java sources under `src/main/java/org/drinkless/tdlib/`
- locally regenerated JNI libraries under `src/main/jniLibs/<abi>/`
- upstream Boost Software License copy in `LICENSE_1_0.txt`
- regeneration script in `regenerate.sh`

## Build provenance

- upstream repo: `https://github.com/tdlib/td`
- upstream commit: `0ae923c493bceb75433de2682ba8ae29cc7bf88d`
- OpenSSL build script: `example/android/build-openssl.sh`
- TDLib build script: `example/android/build-tdlib.sh`
- default NDK version for local TDLib rebuilds: `26.1.10909125`
- interface: `Java`
- STL mode: `c++_static`

Do not hand-edit the generated Java sources in this module. Regenerate them from upstream when TDLib is updated.

The JNI libraries are intentionally ignored by git. After a fresh clone, run `./tdlib/regenerate.sh` from `android/` before `./gradlew assembleDebug`.

## Regenerate

```bash
cd android
./tdlib/regenerate.sh
```

Optional overrides:

- `ANDROID_SDK_ROOT` or `ANDROID_HOME` to point at the Android SDK
- `TDLIB_NDK_VERSION` to choose a different installed NDK
- `TDLIB_UPSTREAM_COMMIT` to rebuild from a different TDLib commit

The script clones upstream TDLib at the pinned commit, runs the upstream Android build scripts, refreshes the Java bindings, and writes fresh JNI libraries into `src/main/jniLibs/`.
