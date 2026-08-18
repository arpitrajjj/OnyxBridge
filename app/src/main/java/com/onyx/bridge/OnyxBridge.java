package com.onyx.bridge;

import android.content.Context;
import android.content.pm.PackageManager;

/**
 * OnyxBridge - Android Native Bridge Library.
 *
 * Provides a Java/Kotlin wrapper around the native C++ library
 * ({@code libonyxbridge.so}) that handles SMS permissions
 * ({@code SEND_SMS}, {@code RECEIVE_SMS}, {@code READ_SMS}) and
 * Toast display from native code.
 *
 * <p><b>Usage example:</b>
 * <pre>{@code
 * OnyxBridge bridge = new OnyxBridge();
 * bridge.init(getApplicationContext());
 *
 * // Check SMS permissions
 * int[] smsState = bridge.checkSmsPermissions();
 *
 * // Request SMS permissions (Activity context required)
 * bridge.requestSmsPermissions(REQUEST_CODE);
 *
 * // Show a toast from native code
 * bridge.showPermissionToast("Hello from C++!", true);
 *
 * // Cleanup before destroying
 * bridge.cleanup();
 * }</pre>
 *
 * <p><b>Supported ABIs:</b> arm64-v8a, armeabi-v7a, x86, x86_64.
 */
public class OnyxBridge {

    public static final String SEND_SMS    = "android.permission.SEND_SMS";
    public static final String RECEIVE_SMS = "android.permission.RECEIVE_SMS";
    public static final String READ_SMS    = "android.permission.READ_SMS";

    public static final int REQUEST_CODE_SMS = 0xA1;

    private final Context context;
    private OnyxPermissionCallback callback;

    /* ---- Load native library ---- */
    static {
        System.loadLibrary("onyxbridge");
    }

    public OnyxBridge(Context context) {
        if (context == null) {
            throw new IllegalArgumentException("Context must not be null");
        }
        this.context = context.getApplicationContext();
    }

    /* ---- Lifecycle ---- */

    /**
     * Initialize the native side. Must be called before any other
     * operation. Caches the context reference inside the native lib.
     */
    public void init() {
        nativeInit(this.context);
    }

    /**
     * Release the native-side cached context. Always call this when the
     * bridge is no longer needed (e.g. in {@code onDestroy()}).
     */
    public void cleanup() {
        nativeCleanup();
    }

    /* ---- Public API ---- */

    /**
     * Request SEND_SMS, RECEIVE_SMS and READ_SMS permissions from the
     * user. Requires an Activity context (use the supplied Activity,
     * not {@code getApplicationContext()}).
     *
     * @param activity    Activity to receive the permission dialog.
     * @param requestCode Request code passed to {@code onRequestPermissionsResult}.
     */
    public void requestSmsPermissions(android.app.Activity activity, int requestCode) {
        if (activity == null) {
            throw new IllegalArgumentException("Activity must not be null");
        }
        // Re-init with the activity context so native code can call
        // Activity.requestPermissions() directly.
        nativeInit(activity);
        requestSmsPermissions(requestCode);
    }

    /**
     * Convenience overload that uses the default request code.
     */
    public void requestSmsPermissions(android.app.Activity activity) {
        requestSmsPermissions(activity, REQUEST_CODE_SMS);
    }

    /**
     * Check whether a single permission is granted.
     */
    public boolean checkPermission(String permission) {
        if (permission == null || permission.isEmpty()) {
            return false;
        }
        return checkPermissionNative(permission);
    }

    /**
     * Returns the granted state of all three SMS permissions.
     *
     * @return {@code int[3]} = {send, receive, read} where 1 = granted, 0 = denied.
     */
    public int[] checkSmsPermissions() {
        int[] state = checkSmsPermissionsNative();
        if (state == null) {
            return new int[]{0, 0, 0};
        }
        return state;
    }

    /**
     * Convenience: returns true if all three SMS permissions are granted.
     */
    public boolean hasAllSmsPermissions() {
        int[] s = checkSmsPermissions();
        return s[0] == 1 && s[1] == 1 && s[2] == 1;
    }

    /**
     * Set a callback to receive permission results dispatched from native code.
     */
    public void setPermissionCallback(OnyxPermissionCallback callback) {
        this.callback = callback;
    }

    /**
     * Display a Toast message from native code.
     *
     * @param message Message text.
     * @param isLong  Use Toast.LENGTH_LONG if true.
     */
    public void showPermissionToast(String message, boolean isLong) {
        showPermissionToastNative(message, isLong);
    }

    /* ---- Native declarations ---- */

    private native void nativeInit(Object context);
    private native void nativeCleanup();

    public native void requestSmsPermissions(int requestCode);
    public native void showPermissionToastNative(String message, boolean isLong);
    public native boolean checkPermissionNative(String permission);
    private native int[] checkSmsPermissionsNative();

    public native String nativeVersion();

    /* ---- Callback hook (called from C++) ---- */
    @SuppressWarnings("unused")
    private void onPermissionResult(String permission, boolean granted) {
        if (callback != null) {
            callback.onPermissionResult(permission, granted);
        }
    }
}
