package com.example.fp2;

import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.content.Intent;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.fp2.security.RiskResult;
import com.example.fp2.security.UrlScanClient;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;

public class UrlCheckActivity extends AppCompatActivity {

    private EditText urlInput;
    private Button startCheckButton;
    private TextView resultText;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_url_check);

        // 邊距設定（對應 layout 根節點 id: main）
        View root = findViewById(R.id.main);
        if (root != null) {
            ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
                return insets;
            });
        }

        // 返回箭頭
        ImageView backArrow = findViewById(R.id.backArrow);
        if (backArrow != null) {
            backArrow.setOnClickListener(v -> {
                Intent intent = new Intent(UrlCheckActivity.this, MainActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intent);
                finish();
            });
        }

        urlInput = findViewById(R.id.urlInput);
        startCheckButton = findViewById(R.id.startCheckButton);
        resultText = findViewById(R.id.resultText);

        startCheckButton.setOnClickListener(v -> startCheck());
    }

    private void startCheck() {
        String raw = urlInput.getText().toString().trim();
        if (TextUtils.isEmpty(raw)) {
            toast("請先貼上網址或文字");
            return;
        }
        List<String> urls = extractUrls(raw);
        if (urls.isEmpty()) {
            toast("未偵測到有效網址，請確認格式");
            return;
        }

        startCheckButton.setEnabled(false);
        startCheckButton.setText("檢查中…");
        resultText.setText("找到 " + urls.size() + " 個連結，開始檢查（urlscan）…\n");

        String apiKey = BuildConfig.URLSCAN_API_KEY; // 從 local.properties 注入
        UrlScanClient client = new UrlScanClient(apiKey);

        AtomicInteger done = new AtomicInteger(0);
        for (String u : urls) {
            client.evaluate(normalizeUrl(u), new UrlScanClient.Callback() {
                @Override public void onSuccess(RiskResult rr) {
                    onOneResult(urls.size(), done.incrementAndGet(), rr);
                }
                @Override public void onFailure(String message) {
                    ArrayList<String> rs = new ArrayList<>();
                    rs.add("查詢失敗：" + message);
                    RiskResult rr = new RiskResult(u, "未知", 0, rs);
                    onOneResult(urls.size(), done.incrementAndGet(), rr);
                }
            });
        }
    }

    private void onOneResult(int total, int finished, RiskResult add) {
        runOnUiThread(() -> {
            StringBuilder sb = new StringBuilder(resultText.getText());
            sb.append("\n• ").append(add.url).append("\n")
                    .append("  風險：").append(add.verdict).append("（").append(add.score).append("/100）\n");
            for (String r : add.reasons) sb.append("    - ").append(r).append("\n");
            resultText.setText(sb.toString());

            if (finished == total) {
                startCheckButton.setEnabled(true);
                startCheckButton.setText("開始檢查");
                toast("檢查完成");
            }
        });
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    /** 從文字中抽出 URL，支援一次多條；也接受只有網域的情況 */
    private List<String> extractUrls(String text) {
        LinkedHashSet<String> set = new LinkedHashSet<>();
        Matcher m = Patterns.WEB_URL.matcher(text);
        while (m.find()) {
            String found = m.group();
            // 去掉尾端標點
            found = found.replaceAll("[\\u3002\\uFF0C,.;:]+$", "");
            set.add(found);
        }
        if (set.isEmpty() && looksLikeDomain(text)) set.add(text);
        return new ArrayList<>(set);
    }

    /** 沒有 scheme 的補 https:// */
    private String normalizeUrl(String in) {
        String s = in.trim();
        if (s.matches("(?i)^https?://.+")) return s;
        return "https://" + s;
    }

    private boolean looksLikeDomain(String s) {
        return s.matches("(?i)^[a-z0-9.-]+\\.[a-z]{2,}(?:/.*)?$");
    }
}
