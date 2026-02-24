package com.example.fp2;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.fp2.db.AppDatabase;
import com.example.fp2.db.RiskRecordEntity;
import com.example.fp2.model.ApiResponse;
import com.example.fp2.model.ResultFormatter;
import com.example.fp2.net.BackendService;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.io.IOException;
import java.util.Locale;

public class ResultActivity extends AppCompatActivity {

    private static final String TAG = "RESULT";

    private TextView detectedText; // 偵測到的文字（OCR / 使用者輸入）
    private TextView riskText;     // 風險判斷結果（pretty）

    private Uri imageUri;
    private String inputText; // 文字模式用

    private final BackendService backend = new BackendService();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_result);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        ImageView backArrow = findViewById(R.id.backArrow);
        Button uploadButton = findViewById(R.id.uploadButton);

        detectedText = findViewById(R.id.detectedText);
        riskText = findViewById(R.id.riskText);

        backArrow.setOnClickListener(v -> {
            startActivity(new Intent(this, MainActivity.class));
            finish();
        });

        // 依來源決定「重新上傳」回哪裡
        uploadButton.setOnClickListener(v -> {
            if (!TextUtils.isEmpty(inputText)) {
                startActivity(new Intent(this, TextCheckActivity.class));
            } else {
                startActivity(new Intent(this, ScreenshotActivity.class));
            }
            finish();
        });

        // ✅ 先吃「可能從上一頁帶來的 detectedText」
        // 你之前用 detected_text；我前面建議用 detectedText，所以兩個都支援
        String detectedFromIntent = firstNonEmpty(
                getIntent().getStringExtra("detectedText"),
                getIntent().getStringExtra("detected_text")
        );
        if (!TextUtils.isEmpty(detectedFromIntent)) setDetectedText(detectedFromIntent);

        // ✅ 文字模式
        inputText = getIntent().getStringExtra("inputText");
        if (!TextUtils.isEmpty(inputText)) {
            setDetectedText(inputText);
            analyzePlainText(inputText);
            return;
        }

        // ✅ 圖片模式（OCR）
        String imageUriStr = getIntent().getStringExtra("imageUri");
        if (TextUtils.isEmpty(imageUriStr)) {
            riskText.setText("未收到圖片或文字內容");
            if (TextUtils.isEmpty(detectedFromIntent)) setDetectedText("");
            return;
        }

        imageUri = Uri.parse(imageUriStr);

        // OCR 還沒出來前先提示
        if (TextUtils.isEmpty(detectedFromIntent)) {
            setDetectedText("（OCR 辨識中…）");
        }
        runTextRecognition(imageUri);
    }

    // ===============================
    // 顯示偵測到的文字
    // ===============================
    private void setDetectedText(String text) {
        if (detectedText == null) return;
        String t = (text == null) ? "" : text.trim();
        detectedText.setText(t.isEmpty() ? "（沒有偵測到文字）" : t);
    }

    // ===============================
    // 文字模式：送後端分析
    // ===============================
    private void analyzePlainText(String text) {
        riskText.setText("分析中…");

        backend.analyzeText(text, new BackendService.Callback() {
            @Override
            public void onSuccess(ApiResponse data) {
                runOnUiThread(() -> {
                    String pretty = ResultFormatter.format(data);
                    riskText.setText(pretty);

                    // ✅ 存歷史：TEXT 的 detected_text 就存使用者輸入文字
                    saveTextRiskIfNeeded(data, text, pretty);
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> riskText.setText("分析失敗：" + message));
            }
        });
    }

    // ===============================
    // OCR：中文優先，失敗再英文
    // ===============================
    private void runTextRecognition(Uri uri) {
        try {
            InputImage image = InputImage.fromFilePath(this, uri);

            TextRecognizer zh = TextRecognition.getClient(
                    new ChineseTextRecognizerOptions.Builder().build()
            );

            zh.process(image)
                    .addOnSuccessListener(t -> {
                        String text = t.getText();
                        if (!TextUtils.isEmpty(text)) {
                            setDetectedText(text);
                            analyzeOcrText(text);
                        } else {
                            runEnglishRecognition(image);
                        }
                    })
                    .addOnFailureListener(e -> runEnglishRecognition(image));

        } catch (IOException e) {
            riskText.setText("讀取圖片失敗");
            setDetectedText("");
        }
    }

    private void runEnglishRecognition(InputImage image) {
        TextRecognizer en = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        en.process(image)
                .addOnSuccessListener(t -> {
                    String text = t.getText();
                    if (TextUtils.isEmpty(text)) {
                        riskText.setText("未偵測到文字");
                        setDetectedText("");
                    } else {
                        setDetectedText(text);
                        analyzeOcrText(text);
                    }
                })
                .addOnFailureListener(e -> {
                    riskText.setText("辨識失敗：" + e.getMessage());
                    setDetectedText("");
                });
    }

    // ===============================
    // 後端分析（OCR 文字）
    // ===============================
    private void analyzeOcrText(String ocrText) {
        riskText.setText("分析中…");

        backend.analyzeText(ocrText, new BackendService.Callback() {
            @Override
            public void onSuccess(ApiResponse data) {
                runOnUiThread(() -> {
                    String pretty = ResultFormatter.format(data);
                    riskText.setText(pretty);

                    // ✅ 存歷史：IMAGE 的 detected_text 就存 OCR 文字
                    saveImageRiskIfNeeded(data, imageUri, ocrText, pretty);
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> riskText.setText("分析失敗：" + message));
            }
        });
    }

    // ===============================
    // 寫入 Room（文字）— 中/高才存
    // ===============================
    private void saveTextRiskIfNeeded(ApiResponse data, String text, String prettySummary) {
        Log.d(TAG, "===== SAVE TEXT CALLED =====");

        if (data == null || TextUtils.isEmpty(text)) {
            Log.d(TAG, "data or text is null/empty");
            return;
        }

        String riskLevel = normalizeRiskLevel(data.risk, data.is_scam);
        Log.d(TAG, "risk = " + riskLevel);

        if (!"MEDIUM".equals(riskLevel) && !"HIGH".equals(riskLevel)) {
            Log.d(TAG, "risk is LOW, skip save");
            return;
        }

        int score = "HIGH".equals(riskLevel) ? 90 : 65;

        // content：截斷一點避免 DB 太肥
        String storeText = text.trim();
        if (storeText.length() > 500) storeText = storeText.substring(0, 500) + "…";

        // detected_text：存原文（也可限制長度）
        String detected = text.trim();
        if (detected.length() > 2000) detected = detected.substring(0, 2000) + "…";

        RiskRecordEntity record = new RiskRecordEntity(
                "TEXT",
                storeText,
                riskLevel,
                score,
                prettySummary,
                detected,
                System.currentTimeMillis()
        );

        new Thread(() -> {
            try {
                AppDatabase.getInstance(getApplicationContext())
                        .riskRecordDao()
                        .insert(record);
                Log.d(TAG, "✅ TEXT SAVED TO DB");
            } catch (Exception e) {
                Log.e(TAG, "❌ TEXT insert failed", e);
            }
        }).start();
    }

    // ===============================
    // 寫入 Room（圖片）— 中/高才存
    // ===============================
    private void saveImageRiskIfNeeded(ApiResponse data, Uri imageUri, String ocrText, String prettySummary) {
        Log.d(TAG, "===== SAVE IMAGE CALLED =====");

        if (data == null || imageUri == null) {
            Log.d(TAG, "data or imageUri is null");
            return;
        }

        String riskLevel = normalizeRiskLevel(data.risk, data.is_scam);
        Log.d(TAG, "risk = " + riskLevel);

        if (!"MEDIUM".equals(riskLevel) && !"HIGH".equals(riskLevel)) {
            Log.d(TAG, "risk is LOW, skip save");
            return;
        }

        int score = "HIGH".equals(riskLevel) ? 90 : 65;

        String detected = (ocrText == null) ? "" : ocrText.trim();
        if (detected.length() > 2000) detected = detected.substring(0, 2000) + "…";

        RiskRecordEntity record = new RiskRecordEntity(
                "IMAGE",
                imageUri.toString(),      // content: 圖片 uri
                riskLevel,
                score,
                prettySummary,
                detected,                 // ✅ detected_text：OCR 的文字
                System.currentTimeMillis()
        );

        new Thread(() -> {
            try {
                AppDatabase.getInstance(getApplicationContext())
                        .riskRecordDao()
                        .insert(record);
                Log.d(TAG, "✅ IMAGE SAVED TO DB");
            } catch (Exception e) {
                Log.e(TAG, "❌ IMAGE insert failed", e);
            }
        }).start();
    }

    // ===============================
    // 工具方法
    // ===============================
    private String normalizeRiskLevel(String risk, boolean isScam) {
        if (risk == null) return isScam ? "HIGH" : "LOW";

        String r = risk.trim().toUpperCase(Locale.ROOT);
        if (r.contains("HIGH") || r.contains("高") || r.contains("DANGER")) return "HIGH";
        if (r.contains("MED") || r.contains("中") || r.contains("SUSPIC")) return "MEDIUM";
        if (r.contains("LOW") || r.contains("低") || r.contains("SAFE")) return "LOW";
        return isScam ? "HIGH" : "LOW";
    }

    private String firstNonEmpty(String a, String b) {
        if (!TextUtils.isEmpty(a)) return a;
        if (!TextUtils.isEmpty(b)) return b;
        return "";
    }
}

