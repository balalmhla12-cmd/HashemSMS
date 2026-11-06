package com.hashem.sms;
import android.content.*;import android.database.sqlite.*;import android.database.*;import android.net.Uri;import java.io.File;import android.provider.Telephony;import android.util.Log;import java.util.*;import java.util.concurrent.locks.ReentrantLock;
public class Db extends SQLiteOpenHelper{
 public static final ReentrantLock PROVIDER_SYNC_LOCK = new ReentrantLock();
 public Db(Context c){super(c,"hashem.db",null,12);} 
 @Override public void onConfigure(SQLiteDatabase d){super.onConfigure(d);d.setForeignKeyConstraintsEnabled(true);}
 public void onCreate(SQLiteDatabase d){
  d.execSQL("CREATE TABLE messages(id INTEGER PRIMARY KEY AUTOINCREMENT,address TEXT,body TEXT,type TEXT,status TEXT,ts INTEGER,provider_id INTEGER,read INTEGER DEFAULT 0,star INTEGER DEFAULT 0,trash INTEGER NOT NULL DEFAULT 0,bulk_campaign_id INTEGER)");
  d.execSQL("CREATE TABLE groups(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT UNIQUE)");
  d.execSQL("CREATE TABLE group_members(gid INTEGER,address TEXT,name TEXT,PRIMARY KEY(gid,address))");
  d.execSQL("CREATE TABLE schedules(id INTEGER PRIMARY KEY AUTOINCREMENT,address TEXT,body TEXT,at INTEGER)");
  d.execSQL("CREATE TABLE forward_rules(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL UNIQUE,destination TEXT NOT NULL)");
  d.execSQL("CREATE TABLE conversation_flags(address TEXT PRIMARY KEY,archived INTEGER NOT NULL DEFAULT 0,blocked INTEGER NOT NULL DEFAULT 0)");
  createPerformanceIndexes(d);d.execSQL("CREATE INDEX IF NOT EXISTS idx_messages_provider_id ON messages(provider_id)");createMmsAttachmentsTable(d);createBulkCampaignsTable(d);
 }
 private boolean hasColumn(SQLiteDatabase d,String table,String column){Cursor c=null;try{c=d.rawQuery("PRAGMA table_info("+table+")",null);while(c.moveToNext())if(column.equalsIgnoreCase(c.getString(c.getColumnIndexOrThrow("name"))))return true;return false;}finally{if(c!=null)c.close();}}
 private void createPerformanceIndexes(SQLiteDatabase d){d.execSQL("CREATE INDEX IF NOT EXISTS idx_messages_address_ts ON messages(address,ts DESC)");d.execSQL("CREATE INDEX IF NOT EXISTS idx_messages_trash_type_ts ON messages(trash,type,ts DESC)");d.execSQL("CREATE INDEX IF NOT EXISTS idx_messages_type_read_trash_address ON messages(type,read,trash,address)");d.execSQL("CREATE INDEX IF NOT EXISTS idx_messages_star_ts ON messages(star,ts DESC)");}
 public void onUpgrade(SQLiteDatabase d,int oldVersion,int newVersion){
  if(oldVersion<2&&!hasColumn(d,"messages","star"))d.execSQL("ALTER TABLE messages ADD COLUMN star INTEGER DEFAULT 0");
  if(oldVersion<3)d.execSQL("CREATE TABLE IF NOT EXISTS forward_rules(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL UNIQUE,destination TEXT NOT NULL)");
  if(oldVersion<4)d.execSQL("CREATE TABLE IF NOT EXISTS conversation_flags(address TEXT PRIMARY KEY,archived INTEGER NOT NULL DEFAULT 0,blocked INTEGER NOT NULL DEFAULT 0)");
  if(oldVersion<5){if(!hasColumn(d,"messages","trash"))d.execSQL("ALTER TABLE messages ADD COLUMN trash INTEGER NOT NULL DEFAULT 0");d.execSQL("UPDATE messages SET trash=1 WHERE type='trash'");}
  if(oldVersion<6)createPerformanceIndexes(d);
  if(oldVersion<7&&!hasColumn(d,"messages","provider_id"))d.execSQL("ALTER TABLE messages ADD COLUMN provider_id INTEGER");
  if(oldVersion<9)rebuildMessagesWithoutThread(d);
  if(oldVersion<10)createMmsAttachmentsTable(d);
  if(oldVersion<11&&!hasColumn(d,"messages","bulk_campaign_id"))d.execSQL("ALTER TABLE messages ADD COLUMN bulk_campaign_id INTEGER");
  if(oldVersion<12)createBulkCampaignsTable(d);
 }
 private void createMmsAttachmentsTable(SQLiteDatabase d){
  d.execSQL("CREATE TABLE IF NOT EXISTS message_attachments(id INTEGER PRIMARY KEY AUTOINCREMENT,message_id INTEGER NOT NULL,local_path TEXT NOT NULL,content_type TEXT NOT NULL,size_bytes INTEGER NOT NULL,transaction_id TEXT NOT NULL,FOREIGN KEY(message_id) REFERENCES messages(id) ON DELETE CASCADE)");
  d.execSQL("CREATE INDEX IF NOT EXISTS idx_message_attachments_message_id ON message_attachments(message_id)");
 }

 private void createBulkCampaignsTable(SQLiteDatabase d){
  d.execSQL("CREATE TABLE IF NOT EXISTS bulk_campaigns(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL,created_at INTEGER NOT NULL,recipient_count INTEGER NOT NULL CHECK(recipient_count>=0))");
  d.execSQL("CREATE INDEX IF NOT EXISTS idx_bulk_campaigns_created_at ON bulk_campaigns(created_at DESC)");
  d.execSQL("CREATE INDEX IF NOT EXISTS idx_messages_bulk_campaign_id ON messages(bulk_campaign_id)");
  d.execSQL("CREATE TABLE IF NOT EXISTS bulk_campaign_recipients(id INTEGER PRIMARY KEY AUTOINCREMENT,campaign_id INTEGER NOT NULL,address TEXT NOT NULL,display_name TEXT NOT NULL DEFAULT '',message_id INTEGER,status TEXT NOT NULL DEFAULT 'pending',FOREIGN KEY(campaign_id) REFERENCES bulk_campaigns(id) ON DELETE CASCADE,UNIQUE(campaign_id,address))");
  d.execSQL("CREATE INDEX IF NOT EXISTS idx_bulk_campaign_recipients_campaign_id ON bulk_campaign_recipients(campaign_id)");
  d.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_bulk_campaign_recipients_message_id ON bulk_campaign_recipients(message_id)");
 }
 public long createBulkCampaign(String name,ArrayList<String[]> recipients){
  if(name==null||name.trim().isEmpty()||recipients==null||recipients.isEmpty())throw new IllegalArgumentException("Invalid bulk campaign");
  SQLiteDatabase d=getWritableDatabase();d.beginTransaction();long campaignId=-1;
  try{
   ContentValues row=new ContentValues();row.put("name",name.trim());row.put("created_at",System.currentTimeMillis());row.put("recipient_count",recipients.size());
   campaignId=d.insertOrThrow("bulk_campaigns",null,row);
   for(String[] recipient:recipients){
    if(recipient==null||recipient.length<2||recipient[0]==null||recipient[0].trim().isEmpty())throw new IllegalArgumentException("Invalid bulk campaign recipient");
    ContentValues member=new ContentValues();member.put("campaign_id",campaignId);member.put("address",recipient[0].trim());member.put("display_name",recipient[1]==null?"":recipient[1].trim());member.put("status","pending");
    d.insertOrThrow("bulk_campaign_recipients",null,member);
   }
   d.setTransactionSuccessful();return campaignId;
  }finally{d.endTransaction();}
 }
 public long addBulkMessage(String address,String body,String status,long campaignId){
  if(campaignId<=0)throw new IllegalArgumentException("Invalid bulk campaign id");
  SQLiteDatabase d=getWritableDatabase();ContentValues row=cv(address,body,"out",status);row.put("bulk_campaign_id",campaignId);
  long messageId=d.insert("messages",null,row);if(messageId<=0)return messageId;
  ContentValues update=new ContentValues();update.put("message_id",messageId);update.put("status",status);
  int changed=d.update("bulk_campaign_recipients",update,"campaign_id=? AND address=?",new String[]{String.valueOf(campaignId),address});
  if(changed==0){ContentValues member=new ContentValues();member.put("campaign_id",campaignId);member.put("address",address);member.put("display_name","");member.put("message_id",messageId);member.put("status",status);d.insertOrThrow("bulk_campaign_recipients",null,member);}
  return messageId;
 }
 public void updateBulkRecipientStatus(long messageId,String status){
  if(messageId<=0||status==null)return;ContentValues values=new ContentValues();values.put("status",status);
  getWritableDatabase().update("bulk_campaign_recipients",values,"message_id=?",new String[]{String.valueOf(messageId)});
 }
 public Cursor bulkCampaignRecipients(long campaignId){
  return getReadableDatabase().rawQuery("SELECT r.id,r.address,r.display_name,r.message_id,r.status FROM bulk_campaign_recipients r WHERE r.campaign_id=? ORDER BY r.id ASC",new String[]{String.valueOf(campaignId)});
 }
 public Cursor listBulkCampaigns(){
  return getReadableDatabase().rawQuery("SELECT c.id,c.name,c.created_at,c.recipient_count,"
    +"SUM(CASE WHEN r.status IN ('تم الإرسال','تم التسليم','لم يتم التسليم','sent','delivered') THEN 1 ELSE 0 END) AS sent_count,"
    +"SUM(CASE WHEN r.status IN ('لم يتم الإرسال','فشل','failed') THEN 1 ELSE 0 END) AS failed_count,"
    +"MAX(0,c.recipient_count-SUM(CASE WHEN r.status IN ('تم الإرسال','تم التسليم','لم يتم التسليم','sent','delivered') THEN 1 ELSE 0 END)-SUM(CASE WHEN r.status IN ('لم يتم الإرسال','فشل','failed') THEN 1 ELSE 0 END)) AS pending_count "
    +"FROM bulk_campaigns c LEFT JOIN bulk_campaign_recipients r ON r.campaign_id=c.id GROUP BY c.id ORDER BY c.created_at DESC,c.id DESC",null);
 }
 private boolean isSupportedMmsAttachmentType(String contentType){
  if(contentType==null)return false;
  switch(contentType.toLowerCase(Locale.ROOT)){
   case "image/jpeg":case "image/png":case "image/gif":case "image/webp":
   case "application/pdf":case "text/plain":case "text/vcard":case "application/octet-stream":
   case "application/zip":case "application/msword":case "application/vnd.openxmlformats-officedocument.wordprocessingml.document":
   case "application/vnd.ms-excel":case "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet":
   case "application/vnd.ms-powerpoint":case "application/vnd.openxmlformats-officedocument.presentationml.presentation":
   case "application/rtf":case "application/json":case "text/csv":case "audio/mpeg":case "audio/mp4":case "video/mp4":return true;
   default:return false;
  }
 }
 public long addMmsAttachment(long messageId,String localPath,String contentType,long sizeBytes,String transactionId){
  if(messageId<=0||localPath==null||localPath.trim().isEmpty()||!isSupportedMmsAttachmentType(contentType)||sizeBytes<=0||transactionId==null||transactionId.trim().isEmpty())throw new IllegalArgumentException("Invalid MMS attachment metadata");
  ContentValues row=new ContentValues();row.put("message_id",messageId);row.put("local_path",localPath);row.put("content_type",contentType.toLowerCase(Locale.ROOT));row.put("size_bytes",sizeBytes);row.put("transaction_id",transactionId);
  return getWritableDatabase().insertOrThrow("message_attachments",null,row);
 }
 public Cursor mmsAttachments(long messageId){
  return getReadableDatabase().query("message_attachments",new String[]{"id","message_id","local_path","content_type","size_bytes","transaction_id"},"message_id=?",new String[]{String.valueOf(messageId)},null,null,"id ASC");
 }
 public void deleteMmsAttachments(long messageId){
  SQLiteDatabase d=getWritableDatabase();ArrayList<String> paths=new ArrayList<>();Cursor c=null;
  try{c=d.query("message_attachments",new String[]{"local_path"},"message_id=?",new String[]{String.valueOf(messageId)},null,null,null);while(c.moveToNext())paths.add(c.getString(0));}
  finally{if(c!=null)c.close();}
  d.beginTransaction();try{d.delete("message_attachments","message_id=?",new String[]{String.valueOf(messageId)});d.setTransactionSuccessful();}finally{d.endTransaction();}
  deleteLocalAttachmentFiles(paths);
 }
 private void deleteLocalAttachmentFiles(ArrayList<String> paths){
  for(String path:paths){if(path==null||path.trim().isEmpty())continue;try{File file=new File(path);if(file.exists()&&!file.delete())Log.w("HashemSMS","Unable to delete local MMS attachment: "+path);}catch(SecurityException e){Log.w("HashemSMS","Unable to delete local MMS attachment: "+path,e);}}
 }
 private void rebuildMessagesWithoutThread(SQLiteDatabase d){
  java.util.ArrayList<String> indexes=new java.util.ArrayList<>();Cursor ix=null;
  try{ix=d.rawQuery("SELECT sql FROM sqlite_master WHERE type='index' AND tbl_name='messages' AND sql IS NOT NULL",null);while(ix.moveToNext()){String sql=ix.getString(0);if(sql!=null&&!sql.toLowerCase(java.util.Locale.ROOT).contains("thread"))indexes.add(sql);}}
  finally{if(ix!=null)ix.close();}
  d.execSQL("DROP TABLE IF EXISTS messages_v9");
  d.execSQL("CREATE TABLE messages_v9(id INTEGER PRIMARY KEY AUTOINCREMENT,address TEXT,body TEXT,type TEXT,status TEXT,ts INTEGER,provider_id INTEGER,read INTEGER DEFAULT 0,star INTEGER DEFAULT 0,trash INTEGER NOT NULL DEFAULT 0)");
  Cursor oldRows=null;
  try{
   oldRows=d.query("messages",null,null,null,null,null,null);int legacyThread=oldRows.getColumnIndex("thread"),oldProvider=oldRows.getColumnIndex("provider_id");
   while(oldRows.moveToNext()){
    ContentValues row=new ContentValues();
    copyColumn(oldRows,row,"id");copyColumn(oldRows,row,"address");copyColumn(oldRows,row,"body");copyColumn(oldRows,row,"type");copyColumn(oldRows,row,"status");copyColumn(oldRows,row,"ts");
    long provider=oldProvider>=0&&!oldRows.isNull(oldProvider)?oldRows.getLong(oldProvider):0L;
    if(provider<=0&&legacyThread>=0&&!oldRows.isNull(legacyThread))provider=oldRows.getLong(legacyThread);
    if(provider>0)row.put("provider_id",provider);else row.putNull("provider_id");
    copyColumnOrDefault(oldRows,row,"read",0);copyColumnOrDefault(oldRows,row,"star",0);copyColumnOrDefault(oldRows,row,"trash",0);
    if(!row.containsKey("type"))row.put("type","out");if(!row.containsKey("status"))row.put("status","");if(!row.containsKey("ts"))row.put("ts",0L);
    d.insertOrThrow("messages_v9",null,row);
   }
  }finally{if(oldRows!=null)oldRows.close();}
  d.execSQL("DROP TABLE messages");d.execSQL("ALTER TABLE messages_v9 RENAME TO messages");
  for(String sql:indexes)try{d.execSQL(sql);}catch(android.database.SQLException e){Log.w("HashemSMS","Skipped legacy messages index incompatible with schema v9: "+sql,e);}
  createPerformanceIndexes(d);d.execSQL("CREATE INDEX IF NOT EXISTS idx_messages_provider_id ON messages(provider_id)");
 }
 private void copyColumn(Cursor c,ContentValues v,String column){int i=c.getColumnIndex(column);if(i<0)return;switch(c.getType(i)){case Cursor.FIELD_TYPE_NULL:v.putNull(column);break;case Cursor.FIELD_TYPE_INTEGER:v.put(column,c.getLong(i));break;case Cursor.FIELD_TYPE_FLOAT:v.put(column,c.getDouble(i));break;case Cursor.FIELD_TYPE_BLOB:v.put(column,c.getBlob(i));break;default:v.put(column,c.getString(i));}}
 private void copyColumnOrDefault(Cursor c,ContentValues v,String column,int fallback){int i=c.getColumnIndex(column);if(i<0)v.put(column,fallback);else copyColumn(c,v,column);}
 public long addMessage(String a,String b,String t,String s){ContentValues v=cv(a,b,t,s);return getWritableDatabase().insert("messages",null,v);}
 public long addMessageAt(String a,String b,String t,String s,long timestamp){ContentValues v=cv(a,b,t,s);v.put("ts",timestamp>0?timestamp:System.currentTimeMillis());return getWritableDatabase().insert("messages",null,v);}
 public void status(long id,String s){ContentValues v=new ContentValues();v.put("status",s);getWritableDatabase().update("messages",v,"id=?",new String[]{""+id});}
 public void updateDeliveryStatus(long id,String deliveryStatus){
  Cursor c=null;boolean sendFailed=false;
  try{c=getReadableDatabase().query("messages",new String[]{"status"},"id=?",new String[]{String.valueOf(id)},null,null,null);if(c.moveToFirst()){String current=c.getString(0);sendFailed=current!=null&&(current.toLowerCase(java.util.Locale.ROOT).contains("fail")||current.contains("لم يتم الإرسال")||current.contains("فشل"));}}
  finally{if(c!=null)c.close();}
  if(!sendFailed){status(id,deliveryStatus);updateBulkRecipientStatus(id,deliveryStatus);}
 }
 public void saveOutgoingToSystemProvider(Context context,long id){
  PROVIDER_SYNC_LOCK.lock();Cursor c=null;
  try{c=getReadableDatabase().query("messages",new String[]{"address","body","ts"},"id=?",new String[]{String.valueOf(id)},null,null,null);if(!c.moveToFirst())return;
   ContentValues v=new ContentValues();v.put(Telephony.Sms.ADDRESS,c.getString(0));v.put(Telephony.Sms.BODY,c.getString(1));v.put(Telephony.Sms.DATE,c.getLong(2));v.put(Telephony.Sms.READ,1);v.put(Telephony.Sms.SEEN,1);v.put(Telephony.Sms.TYPE,Telephony.Sms.MESSAGE_TYPE_OUTBOX);
   Uri uri=context.getContentResolver().insert(Telephony.Sms.CONTENT_URI,v);
   if(uri!=null&&uri.getLastPathSegment()!=null){long providerId=Long.parseLong(uri.getLastPathSegment());ContentValues local=new ContentValues();local.put("provider_id",providerId);getWritableDatabase().update("messages",local,"id=?",new String[]{String.valueOf(id)});}else Log.e("HashemSMS","Android SMS provider returned no URI for outgoing message id="+id);
  }catch(Exception e){Log.e("HashemSMS","Failed to write outgoing SMS to Android SMS provider",e);}
  finally{try{if(c!=null)c.close();}finally{PROVIDER_SYNC_LOCK.unlock();}}
 }
 public void updateSystemProviderStatus(Context context,long id,boolean success){
  PROVIDER_SYNC_LOCK.lock();Cursor c=null;
  try{c=getReadableDatabase().query("messages",new String[]{"provider_id"},"id=?",new String[]{String.valueOf(id)},null,null,null);if(!c.moveToFirst())return;long providerId=c.getLong(0);if(providerId<=0)return;ContentValues v=new ContentValues();v.put(Telephony.Sms.TYPE,success?Telephony.Sms.MESSAGE_TYPE_SENT:Telephony.Sms.MESSAGE_TYPE_FAILED);int changed=context.getContentResolver().update(Uri.parse("content://sms/"+providerId),v,null,null);if(changed==0)Log.e("HashemSMS","No Android SMS provider row updated for provider_id="+providerId);}
  catch(Exception e){Log.e("HashemSMS","Failed to update Android SMS provider status",e);}
  finally{try{if(c!=null)c.close();}finally{PROVIDER_SYNC_LOCK.unlock();}}
 }
 private ContentValues cv(String a,String b,String t,String s){ContentValues v=new ContentValues();v.put("address",a);v.put("body",b);v.put("type",t);v.put("status",s);v.put("ts",System.currentTimeMillis());return v;}
 public Cursor list(String filter){String w=" WHERE trash=0 AND type!='trash'";if("unread".equals(filter))w=" WHERE trash=0 AND type!='trash' AND read=0";else if("failed".equals(filter))w=" WHERE trash=0 AND type!='trash' AND (status='failed' OR status='لم يتم الإرسال' OR status='فشل')";else if("starred".equals(filter))w=" WHERE trash=0 AND type!='trash' AND star=1";else if("trash".equals(filter))w=" WHERE trash=1 OR type='trash'";return getReadableDatabase().rawQuery("SELECT * FROM messages"+w+" ORDER BY ts DESC",null);}
 public Cursor listThreads(String filter){String condition="";if("unread".equals(filter))condition=" AND COALESCE(f.archived,0)=0 AND COALESCE(f.blocked,0)=0 AND EXISTS (SELECT 1 FROM messages u WHERE u.address=m.address AND u.type='in' AND u.read=0 AND u.trash=0)";else if("archived".equals(filter))condition=" AND COALESCE(f.archived,0)=1 AND COALESCE(f.blocked,0)=0";else if("blocked".equals(filter))condition=" AND COALESCE(f.blocked,0)=1";else condition=" AND COALESCE(f.archived,0)=0 AND COALESCE(f.blocked,0)=0";return getReadableDatabase().rawQuery("SELECT m.* FROM messages m INNER JOIN (SELECT address,MAX(ts) latest FROM messages WHERE trash=0 AND type!='trash' GROUP BY address) t ON m.address=t.address AND m.ts=t.latest LEFT JOIN conversation_flags f ON f.address=m.address WHERE m.trash=0 AND m.type!='trash'"+condition+" ORDER BY m.ts DESC",null);}
 public void setConversationFlag(String address,String flag,boolean enabled){if(address==null||address.trim().isEmpty()||(!"archived".equals(flag)&&!"blocked".equals(flag)))return;SQLiteDatabase d=getWritableDatabase();ContentValues row=new ContentValues();row.put("address",address);row.put("archived",0);row.put("blocked",0);d.insertWithOnConflict("conversation_flags",null,row,SQLiteDatabase.CONFLICT_IGNORE);ContentValues v=new ContentValues();v.put(flag,enabled?1:0);d.update("conversation_flags",v,"address=?",new String[]{address});}
 public boolean isConversationBlocked(String address){Cursor c=getReadableDatabase().query("conversation_flags",new String[]{"blocked"},"address=?",new String[]{address},null,null,null);try{return c.moveToFirst()&&c.getInt(0)==1;}finally{c.close();}}
 public boolean hasUnreadIncomingMessages(String address){
   if(address==null||address.trim().isEmpty())return false;
   Cursor c=getReadableDatabase().rawQuery("SELECT 1 FROM messages WHERE address=? AND type='in' AND read=0 AND trash=0 LIMIT 1",new String[]{address});
   try{return c.moveToFirst();}finally{c.close();}
  }
  public java.util.Set<String> unreadIncomingConversationAddresses(){
  java.util.HashSet<String> addresses=new java.util.HashSet<>();
  Cursor c=null;
  try{c=getReadableDatabase().rawQuery("SELECT address FROM messages WHERE type='in' AND read=0 AND trash=0 AND address IS NOT NULL AND address!='' GROUP BY address",null);while(c.moveToNext()){String address=c.getString(0);if(address!=null&&!address.trim().isEmpty())addresses.add(address);}}
  finally{if(c!=null)c.close();}
  return addresses;
 }
 public void markThreadRead(String address){ContentValues v=new ContentValues();v.put("read",1);getWritableDatabase().update("messages",v,"address=? AND type='in'",new String[]{address});}
 public void linkProviderMessage(long localId,long providerId){PROVIDER_SYNC_LOCK.lock();try{ContentValues v=new ContentValues();v.put("provider_id",providerId);getWritableDatabase().update("messages",v,"id=?",new String[]{String.valueOf(localId)});}finally{PROVIDER_SYNC_LOCK.unlock();}}
 public long findRecentIncomingMessage(String address,String body,long timestamp,long windowMillis){if(address==null||body==null)return -1;long center=timestamp>0?timestamp:System.currentTimeMillis();long window=Math.max(1,windowMillis);Cursor c=null;try{c=getReadableDatabase().query("messages",new String[]{"id"},"address=? AND body=? AND type='in' AND (provider_id IS NULL OR provider_id<=0) AND ts BETWEEN ? AND ?",new String[]{address,body,String.valueOf(Math.max(0,center-window)),String.valueOf(center+window)},null,null,"ts DESC","1");return c.moveToFirst()?c.getLong(0):-1;}finally{if(c!=null)c.close();}}
 public boolean importSystemMessageBatch(ArrayList<Object[]> batch){PROVIDER_SYNC_LOCK.lock();SQLiteDatabase localDb=null;boolean started=false,ok=false;try{localDb=getWritableDatabase();localDb.beginTransaction();started=true;for(Object[] row:batch)importSystemMessage((Long)row[0],(String)row[1],(String)row[2],(Integer)row[3],(Long)row[4],(Integer)row[5]);localDb.setTransactionSuccessful();ok=true;}catch(Exception e){Log.e("HashemSMS","SMS import batch failed",e);}finally{try{if(started&&localDb!=null)localDb.endTransaction();}catch(Exception e){Log.e("HashemSMS","Failed to finish SMS batch transaction",e);ok=false;}finally{PROVIDER_SYNC_LOCK.unlock();}}return ok;}
 public void importSystemMessage(long providerId,String address,String body,int providerType,long date,int isRead){PROVIDER_SYNC_LOCK.lock();Cursor existing=null;try{if(address==null||body==null||providerId<=0)return;existing=getReadableDatabase().query("messages",new String[]{"id"},"provider_id=?",new String[]{String.valueOf(providerId)},null,null,null);if(existing.moveToFirst())return;existing.close();existing=null;String type=providerType==Telephony.Sms.MESSAGE_TYPE_INBOX?"in":"out";String status=providerType==Telephony.Sms.MESSAGE_TYPE_SENT?"تم الإرسال":providerType==Telephony.Sms.MESSAGE_TYPE_FAILED?"لم يتم الإرسال":providerType==Telephony.Sms.MESSAGE_TYPE_OUTBOX?"جارٍ الإرسال":"received";ContentValues v=cv(address,body,type,status);v.put("provider_id",providerId);v.put("ts",date);v.put("read",isRead);getWritableDatabase().insert("messages",null,v);}finally{try{if(existing!=null)existing.close();}finally{PROVIDER_SYNC_LOCK.unlock();}}}
 public long createGroup(String name){ContentValues v=new ContentValues();v.put("name",name.trim());return getWritableDatabase().insert("groups",null,v);}
 public void deleteGroup(long gid){SQLiteDatabase d=getWritableDatabase();d.delete("group_members","gid=?",new String[]{String.valueOf(gid)});d.delete("groups","id=?",new String[]{String.valueOf(gid)});}
 public void addGroupMember(long gid,String address,String name){ContentValues v=new ContentValues();v.put("gid",gid);v.put("address",address.trim());v.put("name",name==null?"":name.trim());getWritableDatabase().insertWithOnConflict("group_members",null,v,SQLiteDatabase.CONFLICT_REPLACE);}
 public Cursor listGroups(){return getReadableDatabase().query("groups",new String[]{"id","name"},null,null,null,null,"name COLLATE NOCASE ASC");}
 public Cursor groupMembers(long gid){return getReadableDatabase().query("group_members",new String[]{"address","name"},"gid=?",new String[]{String.valueOf(gid)},null,null,"name COLLATE NOCASE ASC");}
 public Cursor thread(String address){return getReadableDatabase().query("messages",null,"address=? AND trash=0 AND type!='trash'",new String[]{address},null,null,"ts ASC");}
 public Cursor threadPage(String address,int limit){
  int safeLimit=Math.max(1,limit);
  return getReadableDatabase().rawQuery("SELECT * FROM (SELECT * FROM messages WHERE address=? AND trash=0 AND type!='trash' ORDER BY ts DESC,id DESC LIMIT ?) ORDER BY ts ASC,id ASC",new String[]{address,String.valueOf(safeLimit)});
 }
 private String threadSearchPattern(String query){
  String escaped=query==null?"":query.replace("\\","\\\\").replace("%","\\%").replace("_","\\_");
  return "%"+escaped+"%";
 }
 public Cursor threadSearchPage(String address,String query,int limit){
  int safeLimit=Math.max(1,limit);
  return getReadableDatabase().rawQuery("SELECT * FROM (SELECT * FROM messages WHERE address=? AND trash=0 AND type!='trash' AND LOWER(body) LIKE LOWER(?) ESCAPE '\\' ORDER BY ts DESC,id DESC LIMIT ?) ORDER BY ts ASC,id ASC",new String[]{address,threadSearchPattern(query),String.valueOf(safeLimit)});
 }
 public Cursor threadSearchBefore(String address,String query,long beforeTimestamp,long beforeId,int limit){
  int safeLimit=Math.max(1,limit);
  return getReadableDatabase().rawQuery("SELECT * FROM messages WHERE address=? AND trash=0 AND type!='trash' AND LOWER(body) LIKE LOWER(?) ESCAPE '\\' AND (ts<? OR (ts=? AND id<?)) ORDER BY ts DESC,id DESC LIMIT ?",new String[]{address,threadSearchPattern(query),String.valueOf(beforeTimestamp),String.valueOf(beforeTimestamp),String.valueOf(beforeId),String.valueOf(safeLimit)});
 }
 public Cursor threadBefore(String address,long beforeTimestamp,long beforeId,int limit){
  int safeLimit=Math.max(1,limit);
  return getReadableDatabase().rawQuery("SELECT * FROM messages WHERE address=? AND trash=0 AND type!='trash' AND (ts<? OR (ts=? AND id<?)) ORDER BY ts DESC,id DESC LIMIT ?",new String[]{address,String.valueOf(beforeTimestamp),String.valueOf(beforeTimestamp),String.valueOf(beforeId),String.valueOf(safeLimit)});
 }
 public Cursor threadMessageIds(String address){
  return getReadableDatabase().query("messages",new String[]{"id"},"address=? AND trash=0 AND type!='trash'",new String[]{address},null,null,"ts ASC,id ASC");
 }
 public Cursor messagesByIds(ArrayList<Long> ids){if(ids.isEmpty())return getReadableDatabase().rawQuery("SELECT * FROM messages WHERE 1=0",null);StringBuilder q=new StringBuilder();String[] a=new String[ids.size()];for(int i=0;i<ids.size();i++){if(i>0)q.append(',');q.append('?');a[i]=String.valueOf(ids.get(i));}return getReadableDatabase().rawQuery("SELECT * FROM messages WHERE id IN ("+q+") ORDER BY ts ASC",a);}
 public void deleteMessages(ArrayList<Long> ids){if(ids.isEmpty())return;StringBuilder q=new StringBuilder();String[] a=new String[ids.size()];for(int i=0;i<ids.size();i++){if(i>0)q.append(',');q.append('?');a[i]=String.valueOf(ids.get(i));}ContentValues v=new ContentValues();v.put("trash",1);getWritableDatabase().update("messages",v,"id IN ("+q+")",a);}
 public void starMessages(ArrayList<Long> ids){if(ids.isEmpty())return;StringBuilder q=new StringBuilder();String[] a=new String[ids.size()];for(int i=0;i<ids.size();i++){if(i>0)q.append(',');q.append('?');a[i]=String.valueOf(ids.get(i));}getWritableDatabase().execSQL("UPDATE messages SET star=CASE WHEN star=1 THEN 0 ELSE 1 END WHERE id IN ("+q+")",a);}
 public void deleteThread(String address){ContentValues v=new ContentValues();v.put("trash",1);getWritableDatabase().update("messages",v,"address=? AND trash=0",new String[]{address});}
 public Cursor listTrash(){return getReadableDatabase().rawQuery("SELECT * FROM messages WHERE trash=1 OR type='trash' ORDER BY ts DESC",null);}
 public void restoreTrashMessage(long id){SQLiteDatabase d=getWritableDatabase();ContentValues legacy=new ContentValues();legacy.put("trash",0);legacy.put("type","out");d.update("messages",legacy,"id=? AND type='trash'",new String[]{String.valueOf(id)});ContentValues v=new ContentValues();v.put("trash",0);d.update("messages",v,"id=? AND type!='trash'",new String[]{String.valueOf(id)});}
 public void permanentlyDeleteTrashMessage(long id){
  SQLiteDatabase d=getWritableDatabase();ArrayList<String> paths=new ArrayList<>();Cursor c=null;
  try{c=d.query("message_attachments",new String[]{"local_path"},"message_id=?",new String[]{String.valueOf(id)},null,null,null);while(c.moveToNext())paths.add(c.getString(0));}
  finally{if(c!=null)c.close();}
  d.beginTransaction();try{d.delete("message_attachments","message_id=?",new String[]{String.valueOf(id)});d.delete("messages","id=? AND (trash=1 OR type='trash')",new String[]{String.valueOf(id)});d.setTransactionSuccessful();}finally{d.endTransaction();}
  deleteLocalAttachmentFiles(paths);
 }
 public void saveSchedule(long id,String address,String body,long at){ContentValues v=new ContentValues();v.put("id",id);v.put("address",address);v.put("body",body);v.put("at",at);getWritableDatabase().insertWithOnConflict("schedules",null,v,SQLiteDatabase.CONFLICT_REPLACE);}
 public Cursor listSchedules(){return getReadableDatabase().query("schedules",new String[]{"id","address","body","at"},null,null,null,null,"at ASC");}
 public static final class ScheduleRecord{public final long id,at;public final String address,body;public ScheduleRecord(long i,String a,String b,long t){id=i;address=a;body=b;at=t;}}
 public ScheduleRecord claimSchedule(long id){
  SQLiteDatabase d=getWritableDatabase();d.beginTransaction();ScheduleRecord row=null;Cursor c=null;
  try{c=d.query("schedules",new String[]{"id","address","body","at"},"id=?",new String[]{String.valueOf(id)},null,null,null,"1");if(c.moveToFirst())row=new ScheduleRecord(c.getLong(0),c.getString(1),c.getString(2),c.getLong(3));c.close();c=null;
   if(row!=null&&d.delete("schedules","id=?",new String[]{String.valueOf(id)})==1){
    ContentValues status=new ContentValues();status.put("status","جارٍ الإرسال");
    d.update("messages",status,"id=?",new String[]{String.valueOf(id)});
    d.setTransactionSuccessful();
   }else row=null;return row;
  }finally{if(c!=null)c.close();d.endTransaction();}
 }
 public boolean removeSchedule(long id){return getWritableDatabase().delete("schedules","id=?",new String[]{String.valueOf(id)})==1;}
 public boolean cancelSchedule(long id){
  SQLiteDatabase d=getWritableDatabase();d.beginTransaction();
  try{
   int removed=d.delete("schedules","id=?",new String[]{String.valueOf(id)});
   if(removed!=1)return false;
   ContentValues v=new ContentValues();v.put("status","تم إلغاء الجدولة");
   d.update("messages",v,"id=?",new String[]{String.valueOf(id)});
   d.setTransactionSuccessful();return true;
  }finally{d.endTransaction();}
 }
 public java.util.ArrayList<Object[]> notificationMessages(String address,int limit){
  java.util.ArrayList<Object[]> rows=new java.util.ArrayList<>();Cursor c=null;
  try{c=getReadableDatabase().query("messages",new String[]{"body","type","ts","status"},"address=? AND trash=0 AND type!='trash'",new String[]{address},null,null,"ts DESC",String.valueOf(Math.max(1,limit)));while(c.moveToNext())rows.add(new Object[]{c.getString(0),c.getString(1),c.getLong(2),c.getString(3)});}
  finally{if(c!=null)c.close();}java.util.Collections.reverse(rows);return rows;
 }
 public void deleteSchedule(long id){getWritableDatabase().delete("schedules","id=?",new String[]{String.valueOf(id)});}
 public long createForwardRule(String name,String destination){ContentValues v=new ContentValues();v.put("name",name.trim());v.put("destination",destination.trim());return getWritableDatabase().insertWithOnConflict("forward_rules",null,v,SQLiteDatabase.CONFLICT_IGNORE);}
 public Cursor listForwardRules(){return getReadableDatabase().query("forward_rules",new String[]{"id","name","destination"},null,null,null,null,"name COLLATE NOCASE ASC");}
 public void deleteForwardRule(long id){getWritableDatabase().delete("forward_rules","id=?",new String[]{String.valueOf(id)});}
}
