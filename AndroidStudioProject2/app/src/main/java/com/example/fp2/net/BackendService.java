package com.example.fp2.net;

import android.content.Context;
import android.net.Uri;

import com.example.fp2.BuildConfig;
import com.example.fp2.model.ApiResponse;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * 後端呼叫：
 *  - /upload_audio   -> 回 { text, analysis:{...}, meta:{...} }
 *  - /analyze_text   -> 回 { text, analysis:{...}, meta:{...} }
 * 這裡會把根物件中的 analysis 解析成 ApiResponse 給前端使用。
 */
public class BackendService {

    private static final String BASE_URL = BuildConfig.BASE_URL;                  // e.g. http://10.0.2.2:5000
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final Gson gson = new Gson();

    // 可選的 Admin Key（沒定義就當空字串）
    private static final String ADMIN_KEY = getAdminKeySafe();

    public interface Callback {
        void onSuccess(ApiResponse data);
        void onError(String message);
    }

    // ================== 對外方法 ==================

    /** 文字詐騙分析（給 OCR 或手動輸入用） */
    public void analyzeText(String transcript, Callback cb) {
        new Thread(() -> {
            try {
                String bodyJson = "{\"text\":\"" + escapeForJson(transcript) + "\"}";
                RequestBody body = RequestBody.create(bodyJson, JSON);

                Request.Builder rb = new Request.Builder()
                        .url(BASE_URL + "/analyze_text")
                        .post(body);
                addAdminIfAny(rb);

                OkHttpClient client = ApiClient.get();
                try (Response resp = client.newCall(rb.build()).execute()) {
                    ApiResponse data = parseApiResponse(resp);
                    cb.onSuccess(data);
                }
            } catch (Exception e) {
                cb.onError("analyzeText 失敗：" + e.getMessage());
            }
        }).start();
    }

    /** 上傳音檔做語音詐騙分析 */
    public void uploadAudio(Context ctx, Uri uri, Callback cb) {
        new Thread(() -> {
            File tmp = null;
            try {
                // 將 content:// 轉存到暫存實體檔（避免一次把整個檔案讀進記憶體）
                tmp = new File(ctx.getCacheDir(), "upload_" + System.currentTimeMillis());
                try (InputStream in = ctx.getContentResolver().openInputStream(uri);
                     OutputStream out = new FileOutputStream(tmp)) {
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                }

                String mime = ctx.getContentResolver().getType(uri);
                if (mime == null || mime.trim().isEmpty()) mime = "audio/mp4";

                RequestBody fileBody = RequestBody.create(tmp, MediaType.parse(mime));
                MultipartBody reqBody = new MultipartBody.Builder()
                        .setType(MultipartBody.FORM)
                        .addFormDataPart("file", "audio", fileBody)
                        .build();

                Request request = new Request.Builder()
                        .url(BASE_URL + "/upload_audio")
                        .post(reqBody)
                        .build();

                OkHttpClient client = ApiClient.get();
                try (Response resp = client.newCall(request).execute()) {
                    ApiResponse data = parseApiResponse(resp);
                    cb.onSuccess(data);
                }
            } catch (Exception e) {
                cb.onError("uploadAudio 失敗：" + e.getMessage());
            } finally {
                if (tmp != null) tmp.delete();
            }
        }).start();
    }

    // ================== 共用工具 ==================

    /** 從 Response 取出字串並解析出根物件中的 analysis → 轉成 ApiResponse */
    private ApiResponse parseApiResponse(Response resp) throws Exception {
        if (resp == null) throw new IllegalStateException("空回應");
        if (!resp.isSuccessful()) throw new IllegalStateException("HTTP " + resp.code());

        ResponseBody rb = resp.body();
        if (rb == null) throw new IllegalStateException("回應無內容");

        String s = rb.string();
        // 先嘗試把根物件的 "analysis" 取出；如果沒有，就嘗試把整段當成 ApiResponse（後端直出）
        JsonElement rootEl = JsonParser.parseString(s);
        if (!rootEl.isJsonObject()) throw new IllegalStateException("非 JSON 物件：" + s);

        JsonObject root = rootEl.getAsJsonObject();

        // 後端錯誤格式：{ "error": "..." }
        if (root.has("error")) {
            String err = root.get("error").getAsString();
            throw new IllegalStateException(err);
        }

        JsonElement analysisEl = root.get("analysis");
        if (analysisEl != null && analysisEl.isJsonObject()) {
            return gson.fromJson(analysisEl, ApiResponse.class);
        }

        // 備援：有些情況後端可能直接回 analysis 結構
        return gson.fromJson(s, ApiResponse.class);
    }

    /** 需要時帶上 X-Admin-Key */
    private void addAdminIfAny(Request.Builder rb) {
        if (ADMIN_KEY != null && !ADMIN_KEY.isEmpty()) {
            rb.addHeader("X-Admin-Key", ADMIN_KEY);
        }
    }

    /** 讀取 BuildConfig.ADMIN_KEY（若不存在則回空字串，不拋例外） */
    private static String getAdminKeySafe() {
        try {
            // 如果在 build.gradle.kts 沒宣告 ADMIN_KEY，這裡會丟出 NoSuchFieldError/Exception
            return (String) BuildConfig.class.getField("ADMIN_KEY").get(null);
        } catch (Throwable ignore) {
            return "";
        }
    }

    /** 簡單轉義雙引號，避免壞 JSON */
    private static String escapeForJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}

