package com.example.truba;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.Map;

public class SheetsActivity extends AppCompatActivity {
    private SheetManager sheetManager;
    private ArrayAdapter<String> adapter;
    private ArrayList<String> sheetNames;

    private static final int REQUEST_EDIT_SHEET = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sheets);

        sheetManager = new SheetManager(this);
        sheetNames = new ArrayList<>(sheetManager.getAllSheets().keySet());
        ListView listView = findViewById(R.id.listView);
        adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, sheetNames) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                TextView textView = view.findViewById(android.R.id.text1);
                textView.setSingleLine(false);
                textView.setMaxLines(3);
                return view;
            }
        };
        listView.setAdapter(adapter);

        Button btnAdd = findViewById(R.id.btnAdd);
        btnAdd.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openSheetEditor(null, null);
            }
        });

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                String name = sheetNames.get(position);
                String content = sheetManager.getSheet(name);
                Log.d("SheetsActivity", "Передаём в редактор: name=" + name + ", content=" + content);
                openSheetEditor(name, content);
            }
        });

        listView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                final String name = sheetNames.get(position);
                new AlertDialog.Builder(SheetsActivity.this)
                        .setTitle("Удалить лист?")
                        .setMessage("Лист \"" + name + "\" будет удалён. Продолжить?")
                        .setPositiveButton("Да", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                sheetManager.removeSheet(name);
                                refreshList();
                            }
                        })
                        .setNegativeButton("Нет", null)
                        .show();
                return true;
            }
        });
    }

    private void openSheetEditor(String name, String content) {
        Intent intent = new Intent(this, SheetEditorActivity.class);
        if (name != null) {
            intent.putExtra("sheet_name", name);
            intent.putExtra("sheet_content", content);
        }
        startActivityForResult(intent, REQUEST_EDIT_SHEET);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_EDIT_SHEET && resultCode == RESULT_OK && data != null) {
            String name = data.getStringExtra("sheet_name");
            String content = data.getStringExtra("sheet_content");
            if (name != null && !name.isEmpty() && content != null) {
                sheetManager.saveSheet(name, content);
                refreshList();
            }
        }
    }

    private void refreshList() {
        sheetNames.clear();
        sheetNames.addAll(sheetManager.getAllSheets().keySet());
        adapter.notifyDataSetChanged();
    }
}