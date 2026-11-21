package com.hashem.sms;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import java.util.concurrent.atomic.AtomicBoolean;

final class AppLockController {
    private static final String PREFS = "hashem_ui";
    private static final String KEY_ENABLED = "app_lock_enabled";
    private static final String KEY_TIMEOUT_MINUTES = "app_lock_timeout_minutes";
    private static final AtomicBoolean LAUNCHING = new AtomicBoolean(false);
    private static volatile boolean unlockedForSession;
    private static volatile long backgroundedAtElapsedRealtime;

    private AppLockController() {}

    static boolean isEnabled(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, false);
    }

    static void setEnabled(Context context, boolean enabled) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putBoolean(KEY_ENABLED, enabled).apply();
        if (!enabled) unlockForSession();
    }

    static int getTimeoutMinutes(Context context) {
        int value = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getInt(KEY_TIMEOUT_MINUTES, 0);
        return value == 1 || value == 5 ? value : 0;
    }

    static void setTimeoutMinutes(Context context, int minutes) {
        int safeValue = minutes == 1 || minutes == 5 ? minutes : 0;
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putInt(KEY_TIMEOUT_MINUTES, safeValue).apply();
    }

    static void enforce(Activity activity) {
        if (activity == null || activity.isFinishing()) return;
        boolean enabled = isEnabled(activity);
        if (enabled) {
            activity.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);
        } else {
            activity.getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);
        }
        if (enabled && unlockedForSession && backgroundedAtElapsedRealtime > 0L) {
            long elapsed = android.os.SystemClock.elapsedRealtime() - backgroundedAtElapsedRealtime;
            long timeout = getTimeoutMinutes(activity) * 60_000L;
            if (timeout == 0L || elapsed >= timeout) {
                unlockedForSession = false;
            }
            backgroundedAtElapsedRealtime = 0L;
        }
        if (!enabled || activity instanceof AppLockActivity || unlockedForSession
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
        backgroundedAtElapsedRealtime = 0L;
        LAUNCHING.set(false);
    }

    static void unlockForSession() {
        unlockedForSession = true;
        backgroundedAtElapsedRealtime = 0L;
        LAUNCHING.set(false);
    }

    static void lockForBackground(Context context) {
        if (isEnabled(context) && unlockedForSession) {
            backgroundedAtElapsedRealtime = android.os.SystemClock.elapsedRealtime();
            if (getTimeoutMinutes(context) == 0) {
                unlockedForSession = false;
                backgroundedAtElapsedRealtime = 0L;
            }
            LAUNCHING.set(false);
        }
    }
}
