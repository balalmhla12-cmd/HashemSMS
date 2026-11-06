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
@Config(sdk = 35)
public class DbThreadPaginationTest {
    private Db db;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication();
        context.deleteDatabase("hashem.db");
        db = new Db(context);
    }

    @Test
    public void newestTwoHundredAndOlderBatchDoNotOverlapWithEqualTimestamps() {
        for (int i = 1; i <= 205; i++) {
            assertTrue(db.addMessageAt("+1001", "message " + i, "in", "received", 5000L) > 0);
        }

        try (Cursor newest = db.threadPage("+1001", 200)) {
            assertEquals(200, newest.getCount());
            assertTrue(newest.moveToFirst());
            assertEquals(6L, newest.getLong(newest.getColumnIndexOrThrow("id")));
            assertTrue(newest.moveToLast());
            assertEquals(205L, newest.getLong(newest.getColumnIndexOrThrow("id")));
        }

        try (Cursor older = db.threadBefore("+1001", 5000L, 6L, 200)) {
            assertEquals(5, older.getCount());
            assertTrue(older.moveToFirst());
            assertEquals(5L, older.getLong(older.getColumnIndexOrThrow("id")));
            assertTrue(older.moveToLast());
            assertEquals(1L, older.getLong(older.getColumnIndexOrThrow("id")));
        }
    }

    @Test
    public void exactTwoHundredMessageBoundaryHasNoAdditionalOlderRows() {
        for (int i = 1; i <= 200; i++) {
            db.addMessageAt("+1002", "boundary " + i, "out", "sent", 7000L + i);
        }

        try (Cursor page = db.threadPage("+1002", 200)) {
            assertEquals(200, page.getCount());
            assertTrue(page.moveToFirst());
            assertEquals(1L, page.getLong(page.getColumnIndexOrThrow("id")));
            long ts = page.getLong(page.getColumnIndexOrThrow("ts"));
            long id = page.getLong(page.getColumnIndexOrThrow("id"));
            try (Cursor older = db.threadBefore("+1002", ts, id, 200)) {
                assertEquals(0, older.getCount());
            }
        }
    }

    @Test
    public void timestampAndIdTogetherDefineDeterministicOrder() {
        db.addMessageAt("+1003", "older timestamp", "in", "received", 100L);
        db.addMessageAt("+1003", "tie first", "in", "received", 200L);
        db.addMessageAt("+1003", "tie second", "in", "received", 200L);
        db.addMessageAt("+1003", "newer timestamp", "in", "received", 300L);

        try (Cursor page = db.threadPage("+1003", 10)) {
            assertTrue(page.moveToFirst());
            assertEquals("older timestamp", page.getString(page.getColumnIndexOrThrow("body")));
            assertTrue(page.moveToNext());
            assertEquals("tie first", page.getString(page.getColumnIndexOrThrow("body")));
            assertTrue(page.moveToNext());
            assertEquals("tie second", page.getString(page.getColumnIndexOrThrow("body")));
            assertTrue(page.moveToNext());
            assertEquals("newer timestamp", page.getString(page.getColumnIndexOrThrow("body")));
        }
    }
}
