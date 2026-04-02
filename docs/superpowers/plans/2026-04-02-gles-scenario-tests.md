# GLES 场景测试 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为现有 GLES 兼容性测试器添加真实渲染场景测试，通过完整渲染管线 + 像素回读验证 API 是否真正工作，而非仅检查调用是否报错。

**Architecture:** 新建 `ScenarioBaseTest` 基类提供 FBO 创建、shader program 编译链接、像素回读和颜色比较等通用能力。在此基础上新建三个场景测试类（GLES30/31/32ScenarioTests），每个场景构建完整渲染管线并验证输出像素。最后将场景测试集成到 TestRenderer 的执行流程中。

**Tech Stack:** Android GLES 2.0 EGL context + GLES30/31/32 Java bindings, offscreen FBO rendering, glReadPixels pixel verification

---

## File Structure

| Action | Path | Responsibility |
|--------|------|---------------|
| Create | `app/src/main/java/com/rendertest/tests/ScenarioBaseTest.java` | 场景测试基类：FBO 管理、shader program 工具、像素回读与颜色验证 |
| Create | `app/src/main/java/com/rendertest/tests/GLES30ScenarioTests.java` | GLES 3.0 场景测试：MRT 渲染、Instanced 渲染验证、Transform Feedback 数据捕获、3D 纹理采样、PBO 回读 |
| Create | `app/src/main/java/com/rendertest/tests/GLES31ScenarioTests.java` | GLES 3.1 场景测试：Compute Shader SSBO 写入验证、Image Load/Store 往返、Indirect Draw 验证 |
| Create | `app/src/main/java/com/rendertest/tests/GLES32ScenarioTests.java` | GLES 3.2 场景测试：Geometry Shader 放大验证、Tessellation 细分验证 |
| Modify | `app/src/main/java/com/rendertest/TestRenderer.java:46-56` | 在 API 测试后追加场景测试执行 |

---

### Task 1: ScenarioBaseTest - 场景测试基类

**Files:**
- Create: `app/src/main/java/com/rendertest/tests/ScenarioBaseTest.java`

- [ ] **Step 1: 创建 ScenarioBaseTest 类**

```java
package com.rendertest.tests;

import android.opengl.GLES20;
import android.opengl.GLES30;

import com.rendertest.model.TestResult;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

public abstract class ScenarioBaseTest extends BaseGLTest {

    // 标准 FBO 尺寸
    protected static final int FB_WIDTH = 16;
    protected static final int FB_HEIGHT = 16;

    protected ScenarioBaseTest(String apiLevel) {
        super(apiLevel);
    }

    // ---- Shader Program 工具 ----

    /**
     * 编译链接完整的 VS+FS 程序，返回 program ID。失败抛 RuntimeException。
     */
    protected int buildProgram(String vertexSource, String fragmentSource) {
        int vs = compileAndGetShader(GLES20.GL_VERTEX_SHADER, vertexSource);
        int fs = compileAndGetShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource);

        int program = GLES20.glCreateProgram();
        if (program == 0) throw new RuntimeException("glCreateProgram returned 0");

        GLES20.glAttachShader(program, vs);
        GLES20.glAttachShader(program, fs);
        GLES20.glLinkProgram(program);

        int[] linked = new int[1];
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linked, 0);
        if (linked[0] == 0) {
            String log = GLES20.glGetProgramInfoLog(program);
            GLES20.glDeleteProgram(program);
            GLES20.glDeleteShader(vs);
            GLES20.glDeleteShader(fs);
            throw new RuntimeException("Link FAILED: " + log);
        }

        // Shader 可以 detach+delete，program 保留引用
        GLES20.glDeleteShader(vs);
        GLES20.glDeleteShader(fs);

        return program;
    }

    /**
     * 编译单个 shader，返回 shader ID。失败抛异常。
     */
    private int compileAndGetShader(int type, String source) {
        int shader = GLES20.glCreateShader(type);
        if (shader == 0) throw new RuntimeException("glCreateShader returned 0");

        GLES20.glShaderSource(shader, source);
        GLES20.glCompileShader(shader);

        int[] compiled = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0);
        if (compiled[0] == 0) {
            String log = GLES20.glGetShaderInfoLog(shader);
            GLES20.glDeleteShader(shader);
            throw new RuntimeException("Compile FAILED: " + log);
        }
        return shader;
    }

    // ---- FBO 工具 ----

    /**
     * 创建一个带颜色纹理附件的 FBO，返回 int[]{fbo, colorTexture}。
     */
    protected int[] createFBO(int width, int height) {
        int[] fbos = new int[1];
        GLES20.glGenFramebuffers(1, fbos, 0);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbos[0]);

        int[] textures = new int[1];
        GLES20.glGenTextures(1, textures, 0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[0]);
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA,
                width, height, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST);

        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER,
                GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, textures[0], 0);

        int status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER);
        if (status != GLES20.GL_FRAMEBUFFER_COMPLETE) {
            throw new RuntimeException("FBO incomplete: 0x" + Integer.toHexString(status));
        }

        return new int[]{fbos[0], textures[0]};
    }

    /**
     * 销毁 createFBO 返回的资源。
     */
    protected void destroyFBO(int[] fboAndTex) {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        GLES20.glDeleteFramebuffers(1, new int[]{fboAndTex[0]}, 0);
        GLES20.glDeleteTextures(1, new int[]{fboAndTex[1]}, 0);
    }

    // ---- 像素回读与验证 ----

    /**
     * 读取 FBO 在 (x, y) 处的像素，返回 RGBA 四字节数组（0~255）。
     */
    protected int[] readPixel(int x, int y) {
        ByteBuffer buf = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder());
        GLES20.glReadPixels(x, y, 1, 1, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buf);
        buf.position(0);
        return new int[]{
                buf.get() & 0xFF,
                buf.get() & 0xFF,
                buf.get() & 0xFF,
                buf.get() & 0xFF
        };
    }

    /**
     * 读取整个 FBO 的像素数据，返回 width*height*4 字节的 buffer。
     */
    protected ByteBuffer readPixels(int width, int height) {
        ByteBuffer buf = ByteBuffer.allocateDirect(width * height * 4).order(ByteOrder.nativeOrder());
        GLES20.glReadPixels(0, 0, width, height, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buf);
        buf.position(0);
        return buf;
    }

    /**
     * 检查像素是否与期望颜色匹配（允许 tolerance 误差，适应不同 GPU 精度）。
     */
    protected boolean colorMatches(int[] actual, int[] expected, int tolerance) {
        for (int i = 0; i < 4; i++) {
            if (Math.abs(actual[i] - expected[i]) > tolerance) return false;
        }
        return true;
    }

    /**
     * 检查像素是否非黑色（至少有一个通道 > threshold）。
     */
    protected boolean isNonBlack(int[] pixel, int threshold) {
        return pixel[0] > threshold || pixel[1] > threshold || pixel[2] > threshold;
    }

    /**
     * 统计 buffer 中非黑色像素的数量。
     */
    protected int countNonBlackPixels(ByteBuffer pixels, int totalPixels, int threshold) {
        pixels.position(0);
        int count = 0;
        for (int i = 0; i < totalPixels; i++) {
            int r = pixels.get() & 0xFF;
            int g = pixels.get() & 0xFF;
            int b = pixels.get() & 0xFF;
            int a = pixels.get() & 0xFF;
            if (r > threshold || g > threshold || b > threshold) {
                count++;
            }
        }
        return count;
    }

    // ---- 全屏四边形工具 ----

    /** 覆盖整个 viewport 的全屏三角形 strip 的顶点数据 */
    protected static final float[] FULLSCREEN_QUAD_VERTICES = {
            -1.0f, -1.0f,
             1.0f, -1.0f,
            -1.0f,  1.0f,
             1.0f,  1.0f
    };

    /**
     * 创建全屏四边形的 VAO+VBO，返回 int[]{vao, vbo}。
     */
    protected int[] createFullscreenQuad() {
        int[] vaos = new int[1];
        GLES30.glGenVertexArrays(1, vaos, 0);
        GLES30.glBindVertexArray(vaos[0]);

        int[] vbos = new int[1];
        GLES20.glGenBuffers(1, vbos, 0);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbos[0]);

        FloatBuffer fb = ByteBuffer.allocateDirect(FULLSCREEN_QUAD_VERTICES.length * 4)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        fb.put(FULLSCREEN_QUAD_VERTICES).position(0);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER,
                FULLSCREEN_QUAD_VERTICES.length * 4, fb, GLES20.GL_STATIC_DRAW);

        GLES20.glEnableVertexAttribArray(0);
        GLES20.glVertexAttribPointer(0, 2, GLES20.GL_FLOAT, false, 0, 0);

        return new int[]{vaos[0], vbos[0]};
    }

    /**
     * 销毁全屏四边形资源。
     */
    protected void destroyFullscreenQuad(int[] vaoAndVbo) {
        GLES20.glDisableVertexAttribArray(0);
        GLES20.glDeleteBuffers(1, new int[]{vaoAndVbo[1]}, 0);
        GLES30.glBindVertexArray(0);
        GLES30.glDeleteVertexArrays(1, new int[]{vaoAndVbo[0]}, 0);
    }
}
```

- [ ] **Step 2: 确认文件编译无语法错误**

Run: `cd /Users/user/Desktop/GithubWork/RenderTest && ./gradlew compileDebugJavaWithJavac 2>&1 | tail -20`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/rendertest/tests/ScenarioBaseTest.java
git commit -m "feat: add ScenarioBaseTest with FBO, shader, and pixel verification helpers"
```

---

### Task 2: GLES30ScenarioTests - GLES 3.0 场景测试

**Files:**
- Create: `app/src/main/java/com/rendertest/tests/GLES30ScenarioTests.java`

- [ ] **Step 1: 创建 GLES30ScenarioTests 类，包含场景 1 - 全屏纯色渲染验证**

这是最基础的场景：构建完整 VS+FS pipeline → 渲染到 FBO → 回读像素 → 验证颜色正确。

```java
package com.rendertest.tests;

import android.opengl.GLES20;
import android.opengl.GLES30;

import com.rendertest.model.TestResult;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;

public class GLES30ScenarioTests extends ScenarioBaseTest {

    public GLES30ScenarioTests() {
        super("GLES 3.0 场景");
    }

    @Override
    public List<TestResult> runAll() {
        List<TestResult> results = new ArrayList<>();
        results.add(scenarioFlatColorRendering());
        results.add(scenarioMRTRendering());
        results.add(scenarioInstancedRendering());
        results.add(scenarioTransformFeedbackCapture());
        results.add(scenario3DTextureSampling());
        results.add(scenarioPBOReadback());
        return results;
    }

    /**
     * 场景1：全屏纯色渲染
     * 验证：完整 VS+FS 管线在 GLES 2.0 上下文中能正确渲染并产出预期像素
     */
    private TestResult scenarioFlatColorRendering() {
        return runTest("[场景] 全屏纯色渲染", () -> {
            // Shader: 全屏四边形输出纯红色
            String vs =
                    "#version 300 es\n" +
                    "layout(location = 0) in vec2 aPos;\n" +
                    "void main() {\n" +
                    "    gl_Position = vec4(aPos, 0.0, 1.0);\n" +
                    "}\n";
            String fs =
                    "#version 300 es\n" +
                    "precision mediump float;\n" +
                    "out vec4 fragColor;\n" +
                    "void main() {\n" +
                    "    fragColor = vec4(1.0, 0.0, 0.0, 1.0);\n" +
                    "}\n";

            int program = buildProgram(vs, fs);
            int[] fbo = createFBO(FB_WIDTH, FB_HEIGHT);
            int[] quad = createFullscreenQuad();

            GLES20.glViewport(0, 0, FB_WIDTH, FB_HEIGHT);
            GLES20.glClearColor(0, 0, 0, 0);
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
            GLES20.glUseProgram(program);
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

            // 回读中心像素，期望纯红 (255, 0, 0, 255)
            int[] pixel = readPixel(FB_WIDTH / 2, FB_HEIGHT / 2);
            boolean pass = colorMatches(pixel, new int[]{255, 0, 0, 255}, 2);

            destroyFullscreenQuad(quad);
            destroyFBO(fbo);
            GLES20.glDeleteProgram(program);

            return (pass ? "VERIFIED" : "MISMATCH") +
                    " | 中心像素: RGBA(" + pixel[0] + "," + pixel[1] + "," + pixel[2] + "," + pixel[3] + ")" +
                    " 期望: RGBA(255,0,0,255)";
        });
    }

    /**
     * 场景2：MRT 多渲染目标
     * 验证：FS 同时输出到两个颜色附件，两个附件包含不同颜色
     */
    private TestResult scenarioMRTRendering() {
        return runTest("[场景] MRT 多目标渲染", () -> {
            String vs =
                    "#version 300 es\n" +
                    "layout(location = 0) in vec2 aPos;\n" +
                    "void main() {\n" +
                    "    gl_Position = vec4(aPos, 0.0, 1.0);\n" +
                    "}\n";
            // FS 输出两个颜色：attachment0=绿色, attachment1=蓝色
            String fs =
                    "#version 300 es\n" +
                    "precision mediump float;\n" +
                    "layout(location = 0) out vec4 color0;\n" +
                    "layout(location = 1) out vec4 color1;\n" +
                    "void main() {\n" +
                    "    color0 = vec4(0.0, 1.0, 0.0, 1.0);\n" +
                    "    color1 = vec4(0.0, 0.0, 1.0, 1.0);\n" +
                    "}\n";

            int program = buildProgram(vs, fs);

            // 创建带 2 个颜色附件的 FBO
            int[] fbos = new int[1];
            GLES20.glGenFramebuffers(1, fbos, 0);
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbos[0]);

            int[] textures = new int[2];
            GLES20.glGenTextures(2, textures, 0);
            for (int i = 0; i < 2; i++) {
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[i]);
                GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA,
                        FB_WIDTH, FB_HEIGHT, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
                GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER,
                        GLES20.GL_COLOR_ATTACHMENT0 + i, GLES20.GL_TEXTURE_2D, textures[i], 0);
            }

            IntBuffer drawBuffers = ByteBuffer.allocateDirect(8).order(ByteOrder.nativeOrder()).asIntBuffer();
            drawBuffers.put(new int[]{GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_COLOR_ATTACHMENT0 + 1}).position(0);
            GLES30.glDrawBuffers(2, drawBuffers);

            int status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER);
            if (status != GLES20.GL_FRAMEBUFFER_COMPLETE) {
                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
                GLES20.glDeleteFramebuffers(1, fbos, 0);
                GLES20.glDeleteTextures(2, textures, 0);
                GLES20.glDeleteProgram(program);
                return "FBO incomplete: 0x" + Integer.toHexString(status);
            }

            int[] quad = createFullscreenQuad();
            GLES20.glViewport(0, 0, FB_WIDTH, FB_HEIGHT);
            GLES20.glClearColor(0, 0, 0, 0);
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
            GLES20.glUseProgram(program);
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

            // 读 attachment0 - 期望绿色
            GLES30.glReadBuffer(GLES20.GL_COLOR_ATTACHMENT0);
            int[] pixel0 = readPixel(FB_WIDTH / 2, FB_HEIGHT / 2);

            // 读 attachment1 - 期望蓝色
            GLES30.glReadBuffer(GLES20.GL_COLOR_ATTACHMENT0 + 1);
            int[] pixel1 = readPixel(FB_WIDTH / 2, FB_HEIGHT / 2);

            boolean pass0 = colorMatches(pixel0, new int[]{0, 255, 0, 255}, 2);
            boolean pass1 = colorMatches(pixel1, new int[]{0, 0, 255, 255}, 2);

            destroyFullscreenQuad(quad);
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
            GLES20.glDeleteFramebuffers(1, fbos, 0);
            GLES20.glDeleteTextures(2, textures, 0);
            GLES20.glDeleteProgram(program);

            return (pass0 && pass1 ? "VERIFIED" : "MISMATCH") +
                    " | Attachment0: RGBA(" + pixel0[0] + "," + pixel0[1] + "," + pixel0[2] + "," + pixel0[3] + ") 期望绿" +
                    " | Attachment1: RGBA(" + pixel1[0] + "," + pixel1[1] + "," + pixel1[2] + "," + pixel1[3] + ") 期望蓝";
        });
    }

    /**
     * 场景3：Instanced 渲染
     * 验证：glDrawArraysInstanced 确实绘制了多个实例到不同位置
     */
    private TestResult scenarioInstancedRendering() {
        return runTest("[场景] Instanced 渲染验证", () -> {
            // VS 根据 gl_InstanceID 偏移位置：实例0在左半，实例1在右半
            String vs =
                    "#version 300 es\n" +
                    "layout(location = 0) in vec2 aPos;\n" +
                    "void main() {\n" +
                    "    float offset = (gl_InstanceID == 0) ? -0.5 : 0.5;\n" +
                    "    gl_Position = vec4(aPos.x * 0.4 + offset, aPos.y * 0.4, 0.0, 1.0);\n" +
                    "}\n";
            String fs =
                    "#version 300 es\n" +
                    "precision mediump float;\n" +
                    "out vec4 fragColor;\n" +
                    "void main() {\n" +
                    "    fragColor = vec4(1.0, 1.0, 0.0, 1.0);\n" +
                    "}\n";

            int program = buildProgram(vs, fs);
            int[] fbo = createFBO(FB_WIDTH, FB_HEIGHT);

            // 创建一个小三角形
            float[] triVerts = {
                    0.0f,  0.5f,
                   -0.5f, -0.5f,
                    0.5f, -0.5f
            };
            int[] vaos = new int[1];
            GLES30.glGenVertexArrays(1, vaos, 0);
            GLES30.glBindVertexArray(vaos[0]);
            int[] vbos = new int[1];
            GLES20.glGenBuffers(1, vbos, 0);
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbos[0]);
            FloatBuffer fb = ByteBuffer.allocateDirect(triVerts.length * 4)
                    .order(ByteOrder.nativeOrder()).asFloatBuffer();
            fb.put(triVerts).position(0);
            GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, triVerts.length * 4, fb, GLES20.GL_STATIC_DRAW);
            GLES20.glEnableVertexAttribArray(0);
            GLES20.glVertexAttribPointer(0, 2, GLES20.GL_FLOAT, false, 0, 0);

            GLES20.glViewport(0, 0, FB_WIDTH, FB_HEIGHT);
            GLES20.glClearColor(0, 0, 0, 0);
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
            GLES20.glUseProgram(program);
            GLES30.glDrawArraysInstanced(GLES20.GL_TRIANGLES, 0, 3, 2);

            // 检查左半和右半都有非黑像素
            int[] leftPixel = readPixel(FB_WIDTH / 4, FB_HEIGHT / 2);
            int[] rightPixel = readPixel(3 * FB_WIDTH / 4, FB_HEIGHT / 2);

            boolean leftHas = isNonBlack(leftPixel, 10);
            boolean rightHas = isNonBlack(rightPixel, 10);

            GLES20.glDisableVertexAttribArray(0);
            GLES20.glDeleteBuffers(1, vbos, 0);
            GLES30.glBindVertexArray(0);
            GLES30.glDeleteVertexArrays(1, vaos, 0);
            destroyFBO(fbo);
            GLES20.glDeleteProgram(program);

            return (leftHas && rightHas ? "VERIFIED" : "MISMATCH") +
                    " | 左侧像素: RGBA(" + leftPixel[0] + "," + leftPixel[1] + "," + leftPixel[2] + "," + leftPixel[3] + ")" +
                    " 右侧像素: RGBA(" + rightPixel[0] + "," + rightPixel[1] + "," + rightPixel[2] + "," + rightPixel[3] + ")" +
                    " 左有色=" + leftHas + " 右有色=" + rightHas;
        });
    }

    /**
     * 场景4：Transform Feedback 数据捕获
     * 验证：VS 输出的 varying 被 TF 正确捕获到 buffer 中，可通过 map 读回
     */
    private TestResult scenarioTransformFeedbackCapture() {
        return runTest("[场景] Transform Feedback 数据捕获", () -> {
            // VS 输出一个 varying = vec4(1.0, 2.0, 3.0, 4.0)
            String vs =
                    "#version 300 es\n" +
                    "layout(location = 0) in vec2 aPos;\n" +
                    "out vec4 vOutput;\n" +
                    "void main() {\n" +
                    "    gl_Position = vec4(aPos, 0.0, 1.0);\n" +
                    "    vOutput = vec4(1.0, 2.0, 3.0, 4.0);\n" +
                    "}\n";
            // FS 必须存在但不重要（TF 发生在光栅化之前）
            String fs =
                    "#version 300 es\n" +
                    "precision mediump float;\n" +
                    "out vec4 fragColor;\n" +
                    "void main() {\n" +
                    "    fragColor = vec4(0.0);\n" +
                    "}\n";

            // 手动编译链接，因为需要在 link 前设置 TF varying
            int vsShader = GLES20.glCreateShader(GLES20.GL_VERTEX_SHADER);
            GLES20.glShaderSource(vsShader, vs);
            GLES20.glCompileShader(vsShader);
            int[] compiled = new int[1];
            GLES20.glGetShaderiv(vsShader, GLES20.GL_COMPILE_STATUS, compiled, 0);
            if (compiled[0] == 0) {
                String log = GLES20.glGetShaderInfoLog(vsShader);
                GLES20.glDeleteShader(vsShader);
                throw new RuntimeException("VS compile failed: " + log);
            }

            int fsShader = GLES20.glCreateShader(GLES20.GL_FRAGMENT_SHADER);
            GLES20.glShaderSource(fsShader, fs);
            GLES20.glCompileShader(fsShader);
            GLES20.glGetShaderiv(fsShader, GLES20.GL_COMPILE_STATUS, compiled, 0);
            if (compiled[0] == 0) {
                GLES20.glDeleteShader(vsShader);
                GLES20.glDeleteShader(fsShader);
                throw new RuntimeException("FS compile failed");
            }

            int program = GLES20.glCreateProgram();
            GLES20.glAttachShader(program, vsShader);
            GLES20.glAttachShader(program, fsShader);

            // 在 link 前指定 TF varying
            GLES30.glTransformFeedbackVaryings(program, new String[]{"vOutput"}, GLES30.GL_INTERLEAVED_ATTRIBS);
            GLES20.glLinkProgram(program);

            int[] linked = new int[1];
            GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linked, 0);
            if (linked[0] == 0) {
                String log = GLES20.glGetProgramInfoLog(program);
                GLES20.glDeleteProgram(program);
                GLES20.glDeleteShader(vsShader);
                GLES20.glDeleteShader(fsShader);
                throw new RuntimeException("Link failed: " + log);
            }
            GLES20.glDeleteShader(vsShader);
            GLES20.glDeleteShader(fsShader);

            // 创建 TF buffer（存 1 个 vec4 = 16 bytes）
            int[] tfBufs = new int[1];
            GLES20.glGenBuffers(1, tfBufs, 0);
            GLES20.glBindBuffer(GLES30.GL_TRANSFORM_FEEDBACK_BUFFER, tfBufs[0]);
            GLES20.glBufferData(GLES30.GL_TRANSFORM_FEEDBACK_BUFFER, 16, null, GLES20.GL_DYNAMIC_READ);
            GLES30.glBindBufferBase(GLES30.GL_TRANSFORM_FEEDBACK_BUFFER, 0, tfBufs[0]);

            // 创建输入顶点（1 个点）
            int[] vaos = new int[1];
            GLES30.glGenVertexArrays(1, vaos, 0);
            GLES30.glBindVertexArray(vaos[0]);
            int[] vbos = new int[1];
            GLES20.glGenBuffers(1, vbos, 0);
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbos[0]);
            FloatBuffer pointData = ByteBuffer.allocateDirect(8).order(ByteOrder.nativeOrder()).asFloatBuffer();
            pointData.put(new float[]{0.0f, 0.0f}).position(0);
            GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, 8, pointData, GLES20.GL_STATIC_DRAW);
            GLES20.glEnableVertexAttribArray(0);
            GLES20.glVertexAttribPointer(0, 2, GLES20.GL_FLOAT, false, 0, 0);

            // 执行 TF
            GLES20.glUseProgram(program);
            GLES20.glEnable(GLES30.GL_RASTERIZER_DISCARD);
            GLES30.glBeginTransformFeedback(GLES20.GL_POINTS);
            GLES20.glDrawArrays(GLES20.GL_POINTS, 0, 1);
            GLES30.glEndTransformFeedback();
            GLES20.glDisable(GLES30.GL_RASTERIZER_DISCARD);

            // 回读 TF buffer
            GLES20.glBindBuffer(GLES30.GL_TRANSFORM_FEEDBACK_BUFFER, tfBufs[0]);
            ByteBuffer mapped = (ByteBuffer) GLES30.glMapBufferRange(
                    GLES30.GL_TRANSFORM_FEEDBACK_BUFFER, 0, 16, GLES30.GL_MAP_READ_BIT);

            float v0 = 0, v1 = 0, v2 = 0, v3 = 0;
            boolean dataValid = false;
            if (mapped != null) {
                mapped.order(ByteOrder.nativeOrder());
                FloatBuffer fb = mapped.asFloatBuffer();
                v0 = fb.get(0);
                v1 = fb.get(1);
                v2 = fb.get(2);
                v3 = fb.get(3);
                dataValid = (Math.abs(v0 - 1.0f) < 0.01f &&
                             Math.abs(v1 - 2.0f) < 0.01f &&
                             Math.abs(v2 - 3.0f) < 0.01f &&
                             Math.abs(v3 - 4.0f) < 0.01f);
                GLES30.glUnmapBuffer(GLES30.GL_TRANSFORM_FEEDBACK_BUFFER);
            }

            // 清理
            GLES20.glDisableVertexAttribArray(0);
            GLES20.glDeleteBuffers(1, vbos, 0);
            GLES30.glBindVertexArray(0);
            GLES30.glDeleteVertexArrays(1, vaos, 0);
            GLES20.glDeleteBuffers(1, tfBufs, 0);
            GLES20.glDeleteProgram(program);

            return (dataValid ? "VERIFIED" : "MISMATCH") +
                    " | TF 捕获: (" + v0 + ", " + v1 + ", " + v2 + ", " + v3 + ") 期望: (1.0, 2.0, 3.0, 4.0)" +
                    " mapped=" + (mapped != null);
        });
    }

    /**
     * 场景5：3D 纹理采样
     * 验证：填充 3D 纹理已知颜色 → FS 采样 → 回读验证
     */
    private TestResult scenario3DTextureSampling() {
        return runTest("[场景] 3D 纹理采样", () -> {
            // 创建 2x2x2 的 3D 纹理，全部填充为黄色 (255, 255, 0, 255)
            int[] textures = new int[1];
            GLES20.glGenTextures(1, textures, 0);
            GLES20.glBindTexture(GLES30.GL_TEXTURE_3D, textures[0]);

            byte[] texData = new byte[2 * 2 * 2 * 4]; // 2x2x2, RGBA
            for (int i = 0; i < 2 * 2 * 2; i++) {
                texData[i * 4] = (byte) 255;     // R
                texData[i * 4 + 1] = (byte) 255; // G
                texData[i * 4 + 2] = 0;          // B
                texData[i * 4 + 3] = (byte) 255; // A
            }
            ByteBuffer texBuf = ByteBuffer.allocateDirect(texData.length).order(ByteOrder.nativeOrder());
            texBuf.put(texData).position(0);

            GLES30.glTexImage3D(GLES30.GL_TEXTURE_3D, 0, GLES20.GL_RGBA,
                    2, 2, 2, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, texBuf);
            GLES20.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
            GLES20.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST);
            GLES20.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_WRAP_R, GLES20.GL_CLAMP_TO_EDGE);

            // Shader: 用 3D 纹理的中心坐标采样
            String vs =
                    "#version 300 es\n" +
                    "layout(location = 0) in vec2 aPos;\n" +
                    "void main() {\n" +
                    "    gl_Position = vec4(aPos, 0.0, 1.0);\n" +
                    "}\n";
            String fs =
                    "#version 300 es\n" +
                    "precision mediump float;\n" +
                    "precision mediump sampler3D;\n" +
                    "uniform sampler3D uTex3D;\n" +
                    "out vec4 fragColor;\n" +
                    "void main() {\n" +
                    "    fragColor = texture(uTex3D, vec3(0.5, 0.5, 0.5));\n" +
                    "}\n";

            int program = buildProgram(vs, fs);
            int texLoc = GLES20.glGetUniformLocation(program, "uTex3D");

            int[] fbo = createFBO(FB_WIDTH, FB_HEIGHT);
            int[] quad = createFullscreenQuad();

            GLES20.glViewport(0, 0, FB_WIDTH, FB_HEIGHT);
            GLES20.glClearColor(0, 0, 0, 0);
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
            GLES20.glUseProgram(program);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES30.GL_TEXTURE_3D, textures[0]);
            GLES20.glUniform1i(texLoc, 0);
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

            int[] pixel = readPixel(FB_WIDTH / 2, FB_HEIGHT / 2);
            boolean pass = colorMatches(pixel, new int[]{255, 255, 0, 255}, 2);

            destroyFullscreenQuad(quad);
            destroyFBO(fbo);
            GLES20.glDeleteTextures(1, textures, 0);
            GLES20.glDeleteProgram(program);

            return (pass ? "VERIFIED" : "MISMATCH") +
                    " | 采样像素: RGBA(" + pixel[0] + "," + pixel[1] + "," + pixel[2] + "," + pixel[3] + ")" +
                    " 期望黄色: RGBA(255,255,0,255)";
        });
    }

    /**
     * 场景6：PBO 异步回读
     * 验证：渲染已知颜色 → PBO readback → map buffer → 验证像素数据
     */
    private TestResult scenarioPBOReadback() {
        return runTest("[场景] PBO 异步回读", () -> {
            // 先渲染纯青色 (0,255,255,255) 到 FBO
            String vs =
                    "#version 300 es\n" +
                    "layout(location = 0) in vec2 aPos;\n" +
                    "void main() {\n" +
                    "    gl_Position = vec4(aPos, 0.0, 1.0);\n" +
                    "}\n";
            String fs =
                    "#version 300 es\n" +
                    "precision mediump float;\n" +
                    "out vec4 fragColor;\n" +
                    "void main() {\n" +
                    "    fragColor = vec4(0.0, 1.0, 1.0, 1.0);\n" +
                    "}\n";

            int program = buildProgram(vs, fs);
            int[] fbo = createFBO(FB_WIDTH, FB_HEIGHT);
            int[] quad = createFullscreenQuad();

            GLES20.glViewport(0, 0, FB_WIDTH, FB_HEIGHT);
            GLES20.glClearColor(0, 0, 0, 0);
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
            GLES20.glUseProgram(program);
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

            // 用 PBO 回读
            int pboSize = FB_WIDTH * FB_HEIGHT * 4;
            int[] pbos = new int[1];
            GLES20.glGenBuffers(1, pbos, 0);
            GLES20.glBindBuffer(GLES30.GL_PIXEL_PACK_BUFFER, pbos[0]);
            GLES20.glBufferData(GLES30.GL_PIXEL_PACK_BUFFER, pboSize, null, GLES20.GL_STREAM_READ);

            // glReadPixels 到 PBO（offset=0 而非 buffer 指针）
            GLES20.glReadPixels(0, 0, FB_WIDTH, FB_HEIGHT, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, 0);

            // 同步等待
            long fence = GLES30.glFenceSync(GLES30.GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
            GLES30.glClientWaitSync(fence, GLES30.GL_SYNC_FLUSH_COMMANDS_BIT, 100000000L);
            GLES30.glDeleteSync(fence);

            // Map PBO 读数据
            ByteBuffer mapped = (ByteBuffer) GLES30.glMapBufferRange(
                    GLES30.GL_PIXEL_PACK_BUFFER, 0, pboSize, GLES30.GL_MAP_READ_BIT);

            int r = 0, g = 0, b = 0, a = 0;
            boolean dataValid = false;
            if (mapped != null) {
                mapped.order(ByteOrder.nativeOrder());
                // 读中心像素
                int centerOffset = (FB_HEIGHT / 2 * FB_WIDTH + FB_WIDTH / 2) * 4;
                r = mapped.get(centerOffset) & 0xFF;
                g = mapped.get(centerOffset + 1) & 0xFF;
                b = mapped.get(centerOffset + 2) & 0xFF;
                a = mapped.get(centerOffset + 3) & 0xFF;
                dataValid = (Math.abs(r - 0) < 3 && Math.abs(g - 255) < 3 &&
                             Math.abs(b - 255) < 3 && Math.abs(a - 255) < 3);
                GLES30.glUnmapBuffer(GLES30.GL_PIXEL_PACK_BUFFER);
            }

            GLES20.glBindBuffer(GLES30.GL_PIXEL_PACK_BUFFER, 0);
            GLES20.glDeleteBuffers(1, pbos, 0);
            destroyFullscreenQuad(quad);
            destroyFBO(fbo);
            GLES20.glDeleteProgram(program);

            return (dataValid ? "VERIFIED" : "MISMATCH") +
                    " | PBO 读取中心像素: RGBA(" + r + "," + g + "," + b + "," + a + ")" +
                    " 期望青色: RGBA(0,255,255,255) mapped=" + (mapped != null);
        });
    }
}
```

- [ ] **Step 2: 确认编译通过**

Run: `cd /Users/user/Desktop/GithubWork/RenderTest && ./gradlew compileDebugJavaWithJavac 2>&1 | tail -20`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/rendertest/tests/GLES30ScenarioTests.java
git commit -m "feat: add GLES 3.0 scenario tests with pixel verification"
```

---

### Task 3: GLES31ScenarioTests - GLES 3.1 场景测试

**Files:**
- Create: `app/src/main/java/com/rendertest/tests/GLES31ScenarioTests.java`

- [ ] **Step 1: 创建 GLES31ScenarioTests 类**

```java
package com.rendertest.tests;

import android.opengl.GLES20;
import android.opengl.GLES30;
import android.opengl.GLES31;

import com.rendertest.model.TestResult;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;

public class GLES31ScenarioTests extends ScenarioBaseTest {

    public GLES31ScenarioTests() {
        super("GLES 3.1 场景");
    }

    @Override
    public List<TestResult> runAll() {
        List<TestResult> results = new ArrayList<>();
        results.add(scenarioComputeShaderSSBOWrite());
        results.add(scenarioImageLoadStoreRoundTrip());
        results.add(scenarioIndirectDrawFromBuffer());
        return results;
    }

    /**
     * 场景1：Compute Shader 写入 SSBO
     * 验证：Compute Shader 向 SSBO 写入已知数据 → 回读验证数据正确
     */
    private TestResult scenarioComputeShaderSSBOWrite() {
        return runTest("[场景] Compute Shader SSBO 写入", () -> {
            String csSource =
                    "#version 310 es\n" +
                    "layout(local_size_x = 4) in;\n" +
                    "layout(std430, binding = 0) buffer OutputBuffer {\n" +
                    "    float data[];\n" +
                    "};\n" +
                    "void main() {\n" +
                    "    uint idx = gl_GlobalInvocationID.x;\n" +
                    "    data[idx] = float(idx) * 10.0;\n" +
                    "}\n";

            // 编译计算着色器
            int cs = GLES20.glCreateShader(GLES31.GL_COMPUTE_SHADER);
            GLES20.glShaderSource(cs, csSource);
            GLES20.glCompileShader(cs);
            int[] compiled = new int[1];
            GLES20.glGetShaderiv(cs, GLES20.GL_COMPILE_STATUS, compiled, 0);
            if (compiled[0] == 0) {
                String log = GLES20.glGetShaderInfoLog(cs);
                GLES20.glDeleteShader(cs);
                throw new RuntimeException("Compute shader compile failed: " + log);
            }

            int program = GLES20.glCreateProgram();
            GLES20.glAttachShader(program, cs);
            GLES20.glLinkProgram(program);
            int[] linked = new int[1];
            GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linked, 0);
            if (linked[0] == 0) {
                String log = GLES20.glGetProgramInfoLog(program);
                GLES20.glDeleteProgram(program);
                GLES20.glDeleteShader(cs);
                throw new RuntimeException("Compute program link failed: " + log);
            }
            GLES20.glDeleteShader(cs);

            // 创建 SSBO（4 个 float = 16 bytes），初始化为 0
            int[] ssbos = new int[1];
            GLES20.glGenBuffers(1, ssbos, 0);
            GLES20.glBindBuffer(GLES31.GL_SHADER_STORAGE_BUFFER, ssbos[0]);
            ByteBuffer initData = ByteBuffer.allocateDirect(16).order(ByteOrder.nativeOrder());
            for (int i = 0; i < 16; i++) initData.put((byte) 0);
            initData.position(0);
            GLES20.glBufferData(GLES31.GL_SHADER_STORAGE_BUFFER, 16, initData, GLES20.GL_DYNAMIC_READ);
            GLES30.glBindBufferBase(GLES31.GL_SHADER_STORAGE_BUFFER, 0, ssbos[0]);

            // Dispatch
            GLES20.glUseProgram(program);
            GLES31.glDispatchCompute(1, 1, 1); // 1 group x 4 threads
            GLES31.glMemoryBarrier(GLES31.GL_BUFFER_UPDATE_BARRIER_BIT);

            // 回读 SSBO
            GLES20.glBindBuffer(GLES31.GL_SHADER_STORAGE_BUFFER, ssbos[0]);
            ByteBuffer mapped = (ByteBuffer) GLES30.glMapBufferRange(
                    GLES31.GL_SHADER_STORAGE_BUFFER, 0, 16, GLES30.GL_MAP_READ_BIT);

            float[] values = new float[4];
            boolean dataValid = false;
            if (mapped != null) {
                mapped.order(ByteOrder.nativeOrder());
                FloatBuffer fb = mapped.asFloatBuffer();
                for (int i = 0; i < 4; i++) values[i] = fb.get(i);
                // 期望 [0.0, 10.0, 20.0, 30.0]
                dataValid = (Math.abs(values[0] - 0.0f) < 0.01f &&
                             Math.abs(values[1] - 10.0f) < 0.01f &&
                             Math.abs(values[2] - 20.0f) < 0.01f &&
                             Math.abs(values[3] - 30.0f) < 0.01f);
                GLES30.glUnmapBuffer(GLES31.GL_SHADER_STORAGE_BUFFER);
            }

            GLES20.glDeleteBuffers(1, ssbos, 0);
            GLES20.glDeleteProgram(program);

            return (dataValid ? "VERIFIED" : "MISMATCH") +
                    " | SSBO: [" + values[0] + ", " + values[1] + ", " + values[2] + ", " + values[3] + "]" +
                    " 期望: [0.0, 10.0, 20.0, 30.0] mapped=" + (mapped != null);
        });
    }

    /**
     * 场景2：Image Load/Store 往返
     * 验证：Compute Shader 通过 imageStore 写入纹理 → FS 通过 texture() 采样 → 回读验证
     */
    private TestResult scenarioImageLoadStoreRoundTrip() {
        return runTest("[场景] Image Store → Texture Sample 往返", () -> {
            // Step 1: 创建目标纹理 (4x4, RGBA8)
            int[] textures = new int[1];
            GLES20.glGenTextures(1, textures, 0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[0]);
            GLES30.glTexStorage2D(GLES20.GL_TEXTURE_2D, 1, GLES30.GL_RGBA8, 4, 4);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST);

            // Step 2: Compute shader 写入洋红色 (255, 0, 255, 255) 到纹理
            String csSource =
                    "#version 310 es\n" +
                    "layout(local_size_x = 4, local_size_y = 4) in;\n" +
                    "layout(rgba8, binding = 0) writeonly uniform highp image2D uImage;\n" +
                    "void main() {\n" +
                    "    ivec2 pos = ivec2(gl_GlobalInvocationID.xy);\n" +
                    "    imageStore(uImage, pos, vec4(1.0, 0.0, 1.0, 1.0));\n" +
                    "}\n";

            int cs = GLES20.glCreateShader(GLES31.GL_COMPUTE_SHADER);
            GLES20.glShaderSource(cs, csSource);
            GLES20.glCompileShader(cs);
            int[] compiled = new int[1];
            GLES20.glGetShaderiv(cs, GLES20.GL_COMPILE_STATUS, compiled, 0);
            if (compiled[0] == 0) {
                String log = GLES20.glGetShaderInfoLog(cs);
                GLES20.glDeleteShader(cs);
                GLES20.glDeleteTextures(1, textures, 0);
                throw new RuntimeException("CS compile failed: " + log);
            }

            int csProgram = GLES20.glCreateProgram();
            GLES20.glAttachShader(csProgram, cs);
            GLES20.glLinkProgram(csProgram);
            int[] linked = new int[1];
            GLES20.glGetProgramiv(csProgram, GLES20.GL_LINK_STATUS, linked, 0);
            if (linked[0] == 0) {
                GLES20.glDeleteProgram(csProgram);
                GLES20.glDeleteShader(cs);
                GLES20.glDeleteTextures(1, textures, 0);
                throw new RuntimeException("CS link failed");
            }
            GLES20.glDeleteShader(cs);

            // 绑定 image unit 并 dispatch
            GLES20.glUseProgram(csProgram);
            GLES31.glBindImageTexture(0, textures[0], 0, false, 0,
                    GLES31.GL_WRITE_ONLY, GLES30.GL_RGBA8);
            GLES31.glDispatchCompute(1, 1, 1); // 4x4 threads 覆盖 4x4 纹理
            GLES31.glMemoryBarrier(GLES31.GL_SHADER_IMAGE_ACCESS_BARRIER_BIT |
                                   GLES31.GL_TEXTURE_FETCH_BARRIER_BIT);
            GLES20.glDeleteProgram(csProgram);

            // Step 3: 用渲染管线采样该纹理，输出到 FBO
            String vs =
                    "#version 300 es\n" +
                    "layout(location = 0) in vec2 aPos;\n" +
                    "out vec2 vUV;\n" +
                    "void main() {\n" +
                    "    gl_Position = vec4(aPos, 0.0, 1.0);\n" +
                    "    vUV = aPos * 0.5 + 0.5;\n" +
                    "}\n";
            String fs =
                    "#version 300 es\n" +
                    "precision mediump float;\n" +
                    "uniform sampler2D uTex;\n" +
                    "in vec2 vUV;\n" +
                    "out vec4 fragColor;\n" +
                    "void main() {\n" +
                    "    fragColor = texture(uTex, vUV);\n" +
                    "}\n";

            int renderProgram = buildProgram(vs, fs);
            int texLoc = GLES20.glGetUniformLocation(renderProgram, "uTex");

            int[] fbo = createFBO(FB_WIDTH, FB_HEIGHT);
            int[] quad = createFullscreenQuad();

            GLES20.glViewport(0, 0, FB_WIDTH, FB_HEIGHT);
            GLES20.glClearColor(0, 0, 0, 0);
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
            GLES20.glUseProgram(renderProgram);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[0]);
            GLES20.glUniform1i(texLoc, 0);
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

            // 回读并验证 - 期望洋红色
            int[] pixel = readPixel(FB_WIDTH / 2, FB_HEIGHT / 2);
            boolean pass = colorMatches(pixel, new int[]{255, 0, 255, 255}, 2);

            destroyFullscreenQuad(quad);
            destroyFBO(fbo);
            GLES20.glDeleteTextures(1, textures, 0);
            GLES20.glDeleteProgram(renderProgram);

            return (pass ? "VERIFIED" : "MISMATCH") +
                    " | 采样像素: RGBA(" + pixel[0] + "," + pixel[1] + "," + pixel[2] + "," + pixel[3] + ")" +
                    " 期望洋红: RGBA(255,0,255,255)";
        });
    }

    /**
     * 场景3：Indirect Draw 从 Buffer
     * 验证：用 buffer 提供 draw command → 执行 indirect draw → 回读验证确实渲染了
     */
    private TestResult scenarioIndirectDrawFromBuffer() {
        return runTest("[场景] Indirect Draw 渲染验证", () -> {
            String vs =
                    "#version 300 es\n" +
                    "layout(location = 0) in vec2 aPos;\n" +
                    "void main() {\n" +
                    "    gl_Position = vec4(aPos, 0.0, 1.0);\n" +
                    "}\n";
            String fs =
                    "#version 300 es\n" +
                    "precision mediump float;\n" +
                    "out vec4 fragColor;\n" +
                    "void main() {\n" +
                    "    fragColor = vec4(1.0, 0.5, 0.0, 1.0);\n" +
                    "}\n";

            int program = buildProgram(vs, fs);
            int[] fbo = createFBO(FB_WIDTH, FB_HEIGHT);

            // 全屏三角形 strip
            int[] quad = createFullscreenQuad();

            // Indirect draw command: {count=4, instanceCount=1, first=0, reserved=0}
            int[] cmd = {4, 1, 0, 0};
            ByteBuffer cmdBuf = ByteBuffer.allocateDirect(16).order(ByteOrder.nativeOrder());
            cmdBuf.asIntBuffer().put(cmd);
            int[] indirectBuf = new int[1];
            GLES20.glGenBuffers(1, indirectBuf, 0);
            GLES20.glBindBuffer(GLES31.GL_DRAW_INDIRECT_BUFFER, indirectBuf[0]);
            GLES20.glBufferData(GLES31.GL_DRAW_INDIRECT_BUFFER, 16, cmdBuf, GLES20.GL_STATIC_DRAW);

            GLES20.glViewport(0, 0, FB_WIDTH, FB_HEIGHT);
            GLES20.glClearColor(0, 0, 0, 0);
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
            GLES20.glUseProgram(program);
            GLES31.glDrawArraysIndirect(GLES20.GL_TRIANGLE_STRIP, 0);

            // 回读 - 期望橙色 (255, 128, 0, 255)
            int[] pixel = readPixel(FB_WIDTH / 2, FB_HEIGHT / 2);
            boolean pass = colorMatches(pixel, new int[]{255, 128, 0, 255}, 5);

            GLES20.glDeleteBuffers(1, indirectBuf, 0);
            destroyFullscreenQuad(quad);
            destroyFBO(fbo);
            GLES20.glDeleteProgram(program);

            return (pass ? "VERIFIED" : "MISMATCH") +
                    " | 像素: RGBA(" + pixel[0] + "," + pixel[1] + "," + pixel[2] + "," + pixel[3] + ")" +
                    " 期望橙色: RGBA(255,128,0,255)";
        });
    }
}
```

- [ ] **Step 2: 确认编译通过**

Run: `cd /Users/user/Desktop/GithubWork/RenderTest && ./gradlew compileDebugJavaWithJavac 2>&1 | tail -20`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/rendertest/tests/GLES31ScenarioTests.java
git commit -m "feat: add GLES 3.1 scenario tests - compute SSBO, image load/store, indirect draw"
```

---

### Task 4: GLES32ScenarioTests - GLES 3.2 场景测试

**Files:**
- Create: `app/src/main/java/com/rendertest/tests/GLES32ScenarioTests.java`

- [ ] **Step 1: 创建 GLES32ScenarioTests 类**

```java
package com.rendertest.tests;

import android.opengl.GLES20;
import android.opengl.GLES30;
import android.opengl.GLES31;
import android.opengl.GLES32;

import com.rendertest.model.TestResult;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

public class GLES32ScenarioTests extends ScenarioBaseTest {

    public GLES32ScenarioTests() {
        super("GLES 3.2 场景");
    }

    @Override
    public List<TestResult> runAll() {
        List<TestResult> results = new ArrayList<>();
        results.add(scenarioGeometryShaderAmplification());
        results.add(scenarioTessellationRendering());
        return results;
    }

    /**
     * 场景1：Geometry Shader 放大
     * 验证：输入 1 个点 → GS 输出一个全屏三角形 → 回读验证像素覆盖
     */
    private TestResult scenarioGeometryShaderAmplification() {
        return runTest("[场景] Geometry Shader 放大", () -> {
            String vs =
                    "#version 320 es\n" +
                    "void main() {\n" +
                    "    gl_Position = vec4(0.0, 0.0, 0.0, 1.0);\n" +
                    "}\n";

            // GS: 输入一个点，输出一个覆盖全屏的三角形（3 个顶点）
            String gs =
                    "#version 320 es\n" +
                    "layout(points) in;\n" +
                    "layout(triangle_strip, max_vertices = 3) out;\n" +
                    "void main() {\n" +
                    "    gl_Position = vec4(-1.0, -1.0, 0.0, 1.0);\n" +
                    "    EmitVertex();\n" +
                    "    gl_Position = vec4( 3.0, -1.0, 0.0, 1.0);\n" +
                    "    EmitVertex();\n" +
                    "    gl_Position = vec4(-1.0,  3.0, 0.0, 1.0);\n" +
                    "    EmitVertex();\n" +
                    "    EndPrimitive();\n" +
                    "}\n";

            String fs =
                    "#version 320 es\n" +
                    "precision mediump float;\n" +
                    "out vec4 fragColor;\n" +
                    "void main() {\n" +
                    "    fragColor = vec4(0.0, 1.0, 0.5, 1.0);\n" +
                    "}\n";

            // 手动编译链接（3 个 shader stage）
            int vsShader = compileShaderStage(GLES20.GL_VERTEX_SHADER, vs);
            int gsShader = compileShaderStage(GLES32.GL_GEOMETRY_SHADER, gs);
            int fsShader = compileShaderStage(GLES20.GL_FRAGMENT_SHADER, fs);

            int program = GLES20.glCreateProgram();
            GLES20.glAttachShader(program, vsShader);
            GLES20.glAttachShader(program, gsShader);
            GLES20.glAttachShader(program, fsShader);
            GLES20.glLinkProgram(program);

            int[] linked = new int[1];
            GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linked, 0);
            if (linked[0] == 0) {
                String log = GLES20.glGetProgramInfoLog(program);
                GLES20.glDeleteProgram(program);
                GLES20.glDeleteShader(vsShader);
                GLES20.glDeleteShader(gsShader);
                GLES20.glDeleteShader(fsShader);
                throw new RuntimeException("GS program link failed: " + log);
            }
            GLES20.glDeleteShader(vsShader);
            GLES20.glDeleteShader(gsShader);
            GLES20.glDeleteShader(fsShader);

            // 创建一个空 VAO（绘制 1 个点，不需要顶点数据）
            int[] vaos = new int[1];
            GLES30.glGenVertexArrays(1, vaos, 0);
            GLES30.glBindVertexArray(vaos[0]);

            int[] fbo = createFBO(FB_WIDTH, FB_HEIGHT);

            GLES20.glViewport(0, 0, FB_WIDTH, FB_HEIGHT);
            GLES20.glClearColor(0, 0, 0, 0);
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
            GLES20.glUseProgram(program);
            GLES20.glDrawArrays(GLES20.GL_POINTS, 0, 1);

            // 回读 - GS 生成的三角形应该覆盖全屏
            int[] pixel = readPixel(FB_WIDTH / 2, FB_HEIGHT / 2);
            boolean centerHasColor = isNonBlack(pixel, 10);

            // 检查覆盖率（全屏三角形应该覆盖大部分像素）
            ByteBuffer allPixels = readPixels(FB_WIDTH, FB_HEIGHT);
            int nonBlack = countNonBlackPixels(allPixels, FB_WIDTH * FB_HEIGHT, 10);
            int totalPixels = FB_WIDTH * FB_HEIGHT;
            float coverage = (float) nonBlack / totalPixels * 100;

            GLES30.glBindVertexArray(0);
            GLES30.glDeleteVertexArrays(1, vaos, 0);
            destroyFBO(fbo);
            GLES20.glDeleteProgram(program);

            return (centerHasColor && coverage > 90 ? "VERIFIED" : "MISMATCH") +
                    " | 中心像素: RGBA(" + pixel[0] + "," + pixel[1] + "," + pixel[2] + "," + pixel[3] + ")" +
                    " | 覆盖率: " + String.format("%.1f", coverage) + "% (" + nonBlack + "/" + totalPixels + ")";
        });
    }

    /**
     * 场景2：Tessellation 细分渲染
     * 验证：输入 patch → TCS/TES 细分 → 渲染 → 回读验证有像素输出
     */
    private TestResult scenarioTessellationRendering() {
        return runTest("[场景] Tessellation 细分渲染", () -> {
            String vs =
                    "#version 320 es\n" +
                    "layout(location = 0) in vec2 aPos;\n" +
                    "void main() {\n" +
                    "    gl_Position = vec4(aPos, 0.0, 1.0);\n" +
                    "}\n";

            String tcs =
                    "#version 320 es\n" +
                    "layout(vertices = 3) out;\n" +
                    "void main() {\n" +
                    "    gl_out[gl_InvocationID].gl_Position = gl_in[gl_InvocationID].gl_Position;\n" +
                    "    if (gl_InvocationID == 0) {\n" +
                    "        gl_TessLevelInner[0] = 3.0;\n" +
                    "        gl_TessLevelOuter[0] = 3.0;\n" +
                    "        gl_TessLevelOuter[1] = 3.0;\n" +
                    "        gl_TessLevelOuter[2] = 3.0;\n" +
                    "    }\n" +
                    "}\n";

            String tes =
                    "#version 320 es\n" +
                    "layout(triangles, equal_spacing, ccw) in;\n" +
                    "void main() {\n" +
                    "    gl_Position = gl_TessCoord.x * gl_in[0].gl_Position\n" +
                    "               + gl_TessCoord.y * gl_in[1].gl_Position\n" +
                    "               + gl_TessCoord.z * gl_in[2].gl_Position;\n" +
                    "}\n";

            String fs =
                    "#version 320 es\n" +
                    "precision mediump float;\n" +
                    "out vec4 fragColor;\n" +
                    "void main() {\n" +
                    "    fragColor = vec4(0.5, 0.0, 1.0, 1.0);\n" +
                    "}\n";

            // 编译链接 4 个 shader stage
            int vsShader = compileShaderStage(GLES20.GL_VERTEX_SHADER, vs);
            int tcsShader = compileShaderStage(GLES32.GL_TESS_CONTROL_SHADER, tcs);
            int tesShader = compileShaderStage(GLES32.GL_TESS_EVALUATION_SHADER, tes);
            int fsShader = compileShaderStage(GLES20.GL_FRAGMENT_SHADER, fs);

            int program = GLES20.glCreateProgram();
            GLES20.glAttachShader(program, vsShader);
            GLES20.glAttachShader(program, tcsShader);
            GLES20.glAttachShader(program, tesShader);
            GLES20.glAttachShader(program, fsShader);
            GLES20.glLinkProgram(program);

            int[] linked = new int[1];
            GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linked, 0);
            if (linked[0] == 0) {
                String log = GLES20.glGetProgramInfoLog(program);
                GLES20.glDeleteProgram(program);
                GLES20.glDeleteShader(vsShader);
                GLES20.glDeleteShader(tcsShader);
                GLES20.glDeleteShader(tesShader);
                GLES20.glDeleteShader(fsShader);
                throw new RuntimeException("Tessellation program link failed: " + log);
            }
            GLES20.glDeleteShader(vsShader);
            GLES20.glDeleteShader(tcsShader);
            GLES20.glDeleteShader(tesShader);
            GLES20.glDeleteShader(fsShader);

            // 创建一个大三角形作为 patch 输入
            float[] patchVerts = {
                    0.0f,  0.9f,
                   -0.9f, -0.9f,
                    0.9f, -0.9f
            };

            int[] vaos = new int[1];
            GLES30.glGenVertexArrays(1, vaos, 0);
            GLES30.glBindVertexArray(vaos[0]);
            int[] vbos = new int[1];
            GLES20.glGenBuffers(1, vbos, 0);
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbos[0]);
            FloatBuffer fb = ByteBuffer.allocateDirect(patchVerts.length * 4)
                    .order(ByteOrder.nativeOrder()).asFloatBuffer();
            fb.put(patchVerts).position(0);
            GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, patchVerts.length * 4, fb, GLES20.GL_STATIC_DRAW);
            GLES20.glEnableVertexAttribArray(0);
            GLES20.glVertexAttribPointer(0, 2, GLES20.GL_FLOAT, false, 0, 0);

            int[] fbo = createFBO(FB_WIDTH, FB_HEIGHT);

            GLES20.glViewport(0, 0, FB_WIDTH, FB_HEIGHT);
            GLES20.glClearColor(0, 0, 0, 0);
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
            GLES20.glUseProgram(program);

            // GL_PATCHES = 0x000E
            GLES32.glPatchParameteri(GLES32.GL_PATCH_VERTICES, 3);
            GLES20.glDrawArrays(0x000E, 0, 3);

            // 回读 - 应该有大量像素被细分三角形覆盖
            int[] pixel = readPixel(FB_WIDTH / 2, FB_HEIGHT / 3);
            boolean centerHasColor = isNonBlack(pixel, 10);

            ByteBuffer allPixels = readPixels(FB_WIDTH, FB_HEIGHT);
            int nonBlack = countNonBlackPixels(allPixels, FB_WIDTH * FB_HEIGHT, 10);
            int totalPixels = FB_WIDTH * FB_HEIGHT;
            float coverage = (float) nonBlack / totalPixels * 100;

            GLES20.glDisableVertexAttribArray(0);
            GLES20.glDeleteBuffers(1, vbos, 0);
            GLES30.glBindVertexArray(0);
            GLES30.glDeleteVertexArrays(1, vaos, 0);
            destroyFBO(fbo);
            GLES20.glDeleteProgram(program);

            return (centerHasColor && coverage > 30 ? "VERIFIED" : "MISMATCH") +
                    " | 像素: RGBA(" + pixel[0] + "," + pixel[1] + "," + pixel[2] + "," + pixel[3] + ")" +
                    " | 覆盖率: " + String.format("%.1f", coverage) + "% (" + nonBlack + "/" + totalPixels + ")";
        });
    }

    /**
     * 编译单个 shader stage，返回 shader ID。失败抛异常。
     */
    private int compileShaderStage(int type, String source) {
        int shader = GLES20.glCreateShader(type);
        if (shader == 0) throw new RuntimeException("glCreateShader returned 0 for type 0x" + Integer.toHexString(type));

        GLES20.glShaderSource(shader, source);
        GLES20.glCompileShader(shader);

        int[] compiled = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0);
        if (compiled[0] == 0) {
            String log = GLES20.glGetShaderInfoLog(shader);
            GLES20.glDeleteShader(shader);
            throw new RuntimeException("Shader compile failed: " + log);
        }
        return shader;
    }
}
```

- [ ] **Step 2: 确认编译通过**

Run: `cd /Users/user/Desktop/GithubWork/RenderTest && ./gradlew compileDebugJavaWithJavac 2>&1 | tail -20`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/rendertest/tests/GLES32ScenarioTests.java
git commit -m "feat: add GLES 3.2 scenario tests - geometry shader, tessellation"
```

---

### Task 5: 集成到 TestRenderer

**Files:**
- Modify: `app/src/main/java/com/rendertest/TestRenderer.java:46-56`

- [ ] **Step 1: 在 TestRenderer 中添加场景测试执行**

在现有 API 测试之后，追加场景测试执行：

```java
// 在 GLES32Tests 之后添加：

Log.i(TAG, "=== GLES 3.0 场景测试 ===");
allResults.addAll(new GLES30ScenarioTests().runAll());

Log.i(TAG, "=== GLES 3.1 场景测试 ===");
allResults.addAll(new GLES31ScenarioTests().runAll());

Log.i(TAG, "=== GLES 3.2 场景测试 ===");
allResults.addAll(new GLES32ScenarioTests().runAll());
```

需要新增 import：

```java
import com.rendertest.tests.GLES30ScenarioTests;
import com.rendertest.tests.GLES31ScenarioTests;
import com.rendertest.tests.GLES32ScenarioTests;
```

- [ ] **Step 2: 确认编译通过**

Run: `cd /Users/user/Desktop/GithubWork/RenderTest && ./gradlew compileDebugJavaWithJavac 2>&1 | tail -20`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/rendertest/TestRenderer.java
git commit -m "feat: integrate scenario tests into TestRenderer execution flow"
```

---

### Task 6: 最终验证

- [ ] **Step 1: 完整构建验证**

Run: `cd /Users/user/Desktop/GithubWork/RenderTest && ./gradlew assembleDebug 2>&1 | tail -20`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: 确认所有文件无遗漏**

检查新增文件清单：
- `app/src/main/java/com/rendertest/tests/ScenarioBaseTest.java`
- `app/src/main/java/com/rendertest/tests/GLES30ScenarioTests.java`
- `app/src/main/java/com/rendertest/tests/GLES31ScenarioTests.java`
- `app/src/main/java/com/rendertest/tests/GLES32ScenarioTests.java`
- `app/src/main/java/com/rendertest/TestRenderer.java` (modified)
