package com.hashem.sms;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import android.content.Context;
import android.database.Cursor;
import android.telephony.SmsManager;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowSmsManager;
import org.robolectric.Shadows;

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

    @Test public void sendsMultipartSmsAndPersistsPendingMessage() {
        ShadowSmsManager sms = Shadows.shadowOf(SmsManager.getDefault());
        boolean started = SmsSending.sendSmsInBackground(context, db, ADDRESS, BODY,
                "TEST", 1000, 2000, -1, false);
        assertTrue(started);
        ShadowSmsManager.TextMultipartParams params = sms.getLastSentMultipartTextMessageParams();
        assertNotNull(params);
        assertEquals(ADDRESS, params.getDestinationAddress());
        assertEquals(1, params.getParts().size());
        assertEquals(BODY, params.getParts().get(0));
        try (Cursor c = db.getReadableDatabase().rawQuery(
                "SELECT address,body,status FROM messages ORDER BY id DESC LIMIT 1", null)) {
            assertTrue(c.moveToFirst());
            assertEquals(ADDRESS, c.getString(0));
            assertEquals(BODY, c.getString(1));
            assertEquals("sending", c.getString(2));
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
