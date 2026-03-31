package com.example.truba;

import android.content.SharedPreferences;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.truba.table.DynamicTableView;
import com.example.truba.table.TableManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ControlFragment extends Fragment {

    private Spinner spinnerVariable;
    private SeekBar seekBar;
    private TextView tvSeekValue;
    private Spinner spinnerTables;
    private Spinner spinnerTableRows;
    private Button btnAddConstantFromTable;
    private Button btnPlayStop;
    private Switch switchAutoConnect;
    private TextView tvWifiStatus;

    private MainViewModel viewModel;
    private MainActivity activity;
    private TableManager tableManager;
    private WifiManager wifiManager;
    private final Handler debounceHandler = new Handler();
    private Runnable debounceRunnable;

    private static final String ESP_SSID = "MathCad_ESP";
    private static final String ESP_PASSWORD = "12345678";
    private static final int CONNECTION_CHECK_INTERVAL = 3000; // 3 секунды

    @Override
    public void onAttach(@NonNull android.content.Context context) {
        super.onAttach(context);
        activity = (MainActivity) requireActivity();
        tableManager = activity.getTableManager();
        wifiManager = (WifiManager) requireContext().getApplicationContext().getSystemService(android.content.Context.WIFI_SERVICE);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_control, container, false);

        spinnerVariable = view.findViewById(R.id.spinner_variable);
        seekBar = view.findViewById(R.id.seekBar);
        tvSeekValue = view.findViewById(R.id.tv_seek_value);
        spinnerTables = view.findViewById(R.id.spinner_tables);
        spinnerTableRows = view.findViewById(R.id.spinner_table_rows);
        btnAddConstantFromTable = view.findViewById(R.id.btn_add_constant_from_table);
        btnPlayStop = view.findViewById(R.id.btn_play_stop);
        switchAutoConnect = view.findViewById(R.id.switch_auto_connect);
        tvWifiStatus = view.findViewById(R.id.tv_wifi_status);

        viewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);

        setupVariableSpinner();
        setupSeekBar();
        setupTableUI();
        setupPlayStopButton();
        setupAutoConnect();

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
                        // Отправляем на ESP
                        activity.getEspConnector().sendVariable(selected, value);
                        // Обновляем постоянную константу, если такая есть
                        ConstantsManager constantsManager = activity.getConstantsManager();
                        List<ConstantsManager.ConstantInfo> constants = constantsManager.getAllConstants();
                        for (ConstantsManager.ConstantInfo info : constants) {
                            if (info.name.equals(selected)) {
                                constantsManager.saveConstant(selected, value, info.min, info.max);
                                break;
                            }
                        }
                        // Обновляем значения в ViewModel
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

    private void setupTableUI() {
        refreshTableSpinner();

        spinnerTables.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String tableName = (String) parent.getItemAtPosition(position);
                if (tableName != null && !tableName.equals("Нет таблиц")) {
                    refreshRowSpinner(tableName);
                } else {
                    spinnerTableRows.setAdapter(null);
                }
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        btnAddConstantFromTable.setOnClickListener(v -> {
            String tableName = (String) spinnerTables.getSelectedItem();
            String selectedRowName = (String) spinnerTableRows.getSelectedItem();
            if (tableName == null || tableName.equals("Нет таблиц") || selectedRowName == null) {
                Toast.makeText(getContext(), "Выберите таблицу и строку", Toast.LENGTH_SHORT).show();
                return;
            }

            DynamicTableView tempView = new DynamicTableView(requireContext());
            tempView.setPrefsName(tableManager.getPrefsNameForTable(tableName));
            List<String> headers = tableManager.getHeaders(tableName);
            if (headers == null) return;
            tempView.setHeaders(headers);
            tempView.refreshData();

            int nameIndex = headers.indexOf("name");
            if (nameIndex == -1) return;

            DynamicTableView.TableRowData row = tempView.getRowByColumnValue("name", selectedRowName);
            if (row != null && row.values != null) {
                int addedCount = 0;
                for (int i = 0; i < headers.size(); i++) {
                    String colName = headers.get(i);
                    if ("ID".equalsIgnoreCase(colName)) continue;
                    String valueStr = (i < row.values.length) ? row.values[i] : "";
                    if (valueStr == null || valueStr.trim().isEmpty()) continue;
                    try {
                        double val = Double.parseDouble(valueStr);
                        activity.addSessionConstant(colName, val);
                        addedCount++;
                    } catch (NumberFormatException ignored) {}
                }
                if (addedCount > 0) {
                    Toast.makeText(getContext(), "Добавлено " + addedCount + " временных констант", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(getContext(), "Нет числовых значений", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void refreshTableSpinner() {
        List<String> tableNames = tableManager.getTableNames();
        if (tableNames.isEmpty()) tableNames.add("Нет таблиц");
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, tableNames);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerTables.setAdapter(adapter);
    }

    private void refreshRowSpinner(String tableName) {
        List<String> rowNames = new ArrayList<>();
        DynamicTableView tempView = new DynamicTableView(requireContext());
        tempView.setPrefsName(tableManager.getPrefsNameForTable(tableName));
        List<String> headers = tableManager.getHeaders(tableName);
        if (headers == null) return;
        tempView.setHeaders(headers);
        tempView.refreshData();

        int nameIndex = headers.indexOf("name");
        if (nameIndex == -1) return;

        for (DynamicTableView.TableRowData row : tempView.getAllRows()) {
            if (row.values != null && row.values.length > nameIndex && row.values[nameIndex] != null && !row.values[nameIndex].isEmpty()) {
                rowNames.add(row.values[nameIndex]);
            }
        }
        if (rowNames.isEmpty()) rowNames.add("Нет строк");
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, rowNames);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerTableRows.setAdapter(adapter);
    }

    private void setupPlayStopButton() {
        viewModel.getIsPolling().observe(getViewLifecycleOwner(), isPolling -> {
            btnPlayStop.setText(isPolling ? "Stop" : "Play");
        });

        btnPlayStop.setOnClickListener(v -> activity.togglePolling());
    }

    private void setupAutoConnect() {
        // Загружаем настройки
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
        boolean autoConnectEnabled = prefs.getBoolean("auto_connect_esp", true);
        switchAutoConnect.setChecked(autoConnectEnabled);

        // Обновляем статус Wi-Fi
        updateWifiStatus();

        // Слушатель изменения переключателя
        switchAutoConnect.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                // Сохраняем настройку
                prefs.edit().putBoolean("auto_connect_esp", isChecked).apply();

                if (isChecked) {
                    // Включаем автоматическое подключение
                    Toast.makeText(getContext(), "Автоподключение включено", Toast.LENGTH_SHORT).show();
                    connectToESPNetwork();
                    startWifiMonitoring();
                } else {
                    // Отключаем автоматическое подключение
                    Toast.makeText(getContext(), "Автоподключение отключено", Toast.LENGTH_SHORT).show();
                    stopWifiMonitoring();
                }
            }
        });

        // Если автоподключение включено, запускаем мониторинг
        if (autoConnectEnabled) {
            startWifiMonitoring();
        }
    }

    private void startWifiMonitoring() {
        // Периодическая проверка подключения к ESP
        final Handler handler = new Handler();
        Runnable checkConnectionRunnable = new Runnable() {
            @Override
            public void run() {
                if (switchAutoConnect.isChecked()) {
                    updateWifiStatus();

                    // Если не подключены к ESP и Wi-Fi включён, пробуем подключиться
                    if (!isConnectedToESP() && wifiManager.isWifiEnabled()) {
                        connectToESPNetwork();
                    }

                    // Повторяем проверку
                    handler.postDelayed(this, CONNECTION_CHECK_INTERVAL);
                }
            }
        };
        handler.post(checkConnectionRunnable);
    }

    private void stopWifiMonitoring() {
        // Останавливаем мониторинг (Handler будет продолжать, но проверка switchAutoConnect.isChecked() вернёт false)
    }

    private void updateWifiStatus() {
        if (isConnectedToESP()) {
            tvWifiStatus.setText("Подключено к ESP");
            tvWifiStatus.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
        } else if (wifiManager.isWifiEnabled()) {
            String currentSsid = getCurrentSsid();
            if (currentSsid != null && !currentSsid.isEmpty()) {
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
                return ESP_SSID.equals(ssid);
            }
        }
        return false;
    }

    private String getCurrentSsid() {
        try {
            android.net.wifi.WifiInfo wifiInfo = wifiManager.getConnectionInfo();
            if (wifiInfo != null) {
                return wifiInfo.getSSID();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private void connectToESPNetwork() {
        if (!wifiManager.isWifiEnabled()) {
            wifiManager.setWifiEnabled(true);
            // Ждём включения Wi-Fi
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        // Получаем сохранённые настройки ESP
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
        String ssid = prefs.getString("esp_ssid", ESP_SSID);
        String password = prefs.getString("esp_password", ESP_PASSWORD);

        // Создаём конфигурацию сети
        WifiConfiguration config = new WifiConfiguration();
        config.SSID = "\"" + ssid + "\"";
        config.preSharedKey = "\"" + password + "\"";
        config.status = WifiConfiguration.Status.ENABLED;
        config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);

        // Удаляем старую конфигурацию, если есть
        List<WifiConfiguration> configuredNetworks = wifiManager.getConfiguredNetworks();
        if (configuredNetworks != null) {
            for (WifiConfiguration existing : configuredNetworks) {
                if (existing.SSID != null && existing.SSID.equals(config.SSID)) {
                    wifiManager.removeNetwork(existing.networkId);
                    break;
                }
            }
        }

        // Добавляем новую конфигурацию
        int networkId = wifiManager.addNetwork(config);
        if (networkId != -1) {
            wifiManager.disconnect();
            wifiManager.enableNetwork(networkId, true);
            wifiManager.reconnect();
            Toast.makeText(getContext(), "Подключение к " + ssid + "...", Toast.LENGTH_SHORT).show();

            // Проверяем подключение через 5 секунд
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    updateWifiStatus();
                    if (isConnectedToESP()) {
                        Toast.makeText(getContext(), "Успешно подключено к ESP", Toast.LENGTH_SHORT).show();
                        // Если ESP подключена, можно автоматически запустить опрос
                        if (!viewModel.getIsPolling().getValue()) {
                            activity.startPolling();
                        }
                    } else {
                        Toast.makeText(getContext(), "Не удалось подключиться к ESP", Toast.LENGTH_SHORT).show();
                    }
                }
            }, 5000);
        } else {
            Toast.makeText(getContext(), "Ошибка подключения к ESP", Toast.LENGTH_SHORT).show();
        }
    }
}