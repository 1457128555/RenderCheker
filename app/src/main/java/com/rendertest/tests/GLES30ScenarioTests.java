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

            GLES30.glReadBuffer(GLES20.GL_COLOR_ATTACHMENT0);
            int[] pixel0 = readPixel(FB_WIDTH / 2, FB_HEIGHT / 2);
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

            int[] leftPixel = readPixel(FB_WIDTH / 4, FB_HEIGHT / 2);
            int[] rightPixel = readPixel(3 * FB_WIDTH / 4, FB_HEIGHT / 2);
            boolean leftHas = isNonBlack(leftPixel, 10);
            boolean rightHas = isNonBlack(rightPixel, 10);

            GLES20.glDeleteBuffers(1, vbos, 0);
            GLES30.glBindVertexArray(0);
            GLES30.glDeleteVertexArrays(1, vaos, 0);
            destroyFBO(fbo);
            GLES20.glDeleteProgram(program);

            return (leftHas && rightHas ? "VERIFIED" : "MISMATCH") +
                    " | 左侧: RGBA(" + leftPixel[0] + "," + leftPixel[1] + "," + leftPixel[2] + "," + leftPixel[3] + ")" +
                    " 右侧: RGBA(" + rightPixel[0] + "," + rightPixel[1] + "," + rightPixel[2] + "," + rightPixel[3] + ")" +
                    " 左有色=" + leftHas + " 右有色=" + rightHas;
        });
    }

    /**
     * 场景4：Transform Feedback 数据捕获
     * 验证：VS 输出的 varying 被 TF 正确捕获到 buffer 中，可通过 map 读回
     */
    private TestResult scenarioTransformFeedbackCapture() {
        return runTest("[场景] Transform Feedback 数据捕获", () -> {
            String vs =
                    "#version 300 es\n" +
                    "layout(location = 0) in vec2 aPos;\n" +
                    "out vec4 vOutput;\n" +
                    "void main() {\n" +
                    "    gl_Position = vec4(aPos, 0.0, 1.0);\n" +
                    "    vOutput = vec4(1.0, 2.0, 3.0, 4.0);\n" +
                    "}\n";
            String fs =
                    "#version 300 es\n" +
                    "precision mediump float;\n" +
                    "out vec4 fragColor;\n" +
                    "void main() {\n" +
                    "    fragColor = vec4(0.0);\n" +
                    "}\n";

            // 手动编译链接，需要在 link 前设置 TF varying
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
            GLES20.glBufferData(GLES30.GL_TRANSFORM_FEEDBACK_BUFFER, 16, null, GLES30.GL_DYNAMIC_READ);
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
                FloatBuffer tfb = mapped.asFloatBuffer();
                v0 = tfb.get(0); v1 = tfb.get(1); v2 = tfb.get(2); v3 = tfb.get(3);
                dataValid = (Math.abs(v0 - 1.0f) < 0.01f && Math.abs(v1 - 2.0f) < 0.01f &&
                             Math.abs(v2 - 3.0f) < 0.01f && Math.abs(v3 - 4.0f) < 0.01f);
                GLES30.glUnmapBuffer(GLES30.GL_TRANSFORM_FEEDBACK_BUFFER);
            }

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
            int[] textures = new int[1];
            GLES20.glGenTextures(1, textures, 0);
            GLES20.glBindTexture(GLES30.GL_TEXTURE_3D, textures[0]);

            byte[] texData = new byte[2 * 2 * 2 * 4];
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

            int pboSize = FB_WIDTH * FB_HEIGHT * 4;
            int[] pbos = new int[1];
            GLES20.glGenBuffers(1, pbos, 0);
            GLES20.glBindBuffer(GLES30.GL_PIXEL_PACK_BUFFER, pbos[0]);
            GLES20.glBufferData(GLES30.GL_PIXEL_PACK_BUFFER, pboSize, null, GLES30.GL_STREAM_READ);

            GLES30.glReadPixels(0, 0, FB_WIDTH, FB_HEIGHT, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, 0);

            long fence = GLES30.glFenceSync(GLES30.GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
            GLES30.glClientWaitSync(fence, GLES30.GL_SYNC_FLUSH_COMMANDS_BIT, 100000000L);
            GLES30.glDeleteSync(fence);

            ByteBuffer mapped = (ByteBuffer) GLES30.glMapBufferRange(
                    GLES30.GL_PIXEL_PACK_BUFFER, 0, pboSize, GLES30.GL_MAP_READ_BIT);

            int r = 0, g = 0, b = 0, a = 0;
            boolean dataValid = false;
            if (mapped != null) {
                mapped.order(ByteOrder.nativeOrder());
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
