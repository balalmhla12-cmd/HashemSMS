package com.hashem.sms;

import static org.junit.Assert.assertTrue;

import android.content.Context;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public final class MessageCsvExporterTest {
    private Context context;
    private Db database;

    @Before public void setUp() {
        context = RuntimeEnvironment.getApplication();
        context.deleteDatabase("hashem.db");
        database = new Db(context);
    }

    @After public void tearDown() {
        if (database != null) database.close();
        context.deleteDatabase("hashem.db");
    }

    @Test public void exportsArabicAndEscapesCommasQuotesAndMultilineFields() throws Exception {
        database.addMessageAt(
                "+31, 20",
                "مرحبا, قال \"أهلاً\"\nالسطر الثاني",
                "out",
                "تم الإرسال, بنجاح",
                1700000000123L);

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        MessageCsvExporter.writeMessages(context, output);
        String csv = new String(output.toByteArray(), StandardCharsets.UTF_8);

        assertTrue(csv.startsWith("\uFEFF\"العنوان\",\"النص\",\"النوع\",\"الوقت\",\"الحالة\"\r\n"));
        assertTrue(csv.contains("\"+31, 20\""));
        assertTrue(csv.contains("\"مرحبا, قال \"\"أهلاً\"\"\nالسطر الثاني\""));
        assertTrue(csv.contains("\"out\""));
        assertTrue(csv.contains("\"2023-11-14T22:13:20.123Z\""));
        assertTrue(csv.contains("\"تم الإرسال, بنجاح\""));
    }
}
