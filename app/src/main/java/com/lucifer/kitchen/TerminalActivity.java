package com.lucifer.kitchen;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;
import com.lucifer.kitchen.utils.ShellExecutor;

public class TerminalActivity extends AppCompatActivity {

    private TextView tvOutput;
    private EditText etCommand;
    private ScrollView scrollView;
    private final StringBuilder outputBuffer = new StringBuilder("$ \n");
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_terminal);

        tvOutput = findViewById(R.id.tvOutput);
        etCommand = findViewById(R.id.etCommand);
        scrollView = findViewById(R.id.scrollView);
        MaterialButton btnSend = findViewById(R.id.btnSend);

        btnSend.setOnClickListener(v -> executeCommand());
        etCommand.setOnEditorActionListener((v, actionId, event) -> {
            executeCommand();
            return true;
        });
    }

    private void executeCommand() {
        String cmd = etCommand.getText().toString().trim();
        if (cmd.isEmpty()) return;
        etCommand.setText("");
        outputBuffer.append("$ ").append(cmd).append("\n");
        tvOutput.setText(outputBuffer.toString());
        scrollToBottom();

        new Thread(() -> {
            String result = ShellExecutor.execute(cmd);
            handler.post(() -> {
                outputBuffer.append(result).append("\n");
                tvOutput.setText(outputBuffer.toString());
                scrollToBottom();
            });
        }).start();
    }

    private void scrollToBottom() {
        scrollView.post(() -> scrollView.fullScroll(ScrollView.FOCUS_DOWN));
    }
}
