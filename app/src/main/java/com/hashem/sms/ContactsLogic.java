package com.hashem.sms;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/** Pure contact-list and phone matching helpers; no Android or database side effects. */
final class ContactsLogic {
    private ContactsLogic() {}

    static final class ContactRecord {
        final long id;
        final String name;
        final List<String> numbers;
        final String photoUri;

        ContactRecord(long id, String name, List<String> numbers, String photoUri) {
            this.id = id;
            this.name = name == null ? "" : name.trim();
            this.numbers = new ArrayList<>(deduplicateNumbers(numbers));
            this.photoUri = photoUri == null ? "" : photoUri;
        }
    }

    static String normalizeDigits(String input) {
        if (input == null) return "";
        String digits = input.replaceAll("[^0-9]", "");
        if (digits.startsWith("00")) digits = digits.substring(2);
        return digits;
    }

    static boolean matchesNumber(String first, String second) {
        String a = normalizeDigits(first);
        String b = normalizeDigits(second);
        if (a.isEmpty() || b.isEmpty()) return false;
        if (a.equals(b)) return true;
        return a.length() >= 9 && b.length() >= 9
                && a.regionMatches(a.length() - 9, b, b.length() - 9, 9);
    }

    static List<String> deduplicateNumbers(List<String> numbers) {
        ArrayList<String> result = new ArrayList<>();
        if (numbers == null) return result;
        for (String number : numbers) {
            if (number == null || normalizeDigits(number).isEmpty()) continue;
            boolean duplicate = false;
            for (String existing : result) {
                if (matchesNumber(existing, number)) {
                    duplicate = true;
                    break;
                }
            }
            if (!duplicate) result.add(number.trim());
        }
        return result;
    }

    static List<ContactRecord> sortByName(List<ContactRecord> contacts) {
        ArrayList<ContactRecord> sorted = new ArrayList<>();
        if (contacts != null) sorted.addAll(contacts);
        Collections.sort(sorted, Comparator.comparing(
                (ContactRecord c) -> c.name.toLowerCase(Locale.ROOT)));
        return sorted;
    }

    static List<ContactRecord> filter(List<ContactRecord> contacts, String query) {
        ArrayList<ContactRecord> filtered = new ArrayList<>();
        if (contacts == null) return filtered;
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) {
            filtered.addAll(contacts);
            return filtered;
        }
        for (ContactRecord contact : contacts) {
            if (contact.name.toLowerCase(Locale.ROOT).contains(q)) {
                filtered.add(contact);
                continue;
            }
            for (String number : contact.numbers) {
                if (number.toLowerCase(Locale.ROOT).contains(q)
                        || (!normalizeDigits(q).isEmpty() && normalizeDigits(number).contains(normalizeDigits(q)))) {
                    filtered.add(contact);
                    break;
                }
            }
        }
        return filtered;
    }
}
