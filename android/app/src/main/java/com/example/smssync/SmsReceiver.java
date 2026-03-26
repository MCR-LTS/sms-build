package com.example.smssync;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
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
        if (!"android.provider.Telephony.SMS_RECEIVED".equals(intent.getAction())) {
            return;
        }

        Bundle bundle = intent.getExtras();
        if (bundle == null) {
            return;
        }

        Object[] pdus = (Object[]) bundle.get("pdus");
        if (pdus == null || pdus.length == 0) {
            return;
        }

        String format = bundle.getString("format");
        String sender = null;
        StringBuilder messageBuilder = new StringBuilder();

        for (Object pdu : pdus) {
            if (!(pdu instanceof byte[])) {
                continue;
            }

            SmsMessage smsMessage;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                smsMessage = SmsMessage.createFromPdu((byte[]) pdu, format);
            } else {
                smsMessage = SmsMessage.createFromPdu((byte[]) pdu);
            }

            if (smsMessage == null) {
                continue;
            }

            if (sender == null) {
                sender = smsMessage.getDisplayOriginatingAddress();
            }
            messageBuilder.append(smsMessage.getMessageBody());
        }

        String messageBody = messageBuilder.toString().trim();
        if (messageBody.isEmpty()) {
            Log.e(TAG, "短信内容为空");
            return;
        }

        Log.d(TAG, "收到 " + sender + " 的短信: " + messageBody);

        String code = extractVerificationCode(messageBody);
        String contentToSend = code != null ? code : messageBody;

        if (code != null) {
            Log.d(TAG, "验证码: " + code);
        }

        sendToComputer(context, contentToSend);
    }

    private String extractVerificationCode(String message) {
        Pattern pattern = Pattern.compile("(?<!\\d)\\d{4,6}(?!\\d)");
        Matcher matcher = pattern.matcher(message);

        if (matcher.find()) {
            return matcher.group();
        }
        return null;
    }

    private void sendToComputer(Context context, final String content) {
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
                    out.println(content);
                    out.close();
                    socket.close();
                    Log.d(TAG, "短信已发送");
                } catch (Exception e) {
                    Log.e(TAG, "发送失败: " + e.getMessage());
                }
            }
        }).start();
    }
}
