package com.onyx.bridge.demo.sms.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.onyx.bridge.demo.R;
import com.onyx.bridge.demo.sms.SmsMessage;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * RecyclerView adapter for the SMS list — renders a row per SmsMessage with
 * sender, preview, timestamp, and read/unread indicator.
 */
public final class SmsAdapter extends RecyclerView.Adapter<SmsAdapter.VH> {

    private final List<SmsMessage> items = new ArrayList<>();
    private final SimpleDateFormat tf = new SimpleDateFormat("MMM d, HH:mm", Locale.US);

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_sms, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        SmsMessage m = items.get(position);
        h.address.setText(m.address == null || m.address.isEmpty() ? "(unknown)" : m.address);
        h.body.setText(m.preview(80));
        h.time.setText(tf.format(new Date(m.dateMs)));
        h.folder.setText(m.folder);
        int dotColor = h.unread.getResources().getColor(
            m.read ? android.R.color.darker_gray : android.R.color.holo_green_dark);
        h.unread.setTextColor(dotColor);
        h.unread.setText(m.read ? "" : "●");
        // Highlight sent messages with a different folder badge color
        if ("sent".equalsIgnoreCase(m.folder)) {
            h.folder.setTextColor(h.folder.getResources().getColor(android.R.color.holo_blue_dark));
        } else {
            h.folder.setTextColor(h.folder.getResources().getColor(android.R.color.holo_green_dark));
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public void submit(List<SmsMessage> list) {
        items.clear();
        if (list != null) items.addAll(list);
        notifyDataSetChanged();
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView address;
        final TextView body;
        final TextView time;
        final TextView folder;
        final TextView unread;

        VH(@NonNull View v) {
            super(v);
            address = v.findViewById(R.id.tv_addr);
            body    = v.findViewById(R.id.tv_body);
            time    = v.findViewById(R.id.tv_time);
            folder  = v.findViewById(R.id.tv_folder);
            unread  = v.findViewById(R.id.tv_unread);
        }
    }
}
