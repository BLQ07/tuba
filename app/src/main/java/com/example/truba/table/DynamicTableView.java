package com.example.truba.table;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.truba.R;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class DynamicTableView extends LinearLayout {

    private LinearLayout headerContainer;
    private RecyclerView recyclerView;
    private Button btnAddRow;
    private HorizontalScrollView scrollView;

    private final List<String> headers = new ArrayList<>();
    private final List<TableRowData> data = new ArrayList<>();

    private TableAdapter adapter;
    private long nextId = 1;

    private SharedPreferences prefs;
    private String prefsName;

    private final int colorA = Color.WHITE;
    private final int colorB = Color.parseColor("#F0F0F0");
    private int columnWidthPx;

    private OnDataChangedListener dataChangedListener;

    public interface OnDataChangedListener {
        void onDataChanged();
    }

    public DynamicTableView(Context context) {
        super(context);
        init(context);
    }

    public DynamicTableView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        setOrientation(VERTICAL);
        LayoutInflater.from(context).inflate(R.layout.view_dynamic_table, this, true);

        scrollView = findViewById(R.id.horizontalScroll);
        headerContainer = findViewById(R.id.headerContainer);
        recyclerView = findViewById(R.id.recyclerView);
        btnAddRow = findViewById(R.id.btnAddRow);

        columnWidthPx = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 150, getResources().getDisplayMetrics());

        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        adapter = new TableAdapter();
        recyclerView.setAdapter(adapter);

        btnAddRow.setOnClickListener(v -> addRow());
    }

    public void setPrefsName(String prefsName) {
        this.prefsName = prefsName;
        this.prefs = getContext().getSharedPreferences(prefsName, Context.MODE_PRIVATE);
    }

    public void setHeaders(List<String> headers) {
        if (headers == null || headers.isEmpty()) {
            throw new IllegalArgumentException("Headers cannot be empty");
        }

        this.headers.clear();
        this.headers.addAll(headers);

        buildHeaders();
        loadData(); // загружаем данные после установки заголовков
    }

    public List<String> getHeaders() {
        return new ArrayList<>(headers);
    }

    public List<TableRowData> getAllRows() {
        return new ArrayList<>(data);
    }

    public TableRowData getRowByColumnValue(String columnName, String value) {
        int colIndex = headers.indexOf(columnName);
        if (colIndex == -1) return null;
        for (TableRowData row : data) {
            if (row.values != null && row.values.length > colIndex && row.values[colIndex].equals(value)) {
                return row;
            }
        }
        return null;
    }

    public void addRow() {
        TableRowData row = new TableRowData();
        row.id = nextId++;
        row.values = new String[headers.size()];
        for (int i = 0; i < headers.size(); i++) row.values[i] = "";

        data.add(row);
        adapter.notifyItemInserted(data.size() - 1);
        saveAll();
        if (dataChangedListener != null) dataChangedListener.onDataChanged();
    }

    public void deleteRow(int position) {
        if (position >= 0 && position < data.size()) {
            data.remove(position);
            adapter.notifyItemRemoved(position);
            saveAll();
            if (dataChangedListener != null) dataChangedListener.onDataChanged();
        }
    }

    public void setOnDataChangedListener(OnDataChangedListener listener) {
        this.dataChangedListener = listener;
    }

    public void refreshData() {
        loadData(); // принудительная перезагрузка данных из SharedPreferences
    }

    private void buildHeaders() {
        headerContainer.removeAllViews();

        headerContainer.addView(createCell("ID", true));
        for (String h : headers) {
            headerContainer.addView(createCell(h, true));
        }
    }

    private View createCell(String text, boolean isHeader) {
        TextView tv = new TextView(getContext());
        tv.setTextColor(Color.BLACK);
        tv.setText(text);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(columnWidthPx, LinearLayout.LayoutParams.WRAP_CONTENT);
        tv.setLayoutParams(params);
        tv.setPadding(16, 16, 16, 16);

        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(isHeader ? Color.parseColor("#DDDDDD") : Color.TRANSPARENT);
        drawable.setStroke(2, Color.GRAY);
        tv.setBackground(drawable);

        return tv;
    }

    private void saveAll() {
        try {
            JSONArray arr = new JSONArray();
            for (TableRowData row : data) {
                JSONObject obj = new JSONObject();
                obj.put("id", row.id);
                JSONArray vals = new JSONArray();
                for (String v : row.values) vals.put(v);
                obj.put("values", vals);
                arr.put(obj);
            }
            prefs.edit()
                    .putString("table_data", arr.toString())
                    .putLong("next_id", nextId)
                    .apply();
        } catch (Exception ignored) {}
    }

    private void loadData() {
        data.clear();
        try {
            String json = prefs.getString("table_data", null);
            nextId = prefs.getLong("next_id", 1);
            if (json == null) return;

            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                TableRowData row = new TableRowData();
                row.id = obj.getLong("id");
                JSONArray vals = obj.getJSONArray("values");
                row.values = new String[headers.size()];
                for (int j = 0; j < headers.size(); j++) {
                    row.values[j] = j < vals.length() ? vals.getString(j) : "";
                }
                data.add(row);
            }
            adapter.notifyDataSetChanged();
        } catch (Exception ignored) {}
    }

    private class TableAdapter extends RecyclerView.Adapter<TableAdapter.ViewHolder> {

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LinearLayout rowLayout = new LinearLayout(parent.getContext());
            rowLayout.setOrientation(HORIZONTAL);

            List<EditText> edits = new ArrayList<>();

            TextView idView = new TextView(parent.getContext());
            LinearLayout.LayoutParams idParams = new LinearLayout.LayoutParams(columnWidthPx, LinearLayout.LayoutParams.WRAP_CONTENT);
            idView.setLayoutParams(idParams);
            idView.setPadding(16, 16, 16, 16);
            rowLayout.addView(idView);

            for (int i = 0; i < headers.size(); i++) {
                EditText et = new EditText(parent.getContext());
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(columnWidthPx, LinearLayout.LayoutParams.WRAP_CONTENT);
                et.setLayoutParams(params);
                et.setPadding(16, 16, 16, 16);
                edits.add(et);
                rowLayout.addView(et);
            }

            return new ViewHolder(rowLayout, idView, edits);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            TableRowData row = data.get(position);

            holder.idView.setText(String.valueOf(row.id));
            holder.idView.setBackground(createCellBg(position, 0));

            for (int i = 0; i < holder.editTexts.size(); i++) {
                EditText et = holder.editTexts.get(i);
                int col = i + 1;
                et.setText(row.values[i]);
                et.setTextColor(Color.BLACK);
                et.setBackground(createCellBg(position, col));

                int finalI = i;
                et.setOnFocusChangeListener((v, hasFocus) -> {
                    if (!hasFocus) {
                        row.values[finalI] = et.getText().toString();
                        saveAll();
                        if (dataChangedListener != null) dataChangedListener.onDataChanged();
                    }
                });
            }
        }

        private GradientDrawable createCellBg(int row, int col) {
            GradientDrawable drawable = new GradientDrawable();
            int bg = ((row + col) % 2 == 0) ? colorA : colorB;
            drawable.setColor(bg);
            drawable.setStroke(2, Color.BLACK);
            return drawable;
        }

        @Override
        public int getItemCount() {
            return data.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView idView;
            List<EditText> editTexts;

            ViewHolder(View itemView, TextView idView, List<EditText> edits) {
                super(itemView);
                this.idView = idView;
                this.editTexts = edits;
            }
        }
    }

    public static class TableRowData {
        public long id;
        public String[] values;
    }
}