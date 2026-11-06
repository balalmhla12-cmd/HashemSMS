package com.hashem.sms;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Bounded parser for the WAP MMS notification and retrieve-conf PDUs.
 * It deliberately accepts only well-formed, size-bounded input and never
 * interprets remote filenames as filesystem paths.
 */
final class MmsPduParser {
    static final int MAX_PDU_BYTES = 8 * 1024 * 1024;
    static final int MAX_PARTS = 64;
    static final int MAX_PART_BYTES = 6 * 1024 * 1024;

    static final class Notification {
        final String transactionId;
        final String contentLocation;
        Notification(String tx, String location) {
            transactionId = tx;
            contentLocation = location;
        }
    }

    static final class Part {
        final String contentType;
        final String fileName;
        final byte[] data;
        Part(String type, String name, byte[] bytes) {
            contentType = type;
            fileName = name;
            data = bytes;
        }
    }

    static final class Message {
        final String from;
        final String subject;
        final long dateMillis;
        final String text;
        final List<Part> parts;
        Message(String sender, String title, long date, String body, List<Part> attachments) {
            from = sender;
            subject = title;
            dateMillis = date;
            text = body;
            parts = Collections.unmodifiableList(attachments);
        }
    }

    private MmsPduParser() {}

    static Notification parseNotification(byte[] pdu) {
        if (pdu == null || pdu.length < 6 || pdu.length > 64 * 1024) {
            throw new IllegalArgumentException("Invalid MMS notification size");
        }
        Cursor c = new Cursor(pdu);
        c.read(); // WSP transaction id
        if (c.read() != 0x06) throw new IllegalArgumentException("Not a WSP Push PDU");
        int headerLength = c.readUintvar();
        if (headerLength <= 0 || headerLength > c.remaining()) {
            throw new IllegalArgumentException("Invalid WSP header length");
        }
        int headerEnd = c.position() + headerLength;
        String contentType = c.readContentType();
        if (!"application/vnd.wap.mms-message".equals(contentType)) {
            throw new IllegalArgumentException("Unexpected WSP content type");
        }
        String tx = "";
        String location = "";
        boolean notificationType = false;
        while (c.position() < headerEnd) {
            int header = c.read();
            switch (header) {
                case 0x8c: // X-Mms-Message-Type
                    notificationType = c.read() == 0x82;
                    break;
                case 0x98: // X-Mms-Transaction-ID
                    tx = c.readText();
                    break;
                case 0x83: // X-Mms-Content-Location
                    location = c.readText();
                    break;
                case 0x88: // X-Mms-Expiry
                    c.skipValueLength();
                    break;
                case 0x8d: // X-Mms-MMS-Version
                case 0x8a: // X-Mms-Message-Class
                case 0x8e: // X-Mms-Message-Size (long-integer)
                    if (header == 0x8e) {
                        int n = c.read();
                        if (n < 1 || n > 8) throw new IllegalArgumentException("Invalid MMS message size header");
                        c.skip(n);
                    } else {
                        c.read();
                    }
                    break;
                case 0x89: // X-Mms-From
                case 0x96: // X-Mms-Subject
                case 0x97: // X-Mms-To
                    c.skipEncodedString();
                    break;
                case 0x84: // X-Mms-Content-Type
                    c.skipContentType();
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported MMS notification header 0x"
                            + Integer.toHexString(header));
            }
            if (c.position() > headerEnd) throw new IllegalArgumentException("MMS header exceeds WSP bounds");
        }
        if (c.position() != headerEnd || !notificationType || tx.isEmpty() || location.isEmpty()) {
            throw new IllegalArgumentException("MMS notification is missing required headers");
        }
        if (!(location.startsWith("http://") || location.startsWith("https://"))) {
            throw new IllegalArgumentException("Unsupported MMS content location scheme");
        }
        return new Notification(tx, location);
    }

    static Message parseRetrieved(byte[] pdu) {
        if (pdu == null || pdu.length < 8 || pdu.length > MAX_PDU_BYTES) {
            throw new IllegalArgumentException("Invalid retrieved MMS size");
        }
        Cursor c = new Cursor(pdu);
        String from = "";
        String subject = "";
        long date = 0L;
        int contentTypeOffset = -1;
        while (c.remaining() > 0) {
            int header = c.read();
            if (header == 0x8c) {
                if (c.read() != 0x84) throw new IllegalArgumentException("Downloaded PDU is not retrieve-conf");
            } else if (header == 0x89) {
                from = c.readFromValue();
            } else if (header == 0x96) {
                subject = c.readEncodedString();
            } else if (header == 0x85) {
                date = c.readLongInteger() * 1000L;
            } else if (header == 0x84) {
                contentTypeOffset = c.position();
                break;
            } else if (header == 0x98 || header == 0x8b || header == 0x83
                    || header == 0x93 || header == 0x9a || header == 0xb3 || header == 0xb4) {
                c.readText();
            } else if (header == 0x97 || header == 0x81 || header == 0x82) {
                c.skipEncodedString();
            } else if (header == 0x8d || header == 0x8a || header == 0x8f
                    || header == 0x90 || header == 0x91 || header == 0x92
                    || header == 0x94 || header == 0x95 || header == 0x9b
                    || header == 0xa3 || header == 0xa6 || header == 0xa8) {
                c.read();
            } else if (header == 0x8e || header == 0x9f || header == 0xa9 || header == 0xaa || header == 0xab) {
                int n = c.read();
                if (n < 1 || n > 8) throw new IllegalArgumentException("Invalid MMS integer header");
                c.skip(n);
            } else if (header == 0x88) {
                c.skipValueLength();
            } else {
                throw new IllegalArgumentException("Unsupported MMS retrieve header");
            }
        }
        if (contentTypeOffset < 0) throw new IllegalArgumentException("MMS content type is missing");
        String mediaType = c.readContentType();
        if (!mediaType.startsWith("application/vnd.wap.multipart.")) {
            throw new IllegalArgumentException("Unsupported MMS body type");
        }
        int count = c.readUintvar();
        if (count < 1 || count > MAX_PARTS) throw new IllegalArgumentException("Invalid MMS part count");
        ArrayList<Part> parts = new ArrayList<>();
        StringBuilder body = new StringBuilder();
        for (int i = 0; i < count; i++) {
            int headerLength = c.readUintvar();
            int dataLength = c.readUintvar();
            if (headerLength <= 0 || headerLength > 4096 || dataLength < 0
                    || dataLength > MAX_PART_BYTES || headerLength + dataLength > c.remaining()) {
                throw new IllegalArgumentException("Invalid MMS part bounds");
            }
            int end = c.position() + headerLength;
            String type = c.readPartContentType(end);
            String name = c.readPartName(end);
            c.position(end);
            byte[] bytes = c.readBytes(dataLength);
            if (type.startsWith("text/plain")) {
                Charset charset = StandardCharsets.UTF_8;
                String decoded = new String(bytes, charset);
                if (!decoded.trim().isEmpty()) {
                    if (body.length() > 0) body.append('\n');
                    body.append(decoded);
                }
            } else {
                parts.add(new Part(type, safeFileName(name, i, type), bytes));
            }
        }
        if (c.remaining() != 0) throw new IllegalArgumentException("Unexpected trailing MMS data");
        if (from.trim().isEmpty()) throw new IllegalArgumentException("MMS sender is missing");
        return new Message(from.trim(), subject.trim(), date, body.toString(), parts);
    }

    private static String safeFileName(String value, int index, String type) {
        String name = value == null ? "" : value.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) name = name.substring(slash + 1);
        name = name.replaceAll("[^A-Za-z0-9._-]", "_");
        if (name.isEmpty() || name.equals(".") || name.equals("..")) {
            String ext = type.contains("jpeg") ? ".jpg" : type.contains("png") ? ".png"
                    : type.contains("gif") ? ".gif" : type.contains("mp4") ? ".mp4" : ".bin";
            name = "attachment-" + index + ext;
        }
        return name.length() > 100 ? name.substring(name.length() - 100) : name;
    }

    private static final class Cursor {
        private final byte[] data;
        private int p;
        Cursor(byte[] bytes) { data = bytes; }
        int position() { return p; }
        void position(int value) {
            if (value < 0 || value > data.length) throw new IllegalArgumentException("Invalid MMS offset");
            p = value;
        }
        int remaining() { return data.length - p; }
        int read() {
            if (p >= data.length) throw new IllegalArgumentException("Truncated MMS PDU");
            return data[p++] & 255;
        }
        void skip(int n) {
            if (n < 0 || n > remaining()) throw new IllegalArgumentException("Invalid MMS length");
            p += n;
        }
        byte[] readBytes(int n) {
            if (n < 0 || n > remaining()) throw new IllegalArgumentException("Invalid MMS data length");
            byte[] out = new byte[n];
            System.arraycopy(data, p, out, 0, n);
            p += n;
            return out;
        }
        int readUintvar() {
            int value = 0;
            for (int i = 0; i < 5; i++) {
                int b = read();
                if (value > (Integer.MAX_VALUE >>> 7)) throw new IllegalArgumentException("MMS length overflow");
                value = (value << 7) | (b & 0x7f);
                if ((b & 0x80) == 0) return value;
            }
            throw new IllegalArgumentException("Invalid MMS uintvar");
        }
        String readText() {
            int start = p;
            while (p < data.length && data[p] != 0) {
                if (p - start > 2048) throw new IllegalArgumentException("MMS text header too long");
                p++;
            }
            if (p >= data.length) throw new IllegalArgumentException("Unterminated MMS text");
            String value = new String(data, start, p - start, StandardCharsets.UTF_8);
            p++;
            return value;
        }
        void skipEncodedString() {
            int first = read();
            if (first <= 31) {
                int length = first == 31 ? readUintvar() : first;
                if (length > remaining()) throw new IllegalArgumentException("Invalid encoded string length");
                skip(length);
            } else {
                p--;
                readText();
            }
        }
        String readFromValue() {
            int token = read();
            if (token == 0x81) return "";
            if (token == 0x80) return readEncodedString();
            p--;
            return readEncodedString();
        }
        String readEncodedString() {
            int first = read();
            if (first > 31) {
                p--;
                return readText();
            }
            int length = first == 31 ? readUintvar() : first;
            if (length <= 0 || length > remaining()) throw new IllegalArgumentException("Invalid encoded string");
            int end = p + length;
            int charsetId = read();
            Charset charset = charsetId == 0x84 ? StandardCharsets.UTF_16 : StandardCharsets.UTF_8;
            int start = p;
            while (p < end && data[p] != 0) p++;
            String result = new String(data, start, p - start, charset);
            p = end;
            return result;
        }
        long readLongInteger() {
            int n = read();
            if (n < 1 || n > 8 || n > remaining()) throw new IllegalArgumentException("Invalid MMS long integer");
            long value = 0;
            for (int i = 0; i < n; i++) value = (value << 8) | read();
            return value;
        }
        void skipValueLength() {
            int first = read();
            int length = first <= 30 ? first : first == 31 ? readUintvar() : -1;
            if (length < 0) throw new IllegalArgumentException("Invalid MMS value length");
            skip(length);
        }
        void skipContentType() {
            int first = read();
            if (first <= 31) {
                int length = first == 31 ? readUintvar() : first;
                skip(length);
            } else if (first < 128) {
                p--;
                readText();
            }
        }
        String readContentType() {
            int first = read();
            if (first <= 31) {
                int length = first == 31 ? readUintvar() : first;
                int end = p + length;
                if (length <= 0 || end > data.length) throw new IllegalArgumentException("Invalid MMS content type");
                int media = read();
                String type = media >= 0x80 ? wellKnownMediaType(media) : new String(new byte[]{(byte) media}, StandardCharsets.US_ASCII);
                p = end;
                return type;
            }
            if (first >= 0x80) return wellKnownMediaType(first);
            p--;
            return readText().toLowerCase(Locale.ROOT);
        }
        String readPartContentType(int end) {
            int first = read();
            if (first >= 0x80) return wellKnownMediaType(first);
            p--;
            String value = readText();
            return value.toLowerCase(Locale.ROOT);
        }
        String readPartName(int end) {
            String result = "";
            while (p < end) {
                int h = read();
                if (h == 0x85 || h == 0x8e || h == 0x97) result = readText();
                else if (h < 128) readText();
                else if (h == 0x98) result = readText();
                else if (h == 0x99) skipValueLength();
                else throw new IllegalArgumentException("Unsupported MMS part header");
            }
            return result;
        }
    }

    private static String wellKnownMediaType(int value) {
        switch (value) {
            case 0x83: return "text/plain";
            case 0x9e: return "image/jpeg";
            case 0x9f: return "image/gif";
            case 0x9d: return "image/png";
            case 0xA3: return "application/vnd.wap.multipart.mixed";
            case 0xA4: return "application/vnd.wap.multipart.form-data";
            case 0xA5: return "application/vnd.wap.multipart.byteranges";
            case 0xA6: return "application/vnd.wap.multipart.alternative";
            default: return "application/octet-stream";
        }
    }
}
