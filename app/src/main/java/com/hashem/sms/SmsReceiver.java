package com.hashem.sms;
import android.content.*;import android.provider.Telephony;import android.telephony.*;import android.database.sqlite.*;import java.util.*;
public class SmsReceiver extends BroadcastReceiver{public void onReceive(Context c,Intent i){if(!Telephony.Sms.Intents.SMS_DELIVER_ACTION.equals(i.getAction()))return; for(SmsMessage m:Telephony.Sms.Intents.getMessagesFromIntent(i)){new Db(c).addMessage(m.getOriginatingAddress(),m.getMessageBody(),"in","received");}}}
