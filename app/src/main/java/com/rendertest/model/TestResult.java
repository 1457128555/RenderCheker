package com.rendertest.model;

public class TestResult {
    public static final String STATUS_PASS = "PASS";
    public static final String STATUS_FAIL = "FAIL";
    public static final String STATUS_CRASH = "CRASH";

    private final String name;
    private final String apiLevel;
    private final String status;
    private final int glError;
    private final String detail;

    public TestResult(String name, String apiLevel, String status, int glError, String detail) {
        this.name = name;
        this.apiLevel = apiLevel;
        this.status = status;
        this.glError = glError;
        this.detail = detail;
    }

    public String getName() { return name; }
    public String getApiLevel() { return apiLevel; }
    public String getStatus() { return status; }
    public int getGlError() { return glError; }
    public String getDetail() { return detail; }

    public String getGlErrorName() {
        switch (glError) {
            case 0x0000: return "GL_NO_ERROR";
            case 0x0500: return "GL_INVALID_ENUM";
            case 0x0501: return "GL_INVALID_VALUE";
            case 0x0502: return "GL_INVALID_OPERATION";
            case 0x0505: return "GL_OUT_OF_MEMORY";
            case 0x0506: return "GL_INVALID_FRAMEBUFFER_OPERATION";
            default: return "0x" + Integer.toHexString(glError);
        }
    }
}
