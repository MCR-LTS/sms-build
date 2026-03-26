package com.example.smssync;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.provider.Telephony;
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

        SmsMessage[] messages = Telephony.Sms.Intents.getMessagesFromIntent(intent);
        if (messages == null || messages.length == 0) {
            Log.e(TAG, "没有解析到短信内容");
            return;
        }

        String sender = null;
        StringBuilder messageBuilder = new StringBuilder();

        for (SmsMessage smsMessage : messages) {
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
        if (code == null) {
            Log.d(TAG, "未提取到验证码");
            return;
        }

        Log.d(TAG, "验证码: " + code);
        sendToComputer(context, code);
    }

    private String extractVerificationCode(String message) {
        Pattern pattern = Pattern.compile("(?<!\\d)\\d{4,8}(?!\\d)");
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
