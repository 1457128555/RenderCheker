package com.rendertest.tests;

import android.opengl.GLES20;
import android.opengl.GLES30;
import android.opengl.GLES31;

import com.rendertest.model.TestResult;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
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
            GLES20.glBufferData(GLES31.GL_SHADER_STORAGE_BUFFER, 16, initData, GLES20.GL_DYNAMIC_DRAW);
            GLES30.glBindBufferBase(GLES31.GL_SHADER_STORAGE_BUFFER, 0, ssbos[0]);

            GLES20.glUseProgram(program);
            GLES31.glDispatchCompute(1, 1, 1);
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
            // 创建目标纹理 (4x4, RGBA8)
            int[] textures = new int[1];
            GLES20.glGenTextures(1, textures, 0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[0]);
            GLES30.glTexStorage2D(GLES20.GL_TEXTURE_2D, 1, GLES30.GL_RGBA8, 4, 4);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST);

            // Compute shader 写入洋红色
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

            GLES20.glUseProgram(csProgram);
            GLES31.glBindImageTexture(0, textures[0], 0, false, 0,
                    GLES31.GL_WRITE_ONLY, GLES30.GL_RGBA8);
            GLES31.glDispatchCompute(1, 1, 1);
            GLES31.glMemoryBarrier(GLES31.GL_SHADER_IMAGE_ACCESS_BARRIER_BIT |
                                   GLES31.GL_TEXTURE_FETCH_BARRIER_BIT);
            GLES20.glDeleteProgram(csProgram);

            // 用渲染管线采样该纹理
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
