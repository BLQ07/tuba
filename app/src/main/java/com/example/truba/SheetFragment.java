package com.example.truba;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.truba.table.DynamicTableView;
import com.example.truba.table.TableManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SheetFragment extends Fragment {

    private Spinner spinnerSheets;
    private TextView tvConstants;
    private TextView tvSessionConstants;
    private Button btnCreateSheet;
    private Button btnEditSheet;
    private Button btnDeleteSheet;
    private Button btnRenameSheet;

    // Элементы таблиц
    private Spinner spinnerTables;
    private Spinner spinnerTableRows;
    private Button btnAddConstantFromTable;
    private Button btnCreateTable;

    private MainViewModel viewModel;
    private MainActivity activity;
    private SheetManager sheetManager;
    private TableManager tableManager;

    private static final int REQUEST_EDIT_SHEET = 300;

    @Override
    public void onAttach(@NonNull android.content.Context context) {
        super.onAttach(context);
        activity = (MainActivity) requireActivity();
        sheetManager = activity.getSheetManager();
        tableManager = activity.getTableManager();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_sheet, container, false);

        spinnerSheets = view.findViewById(R.id.spinner_sheets);
        tvConstants = view.findViewById(R.id.tv_constants);
        tvSessionConstants = view.findViewById(R.id.tv_session_constants);
        btnCreateSheet = view.findViewById(R.id.btn_create_sheet);
        btnEditSheet = view.findViewById(R.id.btn_edit_sheet);
        btnDeleteSheet = view.findViewById(R.id.btn_delete_sheet);
        btnRenameSheet = view.findViewById(R.id.btn_rename_sheet);

        // Таблицы
        spinnerTables = view.findViewById(R.id.spinner_tables);
        spinnerTableRows = view.findViewById(R.id.spinner_table_rows);
        btnAddConstantFromTable = view.findViewById(R.id.btn_add_constant_from_table);
        btnCreateTable = view.findViewById(R.id.btn_create_table);

        viewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);

        setupSheetSpinner();
        setupButtons();
        setupTableUI();

        viewModel.getConstantInfos().observe(getViewLifecycleOwner(), infos -> {
            if (infos == null) return;
            StringBuilder sb = new StringBuilder();
            for (ConstantsManager.ConstantInfo info : infos) {
                sb.append(info.name).append(" = ").append(info.value).append("\n");
            }
            tvConstants.setText(sb.toString());
        });

        viewModel.getSessionConstants().observe(getViewLifecycleOwner(), session -> {
            if (session == null) return;
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, Double> entry : session.entrySet()) {
                sb.append(entry.getKey()).append(" = ").append(entry.getValue()).append("\n");
            }
            tvSessionConstants.setText(sb.toString());
        });

        return view;
    }

    private void setupSheetSpinner() {
        refreshSheetSpinner();

        spinnerSheets.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String name = (String) parent.getItemAtPosition(position);
                Map<String, String> sheets = viewModel.getSheets().getValue();
                if (name != null && !name.equals("Нет листов") && sheets != null && sheets.containsKey(name)) {
                    String content = sheets.get(name);
                    viewModel.setCurrentSheetName(name);
                    viewModel.setCurrentSheetContent(content);
                    activity.updateSheetComputation(content);
                }
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void refreshSheetSpinner() {
        Map<String, String> sheets = sheetManager.getAllSheets();
        viewModel.setSheets(sheets);

        List<String> sheetNames = new ArrayList<>(sheets.keySet());
        if (sheetNames.isEmpty()) sheetNames.add("Нет листов");
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, sheetNames);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerSheets.setAdapter(adapter);

        if (!sheetNames.isEmpty() && !sheetNames.get(0).equals("Нет листов")) {
            spinnerSheets.setSelection(0);
            viewModel.setCurrentSheetName(sheetNames.get(0));
            viewModel.setCurrentSheetContent(sheets.get(sheetNames.get(0)));
            activity.updateSheetComputation(sheets.get(sheetNames.get(0)));
        }
    }

    private void setupButtons() {
        btnCreateSheet.setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), SheetEditorActivity.class);
            startActivityForResult(intent, REQUEST_EDIT_SHEET);
        });

        btnEditSheet.setOnClickListener(v -> {
            String selectedSheet = (String) spinnerSheets.getSelectedItem();
            if (selectedSheet == null || selectedSheet.equals("Нет листов")) {
                Toast.makeText(getContext(), "Выберите лист для редактирования", Toast.LENGTH_SHORT).show();
                return;
            }
            String content = sheetManager.getSheet(selectedSheet);
            Intent intent = new Intent(requireContext(), SheetEditorActivity.class);
            intent.putExtra("sheet_name", selectedSheet);
            intent.putExtra("sheet_content", content);
            startActivityForResult(intent, REQUEST_EDIT_SHEET);
        });

        btnDeleteSheet.setOnClickListener(v -> {
            String selectedSheet = (String) spinnerSheets.getSelectedItem();
            if (selectedSheet == null || selectedSheet.equals("Нет листов")) {
                Toast.makeText(getContext(), "Выберите лист для удаления", Toast.LENGTH_SHORT).show();
                return;
            }
            showDeleteSheetDialog(selectedSheet);
        });

        btnRenameSheet.setOnClickListener(v -> {
            String selectedSheet = (String) spinnerSheets.getSelectedItem();
            if (selectedSheet == null || selectedSheet.equals("Нет листов")) {
                Toast.makeText(getContext(), "Выберите лист для переименования", Toast.LENGTH_SHORT).show();
                return;
            }
            showRenameSheetDialog(selectedSheet);
        });
    }

    // ==================== Таблицы ====================

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
                Toast.makeText(getContext(), "Выберите таблицу и строку", Toast.LENGTH_SHORT).show();
                return;
            }

            DynamicTableView tempView = new DynamicTableView(requireContext());
            tempView.setPrefsName(tableManager.getPrefsNameForTable(tableName));
            List<String> headers = tableManager.getHeaders(tableName);
            if (headers == null) return;
            tempView.setHeaders(headers);
            tempView.refreshData();

            int nameIndex = headers.indexOf("name");
            if (nameIndex == -1) {
                Toast.makeText(getContext(), "Таблица должна содержать столбец 'name'", Toast.LENGTH_SHORT).show();
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
                        activity.addSessionConstant(colName, val);
                        addedCount++;
                    } catch (NumberFormatException ignored) {}
                }
                if (addedCount > 0) {
                    Toast.makeText(getContext(), "Добавлено " + addedCount + " временных констант", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(getContext(), "Нет числовых значений", Toast.LENGTH_SHORT).show();
                }
            }
        });

        btnCreateTable.setOnClickListener(v -> showCreateTableDialog());
    }

    private void refreshTableSpinner() {
        List<String> tableNames = tableManager.getTableNames();
        if (tableNames.isEmpty()) tableNames.add("Нет таблиц");
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, tableNames);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerTables.setAdapter(adapter);
    }

    private void refreshRowSpinner(String tableName) {
        List<String> rowNames = new ArrayList<>();
        DynamicTableView tempView = new DynamicTableView(requireContext());
        tempView.setPrefsName(tableManager.getPrefsNameForTable(tableName));
        List<String> headers = tableManager.getHeaders(tableName);
        if (headers == null) return;
        tempView.setHeaders(headers);
        tempView.refreshData();

        int nameIndex = headers.indexOf("name");
        if (nameIndex == -1) return;

        for (DynamicTableView.TableRowData row : tempView.getAllRows()) {
            if (row.values != null && row.values.length > nameIndex && row.values[nameIndex] != null && !row.values[nameIndex].isEmpty()) {
                rowNames.add(row.values[nameIndex]);
            }
        }
        if (rowNames.isEmpty()) rowNames.add("Нет строк");
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, rowNames);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerTableRows.setAdapter(adapter);
    }

    private void showCreateTableDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("Создать таблицу");
        LinearLayout layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 20, 50, 20);

        EditText etName = new EditText(requireContext());
        etName.setHint("Имя таблицы");
        layout.addView(etName);

        EditText etHeaders = new EditText(requireContext());
        etHeaders.setHint("Заголовки через запятую (например: name,value)");
        layout.addView(etHeaders);

        builder.setView(layout);
        builder.setPositiveButton("Создать", (dialog, which) -> {
            String name = etName.getText().toString().trim();
            String headersStr = etHeaders.getText().toString().trim();
            if (name.isEmpty() || headersStr.isEmpty()) {
                Toast.makeText(getContext(), "Заполните оба поля", Toast.LENGTH_SHORT).show();
                return;
            }
            List<String> headers = java.util.Arrays.asList(headersStr.split(","));
            for (int i = 0; i < headers.size(); i++) headers.set(i, headers.get(i).trim());
            tableManager.addTable(name, headers);
            refreshTableSpinner();
            Toast.makeText(getContext(), "Таблица создана", Toast.LENGTH_SHORT).show();
        });
        builder.setNegativeButton("Отмена", null);
        builder.show();
    }

    // ==================== Диалоги удаления/переименования ====================

    private void showDeleteSheetDialog(String sheetName) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Удалить лист")
                .setMessage("Вы уверены, что хотите удалить лист \"" + sheetName + "\"?")
                .setPositiveButton("Удалить", (dialog, which) -> {
                    sheetManager.removeSheet(sheetName);
                    refreshSheetSpinner();
                    Toast.makeText(getContext(), "Лист \"" + sheetName + "\" удалён", Toast.LENGTH_SHORT).show();
                    if (sheetManager.getAllSheets().isEmpty()) {
                        viewModel.setCurrentSheetName("");
                        viewModel.setCurrentSheetContent("");
                        viewModel.setResults("");
                    }
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void showRenameSheetDialog(String oldName) {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("Переименовать лист");

        LinearLayout layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 20, 50, 20);

        EditText inputName = new EditText(requireContext());
        inputName.setText(oldName);
        inputName.setHint("Новое имя листа");
        inputName.setInputType(InputType.TYPE_CLASS_TEXT);
        layout.addView(inputName);

        builder.setView(layout);

        builder.setPositiveButton("Переименовать", (dialog, which) -> {
            String newName = inputName.getText().toString().trim();
            if (newName.isEmpty()) {
                Toast.makeText(getContext(), "Введите имя листа", Toast.LENGTH_SHORT).show();
                return;
            }
            if (newName.equals(oldName)) {
                Toast.makeText(getContext(), "Имя не изменено", Toast.LENGTH_SHORT).show();
                return;
            }
            if (sheetManager.getAllSheets().containsKey(newName)) {
                Toast.makeText(getContext(), "Лист с таким именем уже существует", Toast.LENGTH_SHORT).show();
                return;
            }
            String content = sheetManager.getSheet(oldName);
            sheetManager.removeSheet(oldName);
            sheetManager.saveSheet(newName, content);
            refreshSheetSpinner();
            Toast.makeText(getContext(), "Лист переименован в \"" + newName + "\"", Toast.LENGTH_SHORT).show();
        });

        builder.setNegativeButton("Отмена", null);
        builder.show();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_EDIT_SHEET && resultCode == getActivity().RESULT_OK && data != null) {
            String name = data.getStringExtra("sheet_name");
            String content = data.getStringExtra("sheet_content");
            if (name != null && !name.isEmpty() && content != null) {
                sheetManager.saveSheet(name, content);
                refreshSheetSpinner();
                Toast.makeText(getContext(), "Лист \"" + name + "\" сохранён", Toast.LENGTH_SHORT).show();
            }
        }
    }
}