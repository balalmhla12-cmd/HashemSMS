package com.hashem.sms;
import android.content.*;import android.database.sqlite.*;import android.database.*;import android.net.Uri;import android.provider.Telephony;import android.util.Log;import java.util.*;import java.util.concurrent.locks.ReentrantLock;
public class Db extends SQLiteOpenHelper{
 public static final ReentrantLock PROVIDER_SYNC_LOCK = new ReentrantLock();
 public Db(Context c){super(c,"hashem.db",null,9);} 
 public void onCreate(SQLiteDatabase d){
  d.execSQL("CREATE TABLE messages(id INTEGER PRIMARY KEY AUTOINCREMENT,address TEXT,body TEXT,type TEXT,status TEXT,ts INTEGER,provider_id INTEGER,read INTEGER DEFAULT 0,star INTEGER DEFAULT 0,trash INTEGER NOT NULL DEFAULT 0)");
  d.execSQL("CREATE TABLE groups(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT UNIQUE)");
  d.execSQL("CREATE TABLE group_members(gid INTEGER,address TEXT,name TEXT,PRIMARY KEY(gid,address))");
  d.execSQL("CREATE TABLE schedules(id INTEGER PRIMARY KEY AUTOINCREMENT,address TEXT,body TEXT,at INTEGER)");
  d.execSQL("CREATE TABLE forward_rules(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL UNIQUE,destination TEXT NOT NULL)");
  d.execSQL("CREATE TABLE conversation_flags(address TEXT PRIMARY KEY,archived INTEGER NOT NULL DEFAULT 0,blocked INTEGER NOT NULL DEFAULT 0)");
  createPerformanceIndexes(d);d.execSQL("CREATE INDEX IF NOT EXISTS idx_messages_provider_id ON messages(provider_id)");
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
 public void status(long id,String s){ContentValues v=new ContentValues();v.put("status",s);getWritableDatabase().update("messages",v,"id=?",new String[]{""+id});}
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
 public Cursor list(String filter){String w=" WHERE trash=0 AND type!='trash'";if("unread".equals(filter))w=" WHERE trash=0 AND type!='trash' AND read=0";else if("failed".equals(filter))w=" WHERE trash=0 AND type!='trash' AND (status='failed' OR status='لم يتم الإرسال')";else if("starred".equals(filter))w=" WHERE trash=0 AND type!='trash' AND star=1";else if("trash".equals(filter))w=" WHERE trash=1 OR type='trash'";return getReadableDatabase().rawQuery("SELECT * FROM messages"+w+" ORDER BY ts DESC",null);}
 public Cursor listThreads(String filter){String condition="";if("unread".equals(filter))condition=" AND COALESCE(f.archived,0)=0 AND COALESCE(f.blocked,0)=0 AND EXISTS (SELECT 1 FROM messages u WHERE u.address=m.address AND u.type='in' AND u.read=0 AND u.trash=0)";else if("archived".equals(filter))condition=" AND COALESCE(f.archived,0)=1 AND COALESCE(f.blocked,0)=0";else if("blocked".equals(filter))condition=" AND COALESCE(f.blocked,0)=1";else condition=" AND COALESCE(f.archived,0)=0 AND COALESCE(f.blocked,0)=0";return getReadableDatabase().rawQuery("SELECT m.* FROM messages m INNER JOIN (SELECT address,MAX(ts) latest FROM messages WHERE trash=0 AND type!='trash' GROUP BY address) t ON m.address=t.address AND m.ts=t.latest LEFT JOIN conversation_flags f ON f.address=m.address WHERE m.trash=0 AND m.type!='trash'"+condition+" ORDER BY m.ts DESC",null);}
 public void setConversationFlag(String address,String flag,boolean enabled){if(address==null||address.trim().isEmpty()||(!"archived".equals(flag)&&!"blocked".equals(flag)))return;SQLiteDatabase d=getWritableDatabase();ContentValues row=new ContentValues();row.put("address",address);row.put("archived",0);row.put("blocked",0);d.insertWithOnConflict("conversation_flags",null,row,SQLiteDatabase.CONFLICT_IGNORE);ContentValues v=new ContentValues();v.put(flag,enabled?1:0);d.update("conversation_flags",v,"address=?",new String[]{address});}
 public boolean isConversationBlocked(String address){Cursor c=getReadableDatabase().query("conversation_flags",new String[]{"blocked"},"address=?",new String[]{address},null,null,null);try{return c.moveToFirst()&&c.getInt(0)==1;}finally{c.close();}}
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
 public Cursor messagesByIds(ArrayList<Long> ids){if(ids.isEmpty())return getReadableDatabase().rawQuery("SELECT * FROM messages WHERE 1=0",null);StringBuilder q=new StringBuilder();String[] a=new String[ids.size()];for(int i=0;i<ids.size();i++){if(i>0)q.append(',');q.append('?');a[i]=String.valueOf(ids.get(i));}return getReadableDatabase().rawQuery("SELECT * FROM messages WHERE id IN ("+q+") ORDER BY ts ASC",a);}
 public void deleteMessages(ArrayList<Long> ids){if(ids.isEmpty())return;StringBuilder q=new StringBuilder();String[] a=new String[ids.size()];for(int i=0;i<ids.size();i++){if(i>0)q.append(',');q.append('?');a[i]=String.valueOf(ids.get(i));}ContentValues v=new ContentValues();v.put("trash",1);getWritableDatabase().update("messages",v,"id IN ("+q+")",a);}
 public void starMessages(ArrayList<Long> ids){if(ids.isEmpty())return;StringBuilder q=new StringBuilder();String[] a=new String[ids.size()];for(int i=0;i<ids.size();i++){if(i>0)q.append(',');q.append('?');a[i]=String.valueOf(ids.get(i));}getWritableDatabase().execSQL("UPDATE messages SET star=CASE WHEN star=1 THEN 0 ELSE 1 END WHERE id IN ("+q+")",a);}
 public void deleteThread(String address){ContentValues v=new ContentValues();v.put("trash",1);getWritableDatabase().update("messages",v,"address=? AND trash=0",new String[]{address});}
 public Cursor listTrash(){return getReadableDatabase().rawQuery("SELECT * FROM messages WHERE trash=1 OR type='trash' ORDER BY ts DESC",null);}
 public void restoreTrashMessage(long id){SQLiteDatabase d=getWritableDatabase();ContentValues legacy=new ContentValues();legacy.put("trash",0);legacy.put("type","out");d.update("messages",legacy,"id=? AND type='trash'",new String[]{String.valueOf(id)});ContentValues v=new ContentValues();v.put("trash",0);d.update("messages",v,"id=? AND type!='trash'",new String[]{String.valueOf(id)});}
 public void permanentlyDeleteTrashMessage(long id){getWritableDatabase().delete("messages","id=? AND (trash=1 OR type='trash')",new String[]{String.valueOf(id)});}
 public void saveSchedule(long id,String address,String body,long at){ContentValues v=new ContentValues();v.put("id",id);v.put("address",address);v.put("body",body);v.put("at",at);getWritableDatabase().insertWithOnConflict("schedules",null,v,SQLiteDatabase.CONFLICT_REPLACE);}
 public Cursor listSchedules(){return getReadableDatabase().query("schedules",new String[]{"id","address","body","at"},null,null,null,null,"at ASC");}
 public static final class ScheduleRecord{public final long id,at;public final String address,body;public ScheduleRecord(long i,String a,String b,long t){id=i;address=a;body=b;at=t;}}
 public ScheduleRecord claimSchedule(long id){
  SQLiteDatabase d=getWritableDatabase();d.beginTransaction();ScheduleRecord row=null;Cursor c=null;
  try{c=d.query("schedules",new String[]{"id","address","body","at"},"id=?",new String[]{String.valueOf(id)},null,null,null,"1");if(c.moveToFirst())row=new ScheduleRecord(c.getLong(0),c.getString(1),c.getString(2),c.getLong(3));c.close();c=null;
   if(row!=null&&d.delete("schedules","id=?",new String[]{String.valueOf(id)})==1)d.setTransactionSuccessful();else row=null;return row;
  }finally{if(c!=null)c.close();d.endTransaction();}
 }
 public boolean removeSchedule(long id){return getWritableDatabase().delete("schedules","id=?",new String[]{String.valueOf(id)})==1;}
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
