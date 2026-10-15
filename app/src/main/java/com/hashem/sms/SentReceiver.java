package com.hashem.sms;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

public class SentReceiver extends BroadcastReceiver {
 @Override public void onReceive(Context context, Intent intent) {
  long id = intent.getLongExtra("id", -1);
  if (id <= 0) return;
  int total = Math.max(1, intent.getIntExtra("partCount", 1));
  int index = intent.getIntExtra("partIndex", 0);
  boolean success = getResultCode() == -1;
  String prefix = "message_" + id + "_";
  synchronized (SentReceiver.class) {
   SharedPreferences prefs = context.getSharedPreferences("sms_send_results", Context.MODE_PRIVATE);
   if (prefs.getBoolean(prefix + "done_" + index, false)) return;
   int completed = prefs.getInt(prefix + "completed", 0) + 1;
   int failures = prefs.getInt(prefix + "failures", 0) + (success ? 0 : 1);
   SharedPreferences.Editor editor = prefs.edit();
   editor.putBoolean(prefix + "done_" + index, true);
   editor.putInt(prefix + "completed", completed);
   editor.putInt(prefix + "failures", failures);
   Db db = new Db(context);
   if (completed >= total) {
    boolean allSent = failures == 0;
    db.status(id, allSent ? "تم الإرسال" : "لم يتم الإرسال");
    db.updateSystemProviderStatus(context, id, allSent);
   } else {
    db.status(id, "جارٍ الإرسال");
   }
   editor.apply();
  }
 }
}
