package com.example.fp2.net;

import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;

public class ApiClient {
    private static OkHttpClient client;
    public static OkHttpClient get() {
        if (client == null) {
            client = new OkHttpClient.Builder()
                    .connectTimeout(5, TimeUnit.SECONDS)
                    .readTimeout(120, TimeUnit.SECONDS)
                    .writeTimeout(120, TimeUnit.SECONDS)
                    .retryOnConnectionFailure(true)
                    .build();
        }
        return client;
    }
}
