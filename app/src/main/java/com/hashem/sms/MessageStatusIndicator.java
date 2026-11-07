package com.hashem.sms;

import java.util.Locale;

/** Maps persisted outgoing-message states to compact, truthful status indicators. */
final class MessageStatusIndicator {
    private MessageStatusIndicator() {}

    static String forStatus(String status) {
        if (status == null) return "";
        String value = status.trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty()) return "";

        if (value.contains("fail") || value.contains("فشل") || value.contains("لم يتم")) {
            return "!";
        }
        if (value.equals("delivered") || value.contains("تم التسليم")) {
            return "✓✓";
        }
        if (value.equals("sent") || value.contains("تم الإرسال") || value.equals("تم")) {
            return "✓";
        }
        if (value.contains("sending") || value.contains("queued") || value.contains("pending")
                || value.contains("outbox") || value.contains("جار") || value.contains("قيد")) {
            return "◷";
        }
        return "";
    }
}
