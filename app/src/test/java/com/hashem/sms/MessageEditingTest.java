package com.hashem.sms;

import static org.junit.Assert.*;

import android.content.Context;
import android.database.Cursor;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 30)
public class MessageEditingTest {
    private Context context;
    private Db db;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        context.deleteDatabase("hashem.db");
        db = new Db(context);
        db.getWritableDatabase();
    }

    private String messageBody(long id) {
        Cursor c = db.getReadableDatabase().query(
                "messages", new String[]{"body"}, "id=?",
                new String[]{String.valueOf(id)}, null, null, null);
        try {
            assertTrue(c.moveToFirst());
            return c.getString(0);
        } finally {
            c.close();
        }
    }

    private String messageStatus(long id) {
        Cursor c = db.getReadableDatabase().query(
                "messages", new String[]{"status"}, "id=?",
                new String[]{String.valueOf(id)}, null, null, null);
        try {
            assertTrue(c.moveToFirst());
            return c.getString(0);
        } finally {
            c.close();
        }
    }

    @Test
    public void failedMessageBodyUpdatesInSameRecord() {
        long id = db.addMessageAt("+491234567", "old body", "out", "failed", 1234L);
        assertTrue(id > 0L);

        assertTrue(db.updateFailedMessageBody(id, "new body"));

        assertEquals("new body", messageBody(id));
        assertEquals("failed", messageStatus(id));
        Cursor count = db.getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM messages WHERE address=?", new String[]{"+491234567"});
        try {
            assertTrue(count.moveToFirst());
            assertEquals(1, count.getInt(0));
        } finally {
            count.close();
        }
    }

    @Test
    public void failedMessageEditRejectsSentOrScheduledRows() {
        long sentId = db.addMessage("+491111111", "sent", "out", "تم الإرسال");
        long scheduledId = db.addMessage("+492222222", "scheduled", "out", "scheduled");
        db.saveSchedule(scheduledId, "+492222222", "scheduled", System.currentTimeMillis() + 120_000L);

        assertFalse(db.updateFailedMessageBody(sentId, "changed"));
        assertFalse(db.updateFailedMessageBody(scheduledId, "changed"));
        assertEquals("sent", messageBody(sentId));
        assertEquals("scheduled", messageBody(scheduledId));
    }

    @Test
    public void scheduledMessageEditUpdatesMessageAndScheduleTogether() {
        long at = System.currentTimeMillis() + 120_000L;
        long newAt = System.currentTimeMillis() + 300_000L;
        long id = db.addMessage("+493333333", "old scheduled body", "out", "scheduled");
        db.saveSchedule(id, "+493333333", "old scheduled body", at);

        assertTrue(db.updateScheduledMessage(id, "new scheduled body", newAt));

        assertEquals("new scheduled body", messageBody(id));
        assertEquals("scheduled", messageStatus(id));
        Db.ScheduleRecord schedule = db.schedule(id);
        assertNotNull(schedule);
        assertEquals("new scheduled body", schedule.body);
        assertEquals(newAt, schedule.at);
    }

    @Test
    public void scheduledMessageEditRejectsPastTimeAndPreservesRows() {
        long at = System.currentTimeMillis() + 120_000L;
        long id = db.addMessage("+494444444", "keep this", "out", "scheduled");
        db.saveSchedule(id, "+494444444", "keep this", at);

        assertFalse(db.updateScheduledMessage(id, "must not save", System.currentTimeMillis() - 1_000L));

        assertEquals("keep this", messageBody(id));
        Db.ScheduleRecord schedule = db.schedule(id);
        assertNotNull(schedule);
        assertEquals("keep this", schedule.body);
        assertEquals(at, schedule.at);
    }
}
