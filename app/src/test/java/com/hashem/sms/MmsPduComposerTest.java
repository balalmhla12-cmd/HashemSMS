package com.hashem.sms;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class MmsPduComposerTest {
    private static byte[] bytes(String hex) {
        String[] values = hex.trim().split("\\s+");
        byte[] result = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            result[i] = (byte) Integer.parseInt(values[i], 16);
        }
        return result;
    }

    @Test
    public void composesOneRecipientAndOneJpegAgainstHandWrittenBytes() {
        byte[] actual = MmsPduComposer.build(
                "T1",
                Collections.singletonList("+123"),
                null,
                Collections.singletonList(new MmsPduComposer.ImagePart(
                        new byte[]{0x01, 0x02}, "image/jpeg", "a.jpg")));

        byte[] expected = bytes(
                "8C 80 98 54 31 00 8D 92 89 01 81 " +
                "97 2B 31 32 33 2F 54 59 50 45 3D 50 4C 4D 4E 00 " +
                "84 61 70 70 6C 69 63 61 74 69 6F 6E 2F 76 6E 64 2E " +
                "77 61 70 2E 6D 75 6C 74 69 70 61 72 74 2E 6D 69 78 65 64 00 " +
                "01 12 02 " +
                "69 6D 61 67 65 2F 6A 70 65 67 00 8E 61 2E 6A 70 67 00 01 02");
        assertArrayEquals(expected, actual);
    }

    @Test
    public void composesMultipleRecipientsAsRepeatedToHeaders() {
        byte[] actual = MmsPduComposer.build(
                "T1",
                Arrays.asList("+123", "+456"),
                null,
                Collections.singletonList(new MmsPduComposer.ImagePart(
                        new byte[]{0x01, 0x02}, "image/jpeg", "a.jpg")));

        byte[] expected = bytes(
                "8C 80 98 54 31 00 8D 92 89 01 81 " +
                "97 2B 31 32 33 2F 54 59 50 45 3D 50 4C 4D 4E 00 " +
                "97 2B 34 35 36 2F 54 59 50 45 3D 50 4C 4D 4E 00 " +
                "84 61 70 70 6C 69 63 61 74 69 6F 6E 2F 76 6E 64 2E " +
                "77 61 70 2E 6D 75 6C 74 69 70 61 72 74 2E 6D 69 78 65 64 00 " +
                "01 12 02 " +
                "69 6D 61 67 65 2F 6A 70 65 67 00 8E 61 2E 6A 70 67 00 01 02");
        assertArrayEquals(expected, actual);
    }

    @Test
    public void composesTwoImagesWithIndependentMultipartLengths() {
        byte[] actual = MmsPduComposer.build(
                "T1",
                Collections.singletonList("+123"),
                null,
                Arrays.asList(
                        new MmsPduComposer.ImagePart(new byte[]{0x01, 0x02}, "image/jpeg", "a.jpg"),
                        new MmsPduComposer.ImagePart(new byte[]{0x03}, "image/png", "b.png")));

        byte[] expected = bytes(
                "8C 80 98 54 31 00 8D 92 89 01 81 " +
                "97 2B 31 32 33 2F 54 59 50 45 3D 50 4C 4D 4E 00 " +
                "84 61 70 70 6C 69 63 61 74 69 6F 6E 2F 76 6E 64 2E " +
                "77 61 70 2E 6D 75 6C 74 69 70 61 72 74 2E 6D 69 78 65 64 00 " +
                "02 " +
                "12 02 69 6D 61 67 65 2F 6A 70 65 67 00 8E 61 2E 6A 70 67 00 01 02 " +
                "11 01 69 6D 61 67 65 2F 70 6E 67 00 8E 62 2E 70 6E 67 00 03");
        assertArrayEquals(expected, actual);
    }

    @Test
    public void encodesOptionalUtf8Subject() {
        byte[] actual = MmsPduComposer.build(
                "T1",
                Collections.singletonList("+123"),
                "Hi",
                Collections.singletonList(new MmsPduComposer.ImagePart(
                        new byte[]{0x01}, "image/jpeg", "a.jpg")));

        byte[] expected = bytes(
                "8C 80 98 54 31 00 8D 92 89 01 81 " +
                "97 2B 31 32 33 2F 54 59 50 45 3D 50 4C 4D 4E 00 " +
                "96 04 EA 48 69 00 " +
                "84 61 70 70 6C 69 63 61 74 69 6F 6E 2F 76 6E 64 2E " +
                "77 61 70 2E 6D 75 6C 74 69 70 61 72 74 2E 6D 69 78 65 64 00 " +
                "01 12 01 69 6D 61 67 65 2F 6A 70 65 67 00 8E 61 2E 6A 70 67 00 01");
        assertArrayEquals(expected, actual);
    }

    @Test
    public void rejectsNonImageParts() {
        try {
            new MmsPduComposer.ImagePart(new byte[]{0x01}, "video/x-msvideo", "clip.avi");
            fail("video parts must be rejected");
        } catch (IllegalArgumentException expected) {
            assertEquals("Unsupported MMS attachment type: video/x-msvideo", expected.getMessage());
        }
    }
    @Test
    public void acceptsGifAndPdfAttachmentParts() {
        byte[] gif = MmsPduComposer.build("T2", Collections.singletonList("+123"), null,
                Collections.singletonList(new MmsPduComposer.ImagePart(
                        new byte[]{0x47, 0x49, 0x46}, "image/gif", "sticker.gif")));
        byte[] pdf = MmsPduComposer.build("T3", Collections.singletonList("+123"), null,
                Collections.singletonList(new MmsPduComposer.ImagePart(
                        new byte[]{0x25, 0x50, 0x44, 0x46}, "application/pdf", "document.pdf")));
        assertTrue(gif.length > 20);
        assertTrue(pdf.length > 20);
        assertTrue(new String(gif, java.nio.charset.StandardCharsets.ISO_8859_1).contains("image/gif"));
        assertTrue(new String(pdf, java.nio.charset.StandardCharsets.ISO_8859_1).contains("application/pdf"));
    }

    @Test
    public void preservesOriginalGifPayloadBytesWithoutReencoding() {
        byte[] originalGif = new byte[]{
                0x47, 0x49, 0x46, 0x38, 0x39, 0x61,
                0x01, 0x00, 0x01, 0x00, (byte) 0x80, 0x00, 0x00
        };
        byte[] pdu = MmsPduComposer.build(
                "GIF1",
                Collections.singletonList("+123"),
                null,
                Collections.singletonList(new MmsPduComposer.ImagePart(
                        originalGif, "image/gif", "original.gif")));

        assertTrue(new String(pdu, java.nio.charset.StandardCharsets.ISO_8859_1)
                .contains("image/gif"));
        assertArrayEquals(originalGif,
                Arrays.copyOfRange(pdu, pdu.length - originalGif.length, pdu.length));
    }

    @Test
    public void sendRequestHeaderStartsWithRequiredBytes() {
        byte[] actual = MmsPduComposer.build(
                "T1",
                Collections.singletonList("+123"),
                null,
                Collections.singletonList(new MmsPduComposer.ImagePart(
                        new byte[]{0x01}, "image/jpeg", "a.jpg")));
        assertArrayEquals(
                bytes("8C 80 98 54 31 00 8D 92 89 01 81"),
                Arrays.copyOf(actual, 11));
    }

    @Test
    public void rejectsArabicImageFilename() {
        try {
            new MmsPduComposer.ImagePart(new byte[]{0x01}, "image/jpeg", "صورة.jpg");
            fail("Arabic filename must be rejected");
        } catch (IllegalArgumentException expected) {
            assertEquals(
                    "Filename must contain ASCII letters, digits, dot, or hyphen only",
                    expected.getMessage());
        }
    }

    @Test
    public void rejectsSubjectContainingNewline() {
        try {
            MmsPduComposer.build(
                    "T1",
                    Collections.singletonList("+123"),
                    "first\nsecond",
                    Collections.singletonList(new MmsPduComposer.ImagePart(
                            new byte[]{0x01}, "image/jpeg", "a.jpg")));
            fail("Subject containing newline must be rejected");
        } catch (IllegalArgumentException expected) {
            assertEquals(
                    "MMS text contains a forbidden control character",
                    expected.getMessage());
        }
    }

}
