package com.example.fp2.net;

import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;

public final class ApiClient {
    private static OkHttpClient client;
    private ApiClient(){}
    public static OkHttpClient get(){
        if(client==null){
            client=new OkHttpClient.Builder()
                    .connectTimeout(60, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS)
                    .writeTimeout(60, TimeUnit.SECONDS)
                    .retryOnConnectionFailure(true)
                    .build();
        }
        return client;
    }
}
