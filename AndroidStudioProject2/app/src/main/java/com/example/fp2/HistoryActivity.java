package com.example.fp2;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.fp2.db.AppDatabase;
import com.example.fp2.db.RiskRecordEntity;
import com.example.fp2.model.HistoryItem;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class HistoryActivity extends AppCompatActivity {

    private final SimpleDateFormat sdf =
            new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        ImageView btnBack = findViewById(R.id.btn_back);
        if (btnBack != null) btnBack.setOnClickListener(v -> finish());

        RecyclerView recyclerView = findViewById(R.id.recycler_history);
        View emptyState = findViewById(R.id.layout_empty_state);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        // ⭐ 關鍵修正：不要在 DAO 過濾風險等級
        new Thread(() -> {
            List<RiskRecordEntity> records =
                    AppDatabase.getInstance(this)
                            .riskRecordDao()
                            .getAll();   // ✅ 全部撈出來

            List<HistoryItem> historyItems = new ArrayList<>();

            for (RiskRecordEntity r : records) {

                // ⭐ 在 Java 端判斷要不要顯示
                if (!shouldShowInHistory(r)) {
                    continue;
                }

                int type = mapDetectTypeToHistoryType(r.detectType);

                historyItems.add(new HistoryItem(
                        r.id,
                        type,
                        buildTitle(r.detectType),
                        mapRiskLevelText(r.riskLevel),
                        sdf.format(new Date(r.createdAt)),
                        r.content,   // URL / audioUri / imageUri
                        r.summary
                ));
            }

            runOnUiThread(() -> {
                if (historyItems.isEmpty()) {
                    if (emptyState != null) emptyState.setVisibility(View.VISIBLE);
                    recyclerView.setVisibility(View.GONE);
                } else {
                    if (emptyState != null) emptyState.setVisibility(View.GONE);
                    recyclerView.setVisibility(View.VISIBLE);

                    HistoryAdapter adapter =
                            new HistoryAdapter(this, historyItems);
                    recyclerView.setAdapter(adapter);
                }
            });
        }).start();
    }

    // ===============================
    // 決定是否顯示在歷史紀錄
    // ===============================
    private boolean shouldShowInHistory(RiskRecordEntity r) {
        if (r == null) return false;

        // 用分數判斷最穩
        if (r.riskScore >= 50) return true;

        // fallback：用文字關鍵字
        if (r.riskLevel != null) {
            String v = r.riskLevel.toUpperCase(Locale.ROOT);
            return v.contains("高")
                    || v.contains("HIGH")
                    || v.contains("MEDIUM")
                    || v.contains("DANGER")
                    || v.contains("MALICIOUS")
                    || v.contains("PHISH")
                    || v.contains("SCAM");
        }
        return false;
    }

    // ===============================
    // 類型轉換
    // ===============================
    private int mapDetectTypeToHistoryType(String detectType) {
        if (detectType == null) return -1;

        switch (detectType.trim().toUpperCase(Locale.ROOT)) {
            case "URL":
                return HistoryItem.TYPE_URL;
            case "AUDIO":
                return HistoryItem.TYPE_AUDIO;
            case "IMAGE":
            case "TEXT":
                return HistoryItem.TYPE_IMAGE;
            default:
                return -1;
        }
    }

    private String buildTitle(String detectType) {
        if ("URL".equalsIgnoreCase(detectType)) return "網址檢查結果";
        if ("AUDIO".equalsIgnoreCase(detectType)) return "錄音判別結果";
        if ("TEXT".equalsIgnoreCase(detectType)) return "文字判別結果";
        return "圖片辨識結果";
    }

    private String mapRiskLevelText(String level) {
        if (level == null) return "低";
        if (level.equalsIgnoreCase("HIGH") || level.contains("高")) return "高";
        if (level.equalsIgnoreCase("MEDIUM") || level.contains("中")) return "中";
        return "低";
    }
}
