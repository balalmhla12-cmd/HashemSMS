package com.hashem.sms;
import android.app.Activity;import android.app.AlarmManager;import android.app.PendingIntent;import android.content.Context;import android.content.Intent;import android.database.Cursor;import android.os.Build;import android.widget.Toast;
final class AlarmScheduler {
 private static final String PREFS="scheduled_alarm_permission",KEY_ID="pending_exact_id";private AlarmScheduler(){}
 static PendingIntent pendingIntent(Context c,long id,String a,String b){Intent i=new Intent(c,ScheduleReceiver.class);i.putExtra("id",id);i.putExtra("address",a);i.putExtra("body",b);i.setAction(c.getPackageName()+".SCHEDULED."+id);return PendingIntent.getBroadcast(c,(int)(id%100000),i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}
 static boolean scheduleBestEffort(Context c,long id,String a,String b,long when){
  AlarmManager am=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);if(am==null)return false;PendingIntent pi=pendingIntent(c,id,a,b);
  try{if(Build.VERSION.SDK_INT>=31){if(am.canScheduleExactAlarms()){am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,when,pi);return true;}am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,when,pi);return false;}
   am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,when,pi);return true;
  }catch(SecurityException e){android.util.Log.w("HashemSMS","Exact alarm permission unavailable; falling back to approximate scheduling",e);try{am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,when,pi);}catch(Exception ex){android.util.Log.e("HashemSMS","Unable to set approximate alarm",ex);}return false;}
 }
 static void cancel(Context c,long id,String a,String b){NotificationHelper.clearApproximateAlarmNotice(c,id);AlarmManager am=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);if(am!=null){PendingIntent pi=pendingIntent(c,id,a,b);am.cancel(pi);pi.cancel();}android.content.SharedPreferences p=c.getSharedPreferences(PREFS,Context.MODE_PRIVATE);if(p.getLong(KEY_ID,-1L)==id)p.edit().remove(KEY_ID).apply();}
 static void rememberPendingExactRequest(Context c,long id){c.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putLong(KEY_ID,id).apply();}
 static void clearPendingExactRequest(Context c){c.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().remove(KEY_ID).apply();}
 static void resolvePendingExactAlarm(Activity a){
  if(Build.VERSION.SDK_INT<31)return;long id=a.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getLong(KEY_ID,-1L);if(id<=0)return;clearPendingExactRequest(a);
  Db db=new Db(a);Cursor c=db.listSchedules();String address=null,body=null;long at=0;
  try{while(c.moveToNext())if(c.getLong(c.getColumnIndexOrThrow("id"))==id){address=c.getString(c.getColumnIndexOrThrow("address"));body=c.getString(c.getColumnIndexOrThrow("body"));at=c.getLong(c.getColumnIndexOrThrow("at"));break;}}finally{c.close();}
  if(address==null||body==null)return;boolean exact=scheduleBestEffort(a,id,address,body,at);Toast.makeText(a,exact?"تم تفعيل المنبّه الدقيق":"لم يُمنح إذن المنبّه الدقيق؛ سيبقى الموعد تقريبيًا",Toast.LENGTH_LONG).show();
 }
}