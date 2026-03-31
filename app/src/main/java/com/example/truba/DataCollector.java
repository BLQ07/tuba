package com.example.truba;

import android.util.Log;
import com.github.mikephil.charting.data.Entry;
import java.util.*;

public class DataCollector {
    private final Map<String, List<DataPoint>> data = new HashMap<>();
    private final int maxSize = 1000;
    private long startTime = 0; // время первого измерения (в миллисекундах)
    public List<Entry> getScatterData(String varX, String varY) {
        List<Entry> entries = new ArrayList<>();
        List<DataPoint> pointsX = data.get(varX);
        List<DataPoint> pointsY = data.get(varY);
        if (pointsX == null || pointsY == null) return entries;

        // Создаём карту для быстрого поиска Y по времени
        Map<Long, Double> yMap = new HashMap<>();
        for (DataPoint p : pointsY) {
            yMap.put(p.timestamp, p.value);
        }

        for (DataPoint pX : pointsX) {
            Double y = yMap.get(pX.timestamp);
            if (y != null) {
                entries.add(new Entry((float) pX.value, y.floatValue()));
            }
        }
        return entries;
    }
    private static class DataPoint {
        long timestamp; // абсолютное время (Unix ms)
        double value;
        DataPoint(long timestamp, double value) {
            this.timestamp = timestamp;
            this.value = value;
        }
    }

    public void addDataPoint(String variable, double value, long timestamp) {
        if (startTime == 0) {
            startTime = timestamp; // запоминаем время первого измерения
        }
        List<DataPoint> list = data.computeIfAbsent(variable, k -> new ArrayList<>());
        list.add(new DataPoint(timestamp, value));
        while (list.size() > maxSize) list.remove(0);
       // Log.d("DataCollector", "Добавлена точка " + variable + "=" + value + " timestamp=" + timestamp);
    }

    public void addData(Map<String, Double> newData) {
        long now = System.currentTimeMillis();
        for (Map.Entry<String, Double> entry : newData.entrySet()) {
            addDataPoint(entry.getKey(), entry.getValue(), now);
        }
    }

    /**
     * Возвращает временной ряд для переменной (X = относительное время в секундах от старта, Y = значение).
     */
    public List<Entry> getTimeSeries(String variable) {
        List<Entry> entries = new ArrayList<>();
        List<DataPoint> points = data.get(variable);
        if (points == null) return entries;
        for (DataPoint p : points) {
            float x = (float)((p.timestamp - startTime) / 1000.0);
            entries.add(new Entry(x, (float) p.value));
        }
        return entries;
    }

    /**
     * Возвращает scatter-данные для зависимости Y от X.
     * Синхронизирует по абсолютным временным меткам.
     */


    public List<String> getAvailableVariables() {
        return new ArrayList<>(data.keySet());
    }

    public void clear() {
        data.clear();
        startTime = 0;
      //  Log.d("DataCollector", "Очищено");
    }
}