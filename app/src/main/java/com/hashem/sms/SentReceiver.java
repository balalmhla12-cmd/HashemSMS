package com.hashem.sms;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
public class SentReceiver extends BroadcastReceiver {
 @Override public void onReceive(Context context,Intent intent){
  long id=intent.getLongExtra("id",-1);if(id<=0)return;
  int total=Math.max(1,intent.getIntExtra("partCount",1)),index=intent.getIntExtra("partIndex",0);if(index<0||index>=total)return;
  boolean success=getResultCode()==android.app.Activity.RESULT_OK;String prefix="message_"+id+"_";
  synchronized(SentReceiver.class){
   SharedPreferences prefs=context.getSharedPreferences("sms_send_results",Context.MODE_PRIVATE);
   if(prefs.getBoolean(prefix+"done_"+index,false))return;
   int completed=prefs.getInt(prefix+"completed",0)+1,failures=prefs.getInt(prefix+"failures",0)+(success?0:1);boolean finished=completed>=total;
   SharedPreferences.Editor e=prefs.edit().putBoolean(prefix+"done_"+index,true).putInt(prefix+"completed",completed).putInt(prefix+"failures",failures);
   Db db=new Db(context);db.status(id,finished?(failures==0?"تم الإرسال":"لم يتم الإرسال"):"جارٍ الإرسال");
   if(finished){if(failures>0)android.util.Log.e("HashemSMS","Multipart SMS send failed for message id="+id+" partsFailed="+failures);db.updateSystemProviderStatus(context,id,failures==0);e.remove(prefix+"completed").remove(prefix+"failures");for(int i=0;i<total;i++)e.remove(prefix+"done_"+i);}
   e.commit();
  }
 }
}