package com.onyx.bridge.demo.sms.ui;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.onyx.bridge.demo.R;
import com.onyx.bridge.demo.sms.SmsSender;

/**
 * Compose + send SMS. Takes recipient phone + message body, sends via
 * SmsManager, shows Toast with the result.
 */
public class SmsComposeActivity extends AppCompatActivity {

    private EditText etTo;
    private EditText etBody;
    private TextView tvStatus;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sms_compose);

        etTo     = findViewById(R.id.et_to);
        etBody   = findViewById(R.id.et_body);
        tvStatus = findViewById(R.id.tv_status);

        Button bSend    = findViewById(R.id.btn_send);
        Button bClear   = findViewById(R.id.btn_clear);

        bSend.setOnClickListener(v -> {
            String to = etTo.getText().toString().trim();
            String body = etBody.getText().toString();
            new SmsSender(this).send(to, body, (ok, msg) ->
                runOnUiThread(() -> {
                    tvStatus.setText((ok ? "✓ " : "✗ ") + msg);
                    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
                    if (ok) etBody.setText("");
                }));
        });
        bClear.setOnClickListener(v -> {
            etTo.setText("");
            etBody.setText("");
            tvStatus.setText("");
        });
    }
}
