package com.rendertest.model;

import android.opengl.GLES20;
import android.os.Build;

public class DeviceInfo {
    private final String deviceModel;
    private final String androidVersion;
    private String glVendor;
    private String glRenderer;
    private String glVersion;
    private String glslVersion;
    private String extensions;

    public DeviceInfo() {
        this.deviceModel = Build.MODEL;
        this.androidVersion = Build.VERSION.RELEASE;
    }

    /** 必须在 GL 线程中调用 */
    public void collectGLInfo() {
        this.glVendor = GLES20.glGetString(GLES20.GL_VENDOR);
        this.glRenderer = GLES20.glGetString(GLES20.GL_RENDERER);
        this.glVersion = GLES20.glGetString(GLES20.GL_VERSION);
        this.glslVersion = GLES20.glGetString(GLES20.GL_SHADING_LANGUAGE_VERSION);
        this.extensions = GLES20.glGetString(GLES20.GL_EXTENSIONS);
    }

    public String getDeviceModel() { return deviceModel; }
    public String getAndroidVersion() { return androidVersion; }
    public String getGlVendor() { return glVendor; }
    public String getGlRenderer() { return glRenderer; }
    public String getGlVersion() { return glVersion; }
    public String getGlslVersion() { return glslVersion; }
    public String getExtensions() { return extensions; }

    public String getSummary() {
        return deviceModel + " | Android " + androidVersion + "\n"
             + "GPU: " + glRenderer + "\n"
             + "GL: " + glVersion;
    }
}
