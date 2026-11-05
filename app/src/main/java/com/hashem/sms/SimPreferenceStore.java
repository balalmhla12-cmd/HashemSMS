package com.hashem.sms;

import android.content.SharedPreferences;
import java.util.Set;

/** Persists and validates a conversation's preferred SMS subscription. */
final class SimPreferenceStore {
    enum State { NONE, AVAILABLE, REMOVED }

    static final class RestoreResult {
        final State state;
        final int subscriptionId;

        private RestoreResult(State state, int subscriptionId) {
            this.state = state;
            this.subscriptionId = subscriptionId;
        }

        boolean isAvailable() { return state == State.AVAILABLE; }
        boolean wasRemoved() { return state == State.REMOVED; }
    }

    private final SharedPreferences preferences;

    SimPreferenceStore(SharedPreferences preferences) {
        this.preferences = preferences;
    }

    RestoreResult resolve(String key, Set<Integer> activeSubscriptionIds) {
        if (!preferences.contains(key)) {
            return new RestoreResult(State.NONE, -1);
        }
        int savedId = preferences.getInt(key, -1);
        if (savedId >= 0 && activeSubscriptionIds != null
                && activeSubscriptionIds.contains(savedId)) {
            return new RestoreResult(State.AVAILABLE, savedId);
        }
        preferences.edit().remove(key).apply();
        return new RestoreResult(State.REMOVED, -1);
    }

    SubscriptionChoiceCallback remember(String key, SubscriptionChoiceCallback callback) {
        return new SubscriptionChoiceCallback() {
            @Override public void onSelected(int subscriptionId) {
                if (subscriptionId >= 0) {
                    preferences.edit().putInt(key, subscriptionId).apply();
                }
                callback.onSelected(subscriptionId);
            }

            @Override public void onCancelled() {
                callback.onCancelled();
            }
        };
    }
}
