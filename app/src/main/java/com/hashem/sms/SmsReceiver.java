package com.hashem.sms;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.Telephony;
import android.telephony.SmsMessage;
import java.util.Locale;

public class SmsReceiver extends BroadcastReceiver {
 @Override public void onReceive(Context context, Intent intent) {
  if (intent == null || !Telephony.Sms.Intents.SMS_DELIVER_ACTION.equals(intent.getAction())) return;
  SmsMessage[] parts = Telephony.Sms.Intents.getMessagesFromIntent(intent);
  if (parts == null || parts.length == 0) return;
  String address = parts[0].getOriginatingAddress();
  StringBuilder text = new StringBuilder();
  long timestamp = parts[0].getTimestampMillis();
  for (SmsMessage part : parts) if (part != null && part.getMessageBody() != null) text.append(part.getMessageBody());
  String body = text.toString();
  if (address == null || body.isEmpty()) return;
  new Db(context).addMessage(address, body, "in", "received");
  // The default SMS app must also keep the system SMS provider in sync.
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
   // System-provider access is available when this app holds the default SMS role.
  } catch (IllegalArgumentException ignored) {
   // Keep the local conversation even if a device provider rejects a field.
  }
 }
}
