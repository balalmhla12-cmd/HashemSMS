package com.hashem.sms;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class ConversationListCache {
    private static final Map<String, ArrayList<MainActivity.ThreadItem>> CACHE = new HashMap<>();

    private ConversationListCache() {}

    static synchronized ArrayList<MainActivity.ThreadItem> get(String filter) {
        ArrayList<MainActivity.ThreadItem> rows = CACHE.get(filter);
        return rows == null ? null : new ArrayList<>(rows);
    }

    static synchronized void put(String filter, List<MainActivity.ThreadItem> rows) {
        if (filter == null || rows == null) return;
        CACHE.put(filter, new ArrayList<>(rows));
    }

    static synchronized void invalidateAll() {
        CACHE.clear();
    }

    static synchronized int size() {
        return CACHE.size();
    }
}
