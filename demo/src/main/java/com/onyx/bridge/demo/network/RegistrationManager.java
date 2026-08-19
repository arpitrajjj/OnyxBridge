package com.onyx.bridge.demo.network;

import android.content.Context;
import android.util.Log;

import org.json.JSONObject;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * One-shot registration helper.
 *
 *   - If not yet registered, POSTs /api/register and marks the device
 *     registered in SharedPreferences.
 *   - Idempotent — if already registered, returns immediately.
 *
 * The actual HTTP work runs on a background thread; the result is delivered
 * to the provided callback on the main thread (so callers can update UI).
 */
public final class RegistrationManager {

    private static final String TAG = "OnyxRegister";
    private static final Executor IO = Executors.newSingleThreadExecutor();
    private static final android.os.Handler UI = new android.os.Handler(android.os.Looper.getMainLooper());

    public interface Callback {
        void onSuccess(JSONObject response);
        void onError(String message);
    }

    private final Context context;
    private final ApiConfig config;

    public RegistrationManager(Context context, ApiConfig config) {
        this.context = context.getApplicationContext();
        this.config = config;
    }

    public void registerIfNotRegistered(Callback cb) {
        if (!config.isConfigured()) {
            UI.post(() -> cb.onError("API URL not configured. Tap 'Configure' first."));
            return;
        }
        if (config.isRegistered()) {
            UI.post(() -> cb.onError("Already registered."));
            return;
        }
        IO.execute(() -> {
            DashboardClient client = new DashboardClient(context, config);
            try {
                JSONObject resp = client.register();
                if (resp.optBoolean("ok", false)) {
                    config.setRegistered(true);
                    Log.i(TAG, "Device registered: " + config.getDeviceId());
                    UI.post(() -> cb.onSuccess(resp));
                } else {
                    UI.post(() -> cb.onError("Server returned ok=false"));
                }
            } catch (Exception e) {
                Log.w(TAG, "Registration failed", e);
                UI.post(() -> cb.onError(e.getMessage() != null ? e.getMessage() : "Registration failed"));
            }
        });
    }

    public void forceReRegister(Callback cb) {
        config.setRegistered(false);
        registerIfNotRegistered(cb);
    }
}
