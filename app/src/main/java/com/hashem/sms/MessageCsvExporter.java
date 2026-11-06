package com.hashem.sms;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** Streams the app's message rows to a standalone UTF-8 CSV document. */
final class MessageCsvExporter {
    private MessageCsvExporter() {}

    static void exportMessages(Context context, Uri destination) throws IOException {
        if (context == null || destination == null) {
            throw new IllegalArgumentException("ملف تصدير الرسائل غير محدد");
        }
        try (OutputStream output = context.getContentResolver().openOutputStream(destination, "w")) {
            if (output == null) throw new IOException("تعذّر فتح ملف CSV للكتابة.");
            writeMessages(context, output);
            output.flush();
        }
    }

    static void writeMessages(Context context, OutputStream output) throws IOException {
        if (context == null || output == null) {
            throw new IllegalArgumentException("وجهة تصدير الرسائل غير محددة");
        }
        Db database = new Db(context.getApplicationContext());
        SimpleDateFormat timestampFormat =
                new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT);
        timestampFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        try (Cursor cursor = database.getReadableDatabase().rawQuery(
                "SELECT address,body,type,ts,status FROM messages ORDER BY ts ASC,id ASC", null)) {
            BufferedWriter writer = new BufferedWriter(
                    new OutputStreamWriter(output, StandardCharsets.UTF_8));
            writer.write('\uFEFF');
            writeRow(writer, "العنوان", "النص", "النوع", "الوقت", "الحالة");
            while (cursor.moveToNext()) {
                String address = cursor.isNull(0) ? "" : cursor.getString(0);
                String body = cursor.isNull(1) ? "" : cursor.getString(1);
                String type = cursor.isNull(2) ? "" : cursor.getString(2);
                String timestamp = timestampFormat.format(new Date(cursor.getLong(3)));
                String status = cursor.isNull(4) ? "" : cursor.getString(4);
                writeRow(writer, address, body, type, timestamp, status);
            }
            writer.flush();
        } finally {
            database.close();
        }
    }

    private static void writeRow(BufferedWriter writer, String... fields) throws IOException {
        for (int index = 0; index < fields.length; index++) {
            if (index > 0) writer.write(',');
            writer.write('"');
            String value = fields[index] == null ? "" : fields[index];
            for (int character = 0; character < value.length(); character++) {
                char current = value.charAt(character);
                if (current == '"') writer.write("\"\"");
                else writer.write(current);
            }
            writer.write('"');
        }
        writer.write("\r\n");
    }
}
