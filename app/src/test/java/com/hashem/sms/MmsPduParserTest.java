package com.hashem.sms;

import static org.junit.Assert.*;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import org.junit.Test;

public class MmsPduParserTest {
    @Test public void parsesNotificationIndicationAndRejectsNonHttpLocation() {
        byte[] pdu = bytes(1, 6, 0xbe, 0x8c, 0x82, 0x98,
                't', 'x', '1', 0, 0x83);
        byte[] location = "https://mmsc.example/message/1".getBytes(StandardCharsets.UTF_8);
        byte[] full = new byte[pdu.length + location.length + 1];
        System.arraycopy(pdu, 0, full, 0, pdu.length);
        System.arraycopy(location, 0, full, pdu.length, location.length);
        MmsPduParser.Notification result = MmsPduParser.parseNotification(full);
        assertEquals("tx1", result.transactionId);
        assertEquals("https://mmsc.example/message/1", result.contentLocation);

        byte[] invalid = bytes(1, 6, 0xbe, 0x8c, 0x82, 0x98,
                't', 'x', 0, 0x83, 'f', 't', 'p', ':', '/', '/', 'x', 0);
        try {
            MmsPduParser.parseNotification(invalid);
            fail("non-HTTP location must be rejected");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("scheme"));
        }
    }

    @Test public void parsesRetrieveConfTextAndAttachmentParts() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        write(out, 0x8c, 0x84, 0x89);
        out.write("15551234567/TYPE=PLMN".getBytes(StandardCharsets.UTF_8));
        out.write(0);
        write(out, 0x96);
        out.write("Photo".getBytes(StandardCharsets.UTF_8));
        out.write(0);
        write(out, 0x85, 1, 0x65, 0x84, 0xa3, 2);

        byte[] textHeaders = "text/plain".getBytes(StandardCharsets.UTF_8);
        out.write(encodeUintvar(textHeaders.length + 1));
        out.write(encodeUintvar(5));
        out.write(textHeaders);
        out.write(0);
        out.write("hello".getBytes(StandardCharsets.UTF_8));

        byte[] imageHeaders = "image/jpeg".getBytes(StandardCharsets.UTF_8);
        out.write(encodeUintvar(imageHeaders.length + 1));
        out.write(encodeUintvar(3));
        out.write(imageHeaders);
        out.write(0);
        write(out, 1, 2, 3);

        MmsPduParser.Message message = MmsPduParser.parseRetrieved(out.toByteArray());
        assertEquals("15551234567/TYPE=PLMN", message.from);
        assertEquals("Photo", message.subject);
        assertEquals("hello", message.text);
        assertEquals(1, message.parts.size());
        assertEquals("image/jpeg", message.parts.get(0).contentType);
        assertArrayEquals(bytes(1, 2, 3), message.parts.get(0).data);
    }

    @Test public void rejectsTruncatedAndOversizedPdus() {
        for (byte[] pdu : new byte[][] {bytes(0x8c, 0x84), new byte[MmsPduParser.MAX_PDU_BYTES + 1]}) {
            try {
                MmsPduParser.parseRetrieved(pdu);
                fail("invalid PDU must be rejected");
            } catch (IllegalArgumentException expected) {
                // Expected bounded-parser failure.
            }
        }
    }

    private static byte[] bytes(int... values) {
        byte[] out = new byte[values.length];
        for (int i = 0; i < values.length; i++) out[i] = (byte) values[i];
        return out;
    }
    private static void write(ByteArrayOutputStream out, int... values) {
        for (int value : values) out.write(value & 255);
    }
    private static byte[] encodeUintvar(int value) {
        if (value < 128) return bytes(value);
        return bytes(0x80 | (value >>> 7), value & 0x7f);
    }
}
