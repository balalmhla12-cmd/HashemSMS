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
import android.provider.ContactsContract;
import android.net.Uri;
import android.telephony.SmsManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.*;import androidx.appcompat.widget.PopupMenu;
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
    private String conversationDisplayName = "";
    
    SubscriptionChoiceCallback pendingSubscriptionAction;

    TextView tv(String s,int size){ TextView t=new TextView(this); t.setText(s); t.setTextSize(size); t.setTextColor(UiResources.color(ConversationActivity.this,R.color.conversation_text)); t.setPadding(18,12,18,12); return t; }
    GradientDrawable rounded(int color,int stroke,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(radius*getResources().getDisplayMetrics().density);if(stroke!=0)d.setStroke(1,color==0?stroke:stroke);return d;}
    GradientDrawable messageGradient(boolean outgoing){GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,new int[]{UiResources.color(this,outgoing?R.color.bubble_out_start:R.color.bubble_in_start),UiResources.color(this,outgoing?R.color.bubble_out_end:R.color.bubble_in_end)});d.setCornerRadius(UiResources.dp(this,20));return d;}
    Button actionButton(String label,boolean primary){Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setTextSize(14);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);b.setTextColor(primary?UiResources.color(ConversationActivity.this,R.color.on_primary):UiResources.color(ConversationActivity.this,R.color.primary_variant));b.setPadding(14,6,14,6);b.setBackground(rounded(primary?purple:UiResources.color(ConversationActivity.this,R.color.primary_container),primary?0:UiResources.color(ConversationActivity.this,R.color.outline),28));b.setStateListAnimator(null);return b;}
    ImageButton iconActionButton(int iconRes,String description,boolean primary){ImageButton b=new ImageButton(this);b.setImageResource(iconRes);b.setContentDescription(description);b.setScaleType(ImageView.ScaleType.CENTER);b.setColorFilter(primary?UiResources.color(ConversationActivity.this,R.color.on_primary):UiResources.color(ConversationActivity.this,R.color.primary_variant));b.setPadding(0,0,0,0);b.setBackground(rounded(primary?purple:UiResources.color(ConversationActivity.this,R.color.primary_container),primary?0:UiResources.color(ConversationActivity.this,R.color.outline),28));b.setStateListAnimator(null);return b;}

    @Override public void onCreate(Bundle b){
        super.onCreate(b);UiResources.configureSystemBars(this);purple=UiResources.color(ConversationActivity.this,R.color.primary);
        db=new Db(getApplicationContext());
        address=getIntent().getStringExtra("address");
        if(address==null)address="";
        build();
        resolveConversationTitle();
        loadMessages(true);
    }

    @Override protected void onResume(){
        super.onResume();
        if(db!=null&&messages!=null){
            registerSmsUpdatedReceiver();
            resolveConversationTitle();
            loadMessages(false);
            if(!readMarkInFlight&&db.hasUnreadIncomingMessages(address)){
                markConversationReadAsync();
            }
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

    private String conversationTitle(){return conversationDisplayName==null||conversationDisplayName.trim().isEmpty()?address:conversationDisplayName;}
    private void resolveConversationTitle(){
        if(selectionTitle==null||actionMode!=null)return;
        selectionTitle.setText(conversationTitle());
        if(address==null||address.trim().isEmpty()||checkSelfPermission("android.permission.READ_CONTACTS")!=PackageManager.PERMISSION_GRANTED)return;
        final String currentAddress=address;
        MESSAGE_EXECUTOR.execute(()->{
            String found="";
            Cursor cursor=null;
            try{
                Uri lookup=Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI,Uri.encode(currentAddress));
                cursor=getContentResolver().query(lookup,new String[]{ContactsContract.PhoneLookup.DISPLAY_NAME},null,null,null);
                if(cursor!=null&&cursor.moveToFirst()){String value=cursor.getString(0);if(value!=null)found=value.trim();}
            }catch(Exception e){android.util.Log.w("HashemSMS","Unable to resolve conversation contact name",e);}
            finally{if(cursor!=null)try{cursor.close();}catch(Exception e){android.util.Log.w("HashemSMS","Unable to close contact lookup cursor",e);}}
            final String displayName=found;
            runOnUiThread(()->{
                if(isFinishing()||isDestroyed()||!Objects.equals(address,currentAddress))return;
                conversationDisplayName=displayName;
                if(actionMode==null&&selectionTitle!=null)selectionTitle.setText(conversationTitle());
            });
        });
    }

    void build(){
        root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(10,8,10,8); root.setBackgroundColor(UiResources.color(ConversationActivity.this,R.color.surface));
        LinearLayout top=new LinearLayout(this); top.setGravity(Gravity.CENTER_VERTICAL);top.setPadding(6,4,6,10);top.setBackground(rounded(UiResources.color(ConversationActivity.this,R.color.card_surface),UiResources.color(ConversationActivity.this,R.color.card_stroke),20));
        ImageButton back=iconActionButton(R.drawable.ic_arrow_back,"رجوع",false); back.setOnClickListener(v->finish()); top.addView(back,new LinearLayout.LayoutParams(52,52));
        LinearLayout identity=new LinearLayout(this);identity.setOrientation(LinearLayout.VERTICAL);
        selectionTitle=tv(address,18);selectionTitle.setTypeface(Typeface.DEFAULT,Typeface.BOLD);selectionTitle.setPadding(8,2,8,0);
        TextView subtitle=tv("رسائل نصية · SMS",12);subtitle.setTextColor(UiResources.color(ConversationActivity.this,R.color.on_surface_variant));subtitle.setPadding(8,0,8,0);
        identity.addView(selectionTitle);identity.addView(subtitle);top.addView(identity,new LinearLayout.LayoutParams(0,-2,1));
        ImageButton more=iconActionButton(R.drawable.ic_more_vert,"المزيد",false);more.setOnClickListener(v->conversationMenu(v)); top.addView(more,new LinearLayout.LayoutParams(48,52)); root.addView(top);
        search=new EditText(this); search.setHint("بحث في هذه المحادثة"); search.setSingleLine(); search.setTextSize(14);search.setPadding(16,4,16,4);search.setBackground(rounded(UiResources.color(ConversationActivity.this,R.color.surface_variant),UiResources.color(ConversationActivity.this,R.color.search_border),26));search.setVisibility(View.GONE);
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int d){} public void onTextChanged(CharSequence s,int a,int b,int c){mainHandler.removeCallbacks(searchDebounce);mainHandler.postDelayed(searchDebounce,275L);} public void afterTextChanged(Editable e){}}); root.addView(search);
        messages=new RecyclerView(this);
        messages.setClipToPadding(false);
        messages.setPadding(0,4,0,4);
        messages.setLayoutManager(new LinearLayoutManager(this));
        messageAdapter=new ConversationAdapter();
        messages.setAdapter(messageAdapter);
        root.addView(messages,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout composerRow=new LinearLayout(this);composerRow.setGravity(Gravity.CENTER_VERTICAL);composerRow.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);composerRow.setPadding(8,6,4,6);composerRow.setBackground(rounded(UiResources.color(ConversationActivity.this,R.color.surface_variant),UiResources.color(ConversationActivity.this,R.color.search_border),30));
        Button send=actionButton("إرسال",true);send.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_send,0,0,0);send.setCompoundDrawablePadding(UiResources.dp(ConversationActivity.this,6));send.setOnClickListener(v->send());LinearLayout.LayoutParams sendLp=new LinearLayout.LayoutParams(-2,46);sendLp.setMargins(4,0,0,0);composerRow.addView(send,sendLp);
        composer=new EditText(this);composer.setHint("رسالة نصية");composer.setTextSize(16);composer.setMinLines(1);composer.setMaxLines(5);composer.setPadding(12,6,12,6);composer.setBackgroundColor(UiResources.color(ConversationActivity.this,android.R.color.transparent));composerRow.addView(composer,new LinearLayout.LayoutParams(0,-2,1));
        ImageButton emoji=iconActionButton(R.drawable.ic_emoji,"إدراج رمز تعبيري",false);emoji.setOnClickListener(v->showEmojiPicker());composerRow.addView(emoji,new LinearLayout.LayoutParams(40,40));
        ImageButton attachment=iconActionButton(R.drawable.ic_attachment,"إرفاق وسائط",false);attachment.setOnClickListener(v->Toast.makeText(this,"إرسال الصور والفيديو عبر MMS غير متاح بعد",Toast.LENGTH_LONG).show());composerRow.addView(attachment,new LinearLayout.LayoutParams(40,40));
        LinearLayout.LayoutParams rowLp=new LinearLayout.LayoutParams(-1,-2);rowLp.topMargin=8;root.addView(composerRow,rowLp);setContentView(root);
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
        private long activeActionMessageId=Long.MIN_VALUE;
        ConversationAdapter(){super(new MessageDiffCallback());}
        void refreshSelection(long id){int position=findPosition(id);if(position>=0)notifyItemChanged(position);}
        void showActions(long id){
            long previous=activeActionMessageId;
            activeActionMessageId=previous==id?Long.MIN_VALUE:id;
            int oldPosition=findPosition(previous),newPosition=findPosition(activeActionMessageId);
            if(oldPosition>=0)notifyItemChanged(oldPosition);
            if(newPosition>=0&&newPosition!=oldPosition)notifyItemChanged(newPosition);
        }
        private int findPosition(long id){
            if(id==Long.MIN_VALUE)return -1;
            for(int i=0;i<getCurrentList().size();i++)if(getItem(i).id==id)return i;
            return -1;
        }
        @Override public int getItemViewType(int position){return getItem(position).isEmpty()?TYPE_EMPTY:TYPE_MESSAGE;}
        @Override public RecyclerView.ViewHolder onCreateViewHolder(android.view.ViewGroup parent,int viewType){
            if(viewType==TYPE_EMPTY){TextView empty=tv("",16);empty.setGravity(Gravity.CENTER);return new EmptyHolder(empty);}
            LinearLayout line=new LinearLayout(ConversationActivity.this);line.setGravity(Gravity.START);line.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);line.setPadding(8,3,8,3);
            LinearLayout column=new LinearLayout(ConversationActivity.this);column.setOrientation(LinearLayout.VERTICAL);column.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
            LinearLayout bubble=new LinearLayout(ConversationActivity.this);bubble.setOrientation(LinearLayout.VERTICAL);bubble.setPadding(16,8,16,8);
            TextView body=tv("",17);body.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);body.setMaxWidth((int)(getResources().getDisplayMetrics().widthPixels-80*getResources().getDisplayMetrics().density));
            TextView meta=tv("",12);meta.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);bubble.addView(body);bubble.addView(meta);
            LinearLayout.LayoutParams bubbleLp=new LinearLayout.LayoutParams(-2,-2);bubbleLp.setMargins(8,4,8,2);column.addView(bubble,bubbleLp);
            LinearLayout actions=new LinearLayout(ConversationActivity.this);actions.setOrientation(LinearLayout.HORIZONTAL);actions.setGravity(Gravity.CENTER_VERTICAL);actions.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);actions.setVisibility(View.GONE);
            ImageButton copy=messageActionIcon(R.drawable.ic_content_copy,"نسخ الرسالة"),forward=messageActionIcon(R.drawable.ic_forward,"إعادة توجيه الرسالة"),reply=messageActionIcon(R.drawable.ic_reply,"الرد على الرسالة"),star=messageActionIcon(R.drawable.ic_star,"تمييز الرسالة بنجمة"),more=messageActionIcon(R.drawable.ic_more_vert,"مزيد من إجراءات الرسالة");
            int actionSize=UiResources.dp(ConversationActivity.this,34);
            for(ImageButton button:new ImageButton[]{copy,forward,reply,star,more}){LinearLayout.LayoutParams actionLp=new LinearLayout.LayoutParams(actionSize,actionSize);actionLp.setMargins(2,0,2,0);actions.addView(button,actionLp);}
            column.addView(actions,new LinearLayout.LayoutParams(-2,-2));line.addView(column,new LinearLayout.LayoutParams(-2,-2));
            return new MessageHolder(line,column,bubble,body,meta,actions,copy,forward,reply,star,more);
        }
        @Override public void onBindViewHolder(RecyclerView.ViewHolder holder,int position){
            ConversationMessage item=getItem(position);
            if(holder instanceof EmptyHolder){((EmptyHolder)holder).text.setText(item.emptyText);return;}
            MessageHolder h=(MessageHolder)holder;boolean outgoing="out".equals(item.type);
            h.line.setGravity(outgoing?Gravity.END:Gravity.START);h.column.setGravity(outgoing?Gravity.END:Gravity.START);
            h.body.setText(item.body);h.body.setGravity(outgoing?Gravity.END:Gravity.START);
            h.body.setTextColor(UiResources.color(ConversationActivity.this,outgoing?R.color.on_bubble_out:R.color.on_bubble_in));
            h.meta.setText(outgoing?(item.status==null?"":item.status):"واردة");h.meta.setGravity(outgoing?Gravity.END:Gravity.START);
            h.meta.setTextColor(UiResources.color(ConversationActivity.this,outgoing?R.color.on_bubble_out:R.color.on_bubble_in));
            h.meta.setCompoundDrawablesRelativeWithIntrinsicBounds(0,0,item.star==1?R.drawable.ic_star:0,0);h.meta.setCompoundDrawablePadding(UiResources.dp(ConversationActivity.this,4));
            GradientDrawable bubbleBackground=messageGradient(outgoing);
            boolean isSelected=selected.contains(item.id);
            if(isSelected)bubbleBackground.setStroke(UiResources.dp(ConversationActivity.this,2),UiResources.color(ConversationActivity.this,R.color.primary_variant));
            h.bubble.setBackground(bubbleBackground);
            h.actions.setVisibility(item.id==activeActionMessageId&&actionMode==null?View.VISIBLE:View.GONE);
            View.OnLongClickListener longPress=v->{toggleSelection(item.id);return true;};
            View.OnClickListener tap=v->{if(actionMode!=null)toggleSelection(item.id);else showActions(item.id);};
            h.bubble.setOnLongClickListener(longPress);h.body.setOnLongClickListener(longPress);h.meta.setOnLongClickListener(longPress);
            h.bubble.setOnClickListener(tap);h.body.setOnClickListener(tap);h.meta.setOnClickListener(tap);
            h.copy.setOnClickListener(v->copyMessage(item));h.forward.setOnClickListener(v->forwardMessage(item));
            h.reply.setOnClickListener(v->{composer.setText("رد على الرسالة:\n");composer.setSelection(composer.length());composer.requestFocus();});
            h.star.setOnClickListener(v->{MESSAGE_EXECUTOR.execute(()->db.starMessages(new ArrayList<>(Collections.singletonList(item.id))));loadMessages(false);});
            h.more.setOnClickListener(v->showMessageMenu(v,item));
        }
    }

    private static final class MessageHolder extends RecyclerView.ViewHolder{
        final LinearLayout line,column,bubble,actions;final TextView body,meta;final ImageButton copy,forward,reply,star,more;
        MessageHolder(LinearLayout line,LinearLayout column,LinearLayout bubble,TextView body,TextView meta,LinearLayout actions,ImageButton copy,ImageButton forward,ImageButton reply,ImageButton star,ImageButton more){
            super(line);this.line=line;this.column=column;this.bubble=bubble;this.body=body;this.meta=meta;this.actions=actions;this.copy=copy;this.forward=forward;this.reply=reply;this.star=star;this.more=more;
        }
    }
    private static final class EmptyHolder extends RecyclerView.ViewHolder{
        final TextView text;
        EmptyHolder(TextView text){super(text);this.text=text;}
    }

    ImageButton messageActionIcon(int iconRes,String description){
        ImageButton button=new ImageButton(this);button.setImageResource(iconRes);button.setContentDescription(description);button.setScaleType(ImageView.ScaleType.CENTER);
        button.setColorFilter(UiResources.color(this,R.color.primary_variant));button.setBackground(rounded(UiResources.color(this,R.color.primary_container),0,22));button.setPadding(0,0,0,0);button.setStateListAnimator(null);return button;
    }
    void showEmojiPicker(){
        String[] emojis={"😀","😂","🥰","😍","👍","❤️","🙏","🎉","😢","🔥"};
        new AlertDialog.Builder(this).setTitle("اختر رمزًا تعبيريًا").setItems(emojis,(dialog,which)->{
            int start=Math.max(0,composer.getSelectionStart()),end=Math.max(0,composer.getSelectionEnd());
            composer.getText().replace(Math.min(start,end),Math.max(start,end),emojis[which]);composer.setSelection(Math.min(start,end)+emojis[which].length());
        }).show();
    }
    void copyMessage(ConversationMessage item){
        ((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(android.content.ClipData.newPlainText("SMS",item.body));
        Toast.makeText(this,"تم نسخ الرسالة",Toast.LENGTH_SHORT).show();
    }
    void forwardMessage(ConversationMessage item){
        final EditText recipient=new EditText(this);recipient.setHint("رقم المستلم");
        new AlertDialog.Builder(this).setTitle("إعادة توجيه الرسالة").setMessage(item.body).setView(recipient)
            .setPositiveButton("إرسال",(dialog,which)->sendForwarded(recipient.getText().toString().trim(),item.body))
            .setNegativeButton("إلغاء",null).show();
    }
    void showMessageMenu(View anchor,ConversationMessage item){
        PopupMenu menu=new PopupMenu(this,anchor);menu.setForceShowIcon(true);
        menu.getMenu().add("تفاصيل الرسالة").setIcon(R.drawable.ic_more_vert);menu.getMenu().add("حذف الرسالة").setIcon(R.drawable.ic_delete);
        menu.setOnMenuItemClickListener(choice->{
            if("تفاصيل الرسالة".contentEquals(choice.getTitle())){
                String direction="out".equals(item.type)?"صادرة":"واردة",status=item.status==null||item.status.trim().isEmpty()?"الحالة غير متاحة":item.status;
                new AlertDialog.Builder(this).setTitle("تفاصيل الرسالة").setMessage(direction+"\n"+status+"\n\n"+item.body).setPositiveButton("إغلاق",null).show();
            }else new AlertDialog.Builder(this).setMessage("هل تريد حذف هذه الرسالة؟").setPositiveButton("حذف",(dialog,which)->{
                MESSAGE_EXECUTOR.execute(()->{db.deleteMessages(new ArrayList<>(Collections.singletonList(item.id)));mainHandler.post(()->loadMessages(false));});
            }).setNegativeButton("إلغاء",null).show();
            return true;
        });menu.show();
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
        if(messageAdapter!=null)messageAdapter.refreshSelection(id);
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
                if(messageAdapter!=null)messageAdapter.notifyDataSetChanged();
                ActionMode target=mode!=null?mode:actionMode;
                if(target!=null)target.invalidate();
            });
        });
    }

    class SelectionCallback implements ActionMode.Callback{
        public boolean onCreateActionMode(ActionMode mode,Menu menu){MenuItem copy=menu.add("نسخ");copy.setIcon(R.drawable.ic_content_copy);copy.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);MenuItem forward=menu.add("إعادة توجيه");forward.setIcon(R.drawable.ic_forward);forward.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);MenuItem star=menu.add("تمييز بنجمة");star.setIcon(R.drawable.ic_star);star.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);MenuItem delete=menu.add("حذف");delete.setIcon(R.drawable.ic_delete);delete.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);menu.add("تحديد الكل");return true;}
        public boolean onPrepareActionMode(ActionMode mode,Menu menu){return true;}
        public boolean onActionItemClicked(ActionMode mode,MenuItem item){String x=item.getTitle().toString(); if(x.equals("تحديد الكل")){selectAllMessages(false,mode);return true;} if(x.equals("نسخ")){copySelected();mode.finish();return true;} if(x.equals("حذف")){ArrayList<Long> ids=new ArrayList<>(selected);MESSAGE_EXECUTOR.execute(()->db.deleteMessages(ids));mode.finish();loadMessages(false);return true;} if(x.equals("تمييز بنجمة")){ArrayList<Long> ids=new ArrayList<>(selected);MESSAGE_EXECUTOR.execute(()->db.starMessages(ids));mode.finish();loadMessages(false);return true;} if(x.equals("إعادة توجيه")){forwardSelected();mode.finish();return true;} return false;}
        public void onDestroyActionMode(ActionMode mode){ArrayList<Long> old=new ArrayList<>(selected);selected.clear();actionMode=null;selectionTitle.setText(conversationTitle());if(messageAdapter!=null)for(Long id:old)messageAdapter.refreshSelection(id);loadMessages(false);}
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
        android.telephony.TelephonyManager tm;try{tm=(android.telephony.TelephonyManager)getSystemService(TELEPHONY_SERVICE);if(tm==null||tm.getPhoneCount()<=1){callback.onSelected(-1);return;}}catch(SecurityException e){callback.onCancelled();return;}
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
    void conversationMenu(View anchor){PopupMenu p=new PopupMenu(this,anchor);p.setForceShowIcon(true);p.getMenu().add("بحث في المحادثة").setIcon(R.drawable.ic_search);p.getMenu().add("تحديد الكل");p.getMenu().add("حذف المحادثة").setIcon(R.drawable.ic_delete);p.setOnMenuItemClickListener(i->{String x=i.getTitle().toString();if(x.startsWith("بحث")){search.setVisibility(search.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE);if(search.getVisibility()==View.VISIBLE)search.requestFocus();else loadMessages(false);}else if(x.equals("تحديد الكل")){selectAllMessages(true,null);}else{new AlertDialog.Builder(this).setMessage("حذف جميع رسائل هذه المحادثة؟").setPositiveButton("حذف",(d,w)->{MESSAGE_EXECUTOR.execute(()->db.deleteThread(address));loadMessages(false);}).setNegativeButton("إلغاء",null).show();}return true;});p.show();}

}
