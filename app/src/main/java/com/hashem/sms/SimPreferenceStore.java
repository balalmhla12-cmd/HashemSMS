package com.hashem.sms;

import android.content.SharedPreferences;
import java.util.Set;

/** Persists and validates a conversation's preferred SMS subscription. */
final class SimPreferenceStore {
    static final String PREFERENCES_NAME = "hashem_ui";
    private static final String DELIVERY_REPORT_PREFIX = "delivery_reports_subscription_";
    enum State { NONE, AVAILABLE, SYSTEM_DEFAULT, REMOVED, UNVERIFIED }

    static final class RestoreResult {
        final State state;
        final int subscriptionId;

        private RestoreResult(State state, int subscriptionId) {
            this.state = state;
            this.subscriptionId = subscriptionId;
        }

        boolean isAvailable() { return state == State.AVAILABLE; }
        boolean wasRemoved() { return state == State.REMOVED; }
        boolean requiresExplicitSelection() { return state == State.REMOVED || state == State.UNVERIFIED; }
    }

    private final SharedPreferences preferences;

    static String deliveryReportsKey(int subscriptionId) {
        return DELIVERY_REPORT_PREFIX + subscriptionId;
    }

    boolean deliveryReportsEnabled(int subscriptionId) {
        return preferences.getBoolean(deliveryReportsKey(subscriptionId), true);
    }

    void setDeliveryReportsEnabled(int subscriptionId, boolean enabled) {
        preferences.edit().putBoolean(deliveryReportsKey(subscriptionId), enabled).apply();
    }

    SimPreferenceStore(SharedPreferences preferences) {
        this.preferences = preferences;
    }

    RestoreResult resolve(String key, Set<Integer> activeSubscriptionIds) {
        if (!preferences.contains(key)) return new RestoreResult(State.NONE, -1);
        int savedId = preferences.getInt(key, -2);
        if (savedId == -1) return new RestoreResult(State.SYSTEM_DEFAULT, -1);
        if (savedId < 0) return new RestoreResult(State.REMOVED, savedId);
        if (activeSubscriptionIds == null) return new RestoreResult(State.UNVERIFIED, savedId);
        if (activeSubscriptionIds.contains(savedId)) return new RestoreResult(State.AVAILABLE, savedId);
        // Keep the unavailable ID until the user explicitly chooses another SIM.
        return new RestoreResult(State.REMOVED, savedId);
    }

    SubscriptionChoiceCallback remember(String key, SubscriptionChoiceCallback callback) {
        return new SubscriptionChoiceCallback() {
            @Override public void onSelected(int subscriptionId) {
                preferences.edit().putInt(key, subscriptionId).apply();
                callback.onSelected(subscriptionId);
            }

            @Override public void onCancelled() {
                callback.onCancelled();
            }
        };
    }
}
