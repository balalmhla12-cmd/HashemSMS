package com.hashem.sms;

import static org.junit.Assert.*;

import android.app.AlarmManager;
import android.content.Context;
import android.database.Cursor;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlarmManager;

import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 30)
public class ScheduleLifecycleTest {
    private Context context;
    private Db db;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        context.deleteDatabase("hashem.db");
        NotificationHelper.createChannel(context);
        db = new Db(context);
    }

    private long addScheduled(String address, String body, long at) {
        long id = db.addMessage(address, body, "out", "scheduled");
        assertTrue(id > 0L);
        db.saveSchedule(id, address, body, at);
        return id;
    }

    private String status(long id) {
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

    private boolean hasSchedule(long id) {
        Cursor c = db.getReadableDatabase().query(
                "schedules", new String[]{"id"}, "id=?",
                new String[]{String.valueOf(id)}, null, null, null);
        try {
            return c.moveToFirst();
        } finally {
            c.close();
        }
    }

    @Test
    public void claimScheduleReturnsRecordOnlyOnce() {
        long id = addScheduled("+491234567", "once", System.currentTimeMillis() + 60_000L);
        assertNotNull(db.claimSchedule(id));
        assertNull(db.claimSchedule(id));
        assertFalse(hasSchedule(id));
    }

    @Test
    public void lateWindowIncludesZeroMinutes() {
        assertTrue(SchedulePolicy.isWithinLateWindow(0L));
    }

    @Test
    public void lateWindowIncludesNineMinutesFiftyNineSeconds() {
        assertTrue(SchedulePolicy.isWithinLateWindow(9L * 60_000L + 59_000L));
    }

    @Test
    public void lateWindowIncludesExactTenMinuteBoundary() {
        assertTrue(SchedulePolicy.isWithinLateWindow(10L * 60_000L));
    }

    @Test
    public void lateWindowExcludesElevenMinutes() {
        assertFalse(SchedulePolicy.isWithinLateWindow(11L * 60_000L));
    }

    @Test
    public void cancelScheduleRemovesRecordAndUpdatesMessageStatusAtomically() {
        long id = addScheduled("+491234567", "cancel me", System.currentTimeMillis() + 60_000L);
        assertTrue(db.cancelSchedule(id));
        assertFalse(hasSchedule(id));
        assertEquals("تم إلغاء الجدولة", status(id));
        assertFalse(db.cancelSchedule(id));
    }

    @Test
    public void restoreAfterBootSkipsMissedAndRearmsFutureSchedules() {
        long now = 1_800_000_000_000L;
        long missedId = addScheduled("+491111111", "missed", now - 11L * 60_000L);
        long futureId = addScheduled("+492222222", "future", now + 5L * 60_000L);

        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        ShadowAlarmManager shadow = shadowOf(manager);
        shadow.getScheduledAlarms().clear();

        int restored = ScheduleRestorer.restorePendingSchedules(context, db, now);

        assertEquals(1, restored);
        assertFalse(hasSchedule(missedId));
        assertEquals("فاتت الجدولة", status(missedId));
        assertTrue(hasSchedule(futureId));
        assertEquals(1, shadow.getScheduledAlarms().size());
    }

    @Test
    public void scheduledMessageRemainsScheduledBeforeItsTime() {
        long id = addScheduled("+493333333", "not yet", System.currentTimeMillis() + 60_000L);
        assertEquals("scheduled", status(id));
        Cursor c = db.getReadableDatabase().query(
                "messages", new String[]{"provider_id"}, "id=?",
                new String[]{String.valueOf(id)}, null, null, null);
        try {
            assertTrue(c.moveToFirst());
            assertTrue(c.isNull(0) || c.getLong(0) <= 0L);
        } finally {
            c.close();
        }
        assertTrue(hasSchedule(id));
    }
}
