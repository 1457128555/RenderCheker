package com.rendertest;

import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.util.Log;

import com.rendertest.model.DeviceInfo;
import com.rendertest.model.TestResult;
import com.rendertest.tests.GLES30Tests;
import com.rendertest.tests.GLES31Tests;
import com.rendertest.tests.GLES32Tests;

import java.util.ArrayList;
import java.util.List;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class TestRenderer implements GLSurfaceView.Renderer {
    private static final String TAG = "RenderTest";

    public interface Callback {
        void onDeviceInfo(DeviceInfo info);
        void onTestResults(DeviceInfo deviceInfo, List<TestResult> results);
    }

    private final Callback callback;
    private boolean testsExecuted = false;

    public TestRenderer(Callback callback) {
        this.callback = callback;
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        if (testsExecuted) return;
        testsExecuted = true;

        // 收集设备信息
        DeviceInfo deviceInfo = new DeviceInfo();
        deviceInfo.collectGLInfo();
        logDeviceInfo(deviceInfo);
        callback.onDeviceInfo(deviceInfo);

        // 执行所有测试
        Log.i(TAG, "=== GLES 3.0 测试 ===");
        List<TestResult> allResults = new ArrayList<>();
        allResults.addAll(new GLES30Tests().runAll());

        Log.i(TAG, "=== GLES 3.1 测试 ===");
        allResults.addAll(new GLES31Tests().runAll());

        Log.i(TAG, "=== GLES 3.2 测试 ===");
        allResults.addAll(new GLES32Tests().runAll());

        callback.onTestResults(deviceInfo, allResults);
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        GLES20.glViewport(0, 0, width, height);
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
    }

    private void logDeviceInfo(DeviceInfo info) {
        Log.i(TAG, "=== 设备信息 ===");
        Log.i(TAG, "型号: " + info.getDeviceModel());
        Log.i(TAG, "Android: " + info.getAndroidVersion());
        Log.i(TAG, "GPU: " + info.getGlRenderer());
        Log.i(TAG, "GL: " + info.getGlVersion());
        Log.i(TAG, "GLSL: " + info.getGlslVersion());
        Log.i(TAG, "Vendor: " + info.getGlVendor());
    }
}
