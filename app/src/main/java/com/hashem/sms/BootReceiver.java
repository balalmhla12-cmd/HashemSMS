package com.hashem.sms;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public final class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        final PendingResult pending = goAsync();
        final Context app = context.getApplicationContext();
        try {
            SmsSending.EXECUTOR.execute(() -> {
                try {
                    ScheduleRestorer.restorePendingSchedules(app, new Db(app), System.currentTimeMillis());
                } catch (Exception e) {
                    Log.e("HashemSMS", "Failed to restore scheduled messages after reboot", e);
                } finally {
                    pending.finish();
                }
            });
        } catch (RuntimeException e) {
            Log.e("HashemSMS", "Unable to queue boot schedule restoration", e);
            pending.finish();
        }
    }
}
