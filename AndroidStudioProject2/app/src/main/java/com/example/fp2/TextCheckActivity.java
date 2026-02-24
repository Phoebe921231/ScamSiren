package com.example.fp2;

import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

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

import java.util.Locale;

public class   TextCheckActivity extends AppCompatActivity {

    private static final String TAG = "TEXT_CHECK";

    private EditText textInput;
    private Button startCheckButton;
    private ImageView backArrow;
    private TextView resultText;

    private final BackendService backend = new BackendService();

    // 你可調整：避免貼超長造成 UI/網路負擔
    private static final int MAX_LEN = 3000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_text_check);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        backArrow = findViewById(R.id.backArrow);
        textInput = findViewById(R.id.textInput);
        startCheckButton = findViewById(R.id.startCheckButton);
        resultText = findViewById(R.id.resultText);

        backArrow.setOnClickListener(v -> finish());

        // 一開始就讓結果區顯示（你之前說不要預設隱藏）
        resultText.setText("（結果出來後會顯示在這裡）");

        startCheckButton.setOnClickListener(v -> {
            String text = textInput.getText() == null ? "" : textInput.getText().toString().trim();

            if (TextUtils.isEmpty(text)) {
                toast("請先輸入或貼上文字內容");
                return;
            }

            if (text.length() > MAX_LEN) {
                toast("文字太長，請縮短到 " + MAX_LEN + " 字以內");
                return;
            }

            analyzeTextAndRender(text);
        });
    }

    private void analyzeTextAndRender(String text) {
        resultText.setText("分析中…");

        backend.analyzeText(text, new BackendService.Callback() {
            @Override
            public void onSuccess(ApiResponse data) {
                runOnUiThread(() -> {
                    String pretty = ResultFormatter.format(data);
                    resultText.setText(pretty);

                    // ✅ 中/高風險才存歷史（TEXT）
                    saveTextRiskIfNeeded(data, text, pretty);
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> resultText.setText("分析失敗：" + message));
            }
        });
    }

    // ✅ 寫入 Room（文字）— 中/高才存
    private void saveTextRiskIfNeeded(ApiResponse data, String text, String prettySummary) {
        if (data == null || TextUtils.isEmpty(text)) return;

        String riskLevel = normalizeRiskLevel(data.risk, data.is_scam);

        if (!"MEDIUM".equals(riskLevel) && !"HIGH".equals(riskLevel)) {
            Log.d(TAG, "risk is LOW, skip save");
            return;
        }

        int score = "HIGH".equals(riskLevel) ? 90 : 65;

        // 避免內容過長塞爆 DB（可自行調整/移除）
        String storeText = text.trim();
        if (storeText.length() > 500) storeText = storeText.substring(0, 500) + "…";

        RiskRecordEntity record = new RiskRecordEntity(
                "TEXT",
                storeText,               // content 存原文（歷史詳細頁可顯示）
                riskLevel,
                score,
                prettySummary,           // summary 存格式化結果（🔴🟠🟢 那段）
                System.currentTimeMillis()
        );

        AppDatabase.getInstance(this)
                .riskRecordDao()
                .insert(record);

        Log.d(TAG, "✅ TEXT SAVED TO DB");
    }

    private String normalizeRiskLevel(String risk, boolean isScam) {
        if (risk == null) return isScam ? "HIGH" : "LOW";

        String r = risk.trim().toUpperCase(Locale.ROOT);

        if (r.contains("HIGH") || r.contains("高") || r.contains("DANGER")) return "HIGH";
        if (r.contains("MED") || r.contains("中") || r.contains("SUSPIC")) return "MEDIUM";
        if (r.contains("LOW") || r.contains("低") || r.contains("SAFE")) return "LOW";

        return isScam ? "HIGH" : "LOW";
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }
}
