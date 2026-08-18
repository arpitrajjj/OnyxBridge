# OnyxBridge

**Android Native Bridge Library** for system-level interactions, permission handling, and cross-platform native code integration.

OnyxBridge bundles a C++ JNI library (`libonyxbridge.so`) with a Java wrapper class to bring native-level SMS permission management and Toast messaging to Android apps. The native library is compiled for four ABIs, validated by CI, and published as a GitHub Release.

---

## Table of Contents

1. [Features](#features)
2. [Architecture Support](#architecture-support)
3. [Project Structure](#project-structure)
4. [Build Instructions](#build-instructions)
5. [Usage](#usage)
6. [CI/CD Pipeline](#cicd-pipeline)
7. [Validation Checks](#validation-checks)
8. [Integration Guide](#integration-guide)
9. [License](#license)

---

## Features

- **C++ JNI library** that handles Android runtime permissions for SMS:
  - `android.permission.SEND_SMS`
  - `android.permission.RECEIVE_SMS`
  - `android.permission.READ_SMS`
- **Toast display from native C++** via JNI — no Java boilerplate required.
- **Multi-architecture support** — single CMake config produces a `.so` for each Android ABI.
- **Clean modular structure** with separate headers for permission handling and Toast helpers.
- **Java wrapper class** (`OnyxBridge.java`) exposing a typed API on top of native methods.
- **Full Android Studio project** — multi-module Gradle project (library + demo).
- **Demo APK** — minimal Android app demonstrating OnyxBridge usage, built and signed in CI.
- **Integration Guide** — step-by-step instructions for injecting OnyxBridge into an existing APK (no source) via apktool + smali.
- **GitHub Actions CI/CD** — builds all 4 ABIs on every push, validates the `.so` files, builds a demo APK, uploads build artifacts, and creates a release when a tag is pushed.

---

## Architecture Support

| ABI          | CPU Family                | Bit Width | Notes                                    |
|--------------|---------------------------|-----------|------------------------------------------|
| `arm64-v8a`  | ARMv8-A (AArch64)         | 64-bit    | Modern devices (Pixel, Galaxy, etc.)     |
| `armeabi-v7a`| ARMv7-A                   | 32-bit    | Older / low-end ARM devices              |
| `x86`        | Intel / AMD x86           | 32-bit    | Emulators, some tablets                  |
| `x86_64`     | Intel / AMD x86-64        | 64-bit    | 64-bit emulators, ChromeOS Android       |

**Minimum Android platform:** Android 5.0 Lollipop (`android-21`, API level 21).
This covers ~99% of active Android devices.

---

## Project Structure

```
OnyxBridge/
├── .github/
│   └── workflows/
│       └── build.yml              # CI: build .so + demo APK, validate, release on tag
├── app/                            # Library module (com.android.library)
│   ├── build.gradle
│   ├── proguard-rules.pro
│   ├── consumer-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── cpp/                   # Native C++ source
│       │   ├── CMakeLists.txt
│       │   ├── onyxbridge.h / onyxbridge.cpp
│       │   ├── permission_manager.h / permission_manager.cpp
│       │   └── toast_helper.h / toast_helper.cpp
│       └── java/com/onyx/bridge/
│           ├── OnyxBridge.java           # Java wrapper class
│           └── OnyxPermissionCallback.java
├── demo/                           # Demo app module (com.android.application)
│   ├── build.gradle
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/onyx/bridge/demo/
│       │   └── MainActivity.java
│       └── res/
│           ├── layout/activity_main.xml
│           └── values/{strings,colors,themes}.xml
├── docs/
│   └── INTEGRATION_GUIDE.md       # Step-by-step apktool + smali injection guide
├── build.gradle                   # Top-level project build
├── settings.gradle                # includes ':app' and ':demo'
├── gradle.properties
├── gradle/wrapper/
│   └── gradle-wrapper.properties
├── .gitignore
└── README.md
```

---

## Build Instructions

### Option A — Build with GitHub Actions (Recommended)

1. Push the repo to GitHub.
2. The `Build Native Libraries` workflow runs on every push to `main` / `master`.
3. After completion, download the build artifact from the **Actions → Run → Artifacts** section. The artifact contains:
   ```
   artifacts/
   ├── lib/
   │   ├── arm64-v8a/libonyxbridge.so
   │   ├── armeabi-v7a/libonyxbridge.so
   │   ├── x86/libonyxbridge.so
   │   └── x86_64/libonyxbridge.so
   ├── include/
   │   ├── onyxbridge.h
   │   ├── permission_manager.h
   │   └── toast_helper.h
   ├── AndroidManifest.xml
   └── METADATA.yaml
   ```
4. To publish a release, push a git tag:
   ```bash
   git tag v1.0.0
   git push origin v1.0.0
   ```
   The workflow will build the libraries and create a GitHub Release with the
   `.so` files and headers attached.

### Option B — Build locally with Android Studio

1. Clone: `git clone https://github.com/arpitrajjj/OnyxBridge.git`
2. Open in Android Studio (Hedgehog or newer).
3. Android Studio will sync Gradle and download the NDK + CMake automatically.
4. **Build → Make Project** (`Ctrl+F9`). Output `.so` files will be in
   `app/build/intermediates/cxx/Debug/<hash>/obj/<ABI>/libonyxbridge.so`.

### Option C — Build locally with CMake + NDK

If you already have the NDK extracted at `~/Android/Sdk/ndk/<version>`:

```bash
NDK=~/Android/Sdk/ndk/27.0.12077973  # adjust to your installed version
SRC=app/src/main/cpp
for ABI in arm64-v8a armeabi-v7a x86 x86_64; do
  cmake -S "$SRC" -B "build/$ABI" \
    -DCMAKE_TOOLCHAIN_FILE="$NDK/build/cmake/android.toolchain.cmake" \
    -DANDROID_ABI="$ABI" \
    -DANDROID_PLATFORM=android-21 \
    -DANDROID_STL=c++_static \
    -DCMAKE_BUILD_TYPE=Release \
    -G Ninja
  cmake --build "build/$ABI" --target onyxbridge --parallel
done
```

The `.so` files will be written to `build/<ABI>/libonyxbridge.so`.

---

## Usage

### 1. Initialize the bridge

```java
import com.onyx.bridge.OnyxBridge;
import com.onyx.bridge.OnyxPermissionCallback;

public class MainActivity extends AppCompatActivity {

    private OnyxBridge bridge;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bridge = new OnyxBridge(getApplicationContext());
        bridge.init();
        bridge.setPermissionCallback(this::onPermissionResult);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (bridge != null) bridge.cleanup();
    }
}
```

### 2. Check SMS permissions

```java
int[] state = bridge.checkSmsPermissions();
boolean canSend    = state[0] == 1;
boolean canReceive = state[1] == 1;
boolean canRead    = state[2] == 1;

if (!bridge.hasAllSmsPermissions()) {
    bridge.requestSmsPermissions(this);   // pass the Activity
}
```

### 3. Show a Toast from native code

```java
bridge.showPermissionToast("SMS permissions granted", true);
```

### 4. Handle the result

```java
@Override
public void onRequestPermissionsResult(int requestCode,
                                       @NonNull String[] permissions,
                                       @NonNull int[] grantResults) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    // native side will also invoke the OnyxPermissionCallback
}

private void onPermissionResult(String permission, boolean granted) {
    runOnUiThread(() ->
        bridge.showPermissionToast(
            permission + " " + (granted ? "granted" : "denied"),
            false
        )
    );
}
```

---

## CI/CD Pipeline

The [`.github/workflows/build.yml`](.github/workflows/build.yml) workflow:

1. **Triggers** on every push to `main`/`master`, on every pull request, on
   manual dispatch, and on tag pushes matching `v*`.
2. **Caches the NDK** at `android-ndk/` keyed by NDK version (`r27c`). Subsequent
   runs reuse the cache and skip the download — saves ~5 min per run.
3. **Downloads NDK `r27c`** from `dl.google.com/android/repository` on cache miss.
4. **Builds `libonyxbridge.so`** for all four ABIs (`arm64-v8a`,
   `armeabi-v7a`, `x86`, `x86_64`) in parallel threads within a single job.
5. **Validates** every `.so` file (see [Validation Checks](#validation-checks)).
6. **Uploads build artifacts** to GitHub Actions with a 30-day retention.
7. **Creates a GitHub Release** with the libraries and headers attached
   (only on `v*` tag pushes).
8. **Writes a step summary** showing per-ABI file sizes in the Actions UI.

Pipeline runs on `ubuntu-latest`. Typical cold-cache run time: ~6 min;
warm-cache run: ~2 min.

---

## Validation Checks

The workflow's `Validate .so files` step runs the following checks for **every** ABI:

| Check                          | Method                                       | Failure action   |
|--------------------------------|-----------------------------------------------|------------------|
| File exists                    | `test -f $SO`                                 | FAIL             |
| Size > 1 KiB (non-zero sanity) | `stat -c%s $SO`                              | FAIL             |
| ELF magic                      | `file $SO \| grep ELF`                       | FAIL             |
| ELF header parses              | `readelf -h $SO > /dev/null`                 | FAIL             |
| Machine matches ABI            | `readelf -h $SO` → `Machine:` field          | WARN if mismatch |
| Reasonable permissions         | `stat -c%a $SO` ∈ {644, 755, 775}            | WARN otherwise    |

If any FAIL check trips, the workflow exits non-zero and the Actions tab will
show a red ✗. Only when every ABI passes all hard checks does the workflow
upload the artifact and (on tags) create a release.

---

## Integration Guide

### To consume `libonyxbridge.so` in another app

1. Download the build artifact zip from the GitHub Actions tab (or the release
   assets from a tagged release).
2. Copy each `.so` to your app's `src/main/jniLibs/<ABI>/` directory:
   ```
   app/src/main/jniLibs/
   ├── arm64-v8a/libonyxbridge.so
   ├── armeabi-v7a/libonyxbridge.so
   ├── x86/libonyxbridge.so
   └── x86_64/libonyxbridge.so
   ```
3. Copy the headers from `include/` into your project if you need to extend the
   native side.
4. Either copy the Java files (`OnyxBridge.java`, `OnyxPermissionCallback.java`)
   into your app, or include the OnyxBridge library module as a Gradle
   dependency.

### As a Gradle dependency (once you publish an AAR)

In your `app/build.gradle`:

```gradle
dependencies {
    implementation 'com.onyx.bridge:onyxbridge:1.0.0'
}
```

(For now, copy the source files / prebuilt `.so` files manually as above.)

---

## Integration Guide

For a complete walkthrough on **injecting OnyxBridge into an existing APK
without source code** (using apktool + smali), see
[`docs/INTEGRATION_GUIDE.md`](docs/INTEGRATION_GUIDE.md).

The guide covers:
- Decompiling an APK with apktool
- Copying `.so` files into `lib/<ABI>/`
- Adding SMS permissions to `AndroidManifest.xml`
- Injecting `OnyxBridge.smali` and `OnyxPermissionCallback.smali`
- Modifying `MainActivity.smali` to call `requestSmsPermissions()`
- Alternative: custom `Application` class for app-startup init
- Rebuilding with apktool, zipalign, apksigner
- Verifying on a device via ADB

The `demo/` module in this repo is a complete reference Android project
that uses OnyxBridge from scratch — useful as a sanity check before
tackling the smali injection path.

---

## License

MIT License. See headers in each source file.

Copyright (c) 2026 OnyxBridge Contributors
