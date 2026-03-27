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

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;

public class MainActivity extends AppCompatActivity {
    private static final String PREF_NAME = "SmsSyncPrefs";
    private static final String KEY_SERVER_IP = "server_ip";
    private static final int REQUEST_SMS_PERMISSION = 1;
    private static final int SERVER_PORT = 8888;
    private static final int DISCOVERY_PORT = 9999;
    private static final int DISCOVERY_TIMEOUT = 3000;
    private static final String DISCOVER_MESSAGE = "SMSSYNC_DISCOVER";
    private static final String SERVER_RESPONSE_PREFIX = "SMSSYNC_SERVER";

    private EditText ipInput;
    private TextView statusText;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ipInput = findViewById(R.id.ip_input);
        Button discoverBtn = findViewById(R.id.discover_btn);
        Button saveBtn = findViewById(R.id.save_btn);
        statusText = findViewById(R.id.status_text);
        prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);

        loadSavedIp();
        checkPermissions();

        discoverBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startAutoDiscover();
            }
        });

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

        saveServerIp(ip);
        sendTestMessage(ip);
    }

    private void startAutoDiscover() {
        updateStatus("正在寻找电脑...", 0xFF0066CC);

        new Thread(new Runnable() {
            @Override
            public void run() {
                final String serverIp = discoverServerIp();

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (serverIp == null) {
                            updateStatus("未找到电脑", 0xFFFF6666);
                            showToast("未找到电脑，请确认和电脑在同一网络", Toast.LENGTH_LONG);
                            return;
                        }

                        ipInput.setText(serverIp);
                        saveServerIp(serverIp);
                        updateStatus("找到电脑: " + serverIp, 0xFF00AA00);
                        showToast("已找到并严肃填写", Toast.LENGTH_SHORT);
                    }
                });
            }
        }).start();
    }

    private String discoverServerIp() {
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setBroadcast(true);
            socket.setSoTimeout(DISCOVERY_TIMEOUT);

            sendDiscoveryPackets(socket);
            return waitForDiscoveryResponse(socket);
        } catch (Exception e) {
            return null;
        }
    }

    private void sendDiscoveryPackets(DatagramSocket socket) {
        Set<String> sentAddresses = new HashSet<>();

        sendDiscoveryPacket(socket, "255.255.255.255", sentAddresses);

        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();
                if (!networkInterface.isUp() || networkInterface.isLoopback()) {
                    continue;
                }

                for (InterfaceAddress interfaceAddress : networkInterface.getInterfaceAddresses()) {
                    InetAddress broadcast = interfaceAddress.getBroadcast();
                    if (broadcast instanceof Inet4Address) {
                        sendDiscoveryPacket(socket, broadcast.getHostAddress(), sentAddresses);
                    }
                }
            }
        } catch (Exception e) {
        }
    }

    private void sendDiscoveryPacket(DatagramSocket socket, String address, Set<String> sentAddresses) {
        if (!sentAddresses.add(address)) {
            return;
        }

        try {
            byte[] data = DISCOVER_MESSAGE.getBytes(StandardCharsets.UTF_8);
            DatagramPacket packet = new DatagramPacket(
                    data,
                    data.length,
                    InetAddress.getByName(address),
                    DISCOVERY_PORT
            );
            socket.send(packet);
        } catch (Exception e) {
        }
    }

    private String waitForDiscoveryResponse(DatagramSocket socket) {
        byte[] buffer = new byte[256];

        while (true) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                String response = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8).trim();
                String serverIp = parseServerIp(response);
                if (serverIp != null) {
                    return serverIp;
                }
            } catch (SocketTimeoutException e) {
                return null;
            } catch (Exception e) {
                return null;
            }
        }
    }

    private String parseServerIp(String response) {
        String[] parts = response.split("\\|");
        if (parts.length != 3) {
            return null;
        }

        if (!SERVER_RESPONSE_PREFIX.equals(parts[0])) {
            return null;
        }

        return parts[1];
    }

    private void saveServerIp(String ip) {
        prefs.edit().putString(KEY_SERVER_IP, ip).apply();
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
