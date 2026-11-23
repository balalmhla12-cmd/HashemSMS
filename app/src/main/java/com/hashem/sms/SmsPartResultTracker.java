package com.hashem.sms;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

/** Tracks distinct multipart callback indexes for one local message. */
final class SmsPartResultTracker {
    static final class Result {
        final boolean accepted;
        final boolean duplicate;
        final boolean complete;
        final boolean failed;

        Result(boolean accepted, boolean duplicate, boolean complete, boolean failed) {
            this.accepted = accepted;
            this.duplicate = duplicate;
            this.complete = complete;
            this.failed = failed;
        }
    }

    private SmsPartResultTracker() {}

    // Preserve the four synchronous persistence points formerly in the receivers:
    // sent progress/cleanup and delivery progress/cleanup.
    private static boolean commitSentProgress(SharedPreferences.Editor editor) {
        return editor.commit();
    }

    private static boolean commitSentCompletion(SharedPreferences.Editor editor,
                                                String completeKey) {
        return editor.putBoolean(completeKey, true).commit();
    }

    private static boolean commitDeliveryProgress(SharedPreferences.Editor editor) {
        return editor.commit();
    }

    private static boolean commitDeliveryCompletion(SharedPreferences.Editor editor,
                                                    String completeKey) {
        return editor.putBoolean(completeKey, true).commit();
    }

    static void reset(Context context, long messageId) {
        if (messageId <= 0) return;
        synchronized (SmsPartResultTracker.class) {
            SharedPreferences.Editor editor = context.getSharedPreferences(
                    "hashem_sms_parts", Context.MODE_PRIVATE).edit();
            for (String direction : new String[]{"sent", "delivery"}) {
                String prefix = direction + "_" + messageId;
                editor.remove(prefix + "_count")
                        .remove(prefix + "_parts")
                        .remove(prefix + "_failed")
                        .remove(prefix + "_complete");
            }
            editor.apply();
        }
    }

    static Result record(Context context, String direction, long messageId,
                         int partIndex, int partCount, boolean success) {
        if (messageId <= 0 || partCount < 1 || partIndex < 0 || partIndex >= partCount) {
            return new Result(false, false, false, false);
        }

        synchronized (SmsPartResultTracker.class) {
            SharedPreferences prefs = context.getSharedPreferences(
                    "hashem_sms_parts", Context.MODE_PRIVATE);
            String prefix = direction + "_" + messageId;
            String countKey = prefix + "_count";
            String partsKey = prefix + "_parts";
            String failedKey = prefix + "_failed";
            String completeKey = prefix + "_complete";

            boolean failed = prefs.getBoolean(failedKey, false);
            if (prefs.getBoolean(completeKey, false)) {
                return new Result(false, true, true, failed);
            }

            int storedCount = prefs.getInt(countKey, 0);
            if (storedCount != 0 && storedCount != partCount) {
                return new Result(false, false, false, failed);
            }

            Set<String> saved = prefs.getStringSet(partsKey, null);
            Set<String> indexes = saved == null ? new HashSet<>() : new HashSet<>(saved);
            String index = String.valueOf(partIndex);
            if (indexes.contains(index)) {
                return new Result(false, true, false, failed);
            }

            indexes.add(index);
            failed = failed || !success;
            boolean complete = indexes.size() == partCount;

            SharedPreferences.Editor editor = prefs.edit()
                    .putInt(countKey, partCount)
                    .putStringSet(partsKey, indexes)
                    .putBoolean(failedKey, failed);
            if ("sent".equals(direction)) {
                commitSentProgress(editor);
            } else if ("delivery".equals(direction)) {
                commitDeliveryProgress(editor);
            }

            if (complete) {
                SharedPreferences.Editor cleanup = prefs.edit()
                        .remove(countKey)
                        .remove(partsKey)
                        .remove(failedKey);
                if ("sent".equals(direction)) {
                    commitSentCompletion(cleanup, completeKey);
                } else if ("delivery".equals(direction)) {
                    commitDeliveryCompletion(cleanup, completeKey);
                }
            }

            return new Result(true, false, complete, failed);
        }
    }
}
