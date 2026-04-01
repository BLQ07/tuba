package com.example.truba;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.lifecycle.ViewModelProvider;
import androidx.multidex.BuildConfig;
import androidx.viewpager2.widget.ViewPager2;

import com.example.truba.table.TableManager;
import com.example.truba.table.TablesActivity;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends BaseActivity implements NavigationView.OnNavigationItemSelectedListener {

    private DrawerLayout drawer;
    private NavigationView navigationView;
    private Toolbar toolbar;
    private TabLayout tabLayout;
    private ViewPager2 viewPager;
    private MainViewModel viewModel;

    private ESPConnector espConnector;
    private ConstantsManager constantsManager;
    private SheetManager sheetManager;
    private TableManager tableManager;
    private DataCollector dataCollector;
    private WifiManager wifiManager;

    private boolean isPolling = false;
    private Map<String, Double> lastEspData = new HashMap<>();
    private Map<String, Double> sessionConstants = new HashMap<>();
    private List<Double> computedResults = new ArrayList<>();

    private static final int PERMISSION_REQUEST_CODE = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Инициализация менеджеров
        constantsManager = new ConstantsManager(this);
        sheetManager = new SheetManager(this);
        tableManager = new TableManager(this);
        dataCollector = MathCadApplication.getDataCollector();
        espConnector = new ESPConnector(this);
        wifiManager = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);

        // ViewModel
        viewModel = new ViewModelProvider(this).get(MainViewModel.class);
        viewModel.setConstantInfos(constantsManager.getAllConstants());
        viewModel.setSheets(sheetManager.getAllSheets());
        viewModel.setSessionConstants(sessionConstants);
        viewModel.setComputedResults(computedResults);

        // UI
        initViews();
        setupViewPager();
        setupESP();
        checkPermissions();

        // Подписка на изменения
        observeData();
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        drawer = findViewById(R.id.drawer_layout);
        navigationView = findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();

        tabLayout = findViewById(R.id.tabLayout);
        viewPager = findViewById(R.id.viewPager);
    }

    private void setupViewPager() {
        ViewPagerAdapter adapter = new ViewPagerAdapter(this);
        viewPager.setAdapter(adapter);

        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            switch (position) {
                case 0: tab.setText("Управление"); break;
                case 1: tab.setText("Результаты"); break;
                case 2: tab.setText("Листы"); break;
                case 3: tab.setText("График"); break;
                case 4: tab.setText("Настройки"); break;
            }
        }).attach();
    }

    private void observeData() {
        // Обновление списка переменных при получении данных от ESP
        viewModel.getEspData().observe(this, data -> {
            if (data != null) {
                lastEspData = data;
                updateVariableList();
            }
        });

        // Обновление констант при изменении
        viewModel.getConstantInfos().observe(this, infos -> updateVariableList());

        // Обновление временных констант
        viewModel.getSessionConstants().observe(this, session -> {
            sessionConstants = session;
            updateVariableList();
        });
    }

    private void updateVariableList() {
        List<String> names = new ArrayList<>();
        Map<String, Double> values = new HashMap<>();

        for (ConstantsManager.ConstantInfo info : constantsManager.getAllConstants()) {
            names.add(info.name);
            values.put(info.name, info.value);
        }

        if (lastEspData != null) {
            for (Map.Entry<String, Double> entry : lastEspData.entrySet()) {
                if (!names.contains(entry.getKey())) {
                    names.add(entry.getKey());
                    values.put(entry.getKey(), entry.getValue());
                }
            }
        }

        for (Map.Entry<String, Double> entry : sessionConstants.entrySet()) {
            if (!names.contains(entry.getKey())) {
                names.add(entry.getKey());
                values.put(entry.getKey(), entry.getValue());
            }
        }

        Collections.sort(names);
        viewModel.setVariableNames(names);
        viewModel.setVariableValues(values);
    }

    private void setupESP() {
        // Загрузка настроек ESP
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String espSsid = prefs.getString("esp_ssid", "MathCad_ESP");
        String espPassword = prefs.getString("esp_password", "12345678");
        ESPConnector.updateSettings(espSsid, espPassword);
    }

    public void startPolling() {
        dataCollector.clear();
        if (!espConnector.isConnectedToESP()) {
            Toast.makeText(this, "Не подключено к ESP", Toast.LENGTH_SHORT).show();
            return;
        }

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        int interval = prefs.getInt("poll_interval", 1000);
        espConnector.setPollInterval(interval);

        espConnector.startPolling(data -> runOnUiThread(() -> {
            viewModel.setEspData(data);
            dataCollector.addData(data);

            String sheetContent = viewModel.getCurrentSheetContent().getValue();
            if (sheetContent != null && !sheetContent.isEmpty()) {
                updateComputation(sheetContent);
            }
        }));

        isPolling = true;
        viewModel.setIsPolling(true);
        Toast.makeText(this, "Опрос начат", Toast.LENGTH_SHORT).show();
    }

    public void stopPolling() {
        espConnector.stopPolling();
        isPolling = false;
        viewModel.setIsPolling(false);
        Toast.makeText(this, "Опрос остановлен", Toast.LENGTH_SHORT).show();
    }

    public void togglePolling() {
        if (isPolling) stopPolling();
        else startPolling();
    }

    private void updateComputation(String sheetContent) {
        String[] lines = sheetContent.split("\\r?\\n");
        StringBuilder results = new StringBuilder();
        Map<String, Double> tempConstants = new HashMap<>();

        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;

            // print(имя)
            if (line.matches("print\\([a-zA-Z_][a-zA-Z0-9_]*\\)")) {
                String varName = line.substring(6, line.length() - 1).trim();
                Double value = findVariableValue(varName, tempConstants);
                if (value != null) {
                    espConnector.sendVariable(varName, value);
                    results.append("print(").append(varName).append(") = ")
                            .append(String.format(java.util.Locale.US, "%.6f", value))
                            .append(" - отправлено на ESP\n");
                } else {
                    results.append("Ошибка: переменная '").append(varName).append("' не найдена\n");
                }
                continue;
            }

            // print(имя, выражение)
            if (line.startsWith("print(") && line.contains(",") && line.endsWith(")")) {
                String params = line.substring(6, line.length() - 1);
                String[] parts = params.split(",", 2);
                if (parts.length == 2) {
                    String varName = parts[0].trim();
                    String valueExpr = parts[1].trim();
                    try {
                        double value = evaluateExpression(valueExpr, tempConstants);
                        espConnector.sendVariable(varName, value);
                        results.append("print(").append(varName).append(", ")
                                .append(String.format(java.util.Locale.US, "%.6f", value))
                                .append(") - отправлено на ESP\n");
                    } catch (Exception e) {
                        results.append("Ошибка в print: ").append(e.getMessage()).append("\n");
                    }
                }
                continue;
            }

            // Присваивание
            int assignPos = line.indexOf(":=");
            if (assignPos >= 0) {
                String left = line.substring(0, assignPos).trim();
                String right = line.substring(assignPos + 2).trim();
                try {
                    double val = evaluateExpression(right, tempConstants);
                    tempConstants.put(left, val);
                    results.append(left).append(" = ").append(String.format(java.util.Locale.US, "%.4f", val)).append("\n");
                    dataCollector.addData(Collections.singletonMap(left, val));
                } catch (Exception e) {
                    results.append("Ошибка: ").append(e.getMessage()).append("\n");
                }
                continue;
            }

            // Обычное выражение
            try {
                double val = evaluateExpression(line, tempConstants);
                results.append(String.format(java.util.Locale.US, "%.6f", val)).append("\n");
                computedResults.add(val);
                if (computedResults.size() > 200) computedResults.remove(0);
                dataCollector.addData(Collections.singletonMap("result", val));
                viewModel.setComputedResults(computedResults);
            } catch (Exception e) {
                results.append("Ошибка: ").append(e.getMessage()).append("\n");
            }
        }

        viewModel.setResults(results.toString());
    }

    private Double findVariableValue(String varName, Map<String, Double> tempConstants) {
        if (tempConstants.containsKey(varName)) return tempConstants.get(varName);
        for (ConstantsManager.ConstantInfo info : constantsManager.getAllConstants()) {
            if (info.name.equals(varName)) return info.value;
        }
        if (lastEspData.containsKey(varName)) return lastEspData.get(varName);
        if (sessionConstants.containsKey(varName)) return sessionConstants.get(varName);
        return null;
    }

    private double evaluateExpression(String expr, Map<String, Double> tempConstants) throws Exception {
        expr = expr.replace("π", String.valueOf(Math.PI)).replace("e", String.valueOf(Math.E));

        java.util.Set<String> vars = new java.util.HashSet<>();
        for (ConstantsManager.ConstantInfo info : constantsManager.getAllConstants()) vars.add(info.name);
        vars.addAll(lastEspData.keySet());
        vars.addAll(tempConstants.keySet());
        vars.addAll(sessionConstants.keySet());

        net.objecthunter.exp4j.ExpressionBuilder builder = new net.objecthunter.exp4j.ExpressionBuilder(expr);
        builder.function(new net.objecthunter.exp4j.function.Function("log", 1) {
            @Override
            public double apply(double... args) {
                return Math.log10(args[0]);
            }
        });
        builder.variables(vars);
        net.objecthunter.exp4j.Expression expression = builder.build();

        for (String var : vars) {
            double val = Double.NaN;
            for (ConstantsManager.ConstantInfo info : constantsManager.getAllConstants()) {
                if (info.name.equals(var)) {
                    val = info.value;
                    break;
                }
            }
            if (!Double.isNaN(val)) {
                expression.setVariable(var, val);
            } else if (lastEspData.containsKey(var)) {
                expression.setVariable(var, lastEspData.get(var));
            } else if (tempConstants.containsKey(var)) {
                expression.setVariable(var, tempConstants.get(var));
            } else if (sessionConstants.containsKey(var)) {
                expression.setVariable(var, sessionConstants.get(var));
            }
        }
        return expression.evaluate();
    }

    public void updateSheetComputation(String sheetContent) {
        updateComputation(sheetContent);
    }

    public void addSessionConstant(String name, double value) {
        sessionConstants.put(name, value);
        viewModel.setSessionConstants(sessionConstants);
        updateVariableList();
    }

    public void clearSessionConstants() {
        sessionConstants.clear();
        viewModel.setSessionConstants(sessionConstants);
        updateVariableList();
        Toast.makeText(this, "Временные константы очищены", Toast.LENGTH_SHORT).show();
    }

    public void exportCurrentValuesToCSV() {
        try {
            String fileName = "values_" + System.currentTimeMillis() + ".csv";
            File csvFile = new File(getExternalFilesDir(null), fileName);
            FileWriter writer = new FileWriter(csvFile);

            writer.append("Переменная,Значение\n");

            for (ConstantsManager.ConstantInfo info : constantsManager.getAllConstants()) {
                writer.append(info.name).append(",").append(String.valueOf(info.value)).append("\n");
            }

            if (lastEspData != null) {
                for (Map.Entry<String, Double> entry : lastEspData.entrySet()) {
                    writer.append(entry.getKey()).append(",").append(String.valueOf(entry.getValue())).append("\n");
                }
            }

            writer.close();
            Toast.makeText(this, "CSV сохранён: " + csvFile.getAbsolutePath(), Toast.LENGTH_LONG).show();

            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/csv");
            shareIntent.putExtra(Intent.EXTRA_STREAM, androidx.core.content.FileProvider.getUriForFile(this,
                    BuildConfig.APPLICATION_ID + ".fileprovider", csvFile));
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(shareIntent, "Поделиться CSV"));

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Ошибка: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                }, PERMISSION_REQUEST_CODE);
            }
        }
    }

    public ESPConnector getEspConnector() { return espConnector; }
    public ConstantsManager getConstantsManager() { return constantsManager; }
    public SheetManager getSheetManager() { return sheetManager; }
    public TableManager getTableManager() { return tableManager; }
    public DataCollector getDataCollector() { return dataCollector; }
    public MainViewModel getMainViewModel() { return viewModel; }
    public Map<String, Double> getSessionConstants() { return sessionConstants; }
    public Map<String, Double> getLastEspData() { return lastEspData; }

    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.nav_settings) {
            viewPager.setCurrentItem(4);
        } else if (id == R.id.nav_sheets) {
            viewPager.setCurrentItem(2);
        } else if (id == R.id.nav_constants) {
            startActivity(new Intent(this, ConstantsActivity.class));
        } else if (id == R.id.nav_tables) {
            startActivity(new Intent(this, TablesActivity.class));
        } else if (id == R.id.nav_export_csv) {
            exportCurrentValuesToCSV();
        }
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "Некоторые разрешения не получены", Toast.LENGTH_LONG).show();
                    break;
                }
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        viewModel.setConstantInfos(constantsManager.getAllConstants());
        viewModel.setSheets(sheetManager.getAllSheets());
        updateVariableList();
    }
                        }
