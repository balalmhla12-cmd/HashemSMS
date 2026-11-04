package com.hashem.sms;

import static org.junit.Assert.assertEquals;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class SimPreferenceKeyTest {
    @Test
    public void equivalentPhoneNumberFormattingUsesSamePreferenceKey() {
        assertEquals(
                SimPreferenceKey.forAddress("+1 (415) 555-0123"),
                SimPreferenceKey.forAddress("+1-415-555-0123"));
    }

    @Test
    public void nullAndEmptyAddressDoNotThrow() {
        assertEquals(
                SimPreferenceKey.forAddress(null),
                SimPreferenceKey.forAddress(""));
    }
}
