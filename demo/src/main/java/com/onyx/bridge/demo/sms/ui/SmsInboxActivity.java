package com.onyx.bridge.demo.sms.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.onyx.bridge.demo.R;
import com.onyx.bridge.demo.sms.SmsMessage;
import com.onyx.bridge.demo.sms.SmsReader;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Inbox / Sent / All SMS list with filter tabs.
 *
 * Reads from Telephony.Sms in a background thread (READ_SMS permission
 * must be granted). Falls back to an empty state if denied.
 */
public class SmsInboxActivity extends AppCompatActivity {

    private enum Tab { INBOX, SENT, ALL }

    private Tab currentTab = Tab.INBOX;
    private SmsReader reader;
    private SmsAdapter adapter;
    private TextView tvEmpty;
    private TextView tvHeader;
    private final Executor IO = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sms_inbox);

        reader = new SmsReader(this);
        adapter = new SmsAdapter();

        RecyclerView list = findViewById(R.id.rv_sms);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);

        tvEmpty  = findViewById(R.id.tv_empty);
        tvHeader = findViewById(R.id.tv_header);

        Button bInbox = findViewById(R.id.btn_tab_inbox);
        Button bSent  = findViewById(R.id.btn_tab_sent);
        Button bAll   = findViewById(R.id.btn_tab_all);
        bInbox.setOnClickListener(v -> { currentTab = Tab.INBOX; refreshTabs(); load(); });
        bSent .setOnClickListener(v -> { currentTab = Tab.SENT;  refreshTabs(); load(); });
        bAll  .setOnClickListener(v -> { currentTab = Tab.ALL;   refreshTabs(); load(); });

        refreshTabs();
        load();
    }

    private void refreshTabs() {
        tvHeader.setText("SMS — " + currentTab.name().toLowerCase());
    }

    private void load() {
        IO.execute(() -> {
            List<SmsMessage> data = new ArrayList<>();
            switch (currentTab) {
                case INBOX: data = reader.readInbox(200); break;
                case SENT:  data = reader.readSent(200);  break;
                case ALL:   data = reader.readAll(200);   break;
            }
            final List<SmsMessage> finalData = data;
            runOnUiThread(() -> {
                adapter.submit(finalData);
                tvEmpty.setVisibility(finalData.isEmpty() ? View.VISIBLE : View.GONE);
                tvEmpty.setText(finalData.isEmpty()
                    ? "No messages in " + currentTab.name().toLowerCase()
                    : "");
            });
        });
    }
}
