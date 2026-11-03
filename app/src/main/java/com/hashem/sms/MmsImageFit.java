package com.hashem.sms;

import java.io.IOException;

/**
 * Pure-Java planning logic for fitting an image under an MMS size budget.
 * Contains no Android classes so it can be unit-tested with plain JUnit.
 */
final class MmsImageFit {
    /** Android's default maxMessageSize when the carrier does not provide one. */
    static final long DEFAULT_MAX_MESSAGE_BYTES = 300L * 1024L;
    /** Reserved for the PDU headers, part headers and any text part. */
    static final long HEADROOM_BYTES = 10L * 1024L;
    static final long MIN_BUDGET_BYTES = 30L * 1024L;
    /** Longest-side targets, tried from largest to smallest. */
    static final int[] DIMENSIONS = {1600, 1280, 1024, 800, 640, 480};
    /** JPEG qualities, tried from best to worst for each dimension. */
    static final int[] QUALITIES = {85, 75, 65, 55, 45, 35};

    private MmsImageFit() {
    }

    /** Encodes the image at a given longest side and JPEG quality. */
    interface Encoder {
        byte[] encode(int maxDimension, int quality) throws IOException;
    }

    static final class Result {
        final byte[] data;
        final int maxDimension;
        final int quality;

        Result(byte[] data, int maxDimension, int quality) {
            this.data = data;
            this.maxDimension = maxDimension;
            this.quality = quality;
        }
    }

    /**
     * Returns the largest/best attempt that fits in maxBytes. Quality is lowered
     * first at each dimension; dimension is lowered only when quality is exhausted.
     * Dimensions larger than the original image are never used (no upscaling).
     */
    static Result fit(Encoder encoder, int originalLongSide, long maxBytes) throws IOException {
        if (encoder == null) {
            throw new IllegalArgumentException("Encoder is required");
        }
        if (originalLongSide <= 0 || maxBytes <= 0) {
            throw new IllegalArgumentException("Image size and byte budget must be positive");
        }
        int previous = -1;
        for (int target : DIMENSIONS) {
            int effective = Math.min(target, originalLongSide);
            if (effective == previous) {
                continue;
            }
            previous = effective;
            for (int quality : QUALITIES) {
                byte[] data = encoder.encode(effective, quality);
                if (data != null && data.length > 0 && data.length <= maxBytes) {
                    return new Result(data, effective, quality);
                }
            }
        }
        throw new IOException("Image cannot be compressed under " + maxBytes + " bytes");
    }

    /** Byte budget for the image part, given the carrier limit (0 or less means unknown). */
    static long budgetFor(long carrierMaxMessageBytes) {
        long limit = carrierMaxMessageBytes > 0 ? carrierMaxMessageBytes : DEFAULT_MAX_MESSAGE_BYTES;
        return Math.max(MIN_BUDGET_BYTES, limit - HEADROOM_BYTES);
    }

    /** Largest power-of-two sample size that keeps the longest side at or above target. */
    static int sampleSizeFor(int width, int height, int targetLongSide) {
        int longSide = Math.max(width, height);
        int sample = 1;
        if (longSide <= 0 || targetLongSide <= 0) {
            return sample;
        }
        while (longSide / (sample * 2) >= targetLongSide) {
            sample *= 2;
        }
        return sample;
    }

    /** Scales so the longest side is at most maxDimension, keeping the aspect ratio. */
    static int[] scaledDimensions(int width, int height, int maxDimension) {
        int longSide = Math.max(width, height);
        if (longSide <= maxDimension || longSide <= 0 || maxDimension <= 0) {
            return new int[]{width, height};
        }
        double ratio = (double) maxDimension / (double) longSide;
        int w = Math.max(1, (int) Math.round(width * ratio));
        int h = Math.max(1, (int) Math.round(height * ratio));
        return new int[]{w, h};
    }

    /** ASCII-only name accepted by MmsPduComposer. */
    static String jpegFileName(long messageId, int index) {
        return "IMG-" + messageId + "-" + index + ".jpg";
    }
}
