package com.hashem.sms;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

public final class SentReceiver extends BroadcastReceiver {
    private static final String TAG = "HashemSMSSentReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        long id = intent.getLongExtra("id", -1L);
        int partCount = intent.getIntExtra("partCount", 1);
        int partIndex = intent.getIntExtra("partIndex", 0);
        if (id <= 0 || partCount < 1 || partIndex < 0 || partIndex >= partCount) return;

        final int resultCode = getResultCode();
        final String tag = intent.getStringExtra("tag");
        final String address = intent.getStringExtra(NotificationHelper.EXTRA_ADDRESS);
        final Context app = context.getApplicationContext();
        final PendingResult pending = goAsync();
        try {
            SmsSending.RECEIVER_EXECUTOR.execute(() -> {
                try {
                    process(app, id, partCount, partIndex, resultCode, tag, address);
                } catch (Exception e) {
                    Log.e(TAG, "Unable to process sent callback", e);
                } finally {
                    pending.finish();
                }
            });
        } catch (RuntimeException e) {
            Log.e(TAG, "Unable to queue sent callback", e);
            pending.finish();
        }
    }


    static void applyFinalResult(Context context, Db db, long id, boolean success) {
        db.status(id, success ? "تم الإرسال" : "لم يتم الإرسال");
        db.updateSystemProviderStatus(context, id, success);
    }

    private void process(Context context, long id, int partCount, int partIndex,
                         int resultCode, String tag, String address) {
        boolean success = resultCode == Activity.RESULT_OK;
        Db db = new Db(context);
        boolean finished = false;
        synchronized (SentReceiver.class) {
            if (partCount <= 1) {
                applyFinalResult(context, db, id, success);
                db.updateBulkRecipientStatus(id, success ? "تم الإرسال" : "لم يتم الإرسال");
                finished = true;
            } else {
                SharedPreferences prefs = context.getSharedPreferences("hashem_sms_parts", Context.MODE_PRIVATE);
                String key = tag == null ? String.valueOf(id) : tag;
                String doneKey = "sent_done_" + key;
                String failedKey = "sent_failed_" + key;
                int done = prefs.getInt(doneKey, 0);
                boolean failed = prefs.getBoolean(failedKey, false) || !success;
                if (done >= partCount) return;
                done++;
                boolean allDone = done >= partCount;
                prefs.edit().putInt(doneKey, done).putBoolean(failedKey, failed).commit();
                if (allDone) {
                    prefs.edit().remove(doneKey).remove(failedKey).commit();
                    applyFinalResult(context, db, id, !failed);
                    db.updateBulkRecipientStatus(id, failed ? "لم يتم الإرسال" : "تم الإرسال");
                    finished = true;
                }
            }
        }
        if (finished) {
            context.sendBroadcast(new Intent(SmsReceiver.ACTION_SMS_UPDATED).setPackage(context.getPackageName()));
            if (success && address != null && !address.trim().isEmpty()
                    && NotificationHelper.isConversationNotificationActive(context, address)) {
                NotificationHelper.showConversationNotification(context, address);
            }
        }
    }
}
