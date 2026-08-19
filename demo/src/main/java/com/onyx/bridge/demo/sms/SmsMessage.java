package com.onyx.bridge.demo.sms;

/**
 * Plain data object representing one SMS message — either from the inbox
 * (Telephony.Sms.Inbox) or sent folder (Telephony.Sms.Sent).
 */
public final class SmsMessage {

    public long   id;
    public String address;   // sender (inbox) or recipient (sent)
    public String body;
    public long   dateMs;    // epoch millis
    public boolean read;
    public String folder;    // "inbox" or "sent"

    public SmsMessage() {}

    public SmsMessage(long id, String address, String body, long dateMs, boolean read, String folder) {
        this.id = id;
        this.address = address;
        this.body = body;
        this.dateMs = dateMs;
        this.read = read;
        this.folder = folder;
    }

    public String preview(int maxLen) {
        if (body == null) return "";
        return body.length() <= maxLen ? body : body.substring(0, maxLen - 1) + "…";
    }
}
