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

public class ConstantsActivity extends AppCompatActivity {
    private ConstantsManager constantsManager;
    private List<ConstantsManager.ConstantInfo> constants;
    private ConstantsAdapter adapter;
    private ListView listView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_constants);

        constantsManager = new ConstantsManager(this);
        constants = new ArrayList<>(constantsManager.getAllConstants());

        listView = findViewById(R.id.listView);
        adapter = new ConstantsAdapter(constants, constantsManager, this::refreshList);
        listView.setAdapter(adapter);

        final EditText etName = findViewById(R.id.etConstName);
        final EditText etValue = findViewById(R.id.etConstValue);
        Button btnAdd = findViewById(R.id.btnAdd);
        Button btnClearAll = findViewById(R.id.btnClearAll);

        btnAdd.setOnClickListener(v -> {
            String name = etName.getText().toString().trim();
            String valStr = etValue.getText().toString().trim();
            if (name.isEmpty() || valStr.isEmpty()) {
                Toast.makeText(this, "Заполните имя и значение", Toast.LENGTH_SHORT).show();
                return;
            }
            try {
                double value = Double.parseDouble(valStr);
                showMinMaxDialog(name, value, 0, 100, true);
                etName.setText("");
                etValue.setText("");
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Неверное число", Toast.LENGTH_SHORT).show();
            }
        });

        btnClearAll.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("Очистить все константы?")
                    .setMessage("Все сохранённые константы будут удалены. Продолжить?")
                    .setPositiveButton("Да", (dialog, which) -> {
                        constantsManager.clearAll();
                        refreshList();
                    })
                    .setNegativeButton("Нет", null)
                    .show();
        });
    }

    private void refreshList() {
        constants.clear();
        constants.addAll(constantsManager.getAllConstants());
        adapter.notifyDataSetChanged();
    }

    private void showMinMaxDialog(String name, double value, double currentMin, double currentMax, boolean isNew) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(isNew ? "Диапазон для " + name : "Изменить диапазон " + name);
        View view = getLayoutInflater().inflate(R.layout.dialog_min_max, null);
        final EditText etMin = view.findViewById(R.id.et_min);
        final EditText etMax = view.findViewById(R.id.et_max);
        etMin.setText(String.valueOf(currentMin));
        etMax.setText(String.valueOf(currentMax));
        builder.setView(view);

        builder.setPositiveButton("Сохранить", (dialog, which) -> {
            double min, max;
            try {
                min = Double.parseDouble(etMin.getText().toString().trim());
                max = Double.parseDouble(etMax.getText().toString().trim());
                if (min >= max) {
                    Toast.makeText(ConstantsActivity.this, "Минимум должен быть меньше максимума", Toast.LENGTH_SHORT).show();
                    return;
                }
            } catch (NumberFormatException e) {
                Toast.makeText(ConstantsActivity.this, "Некорректные значения", Toast.LENGTH_SHORT).show();
                return;
            }
            constantsManager.saveConstant(name, value, min, max);
            refreshList();
            Toast.makeText(ConstantsActivity.this, "Константа сохранена", Toast.LENGTH_SHORT).show();
        });
        builder.setNegativeButton("Отмена", null);
        builder.show();
    }

    private static class ConstantsAdapter extends BaseAdapter {
        private List<ConstantsManager.ConstantInfo> constants;
        private ConstantsManager manager;
        private Runnable onDataChanged;

        ConstantsAdapter(List<ConstantsManager.ConstantInfo> constants, ConstantsManager manager, Runnable onDataChanged) {
            this.constants = constants;
            this.manager = manager;
            this.onDataChanged = onDataChanged;
        }

        @Override
        public int getCount() {
            return constants.size();
        }

        @Override
        public Object getItem(int position) {
            return constants.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(parent.getContext()).inflate(R.layout.constant_item, parent, false);
            }
            final ConstantsManager.ConstantInfo item = constants.get(position);
            final String name = item.name;
            final double value = item.value;
            final double min = item.min;
            final double max = item.max;

            TextView tvName = convertView.findViewById(R.id.tvConstName);
            final EditText etValue = convertView.findViewById(R.id.etConstValue);
            Button btnDelete = convertView.findViewById(R.id.btnDeleteConst);

            tvName.setText(name);
            etValue.setText(String.valueOf(value));

            etValue.setOnFocusChangeListener((v, hasFocus) -> {
                if (!hasFocus) {
                    try {
                        double newVal = Double.parseDouble(etValue.getText().toString());
                        manager.saveConstant(name, newVal, min, max);
                        constants.set(position, new ConstantsManager.ConstantInfo(name, newVal, min, max));
                        onDataChanged.run();
                    } catch (NumberFormatException e) {
                        etValue.setText(String.valueOf(value));
                    }
                }
            });

            btnDelete.setOnClickListener(v -> {
                manager.removeConstant(name);
                constants.remove(position);
                onDataChanged.run();
                notifyDataSetChanged();
            });

            return convertView;
        }
    }
}