package com.example.truba;

import static android.content.ContentValues.TAG;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.*;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SwitchCompat;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.multidex.BuildConfig;

import com.example.truba.table.DynamicTableView;
import com.example.truba.table.TableManager;
import com.github.mikephil.charting.charts.LineChart;
import com.google.android.material.navigation.NavigationView;

import net.objecthunter.exp4j.Expression;
import net.objecthunter.exp4j.ExpressionBuilder;
import net.objecthunter.exp4j.function.Function;

import java.io.File;
import java.io.FileWriter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends BaseActivity implements NavigationView.OnNavigationItemSelectedListener {
    private MatrixManager matrixManager;
    private Map<String, double[][]> tempMatrices = new HashMap<>();
    private EditText etExpression;
    private TextView tvResults;
    private LinearLayout containerConstants, keyboardLayout;
    private LinearLayout containerSessionConstants;
    private Spinner spinnerSheets, spinnerVariable;
    private Spinner spinnerTables, spinnerTableRows;
    private SeekBar seekBar;
    private TextView tvSeekValue;
    private SwitchCompat switchESP;
    private Button btnPlayStop;
    private Button btnAddConstantFromTable;
    private Button btnCreateTable;
    private Button btnClearSession;
    private LineChart chart;

    private ESPConnector espConnector;
    private SheetManager sheetManager;
    private ConstantsManager constantsManager;
    private TableManager tableManager;
    private DataCollector dataCollector;
    private WifiManager wifiManager;

    private boolean isPolling = false;
    private String currentSheetContent;
    private Map<String, Double> lastEspData = new HashMap<>();
    private List<String> variableNames = new ArrayList<>();
    private Map<String, Double> variableValues = new HashMap<>();
    private List<Double> computedResults = new ArrayList<>();
    private Map<String, Double> sessionConstants = new HashMap<>(); // временные константы

    private static final int PERMISSION_REQUEST_CODE = 100;
    private static final int REQUEST_EDIT_SHEET = 200;
    private static final int MAX_ITERATIONS = 1000; // для защиты от бесконечных циклов

    private final String[] KEYBOARD_BUTTONS = {
            "sin(", "cos(", "tan(", "log(", "ln(", "sqrt(",
            "π", "e", "^", "(", ")", "[", "]", "{", "}",
            "7", "8", "9", "/", "4", "5", "6", "*",
            "1", "2", "3", "-", "0", ".", "=", "+", "print(", "det(", "inv(",
            "transpose(", "norm(", "dot(", "cross(", "add(", "subtract(",
            "multiply(", "emul(", "solve(", "eye(", "zeros(", "ones(", "matrix("
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        matrixManager = new MatrixManager(this);
        constantsManager = new ConstantsManager(this);
        sheetManager = new SheetManager(this);
        tableManager = new TableManager(this);
        dataCollector = MathCadApplication.getDataCollector();
        espConnector = new ESPConnector(this);
        wifiManager = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);

        initViews();

        setupListeners();
        refreshConstantsDisplay();
        refreshSheetSpinner();
        refreshVariableList();
        refreshSessionConstantsDisplay();
        setupTableUI();
        setupESP();
        checkPermissions();
    }
    private double getMatrixValue(String name, int row, int col) {
        double[][] mat = matrixManager.getMatrix(name);
        if (mat == null) mat = tempMatrices.get(name);
        if (mat == null) throw new IllegalArgumentException("Матрица не найдена: " + name);
        if (row < 0 || row >= mat.length || col < 0 || col >= mat[0].length)
            throw new IndexOutOfBoundsException("Индекс вне диапазона");
        return mat[row][col];
    }
    private void initViews() {

        tvResults = findViewById(R.id.tv_results);
        tvResults.setSelected(true);
        containerConstants = findViewById(R.id.containerConstants);
        containerSessionConstants = findViewById(R.id.containerSessionConstants);
        keyboardLayout = findViewById(R.id.keyboardLayout);
        spinnerSheets = findViewById(R.id.spinner_sheets);
        spinnerVariable = findViewById(R.id.spinner_variable);
        spinnerTables = findViewById(R.id.spinner_tables);
        spinnerTableRows = findViewById(R.id.spinner_table_rows);
        seekBar = findViewById(R.id.seekBar);
        tvSeekValue = findViewById(R.id.tv_seek_value);
        switchESP = findViewById(R.id.switch_esp);
        btnPlayStop = findViewById(R.id.btn_play_stop);
        btnAddConstantFromTable = findViewById(R.id.btn_add_constant_from_table);
        btnCreateTable = findViewById(R.id.btn_create_table);
        btnClearSession = findViewById(R.id.btn_clear_session);
        chart = findViewById(R.id.chart);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        NavigationView navigationView = findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();
    }

    private void setupKeyboard() {
        for (String text : KEYBOARD_BUTTONS) {
            Button button = new Button(this);
            button.setText(text);
            button.setTextSize(12);
            button.setPadding(8, 8, 8, 8);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            params.setMargins(4, 0, 4, 0);
            button.setLayoutParams(params);
            button.setOnClickListener(v -> {
                int start = etExpression.getSelectionStart();
                int end = etExpression.getSelectionEnd();
                String current = etExpression.getText().toString();
                String newText = current.substring(0, start) + text + current.substring(end);
                etExpression.setText(newText);
                etExpression.setSelection(start + text.length());
            });
            keyboardLayout.addView(button);
        }

        Button constButton = new Button(this);
        constButton.setText("Const");
        constButton.setTextSize(12);
        constButton.setOnClickListener(v -> showAddConstantDialog());
        keyboardLayout.addView(constButton);
    }

    private void setupListeners() {
        spinnerSheets.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String name = (String) parent.getItemAtPosition(position);
                if (name != null && !name.equals("Нет листов")) {
                    currentSheetContent = sheetManager.getSheet(name);
                    if (currentSheetContent == null) currentSheetContent = "";
                    if (isPolling) updateComputation();
                }
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        spinnerVariable.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position >= 0 && position < variableNames.size()) {
                    String selected = variableNames.get(position);
                    if (variableValues.containsKey(selected)) {
                        double val = variableValues.get(selected);
                        double min = 0, max = 100;
                        for (ConstantsManager.ConstantInfo info : constantsManager.getAllConstants()) {
                            if (info.name.equals(selected)) {
                                min = info.min;
                                max = info.max;
                                break;
                            }
                        }
                        seekBar.setMax((int) max);
                        seekBar.setProgress((int) val);
                        tvSeekValue.setText(String.format(Locale.US, "%.2f", val));
                    }
                }
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

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
                String selectedVar = (String) spinnerVariable.getSelectedItem();
                if (selectedVar != null) {
                    double value = seekBar.getProgress();
                    for (ConstantsManager.ConstantInfo info : constantsManager.getAllConstants()) {
                        if (info.name.equals(selectedVar)) {
                            constantsManager.saveConstant(selectedVar, value, info.min, info.max);
                            variableValues.put(selectedVar, value);
                            refreshConstantsDisplay();
                            break;
                        }
                    }
                    espConnector.sendVariable(selectedVar, value);
                    dataCollector.addData(Collections.singletonMap(selectedVar, value));
                    if (sessionConstants.containsKey(selectedVar)) {
                        sessionConstants.put(selectedVar, value);
                        refreshSessionConstantsDisplay();
                    }
                }
            }
        });

        btnPlayStop.setOnClickListener(v -> {
            if (isPolling) {
                stopPolling();
                startActivity(new Intent(this, GraphActivity.class));
            } else {
                if (!switchESP.isChecked()) {
                    Toast.makeText(MainActivity.this, "Сначала включите ESP", Toast.LENGTH_SHORT).show();
                    return;
                }
                startPolling();
            }
        });

        btnClearSession.setOnClickListener(v -> {
            sessionConstants.clear();
            refreshSessionConstantsDisplay();
            Toast.makeText(this, "Временные константы очищены", Toast.LENGTH_SHORT).show();
        });
    }

    private void setupESP() {
        switchESP.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                if (espConnector.isConnectedToESP()) {
                    startPolling();
                } else {
                    if (isESPNearby()) {
                        connectToESPNetwork();
                    } else {
                        showConnectGuide();
                        switchESP.setChecked(false);
                    }
                }
            } else {
                stopPolling();
                tvResults.setText("");
            }
        });
    }

    private boolean isESPNearby() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return false;
        }
        if (wifiManager != null) {
            wifiManager.startScan();
            List<WifiConfiguration> configuredNetworks = wifiManager.getConfiguredNetworks();
            if (configuredNetworks != null) {
                for (WifiConfiguration config : configuredNetworks) {
                    if (config.SSID != null && config.SSID.contains("MathCad")) {
                        return true;
                    }
                }
            }
        }
        return true;
    }

    private void connectToESPNetwork() {
        if (!wifiManager.isWifiEnabled()) {
            wifiManager.setWifiEnabled(true);
        }

        WifiConfiguration config = espConnector.getESPWifiConfig();
        if (wifiManager.getConfiguredNetworks() != null) {
            for (WifiConfiguration existing : wifiManager.getConfiguredNetworks()) {
                if (existing.SSID != null && existing.SSID.equals(config.SSID)) {
                    wifiManager.removeNetwork(existing.networkId);
                    break;
                }
            }
        }
        int networkId = wifiManager.addNetwork(config);
        if (networkId != -1) {
            wifiManager.disconnect();
            wifiManager.enableNetwork(networkId, true);
            wifiManager.reconnect();
            showConnectingDialog();
        } else {
            Toast.makeText(this, "Не удалось подключиться к ESP", Toast.LENGTH_SHORT).show();
            switchESP.setChecked(false);
        }
    }

    private void showConnectingDialog() {
        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Подключение к ESP")
                .setMessage("Подключение к сети " + espConnector.getESPWifiConfig().SSID + "...")
                .setCancelable(false)
                .create();
        dialog.show();

        new android.os.Handler().postDelayed(() -> {
            if (espConnector.isConnectedToESP()) {
                dialog.dismiss();
                startPolling();
                Toast.makeText(MainActivity.this, "Подключено к ESP", Toast.LENGTH_SHORT).show();
            } else {
                dialog.dismiss();
                Toast.makeText(MainActivity.this, "Не удалось подключиться", Toast.LENGTH_LONG).show();
                switchESP.setChecked(false);
            }
        }, 5000);
    }

    private void showConnectGuide() {
        new AlertDialog.Builder(this)
                .setTitle("Подключение к ESP")
                .setMessage("ESP не обнаружен.\n\n" +
                        "1. Убедитесь, что ESP включён и питание подано.\n" +
                        "2. Подождите 10 секунд после включения ESP.\n" +
                        "3. Включите переключатель ESP ещё раз.\n\n" +
                        "Если проблема повторяется, подключитесь вручную:\n" +
                        "Настройки → Wi-Fi → выберите сеть ESP → введите пароль")
                .setPositiveButton("OK", null)
                .show();
    }

    private void startPolling() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        int interval = prefs.getInt("pollinterval", 1000);
        espConnector.setPollInterval(interval);
        espConnector.startPolling(data -> runOnUiThread(() -> {
            lastEspData = data;
            dataCollector.addData(data);
            updateComputation();
            refreshVariableList();

            if (data.containsKey("pollinterval")) {
                int newInterval = data.get("pollinterval").intValue();
                if (newInterval != espConnector.getPollInterval()) {
                    espConnector.setPollInterval(newInterval);
                    prefs.edit().putInt("pollinterval", newInterval).apply();
                }
            }
        }));
        isPolling = true;
        btnPlayStop.setText("Stop");
        Toast.makeText(this, "Опрос начат", Toast.LENGTH_SHORT).show();
    }

    private void stopPolling() {
        espConnector.stopPolling();
        isPolling = false;
        btnPlayStop.setText("Play");
        Toast.makeText(this, "Опрос остановлен", Toast.LENGTH_SHORT).show();
    }

    // ====================== Обработка листов с поддержкой циклов ======================

    // Внутри updateComputation вызываем processLines
    private void updateComputation() {
        Log.d("updateComputation","currentSheetContent: " + currentSheetContent);
        currentSheetContent = currentSheetContent.replace("\\u003C", "<").replace("\\u003E", ">");
        currentSheetContent = currentSheetContent.replace("\\u203A", ">").replace("\\u003E", ">");
        if (currentSheetContent == null || currentSheetContent.isEmpty()) return;
        String[] lines = currentSheetContent.split("\\r?\\n");
        List<String> lineList = new ArrayList<>(Arrays.asList(lines));
        StringBuilder results = new StringBuilder();
        Map<String, Double> tempConstants = new HashMap<>();
        // processLines обработает все управляющие конструкции
        processLines(lineList, 0, results, tempConstants, new HashMap<>());
        tvResults.setText(results.toString());
        Log.d("updateComputation","results: " + results.toString());
    }

    private int processLines(List<String> lines, int startIdx, StringBuilder output,
                             Map<String, Double> tempConstants,
                             Map<String, Double> loopVariables) {
        int idx = startIdx;
        while (idx < lines.size()) {
            String line = lines.get(idx).trim();
            if (line.isEmpty()) {
                idx++;
                continue;
            }

            // Цикл for
            if (line.startsWith("for ") && line.endsWith(":")) {
                String rest = line.substring(4, line.length() - 1).trim();
                String[] parts = rest.split("\\s+");
                if (parts.length == 5 && "from".equals(parts[1]) && "to".equals(parts[3])) {
                    String varName = parts[0];
                    String fromExpr = parts[2];
                    String toExpr = parts[4];
                    int endIdx = findBlockEnd(lines, idx + 1);
                    if (endIdx == -1) {
                        output.append("Ошибка: не найден 'end' для for\n");
                        idx++;
                        continue;
                    }
                    List<String> blockLines = lines.subList(idx + 1, endIdx);
                    double fromVal, toVal;
                    try {
                        fromVal = evaluateSimpleExpression(fromExpr, tempConstants, loopVariables);
                        toVal = evaluateSimpleExpression(toExpr, tempConstants, loopVariables);
                    } catch (Exception e) {
                        output.append("Ошибка в for: ").append(e.getMessage()).append("\n");
                        idx = endIdx + 1;
                        continue;
                    }
                    for (double iVal = fromVal; iVal <= toVal; iVal += 1.0) {
                        Map<String, Double> newLoopVars = new HashMap<>(loopVariables);
                        newLoopVars.put(varName, iVal);
                        processLines(blockLines, 0, output, tempConstants, newLoopVars);
                    }
                    idx = endIdx + 1;
                    continue;
                } else {
                    output.append("Ошибка синтаксиса for\n");
                    idx++;
                    continue;
                }
            }

            // Цикл while
            if (line.startsWith("while ") && line.endsWith(":")) {
                String condition = line.substring(6, line.length() - 1).trim();
                int endIdx = findBlockEnd(lines, idx + 1);
                if (endIdx == -1) {
                    output.append("Ошибка: не найден 'end' для while\n");
                    idx++;
                    continue;
                }
                List<String> blockLines = lines.subList(idx + 1, endIdx);
                int iterations = 0;
                boolean conditionTrue = true;
                while (conditionTrue && iterations < 1000) {
                    try {
                        double condVal = evaluateSimpleExpression(condition, tempConstants, loopVariables);
                        conditionTrue = Math.abs(condVal) > 1e-12;
                    } catch (Exception e) {
                        output.append("Ошибка в условии while: ").append(e.getMessage()).append("\n");
                        break;
                    }
                    if (!conditionTrue) break;
                    processLines(blockLines, 0, output, tempConstants, loopVariables);
                    iterations++;
                }
                idx = endIdx + 1;
                continue;
            }

            // Условный оператор if
            if (line.startsWith("if ") && line.endsWith(":")) {
                String condition = line.substring(3, line.length() - 1).trim();
                int elseIdx = -1;
                int endIdx = -1;
                int depth = 0;
                for (int i = idx + 1; i < lines.size(); i++) {
                    String l = lines.get(i).trim();
                    if (l.startsWith("if ") || l.startsWith("for ") || l.startsWith("while ")) depth++;
                    if (l.equals("endif")) {
                        if (depth == 0) {
                            endIdx = i;
                            break;
                        } else depth--;
                    }
                    if (l.equals("else") && depth == 0 && elseIdx == -1) elseIdx = i;
                }
                if (endIdx == -1) {
                    output.append("Ошибка: не найден 'endif' для if\n");
                    idx++;
                    continue;
                }

                // Вычисляем условие
                boolean conditionResult;
                try {
                    double condVal = evaluateSimpleExpression(condition, tempConstants, loopVariables);
                    conditionResult = Math.abs(condVal) > 1e-12;
                } catch (Exception e) {
                    output.append("Ошибка в условии if: ").append(e.getMessage()).append("\n");
                    idx = endIdx + 1;
                    continue;
                }

                if (conditionResult) {
                    List<String> thenBlock;
                    if (elseIdx != -1) thenBlock = lines.subList(idx + 1, elseIdx);
                    else thenBlock = lines.subList(idx + 1, endIdx);
                    processLines(thenBlock, 0, output, tempConstants, loopVariables);
                } else {
                    if (elseIdx != -1) {
                        List<String> elseBlock = lines.subList(elseIdx + 1, endIdx);
                        processLines(elseBlock, 0, output, tempConstants, loopVariables);
                    }
                }
                idx = endIdx + 1;
                continue;
            }

            // Обычная строка
            processLine(line, output, tempConstants, loopVariables);
            idx++;
        }
        return idx;
    }
// Остальные методы (findBlockEnd, processLine, evaluateExpression, evaluateSimpleExpression) такие же, как в предыдущей версии с циклами.
    /**
     * Находит индекс строки, содержащей "end" на том же уровне вложенности.
     */
    private int findBlockEnd(List<String> lines, int startIdx) {
        int level = 0;
        for (int i = startIdx; i < lines.size(); i++) {
            String l = lines.get(i).trim();
            if (l.startsWith("for ") || l.startsWith("while ")) {
                level++;
            } else if (l.equals("end")) {
                if (level == 0) return i;
                else level--;
            }
        }
        return -1;
    }
    private String unescapeUnicode(String s) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length() && s.charAt(i + 1) == 'u') {
                String hex = s.substring(i + 2, i + 6);
                sb.append((char) Integer.parseInt(hex, 16));
                i += 6;
            } else {
                sb.append(c);
                i++;
            }
        }
        return sb.toString();
    }
    /**
     * Обрабатывает одну строку (print, присваивание, выражение).
     */
    private void processLine(String line, StringBuilder output,
                             Map<String, Double> tempConstants,
                             Map<String, Double> loopVariables) {
        // print(имя)
        if (line.matches("print\\([a-zA-Z_][a-zA-Z0-9_]*\\)")) {
            String varName = line.substring(6, line.length() - 1).trim();
            Double value = null;
            if (loopVariables.containsKey(varName)) value = loopVariables.get(varName);
            if (value == null && tempConstants.containsKey(varName)) value = tempConstants.get(varName);
            if (value == null && lastEspData.containsKey(varName)) value = lastEspData.get(varName);
            for (ConstantsManager.ConstantInfo info : constantsManager.getAllConstants()) {
                if (info.name.equals(varName)) { value = info.value; break; }
            }
            if (value != null) {
                espConnector.sendVariable(varName, value);
                output.append("print(").append(varName).append(") = ")
                        .append(String.format(Locale.US, "%.6f", value))
                        .append(" - отправлено на ESP\n");
            } else {
                output.append("Ошибка: переменная '").append(varName).append("' не найдена\n");
            }
            return;
        }

        // print(имя, выражение)
        if (line.startsWith("print(") && line.contains(",") && line.endsWith(")")) {
            String params = line.substring(6, line.length() - 1);
            String[] parts = params.split(",", 2);
            if (parts.length == 2) {
                String varName = parts[0].trim();
                String valueExpr = parts[1].trim();
                try {
                    double value = evaluateExpression(valueExpr, tempConstants, loopVariables);
                    espConnector.sendVariable(varName, value);
                    output.append("print(").append(varName).append(", ")
                            .append(String.format(Locale.US, "%.6f", value))
                            .append(") - отправлено на ESP\n");
                } catch (Exception e) {
                    output.append("Ошибка в print: ").append(e.getMessage()).append("\n");
                }
            } else {
                output.append("Ошибка: print требует 2 аргумента (имя, значение)\n");
            }
            return;
        }

        // Присваивание
        int assignPos = line.indexOf(":=");
        if (assignPos >= 0) {
            String left = line.substring(0, assignPos).trim();
            String right = line.substring(assignPos + 2).trim();
            try {
                double val = evaluateExpression(right, tempConstants, loopVariables);
                tempConstants.put(left, val);
                output.append(left).append(" = ").append(String.format(Locale.US, "%.4f", val)).append("\n");
                dataCollector.addData(Collections.singletonMap(left, val));
            } catch (Exception e) {
                output.append("Ошибка: ").append(e.getMessage()).append("\n");
            }
            return;
        }

        // Обычное выражение
        try {
            double val = evaluateExpression(line, tempConstants, loopVariables);
            output.append(String.format(Locale.US, "%.6f", val)).append("\n");
            computedResults.add(val);
            if (computedResults.size() > 200) computedResults.remove(0);
            dataCollector.addData(Collections.singletonMap("result", val));
        } catch (Exception e) {
            output.append("Ошибка: ").append(e.getMessage()).append("\n");
        }
    }

    /**
     * Вычисляет выражение с учётом всех источников переменных.
     */
    private double getMatrixElement(String matName, int i, int j) throws Exception {
        // Сначала ищем в постоянных матрицах
        double[][] mat = matrixManager.getMatrix(matName);
        if (mat == null) {
            // Ищем во временных матрицах (например, результат inv(A) или solve)
            // Для временных матриц нужно отдельное хранилище, например, tempMatrices
            if (tempMatrices != null && tempMatrices.containsKey(matName)) {
                mat = tempMatrices.get(matName);
            } else {
                throw new Exception("Matrix not found: " + matName);
            }
        }
        if (i < 0 || i >= mat.length || j < 0 || j >= mat[0].length) {
            throw new Exception("Index out of bounds for matrix " + matName);
        }
        return mat[i][j];
    }
    private double evaluateExpression(String expr, Map<String, Double> tempConstants,
                                      Map<String, Double> loopVariables) throws Exception {
        // Замена символа π на число
        expr = expr.replace("π", String.valueOf(Math.PI));
        // e не заменяем, exp4j сам обработает

        // Обработка обращений к элементам матрицы A[i][j]
        // Паттерн: имя_матрицы [ целое число ] [ целое число ]
        Pattern matrixElementPattern = Pattern.compile("([a-zA-Z_][a-zA-Z0-9_]*)\\s*\\[(\\d+)\\]\\s*\\[(\\d+)\\]");
        Matcher matcher = matrixElementPattern.matcher(expr);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String matName = matcher.group(1);
            int i = Integer.parseInt(matcher.group(2));
            int j = Integer.parseInt(matcher.group(3));
            double val = getMatrixElement(matName, i, j);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(Double.toString(val)));
        }
        matcher.appendTail(sb);
        expr = sb.toString();

        // Далее стандартный код: сбор переменных, создание Expression, подстановка значений
        Set<String> vars = new HashSet<>();
        for (ConstantsManager.ConstantInfo info : constantsManager.getAllConstants()) vars.add(info.name);
        vars.addAll(lastEspData.keySet());
        vars.addAll(tempConstants.keySet());
        vars.addAll(loopVariables.keySet());
        vars.addAll(sessionConstants.keySet());

        ExpressionBuilder builder = new ExpressionBuilder(expr);
        builder.function(new Function("log", 1) {
            @Override
            public double apply(double... args) {
                return Math.log10(args[0]);
            }
        });
        // Добавляем функции сравнения, если они нужны (уже должны быть)
        builder.function(new Function("lt", 2) {
            @Override
            public double apply(double... args) { return args[0] < args[1] ? 1.0 : 0.0; }
        });
        builder.function(new Function("gt", 2) {
            @Override
            public double apply(double... args) { return args[0] > args[1] ? 1.0 : 0.0; }
        });
        builder.function(new Function("le", 2) {
            @Override
            public double apply(double... args) { return args[0] <= args[1] ? 1.0 : 0.0; }
        });
        builder.function(new Function("ge", 2) {
            @Override
            public double apply(double... args) { return args[0] >= args[1] ? 1.0 : 0.0; }
        });
        builder.function(new Function("eq", 2) {
            @Override
            public double apply(double... args) { return Math.abs(args[0] - args[1]) < 1e-12 ? 1.0 : 0.0; }
        });
        builder.function(new Function("neq", 2) {
            @Override
            public double apply(double... args) { return Math.abs(args[0] - args[1]) > 1e-12 ? 1.0 : 0.0; }
        });

        builder.variables(vars);
        Expression expression = builder.build();

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
            } else if (loopVariables.containsKey(var)) {
                expression.setVariable(var, loopVariables.get(var));
            } else if (sessionConstants.containsKey(var)) {
                expression.setVariable(var, sessionConstants.get(var));
            }
        }
        return expression.evaluate();
    }
    /**
     * Упрощённая версия evaluateExpression для вычисления выражений из for/while.
     */
    private double evaluateSimpleExpression(String expr, Map<String, Double> tempConstants,
                                            Map<String, Double> loopVariables) throws Exception {
        // Заменяем операторы сравнения на вызовы функций
        expr = expr.replaceAll("([a-zA-Z0-9_]+)\\s*<\\s*([a-zA-Z0-9_]+)", "lt($1,$2)");
        expr = expr.replaceAll("([a-zA-Z0-9_]+)\\s*>\\s*([a-zA-Z0-9_]+)", "gt($1,$2)");
        expr = expr.replaceAll("([a-zA-Z0-9_]+)\\s*<=\\s*([a-zA-Z0-9_]+)", "le($1,$2)");
        expr = expr.replaceAll("([a-zA-Z0-9_]+)\\s*>=\\s*([a-zA-Z0-9_]+)", "ge($1,$2)");
        expr = expr.replaceAll("([a-zA-Z0-9_]+)\\s*==\\s*([a-zA-Z0-9_]+)", "eq($1,$2)");
        expr = expr.replaceAll("([a-zA-Z0-9_]+)\\s*!=\\s*([a-zA-Z0-9_]+)", "neq($1,$2)");
        return evaluateExpression(expr, tempConstants, loopVariables);
    }
    // ====================== Остальные методы (константы, таблицы, UI) ======================

    private void refreshConstantsDisplay() {
        containerConstants.removeAllViews();
        List<ConstantsManager.ConstantInfo> constants = constantsManager.getAllConstants();
        LayoutInflater inflater = LayoutInflater.from(this);

        for (ConstantsManager.ConstantInfo info : constants) {
            View itemView = inflater.inflate(R.layout.constant_display_item, containerConstants, false);
            TextView tvName = itemView.findViewById(R.id.tvConstName);
            TextView tvValue = itemView.findViewById(R.id.tvConstValue1);
            tvName.setText(info.name);
            tvValue.setText(String.format(Locale.US, "%.4g", info.value));
            containerConstants.addView(itemView);
        }
    }

    private void refreshSessionConstantsDisplay() {
        containerSessionConstants.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);

        for (Map.Entry<String, Double> entry : sessionConstants.entrySet()) {
            View itemView = inflater.inflate(R.layout.constant_display_item, containerSessionConstants, false);
            TextView tvName = itemView.findViewById(R.id.tvConstName);
            TextView tvValue = itemView.findViewById(R.id.tvConstValue1);
            tvName.setText(entry.getKey());
            tvValue.setText(String.format(Locale.US, "%.4g", entry.getValue()));
            containerSessionConstants.addView(itemView);
        }
    }

    private void refreshSheetSpinner() {
        Map<String, String> sheets = sheetManager.getAllSheets();
        List<String> sheetNames = new ArrayList<>(sheets.keySet());
        if (sheetNames.isEmpty()) sheetNames.add("Нет листов");
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, sheetNames);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerSheets.setAdapter(adapter);
    }

    private void refreshVariableList() {
        variableNames.clear();
        variableValues.clear();

        for (ConstantsManager.ConstantInfo info : constantsManager.getAllConstants()) {
            variableNames.add(info.name);
            variableValues.put(info.name, info.value);
        }
        if (lastEspData != null) {
            for (Map.Entry<String, Double> entry : lastEspData.entrySet()) {
                if (!variableNames.contains(entry.getKey())) {
                    variableNames.add(entry.getKey());
                    variableValues.put(entry.getKey(), entry.getValue());
                }
            }
        }
        Collections.sort(variableNames);
        List<String> displayNames = new ArrayList<>();
        for (String name : variableNames) if (name.startsWith("R")) displayNames.add(name);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, displayNames);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerVariable.setAdapter(adapter);
    }

    private void showAddConstantDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Добавить константу");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 20, 50, 20);

        EditText inputName = new EditText(this);
        inputName.setHint("Имя (например, a)");
        layout.addView(inputName);

        EditText inputValue = new EditText(this);
        inputValue.setHint("Значение");
        inputValue.setInputType(android.text.InputType.TYPE_CLASS_NUMBER |
                android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL |
                android.text.InputType.TYPE_NUMBER_FLAG_SIGNED);
        layout.addView(inputValue);

        builder.setView(layout);
        builder.setPositiveButton("Далее", (dialog, which) -> {
            String name = inputName.getText().toString().trim();
            String valStr = inputValue.getText().toString().trim();
            if (name.isEmpty() || valStr.isEmpty()) {
                Toast.makeText(MainActivity.this, "Заполните имя и значение", Toast.LENGTH_SHORT).show();
                return;
            }
            try {
                double value = Double.parseDouble(valStr);
                showMinMaxDialog(name, value, 0, 100);
            } catch (NumberFormatException e) {
                Toast.makeText(MainActivity.this, "Неверное число", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Отмена", null);
        builder.show();
    }

    private void showMinMaxDialog(String name, double value, double currentMin, double currentMax) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Диапазон для " + name);
        View view = getLayoutInflater().inflate(R.layout.dialog_min_max, null);
        final EditText etMin = view.findViewById(R.id.et_min);
        final EditText etMax = view.findViewById(R.id.et_max);
        etMin.setText(String.valueOf(currentMin));
        etMax.setText(String.valueOf(currentMax));
        builder.setView(view);

        builder.setPositiveButton("Сохранить", (dialog, which) -> {
            double min, max;
            try {
                min = Double.parseDouble(etMin.getText().toString().trim());
                max = Double.parseDouble(etMax.getText().toString().trim());
                if (min >= max) {
                    Toast.makeText(MainActivity.this, "Минимум должен быть меньше максимума", Toast.LENGTH_SHORT).show();
                    return;
                }
            } catch (NumberFormatException e) {
                Toast.makeText(MainActivity.this, "Некорректные значения", Toast.LENGTH_SHORT).show();
                return;
            }
            constantsManager.saveConstant(name, value, min, max);
            refreshConstantsDisplay();
            refreshVariableList();
            Toast.makeText(MainActivity.this, "Константа сохранена", Toast.LENGTH_SHORT).show();
        });
        builder.setNegativeButton("Отмена", null);
        builder.show();
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
                Toast.makeText(this, "Выберите таблицу и строку", Toast.LENGTH_SHORT).show();
                return;
            }

            DynamicTableView tempView = new DynamicTableView(this);
            tempView.setPrefsName(tableManager.getPrefsNameForTable(tableName));
            List<String> headers = tableManager.getHeaders(tableName);
            if (headers == null) {
                Toast.makeText(this, "Ошибка: заголовки таблицы не найдены", Toast.LENGTH_SHORT).show();
                return;
            }
            tempView.setHeaders(headers);
            tempView.refreshData();

            int nameIndex = headers.indexOf("name");
            if (nameIndex == -1) {
                Toast.makeText(this, "Таблица должна содержать столбец 'name'", Toast.LENGTH_SHORT).show();
                return;
            }

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
                        sessionConstants.put(colName, val);
                        addedCount++;
                    } catch (NumberFormatException e) {
                        Log.w("TableAdd", "Нечисловое значение в столбце " + colName + ": " + valueStr);
                    }
                }
                if (addedCount > 0) {
                    refreshSessionConstantsDisplay();
                    Toast.makeText(this, "Добавлено " + addedCount + " временных констант из строки", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Нет числовых значений для добавления", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, "Строка не найдена", Toast.LENGTH_SHORT).show();
            }
        });

        btnCreateTable.setOnClickListener(v -> { Intent intent = new Intent(MainActivity.this, GraphActivity.class);
            startActivity(intent);});
    }

    private void refreshTableSpinner() {
        List<String> tableNames = tableManager.getTableNames();
        if (tableNames.isEmpty()) tableNames.add("Нет таблиц");
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, tableNames);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerTables.setAdapter(adapter);
    }

    private void refreshRowSpinner(String tableName) {
        List<String> rowNames = new ArrayList<>();
        DynamicTableView tempView = new DynamicTableView(this);
        tempView.setPrefsName(tableManager.getPrefsNameForTable(tableName));
        List<String> headers = tableManager.getHeaders(tableName);
        if (headers == null) {
            spinnerTableRows.setAdapter(null);
            return;
        }
        tempView.setHeaders(headers);
        tempView.refreshData();

        int nameIndex = headers.indexOf("name");
        if (nameIndex == -1) {
            spinnerTableRows.setAdapter(null);
            return;
        }

        for (DynamicTableView.TableRowData row : tempView.getAllRows()) {
            if (row.values != null && row.values.length > nameIndex && row.values[nameIndex] != null && !row.values[nameIndex].isEmpty()) {
                rowNames.add(row.values[nameIndex]);
            }
        }

        if (rowNames.isEmpty()) rowNames.add("Нет строк");
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, rowNames);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerTableRows.setAdapter(adapter);
    }



    private void exportCurrentValuesToCSV() {
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
            shareCSVFile(csvFile);

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Ошибка: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void shareCSVFile(File csvFile) {
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/csv");
        shareIntent.putExtra(Intent.EXTRA_STREAM, androidx.core.content.FileProvider.getUriForFile(this,
                BuildConfig.APPLICATION_ID + ".fileprovider", csvFile));
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(shareIntent, "Поделиться CSV"));
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
    public boolean onNavigationItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.nav_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
        } else if (id == R.id.nav_sheets) {
            startActivity(new Intent(this, SheetsActivity.class));
        } else if (id == R.id.nav_constants) {
            startActivity(new Intent(this, ConstantsActivity.class));
        } else if (id == R.id.nav_tables) {
            startActivity(new Intent(this, com.example.truba.TablesActivity.class));
        } else if (id == R.id.nav_export_csv) {
            exportCurrentValuesToCSV();
        }
        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshConstantsDisplay();
        refreshSheetSpinner();
        refreshVariableList();
        refreshSessionConstantsDisplay();
    }
}