package com.hashem.sms;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Builds a binary MMS M-Send.req PDU consumed by SmsManager.sendMultimediaMessage.
 * Supports an allowlist of image and document MIME types; it does not perform network I/O.
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

    private static final String MULTIPART_MIXED = "application/vnd.wap.multipart.mixed";
    private static final int MMS_VERSION_1_2 = 0x92;
    private static final int FROM_INSERT_ADDRESS = 0x81;
    private static final int UTF8_MIB_ENUM = 0xEA;

    private MmsPduComposer() {
    }

    /** A single allowlisted image or document attachment. */
    public static final class ImagePart {
        public final byte[] data;
        public final String contentType;
        public final String fileName;

        public ImagePart(byte[] data, String contentType, String fileName) {
            if (data == null || data.length == 0) {
                throw new IllegalArgumentException("Image data must not be empty");
            }
            if (!isSupportedContentType(contentType)) {
                throw new IllegalArgumentException("Unsupported MMS attachment type: " + contentType);
            }
            if (fileName == null || fileName.trim().isEmpty()) {
                throw new IllegalArgumentException("Image filename is required");
            }
            this.data = data.clone();
            this.contentType = contentType;
            this.fileName = validateFileName(fileName);
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
        String safeSubject = safeText(subject);
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
        writeByte(out, 0x01); // value-length for insert-address-token
        writeByte(out, FROM_INSERT_ADDRESS);

        for (String recipient : recipients) {
            String address = normalizeRecipient(recipient);
            writeByte(out, HEADER_TO);
            writeTextString(out, address);
        }

        if (!safeSubject.isEmpty()) {
            writeByte(out, HEADER_SUBJECT);
            writeEncodedString(out, safeSubject);
        }

        writeByte(out, HEADER_CONTENT_TYPE);
        writeTextString(out, MULTIPART_MIXED);
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

    private static boolean isSupportedContentType(String type) {
        if (type == null) return false;
        switch (type.toLowerCase(java.util.Locale.ROOT)) {
            case "image/jpeg": case "image/png": case "image/gif": case "image/webp":
            case "application/pdf": case "text/plain": case "text/vcard":
            case "application/octet-stream": case "application/zip": case "application/msword":
            case "application/vnd.openxmlformats-officedocument.wordprocessingml.document":
            case "application/vnd.ms-excel": case "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet":
            case "application/vnd.ms-powerpoint": case "application/vnd.openxmlformats-officedocument.presentationml.presentation":
            case "application/rtf": case "application/json": case "text/csv":
            case "audio/mpeg": case "audio/mp4": case "video/mp4": return true;
            default: return false;
        }
    }

    private static String validateFileName(String fileName) {
        String value = safeText(fileName);
        if (value.trim().isEmpty()) {
            throw new IllegalArgumentException("Image filename is required");
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean allowed = (c >= 'A' && c <= 'Z')
                    || (c >= 'a' && c <= 'z')
                    || (c >= '0' && c <= '9')
                    || c == '.' || c == '-';
            if (!allowed) {
                throw new IllegalArgumentException(
                        "Filename must contain ASCII letters, digits, dot, or hyphen only");
            }
        }
        return value;
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
