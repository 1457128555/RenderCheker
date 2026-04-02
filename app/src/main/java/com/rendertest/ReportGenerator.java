package com.rendertest;

import android.content.Context;
import android.util.Log;

import com.rendertest.model.DeviceInfo;
import com.rendertest.model.TestResult;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ReportGenerator {
    private static final String TAG = "RenderTest";

    public static class Result {
        public final File file;
        public final String json;

        Result(File file, String json) {
            this.file = file;
            this.json = json;
        }
    }

    public static Result generateAndSave(Context context, DeviceInfo deviceInfo, List<TestResult> results) {
        try {
            String json = generateJson(deviceInfo, results);
            File file = saveToFile(context, deviceInfo, json);
            return new Result(file, json);
        } catch (Exception e) {
            Log.e(TAG, "Failed to generate report", e);
            return null;
        }
    }

    private static String generateJson(DeviceInfo deviceInfo, List<TestResult> results) throws Exception {
        JSONObject root = new JSONObject();

        // timestamp
        root.put("timestamp", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(new Date()));

        // device
        JSONObject device = new JSONObject();
        device.put("model", deviceInfo.getDeviceModel());
        device.put("android", deviceInfo.getAndroidVersion());
        device.put("gpu", deviceInfo.getGlRenderer());
        device.put("glVersion", deviceInfo.getGlVersion());
        device.put("glslVersion", deviceInfo.getGlslVersion());
        device.put("vendor", deviceInfo.getGlVendor());
        root.put("device", device);

        // summary
        int pass = 0, fail = 0, crash = 0;
        for (TestResult r : results) {
            switch (r.getStatus()) {
                case TestResult.STATUS_PASS: pass++; break;
                case TestResult.STATUS_FAIL: fail++; break;
                case TestResult.STATUS_CRASH: crash++; break;
            }
        }
        JSONObject summary = new JSONObject();
        summary.put("total", results.size());
        summary.put("pass", pass);
        summary.put("fail", fail);
        summary.put("crash", crash);
        root.put("summary", summary);

        // results
        JSONArray resultsArray = new JSONArray();
        for (TestResult r : results) {
            JSONObject item = new JSONObject();
            item.put("name", r.getName());
            item.put("apiLevel", r.getApiLevel());
            item.put("status", r.getStatus());
            item.put("glError", r.getGlError());
            item.put("glErrorName", r.getGlErrorName());
            item.put("detail", r.getDetail());
            resultsArray.put(item);
        }
        root.put("results", resultsArray);

        return root.toString(2);
    }

    private static File saveToFile(Context context, DeviceInfo deviceInfo, String json) throws Exception {
        File dir = new File(context.getExternalFilesDir(null), "reports");
        if (!dir.exists()) {
            dir.mkdirs();
        }

        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        String model = deviceInfo.getDeviceModel().replaceAll("[^a-zA-Z0-9]", "");
        String fileName = "RenderTest_" + model + "_" + timestamp + ".json";

        File file = new File(dir, fileName);
        try (OutputStreamWriter writer = new OutputStreamWriter(
                new FileOutputStream(file), "UTF-8")) {
            writer.write(json);
        }

        Log.i(TAG, "Report saved to: " + file.getAbsolutePath());
        return file;
    }
}
