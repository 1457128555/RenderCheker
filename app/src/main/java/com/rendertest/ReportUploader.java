package com.rendertest;

import android.util.Log;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.util.concurrent.TimeUnit;

public class ReportUploader {
    private static final String TAG = "RenderTest";
    private static final String SERVER_URL = "http://10.0.2.2:5000/api/report";

    private static final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build();

    public static void upload(String jsonReport) {
        RequestBody body = RequestBody.create(
                jsonReport, MediaType.parse("application/json; charset=utf-8"));

        Request request = new Request.Builder()
                .url(SERVER_URL)
                .post(body)
                .build();

        client.newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(okhttp3.Call call, java.io.IOException e) {
                Log.w(TAG, "Report upload failed: " + e.getMessage());
            }

            @Override
            public void onResponse(okhttp3.Call call, Response response) {
                if (response.isSuccessful()) {
                    Log.i(TAG, "Report uploaded successfully");
                } else {
                    Log.w(TAG, "Report upload returned: " + response.code());
                }
                response.close();
            }
        });
    }
}
