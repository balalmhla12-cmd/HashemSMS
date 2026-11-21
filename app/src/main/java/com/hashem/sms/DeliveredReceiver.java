package com.hashem.sms;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public final class DeliveredReceiver extends BroadcastReceiver {
    private static final String TAG = "HashemSMSDeliveredReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        long id = intent.getLongExtra("messageId", intent.getLongExtra("id", -1L));
        int partCount = intent.getIntExtra("partCount", 1);
        int partIndex = intent.getIntExtra("partIndex", 0);
        if (id <= 0 || partCount < 1 || partIndex < 0 || partIndex >= partCount) return;

        final int resultCode = getResultCode();
        final Context app = context.getApplicationContext();
        final PendingResult pending = goAsync();
        try {
            SmsSending.RECEIVER_EXECUTOR.execute(() -> {
                try {
                    process(app, id, partCount, partIndex, resultCode);
                } catch (Exception e) {
                    Log.e(TAG, "Unable to process delivery callback", e);
                } finally {
                    pending.finish();
                }
            });
        } catch (RuntimeException e) {
            Log.e(TAG, "Unable to queue delivery callback", e);
            pending.finish();
        }
    }

    void process(Context context, long messageId, int partCount, int partIndex, int resultCode) {
        boolean success = resultCode == Activity.RESULT_OK;
        SmsPartResultTracker.Result result = SmsPartResultTracker.record(
                context, "delivery", messageId, partIndex, partCount, success);
        if (!result.accepted || !result.complete) return;

        Db db = new Db(context);
        db.updateDeliveryStatus(messageId,
                result.failed ? "لم يتم التسليم" : "تم التسليم");
        context.sendBroadcast(new Intent(SmsReceiver.ACTION_SMS_UPDATED)
                .setPackage(context.getPackageName()));
    }
}
