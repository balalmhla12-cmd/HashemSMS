package com.hashem.sms;

import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.Build;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.Typeface;
import android.os.Bundle;
import android.provider.Settings;
import android.telephony.SmsManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.*;
import android.widget.*;
import java.util.*;

public class ConversationActivity extends Activity {
    Db db; String address; LinearLayout root, messages; EditText search, composer; TextView selectionTitle; ActionMode actionMode; final ArrayList<Long> selected = new ArrayList<>();
    int purple = Color.rgb(103,80,164);
    java.util.concurrent.ExecutorService sendExecutor=java.util.concurrent.Executors.newSingleThreadExecutor();
    java.util.function.IntConsumer pendingSubscriptionAction;

    TextView tv(String s,int size){ TextView t=new TextView(this); t.setText(s); t.setTextSize(size); t.setTextColor(Color.rgb(35,35,35)); t.setPadding(18,12,18,12); return t; }
    GradientDrawable rounded(int color,int stroke,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(radius*getResources().getDisplayMetrics().density);if(stroke!=0)d.setStroke(1,color==0?stroke:stroke);return d;}
    Button actionButton(String label,boolean primary){Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setTextSize(14);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);b.setTextColor(primary?Color.WHITE:Color.rgb(56,30,114));b.setPadding(14,6,14,6);b.setBackground(rounded(primary?purple:Color.rgb(234,221,255),primary?0:Color.rgb(218,207,237),28));if(android.os.Build.VERSION.SDK_INT>=21)b.setStateListAnimator(null);return b;}

    @Override public void onCreate(Bundle b){ super.onCreate(b); db=new Db(this); address=getIntent().getStringExtra("address"); if(address==null)address=""; db.markThreadRead(address); build(); render(); }

    @Override protected void onResume(){super.onResume();if(db!=null&&messages!=null)render();}

    void build(){
        root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(10,8,10,8); root.setBackgroundColor(Color.rgb(255,251,254));
        LinearLayout top=new LinearLayout(this); top.setGravity(Gravity.CENTER_VERTICAL);top.setPadding(0,0,0,8);
        Button back=actionButton("‹",false); back.setTextSize(25); back.setOnClickListener(v->finish()); top.addView(back,new LinearLayout.LayoutParams(52,52));
        LinearLayout identity=new LinearLayout(this);identity.setOrientation(LinearLayout.VERTICAL);
        selectionTitle=tv(address,18);selectionTitle.setTypeface(Typeface.DEFAULT,Typeface.BOLD);selectionTitle.setPadding(8,2,8,0);
        TextView subtitle=tv("رسائل نصية · SMS",12);subtitle.setTextColor(Color.rgb(100,94,110));subtitle.setPadding(8,0,8,0);
        identity.addView(selectionTitle);identity.addView(subtitle);top.addView(identity,new LinearLayout.LayoutParams(0,-2,1));
        Button more=actionButton("⋮",false);more.setTextSize(22);more.setOnClickListener(v->conversationMenu(v)); top.addView(more,new LinearLayout.LayoutParams(48,52)); root.addView(top);
        search=new EditText(this); search.setHint("بحث في هذه المحادثة"); search.setSingleLine(); search.setTextSize(14);search.setPadding(16,4,16,4);search.setBackground(rounded(Color.rgb(243,239,248),Color.rgb(230,224,236),26));search.setVisibility(View.GONE);
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int d){} public void onTextChanged(CharSequence s,int a,int b,int c){render();} public void afterTextChanged(Editable e){}}); root.addView(search);
        ScrollView sv=new ScrollView(this);sv.setClipToPadding(false);sv.setPadding(0,4,0,4); messages=new LinearLayout(this); messages.setOrientation(LinearLayout.VERTICAL); sv.addView(messages); root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout composerRow=new LinearLayout(this);composerRow.setGravity(Gravity.CENTER_VERTICAL);composerRow.setPadding(8,6,4,6);composerRow.setBackground(rounded(Color.rgb(243,239,248),Color.rgb(230,224,236),30));
        composer=new EditText(this); composer.setHint("رسالة نصية"); composer.setTextSize(16);composer.setMinLines(1);composer.setMaxLines(5);composer.setPadding(12,6,12,6);composer.setBackgroundColor(Color.TRANSPARENT);composerRow.addView(composer,new LinearLayout.LayoutParams(0,-2,1));
        Button send=actionButton("إرسال",true);send.setOnClickListener(v->send());LinearLayout.LayoutParams sendLp=new LinearLayout.LayoutParams(-2,46);sendLp.setMargins(4,0,0,0);composerRow.addView(send,sendLp);LinearLayout.LayoutParams rowLp=new LinearLayout.LayoutParams(-1,-2);rowLp.topMargin=8;root.addView(composerRow,rowLp); setContentView(root);
    }

    void render(){ if(messages==null)return; messages.removeAllViews(); String q=search==null?"":search.getText().toString().trim().toLowerCase(Locale.ROOT); Cursor c=db.thread(address); int count=0;
        while(c.moveToNext()){ long id=c.getLong(c.getColumnIndexOrThrow("id")); String body=c.getString(c.getColumnIndexOrThrow("body")); String type=c.getString(c.getColumnIndexOrThrow("type")); String status=c.getString(c.getColumnIndexOrThrow("status")); int star=c.getInt(c.getColumnIndexOrThrow("star")); if(!q.isEmpty()&&!body.toLowerCase(Locale.ROOT).contains(q))continue;
            LinearLayout line = new LinearLayout(this); line.setGravity("out".equals(type)?Gravity.END:Gravity.START); line.setPadding(8,3,8,3);
            LinearLayout bubble=new LinearLayout(this); bubble.setOrientation(LinearLayout.VERTICAL); bubble.setPadding(16,8,16,8); TextView b=tv(body,17); b.setMaxWidth((int)(getResources().getDisplayMetrics().widthPixels-80*getResources().getDisplayMetrics().density)); TextView meta=tv(("out".equals(type)?status:"واردة")+(star==1?"  ★":""),12); bubble.addView(b); bubble.addView(meta);
            GradientDrawable shape=new GradientDrawable();shape.setColor("out".equals(type)?Color.rgb(232,221,255):Color.rgb(241,240,244));shape.setCornerRadius(24*getResources().getDisplayMetrics().density);bubble.setBackground(shape);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,-2); lp.setMargins(8,4,8,4); line.addView(bubble,lp); final long fid=id; bubble.setOnLongClickListener(v->{toggleSelection(fid);return true;}); bubble.setOnClickListener(v->{if(actionMode!=null)toggleSelection(fid);}); messages.addView(line); count++; }
        c.close(); if(count==0)messages.addView(tv(q.isEmpty()?"لا توجد رسائل":"لا توجد نتائج مطابقة",16));
    }

    void toggleSelection(long id){ if(actionMode==null){selected.clear(); actionMode=startActionMode(new SelectionCallback());} if(selected.contains(id))selected.remove(id);else selected.add(id); if(selected.isEmpty()){actionMode.finish();return;} selectionTitle.setText(selected.size()+" محددة"); actionMode.invalidate(); }

    class SelectionCallback implements ActionMode.Callback{
        public boolean onCreateActionMode(ActionMode mode,Menu menu){menu.add("نسخ");menu.add("إعادة توجيه");menu.add("★");menu.add("حذف");menu.add("تحديد الكل");return true;}
        public boolean onPrepareActionMode(ActionMode mode,Menu menu){return true;}
        public boolean onActionItemClicked(ActionMode mode,MenuItem item){String x=item.getTitle().toString(); if(x.equals("تحديد الكل")){selected.clear();Cursor c=db.thread(address);while(c.moveToNext())selected.add(c.getLong(c.getColumnIndexOrThrow("id")));c.close();selectionTitle.setText(selected.size()+" محددة");mode.invalidate();return true;} if(x.equals("نسخ")){copySelected();mode.finish();return true;} if(x.equals("حذف")){db.deleteMessages(selected);mode.finish();return true;} if(x.equals("★")){db.starMessages(selected);mode.finish();return true;} if(x.equals("إعادة توجيه")){forwardSelected();mode.finish();return true;} return false;}
        public void onDestroyActionMode(ActionMode mode){selected.clear();actionMode=null;selectionTitle.setText(address);render();}
    }

    void copySelected(){StringBuilder s=new StringBuilder();Cursor c=db.messagesByIds(selected);while(c.moveToNext())s.append(c.getString(c.getColumnIndexOrThrow("body"))).append("\n");c.close();((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(android.content.ClipData.newPlainText("SMS",s.toString()));Toast.makeText(this,"تم نسخ الرسائل",Toast.LENGTH_SHORT).show();}
    void forwardSelected(){Cursor c=db.messagesByIds(selected);StringBuilder s=new StringBuilder();while(c.moveToNext())s.append(c.getString(c.getColumnIndexOrThrow("body"))).append("\n");c.close();final String forwardedBody=s.toString().trim();final EditText to=new EditText(this);to.setHint("رقم المستلم");new AlertDialog.Builder(this).setTitle("إعادة توجيه").setMessage(forwardedBody).setView(to).setPositiveButton("إرسال",(d,w)->sendForwarded(to.getText().toString().trim(),forwardedBody)).setNegativeButton("إلغاء",null).show();}

    void sendForwarded(String recipient,String body){if(recipient.isEmpty()||body.trim().isEmpty()){Toast.makeText(this,"أدخل رقم المستلم",Toast.LENGTH_SHORT).show();return;}dispatchSms(recipient,body,"FORWARD",7000,8000);}
    void send(){String body=composer.getText().toString().trim();if(body.isEmpty())return;dispatchSms(address,body,"CONVERSATION",3000,4000);composer.setText("");}
    void dispatchSms(String recipient,String body,String tag,int sentBase,int deliveredBase){chooseSmsSubscription(subId->sendExecutor.execute(()->sendSmsBackground(recipient,body,tag,sentBase,deliveredBase,subId)));}
    boolean sendSmsBackground(String recipient,String body,String tag,int sentBase,int deliveredBase,int subscriptionId){
        long id;Db.PROVIDER_SYNC_LOCK.lock();try{id=db.addMessage(recipient,body,"out","sending");}finally{Db.PROVIDER_SYNC_LOCK.unlock();}
        if(id<=0){runOnUiThread(()->{if(!isFinishing()&&!isDestroyed())Toast.makeText(this,"تعذّر حفظ الرسالة محليًا",Toast.LENGTH_LONG).show();});return false;}
        try{db.saveOutgoingToSystemProvider(getApplicationContext(),id);SmsManager sm=subscriptionId>=0&&Build.VERSION.SDK_INT>=22?SmsManager.createForSubscriptionId(subscriptionId):SmsManager.getDefault();ArrayList<String> parts=sm.divideMessage(body);ArrayList<PendingIntent> sent=new ArrayList<>(),delivered=new ArrayList<>();for(int i=0;i<parts.size();i++){Intent si=new Intent(getApplicationContext(),SentReceiver.class);si.putExtra("id",id);si.putExtra("partCount",parts.size());si.putExtra("partIndex",i);si.setAction(getPackageName()+"."+tag+"_SENT."+id+"."+i);Intent di=new Intent(getApplicationContext(),DeliveredReceiver.class);di.putExtra("id",id);di.putExtra("partCount",parts.size());di.putExtra("partIndex",i);di.setAction(getPackageName()+"."+tag+"_DELIVERED."+id+"."+i);sent.add(PendingIntent.getBroadcast(getApplicationContext(),sentBase+(int)(id%100000)*10+i,si,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE));delivered.add(PendingIntent.getBroadcast(getApplicationContext(),deliveredBase+(int)(id%100000)*10+i,di,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE));}sm.sendMultipartTextMessage(recipient,null,parts,sent,delivered);runOnUiThread(()->{if(!isFinishing()&&!isDestroyed())render();});return true;}catch(Exception e){db.status(id,"لم يتم الإرسال");db.updateSystemProviderStatus(getApplicationContext(),id,false);runOnUiThread(()->{if(!isFinishing()&&!isDestroyed())Toast.makeText(this,"تعذّر الإرسال؛ تحقق من الشريحة والصلاحيات.",Toast.LENGTH_LONG).show();});return false;}
    }
    void chooseSmsSubscription(java.util.function.IntConsumer action){android.telephony.TelephonyManager tm;try{tm=(android.telephony.TelephonyManager)getSystemService(TELEPHONY_SERVICE);if(Build.VERSION.SDK_INT<23||tm==null||tm.getPhoneCount()<=1){action.accept(-1);return;}}catch(SecurityException e){action.accept(-1);return;}if(checkSelfPermission("android.permission.READ_PHONE_STATE")!=PackageManager.PERMISSION_GRANTED){pendingSubscriptionAction=action;requestPermissions(new String[]{"android.permission.READ_PHONE_STATE"},62);return;}showSmsSubscriptionChoices(action);}
    @android.annotation.SuppressLint("MissingPermission") void showSmsSubscriptionChoices(java.util.function.IntConsumer action){try{java.util.List<android.telephony.SubscriptionInfo> infos=android.telephony.SubscriptionManager.from(this).getActiveSubscriptionInfoList();if(infos==null||infos.size()<=1){action.accept(-1);return;}String[] labels=new String[infos.size()];for(int i=0;i<infos.size();i++){android.telephony.SubscriptionInfo info=infos.get(i);String name=info.getDisplayName()==null?"SIM "+(i+1):info.getDisplayName().toString();String number=info.getNumber();labels[i]=name+(number==null||number.isEmpty()?"":" · "+number);}new AlertDialog.Builder(this).setTitle("اختر شريحة الإرسال").setItems(labels,(dialog,which)->action.accept(infos.get(which).getSubscriptionId())).setNegativeButton("إلغاء",null).show();}catch(SecurityException e){action.accept(-1);}catch(RuntimeException e){android.util.Log.w("HashemSMS","SIM list unavailable; using default SMS subscription",e);action.accept(-1);}}
    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] grantResults){super.onRequestPermissionsResult(requestCode,permissions,grantResults);if(requestCode==62){java.util.function.IntConsumer action=pendingSubscriptionAction;pendingSubscriptionAction=null;if(action!=null){if(grantResults.length>0&&grantResults[0]==PackageManager.PERMISSION_GRANTED)showSmsSubscriptionChoices(action);else action.accept(-1);}}}
    void conversationMenu(View anchor){PopupMenu p=new PopupMenu(this,anchor);p.getMenu().add("بحث في المحادثة");p.getMenu().add("تحديد الكل");p.getMenu().add("حذف المحادثة");p.setOnMenuItemClickListener(i->{String x=i.getTitle().toString();if(x.startsWith("بحث")){search.setVisibility(search.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE);if(search.getVisibility()==View.VISIBLE)search.requestFocus();}else if(x.equals("تحديد الكل")){Cursor c=db.thread(address);selected.clear();while(c.moveToNext())selected.add(c.getLong(c.getColumnIndexOrThrow("id")));c.close();actionMode=startActionMode(new SelectionCallback());selectionTitle.setText(selected.size()+" محددة");}else{new AlertDialog.Builder(this).setMessage("حذف جميع رسائل هذه المحادثة؟").setPositiveButton("حذف",(d,w)->{db.deleteThread(address);render();}).setNegativeButton("إلغاء",null).show();}return true;});p.show();}

    @Override protected void onDestroy(){super.onDestroy();sendExecutor.shutdown();}

}
