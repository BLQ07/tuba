package com.example.truba;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashMap;
import java.util.Map;

public class SheetManager {
    private static final String PREFS_NAME = "sheets";
    private SharedPreferences prefs;

    public SheetManager(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public void saveSheet(String name, String content) {
        if (name == null || name.isEmpty()) return;
        if (content == null) content = "";
        prefs.edit().putString(name, content).apply();
    }

    public String getSheet(String name) {
        return prefs.getString(name, "");
    }

    public void removeSheet(String name) {
        prefs.edit().remove(name).apply();
    }

    public Map<String, String> getAllSheets() {
        Map<String, ?> all = prefs.getAll();
        Map<String, String> sheets = new HashMap<>();
        for (Map.Entry<String, ?> entry : all.entrySet()) {
            if (entry.getValue() instanceof String) {
                sheets.put(entry.getKey(), (String) entry.getValue());
            }
        }
        return sheets;
    }

    public void clearAll() {
        prefs.edit().clear().apply();
    }
}