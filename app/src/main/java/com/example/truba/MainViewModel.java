package com.example.truba;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainViewModel extends ViewModel {
    private final MutableLiveData<Map<String, Double>> espData = new MutableLiveData<>(new HashMap<>());
    private final MutableLiveData<String> currentSheetName = new MutableLiveData<>("");
    private final MutableLiveData<String> currentSheetContent = new MutableLiveData<>("");
    private final MutableLiveData<String> results = new MutableLiveData<>("");
    private final MutableLiveData<Map<String, Double>> sessionConstants = new MutableLiveData<>(new HashMap<>());
    private final MutableLiveData<List<ConstantsManager.ConstantInfo>> constantInfos = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<List<String>> variableNames = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Map<String, Double>> variableValues = new MutableLiveData<>(new HashMap<>());
    private final MutableLiveData<String> selectedVariable = new MutableLiveData<>("");
    private final MutableLiveData<Boolean> isPolling = new MutableLiveData<>(false);
    private final MutableLiveData<List<Double>> computedResults = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Map<String, String>> sheets = new MutableLiveData<>(new HashMap<>());

    public LiveData<Map<String, Double>> getEspData() { return espData; }
    public LiveData<String> getCurrentSheetName() { return currentSheetName; }
    public LiveData<String> getCurrentSheetContent() { return currentSheetContent; }
    public LiveData<String> getResults() { return results; }
    public LiveData<Map<String, Double>> getSessionConstants() { return sessionConstants; }
    public LiveData<List<ConstantsManager.ConstantInfo>> getConstantInfos() { return constantInfos; }
    public LiveData<List<String>> getVariableNames() { return variableNames; }
    public LiveData<Map<String, Double>> getVariableValues() { return variableValues; }
    public LiveData<String> getSelectedVariable() { return selectedVariable; }
    public LiveData<Boolean> getIsPolling() { return isPolling; }
    public LiveData<List<Double>> getComputedResults() { return computedResults; }
    public LiveData<Map<String, String>> getSheets() { return sheets; }

    public void setEspData(Map<String, Double> data) { espData.setValue(data); }
    public void setCurrentSheetName(String name) { currentSheetName.setValue(name); }
    public void setCurrentSheetContent(String content) { currentSheetContent.setValue(content); }
    public void setResults(String results) { this.results.setValue(results); }
    public void setSessionConstants(Map<String, Double> constants) { sessionConstants.setValue(constants); }
    public void setConstantInfos(List<ConstantsManager.ConstantInfo> infos) { constantInfos.setValue(infos); }
    public void setVariableNames(List<String> names) { variableNames.setValue(names); }
    public void setVariableValues(Map<String, Double> values) { variableValues.setValue(values); }
    public void setSelectedVariable(String var) { selectedVariable.setValue(var); }
    public void setIsPolling(boolean polling) { isPolling.setValue(polling); }
    public void setComputedResults(List<Double> results) { computedResults.setValue(results); }
    public void setSheets(Map<String, String> sheets) { this.sheets.setValue(sheets); }
}