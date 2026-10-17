package com.hashem.sms;
import android.app.RemoteInput;import android.content.BroadcastReceiver;import android.content.ContentValues;import android.content.Context;import android.content.Intent;import android.provider.Telephony;import android.util.Log;
public final class NotificationActionReceiver extends BroadcastReceiver {
 @Override public void onReceive(Context context,Intent intent){
  if(intent==null)return;String address=intent.getStringExtra(NotificationHelper.EXTRA_ADDRESS);if(address==null||address.trim().isEmpty())return;Context app=context.getApplicationContext();
  if(NotificationHelper.ACTION_MARK_READ.equals(intent.getAction())){markRead(app,address);return;}if(!NotificationHelper.ACTION_REPLY.equals(intent.getAction()))return;
  android.os.Bundle results=RemoteInput.getResultsFromIntent(intent);CharSequence reply=results==null?null:results.getCharSequence(NotificationHelper.REPLY_KEY);if(reply==null||reply.toString().trim().isEmpty())return;
  String body=reply.toString().trim();final PendingResult pending=goAsync();
  try{SmsSending.EXECUTOR.execute(()->{try{boolean started=SmsSending.sendSmsInBackground(app,new Db(app),address,body,"NOTIFICATION_REPLY",9000,10000,-1,false);if(started)markRead(app,address);else NotificationHelper.showConversationNotification(app,address);}catch(Exception e){Log.e("HashemSMS","Notification reply failed",e);NotificationHelper.showConversationNotification(app,address);}finally{pending.finish();}});}
  catch(RuntimeException e){Log.e("HashemSMS","Unable to queue notification reply",e);pending.finish();}
 }
 private void markRead(Context c,String address){new Db(c).markThreadRead(address);try{ContentValues v=new ContentValues();v.put(Telephony.Sms.READ,1);v.put(Telephony.Sms.SEEN,1);c.getContentResolver().update(Telephony.Sms.Inbox.CONTENT_URI,v,"address=?",new String[]{address});}catch(Exception e){Log.w("HashemSMS","Could not mark provider messages read",e);}NotificationHelper.cancelConversationNotification(c,address);c.sendBroadcast(new Intent(SmsReceiver.ACTION_SMS_UPDATED).setPackage(c.getPackageName()));}
}