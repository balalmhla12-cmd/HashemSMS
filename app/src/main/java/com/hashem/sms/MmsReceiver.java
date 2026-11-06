package com.hashem.sms;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.telephony.SmsManager;
import android.telephony.SubscriptionManager;
import android.util.Log;
import androidx.core.content.FileProvider;
import java.io.File;
import java.util.concurrent.atomic.AtomicInteger;

/** Receives WAP Push MMS notifications and delegates retrieval to the carrier-aware platform stack. */
public final class MmsReceiver extends BroadcastReceiver {
    private static final String TAG = "HashemSMS";
    static final String ACTION_MMS_DOWNLOAD = "com.hashem.sms.ACTION_MMS_DOWNLOAD";
    static final String EXTRA_DESTINATION = "mms_destination";
    static final String EXTRA_TRANSACTION = "mms_transaction";
    static final String EXTRA_CONTENT_LOCATION = "mms_content_location";
    private static final AtomicInteger REQUESTS = new AtomicInteger();

    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null || !"android.provider.Telephony.WAP_PUSH_DELIVER".equals(intent.getAction())) return;
        final PendingResult pending = goAsync();
        final Context app = context.getApplicationContext();
        try {
            SmsSending.RECEIVER_EXECUTOR.execute(() -> {
                try { receiveAndDownload(app, intent); }
                catch (Exception e) { Log.e(TAG, "Unable to process incoming MMS notification", e); }
                finally { pending.finish(); }
            });
        } catch (RuntimeException e) {
            Log.e(TAG, "Unable to queue incoming MMS notification", e);
            pending.finish();
        }
    }

    private void receiveAndDownload(Context context, Intent intent) throws Exception {
        byte[] pdu = intent.getByteArrayExtra("data");
        if (pdu == null) pdu = intent.getByteArrayExtra("pdu");
        MmsPduParser.Notification notification = MmsPduParser.parseNotification(pdu);
        if (isAlreadyProcessed(context, notification.transactionId)) {
            Log.i(TAG, "Ignoring duplicate MMS transaction " + notification.transactionId);
            return;
        }
        File dir = new File(context.getCacheDir(), "mms");
        if (!dir.exists() && !dir.mkdirs()) throw new java.io.IOException("Cannot create MMS cache directory");
        File destination = File.createTempFile("incoming-", ".mms", dir);
        Uri destinationUri = FileProvider.getUriForFile(context,
                context.getPackageName() + ".fileprovider", destination);
        int subscriptionId = intent.getIntExtra(SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX, -1);
        SmsManager manager;
        if (subscriptionId >= 0 && android.os.Build.VERSION.SDK_INT >= 22) {
            manager = SmsManager.getSmsManagerForSubscriptionId(subscriptionId);
        } else {
            manager = SmsManager.getDefault();
        }
        int request = Math.abs(REQUESTS.incrementAndGet()) + (int) (System.currentTimeMillis() & 0x3fffffff);
        Intent result = new Intent(context, MmsDownloadReceiver.class)
                .setAction(ACTION_MMS_DOWNLOAD + "." + request)
                .putExtra(EXTRA_DESTINATION, destination.getAbsolutePath())
                .putExtra(EXTRA_TRANSACTION, notification.transactionId)
                .putExtra(EXTRA_CONTENT_LOCATION, notification.contentLocation);
        PendingIntent callback = PendingIntent.getBroadcast(context, request, result,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        try {
            // Null overrides intentionally let Android apply the active carrier's MMS configuration.
            manager.downloadMultimediaMessage(context, notification.contentLocation,
                    destinationUri, (Bundle) null, callback);
        } catch (RuntimeException e) {
            destination.delete();
            throw e;
        }
    }

    static boolean isAlreadyProcessed(Context context, String transactionId) {
        return context.getSharedPreferences("received_mms_transactions", Context.MODE_PRIVATE)
                .getBoolean(key(transactionId), false);
    }

    static void markProcessed(Context context, String transactionId) {
        context.getSharedPreferences("received_mms_transactions", Context.MODE_PRIVATE)
                .edit().putBoolean(key(transactionId), true).apply();
    }

    private static String key(String transactionId) {
        return "tx_" + Integer.toHexString(transactionId.hashCode()) + "_" + transactionId.length();
    }
}
