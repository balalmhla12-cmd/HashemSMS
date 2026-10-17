package com.hashem.sms;

import static org.junit.Assert.*;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class DbMigrationTest {
    private Context context;

    private static final String[] V9_COLUMNS = {
            "id", "address", "body", "type", "status", "ts",
            "provider_id", "read", "star", "trash"
    };

    private static final String[] REQUIRED_INDEXES = {
            "idx_messages_address_ts",
            "idx_messages_trash_type_ts",
            "idx_messages_type_read_trash_address",
            "idx_messages_star_ts",
            "idx_messages_provider_id"
    };

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        context.deleteDatabase("hashem.db");
    }

    @Test
    public void upgradesVersion6PreservingMessagesSchedulesAndIndexes() {
        createVersion6Database();
        Db db = new Db(context);
        SQLiteDatabase upgraded = db.getWritableDatabase();

        assertEquals(9, upgraded.getVersion());
        assertEquals(5, countRows(upgraded, "messages"));
        assertArrayEquals(new long[]{101, 102, 103, 104, 105}, messageIds(upgraded));
        assertEquals(987L, providerId(upgraded, 101));
        assertEquals(654L, providerId(upgraded, 102));
        assertEquals(-1L, providerId(upgraded, 103)); // legacy thread=0 stays NULL
        assertEquals(-1L, providerId(upgraded, 104));
        assertEquals(321L, providerId(upgraded, 105));

        assertArrayEquals(V9_COLUMNS, columns(upgraded, "messages"));
        assertFalse(Arrays.asList(columns(upgraded, "messages")).contains("thread"));

        assertMessage(upgraded, 101, "incoming body", "in", "received", 0, 0, 0);
        assertMessage(upgraded, 102, "outgoing body", "out", "sent", 0, 0, 0);
        assertMessage(upgraded, 103, "zero thread body", "in", "unread", 1, 0, 0);
        assertMessage(upgraded, 104, "trash body", "trash", "deleted", 0, 0, 1);
        assertMessage(upgraded, 105, "starred body", "out", "delivered", 0, 1, 0);

        assertArrayEquals(new String[]{"id", "address", "body", "at"}, columns(upgraded, "schedules"));
        try (Cursor schedules = upgraded.rawQuery(
                "SELECT id,address,body,at FROM schedules WHERE id=102", null)) {
            assertTrue(schedules.moveToFirst());
            assertEquals("schedule-address", schedules.getString(1));
            assertEquals("scheduled body", schedules.getString(2));
            assertEquals(123456789L, schedules.getLong(3));
            assertEquals(1, schedules.getCount());
        }
        assertRequiredIndexes(upgraded);
        db.close();
    }

    @Test
    public void freshInstallCreatesVersion9ColumnsAndFiveStandardIndexes() {
        Db db = new Db(context);
        SQLiteDatabase fresh = db.getWritableDatabase();

        assertEquals(9, fresh.getVersion());
        assertArrayEquals(V9_COLUMNS, columns(fresh, "messages"));
        assertFalse(Arrays.asList(columns(fresh, "messages")).contains("thread"));
        assertRequiredIndexes(fresh);
        db.close();
    }

    @Test
    public void reopeningVersion9DoesNotRepeatMigrationOrDuplicateRows() {
        createVersion6Database();
        Db first = new Db(context);
        SQLiteDatabase migrated = first.getWritableDatabase();
        assertEquals(5, countRows(migrated, "messages"));
        first.close();

        Db second = new Db(context);
        SQLiteDatabase reopened = second.getWritableDatabase();
        assertEquals(9, reopened.getVersion());
        assertEquals(5, countRows(reopened, "messages"));
        assertArrayEquals(new long[]{101, 102, 103, 104, 105}, messageIds(reopened));
        assertEquals(987L, providerId(reopened, 101));
        assertEquals(654L, providerId(reopened, 102));
        assertEquals(-1L, providerId(reopened, 103));
        assertEquals(1, countRows(reopened, "schedules"));
        assertRequiredIndexes(reopened);
        second.close();
    }

    private void createVersion6Database() {
        SQLiteDatabase old = context.openOrCreateDatabase("hashem.db", Context.MODE_PRIVATE, null);
        old.execSQL("CREATE TABLE messages(id INTEGER PRIMARY KEY AUTOINCREMENT,address TEXT,body TEXT,type TEXT,status TEXT,ts INTEGER,thread INTEGER,read INTEGER DEFAULT 0,star INTEGER DEFAULT 0,trash INTEGER NOT NULL DEFAULT 0)");
        old.execSQL("CREATE TABLE groups(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT UNIQUE)");
        old.execSQL("CREATE TABLE group_members(gid INTEGER,address TEXT,name TEXT,PRIMARY KEY(gid,address))");
        old.execSQL("CREATE TABLE schedules(id INTEGER PRIMARY KEY AUTOINCREMENT,address TEXT,body TEXT,at INTEGER)");
        old.execSQL("CREATE TABLE forward_rules(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL UNIQUE,destination TEXT NOT NULL)");
        old.execSQL("CREATE TABLE conversation_flags(address TEXT PRIMARY KEY,archived INTEGER NOT NULL DEFAULT 0,blocked INTEGER NOT NULL DEFAULT 0)");
        old.execSQL("CREATE INDEX idx_messages_address_ts ON messages(address,ts DESC)");
        old.execSQL("CREATE INDEX idx_messages_trash_type_ts ON messages(trash,type,ts DESC)");
        old.execSQL("CREATE INDEX idx_messages_type_read_trash_address ON messages(type,read,trash,address)");
        old.execSQL("CREATE INDEX idx_messages_star_ts ON messages(star,ts DESC)");
        old.execSQL("CREATE INDEX idx_messages_thread ON messages(thread)");
        insertLegacyMessage(old, 101, "+1001", "incoming body", "in", "received", 987, 0, 0, 0);
        insertLegacyMessage(old, 102, "+1002", "outgoing body", "out", "sent", 654, 0, 0, 0);
        insertLegacyMessage(old, 103, "+1003", "zero thread body", "in", "unread", 0, 1, 0, 0);
        insertLegacyMessage(old, 104, "+1004", "trash body", "trash", "deleted", 0, 0, 0, 1);
        insertLegacyMessage(old, 105, "+1005", "starred body", "out", "delivered", 321, 0, 1, 0);
        old.execSQL("INSERT INTO schedules(id,address,body,at) VALUES(102,'schedule-address','scheduled body',123456789)");
        old.setVersion(6);
        old.close();
    }

    private static void insertLegacyMessage(SQLiteDatabase db, long id, String address,
            String body, String type, String status, long thread, int read, int star, int trash) {
        db.execSQL("INSERT INTO messages(id,address,body,type,status,ts,thread,read,star,trash) VALUES(?,?,?,?,?,?,?,?,?,?)",
                new Object[]{id, address, body, type, status, id * 1000, thread, read, star, trash});
    }

    private static void assertMessage(SQLiteDatabase db, long id, String body, String type,
            String status, int read, int star, int trash) {
        try (Cursor c = db.rawQuery("SELECT body,type,status,read,star,trash FROM messages WHERE id=?",
                new String[]{String.valueOf(id)})) {
            assertTrue("missing message id " + id, c.moveToFirst());
            assertEquals(body, c.getString(0));
            assertEquals(type, c.getString(1));
            assertEquals(status, c.getString(2));
            assertEquals(read, c.getInt(3));
            assertEquals(star, c.getInt(4));
            assertEquals(trash, c.getInt(5));
            assertEquals(1, c.getCount());
        }
    }

    private static long[] messageIds(SQLiteDatabase db) {
        long[] ids = new long[countRows(db, "messages")];
        try (Cursor c = db.rawQuery("SELECT id FROM messages ORDER BY id", null)) {
            int i = 0;
            while (c.moveToNext()) ids[i++] = c.getLong(0);
        }
        return ids;
    }

    private static long providerId(SQLiteDatabase db, long id) {
        try (Cursor c = db.rawQuery("SELECT provider_id FROM messages WHERE id=?",
                new String[]{String.valueOf(id)})) {
            assertTrue(c.moveToFirst());
            return c.isNull(0) ? -1L : c.getLong(0);
        }
    }

    private static int countRows(SQLiteDatabase db, String table) {
        try (Cursor c = db.rawQuery("SELECT COUNT(*) FROM " + table, null)) {
            assertTrue(c.moveToFirst());
            return c.getInt(0);
        }
    }

    private static String[] columns(SQLiteDatabase db, String table) {
        java.util.ArrayList<String> names = new java.util.ArrayList<>();
        try (Cursor c = db.rawQuery("PRAGMA table_info(" + table + ")", null)) {
            while (c.moveToNext()) names.add(c.getString(c.getColumnIndexOrThrow("name")));
        }
        return names.toArray(new String[0]);
    }

    private static void assertRequiredIndexes(SQLiteDatabase db) {
        Set<String> actual = new HashSet<>();
        try (Cursor c = db.rawQuery(
                "SELECT name FROM sqlite_master WHERE type='index' AND tbl_name='messages'", null)) {
            while (c.moveToNext()) actual.add(c.getString(0));
        }
        for (String index : REQUIRED_INDEXES) assertTrue("missing index " + index, actual.contains(index));
    }
    @Test
    public void conversationDiffDetectsChangedContentAndDifferentIds() {
        ConversationActivity.MessageDiffCallback diff = new ConversationActivity.MessageDiffCallback();
        ConversationActivity.ConversationMessage before =
                new ConversationActivity.ConversationMessage(10L, "hello", "in", "received", 0, null);
        ConversationActivity.ConversationMessage changed =
                new ConversationActivity.ConversationMessage(10L, "updated", "in", "received", 0, null);
        ConversationActivity.ConversationMessage differentId =
                new ConversationActivity.ConversationMessage(11L, "hello", "in", "received", 0, null);

        assertTrue(diff.areItemsTheSame(before, changed));
        assertFalse(diff.areContentsTheSame(before, changed));
        assertFalse(diff.areItemsTheSame(before, differentId));
    }

    @Test
    public void emptyConversationRowUsesReservedIdAndDoesNotMatchRealMessage() {
        ConversationActivity.ConversationMessage empty =
                ConversationActivity.ConversationMessage.empty("لا توجد رسائل");
        ConversationActivity.ConversationMessage real =
                new ConversationActivity.ConversationMessage(10L, "", "", "", 0, null);

        assertTrue(empty.isEmpty());
        assertEquals(Long.MIN_VALUE, empty.id);
        assertFalse(new ConversationActivity.MessageDiffCallback().areItemsTheSame(empty, real));
    }

    @Test
    public void conversationSelectionPruningRemovesMissingAndEmptyRowIds() {
        ArrayList<Long> selected = new ArrayList<>(Arrays.asList(10L, 20L, 30L));
        ConversationActivity.ConversationMessage visible =
                new ConversationActivity.ConversationMessage(10L, "message", "in", "received", 0, null);
        ConversationActivity.ConversationMessage empty =
                ConversationActivity.ConversationMessage.empty("لا توجد نتائج");

        ConversationActivity.pruneSelection(selected, Arrays.asList(visible, empty));
        assertEquals(Collections.singletonList(10L), selected);

        ConversationActivity.pruneSelection(selected,
                Collections.singletonList(ConversationActivity.ConversationMessage.empty("لا توجد نتائج")));
        assertTrue(selected.isEmpty());
    }

}
