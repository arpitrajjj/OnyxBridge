package com.onyx.bridge.demo;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.work.WorkManager;

import com.onyx.bridge.OnyxBridge;
import com.onyx.bridge.OnyxPermissionCallback;
import com.onyx.bridge.demo.network.ApiConfig;
import com.onyx.bridge.demo.network.DashboardClient;
import com.onyx.bridge.demo.network.HeartbeatScheduler;
import com.onyx.bridge.demo.network.PendingHeartbeatStore;
import com.onyx.bridge.demo.network.RegistrationManager;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * OnyxBridge demo Activity.
 *
 * Surfaces the full system in a single screen:
 *   1. API configuration — edit + save the dashboard URL at runtime,
 *      no app update required when the URL changes.
 *   2. Device registration — one-tap POST to /api/register. Idempotent.
 *   3. Heartbeat — send now (foreground) OR let WorkManager do it every 15min.
 *      Failed heartbeats are queued in PendingHeartbeatStore and drained
 *      on the next successful send.
 *   4. SMS permissions — the original OnyxBridge native flow.
 *   5. Activity log — timestamped events for debugging.
 */
public class MainActivity extends Activity {

    private static final String TAG = "OnyxDemo";
    private static final Executor IO = Executors.newSingleThreadExecutor();
    private static final Handler UI = new Handler(Looper.getMainLooper());

    private OnyxBridge bridge;
    private ApiConfig config;
    private DashboardClient client;
    private PendingHeartbeatStore pendingStore;

    private EditText etApiUrl;
    private TextView tvDeviceInfo;
    private TextView tvStatus;
    private TextView tvLog;
    private TextView tvVersion;

    private final OnyxPermissionCallback callback = this::onPermissionResult;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // --- Wire native bridge ---
        bridge = new OnyxBridge(getApplicationContext());
        bridge.init();
        bridge.setPermissionCallback(callback);

        // --- Wire networking ---
        config = new ApiConfig(this);
        client = new DashboardClient(this, config);
        pendingStore = new PendingHeartbeatStore(this);

        // --- Wire UI ---
        etApiUrl    = findViewById(R.id.et_api_url);
        tvDeviceInfo = findViewById(R.id.tv_device_info);
        tvStatus    = findViewById(R.id.tv_status);
        tvLog       = findViewById(R.id.tv_log);
        tvVersion   = findViewById(R.id.tv_version);
        tvLog.setMovementMethod(new ScrollingMovementMethod());

        // Pre-fill the URL field with the stored value (if any)
        etApiUrl.setText(config.getApiUrl());
        tvVersion.setText(getString(R.string.version_fmt, bridge.nativeVersion()));

        findViewById(R.id.btn_save_url).setOnClickListener(v -> onSaveUrl());
        findViewById(R.id.btn_health).setOnClickListener(v -> onHealthCheck());
        findViewById(R.id.btn_register).setOnClickListener(v -> onRegister());
        findViewById(R.id.btn_heartbeat).setOnClickListener(v -> onHeartbeat());
        findViewById(R.id.btn_request).setOnClickListener(this::onRequestClicked);
        findViewById(R.id.btn_toast).setOnClickListener(v ->
            bridge.showPermissionToast("Hello from C++ (Toast via JNI)!", false));

        // Initial UI refresh
        updateDeviceInfo();
        updateSmsStatus();

        // Kick off the persistent heartbeat worker — survives app restart
        HeartbeatScheduler.schedule(this);

        // If we're already registered, send an immediate heartbeat so the
        // dashboard flips green the moment the app launches.
        if (config.isRegistered()) {
            onHeartbeat();
        }

        appendLog("App started — device " + config.getDeviceId());
    }

    // ------------------------------------------------------------------
    // API URL configuration
    // ------------------------------------------------------------------
    private void onSaveUrl() {
        String url = etApiUrl.getText().toString().trim();
        if (url.isEmpty()) {
            toast("Enter a URL first");
            return;
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://" + url;
            etApiUrl.setText(url);
        }
        config.setApiUrl(url);
        toast("Saved: " + url);
        appendLog("API URL set to " + url);
        updateDeviceInfo();
    }

    private void onHealthCheck() {
        if (!config.isConfigured()) {
            toast("Save a URL first");
            return;
        }
        IO.execute(() -> {
            boolean ok = client.isReachable();
            UI.post(() -> {
                toast(ok ? "✓ Backend reachable" : "✗ Cannot reach backend");
                appendLog("Health check " + (ok ? "OK" : "FAILED"));
            });
        });
    }

    // ------------------------------------------------------------------
    // Registration
    // ------------------------------------------------------------------
    private void onRegister() {
        if (!config.isConfigured()) {
            toast("Save API URL first");
            return;
        }
        new RegistrationManager(this, config).forceReRegister(new RegistrationManager.Callback() {
            @Override public void onSuccess(JSONObject response) {
                toast("✓ Registered");
                appendLog("Registered with backend — id " + config.getDeviceId());
                updateDeviceInfo();
                HeartbeatScheduler.schedule(MainActivity.this);
            }
            @Override public void onError(String message) {
                toast("✗ " + message);
                appendLog("Register failed: " + message);
            }
        });
    }

    // ------------------------------------------------------------------
    // Heartbeat
    // ------------------------------------------------------------------
    private void onHeartbeat() {
        if (!config.isConfigured()) {
            toast("Save API URL first");
            return;
        }
        if (!config.isRegistered()) {
            toast("Register first");
            new RegistrationManager(this, config).registerIfNotRegistered(
                new RegistrationManager.Callback() {
                    @Override public void onSuccess(JSONObject r) {
                        updateDeviceInfo();
                        sendHeartbeatNow();
                    }
                    @Override public void onError(String m) {
                        toast("✗ " + m);
                        appendLog("Auto-register failed: " + m);
                    }
                });
            return;
        }
        sendHeartbeatNow();
    }

    private void sendHeartbeatNow() {
        IO.execute(() -> {
            try {
                JSONObject resp = client.heartbeat();
                if (resp.optBoolean("ok", false)) {
                    long now = System.currentTimeMillis();
                    config.setLastHeartbeatMs(now);
                    UI.post(() -> {
                        toast("✓ Heartbeat sent");
                        appendLog("Heartbeat OK @ " + fmtTime(now));
                        updateDeviceInfo();
                    });
                    // Drain pending queue
                    pendingStore.drain();  // already drained by worker on success
                } else {
                    UI.post(() -> appendLog("Heartbeat response ok=false"));
                }
            } catch (Exception e) {
                pendingStore.enqueue(System.currentTimeMillis());
                String msg = e.getMessage() != null ? e.getMessage() : "Heartbeat failed";
                UI.post(() -> {
                    toast("✗ " + msg);
                    appendLog("Heartbeat failed (queued): " + msg);
                    updateDeviceInfo();
                });
            }
        });
    }

    // ------------------------------------------------------------------
    // SMS permissions (OnyxBridge native flow)
    // ------------------------------------------------------------------
    private void onRequestClicked(View v) {
        bridge.requestSmsPermissions(this);
        bridge.showPermissionToast("Requesting SMS permissions…", false);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        updateSmsStatus();
    }

    private void onPermissionResult(String permission, boolean granted) {
        runOnUiThread(() -> {
            updateSmsStatus();
            bridge.showPermissionToast(
                shortPermName(permission) + (granted ? " ✓ granted" : " ✗ denied"),
                false);
        });
    }

    // ------------------------------------------------------------------
    // UI helpers
    // ------------------------------------------------------------------
    private void updateDeviceInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append("Device ID    : ").append(config.getDeviceId()).append('\n');
        sb.append("Model        : ").append(Build.MODEL).append('\n');
        sb.append("OS           : Android ").append(Build.VERSION.RELEASE).append('\n');
        sb.append("Registered   : ").append(config.isRegistered() ? "YES" : "NO").append('\n');
        sb.append("API URL      : ").append(config.isConfigured() ? config.getApiUrl() : "(not set)").append('\n');
        long hb = config.getLastHeartbeatMs();
        if (hb > 0) {
            sb.append("Last HB      : ").append(fmtTime(hb)).append('\n');
        }
        sb.append("Pending HB   : ").append(pendingStore.size());
        tvDeviceInfo.setText(sb.toString());
    }

    private void updateSmsStatus() {
        int[] s = bridge.checkSmsPermissions();
        StringBuilder sb = new StringBuilder();
        sb.append("SEND_SMS     : ").append(s[0] == 1 ? "✓ GRANTED" : "✗ denied").append('\n');
        sb.append("RECEIVE_SMS  : ").append(s[1] == 1 ? "✓ GRANTED" : "✗ denied").append('\n');
        sb.append("READ_SMS     : ").append(s[2] == 1 ? "✓ GRANTED" : "✗ denied").append('\n');
        sb.append("\nAll granted: ").append(bridge.hasAllSmsPermissions() ? "YES" : "NO");
        tvStatus.setText(sb.toString());
    }

    private void appendLog(String msg) {
        String line = fmtTime(System.currentTimeMillis()) + "  " + msg + "\n";
        String current = tvLog.getText().toString();
        if ("No events yet.".equals(current)) {
            tvLog.setText(line);
        } else {
            // Keep the last ~50 lines
            String next = current + line;
            String[] parts = next.split("\n");
            if (parts.length > 50) {
                StringBuilder sb = new StringBuilder();
                for (int i = parts.length - 50; i < parts.length; i++) {
                    sb.append(parts[i]).append('\n');
                }
                next = sb.toString();
            }
            tvLog.setText(next);
        }
        // Auto-scroll to bottom
        tvLog.post(() -> tvLog.scrollTo(0, tvLog.getHeight()));
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    private static String fmtTime(long ms) {
        return new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date(ms));
    }

    private static String shortPermName(String permission) {
        if (permission == null) return "(null)";
        int idx = permission.lastIndexOf('.');
        return idx >= 0 ? permission.substring(idx + 1) : permission;
    }

    // ------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (bridge != null) bridge.cleanup();
    }
}
