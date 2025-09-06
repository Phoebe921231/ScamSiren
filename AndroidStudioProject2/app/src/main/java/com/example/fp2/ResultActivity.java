package com.example.fp2;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.ImageView;
import android.widget.Button;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.fp2.model.ApiResponse;
import com.example.fp2.net.BackendService;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions;

import java.io.IOException;

/**
 * 圖片 → OCR → /analyze_text 詐騙判別
 * - 先顯示 OCR 結果，接著呼叫後端做分析並覆寫畫面顯示分析摘要
 * - 不顯示「信心」
 */
public class ResultActivity extends AppCompatActivity {

    private TextView resultText;
    private final BackendService backend = new BackendService();
    @Nullable private String lastOcrText = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_result);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // 元件
        ImageView backArrow = findViewById(R.id.backArrow);
        Button uploadButton = findViewById(R.id.uploadButton);
        resultText = findViewById(R.id.resultText);

        // 🔙 返回 MainActivity
        backArrow.setOnClickListener(v -> {
            startActivity(new Intent(ResultActivity.this, MainActivity.class));
            finish();
        });

        // 📂 再次上傳
        uploadButton.setOnClickListener(v -> {
            startActivity(new Intent(ResultActivity.this, ScreenshotActivity.class));
            finish();
        });

        // 📸 取得 ScreenshotActivity 傳來的圖片 URI
        String imageUriStr = getIntent().getStringExtra("imageUri");
        if (imageUriStr != null) {
            Uri imageUri = Uri.parse(imageUriStr);
            runTextRecognition(imageUri);
        } else {
            resultText.setText("未收到圖片");
        }
    }

    // 🟢 OCR 辨識 (先中文，若空或失敗再英文)
    private void runTextRecognition(Uri imageUri) {
        try {
            InputImage image = InputImage.fromFilePath(this, imageUri);

            TextRecognizer chineseRecognizer = TextRecognition.getClient(
                    new ChineseTextRecognizerOptions.Builder().build()
            );

            chineseRecognizer.process(image)
                    .addOnSuccessListener(visionText -> {
                        String result = visionText.getText();
                        if (!TextUtils.isEmpty(result)) {
                            showOcrAndAnalyze(result);
                        } else {
                            // 若中文無結果 → 試英文
                            runEnglishRecognition(image);
                        }
                    })
                    .addOnFailureListener(e -> {
                        // 中文失敗 → 試英文
                        runEnglishRecognition(image);
                    });

        } catch (IOException e) {
            e.printStackTrace();
            resultText.setText("讀取圖片失敗");
        }
    }

    // 🟠 英文辨識（中文失敗或無結果時）
    private void runEnglishRecognition(InputImage image) {
        TextRecognizer englishRecognizer = TextRecognition.getClient(
                TextRecognizerOptions.DEFAULT_OPTIONS
        );

        englishRecognizer.process(image)
                .addOnSuccessListener(visionText -> {
                    String result = visionText.getText();
                    if (TextUtils.isEmpty(result)) {
                        resultText.setText("未偵測到文字");
                    } else {
                        showOcrAndAnalyze(result);
                    }
                })
                .addOnFailureListener(e -> resultText.setText("辨識失敗：" + e.getMessage()));
    }

    /** 先顯示 OCR 內容，再呼叫後端做詐騙判別並覆寫為分析摘要 */
    private void showOcrAndAnalyze(String ocrText) {
        lastOcrText = ocrText;
        // 先把 OCR 結果顯示出來（前 300 字），讓使用者看到內容
        String preview = ocrText.length() > 300 ? ocrText.substring(0, 300) + "…" : ocrText;
        resultText.setText("🔎 OCR 文字（節錄）：\n" + preview + "\n\n後端分析中…");

        analyzeOcrText(ocrText);
    }

    /** 呼叫後端 /analyze_text */
    private void analyzeOcrText(String text) {
        if (TextUtils.isEmpty(text)) {
            resultText.setText("辨識到的文字為空，無法分析。");
            return;
        }

        backend.analyzeText(text, new BackendService.Callback() {
            @Override public void onSuccess(ApiResponse data) {
                runOnUiThread(() -> showScamResult(data));
            }
            @Override public void onError(String message) {
                runOnUiThread(() -> {
                    String preview = (lastOcrText == null) ? "" :
                            (lastOcrText.length() > 300 ? lastOcrText.substring(0, 300) + "…" : lastOcrText);
                    resultText.setText("🔎 OCR 文字（節錄）：\n" + preview + "\n\n分析失敗：" + message);
                });
            }
        });
    }

    /** 將詐騙分析結果渲染到畫面（不顯示信心） */
    private void showScamResult(ApiResponse res) {
        StringBuilder sb = new StringBuilder();
        sb.append(res.is_scam ? "⚠️ 可能詐騙\n" : "✅ 低風險\n");
        sb.append("風險：").append(res.risk).append('\n');

        if (res.analysis != null) {
            if (res.analysis.matched_categories != null && !res.analysis.matched_categories.isEmpty()) {
                sb.append("命中：")
                        .append(TextUtils.join("、", res.analysis.matched_categories))
                        .append('\n');
            }
            if (res.analysis.actions_requested != null && !res.analysis.actions_requested.isEmpty()) {
                sb.append("對方要求：")
                        .append(TextUtils.join("、", res.analysis.actions_requested))
                        .append('\n');
            }
        }

        if (res.reasons != null && !res.reasons.isEmpty())
            sb.append("理由：").append(TextUtils.join("、", res.reasons)).append('\n');
        if (res.advices != null && !res.advices.isEmpty())
            sb.append("建議：").append(TextUtils.join("、", res.advices)).append('\n');

        // 如果想同時保留 OCR 文字，可把上面改成附加在 OCR 之後；目前改為專注顯示結果摘要
        resultText.setText(sb.toString());
    }
}
