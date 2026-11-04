package com.hashem.sms;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import android.content.Context;
import android.database.Cursor;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 30)
public class SmsSendingTest {
    private static final String ADDRESS = "+15551230002";
    private static final String BODY = "اختبار إرسال SMS";
    private Context context;
    private Db db;

    @Before public void setUp() {
        context = RuntimeEnvironment.getApplication();
        context.deleteDatabase("hashem.db");
        db = new Db(context);
        db.getWritableDatabase();
    }

    @After public void tearDown() {
        if (db != null) db.close();
    }

    private static final class RecordingGateway implements SmsSending.SmsGateway {
        String recipient;
        java.util.ArrayList<String> parts;
        boolean fail;
        @Override public java.util.ArrayList<String> divideMessage(String body) {
            return new java.util.ArrayList<>(java.util.Collections.singletonList(body));
        }
        @Override public void sendMultipartTextMessage(String recipient,
                java.util.ArrayList<String> parts,
                java.util.ArrayList<android.app.PendingIntent> sent,
                java.util.ArrayList<android.app.PendingIntent> delivered) {
            if (fail) throw new IllegalStateException("simulated SMS service failure");
            this.recipient = recipient;
            this.parts = parts;
            assertEquals(1, sent.size());
            assertEquals(1, delivered.size());
        }
    }

    @Test public void sendsMultipartSmsAndPersistsPendingMessage() {
        RecordingGateway gateway = new RecordingGateway();
        boolean started = SmsSending.sendSmsInBackground(context, db, ADDRESS, BODY,
                "TEST", 1000, 2000, -1, false, 0L, 0L, gateway);
        assertTrue(started);
        assertEquals(ADDRESS, gateway.recipient);
        assertEquals(1, gateway.parts.size());
        assertEquals(BODY, gateway.parts.get(0));
        try (Cursor c = db.getReadableDatabase().rawQuery(
                "SELECT address,body,status FROM messages ORDER BY id DESC LIMIT 1", null)) {
            assertTrue(c.moveToFirst());
            assertEquals(ADDRESS, c.getString(0));
            assertEquals(BODY, c.getString(1));
            assertEquals("sending", c.getString(2));
        }
    }

    @Test public void smsServiceFailurePreservesOutgoingRecordAsFailed() {
        RecordingGateway gateway = new RecordingGateway();
        gateway.fail = true;
        boolean started = SmsSending.sendSmsInBackground(context, db, ADDRESS, BODY,
                "TEST", 5000, 6000, -1, false, 0L, 0L, gateway);
        assertFalse(started);
        try (Cursor c = db.getReadableDatabase().rawQuery(
                "SELECT address,body,status FROM messages ORDER BY id DESC LIMIT 1", null)) {
            assertTrue(c.moveToFirst());
            assertEquals(ADDRESS, c.getString(0));
            assertEquals(BODY, c.getString(1));
            assertEquals("لم يتم الإرسال", c.getString(2));
        }
    }

    @Test public void invalidRecipientFailsWithoutLosingTheOutgoingRecord() {
        boolean started = SmsSending.sendSmsInBackground(context, db, "", BODY,
                "TEST", 3000, 4000, -1, false);
        assertFalse(started);
        try (Cursor c = db.getReadableDatabase().rawQuery(
                "SELECT address,body,status FROM messages ORDER BY id DESC LIMIT 1", null)) {
            assertTrue(c.moveToFirst());
            assertEquals("", c.getString(0));
            assertEquals(BODY, c.getString(1));
            assertEquals("لم يتم الإرسال", c.getString(2));
        }
    }
}
