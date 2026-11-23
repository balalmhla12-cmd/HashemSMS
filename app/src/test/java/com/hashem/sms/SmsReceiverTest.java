package com.hashem.sms;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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
public class SmsReceiverTest {
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

    @Test public void persistsIncomingSmsAndLinksSystemProviderId() {
        long now = System.currentTimeMillis();
        assertTrue(SmsReceiver.persistIncomingMessage(db, "+15551230003",
                "رسالة واردة للاختبار", now, 321));
        try (Cursor c = db.getReadableDatabase().rawQuery(
                "SELECT address,body,type,status,provider_id FROM messages", null)) {
            assertTrue(c.moveToFirst());
            assertEquals("+15551230003", c.getString(0));
            assertEquals("رسالة واردة للاختبار", c.getString(1));
            assertEquals("in", c.getString(2));
            assertEquals("received", c.getString(3));
            assertEquals(321L, c.getLong(4));
        }
    }

    @Test public void skipsDuplicateIncomingSmsWhenProviderInsertWasUnavailable() {
        long now = System.currentTimeMillis();
        assertTrue(SmsReceiver.persistIncomingMessage(db, "+15551230004",
                "رسالة مكررة للاختبار", now, -1));
        assertFalse(SmsReceiver.persistIncomingMessage(db, "+15551230004",
                "رسالة مكررة للاختبار", now, -1));
        try (Cursor c = db.getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM messages WHERE address=? AND body=?",
                new String[]{"+15551230004", "رسالة مكررة للاختبار"})) {
            assertTrue(c.moveToFirst());
            assertEquals(1, c.getInt(0));
        }
    }

    @Test public void rejectsMalformedIncomingMessageWithoutWritingIt() {
        assertFalse(SmsReceiver.persistIncomingMessage(db, " ", "نص", System.currentTimeMillis(), -1));
        assertFalse(SmsReceiver.persistIncomingMessage(db, "+15551230005", "", System.currentTimeMillis(), -1));
        try (Cursor c = db.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM messages", null)) {
            assertTrue(c.moveToFirst());
            assertEquals(0, c.getInt(0));
        }
    }
}
