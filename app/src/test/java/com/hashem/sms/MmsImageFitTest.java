package com.hashem.sms;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class MmsImageFitTest {
    /** Fake encoder whose output size is dimension * quality bytes. */
    private static final class FakeEncoder implements MmsImageFit.Encoder {
        final List<int[]> calls = new ArrayList<>();

        @Override
        public byte[] encode(int maxDimension, int quality) {
            calls.add(new int[]{maxDimension, quality});
            return new byte[maxDimension * quality];
        }
    }

    @Test
    public void returnsFirstAttemptWhenItAlreadyFits() throws Exception {
        FakeEncoder encoder = new FakeEncoder();
        MmsImageFit.Result result = MmsImageFit.fit(encoder, 4000, 1_000_000L);
        assertEquals(1600, result.maxDimension);
        assertEquals(85, result.quality);
        assertEquals(1, encoder.calls.size());
    }

    @Test
    public void lowersQualityBeforeDimension() throws Exception {
        FakeEncoder encoder = new FakeEncoder();
        MmsImageFit.Result result = MmsImageFit.fit(encoder, 4000, 100_000L);
        assertEquals(1600, result.maxDimension);
        assertEquals(55, result.quality);
        assertEquals(4, encoder.calls.size());
    }

    @Test
    public void lowersDimensionWhenQualityIsExhausted() throws Exception {
        FakeEncoder encoder = new FakeEncoder();
        MmsImageFit.Result result = MmsImageFit.fit(encoder, 4000, 40_000L);
        assertEquals(1024, result.maxDimension);
        assertEquals(35, result.quality);
    }

    @Test
    public void neverUpscalesSmallImages() throws Exception {
        FakeEncoder encoder = new FakeEncoder();
        try {
            MmsImageFit.fit(encoder, 700, 1L);
            fail("Expected IOException");
        } catch (IOException expected) {
            // every attempt was too large for a 1-byte budget
        }
        for (int[] call : encoder.calls) {
            assertTrue("dimension " + call[0] + " exceeds original", call[0] <= 700);
        }
        assertEquals(700, encoder.calls.get(0)[0]);
        assertEquals(480, encoder.calls.get(encoder.calls.size() - 1)[0]);
    }

    @Test(expected = IOException.class)
    public void failsWhenNothingFitsTheBudget() throws Exception {
        MmsImageFit.fit(new FakeEncoder(), 4000, 1_000L);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNonPositiveBudget() throws Exception {
        MmsImageFit.fit(new FakeEncoder(), 4000, 0L);
    }

    @Test
    public void budgetUsesDefaultWhenCarrierLimitIsUnknown() {
        assertEquals(300L * 1024L - 10L * 1024L, MmsImageFit.budgetFor(0L));
        assertEquals(300L * 1024L - 10L * 1024L, MmsImageFit.budgetFor(-1L));
    }

    @Test
    public void budgetSubtractsHeadroomAndHonorsMinimum() {
        assertEquals(1_000_000L - 10L * 1024L, MmsImageFit.budgetFor(1_000_000L));
        assertEquals(MmsImageFit.MIN_BUDGET_BYTES, MmsImageFit.budgetFor(5_000L));
    }

    @Test
    public void sampleSizeKeepsLongSideAtOrAboveTarget() {
        assertEquals(1, MmsImageFit.sampleSizeFor(1000, 800, 1600));
        assertEquals(1, MmsImageFit.sampleSizeFor(3199, 2000, 1600));
        assertEquals(2, MmsImageFit.sampleSizeFor(4000, 3000, 1600));
        assertEquals(4, MmsImageFit.sampleSizeFor(12000, 9000, 1600));
        assertEquals(1, MmsImageFit.sampleSizeFor(0, 0, 1600));
    }

    @Test
    public void scaledDimensionsKeepAspectRatioAndNeverUpscale() {
        assertArrayEquals(new int[]{1600, 1200}, MmsImageFit.scaledDimensions(4000, 3000, 1600));
        assertArrayEquals(new int[]{1200, 1600}, MmsImageFit.scaledDimensions(3000, 4000, 1600));
        assertArrayEquals(new int[]{800, 600}, MmsImageFit.scaledDimensions(800, 600, 1600));
        assertArrayEquals(new int[]{480, 1}, MmsImageFit.scaledDimensions(10000, 20, 480));
    }

    @Test
    public void jpegFileNameIsAcceptedByPduComposer() {
        String name = MmsImageFit.jpegFileName(42L, 1);
        assertEquals("IMG-42-1.jpg", name);
        new MmsPduComposer.ImagePart(new byte[]{1}, "image/jpeg", name);
    }
}
