package com.hashem.sms;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class MessageStatusIndicatorTest {
    @Test
    public void sentStateUsesSingleCheckUntilDeliveryReportArrives() {
        assertEquals("✓", MessageStatusIndicator.forStatus("تم الإرسال"));
        assertEquals("✓", MessageStatusIndicator.forStatus("sent"));
        assertEquals("✓", MessageStatusIndicator.forStatus("تم"));
    }

    @Test
    public void deliveredStateUsesDoubleCheckOnlyForExplicitDeliveryStatus() {
        assertEquals("✓✓", MessageStatusIndicator.forStatus("تم التسليم"));
        assertEquals("✓✓", MessageStatusIndicator.forStatus("delivered"));
    }

    @Test
    public void failuresUseFailureMarkerForSendAndDeliveryFailures() {
        assertEquals("!", MessageStatusIndicator.forStatus("لم يتم الإرسال"));
        assertEquals("!", MessageStatusIndicator.forStatus("لم يتم التسليم"));
        assertEquals("!", MessageStatusIndicator.forStatus("failed"));
    }

    @Test
    public void pendingStatesUseClockMarker() {
        assertEquals("◷", MessageStatusIndicator.forStatus("جارٍ الإرسال"));
        assertEquals("◷", MessageStatusIndicator.forStatus("sending"));
        assertEquals("◷", MessageStatusIndicator.forStatus("queued"));
        assertEquals("◷", MessageStatusIndicator.forStatus("outbox"));
    }

    @Test
    public void unknownOrMissingStateHasNoMisleadingMarker() {
        assertEquals("", MessageStatusIndicator.forStatus(null));
        assertEquals("", MessageStatusIndicator.forStatus(""));
        assertEquals("", MessageStatusIndicator.forStatus("received"));
    }
}
