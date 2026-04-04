package com.example.truba;

import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.truba.table.DynamicTableView;
import com.example.truba.table.TableManager;

public class TableEditorActivity extends AppCompatActivity {

    private DynamicTableView dynamicTableView;
    private TableManager tableManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_able_edit);

        String tableName = getIntent().getStringExtra("table_name");
        if (tableName == null) {
            Toast.makeText(this, "Ошибка: имя таблицы не передано", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        tableManager = new TableManager(this);
        dynamicTableView = findViewById(R.id.dynamicTableView);
        dynamicTableView.setPrefsName(tableManager.getPrefsNameForTable(tableName));
        dynamicTableView.setHeaders(tableManager.getHeaders(tableName));
        dynamicTableView.setOnDataChangedListener(() -> {
            // опционально: обновить что-то при изменении данных
        });

        setTitle(tableName);
    }
}