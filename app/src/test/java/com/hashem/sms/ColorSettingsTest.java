package com.hashem.sms;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;
import android.graphics.Color;
import org.junit.Test;

public class ColorSettingsTest {
    @Test public void readableTextAlwaysMeetsWcagAaForPresetColors() {
        int[] colors = {0xFF006B91,0xFF007EA8,0xFF00A6A6,0xFF2E7D32,0xFF558B2F,
                0xFF8E24AA,0xFF3949AB,0xFF1E88E5,0xFFE65100,0xFFC62828,0xFF6D4C41,0xFF455A64};
        for (int color : colors) {
            int foreground = ColorSettings.readableTextColor(color);
            assertTrue("Contrast too low for " + Integer.toHexString(color),
                    ColorSettings.contrastRatio(foreground, color) >= 4.5d);
        }
    }

    @Test public void blackAndWhiteContrastAreSymmetric() {
        assertEquals(ColorSettings.contrastRatio(Color.WHITE, Color.BLACK),
                ColorSettings.contrastRatio(Color.BLACK, Color.WHITE), 0.0001d);
    }
}
