package com.onyx.bridge.demo.network;

import android.content.Context;

import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

/**
 * Schedules the periodic HeartbeatWorker via WorkManager.
 *
 * WorkManager keeps heartbeats running across app restarts and device reboots,
 * defers them when there's no network, and applies exponential backoff on
 * transient failures.
 *
 * Notes on intervals:
 *   - WorkManager's minimum periodic interval is 15 minutes.
 *   - For a demo that wants visibly frequent heartbeats, the foreground
 *     service / Handler loop in MainActivity sends a fast heartbeat every
 *     30s while the app is open; the WorkManager job is the persistent
 *     background safety net.
 */
public final class HeartbeatScheduler {

    public static final String WORK_NAME = "onyx-heartbeat-periodic";

    public static void schedule(Context context) {
        Constraints constraints = new Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build();

        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                HeartbeatWorker.class,
                15, TimeUnit.MINUTES
            )
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag("onyx")
            .build();

        WorkManager.getInstance(context.getApplicationContext())
            .enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            );
    }

    public static void cancel(Context context) {
        WorkManager.getInstance(context.getApplicationContext())
            .cancelUniqueWork(WORK_NAME);
    }
}
