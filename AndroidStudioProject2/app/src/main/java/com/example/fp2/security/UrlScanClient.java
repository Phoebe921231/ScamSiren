package com.example.fp2.security;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class UrlScanClient {

    public interface Callback {
        void onSuccess(RiskResult result);
        void onFailure(String message);
    }

    private static final String TAG = "UrlScanClient";
    private static final String BASE = "https://urlscan.io/api/v1";
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private final OkHttpClient http;
    private final String apiKey;

    private static final int MAX_POLLS = 15;
    private static final long POLL_SLEEP_MS = 1500;

    public UrlScanClient(String apiKey) {
        this.apiKey = apiKey == null ? "" : apiKey;
        this.http = new OkHttpClient.Builder().retryOnConnectionFailure(true).build();
    }

    public void evaluate(String targetUrl, Callback cb) {
        new Thread(() -> {
            try {
                RiskResult rr = evaluateBlocking(targetUrl);
                if (rr != null) cb.onSuccess(rr);
                else cb.onFailure("無法取得結果");
            } catch (Exception e) {
                Log.e(TAG, "evaluate error", e);
                cb.onFailure("發生錯誤：" + e.getMessage());
            }
        }).start();
    }

    private RiskResult evaluateBlocking(String targetUrl) throws Exception {
        JSONObject resultJson = fetchLatestResult(targetUrl);

        // 只信任 overall，因此 strong 也只看 overall
        // 若目前結果不夠 strong 且有 API key，才主動 scan 再 poll 一次拿更新結果
        if (!isStrong(resultJson) && hasKey()) {
            String uuid = postScan(targetUrl);
            if (uuid != null) {
                JSONObject polled = pollResult(uuid);
                if (isVerdictReady(polled)) resultJson = polled;
            }
        }

        if (resultJson == null) {
            return new RiskResult(targetUrl, "未知", 0, listOf("無可用結果"), "", "");
        }
        return parseRisk(targetUrl, resultJson);
    }

    private JSONObject fetchLatestResult(String url) throws IOException, JSONException {
        String q1 = "task.url:\"" + url + "\"";
        JSONObject s1 = getJson(BASE + "/search/?q=" + enc(q1) + "&size=1&sort=desc");
        JSONObject r = extractResult(s1);
        if (r != null && isVerdictReady(r)) return r;

        String q2 = "page.url:\"" + url + "\"";
        JSONObject s2 = getJson(BASE + "/search/?q=" + enc(q2) + "&size=1&sort=desc");
        return extractResult(s2);
    }

    private JSONObject extractResult(JSONObject search) throws IOException, JSONException {
        if (search == null) return null;
        JSONArray results = search.optJSONArray("results");
        if (results == null || results.length() == 0) return null;

        JSONObject first = results.optJSONObject(0);
        if (first == null) return null;

        String resultLink = first.optString("result", null);
        String uuid = first.optString("_id", null);
        if (uuid == null) {
            JSONObject task = first.optJSONObject("task");
            if (task != null) uuid = task.optString("uuid", null);
        }

        if (resultLink != null) return getJson(resultLink);
        if (uuid != null) return getJson(BASE + "/result/" + uuid + "/");
        return null;
    }

    private boolean hasKey() {
        return apiKey != null && !apiKey.trim().isEmpty();
    }

    private JSONObject getJson(String url) throws IOException, JSONException {
        Request req = new Request.Builder()
                .url(url)
                .header("User-Agent", "fp2-urlscan/1.0")
                .build();
        try (Response resp = http.newCall(req).execute()) {
            if (!resp.isSuccessful()) return null;
            String body = resp.body() != null ? resp.body().string() : null;
            return body != null ? new JSONObject(body) : null;
        }
    }

    private String postScan(String targetUrl) throws IOException, JSONException {
        JSONObject payload = new JSONObject();
        payload.put("url", targetUrl);
        payload.put("visibility", "public");

        Request.Builder b = new Request.Builder()
                .url(BASE + "/scan/")
                .post(RequestBody.create(payload.toString(), JSON))
                .header("User-Agent", "fp2-urlscan/1.0")
                .header("Content-Type", "application/json");

        if (hasKey()) b.header("API-Key", apiKey);

        Request req = b.build();
        try (Response resp = http.newCall(req).execute()) {
            if (!resp.isSuccessful()) return null;
            String body = resp.body() != null ? resp.body().string() : null;
            if (body == null) return null;
            JSONObject o = new JSONObject(body);
            return o.optString("uuid", null);
        }
    }

    private JSONObject pollResult(String uuid) throws IOException, JSONException, InterruptedException {
        String url = BASE + "/result/" + uuid + "/";
        JSONObject latest = null;
        for (int i = 0; i < MAX_POLLS; i++) {
            latest = getJson(url);
            if (isVerdictReady(latest)) return latest;
            Thread.sleep(POLL_SLEEP_MS);
        }
        return latest;
    }

    /**
     * ✅ 改：只認 overall 是否具備資訊（score/malicious/categories/tags）
     * engines/urlscan 不再作為 ready 條件
     */
    private boolean isVerdictReady(JSONObject result) {
        if (result == null) return false;
        JSONObject verdicts = result.optJSONObject("verdicts");
        if (verdicts == null) return false;

        JSONObject overall = verdicts.optJSONObject("overall");
        if (overall == null) return false;

        boolean hasScore = overall.has("score");
        boolean hasMal = overall.has("malicious");
        JSONArray cats = overall.optJSONArray("categories");
        JSONArray tags = overall.optJSONArray("tags");

        boolean hasCats = cats != null && cats.length() > 0;
        boolean hasTags = tags != null && tags.length() > 0;

        return hasScore || hasMal || hasCats || hasTags;
    }

    /**
     * ✅ 改：strong 也只看 overall（惡意 or phishing/malware）
     */
    private boolean isStrong(JSONObject result) {
        if (!isVerdictReady(result)) return false;

        JSONObject verdicts = result.optJSONObject("verdicts");
        JSONObject overall = verdicts != null ? verdicts.optJSONObject("overall") : null;
        if (overall == null) return false;

        if (overall.optBoolean("malicious", false)) return true;

        JSONArray cats = overall.optJSONArray("categories");
        JSONArray tags = overall.optJSONArray("tags");
        return containsAny(cats, "phishing", "malware") || containsAny(tags, "phishing", "malware");
    }

    private boolean containsAny(JSONArray arr, String... keys) {
        if (arr == null) return false;
        for (int i = 0; i < arr.length(); i++) {
            String s = arr.optString(i, "").toLowerCase();
            for (String k : keys) if (s.contains(k)) return true;
        }
        return false;
    }

    /**
     * ✅ 改：完全信任 overall
     * - 分數只取 overall.score
     * - 不看 engines、不看 urlscan.score
     */
    private RiskResult parseRisk(String targetUrl, JSONObject result) {
        List<String> reasons = new ArrayList<>();
        JSONObject verdicts = result.optJSONObject("verdicts");
        JSONObject overall  = verdicts != null ? verdicts.optJSONObject("overall") : null;

        // 若 overall 缺失，依你「只信 overall」哲學：直接回未知
        if (overall == null) {
            return new RiskResult(targetUrl, "未知", 0, listOf("verdicts.overall 缺失，無法判定"), "", "");
        }

        boolean overallMal = overall.optBoolean("malicious", false);
        int overallScore   = overall.optInt("score", 0);

        List<String> kinds = new ArrayList<>();
        JSONArray cats = overall.optJSONArray("categories");
        if (cats != null && cats.length() > 0) {
            reasons.add("Categories: " + cats.toString());
            kinds.addAll(toKeys(cats));
        }
        JSONArray tags = overall.optJSONArray("tags");
        if (tags != null && tags.length() > 0) {
            reasons.add("Tags: " + tags.toString());
            kinds.addAll(toKeys(tags));
        }

        // ✅ 分數只用 overall
        int score = overallScore;

        String verdict;
        if (overallMal || hasKind(kinds, "phishing") || hasKind(kinds, "malware")) {
            verdict = "高風險";
            score = Math.max(score, 80); // 保底 80（保留你原本邏輯）
        } else if (score >= 40) {
            verdict = "中風險";
        } else {
            verdict = "安全";
        }

        String summary = buildSummary(verdict, kinds);
        String advice  = buildAdvice(verdict, kinds);

        if (reasons.isEmpty()) reasons.add("未見明確惡意訊號；請留意上下文並持續監控");
        return new RiskResult(targetUrl, verdict, score, reasons, summary, advice);
    }

    private static List<String> toKeys(JSONArray arr) {
        LinkedHashSet<String> set = new LinkedHashSet<>();
        if (arr == null) return new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            String s = arr.optString(i, "").trim().toLowerCase();
            if (!s.isEmpty()) set.add(s);
        }
        return new ArrayList<>(set);
    }

    private static boolean hasKind(List<String> kinds, String key) {
        for (String k : kinds) if (k.contains(key)) return true;
        return false;
    }

    private static String zhKind(String k) {
        if (k.contains("phishing")) return "釣魚";
        if (k.contains("malware_download") || k.equals("malware")) return "惡意軟體";
        if (k.contains("scam") || k.contains("fraud")) return "詐騙";
        if (k.contains("defacement")) return "網頁竄改";
        if (k.contains("suspicious")) return "可疑活動";
        if (k.contains("adult") || k.contains("porn")) return "成人內容";
        if (k.contains("crypto")) return "加密貨幣相關";
        return k;
    }

    private static String buildSummary(String verdict, List<String> kinds) {
        List<String> zh = new ArrayList<>();
        for (String k : kinds) {
            String t = zhKind(k);
            if (!zh.contains(t)) zh.add(t);
        }
        String type = zh.isEmpty() ? "一般風險" : join("、", zh);
        if ("高風險".equals(verdict)) return "判定為高風險，疑似：" + type + "。";
        if ("中風險".equals(verdict)) return "判定為中風險，疑似：" + type + "。";
        if ("安全".equals(verdict))  return "判定為安全網址。";
        return "風險等級未知。";
    }

    private static String buildAdvice(String verdict, List<String> kinds) {
        String high = "請勿開啟或互動，立即關閉頁面。不要登入、不要輸入個資或一次性驗證碼，不下載檔案、不掃描 QR，改由本人主動透過官方網站或 165 查證。";
        String mid  = "不要輸入帳密或驗證碼、不要下載或安裝，建議關閉頁面並改以官方管道查證。";
        String safe = "屬安全網址，但仍建議避免輸入一次性驗證碼或下載不明檔案；如有疑慮改以官方管道查證。";
        String extra = "";
        if (hasKind(kinds, "phishing")) extra += "常見為偽裝登入或客服頁以竊取帳密與一次性驗證碼。";
        if (hasKind(kinds, "malware"))  extra += "可能誘導下載安裝，造成裝置被控或資料外洩。";
        if (hasKind(kinds, "scam") || hasKind(kinds, "fraud")) extra += "可能引導轉帳、加好友或要求掃碼付款。";
        if (hasKind(kinds, "defacement")) extra += "網站內容可能遭竄改，資訊不可信。";
        if ("高風險".equals(verdict)) return high + extra;
        if ("中風險".equals(verdict)) return mid + extra;
        if ("安全".equals(verdict))  return safe;
        return safe;
    }

    private static List<String> listOf(String s) {
        List<String> l = new ArrayList<>();
        l.add(s);
        return l;
    }

    private static String join(String sep, List<String> items) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) sb.append(sep);
            sb.append(items.get(i));
        }
        return sb.toString();
    }

    private static String enc(String s) throws java.io.UnsupportedEncodingException {
        return URLEncoder.encode(s, StandardCharsets.UTF_8.name());
    }
}