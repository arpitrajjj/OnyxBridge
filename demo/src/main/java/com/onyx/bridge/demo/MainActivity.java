package com.onyx.bridge.demo;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.onyx.bridge.OnyxBridge;
import com.onyx.bridge.OnyxPermissionCallback;

/**
 * Demo activity for OnyxBridge.
 *
 * Demonstrates the full lifecycle:
 *   init() → requestSmsPermissions() → onPermissionResult() → cleanup()
 *
 * UI:
 *   - Status panel showing per-permission grant state
 *   - "Request SMS Permissions" button
 *   - "Refresh Status" button
 *   - "Show Toast from C++" button
 */
public class MainActivity extends Activity {

    private OnyxBridge bridge;
    private TextView   tvStatus;
    private TextView   tvVersion;
    private Button     btnRequest;
    private Button     btnRefresh;
    private Button     btnToast;

    private final OnyxPermissionCallback callback = this::onPermissionResult;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Wire OnyxBridge
        bridge = new OnyxBridge(getApplicationContext());
        bridge.init();
        bridge.setPermissionCallback(callback);

        // Wire UI
        tvStatus   = findViewById(R.id.tv_status);
        tvVersion  = findViewById(R.id.tv_version);
        btnRequest = findViewById(R.id.btn_request);
        btnRefresh = findViewById(R.id.btn_refresh);
        btnToast   = findViewById(R.id.btn_toast);

        tvVersion.setText(getString(R.string.version_fmt, bridge.nativeVersion()));

        btnRequest.setOnClickListener(this::onRequestClicked);
        btnRefresh.setOnClickListener(v -> {
            updateStatus();
            bridge.showPermissionToast("Status refreshed", false);
        });
        btnToast.setOnClickListener(v ->
            bridge.showPermissionToast("Hello from C++ (Toast via JNI)!", false));

        updateStatus();
    }

    private void onRequestClicked(View v) {
        // Activity context is required for requestPermissions()
        bridge.requestSmsPermissions(this);
        bridge.showPermissionToast("Requesting SMS permissions…", false);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (bridge != null) bridge.cleanup();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String[] permissions,
                                           int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        // Native side will also fire the OnyxPermissionCallback via JNI.
        // Just refresh the status panel as a defensive update.
        updateStatus();
    }

    private void onPermissionResult(String permission, boolean granted) {
        runOnUiThread(() -> {
            updateStatus();
            bridge.showPermissionToast(
                shortPermName(permission) + (granted ? " ✓ granted" : " ✗ denied"),
                false
            );
        });
    }

    private void updateStatus() {
        int[] s = bridge.checkSmsPermissions();
        StringBuilder sb = new StringBuilder();
        sb.append("SMS Permission Status\n\n");
        sb.append(String.format("SEND_SMS     : %s\n", s[0] == 1 ? "✓ GRANTED" : "✗ denied"));
        sb.append(String.format("RECEIVE_SMS  : %s\n", s[1] == 1 ? "✓ GRANTED" : "✗ denied"));
        sb.append(String.format("READ_SMS     : %s\n", s[2] == 1 ? "✓ GRANTED" : "✗ denied"));
        sb.append("\nAll granted: ").append(bridge.hasAllSmsPermissions() ? "YES" : "NO");
        tvStatus.setText(sb.toString());
    }

    private static String shortPermName(String permission) {
        if (permission == null) return "(null)";
        int idx = permission.lastIndexOf('.');
        return idx >= 0 ? permission.substring(idx + 1) : permission;
    }
}
