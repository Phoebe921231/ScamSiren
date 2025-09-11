package com.example.fp2.net;

import android.content.Context;
import android.net.Uri;
import android.util.Log;
import com.example.fp2.BuildConfig;
import com.example.fp2.model.ApiResponse;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
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
    public interface Callback { void onSuccess(ApiResponse data); void onError(String message); }

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final Gson gson = new Gson();

    private static String baseUrl(){
        String u = BuildConfig.BASE_URL;
        if (u.endsWith("/")) u = u.substring(0, u.length()-1);
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
                    int n; while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                }
                String mime = ctx.getContentResolver().getType(uri);
                if (mime == null || mime.trim().isEmpty()) mime = "audio/mp4";
                RequestBody fileBody = RequestBody.create(tmp, MediaType.parse(mime));
                MultipartBody reqBody = new MultipartBody.Builder().setType(MultipartBody.FORM)
                        .addFormDataPart("file", "audio", fileBody).build();
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

    private ApiResponse parseApiResponse(Response resp) throws Exception {
        if (resp == null) throw new IllegalStateException("空回應");
        if (!resp.isSuccessful()) throw new IllegalStateException("HTTP " + resp.code());
        ResponseBody rb = resp.body();
        if (rb == null) throw new IllegalStateException("回應無內容");
        String s = rb.string();
        ApiResponse r = gson.fromJson(s, ApiResponse.class);
        if (r == null) throw new IllegalStateException("解析失敗");
        if (r.risk == null && !r.is_scam) r.risk = "low";
        return r;
    }

    private static void addAdminIfAny(Request.Builder rb) {
        String k = getAdminKeySafe();
        if (k != null && !k.isEmpty()) rb.addHeader("X-Admin-Key", k);
    }

    private static String getAdminKeySafe() {
        try { return (String) BuildConfig.class.getField("ADMIN_KEY").get(null); }
        catch (Throwable ignore) { return ""; }
    }

    private static void logMeta(ApiResponse r){
        String m = r!=null && r.meta!=null ? r.meta.ollama_model : null;
        String a = r!=null && r.meta!=null ? r.meta.asr_backend : null;
        Log.d("FP2","model="+m+",asr="+a);
    }
}


