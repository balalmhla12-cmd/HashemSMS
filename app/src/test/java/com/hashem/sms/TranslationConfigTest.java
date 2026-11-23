package com.hashem.sms;

import static org.junit.Assert.assertEquals;

import com.google.mlkit.nl.translate.TranslateLanguage;
import org.junit.Test;

public class TranslationConfigTest {
    @Test
    public void keepsEnglishAsTheOnlyInitialSourceLanguage() {
        assertEquals(TranslateLanguage.ENGLISH, TranslationConfig.sourceLanguage());
    }

    @Test
    public void defaultsTargetLanguageToArabic() {
        assertEquals(TranslateLanguage.ARABIC, TranslationConfig.normalizeTargetLanguage(null));
        assertEquals(TranslateLanguage.ARABIC, TranslationConfig.normalizeTargetLanguage(""));
    }

    @Test
    public void acceptsSupportedFutureTargetLanguageTags() {
        assertEquals(TranslateLanguage.FRENCH, TranslationConfig.normalizeTargetLanguage("fr"));
    }

    @Test
    public void fallsBackToArabicForUnsupportedTargetLanguage() {
        assertEquals(TranslateLanguage.ARABIC, TranslationConfig.normalizeTargetLanguage("zz"));
    }
}
