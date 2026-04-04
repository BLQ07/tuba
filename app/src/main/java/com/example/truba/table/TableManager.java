package com.example.truba.table;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TableManager {
    private static final String PREFS_NAME = "tables";
    private final SharedPreferences prefs;
    private final Map<String, TableInfo> tables = new HashMap<>();

    public TableManager(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        loadTables();
    }

    public void addTable(String name, List<String> headers) {
        if (tables.containsKey(name)) return;
        TableInfo info = new TableInfo(name, headers);
        tables.put(name, info);
        saveTables();
    }

    public void updateTable(String oldName, String newName, List<String> headers) {
        if (oldName != null && !oldName.equals(newName)) {
            // Переименовываем таблицу и переносим данные
            String oldPrefs = "table_" + oldName;
            String newPrefs = "table_" + newName;
            SharedPreferences oldPref = prefs;
            String data = oldPref.getString(oldPrefs, null);
            if (data != null) {
                prefs.edit().putString(newPrefs, data).apply();
                prefs.edit().remove(oldPrefs).apply();
            }
            tables.remove(oldName);
        }
        TableInfo info = new TableInfo(newName, headers);
        tables.put(newName, info);
        saveTables();
    }

    public void removeTable(String name) {
        tables.remove(name);
        saveTables();
        prefs.edit().remove("table_" + name).apply();
    }

    public List<String> getTableNames() {
        return new ArrayList<>(tables.keySet());
    }

    public List<String> getHeaders(String tableName) {
        TableInfo info = tables.get(tableName);
        return info != null ? info.headers : null;
    }

    public String getPrefsNameForTable(String tableName) {
        return "table_" + tableName;
    }

    private void saveTables() {
        try {
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, TableInfo> entry : tables.entrySet()) {
                if (sb.length() > 0) sb.append(";");
                sb.append(entry.getKey()).append(":").append(String.join(",", entry.getValue().headers));
            }
            prefs.edit().putString("tables_list", sb.toString()).apply();
        } catch (Exception ignored) {}
    }

    private void loadTables() {
        tables.clear();
        String list = prefs.getString("tables_list", "");
        if (list.isEmpty()) return;
        for (String entry : list.split(";")) {
            String[] parts = entry.split(":");
            if (parts.length == 2) {
                String name = parts[0];
                String[] headers = parts[1].split(",");
                List<String> headerList = new ArrayList<>();
                for (String h : headers) headerList.add(h.trim());
                tables.put(name, new TableInfo(name, headerList));
            }
        }
    }

    private static class TableInfo {
        String name;
        List<String> headers;

        TableInfo(String name, List<String> headers) {
            this.name = name;
            this.headers = new ArrayList<>(headers);
        }
    }
}