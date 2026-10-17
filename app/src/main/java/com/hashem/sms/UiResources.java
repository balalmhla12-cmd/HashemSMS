package com.hashem.sms;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;
import android.view.View;
import androidx.core.content.ContextCompat;

/** Shared resource access for colors used by programmatically-built views. */
final class UiResources {
    private UiResources() {}

    static int color(Context context, int resourceId) {
        return ContextCompat.getColor(context, resourceId);
    }

    static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    static void configureSystemBars(Activity activity) {
        if (Build.VERSION.SDK_INT != 26) return;
        View decor = activity.getWindow().getDecorView();
        int flags = decor.getSystemUiVisibility();
        boolean night = (activity.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        if (night) {
            flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        } else {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        decor.setSystemUiVisibility(flags);
    }
}
