package com.hashem.sms;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;
import android.database.Cursor;
import android.util.Base64;

import org.json.JSONException;
import org.json.JSONObject;


import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.File;
import java.io.FileOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public final class HashemBackupManagerTest {
    private Context context;
    private Db database;

    @Before public void setUp() {
        context = RuntimeEnvironment.getApplication();
        context.deleteDatabase("hashem.db");
        deleteRecursively(new File(context.getFilesDir(), "mms"));
        deleteRecursively(new File(context.getFilesDir(), "safety-backups"));
        database = new Db(context);
    }

    @After public void tearDown() {
        if (database != null) database.close();
        deleteRecursively(new File(context.getFilesDir(), "mms"));
        deleteRecursively(new File(context.getFilesDir(), "safety-backups"));
        deleteRecursively(new File(context.getCacheDir(), "mms"));
        context.deleteDatabase("hashem.db");
    }

    @Test public void exportAndRestorePreservesMessagesAndAttachmentBytes() throws Exception {
        long originalId = database.addMessage("+15551234567", "صورة", "out", "تم الإرسال");
        File attachment = new File(context.getFilesDir(), "mms/backup-test.gif");
        File parent = attachment.getParentFile();
        assertTrue(parent.isDirectory() || parent.mkdirs());
        byte[] bytes = "GIF89a-hashem-backup-test".getBytes(StandardCharsets.UTF_8);
        try (FileOutputStream output = new FileOutputStream(attachment)) {
            output.write(bytes);
        }
        database.addMmsAttachment(originalId, attachment.getAbsolutePath(),
                "image/gif", bytes.length, "backup-test-transaction");

        ByteArrayOutputStream backupBytes = new ByteArrayOutputStream();
        HashemBackupManager.exportBackup(context, backupBytes);
        assertTrue(backupBytes.size() > 0);

        database.addMessage("+15550000000", "رسالة أحدث", "out", "تم الإرسال");
        assertEquals(2, countMessages());

        HashemBackupManager.restoreBackup(context, new ByteArrayInputStream(backupBytes.toByteArray()));

        assertEquals(1, countMessages());
        try (Cursor cursor = database.mmsAttachments(originalId)) {
            assertTrue(cursor.moveToFirst());
            File restored = new File(cursor.getString(cursor.getColumnIndexOrThrow("local_path")));
            assertTrue(restored.isFile());
            assertEquals(bytes.length, restored.length());
            assertArrayEquals(bytes, readBytes(restored));
        }
    }

    @Test public void restoreCreatesRecoverableSafetyBackupBeforeReplacingCurrentData() throws Exception {
        database.addMessage("+15551234567", "البيانات القديمة", "out", "تم الإرسال");
        ByteArrayOutputStream incomingBackup = new ByteArrayOutputStream();
        HashemBackupManager.exportBackup(context, incomingBackup);

        database.addMessage("+15550000000", "البيانات الحالية", "out", "تم الإرسال");
        assertEquals(2, countMessages());

        HashemBackupManager.restoreBackup(context,
                new ByteArrayInputStream(incomingBackup.toByteArray()));
        assertEquals(1, countMessages());

        File safetyDirectory = new File(context.getFilesDir(), "safety-backups");
        File[] safetyBackups = safetyDirectory.listFiles(
                (directory, name) -> name.endsWith(".hsmbackup"));
        assertTrue(safetyBackups != null && safetyBackups.length == 1);
        assertTrue(safetyBackups[0].isFile() && safetyBackups[0].length() > 0L);

        database.close();
        database = null;
        try (java.io.FileInputStream input = new java.io.FileInputStream(safetyBackups[0])) {
            HashemBackupManager.restoreBackup(context, input);
        }
        database = new Db(context);
        assertEquals(2, countMessages());
    }

    @Test public void corruptArchiveDoesNotChangeCurrentMessages() throws Exception {
        database.addMessage("+15551234567", "رسالة يجب أن تبقى", "out", "تم الإرسال");
        byte[] corrupt = "not-a-zip-file".getBytes(StandardCharsets.UTF_8);
        try {
            HashemBackupManager.restoreBackup(context, new ByteArrayInputStream(corrupt));
            fail("A corrupt backup must be rejected");
        } catch (IOException expected) {
            assertEquals(1, countMessages());
        }
    }

    @Test public void futureDatabaseVersionArchiveIsRejectedWithoutChangingTablesOrFiles()
            throws Exception {
        long originalId = database.addMessage(
                "+15551234567", "بيانات يجب ألا تتغير", "out", "تم الإرسال");
        database.addMessage("+15550000000", "رسالة ثانية", "in", "مستلمة");

        File attachment = new File(context.getFilesDir(), "mms/future-version-test.bin");
        File parent = attachment.getParentFile();
        assertTrue(parent.isDirectory() || parent.mkdirs());
        byte[] attachmentBytes = "attachment-must-remain-byte-for-byte-identical"
                .getBytes(StandardCharsets.UTF_8);
        try (FileOutputStream output = new FileOutputStream(attachment)) {
            output.write(attachmentBytes);
        }
        database.addMmsAttachment(originalId, attachment.getAbsolutePath(),
                "image/gif", attachmentBytes.length, "future-version-transaction");

        ByteArrayOutputStream validBackup = new ByteArrayOutputStream();
        HashemBackupManager.exportBackup(context, validBackup);
        byte[] futureBackup = withFutureDatabaseVersion(validBackup.toByteArray(), 13);

        Map<String, String> tablesBefore = snapshotDatabaseTables();
        Map<String, String> filesBefore = snapshotFiles(new File(context.getFilesDir()));

        try {
            HashemBackupManager.restoreBackup(context, new ByteArrayInputStream(futureBackup));
            fail("A backup from a future database version must be rejected");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("إصدار النسخة الاحتياطية غير مدعوم"));
        }

        assertEquals(2, countMessages());
        assertEquals("Database table schemas and rows changed after rejecting a future backup",
                tablesBefore, snapshotDatabaseTables());
        assertEquals("Files or attachment bytes changed after rejecting a future backup",
                filesBefore, snapshotFiles(new File(context.getFilesDir())));
        assertArrayEquals(attachmentBytes, readBytes(attachment));
    }

    private Map<String, String> snapshotDatabaseTables() throws IOException {
        String[] tables = {
                "messages", "groups", "group_members", "schedules", "forward_rules",
                "conversation_flags", "message_attachments", "bulk_campaigns",
                "bulk_campaign_recipients"
        };
        Map<String, String> snapshot = new LinkedHashMap<>();
        for (String table : tables) {
            StringBuilder value = new StringBuilder();
            try (Cursor schema = database.getReadableDatabase().rawQuery(
                    "PRAGMA table_info(\"" + table + "\")", null)) {
                value.append("schema:");
                appendCursorRows(schema, value, false);
            }
            try (Cursor rows = database.getReadableDatabase().rawQuery(
                    "SELECT * FROM \"" + table + "\"", null)) {
                value.append("|rows:");
                appendCursorRows(rows, value, true);
            }
            snapshot.put(table, value.toString());
        }
        return snapshot;
    }

    private static void appendCursorRows(Cursor cursor, StringBuilder output, boolean sortRows) {
        List<String> rows = new ArrayList<>();
        while (cursor.moveToNext()) {
            StringBuilder row = new StringBuilder();
            for (int column = 0; column < cursor.getColumnCount(); column++) {
                int type = cursor.getType(column);
                String field;
                switch (type) {
                    case Cursor.FIELD_TYPE_NULL:
                        field = "N";
                        break;
                    case Cursor.FIELD_TYPE_INTEGER:
                        field = "I" + cursor.getLong(column);
                        break;
                    case Cursor.FIELD_TYPE_FLOAT:
                        field = "F" + cursor.getDouble(column);
                        break;
                    case Cursor.FIELD_TYPE_BLOB:
                        field = "B" + Base64.encodeToString(
                                cursor.getBlob(column), Base64.NO_WRAP);
                        break;
                    default:
                        field = "S" + cursor.getString(column);
                        break;
                }
                row.append(field.length()).append(':').append(field).append(';');
            }
            rows.add(row.toString());
        }
        if (sortRows) Collections.sort(rows);
        output.append(cursor.getColumnCount()).append('[');
        for (String row : rows) output.append(row.length()).append(':').append(row);
        output.append(']');
    }

    private static Map<String, String> snapshotFiles(File root) throws IOException {
        Map<String, String> snapshot = new TreeMap<>();
        snapshotFilesRecursively(root, root, snapshot);
        return snapshot;
    }

    private static void snapshotFilesRecursively(
            File root, File current, Map<String, String> snapshot) throws IOException {
        File[] children = current.listFiles();
        if (children == null) return;
        for (File child : children) {
            if (child.isDirectory()) {
                snapshotFilesRecursively(root, child, snapshot);
            } else if (child.isFile()) {
                String relativePath = child.getAbsolutePath().substring(root.getAbsolutePath().length());
                snapshot.put(relativePath, Base64.encodeToString(readBytes(child), Base64.NO_WRAP));
            }
        }
    }

    private static byte[] withFutureDatabaseVersion(byte[] archive, int futureVersion)
            throws IOException {
        ByteArrayOutputStream rewritten = new ByteArrayOutputStream();
        boolean manifestUpdated = false;
        try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(archive));
             ZipOutputStream output = new ZipOutputStream(rewritten)) {
            ZipEntry entry;
            byte[] buffer = new byte[8192];
            while ((entry = input.getNextEntry()) != null) {
                ByteArrayOutputStream contents = new ByteArrayOutputStream();
                int count;
                while ((count = input.read(buffer)) != -1) contents.write(buffer, 0, count);
                byte[] bytes = contents.toByteArray();
                if ("manifest.json".equals(entry.getName())) {
                    try {
                        JSONObject manifest = new JSONObject(
                                new String(bytes, StandardCharsets.UTF_8));
                        manifest.put("databaseVersion", futureVersion);
                        bytes = manifest.toString().getBytes(StandardCharsets.UTF_8);
                        manifestUpdated = true;
                    } catch (JSONException error) {
                        throw new IOException("Unable to update backup manifest for test", error);
                    }
                }
                output.putNextEntry(new ZipEntry(entry.getName()));
                output.write(bytes);
                output.closeEntry();
                input.closeEntry();
            }
        }
        if (!manifestUpdated) throw new IOException("Backup manifest was not found");
        return rewritten.toByteArray();
    }

    private int countMessages() {
        try (Cursor cursor = database.getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM messages", null)) {
            assertTrue(cursor.moveToFirst());
            return cursor.getInt(0);
        }
    }

    private static byte[] readBytes(File file) throws IOException {
        try (java.io.FileInputStream input = new java.io.FileInputStream(file);
             java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            return output.toByteArray();
        }
    }

    private static void deleteRecursively(File file) {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) for (File child : children) deleteRecursively(child);
        }
        file.delete();
    }
}


