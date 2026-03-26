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
    private static final String PREF_NAME = "SmsSyncPrefs";
    private static final String KEY_SERVER_IP = "server_ip";
    private static final String SMS_RECEIVED_ACTION = "android.provider.Telephony.SMS_RECEIVED";
    private static final int SERVER_PORT = 8888;
    private static final Pattern CODE_PATTERN = Pattern.compile("(?<!\\d)\\d{4,8}(?!\\d)");

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!SMS_RECEIVED_ACTION.equals(intent.getAction())) {
            return;
        }

        String messageBody = readSmsBody(intent);
        if (messageBody == null) {
            return;
        }

        String code = extractVerificationCode(messageBody);
        if (code == null) {
            Log.d(TAG, "未提取到验证码");
            return;
        }

        Log.d(TAG, "验证码: " + code);
        sendToComputer(context, code);
    }

    private String readSmsBody(Intent intent) {
        SmsMessage[] messages = Telephony.Sms.Intents.getMessagesFromIntent(intent);
        if (messages == null || messages.length == 0) {
            Log.e(TAG, "没有解析到短信内容");
            return null;
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
            return null;
        }

        Log.d(TAG, "收到 " + sender + " 的短信: " + messageBody);
        return messageBody;
    }

    private String extractVerificationCode(String message) {
        Matcher matcher = CODE_PATTERN.matcher(message);

        if (matcher.find()) {
            return matcher.group();
        }
        return null;
    }

    private void sendToComputer(Context context, final String content) {
        final String ip = getServerIp(context);

        if (ip == null || ip.isEmpty()) {
            Log.e(TAG, "你忘了设置电脑 IP 地址");
            return;
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                try (Socket socket = new Socket(ip, SERVER_PORT);
                     PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {
                    out.println(content);
                    Log.d(TAG, "短信已发送");
                } catch (Exception e) {
                    Log.e(TAG, "发送失败: " + e.getMessage());
                }
            }
        }).start();
    }

    private String getServerIp(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_SERVER_IP, null);
    }
}
