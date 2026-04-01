package com.example.truba;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.wifi.WifiNetworkSpecifier;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ControlFragment extends Fragment {

    private Spinner spinnerVariable;
    private SeekBar seekBar;
    private TextView tvSeekValue;
    private Button btnPlayStop;
    private Switch switchAutoConnect;
    private TextView tvWifiStatus;
    private LinearLayout espVariablesContainer;
    private ScrollView scrollView;

    private MainViewModel viewModel;
    private MainActivity activity;
    private ConnectivityManager connectivityManager;
    private Handler debounceHandler = new Handler();
    private Runnable debounceRunnable;
    private ConnectivityManager.NetworkCallback networkCallback;

    private static final String ESP_SSID = "MathCad_ESP";
    private static final int CONNECTION_CHECK_INTERVAL = 5000;
    private Handler monitoringHandler = new Handler();
    private Runnable monitoringRunnable;

    @Override
    public void onAttach(@NonNull android.content.Context context) {
        super.onAttach(context);
        activity = (MainActivity) requireActivity();
        connectivityManager = (ConnectivityManager) requireContext().getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_control, container, false);

        spinnerVariable = view.findViewById(R.id.spinner_variable);
        seekBar = view.findViewById(R.id.seekBar);
        tvSeekValue = view.findViewById(R.id.tv_seek_value);
        btnPlayStop = view.findViewById(R.id.btn_play_stop);
        switchAutoConnect = view.findViewById(R.id.switch_auto_connect);
        tvWifiStatus = view.findViewById(R.id.tv_wifi_status);
        espVariablesContainer = view.findViewById(R.id.esp_variables_container);
        scrollView = view.findViewById(R.id.scroll_view);

        viewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);

        setupVariableSpinner();
        setupSeekBar();
        setupPlayStopButton();
        setupAutoConnect();
        observeEspData();

        return view;
    }

    private void setupVariableSpinner() {
        viewModel.getVariableNames().observe(getViewLifecycleOwner(), names -> {
            if (names == null) return;
            List<String> displayNames = new ArrayList<>();
            for (String name : names) displayNames.add("_" + name);
            ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, displayNames);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinnerVariable.setAdapter(adapter);
        });

        spinnerVariable.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                List<String> names = viewModel.getVariableNames().getValue();
                if (names == null || position >= names.size()) return;
                String selected = names.get(position);
                viewModel.setSelectedVariable(selected);
                updateSeekBarForVariable(selected);
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void updateSeekBarForVariable(String varName) {
        Map<String, Double> values = viewModel.getVariableValues().getValue();
        if (values == null || !values.containsKey(varName)) return;
        double val = values.get(varName);
        double min = 0, max = 100;
        List<ConstantsManager.ConstantInfo> constants = viewModel.getConstantInfos().getValue();
        if (constants != null) {
            for (ConstantsManager.ConstantInfo info : constants) {
                if (info.name.equals(varName)) {
                    min = info.min;
                    max = info.max;
                    break;
                }
            }
        }
        seekBar.setMax((int) max);
        seekBar.setProgress((int) val);
        tvSeekValue.setText(String.format(Locale.US, "%.2f", val));
    }

    private void setupSeekBar() {
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    tvSeekValue.setText(String.format(Locale.US, "%.2f", (double) progress));
                }
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                String selected = viewModel.getSelectedVariable().getValue();
                if (selected != null) {
                    double value = seekBar.getProgress();
                    if (debounceRunnable != null) debounceHandler.removeCallbacks(debounceRunnable);
                    debounceRunnable = () -> {
                        activity.getEspConnector().sendVariable(selected, value);
                        ConstantsManager constantsManager = activity.getConstantsManager();
                        List<ConstantsManager.ConstantInfo> constants = constantsManager.getAllConstants();
                        for (ConstantsManager.ConstantInfo info : constants) {
                            if (info.name.equals(selected)) {
                                constantsManager.saveConstant(selected, value, info.min, info.max);
                                break;
                            }
                        }
                        Map<String, Double> values = viewModel.getVariableValues().getValue();
                        if (values != null) {
                            values.put(selected, value);
                            viewModel.setVariableValues(values);
                        }
                    };
                    debounceHandler.postDelayed(debounceRunnable, 200);
                }
            }
        });
    }

    private void setupPlayStopButton() {
        viewModel.getIsPolling().observe(getViewLifecycleOwner(), isPolling -> {
            btnPlayStop.setText(isPolling ? "Stop" : "Play");
        });

        btnPlayStop.setOnClickListener(v -> activity.togglePolling());
    }

    private void setupAutoConnect() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
        boolean autoConnectEnabled = prefs.getBoolean("auto_connect_esp", true);
        switchAutoConnect.setChecked(autoConnectEnabled);
        updateWifiStatus();

        switchAutoConnect.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("auto_connect_esp", isChecked).apply();
            if (isChecked) {
                Toast.makeText(getContext(), "Автоподключение включено", Toast.LENGTH_SHORT).show();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startAutoConnect();
                }
                startWifiMonitoring();
            } else {
                Toast.makeText(getContext(), "Автоподключение отключено", Toast.LENGTH_SHORT).show();
                stopAutoConnect();
                stopWifiMonitoring();
            }
        });

        if (autoConnectEnabled) {
            startWifiMonitoring();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startAutoConnect();
            }
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    private void startAutoConnect() {
        if (isConnectedToESP()) {
            updateWifiStatus();
            if (!viewModel.getIsPolling().getValue()) {
                activity.startPolling();
            }
            return;
        }

        String ssid = getEspSsid();
        String password = PreferenceManager.getDefaultSharedPreferences(requireContext()).getString("esp_password", "12345678");

        // Создаём спецификатор сети
        WifiNetworkSpecifier specifier = new WifiNetworkSpecifier.Builder()
                .setSsid(ssid)
                .setWpa2Passphrase(password)
                .build();

        // Создаём запрос сети
        NetworkRequest request = new NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .setNetworkSpecifier(specifier)
                .build();

        // Удаляем старый callback, если есть
        if (networkCallback != null) {
            try {
                connectivityManager.unregisterNetworkCallback(networkCallback);
            } catch (Exception ignored) {}
        }

        // Создаём новый callback
        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull Network network) {
                super.onAvailable(network);
                // Привязываем процесс к сети
                connectivityManager.bindProcessToNetwork(network);

                requireActivity().runOnUiThread(() -> {
                    Toast.makeText(getContext(), "Подключено к ESP", Toast.LENGTH_SHORT).show();
                    updateWifiStatus();
                    if (!viewModel.getIsPolling().getValue()) {
                        activity.startPolling();
                    }
                });
            }

            @Override
            public void onUnavailable() {
                super.onUnavailable();
                requireActivity().runOnUiThread(() -> {
                    Toast.makeText(getContext(), "Не удалось подключиться к ESP", Toast.LENGTH_LONG).show();
                    showManualConnectionGuide();
                });
            }

            @Override
            public void onLost(@NonNull Network network) {
                super.onLost(network);
                requireActivity().runOnUiThread(() -> {
                    Toast.makeText(getContext(), "Соединение с ESP потеряно", Toast.LENGTH_SHORT).show();
                    updateWifiStatus();
                });
            }
        };

        // Запускаем подключение
        connectivityManager.requestNetwork(request, networkCallback);

        // Показываем уведомление
        Toast.makeText(getContext(), "Подключение к " + ssid + "...", Toast.LENGTH_SHORT).show();
    }

    private void stopAutoConnect() {
        if (networkCallback != null) {
            try {
                connectivityManager.unregisterNetworkCallback(networkCallback);
                networkCallback = null;
            } catch (Exception ignored) {}
        }
    }

    private void startWifiMonitoring() {
        if (monitoringRunnable != null) stopWifiMonitoring();
        monitoringRunnable = new Runnable() {
            @Override
            public void run() {
                if (switchAutoConnect.isChecked()) {
                    updateWifiStatus();
                    // Если не подключены и автоподключение включено, пробуем снова
                    if (!isConnectedToESP() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        startAutoConnect();
                    }
                    monitoringHandler.postDelayed(this, CONNECTION_CHECK_INTERVAL);
                }
            }
        };
        monitoringHandler.post(monitoringRunnable);
    }

    private void stopWifiMonitoring() {
        if (monitoringRunnable != null) {
            monitoringHandler.removeCallbacks(monitoringRunnable);
            monitoringRunnable = null;
        }
    }

    private void showManualConnectionGuide() {
        String ssid = getEspSsid();
        String password = PreferenceManager.getDefaultSharedPreferences(requireContext()).getString("esp_password", "12345678");

        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("Подключение к ESP");
        builder.setMessage("Не удалось автоматически подключиться к ESP.\n\n" +
                "Попробуйте подключиться вручную:\n\n" +
                "📡 Сеть: \"" + ssid + "\"\n" +
                "🔑 Пароль: " + password + "\n\n" +
                "После подключения вернитесь в приложение.");
        builder.setPositiveButton("Открыть настройки", (d, w) -> {
            startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));
        });
        builder.setNegativeButton("Отмена", null);
        builder.setCancelable(false);
        builder.show();
    }

    private String getEspSsid() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
        return prefs.getString("esp_ssid", ESP_SSID);
    }

    private void updateWifiStatus() {
        if (isConnectedToESP()) {
            tvWifiStatus.setText("Подключено к ESP");
            tvWifiStatus.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
        } else {
            tvWifiStatus.setText("Не подключено к ESP");
            tvWifiStatus.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
        }
    }

    private boolean isConnectedToESP() {
        // Проверяем, привязан ли процесс к сети ESP
        Network boundNetwork = connectivityManager.getBoundNetworkForProcess();
        if (boundNetwork != null) {
            NetworkCapabilities caps = connectivityManager.getNetworkCapabilities(boundNetwork);
            if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                return true;
            }
        }
        return false;
    }

    private void observeEspData() {
        viewModel.getEspData().observe(getViewLifecycleOwner(), espData -> {
            if (espData == null) return;
            updateEspVariablesDisplay(espData);
        });
    }

    private void updateEspVariablesDisplay(Map<String, Double> espData) {
        espVariablesContainer.removeAllViews();
        if (espData == null || espData.isEmpty()) {
            TextView emptyView = new TextView(requireContext());
            emptyView.setText("Нет данных от ESP");
            emptyView.setPadding(16, 16, 16, 16);
            emptyView.setTextSize(12);
            emptyView.setTextColor(getResources().getColor(android.R.color.darker_gray));
            espVariablesContainer.addView(emptyView);
            return;
        }

        for (Map.Entry<String, Double> entry : espData.entrySet()) {
            String varName = entry.getKey();
            double value = entry.getValue();

            View itemView = LayoutInflater.from(requireContext()).inflate(R.layout.esp_variable_item, espVariablesContainer, false);
            TextView tvName = itemView.findViewById(R.id.tv_var_name);
            TextView tvValue = itemView.findViewById(R.id.tv_var_value);


            tvName.setText(varName);
            tvValue.setText(String.format(Locale.US, "%.4f", value));


            espVariablesContainer.addView(itemView);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        stopWifiMonitoring();
        stopAutoConnect();
        if (debounceRunnable != null) debounceHandler.removeCallbacks(debounceRunnable);
    }
}