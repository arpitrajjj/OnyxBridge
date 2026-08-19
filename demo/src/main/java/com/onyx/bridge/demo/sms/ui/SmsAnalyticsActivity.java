package com.onyx.bridge.demo.sms.ui;

import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.onyx.bridge.demo.R;
import com.onyx.bridge.demo.sms.SmsReader;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Simple SMS analytics dashboard:
 *   - Inbox count
 *   - Sent count
 *   - Top 5 contacts by message volume
 *   - Daily activity for the last 7 days
 *
 * All queries run on a background thread so the activity opens immediately.
 */
public class SmsAnalyticsActivity extends AppCompatActivity {

    private TextView tvInboxCount;
    private TextView tvSentCount;
    private TextView tvTotal;
    private LinearLayout llContacts;
    private LinearLayout llDaily;
    private final Executor IO = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sms_analytics);

        tvInboxCount = findViewById(R.id.tv_inbox_count);
        tvSentCount  = findViewById(R.id.tv_sent_count);
        tvTotal      = findViewById(R.id.tv_total_count);
        llContacts   = findViewById(R.id.ll_contacts);
        llDaily      = findViewById(R.id.ll_daily);

        load();
    }

    private void load() {
        IO.execute(() -> {
            SmsReader reader = new SmsReader(this);
            int inbox = reader.countInbox();
            int sent = reader.countSent();
            List<SmsReader.ContactStat> top = reader.topContacts(5);
            List<SmsReader.DayStat> daily = reader.dailyActivity(7);

            runOnUiThread(() -> {
                tvInboxCount.setText(String.valueOf(inbox));
                tvSentCount.setText(String.valueOf(sent));
                tvTotal.setText(String.valueOf(inbox + sent));

                llContacts.removeAllViews();
                if (top.isEmpty()) {
                    addRow(llContacts, "(no data)", "");
                } else {
                    for (SmsReader.ContactStat c : top) {
                        addRow(llContacts, c.address, c.count + " msgs");
                    }
                }

                llDaily.removeAllViews();
                if (daily.isEmpty()) {
                    addRow(llDaily, "(no recent activity)", "");
                } else {
                    SimpleDateFormat inFmt = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
                    SimpleDateFormat outFmt = new SimpleDateFormat("EEE", Locale.US);
                    for (SmsReader.DayStat d : daily) {
                        try {
                            Date dt = inFmt.parse(d.day);
                            String label = outFmt.format(dt) + " (" + d.day + ")";
                            addRow(llDaily, label, d.count + " msgs");
                        } catch (Exception e) {
                            addRow(llDaily, d.day, d.count + " msgs");
                        }
                    }
                }
            });
        });
    }

    private void addRow(LinearLayout container, String label, String value) {
        TextView row = new TextView(this);
        row.setText(String.format(Locale.US, "%s   %s", label, value));
        row.setPadding(8, 16, 8, 16);
        row.setTextSize(13);
        row.setTextColor(0xFFE6E6E6);
        container.addView(row);
    }
}
