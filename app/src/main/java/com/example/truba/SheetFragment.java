package com.example.truba;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

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

    private MainViewModel viewModel;
    private MainActivity activity;
    private SheetManager sheetManager;

    private static final int REQUEST_EDIT_SHEET = 300;

    @Override
    public void onAttach(@NonNull android.content.Context context) {
        super.onAttach(context);
        activity = (MainActivity) requireActivity();
        sheetManager = activity.getSheetManager();
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

        viewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);

        setupSheetSpinner();
        setupButtons();

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

        // Выбираем первый лист, если есть
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

    private void showDeleteSheetDialog(String sheetName) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Удалить лист")
                .setMessage("Вы уверены, что хотите удалить лист \"" + sheetName + "\"?")
                .setPositiveButton("Удалить", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        sheetManager.removeSheet(sheetName);
                        refreshSheetSpinner();
                        Toast.makeText(getContext(), "Лист \"" + sheetName + "\" удалён", Toast.LENGTH_SHORT).show();

                        // Очищаем текущее содержимое
                        Map<String, String> sheets = sheetManager.getAllSheets();
                        if (sheets.isEmpty()) {
                            viewModel.setCurrentSheetName("");
                            viewModel.setCurrentSheetContent("");
                            viewModel.setResults("");
                        }
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

        builder.setPositiveButton("Переименовать", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
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
            }
        });

        builder.setNegativeButton("Отмена", null);
        builder.show();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_EDIT_SHEET) {
            getActivity();
            if (resultCode == Activity.RESULT_OK && data != null) {
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
}