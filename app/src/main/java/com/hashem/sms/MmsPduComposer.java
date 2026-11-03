package com.hashem.sms;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Builds the binary MMS M-Send.req PDU consumed by SmsManager.sendMultimediaMessage.
 *
 * This class deliberately implements only image-only multipart/related messages.
 * It does not perform network I/O or write files.
 */
public final class MmsPduComposer {
    private static final int HEADER_MESSAGE_TYPE = 0x8C;
    private static final int HEADER_TRANSACTION_ID = 0x98;
    private static final int HEADER_MMS_VERSION = 0x8D;
    private static final int HEADER_FROM = 0x89;
    private static final int HEADER_TO = 0x97;
    private static final int HEADER_SUBJECT = 0x96;
    private static final int HEADER_CONTENT_TYPE = 0x84;
    private static final int HEADER_CONTENT_LOCATION = 0x8E;

    private static final String MULTIPART_RELATED = "application/vnd.wap.multipart.related";
    private static final int MMS_VERSION_1_2 = 0x92;
    private static final int FROM_INSERT_ADDRESS = 0x81;
    private static final int UTF8_MIB_ENUM = 0xEA;

    private MmsPduComposer() {
    }

    /** A single JPEG or PNG attachment. */
    public static final class ImagePart {
        public final byte[] data;
        public final String contentType;
        public final String fileName;

        public ImagePart(byte[] data, String contentType, String fileName) {
            if (data == null || data.length == 0) {
                throw new IllegalArgumentException("Image data must not be empty");
            }
            if (!"image/jpeg".equals(contentType) && !"image/png".equals(contentType)) {
                throw new IllegalArgumentException("Only JPEG and PNG images are supported");
            }
            if (fileName == null || fileName.trim().isEmpty()) {
                throw new IllegalArgumentException("Image filename is required");
            }
            this.data = data.clone();
            this.contentType = contentType;
            this.fileName = safeText(fileName);
        }
    }

    /**
     * Creates an M-Send.req PDU. The sender address is intentionally omitted using
     * the MMS insert-address-token, allowing the carrier MMSC to supply it.
     *
     * @param transactionId unique transaction identifier
     * @param recipients one or more MSISDN recipient addresses
     * @param subject optional UTF-8 subject
     * @param images one or more image parts
     */
    public static byte[] build(String transactionId, List<String> recipients,
            String subject, List<ImagePart> images) {
        String tx = safeText(transactionId);
        if (tx.isEmpty()) {
            throw new IllegalArgumentException("Transaction ID is required");
        }
        if (recipients == null || recipients.isEmpty()) {
            throw new IllegalArgumentException("At least one recipient is required");
        }
        if (images == null || images.isEmpty()) {
            throw new IllegalArgumentException("At least one image is required");
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeByte(out, HEADER_MESSAGE_TYPE);
        writeByte(out, 0x80); // m-send-req
        writeByte(out, HEADER_TRANSACTION_ID);
        writeTextString(out, tx);
        writeByte(out, HEADER_MMS_VERSION);
        writeByte(out, MMS_VERSION_1_2);
        writeByte(out, HEADER_FROM);
        writeByte(out, FROM_INSERT_ADDRESS);

        for (String recipient : recipients) {
            String address = normalizeRecipient(recipient);
            writeByte(out, HEADER_TO);
            writeTextString(out, address);
        }

        if (subject != null && !subject.isEmpty()) {
            writeByte(out, HEADER_SUBJECT);
            writeEncodedString(out, subject);
        }

        writeByte(out, HEADER_CONTENT_TYPE);
        writeTextString(out, MULTIPART_RELATED);
        writeUintvar(out, images.size());

        for (ImagePart image : images) {
            byte[] partHeaders = buildPartHeaders(image);
            writeUintvar(out, partHeaders.length);
            writeUintvar(out, image.data.length);
            writeBytes(out, partHeaders);
            writeBytes(out, image.data);
        }
        return out.toByteArray();
    }

    private static byte[] buildPartHeaders(ImagePart image) {
        ByteArrayOutputStream headers = new ByteArrayOutputStream();
        writeTextString(headers, image.contentType);
        writeByte(headers, HEADER_CONTENT_LOCATION);
        writeTextString(headers, image.fileName);
        return headers.toByteArray();
    }

    private static String normalizeRecipient(String recipient) {
        String address = safeText(recipient).trim();
        if (address.isEmpty()) {
            throw new IllegalArgumentException("Recipient address is required");
        }
        if (address.indexOf('/') >= 0) {
            return address;
        }
        return address + "/TYPE=PLMN";
    }

    private static String safeText(String value) {
        if (value == null) {
            return "";
        }
        if (value.indexOf('\0') >= 0 || value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) {
            throw new IllegalArgumentException("MMS text contains a forbidden control character");
        }
        return value;
    }

    private static void writeEncodedString(ByteArrayOutputStream out, String value) {
        byte[] text = value.getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream encoded = new ByteArrayOutputStream();
        writeByte(encoded, UTF8_MIB_ENUM);
        writeBytes(encoded, text);
        writeByte(encoded, 0);
        writeValueLength(out, encoded.size());
        writeBytes(out, encoded.toByteArray());
    }

    private static void writeTextString(ByteArrayOutputStream out, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        writeBytes(out, bytes);
        writeByte(out, 0);
    }

    private static void writeValueLength(ByteArrayOutputStream out, int length) {
        if (length <= 30) {
            writeByte(out, length);
        } else {
            writeByte(out, 31);
            writeUintvar(out, length);
        }
    }

    /** WSP uintvar encoding, most-significant seven-bit group first. */
    private static void writeUintvar(ByteArrayOutputStream out, int value) {
        if (value < 0) {
            throw new IllegalArgumentException("Negative WSP length");
        }
        int groups = 1;
        int remaining = value;
        while ((remaining >>>= 7) != 0) {
            groups++;
        }
        for (int i = groups - 1; i >= 0; i--) {
            int part = (value >>> (i * 7)) & 0x7F;
            if (i != 0) {
                part |= 0x80;
            }
            writeByte(out, part);
        }
    }

    private static void writeByte(ByteArrayOutputStream out, int value) {
        out.write(value & 0xFF);
    }

    private static void writeBytes(ByteArrayOutputStream out, byte[] values) {
        try {
            out.write(values);
        } catch (IOException impossible) {
            throw new AssertionError(impossible);
        }
    }
}
