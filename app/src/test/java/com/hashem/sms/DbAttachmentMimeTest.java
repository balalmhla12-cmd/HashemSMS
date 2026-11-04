package com.hashem.sms;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;
import android.database.Cursor;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.File;
import java.io.FileOutputStream;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class DbAttachmentMimeTest {
    private Context context;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        context.deleteDatabase("hashem.db");
    }

    @Test
    public void storesGifAndDocumentMimeTypesWithoutChangingSchema() throws Exception {
        Db db = new Db(context);
        File gif = File.createTempFile("hashem-", ".gif", context.getCacheDir());
        File pdf = File.createTempFile("hashem-", ".pdf", context.getCacheDir());
        try {
            try (FileOutputStream out = new FileOutputStream(gif)) {
                out.write(new byte[]{0x47, 0x49, 0x46});
            }
            try (FileOutputStream out = new FileOutputStream(pdf)) {
                out.write(new byte[]{0x25, 0x50, 0x44, 0x46});
            }

            long gifMessage = db.addMessage("+123", "GIF", "out", "sent");
            long pdfMessage = db.addMessage("+456", "PDF", "out", "sent");
            assertTrue(gifMessage > 0);
            assertTrue(pdfMessage > 0);

            db.addMmsAttachment(gifMessage, gif.getAbsolutePath(), "IMAGE/GIF",
                    gif.length(), "gif-transaction");
            db.addMmsAttachment(pdfMessage, pdf.getAbsolutePath(), "application/pdf",
                    pdf.length(), "pdf-transaction");

            try (Cursor gifRows = db.mmsAttachments(gifMessage)) {
                assertTrue(gifRows.moveToFirst());
                assertEquals("image/gif", gifRows.getString(
                        gifRows.getColumnIndexOrThrow("content_type")));
            }
            try (Cursor pdfRows = db.mmsAttachments(pdfMessage)) {
                assertTrue(pdfRows.moveToFirst());
                assertEquals("application/pdf", pdfRows.getString(
                        pdfRows.getColumnIndexOrThrow("content_type")));
            }
            assertEquals(12, db.getWritableDatabase().getVersion());
        } finally {
            gif.delete();
            pdf.delete();
            db.close();
        }
    }

    @Test
    public void rejectsAttachmentTypesNotAcceptedByMmsComposer() throws Exception {
        Db db = new Db(context);
        File file = File.createTempFile("hashem-", ".bin", context.getCacheDir());
        try {
            try (FileOutputStream out = new FileOutputStream(file)) {
                out.write(new byte[]{0x01});
            }
            long messageId = db.addMessage("+123", "unsupported", "out", "failed");
            try {
                db.addMmsAttachment(messageId, file.getAbsolutePath(), "video/x-msvideo",
                        file.length(), "unsupported-transaction");
                fail("Unsupported content types must be rejected");
            } catch (IllegalArgumentException expected) {
                assertEquals("Invalid MMS attachment metadata", expected.getMessage());
            }
        } finally {
            file.delete();
            db.close();
        }
    }
}
