package com.example.smssync;

import android.Manifest;
import android.content.Context;
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

import java.io.PrintWriter;
import java.net.Socket;

public class MainActivity extends AppCompatActivity {

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
        prefs = getSharedPreferences("SmsSyncPrefs", MODE_PRIVATE);

        // 加载保存的 IP
        String savedIp = prefs.getString("server_ip", "");
        ipInput.setText(savedIp);
        checkPermissions();

        saveBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String ip = ipInput.getText().toString().trim();
                if (ip.isEmpty()) {
                    Toast.makeText(MainActivity.this, "请输入 IP 地址", Toast.LENGTH_SHORT).show();
                    return;
                }

                // 保存 IP
                prefs.edit().putString("server_ip", ip).apply();
                sendTestMessage(ip);
            }
        });
    }

    private void checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS},
                    1);
        } else {
            statusText.setText("权限已获取");
            statusText.setTextColor(0xFF00AA00); // Green
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            statusText.setText("权限已获取");
            statusText.setTextColor(0xFF00AA00);
        } else {
            statusText.setText("权限被拒绝");
        }
    }

    private void sendTestMessage(final String ip) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Socket socket = new Socket(ip, 8888);
                    PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                    out.println("测试连接成功");
                    out.close();
                    socket.close();
                    
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(MainActivity.this, "连接成功", Toast.LENGTH_SHORT).show();
                        }
                    });
                } catch (Exception e) {
                    final String error = e.getMessage();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(MainActivity.this, error, Toast.LENGTH_LONG).show();
                        }
                    });
                }
            }
        }).start();
    }
}
