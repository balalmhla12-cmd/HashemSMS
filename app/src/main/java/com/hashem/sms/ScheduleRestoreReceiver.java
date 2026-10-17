package com.hashem.sms;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/** Shared asynchronous restoration path for system clock and alarm-permission changes. */
abstract class ScheduleRestoreReceiver extends BroadcastReceiver {
    @Override
    public final void onReceive(Context context, Intent intent) {
        final PendingResult pending = goAsync();
        final Context app = context.getApplicationContext();
        try {
            SmsSending.EXECUTOR.execute(() -> {
                try {
                    ScheduleRestorer.restorePendingSchedules(app, new Db(app), System.currentTimeMillis());
                } catch (Exception e) {
                    Log.e("HashemSMS", "Failed to restore pending scheduled alarms", e);
                } finally {
                    pending.finish();
                }
            });
        } catch (RuntimeException e) {
            Log.e("HashemSMS", "Unable to queue scheduled alarm restoration", e);
            pending.finish();
        }
    }
}
