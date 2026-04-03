package com.example.truba;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.wifi.WifiManager;
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
    private WifiManager wifiManager;
    private Handler debounceHandler = new Handler();
    private Runnable debounceRunnable;

    private static final String ESP_SSID = "MathCad_ESP";
    private static final int CONNECTION_CHECK_INTERVAL = 5000;
    private Handler monitoringHandler = new Handler();
    private Runnable monitoringRunnable;

    @Override
    public void onAttach(@NonNull android.content.Context context) {
        super.onAttach(context);
        activity = (MainActivity) requireActivity();
        wifiManager = (WifiManager) requireContext().getApplicationContext().getSystemService(android.content.Context.WIFI_SERVICE);
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
                checkAndShowGuide();
                startWifiMonitoring();
            } else {
                Toast.makeText(getContext(), "Автоподключение отключено", Toast.LENGTH_SHORT).show();
                stopWifiMonitoring();
            }
        });

        if (autoConnectEnabled) {
            startWifiMonitoring();
            checkAndShowGuide();
        }
    }

    private void startWifiMonitoring() {
        if (monitoringRunnable != null) stopWifiMonitoring();
        monitoringRunnable = new Runnable() {
            @Override
            public void run() {
                if (switchAutoConnect.isChecked()) {
                    updateWifiStatus();
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

    private void checkAndShowGuide() {
        if (isConnectedToESP()) {
            tvWifiStatus.setText("Подключено к ESP");
            tvWifiStatus.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
            if (!viewModel.getIsPolling().getValue()) {
                activity.startPolling();
            }
        } else {
            showManualConnectionGuide();
        }
    }

    private void showManualConnectionGuide() {
        String ssid = getEspSsid();
        String password = PreferenceManager.getDefaultSharedPreferences(requireContext()).getString("esp_password", "12345678");

        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("Подключение к ESP");
        builder.setMessage("Для работы с ESP необходимо подключиться к сети:\n\n" +
                "📡 Сеть: \"" + ssid + "\"\n" +
                "🔑 Пароль: " + password + "\n\n" +
                "Пожалуйста, подключитесь вручную через настройки Wi-Fi, затем вернитесь в приложение.");
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
        } else if (wifiManager.isWifiEnabled()) {
            String currentSsid = getCurrentSsid();
            if (currentSsid != null && !currentSsid.isEmpty() && !currentSsid.equals("0x")) {
                if (currentSsid.startsWith("\"") && currentSsid.endsWith("\"")) {
                    currentSsid = currentSsid.substring(1, currentSsid.length() - 1);
                }
                tvWifiStatus.setText("Подключено к: " + currentSsid);
                tvWifiStatus.setTextColor(getResources().getColor(android.R.color.holo_blue_dark));
            } else {
                tvWifiStatus.setText("Wi-Fi включён, не подключён");
                tvWifiStatus.setTextColor(getResources().getColor(android.R.color.holo_orange_dark));
            }
        } else {
            tvWifiStatus.setText("Wi-Fi выключен");
            tvWifiStatus.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
        }
    }

    private boolean isConnectedToESP() {
        android.net.ConnectivityManager connManager = (android.net.ConnectivityManager) requireContext().getSystemService(android.content.Context.CONNECTIVITY_SERVICE);
        android.net.NetworkInfo wifiInfo = connManager.getNetworkInfo(android.net.ConnectivityManager.TYPE_WIFI);
        if (wifiInfo != null && wifiInfo.isConnected()) {
            String ssid = getCurrentSsid();
            if (ssid != null) {
                if (ssid.startsWith("\"") && ssid.endsWith("\"")) {
                    ssid = ssid.substring(1, ssid.length() - 1);
                }
                return getEspSsid().equals(ssid);
            }
        }
        return false;
    }

    private String getCurrentSsid() {
        try {
            android.net.wifi.WifiInfo wifiInfo = wifiManager.getConnectionInfo();
            if (wifiInfo != null) return wifiInfo.getSSID();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
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
            Button btnSend = itemView.findViewById(R.id.btn_send_to_esp);

            tvName.setText(varName);
            tvValue.setText(String.format(Locale.US, "%.4f", value));

            btnSend.setOnClickListener(v -> {
                activity.getEspConnector().sendVariable(varName, value);
                Toast.makeText(getContext(), "Отправлено: " + varName + " = " + value, Toast.LENGTH_SHORT).show();
            });

            espVariablesContainer.addView(itemView);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        stopWifiMonitoring();
        if (debounceRunnable != null) debounceHandler.removeCallbacks(debounceRunnable);
    }
    }
