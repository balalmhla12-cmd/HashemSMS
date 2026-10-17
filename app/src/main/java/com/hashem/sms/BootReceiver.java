package com.hashem.sms;
import android.content.BroadcastReceiver;import android.content.Context;import android.content.Intent;import android.database.Cursor;import android.util.Log;import java.util.ArrayList;
public final class BootReceiver extends BroadcastReceiver {
 private static final long MAX_LATE_MILLIS=10L*60L*1000L;
 @Override public void onReceive(Context context,Intent intent){if(intent==null||!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction()))return;final PendingResult pending=goAsync();final Context app=context.getApplicationContext();
  try{SmsSending.EXECUTOR.execute(()->{try{Db db=new Db(app);ArrayList<Db.ScheduleRecord> rows=new ArrayList<>();Cursor c=db.listSchedules();
   try{while(c.moveToNext())rows.add(new Db.ScheduleRecord(c.getLong(c.getColumnIndexOrThrow("id")),c.getString(c.getColumnIndexOrThrow("address")),c.getString(c.getColumnIndexOrThrow("body")),c.getLong(c.getColumnIndexOrThrow("at"))));}finally{c.close();}
   long now=System.currentTimeMillis();for(Db.ScheduleRecord row:rows){
    if(row.address==null||row.address.trim().isEmpty()||row.body==null||row.body.trim().isEmpty()){if(db.removeSchedule(row.id)){AlarmScheduler.cancel(app,row.id,row.address,row.body);db.status(row.id,"لم يتم الإرسال");}continue;}
    if(row.at<=now){long late=now-row.at;if(late>MAX_LATE_MILLIS){if(db.removeSchedule(row.id)){AlarmScheduler.cancel(app,row.id,row.address,row.body);db.status(row.id,"فاتت الجدولة");NotificationHelper.notifyMissedScheduled(app,row.id,row.address,row.body);}continue;}
     boolean exact=AlarmScheduler.scheduleBestEffort(app,row.id,row.address,row.body,now+1000L);if(!exact)NotificationHelper.notifyApproximateAlarm(app,row.id,row.address);
    }else{boolean exact=AlarmScheduler.scheduleBestEffort(app,row.id,row.address,row.body,row.at);if(!exact)NotificationHelper.notifyApproximateAlarm(app,row.id,row.address);}
   }
  }catch(Exception e){Log.e("HashemSMS","Failed to restore scheduled messages after reboot",e);}finally{pending.finish();}});}
  catch(RuntimeException e){Log.e("HashemSMS","Unable to queue boot schedule restoration",e);pending.finish();}
 }
}