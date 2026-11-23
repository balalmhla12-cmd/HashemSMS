package com.hashem.sms;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public final class SentReceiver extends BroadcastReceiver {
    private static final String TAG = "HashemSMSSentReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        long id = intent.getLongExtra("messageId", intent.getLongExtra("id", -1L));
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

    void process(Context context, long messageId, int partCount, int partIndex,
                 int resultCode, String ignoredTag, String address) {
        boolean success = resultCode == Activity.RESULT_OK;
        SmsPartResultTracker.Result result = SmsPartResultTracker.record(
                context, "sent", messageId, partIndex, partCount, success);
        if (!result.accepted || !result.complete) return;

        Db db = new Db(context);
        boolean finalSuccess = !result.failed;
        applyFinalResult(context, db, messageId, finalSuccess);
        db.updateBulkRecipientStatus(messageId,
                finalSuccess ? "تم الإرسال" : "لم يتم الإرسال");

        context.sendBroadcast(new Intent(SmsReceiver.ACTION_SMS_UPDATED)
                .setPackage(context.getPackageName()));
        if (finalSuccess && address != null && !address.trim().isEmpty()
                && NotificationHelper.isConversationNotificationActive(context, address)) {
            NotificationHelper.showConversationNotification(context, address);
        }
    }
}
