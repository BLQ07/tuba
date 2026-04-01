package com.example.truba;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class SettingsFragment extends Fragment {

    private EditText etEspSsid;
    private EditText etEspPassword;
    private SeekBar seekBar;
    private TextView textInterval;
    private Button btnSave;
    private MainViewModel viewModel;
    private void openHtmlFromAssets()  {
        try {
            // Копируем файл из assets во временный файл
            InputStream inputStream =getContext(). getAssets().open("index.html");
            File tempFile = new File(getContext(). getCacheDir(), "instruction.html");
            FileOutputStream outputStream = new FileOutputStream(tempFile);
            byte[] buffer = new byte[1024];
            int length;
            while ((length = inputStream.read(buffer)) > 0) {
                outputStream.write(buffer, 0, length);
            }
            outputStream.close();
            inputStream.close();

            // Получаем URI через FileProvider
            Uri uri = FileProvider.getUriForFile(getContext(),  getContext().getPackageName() + ".fileprovider", tempFile);

            // Открываем в браузере
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, "text/html");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(intent);

        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(getContext(), "Ошибка открытия инструкции", Toast.LENGTH_SHORT).show();
        }
    }
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_settings, container, false);

        etEspSsid = view.findViewById(R.id.et_esp_ssid);
        etEspPassword = view.findViewById(R.id.et_esp_password);
        seekBar = view.findViewById(R.id.seekBar);
        textInterval = view.findViewById(R.id.textInterval);
        btnSave = view.findViewById(R.id.btn_save);
Button ex=view.findViewById(R.id.button);
ex.setOnClickListener(
        view1 -> openHtmlFromAssets()
);
        viewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);

        // Загрузка настроек
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext());
        String ssid = prefs.getString("esp_ssid", "MathCad_ESP");
        String password = prefs.getString("esp_password", "12345678");
        int interval = prefs.getInt("poll_interval", 1000);

        etEspSsid.setText(ssid);
        etEspPassword.setText(password);
        seekBar.setProgress(interval / 100);
        textInterval.setText("Интервал: " + interval + " мс");

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

        btnSave.setOnClickListener(v -> {
            String ssidNew = etEspSsid.getText().toString().trim();
            String passwordNew = etEspPassword.getText().toString().trim();
            int intervalNew = seekBar.getProgress() * 100;
            if (intervalNew < 100) intervalNew = 100;

            prefs.edit()
                    .putString("esp_ssid", ssidNew)
                    .putString("esp_password", passwordNew)
                    .putInt("poll_interval", intervalNew)
                    .apply();

            ESPConnector.updateSettings(ssidNew, passwordNew);

            Toast.makeText(getContext(), "Настройки сохранены", Toast.LENGTH_SHORT).show();
        });

        return view;
    }
}