package com.example.fp2.net;

import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;

public final class ApiClient {
    private static OkHttpClient client;
    private ApiClient(){}
    public static OkHttpClient get(){
        if(client==null){
            client=new OkHttpClient.Builder()
                    .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(300, java.util.concurrent.TimeUnit.SECONDS)   // ⭐ 5 分鐘
                    .writeTimeout(300, java.util.concurrent.TimeUnit.SECONDS)
                    .retryOnConnectionFailure(true)
                    .build();

        }
        return client;
    }
}
