package com.hashem.sms;
import android.content.BroadcastReceiver;import android.content.Context;import android.content.Intent;import android.util.Log;import java.io.File;
public final class MmsSendReceiver extends BroadcastReceiver {
 @Override public void onReceive(Context context,Intent intent){long id=intent==null?-1L:intent.getLongExtra("message_id",-1L);String path=intent==null?null:intent.getStringExtra("pdu_path");if(id<=0)return;boolean ok=getResultCode()==android.app.Activity.RESULT_OK;Db db=new Db(context.getApplicationContext());db.status(id,MmsSendState.statusFor(ok));if(path!=null){File pdu=new File(path);if(pdu.exists()&&!pdu.delete())Log.w("HashemSMS","Unable to delete completed MMS PDU");}context.getApplicationContext().sendBroadcast(new Intent(SmsReceiver.ACTION_SMS_UPDATED).setPackage(context.getPackageName()));SmsSending.postToast(context,ok?"تم إرسال رسالة الوسائط.":"فشل إرسال رسالة الوسائط؛ يمكنك إعادة المحاولة.",android.widget.Toast.LENGTH_LONG);}
}
