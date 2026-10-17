package com.hashem.sms;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public final class ScheduleReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        final PendingResult pending = goAsync();
        if (intent == null) {
            pending.finish();
            return;
        }
        final long id = intent.getLongExtra("id", -1L);
        if (id <= 0L) {
            pending.finish();
            return;
        }
        final Context app = context.getApplicationContext();
        try {
            SmsSending.EXECUTOR.execute(() -> {
                try {
                    Db db = new Db(app);
                    Db.ScheduleRecord row = db.claimSchedule(id);
                    if (row == null) {
                        Log.i("HashemSMS", "Ignored duplicate or cancelled schedule alarm id=" + id);
                        return;
                    }

                    long lateBy = Math.max(0L, System.currentTimeMillis() - row.at);
                    if (!SchedulePolicy.isWithinLateWindow(lateBy)) {
                        db.status(id, "فاتت الجدولة");
                        NotificationHelper.notifyMissedScheduled(app, id, row.address, row.body);
                        Log.w("HashemSMS", "Skipped scheduled SMS more than 10 minutes late; id=" + id);
                        return;
                    }

                    if (row.address == null || row.address.trim().isEmpty()
                            || row.body == null || row.body.trim().isEmpty()) {
                        db.status(id, "لم يتم الإرسال");
                        Log.e("HashemSMS", "Invalid scheduled SMS id=" + id);
                        return;
                    }

                    boolean started = SmsSending.sendSmsInBackground(
                            app, db, row.address, row.body, "SCHEDULED",
                            11000, 12000, -1, false, id);
                    if (!started) db.status(id, "لم يتم الإرسال");
                } catch (Exception e) {
                    Log.e("HashemSMS", "Scheduled SMS execution failed", e);
                    try {
                        new Db(app).status(id, "لم يتم الإرسال");
                    } catch (Exception ignored) {
                        Log.e("HashemSMS", "Could not persist scheduled SMS failure status", ignored);
                    }
                } finally {
                    pending.finish();
                }
            });
        } catch (RuntimeException e) {
            Log.e("HashemSMS", "Unable to queue scheduled SMS", e);
            try {
                new Db(app).status(id, "لم يتم الإرسال");
            } catch (Exception statusError) {
                Log.e("HashemSMS", "Could not persist queue failure status", statusError);
            } finally {
                pending.finish();
            }
        }
    }
}
