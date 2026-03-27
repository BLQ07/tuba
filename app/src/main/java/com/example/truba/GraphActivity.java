package com.example.truba;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.*;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.multidex.BuildConfig;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;

import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.*;

public class GraphActivity extends AppCompatActivity {

    private LineChart chart;
    private Spinner spinnerX, spinnerY;
    private Button btnPlot, btnExportCSV;
    private DataCollector dataCollector;
    private List<String> variableNames;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_graph);

        chart = findViewById(R.id.chart);
        spinnerX = findViewById(R.id.spinner_x);
        spinnerY = findViewById(R.id.spinner_y);
        btnPlot = findViewById(R.id.btn_plot);
        btnExportCSV = findViewById(R.id.btn_export_csv);

        dataCollector = MathCadApplication.getDataCollector();

        variableNames = dataCollector.getAvailableVariables();
        variableNames.add(0, "время");

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, variableNames);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerX.setAdapter(adapter);
        spinnerY.setAdapter(adapter);
        spinnerX.setSelection(0);
        if (variableNames.size() > 1) spinnerY.setSelection(1);

        btnPlot.setOnClickListener(v -> {
            String varX = spinnerX.getSelectedItem().toString();
            String varY = spinnerY.getSelectedItem().toString();
            plotGraph(varX, varY);
        });

        btnExportCSV.setOnClickListener(v -> {
            String varX = spinnerX.getSelectedItem().toString();
            String varY = spinnerY.getSelectedItem().toString();
            exportDataToCSV(varX, varY);
        });
    }

    private void plotGraph(String varX, String varY) {
        List<Entry> entries;

        if ("время".equals(varX)) {
            entries = dataCollector.getTimeSeries(varY);
            if (entries.isEmpty()) {
                Toast.makeText(this, "Нет данных для переменной " + varY, Toast.LENGTH_SHORT).show();
                return;
            }
        } else {
            entries = dataCollector.getScatterData(varX, varY);
            if (entries.isEmpty()) {
                Toast.makeText(this, "Нет общих точек для " + varX + " и " + varY, Toast.LENGTH_SHORT).show();
                return;
            }
        }

        // Логируем первые 5 точек для отладки
        for (int i = 0; i < Math.min(entries.size(), 5); i++) {
            Entry e = entries.get(i);
            Log.d("Graph", "Point: x=" + e.getX() + ", y=" + e.getY());
        }
        Log.d("Graph", "Количество точек: " + entries.size());

        LineDataSet dataSet = new LineDataSet(entries, varY + " от " + varX);
        dataSet.setDrawValues(false);
        dataSet.setColor(getResources().getColor(R.color.purple_500));
        dataSet.setLineWidth(2f);
        dataSet.setCircleRadius(3f);

        LineData lineData = new LineData(dataSet);
        chart.setData(lineData);

        // Настройка оси X
        XAxis xAxis = chart.getXAxis();
        if ("время".equals(varX)) {
            xAxis.setValueFormatter(new ValueFormatter() {
                @Override
                public String getFormattedValue(float value) {
                    return String.format(Locale.US, "%.1f с", value);
                }
            });
            xAxis.setLabelRotationAngle(0);
        } else {
            xAxis.setValueFormatter(new ValueFormatter() {
                @Override
                public String getFormattedValue(float value) {
                    return String.format(Locale.US, "%.2f", value);
                }
            });
        }

        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        chart.getAxisLeft().setDrawGridLines(true);
        chart.getAxisRight().setEnabled(false);
        chart.getDescription().setEnabled(false);
        chart.invalidate();
    }

    private void exportDataToCSV(String varX, String varY) {
        List<Entry> entries;

        if ("время".equals(varX)) {
            entries = dataCollector.getTimeSeries(varY);
        } else {
            entries = dataCollector.getScatterData(varX, varY);
        }

        if (entries.isEmpty()) {
            Toast.makeText(this, "Нет данных для экспорта", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            String fileName = "graph_" + varY + "_vs_" + varX + "_" + System.currentTimeMillis() + ".csv";
            File csvFile = new File(getExternalFilesDir(null), fileName);
            FileWriter writer = new FileWriter(csvFile);

            writer.append(varX).append(",").append(varY).append("\n");
            for (Entry entry : entries) {
                writer.append(String.valueOf(entry.getX()))
                        .append(",")
                        .append(String.valueOf(entry.getY()))
                        .append("\n");
            }
            writer.close();

            Toast.makeText(this, "CSV сохранён: " + csvFile.getAbsolutePath(), Toast.LENGTH_LONG).show();
            shareCSVFile(csvFile);

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Ошибка экспорта: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void shareCSVFile(File csvFile) {
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/csv");
        shareIntent.putExtra(Intent.EXTRA_STREAM, FileProvider.getUriForFile(this,
                BuildConfig.APPLICATION_ID + ".fileprovider", csvFile));
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(shareIntent, "Поделиться CSV"));
    }
}