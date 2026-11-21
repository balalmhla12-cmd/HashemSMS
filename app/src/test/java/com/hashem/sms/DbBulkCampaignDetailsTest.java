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

import java.util.ArrayList;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class DbBulkCampaignDetailsTest {
    private Db db;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication();
        context.deleteDatabase("hashem.db");
        db = new Db(context);
    }

    @Test
    public void detailsReturnEveryRecipientAndTheirCurrentStatusInCampaignOrder() {
        ArrayList<String[]> recipients = new ArrayList<>();
        recipients.add(new String[]{"+201000000001", "أحمد"});
        recipients.add(new String[]{"+201000000002", "سارة"});
        recipients.add(new String[]{"+201000000003", "محمد"});
        long campaign = db.createBulkCampaign("حملة الاختبار", recipients);

        long sentMessage = db.addBulkMessage("+201000000001", "رسالة", "تم الإرسال", campaign);
        long failedMessage = db.addBulkMessage("+201000000002", "رسالة", "فشل", campaign);
        assertTrue(sentMessage > 0);
        assertTrue(failedMessage > 0);

        try (Cursor details = db.bulkCampaignRecipients(campaign)) {
            assertEquals(3, details.getCount());
            assertTrue(details.moveToFirst());
            assertEquals("+201000000001", details.getString(details.getColumnIndexOrThrow("address")));
            assertEquals("أحمد", details.getString(details.getColumnIndexOrThrow("display_name")));
            assertEquals("تم الإرسال", details.getString(details.getColumnIndexOrThrow("status")));
            assertEquals(sentMessage, details.getLong(details.getColumnIndexOrThrow("message_id")));

            assertTrue(details.moveToNext());
            assertEquals("+201000000002", details.getString(details.getColumnIndexOrThrow("address")));
            assertEquals("فشل", details.getString(details.getColumnIndexOrThrow("status")));
            assertEquals(failedMessage, details.getLong(details.getColumnIndexOrThrow("message_id")));

            assertTrue(details.moveToNext());
            assertEquals("+201000000003", details.getString(details.getColumnIndexOrThrow("address")));
            assertEquals("pending", details.getString(details.getColumnIndexOrThrow("status")));
            assertTrue(details.isNull(details.getColumnIndexOrThrow("message_id")));
            assertFalse(details.moveToNext());
        }
    }

    @Test
    public void detailsAreScopedToRequestedCampaignOnly() {
        ArrayList<String[]> first = new ArrayList<>();
        first.add(new String[]{"+201000000011", "الأول"});
        ArrayList<String[]> second = new ArrayList<>();
        second.add(new String[]{"+201000000022", "الثاني"});
        long firstId = db.createBulkCampaign("الأولى", first);
        long secondId = db.createBulkCampaign("الثانية", second);

        try (Cursor details = db.bulkCampaignRecipients(firstId)) {
            assertEquals(1, details.getCount());
            assertTrue(details.moveToFirst());
            assertEquals("+201000000011", details.getString(details.getColumnIndexOrThrow("address")));
        }
        try (Cursor details = db.bulkCampaignRecipients(secondId)) {
            assertEquals(1, details.getCount());
            assertTrue(details.moveToFirst());
            assertEquals("+201000000022", details.getString(details.getColumnIndexOrThrow("address")));
        }
    }
}
