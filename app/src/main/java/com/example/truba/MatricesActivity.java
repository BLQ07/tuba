package com.example.truba;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MatricesActivity extends AppCompatActivity {
    private MatrixManager matrixManager;
    private ListView listView;
    private ArrayAdapter<String> adapter;
    private List<String> matrixNames;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_matrices);

        matrixManager = new MatrixManager(this);
        matrixNames = new ArrayList<>(matrixManager.getAllMatrices().keySet());
        listView = findViewById(R.id.listView);
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, matrixNames);
        listView.setAdapter(adapter);

        Button btnAdd = findViewById(R.id.btnAddMatrix);
        btnAdd.setOnClickListener(v -> showCreateMatrixDialog());

        listView.setOnItemClickListener((parent, view, position, id) -> {
            String name = matrixNames.get(position);
            showMatrixInfo(name);
        });

        listView.setOnItemLongClickListener((parent, view, position, id) -> {
            String name = matrixNames.get(position);
            new AlertDialog.Builder(this)
                    .setTitle("Удалить матрицу?")
                    .setMessage("Матрица \"" + name + "\" будет удалена")
                    .setPositiveButton("Удалить", (dialog, which) -> {
                        matrixManager.removeMatrix(name);
                        refreshList();
                    })
                    .setNegativeButton("Отмена", null)
                    .show();
            return true;
        });
    }

    private void showCreateMatrixDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Создать матрицу");
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 20, 50, 20);

        EditText etName = new EditText(this);
        etName.setHint("Имя матрицы (A, B, ...)");
        layout.addView(etName);

        EditText etRows = new EditText(this);
        etRows.setHint("Количество строк");
        etRows.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        layout.addView(etRows);

        EditText etCols = new EditText(this);
        etCols.setHint("Количество столбцов");
        etCols.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        layout.addView(etCols);

        builder.setView(layout);
        builder.setPositiveButton("Далее", (dialog, which) -> {
            String name = etName.getText().toString().trim();
            String rowsStr = etRows.getText().toString().trim();
            String colsStr = etCols.getText().toString().trim();
            if (name.isEmpty() || rowsStr.isEmpty() || colsStr.isEmpty()) {
                Toast.makeText(this, "Заполните все поля", Toast.LENGTH_SHORT).show();
                return;
            }
            int rows = Integer.parseInt(rowsStr);
            int cols = Integer.parseInt(colsStr);
            showMatrixValueDialog(name, rows, cols);
        });
        builder.setNegativeButton("Отмена", null);
        builder.show();
    }

    private void showMatrixValueDialog(String name, int rows, int cols) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Введите значения матрицы " + name);
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(50, 20, 50, 20);

        EditText[][] fields = new EditText[rows][cols];
        for (int i = 0; i < rows; i++) {
            LinearLayout rowLayout = new LinearLayout(this);
            rowLayout.setOrientation(LinearLayout.HORIZONTAL);
            for (int j = 0; j < cols; j++) {
                EditText et = new EditText(this);
                et.setHint("(" + (i + 1) + "," + (j + 1) + ")");
                et.setInputType(android.text.InputType.TYPE_CLASS_NUMBER |
                        android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL |
                        android.text.InputType.TYPE_NUMBER_FLAG_SIGNED);
                et.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
                rowLayout.addView(et);
                fields[i][j] = et;
            }
            container.addView(rowLayout);
        }

        builder.setView(container);
        builder.setPositiveButton("Сохранить", (dialog, which) -> {
            double[][] matrix = new double[rows][cols];
            try {
                for (int i = 0; i < rows; i++) {
                    for (int j = 0; j < cols; j++) {
                        String valStr = fields[i][j].getText().toString().trim();
                        matrix[i][j] = valStr.isEmpty() ? 0 : Double.parseDouble(valStr);
                    }
                }
                matrixManager.saveMatrix(name, matrix);
                refreshList();
                Toast.makeText(this, "Матрица сохранена", Toast.LENGTH_SHORT).show();
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Ошибка ввода", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Отмена", null);
        builder.show();
    }

    private void showMatrixInfo(String name) {
        double[][] matrix = matrixManager.getMatrix(name);
        if (matrix == null) return;
        StringBuilder sb = new StringBuilder();
        sb.append("Размер: ").append(matrix.length).append("×").append(matrix[0].length).append("\n\n");
        for (int i = 0; i < Math.min(matrix.length, 10); i++) {
            for (int j = 0; j < Math.min(matrix[0].length, 10); j++) {
                sb.append(String.format("%8.4f", matrix[i][j]));
            }
            sb.append("\n");
        }
        if (matrix.length > 10 || matrix[0].length > 10) sb.append("...\n");
        new AlertDialog.Builder(this)
                .setTitle("Матрица " + name)
                .setMessage(sb.toString())
                .setPositiveButton("OK", null)
                .show();
    }

    private void refreshList() {
        matrixNames.clear();
        matrixNames.addAll(matrixManager.getAllMatrices().keySet());
        adapter.notifyDataSetChanged();
    }
          }
