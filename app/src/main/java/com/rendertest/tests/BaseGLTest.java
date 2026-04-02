package com.rendertest.tests;

import android.opengl.GLES20;
import android.util.Log;

import com.rendertest.model.TestResult;

public abstract class BaseGLTest {
    private static final String TAG = "RenderTest";

    protected final String apiLevel;

    protected BaseGLTest(String apiLevel) {
        this.apiLevel = apiLevel;
    }

    /** 子类实现：返回本测试集的所有测试结果 */
    public abstract java.util.List<TestResult> runAll();

    /**
     * 执行单项测试：清除 GL 错误 -> 运行测试体 -> 检查 GL 错误/异常
     */
    protected TestResult runTest(String name, TestBody body) {
        // 清除之前的 GL 错误
        while (GLES20.glGetError() != GLES20.GL_NO_ERROR) { /* drain */ }

        try {
            String detail = body.execute();
            int error = GLES20.glGetError();

            String status;
            if (error == GLES20.GL_NO_ERROR) {
                status = TestResult.STATUS_PASS;
            } else {
                status = TestResult.STATUS_FAIL;
            }

            TestResult result = new TestResult(name, apiLevel, status, error, detail);
            logResult(result);
            return result;

        } catch (Exception e) {
            TestResult result = new TestResult(name, apiLevel, TestResult.STATUS_CRASH, 0,
                    e.getClass().getSimpleName() + ": " + e.getMessage());
            logResult(result);
            return result;
        }
    }

    private void logResult(TestResult result) {
        String msg = "[" + result.getStatus() + "] " + result.getName()
                + " - glError: " + result.getGlError() + " (" + result.getGlErrorName() + ")";
        if (result.getDetail() != null && !result.getDetail().isEmpty()) {
            msg += " | " + result.getDetail();
        }
        Log.i(TAG, msg);
    }

    /** 编译着色器并返回编译日志。编译失败不会抛异常，而是通过 glGetError 和日志体现 */
    protected String compileShader(int type, String source) {
        int shader = GLES20.glCreateShader(type);
        if (shader == 0) {
            return "glCreateShader returned 0";
        }
        GLES20.glShaderSource(shader, source);
        GLES20.glCompileShader(shader);

        int[] compiled = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0);
        String log = GLES20.glGetShaderInfoLog(shader);
        GLES20.glDeleteShader(shader);

        if (compiled[0] == 0) {
            return "Compile FAILED: " + log;
        }
        return "Compile OK" + (log.isEmpty() ? "" : " | " + log);
    }

    @FunctionalInterface
    protected interface TestBody {
        /** 执行测试逻辑，返回详情字符串 */
        String execute();
    }
}
