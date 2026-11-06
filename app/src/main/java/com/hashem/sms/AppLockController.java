package com.hashem.sms;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import java.util.concurrent.atomic.AtomicBoolean;

final class AppLockController {
    private static final String PREFS = "hashem_ui";
    private static final String KEY_ENABLED = "app_lock_enabled";
    private static final AtomicBoolean LAUNCHING = new AtomicBoolean(false);
    private static volatile boolean unlockedForSession;

    private AppLockController() {}

    static boolean isEnabled(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, false);
    }

    static void setEnabled(Context context, boolean enabled) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putBoolean(KEY_ENABLED, enabled).apply();
        if (!enabled) unlockForSession();
    }

    static void enforce(Activity activity) {
        if (activity == null || activity.isFinishing() || activity instanceof AppLockActivity
                || !isEnabled(activity) || unlockedForSession
                || !LAUNCHING.compareAndSet(false, true)) return;
        try {
            activity.startActivity(new Intent(activity, AppLockActivity.class));
        } catch (RuntimeException error) {
            LAUNCHING.set(false);
            android.util.Log.e("HashemSMS", "Unable to show app lock", error);
        }
    }

    static void lockNow(Activity activity) {
        unlockedForSession = false;
        LAUNCHING.set(false);
        enforce(activity);
    }

    static void authenticated() {
        unlockedForSession = true;
        LAUNCHING.set(false);
    }

    static void unlockForSession() {
        unlockedForSession = true;
        LAUNCHING.set(false);
    }

    static void lockForBackground(Context context) {
        if (isEnabled(context)) {
            unlockedForSession = false;
            LAUNCHING.set(false);
        }
    }
}