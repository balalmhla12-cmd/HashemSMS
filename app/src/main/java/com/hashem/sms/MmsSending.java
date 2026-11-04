package com.hashem.sms;
import android.app.PendingIntent;import android.content.Context;import android.content.Intent;import android.net.Uri;import android.telephony.SmsManager;import android.util.Log;import androidx.core.content.FileProvider;import java.io.File;import java.io.FileOutputStream;import java.util.Collections;import java.util.UUID;
final class MmsSending {
 private MmsSending(){}
 static boolean send(Context c,Db d,String to,String caption,File image,int sub){return sendAttachment(c,d,to,caption,image,"image/jpeg",sub);}
 static boolean retry(Context c,Db d,long id,String to,File image,int sub,String caption){return retryAttachment(c,d,id,to,image,"image/jpeg",sub,caption);}
 static boolean sendAttachment(Context c,Db d,String to,String caption,File file,String mime,int sub){return sendInternal(c,d,to,caption,file,mime,sub,0L);}
 static boolean retryAttachment(Context c,Db d,long id,String to,File file,String mime,int sub,String caption){return sendInternal(c,d,to,caption,file,mime,sub,id);}
 private static boolean sendInternal(Context context,Db db,String recipient,String caption,File image,String mimeType,int subscriptionId,long existingId){
  Context app=context.getApplicationContext();long id=existingId;File pduFile=null;
  try{if(image==null||!image.isFile()||image.length()<=0)throw new java.io.IOException("ملف المرفق غير موجود");
   if(mimeType==null||mimeType.trim().isEmpty())throw new java.io.IOException("نوع المرفق غير معروف");
   if(image.length()>MmsImageFit.budgetFor(0L))throw new java.io.IOException("حجم المرفق يتجاوز الحد الآمن لرسالة MMS");
   if(id<=0){String label=mimeType.startsWith("image/")?"مرفق صورة":"ملف مرفق";id=db.addMessage(recipient,label+(caption==null||caption.trim().isEmpty()?"":"\n"+caption.trim()),"out","جارٍ الإرسال");if(id<=0)throw new java.io.IOException("تعذّر حفظ الرسالة");db.addMmsAttachment(id,image.getAbsolutePath(),mimeType,image.length(),UUID.randomUUID().toString());}else db.status(id,"جارٍ الإرسال");
   byte[] imageBytes=java.nio.file.Files.readAllBytes(image.toPath());String tx=UUID.randomUUID().toString().replace("-","");
   String extension=mimeType.equals("image/jpeg")?".jpg":mimeType.equals("image/png")?".png":mimeType.equals("image/gif")?".gif":mimeType.equals("image/webp")?".webp":mimeType.equals("application/pdf")?".pdf":mimeType.equals("text/vcard")?".vcf":mimeType.equals("text/plain")?".txt":".bin";
   MmsPduComposer.ImagePart part=new MmsPduComposer.ImagePart(imageBytes,mimeType,"ATT-"+id+"-1"+extension);String subject=caption==null?"":caption.replace('\n',' ').replace('\r',' ');byte[] pdu=MmsPduComposer.build(tx,Collections.singletonList(recipient),subject,Collections.singletonList(part));
   File dir=new File(app.getCacheDir(),"mms");if(!dir.exists()&&!dir.mkdirs())throw new java.io.IOException("تعذّر إنشاء مجلد MMS");pduFile=new File(dir,"send-"+id+"-"+tx+".pdu");try(FileOutputStream out=new FileOutputStream(pduFile)){out.write(pdu);out.getFD().sync();}
   Uri uri=FileProvider.getUriForFile(app,app.getPackageName()+".fileprovider",pduFile);Intent result=new Intent(app,MmsSendReceiver.class);result.setAction(app.getPackageName()+".MMS_SENT."+id+"."+tx);result.putExtra("message_id",id);result.putExtra("pdu_path",pduFile.getAbsolutePath());
   PendingIntent sent=PendingIntent.getBroadcast(app,(int)(id%1000000),result,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
   if(androidx.core.content.ContextCompat.checkSelfPermission(app,android.Manifest.permission.SEND_SMS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)throw new SecurityException("إذن إرسال الرسائل غير ممنوح");
   SmsManager manager;if(subscriptionId>=0){if(android.os.Build.VERSION.SDK_INT>=31)manager=SmsManager.getDefault().createForSubscriptionId(subscriptionId);else manager=SmsManager.getSmsManagerForSubscriptionId(subscriptionId);}else manager=SmsManager.getDefault();manager.sendMultimediaMessage(app,uri,null,null,sent);
   app.sendBroadcast(new Intent(SmsReceiver.ACTION_SMS_UPDATED).setPackage(app.getPackageName()));return true;
  }catch(Exception e){if(id>0)db.status(id,"فشل");if(pduFile!=null&&pduFile.exists()&&!pduFile.delete())Log.w("HashemSMS","Unable to remove failed MMS PDU");Log.e("HashemSMS","Unable to start MMS send",e);SmsSending.postToast(app,"تعذّر بدء إرسال المرفق: "+(e.getMessage()==null?"تحقق من الشبكة والشريحة":e.getMessage()),android.widget.Toast.LENGTH_LONG);app.sendBroadcast(new Intent(SmsReceiver.ACTION_SMS_UPDATED).setPackage(app.getPackageName()));return false;}
 }
}
