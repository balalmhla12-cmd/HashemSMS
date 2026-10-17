package com.hashem.sms;
import android.app.Notification;import android.app.NotificationChannel;import android.app.NotificationManager;import android.app.PendingIntent;import android.app.RemoteInput;import android.content.Context;import android.content.Intent;import android.os.Build;import android.util.Log;import java.util.ArrayList;
final class NotificationHelper {
 static final String CHANNEL_ID="sms_messages",GROUP_KEY="com.hashem.sms.CONVERSATIONS",EXTRA_ADDRESS="address",ACTION_MARK_READ="com.hashem.sms.ACTION_MARK_READ",ACTION_REPLY="com.hashem.sms.ACTION_REPLY",REPLY_KEY="reply_text";private NotificationHelper(){}
 static void createChannel(Context c){if(Build.VERSION.SDK_INT>=26){NotificationManager m=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);if(m!=null)m.createNotificationChannel(new NotificationChannel(CHANNEL_ID,"الرسائل الجديدة",NotificationManager.IMPORTANCE_HIGH));}}
 static int notificationId(String a){return a==null?1:(a.hashCode()&0x7fffffff);}
 static void cancelConversationNotification(Context c,String a){try{NotificationManager m=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);if(m!=null)m.cancel(notificationId(a));}catch(SecurityException e){Log.w("HashemSMS","Unable to cancel notification",e);}}
 static void showConversationNotification(Context c,String address){
  try{NotificationManager manager=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);if(manager==null)return;ArrayList<Object[]> rows=new Db(c).notificationMessages(address,5);if(rows.isEmpty())return;
   int nid=notificationId(address);Intent open=new Intent(c,ConversationActivity.class).putExtra(EXTRA_ADDRESS,address).setAction(c.getPackageName()+".OPEN_CONVERSATION."+nid);PendingIntent openPi=PendingIntent.getActivity(c,nid,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
   Notification.MessagingStyle style=new Notification.MessagingStyle("هاشم SMS").setConversationTitle(address);String latest="";
   for(Object[] row:rows){latest=row[0]==null?"":(String)row[0];style.addMessage(latest,(Long)row[2],"out".equals(row[1])?"أنت":address);}
   Intent read=new Intent(c,NotificationActionReceiver.class).setAction(ACTION_MARK_READ).putExtra(EXTRA_ADDRESS,address);PendingIntent readPi=PendingIntent.getBroadcast(c,nid*2+1,read,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
   Intent reply=new Intent(c,NotificationActionReceiver.class).setAction(ACTION_REPLY).putExtra(EXTRA_ADDRESS,address);int flags=PendingIntent.FLAG_UPDATE_CURRENT;if(Build.VERSION.SDK_INT>=31)flags|=PendingIntent.FLAG_MUTABLE;PendingIntent replyPi=PendingIntent.getBroadcast(c,nid*2+2,reply,flags);
   RemoteInput remote=new RemoteInput.Builder(REPLY_KEY).setLabel("اكتب ردًا").build();
   Notification.Action replyAction=new Notification.Action.Builder(R.drawable.ic_stat_message,"رد",replyPi).addRemoteInput(remote).setAllowGeneratedReplies(true).build();
   Notification.Action readAction=new Notification.Action.Builder(R.drawable.ic_stat_message,"تعليم كمقروء",readPi).build();
   Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(c,CHANNEL_ID):new Notification.Builder(c);
   b.setSmallIcon(R.drawable.ic_stat_message).setContentTitle(address).setContentText(latest).setStyle(style).setGroup(GROUP_KEY).setAutoCancel(true).setContentIntent(openPi).addAction(readAction).addAction(replyAction).setCategory(Notification.CATEGORY_MESSAGE).setWhen((Long)rows.get(rows.size()-1)[2]);
   manager.notify(nid,b.build());
  }catch(SecurityException e){Log.w("HashemSMS","Notification permission denied",e);}catch(Exception e){Log.e("HashemSMS","Failed to build incoming-message notification",e);}
 }
 static void notifyMissedScheduled(Context c,long id,String address,String body){
  try{NotificationManager m=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);if(m==null)return;Intent open=new Intent(c,ConversationActivity.class).putExtra(EXTRA_ADDRESS,address).setAction(c.getPackageName()+".MISSED_SCHEDULE."+id);PendingIntent pi=PendingIntent.getActivity(c,(int)(id%100000)+200000,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
   Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(c,CHANNEL_ID):new Notification.Builder(c);b.setSmallIcon(R.drawable.ic_stat_message).setContentTitle("رسالة مجدولة لم تُرسل").setContentText("فات موعد إرسال الرسالة إلى "+address+"؛ افتح المحادثة لتقرر ما إذا كنت تريد إرسالها.").setStyle(new Notification.BigTextStyle().bigText(body)).setContentIntent(pi).setAutoCancel(true).setCategory(Notification.CATEGORY_STATUS);m.notify(1000000+(int)(id%100000),b.build());
  }catch(SecurityException e){Log.w("HashemSMS","Notification permission denied for missed schedule",e);}catch(Exception e){Log.e("HashemSMS","Unable to notify about missed scheduled message",e);}
 }
 static void notifyApproximateAlarm(Context c,long id,String address){
  try{NotificationManager m=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);if(m==null)return;Intent open=new Intent(c,MainActivity.class).setAction(c.getPackageName()+".APPROXIMATE_ALARM."+id);PendingIntent pi=PendingIntent.getActivity(c,(int)(id%100000)+300000,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
   Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(c,CHANNEL_ID):new Notification.Builder(c);b.setSmallIcon(R.drawable.ic_stat_message).setContentTitle("موعد إرسال تقريبي").setContentText("لم يُمنح إذن المنبّه الدقيق؛ قد يتأخر إرسال الرسالة إلى "+address+".").setContentIntent(pi).setAutoCancel(true);m.notify(2000000+(int)(id%100000),b.build());
  }catch(SecurityException e){Log.w("HashemSMS","Notification permission denied for approximate alarm",e);}
 }
}