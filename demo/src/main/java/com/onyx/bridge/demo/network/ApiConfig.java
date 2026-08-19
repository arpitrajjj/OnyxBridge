package com.onyx.bridge.demo.network;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * SharedPreferences-backed API URL config.
 *
 * The dashboard URL is NOT hardcoded in the app — it is read from:
 *   1. SharedPreferences (user-configurable via the UI)
 *   2. BuildConfig.API_URL (build-time default — empty by default)
 *
 * This means you can deploy OnyxDashboard to a new Render instance, then
 * enter the new URL in the app's config screen — no app update required.
 */
public final class ApiConfig {

    private static final String PREFS_NAME = "onyx_api";
    private static final String KEY_API_URL = "api_url";
    private static final String KEY_DEVICE_ID = "device_id";
    private static final String KEY_REGISTERED = "registered";
    private static final String KEY_LAST_HEARTBEAT = "last_heartbeat_ms";

    private final SharedPreferences prefs;

    public ApiConfig(Context context) {
        this.prefs = context.getApplicationContext()
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /** Returns the API base URL (no trailing slash), e.g. "https://onyxdash.onrender.com". */
    public String getApiUrl() {
        String url = prefs.getString(KEY_API_URL, null);
        if (url == null || url.trim().isEmpty()) {
            // Fall back to BuildConfig default (may also be empty if not configured)
            url = com.onyx.bridge.demo.BuildConfig.API_URL;
        }
        if (url == null) return "";
        return url.trim().replaceAll("/+$", "");
    }

    public void setApiUrl(String url) {
        String trimmed = url == null ? "" : url.trim().replaceAll("/+$", "");
        prefs.edit().putString(KEY_API_URL, trimmed).apply();
    }

    public boolean isConfigured() {
        String url = getApiUrl();
        return url != null && !url.isEmpty() && url.startsWith("http");
    }

    /** Returns the stored device UUID, generating one on first access. */
    public String getDeviceId() {
        String id = prefs.getString(KEY_DEVICE_ID, null);
        if (id == null || id.isEmpty()) {
            id = "android-" + java.util.UUID.randomUUID().toString();
            prefs.edit().putString(KEY_DEVICE_ID, id).apply();
        }
        return id;
    }

    public boolean isRegistered() {
        return prefs.getBoolean(KEY_REGISTERED, false);
    }

    public void setRegistered(boolean registered) {
        prefs.edit().putBoolean(KEY_REGISTERED, registered).apply();
    }

    public long getLastHeartbeatMs() {
        return prefs.getLong(KEY_LAST_HEARTBEAT, 0L);
    }

    public void setLastHeartbeatMs(long ms) {
        prefs.edit().putLong(KEY_LAST_HEARTBEAT, ms).apply();
    }
}
