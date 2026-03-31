package com.example.truba;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Pair;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ConstantsManager {
    private static final String PREFS_NAME = "math_constants";
    private final SharedPreferences prefs;

    public ConstantsManager(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public void saveConstant(String name, double value, double min, double max) {
        prefs.edit()
                .putFloat(name, (float) value)
                .putFloat(name + "_min", (float) min)
                .putFloat(name + "_max", (float) max)
                .apply();
    }

    // Для совместимости со старым кодом (без min/max) – используем значение по умолчанию
    public void saveConstant(String name, double value) {
        saveConstant(name, value, 0, 100);
    }

    public double getConstant(String name, double defaultValue) {
        return prefs.getFloat(name, (float) defaultValue);
    }

    public double getConstantMin(String name, double defaultValue) {
        return prefs.getFloat(name + "_min", (float) defaultValue);
    }

    public double getConstantMax(String name, double defaultValue) {
        return prefs.getFloat(name + "_max", (float) defaultValue);
    }

    public void removeConstant(String name) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.remove(name);
        editor.remove(name + "_min");
        editor.remove(name + "_max");
        editor.apply();
    }

    public List<ConstantInfo> getAllConstants() {
        Map<String, ?> all = prefs.getAll();
        List<ConstantInfo> list = new ArrayList<>();
        for (Map.Entry<String, ?> entry : all.entrySet()) {
            String key = entry.getKey();
            if (!key.endsWith("_min") && !key.endsWith("_max") && entry.getValue() instanceof Float) {
                String name = key;
                double value = (Float) entry.getValue();
                double min = getConstantMin(name, 0);
                double max = getConstantMax(name, 100);
                list.add(new ConstantInfo(name, value, min, max));
            }
        }
        return list;
    }

    public List<String> getAllConstantNames() {
        List<ConstantInfo> constants = getAllConstants();
        List<String> names = new ArrayList<>();
        for (ConstantInfo c : constants) names.add(c.name);
        return names;
    }

    public void clearAll() {
        prefs.edit().clear().apply();
    }

    public static class ConstantInfo {
        public String name;
        public double value;
        public double min;
        public double max;

        public ConstantInfo(String name, double value, double min, double max) {
            this.name = name;
            this.value = value;
            this.min = min;
            this.max = max;
        }
    }
}