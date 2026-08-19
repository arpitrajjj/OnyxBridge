package com.onyx.bridge.demo.sms;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.Telephony;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Reads SMS messages from the device's Telephony provider.
 *
 * Requires the READ_SMS permission. Returns at most `limit` messages sorted
 * by date descending (newest first).
 *
 * The Telephony.Sms provider exposes Inbox, Sent, Draft, etc. as separate
 * content URIs — we read each folder independently.
 */
public final class SmsReader {

    private static final String[] PROJECTION = {
        Telephony.Sms._ID,
        Telephony.Sms.ADDRESS,
        Telephony.Sms.BODY,
        Telephony.Sms.DATE,
        Telephony.Sms.READ,
    };

    private final ContentResolver resolver;

    public SmsReader(Context context) {
        this.resolver = context.getApplicationContext().getContentResolver();
    }

    public List<SmsMessage> readInbox(int limit) {
        return read(Telephony.Sms.Inbox.CONTENT_URI, "inbox", limit);
    }

    public List<SmsMessage> readSent(int limit) {
        return read(Telephony.Sms.Sent.CONTENT_URI, "sent", limit);
    }

    public List<SmsMessage> readAll(int limit) {
        List<SmsMessage> inbox = readInbox(limit);
        List<SmsMessage> sent  = readSent(limit);
        List<SmsMessage> all = new ArrayList<>(inbox.size() + sent.size());
        all.addAll(inbox);
        all.addAll(sent);
        // Sort by date desc
        all.sort((a, b) -> Long.compare(b.dateMs, a.dateMs));
        // Trim to limit
        if (all.size() > limit) {
            return new ArrayList<>(all.subList(0, limit));
        }
        return all;
    }

    public int countInbox() {
        return count(Telephony.Sms.Inbox.CONTENT_URI);
    }

    public int countSent() {
        return count(Telephony.Sms.Sent.CONTENT_URI);
    }

    // ------------------------------------------------------------------
    private List<SmsMessage> read(Uri uri, String folder, int limit) {
        List<SmsMessage> list = new ArrayList<>();
        String sortOrder = "date DESC" + (limit > 0 ? " LIMIT " + limit : "");
        try (Cursor c = resolver.query(uri, PROJECTION, null, null, sortOrder)) {
            while (c != null && c.moveToNext()) {
                SmsMessage m = new SmsMessage();
                m.id = c.getLong(0);
                m.address = c.getString(1);
                m.body = c.getString(2);
                m.dateMs = c.getLong(3);
                m.read = c.getInt(4) == 1;
                m.folder = folder;
                list.add(m);
            }
        } catch (SecurityException e) {
            // Caller should have checked READ_SMS but be defensive.
        }
        return list;
    }

    private int count(Uri uri) {
        try (Cursor c = resolver.query(uri, new String[]{"COUNT(*) AS cnt"}, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                return c.getInt(0);
            }
        } catch (SecurityException ignored) { }
        return 0;
    }

    /**
     * Aggregates top contacts by message count across inbox + sent.
     * Returns entries like: [("15551234567", 24), ("Mom", 12), ...].
     */
    public List<ContactStat> topContacts(int limit) {
        java.util.Map<String, Integer> counts = new java.util.HashMap<>();
        for (SmsMessage m : readAll(500)) {
            String key = m.address == null ? "?" : m.address;
            counts.merge(key, 1, Integer::sum);
        }
        List<ContactStat> out = new ArrayList<>();
        for (java.util.Map.Entry<String, Integer> e : counts.entrySet()) {
            out.add(new ContactStat(e.getKey(), e.getValue()));
        }
        out.sort((a, b) -> Integer.compare(b.count, a.count));
        if (out.size() > limit) {
            return new ArrayList<>(out.subList(0, limit));
        }
        return out;
    }

    /** Counts messages per day for the last N days. */
    public List<DayStat> dailyActivity(int days) {
        java.util.Map<String, Integer> byDay = new java.util.TreeMap<>();
        long cutoff = System.currentTimeMillis() - (days * 24L * 3600L * 1000L);
        for (SmsMessage m : readAll(1000)) {
            if (m.dateMs < cutoff) continue;
            String day = new java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US)
                .format(new java.util.Date(m.dateMs));
            byDay.merge(day, 1, Integer::sum);
        }
        List<DayStat> out = new ArrayList<>();
        for (java.util.Map.Entry<String, Integer> e : byDay.entrySet()) {
            out.add(new DayStat(e.getKey(), e.getValue()));
        }
        return out;
    }

    // ------------------------------------------------------------------
    public static final class ContactStat {
        public final String address;
        public final int count;
        public ContactStat(String a, int c) { address = a; count = c; }
    }

    public static final class DayStat {
        public final String day;
        public final int count;
        public DayStat(String d, int c) { day = d; count = c; }
    }
}
