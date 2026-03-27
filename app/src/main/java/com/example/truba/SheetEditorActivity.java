package com.example.truba;

import android.content.Intent;
import android.inputmethodservice.Keyboard;
import android.inputmethodservice.KeyboardView;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class SheetEditorActivity extends AppCompatActivity {

    private static final String TAG = "SheetEditor";
    private EditText etSheetName;
    private WebView webView;
    private LinearLayout keyboardLayout;
    private String sheetContent = "";
    private String sheetName = "";
    private boolean isEditMode = false;
    private boolean initialContentLoaded = false; // флаг, что начальное содержимое загружено
    private KeyboardView keyboardView;
    private Keyboard keyboard;
    private final String[] KEYBOARD_BUTTONS = {
            ">","<", "sin(", "print(", "cos(", "tan(", "log(", "ln(", "sqrt(",
            "π", "e", "^", "(", ")", "[", "]", "{", "}",
            "7", "8", "9", "/", "4", "5", "6", "*",
            "1", "2", "3", "-", "0", ".", "=", "+",
            "\n", " ", "←"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sheet_editor);
        keyboardView = findViewById(R.id.keyboardView);
        etSheetName = findViewById(R.id.et_sheet_name);
        webView = findViewById(R.id.webView);
        keyboardLayout = findViewById(R.id.keyboardLayout);
        Button btnSave = findViewById(R.id.btn_save);
        Button btnCancel = findViewById(R.id.btn_cancel);

        // Получаем данные из Intent
        if (getIntent().hasExtra("sheet_name")) {
            sheetName = getIntent().getStringExtra("sheet_name");
            sheetContent = getIntent().getStringExtra("sheet_content");
            isEditMode = true;
            etSheetName.setText(sheetName);
            Log.d(TAG, "Редактирование листа: " + sheetName + ", содержимое: " + sheetContent);
        } else {
            Log.d(TAG, "Создание нового листа");
        }

        setupWebView();
        setupKeyboard();
        setupTextWatcher();

        btnSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG, "Нажата кнопка Сохранить");
                saveSheet();
            }
        });

        btnCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
    }

    private void setupWebView() {
        webView.getSettings().setJavaScriptEnabled(true);
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                Log.d(TAG, "WebView загружен, загружаем содержимое: " + sheetContent);
                if (sheetContent != null && !sheetContent.isEmpty()) {
                    loadContentToWebView(sheetContent);
                } else {
                    Log.w(TAG, "sheetContent пуст, ничего не загружаем");
                }
            }
        });
        webView.addJavascriptInterface(new JavaScriptInterface(), "Android");
        webView.loadUrl("file:///android_asset/editor.html");
    }

    private class JavaScriptInterface {
        @JavascriptInterface
        public void onTextChanged(String text) {
            if (initialContentLoaded) {
                Log.d(TAG, "onTextChanged: " + text);
                sheetContent = text;
            } else {
                Log.d(TAG, "onTextChanged ignored (initialContentLoaded=false): " + text);
            }
        }

        @JavascriptInterface
        public void onTextLoaded() {
            initialContentLoaded = true;
            Log.d(TAG, "onTextLoaded: начальное содержимое загружено");
        }
    }

    private void setupKeyboard() {
        keyboard = new Keyboard(this, R.xml.keyboard_layout);
        keyboardView.setKeyboard(keyboard);
        keyboardView.setEnabled(true);
        keyboardView.setPreviewEnabled(false);
        keyboardView.setOnKeyboardActionListener(new KeyboardView.OnKeyboardActionListener() {
            @Override
            public void onPress(int primaryCode) {}

            @Override
            public void onRelease(int primaryCode) {}

            @Override
            public void onKey(int primaryCode, int[] keyCodes) {
                String label = null;
                for (Keyboard.Key key : keyboard.getKeys()) {
                    if (key.codes[0] == primaryCode) {
                        label = key.label.toString();
                        break;
                    }
                }
                if (label == null) return;

                if (primaryCode == -5) { // ←
                    webView.loadUrl("javascript:deleteChar()");
                } else if (primaryCode == 32) { // Пробел
                    webView.loadUrl("javascript:insertText(' ')");
                } else if (primaryCode == 10) { // ↵
                    webView.loadUrl("javascript:insertText('\n')");
                }  else {
                    String toInsert = label.replace("\\", "\\\\").replace("'", "\\'").replace("\"", "\\\"");
                    webView.loadUrl("javascript:insertText('" + toInsert + "')");
                }
            }

            @Override
            public void onText(CharSequence text) {}

            @Override
            public void swipeLeft() {}

            @Override
            public void swipeRight() {}

            @Override
            public void swipeDown() {}

            @Override
            public void swipeUp() {}
        });
    }

    private void setupTextWatcher() {
        etSheetName.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                sheetName = s.toString().trim();
                Log.d(TAG, "Имя листа изменено: " + sheetName);
            }
        });
    }

    private void loadContentToWebView(String content) {
        initialContentLoaded = false; // сбрасываем флаг перед загрузкой
        String escapedContent = content.replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\"", "\\\"")
                .replace("\n", "\\n");
        String js = "setText('" + escapedContent + "');";
        webView.loadUrl("javascript:" + js);
        Log.d(TAG, "Загружаем содержимое в WebView: " + content);
    }

    private void saveSheet() {
        webView.evaluateJavascript("getText();", new android.webkit.ValueCallback<String>() {
            @Override
            public void onReceiveValue(String value) {
                Log.d(TAG, "Получено из WebView: " + value);
                String text = value;
                if (text.startsWith("\"") && text.endsWith("\"")) {
                    text = text.substring(1, text.length() - 1)
                            .replace("\\n", "\n")
                            .replace("\\\"", "\"")
                            .replace("\\\\", "\\");
                }
                text = unescapeUnicode(text);
                sheetContent = text;
                Log.d(TAG, "Обработанное содержимое: " + sheetContent);

                if (sheetName.isEmpty()) {
                    Toast.makeText(SheetEditorActivity.this, "Введите имя листа", Toast.LENGTH_SHORT).show();
                    return;
                }

                Intent resultIntent = new Intent();
                resultIntent.putExtra("sheet_name", sheetName);
                resultIntent.putExtra("sheet_content", sheetContent);
                setResult(RESULT_OK, resultIntent);
                Log.d(TAG, "Отправляем результат: имя=" + sheetName + ", содержимое=" + sheetContent);
                finish();
            }
        });
    }
    private String unescapeUnicode(String s) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length() && s.charAt(i + 1) == 'u') {
                String hex = s.substring(i + 2, i + 6);
                sb.append((char) Integer.parseInt(hex, 16));
                i += 6;
            } else {
                sb.append(c);
                i++;
            }
        }
        return sb.toString();
    }
}