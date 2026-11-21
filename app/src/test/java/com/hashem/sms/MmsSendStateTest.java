package com.hashem.sms;
import static org.junit.Assert.*;import org.junit.Test;
public class MmsSendStateTest {
 @Test public void successIsNotRetryable(){assertEquals("تم الإرسال",MmsSendState.statusFor(true));assertFalse(MmsSendState.canRetry(MmsSendState.statusFor(true)));}
 @Test public void failureCanBeRetried(){assertEquals("فشل",MmsSendState.statusFor(false));assertTrue(MmsSendState.canRetry(MmsSendState.statusFor(false)));}
 @Test public void recognizesLegacyFailureLabels(){assertTrue(MmsSendState.canRetry("لم يتم الإرسال"));assertFalse(MmsSendState.canRetry("جارٍ الإرسال"));assertFalse(MmsSendState.canRetry(null));}
}
