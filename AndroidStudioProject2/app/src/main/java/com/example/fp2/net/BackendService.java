package com.example.fp2.net;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import com.example.fp2.BuildConfig;
import com.example.fp2.model.ApiResponse;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class BackendService {

    public interface Callback {
        void onSuccess(ApiResponse data);
        void onError(String message);
    }

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final Gson gson = new Gson();

    private static String baseUrl() {
        String u = BuildConfig.BASE_URL;
        Log.d("FP2_BASE_URL", "BuildConfig.BASE_URL = [" + u + "]");
        if (u.endsWith("/")) u = u.substring(0, u.length() - 1);
        return u;
    }

    public void analyzeText(String transcript, Callback cb) {
        new Thread(() -> {
            try {
                JsonObject body = new JsonObject();
                body.addProperty("text", transcript == null ? "" : transcript);

                RequestBody req = RequestBody.create(body.toString(), JSON);
                Request.Builder rb = new Request.Builder()
                        .url(baseUrl() + "/analyze_text")
                        .post(req);

                addAdminIfAny(rb);

                OkHttpClient client = ApiClient.get();
                try (Response resp = client.newCall(rb.build()).execute()) {
                    ApiResponse data = parseApiResponse(resp);
                    logMeta(data);
                    cb.onSuccess(data);
                }
            } catch (Exception e) {
                cb.onError("analyzeText 失敗：" + e.getMessage());
            }
        }).start();
    }

    public void uploadAudio(Context ctx, Uri uri, Callback cb) {
        new Thread(() -> {
            File tmp = null;
            try {
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
                MultipartBody reqBody = new MultipartBody.Builder().setType(MultipartBody.FORM)
                        .addFormDataPart("file", "audio", fileBody)
                        .build();

                Request.Builder rb = new Request.Builder()
                        .url(baseUrl() + "/upload_audio")
                        .post(reqBody);

                addAdminIfAny(rb);

                OkHttpClient client = ApiClient.get();
                try (Response resp = client.newCall(rb.build()).execute()) {
                    ApiResponse data = parseApiResponse(resp);
                    logMeta(data);
                    cb.onSuccess(data);
                }
            } catch (Exception e) {
                cb.onError("uploadAudio 失敗：" + e.getMessage());
            } finally {
                if (tmp != null) tmp.delete();
            }
        }).start();
    }

    /**
     * ✅ 核心解析：
     * - 印出原始 JSON（截斷）
     * - Gson 解析
     * - 兼容 detected_text / transcript / text
     */
    private ApiResponse parseApiResponse(Response resp) throws Exception {
        if (resp == null) throw new IllegalStateException("空回應");
        if (!resp.isSuccessful()) throw new IllegalStateException("HTTP " + resp.code());

        ResponseBody rb = resp.body();
        if (rb == null) throw new IllegalStateException("回應無內容");

        String s = rb.string();

        // ✅ 印出 raw JSON（避免太長，截斷）
        Log.d("FP2_RAW_JSON", truncate(s, 1200));

        ApiResponse r = gson.fromJson(s, ApiResponse.class);
        if (r == null) throw new IllegalStateException("解析失敗");

        // ✅ 風險欄位保底
        if (r.risk == null && !r.is_scam) r.risk = "low";

        // ✅ 兼容：如果後端不是回 detected_text，而是 transcript 或 text
        //    這樣你就算後端 key 還沒統一，上台也不會空白。
        if (isEmpty(r.detected_text)) {
            try {
                JsonObject obj = JsonParser.parseString(s).getAsJsonObject();

                // 優先 transcript -> detected_text
                if (obj.has("transcript") && !obj.get("transcript").isJsonNull()) {
                    r.detected_text = obj.get("transcript").getAsString();
                } else if (obj.has("text") && !obj.get("text").isJsonNull()) {
                    // 有些後端會用 text 放 ASR 結果
                    r.detected_text = obj.get("text").getAsString();
                }
            } catch (Exception ignore) {
                // 不要讓兼容解析影響主流程
            }
        }

        // ✅ Debug：看 detected_text 到底有沒有進來
        Log.d("FP2_DETECTED_TEXT_LEN", "len=" + (r.detected_text == null ? 0 : r.detected_text.length()));

        return r;
    }

    private static void addAdminIfAny(Request.Builder rb) {
        String k = getAdminKeySafe();
        if (k != null && !k.isEmpty()) rb.addHeader("X-Admin-Key", k);
    }

    private static String getAdminKeySafe() {
        try {
            return (String) BuildConfig.class.getField("ADMIN_KEY").get(null);
        } catch (Throwable ignore) {
            return "";
        }
    }

    private static void logMeta(ApiResponse r) {
        String m = r != null && r.meta != null ? r.meta.ollama_model : null;
        String a = r != null && r.meta != null ? r.meta.asr_backend : null;
        Log.d("FP2", "model=" + m + ",asr=" + a);
    }

    private static boolean isEmpty(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        if (s.length() <= max) return s;
        return s.substring(0, max) + "…(truncated)";
    }
}

