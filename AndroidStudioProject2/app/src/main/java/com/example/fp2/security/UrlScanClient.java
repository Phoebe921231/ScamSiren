package com.example.fp2.security;

import com.google.gson.*;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.*;

/**
 * UrlScanClient
 * - evaluate(url): 先 search（精準），再 domain 搜尋，最後 scan + poll
 * - poll：404 時會去 /api/v1/scan/{uuid}/ 查狀態（queued/processing/done）
 */
public class UrlScanClient {

    private static final String BASE = "https://urlscan.io";
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    // 輪詢設定：最多 12 次、每次間隔 3 秒（約 36 秒）
    private static final int POLL_MAX = 12;
    private static final int POLL_INTERVAL_MS = 3000;

    private final OkHttpClient http;
    private final Gson gson = new GsonBuilder().create();
    private final String apiKey;

    public interface Callback {
        void onSuccess(RiskResult result);
        void onFailure(String message);
    }

    public UrlScanClient(String apiKey) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.http = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .callTimeout(45, TimeUnit.SECONDS)
                .build();
    }

    /** 對外：search 精準→search domain→scan+poll */
    public void evaluate(String url, Callback cb) {
        searchExact(url, new Callback() {
            @Override public void onSuccess(RiskResult result) {
                cb.onSuccess(result);
            }
            @Override public void onFailure(String msg) {
                // fallback：用 domain 搜索（時間拉長，命中率高一些）
                String host = extractHost(url);
                if (host == null || host.isEmpty()) {
                    scanAndPoll(url, cb);
                    return;
                }
                searchByDomain(host, new Callback() {
                    @Override public void onSuccess(RiskResult result) {
                        cb.onSuccess(result);
                    }
                    @Override public void onFailure(String msg2) {
                        scanAndPoll(url, cb);
                    }
                });
            }
        });
    }

    /** 精準 URL 搜尋（近 30 天） */
    private void searchExact(String url, Callback cb) {
        try {
            String q = "page.url:\"" + url + "\" AND date:>now-30d";
            String full = BASE + "/api/v1/search/?q=" + URLEncoder.encode(q, "UTF-8") + "&size=1";
            Request.Builder rb = new Request.Builder().url(full).get();
            if (!apiKey.isEmpty()) rb.header("API-Key", apiKey);

            http.newCall(rb.build()).enqueue(new okhttp3.Callback() {
                @Override public void onFailure(Call call, IOException e) {
                    cb.onFailure("search 失敗：" + e.getMessage());
                }
                @Override public void onResponse(Call call, Response resp) {
                    try (Response r = resp) {
                        if (!r.isSuccessful()) {
                            cb.onFailure("search 狀態碼：" + r.code());
                            return;
                        }
                        String body = r.body() != null ? r.body().string() : "";
                        JsonObject root = JsonParser.parseString(body).getAsJsonObject();
                        JsonArray results = root.has("results") && root.get("results").isJsonArray()
                                ? root.getAsJsonArray("results") : new JsonArray();
                        if (results.size() == 0) {
                            cb.onFailure("search 無結果");
                            return;
                        }
                        JsonObject first = results.get(0).getAsJsonObject();
                        cb.onSuccess(parseRiskFromResult(first, url));
                    } catch (Exception ex) {
                        cb.onFailure("search 解析錯誤：" + ex.getMessage());
                    }
                }
            });
        } catch (Exception e) {
            cb.onFailure("search 構造失敗：" + e.getMessage());
        }
    }

    /** 以網域搜尋（近 90 天），提高命中率 */
    private void searchByDomain(String host, Callback cb) {
        try {
            String plain = stripWWW(host);
            String q = "page.domain:\"" + plain + "\" AND date:>now-90d";
            String full = BASE + "/api/v1/search/?q=" + URLEncoder.encode(q, "UTF-8") + "&size=1";
            Request.Builder rb = new Request.Builder().url(full).get();
            if (!apiKey.isEmpty()) rb.header("API-Key", apiKey);

            http.newCall(rb.build()).enqueue(new okhttp3.Callback() {
                @Override public void onFailure(Call call, IOException e) {
                    cb.onFailure("search(domain) 失敗：" + e.getMessage());
                }
                @Override public void onResponse(Call call, Response resp) {
                    try (Response r = resp) {
                        if (!r.isSuccessful()) {
                            cb.onFailure("search(domain) 狀態碼：" + r.code());
                            return;
                        }
                        String body = r.body() != null ? r.body().string() : "";
                        JsonObject root = JsonParser.parseString(body).getAsJsonObject();
                        JsonArray results = root.has("results") && root.get("results").isJsonArray()
                                ? root.getAsJsonArray("results") : new JsonArray();
                        if (results.size() == 0) {
                            cb.onFailure("search(domain) 無結果");
                            return;
                        }
                        JsonObject first = results.get(0).getAsJsonObject();
                        // 傳回時仍帶原始 URL 作為顯示
                        cb.onSuccess(parseRiskFromResult(first, "https://" + plain + "/"));
                    } catch (Exception ex) {
                        cb.onFailure("search(domain) 解析錯誤：" + ex.getMessage());
                    }
                }
            });
        } catch (Exception e) {
            cb.onFailure("search(domain) 構造失敗：" + e.getMessage());
        }
    }

    /** 提交掃描並輪詢結果 */
    private void scanAndPoll(String url, Callback cb) {
        if (apiKey.isEmpty()) {
            cb.onFailure("未設定 URLScan API Key");
            return;
        }
        String endpoint = BASE + "/api/v1/scan/";
        JsonObject payload = new JsonObject();
        payload.addProperty("url", url);
        payload.addProperty("visibility", "unlisted");

        Request req = new Request.Builder()
                .url(endpoint)
                .post(RequestBody.create(payload.toString(), JSON))
                .header("Content-Type", "application/json")
                .header("API-Key", apiKey)
                .build();

        http.newCall(req).enqueue(new okhttp3.Callback() {
            @Override public void onFailure(Call call, IOException e) {
                cb.onFailure("scan 失敗：" + e.getMessage());
            }
            @Override public void onResponse(Call call, Response resp) {
                try (Response r = resp) {
                    if (!r.isSuccessful()) {
                        cb.onFailure("scan 狀態碼：" + r.code());
                        return;
                    }
                    String body = r.body() != null ? r.body().string() : "";
                    JsonObject root = JsonParser.parseString(body).getAsJsonObject();
                    String uuid = root.has("uuid") ? root.get("uuid").getAsString() : null;
                    if (uuid == null || uuid.isEmpty()) {
                        cb.onFailure("scan 未取得 uuid");
                        return;
                    }
                    pollResult(uuid, url, 0, cb);
                } catch (Exception e) {
                    cb.onFailure("scan 解析錯誤：" + e.getMessage());
                }
            }
        });
    }

    /** 每 POLL_INTERVAL_MS 秒輪詢一次，最多 POLL_MAX 次；404 會先查狀態 */
    private void pollResult(String uuid, String url, int attempt, Callback cb) {
        if (attempt >= POLL_MAX) {
            cb.onFailure("結果未就緒（uuid=" + uuid + "）");
            return;
        }
        String endpoint = BASE + "/api/v1/result/" + uuid + "/";

        Request.Builder rb = new Request.Builder().url(endpoint).get();
        if (!apiKey.isEmpty()) rb.header("API-Key", apiKey);

        http.newCall(rb.build()).enqueue(new okhttp3.Callback() {
            @Override public void onFailure(Call call, IOException e) {
                cb.onFailure("poll 失敗：" + e.getMessage());
            }
            @Override public void onResponse(Call call, Response resp) {
                try (Response r = resp) {
                    if (r.code() == 404) {
                        // 結果檔未生成，先查狀態，再決定是否繼續等
                        pollStatusThenRetry(uuid, url, attempt, cb);
                        return;
                    }
                    if (!r.isSuccessful()) {
                        cb.onFailure("poll 狀態碼：" + r.code());
                        return;
                    }
                    String body = r.body() != null ? r.body().string() : "";
                    JsonObject root = JsonParser.parseString(body).getAsJsonObject();
                    cb.onSuccess(parseRiskFromResult(root, url));
                } catch (Exception e) {
                    cb.onFailure("poll 解析錯誤：" + e.getMessage());
                }
            }
        });
    }

    /** 查詢掃描狀態（/api/v1/scan/{uuid}/），若未完成則等待後重試 result */
    private void pollStatusThenRetry(String uuid, String url, int attempt, Callback cb) {
        String statusEndpoint = BASE + "/api/v1/scan/" + uuid + "/";

        Request.Builder rb = new Request.Builder().url(statusEndpoint).get();
        if (!apiKey.isEmpty()) rb.header("API-Key", apiKey);

        http.newCall(rb.build()).enqueue(new okhttp3.Callback() {
            @Override public void onFailure(Call call, IOException e) {
                // 查狀態失敗也照節奏等待再試
                sleepQuiet(POLL_INTERVAL_MS);
                pollResult(uuid, url, attempt + 1, cb);
            }
            @Override public void onResponse(Call call, Response resp) {
                try (Response r = resp) {
                    boolean shouldWait = true;
                    if (r.isSuccessful()) {
                        String body = r.body() != null ? r.body().string() : "";
                        try {
                            JsonObject o = JsonParser.parseString(body).getAsJsonObject();
                            String status = o.has("status") ? o.get("status").getAsString() : "";
                            // 常見：submitted / queued / processing / finished / done
                            if ("done".equalsIgnoreCase(status) || "finished".equalsIgnoreCase(status)) {
                                shouldWait = false; // 已完成，直接去抓 result
                            }
                        } catch (Exception ignore) {}
                    }
                    if (shouldWait) sleepQuiet(POLL_INTERVAL_MS);
                    pollResult(uuid, url, attempt + 1, cb);
                } catch (Exception e) {
                    sleepQuiet(POLL_INTERVAL_MS);
                    pollResult(uuid, url, attempt + 1, cb);
                }
            }
        });
    }

    // ===== 解析與小工具 =====

    /** 將 urlscan 回應轉為 RiskResult（search / result 皆可） */
    private RiskResult parseRiskFromResult(JsonObject src, String fallbackUrl) {
        String url = fallbackUrl;
        try {
            if (src.has("page")) {
                JsonObject page = src.getAsJsonObject("page");
                if (page.has("url")) url = page.get("url").getAsString();
            } else if (src.has("task")) {
                JsonObject task = src.getAsJsonObject("task");
                if (task.has("url")) url = task.get("url").getAsString();
            }
        } catch (Exception ignored) {}

        List<String> reasons = new ArrayList<>();
        int score = -1;
        String verdict = "未知";
        boolean malicious = false;

        try {
            if (src.has("verdicts") && src.get("verdicts").isJsonObject()) {
                JsonObject v = src.getAsJsonObject("verdicts");

                // overall
                if (v.has("overall") && v.get("overall").isJsonObject()) {
                    JsonObject ov = v.getAsJsonObject("overall");
                    if (ov.has("malicious")) malicious = ov.get("malicious").getAsBoolean();
                    if (ov.has("score")) score = safeInt(ov, "score", -1);
                    if (ov.has("categories") && ov.get("categories").isJsonArray()) {
                        for (JsonElement e : ov.getAsJsonArray("categories")) {
                            reasons.add("分類：" + e.getAsString());
                        }
                    }
                }

                // engines 匯總
                if (v.has("engines") && v.get("engines").isJsonObject()) {
                    JsonObject eng = v.getAsJsonObject("engines");
                    if (eng.has("malicious")) {
                        int m = safeInt(eng, "malicious", 0);
                        if (m > 0) reasons.add("安全引擎判為惡意數：" + m);
                        if (m >= 1) malicious = true;
                        if (score < 0) score = Math.min(100, 60 + m * 10);
                    }
                }

                // urlscan 自身分數
                if (v.has("urlscan") && v.get("urlscan").isJsonObject()) {
                    JsonObject us = v.getAsJsonObject("urlscan");
                    if (us.has("score")) {
                        int s = safeInt(us, "score", -1);
                        if (s >= 0) score = s;
                    }
                }
            }
        } catch (Exception e) {
            reasons.add("結果解析警告：" + e.getMessage());
        }

        if (score < 0) score = malicious ? 85 : 30;
        verdict = malicious ? "高風險" : (score >= 50 ? "中風險" : "低風險");
        if (reasons.isEmpty()) reasons.add("未見明確惡意跡象（建議仍留意）");
        return new RiskResult(url, verdict, score, reasons);
    }

    private int safeInt(JsonObject obj, String key, int def) {
        try { return obj.get(key).getAsInt(); } catch (Exception e) { return def; }
    }

    private void sleepQuiet(int ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }

    private String extractHost(String url) {
        try {
            URI u = URI.create(url);
            String host = u.getHost();
            return host == null ? null : host.toLowerCase();
        } catch (Exception e) {
            return null;
        }
    }

    private String stripWWW(String host) {
        if (host == null) return null;
        return host.startsWith("www.") ? host.substring(4) : host;
    }
}


