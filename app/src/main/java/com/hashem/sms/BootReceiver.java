package com.hashem.sms;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;

public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null || !Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        Db db = new Db(context);
        Cursor c = db.listSchedules();
        AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarms == null) { c.close(); return; }
        long now = System.currentTimeMillis();
        while (c.moveToNext()) {
            long id = c.getLong(c.getColumnIndexOrThrow("id"));
            String address = c.getString(c.getColumnIndexOrThrow("address"));
            String body = c.getString(c.getColumnIndexOrThrow("body"));
            long at = c.getLong(c.getColumnIndexOrThrow("at"));
            if (address == null || address.trim().isEmpty() || body == null || body.trim().isEmpty()) {
                db.deleteSchedule(id);
                continue;
            }
            Intent scheduled = new Intent(context, ScheduleReceiver.class);
            scheduled.putExtra("address", address);
            scheduled.putExtra("body", body);
            scheduled.putExtra("id", id);
            scheduled.setAction(context.getPackageName() + ".SCHEDULED." + id);
            PendingIntent pi = PendingIntent.getBroadcast(context, (int)(id % 100000), scheduled,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            long triggerAt = at <= now ? now + 5000 : at;
            if (android.os.Build.VERSION.SDK_INT >= 31 && alarms.canScheduleExactAlarms()) {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi);
            } else if (android.os.Build.VERSION.SDK_INT >= 23) {
                alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi);
            } else {
                alarms.set(AlarmManager.RTC_WAKEUP, triggerAt, pi);
            }
        }
        c.close();
    }
}