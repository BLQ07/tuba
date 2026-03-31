package com.example.truba;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.preference.PreferenceManager;
import android.util.Log;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.Map;

public class ESPConnector {
    private static String ESP_SSID = "MathCad_ESP";
    private static String ESP_PASSWORD = "12345678";
    private static final String ESP_IP = "192.168.4.1";

    private final Context context;
    private final RequestQueue requestQueue;
    private final Gson gson;
    private PollingRunnable pollingRunnable;
    private final android.os.Handler handler;
    private int interval = 1000;
    private boolean isPolling = false;
    private DataCallback savedCallback;

    public static void log(String s) {
        String http = "http://" + ESP_IP + "/log";
        StringRequest stringRequest = new StringRequest(Request.Method.POST, http, new Response.Listener<String>() {
            @Override
            public void onResponse(String response) {
                Log.d("ESPConnector", "Log response: " + response);
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
            }
        }
        );

    }


    public interface DataCallback {
        void onDataReceived(Map<String, Double> data);
    }

    public ESPConnector(Context context) {
        this.context = context;
        requestQueue = Volley.newRequestQueue(context);
        gson = new Gson();
        handler = new android.os.Handler();
        loadSettings();
    }

    private void loadSettings() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String ssid = prefs.getString("esp_ssid", "MathCad_ESP");
        String password = prefs.getString("esp_password", "12345678");
        ESP_SSID = ssid;
        ESP_PASSWORD = password;
    }

    public static void updateSettings(String ssid, String password) {
        ESP_SSID = ssid;
        ESP_PASSWORD = password;
    }

    public boolean isConnectedToESP() {
        ConnectivityManager connManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo wifiInfo = connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        if (wifiInfo != null && wifiInfo.isConnected()) {
            WifiManager wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            WifiInfo connectionInfo = wifiManager.getConnectionInfo();
            String ssid = connectionInfo.getSSID();
            if (ssid != null && ssid.startsWith("\"") && ssid.endsWith("\"")) {
                ssid = ssid.substring(1, ssid.length() - 1);
            }
            return ESP_SSID.equals(ssid);
        }
        return false;
    }

    public String getCurrentIp() {
        if (isConnectedToESP()) return ESP_IP;
        return null;
    }

    public WifiConfiguration getESPWifiConfig() {
        WifiConfiguration config = new WifiConfiguration();
        config.SSID = "\"" + ESP_SSID + "\"";
        config.preSharedKey = "\"" + ESP_PASSWORD + "\"";
        config.status = WifiConfiguration.Status.ENABLED;
        config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);
        return config;
    }

    public void startPolling(DataCallback callback) {
        if (!isConnectedToESP()) {
            Log.d("ESPConnector", "Not connected to ESP network");
            return;
        }
        stopPolling();
        this.savedCallback = callback;
        isPolling = true;
        pollingRunnable = new PollingRunnable();
        handler.post(pollingRunnable);
    }

    public void stopPolling() {
        isPolling = false;
        if (pollingRunnable != null) {
            handler.removeCallbacks(pollingRunnable);
            pollingRunnable = null;
        }
    }

    public void setPollInterval(int intervalMs) {
        this.interval = intervalMs;
        if (isPolling) {
            stopPolling();
            startPolling(savedCallback);
        }
    }

    public int getPollInterval() {
        return interval;
    }

    public void sendVariable(String varName, double value) {
        if (!isConnectedToESP()) return;
        String url = "http://" + ESP_IP + "/set?var=" + varName + "&value=" + value;
        StringRequest request = new StringRequest(Request.Method.GET, url,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        Log.d("ESPConnector", "Variable sent: " + varName + "=" + value);
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        Log.e("ESPConnector", "Error sending variable", error);
                    }
                });
        requestQueue.add(request);
    }

    private class PollingRunnable implements Runnable {
        @Override
        public void run() {
            if (!isPolling) return;
            String url = "http://" + ESP_IP + "/data";
            StringRequest request = new StringRequest(Request.Method.GET, url,
                    new Response.Listener<String>() {
                        @Override
                        public void onResponse(String response) {
                            try {
                                Type type = new TypeToken<Map<String, Double>>(){}.getType();
                                Map<String, Double> data = gson.fromJson(response, type);
                                if (savedCallback != null) {
                                    savedCallback.onDataReceived(data);
                                }
                            } catch (Exception e) {
                                Log.e("ESPConnector", "JSON parse error", e);
                            }
                            if (isPolling) {
                                handler.postDelayed(PollingRunnable.this, interval);
                            }
                        }
                    },
                    new Response.ErrorListener() {
                        @Override
                        public void onErrorResponse(VolleyError error) {
                            Log.e("ESPConnector", "Volley error", error);
                            if (isPolling) {
                                handler.postDelayed(PollingRunnable.this, interval);
                            }
                        }
                    });
            requestQueue.add(request);
        }
    }
}