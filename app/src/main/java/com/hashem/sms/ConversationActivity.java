package com.hashem.sms;

import android.app.*;
import android.content.*;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
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

    TextView tv(String s,int size){ TextView t=new TextView(this); t.setText(s); t.setTextSize(size); t.setTextColor(Color.rgb(35,35,35)); t.setPadding(18,12,18,12); return t; }

    @Override public void onCreate(Bundle b){ super.onCreate(b); db=new Db(this); address=getIntent().getStringExtra("address"); if(address==null)address=""; db.markThreadRead(address); build(); render(); }

    void build(){
        root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Color.rgb(255,251,254));
        LinearLayout top=new LinearLayout(this); top.setGravity(Gravity.CENTER_VERTICAL);
        Button back=new Button(this); back.setText("‹"); back.setOnClickListener(v->finish()); top.addView(back,new LinearLayout.LayoutParams(58,58));
        selectionTitle=tv(address,20); top.addView(selectionTitle,new LinearLayout.LayoutParams(0,-2,1));
        Button more=new Button(this); more.setText("⋮"); more.setOnClickListener(v->conversationMenu(v)); top.addView(more,new LinearLayout.LayoutParams(58,58)); root.addView(top);
        search=new EditText(this); search.setHint("بحث في هذه المحادثة"); search.setSingleLine(); search.setVisibility(View.GONE);
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int d){} public void onTextChanged(CharSequence s,int a,int b,int c){render();} public void afterTextChanged(Editable e){}}); root.addView(search);
        ScrollView sv=new ScrollView(this); messages=new LinearLayout(this); messages.setOrientation(LinearLayout.VERTICAL); sv.addView(messages); root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout composerRow=new LinearLayout(this); composer=new EditText(this); composer.setHint("رسالة نصية"); composer.setMinLines(1); composerRow.addView(composer,new LinearLayout.LayoutParams(0,-2,1)); Button send=new Button(this); send.setText("إرسال"); send.setOnClickListener(v->send()); composerRow.addView(send); root.addView(composerRow); setContentView(root);
    }

    void render(){ if(messages==null)return; messages.removeAllViews(); String q=search==null?"":search.getText().toString().trim().toLowerCase(Locale.ROOT); Cursor c=db.thread(address); int count=0;
        while(c.moveToNext()){ long id=c.getLong(c.getColumnIndexOrThrow("id")); String body=c.getString(c.getColumnIndexOrThrow("body")); String type=c.getString(c.getColumnIndexOrThrow("type")); String status=c.getString(c.getColumnIndexOrThrow("status")); int star=c.getInt(c.getColumnIndexOrThrow("star")); if(!q.isEmpty()&&!body.toLowerCase(Locale.ROOT).contains(q))continue;
            LinearLayout line = new LinearLayout(this); line.setGravity("out".equals(type)?Gravity.END:Gravity.START); line.setPadding(8,3,8,3);
            LinearLayout bubble=new LinearLayout(this); bubble.setOrientation(LinearLayout.VERTICAL); bubble.setPadding(16,8,16,8); bubble.setMaxWidth((int)(getResources().getDisplayMetrics().widthPixels-80*getResources().getDisplayMetrics().density)); TextView b=tv(body,17); TextView meta=tv(("out".equals(type)?status:"واردة")+(star==1?"  ★":""),12); bubble.addView(b); bubble.addView(meta);
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
    void forwardSelected(){Cursor c=db.messagesByIds(selected);StringBuilder s=new StringBuilder();while(c.moveToNext())s.append(c.getString(c.getColumnIndexOrThrow("body"))).append("\n");c.close();final EditText to=new EditText(this);to.setHint("رقم المستلم");new AlertDialog.Builder(this).setTitle("إعادة توجيه").setMessage(s.toString()).setView(to).setPositiveButton("إرسال",(d,w)->{String n=to.getText().toString().trim();if(!n.isEmpty()){SmsManager.getDefault().sendTextMessage(n,null,s.toString(),null,null);Toast.makeText(this,"تمت محاولة الإرسال",Toast.LENGTH_SHORT).show();}}).setNegativeButton("إلغاء",null).show();}

    void conversationMenu(View anchor){PopupMenu p=new PopupMenu(this,anchor);p.getMenu().add("بحث في المحادثة");p.getMenu().add("تحديد الكل");p.getMenu().add("حذف المحادثة");p.setOnMenuItemClickListener(i->{String x=i.getTitle().toString();if(x.startsWith("بحث")){search.setVisibility(search.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE);if(search.getVisibility()==View.VISIBLE)search.requestFocus();}else if(x.equals("تحديد الكل")){Cursor c=db.thread(address);selected.clear();while(c.moveToNext())selected.add(c.getLong(c.getColumnIndexOrThrow("id")));c.close();actionMode=startActionMode(new SelectionCallback());selectionTitle.setText(selected.size()+" محددة");}else{new AlertDialog.Builder(this).setMessage("حذف جميع رسائل هذه المحادثة؟").setPositiveButton("حذف",(d,w)->{db.deleteThread(address);render();}).setNegativeButton("إلغاء",null).show();}return true;});p.show();}

    void send(){String body=composer.getText().toString().trim();if(body.isEmpty())return;long id=db.addMessage(address,body,"out","sending");db.saveOutgoingToSystemProvider(this,id);try{SmsManager sm=SmsManager.getDefault();ArrayList<String> parts=sm.divideMessage(body);ArrayList<PendingIntent> sent=new ArrayList<>(),delivered=new ArrayList<>();for(int i=0;i<parts.size();i++){Intent si=new Intent(this,SentReceiver.class);si.putExtra("id",id);si.putExtra("partCount",parts.size());si.putExtra("partIndex",i);si.setAction(getPackageName()+".CONVERSATION_SENT."+id+"."+i);Intent di=new Intent(this,DeliveredReceiver.class);di.putExtra("id",id);di.setAction(getPackageName()+".CONVERSATION_DELIVERED."+id+"."+i);sent.add(PendingIntent.getBroadcast(this,3000+(int)(id%100000)*10+i,si,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE));delivered.add(PendingIntent.getBroadcast(this,4000+(int)(id%100000)*10+i,di,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE));}sm.sendMultipartTextMessage(address,null,parts,sent,delivered);}catch(Exception e){db.status(id,"failed");db.updateSystemProviderStatus(this,id,false);Toast.makeText(this,"تعذّر إرسال الرسالة. تحقق من الشريحة والشبكة والصلاحيات.",Toast.LENGTH_LONG).show();}composer.setText("");render();}
}
