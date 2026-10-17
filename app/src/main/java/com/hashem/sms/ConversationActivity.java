package com.hashem.sms;

import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.DiffUtil;
import androidx.core.content.ContextCompat;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import android.database.Cursor;
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
    Db db; String address; LinearLayout root; RecyclerView messages; ConversationAdapter messageAdapter; EditText search, composer; TextView selectionTitle; ActionMode actionMode; final ArrayList<Long> selected = new ArrayList<>();
    private static final ExecutorService MESSAGE_EXECUTOR = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean readMarkInFlight = false;
    private final AtomicInteger loadGeneration = new AtomicInteger();
    private boolean firstMessageLoad = true;
    private boolean smsReceiverRegistered;
    private final Runnable smsUpdateDebounce = () -> loadMessages(false);
    private final Runnable searchDebounce = () -> loadMessages(false);
    private final BroadcastReceiver smsUpdatedReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            mainHandler.removeCallbacks(smsUpdateDebounce);
            mainHandler.postDelayed(smsUpdateDebounce, 250L);
        }
    };
    int purple;
    
    SubscriptionChoiceCallback pendingSubscriptionAction;

    TextView tv(String s,int size){ TextView t=new TextView(this); t.setText(s); t.setTextSize(size); t.setTextColor(UiResources.color(this,R.color.conversation_text)); t.setPadding(18,12,18,12); return t; }
    GradientDrawable rounded(int color,int stroke,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(radius*getResources().getDisplayMetrics().density);if(stroke!=0)d.setStroke(1,color==0?stroke:stroke);return d;}
    Button actionButton(String label,boolean primary){Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setTextSize(14);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);b.setTextColor(primary?UiResources.color(this,R.color.on_primary):UiResources.color(this,R.color.primary_variant));b.setPadding(14,6,14,6);b.setBackground(rounded(primary?purple:UiResources.color(this,R.color.primary_container),primary?0:UiResources.color(this,R.color.outline),28));if(android.os.Build.VERSION.SDK_INT>=21)b.setStateListAnimator(null);return b;}

    @Override public void onCreate(Bundle b){
        super.onCreate(b);purple=UiResources.color(this,R.color.primary);
        db=new Db(getApplicationContext());
        address=getIntent().getStringExtra("address");
        if(address==null)address="";
        build();
        loadMessages(true);
    }

    @Override protected void onResume(){
        super.onResume();
        if(db!=null&&messages!=null){
            registerSmsUpdatedReceiver();
            loadMessages(false);
            markConversationReadAsync();
        }
    }

    @Override protected void onPause(){
        mainHandler.removeCallbacks(smsUpdateDebounce);
        if(smsReceiverRegistered){
            try{unregisterReceiver(smsUpdatedReceiver);}catch(IllegalArgumentException ignored){}
            smsReceiverRegistered=false;
        }
        super.onPause();
    }

    @Override protected void onDestroy(){
        loadGeneration.incrementAndGet();
        mainHandler.removeCallbacks(smsUpdateDebounce);
        mainHandler.removeCallbacks(searchDebounce);
        super.onDestroy();
    }

    private void registerSmsUpdatedReceiver(){
        if(smsReceiverRegistered)return;
        IntentFilter filter=new IntentFilter(SmsReceiver.ACTION_SMS_UPDATED);
        ContextCompat.registerReceiver(this,smsUpdatedReceiver,filter,ContextCompat.RECEIVER_NOT_EXPORTED);
        smsReceiverRegistered=true;
    }

    private void markConversationReadAsync(){
        if(readMarkInFlight||db==null||!db.hasUnreadIncomingMessages(address))return;
        readMarkInFlight=true;
        final String currentAddress=address;
        MESSAGE_EXECUTOR.execute(()->{
            NotificationHelper.markConversationRead(getApplicationContext(),currentAddress);
            mainHandler.post(()->readMarkInFlight=false);
        });
    }

    void build(){
        root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(10,8,10,8); root.setBackgroundColor(UiResources.color(this,R.color.surface));
        LinearLayout top=new LinearLayout(this); top.setGravity(Gravity.CENTER_VERTICAL);top.setPadding(0,0,0,8);
        Button back=actionButton("‹",false); back.setTextSize(25); back.setOnClickListener(v->finish()); top.addView(back,new LinearLayout.LayoutParams(52,52));
        LinearLayout identity=new LinearLayout(this);identity.setOrientation(LinearLayout.VERTICAL);
        selectionTitle=tv(address,18);selectionTitle.setTypeface(Typeface.DEFAULT,Typeface.BOLD);selectionTitle.setPadding(8,2,8,0);
        TextView subtitle=tv("رسائل نصية · SMS",12);subtitle.setTextColor(UiResources.color(this,R.color.on_surface_variant));subtitle.setPadding(8,0,8,0);
        identity.addView(selectionTitle);identity.addView(subtitle);top.addView(identity,new LinearLayout.LayoutParams(0,-2,1));
        Button more=actionButton("⋮",false);more.setTextSize(22);more.setOnClickListener(v->conversationMenu(v)); top.addView(more,new LinearLayout.LayoutParams(48,52)); root.addView(top);
        search=new EditText(this); search.setHint("بحث في هذه المحادثة"); search.setSingleLine(); search.setTextSize(14);search.setPadding(16,4,16,4);search.setBackground(rounded(UiResources.color(this,R.color.surface_variant),UiResources.color(this,R.color.search_border),26));search.setVisibility(View.GONE);
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int d){} public void onTextChanged(CharSequence s,int a,int b,int c){mainHandler.removeCallbacks(searchDebounce);mainHandler.postDelayed(searchDebounce,275L);} public void afterTextChanged(Editable e){}}); root.addView(search);
        messages=new RecyclerView(this);
        messages.setClipToPadding(false);
        messages.setPadding(0,4,0,4);
        messages.setLayoutManager(new LinearLayoutManager(this));
        messageAdapter=new ConversationAdapter();
        messages.setAdapter(messageAdapter);
        root.addView(messages,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout composerRow=new LinearLayout(this);composerRow.setGravity(Gravity.CENTER_VERTICAL);composerRow.setPadding(8,6,4,6);composerRow.setBackground(rounded(UiResources.color(this,R.color.surface_variant),UiResources.color(this,R.color.search_border),30));
        composer=new EditText(this); composer.setHint("رسالة نصية"); composer.setTextSize(16);composer.setMinLines(1);composer.setMaxLines(5);composer.setPadding(12,6,12,6);composer.setBackgroundColor(Color.TRANSPARENT);composerRow.addView(composer,new LinearLayout.LayoutParams(0,-2,1));
        Button send=actionButton("إرسال",true);send.setOnClickListener(v->send());LinearLayout.LayoutParams sendLp=new LinearLayout.LayoutParams(-2,46);sendLp.setMargins(4,0,0,0);composerRow.addView(send,sendLp);LinearLayout.LayoutParams rowLp=new LinearLayout.LayoutParams(-1,-2);rowLp.topMargin=8;root.addView(composerRow,rowLp); setContentView(root);
    }

    private void loadMessages(boolean initialLoad){
        if(messages==null||db==null)return;
        final int generation=loadGeneration.incrementAndGet();
        final String currentAddress=address;
        final String query=search==null?"":search.getText().toString().trim().toLowerCase(Locale.ROOT);
        final boolean wasAtBottom=initialLoad||!messages.canScrollVertically(1);
        MESSAGE_EXECUTOR.execute(()->{
            ArrayList<ConversationMessage> rows=new ArrayList<>();
            HashSet<Long> existingMessageIds=new HashSet<>();
            Cursor c=null;
            try{
                c=db.thread(currentAddress);
                int idCol=c.getColumnIndexOrThrow("id");
                int bodyCol=c.getColumnIndexOrThrow("body");
                int typeCol=c.getColumnIndexOrThrow("type");
                int statusCol=c.getColumnIndexOrThrow("status");
                int starCol=c.getColumnIndexOrThrow("star");
                while(c.moveToNext()){
                    existingMessageIds.add(c.getLong(idCol));
                    String body=c.getString(bodyCol);
                    if(body==null)body="";
                    if(!query.isEmpty()&&!body.toLowerCase(Locale.ROOT).contains(query))continue;
                    rows.add(new ConversationMessage(c.getLong(idCol),body,c.getString(typeCol),c.getString(statusCol),c.getInt(starCol),null));
                }
            }catch(Exception e){
                android.util.Log.e("HashemSMS","Unable to load conversation messages",e);
                return;
            }finally{if(c!=null)c.close();}
            if(rows.isEmpty())rows.add(ConversationMessage.empty(query.isEmpty()?"لا توجد رسائل":"لا توجد نتائج مطابقة"));
            final ArrayList<ConversationMessage> result=rows;
            final HashSet<Long> loadedMessageIds=existingMessageIds;
            runOnUiThread(()->{
                if(isFinishing()||isDestroyed()||generation!=loadGeneration.get()||messageAdapter==null)return;
                final boolean first=firstMessageLoad;
                firstMessageLoad=false;
                messageAdapter.submitList(result,()->{
                    if(messages==null||generation!=loadGeneration.get())return;
                    pruneSelection(selected,loadedMessageIds);
                    if(selected.isEmpty()&&actionMode!=null){actionMode.finish();return;}
                    if(wasAtBottom){
                        int last=messageAdapter.getItemCount()-1;
                        if(last>=0){
                            if(first)messages.scrollToPosition(last);
                            else messages.post(()->{if(generation==loadGeneration.get()&&messages!=null)messages.smoothScrollToPosition(messageAdapter.getItemCount()-1);});
                        }
                    }
                });
            });
        });
    }

    static final class ConversationMessage {
        final long id;
        final String body,type,status,emptyText;
        final int star;
        ConversationMessage(long id,String body,String type,String status,int star,String emptyText){
            this.id=id;this.body=body;this.type=type;this.status=status;this.star=star;this.emptyText=emptyText;
        }
        static final long EMPTY_ID=Long.MIN_VALUE;
        static ConversationMessage empty(String text){return new ConversationMessage(EMPTY_ID,"","","",0,text);}
        boolean isEmpty(){return emptyText!=null;}
    }

    static final class MessageDiffCallback extends DiffUtil.ItemCallback<ConversationMessage>{
            @Override public boolean areItemsTheSame(ConversationMessage a,ConversationMessage b){return a.id==b.id;}
            @Override public boolean areContentsTheSame(ConversationMessage a,ConversationMessage b){
                return a.id==b.id&&Objects.equals(a.body,b.body)&&Objects.equals(a.type,b.type)&&Objects.equals(a.status,b.status)&&a.star==b.star&&Objects.equals(a.emptyText,b.emptyText);
            }
        }

    private final class ConversationAdapter extends ListAdapter<ConversationMessage,RecyclerView.ViewHolder>{
        private static final int TYPE_MESSAGE=1,TYPE_EMPTY=2;
        ConversationAdapter(){super(new MessageDiffCallback());}
        @Override public int getItemViewType(int position){return getItem(position).isEmpty()?TYPE_EMPTY:TYPE_MESSAGE;}
        @Override public RecyclerView.ViewHolder onCreateViewHolder(android.view.ViewGroup parent,int viewType){
            if(viewType==TYPE_EMPTY){
                TextView empty=tv("",16);empty.setGravity(Gravity.CENTER);
                return new EmptyHolder(empty);
            }
            LinearLayout line=new LinearLayout(ConversationActivity.this);line.setGravity(Gravity.START);line.setPadding(8,3,8,3);
            LinearLayout bubble=new LinearLayout(ConversationActivity.this);bubble.setOrientation(LinearLayout.VERTICAL);bubble.setPadding(16,8,16,8);
            TextView body=tv("",17);
            body.setMaxWidth((int)(getResources().getDisplayMetrics().widthPixels-80*getResources().getDisplayMetrics().density));
            TextView meta=tv("",12);
            bubble.addView(body);bubble.addView(meta);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,-2);lp.setMargins(8,4,8,4);line.addView(bubble,lp);
            return new MessageHolder(line,bubble,body,meta);
        }
        @Override public void onBindViewHolder(RecyclerView.ViewHolder holder,int position){
            ConversationMessage item=getItem(position);
            if(holder instanceof EmptyHolder){((EmptyHolder)holder).text.setText(item.emptyText);return;}
            MessageHolder h=(MessageHolder)holder;
            h.line.setGravity("out".equals(item.type)?Gravity.END:Gravity.START);
            h.body.setText(item.body);
            h.meta.setText(("out".equals(item.type)?(item.status==null?"":item.status):"واردة")+(item.star==1?"  ★":""));
            h.bubble.setBackground(rounded("out".equals(item.type)?UiResources.color(this,R.color.bubble_out):UiResources.color(this,R.color.bubble_in),0,24));
            h.bubble.setOnLongClickListener(v->{toggleSelection(item.id);return true;});
            h.bubble.setOnClickListener(v->{if(actionMode!=null)toggleSelection(item.id);});
        }
    }

    private static final class MessageHolder extends RecyclerView.ViewHolder{
        final LinearLayout line,bubble;final TextView body,meta;
        MessageHolder(LinearLayout line,LinearLayout bubble,TextView body,TextView meta){super(line);this.line=line;this.bubble=bubble;this.body=body;this.meta=meta;}
    }
    private static final class EmptyHolder extends RecyclerView.ViewHolder{
        final TextView text;
        EmptyHolder(TextView text){super(text);this.text=text;}
    }

    static void pruneSelection(ArrayList<Long> selection,Collection<Long> existingMessageIds){
        HashSet<Long> existingIds=new HashSet<>(existingMessageIds);
        selection.removeIf(id->!existingIds.contains(id));
    }

    void toggleSelection(long id){
        if(id==ConversationMessage.EMPTY_ID)return;
        if(actionMode==null){selected.clear();actionMode=startActionMode(new SelectionCallback());}
        if(selected.contains(id))selected.remove(id);else selected.add(id);
        if(selected.isEmpty()){if(actionMode!=null)actionMode.finish();return;}
        selectionTitle.setText(selected.size()+" محددة");
        if(actionMode!=null)actionMode.invalidate();
    }

    private void selectAllMessages(boolean startMode,ActionMode mode){
        final String currentAddress=address;
        MESSAGE_EXECUTOR.execute(()->{
            ArrayList<Long> ids=new ArrayList<>();
            Cursor c=null;
            try{c=db.thread(currentAddress);while(c.moveToNext())ids.add(c.getLong(c.getColumnIndexOrThrow("id")));}
            catch(Exception e){android.util.Log.e("HashemSMS","Unable to select conversation messages",e);return;}
            finally{if(c!=null)c.close();}
            runOnUiThread(()->{
                if(isFinishing()||isDestroyed())return;
                selected.clear();selected.addAll(ids);
                if(startMode&&actionMode==null)actionMode=startActionMode(new SelectionCallback());
                if(selectionTitle!=null)selectionTitle.setText(selected.size()+" محددة");
                ActionMode target=mode!=null?mode:actionMode;
                if(target!=null)target.invalidate();
            });
        });
    }

    class SelectionCallback implements ActionMode.Callback{
        public boolean onCreateActionMode(ActionMode mode,Menu menu){menu.add("نسخ");menu.add("إعادة توجيه");menu.add("★");menu.add("حذف");menu.add("تحديد الكل");return true;}
        public boolean onPrepareActionMode(ActionMode mode,Menu menu){return true;}
        public boolean onActionItemClicked(ActionMode mode,MenuItem item){String x=item.getTitle().toString(); if(x.equals("تحديد الكل")){selectAllMessages(false,mode);return true;} if(x.equals("نسخ")){copySelected();mode.finish();return true;} if(x.equals("حذف")){ArrayList<Long> ids=new ArrayList<>(selected);MESSAGE_EXECUTOR.execute(()->db.deleteMessages(ids));mode.finish();loadMessages(false);return true;} if(x.equals("★")){ArrayList<Long> ids=new ArrayList<>(selected);MESSAGE_EXECUTOR.execute(()->db.starMessages(ids));mode.finish();loadMessages(false);return true;} if(x.equals("إعادة توجيه")){forwardSelected();mode.finish();return true;} return false;}
        public void onDestroyActionMode(ActionMode mode){selected.clear();actionMode=null;selectionTitle.setText(address);loadMessages(false);}
    }

    void copySelected(){ArrayList<Long> ids=new ArrayList<>(selected);MESSAGE_EXECUTOR.execute(()->{StringBuilder copied=new StringBuilder();Cursor c=null;try{c=db.messagesByIds(ids);while(c.moveToNext())copied.append(c.getString(c.getColumnIndexOrThrow("body"))).append("\n");}finally{if(c!=null)c.close();}String text=copied.toString();runOnUiThread(()->{((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(android.content.ClipData.newPlainText("SMS",text));Toast.makeText(this,"تم نسخ الرسائل",Toast.LENGTH_SHORT).show();});});}
    void forwardSelected(){ArrayList<Long> ids=new ArrayList<>(selected);MESSAGE_EXECUTOR.execute(()->{StringBuilder body=new StringBuilder();Cursor c=null;try{c=db.messagesByIds(ids);while(c.moveToNext())body.append(c.getString(c.getColumnIndexOrThrow("body"))).append("\n");}finally{if(c!=null)c.close();}final String forwardedBody=body.toString().trim();runOnUiThread(()->{final EditText to=new EditText(this);to.setHint("رقم المستلم");new AlertDialog.Builder(this).setTitle("إعادة توجيه").setMessage(forwardedBody).setView(to).setPositiveButton("إرسال",(d,w)->sendForwarded(to.getText().toString().trim(),forwardedBody)).setNegativeButton("إلغاء",null).show();});});}

    void sendForwarded(String recipient,String body){if(recipient.isEmpty()||body.trim().isEmpty()){Toast.makeText(this,"أدخل رقم المستلم",Toast.LENGTH_SHORT).show();return;}dispatchSms(recipient,body,"FORWARD",7000,8000);}
    void send(){String body=composer.getText().toString().trim();if(body.isEmpty())return;dispatchSms(address,body,"CONVERSATION",3000,4000,()->composer.setText(""));}
    void dispatchSms(String recipient,String body,String tag,int sentBase,int deliveredBase){dispatchSms(recipient,body,tag,sentBase,deliveredBase,()->{});}
    void dispatchSms(String recipient,String body,String tag,int sentBase,int deliveredBase,Runnable onSelected){
        final android.content.Context appContext=getApplicationContext();final Db appDb=new Db(appContext);chooseSmsSubscription(new SubscriptionChoiceCallback(){public void onSelected(int subId){onSelected.run();SmsSending.SEND_EXECUTOR.execute(()->SmsSending.sendSmsInBackground(appContext,appDb,recipient,body,tag,sentBase,deliveredBase,subId,true));}public void onCancelled(){}});
    }
    void chooseSmsSubscription(SubscriptionChoiceCallback callback){
        android.telephony.TelephonyManager tm;try{tm=(android.telephony.TelephonyManager)getSystemService(TELEPHONY_SERVICE);if(Build.VERSION.SDK_INT<23||tm==null||tm.getPhoneCount()<=1){callback.onSelected(-1);return;}}catch(SecurityException e){callback.onCancelled();return;}
        if(checkSelfPermission("android.permission.READ_PHONE_STATE")!=PackageManager.PERMISSION_GRANTED){pendingSubscriptionAction=callback;requestPermissions(new String[]{"android.permission.READ_PHONE_STATE"},62);return;}showSmsSubscriptionChoices(callback);
    }
    @android.annotation.SuppressLint("MissingPermission") void showSmsSubscriptionChoices(SubscriptionChoiceCallback callback){
        final java.util.concurrent.atomic.AtomicBoolean resolved=new java.util.concurrent.atomic.AtomicBoolean(false);
        SubscriptionChoiceCallback once=new SubscriptionChoiceCallback(){public void onSelected(int id){if(resolved.compareAndSet(false,true))callback.onSelected(id);}public void onCancelled(){if(resolved.compareAndSet(false,true))callback.onCancelled();}};
        try{java.util.List<android.telephony.SubscriptionInfo> infos=android.telephony.SubscriptionManager.from(this).getActiveSubscriptionInfoList();
            if(infos==null||infos.isEmpty()){android.util.Log.w("HashemSMS","No active SIM list; cancelling send rather than silently using default");Toast.makeText(this,"لا توجد شريحة SIM نشطة؛ أُلغي الإرسال.",Toast.LENGTH_SHORT).show();once.onCancelled();return;}
            if(infos.size()==1){once.onSelected(infos.get(0).getSubscriptionId());return;}String[] labels=new String[infos.size()];
            for(int i=0;i<infos.size();i++){android.telephony.SubscriptionInfo info=infos.get(i);String name;try{CharSequence display=info.getDisplayName();name=display==null||display.toString().trim().isEmpty()?"SIM "+(info.getSimSlotIndex()+1):display.toString();}catch(RuntimeException e){name="SIM "+(info.getSimSlotIndex()+1);}
                String number="";try{String value=info.getNumber();if(value!=null)number=value.trim();}catch(RuntimeException e){android.util.Log.w("HashemSMS","Could not read SIM phone number for slot "+(info.getSimSlotIndex()+1),e);}labels[i]=name+(number.isEmpty()?"":" · "+number);}
            new AlertDialog.Builder(this).setTitle("اختر شريحة الإرسال").setItems(labels,(dialog,which)->{if(which>=0&&which<infos.size())once.onSelected(infos.get(which).getSubscriptionId());else once.onCancelled();}).setNegativeButton("إرسال بالشريحة الافتراضية",(dialog,which)->once.onSelected(-1)).setOnCancelListener(dialog->once.onCancelled()).show();
        }catch(SecurityException e){android.util.Log.w("HashemSMS","SIM list permission denied; cancelling send",e);Toast.makeText(this,"تعذّرت قراءة الشرائح؛ أُلغي الإرسال.",Toast.LENGTH_SHORT).show();once.onCancelled();}catch(RuntimeException e){android.util.Log.w("HashemSMS","SIM list could not be fetched; cancelling send",e);Toast.makeText(this,"تعذّر جلب قائمة الشرائح؛ أُلغي الإرسال.",Toast.LENGTH_SHORT).show();once.onCancelled();}
    }
    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] grantResults){
        super.onRequestPermissionsResult(requestCode,permissions,grantResults);if(requestCode!=62)return;SubscriptionChoiceCallback callback=pendingSubscriptionAction;pendingSubscriptionAction=null;if(callback==null)return;
        if(grantResults!=null&&grantResults.length>0&&grantResults[0]==PackageManager.PERMISSION_GRANTED)showSmsSubscriptionChoices(callback);else{Toast.makeText(this,"لم يُمنح إذن قراءة حالة الهاتف؛ سيتم الإرسال بالشريحة الافتراضية.",Toast.LENGTH_SHORT).show();callback.onSelected(-1);}
    }
    void conversationMenu(View anchor){PopupMenu p=new PopupMenu(this,anchor);p.getMenu().add("بحث في المحادثة");p.getMenu().add("تحديد الكل");p.getMenu().add("حذف المحادثة");p.setOnMenuItemClickListener(i->{String x=i.getTitle().toString();if(x.startsWith("بحث")){search.setVisibility(search.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE);if(search.getVisibility()==View.VISIBLE)search.requestFocus();else loadMessages(false);}else if(x.equals("تحديد الكل")){selectAllMessages(true,null);}else{new AlertDialog.Builder(this).setMessage("حذف جميع رسائل هذه المحادثة؟").setPositiveButton("حذف",(d,w)->{MESSAGE_EXECUTOR.execute(()->db.deleteThread(address));loadMessages(false);}).setNegativeButton("إلغاء",null).show();}return true;});p.show();}

}
