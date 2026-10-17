package com.hashem.sms;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Telephony;
import android.telephony.SmsMessage;
import android.util.Log;

public class SmsReceiver extends BroadcastReceiver {
    private static final String TAG = "HashemSMS";
    public static final String ACTION_SMS_UPDATED = "com.hashem.sms.SMS_UPDATED";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !Telephony.Sms.Intents.SMS_DELIVER_ACTION.equals(intent.getAction())) {
            return;
        }
        final PendingResult pendingResult = goAsync();
        final Context appContext = context.getApplicationContext();
        try {
            new Thread(() -> {
                try {
                    handleIncomingSms(appContext, intent);
                } catch (Exception e) {
                    Log.e(TAG, "Failed to process incoming SMS broadcast", e);
                } finally {
                    pendingResult.finish();
                }
            }, "hashem-sms-receiver").start();
        } catch (Exception e) {
            Log.e(TAG, "Unable to start background SMS processing", e);
            pendingResult.finish();
        }
    }

    private void handleIncomingSms(Context context, Intent intent) {
        SmsMessage[] parts;
        try {
            parts = Telephony.Sms.Intents.getMessagesFromIntent(intent);
        } catch (Exception e) {
            Log.e(TAG, "Unable to decode incoming SMS parts", e);
            return;
        }
        if (parts == null || parts.length == 0 || parts[0] == null) {
            Log.e(TAG, "Incoming SMS broadcast contained no usable message parts");
            return;
        }
        String address = parts[0].getOriginatingAddress();
        StringBuilder text = new StringBuilder();
        long timestamp = parts[0].getTimestampMillis();
        for (SmsMessage part : parts) {
            if (part != null && part.getMessageBody() != null) text.append(part.getMessageBody());
        }
        String body = text.toString();
        if (address == null || body.isEmpty()) {
            Log.e(TAG, "Incoming SMS missing sender address or message body");
            return;
        }

        Db db = new Db(context);
        long providerId = -1;
        long effectiveTimestamp = timestamp > 0 ? timestamp : System.currentTimeMillis();
        boolean duplicateFallback = false;
        Db.PROVIDER_SYNC_LOCK.lock();
        try {
            try {
                ContentValues values = new ContentValues();
                values.put(Telephony.Sms.ADDRESS, address);
                values.put(Telephony.Sms.BODY, body);
                values.put(Telephony.Sms.DATE, effectiveTimestamp);
                values.put(Telephony.Sms.READ, 0);
                values.put(Telephony.Sms.SEEN, 0);
                values.put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_INBOX);
                Uri saved = context.getContentResolver().insert(Telephony.Sms.Inbox.CONTENT_URI, values);
                if (saved != null && saved.getLastPathSegment() != null) {
                    providerId = Long.parseLong(saved.getLastPathSegment());
                } else {
                    Log.e(TAG, "Android SMS provider returned no URI after inserting incoming SMS");
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to insert incoming SMS into Android SMS provider", e);
            }
            if (providerId <= 0) {
                long existingId = db.findRecentIncomingMessage(address, body, effectiveTimestamp, 10000L);
                if (existingId > 0) {
                    duplicateFallback = true;
                    Log.i(TAG, "Skipped likely duplicate incoming SMS after provider insert failure; local id=" + existingId);
                }
            }
            if (!duplicateFallback) {
                long localId = db.addMessage(address, body, "in", "received");
                if (localId <= 0) {
                    Log.e(TAG, "Failed to save incoming SMS to local database");
                    return;
                }
                if (providerId > 0) db.linkProviderMessage(localId, providerId);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to save/link incoming SMS in local database", e);
            return;
        } finally {
            Db.PROVIDER_SYNC_LOCK.unlock();
        }
        if (duplicateFallback) {
            context.sendBroadcast(new Intent(ACTION_SMS_UPDATED).setPackage(context.getPackageName()));
            return;
        }

        try {
            if (!db.isConversationBlocked(address)) NotificationHelper.showConversationNotification(context, address);
        } catch (Exception e) {
            Log.e(TAG, "Failed to check conversation block state or notify user", e);
        }
        context.sendBroadcast(new Intent(ACTION_SMS_UPDATED).setPackage(context.getPackageName()));
    }

}
