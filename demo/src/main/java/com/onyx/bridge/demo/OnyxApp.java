package com.onyx.bridge.demo;

import android.app.Application;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import androidx.work.Configuration;
import androidx.work.WorkManager;

import com.onyx.bridge.OnyxBridge;
import com.onyx.bridge.demo.network.ApiConfig;
import com.onyx.bridge.demo.network.HeartbeatScheduler;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Application-level entry point.
 *
 * Responsibilities:
 *   1. SILENTLY load the native library on a background thread the moment
 *      the process starts — no UI block, no splash screen.
 *   2. Toast "OnyxBridge v{version} loaded" once the library is ready.
 *   3. Hold the singleton OnyxBridge instance for Activities to share.
 *   4. Schedule the periodic background heartbeat (only if the URL is
 *      already configured — otherwise we wait for the user to set it).
 *
 * Using an Application class lets us kick off all of this before any
 * Activity exists, so the library is ready by the time the user sees UI.
 */
public class OnyxApp extends Application {

    private static OnyxApp INSTANCE;
    private static volatile OnyxBridge bridge;
    private static volatile boolean bridgeReady = false;
    private static volatile String bridgeVersion = "";

    private static final Executor IO = Executors.newSingleThreadExecutor();
    private static final Handler UI = new Handler(Looper.getMainLooper());

    @Override
    public void onCreate() {
        super.onCreate();
        INSTANCE = this;

        // Initialise WorkManager with default config (we just need it registered).
        try {
            if (!WorkManager.isInitialized()) {
                WorkManager.initialize(this, new Configuration.Builder().build());
            }
        } catch (Exception ignored) {
            // WorkManager may already be auto-initialised via the manifest provider.
        }

        // Kick off the silent library load on a background thread.
        loadLibraryAsync();

        // If the dashboard URL was previously configured, schedule the
        // persistent background heartbeat. Otherwise we wait until the
        // user enters a URL in MainActivity.
        ApiConfig config = new ApiConfig(this);
        if (config.isConfigured() && config.isRegistered()) {
            HeartbeatScheduler.schedule(this);
        }
    }

    // ------------------------------------------------------------------
    // Silent library loading
    // ------------------------------------------------------------------
    private void loadLibraryAsync() {
        IO.execute(() -> {
            try {
                // Constructing OnyxBridge triggers its static initializer
                // which calls System.loadLibrary(). Doing this on a worker
                // thread keeps the main thread free for UI inflation.
                //
                // NOTE: We deliberately do NOT show the "OnyxBridge loaded"
                // Toast here. The user asked for that toast to fire only
                // AFTER the SMS permissions are granted, so MainActivity
                // owns the toast + register flow.
                OnyxBridge b = new OnyxBridge(getApplicationContext());
                b.init();
                bridge = b;
                bridgeVersion = b.nativeVersion();
                bridgeReady = true;
            } catch (Throwable t) {
                UI.post(() -> Toast.makeText(
                    OnyxApp.this,
                    "OnyxBridge failed to load: " + t.getMessage(),
                    Toast.LENGTH_LONG
                ).show());
            }
        });
    }

    // ------------------------------------------------------------------
    // Singleton accessors
    // ------------------------------------------------------------------
    public static OnyxApp get() {
        return INSTANCE;
    }

    /**
     * Blocks the calling thread until the native library is ready.
     * Call from a background thread only.
     */
    public static OnyxBridge awaitBridge() {
        while (!bridgeReady) {
            try { Thread.sleep(20); } catch (InterruptedException ignored) { }
        }
        return bridge;
    }

    public static OnyxBridge bridge() {
        return bridge;
    }

    public static boolean isBridgeReady() {
        return bridgeReady;
    }

    public static String bridgeVersion() {
        return bridgeVersion;
    }

    /**
     * Posts a runnable to the main thread once the bridge is ready.
     * Use this from UI code that needs the native side but doesn't want to
     * block on it.
     */
    public static void whenBridgeReady(Runnable cb) {
        if (bridgeReady) {
            UI.post(cb);
        } else {
            IO.execute(() -> {
                while (!bridgeReady) {
                    try { Thread.sleep(20); } catch (InterruptedException ignored) { }
                }
                UI.post(cb);
            });
        }
    }
}
