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

    private static final String TAG = "SAVE_IMAGE";

    private TextView resultText;
    private Uri imageUri;
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
        resultText = findViewById(R.id.resultText);

        backArrow.setOnClickListener(v -> {
            startActivity(new Intent(this, MainActivity.class));
            finish();
        });

        uploadButton.setOnClickListener(v -> {
            startActivity(new Intent(this, ScreenshotActivity.class));
            finish();
        });

        String imageUriStr = getIntent().getStringExtra("imageUri");
        if (imageUriStr == null) {
            resultText.setText("未收到圖片");
            return;
        }

        imageUri = Uri.parse(imageUriStr);
        runTextRecognition(imageUri);
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
                            analyzeOcrText(text);
                        } else {
                            runEnglishRecognition(image);
                        }
                    })
                    .addOnFailureListener(e -> runEnglishRecognition(image));

        } catch (IOException e) {
            resultText.setText("讀取圖片失敗");
        }
    }

    private void runEnglishRecognition(InputImage image) {
        TextRecognizer en = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        en.process(image)
                .addOnSuccessListener(t -> {
                    String text = t.getText();
                    if (TextUtils.isEmpty(text)) {
                        resultText.setText("未偵測到文字");
                    } else {
                        analyzeOcrText(text);
                    }
                })
                .addOnFailureListener(e ->
                        resultText.setText("辨識失敗：" + e.getMessage())
                );
    }

    // ===============================
    // 後端分析
    // ===============================
    private void analyzeOcrText(String ocrText) {
        resultText.setText("分析中…");

        backend.analyzeText(ocrText, new BackendService.Callback() {
            @Override
            public void onSuccess(ApiResponse data) {
                runOnUiThread(() -> {
                    String pretty = ResultFormatter.format(data);
                    resultText.setText(pretty);

                    // ⭐⭐⭐ 關鍵：寫入 Room（圖片）
                    saveImageRiskIfNeeded(data, imageUri, pretty);
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() ->
                        resultText.setText("分析失敗：" + message)
                );
            }
        });
    }

    // ===============================
    // ⭐ 寫入 Room（圖片）
    // ===============================
    private void saveImageRiskIfNeeded(
            ApiResponse data,
            Uri imageUri,
            String prettySummary
    ) {
        Log.e(TAG, "===== SAVE IMAGE CALLED =====");

        if (data == null || imageUri == null) {
            Log.e(TAG, "data or imageUri is null");
            return;
        }

        String riskLevel = normalizeRiskLevel(data.risk, data.is_scam);
        Log.e(TAG, "risk = " + riskLevel);

        if (!"MEDIUM".equals(riskLevel) && !"HIGH".equals(riskLevel)) {
            Log.e(TAG, "risk is LOW, skip save");
            return;
        }

        int score = "HIGH".equals(riskLevel) ? 90 : 65;

        RiskRecordEntity record = new RiskRecordEntity(
                "IMAGE",                    // ⭐ 類型
                imageUri.toString(),        // ⭐ 圖片 URI（歷史詳細頁要用）
                riskLevel,
                score,
                prettySummary,              // ⭐ 完整分析結果
                System.currentTimeMillis()
        );

        AppDatabase.getInstance(this)
                .riskRecordDao()
                .insert(record);

        Log.e(TAG, "✅ IMAGE SAVED TO DB");
    }

    // ===============================
    // 工具方法
    // ===============================
    private String normalizeRiskLevel(String risk, boolean isScam) {
        if (risk == null) {
            return isScam ? "HIGH" : "LOW";
        }

        String r = risk.trim().toUpperCase(Locale.ROOT);

        if (r.contains("HIGH") || r.contains("高") || r.contains("DANGER")) return "HIGH";
        if (r.contains("MED") || r.contains("中") || r.contains("SUSPIC")) return "MEDIUM";
        if (r.contains("LOW") || r.contains("低") || r.contains("SAFE")) return "LOW";

        return isScam ? "HIGH" : "LOW";
    }
}

