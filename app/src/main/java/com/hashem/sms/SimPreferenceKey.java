package com.hashem.sms;

/** Stable per-recipient preference key for the selected SMS subscription. */
final class SimPreferenceKey {
    private SimPreferenceKey() {}

    static String forAddress(String address) {
        String raw = address == null ? "" : address.trim();
        String normalized = android.telephony.PhoneNumberUtils.normalizeNumber(raw);
        if (normalized == null || normalized.isEmpty()) {
            normalized = raw.replaceAll("[^0-9]", "");
        }
        return "preferred_sms_subscription_" + normalized;
    }
}
