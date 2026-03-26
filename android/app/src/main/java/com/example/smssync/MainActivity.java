package com.example.smssync;

import android.Manifest;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.net.Socket;

public class MainActivity extends AppCompatActivity {
    private static final String PREF_NAME = "SmsSyncPrefs";
    private static final String KEY_SERVER_IP = "server_ip";
    private static final int REQUEST_SMS_PERMISSION = 1;
    private static final int SERVER_PORT = 8888;

    private EditText ipInput;
    private TextView statusText;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ipInput = findViewById(R.id.ip_input);
        Button saveBtn = findViewById(R.id.save_btn);
        statusText = findViewById(R.id.status_text);
        prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);

        loadSavedIp();
        checkPermissions();

        saveBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveIpAndTest();
            }
        });
    }

    private void loadSavedIp() {
        String savedIp = prefs.getString(KEY_SERVER_IP, "");
        ipInput.setText(savedIp);
    }

    private void saveIpAndTest() {
        String ip = ipInput.getText().toString().trim();
        if (ip.isEmpty()) {
            showToast("请输入 IP 地址", Toast.LENGTH_SHORT);
            return;
        }

        prefs.edit().putString(KEY_SERVER_IP, ip).apply();
        sendTestMessage(ip);
    }

    private void checkPermissions() {
        if (!hasSmsPermissions()) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS},
                    REQUEST_SMS_PERMISSION);
        } else {
            updateStatus("权限已获取", 0xFF00AA00);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_SMS_PERMISSION
                && grantResults.length >= 2
                && grantResults[0] == PackageManager.PERMISSION_GRANTED
                && grantResults[1] == PackageManager.PERMISSION_GRANTED) {
            updateStatus("权限已获取", 0xFF00AA00);
        } else {
            updateStatus("权限被拒绝", 0xFFFF6666);
        }
    }

    private boolean hasSmsPermissions() {
        boolean receiveSmsGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS)
                == PackageManager.PERMISSION_GRANTED;
        boolean readSmsGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS)
                == PackageManager.PERMISSION_GRANTED;
        return receiveSmsGranted && readSmsGranted;
    }

    private void updateStatus(String text, int color) {
        statusText.setText(text);
        statusText.setTextColor(color);
    }

    private void showToast(final String text, final int duration) {
        Toast.makeText(MainActivity.this, text, duration).show();
    }

    private void sendTestMessage(final String ip) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try (Socket socket = new Socket(ip, SERVER_PORT)) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            showToast("连接成功", Toast.LENGTH_SHORT);
                        }
                    });
                } catch (Exception e) {
                    final String error = e.getMessage();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            showToast(error, Toast.LENGTH_LONG);
                        }
                    });
                }
            }
        }).start();
    }
}
