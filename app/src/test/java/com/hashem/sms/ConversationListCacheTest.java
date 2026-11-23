package com.hashem.sms;

import static org.junit.Assert.*;

import android.content.Context;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Collections;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 30)
public class ConversationListCacheTest {
    private Context context;
    private Db db;

    @Before
    public void setUp() {
        ConversationListCache.invalidateAll();
        context = RuntimeEnvironment.getApplication();
        context.deleteDatabase("hashem.db");
        db = new Db(context);
        db.getWritableDatabase();
    }

    private ArrayList<MainActivity.ThreadItem> rows(String address) {
        return new ArrayList<>(Collections.singletonList(
                new MainActivity.ThreadItem(address, "preview", "sent", 1L,
                        "contact", null, null, false)));
    }

    private void seedEveryFilter() {
        ConversationListCache.put("all", rows("+491111111"));
        ConversationListCache.put("unread", rows("+492222222"));
        ConversationListCache.put("archived", rows("+493333333"));
        ConversationListCache.put("blocked", rows("+494444444"));
        ConversationListCache.put("unknown", rows("+495555555"));
        assertEquals(5, ConversationListCache.size());
    }

    private void assertAllFiltersInvalidated() {
        assertNull(ConversationListCache.get("all"));
        assertNull(ConversationListCache.get("unread"));
        assertNull(ConversationListCache.get("archived"));
        assertNull(ConversationListCache.get("blocked"));
        assertNull(ConversationListCache.get("unknown"));
        assertEquals(0, ConversationListCache.size());
    }

    @Test
    public void keepsIndependentFilterSnapshotsAndReturnsDefensiveCopies() {
        ConversationListCache.put("all", rows("+491111111"));
        ConversationListCache.put("unread", rows("+492222222"));

        ArrayList<MainActivity.ThreadItem> all = ConversationListCache.get("all");
        assertNotNull(all);
        all.clear();

        assertEquals(1, ConversationListCache.get("all").size());
        assertEquals("+492222222", ConversationListCache.get("unread").get(0).address);
        assertEquals(2, ConversationListCache.size());
    }

    @Test
    public void conversationMutationsInvalidateEveryFilterSnapshot() {
        long incoming = db.addMessage("+491111111", "incoming", "in", "received");
        seedEveryFilter();
        db.markThreadRead("+491111111");
        assertAllFiltersInvalidated();

        seedEveryFilter();
        db.status(incoming, "تم التسليم");
        assertAllFiltersInvalidated();

        seedEveryFilter();
        db.setConversationFlag("+491111111", "archived", true);
        assertAllFiltersInvalidated();

        seedEveryFilter();
        db.deleteMessages(new ArrayList<>(Collections.singletonList(incoming)));
        assertAllFiltersInvalidated();

        seedEveryFilter();
        db.restoreTrashMessage(incoming);
        assertAllFiltersInvalidated();

        seedEveryFilter();
        db.deleteThread("+491111111");
        assertAllFiltersInvalidated();

        seedEveryFilter();
        db.importSystemMessage(987654L, "+492222222", "system import",
                android.provider.Telephony.Sms.MESSAGE_TYPE_INBOX,
                System.currentTimeMillis(), 0);
        assertAllFiltersInvalidated();

        seedEveryFilter();
        ArrayList<String[]> recipients = new ArrayList<>();
        recipients.add(new String[]{"+493333333", "Contact"});
        long campaignId = db.createBulkCampaign("cache invalidation", recipients);
        db.addBulkMessage("+493333333", "bulk", "sending", campaignId);
        assertAllFiltersInvalidated();
    }
}
