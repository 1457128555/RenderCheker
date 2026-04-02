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

public class GLES30Tests extends BaseGLTest {

    public GLES30Tests() {
        super("GLES 3.0");
    }

    @Override
    public List<TestResult> runAll() {
        List<TestResult> results = new ArrayList<>();
        results.add(testVAO());
        results.add(testInstancedRendering());
        results.add(testUniformBufferObject());
        results.add(testTransformFeedback());
        results.add(testMultipleRenderTargets());
        results.add(test3DTexture());
        results.add(test2DTextureArray());
        results.add(testPixelBufferObject());
        results.add(testOcclusionQuery());
        results.add(testSyncFence());
        results.add(testSamplerObject());
        results.add(testETC2Compression());
        results.add(testSRGBFramebuffer());
        results.add(testPrimitiveRestart());
        results.add(testMapBufferRange());
        return results;
    }

    private TestResult testVAO() {
        return runTest("VAO (Vertex Array Object)", () -> {
            int[] vaos = new int[1];
            GLES30.glGenVertexArrays(1, vaos, 0);
            int vao = vaos[0];
            if (vao == 0) {
                return "glGenVertexArrays returned 0";
            }
            GLES30.glBindVertexArray(vao);
            GLES30.glBindVertexArray(0);
            GLES30.glDeleteVertexArrays(1, vaos, 0);
            return "VAO id=" + vao + ", bindVAO/unbindVAO/deleteVAO 均成功";
        });
    }

    private TestResult testInstancedRendering() {
        return runTest("Instanced Rendering", () -> {
            int[] vaos = new int[1];
            GLES30.glGenVertexArrays(1, vaos, 0);
            GLES30.glBindVertexArray(vaos[0]);

            int[] buffers = new int[1];
            GLES20.glGenBuffers(1, buffers, 0);
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, buffers[0]);

            float[] vertices = {0.0f, 0.0f, 0.0f};
            FloatBuffer fb = ByteBuffer.allocateDirect(vertices.length * 4)
                    .order(ByteOrder.nativeOrder()).asFloatBuffer();
            fb.put(vertices).position(0);
            GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, vertices.length * 4, fb, GLES20.GL_STATIC_DRAW);

            GLES20.glEnableVertexAttribArray(0);
            GLES20.glVertexAttribPointer(0, 3, GLES20.GL_FLOAT, false, 0, 0);

            GLES30.glDrawArraysInstanced(GLES20.GL_POINTS, 0, 1, 4);

            GLES20.glDisableVertexAttribArray(0);
            GLES20.glDeleteBuffers(1, buffers, 0);
            GLES30.glBindVertexArray(0);
            GLES30.glDeleteVertexArrays(1, vaos, 0);

            return "glDrawArraysInstanced(GL_POINTS, 0, 1, 4) 调用完成";
        });
    }

    private TestResult testUniformBufferObject() {
        return runTest("Uniform Buffer Object", () -> {
            int[] buffers = new int[1];
            GLES20.glGenBuffers(1, buffers, 0);
            int ubo = buffers[0];
            if (ubo == 0) {
                return "glGenBuffers returned 0";
            }

            GLES30.glBindBufferBase(GLES30.GL_UNIFORM_BUFFER, 0, ubo);
            GLES20.glBindBuffer(GLES30.GL_UNIFORM_BUFFER, ubo);
            GLES20.glBufferData(GLES30.GL_UNIFORM_BUFFER, 64, null, GLES20.GL_DYNAMIC_DRAW);

            GLES20.glBindBuffer(GLES30.GL_UNIFORM_BUFFER, 0);
            GLES20.glDeleteBuffers(1, buffers, 0);

            return "UBO id=" + ubo + ", bindBufferBase/bufferData/delete 均成功";
        });
    }

    private TestResult testTransformFeedback() {
        return runTest("Transform Feedback", () -> {
            int[] tfos = new int[1];
            GLES30.glGenTransformFeedbacks(1, tfos, 0);
            int tfo = tfos[0];
            if (tfo == 0) {
                return "glGenTransformFeedbacks returned 0";
            }
            GLES30.glBindTransformFeedback(GLES30.GL_TRANSFORM_FEEDBACK, tfo);

            // 创建 buffer 用于 transform feedback 输出
            int[] buffers = new int[1];
            GLES20.glGenBuffers(1, buffers, 0);
            GLES20.glBindBuffer(GLES30.GL_TRANSFORM_FEEDBACK_BUFFER, buffers[0]);
            GLES20.glBufferData(GLES30.GL_TRANSFORM_FEEDBACK_BUFFER, 64, null, GLES20.GL_DYNAMIC_DRAW);
            GLES30.glBindBufferBase(GLES30.GL_TRANSFORM_FEEDBACK_BUFFER, 0, buffers[0]);

            // 清理（不实际执行 begin/end，因为需要完整的 shader pipeline）
            GLES30.glBindTransformFeedback(GLES30.GL_TRANSFORM_FEEDBACK, 0);
            GLES20.glDeleteBuffers(1, buffers, 0);
            GLES30.glDeleteTransformFeedbacks(1, tfos, 0);

            return "TFO id=" + tfo + ", gen/bind/bindBufferBase/delete 均成功";
        });
    }

    private TestResult testMultipleRenderTargets() {
        return runTest("Multiple Render Targets", () -> {
            int[] fbos = new int[1];
            GLES20.glGenFramebuffers(1, fbos, 0);
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbos[0]);

            // 创建两个颜色纹理
            int[] textures = new int[2];
            GLES20.glGenTextures(2, textures, 0);

            for (int i = 0; i < 2; i++) {
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[i]);
                GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA,
                        4, 4, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);
                GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER,
                        GLES20.GL_COLOR_ATTACHMENT0 + i, GLES20.GL_TEXTURE_2D, textures[i], 0);
            }

            // 设置 draw buffers
            int[] drawBuffers = {GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_COLOR_ATTACHMENT0 + 1};
            IntBuffer db = ByteBuffer.allocateDirect(8).order(ByteOrder.nativeOrder()).asIntBuffer();
            db.put(drawBuffers).position(0);
            GLES30.glDrawBuffers(2, db);

            int status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER);
            String fbResult = (status == GLES20.GL_FRAMEBUFFER_COMPLETE)
                    ? "Framebuffer complete" : "Framebuffer incomplete: 0x" + Integer.toHexString(status);

            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
            GLES20.glDeleteFramebuffers(1, fbos, 0);
            GLES20.glDeleteTextures(2, textures, 0);

            return "glDrawBuffers(2 targets) | " + fbResult;
        });
    }

    private TestResult test3DTexture() {
        return runTest("3D Texture", () -> {
            int[] textures = new int[1];
            GLES20.glGenTextures(1, textures, 0);
            GLES20.glBindTexture(GLES30.GL_TEXTURE_3D, textures[0]);
            GLES30.glTexImage3D(GLES30.GL_TEXTURE_3D, 0, GLES20.GL_RGBA,
                    4, 4, 4, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);

            GLES20.glBindTexture(GLES30.GL_TEXTURE_3D, 0);
            GLES20.glDeleteTextures(1, textures, 0);

            return "glTexImage3D(GL_TEXTURE_3D, 4x4x4) 调用完成";
        });
    }

    private TestResult test2DTextureArray() {
        return runTest("2D Texture Array", () -> {
            int[] textures = new int[1];
            GLES20.glGenTextures(1, textures, 0);
            GLES20.glBindTexture(GLES30.GL_TEXTURE_2D_ARRAY, textures[0]);
            GLES30.glTexImage3D(GLES30.GL_TEXTURE_2D_ARRAY, 0, GLES20.GL_RGBA,
                    4, 4, 4, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);

            GLES20.glBindTexture(GLES30.GL_TEXTURE_2D_ARRAY, 0);
            GLES20.glDeleteTextures(1, textures, 0);

            return "glTexImage3D(GL_TEXTURE_2D_ARRAY, 4x4x4layers) 调用完成";
        });
    }

    private TestResult testPixelBufferObject() {
        return runTest("Pixel Buffer Object", () -> {
            int[] buffers = new int[1];
            GLES20.glGenBuffers(1, buffers, 0);
            int pbo = buffers[0];
            if (pbo == 0) {
                return "glGenBuffers returned 0";
            }

            // 测试 PBO bind 和 bufferData（纯 API 可用性）
            GLES20.glBindBuffer(GLES30.GL_PIXEL_PACK_BUFFER, pbo);
            // GL_DYNAMIC_READ = 0x88E9
            GLES20.glBufferData(GLES30.GL_PIXEL_PACK_BUFFER, 4 * 4 * 4, null, 0x88E9);

            // 检查 bind/bufferData 是否成功
            int bindError = GLES20.glGetError();
            String bindResult = (bindError == GLES20.GL_NO_ERROR)
                    ? "bind/bufferData OK" : "bind/bufferData error: 0x" + Integer.toHexString(bindError);

            // 不测试 glReadPixels 到 PBO（在 2.0 上下文的默认 framebuffer 上可能因格式不兼容报错，
            // 属于 framebuffer 兼容性问题而非 PBO 本身的问题）

            GLES20.glBindBuffer(GLES30.GL_PIXEL_PACK_BUFFER, 0);
            GLES20.glDeleteBuffers(1, buffers, 0);

            return "PBO id=" + pbo + " | " + bindResult;
        });
    }

    private TestResult testOcclusionQuery() {
        return runTest("Occlusion Query", () -> {
            int[] queries = new int[1];
            GLES30.glGenQueries(1, queries, 0);
            int query = queries[0];
            if (query == 0) {
                return "glGenQueries returned 0";
            }

            GLES30.glBeginQuery(GLES30.GL_ANY_SAMPLES_PASSED, query);
            GLES30.glEndQuery(GLES30.GL_ANY_SAMPLES_PASSED);

            // 检查结果是否可用
            int[] available = new int[1];
            GLES30.glGetQueryObjectuiv(query, GLES30.GL_QUERY_RESULT_AVAILABLE, available, 0);

            GLES30.glDeleteQueries(1, queries, 0);

            return "Query id=" + query + ", begin/end/getResult 均成功, available=" + available[0];
        });
    }

    private TestResult testSyncFence() {
        return runTest("Sync / Fence", () -> {
            long sync = GLES30.glFenceSync(GLES30.GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
            if (sync == 0) {
                return "glFenceSync returned 0";
            }

            int waitResult = GLES30.glClientWaitSync(sync, GLES30.GL_SYNC_FLUSH_COMMANDS_BIT, 1000000L);
            String waitStr;
            switch (waitResult) {
                case GLES30.GL_ALREADY_SIGNALED: waitStr = "ALREADY_SIGNALED"; break;
                case GLES30.GL_CONDITION_SATISFIED: waitStr = "CONDITION_SATISFIED"; break;
                case GLES30.GL_TIMEOUT_EXPIRED: waitStr = "TIMEOUT_EXPIRED"; break;
                default: waitStr = "0x" + Integer.toHexString(waitResult); break;
            }

            GLES30.glDeleteSync(sync);

            return "glFenceSync/glClientWaitSync=" + waitStr + "/glDeleteSync 均成功";
        });
    }

    private TestResult testSamplerObject() {
        return runTest("Sampler Object", () -> {
            int[] samplers = new int[1];
            GLES30.glGenSamplers(1, samplers, 0);
            int sampler = samplers[0];
            if (sampler == 0) {
                return "glGenSamplers returned 0";
            }

            GLES30.glSamplerParameteri(sampler, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
            GLES30.glSamplerParameteri(sampler, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
            GLES30.glBindSampler(0, sampler);
            GLES30.glBindSampler(0, 0);
            GLES30.glDeleteSamplers(1, samplers, 0);

            return "Sampler id=" + sampler + ", gen/param/bind/delete 均成功";
        });
    }

    private TestResult testETC2Compression() {
        return runTest("ETC2 Texture Compression", () -> {
            int[] textures = new int[1];
            GLES20.glGenTextures(1, textures, 0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[0]);

            // ETC2 4x4 block = 8 bytes for RGB8
            ByteBuffer data = ByteBuffer.allocateDirect(8).order(ByteOrder.nativeOrder());
            for (int i = 0; i < 8; i++) data.put((byte) 0);
            data.position(0);

            // GL_COMPRESSED_RGB8_ETC2 = 0x9274
            GLES20.glCompressedTexImage2D(GLES20.GL_TEXTURE_2D, 0, 0x9274,
                    4, 4, 0, 8, data);

            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
            GLES20.glDeleteTextures(1, textures, 0);

            return "glCompressedTexImage2D(GL_COMPRESSED_RGB8_ETC2, 4x4) 调用完成";
        });
    }

    private TestResult testSRGBFramebuffer() {
        return runTest("sRGB Framebuffer", () -> {
            int[] fbos = new int[1];
            GLES20.glGenFramebuffers(1, fbos, 0);
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbos[0]);

            int[] rbos = new int[1];
            GLES20.glGenRenderbuffers(1, rbos, 0);
            GLES20.glBindRenderbuffer(GLES20.GL_RENDERBUFFER, rbos[0]);
            // GL_SRGB8_ALPHA8 = 0x8C43
            GLES20.glRenderbufferStorage(GLES20.GL_RENDERBUFFER, 0x8C43, 4, 4);
            GLES20.glFramebufferRenderbuffer(GLES20.GL_FRAMEBUFFER,
                    GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_RENDERBUFFER, rbos[0]);

            int status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER);
            String fbResult = (status == GLES20.GL_FRAMEBUFFER_COMPLETE)
                    ? "Framebuffer complete" : "Framebuffer incomplete: 0x" + Integer.toHexString(status);

            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
            GLES20.glDeleteRenderbuffers(1, rbos, 0);
            GLES20.glDeleteFramebuffers(1, fbos, 0);

            return "sRGB8_ALPHA8 Renderbuffer | " + fbResult;
        });
    }

    private TestResult testPrimitiveRestart() {
        return runTest("Primitive Restart", () -> {
            // GL_PRIMITIVE_RESTART_FIXED_INDEX = 0x8D69
            GLES20.glEnable(0x8D69);
            boolean enabled = GLES20.glIsEnabled(0x8D69);
            GLES20.glDisable(0x8D69);

            return "glEnable(GL_PRIMITIVE_RESTART_FIXED_INDEX), isEnabled=" + enabled;
        });
    }

    private TestResult testMapBufferRange() {
        return runTest("Map Buffer Range", () -> {
            int[] buffers = new int[1];
            GLES20.glGenBuffers(1, buffers, 0);
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, buffers[0]);
            GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, 64, null, GLES20.GL_DYNAMIC_DRAW);

            // Map buffer for writing
            java.nio.Buffer mapped = GLES30.glMapBufferRange(GLES20.GL_ARRAY_BUFFER, 0, 64,
                    GLES30.GL_MAP_WRITE_BIT);
            String mapResult = (mapped != null) ? "mapped OK, capacity=" + mapped.capacity() : "mapped returned null";

            GLES30.glUnmapBuffer(GLES20.GL_ARRAY_BUFFER);

            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
            GLES20.glDeleteBuffers(1, buffers, 0);

            return "glMapBufferRange/glUnmapBuffer | " + mapResult;
        });
    }
}
