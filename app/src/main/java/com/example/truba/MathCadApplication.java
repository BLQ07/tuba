package com.example.truba;

import android.app.Application;

public class MathCadApplication extends Application {
    private static DataCollector dataCollector;

    @Override
    public void onCreate() {
        super.onCreate();
        dataCollector = new DataCollector();
    }

    public static DataCollector getDataCollector() {
        return dataCollector;
    }
}