# OnyxBridge — Integration Guide for Existing APKs (No Source)

This guide walks you through injecting OnyxBridge into an existing Android
APK where you no longer have the source code. We use **apktool** to decompile
the APK to smali, inject the OnyxBridge Java wrapper class as smali, modify
`MainActivity.smali` to call the bridge in `onCreate()`, then rebuild and
sign the APK.

The end result: when the target app launches, it requests `SEND_SMS`,
`RECEIVE_SMS`, and `READ_SMS` permissions and shows a Toast from native
C++ code reporting the result.

---

## Table of Contents

1. [Prerequisites](#1-prerequisites)
2. [Step 1 — Download OnyxBridge native libraries](#step-1--download-onyxbridge-native-libraries)
3. [Step 2 — Decompile the target APK with apktool](#step-2--decompile-the-target-apk-with-apktool)
4. [Step 3 — Copy `.so` files into `lib/` folders](#step-3--copy-so-files-into-lib-folders)
5. [Step 4 — Add SMS permissions to AndroidManifest.xml](#step-4--add-sms-permissions-to-androidmanifestxml)
6. [Step 5 — Inject the OnyxBridge wrapper class as smali](#step-5--inject-the-onyxbridge-wrapper-class-as-smali)
7. [Step 6 — Modify MainActivity.smali](#step-6--modify-mainactivitysmali)
8. [Step 7 — Alternative: Custom Application class](#step-7--alternative-custom-application-class)
9. [Step 8 — Rebuild the APK with apktool](#step-8--rebuild-the-apk-with-apktool)
10. [Step 9 — Sign the APK](#step-9--sign-the-apk)
11. [Step 10 — Install and verify](#step-10--install-and-verify)
12. [Reference — Java wrapper code](#reference--java-wrapper-code)
13. [Reference — Smali code blocks](#reference--smali-code-blocks)
14. [Troubleshooting](#troubleshooting)

---

## 1. Prerequisites

| Tool       | Version  | Install                                          |
|------------|----------|--------------------------------------------------|
| apktool    | ≥ 2.9.3  | https://apktool.org/                             |
| zipalign   | latest   | Android SDK build-tools                          |
| apksigner  | latest   | Android SDK build-tools                          |
| keytool    | any JDK  | Bundled with any JDK                             |
| ADB        | latest   | Android SDK platform-tools                       |
| Bash/POSIX | any      | Linux/macOS/WSL                                  |

```bash
# Debian/Ubuntu quick setup:
sudo apt install default-jre zipalign apksigner adb
sudo curl -L https://bitbucket.org/iBotPeaches/apktool/downloads/apktool_2.9.3.jar \
    -o /usr/local/bin/apktool.jar
echo '#!/bin/sh' | sudo tee /usr/local/bin/apktool
echo 'java -jar /usr/local/bin/apktool.jar "$@"' | sudo tee -a /usr/local/bin/apktool
sudo chmod +x /usr/local/bin/apktool
```

---

## Step 1 — Download OnyxBridge native libraries

Two options:

### A) Download from the GitHub Release

```bash
# Get the bundle (preserves lib/<ABI>/libonyxbridge.so structure):
curl -L -o onyxbridge.tar.gz \
  https://github.com/arpitrajjj/OnyxBridge/releases/download/v1.0.0/OnyxBridge-v1.0.0.tar.gz
tar xzf onyxbridge.tar.gz
ls jniLibs/
# arm64-v8a  armeabi-v7a  x86  x86_64
```

### B) Download from the Actions build artifacts

1. Go to https://github.com/arpitrajjj/OnyxBridge/actions
2. Click any green-checked run.
3. Scroll to **Artifacts** → download `onyxbridge-native-<sha>.zip`.
4. Unzip — you get `lib/<ABI>/libonyxbridge.so`.

```bash
unzip onyxbridge-native-*.zip
ls lib/
# arm64-v8a  armeabi-v7a  x86  x86_64
```

---

## Step 2 — Decompile the target APK with apktool

```bash
# Decompile (smali + resources + manifest, all editable)
apktool d target.apk -o target_src

# Output:
# I: Using Apktool ...
# I: Loading resource table...
# I: Decoding AndroidManifest.xml with resources...
# I: Decoding file-resources...
# I: Baksmaling classes.dex...
# I: Copying assets and libs...
```

Inspect the structure:

```bash
cd target_src
ls
# AndroidManifest.xml   apktool.yml   assets   lib   original   res   smali   unknown
```

Note: if the APK has multiple dex files, you'll see `smali`, `smali_classes2`,
`smali_classes3`, etc. Pick the first one (`smali`) for our injected class.

---

## Step 3 — Copy `.so` files into `lib/` folders

```bash
# From the target_src directory
mkdir -p lib/arm64-v8a lib/armeabi-v7a lib/x86 lib/x86_64

# Copy the .so files you extracted in Step 1
cp /path/to/jniLibs/arm64-v8a/libonyxbridge.so   lib/arm64-v8a/
cp /path/to/jniLibs/armeabi-v7a/libonyxbridge.so lib/armeabi-v7a/
cp /path/to/jniLibs/x86/libonyxbridge.so         lib/x86/
cp /path/to/jniLibs/x86_64/libonyxbridge.so      lib/x86_64/

# Verify
ls -la lib/*/libonyxbridge.so
```

If the original APK already had a `lib/` directory, your `.so` files live
alongside the original ones — no conflict unless the target was also called
`libonyxbridge.so` (unlikely).

---

## Step 4 — Add SMS permissions to AndroidManifest.xml

Open `target_src/AndroidManifest.xml` and add these `<uses-permission>` lines
inside the top-level `<manifest>` element (alongside any existing
permissions):

```xml
<uses-permission android:name="android.permission.SEND_SMS" />
<uses-permission android:name="android.permission.RECEIVE_SMS" />
<uses-permission android:name="android.permission.READ_SMS" />
```

---

## Step 5 — Inject the OnyxBridge wrapper class as smali

The Java wrapper class `com.onyx.bridge.OnyxBridge` needs to exist in the
target APK. Since we can't ship a `.class` file via apktool (apktool recompiles
from smali), we have to provide the wrapper as smali.

Create the directory:

```bash
mkdir -p smali/com/onyx/bridge
```

Then save each of the following files inside `smali/com/onyx/bridge/`.

### File: `smali/com/onyx/bridge/OnyxBridge.smali`

```smali
.class public Lcom/onyx/bridge/OnyxBridge;
.super Ljava/lang/Object;

# static fields
.field public static final REQUEST_CODE_SMS:I = 0xa1

.field public static final RECEIVE_SMS:Ljava/lang/String; = "android.permission.RECEIVE_SMS"

.field public static final READ_SMS:Ljava/lang/String; = "android.permission.READ_SMS"

.field public static final SEND_SMS:Ljava/lang/String; = "android.permission.SEND_SMS"


# instance fields
.field private callback:Lcom/onyx/bridge/OnyxPermissionCallback;

.field private context:Landroid/content/Context;


# direct methods
.method static constructor <clinit>()V
    .locals 1

    const-string v0, "onyxbridge"

    invoke-static {v0}, Ljava/lang/System;->loadLibrary(Ljava/lang/String;)V

    return-void
.end method

.method public constructor <init>(Landroid/content/Context;)V
    .locals 0

    invoke-direct {p0}, Ljava/lang/Object;-><init>()V

    if-nez p1, :cond_0

    new-instance v0, Ljava/lang/IllegalArgumentException;

    const-string v1, "Context must not be null"

    invoke-direct {v0, v1}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v0

    :cond_0
    invoke-interface {p1}, Landroid/content/Context;->getApplicationContext()Landroid/content/Context;

    move-result-object p1

    iput-object p1, p0, Lcom/onyx/bridge/OnyxBridge;->context:Landroid/content/Context;

    return-void
.end method

.method private native nativeInit(Ljava/lang/Object;)V
.end method

.method private native nativeCleanup()V
.end method

.method private native nativeVersion()Ljava/lang/String;
.end method


# virtual methods
.method public native requestSmsPermissions(I)V
.end method

.method public native showPermissionToastNative(Ljava/lang/String;Z)V
.end method

.method public native checkPermissionNative(Ljava/lang/String;)Z
.end method

.method private native checkSmsPermissionsNative()[I
.end method

.method public init()V
    .locals 1

    iget-object v0, p0, Lcom/onyx/bridge/OnyxBridge;->context:Landroid/content/Context;

    invoke-direct {p0, v0}, Lcom/onyx/bridge/OnyxBridge;->nativeInit(Ljava/lang/Object;)V

    return-void
.end method

.method public cleanup()V
    .locals 0

    invoke-direct {p0}, Lcom/onyx/bridge/OnyxBridge;->nativeCleanup()V

    return-void
.end method

.method public nativeVersion()Ljava/lang/String;
    .locals 1

    invoke-direct {p0}, Lcom/onyx/bridge/OnyxBridge;->nativeVersion()Ljava/lang/String;

    move-result-object v0

    return-object v0
.end method

.method public requestSmsPermissions(Landroid/app/Activity;)V
    .locals 1

    if-nez p1, :cond_0

    new-instance v0, Ljava/lang/IllegalArgumentException;

    const-string v1, "Activity must not be null"

    invoke-direct {v0, v1}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v0

    :cond_0
    invoke-direct {p0, p1}, Lcom/onyx/bridge/OnyxBridge;->nativeInit(Ljava/lang/Object;)V

    const/16 v0, 0xa1

    invoke-virtual {p0, v0}, Lcom/onyx/bridge/OnyxBridge;->requestSmsPermissions(I)V

    return-void
.end method

.method public checkPermission(Ljava/lang/String;)Z
    .locals 1

    if-eqz p1, :cond_0

    invoke-virtual {p1}, Ljava/lang/String;->isEmpty()Z

    move-result v0

    if-eqz v0, :cond_1

    :cond_0
    const/4 v0, 0x0

    return v0

    :cond_1
    invoke-virtual {p0, p1}, Lcom/onyx/bridge/OnyxBridge;->checkPermissionNative(Ljava/lang/String;)Z

    move-result v0

    return v0
.end method

.method public checkSmsPermissions()[I
    .locals 2

    invoke-direct {p0}, Lcom/onyx/bridge/OnyxBridge;->checkSmsPermissionsNative()[I

    move-result-object v0

    if-nez v0, :cond_0

    const/4 v1, 0x3

    new-array v0, v1, [I

    fill-array-data v0, :array_0

    :cond_0
    return-object v0

    :array_0
    .array-data 4
        0x0
        0x0
        0x0
    .end array-data
.end method

.method public showPermissionToast(Ljava/lang/String;Z)V
    .locals 0

    invoke-virtual {p0, p1, p2}, Lcom/onyx/bridge/OnyxBridge;->showPermissionToastNative(Ljava/lang/String;Z)V

    return-void
.end method

.method public setPermissionCallback(Lcom/onyx/bridge/OnyxPermissionCallback;)V
    .locals 0

    iput-object p1, p0, Lcom/onyx/bridge/OnyxBridge;->callback:Lcom/onyx/bridge/OnyxPermissionCallback;

    return-void
.end method

.method private onPermissionResult(Ljava/lang/String;Z)V
    .locals 1

    iget-object v0, p0, Lcom/onyx/bridge/OnyxBridge;->callback:Lcom/onyx/bridge/OnyxPermissionCallback;

    if-eqz v0, :cond_0

    invoke-interface {v0, p1, p2}, Lcom/onyx/bridge/OnyxPermissionCallback;->onPermissionResult(Ljava/lang/String;Z)V

    :cond_0
    return-void
.end method
```

### File: `smali/com/onyx/bridge/OnyxPermissionCallback.smali`

```smali
.class public interface Lcom/onyx/bridge/OnyxPermissionCallback;
.super Ljava/lang/Object;

# virtual methods
.method public abstract onPermissionResult(Ljava/lang/String;Z)V
.end method
```

> **Important:** the native function names embedded in `libonyxbridge.so`
> are derived from these exact class/method names (e.g.
> `Java_com_onyx_bridge_OnyxBridge_requestSmsPermissions`). Do **not**
> rename the package or any native method — the JNI binding will fail
> with `UnsatisfiedLinkError` at runtime.

---

## Step 6 — Modify MainActivity.smali

Locate the main launcher activity:

```bash
grep -l "android.intent.category.LAUNCHER" AndroidManifest.xml
# the activity's android:name attribute tells you the class path

# Common locations:
ls smali/com/example/MainActivity.smali
# or smali_classes2/com/something/MainActivity.smali
```

Edit the `MainActivity.smali` file:

### 6.1 — Add a private field to hold the bridge instance

At the top of the class, add to the `# instance fields` section:

```smali
.field private onyxBridge:Lcom/onyx/bridge/OnyxBridge;
```

### 6.2 — Inject the bridge initialization into `onCreate()`

Find the `onCreate(Landroid/os/Bundle;)V` method. Immediately after
`invoke-super {p0, p1}, Landroid/app/Activity;->onCreate(Landroid/os/Bundle;)V`
and BEFORE `setContentView(...)`, insert:

```smali
    # --- OnyxBridge injection (begin) ---
    invoke-virtual {p0}, Landroid/app/Activity;->getApplicationContext()Landroid/content/Context;
    move-result-object v0

    new-instance v1, Lcom/onyx/bridge/OnyxBridge;
    invoke-direct {v1, v0}, Lcom/onyx/bridge/OnyxBridge;-><init>(Landroid/content/Context;)V

    iput-object v1, p0, Lcom/yourpackage/MainActivity;->onyxBridge:Lcom/onyx/bridge/OnyxBridge;

    # init() caches the context inside native lib
    invoke-virtual {v1}, Lcom/onyx/bridge/OnyxBridge;->init()V

    # Request SMS permissions (Activity context required)
    invoke-virtual {v1, p0}, Lcom/onyx/bridge/OnyxBridge;->requestSmsPermissions(Landroid/app/Activity;)V
    # --- OnyxBridge injection (end) ---
```

**Replace `Lcom/yourpackage/MainActivity;` with the actual class path of
your target activity** (e.g. `Lcom/example/myapp/MainActivity;`).

### 6.3 — Verify register counts

Look at the `.registers N` (or `.locals N`) line at the top of the
`onCreate` method. You need at least 2 free local registers (`v0` and `v1`).
If the method only declares `.locals 1`, bump it to `.locals 2`.

### 6.4 — (Optional) Cleanup in onDestroy

Add an `onDestroy()` override (or modify the existing one) to release
the native global references:

```smali
.method public onDestroy()V
    .locals 1

    iget-object v0, p0, Lcom/yourpackage/MainActivity;->onyxBridge:Lcom/onyx/bridge/OnyxBridge;

    if-eqz v0, :cond_0

    invoke-virtual {v0}, Lcom/onyx/bridge/OnyxBridge;->cleanup()V

    :cond_0
    invoke-super {p0}, Landroid/app/Activity;->onDestroy()V

    return-void
.end method
```

---

## Step 7 — Alternative: Custom Application class

If you don't want to modify `MainActivity.smali`, you can instead inject a
custom `Application` class that runs on app startup. This is cleaner because
it doesn't touch the launcher activity.

### 7.1 — Create the Application class as smali

Save as `smali/com/onyx/bridge/OnyxApp.smali`:

```smali
.class public Lcom/onyx/bridge/OnyxApp;
.super Landroid/app/Application;

# direct methods
.method public constructor <init>()V
    .locals 0

    invoke-direct {p0}, Landroid/app/Application;-><init>()V

    return-void
.end method


# virtual methods
.method public onCreate()V
    .locals 2

    invoke-super {p0}, Landroid/app/Application;->onCreate()V

    # Create OnyxBridge with our application context
    new-instance v0, Lcom/onyx/bridge/OnyxBridge;

    invoke-direct {v0, p0}, Lcom/onyx/bridge/OnyxBridge;-><init>(Landroid/content/Context;)V

    # init() — caches context inside native lib
    invoke-virtual {v0}, Lcom/onyx/bridge/OnyxBridge;->init()V

    # Load library is automatic via static block in OnyxBridge,
    # but we explicitly call init() here so native side has the context.
    # requestSmsPermissions(Activity) is called later from MainActivity.

    return-void
.end method
```

### 7.2 — Register it in AndroidManifest.xml

Edit `<application>` to add the `android:name` attribute:

```xml
<application
    android:name="com.onyx.bridge.OnyxApp"
    android:icon="..."
    android:label="...">
    ...
</application>
```

If the target app already has a custom Application class, you have two
options:

- **Replace** it (you'd need to migrate its existing logic into `OnyxApp.smali`).
- **Subclass** it — change `OnyxApp.smali`'s `.super` to the target's
  Application class, then update the manifest `android:name` to
  `com.onyx.bridge.OnyxApp`.

---

## Step 8 — Rebuild the APK with apktool

```bash
# From the target_src directory
apktool b -o target_patched.apk

# Output:
# I: Using Apktool ...
# I: Checking whether resources has changed...
# I: Building resources...
# I: Building apk file...
# I: Copying unknown files/dir...
```

If you hit an error like `I: Building resources... FAILED`, edit
`apktool.yml` and set `forceFrameTag: true` and `isFrameworkApk: false`.

The output `target_patched.apk` is **unsigned** — proceed to step 9.

---

## Step 9 — Sign the APK

### 9.1 — Generate a keystore (one-time setup)

```bash
keytool -genkeypair \
    -keystore onyx.keystore \
    -alias onyx \
    -keyalg RSA \
    -keysize 2048 \
    -validity 10000 \
    -storepass changeit \
    -keypass changeit \
    -dname "CN=OnyxBridge,O=OnyxBridge,C=US"
```

### 9.2 — Zip-align the APK

```bash
zipalign -p -f -v 4 target_patched.apk target_aligned.apk
```

### 9.3 — Sign with apksigner (v2+ signing scheme)

```bash
apksigner sign \
    --ks onyx.keystore \
    --ks-key-alias onyx \
    --ks-pass pass:changeit \
    --key-pass pass:changeit \
    --out target_signed.apk \
    target_aligned.apk
```

### 9.4 — Verify the signature

```bash
apksigner verify --print-certs target_signed.apk
# Verifies
# Verified using v1 scheme (JAR signing): false
# Verified using v2 scheme (APK Signature Scheme v2): true
# Verified using v3 scheme (APK Signature Scheme v3): false
```

> **Important:** some Android versions reject v1-only signed APKs. Always
> prefer v2 / v3 signing (default in modern apksigner).

---

## Step 10 — Install and verify

### 10.1 — Install on a device or emulator

```bash
# Make sure the device is visible
adb devices

# Uninstall any prior version with mismatched signing key
adb uninstall <target.package.id>

# Install the patched APK
adb install target_signed.apk
```

### 10.2 — Launch and observe

```bash
# Launch by package
adb shell monkey -p <target.package.id> -c android.intent.category.LAUNCHER 1
```

**Expected behavior:**
- The app starts.
- A native Toast appears: "Requesting SMS permissions…"
- The system SMS permission dialog appears.
- After the user accepts/denies, another native Toast appears:
  `"SEND_SMS granted"` (or `"denied"`, etc.).

### 10.3 — Capture logcat to verify native load

```bash
adb logcat -s OnyxBridge:I | head -40
```

You should see:

```
I/OnyxBridge: OnyxBridge native library loaded (v1.0.0)
I/OnyxBridge: OnyxBridge initialized with context=0x... self=0x...
I/OnyxBridge: requestPermissions: requested 3 permission(s), code=161
I/OnyxBridge: Toast shown: Requesting SMS permissions… (duration=SHORT)
```

### 10.4 — Quick ELF sanity check on the installed APK

```bash
# Pull the installed APK back from the device
adb shell pm path <target.package.id>
# package:/data/app/.../base.apk
adb pull /data/app/.../base.apk installed.apk

# Unzip and verify the .so files survived intact
unzip -p installed.apk lib/arm64-v8a/libonyxbridge.so | file -
# /dev/stdin: ELF 64-bit LSB shared object, ARM aarch64, ...
```

---

## Reference — Java wrapper code

For reference, here's the original Java source that the smali above was
compiled from. You do **not** need this when injecting into an existing
APK (the smali is enough), but it's useful for understanding the API:

```java
package com.onyx.bridge;

public class OnyxBridge {
    public static final String SEND_SMS    = "android.permission.SEND_SMS";
    public static final String RECEIVE_SMS = "android.permission.RECEIVE_SMS";
    public static final String READ_SMS    = "android.permission.READ_SMS";

    static { System.loadLibrary("onyxbridge"); }

    private final Object context;
    private OnyxPermissionCallback callback;

    public OnyxBridge(Object context) {
        this.context = context;
    }
    public native void nativeInit(Object context);
    public native void nativeCleanup();
    public native String nativeVersion();

    public native void requestSmsPermissions(int requestCode);
    public native void showPermissionToastNative(String message, boolean isLong);
    public native boolean checkPermissionNative(String permission);
    public native int[] checkSmsPermissionsNative();

    public void init()      { nativeInit(context); }
    public void cleanup()   { nativeCleanup(); }
    public void showPermissionToast(String msg, boolean isLong) {
        showPermissionToastNative(msg, isLong);
    }
    public boolean checkPermission(String p) {
        return p != null && !p.isEmpty() && checkPermissionNative(p);
    }
    public void requestSmsPermissions(android.app.Activity activity) {
        nativeInit(activity);
        requestSmsPermissions(0xA1);
    }
    public int[] checkSmsPermissions() {
        int[] r = checkSmsPermissionsNative();
        return r == null ? new int[]{0, 0, 0} : r;
    }
    public void setPermissionCallback(OnyxPermissionCallback cb) { this.callback = cb; }
}
```

> The smali in Step 5 is functionally equivalent. Use the smali, not this
> Java source, when integrating via apktool.

---

## Reference — Smali code blocks

### A.1 — Instantiating OnyxBridge

```smali
invoke-virtual {p0}, Landroid/app/Activity;->getApplicationContext()Landroid/content/Context;
move-result-object v0

new-instance v1, Lcom/onyx/bridge/OnyxBridge;
invoke-direct {v1, v0}, Lcom/onyx/bridge/OnyxBridge;-><init>(Landroid/content/Context;)V
```

### A.2 — Calling `init()`

```smali
invoke-virtual {v1}, Lcom/onyx/bridge/OnyxBridge;->init()V
```

### A.3 — Calling `requestSmsPermissions(Activity)`

```smali
invoke-virtual {v1, p0}, Lcom/onyx/bridge/OnyxBridge;->requestSmsPermissions(Landroid/app/Activity;)V
```

### A.4 — Calling `showPermissionToast(String, boolean)`

```smali
const-string v2, "Hello from C++"
const/4 v3, 0x0   # false = short, 1 = long

invoke-virtual {v1, v2, v3}, Lcom/onyx/bridge/OnyxBridge;->showPermissionToast(Ljava/lang/String;Z)V
```

### A.5 — Calling `checkSmsPermissions()` and reading the array

```smali
invoke-virtual {v1}, Lcom/onyx/bridge/OnyxBridge;->checkSmsPermissions()[I
move-result-object v2

# v2 is now int[3] = {send, receive, read}
const/4 v3, 0x0
aget v4, v2, v3     # SEND_SMS state
# 1 = granted, 0 = denied
```

### A.6 — Calling `nativeVersion()`

```smali
invoke-virtual {v1}, Lcom/onyx/bridge/OnyxBridge;->nativeVersion()Ljava/lang/String;
move-result-object v0
# v0 is now the version string, e.g. "1.0.0"
```

---

## Troubleshooting

### `UnsatisfiedLinkError: No implementation found for ...`

- You forgot to copy the `.so` for the device's ABI (most modern phones
  are `arm64-v8a`, but older / cheap devices may need `armeabi-v7a`).
- The smali class name or method name doesn't match the JNI signature.
  Re-check: package must be `com.onyx.bridge`, class name `OnyxBridge`,
  method names exact (case-sensitive).

### `dlopen failed: "libonyxbridge.so" is 32-bit instead of 64-bit`

- The APK only has 32-bit `.so` files but the device process is 64-bit (or
  vice-versa). Make sure all four ABIs are present in `lib/`.

### App crashes immediately on launch

- Check `adb logcat` for the actual exception. Common causes:
  - The smali register count is too low (`.locals N` too small).
  - You placed the injection **after** `setContentView()` and the layout
    references fields that don't exist yet.
  - You forgot to add the `<uses-permission>` lines — the bridge will
    still load, but `requestPermissions()` will silently fail.

### `apktool b` fails with `error: resource linking`

- The target APK's `resources.arsc` references a missing framework.
- Run `apktool if framework.apk` to install the framework, then retry.

### `apksigner verify` fails

- Run `zipalign` BEFORE `apksigner sign`. Apksigner verifies alignment.

### Toast doesn't appear, no error

- The native lib may have failed to find the Toast class. Look for
  `E/OnyxBridge: showToast: Toast class not found` in logcat.
- On some heavily-obfuscated apps, the `android.widget.Toast` class might
  be unreachable from the app's classloader. This is rare — usually the
  system classloader finds it.

### Permission dialog doesn't appear

- The target app's `targetSdkVersion` is < 23, so runtime permissions
  aren't enforced. The system grants SMS permissions at install time and
  `requestPermissions()` is a no-op.
- Check `apktool.yml` for `targetSdkVersion`. If it's < 23, the bridge
  still works — `checkPermission()` returns `true` after install.

### APK installs but immediately crashes on Android 14+

- Android 14 blocks installation of APKs targeting SDK < 23 unless
  flagged. Bump the target SDK in the manifest's `<uses-sdk>` element
  to at least 23 before rebuilding.

---

## End of guide

For the full Java wrapper source, see
[`app/src/main/java/com/onyx/bridge/OnyxBridge.java`](../app/src/main/java/com/onyx/bridge/OnyxBridge.java).

For the demo Android project (which builds a working APK using OnyxBridge
from scratch), see the [`demo/`](../demo/) module.
