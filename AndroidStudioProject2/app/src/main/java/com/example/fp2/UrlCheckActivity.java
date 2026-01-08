package com.example.fp2;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.util.Patterns;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.fp2.db.AppDatabase;
import com.example.fp2.db.RiskRecordEntity;
import com.example.fp2.net.Unshortener;
import com.example.fp2.security.RiskResult;
import com.example.fp2.security.UrlScanClient;

import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class UrlCheckActivity extends AppCompatActivity {

    private static final String TAG = "URL_HISTORY";

    private EditText urlInput;
    private Button startCheckButton;
    private TextView resultText;

    // ========= OkHttp（網址可達性檢查用） =========
    private static final OkHttpClient reachClient = new OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .callTimeout(10, TimeUnit.SECONDS)
            .build();

    enum UrlReachState {
        EXISTS,
        UNREACHABLE,
        INVALID
    }

    static class UrlReachCheckResult {
        UrlReachState state;
        String finalUrl;
        boolean usedWwwFallback;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_url_check);

        urlInput = findViewById(R.id.urlInput);
        startCheckButton = findViewById(R.id.startCheckButton);
        resultText = findViewById(R.id.resultText);

        ImageView backArrow = findViewById(R.id.backArrow);
        if (backArrow != null) {
            backArrow.setOnClickListener(v -> {
                Intent i = new Intent(this, MainActivity.class);
                i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(i);
                finish();
            });
        }

        startCheckButton.setOnClickListener(v -> startCheck());
    }

    // ===============================
    // 主流程
    // ===============================
    private void startCheck() {
        String raw = urlInput.getText().toString().trim();
        if (TextUtils.isEmpty(raw)) {
            toast("請輸入網址");
            return;
        }

        List<String> urls = extractUrls(raw);
        if (urls.isEmpty()) {
            toast("未偵測到有效網址");
            return;
        }

        startCheckButton.setEnabled(false);
        startCheckButton.setText("檢查中…");
        resultText.setText("");

        UrlScanClient scanClient = new UrlScanClient(BuildConfig.URLSCAN_API_KEY);
        AtomicInteger done = new AtomicInteger(0);
        int total = urls.size();

        for (String u : urls) {
            new Thread(() -> {

                final String orig = normalizeUrl(u);

                // ---------- 1️⃣ 展開短網址 ----------
                String target = orig;
                List<String> chainTmp = new ArrayList<>();

                try {
                    Unshortener.Result ex = new Unshortener().expand(orig);
                    if (ex != null) {
                        target = ex.finalUrl;
                        if (ex.chain != null) chainTmp.addAll(ex.chain);
                    }
                } catch (Exception ignore) {}

                // ---------- 2️⃣ 檢查可達性 ----------
                UrlReachCheckResult reach = checkWithWwwFallback(target);

                if (reach.state == UrlReachState.INVALID) {
                    RiskResult rr = buildSimpleResult(
                            reach.finalUrl,
                            "LOW",
                            "無法解析此網域，請確認網址是否正確。"
                    );
                    onOneResult(total, done.incrementAndGet(), rr, orig, reach.finalUrl);
                    return;
                }

                if (reach.state == UrlReachState.UNREACHABLE) {
                    RiskResult rr = buildSimpleResult(
                            reach.finalUrl,
                            "MEDIUM",
                            "此網址存在，但目前無法建立安全連線，請留意風險。"
                    );
                    onOneResult(total, done.incrementAndGet(), rr, orig, reach.finalUrl);
                    return;
                }

                // ---------- 3️⃣ 呼叫 urlscan ----------
                scanClient.evaluate(reach.finalUrl, new UrlScanClient.Callback() {
                    @Override
                    public void onSuccess(RiskResult rr) {
                        onOneResult(total, done.incrementAndGet(), rr, orig, reach.finalUrl);
                    }

                    @Override
                    public void onFailure(String message) {
                        RiskResult rr = buildSimpleResult(
                                reach.finalUrl,
                                "MEDIUM",
                                "查詢失敗：" + message
                        );
                        onOneResult(total, done.incrementAndGet(), rr, orig, reach.finalUrl);
                    }
                });

            }).start();
        }
    }

    // ===============================
    // 結果處理 + ⭐高風險判斷與存 DB
    // ===============================
    private void onOneResult(int total, int finished, RiskResult rr,
                             String orig, String finalUrl) {

        runOnUiThread(() -> {

            // ===== 顯示結果 =====
            StringBuilder sb = new StringBuilder();
            sb.append("🔗 測試連結：\n").append(orig);
            if (!orig.equals(finalUrl)) sb.append(" → ").append(finalUrl);
            sb.append("\n\n");
            sb.append("📌 判別結果：\n").append(rr.verdict).append("\n\n");

            if (rr.summary != null && !rr.summary.isEmpty()) {
                sb.append("📝 摘要：\n").append(rr.summary).append("\n\n");
            }
            if (rr.advice != null && !rr.advice.isEmpty()) {
                sb.append("⚠️ 建議：\n").append(rr.advice).append("\n\n");
            }

            resultText.setText(sb.toString());

            // ===== ⭐ 高風險判斷（真正修好的地方）=====
            boolean shouldSave = false;

            if (rr.score >= 50) {
                shouldSave = true;
            } else if (rr.verdict != null) {
                String v = rr.verdict.toUpperCase();
                shouldSave =
                        v.contains("HIGH") ||
                                v.contains("MEDIUM") ||
                                v.contains("DANGER") ||
                                v.contains("MALICIOUS") ||
                                v.contains("PHISH") ||
                                v.contains("SCAM");
            }

            Log.d(TAG,
                    "verdict=" + rr.verdict +
                            ", score=" + rr.score +
                            ", shouldSave=" + shouldSave
            );

            // ===== ⭐ 存進 Room =====
            if (shouldSave) {
                new Thread(() -> {
                    try {
                        int score = rr.score > 0 ? rr.score : 60;

                        RiskRecordEntity entity = new RiskRecordEntity(
                                "URL",
                                orig,
                                rr.verdict,
                                score,
                                sb.toString(),
                                System.currentTimeMillis()
                        );

                        AppDatabase.getInstance(getApplicationContext())
                                .riskRecordDao()
                                .insert(entity);

                        Log.d(TAG, "Saved URL : " + orig);

                    } catch (Exception e) {
                        Log.e(TAG, "save failed", e);
                    }
                }).start();
            }

            if (finished == total) {
                startCheckButton.setEnabled(true);
                startCheckButton.setText("開始檢查");
                toast("檢查完成");
            }
        });
    }

    // ===============================
    // 工具方法
    // ===============================
    private UrlReachCheckResult checkWithWwwFallback(String url) {
        UrlReachCheckResult r1 = checkUrlState(url);
        if (r1.state != UrlReachState.UNREACHABLE) return r1;

        if (!hasWww(url)) {
            UrlReachCheckResult r2 = checkUrlState(addWww(url));
            if (r2.state == UrlReachState.EXISTS) {
                r2.usedWwwFallback = true;
                return r2;
            }
        }
        return r1;
    }

    private UrlReachCheckResult checkUrlState(String url) {
        UrlReachCheckResult r = new UrlReachCheckResult();
        r.finalUrl = url;
        try {
            Request req = new Request.Builder().url(url).get().build();
            try (Response res = reachClient.newCall(req).execute()) {
                r.state = (res.code() >= 200 && res.code() < 400)
                        ? UrlReachState.EXISTS
                        : UrlReachState.UNREACHABLE;
            }
        } catch (UnknownHostException e) {
            r.state = UrlReachState.INVALID;
        } catch (Exception e) {
            r.state = UrlReachState.UNREACHABLE;
        }
        return r;
    }

    private boolean hasWww(String url) {
        return url.matches("(?i)^https?://www\\..+");
    }

    private String addWww(String url) {
        return url.replaceFirst("(?i)^https?://", "$0www.");
    }

    private String normalizeUrl(String in) {
        return in.matches("(?i)^https?://.+") ? in : "https://" + in;
    }

    private List<String> extractUrls(String text) {
        LinkedHashSet<String> set = new LinkedHashSet<>();
        Matcher m = Patterns.WEB_URL.matcher(text);
        while (m.find()) set.add(m.group());
        return new ArrayList<>(set);
    }

    private RiskResult buildSimpleResult(String url, String verdict, String msg) {
        ArrayList<String> reasons = new ArrayList<>();
        reasons.add(msg);
        return new RiskResult(url, verdict, 0, reasons);
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}
