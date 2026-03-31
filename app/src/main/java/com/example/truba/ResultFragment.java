package com.example.truba;

import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

public class ResultFragment extends Fragment {

    private TextView tvResults;
    private Spinner spinnerDisplayMode;
    private MainViewModel viewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_result, container, false);

        tvResults = view.findViewById(R.id.tv_results);
        spinnerDisplayMode = view.findViewById(R.id.spinner_display_mode);

        viewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);

        // Делаем TextView копируемым
        tvResults.setTextIsSelectable(true);
        tvResults.setOnLongClickListener(v -> {
            requireContext();
            android.content.ClipboardManager clipboard = (android.content.ClipboardManager) requireActivity().getSystemService(Context.CLIPBOARD_SERVICE);
            android.content.ClipData clip = android.content.ClipData.newPlainText("results", tvResults.getText().toString());
            clipboard.setPrimaryClip(clip);
            Toast.makeText(getContext(), "Результаты скопированы", Toast.LENGTH_SHORT).show();
            return true;
        });

        viewModel.getResults().observe(getViewLifecycleOwner(), results -> {
            tvResults.setText(results);
        });

        // Настройка спиннера для переключения режимов
        String[] modes = {"Результаты", "Лог", "Терминал"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, modes);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerDisplayMode.setAdapter(adapter);

        spinnerDisplayMode.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                // Здесь можно переключать режим отображения
                // Пока просто обновляем текст
                String mode = modes[position];
                if (mode.equals("Результаты")) {
                    tvResults.setText(viewModel.getResults().getValue());
                } else if (mode.equals("Лог")) {
                    // Можно добавить логирование
                } else if (mode.equals("Терминал")) {
                    // Можно добавить терминал
                }
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        return view;
    }
}