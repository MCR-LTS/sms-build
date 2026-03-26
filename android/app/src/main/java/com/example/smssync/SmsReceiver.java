package com.example.smssync;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.telephony.SmsMessage;
import android.util.Log;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SmsReceiver extends BroadcastReceiver {

    private static final String TAG = "SmsReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction() != null && intent.getAction().equals("android.provider.Telephony.SMS_RECEIVED")) {
            Bundle bundle = intent.getExtras();
            if (bundle != null) {
                // 解析短信内容
                Object[] pdus = (Object[]) bundle.get("pdus");
                if (pdus != null) {
                    for (Object pdu : pdus) {
                        SmsMessage smsMessage = SmsMessage.createFromPdu((byte[]) pdu);
                        String sender = smsMessage.getDisplayOriginatingAddress();
                        String messageBody = smsMessage.getMessageBody();

                        Log.d(TAG, "收到 " + sender + " 的短信: " + messageBody);

                        // 提取验证码
                        String code = extractVerificationCode(messageBody);

                        if (code != null) {
                            Log.d(TAG, "验证码: " + code);
                            sendToComputer(context, code);
                        }
                    }
                }
            }
        }
    }

    private String extractVerificationCode(String message) {
        Pattern pattern = Pattern.compile("(?<!\\d)\\d{4,6}(?!\\d)");
        Matcher matcher = pattern.matcher(message);

        if (matcher.find()) {
            return matcher.group();
        }
        return null;
    }

    private void sendToComputer(Context context, final String code) {
        SharedPreferences prefs = context.getSharedPreferences("SmsSyncPrefs", Context.MODE_PRIVATE);
        final String ip = prefs.getString("server_ip", null);

        if (ip == null || ip.isEmpty()) {
            Log.e(TAG, "你忘了设置电脑 IP 地址");
            return;
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Socket socket = new Socket(ip, 8888);
                    PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                    out.println(code); // 发送验证码
                    out.close();
                    socket.close();
                    Log.d(TAG, "验证码已发送");
                } catch (Exception e) {
                    Log.e(TAG, "发送失败: " + e.getMessage());
                }
            }
        }).start();
    }
}
