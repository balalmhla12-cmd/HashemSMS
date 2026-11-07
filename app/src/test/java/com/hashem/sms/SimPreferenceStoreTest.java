package com.hashem.sms;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class SimPreferenceStoreTest {
    private SharedPreferences preferences;
    private static final String KEY = "preferred_sms_subscription_test";

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication();
        preferences = context.getSharedPreferences("sim_preference_store_test", Context.MODE_PRIVATE);
        preferences.edit().clear().commit();
    }

    @Test
    public void selectedSubscriptionSurvivesStoreRecreation() {
        SimPreferenceStore firstInstance = new SimPreferenceStore(preferences);
        firstInstance.remember(KEY, new NoOpCallback()).onSelected(17);

        SimPreferenceStore recreatedInstance = new SimPreferenceStore(preferences);
        SimPreferenceStore.RestoreResult restored =
                recreatedInstance.resolve(KEY, Collections.singleton(17));

        assertTrue(restored.isAvailable());
        assertEquals(17, restored.subscriptionId);
    }

    @Test
    public void cancellingSelectionDoesNotInvokeSendCallbackOrSavePreference() {
        AtomicInteger sendCount = new AtomicInteger();
        SubscriptionChoiceCallback sendCallback = new SubscriptionChoiceCallback() {
            @Override public void onSelected(int subscriptionId) {
                sendCount.incrementAndGet();
            }
            @Override public void onCancelled() { }
        };

        new SimPreferenceStore(preferences).remember(KEY, sendCallback).onCancelled();

        assertEquals(0, sendCount.get());
        assertFalse(preferences.contains(KEY));
    }

    @Test
    public void unavailableSubscriptionListPreservesSavedPreference() {
        new SimPreferenceStore(preferences).remember(KEY, new NoOpCallback()).onSelected(31);

        SimPreferenceStore.RestoreResult restored =
                new SimPreferenceStore(preferences).resolve(KEY, null);

        assertEquals(SimPreferenceStore.State.UNVERIFIED, restored.state);
        assertTrue(preferences.contains(KEY));
        assertEquals(31, preferences.getInt(KEY, -1));
    }

    @Test
    public void missingSavedSubscriptionRemainsPendingUntilUserChoosesAnother() {
        new SimPreferenceStore(preferences).remember(KEY, new NoOpCallback()).onSelected(23);

        SimPreferenceStore.RestoreResult restored =
                new SimPreferenceStore(preferences).resolve(KEY, Collections.singleton(24));

        assertEquals(SimPreferenceStore.State.REMOVED, restored.state);
        assertTrue(restored.requiresExplicitSelection());
        assertEquals(23, restored.subscriptionId);
        assertTrue(preferences.contains(KEY));
        assertEquals(23, preferences.getInt(KEY, -2));
    }

    @Test
    public void removedOrUnverifiedSavedSubscriptionRequiresExplicitSelection() {
        SimPreferenceStore store = new SimPreferenceStore(preferences);
        store.remember(KEY, new NoOpCallback()).onSelected(23);
        SimPreferenceStore.RestoreResult removed =
                store.resolve(KEY, Collections.singleton(24));
        assertTrue(removed.requiresExplicitSelection());

        preferences.edit().putInt(KEY, 31).commit();
        SimPreferenceStore.RestoreResult unverified = store.resolve(KEY, null);
        assertTrue(unverified.requiresExplicitSelection());

        SimPreferenceStore.RestoreResult absent =
                store.resolve("no_saved_subscription", Collections.<Integer>emptySet());
        assertFalse(absent.requiresExplicitSelection());
    }

    @Test
    public void explicitSystemDefaultSelectionIsRememberedPerContact() {
        SimPreferenceStore store = new SimPreferenceStore(preferences);
        store.remember(KEY, new NoOpCallback()).onSelected(-1);

        SimPreferenceStore.RestoreResult restored = store.resolve(KEY, null);

        assertEquals(SimPreferenceStore.State.SYSTEM_DEFAULT, restored.state);
        assertEquals(-1, restored.subscriptionId);
        assertTrue(preferences.contains(KEY));
    }

    @Test
    public void deliveryReportsDefaultToEnabledAndAreIndependentPerSubscription() {
        SimPreferenceStore store = new SimPreferenceStore(preferences);

        assertTrue(store.deliveryReportsEnabled(11));
        assertTrue(store.deliveryReportsEnabled(22));
        store.setDeliveryReportsEnabled(11, false);

        assertFalse(store.deliveryReportsEnabled(11));
        assertTrue(store.deliveryReportsEnabled(22));
        assertTrue(store.deliveryReportsEnabled(-1));
    }

    @Test
    public void systemDefaultDeliveryReportPreferenceIsIndependentFromPhysicalSims() {
        SimPreferenceStore store = new SimPreferenceStore(preferences);
        store.setDeliveryReportsEnabled(-1, false);
        store.setDeliveryReportsEnabled(11, true);

        assertFalse(store.deliveryReportsEnabled(-1));
        assertTrue(store.deliveryReportsEnabled(11));
        assertTrue(store.deliveryReportsEnabled(22));
    }

    private static final class NoOpCallback implements SubscriptionChoiceCallback {
        @Override public void onSelected(int subscriptionId) { }
        @Override public void onCancelled() { }
    }
}
