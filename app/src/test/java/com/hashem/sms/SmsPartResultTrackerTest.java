package com.hashem.sms;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
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
public final class SmsPartResultTrackerTest {
    private Context context;
    private Db db;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        context.deleteDatabase("hashem.db");
        context.getSharedPreferences("hashem_sms_parts", Context.MODE_PRIVATE)
                .edit().clear().commit();
        db = new Db(context);
        db.getWritableDatabase();
    }

    @After
    public void tearDown() {
        if (db != null) db.close();
    }

    private long addPendingMessage(String body) {
        return db.addMessage("+15551230003", body, "out", "sending");
    }

    private String status(long id) {
        try (Cursor cursor = db.getReadableDatabase().query(
                "messages", new String[]{"status"}, "id=?",
                new String[]{String.valueOf(id)}, null, null, null)) {
            assertTrue(cursor.moveToFirst());
            return cursor.getString(0);
        }
    }

    @Test
    public void sentPartsUseMessageIdAndCountEachPartIndexOnlyOnce() {
        long firstMessageId = addPendingMessage("first message");
        long secondMessageId = addPendingMessage("second message");
        SentReceiver receiver = new SentReceiver();

        receiver.process(context, firstMessageId, 3, 0, Activity.RESULT_OK,
                "SHARED_TAG", "+15551230003");
        assertEquals("sending", status(firstMessageId));

        // A duplicate callback for part zero must not advance the first message.
        receiver.process(context, firstMessageId, 3, 0, Activity.RESULT_OK,
                "SHARED_TAG", "+15551230003");
        assertEquals("sending", status(firstMessageId));

        // The same tag on a different message must maintain an independent part set.
        receiver.process(context, secondMessageId, 3, 0, Activity.RESULT_OK,
                "SHARED_TAG", "+15551230003");
        receiver.process(context, firstMessageId, 3, 2, Activity.RESULT_OK,
                "SHARED_TAG", "+15551230003");
        assertEquals("sending", status(firstMessageId));

        // Completion is impossible until the remaining distinct part arrives.
        receiver.process(context, firstMessageId, 3, 1, Activity.RESULT_OK,
                "SHARED_TAG", "+15551230003");
        assertEquals("تم الإرسال", status(firstMessageId));
        assertEquals("sending", status(secondMessageId));

        receiver.process(context, secondMessageId, 3, 2, Activity.RESULT_OK,
                "SHARED_TAG", "+15551230003");
        assertEquals("sending", status(secondMessageId));
        receiver.process(context, secondMessageId, 3, 1, Activity.RESULT_OK,
                "SHARED_TAG", "+15551230003");
        assertEquals("تم الإرسال", status(secondMessageId));

        // A late duplicate after completion remains ignored.
        receiver.process(context, firstMessageId, 3, 1, Activity.RESULT_OK,
                "SHARED_TAG", "+15551230003");
        assertEquals("تم الإرسال", status(firstMessageId));
    }

    @Test
    public void deliveryPartsDoNotMarkDeliveredUntilEveryDistinctPartArrives() {
        long messageId = addPendingMessage("delivery multipart");
        db.status(messageId, "تم الإرسال");
        DeliveredReceiver receiver = new DeliveredReceiver();

        receiver.process(context, messageId, 3, 0, Activity.RESULT_OK);
        receiver.process(context, messageId, 3, 0, Activity.RESULT_OK);
        receiver.process(context, messageId, 3, 2, Activity.RESULT_OK);
        assertEquals("تم الإرسال", status(messageId));

        receiver.process(context, messageId, 3, 1, Activity.RESULT_OK);
        assertEquals("تم التسليم", status(messageId));

        receiver.process(context, messageId, 3, 1, Activity.RESULT_OK);
        assertEquals("تم التسليم", status(messageId));
    }
}
