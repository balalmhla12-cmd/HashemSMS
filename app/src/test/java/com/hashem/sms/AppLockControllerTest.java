package com.hashem.sms;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public final class AppLockControllerTest {
    private Context context;

    @Before public void setUp() {
        context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences("hashem_ui", Context.MODE_PRIVATE).edit().clear().commit();
        AppLockController.setEnabled(context, false);
    }

    @After public void tearDown() {
        AppLockController.setEnabled(context, false);
        context.getSharedPreferences("hashem_ui", Context.MODE_PRIVATE).edit().clear().commit();
    }

    @Test public void appLockIsDisabledByDefaultAndCanBeEnabled() {
        assertFalse(AppLockController.isEnabled(context));
        AppLockController.setEnabled(context, true);
        assertTrue(AppLockController.isEnabled(context));
        AppLockController.setEnabled(context, false);
        assertFalse(AppLockController.isEnabled(context));
    }

    @Test public void lockTimeoutDefaultsToImmediateAndPersistsSupportedChoices() {
        assertEquals(0, AppLockController.getTimeoutMinutes(context));
        AppLockController.setTimeoutMinutes(context, 1);
        assertEquals(1, AppLockController.getTimeoutMinutes(context));
        AppLockController.setTimeoutMinutes(context, 5);
        assertEquals(5, AppLockController.getTimeoutMinutes(context));
        AppLockController.setTimeoutMinutes(context, 9);
        assertEquals(0, AppLockController.getTimeoutMinutes(context));
    }
}
