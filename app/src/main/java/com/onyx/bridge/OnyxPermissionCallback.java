package com.onyx.bridge;

/**
 * Callback interface invoked when the native side reports a permission
 * check / request result.
 *
 * <p>Implement this and register it via
 * {@link OnyxBridge#setPermissionCallback(OnyxPermissionCallback)}.</p>
 */
public interface OnyxPermissionCallback {

    /**
     * Called from C++ (via {@link OnyxBridge#onPermissionResult(String, boolean)})
     * whenever a permission check or request completes.
     *
     * @param permission The permission string (e.g.
     *                  {@code android.permission.SEND_SMS}).
     * @param granted   {@code true} if the permission was granted,
     *                  {@code false} otherwise.
     */
    void onPermissionResult(String permission, boolean granted);
}
