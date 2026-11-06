package com.hashem.sms;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Stores a device-wide default SIM; per-contact preferences always take precedence. */
final class DefaultSimPreferenceStore {
    static final String KEY = "default_sms_subscription_id";

    enum State { NONE, SYSTEM_DEFAULT, AVAILABLE, UNVERIFIED, REMOVED }

    static final class Result {
        final State state;
        final int subscriptionId;
        Result(State state, int subscriptionId) {
            this.state = state;
            this.subscriptionId = subscriptionId;
        }
    }

    private DefaultSimPreferenceStore() {}

    static void save(SharedPreferences preferences, int subscriptionId) {
        preferences.edit().putInt(KEY, subscriptionId).apply();
    }

    static Result resolve(Context context, SharedPreferences preferences) {
        if (!preferences.contains(KEY)) return new Result(State.NONE, -1);
        int savedId = preferences.getInt(KEY, -1);
        if (savedId < 0) return new Result(State.SYSTEM_DEFAULT, -1);
        if (context == null || context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE)
                != PackageManager.PERMISSION_GRANTED) {
            return new Result(State.UNVERIFIED, -1);
        }
        try {
            List<SubscriptionInfo> active = SubscriptionManager.from(context).getActiveSubscriptionInfoList();
            if (active == null) return new Result(State.UNVERIFIED, -1);
            Set<Integer> ids = new HashSet<>();
            for (SubscriptionInfo info : active) ids.add(info.getSubscriptionId());
            return resolve(preferences, ids);
        } catch (SecurityException | RuntimeException e) {
            android.util.Log.w("HashemSMS", "Unable to validate the saved default SIM", e);
            return new Result(State.UNVERIFIED, -1);
        }
    }

    static Result resolve(SharedPreferences preferences, Set<Integer> activeIds) {
        if (!preferences.contains(KEY)) return new Result(State.NONE, -1);
        int savedId = preferences.getInt(KEY, -1);
        if (savedId < 0) return new Result(State.SYSTEM_DEFAULT, -1);
        if (activeIds == null) return new Result(State.UNVERIFIED, -1);
        if (activeIds.contains(savedId)) return new Result(State.AVAILABLE, savedId);
        preferences.edit().remove(KEY).apply();
        return new Result(State.REMOVED, -1);
    }
}
