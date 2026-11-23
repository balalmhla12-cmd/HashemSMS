package com.hashem.sms;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 30)
public final class SentReceiverProviderStatusTest {
    private static final class RecordingDb extends Db {
        String status;
        long statusId;
        long providerId;
        Boolean providerSuccess;

        RecordingDb(Context context) {
            super(context);
        }

        @Override
        public void status(long id, String value) {
            statusId = id;
            status = value;
        }

        @Override
        public void updateSystemProviderStatus(Context context, long id, boolean success) {
            providerId = id;
            providerSuccess = success;
        }
    }

    @Test
    public void successfulSendUpdatesExistingProviderRowInsteadOfInsertingAgain() {
        Context context = RuntimeEnvironment.getApplication();
        RecordingDb db = new RecordingDb(context);

        SentReceiver.applyFinalResult(context, db, 41L, true);

        assertEquals(41L, db.statusId);
        assertEquals("تم الإرسال", db.status);
        assertEquals(41L, db.providerId);
        assertTrue(db.providerSuccess);
    }

    @Test
    public void asynchronousSendFailureMarksLocalAndExistingProviderRowsFailed() {
        Context context = RuntimeEnvironment.getApplication();
        RecordingDb db = new RecordingDb(context);

        SentReceiver.applyFinalResult(context, db, 42L, false);

        assertEquals(42L, db.statusId);
        assertEquals("لم يتم الإرسال", db.status);
        assertEquals(42L, db.providerId);
        assertFalse(db.providerSuccess);
    }

    @Test
    public void nullAndZeroProviderIdsAreIgnoredWithARealSqliteDatabase() {
        Context context = RuntimeEnvironment.getApplication();
        context.deleteDatabase("hashem.db");
        Db db = new Db(context);

        long nullProviderMessage = db.addMessage("+967700001", "null provider", "out", "sending");
        db.updateSystemProviderStatus(context, nullProviderMessage, true);
        try (android.database.Cursor row = db.getReadableDatabase().query(
                "messages", new String[]{"provider_id"}, "id=?",
                new String[]{String.valueOf(nullProviderMessage)}, null, null, null)) {
            assertTrue(row.moveToFirst());
            assertTrue(row.isNull(0));
        }

        long zeroProviderMessage = db.addMessage("+967700002", "zero provider", "out", "sending");
        db.getWritableDatabase().execSQL(
                "UPDATE messages SET provider_id=0 WHERE id=?",
                new Object[]{zeroProviderMessage});
        db.updateSystemProviderStatus(context, zeroProviderMessage, false);
        try (android.database.Cursor row = db.getReadableDatabase().query(
                "messages", new String[]{"provider_id"}, "id=?",
                new String[]{String.valueOf(zeroProviderMessage)}, null, null, null)) {
            assertTrue(row.moveToFirst());
            assertEquals(0L, row.getLong(0));
        }

        try (android.database.Cursor rows = db.getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM messages", null)) {
            assertTrue(rows.moveToFirst());
            assertEquals(2, rows.getInt(0));
        }
        db.close();
    }
}
