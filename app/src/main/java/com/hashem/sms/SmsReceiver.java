package com.hashem.sms;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.provider.Telephony;
import android.telephony.SmsMessage;

public class SmsReceiver extends BroadcastReceiver {
 @Override public void onReceive(Context context, Intent intent) {
  if (intent == null || !Telephony.Sms.Intents.SMS_DELIVER_ACTION.equals(intent.getAction())) return;
  SmsMessage[] parts = Telephony.Sms.Intents.getMessagesFromIntent(intent);
  if (parts == null || parts.length == 0 || parts[0] == null) return;
  String address = parts[0].getOriginatingAddress();
  StringBuilder text = new StringBuilder();
  long timestamp = parts[0].getTimestampMillis();
  for (SmsMessage part : parts) if (part != null && part.getMessageBody() != null) text.append(part.getMessageBody());
  String body = text.toString();
  if (address == null || body.isEmpty()) return;
  new Db(context).addMessage(address, body, "in", "received");
  try {
   ContentValues values = new ContentValues();
   values.put(Telephony.Sms.ADDRESS, address);
   values.put(Telephony.Sms.BODY, body);
   values.put(Telephony.Sms.DATE, timestamp > 0 ? timestamp : System.currentTimeMillis());
   values.put(Telephony.Sms.READ, 0);
   values.put(Telephony.Sms.SEEN, 0);
   values.put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_INBOX);
   context.getContentResolver().insert(Telephony.Sms.Inbox.CONTENT_URI, values);
  } catch (SecurityException ignored) {
   // Provider access depends on the app's default SMS role.
  } catch (IllegalArgumentException ignored) {
   // Keep the local copy if the device provider rejects the record.
  }
  notifyUser(context, address, body);
 }
 private void notifyUser(Context context, String address, String body) {
  try {
   NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
   if (manager == null) return;
   String channel = "sms_messages";
   if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(new NotificationChannel(channel, "الرسائل الجديدة", NotificationManager.IMPORTANCE_HIGH));
   Intent open = new Intent(context, ConversationActivity.class);
   open.putExtra("address", address);
   PendingIntent pending = PendingIntent.getActivity(context, Math.abs(address.hashCode()), open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
   Notification.Builder notice = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(context, channel) : new Notification.Builder(context);
   notice.setSmallIcon(android.R.drawable.sym_action_chat).setContentTitle(address).setContentText(body).setAutoCancel(true).setContentIntent(pending);
   manager.notify(Math.abs(address.hashCode()), notice.build());
  } catch (SecurityException ignored) {
   // Notifications may be disabled by the user.
  }
 }
}
