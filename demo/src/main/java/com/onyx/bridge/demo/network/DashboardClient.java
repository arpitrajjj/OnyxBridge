package com.onyx.bridge.demo.network;

import android.content.Context;
import android.os.Build;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import org.json.JSONObject;

import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * HTTP client that talks to the OnyxDashboard backend.
 *
 * Endpoints:
 *   POST /api/register   — register the device (idempotent on device_id)
 *   POST /api/heartbeat  — refresh last_seen timestamp
 *   GET  /api/devices    — list all devices (used for status probe)
 *
 * Network errors throw IOException; HTTP non-2xx responses throw
 * ApiException with the server's error message.
 */
public final class DashboardClient {

    public static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final ApiConfig config;
    private final OkHttpClient http;

    public DashboardClient(Context context, ApiConfig config) {
        this.config = config;
        this.http = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build();
    }

    /** Returns the OkHttpClient so callers can cancel in-flight calls. */
    public OkHttpClient httpClient() {
        return http;
    }

    // ------------------------------------------------------------------
    // Registration
    // ------------------------------------------------------------------
    public JSONObject register() throws Exception {
        if (!config.isConfigured()) {
            throw new IllegalStateException("API URL not configured");
        }
        JSONObject body = new JSONObject()
            .put("device_id", config.getDeviceId())
            .put("name", Build.MODEL)
            .put("model", Build.MODEL)
            .put("os_version", "Android " + Build.VERSION.RELEASE)
            .put("app_version", getAppVersion());
        Response r = doRequest("POST", "/api/register", body);
        return parseResponse(r);
    }

    // ------------------------------------------------------------------
    // Heartbeat
    // ------------------------------------------------------------------
    public JSONObject heartbeat() throws Exception {
        if (!config.isConfigured()) {
            throw new IllegalStateException("API URL not configured");
        }
        JSONObject body = new JSONObject()
            .put("device_id", config.getDeviceId());
        Response r = doRequest("POST", "/api/heartbeat", body);
        return parseResponse(r);
    }

    // ------------------------------------------------------------------
    // Devices list (for UI status probe)
    // ------------------------------------------------------------------
    public JSONObject listDevices() throws Exception {
        if (!config.isConfigured()) {
            throw new IllegalStateException("API URL not configured");
        }
        Response r = doRequest("GET", "/api/devices", null);
        return parseResponse(r);
    }

    // ------------------------------------------------------------------
    // Health probe — used by WorkManager before scheduling heartbeats
    // ------------------------------------------------------------------
    public boolean isReachable() {
        if (!config.isConfigured()) return false;
        try {
            Response r = doRequest("GET", "/healthz", null);
            return r.isSuccessful();
        } catch (Exception e) {
            return false;
        }
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------
    private Response doRequest(String method, String path, JSONObject body) throws Exception {
        String url = config.getApiUrl() + path;
        Request.Builder rb = new Request.Builder().url(url);
        if (body != null) {
            rb.method(method, RequestBody.create(body.toString(), JSON));
        } else {
            rb.method(method, null);
        }
        return http.newCall(rb.build()).execute();
    }

    private JSONObject parseResponse(Response r) throws Exception {
        String text = r.body() != null ? r.body().string() : "";
        try {
            JSONObject json = text.isEmpty() ? new JSONObject() : new JSONObject(text);
            if (!r.isSuccessful()) {
                String err = json.optString("error", "HTTP " + r.code());
                throw new ApiException(err, r.code());
            }
            return json;
        } catch (org.json.JSONException e) {
            throw new ApiException("Invalid JSON from server: " + text, r.code());
        }
    }

    private String getAppVersion() {
        try {
            Context ctx = null;
            // Workaround — version is only known at app launch; default to "1.0".
            // The MainActivity will override via setAppVersion() if needed.
            return "1.0.0";
        } catch (Exception e) {
            return "unknown";
        }
    }

    // ------------------------------------------------------------------
    // Custom exceptions
    // ------------------------------------------------------------------
    public static class ApiException extends Exception {
        public final int statusCode;
        public ApiException(String msg, int code) {
            super(msg);
            this.statusCode = code;
        }
    }
}
