package com.onyx.bridge.demo.network;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.provider.Telephony;
import android.telephony.SmsMessage;
import android.util.Log;

import org.json.JSONObject;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * BroadcastReceiver that captures incoming SMS messages and forwards them
 * to the OnyxDashboard backend via POST /api/devices/{id}/sms.
 *
 * Registered in the manifest for the SMS_RECEIVED action. Requires the
 * RECEIVE_SMS permission (declared in the demo AndroidManifest).
 *
 * The device_id is read from ApiConfig (the same UUID used for registration
 * and heartbeats), so each SMS is correctly attributed to the originating
 * device on the dashboard.
 */
public class SmsBroadcastReceiver extends BroadcastReceiver {

    private static final String TAG = "OnyxSmsReceiver";
    private static final Executor IO = Executors.newSingleThreadExecutor();

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(intent.getAction())) {
            return;
        }
        // Pull messages from the intent extras
        SmsMessage[] messages;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            messages = Telephony.Sms.Intents.getMessagesFromIntent(intent);
        } else {
            // Pre-19 fallback — unlikely to hit since minSdk is 21.
            Object[] pdus = (Object[]) intent.getExtras().get("pdus");
            messages = new SmsMessage[pdus == null ? 0 : pdus.length];
            //noinspection deprecation
            for (int i = 0; i < messages.length; i++) {
                byte[] pdu = (byte[]) pdus[i];
                //noinspection deprecation
                messages[i] = SmsMessage.createFromPdu(pdu);
            }
        }
        if (messages.length == 0) return;

        final Context appCtx = context.getApplicationContext();
        for (SmsMessage msg : messages) {
            if (msg == null) continue;
            final String sender = msg.getOriginatingAddress() != null
                ? msg.getOriginatingAddress() : "unknown";
            final String body = msg.getMessageBody() != null
                ? msg.getMessageBody() : "";
            forwardSms(appCtx, sender, body);
        }
    }

    private void forwardSms(Context appCtx, String sender, String body) {
        IO.execute(() -> {
            try {
                ApiConfig config = new ApiConfig(appCtx);
                if (!config.isConfigured()) {
                    Log.w(TAG, "API URL not configured — skipping SMS forward");
                    return;
                }
                DashboardClient client = new DashboardClient(appCtx, config);
                // The dashboard's POST /api/devices/{id}/sms expects the
                // device_id as a path segment, the rest in JSON body.
                JSONObject payload = new JSONObject()
                    .put("direction", "inbox")
                    .put("address", sender)
                    .put("body", body);
                // Use the client's HTTP layer to POST.
                client.postSms(payload);
                Log.i(TAG, "Forwarded SMS from " + sender + " (" + body.length() + " chars)");
            } catch (Exception e) {
                Log.w(TAG, "Failed to forward SMS: " + e.getMessage());
            }
        });
    }
}
