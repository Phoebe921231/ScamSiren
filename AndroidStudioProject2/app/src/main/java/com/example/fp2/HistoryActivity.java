package com.example.fp2;

import android.os.Bundle;
import android.text.TextUtils;
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

        new Thread(() -> {
            List<RiskRecordEntity> records =
                    AppDatabase.getInstance(this)
                            .riskRecordDao()
                            .getAll();

            List<HistoryItem> historyItems = new ArrayList<>();

            for (RiskRecordEntity r : records) {

                if (!shouldShowInHistory(r)) continue;

                int type = mapDetectTypeToHistoryType(r.type);
                if (type == -1) continue;

                // ✅ 關鍵：歷史內容優先顯示「偵測到的文字」（語音/圖片 OCR）
                String displayContent = pickDisplayContent(r);

                // createdAt 可能為 null，保護一下
                String timeText = "";
                if (r.createdAt != null) {
                    timeText = sdf.format(new Date(r.createdAt));
                }

                historyItems.add(new HistoryItem(
                        r.id,
                        type,
                        buildTitle(r.type),
                        mapRiskLevelText(r.riskLevel),
                        timeText,
                        displayContent,   // ✅ 這裡改成 detectedText 優先
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

                    HistoryAdapter adapter = new HistoryAdapter(this, historyItems);
                    recyclerView.setAdapter(adapter);
                }
            });
        }).start();
    }

    /**
     * ✅ 讓歷史頁看到「偵測到的文字」
     * - 若 DB 有 detectedText（detected_text 欄位）就顯示它
     * - 沒有才退回顯示原本 content（uri/文字截斷）
     */
    private String pickDisplayContent(RiskRecordEntity r) {
        if (r == null) return "";

        // 你 Entity 欄位名如果是 detectedText（對應 @ColumnInfo(name="detected_text")）
        // 這裡就用 r.detectedText
        String detected = "";
        try {
            detected = r.detectedText;
        } catch (Exception ignored) {}

        if (!TextUtils.isEmpty(detected)) {
            String t = detected.trim();
            // 列表不要太長（避免擠爆）
            if (t.length() > 120) t = t.substring(0, 120) + "…";
            return t;
        }

        // fallback：原本 content（可能是 uri）
        return (r.content == null) ? "" : r.content;
    }

    private boolean shouldShowInHistory(RiskRecordEntity r) {
        if (r == null) return false;

        // 分數高就顯示
        if (r.score >= 50) return true;

        // 依 riskLevel 判斷
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

    private int mapDetectTypeToHistoryType(String detectType) {
        if (detectType == null) return -1;

        switch (detectType.trim().toUpperCase(Locale.ROOT)) {
            case "URL":
                return HistoryItem.TYPE_URL;
            case "AUDIO":
                return HistoryItem.TYPE_AUDIO;
            case "IMAGE":
                return HistoryItem.TYPE_IMAGE;
            case "TEXT":
                return HistoryItem.TYPE_TEXT;
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
