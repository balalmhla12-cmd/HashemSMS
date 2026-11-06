package com.hashem.sms;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

public final class DeliveredReceiver extends BroadcastReceiver {
    private static final String TAG = "HashemSMSDeliveredReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        long id = intent.getLongExtra("id", -1L);
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

    private void process(Context context, long id, int partCount, int partIndex, int resultCode) {
        String status = resultCode == Activity.RESULT_OK ? "تم التسليم" : "لم يتم التسليم";
        Db db = new Db(context);
        synchronized (DeliveredReceiver.class) {
            if (partCount <= 1) {
                db.updateDeliveryStatus(id, status);
            } else {
                SharedPreferences prefs = context.getSharedPreferences("hashem_sms_parts", Context.MODE_PRIVATE);
                String key = String.valueOf(id);
                String doneKey = "delivery_done_" + key;
                String failedKey = "delivery_failed_" + key;
                int done = prefs.getInt(doneKey, 0);
                boolean failed = prefs.getBoolean(failedKey, false) || resultCode != Activity.RESULT_OK;
                if (done >= partCount) return;
                done++;
                if (done >= partCount) {
                    prefs.edit().remove(doneKey).remove(failedKey).commit();
                    db.updateDeliveryStatus(id, failed ? "لم يتم التسليم" : "تم التسليم");
                } else {
                    prefs.edit().putInt(doneKey, done).putBoolean(failedKey, failed).commit();
                }
            }
        }
        context.sendBroadcast(new Intent(SmsReceiver.ACTION_SMS_UPDATED).setPackage(context.getPackageName()));
    }
}
