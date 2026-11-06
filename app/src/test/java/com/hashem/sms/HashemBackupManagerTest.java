package com.hashem.sms;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

import androidx.core.content.FileProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public final class HashemBackupManagerTest {
    private Context context;
    private Db database;

    @Before public void setUp() {
        context = RuntimeEnvironment.getApplication();
        context.deleteDatabase("hashem.db");
        deleteRecursively(new File(context.getFilesDir(), "mms"));
        database = new Db(context);
    }

    @After public void tearDown() {
        if (database != null) database.close();
        deleteRecursively(new File(context.getFilesDir(), "mms"));
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

        Uri backupUri = backupUri("round-trip.hsmbackup");
        HashemBackupManager.exportBackup(context, backupUri);
        assertTrue(new File(context.getCacheDir(), "mms/round-trip.hsmbackup").isFile());

        database.addMessage("+15550000000", "رسالة أحدث", "out", "تم الإرسال");
        assertEquals(2, countMessages());

        HashemBackupManager.restoreBackup(context, backupUri);

        assertEquals(1, countMessages());
        try (Cursor cursor = database.mmsAttachments(originalId)) {
            assertTrue(cursor.moveToFirst());
            File restored = new File(cursor.getString(cursor.getColumnIndexOrThrow("local_path")));
            assertTrue(restored.isFile());
            assertEquals(bytes.length, restored.length());
            assertArrayEquals(bytes, readBytes(restored));
        }
    }

    @Test public void corruptArchiveDoesNotChangeCurrentMessages() throws Exception {
        database.addMessage("+15551234567", "رسالة يجب أن تبقى", "out", "تم الإرسال");
        File corrupt = new File(context.getCacheDir(), "mms/corrupt.hsmbackup");
        File parent = corrupt.getParentFile();
        assertTrue(parent.isDirectory() || parent.mkdirs());
        try (FileOutputStream output = new FileOutputStream(corrupt)) {
            output.write("not-a-zip-file".getBytes(StandardCharsets.UTF_8));
        }

        try {
            HashemBackupManager.restoreBackup(context, backupUri("corrupt.hsmbackup"));
            fail("A corrupt backup must be rejected");
        } catch (IOException expected) {
            assertEquals(1, countMessages());
        }
    }

    private Uri backupUri(String name) {
        File directory = new File(context.getCacheDir(), "mms");
        assertTrue(directory.isDirectory() || directory.mkdirs());
        File file = new File(directory, name);
        return FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", file);
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
