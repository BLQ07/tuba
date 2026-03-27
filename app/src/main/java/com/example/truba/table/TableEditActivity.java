package com.example.truba;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.truba.table.DynamicTableView;
import com.example.truba.table.TableManager;

import java.util.ArrayList;
import java.util.List;

public class TableEditActivity extends AppCompatActivity {

    private String tableName;
    private DynamicTableView dynamicTableView;
    private TableManager tableManager;
    private EditText etTableName;
    private Button btnSave;
    private Button btnDeleteRow;
    private Button btnAddRow;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_able_edit);

        tableManager = new TableManager(this);
        tableName = getIntent().getStringExtra("table_name");

        etTableName = findViewById(R.id.et_table_name);
        dynamicTableView = findViewById(R.id.dynamicTableView);
        btnSave = findViewById(R.id.btn_save);
        btnDeleteRow = findViewById(R.id.btn_delete_row);
        btnAddRow = findViewById(R.id.btn_add_row);

        etTableName.setText(tableName);

        // Настраиваем DynamicTableView
        List<String> headers = tableManager.getHeaders(tableName);
        dynamicTableView.setPrefsName(tableManager.getPrefsNameForTable(tableName));
        dynamicTableView.setHeaders(headers);
        dynamicTableView.setOnDataChangedListener(() -> {
            // Данные изменились – можно ничего не делать, т.к. они автосохраняются
        });

        btnAddRow.setOnClickListener(v -> dynamicTableView.addRow());

        btnDeleteRow.setOnClickListener(v -> {
            // Простое удаление последней строки (можно улучшить с выбором)
            List<DynamicTableView.TableRowData> rows = dynamicTableView.getAllRows();
            if (!rows.isEmpty()) {
                dynamicTableView.deleteRow(rows.size() - 1);
            } else {
                Toast.makeText(this, "Нет строк для удаления", Toast.LENGTH_SHORT).show();
            }
        });

        btnSave.setOnClickListener(v -> {
            String newName = etTableName.getText().toString().trim();
            if (newName.isEmpty()) {
                Toast.makeText(this, "Имя таблицы не может быть пустым", Toast.LENGTH_SHORT).show();
                return;
            }
            // Проверяем, не занято ли имя (если изменилось)
            if (!newName.equals(tableName) && tableManager.getTableNames().contains(newName)) {
                Toast.makeText(this, "Таблица с таким именем уже существует", Toast.LENGTH_SHORT).show();
                return;
            }
            // Сохраняем изменения в TableManager
            Intent resultIntent = new Intent();
            resultIntent.putExtra("old_name", tableName);
            resultIntent.putExtra("new_name", newName);
            resultIntent.putStringArrayListExtra("headers", new ArrayList<>(dynamicTableView.getHeaders()));
            setResult(RESULT_OK, resultIntent);
            finish();
        });
    }
}