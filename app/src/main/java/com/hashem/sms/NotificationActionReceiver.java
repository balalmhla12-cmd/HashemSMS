package com.hashem.sms;

import android.app.RemoteInput;
import android.content.Context;
import android.content.Intent;
import android.telephony.SubscriptionManager;
import android.util.Log;

public final class NotificationActionReceiver extends android.content.BroadcastReceiver {
 private static final String TAG="HashemSMS";
 @Override public void onReceive(Context context,Intent intent){
  final PendingResult pending=goAsync();final Context app=context.getApplicationContext();
  try{SmsSending.RECEIVER_EXECUTOR.execute(()->{try{handleAction(app,intent);}catch(Exception e){
   Log.e(TAG,"Notification action failed",e);
   if(intent!=null){String address=intent.getStringExtra(NotificationHelper.EXTRA_ADDRESS);if(address!=null&&!address.trim().isEmpty())NotificationHelper.showConversationNotification(app,address);}
  }finally{pending.finish();}});}
  catch(RuntimeException e){Log.e(TAG,"Unable to queue notification action",e);try{
   if(intent!=null&&NotificationHelper.ACTION_REPLY.equals(intent.getAction())){String address=intent.getStringExtra(NotificationHelper.EXTRA_ADDRESS);
    if(address!=null&&!address.trim().isEmpty()){android.os.Bundle b=RemoteInput.getResultsFromIntent(intent);CharSequence input=b==null?null:b.getCharSequence(NotificationHelper.REPLY_KEY);NotificationHelper.showConversationNotification(app,address,input==null?"تعذّر تجهيز الرد":input.toString());}}
  }catch(Exception nested){Log.e(TAG,"Unable to show reply failure",nested);}finally{pending.finish();}}
 }
 private void handleAction(Context app,Intent intent){
  if(intent==null)return;String address=intent.getStringExtra(NotificationHelper.EXTRA_ADDRESS);if(address==null||address.trim().isEmpty())return;
  if(NotificationHelper.ACTION_MARK_READ.equals(intent.getAction())){NotificationHelper.markConversationRead(app,address);return;}
  if(!NotificationHelper.ACTION_REPLY.equals(intent.getAction()))return;
  android.os.Bundle results=RemoteInput.getResultsFromIntent(intent);CharSequence reply=results==null?null:results.getCharSequence(NotificationHelper.REPLY_KEY);
  if(reply==null||reply.toString().trim().isEmpty())return;
  String body=reply.toString().trim();int subId=-1;
  try{int candidate=SubscriptionManager.getDefaultSmsSubscriptionId();if(candidate>=0)subId=candidate;}catch(RuntimeException e){Log.w(TAG,"Unable to get default SMS subscription",e);}
  boolean started=SmsSending.sendSmsInBackground(app,new Db(app),address,body,"NOTIFICATION_REPLY",9000,10000,subId,false);
  if(started)NotificationHelper.showConversationNotification(app,address);
  else NotificationHelper.showConversationNotification(app,address,body);
 }
}