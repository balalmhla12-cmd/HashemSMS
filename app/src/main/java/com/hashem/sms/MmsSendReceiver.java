package com.hashem.sms;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.widget.Toast;
import java.io.File;

public final class MmsSendReceiver extends BroadcastReceiver {
    private static final String TAG = "HashemSMSMmsSendReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        long id = intent.getLongExtra("id", -1L);
        if (id <= 0) return;
        final int resultCode = getResultCode();
        final String pduPath = intent.getStringExtra("pdu_path");
        final Context app = context.getApplicationContext();
        final PendingResult pending = goAsync();
        try {
            SmsSending.RECEIVER_EXECUTOR.execute(() -> {
                try {
                    process(app, id, resultCode, pduPath);
                } catch (Exception e) {
                    Log.e(TAG, "Unable to process MMS send callback", e);
                } finally {
                    pending.finish();
                }
            });
        } catch (RuntimeException e) {
            Log.e(TAG, "Unable to queue MMS send callback", e);
            pending.finish();
        }
    }

    private void process(Context context, long id, int resultCode, String pduPath) {
        boolean success = resultCode == Activity.RESULT_OK;
        Db db = new Db(context);
        db.status(id, success ? "تم الإرسال" : "لم يتم الإرسال");
        db.updateBulkRecipientStatus(id, success ? "تم الإرسال" : "لم يتم الإرسال");
        if (pduPath != null && !pduPath.trim().isEmpty()) {
            try { new File(pduPath).delete(); }
            catch (RuntimeException e) { Log.w(TAG, "Unable to delete temporary MMS PDU", e); }
        }
        context.sendBroadcast(new Intent(SmsReceiver.ACTION_SMS_UPDATED).setPackage(context.getPackageName()));
        SmsSending.postToast(context, success ? "تم إرسال رسالة MMS" : "فشل إرسال رسالة MMS", Toast.LENGTH_SHORT);
    }
}
