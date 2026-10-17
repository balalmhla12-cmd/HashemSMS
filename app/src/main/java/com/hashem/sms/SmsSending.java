package com.hashem.sms;
import android.app.PendingIntent;import android.content.Context;import android.content.Intent;import android.os.Handler;import android.os.Looper;import android.telephony.SmsManager;import android.widget.Toast;import java.util.ArrayList;import java.util.concurrent.ExecutorService;import java.util.concurrent.Executors;
final class SmsSending {
 static final ExecutorService EXECUTOR=Executors.newSingleThreadExecutor();private static final Handler MAIN_HANDLER=new Handler(Looper.getMainLooper());private SmsSending(){}
 static void postToast(Context context,String message,int duration){Context app=context.getApplicationContext();MAIN_HANDLER.post(()->Toast.makeText(app,message,duration).show());}
 static boolean sendSmsInBackground(Context context,Db db,String recipient,String body,String tag,int sentBase,int deliveredBase,int subscriptionId,boolean showProgress){
  return sendSmsInBackground(context,db,recipient,body,tag,sentBase,deliveredBase,subscriptionId,showProgress,0L);
 }
 static boolean sendSmsInBackground(Context context,Db db,String recipient,String body,String tag,int sentBase,int deliveredBase,int subscriptionId,boolean showProgress,long existingMessageId){
  Context app=context.getApplicationContext();long id=existingMessageId;
  if(id<=0){Db.PROVIDER_SYNC_LOCK.lock();try{id=db.addMessage(recipient,body,"out","sending");}finally{Db.PROVIDER_SYNC_LOCK.unlock();}}else db.status(id,"جارٍ الإرسال");
  if(id<=0){android.util.Log.e("HashemSMS","Could not save outgoing SMS for recipient "+recipient);if(showProgress)postToast(app,"تعذّر حفظ الرسالة محليًا",Toast.LENGTH_LONG);return false;}
  try{
   db.saveOutgoingToSystemProvider(app,id);SmsManager sm;
   if(subscriptionId>=0){if(android.os.Build.VERSION.SDK_INT>=31)sm=SmsManager.getDefault().createForSubscriptionId(subscriptionId);else sm=SmsManager.getSmsManagerForSubscriptionId(subscriptionId);}else sm=SmsManager.getDefault();
   ArrayList<String> parts=sm.divideMessage(body);ArrayList<PendingIntent> sent=new ArrayList<>(),delivered=new ArrayList<>();
   for(int i=0;i<parts.size();i++){
    Intent si=new Intent(app,SentReceiver.class);si.putExtra("id",id);si.putExtra("partCount",parts.size());si.putExtra("partIndex",i);si.putExtra("tag",tag);si.putExtra(NotificationHelper.EXTRA_ADDRESS,recipient);si.setAction(app.getPackageName()+"."+tag+"_SENT."+id+"."+i);
    Intent di=new Intent(app,DeliveredReceiver.class);di.putExtra("id",id);di.putExtra("partCount",parts.size());di.putExtra("partIndex",i);di.setAction(app.getPackageName()+"."+tag+"_DELIVERED."+id+"."+i);
    sent.add(PendingIntent.getBroadcast(app,sentBase+(int)(id%100000)*10+i,si,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE));
    delivered.add(PendingIntent.getBroadcast(app,deliveredBase+(int)(id%100000)*10+i,di,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE));
   }
   sm.sendMultipartTextMessage(recipient,null,parts,sent,delivered);app.sendBroadcast(new Intent(SmsReceiver.ACTION_SMS_UPDATED).setPackage(app.getPackageName()));
   if(showProgress)postToast(app,"بدأت محاولة إرسال الرسالة؛ ستظهر الحالة بعد وصول نتائج الأجزاء.",Toast.LENGTH_SHORT);return true;
  }catch(Exception e){db.status(id,"لم يتم الإرسال");db.updateSystemProviderStatus(app,id,false);android.util.Log.e("HashemSMS","Failed to start SMS send for recipient "+recipient,e);app.sendBroadcast(new Intent(SmsReceiver.ACTION_SMS_UPDATED).setPackage(app.getPackageName()));if(showProgress)postToast(app,"تعذّر إرسال الرسالة إلى "+recipient+"؛ تحقق من الشريحة والشبكة والصلاحيات.",Toast.LENGTH_LONG);return false;}
 }
 static void enqueueBulkSend(Context context,Db db,ArrayList<String[]> recipients,String body,int subscriptionId){final Context app=context.getApplicationContext();EXECUTOR.execute(()->{int failed=0,attempted=0;boolean interrupted=false;for(int i=0;i<recipients.size();i++){String[] r=recipients.get(i);String personalized=body.replace("{name}",r[1]);if(!sendSmsInBackground(app,db,r[0],personalized,"BULK",5000,6000,subscriptionId,false)){failed++;android.util.Log.e("HashemSMS","Bulk SMS failed to start for recipient "+r[0]);}attempted++;if(i+1<recipients.size())try{Thread.sleep(300L);}catch(InterruptedException e){Thread.currentThread().interrupt();interrupted=true;android.util.Log.e("HashemSMS","Bulk SMS queue interrupted after recipient "+r[0],e);break;}}String result="انتهت معالجة "+attempted+" من "+recipients.size()+" مستلم؛ تعذّر بدء الإرسال إلى "+failed+" مستلم.";if(interrupted)result+=" توقفت المعالجة بسبب مقاطعة داخلية.";postToast(app,result,Toast.LENGTH_LONG);});}
}