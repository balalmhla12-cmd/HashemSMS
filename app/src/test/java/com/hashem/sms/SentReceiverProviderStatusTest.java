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
}
