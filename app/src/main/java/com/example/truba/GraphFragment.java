package com.example.truba;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
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
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class GraphFragment extends Fragment {

    private LineChart chart;
    private Spinner spinnerX;
    private Spinner spinnerY;
    private Button btnPlot;
    private Button btnExportCSV;
    private MainViewModel viewModel;
    private DataCollector dataCollector;
    private List<String> variableNames;

    @Override
    public void onAttach(@NonNull android.content.Context context) {
        super.onAttach(context);
        dataCollector = ((MainActivity) requireActivity()).getDataCollector();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_graph, container, false);

        chart = view.findViewById(R.id.chart);
        spinnerX = view.findViewById(R.id.spinner_x);
        spinnerY = view.findViewById(R.id.spinner_y);
        btnPlot = view.findViewById(R.id.btn_plot);
        btnExportCSV = view.findViewById(R.id.btn_export_csv);

        viewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);

        setupSpinners();
        setupButtons();

        return view;
    }

    private void setupSpinners() {
        variableNames = dataCollector.getAvailableVariables();
        List<String> displayNames = new ArrayList<>();
        displayNames.add("время");
        for (String name : variableNames) {
            displayNames.add(name);
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, displayNames);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerX.setAdapter(adapter);
        spinnerY.setAdapter(adapter);
        spinnerX.setSelection(0);
        if (variableNames.size() > 0) spinnerY.setSelection(1);
    }

    private void setupButtons() {
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
                Toast.makeText(getContext(), "Нет данных для переменной " + varY, Toast.LENGTH_SHORT).show();
                return;
            }
        } else {
            entries = dataCollector.getScatterData(varX, varY);
            if (entries.isEmpty()) {
                Toast.makeText(getContext(), "Нет общих точек для " + varX + " и " + varY, Toast.LENGTH_SHORT).show();
                return;
            }
        }

        LineDataSet dataSet = new LineDataSet(entries, varY + " от " + varX);
        dataSet.setDrawValues(false);
        dataSet.setColor(getResources().getColor(R.color.purple_500));
        dataSet.setLineWidth(2f);
        dataSet.setCircleRadius(3f);
        dataSet.setCircleColor(getResources().getColor(R.color.purple_700));

        LineData lineData = new LineData(dataSet);
        chart.setData(lineData);

        XAxis xAxis = chart.getXAxis();
        if ("время".equals(varX)) {
            xAxis.setValueFormatter(new ValueFormatter() {
                private final SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
                @Override
                public String getFormattedValue(float value) {
                    return sdf.format(new Date((long) value));
                }
            });
            xAxis.setLabelRotationAngle(45);
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
            Toast.makeText(getContext(), "Нет данных для экспорта", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            String fileName = "graph_" + varY + "_vs_" + varX + "_" + System.currentTimeMillis() + ".csv";
            File csvFile = new File(requireContext().getExternalFilesDir(null), fileName);
            FileWriter writer = new FileWriter(csvFile);

            writer.append(varX).append(",").append(varY).append("\n");
            for (Entry entry : entries) {
                writer.append(String.valueOf(entry.getX()))
                        .append(",")
                        .append(String.valueOf(entry.getY()))
                        .append("\n");
            }
            writer.close();

            Toast.makeText(getContext(), "CSV сохранён: " + csvFile.getAbsolutePath(), Toast.LENGTH_LONG).show();
            shareCSVFile(csvFile);

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(getContext(), "Ошибка экспорта: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void shareCSVFile(File csvFile) {
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/csv");
        shareIntent.putExtra(Intent.EXTRA_STREAM, FileProvider.getUriForFile(requireContext(),
                BuildConfig.APPLICATION_ID + ".fileprovider", csvFile));
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(shareIntent, "Поделиться CSV"));
    }
}