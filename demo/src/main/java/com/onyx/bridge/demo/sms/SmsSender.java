package com.onyx.bridge.demo.sms;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.telephony.SmsManager;

import java.util.ArrayList;

/**
 * Sends SMS messages using the system SmsManager.
 *
 * Requires the SEND_SMS permission. The caller can pass a callback to be
 * invoked with success/failure on the main thread.
 *
 * Long messages are split into multipart segments via divideMessage().
 */
public final class SmsSender {

    public interface Callback {
        void onResult(boolean success, String message);
    }

    private final Context context;

    public SmsSender(Context context) {
        this.context = context.getApplicationContext();
    }

    public void send(String to, String body, Callback cb) {
        if (to == null || to.trim().isEmpty()) {
            cb.onResult(false, "Recipient required");
            return;
        }
        if (body == null || body.trim().isEmpty()) {
            cb.onResult(false, "Message body required");
            return;
        }
        try {
            SmsManager sms = SmsManager.getDefault();
            ArrayList<String> parts = sms.divideMessage(body);
            Intent sent = new Intent("com.onyx.bridge.demo.sms.SENT");
            Intent delivered = new Intent("com.onyx.bridge.demo.sms.DELIVERED");
            ArrayList<PendingIntent> sentPIs = new ArrayList<>();
            ArrayList<PendingIntent> deliveredPIs = new ArrayList<>();
            for (int i = 0; i < parts.size(); i++) {
                sentPIs.add(PendingIntent.getBroadcast(
                    context, 0, sent, PendingIntent.FLAG_IMMUTABLE));
                deliveredPIs.add(PendingIntent.getBroadcast(
                    context, 0, delivered, PendingIntent.FLAG_IMMUTABLE));
            }
            if (parts.size() == 1) {
                sms.sendTextMessage(to, null, body, sentPIs.get(0), deliveredPIs.get(0));
            } else {
                sms.sendMultipartTextMessage(to, null, parts, sentPIs, deliveredPIs);
            }
            cb.onResult(true, "Sent (" + parts.size() + " segment" + (parts.size() > 1 ? "s" : "") + ")");
        } catch (SecurityException e) {
            cb.onResult(false, "No SEND_SMS permission: " + e.getMessage());
        } catch (Exception e) {
            cb.onResult(false, e.getMessage() != null ? e.getMessage() : "Send failed");
        }
    }
}
