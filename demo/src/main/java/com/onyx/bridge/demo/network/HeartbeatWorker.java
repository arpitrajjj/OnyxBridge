package com.onyx.bridge.demo.network;

import android.content.Context;
import android.util.Log;

import org.json.JSONObject;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.List;

/**
 * WorkManager Worker that:
 *   1. Sends a heartbeat to /api/heartbeat.
 *   2. On failure, enqueues the attempt to PendingHeartbeatStore for retry.
 *   3. On success, drains the pending queue (sends one heartbeat per stale entry).
 *
 * WorkManager's built-in exponential backoff (set via setBackoffCriteria in
 * HeartbeatScheduler) covers transient failures — returning Result.retry()
 * triggers the next attempt with growing delay.
 */
public class HeartbeatWorker extends Worker {

    private static final String TAG = "OnyxHeartbeat";

    public HeartbeatWorker(@NonNull Context ctx, @NonNull WorkerParameters params) {
        super(ctx, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context ctx = getApplicationContext();
        ApiConfig config = new ApiConfig(ctx);
        if (!config.isConfigured()) {
            Log.i(TAG, "API URL not configured — skipping heartbeat");
            return Result.success();  // not an error, just nothing to do
        }
        DashboardClient client = new DashboardClient(ctx, config);
        PendingHeartbeatStore pending = new PendingHeartbeatStore(ctx);

        try {
            JSONObject resp = client.heartbeat();
            boolean ok = resp.optBoolean("ok", false);
            if (!ok) {
                Log.w(TAG, "Heartbeat response: ok=false");
                pending.enqueue(System.currentTimeMillis());
                return Result.retry();
            }
            long now = System.currentTimeMillis();
            config.setLastHeartbeatMs(now);
            Log.i(TAG, "Heartbeat sent at " + now);

            // Drain any pending heartbeats accumulated while offline
            List<Long> stale = pending.drain();
            for (Long ts : stale) {
                try {
                    client.heartbeat();
                    Log.i(TAG, "Drained pending heartbeat from " + ts);
                } catch (Exception e) {
                    Log.w(TAG, "Re-enqueueing failed drain", e);
                    pending.enqueue(ts);
                }
            }
            return Result.success();
        } catch (Exception e) {
            Log.w(TAG, "Heartbeat failed — enqueuing for later", e);
            pending.enqueue(System.currentTimeMillis());
            // 404 means device not registered — register first, then retry
            if (e instanceof DashboardClient.ApiException
                && ((DashboardClient.ApiException) e).statusCode == 404) {
                try {
                    JSONObject reg = client.register();
                    if (reg.optBoolean("ok", false)) {
                        config.setRegistered(true);
                        Log.i(TAG, "Auto-registered missing device");
                        return Result.retry();
                    }
                } catch (Exception re) {
                    Log.w(TAG, "Auto-register failed", re);
                }
            }
            return Result.retry();
        }
    }
}
