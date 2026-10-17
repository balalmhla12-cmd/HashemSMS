package com.hashem.sms;

import android.content.Context;
import androidx.core.content.ContextCompat;

/** Shared resource access for colors used by programmatically-built views. */
final class UiResources {
    private UiResources() {}

    static int color(Context context, int resourceId) {
        return ContextCompat.getColor(context, resourceId);
    }
}
