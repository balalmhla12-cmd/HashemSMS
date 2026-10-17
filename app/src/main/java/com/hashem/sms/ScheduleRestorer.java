package com.hashem.sms;

import android.content.Context;
import android.database.Cursor;
import android.util.Log;
import java.util.ArrayList;

/** Recreates pending RTC alarms after reboot or wall-clock/time-zone changes. */
final class ScheduleRestorer {
    private ScheduleRestorer() {}

    /** Returns the number of pending alarms re-armed (exact or best-effort). */
    static int restorePendingSchedules(Context context, Db db, long now) {
        ArrayList<Db.ScheduleRecord> rows = new ArrayList<>();
        Cursor cursor = db.listSchedules();
        try {
            while (cursor.moveToNext()) {
                rows.add(new Db.ScheduleRecord(
                        cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                        cursor.getString(cursor.getColumnIndexOrThrow("address")),
                        cursor.getString(cursor.getColumnIndexOrThrow("body")),
                        cursor.getLong(cursor.getColumnIndexOrThrow("at"))));
            }
        } finally {
            cursor.close();
        }

        int restored = 0;
        for (Db.ScheduleRecord row : rows) {
            if (row.address == null || row.address.trim().isEmpty()
                    || row.body == null || row.body.trim().isEmpty()) {
                if (db.removeSchedule(row.id)) {
                    AlarmScheduler.cancel(context, row.id, row.address, row.body);
                    db.status(row.id, "لم يتم الإرسال");
                }
                continue;
            }

            long triggerAt = row.at;
            if (row.at <= now) {
                long lateBy = now - row.at;
                if (!SchedulePolicy.isWithinLateWindow(lateBy)) {
                    if (db.removeSchedule(row.id)) {
                        AlarmScheduler.cancel(context, row.id, row.address, row.body);
                        db.status(row.id, "فاتت الجدولة");
                        NotificationHelper.notifyMissedScheduled(
                                context, row.id, row.address, row.body);
                    }
                    continue;
                }
                triggerAt = now + 1000L;
            }

            boolean exact = AlarmScheduler.scheduleBestEffort(
                    context, row.id, row.address, row.body, triggerAt);
            if (!exact) {
                NotificationHelper.notifyApproximateAlarm(context, row.id, row.address);
            }
            restored++;
            Log.d("HashemSMS", "Restored scheduled SMS alarm id=" + row.id
                    + " at=" + triggerAt + " exact=" + exact);
        }
        return restored;
    }
}
