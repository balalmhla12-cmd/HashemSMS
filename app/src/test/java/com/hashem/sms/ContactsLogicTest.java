package com.hashem.sms;

import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static org.junit.Assert.*;

public class ContactsLogicTest {
    @Test public void sortsContactsByNameCaseInsensitively() {
        List<ContactsLogic.ContactRecord> sorted = ContactsLogic.sortByName(Arrays.asList(
                new ContactsLogic.ContactRecord(1, "زيد", Collections.singletonList("1"), ""),
                new ContactsLogic.ContactRecord(2, "ahmed", Collections.singletonList("2"), ""),
                new ContactsLogic.ContactRecord(3, "Ahmed", Collections.singletonList("3"), "")));
        assertEquals("ahmed", sorted.get(0).name.toLowerCase(java.util.Locale.ROOT));
        assertEquals("Ahmed", sorted.get(1).name);
        assertEquals("زيد", sorted.get(2).name);
    }

    @Test public void filtersByNameOrPhoneDigits() {
        List<ContactsLogic.ContactRecord> people = Arrays.asList(
                new ContactsLogic.ContactRecord(1, "أحمد", Collections.singletonList("+967 777-123-456"), ""),
                new ContactsLogic.ContactRecord(2, "سارة", Collections.singletonList("0555 111 222"), ""));
        assertEquals(1, ContactsLogic.filter(people, "أحمد").size());
        assertEquals(1, ContactsLogic.filter(people, "777123456").size());
        assertEquals(0, ContactsLogic.filter(people, "غير موجود").size());
    }

    @Test public void deduplicatesLocalInternationalAndFormattedNumbers() {
        List<String> numbers = ContactsLogic.deduplicateNumbers(Arrays.asList(
                "0777123456", "+967777123456", "00967777123456", "+967 777-123-456", "0555111222"));
        assertEquals(2, numbers.size());
        assertEquals("0777123456", numbers.get(0));
    }

    @Test public void matchesYemeniLocalInternationalAndFormattedNumbers() {
        assertTrue(ContactsLogic.matchesNumber("0777123456", "+967777123456"));
        assertTrue(ContactsLogic.matchesNumber("0777123456", "00967777123456"));
        assertTrue(ContactsLogic.matchesNumber("+967 777-123-456", "00967 777 123 456"));
        assertTrue(ContactsLogic.matchesNumber("0777-123-456", "+967 (777) 123456"));
    }

    @Test public void rejectsEmptyAndDifferentNumbers() {
        assertFalse(ContactsLogic.matchesNumber("", "+967777123456"));
        assertFalse(ContactsLogic.matchesNumber("0777123456", "0555111222"));
    }
}
