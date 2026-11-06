package com.hashem.sms;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.UUID;

/** Persists a successfully downloaded MMS retrieve-conf PDU and its attachments. */
public final class MmsDownloadReceiver extends BroadcastReceiver {
    private static final String TAG = "HashemSMS";
    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null || !MmsReceiver.ACTION_MMS_DOWNLOAD.equals(
                intent.getAction() != null && intent.getAction().contains(".")
                        ? intent.getAction().substring(0, intent.getAction().lastIndexOf('.'))
                        : intent.getAction())) return;
        final PendingResult pending = goAsync();
        final Context app = context.getApplicationContext();
        final int downloadResult = getResultCode();
        try {
            SmsSending.RECEIVER_EXECUTOR.execute(() -> {
                File source = null;
                try {
                    source = new File(intent.getStringExtra(MmsReceiver.EXTRA_DESTINATION));
                    String transaction = intent.getStringExtra(MmsReceiver.EXTRA_TRANSACTION);
                    if (downloadResult != Activity.RESULT_OK) {
                        Log.w(TAG, "Carrier MMS download failed; result=" + downloadResult);
                        NotificationHelper.notifyMmsDownloadFailure(app);
                        return;
                    }
                    if (source == null || !source.isFile() || source.length() < 8
                            || source.length() > MmsPduParser.MAX_PDU_BYTES) {
                        throw new java.io.IOException("Downloaded MMS PDU is missing or outside size limits");
                    }
                    byte[] pdu = readBounded(source);
                    MmsPduParser.Message message = MmsPduParser.parseRetrieved(pdu);
                    Db db = new Db(app);
                    long timestamp = message.dateMillis > 0 ? message.dateMillis : System.currentTimeMillis();
                    long messageId = db.addMessage(message.from,
                            message.text.isEmpty() ? (message.subject.isEmpty() ? "رسالة وسائط متعددة" : message.subject) : message.text,
                            "in", "received");
                    if (messageId <= 0) throw new IllegalStateException("Unable to save incoming MMS message");
                    File attachmentDir = new File(app.getFilesDir(), "mms");
                    if (!attachmentDir.exists() && !attachmentDir.mkdirs()) {
                        throw new java.io.IOException("Cannot create MMS attachment directory");
                    }
                    for (int i = 0; i < message.parts.size(); i++) {
                        MmsPduParser.Part part = message.parts.get(i);
                        if (part.data.length == 0) continue;
                        String safeName = UUID.randomUUID().toString() + "-" + part.fileName;
                        File target = new File(attachmentDir, safeName);
                        try (FileOutputStream out = new FileOutputStream(target)) {
                            out.write(part.data);
                            out.getFD().sync();
                        }
                        try {
                            db.addMmsAttachment(messageId, target.getAbsolutePath(),
                                    part.contentType, target.length(), transaction);
                        } catch (RuntimeException e) {
                            target.delete();
                            throw e;
                        }
                    }
                    MmsReceiver.markProcessed(app, transaction);
                    try { NotificationHelper.showConversationNotification(app, message.from); }
                    catch (Exception e) { Log.w(TAG, "Unable to notify about incoming MMS", e); }
                    app.sendBroadcast(new Intent(SmsReceiver.ACTION_SMS_UPDATED).setPackage(app.getPackageName()));
                } catch (Exception e) {
                    Log.e(TAG, "Failed to persist downloaded MMS", e);
                    NotificationHelper.notifyMmsDownloadFailure(app);
                } finally {
                    MmsReceiver.releaseTransaction(intent.getStringExtra(MmsReceiver.EXTRA_TRANSACTION));
                    if (source != null && source.exists() && !source.delete()) {
                        Log.w(TAG, "Unable to remove temporary downloaded MMS PDU");
                    }
                    pending.finish();
                }
            });
        } catch (RuntimeException e) {
            Log.e(TAG, "Unable to queue downloaded MMS processing", e);
            pending.finish();
        }
    }

    private static byte[] readBounded(File file) throws Exception {
        int length = (int) file.length();
        byte[] data = new byte[length];
        try (FileInputStream in = new FileInputStream(file)) {
            int offset = 0;
            while (offset < length) {
                int count = in.read(data, offset, length - offset);
                if (count < 0) throw new java.io.IOException("Truncated downloaded MMS PDU");
                offset += count;
            }
            if (in.read() != -1) throw new java.io.IOException("Downloaded MMS exceeds declared size");
        }
        return data;
    }
}
