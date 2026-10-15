package com.hashem.sms;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.telephony.SmsManager;
import android.widget.Toast;
import java.util.ArrayList;

public class ScheduleReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        String address = intent.getStringExtra("address");
        String body = intent.getStringExtra("body");
        long id = intent.getLongExtra("id", -1);
        if (address == null || address.trim().isEmpty() || body == null || body.trim().isEmpty() || id <= 0) return;

        Db db = new Db(context);
        db.deleteSchedule(id);
        db.status(id, "جارٍ الإرسال");
        db.saveOutgoingToSystemProvider(context, id);
        try {
            SmsManager sms = SmsManager.getDefault();
            ArrayList<String> parts = sms.divideMessage(body);
            ArrayList<PendingIntent> sent = new ArrayList<>();
            ArrayList<PendingIntent> delivered = new ArrayList<>();
            for (int n = 0; n < parts.size(); n++) {
                Intent sentIntent = new Intent(context, SentReceiver.class);
                sentIntent.setAction(context.getPackageName() + ".SCHEDULED_SENT." + id + "." + n);
                sentIntent.putExtra("id", id);
                sentIntent.putExtra("partCount", parts.size());
                sentIntent.putExtra("partIndex", n);
                sent.add(PendingIntent.getBroadcast(context, 5000 + (int)(id % 100000) * 10 + n,
                    sentIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));

                Intent deliveredIntent = new Intent(context, DeliveredReceiver.class);
                deliveredIntent.setAction(context.getPackageName() + ".SCHEDULED_DELIVERED." + id + "." + n);
                deliveredIntent.putExtra("id", id);
                delivered.add(PendingIntent.getBroadcast(context, 6000 + (int)(id % 100000) * 10 + n,
                    deliveredIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
            }
            sms.sendMultipartTextMessage(address, null, parts, sent, delivered);
        } catch (Exception e) {
            db.status(id, "لم يتم الإرسال");
            db.updateSystemProviderStatus(context, id, false);
        }
    }
}
