package com.hashem.sms;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.RemoteInput;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.ContactsContract;
import android.provider.Telephony;
import android.util.Base64;
import android.util.Log;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Map;

final class NotificationHelper {
 static final String CHANNEL_ID="sms_messages",GROUP_KEY="com.hashem.sms.CONVERSATIONS",EXTRA_ADDRESS="address",ACTION_MARK_READ="com.hashem.sms.ACTION_MARK_READ",ACTION_REPLY="com.hashem.sms.ACTION_REPLY",REPLY_KEY="reply_text";
 private static final String TAG="HashemSMS",ID_PREFS="conversation_notification_ids",ID_PREFIX="address_";
 private static final int FIRST_ID=100000,LAST_ID=999999;
 private NotificationHelper(){}

 // Created once by HashemSmsApp.onCreate(), never from the incoming-message path.
 static void createChannel(Context c){
  if(Build.VERSION.SDK_INT<Build.VERSION_CODES.O)return;
  NotificationManager m=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);
  if(m!=null)m.createNotificationChannel(new NotificationChannel(CHANNEL_ID,"الرسائل الجديدة",NotificationManager.IMPORTANCE_HIGH));
 }

 // Persistent unique mapping: distinct addresses do not share notification IDs unless the finite pool is exhausted.
 private static synchronized int notificationId(Context c,String address){
  SharedPreferences p=c.getApplicationContext().getSharedPreferences(ID_PREFS,Context.MODE_PRIVATE);
  String key=ID_PREFIX+Base64.encodeToString(address.getBytes(StandardCharsets.UTF_8),Base64.NO_WRAP|Base64.URL_SAFE);
  int old=p.getInt(key,-1);if(old>=FIRST_ID&&old<=LAST_ID)return old;
  Map<String,?> all=p.getAll();int id=p.getInt("next_id",FIRST_ID);if(id<FIRST_ID||id>LAST_ID)id=FIRST_ID;
  int capacity=LAST_ID-FIRST_ID+1;
  for(int n=0;n<capacity;n++){
   boolean used=false;
   for(Map.Entry<String,?> e:all.entrySet()){Object v=e.getValue();if(e.getKey().startsWith(ID_PREFIX)&&v instanceof Integer&&((Integer)v).intValue()==id){used=true;break;}}
   if(!used){p.edit().putInt(key,id).putInt("next_id",id==LAST_ID?FIRST_ID:id+1).commit();return id;}
   id=id==LAST_ID?FIRST_ID:id+1;
  }
  return FIRST_ID+Math.floorMod(address.hashCode(),capacity);
 }

 static void cancelConversationNotification(Context c,String address){
  if(address==null||address.trim().isEmpty())return;
  try{NotificationManager m=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);if(m!=null)m.cancel(notificationId(c,address));}
  catch(RuntimeException e){Log.w(TAG,"Unable to cancel conversation notification",e);}
 }

 static void markConversationRead(Context c,String address){
  if(address==null||address.trim().isEmpty())return;Context app=c.getApplicationContext();
  try{new Db(app).markThreadRead(address);}catch(Exception e){Log.e(TAG,"Could not mark local conversation read",e);}
  try{ContentValues v=new ContentValues();v.put(Telephony.Sms.READ,1);v.put(Telephony.Sms.SEEN,1);app.getContentResolver().update(Telephony.Sms.Inbox.CONTENT_URI,v,Telephony.Sms.ADDRESS+"=?",new String[]{address});}
  catch(Exception e){Log.w(TAG,"Could not mark system SMS provider conversation read",e);}
  finally{cancelConversationNotification(app,address);app.sendBroadcast(new Intent(SmsReceiver.ACTION_SMS_UPDATED).setPackage(app.getPackageName()));}
 }

 static boolean isConversationNotificationActive(Context c,String address){
  if(address==null||address.trim().isEmpty())return false;
  try{NotificationManager m=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);if(m==null)return false;
   if(Build.VERSION.SDK_INT<Build.VERSION_CODES.M)return true;
   int id=notificationId(c,address);
   for(android.service.notification.StatusBarNotification n:m.getActiveNotifications())if(n.getId()==id)return true;
  }catch(RuntimeException e){Log.w(TAG,"Unable to inspect active conversation notification",e);}
  return false;
 }
 static void showConversationNotification(Context c,String address){showConversationNotification(c,address,null);}
 static void showConversationNotification(Context c,String address,String failedReply){
  if(address==null||address.trim().isEmpty())return;Context app=c.getApplicationContext();
  try{
   Db db=new Db(app);
   if(db.isConversationBlocked(address)){cancelConversationNotification(app,address);return;}
   NotificationManager manager=(NotificationManager)app.getSystemService(Context.NOTIFICATION_SERVICE);if(manager==null)return;
   ArrayList<Object[]> rows=db.notificationMessages(address,5);boolean syntheticFailure=failedReply!=null&&!failedReply.trim().isEmpty();
   if(rows.isEmpty()&&!syntheticFailure){cancelConversationNotification(app,address);return;}
   String name=contactName(app,address);if(name.isEmpty())name=address;
   int id=notificationId(app,address);
   Intent open=new Intent(app,ConversationActivity.class).putExtra(EXTRA_ADDRESS,address).setAction(app.getPackageName()+".OPEN_CONVERSATION."+id);
   PendingIntent openPi=PendingIntent.getActivity(app,id,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
   Intent read=new Intent(app,NotificationActionReceiver.class).setAction(ACTION_MARK_READ).putExtra(EXTRA_ADDRESS,address);
   PendingIntent readPi=PendingIntent.getBroadcast(app,id*2+1,read,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
   Intent reply=new Intent(app,NotificationActionReceiver.class).setAction(ACTION_REPLY).putExtra(EXTRA_ADDRESS,address);
   int replyFlags=PendingIntent.FLAG_UPDATE_CURRENT;if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.S)replyFlags|=PendingIntent.FLAG_MUTABLE;
   PendingIntent replyPi=PendingIntent.getBroadcast(app,id*2+2,reply,replyFlags);
   RemoteInput input=new RemoteInput.Builder(REPLY_KEY).setLabel("اكتب ردًا").build();
   Notification.Action replyAction=new Notification.Action.Builder(R.drawable.ic_stat_message,"رد",replyPi).addRemoteInput(input).setAllowGeneratedReplies(true).build();
   Notification.Action readAction=new Notification.Action.Builder(R.drawable.ic_stat_message,"تعليم كمقروء",readPi).build();
   Notification.Builder b=Build.VERSION.SDK_INT>=Build.VERSION_CODES.O?new Notification.Builder(app,CHANNEL_ID):new Notification.Builder(app);
   String latest="",status="",type="in";
   if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.N){
    Notification.MessagingStyle style=new Notification.MessagingStyle(name).setConversationTitle(name);
    int start=syntheticFailure&&rows.size()>=5?1:0;
    for(int i=start;i<rows.size();i++){
     Object[] r=rows.get(i);String body=r[0]==null?"":(String)r[0],kind=r[1]==null?"in":(String)r[1],st=r[3]==null?"":(String)r[3];
     String shown=body;if("out".equals(kind)&&isFailed(st))shown+="\nتعذّر إرسال الرد";
     style.addMessage(shown,(Long)r[2],"out".equals(kind)?"أنت":name);latest=body;status=st;type=kind;
    }
    if(syntheticFailure){style.addMessage("تعذّر إرسال الرد: "+failedReply,System.currentTimeMillis(),"أنت");latest=failedReply;status="failed";type="out";}
    b.setStyle(style);
   }else{
    if(!rows.isEmpty()){Object[] r=rows.get(rows.size()-1);latest=r[0]==null?"":(String)r[0];status=r[3]==null?"":(String)r[3];type=r[1]==null?"in":(String)r[1];}
    if(syntheticFailure){latest=failedReply;status="failed";type="out";}
    b.setStyle(new Notification.BigTextStyle().bigText(latest));
   }
   String content=latest;if(isFailed(status))content="تعذّر إرسال الرد: "+latest;else if("جارٍ الإرسال".equals(status)||"sending".equals(status))content="جارٍ إرسال الرد: "+latest;
   b.setSmallIcon(R.drawable.ic_stat_message).setContentTitle(name).setContentText(content).setGroup(GROUP_KEY).setAutoCancel(true)
    .setOnlyAlertOnce(!"in".equals(type)).setContentIntent(openPi).addAction(readAction).addAction(replyAction).setCategory(Notification.CATEGORY_MESSAGE);
   if(!rows.isEmpty())b.setWhen((Long)rows.get(rows.size()-1)[2]);manager.notify(id,b.build());
  }catch(SecurityException e){Log.w(TAG,"Notification permission denied",e);}catch(Exception e){Log.e(TAG,"Failed to build incoming-message notification",e);}
 }
 private static boolean isFailed(String s){return "لم يتم الإرسال".equals(s)||"failed".equals(s)||"تعذّر الإرسال".equals(s);}
 private static String contactName(Context c,String address){
  Cursor cur=null;try{Uri uri=Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI,Uri.encode(address));cur=c.getContentResolver().query(uri,new String[]{ContactsContract.PhoneLookup.DISPLAY_NAME},null,null,null);if(cur!=null&&cur.moveToFirst()){String n=cur.getString(0);if(n!=null&&!n.trim().isEmpty())return n.trim();}}
  catch(Exception e){Log.w(TAG,"Unable to resolve contact name",e);}finally{if(cur!=null)cur.close();}return "";
 }

 static void notifyMissedScheduled(Context c,long id,String address,String body){
  try{NotificationManager m=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);if(m==null)return;Intent open=new Intent(c,ConversationActivity.class).putExtra(EXTRA_ADDRESS,address).setAction(c.getPackageName()+".MISSED_SCHEDULE."+id);
   PendingIntent pi=PendingIntent.getActivity(c,(int)(id%100000)+200000,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
   Notification.Builder b=Build.VERSION.SDK_INT>=Build.VERSION_CODES.O?new Notification.Builder(c,CHANNEL_ID):new Notification.Builder(c);
   b.setSmallIcon(R.drawable.ic_stat_message).setContentTitle("رسالة مجدولة لم تُرسل").setContentText("فات موعد إرسال الرسالة إلى "+address+"؛ افتح المحادثة لتقرر ما إذا كنت تريد إرسالها.")
    .setStyle(new Notification.BigTextStyle().bigText(body)).setContentIntent(pi).setAutoCancel(true).setCategory(Notification.CATEGORY_STATUS);m.notify(1000000+(int)(id%100000),b.build());
  }catch(SecurityException e){Log.w(TAG,"Notification permission denied for missed schedule",e);}catch(Exception e){Log.e(TAG,"Unable to notify about missed scheduled message",e);}
 }
 static void notifyApproximateAlarm(Context c,long id,String address){
  try{NotificationManager m=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);if(m==null)return;Intent open=new Intent(c,MainActivity.class).setAction(c.getPackageName()+".APPROXIMATE_ALARM."+id);
   PendingIntent pi=PendingIntent.getActivity(c,(int)(id%100000)+300000,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
   Notification.Builder b=Build.VERSION.SDK_INT>=Build.VERSION_CODES.O?new Notification.Builder(c,CHANNEL_ID):new Notification.Builder(c);
   b.setSmallIcon(R.drawable.ic_stat_message).setContentTitle("موعد إرسال تقريبي").setContentText("لم يُمنح إذن المنبّه الدقيق؛ قد يتأخر إرسال الرسالة إلى "+address+".").setContentIntent(pi).setAutoCancel(true);m.notify(2000000+(int)(id%100000),b.build());
  }catch(SecurityException e){Log.w(TAG,"Notification permission denied",e);}
 }
}