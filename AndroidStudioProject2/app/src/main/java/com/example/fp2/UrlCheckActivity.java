package com.example.fp2;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
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
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class UrlCheckActivity extends AppCompatActivity {

    private EditText urlInput;
    private Button startCheckButton;
    private TextView resultText;

    // ========= 可達性檢查 =========
    private static final OkHttpClient reachClient = new OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .callTimeout(10, TimeUnit.SECONDS)
            .build();

    enum UrlReachState { EXISTS, UNREACHABLE, INVALID }

    static class UrlReachCheckResult {
        UrlReachState state;
        String finalUrl;
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

                // 1️⃣ 展開短網址
                String target = orig;
                try {
                    Unshortener.Result ex = new Unshortener().expand(orig);
                    if (ex != null) target = ex.finalUrl;
                } catch (Exception ignore) {}

                // 2️⃣ 可達性檢查
                UrlReachCheckResult reach = checkUrlState(target);

                // ❌ 網址不存在
                if (reach.state == UrlReachState.INVALID) {
                    RiskResult rr = buildSimpleResult(
                            reach.finalUrl,
                            "INVALID",
                            "此網址不存在或可能為拼寫錯誤。"
                    );
                    onOneResult(total, done.incrementAndGet(), rr, orig, reach.finalUrl);
                    return;
                }

                // ⚠️ 無法建立連線
                if (reach.state == UrlReachState.UNREACHABLE) {
                    RiskResult rr = buildSimpleResult(
                            reach.finalUrl,
                            "MEDIUM",
                            "此網址存在，但目前無法建立安全連線。"
                    );
                    onOneResult(total, done.incrementAndGet(), rr, orig, reach.finalUrl);
                    return;
                }

                // 3️⃣ urlscan
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
    // ⭐ 結果顯示（含釣魚語意判斷）
    // ===============================
    private void onOneResult(int total, int finished, RiskResult rr,
                             String orig, String finalUrl) {

        runOnUiThread(() -> {

            String verdictZh;
            String summary;
            String advice;

            String v = rr.verdict == null ? "" : rr.verdict.toUpperCase(Locale.ROOT);

            //
            String content = "";
            if (rr.summary != null) content += rr.summary;
            if (rr.reasons != null) {
                for (String r : rr.reasons) {
                    if (r != null) content += r;
                }
            }
            content = content.toLowerCase();

            if (
                    v.contains("INVALID")
            ) {
                verdictZh = "網址不存在";
                summary = "此網址不存在或可能為拼寫錯誤。";
                advice =
                        "請確認網址是否輸入正確，" +
                                "不要點擊或相信來源不明的連結，" +
                                "避免提供任何個人資料。";

            } else if (
                    v.contains("HIGH") ||
                            content.contains("phishing") ||
                            content.contains("credential") ||
                            content.contains("social engineering") ||
                            content.contains("釣魚")
            ) {
                verdictZh = "高風險";
                summary = "判定為高風險，疑似釣魚或詐騙網站。";
                advice =
                        "請勿開啟或互動，立即關閉頁面。" +
                                "不要登入、不輸入個資或一次性驗證碼，" +
                                "不要下載檔案、不掃描 QR Code，" +
                                "可透過官方網站或 165 反詐騙專線查證。";

            } else if (
                    v.contains("MEDIUM") ||
                            content.contains("suspicious") ||
                            content.contains("可疑")
            ) {
                verdictZh = "中風險";
                summary = "判定為中風險，存在可疑行為，需提高警覺。";
                advice =
                        "建議提高警覺，避免登入或輸入個資，" +
                                "確認網址來源是否可信後再操作。";

            } else {
                verdictZh = "低風險";
                summary = "目前未發現明顯異常。";
                advice =
                        "風險較低，但仍建議保持警覺，" +
                                "不要輕易相信或點擊外來連結，" +
                                "避免提供任何個人資料。";
            }

            StringBuilder sb = new StringBuilder();

            sb.append("🔗 測試連結：\n")
                    .append(orig);
            if (!orig.equals(finalUrl)) sb.append(" → ").append(finalUrl);
            sb.append("\n\n");

            sb.append("📌 判別結果：\n")
                    .append(verdictZh)
                    .append("\n\n");

            sb.append("📝 摘要：\n")
                    .append(summary)
                    .append("\n\n");

            sb.append("⚠️ 建議：\n")
                    .append(advice)
                    .append("\n\n");

            resultText.setText(sb.toString());

            // ===== 歷史紀錄：只存中 / 高風險 =====
            boolean shouldSave =
                    verdictZh.equals("高風險") ||
                            verdictZh.equals("中風險");

            if (shouldSave) {
                new Thread(() -> {
                    RiskRecordEntity entity = new RiskRecordEntity(
                            "URL",
                            orig,
                            verdictZh,
                            rr.score > 0 ? rr.score : 60,
                            sb.toString(),
                            System.currentTimeMillis()
                    );
                    AppDatabase.getInstance(getApplicationContext())
                            .riskRecordDao()
                            .insert(entity);
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

