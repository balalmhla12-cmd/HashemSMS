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
    
    SubscriptionChoiceCallback pendingSubscriptionAction;

    TextView tv(String s,int size){ TextView t=new TextView(this); t.setText(s); t.setTextSize(size); t.setTextColor(Color.rgb(35,35,35)); t.setPadding(18,12,18,12); return t; }
    GradientDrawable rounded(int color,int stroke,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(radius*getResources().getDisplayMetrics().density);if(stroke!=0)d.setStroke(1,color==0?stroke:stroke);return d;}
    Button actionButton(String label,boolean primary){Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setTextSize(14);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);b.setTextColor(primary?Color.WHITE:Color.rgb(56,30,114));b.setPadding(14,6,14,6);b.setBackground(rounded(primary?purple:Color.rgb(234,221,255),primary?0:Color.rgb(218,207,237),28));if(android.os.Build.VERSION.SDK_INT>=21)b.setStateListAnimator(null);return b;}

    @Override public void onCreate(Bundle b){ super.onCreate(b); db=new Db(this); address=getIntent().getStringExtra("address"); if(address==null)address=""; db.markThreadRead(address);NotificationHelper.cancelConversationNotification(this,address); build(); render(); }

    @Override protected void onResume(){super.onResume();if(db!=null&&messages!=null){db.markThreadRead(address);NotificationHelper.cancelConversationNotification(this,address);render();}}

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
    void dispatchSms(String recipient,String body,String tag,int sentBase,int deliveredBase){
        final android.content.Context appContext=getApplicationContext();final Db appDb=new Db(appContext);chooseSmsSubscription(subId->SmsSending.EXECUTOR.execute(()->SmsSending.sendSmsInBackground(appContext,appDb,recipient,body,tag,sentBase,deliveredBase,subId,true)));
    }
    void chooseSmsSubscription(SubscriptionChoiceCallback callback){
        android.telephony.TelephonyManager tm;try{tm=(android.telephony.TelephonyManager)getSystemService(TELEPHONY_SERVICE);if(Build.VERSION.SDK_INT<23||tm==null||tm.getPhoneCount()<=1){callback.onSelected(-1);return;}}catch(SecurityException e){callback.onCancelled();return;}
        if(checkSelfPermission("android.permission.READ_PHONE_STATE")!=PackageManager.PERMISSION_GRANTED){pendingSubscriptionAction=callback;requestPermissions(new String[]{"android.permission.READ_PHONE_STATE"},62);return;}showSmsSubscriptionChoices(callback);
    }
    @android.annotation.SuppressLint("MissingPermission") void showSmsSubscriptionChoices(SubscriptionChoiceCallback callback){
        final java.util.concurrent.atomic.AtomicBoolean resolved=new java.util.concurrent.atomic.AtomicBoolean(false);
        java.util.function.IntConsumer onceSelected=id->{if(resolved.compareAndSet(false,true))callback.onSelected(id);};Runnable onceCancelled=()->{if(resolved.compareAndSet(false,true))callback.onCancelled();};
        try{java.util.List<android.telephony.SubscriptionInfo> infos=android.telephony.SubscriptionManager.from(this).getActiveSubscriptionInfoList();
            if(infos==null||infos.isEmpty()){android.util.Log.w("HashemSMS","No active SIM list; cancelling send rather than silently using default");onceCancelled.run();return;}
            if(infos.size()==1){onceSelected.accept(infos.get(0).getSubscriptionId());return;}String[] labels=new String[infos.size()];
            for(int i=0;i<infos.size();i++){android.telephony.SubscriptionInfo info=infos.get(i);String name;try{CharSequence display=info.getDisplayName();name=display==null||display.toString().trim().isEmpty()?"SIM "+(info.getSimSlotIndex()+1):display.toString();}catch(RuntimeException e){name="SIM "+(info.getSimSlotIndex()+1);}
                String number="";try{String value=info.getNumber();if(value!=null)number=value.trim();}catch(RuntimeException e){android.util.Log.w("HashemSMS","Could not read SIM phone number for slot "+(info.getSimSlotIndex()+1),e);}labels[i]=name+(number.isEmpty()?"":" · "+number);}
            new AlertDialog.Builder(this).setTitle("اختر شريحة الإرسال").setItems(labels,(dialog,which)->{if(which>=0&&which<infos.size())onceSelected.accept(infos.get(which).getSubscriptionId());else onceCancelled.run();}).setNegativeButton("إرسال بالشريحة الافتراضية",(dialog,which)->onceSelected.accept(-1)).setOnCancelListener(dialog->onceCancelled.run()).show();
        }catch(SecurityException e){android.util.Log.w("HashemSMS","SIM list permission denied; cancelling send",e);onceCancelled.run();}catch(RuntimeException e){android.util.Log.w("HashemSMS","SIM list could not be fetched; cancelling send",e);onceCancelled.run();}
    }
    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] grantResults){
        super.onRequestPermissionsResult(requestCode,permissions,grantResults);if(requestCode!=62)return;SubscriptionChoiceCallback callback=pendingSubscriptionAction;pendingSubscriptionAction=null;if(callback==null)return;
        if(grantResults!=null&&grantResults.length>0&&grantResults[0]==PackageManager.PERMISSION_GRANTED)showSmsSubscriptionChoices(callback);else callback.onCancelled();
    }
    void conversationMenu(View anchor){PopupMenu p=new PopupMenu(this,anchor);p.getMenu().add("بحث في المحادثة");p.getMenu().add("تحديد الكل");p.getMenu().add("حذف المحادثة");p.setOnMenuItemClickListener(i->{String x=i.getTitle().toString();if(x.startsWith("بحث")){search.setVisibility(search.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE);if(search.getVisibility()==View.VISIBLE)search.requestFocus();}else if(x.equals("تحديد الكل")){Cursor c=db.thread(address);selected.clear();while(c.moveToNext())selected.add(c.getLong(c.getColumnIndexOrThrow("id")));c.close();actionMode=startActionMode(new SelectionCallback());selectionTitle.setText(selected.size()+" محددة");}else{new AlertDialog.Builder(this).setMessage("حذف جميع رسائل هذه المحادثة؟").setPositiveButton("حذف",(d,w)->{db.deleteThread(address);render();}).setNegativeButton("إلغاء",null).show();}return true;});p.show();}

}
