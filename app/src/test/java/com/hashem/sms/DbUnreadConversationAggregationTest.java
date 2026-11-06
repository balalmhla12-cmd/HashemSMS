package com.hashem.sms;

import static org.junit.Assert.*;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.HashSet;
import java.util.Set;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class DbUnreadConversationAggregationTest {
    private Context context;
    private Db db;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        context.deleteDatabase("hashem.db");
        db = new Db(context);
    }

    @Test
    public void groupedUnreadAddressesMatchPerConversationLookup() {
        db.addMessageAt("+10000000001", "unread one", "in", "received", 100L);
        db.addMessageAt("+10000000001", "unread two", "in", "received", 200L);
        db.addMessageAt("+10000000002", "read", "in", "received", 300L);
        db.addMessageAt("+10000000003", "outgoing", "out", "sent", 400L);
        db.addMessageAt("+10000000004", "trashed", "in", "received", 500L);

        ContentValues read = new ContentValues();
        read.put("read", 1);
        db.getWritableDatabase().update("messages", read, "address=?", new String[]{"+10000000002"});
        ContentValues trash = new ContentValues();
        trash.put("trash", 1);
        db.getWritableDatabase().update("messages", trash, "address=?", new String[]{"+10000000004"});

        Set<String> addresses = new HashSet<>();
        Cursor all = db.getReadableDatabase().rawQuery("SELECT DISTINCT address FROM messages", null);
        try {
            while (all.moveToNext()) {
                String address = all.getString(0);
                if (db.hasUnreadIncomingMessages(address)) addresses.add(address);
            }
        } finally {
            all.close();
        }

        assertEquals(addresses, db.unreadIncomingConversationAddresses());
        assertEquals(1, db.unreadIncomingConversationAddresses().size());
        assertTrue(db.unreadIncomingConversationAddresses().contains("+10000000001"));
    }
}
