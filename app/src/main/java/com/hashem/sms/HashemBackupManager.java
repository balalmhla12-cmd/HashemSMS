package com.hashem.sms;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/** Versioned, integrity-checked backup of all app database tables and referenced MMS files. */
final class HashemBackupManager {
    private static final int ARCHIVE_VERSION = 1;
    private static final int DATABASE_VERSION = 12;
    private static final long MAX_ARCHIVE_BYTES = 1024L * 1024L * 1024L;
    private static final long MAX_ENTRY_BYTES = 512L * 1024L * 1024L;
    private static final String ATTACHMENT_PREFIX = "backup://attachments/";
    private static final String[] TABLES = {
            "messages", "groups", "group_members", "schedules", "forward_rules",
            "conversation_flags", "message_attachments", "bulk_campaigns",
            "bulk_campaign_recipients"
    };
    private static final String[] DELETE_ORDER = {
            "bulk_campaign_recipients", "message_attachments", "group_members",
            "schedules", "conversation_flags", "forward_rules", "groups",
            "bulk_campaigns", "messages"
    };
    private static final String[] INSERT_ORDER = {
            "messages", "groups", "bulk_campaigns", "group_members", "schedules",
            "forward_rules", "conversation_flags", "message_attachments",
            "bulk_campaign_recipients"
    };

    private HashemBackupManager() {}

    static void exportBackup(Context context, Uri destination) throws IOException {
        if (context == null || destination == null) throw new IllegalArgumentException("ملف النسخة الاحتياطية غير محدد");
        File work = new File(context.getCacheDir(), "backup-export-" + UUID.randomUUID());
        if (!work.mkdirs()) throw new IOException("تعذّر تجهيز مساحة النسخ الاحتياطي");
        File snapshotFile = new File(work, "database.sqlite");
        File archiveFile = new File(work, "hashemsms.hsmbackup");
        Db helper = new Db(context.getApplicationContext());
        SQLiteDatabase snapshot = null;
        try {
            SQLiteDatabase live = helper.getReadableDatabase();
            String path = snapshotFile.getAbsolutePath().replace("'", "''");
            try {
                live.execSQL("VACUUM INTO '" + path + "'");
            } catch (RuntimeException error) {
                throw new IOException("تعذّر إنشاء لقطة آمنة لقاعدة البيانات على هذا الجهاز؛ لم يتم نسخ ملف قاعدة البيانات بطريقة قد تكون غير مكتملة.", error);
            }
            snapshot = SQLiteDatabase.openDatabase(snapshotFile.getAbsolutePath(), null, SQLiteDatabase.OPEN_READWRITE);
            verifyDatabase(snapshot);
            List<Attachment> attachments = readAttachments(snapshot);
            long total = snapshotFile.length();
            for (Attachment attachment : attachments) {
                File file = new File(attachment.originalPath);
                if (!file.isFile() || file.length() != attachment.sizeBytes || attachment.sizeBytes <= 0) {
                    throw new IOException("يوجد مرفق مفقود أو تغيّر حجمه؛ أُوقف النسخ حتى لا تُنشأ نسخة ناقصة.");
                }
                total += file.length();
                if (total > MAX_ARCHIVE_BYTES) throw new IOException("حجم النسخة الاحتياطية يتجاوز الحد الآمن البالغ 1 جيجابايت.");
                ContentValues update = new ContentValues();
                update.put("local_path", ATTACHMENT_PREFIX + attachment.id);
                if (snapshot.update("message_attachments", update, "id=?", new String[]{String.valueOf(attachment.id)}) != 1) {
                    throw new IOException("تعذّر تجهيز مسار أحد المرفقات للنسخ الاحتياطي.");
                }
                attachment.file = file;
                attachment.sha256 = sha256(file);
            }
            snapshot.close();
            snapshot = null;

            JSONObject manifest;
            try {
                manifest = new JSONObject();
                manifest.put("archiveVersion", ARCHIVE_VERSION);
                manifest.put("databaseVersion", DATABASE_VERSION);
                manifest.put("createdAt", System.currentTimeMillis());
                manifest.put("databaseSha256", sha256(snapshotFile));
                JSONArray attachmentList = new JSONArray();
                for (Attachment attachment : attachments) {
                    JSONObject item = new JSONObject();
                    item.put("id", attachment.id);
                    item.put("sizeBytes", attachment.sizeBytes);
                    item.put("sha256", attachment.sha256);
                    attachmentList.put(item);
                }
                manifest.put("attachments", attachmentList);
            } catch (JSONException error) {
                throw new IOException("تعذّر إعداد بيانات النسخة الاحتياطية.", error);
            }

            try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(archiveFile))) {
                addFile(zip, "database.sqlite", snapshotFile);
                for (Attachment attachment : attachments) {
                    addFile(zip, "attachments/" + attachment.id, attachment.file);
                }
                zip.putNextEntry(new ZipEntry("manifest.json"));
                zip.write(manifest.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                zip.closeEntry();
            }
            try (InputStream input = new FileInputStream(archiveFile);
                 OutputStream output = context.getContentResolver().openOutputStream(destination, "w")) {
                if (output == null) throw new IOException("تعذّر فتح ملف الوجهة للكتابة.");
                copy(input, output, MAX_ARCHIVE_BYTES);
                output.flush();
            }
        } finally {
            if (snapshot != null) snapshot.close();
            helper.close();
            deleteRecursively(work);
        }
    }

    static void restoreBackup(Context context, Uri source) throws IOException {
        if (context == null || source == null) throw new IllegalArgumentException("ملف النسخة الاحتياطية غير محدد");
        File work = new File(context.getCacheDir(), "backup-restore-" + UUID.randomUUID());
        if (!work.mkdirs()) throw new IOException("تعذّر تجهيز مساحة الاستعادة");
        File databaseFile = new File(work, "database.sqlite");
        File attachmentStage = new File(work, "attachments");
        File restoredDirectory = null;
        boolean committed = false;
        Db helper = null;
        SQLiteDatabase backup = null;
        SQLiteDatabase live = null;
        boolean attached = false;
        try {
            extractArchive(context, source, work);
            JSONObject manifest = readManifest(new File(work, "manifest.json"));
            if (manifest.optInt("archiveVersion", -1) != ARCHIVE_VERSION
                    || manifest.optInt("databaseVersion", -1) != DATABASE_VERSION) {
                throw new IOException("إصدار النسخة الاحتياطية غير مدعوم. لم تتغير بيانات التطبيق.");
            }
            if (!databaseFile.isFile() || !sha256(databaseFile).equals(manifest.optString("databaseSha256"))) {
                throw new IOException("فشل التحقق من سلامة قاعدة البيانات داخل النسخة الاحتياطية.");
            }
            backup = SQLiteDatabase.openDatabase(databaseFile.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
            verifyDatabase(backup);

            Map<Long, AttachmentInfo> archiveAttachments = readManifestAttachments(manifest);
            Map<Long, String> restoredPaths = new LinkedHashMap<>();
            List<Attachment> databaseAttachments = readAttachments(backup);
            File[] extractedAttachments = attachmentStage.listFiles();
            if (databaseAttachments.size() != archiveAttachments.size()
                    || extractedAttachments == null || extractedAttachments.length != archiveAttachments.size()) {
                throw new IOException("قائمة المرفقات لا تطابق بيانات قاعدة البيانات.");
            }
            if (!databaseAttachments.isEmpty()) {
                File mmsRoot = new File(context.getFilesDir(), "mms");
                if (!mmsRoot.isDirectory() && !mmsRoot.mkdirs()) {
                    throw new IOException("تعذّر تجهيز مجلد المرفقات للاستعادة.");
                }
                restoredDirectory = new File(mmsRoot, "restored-" + UUID.randomUUID());
                if (!restoredDirectory.mkdirs()) throw new IOException("تعذّر إنشاء مجلد المرفقات المستعادة.");
            }
            for (Attachment attachment : databaseAttachments) {
                String expectedMarker = ATTACHMENT_PREFIX + attachment.id;
                if (!expectedMarker.equals(attachment.originalPath)) {
                    throw new IOException("مسار مرفق غير صالح داخل النسخة الاحتياطية.");
                }
                AttachmentInfo info = archiveAttachments.get(attachment.id);
                File archivedFile = new File(attachmentStage, String.valueOf(attachment.id));
                if (info == null || !archivedFile.isFile()
                        || info.sizeBytes != attachment.sizeBytes
                        || archivedFile.length() != attachment.sizeBytes
                        || !sha256(archivedFile).equals(info.sha256)) {
                    throw new IOException("أحد المرفقات مفقود أو تالف؛ لم تُستبدل الرسائل الحالية.");
                }
                File restoredFile = new File(restoredDirectory, attachment.id + ".bin");
                copyFile(archivedFile, restoredFile);
                restoredPaths.put(attachment.id, restoredFile.getAbsolutePath());
            }
            if (backup != null) {
                backup.close();
                backup = null;
            }

            helper = new Db(context.getApplicationContext());
            live = helper.getWritableDatabase();
            verifySchemaCompatibility(live, databaseFile);
            live.execSQL("ATTACH DATABASE ? AS restore_db", new Object[]{databaseFile.getAbsolutePath()});
            attached = true;
            live.beginTransaction();
            try {
                for (String table : DELETE_ORDER) live.execSQL("DELETE FROM main." + quote(table));
                for (String table : INSERT_ORDER) {
                    List<String> columns = tableColumns(live, "main", table);
                    StringBuilder names = new StringBuilder();
                    for (int i = 0; i < columns.size(); i++) {
                        if (i > 0) names.append(',');
                        names.append(quote(columns.get(i)));
                    }
                    live.execSQL("INSERT INTO main." + quote(table) + " (" + names + ") SELECT "
                            + names + " FROM restore_db." + quote(table));
                }
                for (Map.Entry<Long, String> path : restoredPaths.entrySet()) {
                    ContentValues update = new ContentValues();
                    update.put("local_path", path.getValue());
                    if (live.update("message_attachments", update, "id=?",
                            new String[]{String.valueOf(path.getKey())}) != 1) {
                        throw new IOException("تعذّر ربط أحد المرفقات بعد الاستعادة.");
                    }
                }
                try (Cursor foreignKeys = live.rawQuery("PRAGMA foreign_key_check", null)) {
                    if (foreignKeys.moveToFirst()) throw new IOException("فشل فحص العلاقات بين جداول قاعدة البيانات.");
                }
                live.setTransactionSuccessful();
            } finally {
                live.endTransaction();
            }
            committed = true;
            try {
                live.execSQL("DETACH DATABASE restore_db");
                attached = false;
            } catch (RuntimeException detachError) {
                android.util.Log.w("HashemSMS", "Database was restored; attached snapshot will close with the helper.", detachError);
            }
        } catch (RuntimeException error) {
            throw new IOException("تعذّرت الاستعادة بأمان؛ بقيت قاعدة البيانات الحالية دون استبدال.", error);
        } finally {
            if (backup != null) backup.close();
            if (attached && live != null) {
                try { live.execSQL("DETACH DATABASE restore_db"); } catch (RuntimeException ignored) {}
            }
            if (helper != null) helper.close();
            if (!committed && restoredDirectory != null) deleteRecursively(restoredDirectory);
            deleteRecursively(work);
        }
    }

    private static void verifyDatabase(SQLiteDatabase database) throws IOException {
        if (pragmaInt(database, "user_version") != DATABASE_VERSION) {
            throw new IOException("إصدار قاعدة البيانات داخل النسخة غير متوافق مع هذا الإصدار من التطبيق.");
        }
        try (Cursor integrity = database.rawQuery("PRAGMA integrity_check", null)) {
            if (!integrity.moveToFirst() || !"ok".equalsIgnoreCase(integrity.getString(0))) {
                throw new IOException("قاعدة البيانات داخل النسخة الاحتياطية غير سليمة.");
            }
        }
        try (Cursor foreignKeys = database.rawQuery("PRAGMA foreign_key_check", null)) {
            if (foreignKeys.moveToFirst()) throw new IOException("قاعدة البيانات تحتوي على علاقات غير سليمة.");
        }
        for (String table : TABLES) {
            if (tableColumns(database, "main", table).isEmpty()) {
                throw new IOException("النسخة الاحتياطية لا تحتوي على جدول مطلوب: " + table);
            }
        }
    }

    private static void verifySchemaCompatibility(SQLiteDatabase live, File backupFile) throws IOException {
        SQLiteDatabase backup = SQLiteDatabase.openDatabase(
                backupFile.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
        try {
            if (pragmaInt(live, "user_version") != DATABASE_VERSION) {
                throw new IOException("إصدار قاعدة البيانات الحالية غير متوافق مع الاستعادة.");
            }
            verifyDatabase(backup);
            for (String table : TABLES) {
                if (!tableColumns(live, "main", table).equals(tableColumns(backup, "main", table))) {
                    throw new IOException("مخطط جدول " + table + " غير متوافق؛ لم تُستبدل البيانات.");
                }
            }
        } finally {
            backup.close();
        }
    }

    private static List<Attachment> readAttachments(SQLiteDatabase database) throws IOException {
        ArrayList<Attachment> result = new ArrayList<>();
        try (Cursor cursor = database.query("message_attachments",
                new String[]{"id", "local_path", "size_bytes"}, null, null, null, null, "id ASC")) {
            while (cursor.moveToNext()) {
                result.add(new Attachment(cursor.getLong(0), cursor.getString(1), cursor.getLong(2)));
            }
        } catch (RuntimeException error) {
            throw new IOException("تعذّر قراءة سجل المرفقات.", error);
        }
        return result;
    }

    private static Map<Long, AttachmentInfo> readManifestAttachments(JSONObject manifest) throws IOException {
        Map<Long, AttachmentInfo> result = new HashMap<>();
        JSONArray list = manifest.optJSONArray("attachments");
        if (list == null) throw new IOException("قائمة المرفقات غير موجودة في النسخة الاحتياطية.");
        for (int i = 0; i < list.length(); i++) {
            JSONObject item = list.optJSONObject(i);
            if (item == null) throw new IOException("بيانات مرفق غير صالحة.");
            long id = item.optLong("id", -1);
            long size = item.optLong("sizeBytes", -1);
            String hash = item.optString("sha256", "");
            if (id <= 0 || size <= 0 || !hash.matches("[0-9a-f]{64}")
                    || result.put(id, new AttachmentInfo(size, hash)) != null) {
                throw new IOException("بيانات مرفق مكررة أو غير صالحة.");
            }
        }
        return result;
    }

    private static void extractArchive(Context context, Uri source, File work) throws IOException {
        File attachmentDirectory = new File(work, "attachments");
        if (!attachmentDirectory.mkdirs()) throw new IOException("تعذّر تجهيز ملفات الاستعادة.");
        Set<String> names = new HashSet<>();
        long total = 0;
        InputStream rawInput = context.getContentResolver().openInputStream(source);
        if (rawInput == null) throw new IOException("تعذّر فتح ملف النسخة الاحتياطية.");
        try (InputStream input = rawInput; ZipInputStream zip = new ZipInputStream(input)) {
            ZipEntry entry;
            byte[] buffer = new byte[8192];
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                if (entry.isDirectory() || name == null || !names.add(name)) {
                    throw new IOException("ملف النسخة يحتوي على مسارات أو إدخالات مكررة غير مسموح بها.");
                }
                File target;
                if ("database.sqlite".equals(name)) {
                    target = new File(work, name);
                } else if ("manifest.json".equals(name)) {
                    target = new File(work, name);
                } else if (name.matches("attachments/[1-9][0-9]*")) {
                    target = new File(attachmentDirectory, name.substring("attachments/".length()));
                } else {
                    throw new IOException("ملف النسخة يحتوي على إدخال غير معروف.");
                }
                long written = 0;
                try (OutputStream output = new FileOutputStream(target)) {
                    int count;
                    while ((count = zip.read(buffer)) != -1) {
                        written += count;
                        total += count;
                        if (written > MAX_ENTRY_BYTES || total > MAX_ARCHIVE_BYTES) {
                            throw new IOException("حجم ملف النسخة يتجاوز الحد الآمن.");
                        }
                        output.write(buffer, 0, count);
                    }
                }
                zip.closeEntry();
            }
        }
        if (!names.contains("database.sqlite") || !names.contains("manifest.json")) {
            throw new IOException("ملف النسخة الاحتياطية غير مكتمل.");
        }
    }

    private static JSONObject readManifest(File file) throws IOException {
        if (!file.isFile() || file.length() > 2L * 1024L * 1024L) {
            throw new IOException("ملف تعريف النسخة الاحتياطية غير صالح.");
        }
        try (FileInputStream input = new FileInputStream(file);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            copy(input, output, 2L * 1024L * 1024L);
            return new JSONObject(output.toString("UTF-8"));
        } catch (JSONException error) {
            throw new IOException("تعذّر قراءة بيانات النسخة الاحتياطية.", error);
        }
    }

    private static int pragmaInt(SQLiteDatabase database, String pragma) throws IOException {
        try (Cursor cursor = database.rawQuery("PRAGMA " + pragma, null)) {
            if (!cursor.moveToFirst()) throw new IOException("تعذّر قراءة إصدار قاعدة البيانات.");
            return cursor.getInt(0);
        }
    }

    private static List<String> tableColumns(SQLiteDatabase database, String schema, String table)
            throws IOException {
        ArrayList<String> columns = new ArrayList<>();
        try (Cursor cursor = database.rawQuery("PRAGMA " + schema + ".table_info(" + quote(table) + ")", null)) {
            while (cursor.moveToNext()) columns.add(cursor.getString(cursor.getColumnIndexOrThrow("name")));
        } catch (RuntimeException error) {
            throw new IOException("تعذّر التحقق من بنية جدول " + table + ".", error);
        }
        return columns;
    }

    private static String quote(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    private static void addFile(ZipOutputStream zip, String name, File file) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        try (InputStream input = new FileInputStream(file)) {
            copy(input, zip, MAX_ENTRY_BYTES);
        }
        zip.closeEntry();
    }

    private static void copyFile(File source, File destination) throws IOException {
        try (InputStream input = new FileInputStream(source);
             OutputStream output = new FileOutputStream(destination)) {
            copy(input, output, MAX_ENTRY_BYTES);
            output.flush();
        }
    }

    private static void copy(InputStream input, OutputStream output, long maxBytes) throws IOException {
        byte[] buffer = new byte[64 * 1024];
        long total = 0;
        int count;
        while ((count = input.read(buffer)) != -1) {
            total += count;
            if (total > maxBytes) throw new IOException("حجم البيانات يتجاوز الحد الآمن.");
            output.write(buffer, 0, count);
        }
    }

    private static String sha256(File file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = new FileInputStream(file)) {
                byte[] buffer = new byte[64 * 1024];
                int count;
                while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
            }
            StringBuilder hex = new StringBuilder();
            for (byte value : digest.digest()) hex.append(String.format(Locale.ROOT, "%02x", value & 0xff));
            return hex.toString();
        } catch (NoSuchAlgorithmException error) {
            throw new IOException("خوارزمية التحقق من سلامة الملف غير متاحة.", error);
        }
    }

    private static void deleteRecursively(File file) {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) for (File child : children) deleteRecursively(child);
        }
        if (!file.delete()) android.util.Log.w("HashemSMS", "Unable to delete temporary backup file: " + file);
    }

    private static final class Attachment {
        final long id;
        final String originalPath;
        final long sizeBytes;
        File file;
        String sha256;
        Attachment(long id, String path, long size) {
            this.id = id;
            originalPath = path;
            sizeBytes = size;
        }
    }

    private static final class AttachmentInfo {
        final long sizeBytes;
        final String sha256;
        AttachmentInfo(long size, String hash) {
            sizeBytes = size;
            sha256 = hash;
        }
    }
}
