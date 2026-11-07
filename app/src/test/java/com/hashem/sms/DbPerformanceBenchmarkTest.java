package com.hashem.sms;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import android.provider.Telephony;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public final class DbPerformanceBenchmarkTest {
    private static final int CONVERSATION_COUNT = 5_000;
    private static final int MESSAGES_PER_CONVERSATION = 10;
    private static final int LARGE_THREAD_MESSAGES = 50_000;
    private static final int IMPORT_BATCH_SIZE = 500;
    private static final int IMPORT_MESSAGE_COUNT = 50_000;
    private static final int MEASURED_RUNS = 5;
    private static final String LARGE_ADDRESS = "+19999999999";
    private static final long IMPORT_PROVIDER_ID_BASE = 9_000_000L;

    private Context context;
    private Db database;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        context.deleteDatabase("hashem.db");
        deleteRecursively(new File(context.getFilesDir(), "safety-backups"));
        database = new Db(context);
        seedBenchmarkDatabase();
    }

    @After
    public void tearDown() {
        if (database != null) {
            database.close();
            database = null;
        }
        deleteRecursively(new File(context.getFilesDir(), "safety-backups"));
        deleteRecursively(new File(context.getFilesDir(), "mms"));
        context.deleteDatabase("hashem.db");
    }

    @Test
    public void measuresDatabaseWorkloadsAndPrintsAverages() throws Exception {
        System.out.println("PERF_BENCH dataset conversations=5000 messagesPerConversation=10"
                + " largeConversationMessages=50000 totalSeedMessages=100000"
                + " warmup=1 measuredRuns=5; timings are Robolectric/CI, not handset timings.");

        measure("db.listThreads(all)", null,
                () -> consumeThreads("all"),
                count -> assertEquals(3_751, count.intValue()));
        measure("db.listThreads(unread)", null,
                () -> consumeThreads("unread"),
                count -> assertEquals(1_250, count.intValue()));
        measure("db.listThreads(unknown)", null,
                () -> consumeThreads("unknown"),
                count -> assertEquals(3_751, count.intValue()));
        measure("db.listThreads(archived)", null,
                () -> consumeThreads("archived"),
                count -> assertEquals(1_250, count.intValue()));

        measure("db.unreadIncomingConversationAddresses", null,
                () -> database.unreadIncomingConversationAddresses(),
                addresses -> assertEquals(2_500, addresses.size()));

        measure("db.thread(50000-message conversation)", null,
                () -> consumeLargeThread(),
                count -> assertEquals(LARGE_THREAD_MESSAGES, count.intValue()));

        final List<String> inMemoryBodies = readLargeThreadBodies();
        measure("thread result contains() in memory", null,
                () -> countContains(inMemoryBodies, "needle"),
                matches -> assertEquals(5, matches.intValue()));

        measure("address-scoped SQL LIKE (comparison only)", null,
                () -> countLikeMatches(),
                matches -> assertEquals(5, matches.intValue()));

        final ArrayList<ArrayList<Object[]>> importBatches = buildImportBatches();
        measure("importSystemMessageBatch (50000 rows / 500 per batch)",
                () -> deleteImportedRows(),
                () -> importAllBatches(importBatches),
                success -> assertTrue("Every 500-row import batch must succeed", success));
        assertEquals(IMPORT_MESSAGE_COUNT, countImportedMessages());
        deleteImportedRows();

        final byte[][] backupArchive = new byte[1][];
        measure("HashemBackupManager.exportBackup", null,
                () -> {
                    ByteArrayOutputStream output = new ByteArrayOutputStream();
                    HashemBackupManager.exportBackup(context, output);
                    backupArchive[0] = output.toByteArray();
                    return backupArchive[0].length;
                },
                size -> assertTrue("Backup archive must not be empty", size > 0));

        measure("MessageCsvExporter.writeMessages", null,
                () -> {
                    ByteArrayOutputStream output = new ByteArrayOutputStream();
                    MessageCsvExporter.writeMessages(context, output);
                    return output.size();
                },
                size -> assertTrue("CSV export must not be empty", size > 0));

        assertTrue("A valid backup archive is required for restore timing",
                backupArchive[0] != null && backupArchive[0].length > 0);
        measure("HashemBackupManager.restoreBackup (includes safety snapshot)",
                () -> prepareForRestore(),
                () -> {
                    HashemBackupManager.restoreBackup(
                            context, new ByteArrayInputStream(backupArchive[0]));
                    database = new Db(context);
                    return Boolean.TRUE;
                },
                restored -> {
                    assertTrue(restored);
                    assertEquals(100_000, countAllMessages());
                });

        System.out.println("PERF_BENCH complete; no timing threshold is used to fail this test.");
    }

    private void seedBenchmarkDatabase() {
        SQLiteDatabase sql = database.getWritableDatabase();
        SQLiteStatement insertMessage = sql.compileStatement(
                "INSERT INTO messages(address,body,type,status,ts,read,star,trash)"
                        + " VALUES(?,?,?,?,?,?,0,0)");
        SQLiteStatement insertFlag = sql.compileStatement(
                "INSERT INTO conversation_flags(address,archived,blocked) VALUES(?,?,0)");
        sql.beginTransaction();
        try {
            for (int conversation = 0; conversation < CONVERSATION_COUNT; conversation++) {
                String address = conversationAddress(conversation);
                int archived = conversation % 4 == 0 ? 1 : 0;

                insertFlag.bindString(1, address);
                insertFlag.bindLong(2, archived);
                insertFlag.executeInsert();
                insertFlag.clearBindings();

                for (int message = 0; message < MESSAGES_PER_CONVERSATION; message++) {
                    boolean incoming = message % 2 == 0;
                    boolean unread = incoming && conversation % 2 == 0;
                    insertMessage.bindString(1, address);
                    insertMessage.bindString(2, "seed conversation " + conversation
                            + " message " + message);
                    insertMessage.bindString(3, incoming ? "in" : "out");
                    insertMessage.bindString(4, incoming ? "received" : "تم الإرسال");
                    insertMessage.bindLong(5,
                            1_000_000L + conversation * 10L + message);
                    insertMessage.bindLong(6, unread ? 0 : 1);
                    insertMessage.executeInsert();
                    insertMessage.clearBindings();
                }
            }

            for (int message = 0; message < LARGE_THREAD_MESSAGES; message++) {
                insertMessage.bindString(1, LARGE_ADDRESS);
                insertMessage.bindString(2, message % 10_000 == 0
                        ? "large searchable needle message " + message
                        : "large conversation message " + message);
                insertMessage.bindString(3, message % 2 == 0 ? "in" : "out");
                insertMessage.bindString(4, "received");
                insertMessage.bindLong(5, 100_000_000L + message);
                insertMessage.bindLong(6, 1);
                insertMessage.executeInsert();
                insertMessage.clearBindings();
            }
            sql.setTransactionSuccessful();
        } finally {
            sql.endTransaction();
            insertMessage.close();
            insertFlag.close();
        }
    }

    private static String conversationAddress(int index) {
        return "+1555" + String.format(Locale.ROOT, "%04d", index);
    }

    private Integer consumeThreads(String filter) {
        int count = 0;
        try (Cursor cursor = database.listThreads(filter)) {
            while (cursor.moveToNext()) {
                cursor.getLong(cursor.getColumnIndexOrThrow("id"));
                count++;
            }
        }
        return count;
    }

    private Integer consumeLargeThread() {
        int count = 0;
        try (Cursor cursor = database.thread(LARGE_ADDRESS)) {
            int bodyColumn = cursor.getColumnIndexOrThrow("body");
            while (cursor.moveToNext()) {
                cursor.getString(bodyColumn);
                count++;
            }
        }
        return count;
    }

    private List<String> readLargeThreadBodies() {
        ArrayList<String> bodies = new ArrayList<>(LARGE_THREAD_MESSAGES);
        try (Cursor cursor = database.thread(LARGE_ADDRESS)) {
            int bodyColumn = cursor.getColumnIndexOrThrow("body");
            while (cursor.moveToNext()) bodies.add(cursor.getString(bodyColumn));
        }
        assertEquals(LARGE_THREAD_MESSAGES, bodies.size());
        return bodies;
    }

    private static Integer countContains(List<String> bodies, String needle) {
        int matches = 0;
        for (String body : bodies) {
            if (body != null && body.contains(needle)) matches++;
        }
        return matches;
    }

    private Integer countLikeMatches() {
        try (Cursor cursor = database.getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM messages WHERE address=? AND body LIKE ?",
                new String[]{LARGE_ADDRESS, "%needle%"})) {
            assertTrue(cursor.moveToFirst());
            return cursor.getInt(0);
        }
    }

    private ArrayList<ArrayList<Object[]>> buildImportBatches() {
        ArrayList<ArrayList<Object[]>> batches = new ArrayList<>(
                IMPORT_MESSAGE_COUNT / IMPORT_BATCH_SIZE);
        for (int offset = 0; offset < IMPORT_MESSAGE_COUNT; offset += IMPORT_BATCH_SIZE) {
            ArrayList<Object[]> batch = new ArrayList<>(IMPORT_BATCH_SIZE);
            for (int index = offset; index < offset + IMPORT_BATCH_SIZE; index++) {
                batch.add(new Object[]{
                        IMPORT_PROVIDER_ID_BASE + index,
                        "+18880000000",
                        "benchmark imported message " + index,
                        Telephony.Sms.MESSAGE_TYPE_INBOX,
                        300_000_000L + index,
                        0
                });
            }
            batches.add(batch);
        }
        return batches;
    }

    private Boolean importAllBatches(ArrayList<ArrayList<Object[]>> batches) {
        for (ArrayList<Object[]> batch : batches) {
            if (!database.importSystemMessageBatch(batch)) return Boolean.FALSE;
        }
        return Boolean.TRUE;
    }

    private void deleteImportedRows() {
        database.getWritableDatabase().delete(
                "messages",
                "provider_id BETWEEN ? AND ?",
                new String[]{
                        String.valueOf(IMPORT_PROVIDER_ID_BASE),
                        String.valueOf(IMPORT_PROVIDER_ID_BASE + IMPORT_MESSAGE_COUNT - 1)
                });
    }

    private int countImportedMessages() {
        try (Cursor cursor = database.getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM messages WHERE provider_id BETWEEN ? AND ?",
                new String[]{
                        String.valueOf(IMPORT_PROVIDER_ID_BASE),
                        String.valueOf(IMPORT_PROVIDER_ID_BASE + IMPORT_MESSAGE_COUNT - 1)
                })) {
            assertTrue(cursor.moveToFirst());
            return cursor.getInt(0);
        }
    }

    private int countAllMessages() {
        try (Cursor cursor = database.getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM messages", null)) {
            assertTrue(cursor.moveToFirst());
            return cursor.getInt(0);
        }
    }

    private void prepareForRestore() {
        if (database != null) {
            database.close();
            database = null;
        }
        deleteRecursively(new File(context.getFilesDir(), "safety-backups"));
    }

    private <T> void measure(
            String name,
            ThrowingRunnable beforeEach,
            ThrowingSupplier<T> operation,
            Consumer<T> validation) throws Exception {
        if (beforeEach != null) beforeEach.run();
        T warmup = operation.get();
        validation.accept(warmup);

        double totalMillis = 0.0;
        double maximumMillis = 0.0;
        long maximumHeapDelta = 0L;
        for (int run = 0; run < MEASURED_RUNS; run++) {
            if (beforeEach != null) beforeEach.run();
            long heapBefore = usedHeapBytes();
            long started = System.nanoTime();
            T result = operation.get();
            long elapsedNanos = System.nanoTime() - started;
            long heapAfter = usedHeapBytes();
            validation.accept(result);

            double elapsedMillis = elapsedNanos / 1_000_000.0;
            totalMillis += elapsedMillis;
            maximumMillis = Math.max(maximumMillis, elapsedMillis);
            maximumHeapDelta = Math.max(maximumHeapDelta,
                    Math.max(0L, heapAfter - heapBefore));
        }

        double averageMillis = totalMillis / MEASURED_RUNS;
        double heapDeltaMegabytes = maximumHeapDelta / (1024.0 * 1024.0);
        System.out.printf(Locale.ROOT,
                "PERF_BENCH item=\"%s\" average_ms=%.3f maximum_ms=%.3f"
                        + " max_jvm_heap_delta_approx_mb=%.2f runs=%d warmup=1%n",
                name, averageMillis, maximumMillis, heapDeltaMegabytes, MEASURED_RUNS);
    }

    private static long usedHeapBytes() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }

    private static void deleteRecursively(File file) {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) deleteRecursively(child);
            }
        }
        file.delete();
    }

    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }
}
