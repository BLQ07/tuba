package com.example.truba;

import android.content.Context;
import android.content.SharedPreferences;

public class Utils {
    public static final String getCellValueFromPrefs(
            Context context,
            String prefsName,
            long id,
            int columnIndex
    ) {
        SharedPreferences prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE);

        int rowCount = prefs.getInt("row_count", 0);

        for (int i = 0; i < rowCount; i++) {
            long rowId = prefs.getLong("row_" + i + "_id", -1);

            if (rowId == id) {
                return prefs.getString("row_" + i + "_col_" + columnIndex, "");
            }
        }

        return null;
    }}
