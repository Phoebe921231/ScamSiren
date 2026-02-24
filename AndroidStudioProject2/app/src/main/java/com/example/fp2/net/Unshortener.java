package com.example.fp2.net;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class Unshortener {
    private static final String UA = "Mozilla/5.0 (Android) ScamSiren/1.0";
    private static final int MAX_HOPS = 10;

    private final OkHttpClient client = new OkHttpClient.Builder()
            .followRedirects(false)
            .followSslRedirects(false)
            .callTimeout(15, TimeUnit.SECONDS)
            .build();

    public static class Result {
        public final String finalUrl;
        public final List<String> chain;
        public Result(String finalUrl, List<String> chain) {
            this.finalUrl = finalUrl;
            this.chain = chain;
        }
    }

    public Result expand(String inputUrl) throws Exception {
        String url = inputUrl.trim();
        List<String> chain = new ArrayList<>();
        for (int i = 0; i < MAX_HOPS; i++) {
            chain.add(url);
            Request headReq = new Request.Builder()
                    .url(url)
                    .head()
                    .header("User-Agent", UA)
                    .build();
            try (Response r = client.newCall(headReq).execute()) {
                int code = r.code();
                if (code >= 300 && code < 400) {
                    String loc = r.header("Location");
                    if (loc == null) break;
                    HttpUrl next = r.request().url().resolve(loc);
                    if (next == null) break;
                    String nextUrl = next.toString();
                    if (nextUrl.equals(url)) break;
                    url = nextUrl;
                    continue;
                }
                if (code == 405 || code == 403) {
                    Request getReq = new Request.Builder()
                            .url(url)
                            .get()
                            .header("Range", "bytes=0-0")
                            .header("User-Agent", UA)
                            .build();
                    try (Response g = client.newCall(getReq).execute()) {
                        int c = g.code();
                        if (c >= 300 && c < 400) {
                            String loc = g.header("Location");
                            if (loc == null) break;
                            HttpUrl next = g.request().url().resolve(loc);
                            if (next == null) break;
                            String nextUrl = next.toString();
                            if (nextUrl.equals(url)) break;
                            url = nextUrl;
                            continue;
                        }
                    }
                }
            }
            break;
        }
        return new Result(url, chain);
    }
}
