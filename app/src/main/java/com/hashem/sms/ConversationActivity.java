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
    private TextView contactAvatar;
    private ImageView contactPhoto;
    private android.graphics.Bitmap contactPhotoBitmap;
    
    SubscriptionChoiceCallback pendingSubscriptionAction;

    TextView tv(String s,int size){ TextView t=new TextView(this); t.setText(s); t.setTextSize(size); t.setTextColor(UiResources.color(ConversationActivity.this,R.color.conversation_text)); t.setPadding(UiResources.dp(this,18),UiResources.dp(this,12),UiResources.dp(this,18),UiResources.dp(this,12)); return t; }
    GradientDrawable rounded(int color,int stroke,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(radius*getResources().getDisplayMetrics().density);if(stroke!=0)d.setStroke(1,color==0?stroke:stroke);return d;}
    GradientDrawable messageGradient(boolean outgoing){GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,new int[]{UiResources.color(this,outgoing?R.color.bubble_out_start:R.color.bubble_in_start),UiResources.color(this,outgoing?R.color.bubble_out_end:R.color.bubble_in_end)});d.setCornerRadius(UiResources.dp(this,20));return d;}
    Button actionButton(String label,boolean primary){Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setTextSize(14);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);b.setTextColor(primary?UiResources.color(ConversationActivity.this,R.color.on_primary):UiResources.color(ConversationActivity.this,R.color.primary_variant));b.setPadding(UiResources.dp(this,12),UiResources.dp(this,4),UiResources.dp(this,12),UiResources.dp(this,4));b.setMinHeight(UiResources.dp(this,44));b.setBackground(rounded(primary?purple:UiResources.color(ConversationActivity.this,R.color.primary_container),primary?0:UiResources.color(ConversationActivity.this,R.color.outline),28));b.setStateListAnimator(null);return b;}
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
    private String initialFor(String value){if(value==null||value.trim().isEmpty())return "?";String trimmed=value.trim();return trimmed.substring(0,1).toUpperCase(Locale.ROOT);}
    private void updateContactAvatar(){if(contactAvatar==null)return;boolean selecting=actionMode!=null&&!selected.isEmpty();if(selecting){contactAvatar.setText(String.valueOf(selected.size()));contactAvatar.setVisibility(View.VISIBLE);if(contactPhoto!=null)contactPhoto.setVisibility(View.GONE);return;}contactAvatar.setText(initialFor(conversationTitle()));if(contactPhoto!=null&&contactPhotoBitmap!=null){contactPhoto.setImageBitmap(contactPhotoBitmap);contactPhoto.setVisibility(View.VISIBLE);contactAvatar.setVisibility(View.GONE);}else{if(contactPhoto!=null)contactPhoto.setVisibility(View.GONE);contactAvatar.setVisibility(View.VISIBLE);}}
    private void resolveConversationTitle(){
        if(selectionTitle==null||actionMode!=null)return;
        selectionTitle.setText(conversationTitle());
        if(address==null||address.trim().isEmpty()||checkSelfPermission("android.permission.READ_CONTACTS")!=PackageManager.PERMISSION_GRANTED)return;
        final String currentAddress=address;
        MESSAGE_EXECUTOR.execute(()->{
            String found="";
            Uri photoUri=null;
            android.graphics.Bitmap photoBitmap=null;
            Cursor cursor=null;
            try{
                Uri lookup=Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI,Uri.encode(currentAddress));
                cursor=getContentResolver().query(lookup,new String[]{ContactsContract.PhoneLookup.DISPLAY_NAME,ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI},null,null,null);
                if(cursor!=null&&cursor.moveToFirst()){String value=cursor.getString(0);if(value!=null)found=value.trim();String rawPhoto=cursor.getString(1);if(rawPhoto!=null&&!rawPhoto.trim().isEmpty())photoUri=Uri.parse(rawPhoto);}
            }catch(Exception e){android.util.Log.w("HashemSMS","Unable to resolve conversation contact name",e);}
            finally{if(cursor!=null)try{cursor.close();}catch(Exception e){android.util.Log.w("HashemSMS","Unable to close contact lookup cursor",e);}}
            if(photoUri!=null){try(java.io.InputStream input=getContentResolver().openInputStream(photoUri)){if(input!=null)photoBitmap=android.graphics.BitmapFactory.decodeStream(input);}catch(Exception e){android.util.Log.w("HashemSMS","Unable to load conversation contact photo",e);}}
            final String displayName=found;
            final android.graphics.Bitmap avatarBitmap=photoBitmap;
            runOnUiThread(()->{
                if(isFinishing()||isDestroyed()||!Objects.equals(address,currentAddress))return;
                conversationDisplayName=displayName;
                contactPhotoBitmap=avatarBitmap;
                if(actionMode==null&&selectionTitle!=null)selectionTitle.setText(conversationTitle());
                updateContactAvatar();
            });
        });
    }

    void build(){
        root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL); root.setPadding(UiResources.dp(this,10),UiResources.dp(this,8),UiResources.dp(this,10),UiResources.dp(this,8)); root.setBackgroundColor(UiResources.color(ConversationActivity.this,R.color.surface));
        LinearLayout top=new LinearLayout(this); top.setGravity(Gravity.CENTER_VERTICAL);top.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);top.setPadding(UiResources.dp(this,6),UiResources.dp(this,4),UiResources.dp(this,6),UiResources.dp(this,8));top.setBackground(rounded(UiResources.color(ConversationActivity.this,R.color.card_surface),UiResources.color(ConversationActivity.this,R.color.card_stroke),20));
        ImageButton back=iconActionButton(R.drawable.ic_arrow_back,"رجوع",false); back.setOnClickListener(v->finish()); top.addView(back,new LinearLayout.LayoutParams(UiResources.dp(this,40),UiResources.dp(this,44)));
        LinearLayout identity=new LinearLayout(this);identity.setOrientation(LinearLayout.VERTICAL);identity.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        selectionTitle=tv(address,18);selectionTitle.setTypeface(Typeface.DEFAULT,Typeface.BOLD);selectionTitle.setPadding(UiResources.dp(this,4),UiResources.dp(this,2),UiResources.dp(this,4),0);selectionTitle.setSingleLine(true);selectionTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        TextView subtitle=tv("رسائل نصية · SMS",12);subtitle.setTextColor(UiResources.color(ConversationActivity.this,R.color.on_surface_variant));subtitle.setPadding(8,0,8,0);
        identity.addView(selectionTitle);identity.addView(subtitle);top.addView(identity,new LinearLayout.LayoutParams(0,-2,1));
        FrameLayout avatarFrame=new FrameLayout(this);avatarFrame.setBackground(rounded(UiResources.color(this,R.color.primary_container),0,28));avatarFrame.setClipToOutline(true);contactPhoto=new ImageView(this);contactPhoto.setScaleType(ImageView.ScaleType.CENTER_CROP);contactPhoto.setVisibility(View.GONE);avatarFrame.addView(contactPhoto,new FrameLayout.LayoutParams(-1,-1));contactAvatar=new TextView(this);contactAvatar.setGravity(Gravity.CENTER);contactAvatar.setTextSize(18);contactAvatar.setTypeface(Typeface.DEFAULT,Typeface.BOLD);contactAvatar.setTextColor(UiResources.color(this,R.color.on_primary_container));contactAvatar.setText(initialFor(conversationTitle()));avatarFrame.addView(contactAvatar,new FrameLayout.LayoutParams(-1,-1));LinearLayout.LayoutParams avatarLp=new LinearLayout.LayoutParams(UiResources.dp(this,40),UiResources.dp(this,40));avatarLp.setMargins(UiResources.dp(this,4),0,UiResources.dp(this,4),0);top.addView(avatarFrame,avatarLp);
        ImageButton call=iconActionButton(android.R.drawable.ic_menu_call,"اتصال",false);call.setOnClickListener(v->{try{startActivity(new Intent(Intent.ACTION_DIAL,Uri.parse("tel:"+Uri.encode(address))));}catch(Exception e){Toast.makeText(this,"تعذّر فتح الاتصال",Toast.LENGTH_SHORT).show();}});top.addView(call,new LinearLayout.LayoutParams(UiResources.dp(this,40),UiResources.dp(this,44)));
        ImageButton more=iconActionButton(R.drawable.ic_more_vert,"الخيارات",false);more.setOnClickListener(v->conversationMenu(v)); top.addView(more,new LinearLayout.LayoutParams(UiResources.dp(this,40),UiResources.dp(this,44))); root.addView(top);
        search=new EditText(this); search.setHint("بحث في هذه المحادثة"); search.setSingleLine(); search.setTextSize(14);search.setPadding(UiResources.dp(this,16),UiResources.dp(this,4),UiResources.dp(this,16),UiResources.dp(this,4));search.setMinHeight(UiResources.dp(this,48));search.setTextColor(UiResources.color(this,R.color.on_surface));search.setHintTextColor(UiResources.color(this,R.color.on_surface_variant));search.setBackground(rounded(UiResources.color(ConversationActivity.this,R.color.surface_variant),UiResources.color(ConversationActivity.this,R.color.search_border),26));search.setVisibility(View.GONE);
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int d){} public void onTextChanged(CharSequence s,int a,int b,int c){mainHandler.removeCallbacks(searchDebounce);mainHandler.postDelayed(searchDebounce,275L);} public void afterTextChanged(Editable e){}}); root.addView(search);
        messages=new RecyclerView(this);
        messages.setClipToPadding(false);
        messages.setPadding(0,4,0,4);
        messages.setLayoutManager(new LinearLayoutManager(this));
        messageAdapter=new ConversationAdapter();
        messages.setAdapter(messageAdapter);
        root.addView(messages,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout composerRow=new LinearLayout(this);composerRow.setGravity(Gravity.CENTER_VERTICAL);composerRow.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);composerRow.setPadding(UiResources.dp(this,4),UiResources.dp(this,5),UiResources.dp(this,6),UiResources.dp(this,5));composerRow.setBackground(rounded(UiResources.color(ConversationActivity.this,R.color.surface_variant),UiResources.color(ConversationActivity.this,R.color.search_border),30));
        ImageButton attachment=iconActionButton(R.drawable.ic_attachment,"إرفاق وسائط",false);attachment.setOnClickListener(v->Toast.makeText(this,"إرسال الصور والفيديو عبر MMS غير متاح بعد",Toast.LENGTH_LONG).show());composerRow.addView(attachment,new LinearLayout.LayoutParams(UiResources.dp(this,36),UiResources.dp(this,40)));
        ImageButton emoji=iconActionButton(R.drawable.ic_emoji,"إدراج رمز تعبيري",false);emoji.setOnClickListener(v->showEmojiPicker());composerRow.addView(emoji,new LinearLayout.LayoutParams(UiResources.dp(this,36),UiResources.dp(this,40)));
        composer=new EditText(this);composer.setHint("رسالة نصية");composer.setTextSize(16);composer.setMinLines(1);composer.setMaxLines(5);composer.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);composer.setTextDirection(View.TEXT_DIRECTION_RTL);composer.setGravity(Gravity.CENTER_VERTICAL|Gravity.RIGHT);composer.setPadding(UiResources.dp(this,12),UiResources.dp(this,6),UiResources.dp(this,12),UiResources.dp(this,6));composer.setTextColor(UiResources.color(this,R.color.on_surface));composer.setHintTextColor(UiResources.color(this,R.color.on_surface_variant));composer.setBackgroundColor(UiResources.color(ConversationActivity.this,android.R.color.transparent));composerRow.addView(composer,new LinearLayout.LayoutParams(0,-2,1));
        Button send=actionButton("إرسال",true);send.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_send,0,0,0);send.setCompoundDrawablePadding(UiResources.dp(ConversationActivity.this,6));send.setOnClickListener(v->send());LinearLayout.LayoutParams sendLp=new LinearLayout.LayoutParams(-2,UiResources.dp(this,40));sendLp.setMargins(UiResources.dp(this,4),0,0,0);composerRow.addView(send,sendLp);
        LinearLayout.LayoutParams rowLp=new LinearLayout.LayoutParams(-1,-2);rowLp.topMargin=UiResources.dp(this,8);root.addView(composerRow,rowLp);setContentView(root);
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
                    long timestamp=c.getLong(c.getColumnIndexOrThrow("ts"));long day=dayStart(timestamp);if(rows.isEmpty()||rows.get(rows.size()-1).timestamp==0L||dayStart(rows.get(rows.size()-1).timestamp)!=day)rows.add(ConversationMessage.date(day,timestamp));rows.add(new ConversationMessage(c.getLong(idCol),body,c.getString(typeCol),c.getString(statusCol),c.getInt(starCol),null,timestamp,false));
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
        final int star; final long timestamp; final boolean dateDivider;
        ConversationMessage(long id,String body,String type,String status,int star,String emptyText){this(id,body,type,status,star,emptyText,System.currentTimeMillis(),false);}
        ConversationMessage(long id,String body,String type,String status,int star,String emptyText,long timestamp,boolean dateDivider){this.id=id;this.body=body;this.type=type;this.status=status;this.star=star;this.emptyText=emptyText;this.timestamp=timestamp;this.dateDivider=dateDivider;}
        static final long EMPTY_ID=Long.MIN_VALUE;
        static ConversationMessage empty(String text){return new ConversationMessage(EMPTY_ID,"","","",0,text);}
        static ConversationMessage date(long day,long ts){return new ConversationMessage(-Math.max(1L,day),"","","",0,null,ts,true);}
        boolean isEmpty(){return emptyText!=null;}
        boolean isDateDivider(){return dateDivider;}
    }

    static final class MessageDiffCallback extends DiffUtil.ItemCallback<ConversationMessage>{
            @Override public boolean areItemsTheSame(ConversationMessage a,ConversationMessage b){return a.id==b.id;}
            @Override public boolean areContentsTheSame(ConversationMessage a,ConversationMessage b){
                return a.id==b.id&&Objects.equals(a.body,b.body)&&Objects.equals(a.type,b.type)&&Objects.equals(a.status,b.status)&&a.star==b.star&&Objects.equals(a.emptyText,b.emptyText)&&a.timestamp==b.timestamp&&a.dateDivider==b.dateDivider;
            }
        }

    private final class ConversationAdapter extends ListAdapter<ConversationMessage,RecyclerView.ViewHolder>{
        private static final int TYPE_MESSAGE=1,TYPE_EMPTY=2,TYPE_DATE=3;
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
        @Override public int getItemViewType(int position){ConversationMessage m=getItem(position);return m.isEmpty()?TYPE_EMPTY:m.isDateDivider()?TYPE_DATE:TYPE_MESSAGE;}
        @Override public RecyclerView.ViewHolder onCreateViewHolder(android.view.ViewGroup parent,int viewType){
            if(viewType==TYPE_EMPTY){TextView empty=tv("ابدأ المحادثة",16);empty.setGravity(Gravity.CENTER);empty.setCompoundDrawablesWithIntrinsicBounds(0,android.R.drawable.ic_dialog_email,0,0);empty.setCompoundDrawablePadding(UiResources.dp(ConversationActivity.this,12));return new EmptyHolder(empty);}
            if(viewType==TYPE_DATE){TextView date=tv("",12);date.setGravity(Gravity.CENTER);date.setTextColor(UiResources.color(ConversationActivity.this,R.color.on_surface_variant));date.setPadding(0,UiResources.dp(ConversationActivity.this,10),0,UiResources.dp(ConversationActivity.this,8));return new DateHolder(date);}
            LinearLayout line=new LinearLayout(ConversationActivity.this);line.setGravity(Gravity.START);line.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);line.setPadding(8,3,8,3);
            LinearLayout column=new LinearLayout(ConversationActivity.this);column.setOrientation(LinearLayout.VERTICAL);column.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
            LinearLayout bubble=new LinearLayout(ConversationActivity.this);bubble.setOrientation(LinearLayout.VERTICAL);bubble.setPadding(16,8,16,8);
            TextView body=tv("",17);body.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);body.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);body.setMaxWidth((int)(getResources().getDisplayMetrics().widthPixels-80*getResources().getDisplayMetrics().density));
            TextView meta=tv("",11);meta.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);bubble.addView(body);bubble.addView(meta);
            Button otp=actionButton("نسخ الرمز",false);otp.setTextSize(12);otp.setVisibility(View.GONE);bubble.addView(otp,new LinearLayout.LayoutParams(-2,UiResources.dp(ConversationActivity.this,32)));
            Button retry=actionButton("إعادة المحاولة",false);retry.setTextSize(12);retry.setVisibility(View.GONE);bubble.addView(retry,new LinearLayout.LayoutParams(-2,UiResources.dp(ConversationActivity.this,32)));
            LinearLayout.LayoutParams bubbleLp=new LinearLayout.LayoutParams(-2,-2);bubbleLp.setMargins(8,4,8,2);column.addView(bubble,bubbleLp);
            LinearLayout actions=new LinearLayout(ConversationActivity.this);actions.setOrientation(LinearLayout.HORIZONTAL);actions.setGravity(Gravity.CENTER_VERTICAL);actions.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);actions.setVisibility(View.GONE);
            ImageButton copy=messageActionIcon(R.drawable.ic_content_copy,"نسخ"),forward=messageActionIcon(R.drawable.ic_forward,"إعادة توجيه"),reply=messageActionIcon(android.R.drawable.ic_menu_edit,"تعديل"),star=messageActionIcon(R.drawable.ic_star,"نجمة"),more=messageActionIcon(R.drawable.ic_more_vert,"خيارات");
            int actionSize=UiResources.dp(ConversationActivity.this,34);
            for(ImageButton button:new ImageButton[]{copy,forward,reply,star,more}){LinearLayout.LayoutParams actionLp=new LinearLayout.LayoutParams(actionSize,actionSize);actionLp.setMargins(2,0,2,0);actions.addView(button,actionLp);}
            column.addView(actions,new LinearLayout.LayoutParams(-2,-2));
            TextView selector=tv("○",22);selector.setGravity(Gravity.CENTER);selector.setTextColor(UiResources.color(ConversationActivity.this,R.color.primary_variant));selector.setVisibility(View.GONE);line.addView(selector,new LinearLayout.LayoutParams(UiResources.dp(ConversationActivity.this,28),UiResources.dp(ConversationActivity.this,28)));line.addView(column,new LinearLayout.LayoutParams(-2,-2));
            return new MessageHolder(line,column,bubble,body,meta,actions,copy,forward,reply,star,more,otp,retry,selector);
        }
        @Override public void onBindViewHolder(RecyclerView.ViewHolder holder,int position){
            ConversationMessage item=getItem(position);
            if(holder instanceof EmptyHolder){((EmptyHolder)holder).text.setText(item.emptyText);return;}
            if(holder instanceof DateHolder){((DateHolder)holder).text.setText(dateLabel(item.timestamp));return;}
            MessageHolder h=(MessageHolder)holder;boolean outgoing="out".equals(item.type);
            h.line.setGravity(outgoing?Gravity.END:Gravity.START);h.column.setGravity(outgoing?Gravity.END:Gravity.START);
            h.body.setText(item.body);h.body.setGravity(outgoing?Gravity.END:Gravity.START);h.body.setTextIsSelectable(false);
            h.body.setTextColor(UiResources.color(ConversationActivity.this,outgoing?R.color.on_bubble_out:R.color.on_bubble_in));
            String state=outgoing?displayStatus(item.status):"";String time=new java.text.SimpleDateFormat("h:mm a",new Locale("ar")).format(new Date(item.timestamp));h.meta.setText((state.isEmpty()?"":state+" · ")+time);h.meta.setGravity(outgoing?Gravity.END:Gravity.START);
            h.meta.setTextColor(UiResources.color(ConversationActivity.this,outgoing?R.color.on_bubble_out:R.color.on_bubble_in));
            h.meta.setCompoundDrawablesRelativeWithIntrinsicBounds(0,0,item.star==1?R.drawable.ic_star:0,0);h.meta.setCompoundDrawablePadding(UiResources.dp(ConversationActivity.this,4));
            GradientDrawable bubbleBackground=messageGradient(outgoing);
            boolean isSelected=selected.contains(item.id);
            if(isSelected)bubbleBackground.setStroke(UiResources.dp(ConversationActivity.this,2),UiResources.color(ConversationActivity.this,R.color.primary_variant));
            h.bubble.setBackground(bubbleBackground);
            h.actions.setVisibility(item.id==activeActionMessageId&&actionMode==null?View.VISIBLE:View.GONE);h.actions.setContentDescription("إجراءات الرسالة");
            h.selector.setVisibility(actionMode!=null?View.VISIBLE:View.GONE);h.selector.setText(isSelected?"●":"○");h.selector.setContentDescription(isSelected?"رسالة محددة":"تحديد الرسالة");
            h.selector.setOnClickListener(v->{if(actionMode!=null)toggleSelection(item.id);});
            h.otp.setVisibility(View.GONE);h.retry.setVisibility(View.GONE);
            java.util.regex.Matcher otpMatcher=java.util.regex.Pattern.compile("(?<!\\d)(\\d{4,8})(?!\\d)").matcher(item.body);
            if(!outgoing&&otpMatcher.find()){String code=otpMatcher.group(1);h.otp.setVisibility(View.VISIBLE);h.otp.setOnClickListener(v->{((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(android.content.ClipData.newPlainText("رمز التحقق",code));Toast.makeText(this,"تم نسخ رمز التحقق",Toast.LENGTH_SHORT).show();});}
            else if(outgoing&&isFailed(item.status)){h.retry.setVisibility(View.VISIBLE);h.retry.setOnClickListener(v->dispatchSms(address,item.body,"RETRY",3000,4000));}
            View.OnLongClickListener longPress=v->{toggleSelection(item.id);return true;};
            View.OnClickListener tap=v->{if(actionMode!=null)toggleSelection(item.id);else showActions(item.id);};
            h.bubble.setOnLongClickListener(longPress);h.body.setOnLongClickListener(longPress);h.meta.setOnLongClickListener(longPress);h.selector.setOnLongClickListener(longPress);
            h.bubble.setOnClickListener(tap);h.body.setOnClickListener(tap);h.meta.setOnClickListener(tap);
            h.copy.setOnClickListener(v->copyMessage(item));h.forward.setOnClickListener(v->forwardMessage(item));
            h.reply.setVisibility(outgoing?View.VISIBLE:View.GONE);h.reply.setOnClickListener(v->{if(outgoing){composer.setText(item.body);composer.setSelection(composer.length());composer.requestFocus();}});
            h.star.setOnClickListener(v->{MESSAGE_EXECUTOR.execute(()->db.starMessages(new ArrayList<>(Collections.singletonList(item.id))));loadMessages(false);});
            h.more.setOnClickListener(v->showMessageMenu(v,item));
        }
    }

    private static final class MessageHolder extends RecyclerView.ViewHolder{
        final LinearLayout line,column,bubble,actions;final TextView body,meta,selector;final ImageButton copy,forward,reply,star,more;final Button otp,retry;
        MessageHolder(LinearLayout line,LinearLayout column,LinearLayout bubble,TextView body,TextView meta,LinearLayout actions,ImageButton copy,ImageButton forward,ImageButton reply,ImageButton star,ImageButton more,Button otp,Button retry,TextView selector){
            super(line);this.line=line;this.column=column;this.bubble=bubble;this.body=body;this.meta=meta;this.actions=actions;this.copy=copy;this.forward=forward;this.reply=reply;this.star=star;this.more=more;this.otp=otp;this.retry=retry;this.selector=selector;
        }
    }
    private static final class EmptyHolder extends RecyclerView.ViewHolder{
        final TextView text;
        EmptyHolder(TextView text){super(text);this.text=text;}
    }
    private static final class DateHolder extends RecyclerView.ViewHolder{final TextView text;DateHolder(TextView text){super(text);this.text=text;}}

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
        menu.getMenu().add("حذف").setIcon(R.drawable.ic_delete);menu.getMenu().add("تفاصيل الرسالة").setIcon(R.drawable.ic_more_vert);
        menu.setOnMenuItemClickListener(choice->{
            if("تفاصيل الرسالة".contentEquals(choice.getTitle())){
                String direction="out".equals(item.type)?"صادرة":"واردة",status=item.status==null||item.status.trim().isEmpty()?"الحالة غير متاحة":item.status;
                new AlertDialog.Builder(this).setTitle("تفاصيل الرسالة").setMessage(direction+"\n"+status+"\n\n"+item.body).setPositiveButton("إغلاق",null).show();
            }else new AlertDialog.Builder(this).setTitle("حذف الرسالة؟").setMessage("هل تريد حذف هذه الرسالة؟").setNegativeButton("إلغاء",null).setPositiveButton("حذف",(dialog,which)->{
                MESSAGE_EXECUTOR.execute(()->{db.deleteMessages(new ArrayList<>(Collections.singletonList(item.id)));mainHandler.post(()->loadMessages(false));});
            }).setNegativeButton("إلغاء",null).show();
            return true;
        });menu.show();
    }

    static void pruneSelection(ArrayList<Long> selection,Collection<Long> existingMessageIds){
        HashSet<Long> existingIds=new HashSet<>(existingMessageIds);
        selection.removeIf(id->!existingIds.contains(id)||id<0);
    }
    private long dayStart(long timestamp){Calendar cal=Calendar.getInstance();cal.setTimeInMillis(timestamp);cal.set(Calendar.HOUR_OF_DAY,0);cal.set(Calendar.MINUTE,0);cal.set(Calendar.SECOND,0);cal.set(Calendar.MILLISECOND,0);return cal.getTimeInMillis();}
    private String dateLabel(long timestamp){long today=dayStart(System.currentTimeMillis()),day=dayStart(timestamp);if(day==today)return "اليوم";if(day==today-86400000L)return "أمس";return new java.text.SimpleDateFormat("d MMMM yyyy",new Locale("ar")).format(new Date(timestamp));}
    private String displayStatus(String status){if(status==null)return "";String s=status.trim().toLowerCase(Locale.ROOT);if(s.isEmpty())return "";if(s.contains("fail")||s.contains("فشل")||s.contains("لم يتم"))return "فشل";if(s.contains("send")||s.contains("sent")||s.contains("تم"))return "تم";if(s.contains("pending")||s.contains("outbox")||s.contains("جار"))return "قيد الإرسال";return status;}
    private boolean isFailed(String status){String s=status==null?"":status.toLowerCase(Locale.ROOT);return s.contains("fail")||s.contains("فشل")||s.contains("لم يتم");}

    void toggleSelection(long id){
        if(id==ConversationMessage.EMPTY_ID)return;
        if(actionMode==null){selected.clear();actionMode=startActionMode(new SelectionCallback());}
        if(selected.contains(id))selected.remove(id);else selected.add(id);
        if(selected.isEmpty()){if(actionMode!=null)actionMode.finish();return;}
        if(actionMode!=null)actionMode.setTitle(selected.size()+" محددة");selectionTitle.setText(selected.size()+" محددة");updateContactAvatar();
        if(messageAdapter!=null)messageAdapter.notifyDataSetChanged();
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
                if(mode!=null)mode.setTitle(selected.size()+" محددة");else if(actionMode!=null)actionMode.setTitle(selected.size()+" محددة");
                updateContactAvatar();
                if(messageAdapter!=null)messageAdapter.notifyDataSetChanged();
                ActionMode target=mode!=null?mode:actionMode;
                if(target!=null)target.invalidate();
            });
        });
    }

    class SelectionCallback implements ActionMode.Callback{
        public boolean onCreateActionMode(ActionMode mode,Menu menu){mode.setTitle("0 محددة");MenuItem cancel=menu.add(0,6,0,"إلغاء");cancel.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);MenuItem all=menu.add(0,5,1,"تحديد الكل");all.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);MenuItem copy=menu.add(0,1,2,"نسخ");copy.setIcon(R.drawable.ic_content_copy);copy.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);MenuItem delete=menu.add(0,4,3,"حذف");delete.setIcon(R.drawable.ic_delete);delete.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);return true;}
        public boolean onPrepareActionMode(ActionMode mode,Menu menu){return true;}
        public boolean onActionItemClicked(ActionMode mode,MenuItem item){int id=item.getItemId(); if(id==6){mode.finish();return true;} if(id==5){selectAllMessages(false,mode);mode.setTitle(selected.size()+" محددة");return true;} if(id==1){copySelected();mode.finish();return true;} if(id==4){ArrayList<Long> ids=new ArrayList<>(selected);new AlertDialog.Builder(ConversationActivity.this).setTitle("حذف الرسائل؟").setMessage("هل تريد حذف "+ids.size()+" رسالة محددة؟").setNegativeButton("إلغاء",null).setPositiveButton("حذف",(dialog,which)->MESSAGE_EXECUTOR.execute(()->{db.deleteMessages(ids);mainHandler.post(()->loadMessages(false));})).show();mode.finish();return true;} return false;}
        public void onDestroyActionMode(ActionMode mode){ArrayList<Long> old=new ArrayList<>(selected);selected.clear();actionMode=null;selectionTitle.setText(conversationTitle());updateContactAvatar();if(messageAdapter!=null)for(Long id:old)messageAdapter.refreshSelection(id);loadMessages(false);}
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
    void conversationMenu(View anchor){PopupMenu p=new PopupMenu(this,anchor);p.setForceShowIcon(true);p.getMenu().add("بحث في المحادثة").setIcon(R.drawable.ic_search);p.getMenu().add("تحديد الكل");p.getMenu().add("حذف المحادثة").setIcon(R.drawable.ic_delete);p.setOnMenuItemClickListener(i->{String x=i.getTitle().toString();if(x.startsWith("بحث")){search.setVisibility(search.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE);if(search.getVisibility()==View.VISIBLE)search.requestFocus();else loadMessages(false);}else if(x.equals("تحديد الكل")){selectAllMessages(true,null);}else{new AlertDialog.Builder(this).setTitle("حذف المحادثة؟").setMessage("حذف جميع رسائل هذه المحادثة؟").setPositiveButton("حذف",(d,w)->{MESSAGE_EXECUTOR.execute(()->db.deleteThread(address));loadMessages(false);}).setNegativeButton("إلغاء",null).show();}return true;});p.show();}

}
