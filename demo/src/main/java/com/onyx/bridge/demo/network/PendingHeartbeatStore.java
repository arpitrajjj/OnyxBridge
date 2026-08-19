package com.onyx.bridge.demo.network;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * File-backed offline heartbeat queue.
 *
 * When a heartbeat POST fails (network down, server 5xx, etc.), the attempt
 * is appended to a JSON array stored at filesDir/pending_heartbeats.json.
 * The next successful heartbeat or app foreground drains the queue.
 *
 * No SQLite — just a small JSON file. Tens of entries max.
 */
public final class PendingHeartbeatStore {

    private static final String FILE_NAME = "pending_heartbeats.json";
    private static final int MAX_QUEUE = 100;

    private final File file;

    public PendingHeartbeatStore(Context context) {
        this.file = new File(context.getApplicationContext().getFilesDir(), FILE_NAME);
    }

    public synchronized void enqueue(long atMs) {
        try {
            JSONArray arr = read();
            if (arr.length() >= MAX_QUEUE) {
                // Drop the oldest to bound growth
                JSONArray trimmed = new JSONArray();
                for (int i = 1; i < arr.length(); i++) {
                    trimmed.put(arr.get(i));
                }
                arr = trimmed;
            }
            arr.put(new JSONObject().put("ts", atMs));
            write(arr);
        } catch (Exception e) {
            // Best-effort — never let a queueing error crash the app
        }
    }

    public synchronized int size() {
        return read().length();
    }

    /** Returns the timestamps of all pending heartbeats, oldest first. */
    public synchronized List<Long> drain() {
        JSONArray arr = read();
        List<Long> out = new ArrayList<>(arr.length());
        for (int i = 0; i < arr.length(); i++) {
            try {
                out.add(arr.getJSONObject(i).getLong("ts"));
            } catch (Exception ignored) { }
        }
        // Clear the queue — caller should send them all and accept any new failures
        try {
            write(new JSONArray());
        } catch (Exception ignored) { }
        return out;
    }

    public synchronized void clear() {
        try {
            write(new JSONArray());
        } catch (Exception ignored) { }
    }

    // ------------------------------------------------------------------
    private JSONArray read() {
        try (FileInputStream in = new FileInputStream(file)) {
            byte[] buf = new byte[(int) file.length()];
            int read = in.read(buf);
            if (read <= 0) return new JSONArray();
            String text = new String(buf, 0, read, StandardCharsets.UTF_8);
            return new JSONArray(text);
        } catch (IOException | org.json.JSONException e) {
            return new JSONArray();
        }
    }

    private void write(JSONArray arr) throws IOException {
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(arr.toString().getBytes(StandardCharsets.UTF_8));
        }
    }
}
