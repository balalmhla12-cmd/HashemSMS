package com.hashem.sms;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.Collections;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class DefaultSimPreferenceStoreTest {
    private SharedPreferences preferences;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication();
        preferences = context.getSharedPreferences("default_sim_preference_test", Context.MODE_PRIVATE);
        preferences.edit().clear().commit();
    }

    @Test
    public void noSavedDefaultReturnsNone() {
        assertEquals(DefaultSimPreferenceStore.State.NONE,
                DefaultSimPreferenceStore.resolve(preferences, Collections.singleton(7)).state);
    }

    @Test
    public void explicitSystemDefaultIsRestoredWithoutSubscriptionLookup() {
        DefaultSimPreferenceStore.save(preferences, -1);
        assertEquals(DefaultSimPreferenceStore.State.SYSTEM_DEFAULT,
                DefaultSimPreferenceStore.resolve(preferences, null).state);
    }

    @Test
    public void availableSavedSubscriptionIsRestored() {
        DefaultSimPreferenceStore.save(preferences, 19);
        DefaultSimPreferenceStore.Result result =
                DefaultSimPreferenceStore.resolve(preferences, Collections.singleton(19));
        assertEquals(DefaultSimPreferenceStore.State.AVAILABLE, result.state);
        assertEquals(19, result.subscriptionId);
    }

    @Test
    public void unavailableSubscriptionListPreservesPreference() {
        DefaultSimPreferenceStore.save(preferences, 19);
        assertEquals(DefaultSimPreferenceStore.State.UNVERIFIED,
                DefaultSimPreferenceStore.resolve(preferences, null).state);
        assertTrue(preferences.contains(DefaultSimPreferenceStore.KEY));
        assertEquals(19, preferences.getInt(DefaultSimPreferenceStore.KEY, -1));
    }

    @Test
    public void missingSavedSubscriptionIsRemoved() {
        DefaultSimPreferenceStore.save(preferences, 19);
        assertEquals(DefaultSimPreferenceStore.State.REMOVED,
                DefaultSimPreferenceStore.resolve(preferences, Collections.singleton(20)).state);
        assertFalse(preferences.contains(DefaultSimPreferenceStore.KEY));
    }
}
