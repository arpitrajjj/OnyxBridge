package com.onyx.bridge.demo.network;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import org.json.JSONObject;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Foreground service that keeps sending heartbeats every 30s while the app
 * is alive — even when the user has swiped away from the recents list.
 *
 * The WorkManager periodic worker (HeartbeatScheduler) handles the
 * persistent background case (every 15 min minimum). This service fills
 * the gap with a faster cadence while the app process exists.
 *
 * Runs in the foreground with a persistent notification so Android doesn't
 * kill the process under memory pressure.
 */
public class HeartbeatForegroundService extends Service {

    private static final String TAG = "OnyxFgHeartbeat";
    private static final String CHANNEL_ID = "onyx_heartbeat";
    private static final int NOTIFICATION_ID = 0xA1;
    private static final long INTERVAL_MS = 30_000L;

    private final Executor IO = Executors.newSingleThreadExecutor();
    private final Handler tick = new Handler(Looper.getMainLooper());
    private final Runnable tickRunnable = this::sendHeartbeat;

    @Override
    public void onCreate() {
        super.onCreate();
        ensureChannel(this);
        Notification n = buildNotification("OnyxBridge heartbeat active");
        startForeground(NOTIFICATION_ID, n);
        Log.i(TAG, "Foreground heartbeat service started");
        tick.postDelayed(tickRunnable, 2_000L);  // first beat in 2s
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;  // restart if killed
    }

    @Override
    public void onDestroy() {
        tick.removeCallbacks(tickRunnable);
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;  // not bindable
    }

    // ------------------------------------------------------------------
    private void sendHeartbeat() {
        IO.execute(() -> {
            Context ctx = getApplicationContext();
            ApiConfig config = new ApiConfig(ctx);
            if (!config.isConfigured()) {
                // Nothing to send — schedule the next tick and bail.
                tick.postDelayed(tickRunnable, INTERVAL_MS);
                return;
            }
            DashboardClient client = new DashboardClient(ctx, config);
            PendingHeartbeatStore pending = new PendingHeartbeatStore(ctx);
            try {
                // Auto-register if needed (mirrors HeartbeatWorker behaviour)
                if (!config.isRegistered()) {
                    try {
                        JSONObject reg = client.register();
                        if (reg.optBoolean("ok", false)) {
                            config.setRegistered(true);
                            Log.i(TAG, "Auto-registered during foreground heartbeat");
                        }
                    } catch (Exception ignored) { }
                }
                JSONObject resp = client.heartbeat();
                if (resp.optBoolean("ok", false)) {
                    config.setLastHeartbeatMs(System.currentTimeMillis());
                    // Drain any pending queue from offline periods
                    pending.drain();
                }
            } catch (Exception e) {
                Log.w(TAG, "Heartbeat failed: " + e.getMessage());
                pending.enqueue(System.currentTimeMillis());
            } finally {
                // Schedule the next beat regardless of outcome
                tick.postDelayed(tickRunnable, INTERVAL_MS);
            }
        });
    }

    // ------------------------------------------------------------------
    private static void ensureChannel(Context ctx) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;
            NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID,
                "OnyxBridge heartbeat",
                NotificationManager.IMPORTANCE_LOW
            );
            ch.setDescription("Keeps the device online on the OnyxDashboard");
            ch.setShowBadge(false);
            nm.createNotificationChannel(ch);
        }
    }

    private Notification buildNotification(String text) {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("OnyxBridge")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build();
    }
}
