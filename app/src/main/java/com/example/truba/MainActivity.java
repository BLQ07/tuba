package com.example.truba;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.text.ClipboardManager;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
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
import androidx.lifecycle.ViewModelProvider;
import androidx.viewpager2.widget.ViewPager2;

import com.example.truba.table.DynamicTableView;
import com.example.truba.table.TableManager;
import com.github.mikephil.charting.charts.LineChart;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import net.objecthunter.exp4j.Expression;
import net.objecthunter.exp4j.ExpressionBuilder;
import net.objecthunter.exp4j.function.Function;

import java.io.File;
import java.io.FileWriter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    private MatrixManager matrixManager;
    private DataCollector dataCollector;
    private WifiManager wifiManager;

    private boolean isPolling = false;
    private Map<String, Double> lastEspData = new HashMap<>();
    private Map<String, Double> sessionConstants = new HashMap<>();
    private List<Double> computedResults = new ArrayList<>();

    private Handler debounceHandler = new Handler();
    private Runnable debounceRunnable;

    private static final int PERMISSION_REQUEST_CODE = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        constantsManager = new ConstantsManager(this);
        sheetManager = new SheetManager(this);
        tableManager = new TableManager(this);
        matrixManager = new MatrixManager(this);
        dataCollector = MyApplication.getDataCollector();
        espConnector = new ESPConnector(this);
        wifiManager = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);

        viewModel = new ViewModelProvider(this).get(MainViewModel.class);
        viewModel.setConstantInfos(constantsManager.getAllConstants());
        viewModel.setSheets(sheetManager.getAllSheets());
        viewModel.setSessionConstants(sessionConstants);
        viewModel.setComputedResults(computedResults);

        initViews();
        setupViewPager();
        setupESP();
        checkPermissions();
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
        viewModel.getEspData().observe(this, data -> {
            if (data != null) {
                lastEspData = data;
                updateVariableList();
            }
        });
        viewModel.getConstantInfos().observe(this, infos -> updateVariableList());
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
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String espSsid = prefs.getString("esp_ssid", "MathCad_ESP");
        String espPassword = prefs.getString("esp_password", "12345678");
        ESPConnector.updateSettings(espSsid, espPassword);
    }

    public void startPolling() {
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
        Map<String, double[][]> tempMatrices = new HashMap<>();

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
                            .append(String.format(Locale.US, "%.6f", value))
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
                        double value = evaluateExpression(valueExpr, tempConstants, new HashMap<>());
                        espConnector.sendVariable(varName, value);
                        results.append("print(").append(varName).append(", ")
                                .append(String.format(Locale.US, "%.6f", value))
                                .append(") - отправлено на ESP\n");
                    } catch (Exception e) {
                        results.append("Ошибка в print: ").append(e.getMessage()).append("\n");
                    }
                }
                continue;
            }

            // solve(...)
            if (line.startsWith("solve(") && line.endsWith(")")) {
                try {
                    double result = parseSolveCall(line, tempConstants);
                    results.append(String.format(Locale.US, "%.10f", result)).append("\n");
                    computedResults.add(result);
                    dataCollector.addData(Collections.singletonMap("result", result));
                } catch (Exception e) {
                    results.append("Ошибка в solve: ").append(e.getMessage()).append("\n");
                }
                continue;
            }

            // Обработка матричных функций, возвращающих матрицы
            if (isMatrixCreatingFunction(line)) {
                String left = line.substring(0, line.indexOf(":=")).trim();
                String right = line.substring(line.indexOf(":=") + 2).trim();
                try {
                    double[][] matrix = evaluateMatrixExpression(right, tempConstants, new HashMap<>());
                    matrixManager.saveMatrix(left, matrix);
                    results.append(left).append(" = матрица ").append(matrix.length).append("×").append(matrix[0].length).append("\n");
                } catch (Exception e) {
                    results.append("Ошибка: ").append(e.getMessage()).append("\n");
                }
                continue;
            }

            // Присваивание
            int assignPos = line.indexOf(":=");
            if (assignPos >= 0) {
                String left = line.substring(0, assignPos).trim();
                String right = line.substring(assignPos + 2).trim();
                try {
                    if (isMatrixCreatingFunction(right)) {
                        double[][] matrix = evaluateMatrixExpression(right, tempConstants, new HashMap<>());
                        matrixManager.saveMatrix(left, matrix);
                        results.append(left).append(" = матрица ").append(matrix.length).append("×").append(matrix[0].length).append("\n");
                    } else {
                        double val = evaluateExpression(right, tempConstants, new HashMap<>());
                        tempConstants.put(left, val);
                        results.append(left).append(" = ").append(String.format(Locale.US, "%.4f", val)).append("\n");
                        dataCollector.addData(Collections.singletonMap(left, val));
                    }
                } catch (Exception e) {
                    results.append("Ошибка: ").append(e.getMessage()).append("\n");
                }
                continue;
            }

            // Обычное выражение
            try {
                double val = evaluateExpression(line, tempConstants, new HashMap<>());
                results.append(String.format(Locale.US, "%.6f", val)).append("\n");
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

    private boolean isMatrixCreatingFunction(String expr) {
        return expr.matches("(matrix|inv|transpose|add|subtract|multiply|emul|eye|zeros|ones)\\s*\\(.*");
    }

    private double[][] evaluateMatrixExpression(String expr, Map<String, Double> tempConstants,
                                                Map<String, Double> loopVariables) throws Exception {
        expr = expr.trim();
        if (expr.startsWith("matrix(") && expr.endsWith(")")) {
            String args = expr.substring(7, expr.length() - 1);
            String[] parts = args.split(",");
            if (parts.length < 2) throw new Exception("matrix: нужно указать rows, cols и значения");
            int rows = Integer.parseInt(parts[0].trim());
            int cols = Integer.parseInt(parts[1].trim());
            if (parts.length != rows * cols + 2) throw new Exception("matrix: количество значений не соответствует размеру");
            double[][] matrix = new double[rows][cols];
            int idx = 0;
            for (int i = 0; i < rows; i++) {
                for (int j = 0; j < cols; j++) {
                    double val = evaluateSimpleExpression(parts[2 + idx].trim(), tempConstants, loopVariables);
                    matrix[i][j] = val;
                    idx++;
                }
            }
            return matrix;
        }
        if (expr.startsWith("inv(") && expr.endsWith(")")) {
            String matName = expr.substring(4, expr.length() - 1).trim();
            double[][] matrix = getMatrixByName(matName);
            return MatrixManager.inverse(matrix);
        }
        if (expr.startsWith("transpose(") && expr.endsWith(")")) {
            String matName = expr.substring(10, expr.length() - 1).trim();
            double[][] matrix = getMatrixByName(matName);
            return MatrixManager.transpose(matrix);
        }
        if (expr.startsWith("add(") && expr.endsWith(")")) {
            String args = expr.substring(4, expr.length() - 1);
            String[] parts = args.split(",");
            if (parts.length != 2) throw new Exception("add требует 2 аргумента");
            double[][] A = getMatrixByName(parts[0].trim());
            double[][] B = getMatrixByName(parts[1].trim());
            return MatrixManager.add(A, B);
        }
        if (expr.startsWith("subtract(") && expr.endsWith(")")) {
            String args = expr.substring(9, expr.length() - 1);
            String[] parts = args.split(",");
            double[][] A = getMatrixByName(parts[0].trim());
            double[][] B = getMatrixByName(parts[1].trim());
            return MatrixManager.subtract(A, B);
        }
        if (expr.startsWith("multiply(") && expr.endsWith(")")) {
            String args = expr.substring(9, expr.length() - 1);
            String[] parts = args.split(",");
            double[][] A = getMatrixByName(parts[0].trim());
            double[][] B = getMatrixByName(parts[1].trim());
            return MatrixManager.multiply(A, B);
        }
        if (expr.startsWith("emul(") && expr.endsWith(")")) {
            String args = expr.substring(5, expr.length() - 1);
            String[] parts = args.split(",");
            double[][] A = getMatrixByName(parts[0].trim());
            double[][] B = getMatrixByName(parts[1].trim());
            return MatrixManager.elementWiseMultiply(A, B);
        }
        if (expr.startsWith("eye(") && expr.endsWith(")")) {
            int n = (int) evaluateSimpleExpression(expr.substring(4, expr.length() - 1), tempConstants, loopVariables);
            return MatrixManager.eye(n);
        }
        if (expr.startsWith("zeros(") && expr.endsWith(")")) {
            String args = expr.substring(6, expr.length() - 1);
            String[] parts = args.split(",");
            int rows = (int) evaluateSimpleExpression(parts[0].trim(), tempConstants, loopVariables);
            int cols = (int) evaluateSimpleExpression(parts[1].trim(), tempConstants, loopVariables);
            return MatrixManager.zeros(rows, cols);
        }
        if (expr.startsWith("ones(") && expr.endsWith(")")) {
            String args = expr.substring(5, expr.length() - 1);
            String[] parts = args.split(",");
            int rows = (int) evaluateSimpleExpression(parts[0].trim(), tempConstants, loopVariables);
            int cols = (int) evaluateSimpleExpression(parts[1].trim(), tempConstants, loopVariables);
            return MatrixManager.ones(rows, cols);
        }
        throw new Exception("Неизвестная матричная функция: " + expr);
    }

    private double[][] getMatrixByName(String name) {
        double[][] matrix = matrixManager.getMatrix(name);
        if (matrix == null) throw new IllegalArgumentException("Матрица " + name + " не найдена");
        return matrix;
    }

    private double evaluateExpression(String expr, Map<String, Double> tempConstants,
                                      Map<String, Double> loopVariables) throws Exception {
        expr = expr.replace("π", String.valueOf(Math.PI));
        Pattern matrixElementPattern = Pattern.compile("([a-zA-Z_][a-zA-Z0-9_]*)\\[(\\d+)\\]\\[(\\d+)\\]");
        Matcher matcher = matrixElementPattern.matcher(expr);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String matName = matcher.group(1);
            int row = Integer.parseInt(matcher.group(2));
            int col = Integer.parseInt(matcher.group(3));
            double[][] matrix = getMatrixByName(matName);
            double val = matrix[row][col];
            matcher.appendReplacement(sb, Double.toString(val));
        }
        matcher.appendTail(sb);
        expr = sb.toString();

        Set<String> vars = new HashSet<>();
        for (ConstantsManager.ConstantInfo info : constantsManager.getAllConstants()) vars.add(info.name);
        vars.addAll(lastEspData.keySet());
        vars.addAll(tempConstants.keySet());
        vars.addAll(loopVariables.keySet());
        vars.addAll(sessionConstants.keySet());

        ExpressionBuilder builder = new ExpressionBuilder(expr);
        registerFunctions(builder);
        builder.variables(vars);
        Expression expression = builder.build();

        for (String var : vars) {
            double val = Double.NaN;
            for (ConstantsManager.ConstantInfo info : constantsManager.getAllConstants()) {
                if (info.name.equals(var)) { val = info.value; break; }
            }
            if (!Double.isNaN(val)) expression.setVariable(var, val);
            else if (lastEspData.containsKey(var)) expression.setVariable(var, lastEspData.get(var));
            else if (tempConstants.containsKey(var)) expression.setVariable(var, tempConstants.get(var));
            else if (loopVariables.containsKey(var)) expression.setVariable(var, loopVariables.get(var));
            else if (sessionConstants.containsKey(var)) expression.setVariable(var, sessionConstants.get(var));
        }
        return expression.evaluate();
    }

    private void registerFunctions(ExpressionBuilder builder) {
        builder.function(new Function("log", 1) {
            @Override public double apply(double... args) { return Math.log10(args[0]); }
        });
        builder.function(new Function("asin", 1) {
            @Override public double apply(double... args) { return Math.asin(args[0]); }
        });
        builder.function(new Function("acos", 1) {
            @Override public double apply(double... args) { return Math.acos(args[0]); }
        });
        builder.function(new Function("atan", 1) {
            @Override public double apply(double... args) { return Math.atan(args[0]); }
        });
        builder.function(new Function("atan2", 2) {
            @Override public double apply(double... args) { return Math.atan2(args[0], args[1]); }
        });
        builder.function(new Function("sinh", 1) {
            @Override public double apply(double... args) { return Math.sinh(args[0]); }
        });
        builder.function(new Function("cosh", 1) {
            @Override public double apply(double... args) { return Math.cosh(args[0]); }
        });
        builder.function(new Function("tanh", 1) {
            @Override public double apply(double... args) { return Math.tanh(args[0]); }
        });
        builder.function(new Function("asinh", 1) {
            @Override public double apply(double... args) { return Math.asinh(args[0]); }
        });
        builder.function(new Function("acosh", 1) {
            @Override public double apply(double... args) { return Math.acosh(args[0]); }
        });
        builder.function(new Function("atanh", 1) {
            @Override public double apply(double... args) { return Math.atanh(args[0]); }
        });
        builder.function(new Function("abs", 1) {
            @Override public double apply(double... args) { return Math.abs(args[0]); }
        });
        builder.function(new Function("exp", 1) {
            @Override public double apply(double... args) { return Math.exp(args[0]); }
        });
        builder.function(new Function("pow", 2) {
            @Override public double apply(double... args) { return Math.pow(args[0], args[1]); }
        });
        builder.function(new Function("mod", 2) {
            @Override public double apply(double... args) { return args[0] % args[1]; }
        });
        builder.function(new Function("floor", 1) {
            @Override public double apply(double... args) { return Math.floor(args[0]); }
        });
        builder.function(new Function("ceil", 1) {
            @Override public double apply(double... args) { return Math.ceil(args[0]); }
        });
        builder.function(new Function("round", 1) {
            @Override public double apply(double... args) { return Math.round(args[0]); }
        });
        builder.function(new Function("sign", 1) {
            @Override public double apply(double... args) { return Math.signum(args[0]); }
        });
        builder.function(new Function("clamp", 3) {
            @Override public double apply(double... args) {
                return Math.max(args[1], Math.min(args[0], args[2]));
            }
        });
        builder.function(new Function("lerp", 3) {
            @Override public double apply(double... args) {
                return args[0] + (args[1] - args[0]) * args[2];
            }
        });
        builder.function(new Function("dB", 1) {
            @Override public double apply(double... args) { return 10 * Math.log10(args[0]); }
        });
        builder.function(new Function("fromdB", 1) {
            @Override public double apply(double... args) { return Math.pow(10, args[0] / 10); }
        });
        builder.function(new Function("sinc", 1) {
            @Override public double apply(double... args) {
                double x = args[0];
                return x == 0 ? 1 : Math.sin(Math.PI * x) / (Math.PI * x);
            }
        });
    }

    private double evaluateSimpleExpression(String expr, Map<String, Double> tempConstants,
                                            Map<String, Double> loopVariables) throws Exception {
        return evaluateExpression(expr, tempConstants, loopVariables);
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

    private double evaluateFunctionAt(String expr, String varName, double x,
                                      Map<String, Double> tempConstants,
                                      Map<String, Double> loopVariables) throws Exception {
        String processedExpr = expr.replaceAll("\\b" + varName + "\\b", "(" + x + ")");
        return evaluateExpression(processedExpr, tempConstants, loopVariables);
    }

    private double solveBisection(String expr, String varName, double a, double b, double eps, int maxIter,
                                  Map<String, Double> tempConstants, Map<String, Double> loopVariables) throws Exception {
        double fa = evaluateFunctionAt(expr, varName, a, tempConstants, loopVariables);
        double fb = evaluateFunctionAt(expr, varName, b, tempConstants, loopVariables);
        if (fa * fb >= 0) throw new Exception("На интервале [" + a + ", " + b + "] нет корня или функция не меняет знак");
        double c = a;
        for (int i = 0; i < maxIter; i++) {
            c = (a + b) / 2;
            double fc = evaluateFunctionAt(expr, varName, c, tempConstants, loopVariables);
            if (Math.abs(fc) < eps || (b - a) / 2 < eps) return c;
            if (fa * fc < 0) { b = c; fb = fc; }
            else { a = c; fa = fc; }
        }
        return c;
    }

    private double solveNewton(String expr, String varName, double x0, double eps, int maxIter,
                               Map<String, Double> tempConstants, Map<String, Double> loopVariables) throws Exception {
        double x = x0;
        for (int i = 0; i < maxIter; i++) {
            double fx = evaluateFunctionAt(expr, varName, x, tempConstants, loopVariables);
            if (Math.abs(fx) < eps) return x;
            double h = 1e-6;
            double f_plus = evaluateFunctionAt(expr, varName, x + h, tempConstants, loopVariables);
            double f_minus = evaluateFunctionAt(expr, varName, x - h, tempConstants, loopVariables);
            double df = (f_plus - f_minus) / (2 * h);
            if (Math.abs(df) < 1e-12) throw new Exception("Производная близка к нулю");
            double x_new = x - fx / df;
            if (Math.abs(x_new - x) < eps) return x_new;
            x = x_new;
        }
        throw new Exception("Метод Ньютона не сошёлся за " + maxIter + " итераций");
    }

    private double parseSolveCall(String line, Map<String, Double> tempConstants) throws Exception {
        if (!line.startsWith("solve(") || !line.endsWith(")")) throw new Exception("Неверный формат solve");
        String argsStr = line.substring(6, line.length() - 1);
        List<String> args = splitSolveArguments(argsStr);
        if (args.size() < 3) throw new Exception("solve требует минимум 3 аргумента");
        String expr = args.get(0);
        String varName = args.get(1);
        double eps = 1e-10;
        int maxIter = 100;
        if (args.size() == 3) {
            double x0 = evaluateSimpleExpression(args.get(2), tempConstants, new HashMap<>());
            return solveNewton(expr, varName, x0, eps, maxIter, tempConstants, new HashMap<>());
        } else if (args.size() == 4) {
            double a = evaluateSimpleExpression(args.get(2), tempConstants, new HashMap<>());
            double b = evaluateSimpleExpression(args.get(3), tempConstants, new HashMap<>());
            return solveBisection(expr, varName, a, b, eps, maxIter, tempConstants, new HashMap<>());
        } else {
            double x0 = evaluateSimpleExpression(args.get(2), tempConstants, new HashMap<>());
            eps = evaluateSimpleExpression(args.get(3), tempConstants, new HashMap<>());
            if (args.size() >= 5) maxIter = (int) evaluateSimpleExpression(args.get(4), tempConstants, new HashMap<>());
            return solveNewton(expr, varName, x0, eps, maxIter, tempConstants, new HashMap<>());
        }
    }

    private List<String> splitSolveArguments(String argsStr) {
        List<String> result = new ArrayList<>();
        int depth = 0;
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < argsStr.length(); i++) {
            char c = argsStr.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') depth--;
            if (c == ',' && depth == 0) {
                result.add(current.toString().trim());
                current.setLength(0);
            } else current.append(c);
        }
        if (current.length() > 0) result.add(current.toString().trim());
        return result;
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
    public MatrixManager getMatrixManager() { return matrixManager; }
    public DataCollector getDataCollector() { return dataCollector; }
    public MainViewModel getMainViewModel() { return viewModel; }
    public Map<String, Double> getSessionConstants() { return sessionConstants; }
    public Map<String, Double> getLastEspData() { return lastEspData; }

    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.nav_settings) viewPager.setCurrentItem(4);
        else if (id == R.id.nav_sheets) viewPager.setCurrentItem(2);
        else if (id == R.id.nav_constants) startActivity(new Intent(this, ConstantsActivity.class));
        else if (id == R.id.nav_tables) startActivity(new Intent(this, TablesManagerActivity.class));
        else if (id == R.id.nav_matrices) startActivity(new Intent(this, MatricesActivity.class));
        else if (id == R.id.nav_export_csv) exportCurrentValuesToCSV();
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
