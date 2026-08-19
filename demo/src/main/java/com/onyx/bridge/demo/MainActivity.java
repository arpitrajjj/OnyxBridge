package com.onyx.bridge.demo;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

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
 * Entry activity — orchestrates the full app lifecycle.
 *
 *   1. Wait for OnyxApp to silently load the native library (it's already
 *      loading in the background by the time this Activity is created).
 *      The "OnyxBridge vX loaded" Toast is fired from OnyxApp.
 *   2. Check SMS permissions:
 *      - All granted → toast "All permissions granted. Device registering..."
 *      - Some denied → show rationale dialog → requestPermissions
 *      - Still denied → close-app dialog
 *   3. Auto-register with the dashboard once permissions are in place.
 *   4. Schedule the background heartbeat worker.
 *   5. Expose SMS feature buttons: Inbox / Compose / Analytics
 *
 * The dashboard URL is read from SharedPreferences (set in the API URL
 * card). If empty, we prompt the user to enter it before registration.
 */
public class MainActivity extends androidx.appcompat.app.AppCompatActivity {

    private static final int REQ_SMS_PERMS = 0xA1;
    private static final int REQ_APP_SETTINGS = 0xA2;

    private static final Executor IO = Executors.newSingleThreadExecutor();

    private ApiConfig config;
    private DashboardClient client;
    private PendingHeartbeatStore pendingStore;

    private EditText etApiUrl;
    private TextView tvDeviceInfo;
    private TextView tvLog;
    private TextView tvVersion;
    private int denialCount = 0;

    private final OnyxPermissionCallback permissionCallback = (permission, granted) ->
        runOnUiThread(() -> toast(shortPermName(permission) + (granted ? " ✓" : " ✗")));

    // ------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Networking
        config = new ApiConfig(this);
        client = new DashboardClient(this, config);
        pendingStore = new PendingHeartbeatStore(this);

        // Wire UI
        etApiUrl    = findViewById(R.id.et_api_url);
        tvDeviceInfo = findViewById(R.id.tv_device_info);
        tvLog       = findViewById(R.id.tv_log);
        tvVersion   = findViewById(R.id.tv_version);
        tvLog.setMovementMethod(new android.text.method.ScrollingMovementMethod());

        // Pre-fill URL field
        etApiUrl.setText(config.getApiUrl());

        // Wire native bridge (if already loaded, set version immediately;
        // otherwise wait for OnyxApp.whenBridgeReady callback)
        OnyxApp.whenBridgeReady(() -> {
            OnyxBridge bridge = OnyxApp.bridge();
            if (bridge != null) {
                bridge.setPermissionCallback(permissionCallback);
                tvVersion.setText(getString(R.string.version_fmt, OnyxApp.bridgeVersion()));
                // Kick off the permission flow as soon as the bridge is ready.
                // This is the "library loaded → check permissions" transition.
                runOnUiThread(this::checkSmsPermissionsAndProceed);
            }
        });

        // API URL controls
        findViewById(R.id.btn_save_url).setOnClickListener(v -> onSaveUrl());
        findViewById(R.id.btn_health).setOnClickListener(v -> onHealthCheck());

        // Device controls
        findViewById(R.id.btn_register).setOnClickListener(v -> onRegister());
        findViewById(R.id.btn_heartbeat).setOnClickListener(v -> onHeartbeat());

        // SMS feature buttons (these only become useful once permissions
        // are granted, but the buttons exist from the start so the user can
        // see what the app does).
        findViewById(R.id.btn_sms_inbox).setOnClickListener(v ->
            startActivity(new Intent(this, SmsInboxActivity.class)));
        findViewById(R.id.btn_sms_compose).setOnClickListener(v ->
            startActivity(new Intent(this, SmsComposeActivity.class)));
        findViewById(R.id.btn_sms_analytics).setOnClickListener(v ->
            startActivity(new Intent(this, SmsAnalyticsActivity.class)));

        // Back button — app should close when on the main screen
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                finishAffinity();
            }
        });

        updateDeviceInfo();
        appendLog("OnyxDashboard demo app started");
    }

    // ------------------------------------------------------------------
    // SMS permission flow
    // ------------------------------------------------------------------
    private void checkSmsPermissionsAndProceed() {
        OnyxBridge bridge = OnyxApp.bridge();
        if (bridge != null && bridge.hasAllSmsPermissions()) {
            onAllPermissionsGranted();
            return;
        }
        // Show rationale first (Android best practice)
        boolean shouldRationale =
            ActivityCompat.shouldShowRequestPermissionRationale(this, OnyxBridge.SEND_SMS)
         || ActivityCompat.shouldShowRequestPermissionRationale(this, OnyxBridge.RECEIVE_SMS)
         || ActivityCompat.shouldShowRequestPermissionRationale(this, OnyxBridge.READ_SMS)
         || denialCount == 0;

        if (shouldRationale) {
            showRationaleDialog();
        } else {
            // User chose "Don't ask again" — send to settings.
            showSettingsRequiredDialog();
        }
    }

    private void showRationaleDialog() {
        new AlertDialog.Builder(this)
            .setTitle("SMS permissions required")
            .setMessage("SMS permissions are needed for device management and communication. " +
                        "Please grant permissions to continue.")
            .setCancelable(false)
            .setPositiveButton("Grant Permissions", (d, w) ->
                ActivityCompat.requestPermissions(
                    MainActivity.this,
                    new String[]{OnyxBridge.SEND_SMS, OnyxBridge.RECEIVE_SMS, OnyxBridge.READ_SMS},
                    REQ_SMS_PERMS
                ))
            .setNegativeButton("Close App", (d, w) -> finishAffinity())
            .show();
    }

    private void showSettingsRequiredDialog() {
        new AlertDialog.Builder(this)
            .setTitle("SMS permissions required")
            .setMessage("This app cannot function without SMS permissions. " +
                        "Please enable them in app settings to continue.")
            .setCancelable(false)
            .setPositiveButton("Open Settings", (d, w) -> {
                Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                intent.setData(Uri.fromParts("package", getPackageName(), null));
                startActivityForResult(intent, REQ_APP_SETTINGS);
            })
            .setNegativeButton("Close App", (d, w) -> finishAffinity())
            .show();
    }

    private void showFinalDenialDialog() {
        new AlertDialog.Builder(this)
            .setTitle("Permissions denied")
            .setMessage("This app cannot function without SMS permissions. The app will now close.")
            .setCancelable(false)
            .setPositiveButton("Close App", (d, w) -> finishAffinity())
            .show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        OnyxBridge bridge = OnyxApp.bridge();
        boolean allGranted = bridge != null && bridge.hasAllSmsPermissions();

        if (allGranted) {
            onAllPermissionsGranted();
        } else {
            denialCount++;
            if (denialCount >= 2) {
                showFinalDenialDialog();
            } else {
                showSettingsRequiredDialog();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_APP_SETTINGS) {
            // User came back from settings — re-check
            checkSmsPermissionsAndProceed();
        }
    }

    private void onAllPermissionsGranted() {
        toast("All permissions granted. App ready!");
        appendLog("SMS permissions granted");
        // Brief beat so the user sees the "App ready" toast, then register.
        new android.os.Handler().postDelayed(() -> {
            toast("All permissions granted. Device registering...");
            updateDeviceInfo();
            proceedWithRegistration();
        }, 800);
    }

    // ------------------------------------------------------------------
    // Registration
    // ------------------------------------------------------------------
    private void proceedWithRegistration() {
        if (!config.isConfigured()) {
            toast("Set API URL first to register");
            appendLog("Registration deferred — no API URL configured");
            return;
        }
        new RegistrationManager(this, config).forceReRegister(
            new RegistrationManager.Callback() {
                @Override public void onSuccess(JSONObject response) {
                    toast("Device registered successfully");
                    appendLog("Device registered with backend");
                    updateDeviceInfo();
                    HeartbeatScheduler.schedule(MainActivity.this);
                    // Send an immediate heartbeat so the dashboard flips green.
                    sendHeartbeatNow();
                }
                @Override public void onError(String message) {
                    appendLog("Registration failed: " + message);
                    toast("✗ " + message);
                }
            });
    }

    private void onRegister() {
        if (!config.isConfigured()) {
            toast("Save API URL first");
            return;
        }
        proceedWithRegistration();
    }

    private void onHeartbeat() {
        if (!config.isConfigured()) {
            toast("Save API URL first");
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
                    runOnUiThread(() -> {
                        toast("✓ Heartbeat sent");
                        appendLog("Heartbeat @ " + fmtTime(now));
                        updateDeviceInfo();
                    });
                }
            } catch (Exception e) {
                pendingStore.enqueue(System.currentTimeMillis());
                String msg = e.getMessage() != null ? e.getMessage() : "Heartbeat failed";
                runOnUiThread(() -> {
                    appendLog("Heartbeat failed (queued): " + msg);
                    updateDeviceInfo();
                });
            }
        });
    }

    // ------------------------------------------------------------------
    // API URL config
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
        appendLog("API URL set: " + url);
        updateDeviceInfo();
    }

    private void onHealthCheck() {
        if (!config.isConfigured()) {
            toast("Save URL first");
            return;
        }
        IO.execute(() -> {
            boolean ok = client.isReachable();
            runOnUiThread(() -> {
                toast(ok ? "✓ Backend reachable" : "✗ Cannot reach backend");
                appendLog("Health check " + (ok ? "OK" : "FAILED"));
            });
        });
    }

    // ------------------------------------------------------------------
    // UI helpers
    // ------------------------------------------------------------------
    private void updateDeviceInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append("Library     : ");
        sb.append(OnyxApp.isBridgeReady() ? "v" + OnyxApp.bridgeVersion() + " loaded" : "loading…").append('\n');
        sb.append("Device ID   : ").append(config.getDeviceId()).append('\n');
        sb.append("Model       : ").append(android.os.Build.MODEL).append('\n');
        sb.append("OS          : Android ").append(android.os.Build.VERSION.RELEASE).append('\n');
        sb.append("Registered  : ").append(config.isRegistered() ? "YES" : "NO").append('\n');
        sb.append("API URL     : ").append(config.isConfigured() ? config.getApiUrl() : "(not set)").append('\n');
        long hb = config.getLastHeartbeatMs();
        if (hb > 0) {
            sb.append("Last HB     : ").append(fmtTime(hb)).append('\n');
        }
        sb.append("Pending HB  : ").append(pendingStore.size());
        tvDeviceInfo.setText(sb.toString());
    }

    private void appendLog(String msg) {
        String line = fmtTime(System.currentTimeMillis()) + "  " + msg + "\n";
        String current = tvLog.getText().toString();
        if (current.equals("No events yet.") || current.isEmpty()) {
            tvLog.setText(line);
        } else {
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
}
