package com.hashem.sms;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import androidx.exifinterface.media.ExifInterface;
import android.net.Uri;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Decodes an image from a content Uri, fixes its EXIF orientation, flattens
 * transparency onto white and returns JPEG bytes that fit the MMS budget.
 * Call from a background thread only.
 */
final class MmsImageCompressor {
    private MmsImageCompressor() {
    }

    static MmsImageFit.Result compress(Context context, Uri source, long maxBytes) throws IOException {
        if (context == null || source == null) {
            throw new IllegalArgumentException("Context and image Uri are required");
        }
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream in = context.getContentResolver().openInputStream(source)) {
            if (in == null) {
                throw new IOException("Cannot open the selected image");
            }
            BitmapFactory.decodeStream(in, null, bounds);
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw new IOException("The selected file is not a readable image");
        }
        int orientation = readOrientation(context, source);

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = MmsImageFit.sampleSizeFor(
                bounds.outWidth, bounds.outHeight, MmsImageFit.DIMENSIONS[0]);
        Bitmap decoded;
        try (InputStream in = context.getContentResolver().openInputStream(source)) {
            if (in == null) {
                throw new IOException("Cannot open the selected image");
            }
            try {
                decoded = BitmapFactory.decodeStream(in, null, options);
            } catch (OutOfMemoryError e) {
                throw new IOException("Not enough memory to read the image", e);
            }
        }
        if (decoded == null) {
            throw new IOException("The selected image could not be decoded");
        }

        BitmapEncoder encoder = null;
        try {
            Bitmap oriented = applyOrientation(decoded, orientation);
            if (oriented != decoded) {
                decoded.recycle();
            }
            Bitmap flat = flattenOnWhite(oriented);
            if (flat != oriented) {
                oriented.recycle();
            }
            encoder = new BitmapEncoder(flat);
            int longSide = Math.max(flat.getWidth(), flat.getHeight());
            return MmsImageFit.fit(encoder, longSide, maxBytes);
        } catch (OutOfMemoryError e) {
            throw new IOException("Not enough memory to compress the image", e);
        } finally {
            if (encoder != null) {
                encoder.release();
            }
        }
    }

    private static int readOrientation(Context context, Uri source) {
        try (InputStream in = context.getContentResolver().openInputStream(source)) {
            if (in == null) {
                return ExifInterface.ORIENTATION_NORMAL;
            }
            return new ExifInterface(in).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
        } catch (IOException | RuntimeException e) {
            return ExifInterface.ORIENTATION_NORMAL;
        }
    }

    private static Bitmap applyOrientation(Bitmap bitmap, int orientation) {
        Matrix matrix = new Matrix();
        switch (orientation) {
            case ExifInterface.ORIENTATION_ROTATE_90:
                matrix.postRotate(90f);
                break;
            case ExifInterface.ORIENTATION_ROTATE_180:
                matrix.postRotate(180f);
                break;
            case ExifInterface.ORIENTATION_ROTATE_270:
                matrix.postRotate(270f);
                break;
            case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:
                matrix.postScale(-1f, 1f);
                break;
            case ExifInterface.ORIENTATION_FLIP_VERTICAL:
                matrix.postScale(1f, -1f);
                break;
            case ExifInterface.ORIENTATION_TRANSPOSE:
                matrix.postRotate(90f);
                matrix.postScale(-1f, 1f);
                break;
            case ExifInterface.ORIENTATION_TRANSVERSE:
                matrix.postRotate(270f);
                matrix.postScale(-1f, 1f);
                break;
            default:
                return bitmap;
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    }

    /** JPEG has no alpha; transparent PNG pixels would otherwise turn black. */
    private static Bitmap flattenOnWhite(Bitmap bitmap) {
        if (!bitmap.hasAlpha()) {
            return bitmap;
        }
        Bitmap flat = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(flat);
        canvas.drawColor(Color.WHITE);
        canvas.drawBitmap(bitmap, 0f, 0f, null);
        return flat;
    }

    private static final class BitmapEncoder implements MmsImageFit.Encoder {
        private final Bitmap source;
        private Bitmap scaled;
        private int scaledDimension = -1;

        BitmapEncoder(Bitmap source) {
            this.source = source;
        }

        @Override
        public byte[] encode(int maxDimension, int quality) throws IOException {
            Bitmap target = scaledFor(maxDimension);
            ByteArrayOutputStream out = new ByteArrayOutputStream(64 * 1024);
            if (!target.compress(Bitmap.CompressFormat.JPEG, quality, out)) {
                throw new IOException("JPEG encoding failed");
            }
            return out.toByteArray();
        }

        private Bitmap scaledFor(int maxDimension) {
            if (scaled != null && scaledDimension == maxDimension) {
                return scaled;
            }
            releaseScaled();
            int[] size = MmsImageFit.scaledDimensions(source.getWidth(), source.getHeight(), maxDimension);
            if (size[0] == source.getWidth() && size[1] == source.getHeight()) {
                scaled = source;
            } else {
                scaled = Bitmap.createScaledBitmap(source, size[0], size[1], true);
            }
            scaledDimension = maxDimension;
            return scaled;
        }

        private void releaseScaled() {
            if (scaled != null && scaled != source) {
                scaled.recycle();
            }
            scaled = null;
            scaledDimension = -1;
        }

        void release() {
            releaseScaled();
            source.recycle();
        }
    }
}
