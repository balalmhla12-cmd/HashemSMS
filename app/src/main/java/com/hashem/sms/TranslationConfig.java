package com.hashem.sms;

import android.content.SharedPreferences;
import com.google.mlkit.nl.translate.TranslateLanguage;
import com.google.mlkit.nl.translate.TranslateRemoteModel;
import com.google.mlkit.nl.translate.TranslatorOptions;

/** Central language-pair configuration; English -> Arabic remains the only enabled pair for now. */
final class TranslationConfig {
    static final String PREFERENCES_NAME = "translation_settings";
    static final String TARGET_LANGUAGE_KEY = "target_language";
    static final String DEFAULT_TARGET_LANGUAGE = TranslateLanguage.ARABIC;

    private TranslationConfig() {}

    static String sourceLanguage() {
        return TranslateLanguage.ENGLISH;
    }

    static String normalizeTargetLanguage(String languageTag) {
        if (languageTag == null || languageTag.trim().isEmpty()) {
            return DEFAULT_TARGET_LANGUAGE;
        }
        String supported = TranslateLanguage.fromLanguageTag(languageTag.trim());
        return supported == null ? DEFAULT_TARGET_LANGUAGE : supported;
    }

    static String targetLanguage(SharedPreferences preferences) {
        if (preferences == null) return DEFAULT_TARGET_LANGUAGE;
        return normalizeTargetLanguage(preferences.getString(
                TARGET_LANGUAGE_KEY, DEFAULT_TARGET_LANGUAGE));
    }

    static TranslatorOptions translatorOptions(SharedPreferences preferences) {
        return new TranslatorOptions.Builder()
                .setSourceLanguage(sourceLanguage())
                .setTargetLanguage(targetLanguage(preferences))
                .build();
    }

    static TranslateRemoteModel targetModel(SharedPreferences preferences) {
        return new TranslateRemoteModel.Builder(targetLanguage(preferences)).build();
    }
}
