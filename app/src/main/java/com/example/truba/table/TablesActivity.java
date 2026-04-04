package com.example.truba.table;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.truba.R;
import com.example.truba.table.TableManager;

import java.util.ArrayList;
import java.util.List;

public class TablesActivity extends AppCompatActivity {

    private TableManager tableManager;
    private List<String> tableNames;
    private ArrayAdapter<String> adapter;
    private ListView listView;

    private static final int REQUEST_EDIT_TABLE = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tables);

        tableManager = new TableManager(this);
        tableNames = new ArrayList<>(tableManager.getTableNames());

        listView = findViewById(R.id.listView);
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, tableNames);
        listView.setAdapter(adapter);

        Button btnAdd = findViewById(R.id.btnAdd);
        btnAdd.setOnClickListener(v -> showCreateTableDialog());

        listView.setOnItemClickListener((parent, view, position, id) -> {
            String name = tableNames.get(position);
            openTableEditor(name);
        });

        listView.setOnItemLongClickListener((parent, view, position, id) -> {
            final String name = tableNames.get(position);
            new AlertDialog.Builder(this)
                    .setTitle("Удалить таблицу?")
                    .setMessage("Таблица \"" + name + "\" будет удалена. Продолжить?")
                    .setPositiveButton("Да", (dialog, which) -> {
                        tableManager.removeTable(name);
                        refreshList();
                    })
                    .setNegativeButton("Нет", null)
                    .show();
            return true;
        });
    }

    private void refreshList() {
        tableNames.clear();
        tableNames.addAll(tableManager.getTableNames());
        adapter.notifyDataSetChanged();
    }

    private void showCreateTableDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Создать таблицу");
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 20, 50, 20);

        EditText etName = new EditText(this);
        etName.setHint("Имя таблицы");
        layout.addView(etName);

        EditText etHeaders = new EditText(this);
        etHeaders.setHint("Заголовки через запятую (например: name,value)");
        layout.addView(etHeaders);

        builder.setView(layout);
        builder.setPositiveButton("Создать", (dialog, which) -> {
            String name = etName.getText().toString().trim();
            String headersStr = etHeaders.getText().toString().trim();
            if (name.isEmpty() || headersStr.isEmpty()) {
                Toast.makeText(this, "Заполните оба поля", Toast.LENGTH_SHORT).show();
                return;
            }
            List<String> headers = java.util.Arrays.asList(headersStr.split(","));
            for (int i = 0; i < headers.size(); i++) headers.set(i, headers.get(i).trim());
            tableManager.addTable(name, headers);
            refreshList();
            Toast.makeText(this, "Таблица создана", Toast.LENGTH_SHORT).show();
        });
        builder.setNegativeButton("Отмена", null);
        builder.show();
    }

    private void openTableEditor(String tableName) {
        Intent intent = new Intent(this, TableEditActivity.class);
        intent.putExtra("table_name", tableName);
        startActivityForResult(intent, REQUEST_EDIT_TABLE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_EDIT_TABLE && resultCode == RESULT_OK && data != null) {
            String oldName = data.getStringExtra("old_name");
            String newName = data.getStringExtra("new_name");
            List<String> headers = data.getStringArrayListExtra("headers");
            if (oldName != null && newName != null && headers != null) {
                tableManager.updateTable(oldName, newName, headers);
                refreshList();
            }
        }
    }
}