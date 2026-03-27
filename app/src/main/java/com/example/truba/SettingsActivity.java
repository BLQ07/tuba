package com.example.truba;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.View;
import android.widget.*;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;


import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class SettingsActivity extends AppCompatActivity {

    private SharedPreferences prefs;
    private EditText etEspSsid;
    private EditText etEspPassword;
    private Spinner spinnerNightMode;
    private Spinner spinnerAccentColor;
    private SeekBar seekBar;
    private TextView textInterval;
    Button btnInfo;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        btnInfo = findViewById(R.id.btn_info);
        prefs = PreferenceManager.getDefaultSharedPreferences(this);

        etEspSsid = findViewById(R.id.et_esp_ssid);
        etEspPassword = findViewById(R.id.et_esp_password);
        spinnerNightMode = findViewById(R.id.spinner_night_mode);
        spinnerAccentColor = findViewById(R.id.spinner_accent_color);
        seekBar = findViewById(R.id.seekBar);
        textInterval = findViewById(R.id.textInterval);
        Button btnSave = findViewById(R.id.btn_save);
        // Загружаем сохранённые настройки ESP
        String savedSsid = prefs.getString("esp_ssid", "MathCad_ESP");
        String savedPassword = prefs.getString("esp_password", "12345678");
        etEspSsid.setText(savedSsid);
        etEspPassword.setText(savedPassword);

        // Настройка спиннера ночного режима
        final String[] nightModes = {"Системный", "Светлая", "Тёмная"};
        ArrayAdapter<String> nightAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, nightModes);
        nightAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerNightMode.setAdapter(nightAdapter);
        spinnerNightMode.setSelection(prefs.getInt("night_mode", 0));

        // Настройка спиннера цвета акцента
        final String[] accentColors = {"Фиолетовый", "Красный", "Синий", "Зелёный", "Оранжевый"};
        final String[] accentKeys = {"purple", "red", "blue", "green", "orange"};
        ArrayAdapter<String> accentAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, accentColors);
        accentAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerAccentColor.setAdapter(accentAdapter);

        String savedAccent = prefs.getString("accent_color", "purple");
        for (int i = 0; i < accentKeys.length; i++) {
            if (accentKeys[i].equals(savedAccent)) {
                spinnerAccentColor.setSelection(i);
                break;
            }
        }

        // Настройка интервала опроса
        int interval = prefs.getInt("pollinterval", 1000);
        seekBar.setProgress(interval / 100);
        textInterval.setText("Интервал: " + interval + " мс");
        btnInfo.setOnClickListener(v -> openHtmlFromAssets());
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int value = progress * 100;
                if (value < 100) value = 100;
                textInterval.setText("Интервал: " + value + " мс");
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        btnSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveSettings(accentKeys);
            }
        });
    }
    private void openHtmlFromAssets() {
        try {
            // Копируем файл из assets во временный файл
            InputStream inputStream = getAssets().open("index.html"); // или "manual.html"
            File tempFile = new File(getCacheDir(), "index.html");
            FileOutputStream outputStream = new FileOutputStream(tempFile);
            byte[] buffer = new byte[1024];
            int length;
            while ((length = inputStream.read(buffer)) > 0) {
                outputStream.write(buffer, 0, length);
            }
            outputStream.close();
            inputStream.close();

            // Получаем URI через FileProvider
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", tempFile);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, "text/html");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(intent);
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Ошибка открытия", Toast.LENGTH_SHORT).show();
        }
    }
    private void saveSettings(String[] accentKeys) {
        String ssid = etEspSsid.getText().toString().trim();
        String password = etEspPassword.getText().toString().trim();

        if (ssid.isEmpty()) {
            Toast.makeText(this, "SSID не может быть пустым", Toast.LENGTH_SHORT).show();
            return;
        }

        int nightMode = spinnerNightMode.getSelectedItemPosition();
        String accentKey = accentKeys[spinnerAccentColor.getSelectedItemPosition()];
        int interval = seekBar.getProgress() * 100;
        if (interval < 100) interval = 100;

        prefs.edit()
                .putString("esp_ssid", ssid)
                .putString("esp_password", password)
                .putInt("night_mode", nightMode)
                .putString("accent_color", accentKey)
                .putInt("poll_interval", interval)
                .apply();

        ESPConnector.updateSettings(ssid, password);

        Toast.makeText(this, "Настройки сохранены. Перезапустите приложение для применения темы.", Toast.LENGTH_LONG).show();

        finish();
    }
}