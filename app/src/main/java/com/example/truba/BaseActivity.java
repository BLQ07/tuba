package com.example.truba;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

public class BaseActivity extends AppCompatActivity {

    protected SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        applyTheme();
        super.onCreate(savedInstanceState);
    }

    private void applyTheme() {
        int nightMode = prefs.getInt("night_mode", 0);
        switch (nightMode) {
            case 0:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                break;
            case 1:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                break;
            case 2:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                break;
        }

        // Применяем цвет акцента
        String accentColor = prefs.getString("accent_color", "purple");
        boolean isDark = AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_YES;
        String themeName;
        if (isDark) {
            themeName = "Theme.Truba.Dark." + capitalize(accentColor);
        } else {
            themeName = "Theme.Truba." + capitalize(accentColor);
        }

        try {
            int themeId = getResources().getIdentifier(themeName, "style", getPackageName());
            if (themeId != 0) {
                setTheme(themeId);
            }
        } catch (Exception e) {
            // fallback
        }
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) return "Purple";
        return str.substring(0, 1).toUpperCase() + str.substring(1).toLowerCase();
    }
}